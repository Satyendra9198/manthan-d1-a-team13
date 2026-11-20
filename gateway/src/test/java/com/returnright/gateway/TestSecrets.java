package com.returnright.gateway;

/** Throwaway credential for the in-test gateway only. Real secrets live in the repo-root .env (see .env.example). */
final class TestSecrets {

    static final String GATEWAY_ADMIN_TOKEN = "test-gateway-admin";

    private TestSecrets() {}
}
