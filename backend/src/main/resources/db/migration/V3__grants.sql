-- Least-privilege grants (roles created by infra/postgres/init; skipped when absent, e.g. in Testcontainers)
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rr_app') THEN
    GRANT USAGE ON SCHEMA public TO rr_app;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO rr_app;
    GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO rr_app;
    REVOKE UPDATE, DELETE, TRUNCATE ON ledger_transactions, ledger_entries, audit_log, ledger_accounts FROM rr_app;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rr_readonly') THEN
    GRANT USAGE ON SCHEMA public TO rr_readonly;
    GRANT SELECT ON ALL TABLES IN SCHEMA public TO rr_readonly;
  END IF;
END $$;

-- future tables created by the owner get the same grants
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rr_app') THEN
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO rr_app;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO rr_app;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rr_readonly') THEN
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO rr_readonly;
  END IF;
END $$;
