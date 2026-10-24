import { NextRequest, NextResponse } from "next/server";

export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    const username = (body.username || "").trim().toLowerCase();
    const password = (body.password || "").trim();

    const validUsernames = ["katurtusridhar", "sridhar", "katurtusridhar@gatebuddy.com"];
    const isUserValid = validUsernames.includes(username);
    const isPassValid = password === "Sridhar@758" || password.toLowerCase() === "sridhar@758";

    if (!isUserValid || !isPassValid) {
      return NextResponse.json(
        { success: false, error: "Invalid username or password. Please try again." },
        { status: 401 }
      );
    }

    const response = NextResponse.json({
      success: true,
      user: {
        id: "usr_katurtusridhar",
        email: "katurtusridhar@gatebuddy.com",
        name: "Sridhar",
        username: "katurtusridhar",
      },
    });

    // Set secure auth cookie valid for 30 days
    response.cookies.set("gatebuddy_auth", "authenticated", {
      httpOnly: false, // Accessible to client and middleware
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
