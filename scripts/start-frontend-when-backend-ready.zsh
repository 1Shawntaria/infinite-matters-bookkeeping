#!/usr/bin/env zsh
set -euo pipefail

# Wait for backend readiness, then start Next.js dev server.
BACKEND_HEALTH_URL="${BACKEND_HEALTH_URL:-http://localhost:8080/actuator/health}"
BACKEND_HEALTH_TIMEOUT_SECONDS="${BACKEND_HEALTH_TIMEOUT_SECONDS:-90}"
BACKEND_HEALTH_POLL_INTERVAL_SECONDS="${BACKEND_HEALTH_POLL_INTERVAL_SECONDS:-1}"
BACKEND_READY_ACCEPT_CODES="${BACKEND_READY_ACCEPT_CODES:-200 401 403}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
FRONTEND_DIR="$SCRIPT_DIR/../frontend"
DEADLINE=$((SECONDS + BACKEND_HEALTH_TIMEOUT_SECONDS))

check_backend_ready() {
  local response http_status body
  response="$(curl -sS -m 2 -w $'\n%{http_code}' "$BACKEND_HEALTH_URL" 2>/dev/null || true)"
  http_status="${response##*$'\n'}"
  body="${response%$'\n'*}"

  case " $BACKEND_READY_ACCEPT_CODES " in
    *" $http_status "*)
      if [[ "$http_status" == "200" ]]; then
        [[ "$body" == *'"status":"UP"'* || "$body" == *'"status" : "UP"'* ]]
      else
        return 0
      fi
      ;;
    *)
      return 1
      ;;
  esac
}

echo "Waiting for backend readiness at $BACKEND_HEALTH_URL"
echo "Timeout: ${BACKEND_HEALTH_TIMEOUT_SECONDS}s | Poll interval: ${BACKEND_HEALTH_POLL_INTERVAL_SECONDS}s"

while (( SECONDS < DEADLINE )); do
  if check_backend_ready; then
    echo "Backend is ready. Starting frontend..."
    cd "$FRONTEND_DIR"
    exec npm run dev
  fi

  sleep "$BACKEND_HEALTH_POLL_INTERVAL_SECONDS"
done

echo "Backend did not become ready within ${BACKEND_HEALTH_TIMEOUT_SECONDS}s." >&2
exit 1

