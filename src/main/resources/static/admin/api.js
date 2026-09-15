/* Shared API helper for the admin panel: auth storage, JWT decoding, fetch wrapper. */

const Api = (() => {
  const ACCESS_KEY = "wingmark_admin_access_token";
  const REFRESH_KEY = "wingmark_admin_refresh_token";

  function getAccessToken() {
    return localStorage.getItem(ACCESS_KEY);
  }

  function getRefreshToken() {
    return localStorage.getItem(REFRESH_KEY);
  }

  function setTokens(accessToken, refreshToken) {
    localStorage.setItem(ACCESS_KEY, accessToken);
    localStorage.setItem(REFRESH_KEY, refreshToken);
  }

  function clearTokens() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  }

  function decodeToken(token) {
    try {
      const payload = token.split(".")[1];
      const json = decodeURIComponent(
        atob(payload.replace(/-/g, "+").replace(/_/g, "/"))
          .split("")
          .map((c) => "%" + c.charCodeAt(0).toString(16).padStart(2, "0"))
          .join("")
      );
      return JSON.parse(json);
    } catch (e) {
      return null;
    }
  }

  function currentClaims() {
    const token = getAccessToken();
    if (!token) return null;
    const claims = decodeToken(token);
    if (!claims) return null;
    if (claims.exp && Date.now() >= claims.exp * 1000) return null;
    return claims;
  }

  function isAdminLoggedIn() {
    const claims = currentClaims();
    return !!claims && claims.role === "ADMIN";
  }

  function requireAdminOrRedirect() {
    if (!isAdminLoggedIn()) {
      clearTokens();
      window.location.href = "login.html";
    }
  }

  async function request(path, options = {}) {
    const token = getAccessToken();
    const headers = Object.assign({}, options.headers || {});
    if (token) headers["Authorization"] = "Bearer " + token;
    if (options.body && !(options.body instanceof FormData) && !headers["Content-Type"]) {
      headers["Content-Type"] = "application/json";
    }

    const res = await fetch(path, Object.assign({}, options, { headers }));

    if (res.status === 401) {
      clearTokens();
      window.location.href = "login.html";
      throw new Error("Session expired");
    }

    if (!res.ok) {
      let message = "Request failed (" + res.status + ")";
      try {
        const body = await res.json();
        if (body && body.message) message = body.message;
      } catch (e) {
        /* no JSON body */
      }
      throw new Error(message);
    }

    if (res.status === 204) return null;
    const contentType = res.headers.get("content-type") || "";
    if (contentType.includes("application/json")) return res.json();
    return null;
  }

  async function login(email, password) {
    const res = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
    if (!res.ok) {
      let message = "Login failed";
      try {
        const body = await res.json();
        if (body && body.message) message = body.message;
      } catch (e) {
        /* ignore */
      }
      throw new Error(message);
    }
    const data = await res.json();
    setTokens(data.accessToken, data.refreshToken);
    const claims = decodeToken(data.accessToken);
    if (!claims || claims.role !== "ADMIN") {
      clearTokens();
      throw new Error("This account does not have admin access.");
    }
    return claims;
  }

  function logout() {
    const refreshToken = getRefreshToken();
    clearTokens();
    if (refreshToken) {
      fetch("/api/auth/logout", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken }),
      }).catch(() => {});
    }
    window.location.href = "login.html";
  }

  return {
    request,
    login,
    logout,
    isAdminLoggedIn,
    requireAdminOrRedirect,
    currentClaims,
  };
})();
