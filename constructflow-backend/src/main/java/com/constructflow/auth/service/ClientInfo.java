package com.constructflow.auth.service;

import jakarta.servlet.http.HttpServletRequest;

/** Request metadata stored with a session, so users and admins can recognise where a session came from. */
public record ClientInfo(String ipAddress, String userAgent) {

    public static ClientInfo from(HttpServletRequest request) {
        String agent = request.getHeader("User-Agent");
        if (agent != null && agent.length() > 255) {
            agent = agent.substring(0, 255);
        }
        return new ClientInfo(request.getRemoteAddr(), agent);
    }
}
