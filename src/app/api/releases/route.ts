import { NextRequest, NextResponse } from "next/server";
import { resolveReleaseCatalog } from "@/lib/releases";

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const channel = searchParams.get("channel");
  const pkg = searchParams.get("package");
  const ifNoneMatch = req.headers.get("if-none-match");

  const result = resolveReleaseCatalog({
    channel,
    package: pkg,
    ifNoneMatch,
  });

  if (result.status === 304) {
    return new NextResponse(null, {
      status: 304,
      headers: result.headers,
    });
  }

  return NextResponse.json(result.body, {
    status: result.status,
    headers: result.headers,
  });
}
