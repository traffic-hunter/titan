package org.traffichunter.titan.monitor.http;

import jakarta.servlet.http.HttpServletRequest;

public final class MonitoringAuthorization {

    private final String token;

    public MonitoringAuthorization(String token) {
        this.token = token;
    }

    public boolean required() {
        return token != null && !token.isBlank();
    }

    public boolean allows(HttpServletRequest request) {
        if (!required()) {
            return true;
        }
        return getBearerToken(token).equals(request.getHeader("Authorization"));
    }

    private static String getBearerToken(String token) {
        return "Bearer " + token;
    }
}
