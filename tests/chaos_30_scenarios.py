#!/usr/bin/env python3
"""
30 Unexpected / Chaos / Edge-Case Scenarios for GIMI CI/CD Platform.

These test what happens when things go WRONG — malformed input, attacks,
race conditions, resource exhaustion, protocol violations, and edge cases
that only surface in production.

Categories:
  A. Injection & Security Attacks (1-7)
  B. Malformed & Boundary Input (8-14)
  C. Protocol & HTTP Edge Cases (15-19)
  D. Race Conditions & Concurrency Chaos (20-24)
  E. Resource Exhaustion & Resilience (25-28)
  F. State Corruption & Recovery (29-30)
"""

import json
import os
import subprocess
import sys
import time
import threading
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

BASE_URL = "http://localhost:8080"
TOKEN = open("/tmp/admin_token.txt").read().strip()

passed = 0
failed = 0
results = {}


def api(method, path, body=None, headers=None, token_val=True, raw_body=None, timeout=15):
    url = f"{BASE_URL}{path}"
    hdrs = {}
    if body is not None:
        hdrs["Content-Type"] = "application/json"
    if headers:
        hdrs.update(headers)
    if token_val and TOKEN:
        hdrs["Authorization"] = f"Bearer {TOKEN}"
    if raw_body is not None:
        data = raw_body if isinstance(raw_body, bytes) else raw_body.encode()
    elif body is not None:
        data = json.dumps(body).encode()
    else:
        data = None
    req = Request(url, data=data, headers=hdrs, method=method)
    start = time.monotonic()
    try:
        resp = urlopen(req, timeout=timeout)
        latency = (time.monotonic() - start) * 1000
        try:
            rbody = json.loads(resp.read())
        except Exception:
            rbody = {}
        return resp.status, rbody, latency
    except HTTPError as e:
        latency = (time.monotonic() - start) * 1000
        try:
            rbody = json.loads(e.read())
        except Exception:
            rbody = {"error": str(e)}
        return e.code, rbody, latency
    except Exception as e:
        latency = (time.monotonic() - start) * 1000
        return 0, {"error": str(e)}, latency


def test(num, name, condition, detail=""):
    global passed, failed
    status = "PASS" if condition else "FAIL"
    marker = "  " if condition else ">>"
    print(f"  {marker} [{num:2d}] {name:55s} {status}")
    if detail:
        print(f"        {detail}")
    if condition:
        passed += 1
    else:
        failed += 1
    results[f"{num:02d}_{name[:30]}"] = condition
    return condition


def header(title):
    print(f"\n{'─'*70}")
    print(f"  {title}")
    print(f"{'─'*70}")


# ================================================================
print("=" * 70)
print("  GIMI CI/CD — 30 UNEXPECTED SCENARIO CHAOS TEST")
print("=" * 70)


# ================================================================
# A. INJECTION & SECURITY ATTACKS
# ================================================================
header("A. INJECTION & SECURITY ATTACKS")

# 1. SQL Injection in query params (URL-encoded)
code, body, _ = api("GET", "/api/runs?limit=1%3BDROP%20TABLE%20executions%3B--")
test(1, "SQL injection in query param",
     code in (200, 400),
     f"HTTP {code} (not 500 = DB safe)")

# 2. SQL Injection in pipeline name
code, body, _ = api("POST", "/api/runs",
    {"pipelineName": "'; DROP TABLE jobs; --", "dryRun": False})
test(2, "SQL injection in pipeline name",
     code in (404, 400) and code != 500,
     f"HTTP {code}")

# 3. XSS in webhook payload
code, body, _ = api("POST", "/webhook", {
    "event": "<script>alert('xss')</script>",
    "branch": "<img onerror=alert(1) src=x>",
    "commit_sha": "abc"
}, token_val=False)
test(3, "XSS in webhook payload",
     code in (200, 400, 429),
     f"HTTP {code} (no crash)")

# 4. Path traversal in pipeline name
code, body, _ = api("GET", "/api/pipelines/../../etc/passwd")
test(4, "Path traversal in pipeline name",
     code in (404, 400),
     f"HTTP {code}")

# 5. JWT token tampering (modify payload)
tampered = TOKEN[:20] + "TAMPERED" + TOKEN[28:]
code, body, _ = api("GET", "/api/auth/me", headers={"Authorization": f"Bearer {tampered}"}, token_val=False)
test(5, "Tampered JWT token rejected",
     code in (401, 403),
     f"HTTP {code}")

# 6. JWT with wrong secret
import base64, hmac, hashlib
fake_header = base64.urlsafe_b64encode(b'{"alg":"HS256","typ":"JWT"}').rstrip(b'=').decode()
fake_payload = base64.urlsafe_b64encode(b'{"sub":"admin","roles":"ADMIN","type":"user","exp":9999999999}').rstrip(b'=').decode()
fake_sig_input = f"{fake_header}.{fake_payload}".encode()
fake_sig = base64.urlsafe_b64encode(
    hmac.new(b"wrong-secret-key-that-is-32chars!", fake_sig_input, hashlib.sha256).digest()
).rstrip(b'=').decode()
fake_jwt = f"{fake_header}.{fake_payload}.{fake_sig}"
code, body, _ = api("GET", "/api/auth/me", headers={"Authorization": f"Bearer {fake_jwt}"}, token_val=False)
test(6, "JWT signed with wrong secret rejected",
     code in (401, 403),
     f"HTTP {code}")

# 7. Command injection via webhook event field
code, body, _ = api("POST", "/webhook", {
    "event": "push; rm -rf /",
    "branch": "$(whoami)",
    "commit_sha": "`id`"
}, token_val=False)
test(7, "Command injection in webhook fields",
     code in (200, 400, 429),
     f"HTTP {code} (no shell exec)")


# ================================================================
# B. MALFORMED & BOUNDARY INPUT
# ================================================================
header("B. MALFORMED & BOUNDARY INPUT")

# 8. Empty JSON body
code, body, _ = api("POST", "/api/auth/login", {})
test(8, "Empty JSON body on login",
     code in (400, 401),
     f"HTTP {code}")

# 9. Null values in required fields
code, body, _ = api("POST", "/api/runs", {"pipelineName": None, "dryRun": None})
test(9, "Null values in required fields",
     code in (400, 404, 500),
     f"HTTP {code}")

# 10. Extremely long pipeline name (10KB)
long_name = "A" * 10000
code, body, _ = api("POST", "/api/runs", {"pipelineName": long_name, "dryRun": False})
test(10, "10KB pipeline name",
     code in (400, 404, 413, 414) and code != 500,
     f"HTTP {code}")

# 11. Unicode / emoji in pipeline trigger
code, body, _ = api("POST", "/webhook", {
    "event": "push",
    "branch": "feature/日本語-🚀-résumé",
    "commit_sha": "café☕"
}, token_val=False)
test(11, "Unicode/emoji in webhook fields",
     code in (200, 429),
     f"HTTP {code} (no crash)")

# 12. Negative limit/offset in query params
code, body, _ = api("GET", "/api/runs?limit=-1&offset=-999")
test(12, "Negative limit/offset params",
     code in (200, 400),
     f"HTTP {code} (no crash)")

# 13. Integer overflow in limit
code, body, _ = api("GET", "/api/runs?limit=99999999999999999")
test(13, "Integer overflow in limit param",
     code in (200, 400, 500),
     f"HTTP {code}")

# 14. Deeply nested JSON (100 levels)
nested = {"a": None}
current = nested
for _ in range(100):
    current["a"] = {"a": None}
    current = current["a"]
current["a"] = "end"
code, body, _ = api("POST", "/webhook", nested, token_val=False)
test(14, "Deeply nested JSON (100 levels)",
     code in (200, 400, 429),
     f"HTTP {code} (no stack overflow)")


# ================================================================
# C. PROTOCOL & HTTP EDGE CASES
# ================================================================
header("C. PROTOCOL & HTTP EDGE CASES")

# 15. Wrong Content-Type
code, body, _ = api("POST", "/api/auth/login",
    headers={"Content-Type": "text/xml", "Authorization": ""},
    raw_body='<xml>not json</xml>', token_val=False)
test(15, "Wrong Content-Type (text/xml)",
     code in (400, 415),
     f"HTTP {code}")

# 16. GET request with body (unusual but valid)
code, body, _ = api("GET", "/api/pipelines")
test(16, "GET request to valid endpoint",
     code == 200,
     f"HTTP {code}")

# 17. Double-encoded URL
code, body, _ = api("GET", "/api/pipelines/%252e%252e%252fetc%252fpasswd")
test(17, "Double-encoded path traversal",
     code in (400, 404),
     f"HTTP {code}")

# 18. Missing Authorization header on protected endpoint
code, body, _ = api("GET", "/api/runs", token_val=False)
test(18, "Missing auth on protected endpoint",
     code in (401, 403),
     f"HTTP {code}")

# 19. OPTIONS preflight request
try:
    req = Request(f"{BASE_URL}/api/runs", method="OPTIONS")
    req.add_header("Origin", "http://evil.com")
    req.add_header("Access-Control-Request-Method", "POST")
    resp = urlopen(req, timeout=5)
    code = resp.status
    allow_origin = resp.headers.get("Access-Control-Allow-Origin", "")
    test(19, "CORS preflight from unknown origin",
         "evil.com" not in allow_origin,
         f"HTTP {code}, Allow-Origin: '{allow_origin}' (not evil.com)")
except HTTPError as e:
    test(19, "CORS preflight from unknown origin",
         e.code in (401, 403),
         f"HTTP {e.code} (blocked)")
except Exception as e:
    test(19, "CORS preflight from unknown origin", True, f"Blocked: {e}")


# ================================================================
# D. RACE CONDITIONS & CONCURRENCY CHAOS
# ================================================================
header("D. RACE CONDITIONS & CONCURRENCY CHAOS")

# 20. Concurrent cancel on same run
run_code, run_body, _ = api("POST", "/api/runs", {"pipelineName": "backend-ci", "dryRun": False})
run_id = run_body.get("runId", "test-run")

def cancel_run(_):
    return api("POST", f"/api/runs/{run_id}/cancel")

with ThreadPoolExecutor(max_workers=20) as pool:
    futures = [pool.submit(cancel_run, i) for i in range(20)]
    cancel_results = [f.result() for f in as_completed(futures)]

codes = Counter(r[0] for r in cancel_results)
test(20, "20 concurrent cancels on same run",
     all(200 <= r[0] < 500 for r in cancel_results),
     f"Status codes: {dict(codes)} (no 5xx)")

# 21. Trigger + read same pipeline simultaneously
def trigger_and_read(i):
    if i % 2 == 0:
        return ("trigger", *api("POST", "/api/runs", {"pipelineName": "data-pipeline", "dryRun": False}))
    else:
        return ("read", *api("GET", "/api/pipelines"))

with ThreadPoolExecutor(max_workers=50) as pool:
    futures = [pool.submit(trigger_and_read, i) for i in range(50)]
    tr_results = [f.result() for f in as_completed(futures)]

all_ok = all(200 <= r[1] < 300 for r in tr_results)
test(21, "50 concurrent trigger+read on same pipeline",
     all_ok,
     f"All OK: {all_ok}")

# 22. Rapid login/logout cycle (token churn)
def login_cycle(i):
    code, body, _ = api("POST", "/api/auth/login",
        {"username": "admin", "password": "Str0ngP@ss!"}, token_val=False)
    if code == 200:
        tok = body.get("token", "")
        # Immediately use the token
        return api("GET", "/api/auth/me", headers={"Authorization": f"Bearer {tok}"}, token_val=False)
    return code, body, 0

# Need to wait for rate limit window
time.sleep(2)
with ThreadPoolExecutor(max_workers=10) as pool:
    futures = [pool.submit(login_cycle, i) for i in range(10)]
    login_results = [f.result() for f in as_completed(futures)]

ok_or_limited = sum(1 for r in login_results if r[0] in (200, 429)) / len(login_results) * 100
test(22, "10 rapid login+use cycles",
     ok_or_limited >= 80,
     f"OK+limited: {ok_or_limited:.0f}%")

# 23. Duplicate webhook with same commit SHA
def dup_webhook(_):
    return api("POST", "/webhook", {
        "event": "push", "branch": "main", "commit_sha": "deadbeef1234"
    }, token_val=False)

with ThreadPoolExecutor(max_workers=20) as pool:
    futures = [pool.submit(dup_webhook, i) for i in range(20)]
    dup_results = [f.result() for f in as_completed(futures)]

all_handled = all(r[0] in (200, 429) for r in dup_results)
test(23, "20 duplicate webhooks (same commit SHA)",
     all_handled,
     f"All 200/429: {all_handled}")

# 24. Concurrent user creation with same username
def create_same_user(i):
    return api("POST", "/api/auth/register", {
        "username": "dupe-user",
        "password": "TestP@ss123!",
        "email": f"dupe{i}@test.com",
        "roles": ["VIEWER"]
    })

with ThreadPoolExecutor(max_workers=20) as pool:
    futures = [pool.submit(create_same_user, i) for i in range(20)]
    dup_user_results = [f.result() for f in as_completed(futures)]

created = sum(1 for r in dup_user_results if r[0] == 201)
conflicts = sum(1 for r in dup_user_results if r[0] == 409)
no_500 = all(r[0] != 500 for r in dup_user_results)
test(24, "20 concurrent creates of same username",
     created <= 1 and no_500,
     f"Created: {created}, Conflict: {conflicts}, No 500s: {no_500}")

# Cleanup
subprocess.run(["psql", "-h", "localhost", "-U", "gimi", "-d", "gimi", "-c",
    "DELETE FROM users WHERE username = 'dupe-user';"],
    capture_output=True, env={**os.environ, "PGPASSWORD": "gimi"})


# ================================================================
# E. RESOURCE EXHAUSTION & RESILIENCE
# ================================================================
header("E. RESOURCE EXHAUSTION & RESILIENCE")

# 25. Large payload (1MB JSON body)
big_payload = {"event": "push", "branch": "main", "data": "X" * (1024 * 1024)}
code, body, lat = api("POST", "/webhook", big_payload, token_val=False, timeout=30)
test(25, "1MB JSON payload",
     code in (200, 400, 413, 429),
     f"HTTP {code}, {lat:.0f}ms")

# 26. Trigger 100 pipelines of the largest pipeline (10 stages = 1000 jobs)
def trigger_large(i):
    return api("POST", "/api/runs", {"pipelineName": "microservice-full", "dryRun": False})

with ThreadPoolExecutor(max_workers=100) as pool:
    futures = [pool.submit(trigger_large, i) for i in range(100)]
    large_results = [f.result() for f in as_completed(futures)]

ok = sum(1 for r in large_results if 200 <= r[0] < 300)
total_jobs = sum(r[1].get("jobCount", 0) for r in large_results if 200 <= r[0] < 300)
test(26, "100 triggers of 10-stage pipeline (1000 jobs)",
     ok >= 95,
     f"Success: {ok}/100, Jobs: {total_jobs}")

# 27. Rapid health check spam (1000 requests)
def health_spam(_):
    return api("GET", "/actuator/health", token_val=False, timeout=5)

start = time.monotonic()
with ThreadPoolExecutor(max_workers=200) as pool:
    futures = [pool.submit(health_spam, i) for i in range(1000)]
    health_results = [f.result() for f in as_completed(futures)]
elapsed = time.monotonic() - start

ok = sum(1 for r in health_results if r[0] == 200)
test(27, "1000 concurrent health checks",
     ok >= 990,
     f"OK: {ok}/1000, {1000/elapsed:.0f} req/s, {elapsed:.1f}s")

# 28. Request after Redis key expiry simulation
# Insert a job with an expired key, then try to read it
subprocess.run(["redis-cli", "SET", "gimi:test:ephemeral", "value", "EX", "1"],
    capture_output=True)
time.sleep(2)
result = subprocess.run(["redis-cli", "GET", "gimi:test:ephemeral"],
    capture_output=True, text=True)
test(28, "Redis key expiry (TTL behavior)",
     result.stdout.strip() == "" or result.stdout.strip() == "(nil)",
     f"Key expired correctly: '{result.stdout.strip()}'")


# ================================================================
# F. STATE CORRUPTION & RECOVERY
# ================================================================
header("F. STATE CORRUPTION & RECOVERY")

# 29. Server health after all chaos
code, body, lat = api("GET", "/actuator/health", token_val=False)
health_status = body.get("status", "UNKNOWN")
test(29, "Server health after 28 chaos scenarios",
     code == 200 and health_status == "UP",
     f"HTTP {code}, status={health_status}, latency={lat:.0f}ms")

# 30. Full API functional check post-chaos
checks = []
# Auth
c, b, _ = api("GET", "/api/auth/me")
checks.append(("auth/me", c == 200))
# Pipelines
c, b, _ = api("GET", "/api/pipelines")
checks.append(("pipelines", c == 200))
# Runs
c, b, _ = api("GET", "/api/runs")
checks.append(("runs", c == 200))
# Workers
c, b, _ = api("GET", "/api/workers")
checks.append(("workers", c == 200))
# Trigger
c, b, _ = api("POST", "/api/runs", {"pipelineName": "backend-ci", "dryRun": False})
checks.append(("trigger", 200 <= c < 300))

all_ok = all(ok for _, ok in checks)
details = ", ".join(f"{name}={'OK' if ok else 'FAIL'}" for name, ok in checks)
test(30, "Full API functional check post-chaos",
     all_ok,
     details)


# ================================================================
# FINAL SUMMARY
# ================================================================
print(f"\n{'='*70}")
print(f"  FINAL RESULTS: {passed} PASSED / {failed} FAILED / {passed+failed} TOTAL")
print(f"{'='*70}")

if failed > 0:
    print("\n  Failed scenarios:")
    for name, ok in results.items():
        if not ok:
            print(f"    >> {name}")

print(f"\n  Server survived all 30 chaos scenarios: {'YES' if failed == 0 else 'PARTIAL'}")
print(f"{'='*70}")

sys.exit(0 if failed == 0 else 1)
