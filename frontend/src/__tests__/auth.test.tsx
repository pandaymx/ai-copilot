import { GlobalRegistrator } from "@happy-dom/global-registrator";

if (typeof document === "undefined") {
  GlobalRegistrator.register();
}
(
  globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

import { afterEach, beforeEach, describe, expect, it, mock } from "bun:test";
import {
  clearAuthSession,
  getStoredToken,
  getStoredUser,
  login,
  register,
  saveAuthSession,
  type TokenPair,
} from "../lib/auth-api";

let mockFetch: ReturnType<typeof mock>;
const originalFetch = globalThis.fetch;

const mockTokenPair: TokenPair = {
  accessToken: "mock.jwt.token",
  refreshToken: "mock.refresh.token",
  expiresIn: 900,
  user: {
    id: "u-123",
    username: "testuser",
    role: "ADMIN",
    permissions: ["chat:create", "admin:manage_users"],
    createdAt: 1000,
  },
};

beforeEach(() => {
  localStorage.clear();
  mockFetch = mock().mockResolvedValue(
    new Response(JSON.stringify(mockTokenPair), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    }),
  );
  globalThis.fetch = mockFetch as unknown as typeof fetch;
});

afterEach(() => {
  globalThis.fetch = originalFetch;
});

describe("Auth API and Local Storage Management", () => {
  it("should save and retrieve session in localStorage", () => {
    saveAuthSession(mockTokenPair);

    expect(getStoredToken()).toBe("mock.jwt.token");
    expect(getStoredUser()).toEqual(mockTokenPair.user);

    clearAuthSession();
    expect(getStoredToken()).toBeNull();
    expect(getStoredUser()).toBeNull();
  });

  it("should login successfully and persist token", async () => {
    const pair = await login("testuser", "password123");
    expect(pair.accessToken).toBe("mock.jwt.token");
    expect(getStoredToken()).toBe("mock.jwt.token");
    expect(getStoredUser()?.username).toBe("testuser");
  });

  it("should register successfully and persist token", async () => {
    const pair = await register("newuser", "password123", "USER");
    expect(pair.accessToken).toBe("mock.jwt.token");
    expect(getStoredToken()).toBe("mock.jwt.token");
  });
});
