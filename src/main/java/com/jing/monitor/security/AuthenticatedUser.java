package com.jing.monitor.security;

import java.util.UUID;

/**
 * Lightweight authenticated principal stored in Spring Security context.
 *
 * @param id user id
 * @param email user email
 */
public record AuthenticatedUser(UUID id, String email) {
}
