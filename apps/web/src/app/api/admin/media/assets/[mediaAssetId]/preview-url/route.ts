import { NextRequest } from "next/server";
import { proxyBackendJson } from "@/lib/backend-proxy";

type RouteContext = {
  params: Promise<{
    mediaAssetId: string;
  }>;
};

export async function GET(request: NextRequest, context: RouteContext) {
  const { mediaAssetId } = await context.params;

  return proxyBackendJson({
    path: `/api/admin/media/assets/${mediaAssetId}/preview-url`,
    request,
  });
}
