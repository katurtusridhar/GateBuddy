import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { verifySessionToken, AUTH_COOKIE_NAME } from "./lib/serverAuth";

export async function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const authCookie = request.cookies.get(AUTH_COOKIE_NAME);
  const sessionCheck = await verifySessionToken(authCookie?.value);
  const isAuthenticated = sessionCheck.valid;

  // Strict list of protected application paths
  const protectedPrefixes = [
    "/dashboard",
    "/calendar",
    "/recovery",
    "/subjects",
    "/analytics",
    "/tests",
    "/settings",
    "/achievements",
  ];

  const isProtectedPath = protectedPrefixes.some((prefix) =>
    pathname.startsWith(prefix)
  );

  // If unauthenticated and attempting to access any protected area, redirect to /login
  if (isProtectedPath && !isAuthenticated) {
    const loginUrl = new URL("/login", request.url);
    loginUrl.searchParams.set("from", pathname);
    return NextResponse.redirect(loginUrl);
  }

  // If authenticated and visiting /login, redirect to /dashboard
  if (pathname === "/login" && isAuthenticated) {
    return NextResponse.redirect(new URL("/dashboard", request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    "/dashboard/:path*",
    "/calendar/:path*",
    "/recovery/:path*",
    "/subjects/:path*",
    "/analytics/:path*",
    "/tests/:path*",
    "/settings/:path*",
    "/achievements/:path*",
    "/login",
  ],
};
