import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, api, errorMessage, getAccessToken, onSessionExpired, refreshSession, setAccessToken } from "./client";

function jsonResponse(status, body) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": status >= 400 ? "application/problem+json" : "application/json" },
  });
}

describe("api client", () => {
  beforeEach(() => {
    setAccessToken("expired-token");
    onSessionExpired(null);
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("sends the bearer token and skips empty query parameters", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(200, { ok: true }));
    setAccessToken("abc");
    await api.get("/projects", { q: "", status: "ACTIVE", page: 0 });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/v1/projects?status=ACTIVE&page=0");
    expect(init.headers.Authorization).toBe("Bearer abc");
  });

  it("refreshes once for concurrent 401s and retries each request", async () => {
    let refreshCalls = 0;
    vi.spyOn(globalThis, "fetch").mockImplementation(async (url, init) => {
      if (url.endsWith("/auth/refresh")) {
        refreshCalls += 1;
        return jsonResponse(200, { accessToken: "fresh-token", me: {} });
      }
      return init.headers.Authorization === "Bearer fresh-token" ? jsonResponse(200, { url }) : jsonResponse(401, { code: "UNAUTHENTICATED" });
    });

    const results = await Promise.all([api.get("/a"), api.get("/b"), api.get("/c")]);

    expect(refreshCalls).toBe(1);
    expect(results.map((r) => r.url)).toEqual(["/api/v1/a", "/api/v1/b", "/api/v1/c"]);
    expect(getAccessToken()).toBe("fresh-token");
  });

  it("reports an expired session when the refresh fails", async () => {
    const expired = vi.fn();
    onSessionExpired(expired);
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(401, { code: "SESSION_EXPIRED", detail: "Your session has expired." }));

    await expect(api.get("/projects")).rejects.toMatchObject({ status: 401 });
    expect(expired).toHaveBeenCalledTimes(1);
    expect(getAccessToken()).toBeNull();
  });

  it("parses Problem Details into ApiError with field errors", async () => {
    setAccessToken("valid");
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(400, {
      code: "VALIDATION_ERROR",
      detail: "One or more fields are invalid.",
      correlationId: "abc-123",
      errors: [{ field: "amount", message: "must be greater than zero" }],
    }));

    const error = await api.post("/projects/1/expenses", {}).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe("VALIDATION_ERROR");
    expect(error.fieldErrors).toEqual([{ field: "amount", message: "must be greater than zero" }]);
  });

  it("adds the correlation id only to server errors", () => {
    expect(errorMessage(new ApiError(500, { detail: "Something went wrong.", correlationId: "12345678-aaaa" }))).toBe("Something went wrong. (reference 12345678)");
    expect(errorMessage(new ApiError(409, { detail: "Stale", correlationId: "x" }))).toBe("Stale");
  });

  it("turns network failures into a readable ApiError", async () => {
    setAccessToken("valid");
    vi.spyOn(globalThis, "fetch").mockRejectedValue(new TypeError("Failed to fetch"));
    const error = await api.get("/projects").catch((e) => e);
    expect(error.code).toBe("NETWORK_ERROR");
    expect(error.message).toMatch(/Cannot reach the server/);
  });

  it("deduplicates refreshSession calls", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(200, { accessToken: "t", me: {} }));
    await Promise.all([refreshSession(), refreshSession()]);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
