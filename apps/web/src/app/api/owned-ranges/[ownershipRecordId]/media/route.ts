import { NextRequest } from "next/server";
import { proxyBackendJson } from "@/lib/backend-proxy";

type RouteContext = {
  params: Promise<{
    ownershipRecordId: string;
  }>;
};

export async function GET(request: NextRequest, context: RouteContext) {
  const { ownershipRecordId } = await context.params;

  return proxyBackendJson({
    path: `/api/owned-ranges/${ownershipRecordId}/media`,
    request,
  });
}
