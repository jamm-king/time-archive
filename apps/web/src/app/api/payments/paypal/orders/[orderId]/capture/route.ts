import { NextRequest } from "next/server";
import { proxyBackendJson } from "@/lib/backend-proxy";

type RouteContext = {
  params: Promise<{
    orderId: string;
  }>;
};

export async function POST(request: NextRequest, context: RouteContext) {
  const { orderId } = await context.params;

  return proxyBackendJson({
    method: "POST",
    path: `/api/payments/paypal/orders/${encodeURIComponent(orderId)}/capture`,
    request,
  });
}
