import { NextRequest, NextResponse } from "next/server";
import { checkUserLogin, signSessionToken, AUTH_COOKIE_NAME } from "@/lib/serverAuth";

export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    const username = (body.username || "").trim();
    const password = (body.password || "").trim();

    const authResult = checkUserLogin(username, password);

    if (!authResult.success || !authResult.user) {
      return NextResponse.json(
        { success: false, error: "Invalid username or password. Please try again." },
        { status: 401 }
      );
    }

    const sessionToken = await signSessionToken(authResult.user.username);

    const response = NextResponse.json({
      success: true,
      user: authResult.user,
    });

    // Set cryptographically signed session cookie valid for 30 days
    response.cookies.set(AUTH_COOKIE_NAME, sessionToken, {
      httpOnly: false,
      secure: process.env.NODE_ENV === "production",
      sameSite: "lax",
      maxAge: 60 * 60 * 24 * 30, // 30 days
      path: "/",
    });

    return response;
  } catch {
    return NextResponse.json(
      { success: false, error: "Invalid request" },
      { status: 400 }
    );
  }
}
