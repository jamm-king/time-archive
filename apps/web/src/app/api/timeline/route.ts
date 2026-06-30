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

  const requestId = request.headers.get("x-request-id");
  const requestHeaders = new Headers({
    Accept: "application/json",
  });
  if (requestId) {
    requestHeaders.set("X-Request-Id", requestId);
  }

  const upstreamResponse = await fetch(upstreamUrl, {
    headers: requestHeaders,
    cache: "no-store",
  });
  const body = await upstreamResponse.text();
  const responseHeaders = new Headers({
    "Content-Type":
      upstreamResponse.headers.get("Content-Type") ?? "application/json",
    "Cache-Control": "no-store",
  });
  const upstreamRequestId = upstreamResponse.headers.get("x-request-id");
  if (upstreamRequestId) {
    responseHeaders.set("X-Request-Id", upstreamRequestId);
  }

  return new NextResponse(body, {
    status: upstreamResponse.status,
    headers: responseHeaders,
  });
}
