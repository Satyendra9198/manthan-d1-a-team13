#!/usr/bin/env sh
# Creates .env from .env.example with fresh random secrets. Refuses to overwrite an existing .env.
# Usage (repo root): infra/scripts/init-env.sh [staff-password]
set -eu
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
[ -f "$ROOT/.env" ] && { echo ".env already exists - delete it first if you really want new secrets." >&2; exit 1; }
secret() { head -c "${1:-24}" /dev/urandom | base64 | tr -d '\n=' | tr '+/' 'AB'; }
STAFF="${1:-Demo-$(secret 6)-1}"
while IFS= read -r line; do
  case "$line" in
    \#*|"") echo "$line" ;;
    RR_SEED_STAFF_PASSWORD=*) echo "RR_SEED_STAFF_PASSWORD=$STAFF" ;;
    RR_JWT_SECRET=*) echo "RR_JWT_SECRET=$(secret 48)" ;;
    *=*) echo "${line%%=*}=$(secret)" ;;
    *) echo "$line" ;;
  esac
done < "$ROOT/.env.example" > "$ROOT/.env"
echo "Wrote $ROOT/.env. Staff password for the demo: $STAFF"
