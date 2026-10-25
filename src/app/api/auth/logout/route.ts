import { NextRequest, NextResponse } from "next/server";
import { revokeSessionToken, AUTH_COOKIE_NAME } from "@/lib/serverAuth";
import { getSupabaseServiceClient, isSupabaseServerConfigured } from "@/lib/supabase/server";

export async function POST(req: NextRequest) {
  let tokenToRevoke: string | null = null;

  // 1. Check Bearer Authorization header
  const authHeader = req.headers.get("authorization");
  if (authHeader && authHeader.startsWith("Bearer ")) {
    tokenToRevoke = authHeader.substring(7).trim();
  }

  // 2. Check JSON request body
  if (!tokenToRevoke) {
    try {
      const body = await req.json().catch(() => ({}));
      if (body && typeof body.token === "string" && body.token.trim()) {
        tokenToRevoke = body.token.trim();
      }
    } catch {
      // Body not present or unparseable
    }
  }

  // 3. Check auth cookie
  if (!tokenToRevoke) {
    const cookie = req.cookies.get(AUTH_COOKIE_NAME);
    if (cookie && cookie.value) {
      tokenToRevoke = cookie.value;
    }
  }

  let revokeResult: { success: boolean; durable: boolean; error?: string } = {
    success: false,
    durable: false,
  };

  if (tokenToRevoke) {
    const dbClient = getSupabaseServiceClient();
    revokeResult = await revokeSessionToken(tokenToRevoke, { dbClient });
    if (!revokeResult.success && isSupabaseServerConfigured) {
      return NextResponse.json(
        {
          success: false,
          error: "REVOCATION_PERSISTENCE_FAILED",
          message: "Failed to persist session revocation: " + (revokeResult.error || "Database unavailable"),
        },
        { status: 503 }
      );
    }
  }

  const response = NextResponse.json({
    success: true,
    revoked: revokeResult.success,
    durable: revokeResult.durable,
    message: revokeResult.success
      ? (revokeResult.durable
          ? "Session token successfully revoked durably in PostgreSQL."
          : "Session token successfully revoked on server.")
      : "Logged out successfully.",
  });

  response.cookies.set(AUTH_COOKIE_NAME, "", {
    httpOnly: false,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    maxAge: 0,
    path: "/",
  });

  return response;
}
