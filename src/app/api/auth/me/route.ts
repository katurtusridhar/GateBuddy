import { NextRequest, NextResponse } from "next/server";
import { getAuthorizedUser, serverCredentials } from "@/lib/serverAuth";

export async function GET(req: NextRequest) {
  const auth = await getAuthorizedUser(req);

  if (!auth.authorized || !auth.username) {
    return NextResponse.json(
      { success: false, authenticated: false, user: null },
      { status: 401 }
    );
  }

  const username = auth.username;
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
