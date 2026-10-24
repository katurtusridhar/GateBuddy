import { NextRequest, NextResponse } from "next/server";
import { verifyMasterCode, serverCredentials } from "@/lib/serverAuth";

export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    const { masterCode } = body;

    if (!masterCode || typeof masterCode !== "string") {
      return NextResponse.json(
        { success: false, error: "Master security code is required." },
        { status: 400 }
      );
    }

    const isValid = verifyMasterCode(masterCode.trim());

    if (!isValid) {
      return NextResponse.json(
        { success: false, error: "Invalid Master Authorization Code. Access denied." },
        { status: 401 }
      );
    }

    return NextResponse.json({
      success: true,
      username: serverCredentials.username,
    });
  } catch (error: any) {
    return NextResponse.json(
      { success: false, error: error?.message || "Verification failed" },
      { status: 500 }
    );
  }
}
