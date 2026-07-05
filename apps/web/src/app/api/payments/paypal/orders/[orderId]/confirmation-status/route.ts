import { NextRequest } from "next/server";
import { proxyBackendJson } from "@/lib/backend-proxy";

type RouteContext = {
  params: Promise<{
    orderId: string;
  }>;
};

export async function GET(request: NextRequest, context: RouteContext) {
  const { orderId } = await context.params;

  return proxyBackendJson({
    path: `/api/payments/paypal/orders/${encodeURIComponent(orderId)}/confirmation-status`,
    request,
  });
}
