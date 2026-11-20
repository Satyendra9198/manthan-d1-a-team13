package com.returnright.support;

/**
 * Credentials for THROWAWAY test infrastructure only (Testcontainers databases that live for one test run).
 * Real secrets never appear in code: they live in the repo-root .env (template: .env.example).
 */
public final class TestSecrets {

    public static final String OWNER_DB_PASSWORD = "test-owner-db";
    public static final String APP_DB_PASSWORD = "test-app-db";
    public static final String READONLY_DB_PASSWORD = "test-readonly-db";
    public static final String JWT_SECRET = "test-jwt-secret-at-least-32-characters-long";
    public static final String GATEWAY_ADMIN_TOKEN = "test-gateway-admin";
    public static final String STAFF_PASSWORD = "Test-Staff-1";

    private TestSecrets() {}
}
