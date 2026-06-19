import { NextRequest, NextResponse } from "next/server";
import { getBackendApiBaseUrl } from "@/lib/backend-proxy";

export async function GET(request: NextRequest) {
  const from = request.nextUrl.searchParams.get("from");
  const to = request.nextUrl.searchParams.get("to");
  const upstreamUrl = new URL("/api/timeline", getBackendApiBaseUrl());

  if (from) {
    upstreamUrl.searchParams.set("from", from);
  }
  if (to) {
    upstreamUrl.searchParams.set("to", to);
  }

  const upstreamResponse = await fetch(upstreamUrl, {
    headers: {
      Accept: "application/json",
    },
    cache: "no-store",
  });
  const body = await upstreamResponse.text();

  return new NextResponse(body, {
    status: upstreamResponse.status,
    headers: {
      "Content-Type":
        upstreamResponse.headers.get("Content-Type") ?? "application/json",
      "Cache-Control": "no-store",
    },
  });
}
