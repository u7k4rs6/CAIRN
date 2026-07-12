import { NextRequest, NextResponse } from "next/server";

export async function GET(request: NextRequest) {
  const path = request.nextUrl.searchParams.get("path")?.trim() || "";
  const cleaned = path.replace(/^\/+/, "");
  return NextResponse.redirect(new URL(`/${cleaned}`, request.url));
}
