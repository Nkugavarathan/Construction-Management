/**
 * Centralized API client.
 *
 * - Same-origin requests to /api/v1 (proxied to Spring Boot by next.config.mjs).
 * - The access token lives only in this module's memory, never in localStorage (ADR-0002).
 * - On a 401 the client refreshes once (via the HttpOnly cookie) and retries the request once.
 *   Concurrent 401s share a single in-flight refresh, so a burst of requests does not rotate the
 *   refresh token several times.
 * - Errors are parsed from RFC 9457 Problem Details into ApiError.
 */
export const API_BASE = "/api/v1";

let accessToken = null;
let refreshInFlight = null;
let sessionExpiredHandler = null;

export function setAccessToken(token) {
  accessToken = token;
}

export function getAccessToken() {
  return accessToken;
}

export function onSessionExpired(handler) {
  sessionExpiredHandler = handler;
}

export class ApiError extends Error {
  constructor(status, problem) {
    super(problem?.detail || problem?.title || `Request failed (${status})`);
    this.name = "ApiError";
    this.status = status;
    this.code = problem?.code ?? (status === 0 ? "NETWORK_ERROR" : `HTTP_${status}`);
    this.fieldErrors = Array.isArray(problem?.errors) ? problem.errors : [];
    this.correlationId = problem?.correlationId ?? null;
    this.problem = problem ?? null;
  }

  get isUnauthorized() {
    return this.status === 401;
  }

  get isForbidden() {
    return this.status === 403;
  }

  get isNotFound() {
    return this.status === 404;
  }

  get isConflict() {
    return this.status === 409;
  }
}

async function parseError(response) {
  let problem = null;
  try {
    const text = await response.text();
    problem = text ? JSON.parse(text) : null;
  } catch {
    problem = null;
  }
  return new ApiError(response.status, problem);
}

/** Calls POST /auth/refresh once even if many requests ask at the same time. Resolves to the auth response or null. */
export function refreshSession() {
  if (!refreshInFlight) {
    refreshInFlight = fetch(`${API_BASE}/auth/refresh`, { method: "POST", credentials: "same-origin" })
      .then(async (response) => {
        if (!response.ok) {
          setAccessToken(null);
          return null;
        }
        const body = await response.json();
        setAccessToken(body.accessToken);
        return body;
      })
      .catch(() => null)
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

function buildUrl(path, query) {
  const url = path.startsWith("http") ? path : `${API_BASE}${path}`;
  if (!query) return url;
  const params = new URLSearchParams();
  Object.entries(query).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") return;
    params.append(key, String(value));
  });
  const qs = params.toString();
  return qs ? `${url}?${qs}` : url;
}

/**
 * @param {string} path  path below /api/v1, e.g. "/projects"
 * @param {{method?: string, body?: any, query?: object, headers?: object, signal?: AbortSignal,
 *          auth?: boolean, raw?: boolean}} options
 */
export async function apiRequest(path, options = {}) {
  const { method = "GET", body, query, headers = {}, signal, auth = true, raw = false } = options;
  const url = buildUrl(path, query);

  const send = async () => {
    const finalHeaders = { Accept: "application/json, application/problem+json", ...headers };
    let payload = body;
    if (body !== undefined && !(body instanceof FormData)) {
      finalHeaders["Content-Type"] = "application/json";
      payload = JSON.stringify(body);
    }
    if (auth && accessToken) {
      finalHeaders.Authorization = `Bearer ${accessToken}`;
    }
    try {
      return await fetch(url, { method, headers: finalHeaders, body: payload, signal, credentials: "same-origin" });
    } catch (error) {
      if (error?.name === "AbortError") throw error;
      throw new ApiError(0, { detail: "Cannot reach the server. Check your connection and try again." });
    }
  };

  let response = await send();
  if (response.status === 401 && auth) {
    const refreshed = await refreshSession();
    if (refreshed) {
      response = await send();
    } else {
      sessionExpiredHandler?.();
    }
  }
  if (!response.ok) {
    throw await parseError(response);
  }
  if (raw) return response;
  if (response.status === 204) return null;
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

export const api = {
  get: (path, query, options) => apiRequest(path, { ...options, query }),
  post: (path, body, options) => apiRequest(path, { ...options, method: "POST", body }),
  put: (path, body, options) => apiRequest(path, { ...options, method: "PUT", body }),
  patch: (path, body, options) => apiRequest(path, { ...options, method: "PATCH", body }),
  delete: (path, options) => apiRequest(path, { ...options, method: "DELETE" }),
};

/** Downloads a protected file through the API (with the bearer token) and saves it in the browser. */
export async function downloadFile(path, fallbackName = "download") {
  const response = await apiRequest(path, { raw: true });
  const disposition = response.headers.get("Content-Disposition") ?? "";
  const match = /filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i.exec(disposition);
  const name = match ? decodeURIComponent(match[1] ?? match[2]) : fallbackName;
  const blob = await response.blob();
  const href = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = href;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(href);
}

/** User-facing text for an error, including the correlation ID for unexpected failures. */
export function errorMessage(error) {
  if (!error) return "";
  if (error instanceof ApiError) {
    if (error.status >= 500 && error.correlationId) {
      return `${error.message} (reference ${error.correlationId.slice(0, 8)})`;
    }
    return error.message;
  }
  return error.message || "Something went wrong.";
}

/** Copies server-side field errors (Problem Details errors[]) into a React Hook Form instance. */
export function applyServerErrors(error, setError) {
  if (!(error instanceof ApiError) || error.fieldErrors.length === 0) return false;
  error.fieldErrors.forEach(({ field, message }) => setError(field, { type: "server", message }));
  return true;
}
