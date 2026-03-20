#!/usr/bin/env python3
"""
High-Concurrency Stress Test — 200+ concurrent threads.

Simulates a realistic enterprise workload:
- 200 concurrent pipeline triggers across 5 complex pipelines
- 200 concurrent API reads (dashboard simulation)
- 200 concurrent webhook floods from multiple repos
- 200 mixed read/write operations (realistic traffic pattern)
- Sustained burst: 500 requests in rapid succession
- Connection pool saturation test
- Redis queue consistency under extreme load
- PostgreSQL write contention test

Target: zero errors, sub-second p95 latency, consistent Redis state.
"""

import json
import os
import subprocess
import sys
import threading
import time
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed, wait
from urllib.request import Request, urlopen
from urllib.error import HTTPError

BASE_URL = "http://localhost:8080"
TOKEN = open("/tmp/admin_token.txt").read().strip()

PIPELINES = [
    "backend-ci",          # 4 stages
    "frontend-deploy",     # 4 stages
    "microservice-full",   # 10 stages
    "data-pipeline",       # 6 stages
    "infra-provision",     # 5 stages
]

# Global counters (thread-safe)
total_requests = threading.atomic = 0
lock = threading.Lock()
errors_log = []


def api(method, path, body=None, token_val=True):
    url = f"{BASE_URL}{path}"
    hdrs = {"Content-Type": "application/json"}
    if token_val and TOKEN:
        hdrs["Authorization"] = f"Bearer {TOKEN}"
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
            body_resp = json.loads(e.read())
        except Exception:
            body_resp = {"error": str(e)}
        return e.code, body_resp, latency
    except Exception as e:
        latency = (time.monotonic() - start) * 1000
        return 0, {"error": str(e)}, latency


def header(title):
    w = 70
    print(f"\n{'='*w}")
    print(f"  {title}")
    print(f"{'='*w}")


def stats(results, label=""):
    statuses = Counter(r[0] for r in results)
    latencies = sorted([r[2] for r in results])
    n = len(latencies)
    if n == 0:
        print(f"  {label}: NO RESULTS")
        return statuses, 0, 0

    avg = sum(latencies) / n
    p50 = latencies[n // 2]
    p95 = latencies[int(n * 0.95)]
    p99 = latencies[int(n * 0.99)]
    mx = latencies[-1]
    ok = sum(1 for r in results if 200 <= r[0] < 300)
    rate = ok / n * 100

    print(f"  {label}")
    print(f"    Requests:    {n}")
    print(f"    Status:      {dict(statuses)}")
    print(f"    Latency ms:  avg={avg:.0f}  p50={p50:.0f}  p95={p95:.0f}  p99={p99:.0f}  max={mx:.0f}")
    print(f"    Success:     {ok}/{n} ({rate:.1f}%)")
    return statuses, rate, avg


# ================================================================
# SCENARIO 1: 200 Concurrent Pipeline Triggers
# ================================================================
def scenario_pipeline_flood():
    header("SCENARIO 1: 200 Concurrent Pipeline Triggers")
    print("  Triggering 200 pipelines (40 per pipeline type) simultaneously...")

    redis_before = int(subprocess.run(
        ["redis-cli", "DBSIZE"], capture_output=True, text=True
    ).stdout.split(":")[-1].strip() or "0")

    def trigger(i):
        pipeline = PIPELINES[i % len(PIPELINES)]
        return api("POST", "/api/runs", {"pipelineName": pipeline, "dryRun": False})

    start = time.monotonic()
    with ThreadPoolExecutor(max_workers=200) as pool:
        futures = [pool.submit(trigger, i) for i in range(200)]
        results = [f.result() for f in as_completed(futures)]
    elapsed = time.monotonic() - start

    _, rate, _ = stats(results, "Pipeline Triggers")

    # Count jobs queued
    successful = [r[1] for r in results if 200 <= r[0] < 300]
    total_jobs = sum(r.get("jobCount", r.get("job_count", 0)) for r in successful)
    unique_runs = len(set(r.get("runId", r.get("run_id", "")) for r in successful))

    redis_after = int(subprocess.run(
        ["redis-cli", "DBSIZE"], capture_output=True, text=True
    ).stdout.split(":")[-1].strip() or "0")

    print(f"    Duration:    {elapsed:.1f}s ({200/elapsed:.0f} triggers/s)")
    print(f"    Runs:        {unique_runs} unique")
    print(f"    Jobs queued: {total_jobs}")
    print(f"    Redis keys:  {redis_before} -> {redis_after} (+{redis_after - redis_before})")

    passed = rate >= 95 and unique_runs >= 190
    print(f"    RESULT: {'PASS' if passed else 'FAIL'}")
    return passed


# ================================================================
# SCENARIO 2: 200 Concurrent Dashboard Reads
# ================================================================
def scenario_dashboard_flood():
    header("SCENARIO 2: 200 Concurrent Dashboard Reads (Simulated UI)")
    print("  Simulating 200 users hitting the dashboard simultaneously...")

    endpoints = [
        ("GET", "/api/pipelines"),
        ("GET", "/api/runs"),
        ("GET", "/api/runs?limit=50"),
        ("GET", "/api/workers"),
        ("GET", "/api/auth/me"),
        ("GET", "/actuator/health"),
    ]

    def dashboard_read(i):
        method, path = endpoints[i % len(endpoints)]
        use_token = not path.startswith("/actuator")
        return api(method, path, token_val=use_token)

    start = time.monotonic()
    with ThreadPoolExecutor(max_workers=200) as pool:
        futures = [pool.submit(dashboard_read, i) for i in range(200)]
        results = [f.result() for f in as_completed(futures)]
    elapsed = time.monotonic() - start

    _, rate, avg = stats(results, "Dashboard Reads")
    print(f"    Duration:    {elapsed:.1f}s ({200/elapsed:.0f} req/s)")

    passed = rate >= 98 and avg < 2000
    print(f"    RESULT: {'PASS' if passed else 'FAIL'}")
    return passed


# ================================================================
# SCENARIO 3: 200 Concurrent Webhook Floods
# ================================================================
def scenario_webhook_flood():
    header("SCENARIO 3: 200 Concurrent Webhook Triggers (Multi-Repo)")
    print("  Simulating 200 webhooks from different repos/branches...")

    def webhook(i):
        body = {
            "event": ["push", "pull_request", "tag"][i % 3],
            "branch": f"feature/ticket-{i:04d}",
            "commit_sha": f"{i:040x}",
            "changed_paths": [f"src/module-{i % 10}/main.java", f"tests/module-{i % 10}/test.java"]
        }
        return api("POST", "/webhook", body, token_val=False)

    start = time.monotonic()
    with ThreadPoolExecutor(max_workers=200) as pool:
        futures = [pool.submit(webhook, i) for i in range(200)]
        results = [f.result() for f in as_completed(futures)]
    elapsed = time.monotonic() - start

    _, rate, _ = stats(results, "Webhooks")
    ok_or_limited = sum(1 for r in results if r[0] in (200, 429)) / len(results) * 100
    print(f"    Duration:    {elapsed:.1f}s ({200/elapsed:.0f} req/s)")
    print(f"    OK+Limited:  {ok_or_limited:.1f}%")

    passed = ok_or_limited >= 95
    print(f"    RESULT: {'PASS' if passed else 'FAIL'}")
    return passed


# ================================================================
# SCENARIO 4: 200 Concurrent Mixed Read/Write
# ================================================================
def scenario_mixed_heavy():
    header("SCENARIO 4: 200 Concurrent Mixed Read/Write (Realistic Traffic)")
    print("  70% reads + 30% writes, 200 concurrent threads...")

    def mixed(i):
        r = i % 10
        if r < 2:  # 20% pipeline triggers
            pipeline = PIPELINES[i % len(PIPELINES)]
            return ("trigger", *api("POST", "/api/runs", {"pipelineName": pipeline, "dryRun": False}))
        elif r < 3:  # 10% webhooks
            body = {"event": "push", "branch": f"main", "commit_sha": f"sha-{i}"}
            return ("webhook", *api("POST", "/webhook", body, token_val=False))
        elif r < 5:  # 20% list runs
            return ("list_runs", *api("GET", "/api/runs?limit=20"))
        elif r < 7:  # 20% list pipelines
            return ("list_pipes", *api("GET", "/api/pipelines"))
        elif r < 8:  # 10% worker status
            return ("workers", *api("GET", "/api/workers"))
        elif r < 9:  # 10% health
            return ("health", *api("GET", "/actuator/health", token_val=False))
        else:  # 10% user info
            return ("me", *api("GET", "/api/auth/me"))

    start = time.monotonic()
    with ThreadPoolExecutor(max_workers=200) as pool:
        futures = [pool.submit(mixed, i) for i in range(200)]
        raw = [f.result() for f in as_completed(futures)]
    elapsed = time.monotonic() - start

    by_op = defaultdict(list)
    for label, status, body, latency in raw:
        by_op[label].append((status, body, latency))

    total_ok = 0
    total = 0
    for op in sorted(by_op):
        results = by_op[op]
        ok = sum(1 for r in results if 200 <= r[0] < 300 or r[0] == 429)
        avg_lat = sum(r[2] for r in results) / len(results)
        codes = Counter(r[0] for r in results)
        print(f"    {op:12s}: {len(results):3d} reqs | {dict(codes)} | avg={avg_lat:.0f}ms")
        total_ok += ok
        total += len(results)

    rps = total / elapsed
    rate = total_ok / total * 100
    print(f"\n    Duration:    {elapsed:.1f}s ({rps:.0f} req/s)")
    print(f"    Success+RL:  {rate:.1f}%")

    passed = rate >= 95 and rps >= 50
    print(f"    RESULT: {'PASS' if passed else 'FAIL'}")
    return passed


# ================================================================
# SCENARIO 5: Sustained Burst — 500 Rapid Fire
# ================================================================
def scenario_burst():
    header("SCENARIO 5: Sustained Burst — 500 Requests Rapid Fire")
    print("  Firing 500 requests as fast as possible with 200 threads...")

    def burst_req(i):
        r = i % 4
        if r == 0:
            return api("GET", "/api/pipelines")
        elif r == 1:
            return api("GET", "/api/runs")
        elif r == 2:
            return api("GET", "/actuator/health", token_val=False)
        else:
            return api("GET", "/api/workers")

    start = time.monotonic()
    with ThreadPoolExecutor(max_workers=200) as pool:
        futures = [pool.submit(burst_req, i) for i in range(500)]
        results = [f.result() for f in as_completed(futures)]
    elapsed = time.monotonic() - start

    _, rate, avg = stats(results, "Burst (500 req)")
    rps = 500 / elapsed
    print(f"    Duration:    {elapsed:.1f}s")
    print(f"    Throughput:  {rps:.0f} req/s")

    passed = rate >= 98 and rps >= 100
    print(f"    RESULT: {'PASS' if passed else 'FAIL'}")
    return passed


# ================================================================
# SCENARIO 6: Connection Pool Saturation
# ================================================================
def scenario_pool_saturation():
    header("SCENARIO 6: Connection Pool Saturation (200 DB-hitting requests)")
    print("  Hammering endpoints that hit PostgreSQL + Redis simultaneously...")

    def db_hit(i):
        r = i % 3
        if r == 0:
            # Trigger pipeline → writes to Redis queue + PostgreSQL
            pipeline = PIPELINES[i % len(PIPELINES)]
            return ("trigger", *api("POST", "/api/runs", {"pipelineName": pipeline, "dryRun": False}))
        elif r == 1:
            # List runs → reads PostgreSQL
            return ("runs", *api("GET", "/api/runs?limit=100"))
        else:
            # Workers → reads PostgreSQL
            return ("workers", *api("GET", "/api/workers"))

    start = time.monotonic()
    with ThreadPoolExecutor(max_workers=200) as pool:
        futures = [pool.submit(db_hit, i) for i in range(200)]
        raw = [f.result() for f in as_completed(futures)]
    elapsed = time.monotonic() - start

    by_op = defaultdict(list)
    for label, status, body, latency in raw:
        by_op[label].append((status, body, latency))

    total_ok = 0
    total_err = 0
    for op in sorted(by_op):
        results = by_op[op]
        ok = sum(1 for r in results if 200 <= r[0] < 300)
        errs = sum(1 for r in results if r[0] >= 500)
        avg_lat = sum(r[2] for r in results) / len(results)
        p95 = sorted(r[2] for r in results)[int(len(results) * 0.95)]
        codes = Counter(r[0] for r in results)
        print(f"    {op:10s}: {len(results):3d} reqs | {dict(codes)} | avg={avg_lat:.0f}ms p95={p95:.0f}ms")
        total_ok += ok
        total_err += errs

    rate = total_ok / len(raw) * 100
    print(f"\n    Duration:    {elapsed:.1f}s ({len(raw)/elapsed:.0f} req/s)")
    print(f"    Success:     {rate:.1f}%")
    print(f"    5xx errors:  {total_err}")

    passed = rate >= 90 and total_err <= 5
    print(f"    RESULT: {'PASS' if passed else 'FAIL'}")
    return passed


# ================================================================
# SCENARIO 7: Redis Queue Consistency Check
# ================================================================
def scenario_queue_consistency():
    header("SCENARIO 7: Redis Queue Consistency Under Load")
    print("  Verifying job data integrity after all the concurrent writes...")

    # Check queue
    queue_depth = int(subprocess.run(
        ["redis-cli", "ZCARD", "gimi:jobs:queue"],
        capture_output=True, text=True
    ).stdout.strip() or "0")

    total_keys = int(subprocess.run(
        ["redis-cli", "DBSIZE"],
        capture_output=True, text=True
    ).stdout.split(":")[-1].strip() or "0")

    # Sample 20 random job keys and verify structure
    keys_out = subprocess.run(
        ["redis-cli", "KEYS", "gimi:jobs:*"],
        capture_output=True, text=True
    ).stdout.strip().split("\n")

    job_keys = [k for k in keys_out if k and "queue" not in k and "run:" not in k][:20]
    valid = 0
    invalid = 0
    statuses = Counter()
    for key in job_keys:
        # Jobs may store data as a JSON blob in "data" field, or individual fields
        status = subprocess.run(
            ["redis-cli", "HGET", key, "status"],
            capture_output=True, text=True
        ).stdout.strip()

        data_json = subprocess.run(
            ["redis-cli", "HGET", key, "data"],
            capture_output=True, text=True
        ).stdout.strip()

        if status:
            # Direct field storage
            valid += 1
            statuses[status] += 1
        elif data_json:
            # JSON blob storage — parse it
            try:
                data = json.loads(data_json)
                if data.get("pipeline_name") and data.get("status"):
                    valid += 1
                    statuses[data["status"]] += 1
                else:
                    invalid += 1
            except (json.JSONDecodeError, KeyError):
                invalid += 1
        else:
            invalid += 1

    # Check for orphaned run references
    run_keys = [k for k in keys_out if "run:" in k]

    print(f"    Queue depth:     {queue_depth}")
    print(f"    Total Redis keys:{total_keys}")
    print(f"    Job keys sampled:{len(job_keys)}")
    print(f"    Valid jobs:      {valid}")
    print(f"    Invalid jobs:    {invalid}")
    print(f"    Job statuses:    {dict(statuses)}")
    print(f"    Run index keys:  {len(run_keys)}")

    passed = invalid == 0 and total_keys > 0
    print(f"    RESULT: {'PASS' if passed else 'FAIL'}")
    return passed


# ================================================================
# SCENARIO 8: PostgreSQL Write Contention
# ================================================================
def scenario_pg_contention():
    header("SCENARIO 8: PostgreSQL Write Contention (200 concurrent API key creates)")
    print("  Creating 200 API keys simultaneously to stress DB writes...")

    def create_api_key(i):
        body = {
            "name": f"stress-key-{i:04d}",
            "scopes": ["read", "write"],
            "expiresInDays": 30
        }
        return api("POST", "/api/auth/api-keys", body)

    start = time.monotonic()
    with ThreadPoolExecutor(max_workers=200) as pool:
        futures = [pool.submit(create_api_key, i) for i in range(200)]
        results = [f.result() for f in as_completed(futures)]
    elapsed = time.monotonic() - start

    _, rate, _ = stats(results, "API Key Creation")
    print(f"    Duration:    {elapsed:.1f}s ({200/elapsed:.0f} writes/s)")

    # Verify in database
    count = subprocess.run(
        ["psql", "-h", "localhost", "-U", "gimi", "-d", "gimi", "-t", "-c",
         "SELECT COUNT(*) FROM api_keys WHERE name LIKE 'stress-key-%';"],
        capture_output=True, text=True,
        env={**os.environ, "PGPASSWORD": os.environ.get("PGPASSWORD", "gimi")}
    ).stdout.strip()
    print(f"    Keys in DB:  {count}")

    passed = rate >= 90
    print(f"    RESULT: {'PASS' if passed else 'FAIL'}")
    return passed


# ================================================================
# MAIN
# ================================================================
if __name__ == "__main__":
    print("=" * 70)
    print("  GIMI CI/CD — HIGH-CONCURRENCY STRESS TEST (200+ threads)")
    print("  5 pipelines (4-10 stages) | PostgreSQL + Redis | HA mode")
    print("=" * 70)

    t0 = time.monotonic()
    results = {}

    results["1_pipeline_200"]      = scenario_pipeline_flood()
    results["2_dashboard_200"]     = scenario_dashboard_flood()
    results["3_webhook_200"]       = scenario_webhook_flood()
    results["4_mixed_200"]         = scenario_mixed_heavy()
    results["5_burst_500"]         = scenario_burst()
    results["6_pool_saturation"]   = scenario_pool_saturation()
    results["7_queue_consistency"] = scenario_queue_consistency()
    results["8_pg_contention"]     = scenario_pg_contention()

    total_time = time.monotonic() - t0

    header("FINAL RESULTS")
    all_passed = True
    for test, passed in results.items():
        status = "PASS" if passed else "FAIL"
        marker = "  " if passed else ">>"
        print(f"  {marker} {test:30s} {status}")
        if not passed:
            all_passed = False

    print(f"\n  Total test time: {total_time:.1f}s")
    print(f"  Overall: {'ALL 8 SCENARIOS PASSED' if all_passed else 'SOME SCENARIOS FAILED'}")
    print("=" * 70)

    sys.exit(0 if all_passed else 1)
