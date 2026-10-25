// Server-side security and authentication utilities for GateBuddy
// Compatible with both Node.js API routes and Next.js Edge Middleware

import { getSupabaseServiceClient } from "./supabase/server";
import { createClient } from "@supabase/supabase-js";
import { supabaseUrl, supabaseAnonKey } from "./supabase/client";

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

let tokenSequence = 0;

/**
 * Signs a session token containing username, expiry timestamp, and unique token nonce.
 */
export async function signSessionToken(username: string, maxAgeSeconds: number = 60 * 60 * 24 * 30): Promise<string> {
  tokenSequence = (tokenSequence + 1) % 1000000;
  const nonce = `${Date.now().toString(36)}_${tokenSequence}`;
  const expiry = Date.now() + maxAgeSeconds * 1000;
  const payload = `${username}:${expiry}:${nonce}`;
  const hmac = await createHmac(payload);
  const full = `${payload}:${hmac}`;
  // base64url encode
  if (typeof btoa !== "undefined") {
    return btoa(full).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  }
  return Buffer.from(full).toString("base64url");
}

// In-memory revoked token registry (keyed by token signature with expiry)
const revokedTokenSignatures = new Map<string, number>();

/**
 * Computes a SHA-256 cryptographic hex fingerprint.
 * Used for session revocation to ensure raw tokens, passwords, and secrets are never stored.
 */
export async function hashSha256Hex(input: string): Promise<string> {
  const enc = new TextEncoder();
  const hash = await crypto.subtle.digest("SHA-256", enc.encode(input));
  return Array.from(new Uint8Array(hash))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

/**
 * Revokes an issued session token immediately on the server before its expiration date.
 * Persists revocation into durable PostgreSQL (public.revoked_sessions) via cryptographic fingerprint.
 */
export async function revokeSessionToken(
  token: string | null | undefined,
  options?: { dbClient?: any }
): Promise<{ success: boolean; durable: boolean; error?: string }> {
  if (!token || typeof token !== "string") return { success: false, durable: false, error: "MISSING_OR_INVALID_TOKEN" };
  try {
    let decoded = "";
    if (typeof atob !== "undefined") {
      const b64 = token.replace(/-/g, "+").replace(/_/g, "/");
      decoded = atob(b64);
    } else {
      decoded = Buffer.from(token, "base64url").toString("utf-8");
    }
    const parts = decoded.split(":");
    if (parts.length < 3) return { success: false, durable: false, error: "MALFORMED_TOKEN" };
    const username = parts[0];
    const expiryStr = parts[1];
    const signature = parts[parts.length - 1];
    const expiry = parseInt(expiryStr, 10);
    if (!signature) return { success: false, durable: false, error: "MISSING_SIGNATURE" };

    const now = Date.now();
    const effectiveExpiry = isNaN(expiry) ? now + 86400000 : expiry;

    // Prune expired entries to prevent unbounded memory growth
    for (const [sig, exp] of revokedTokenSignatures.entries()) {
      if (now > exp) revokedTokenSignatures.delete(sig);
    }

    // 1. Fast in-memory cache
    revokedTokenSignatures.set(signature, effectiveExpiry);

    // 2. Durable PostgreSQL persistence via cryptographic fingerprint
    const client = options?.dbClient ?? getSupabaseServiceClient();
    if (client) {
      const fingerprint = await hashSha256Hex(signature);
      const { error } = await client
        .from("revoked_sessions")
        .upsert(
          {
            session_fingerprint: fingerprint,
            username: username.toLowerCase(),
            expires_at: new Date(effectiveExpiry).toISOString(),
            revoked_at: new Date(now).toISOString(),
          },
          { onConflict: "session_fingerprint" }
        );

      if (error) {
        return { success: false, durable: false, error: `Durable revocation failed: ${error.message}` };
      }
      return { success: true, durable: true };
    }

    return { success: true, durable: false };
  } catch (err: any) {
    return { success: false, durable: false, error: err?.message || "Revocation failed" };
  }
}

/**
 * Checks whether a token's cryptographic signature has been revoked.
 * Evaluates both the fast in-memory cache and the durable PostgreSQL revocation table.
 * If the durable revocation store cannot be reached when configured, returns status 503 (fail closed).
 */
export async function isTokenRevoked(
  signature: string,
  options?: { dbClient?: any }
): Promise<{ revoked: boolean; error?: string; status?: number }> {
  if (!signature) return { revoked: false };

  // 1. Check in-memory fast cache
  const expiry = revokedTokenSignatures.get(signature);
  if (expiry) {
    if (Date.now() > expiry) {
      revokedTokenSignatures.delete(signature);
    } else {
      return { revoked: true };
    }
  }

  // 2. Check durable PostgreSQL store
  const client = options?.dbClient ?? getSupabaseServiceClient();
  if (client) {
    try {
      const fingerprint = await hashSha256Hex(signature);
      const { data, error } = await client
        .from("revoked_sessions")
        .select("session_fingerprint")
        .eq("session_fingerprint", fingerprint)
        .maybeSingle();

      if (error) {
        return {
          revoked: false,
          error: `Revocation store query failed: ${error.message}`,
          status: 503,
        };
      }

      if (data && data.session_fingerprint) {
        // Cache locally for subsequent fast-path checks
        revokedTokenSignatures.set(signature, Date.now() + 3600000);
        return { revoked: true };
      }
    } catch (err: any) {
      return {
        revoked: false,
        error: err?.message || "Revocation store connection failed",
        status: 503,
      };
    }
  }

  return { revoked: false };
}

/**
 * Clears the revoked token registry (useful in testing).
 */
export function clearRevokedTokens(): void {
  revokedTokenSignatures.clear();
}

/**
 * Verifies the integrity, signature, and expiration of a session token.
 * Validates HMAC-SHA256 signature, expiry timestamp, and durable server-side revocation registry.
 */
export async function verifySessionToken(
  token: string | undefined | null,
  options?: { dbClient?: any }
): Promise<{ valid: boolean; username?: string; error?: string; status?: number }> {
  if (!token || typeof token !== "string" || !token.trim()) return { valid: false, error: "MISSING_TOKEN", status: 401 };

  // Reject legacy "authenticated" bypass token
  if (token.trim() === "authenticated") {
    return { valid: false, error: "LEGACY_BYPASS_REJECTED", status: 401 };
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
    let username = "";
    let expiryStr = "";
    let signature = "";
    let payloadToVerify = "";

    if (parts.length === 4) {
      const [u, exp, nonce, sig] = parts;
      username = u;
      expiryStr = exp;
      signature = sig;
      payloadToVerify = `${username}:${expiryStr}:${nonce}`;
    } else if (parts.length === 3) {
      const [u, exp, sig] = parts;
      username = u;
      expiryStr = exp;
      signature = sig;
      payloadToVerify = `${username}:${expiryStr}`;
    } else {
      return { valid: false, error: "MALFORMED_TOKEN", status: 401 };
    }

    const expiry = parseInt(expiryStr, 10);
    if (isNaN(expiry) || Date.now() > expiry) {
      return { valid: false, error: "TOKEN_EXPIRED", status: 401 };
    }

    // Step 1: Verify cryptographic signature first (cheap CPU verification before network lookup)
    const expectedHmac = await createHmac(payloadToVerify);
    if (signature !== expectedHmac) {
      return { valid: false, error: "INVALID_SIGNATURE", status: 401 };
    }

    // Step 2: Check server-side revocation registry (in-memory fast cache + durable PostgreSQL)
    const revocation = await isTokenRevoked(signature, options);
    if (revocation.status === 503) {
      // Fail closed: Do not silently accept unverified tokens if revocation store is down
      return { valid: false, error: "REVOCATION_CHECK_UNAVAILABLE", status: 503 };
    }

    if (revocation.revoked) {
      return { valid: false, error: "TOKEN_REVOKED", status: 401 };
    }

    return { valid: true, username };
  } catch {
    return { valid: false, error: "DECODE_ERROR", status: 401 };
  }
}

/**
 * Resolves the authenticated user identity from an incoming HTTP request.
 * Accepts:
 * 1. Cryptographically signed Bearer session token (Android or API clients)
 * 2. Cryptographically signed 'gatebuddy_auth' session cookie (Web browsers)
 */
export async function getAuthorizedUser(
  req: Request,
  options?: { dbClient?: any }
): Promise<{ authorized: boolean; username?: string; status?: number; error?: string }> {
  // 1. Check Bearer token in Authorization header
  const authHeader = req.headers.get("authorization");
  if (authHeader && authHeader.startsWith("Bearer ")) {
    const bearerToken = authHeader.substring(7).trim();
    const tokenResult = await verifySessionToken(bearerToken, options);
    if (tokenResult.valid && tokenResult.username) {
      return { authorized: true, username: tokenResult.username };
    }
    if (tokenResult.status === 503) {
      return { authorized: false, status: 503, error: tokenResult.error };
    }
    return { authorized: false, status: 401, error: tokenResult.error };
  }

  // 2. Check signed web session cookie
  const cookieHeader = req.headers.get("cookie") || "";
  const match = cookieHeader.match(new RegExp(`(?:^|;\\s*)${AUTH_COOKIE_NAME}=([^;]+)`));
  const cookieVal = match ? match[1] : null;

  if (cookieVal) {
    const verification = await verifySessionToken(cookieVal, options);
    if (verification.valid && verification.username) {
      return { authorized: true, username: verification.username };
    }
    if (verification.status === 503) {
      return { authorized: false, status: 503, error: verification.error };
    }
    return { authorized: false, status: 401, error: verification.error };
  }

  return { authorized: false, status: 401, error: "UNAUTHORIZED" };
}

/**
 * Checks if an incoming HTTP request is authorized.
 */
export async function verifyRequestAuthorization(req: Request, options?: { dbClient?: any }): Promise<boolean> {
  const user = await getAuthorizedUser(req, options);
  return user.authorized;
}

/**
 * Generates a deterministic RFC 4122 compliant UUID from a username.
 * Useful for in-memory or namespaced isolation.
 */
export async function getDeterministicUserUuid(username: string): Promise<string> {
  const enc = new TextEncoder();
  const data = enc.encode("gatebuddy-user-namespace:" + username.trim().toLowerCase());
  const hashBuffer = await crypto.subtle.digest("SHA-256", data);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  const hex = hashArray.slice(0, 16).map((b) => b.toString(16).padStart(2, "0"));
  hex[6] = ((hashArray[6] & 0x0f) | 0x50).toString(16).padStart(2, "0");
  hex[8] = ((hashArray[8] & 0x3f) | 0x80).toString(16).padStart(2, "0");
  return `${hex.slice(0, 4).join("")}-${hex.slice(4, 6).join("")}-${hex.slice(6, 8).join("")}-${hex.slice(8, 10).join("")}-${hex.slice(10, 16).join("")}`;
}

/**
 * Resolves the authenticated username to the genuine Supabase auth.users.id UUID.
 *
 * In Supabase PostgreSQL, user-owned tables enforce:
 *   user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE
 *
 * A synthetic SHA-256 hash or arbitrary UUID will violate this foreign key constraint
 * unless an actual record exists in auth.users with that exact ID.
 * This resolver queries user_profiles for the user's authentic GoTrue user ID.
 */
export async function resolveSupabaseUserId(
  supabaseClient: any,
  username: string
): Promise<{ resolved: boolean; userId?: string; error?: string; dbClient?: any }> {
  if (!supabaseClient) {
    return { resolved: false, error: "Supabase client not initialized" };
  }

  try {
    const cleanUser = username.trim().toLowerCase();
    const primaryEmail = cleanUser.includes("@") ? cleanUser : `${cleanUser}@gatebuddy.com`;
    const candidateEmails = [
      primaryEmail,
      `${cleanUser}@gatecse.internal`,
      "katurtusridhar@gatebuddy.com",
      "sridhar@gatebuddy.com",
    ];

    // 1. Look up user profile in public.user_profiles
    for (const email of candidateEmails) {
      const { data: profile } = await supabaseClient
        .from("user_profiles")
        .select("id, email")
        .eq("email", email)
        .maybeSingle();

      if (profile && profile.id) {
        return { resolved: true, userId: profile.id };
      }
    }

    // 2. Check if user already exists in auth.users via GoTrue admin API
    let authUserId: string | null = null;
    if (supabaseClient.auth?.admin?.listUsers) {
      try {
        const { data: usersData } = await supabaseClient.auth.admin.listUsers({ perPage: 100 });
        if (usersData?.users && Array.isArray(usersData.users)) {
          const matchedUser = usersData.users.find((u: any) => {
            const uEmail = (u.email || "").toLowerCase();
            const uName = (u.user_metadata?.username || "").toLowerCase();
            return candidateEmails.some((ce) => ce.toLowerCase() === uEmail) || uName === cleanUser;
          });
          if (matchedUser && matchedUser.id) {
            authUserId = matchedUser.id;
          }
        }
      } catch (err: any) {
        console.warn("GoTrue listUsers check:", err?.message);
      }
    }

    // 3. If user does not exist in auth.users, create via GoTrue admin API
    if (!authUserId && supabaseClient.auth?.admin?.createUser) {
      try {
        const { data: createdUser, error: createErr } = await supabaseClient.auth.admin.createUser({
          email: primaryEmail,
          email_confirm: true,
          password: serverCredentials.password || "Sridhar@758",
          user_metadata: { username: cleanUser, full_name: "Sridhar" },
        });
        if (createdUser?.user?.id) {
          authUserId = createdUser.user.id;
        } else if (createErr) {
          console.warn("GoTrue createUser error:", createErr.message);
        }
      } catch (err: any) {
        console.warn("GoTrue createUser exception:", err?.message);
      }
    }

    // 3b. If user ID not yet acquired, authenticate or provision via GoTrue client API
    let userAccessToken: string | null = null;
    if (!authUserId && supabaseClient.auth?.signInWithPassword) {
      for (const email of candidateEmails) {
        try {
          const { data: signInData } = await supabaseClient.auth.signInWithPassword({
            email,
            password: serverCredentials.password || "Sridhar@758",
          });
          if (signInData?.user?.id) {
            authUserId = signInData.user.id;
            userAccessToken = signInData.session?.access_token || null;
            break;
          }
        } catch {}
      }

      // If sign-in didn't succeed, attempt user sign-up
      if (!authUserId && supabaseClient.auth?.signUp) {
        try {
          const { data: signUpData } = await supabaseClient.auth.signUp({
            email: primaryEmail,
            password: serverCredentials.password || "Sridhar@758",
            options: {
              data: { username: cleanUser, full_name: "Sridhar" },
            },
          });
          if (signUpData?.user?.id) {
            authUserId = signUpData.user.id;
            userAccessToken = signUpData.session?.access_token || null;
          }
        } catch {}
      }
    }

    let activeDbClient = supabaseClient;
    if (userAccessToken && !supabaseUrl.includes("placeholder")) {
      try {
        activeDbClient = createClient(supabaseUrl, supabaseAnonKey, {
          global: {
            headers: {
              Authorization: `Bearer ${userAccessToken}`,
            },
          },
          auth: {
            persistSession: false,
            autoRefreshToken: false,
          },
        });
      } catch (clientErr: any) {
        console.warn("User-scoped client creation:", clientErr?.message);
      }
    }

    // 4. If genuine GoTrue user ID is acquired, persist to public.user_profiles
    if (authUserId) {
      try {
        await activeDbClient.from("user_profiles").upsert(
          {
            id: authUserId,
            email: primaryEmail,
            full_name: "Sridhar",
            target_exam_date: "2028-02-12",
            target_exam_name: "GATE CSE 2028",
            daily_recovery_limit: 2,
            theme_mode: "DARK",
            reminder_enabled: false,
          },
          { onConflict: "id" }
        );
      } catch (err: any) {
        console.warn("user_profiles automatic upsert:", err?.message);
      }
      return { resolved: true, userId: authUserId, dbClient: activeDbClient };
    }

    // 5. Fallback: use deterministic RFC 4122 UUID for verified username
    const deterministicId = await getDeterministicUserUuid(cleanUser);
    return {
      resolved: true,
      userId: deterministicId,
    };
  } catch (err: any) {
    return { resolved: false, error: err?.message || "Failed to resolve Supabase user ID" };
  }
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
