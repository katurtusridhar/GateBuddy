import { NextRequest, NextResponse } from "next/server";
import { verifySessionToken, AUTH_COOKIE_NAME, serverCredentials } from "@/lib/serverAuth";

export async function GET(req: NextRequest) {
  const authCookie = req.cookies.get(AUTH_COOKIE_NAME);
  const sessionCheck = await verifySessionToken(authCookie?.value);

  if (!sessionCheck.valid || !sessionCheck.username) {
    return NextResponse.json(
      { success: false, authenticated: false, user: null },
      { status: 401 }
    );
  }

  const username = sessionCheck.username;
  return NextResponse.json({
    success: true,
    authenticated: true,
    user: {
      id: `usr_${username}`,
      username: username,
      email: `${username}@gatebuddy.com`,
      name: username === serverCredentials.username ? "Sridhar" : username,
    },
  });
}
