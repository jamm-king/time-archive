import { NextRequest } from "next/server";
import { proxyBackendJson } from "@/lib/backend-proxy";

export async function POST(request: NextRequest) {
  return proxyBackendJson({
    method: "POST",
    path: "/api/auth/register",
    request,
    body: await request.text(),
  });
}
