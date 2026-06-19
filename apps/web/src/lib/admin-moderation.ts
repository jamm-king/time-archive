import { fetchCsrfToken } from "@/lib/auth";

export type ModerationStatus =
  | "UPLOADED"
  | "PENDING_REVIEW"
  | "APPROVED"
  | "REJECTED"
  | "HIDDEN"
  | string;

export type AdminMediaAsset = {
  mediaAssetId: string;
  ownershipRecordId: string;
  ownerId: string;
  mediaType: string;
  originalFileUrl: string;
  approvedFileUrl: string | null;
  thumbnailUrl: string | null;
  externalLink: string | null;
  moderationStatus: ModerationStatus;
  publiclyVisible: boolean;
  createdAt: string;
  updatedAt: string;
};

export async function fetchAdminMediaAssets(
  status: ModerationStatus,
  signal?: AbortSignal,
): Promise<AdminMediaAsset[]> {
  const url = new URL("/api/admin/media/assets", window.location.origin);
  url.searchParams.set("status", status);

  const response = await fetch(url, {
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
    },
    signal,
  });

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  return parseAdminMediaAssets(await response.json());
}

export async function approveAdminMediaAsset(
  mediaAssetId: string,
  values: {
    approvedFileUrl: string;
    thumbnailUrl?: string | null;
  },
): Promise<AdminMediaAsset> {
  const csrfToken = await fetchCsrfToken();
  const response = await fetch(
    `/api/admin/media/assets/${mediaAssetId}/approve`,
    {
      method: "POST",
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": csrfToken,
      },
      body: JSON.stringify({
        approvedFileUrl: values.approvedFileUrl,
        thumbnailUrl: values.thumbnailUrl ?? null,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  return parseAdminMediaAsset(await response.json());
}

export async function rejectAdminMediaAsset(
  mediaAssetId: string,
): Promise<AdminMediaAsset> {
  const csrfToken = await fetchCsrfToken();
  const response = await fetch(
    `/api/admin/media/assets/${mediaAssetId}/reject`,
    {
      method: "POST",
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
        "X-XSRF-TOKEN": csrfToken,
      },
    },
  );

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  return parseAdminMediaAsset(await response.json());
}

export async function hideAdminMediaAsset(
  mediaAssetId: string,
): Promise<AdminMediaAsset> {
  const csrfToken = await fetchCsrfToken();
  const response = await fetch(
    `/api/admin/media/assets/${mediaAssetId}/hide`,
    {
      method: "POST",
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
        "X-XSRF-TOKEN": csrfToken,
      },
    },
  );

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  return parseAdminMediaAsset(await response.json());
}

async function getErrorMessage(response: Response): Promise<string> {
  try {
    const body = await response.json();
    if (isRecord(body) && typeof body.message === "string") {
      return body.message;
    }
  } catch {
    return `Request failed with HTTP ${response.status}`;
  }

  return `Request failed with HTTP ${response.status}`;
}

function parseAdminMediaAssets(value: unknown): AdminMediaAsset[] {
  if (!Array.isArray(value)) {
    throw new Error("Admin media assets response must be an array");
  }

  return value.map(parseAdminMediaAsset);
}

function parseAdminMediaAsset(value: unknown): AdminMediaAsset {
  if (!isRecord(value)) {
    throw new Error("Admin media asset response item must be an object");
  }

  const {
    mediaAssetId,
    ownershipRecordId,
    ownerId,
    mediaType,
    originalFileUrl,
    approvedFileUrl,
    thumbnailUrl,
    externalLink,
    moderationStatus,
    publiclyVisible,
    createdAt,
    updatedAt,
  } = value;

  if (
    typeof mediaAssetId !== "string" ||
    typeof ownershipRecordId !== "string" ||
    typeof ownerId !== "string" ||
    typeof mediaType !== "string" ||
    typeof originalFileUrl !== "string" ||
    !isNullableString(approvedFileUrl) ||
    !isNullableString(thumbnailUrl) ||
    !isNullableString(externalLink) ||
    typeof moderationStatus !== "string" ||
    typeof publiclyVisible !== "boolean" ||
    typeof createdAt !== "string" ||
    typeof updatedAt !== "string"
  ) {
    throw new Error("Admin media asset response item has an invalid shape");
  }

  return {
    mediaAssetId,
    ownershipRecordId,
    ownerId,
    mediaType,
    originalFileUrl,
    approvedFileUrl,
    thumbnailUrl,
    externalLink,
    moderationStatus,
    publiclyVisible,
    createdAt,
    updatedAt,
  };
}

function isNullableString(value: unknown): value is string | null {
  return typeof value === "string" || value === null;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
