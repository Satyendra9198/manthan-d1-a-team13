#!/bin/sh
# Runs once on first container start (as POSTGRES_USER = rr_owner). Passwords come from the environment (.env);
# nothing secret is stored in this file. rr_app = DML only (UPDATE/DELETE on ledger + audit revoked by Flyway V3);
# rr_readonly = SELECT only (Grafana, pgAdmin analysts).
set -eu
: "${RR_APP_DB_PASSWORD:?RR_APP_DB_PASSWORD is not set - see .env.example}"
: "${RR_READONLY_DB_PASSWORD:?RR_READONLY_DB_PASSWORD is not set - see .env.example}"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v app_pw="$RR_APP_DB_PASSWORD" -v ro_pw="$RR_READONLY_DB_PASSWORD" <<'SQL'
CREATE ROLE rr_app LOGIN PASSWORD :'app_pw';
CREATE ROLE rr_readonly LOGIN PASSWORD :'ro_pw';
GRANT CONNECT ON DATABASE returnright TO rr_app, rr_readonly;
SQL
