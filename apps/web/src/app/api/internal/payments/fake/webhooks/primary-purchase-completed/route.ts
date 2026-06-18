import { NextRequest } from "next/server";
import { proxyBackendJson } from "@/lib/backend-proxy";

export async function POST(request: NextRequest) {
  return proxyBackendJson({
    method: "POST",
    path: "/api/internal/payments/fake/webhooks/primary-purchase-completed",
    request,
    body: await request.text(),
  });
}
