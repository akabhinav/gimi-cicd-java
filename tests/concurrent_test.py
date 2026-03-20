#!/usr/bin/env python3
"""
Concurrent load test for GIMI CI/CD Platform in HA mode.

Tests:
1. Concurrent pipeline triggers (50 simultaneous)
2. Concurrent API reads under load
3. Concurrent webhook triggers
4. Concurrent user registrations (race condition test)
5. Rate limiter correctness under concurrency
6. Redis queue consistency under load
"""

import json
import os
import sys
import time
import subprocess
from concurrent.futures import ThreadPoolExecutor, as_completed
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError
from collections import Counter

BASE_URL = "http://localhost:8080"
TOKEN = open("/tmp/admin_token.txt").read().strip()

# ---- Helpers ----

def api(method, path, body=None, headers=None, token=True):
    """Make an API call and return (status_code, body_dict, latency_ms)."""
    url = f"{BASE_URL}{path}"
    hdrs = {"Content-Type": "application/json"}
    if token and TOKEN:
        hdrs["Authorization"] = f"Bearer {TOKEN}"
    if headers:
        hdrs.update(headers)
    data = json.dumps(body).encode() if body else None
    req = Request(url, data=data, headers=hdrs, method=method)
    start = time.monotonic()
    try:
        resp = urlopen(req, timeout=30)
        latency = (time.monotonic() - start) * 1000
        return resp.status, json.loads(resp.read()), latency
    except HTTPError as e:
        latency = (time.monotonic() - start) * 1000
        try:
            body = json.loads(e.read())
        except Exception:
            body = {"error": str(e)}
        return e.code, body, latency
    except Exception as e:
        latency = (time.monotonic() - start) * 1000
        return 0, {"error": str(e)}, latency


def print_header(title):
    print(f"\n{'='*60}")
    print(f"  {title}")
    print(f"{'='*60}")


def print_results(results, label=""):
    statuses = Counter(r[0] for r in results)
    latencies = [r[2] for r in results]
    avg_lat = sum(latencies) / len(latencies) if latencies else 0
    p50 = sorted(latencies)[len(latencies)//2] if latencies else 0
    p95 = sorted(latencies)[int(len(latencies)*0.95)] if latencies else 0
    p99 = sorted(latencies)[int(len(latencies)*0.99)] if latencies else 0
    max_lat = max(latencies) if latencies else 0

    print(f"  {label}")
    print(f"  Total requests: {len(results)}")
    print(f"  Status codes:   {dict(statuses)}")
    print(f"  Latency (ms):   avg={avg_lat:.0f}  p50={p50:.0f}  p95={p95:.0f}  p99={p99:.0f}  max={max_lat:.0f}")
    success_rate = sum(1 for r in results if 200 <= r[0] < 300) / len(results) * 100 if results else 0
    print(f"  Success rate:   {success_rate:.1f}%")
    return statuses, success_rate


# ---- Test 1: Concurrent Pipeline Triggers ----

def test_concurrent_pipeline_triggers():
    print_header("TEST 1: Concurrent Pipeline Triggers (50 simultaneous)")

    def trigger_pipeline(i):
        pipeline = "backend-ci" if i % 2 == 0 else "frontend-deploy"
        return api("POST", "/api/runs", {"pipelineName": pipeline, "dryRun": False})

    with ThreadPoolExecutor(max_workers=50) as pool:
        futures = [pool.submit(trigger_pipeline, i) for i in range(50)]
        results = [f.result() for f in as_completed(futures)]

    statuses, success_rate = print_results(results, "Pipeline Triggers")

    # Check Redis queue
    queue_depth = int(subprocess.run(
        ["redis-cli", "ZCARD", "gimi:jobs:queue"],
        capture_output=True, text=True
    ).stdout.strip() or "0")
    total_keys = int(subprocess.run(
        ["redis-cli", "DBSIZE"],
        capture_output=True, text=True
    ).stdout.split(":")[-1].strip() or "0")
    print(f"  Redis queue depth: {queue_depth}")
    print(f"  Redis total keys:  {total_keys}")

    # Count run IDs created (handle both 200 and 202)
    successful = [r[1] for r in results if 200 <= r[0] < 300]
    run_ids = [r.get("runId") or r.get("run_id", "") for r in successful]
    unique_runs = len(set(rid for rid in run_ids if rid))
    print(f"  Successful:        {len(successful)}")
    print(f"  Unique run IDs:    {unique_runs}")
    total_jobs = sum(r.get("jobCount", r.get("job_count", 0)) for r in successful)
    print(f"  Total jobs queued: {total_jobs}")

    passed = success_rate >= 95
    print(f"  RESULT: {'PASS' if passed else 'FAIL'}")
    return passed


# ---- Test 2: Concurrent API Reads ----

def test_concurrent_api_reads():
    print_header("TEST 2: Concurrent API Reads (100 simultaneous)")

    endpoints = [
        ("GET", "/api/pipelines"),
        ("GET", "/api/runs"),
        ("GET", "/api/workers"),
        ("GET", "/actuator/health"),
        ("GET", "/api/auth/me"),
    ]

    def read_endpoint(i):
        method, path = endpoints[i % len(endpoints)]
        use_token = path != "/actuator/health"
        return api(method, path, token=use_token)

    with ThreadPoolExecutor(max_workers=100) as pool:
        futures = [pool.submit(read_endpoint, i) for i in range(100)]
        results = [f.result() for f in as_completed(futures)]

    statuses, success_rate = print_results(results, "API Reads")
    passed = success_rate >= 98
    print(f"  RESULT: {'PASS' if passed else 'FAIL'}")
    return passed


# ---- Test 3: Concurrent Webhook Triggers ----

def test_concurrent_webhooks():
    print_header("TEST 3: Concurrent Webhook Triggers (30 simultaneous)")

    def send_webhook(i):
        body = {
            "event": "push",
            "branch": f"feature/branch-{i}",
            "commit_sha": f"abc{i:04d}",
            "changed_paths": ["src/main.java"]
        }
        return api("POST", "/webhook", body, token=False)

    with ThreadPoolExecutor(max_workers=30) as pool:
        futures = [pool.submit(send_webhook, i) for i in range(30)]
        results = [f.result() for f in as_completed(futures)]

    statuses, success_rate = print_results(results, "Webhooks")
    # Some may be rate limited (120/min), that's expected
    ok_or_limited = sum(1 for r in results if r[0] in (200, 429)) / len(results) * 100
    print(f"  OK + Rate-limited: {ok_or_limited:.1f}%")
    passed = ok_or_limited >= 95
    print(f"  RESULT: {'PASS' if passed else 'FAIL'}")
    return passed


# ---- Test 4: Concurrent Registration Race Condition ----

def test_concurrent_registration():
    print_header("TEST 4: Concurrent First-User Registration (Race Condition)")

    # Clear users table
    subprocess.run(
        ["psql", "-h", "localhost", "-U", "gimi", "-d", "gimi", "-c", "DELETE FROM users;"],
        capture_output=True, env={**os.environ, "PGPASSWORD": os.environ.get("PGPASSWORD", "gimi")}
    )

    def register_admin(i):
        body = {
            "username": f"admin",  # Same username - should only succeed once
            "password": "Str0ngP@ss!",
            "email": f"admin{i}@gimi.dev"
        }
        return api("POST", "/api/auth/register", body, token=False)

    with ThreadPoolExecutor(max_workers=20) as pool:
        futures = [pool.submit(register_admin, i) for i in range(20)]
        results = [f.result() for f in as_completed(futures)]

    statuses, _ = print_results(results, "Registration Race")
    created = sum(1 for r in results if r[0] == 201)
    conflicts = sum(1 for r in results if r[0] == 409)
    forbidden = sum(1 for r in results if r[0] == 403)
    print(f"  Created (201):   {created}")
    print(f"  Conflict (409):  {conflicts}")
    print(f"  Forbidden (403): {forbidden}")

    # Exactly 1 user should be created
    user_count = subprocess.run(
        ["psql", "-h", "localhost", "-U", "gimi", "-d", "gimi", "-t", "-c", "SELECT COUNT(*) FROM users;"],
        capture_output=True, text=True, env={**os.environ, "PGPASSWORD": os.environ.get("PGPASSWORD", "gimi")}
    ).stdout.strip()
    print(f"  Users in DB:     {user_count}")

    passed = created <= 1 and int(user_count) == 1
    print(f"  RESULT: {'PASS' if passed else 'FAIL'} (exactly 1 admin created)")

    # Re-register admin for subsequent tests
    if created == 0:
        api("POST", "/api/auth/register",
            {"username": "admin", "password": "Str0ngP@ss!", "email": "admin@gimi.dev"},
            token=False)

    # Refresh token
    _, login_resp, _ = api("POST", "/api/auth/login",
        {"username": "admin", "password": "Str0ngP@ss!"}, token=False)
    global TOKEN
    TOKEN = login_resp.get("token", TOKEN)
    with open("/tmp/admin_token.txt", "w") as f:
        f.write(TOKEN)

    return passed


# ---- Test 5: Rate Limiter Under Concurrent Load ----

def test_rate_limiter_concurrent():
    print_header("TEST 5: Rate Limiter Under Concurrent Load (50 login attempts)")

    def login_attempt(i):
        body = {"username": "wrong_user", "password": "wrong_pass"}
        return api("POST", "/api/auth/login", body, token=False)

    # Wait for rate limit window to reset
    time.sleep(2)

    with ThreadPoolExecutor(max_workers=50) as pool:
        futures = [pool.submit(login_attempt, i) for i in range(50)]
        results = [f.result() for f in as_completed(futures)]

    statuses, _ = print_results(results, "Rate Limit Test")
    rate_limited = sum(1 for r in results if r[0] == 429)
    auth_failures = sum(1 for r in results if r[0] == 401)
    print(f"  Rate limited (429): {rate_limited}")
    print(f"  Auth failed (401):  {auth_failures}")

    passed = rate_limited > 0  # At least some should be rate limited
    print(f"  RESULT: {'PASS' if passed else 'FAIL'} (rate limiter engaged)")
    return passed


# ---- Test 6: Mixed Concurrent Workload ----

def test_mixed_concurrent_workload():
    print_header("TEST 6: Mixed Concurrent Workload (200 requests)")

    def mixed_request(i):
        op = i % 5
        if op == 0:
            return ("trigger", *api("POST", "/api/runs",
                {"pipelineName": "backend-ci", "dryRun": False}))
        elif op == 1:
            return ("list_runs", *api("GET", "/api/runs"))
        elif op == 2:
            return ("list_pipes", *api("GET", "/api/pipelines"))
        elif op == 3:
            return ("health", *api("GET", "/actuator/health", token=False))
        else:
            return ("workers", *api("GET", "/api/workers"))

    start = time.monotonic()
    with ThreadPoolExecutor(max_workers=50) as pool:
        futures = [pool.submit(mixed_request, i) for i in range(200)]
        raw_results = [f.result() for f in as_completed(futures)]
    elapsed = time.monotonic() - start

    # Group by operation
    by_op = {}
    for label, status, body, latency in raw_results:
        by_op.setdefault(label, []).append((status, body, latency))

    total_ok = 0
    total = 0
    for op, results in sorted(by_op.items()):
        statuses = Counter(r[0] for r in results)
        ok = sum(1 for r in results if 200 <= r[0] < 300)
        avg_lat = sum(r[2] for r in results) / len(results)
        print(f"  {op:12s}: {len(results):3d} reqs | {dict(statuses)} | avg={avg_lat:.0f}ms")
        total_ok += ok
        total += len(results)

    rps = total / elapsed
    success_rate = total_ok / total * 100
    print(f"\n  Total:          {total} requests in {elapsed:.1f}s ({rps:.0f} req/s)")
    print(f"  Overall success: {success_rate:.1f}%")

    passed = success_rate >= 90 and rps >= 10
    print(f"  RESULT: {'PASS' if passed else 'FAIL'}")
    return passed


# ---- Main ----

if __name__ == "__main__":
    print("=" * 60)
    print("  GIMI CI/CD — Concurrent Load Test Suite (HA Mode)")
    print("=" * 60)

    results = {}
    results["1_pipeline_triggers"] = test_concurrent_pipeline_triggers()
    results["2_api_reads"] = test_concurrent_api_reads()
    results["3_webhooks"] = test_concurrent_webhooks()
    results["4_registration_race"] = test_concurrent_registration()
    results["5_rate_limiter"] = test_rate_limiter_concurrent()
    results["6_mixed_workload"] = test_mixed_concurrent_workload()

    print_header("FINAL RESULTS")
    all_passed = True
    for test, passed in results.items():
        status = "PASS" if passed else "FAIL"
        print(f"  {test:30s} {status}")
        if not passed:
            all_passed = False

    print(f"\n  Overall: {'ALL TESTS PASSED' if all_passed else 'SOME TESTS FAILED'}")
    print("=" * 60)

    sys.exit(0 if all_passed else 1)
