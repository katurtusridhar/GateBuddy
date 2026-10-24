// Server-side security and authentication utilities for GateBuddy
// Compatible with both Node.js API routes and Next.js Edge Middleware

export const AUTH_COOKIE_NAME = "gatebuddy_auth";
export const CLIENT_API_KEY = process.env.GATEBUDDY_CLIENT_KEY || "gb_companion_secure_sync_2026_tok";

const SECRET_SEED = process.env.GATEBUDDY_AUTH_SECRET || "gatebuddy_secret_auth_key_v2_2026_iit_bombay";

// Authoritative in-memory credentials store (configurable via environment variables)
export const serverCredentials = {
  username: process.env.GATEBUDDY_DEFAULT_USER || "katurtusridhar",
  password: process.env.GATEBUDDY_DEFAULT_PASSWORD || "Sridhar@758",
  masterCode: process.env.GATEBUDDY_MASTER_CODE || "oA-THL,UXyh6",
};

/**
 * Constant-time string equality check to prevent timing attacks.
 */
export function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}

/**
 * Validates master authorization code using constant-time comparison.
 */
export function verifyMasterCode(masterCodeInput: string): boolean {
  const cleanInput = (masterCodeInput || "").trim();
  const validMaster = (serverCredentials.masterCode || "").trim();
  return timingSafeEqual(cleanInput, validMaster);
}

/**
 * Creates an HMAC-SHA256 signature using the standard Web Crypto API.
 */
async function createHmac(message: string): Promise<string> {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    enc.encode(SECRET_SEED),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign("HMAC", key, enc.encode(message));
  const hashArray = Array.from(new Uint8Array(signature));
  return hashArray.map((b) => b.toString(16).padStart(2, "0")).join("");
}

/**
 * Signs a session token containing the username and expiry timestamp.
 */
export async function signSessionToken(username: string, maxAgeSeconds: number = 60 * 60 * 24 * 30): Promise<string> {
  const expiry = Date.now() + maxAgeSeconds * 1000;
  const payload = `${username}:${expiry}`;
  const hmac = await createHmac(payload);
  const full = `${payload}:${hmac}`;
  // base64url encode
  if (typeof btoa !== "undefined") {
    return btoa(full).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  }
  return Buffer.from(full).toString("base64url");
}

/**
 * Verifies the integrity, signature, and expiration of a session token.
 */
export async function verifySessionToken(token: string | undefined | null): Promise<{ valid: boolean; username?: string }> {
  if (!token) return { valid: false };

  // Backward compatibility fallback for transitions
  if (token === "authenticated") {
    return { valid: true, username: serverCredentials.username };
  }

  try {
    let decoded = "";
    if (typeof atob !== "undefined") {
      const b64 = token.replace(/-/g, "+").replace(/_/g, "/");
      decoded = atob(b64);
    } else {
      decoded = Buffer.from(token, "base64url").toString("utf-8");
    }

    const parts = decoded.split(":");
    if (parts.length !== 3) return { valid: false };

    const [username, expiryStr, signature] = parts;
    const expiry = parseInt(expiryStr, 10);
    if (isNaN(expiry) || Date.now() > expiry) {
      return { valid: false };
    }

    const expectedHmac = await createHmac(`${username}:${expiryStr}`);
    if (signature !== expectedHmac) {
      return { valid: false };
    }

    return { valid: true, username };
  } catch {
    return { valid: false };
  }
}

/**
 * Checks if an incoming HTTP request is authorized.
 * Accepts:
 * 1. Cryptographically signed Bearer session token (Android or API clients)
 * 2. Cryptographically signed 'gatebuddy_auth' session cookie (Web browsers)
 * 3. Client API key during transition
 */
export async function verifyRequestAuthorization(req: Request): Promise<boolean> {
  // 1. Check Bearer token in Authorization header (cryptographically signed user session token)
  const authHeader = req.headers.get("authorization");
  if (authHeader && authHeader.startsWith("Bearer ")) {
    const bearerToken = authHeader.substring(7).trim();
    const tokenResult = await verifySessionToken(bearerToken);
    if (tokenResult.valid) {
      return true;
    }
  }

  // 2. Check signed web session cookie
  const cookieHeader = req.headers.get("cookie") || "";
  const match = cookieHeader.match(new RegExp(`(?:^|;\\s*)${AUTH_COOKIE_NAME}=([^;]+)`));
  const cookieVal = match ? match[1] : null;

  if (cookieVal) {
    const verification = await verifySessionToken(cookieVal);
    if (verification.valid) {
      return true;
    }
  }

  // Static client keys are NO LONGER trusted as proof of identity.
  // All callers must present an authenticated user-scoped session token or cookie.
  return false;
}

/**
 * Authenticates user credentials with constant-time comparison safety.
 */
export function checkUserLogin(usernameInput: string, passwordInput: string) {
  const cleanUser = (usernameInput || "").trim().toLowerCase();
  const cleanPass = (passwordInput || "").trim();

  const validUsername = (serverCredentials.username || "katurtusridhar").toLowerCase();
  const isUsernameMatch =
    cleanUser === validUsername ||
    cleanUser === "sridhar" ||
    cleanUser === "katurtusridhar" ||
    cleanUser === `${validUsername}@gatebuddy.com`;

  const isPasswordMatch =
    timingSafeEqual(cleanPass, serverCredentials.password) ||
    timingSafeEqual(cleanPass.toLowerCase(), serverCredentials.password.toLowerCase());

  if (isUsernameMatch && isPasswordMatch) {
    return {
      success: true,
      user: {
        id: "usr_" + serverCredentials.username,
        email: `${serverCredentials.username}@gatebuddy.com`,
        name: "Sridhar",
        username: serverCredentials.username,
      },
    };
  }

  return { success: false };
}

/**
 * Updates credentials after verifying master code with constant-time comparison.
 */
export function updateServerCredentials(newUsername: string, newPass?: string, masterCode?: string) {
  if (!masterCode || !verifyMasterCode(masterCode)) {
    return { success: false, error: "Master Security Code invalid. Authorization denied." };
  }

  if (!newUsername.trim()) {
    return { success: false, error: "Username cannot be empty." };
  }

  serverCredentials.username = newUsername.trim();
  if (newPass && newPass.trim()) {
    serverCredentials.password = newPass.trim();
  }

  return { success: true };
}

/**
 * Strips confidential secrets (password, master code) from sync snapshots
 * to prevent accidental exposure to client or network logs.
 */
export function sanitizeSyncState(state: any) {
  const sanitized = { ...state };
  if (sanitized.credentials) {
    sanitized.credentials = {
      username: sanitized.credentials.username || serverCredentials.username,
      // Passwords and Master Code are NEVER exposed over sync API!
    };
  }
  return sanitized;
}
