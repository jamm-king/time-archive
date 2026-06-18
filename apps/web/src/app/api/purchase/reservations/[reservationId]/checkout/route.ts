import { NextRequest } from "next/server";
import { proxyBackendJson } from "@/lib/backend-proxy";

type RouteContext = {
  params: Promise<{
    reservationId: string;
  }>;
};

export async function POST(request: NextRequest, context: RouteContext) {
  const { reservationId } = await context.params;

  return proxyBackendJson({
    method: "POST",
    path: `/api/purchase/reservations/${reservationId}/checkout`,
    request,
  });
}
