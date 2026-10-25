import { NextRequest, NextResponse } from "next/server";
import {
  signSessionToken,
  verifySessionToken,
  checkUserLogin,
  verifyMasterCode,
  serverCredentials,
  CLIENT_API_KEY,
  timingSafeEqual,
} from "@/lib/serverAuth";

export async function POST(req: NextRequest) {
  try {
    const body = await req.json().catch(() => ({}));
    const username = (body.username || "").trim();
    const password = (body.password || "").trim();
    const masterCode = (body.masterCode || "").trim();
    const grantType = (body.grantType || "").trim();
    const clientKey = (body.clientKey || "").trim();

    let authenticatedUsername: string | null = null;

    // 1. Authenticate via username and password
    if (username && password) {
      const loginRes = checkUserLogin(username, password);
      if (loginRes.success && loginRes.user) {
        authenticatedUsername = loginRes.user.username;
      }
    }

    // 2. Authenticate via master authorization code
    if (!authenticatedUsername && masterCode && verifyMasterCode(masterCode)) {
      authenticatedUsername = username || serverCredentials.username;
    }

    // 3. Authenticate via companion client grant (requires verified client key)
    if (!authenticatedUsername && grantType === "companion_client") {
      if (clientKey && timingSafeEqual(clientKey, CLIENT_API_KEY)) {
        authenticatedUsername = username || serverCredentials.username;
      }
    }

    // 4. Authenticate via existing valid session token (token refresh)
    if (!authenticatedUsername) {
      const authHeader = req.headers.get("authorization");
      if (authHeader && authHeader.startsWith("Bearer ")) {
        const tokenRes = await verifySessionToken(authHeader.substring(7).trim());
        if (tokenRes.valid && tokenRes.username) {
          authenticatedUsername = tokenRes.username;
        }
      }
    }

    if (!authenticatedUsername) {
      return NextResponse.json(
        { success: false, error: "Invalid credentials. Authentication required to issue session token." },
        { status: 401 }
      );
    }

    // Sign user-scoped cryptographic session token (valid 90 days)
    const token = await signSessionToken(authenticatedUsername, 60 * 60 * 24 * 90);

    return NextResponse.json({
      success: true,
      token,
      username: authenticatedUsername,
      expiresIn: 60 * 60 * 24 * 90,
      tokenType: "Bearer",
    });
  } catch (error: any) {
    return NextResponse.json(
      { success: false, error: error?.message || "Token generation failed" },
      { status: 500 }
    );
  }
}
