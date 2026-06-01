#!/usr/bin/env bash
# Smoke test for Smart Print Server Scheduler API
# Run after the server is up on port 8080

BASE="http://localhost:8080"
PASS=0; FAIL=0

check() {
  local label="$1" expected_code="$2"
  shift 2
  local http_code body
  body=$(curl -s -o /tmp/resp.json -w "%{http_code}" "$@")
  http_code=$body
  body=$(cat /tmp/resp.json)

  if [ "$http_code" = "$expected_code" ]; then
    echo "✅  $label (HTTP $http_code)"
    echo "$body" | python3 -m json.tool 2>/dev/null | head -8
    ((PASS++))
  else
    echo "❌  $label — expected HTTP $expected_code, got $http_code"
    echo "$body"
    ((FAIL++))
  fi
  echo ""
}

echo "==========================================="
echo " Smart Print Server — Smoke Tests"
echo "==========================================="

check "GET /api/health" 200 \
  "$BASE/api/health"

check "POST /start (valid)" 202 \
  -X POST "$BASE/api/simulation/start" \
  -H "Content-Type: application/json" \
  -d '{"numUsers":3,"numPrinters":2,"queueCapacity":10,"jobIntervalMs":3000,"algorithm":"HYBRID","colorJobRatio":0.5,"smallJobPercentage":0.3,"simulationSpeed":1.0}'

check "POST /start again → 409" 409 \
  -X POST "$BASE/api/simulation/start" \
  -H "Content-Type: application/json" \
  -d '{"numUsers":1,"numPrinters":1,"queueCapacity":5,"jobIntervalMs":500,"algorithm":"FCFS","colorJobRatio":0.5,"smallJobPercentage":0.3,"simulationSpeed":1.0}'

check "GET /state (RUNNING)" 200 \
  "$BASE/api/simulation/state"

check "POST /pause" 200 \
  -X POST "$BASE/api/simulation/pause"

check "POST /pause again → 409" 409 \
  -X POST "$BASE/api/simulation/pause"

check "POST /resume" 200 \
  -X POST "$BASE/api/simulation/resume"

check "PUT /configure (algo+speed)" 200 \
  -X PUT "$BASE/api/simulation/configure" \
  -H "Content-Type: application/json" \
  -d '{"algorithm":"SJF","simulationSpeed":2.0}'

check "GET /state (scheduler=SJF)" 200 \
  "$BASE/api/simulation/state"

check "POST /stop" 200 \
  -X POST "$BASE/api/simulation/stop"

check "POST /resume on stopped → 409" 409 \
  -X POST "$BASE/api/simulation/resume"

check "POST /start invalid config → 400" 400 \
  -X POST "$BASE/api/simulation/start" \
  -H "Content-Type: application/json" \
  -d '{"numUsers":0,"numPrinters":25,"algorithm":"INVALID"}'

check "POST /reset" 200 \
  -X POST "$BASE/api/simulation/reset"

check "GET /metrics" 200 \
  "$BASE/api/simulation/metrics"

check "GET /export json" 200 \
  "$BASE/api/simulation/export?format=csv&type=jobs"

echo "==========================================="
echo " Results: $PASS passed, $FAIL failed"
echo "==========================================="
[ $FAIL -eq 0 ] && exit 0 || exit 1
