-- Runs once on first container start, as the owner role rr_owner (POSTGRES_USER).
-- Least-privilege roles: rr_app (DML only; UPDATE/DELETE on ledger + audit revoked by Flyway V3),
-- rr_readonly (Grafana, pgAdmin analysts). DEV PASSWORDS ONLY - override via secrets outside local dev.
CREATE ROLE rr_app LOGIN PASSWORD 'rr_app_dev_pw';
CREATE ROLE rr_readonly LOGIN PASSWORD 'rr_readonly_dev_pw';
GRANT CONNECT ON DATABASE returnright TO rr_app, rr_readonly;
