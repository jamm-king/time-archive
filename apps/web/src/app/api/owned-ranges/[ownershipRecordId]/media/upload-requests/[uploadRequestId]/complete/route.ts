import { NextRequest } from "next/server";
import { proxyBackendJson } from "@/lib/backend-proxy";

type RouteContext = {
  params: Promise<{
    ownershipRecordId: string;
    uploadRequestId: string;
  }>;
};

export async function POST(request: NextRequest, context: RouteContext) {
  const { ownershipRecordId, uploadRequestId } = await context.params;

  return proxyBackendJson({
    method: "POST",
    path: `/api/owned-ranges/${ownershipRecordId}/media/upload-requests/${uploadRequestId}/complete`,
    request,
  });
}
