import { NextRequest } from "next/server";
import { proxyBackendJson } from "@/lib/backend-proxy";

export async function GET(request: NextRequest) {
  const sourceUrl = new URL(request.url);
  const upstreamPath = new URL("/api/archive/availability", "http://localhost");
  const startSecond = sourceUrl.searchParams.get("startSecond");
  const endSecond = sourceUrl.searchParams.get("endSecond");

  if (startSecond !== null) {
    upstreamPath.searchParams.set("startSecond", startSecond);
  }
  if (endSecond !== null) {
    upstreamPath.searchParams.set("endSecond", endSecond);
  }

  return proxyBackendJson({
    path: `${upstreamPath.pathname}${upstreamPath.search}`,
    request,
  });
}
