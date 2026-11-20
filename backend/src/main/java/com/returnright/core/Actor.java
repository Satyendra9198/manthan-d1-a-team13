package com.returnright.core;

import com.returnright.core.enums.ActorType;
import com.returnright.core.enums.Role;

/**
 * Who performs an action. Customers carry {@code customerId}; staff carry {@code userId}; SYSTEM actors carry a
 * descriptive id such as {@code system:reconciler}.
 */
public record Actor(ActorType type, String id, Role role, Long customerId, Long userId, String name) {

    public static Actor customer(long customerId, String name) {
        return new Actor(ActorType.CUSTOMER, "customer:" + customerId, Role.CUSTOMER, customerId, null, name);
    }

    public static Actor staff(long userId, Role role, String name) {
        return new Actor(ActorType.STAFF, "user:" + userId, role, null, userId, name);
    }

    public static Actor system(String job) {
        return new Actor(ActorType.SYSTEM, "system:" + job, Role.SYSTEM, null, null, job);
    }

    public boolean is(Role r) {
        return role == r;
    }
}
