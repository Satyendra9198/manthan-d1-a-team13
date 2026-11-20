package com.returnright.core;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/** Correlation id held in the logging MDC (set per request by CorrelationIdFilter, per job run by jobs). */
public final class Correlation {

    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9\\-_.]{8,64}$");

    private Correlation() {}

    public static String start(String incoming) {
        String cid = incoming != null && VALID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, cid);
        return cid;
    }

    public static String id() {
        String cid = MDC.get(MDC_KEY);
        return cid != null ? cid : start(null);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
