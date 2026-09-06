#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
BACKEND_PORT=${BACKEND_PORT:-18080}
FRONTEND_PORT=${FRONTEND_PORT:-14173}
BACKEND_URL="http://127.0.0.1:${BACKEND_PORT}"
FRONTEND_URL="http://127.0.0.1:${FRONTEND_PORT}"
ARTIFACT_DIR=${SMOKE_ARTIFACT_DIR:-"${ROOT_DIR}/artifacts/smoke"}
RUN_DIR=$(mktemp -d "${TMPDIR:-/tmp}/promova-smoke.XXXXXX")
BACKEND_LOG="${RUN_DIR}/backend.log"
FRONTEND_LOG="${RUN_DIR}/frontend.log"
BACKEND_PID=""
FRONTEND_PID=""

mkdir -p "${ARTIFACT_DIR}"
: >"${BACKEND_LOG}"
: >"${FRONTEND_LOG}"

sanitize_log() {
  sed -E \
    -e 's/(Bearer )[A-Za-z0-9._-]+/\1[REDACTED]/g' \
    -e 's/("token"[[:space:]]*:[[:space:]]*")[^"]+/\1[REDACTED]/g' \
    "$1" >"$2"
}

print_log_tail() {
  tail -n 80 "$1" | sed -E \
    -e 's/(Bearer )[A-Za-z0-9._-]+/\1[REDACTED]/g' \
    -e 's/("token"[[:space:]]*:[[:space:]]*")[^"]+/\1[REDACTED]/g' >&2
}

stop_process() {
  local pid=$1
  if [[ -z "${pid}" ]] || ! kill -0 "${pid}" 2>/dev/null; then
    return
  fi

  kill "${pid}" 2>/dev/null || true
  for _ in {1..25}; do
    if ! kill -0 "${pid}" 2>/dev/null; then
      wait "${pid}" 2>/dev/null || true
      return
    fi
    sleep 0.2
  done

  kill -KILL "${pid}" 2>/dev/null || true
  wait "${pid}" 2>/dev/null || true
}

cleanup() {
  local status=$?
  trap - EXIT INT TERM
  stop_process "${FRONTEND_PID}"
  stop_process "${BACKEND_PID}"
  sanitize_log "${BACKEND_LOG}" "${ARTIFACT_DIR}/backend.log"
  sanitize_log "${FRONTEND_LOG}" "${ARTIFACT_DIR}/frontend.log"
  rm -rf "${RUN_DIR}"
  exit "${status}"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

fail() {
  echo "Smoke test failed: $*" >&2
  echo "Backend log (last 80 sanitized lines):" >&2
  print_log_tail "${BACKEND_LOG}"
  echo "Frontend log (last 80 sanitized lines):" >&2
  print_log_tail "${FRONTEND_LOG}"
  exit 1
}

assert_port_free() {
  local port=$1
  node -e '
    const net = require("node:net");
    const port = Number(process.argv[1]);
    const server = net.createServer();
    server.once("error", () => process.exit(1));
    server.listen(port, "127.0.0.1", () => server.close());
  ' "${port}" || fail "port ${port} is already in use"
}

wait_for_status() {
  local name=$1
  local pid=$2
  local url=$3
  local expected=$4
  local attempts=${5:-60}
  local status

  for ((attempt = 1; attempt <= attempts; attempt += 1)); do
    if ! kill -0 "${pid}" 2>/dev/null; then
      wait "${pid}" 2>/dev/null || true
      fail "${name} exited before becoming ready"
    fi

    status=$(curl --silent --output /dev/null --write-out '%{http_code}' \
      --connect-timeout 1 --max-time 2 "${url}" || true)
    if [[ "${status}" == "${expected}" ]]; then
      return
    fi
    sleep 1
  done

  fail "${name} did not return HTTP ${expected} from ${url} within ${attempts}s"
}

request_expect() {
  local expected=$1
  local output=$2
  shift 2
  local status
  local request_url=""
  local argument
  for argument in "$@"; do
    request_url=${argument}
  done
  status=$(curl --silent --show-error --output "${output}" --write-out '%{http_code}' \
    --connect-timeout 2 --max-time 10 "$@") || fail "request to ${request_url} failed"
  [[ "${status}" == "${expected}" ]] \
    || fail "expected HTTP ${expected}, got ${status} from ${request_url}"
}

assert_clean_startup_log() {
  local name=$1
  local log=$2
  if grep -Eiq 'APPLICATION FAILED TO START|Schema-validation|Migration failed|(^|[[:space:]])ERROR([[:space:]]|$)|Exception' "${log}"; then
    fail "${name} log contains a startup error or exception"
  fi
}

for command in curl java node npm; do
  command -v "${command}" >/dev/null || fail "required command is unavailable: ${command}"
done

assert_port_free "${BACKEND_PORT}"
assert_port_free "${FRONTEND_PORT}"

echo "Building frontend for ${BACKEND_URL}"
PROMOVA_API_BASE_URL="${BACKEND_URL}" npm --prefix "${ROOT_DIR}" run build

echo "Building backend boot JAR"
(
  cd "${ROOT_DIR}/backend"
  ./gradlew bootJar --no-daemon --no-configuration-cache --stacktrace
)

BACKEND_JAR="${ROOT_DIR}/backend/build/libs/promova-backend-0.1.0.jar"
[[ -f "${BACKEND_JAR}" ]] || fail "backend boot JAR was not created at ${BACKEND_JAR}"

echo "Starting backend on ${BACKEND_URL} with the isolated ci profile"
PROMOVA_CORS_ALLOWED_ORIGINS="${FRONTEND_URL}" java -jar "${BACKEND_JAR}" \
  --spring.profiles.active=ci \
  --server.address=127.0.0.1 \
  --server.port="${BACKEND_PORT}" >"${BACKEND_LOG}" 2>&1 &
BACKEND_PID=$!

wait_for_status "backend" "${BACKEND_PID}" "${BACKEND_URL}/profile" 401 90

UNAUTH_BODY="${RUN_DIR}/unauthenticated.json"
request_expect 401 "${UNAUTH_BODY}" "${BACKEND_URL}/profile"

REGISTER_BODY="${RUN_DIR}/register.json"
request_expect 200 "${REGISTER_BODY}" \
  --request POST \
  --header 'Content-Type: application/json' \
  --data '{"name":"CI Smoke User","email":"ci-smoke@promova.invalid","password":"ci-smoke-password"}' \
  "${BACKEND_URL}/auth/register"

LOGIN_BODY="${RUN_DIR}/login.json"
request_expect 200 "${LOGIN_BODY}" \
  --request POST \
  --header 'Content-Type: application/json' \
  --data '{"email":"ci-smoke@promova.invalid","password":"ci-smoke-password"}' \
  "${BACKEND_URL}/auth/login"

AUTH_TOKEN=$(node -e '
  const fs = require("node:fs");
  const payload = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
  if (typeof payload.token !== "string" || payload.token.length < 16) process.exit(1);
  process.stdout.write(payload.token);
' "${LOGIN_BODY}") || fail "login response did not contain a usable token"

ME_BODY="${RUN_DIR}/me.json"
request_expect 200 "${ME_BODY}" \
  --header "Authorization: Bearer ${AUTH_TOKEN}" \
  "${BACKEND_URL}/auth/me"
node -e '
  const fs = require("node:fs");
  const payload = JSON.parse(fs.readFileSync(process.argv[1], "utf8"));
  if (payload.email !== "ci-smoke@promova.invalid" || payload.role !== "EMPLOYEE") process.exit(1);
' "${ME_BODY}" || fail "authenticated current-user response was unexpected"

PROFILE_BODY="${RUN_DIR}/profile.json"
request_expect 200 "${PROFILE_BODY}" \
  --header "Authorization: Bearer ${AUTH_TOKEN}" \
  "${BACKEND_URL}/profile"

echo "Starting built frontend on ${FRONTEND_URL}"
HOST=127.0.0.1 PORT="${FRONTEND_PORT}" node "${ROOT_DIR}/scripts/dist-server.js" \
  >"${FRONTEND_LOG}" 2>&1 &
FRONTEND_PID=$!
wait_for_status "frontend" "${FRONTEND_PID}" "${FRONTEND_URL}/" 200 30

FRONTEND_HTML="${RUN_DIR}/index.html"
FRONTEND_CONFIG="${RUN_DIR}/promova-config.js"
request_expect 200 "${FRONTEND_HTML}" "${FRONTEND_URL}/"
request_expect 200 "${FRONTEND_CONFIG}" "${FRONTEND_URL}/promova-config.js"
grep -F 'promova-config.js' "${FRONTEND_HTML}" >/dev/null \
  || fail "frontend HTML does not load runtime API configuration"
grep -F "${BACKEND_URL}" "${FRONTEND_CONFIG}" >/dev/null \
  || fail "frontend is not configured for the smoke backend"

CORS_HEADERS="${RUN_DIR}/cors-headers.txt"
CORS_BODY="${RUN_DIR}/cors-body.txt"
request_expect 200 "${CORS_BODY}" \
  --dump-header "${CORS_HEADERS}" \
  --request OPTIONS \
  --header "Origin: ${FRONTEND_URL}" \
  --header 'Access-Control-Request-Method: GET' \
  "${BACKEND_URL}/profile"
grep -iF "access-control-allow-origin: ${FRONTEND_URL}" "${CORS_HEADERS}" >/dev/null \
  || fail "backend did not allow the built frontend origin"

kill -0 "${BACKEND_PID}" 2>/dev/null || fail "backend exited during smoke assertions"
kill -0 "${FRONTEND_PID}" 2>/dev/null || fail "frontend exited during smoke assertions"
assert_clean_startup_log "backend" "${BACKEND_LOG}"
assert_clean_startup_log "frontend" "${FRONTEND_LOG}"

echo "Smoke test passed: unauthenticated boundary, registration/login, authenticated profile, frontend, and CORS"
