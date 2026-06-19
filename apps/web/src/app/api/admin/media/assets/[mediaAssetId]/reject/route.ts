import { NextRequest } from "next/server";
import { proxyBackendJson } from "@/lib/backend-proxy";

type RouteContext = {
  params: Promise<{
    mediaAssetId: string;
  }>;
};

export async function POST(request: NextRequest, context: RouteContext) {
  const { mediaAssetId } = await context.params;

  return proxyBackendJson({
    method: "POST",
    path: `/api/admin/media/assets/${mediaAssetId}/reject`,
    request,
  });
}
