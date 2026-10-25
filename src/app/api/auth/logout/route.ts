import { NextRequest, NextResponse } from "next/server";
import { revokeSessionToken, AUTH_COOKIE_NAME } from "@/lib/serverAuth";

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

  let revoked = false;
  if (tokenToRevoke) {
    revoked = revokeSessionToken(tokenToRevoke);
  }

  const response = NextResponse.json({
    success: true,
    revoked,
    message: revoked ? "Session token successfully revoked on server." : "Logged out successfully.",
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
