package com.returnright.web;

import com.returnright.auth.AuthService;
import com.returnright.core.Actor;
import com.returnright.core.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

/** Small helpers shared by controllers. */
final class Web {

    private Web() {}

    static Actor actor(Jwt jwt) {
        if (jwt == null) {
            throw new ApiException("UNAUTHENTICATED", "Please sign in.", HttpStatus.UNAUTHORIZED);
        }
        return AuthService.actorFrom(jwt);
    }

    /** Accepts {@code "7"}, {@code 7} or {@code W/"7"}. */
    static int version(String ifMatch) {
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

    static String etag(int version) {
        return "\"" + version + "\"";
    }
}
