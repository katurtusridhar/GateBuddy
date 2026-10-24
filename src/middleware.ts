import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const authCookie = request.cookies.get("gatebuddy_auth");
  const isAuthenticated = authCookie?.value === "authenticated";

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
