package com.returnright.core.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Accepts or generates X-Correlation-ID, echoes it, puts it in the MDC; adds baseline security headers. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String cid = Correlation.start(req.getHeader(Correlation.HEADER));
        res.setHeader(Correlation.HEADER, cid);
        res.setHeader("Access-Control-Expose-Headers", "X-Correlation-ID, ETag, Idempotent-Replayed");
        try {
            chain.doFilter(req, res);
        } finally {
            Correlation.clear();
        }
    }
}
