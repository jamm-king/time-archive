import { NextRequest } from "next/server";
import { proxyBackendJson } from "@/lib/backend-proxy";

export async function GET(request: NextRequest) {
  const status = request.nextUrl.searchParams.get("status") ?? "UPLOADED";
  const path = `/api/admin/media/assets?status=${encodeURIComponent(status)}`;

  return proxyBackendJson({
    path,
    request,
  });
}
