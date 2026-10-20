"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api, onSessionExpired, refreshSession, setAccessToken } from "@/lib/api/client";

const AuthContext = createContext(null);

/**
 * Session state for the whole app. On first load there is no access token in memory, so the provider
 * calls /auth/refresh; the HttpOnly cookie (if present) restores the session without the page ever
 * seeing a long-lived credential.
 */
export function AuthProvider({ children }) {
  const queryClient = useQueryClient();
  const [state, setState] = useState({ status: "loading", me: null });

  const applySession = useCallback((authResponse) => {
    setAccessToken(authResponse.accessToken);
    setState({ status: "authenticated", me: authResponse.me });
  }, []);

  const clearSession = useCallback(() => {
    setAccessToken(null);
    queryClient.clear();
    setState({ status: "anonymous", me: null });
  }, [queryClient]);

  useEffect(() => {
    let cancelled = false;
    refreshSession().then((result) => {
      if (cancelled) return;
      if (result) {
        setState({ status: "authenticated", me: result.me });
      } else {
        setState({ status: "anonymous", me: null });
      }
    });
    onSessionExpired(() => clearSession());
    return () => {
      cancelled = true;
    };
  }, [clearSession]);

  const login = useCallback(async (credentials) => {
    const result = await api.post("/auth/login", credentials, { auth: false });
    applySession(result);
    return result.me;
  }, [applySession]);

  const register = useCallback(async (payload) => {
    const result = await api.post("/auth/register", payload, { auth: false });
    applySession(result);
    return result.me;
  }, [applySession]);

  const logout = useCallback(async () => {
    try {
      await api.post("/auth/logout", undefined, { auth: false });
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const switchOrganization = useCallback(async (organizationId) => {
    const result = await api.post("/auth/switch-organization", { organizationId });
    queryClient.clear();
    applySession(result);
  }, [applySession, queryClient]);

  const changePassword = useCallback(async (payload) => {
    const result = await api.post("/auth/change-password", payload);
    applySession(result);
  }, [applySession]);

  const value = useMemo(() => {
    const permissions = new Set(state.me?.permissions ?? []);
    return {
      ...state,
      permissions,
      can: (permission) => permissions.has(permission),
      canAny: (...list) => list.some((p) => permissions.has(p)),
      login,
      register,
      logout,
      switchOrganization,
      changePassword,
    };
  }, [state, login, register, logout, switchOrganization, changePassword]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider");
  return context;
}
