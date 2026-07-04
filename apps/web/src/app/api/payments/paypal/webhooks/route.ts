import { NextRequest, NextResponse } from "next/server";
import { getBackendApiBaseUrl } from "@/lib/backend-proxy";

const PAYPAL_WEBHOOK_HEADERS = [
  "paypal-transmission-id",
  "paypal-transmission-time",
  "paypal-cert-url",
  "paypal-auth-algo",
  "paypal-transmission-sig",
] as const;

const FORWARDED_CLOUDFLARE_HEADERS = [
  "cf-connecting-ip",
  "cf-ray",
  "cf-visitor",
  "cf-ipcountry",
] as const;

export async function POST(request: NextRequest) {
  const upstreamUrl = new URL(
    "/api/payments/paypal/webhooks",
    getBackendApiBaseUrl(),
  );
  const headers = new Headers({
    Accept: "application/json",
    "Content-Type": request.headers.get("content-type") ?? "application/json",
  });
  const requestId = request.headers.get("x-request-id");

  if (requestId) {
    headers.set("X-Request-Id", requestId);
  }
  for (const headerName of PAYPAL_WEBHOOK_HEADERS) {
    const headerValue = request.headers.get(headerName);
    if (headerValue) {
      headers.set(headerName, headerValue);
    }
  }
  for (const headerName of FORWARDED_CLOUDFLARE_HEADERS) {
    const headerValue = request.headers.get(headerName);
    if (headerValue) {
      headers.set(headerName, headerValue);
    }
  }

  const upstreamResponse = await fetch(upstreamUrl, {
    method: "POST",
    headers,
    body: await request.text(),
    cache: "no-store",
  });
  const responseBody = allowsResponseBody(upstreamResponse.status)
    ? await upstreamResponse.text()
    : null;

  const response = new NextResponse(responseBody, {
    status: upstreamResponse.status,
    headers: {
      "Content-Type":
        upstreamResponse.headers.get("Content-Type") ?? "application/json",
    },
  });
  const upstreamRequestId = upstreamResponse.headers.get("x-request-id");
  if (upstreamRequestId) {
    response.headers.set("X-Request-Id", upstreamRequestId);
  }

  return response;
}

function allowsResponseBody(status: number): boolean {
  return status !== 204 && status !== 304;
}
