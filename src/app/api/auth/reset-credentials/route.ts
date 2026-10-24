import { NextRequest, NextResponse } from "next/server";
import { updateServerCredentials, signSessionToken, AUTH_COOKIE_NAME } from "@/lib/serverAuth";

export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    const { newUsername, newPassword, masterCode } = body;

    if (!masterCode || !newUsername) {
      return NextResponse.json(
        { success: false, error: "Missing required fields." },
        { status: 400 }
      );
    }

    const updateRes = updateServerCredentials(newUsername, newPassword, masterCode);
    if (!updateRes.success) {
      return NextResponse.json(
        { success: false, error: updateRes.error || "Authorization failed." },
        { status: 401 }
      );
    }

    // Issue new signed session token
    const sessionToken = await signSessionToken(newUsername.trim());

    const response = NextResponse.json({
      success: true,
      username: newUsername.trim(),
      token: sessionToken,
      message: "Credentials successfully updated.",
    });

    response.cookies.set(AUTH_COOKIE_NAME, sessionToken, {
      httpOnly: false,
      secure: process.env.NODE_ENV === "production",
      sameSite: "lax",
      maxAge: 60 * 60 * 24 * 30,
      path: "/",
    });

    return response;
  } catch (error: any) {
    return NextResponse.json(
      { success: false, error: error?.message || "Reset failed." },
      { status: 500 }
    );
  }
}
