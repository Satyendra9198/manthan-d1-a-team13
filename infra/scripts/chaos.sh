#!/usr/bin/env sh
# "Break the gateway" on demand (Part 2 §12.2).
# Usage: infra/scripts/chaos.sh <healthy|slow|flaky|lost-responses|blackhole|declines|processing|status>
# The admin token comes from $GATEWAY_ADMIN_TOKEN, else from .env at the repo root. Nothing secret is stored here.
set -eu
PROFILE="${1:?profile required}"
GW="${GATEWAY_URL:-http://localhost:8100}"
ENV_FILE="$(cd "$(dirname "$0")/../.." && pwd)/.env"
if [ -z "${GATEWAY_ADMIN_TOKEN:-}" ] && [ -f "$ENV_FILE" ]; then
  GATEWAY_ADMIN_TOKEN="$(sed -n 's/^GATEWAY_ADMIN_TOKEN=//p' "$ENV_FILE" | head -n1)"
fi
TOKEN="${GATEWAY_ADMIN_TOKEN:?GATEWAY_ADMIN_TOKEN not set (env or .env)}"
if [ "$PROFILE" = "status" ]; then
  curl -fsS -H "X-Admin-Token: $TOKEN" "$GW/admin/faults"
else
  curl -fsS -X POST -H "X-Admin-Token: $TOKEN" "$GW/admin/faults/profile/$PROFILE"
fi
echo
