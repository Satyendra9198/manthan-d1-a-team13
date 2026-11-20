#!/usr/bin/env sh
# "Break the gateway" on demand (Part 2 §12.2).
# Usage: infra/scripts/chaos.sh <healthy|slow|flaky|lost-responses|blackhole|declines|processing|status>
set -eu
PROFILE="${1:?profile required}"
GW="${GATEWAY_URL:-http://localhost:8100}"
TOKEN="${GATEWAY_ADMIN_TOKEN:-dev-admin-token}"
if [ "$PROFILE" = "status" ]; then
  curl -fsS -H "X-Admin-Token: $TOKEN" "$GW/admin/faults"
else
  curl -fsS -X POST -H "X-Admin-Token: $TOKEN" "$GW/admin/faults/profile/$PROFILE"
fi
echo
