import { NextRequest, NextResponse } from "next/server";

export function getBackendApiBaseUrl(): string {
  return (
    process.env.TIME_ARCHIVE_API_BASE_URL?.replace(/\/$/, "") ??
    "http://localhost:8080"
  );
}

type ProxyOptions = {
  method?: string;
  path: string;
  request: NextRequest;
  body?: string;
};

export async function proxyBackendJson({
  method = "GET",
  path,
  request,
  body,
}: ProxyOptions): Promise<NextResponse> {
  const upstreamUrl = new URL(path, getBackendApiBaseUrl());
  const headers = new Headers({
    Accept: "application/json",
  });
  const cookie = request.headers.get("cookie");
  const csrfToken = request.headers.get("x-xsrf-token");
  const requestId = request.headers.get("x-request-id");

  if (cookie) {
    headers.set("Cookie", cookie);
  }
  if (csrfToken) {
    headers.set("X-XSRF-TOKEN", csrfToken);
  }
  if (requestId) {
    headers.set("X-Request-Id", requestId);
  }
  if (body !== undefined) {
    headers.set("Content-Type", "application/json");
  }

  const upstreamResponse = await fetch(upstreamUrl, {
    method,
    headers,
    body,
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

  for (const cookieValue of getSetCookieValues(upstreamResponse.headers)) {
    response.headers.append("Set-Cookie", cookieValue);
  }
  const upstreamRequestId = upstreamResponse.headers.get("x-request-id");
  if (upstreamRequestId) {
    response.headers.set("X-Request-Id", upstreamRequestId);
  }

  return response;
}

function allowsResponseBody(status: number): boolean {
  return status !== 204 && status !== 304;
}

function getSetCookieValues(headers: Headers): string[] {
  const maybeHeadersWithSetCookie = headers as Headers & {
    getSetCookie?: () => string[];
  };
  const setCookieValues = maybeHeadersWithSetCookie.getSetCookie?.();

  if (setCookieValues?.length) {
    return setCookieValues;
  }

  const singleValue = headers.get("set-cookie");
  return singleValue ? [singleValue] : [];
}
