import { NextRequest } from "next/server";
import { proxyBackendJson } from "@/lib/backend-proxy";

export async function GET(request: NextRequest) {
  return proxyBackendJson({
    path: "/api/csrf",
    request,
  });
}
