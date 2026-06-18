import { NextRequest } from "next/server";
import { proxyBackendJson } from "@/lib/backend-proxy";

type RouteContext = {
  params: Promise<{
    ownershipRecordId: string;
  }>;
};

export async function POST(request: NextRequest, context: RouteContext) {
  const { ownershipRecordId } = await context.params;

  return proxyBackendJson({
    method: "POST",
    path: `/api/owned-ranges/${ownershipRecordId}/media/upload-requests`,
    request,
    body: await request.text(),
  });
}
