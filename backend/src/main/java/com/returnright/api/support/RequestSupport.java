package com.returnright.api.support;

import com.returnright.auth.service.AuthService;
import com.returnright.core.model.Actor;
import com.returnright.core.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

/** Request helpers shared by all controllers: caller identity, If-Match parsing, ETag formatting. */
public final class RequestSupport {

    private RequestSupport() {}

    public static Actor actor(Jwt jwt) {
        if (jwt == null) {
            throw new ApiException("UNAUTHENTICATED", "Please sign in.", HttpStatus.UNAUTHORIZED);
        }
        return AuthService.actorFrom(jwt);
    }

    /** Accepts {@code "7"}, {@code 7} or {@code W/"7"}. */
    public static int version(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw ApiException.badRequest("PRECONDITION_REQUIRED", "The If-Match header is required.");
        }
        String v = ifMatch.strip().replace("W/", "").replace("\"", "");
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("PRECONDITION_REQUIRED", "If-Match must be the return version.");
        }
    }

    public static String etag(int version) {
        return "\"" + version + "\"";
    }
}
