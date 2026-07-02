import { fetchCsrfToken } from "@/lib/auth";

export type MediaType = "IMAGE" | "VIDEO";

export type MediaAsset = {
  mediaAssetId: string;
  ownershipRecordId: string;
  ownerId: string;
  mediaType: MediaType | string;
  originalFileUrl: string;
  approvedFileUrl: string | null;
  thumbnailUrl: string | null;
  externalLink: string | null;
  durationMs: number | null;
  moderationStatus: string;
  publiclyVisible: boolean;
  createdAt: string;
  updatedAt: string;
};

export type MediaUploadRequest = {
  uploadRequestId: string;
  ownershipRecordId: string;
  ownerId: string;
  mediaType: MediaType | string;
  originalFilename: string;
  contentType: string;
  contentLengthBytes: number;
  originalFileUrl: string;
  uploadUrl: string;
  requiredHeaders: Record<string, string>;
  status: string;
  expiresAt: string;
  createdAt: string;
};

export type CompleteMediaUploadResponse = {
  uploadRequestId: string;
  mediaAsset: MediaAsset;
  alreadyCompleted: boolean;
};

export async function fetchOwnedRangeMediaAssets(
  ownershipRecordId: string,
  signal?: AbortSignal,
): Promise<MediaAsset[]> {
  const response = await fetch(`/api/owned-ranges/${ownershipRecordId}/media`, {
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
    },
    signal,
  });

  if (!response.ok) {
    throw new Error(`Owned range media request failed with HTTP ${response.status}`);
  }

  return parseMediaAssets(await response.json());
}

export async function uploadOwnedRangeMedia(
  ownershipRecordId: string,
  file: File,
): Promise<CompleteMediaUploadResponse> {
  const mediaType = inferMediaType(file);
  const uploadRequest = await createUploadRequest(ownershipRecordId, {
    mediaType,
    originalFilename: file.name || "upload",
    contentType: file.type,
    contentLengthBytes: file.size,
  });

  await uploadFileToPresignedUrl(uploadRequest, file);

  return completeUploadRequest(ownershipRecordId, uploadRequest.uploadRequestId);
}

function inferMediaType(file: File): MediaType {
  if (file.type.startsWith("image/")) {
    return "IMAGE";
  }
  if (file.type.startsWith("video/")) {
    return "VIDEO";
  }

  throw new Error("Only image and video files can be uploaded");
}

async function createUploadRequest(
  ownershipRecordId: string,
  body: {
    mediaType: MediaType;
    originalFilename: string;
    contentType: string;
    contentLengthBytes: number;
  },
): Promise<MediaUploadRequest> {
  const csrfToken = await fetchCsrfToken();
  const response = await fetch(
    `/api/owned-ranges/${ownershipRecordId}/media/upload-requests`,
    {
      method: "POST",
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": csrfToken,
      },
      body: JSON.stringify(body),
    },
  );

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  return parseMediaUploadRequest(await response.json());
}

async function uploadFileToPresignedUrl(
  uploadRequest: MediaUploadRequest,
  file: File,
): Promise<void> {
  const headers = new Headers(uploadRequest.requiredHeaders);
  if (!headers.has("Content-Type")) {
    headers.set("Content-Type", uploadRequest.contentType);
  }

  const response = await fetch(uploadRequest.uploadUrl, {
    method: "PUT",
    headers,
    body: file,
  });

  if (!response.ok) {
    throw new Error(`Object upload failed with HTTP ${response.status}`);
  }
}

async function completeUploadRequest(
  ownershipRecordId: string,
  uploadRequestId: string,
): Promise<CompleteMediaUploadResponse> {
  const csrfToken = await fetchCsrfToken();
  const response = await fetch(
    `/api/owned-ranges/${ownershipRecordId}/media/upload-requests/${uploadRequestId}/complete`,
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

  return parseCompleteMediaUploadResponse(await response.json());
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

function parseMediaAssets(value: unknown): MediaAsset[] {
  if (!Array.isArray(value)) {
    throw new Error("Owned range media response must be an array");
  }

  return value.map(parseMediaAsset);
}

function parseMediaAsset(value: unknown): MediaAsset {
  if (!isRecord(value)) {
    throw new Error("Media asset response item must be an object");
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
    durationMs,
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
    !isNullableNumber(durationMs) ||
    typeof moderationStatus !== "string" ||
    typeof publiclyVisible !== "boolean" ||
    typeof createdAt !== "string" ||
    typeof updatedAt !== "string"
  ) {
    throw new Error("Media asset response item has an invalid shape");
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
    durationMs,
    moderationStatus,
    publiclyVisible,
    createdAt,
    updatedAt,
  };
}

function parseMediaUploadRequest(value: unknown): MediaUploadRequest {
  if (!isRecord(value)) {
    throw new Error("Media upload request response must be an object");
  }

  const {
    uploadRequestId,
    ownershipRecordId,
    ownerId,
    mediaType,
    originalFilename,
    contentType,
    contentLengthBytes,
    originalFileUrl,
    uploadUrl,
    requiredHeaders,
    status,
    expiresAt,
    createdAt,
  } = value;

  if (
    typeof uploadRequestId !== "string" ||
    typeof ownershipRecordId !== "string" ||
    typeof ownerId !== "string" ||
    typeof mediaType !== "string" ||
    typeof originalFilename !== "string" ||
    typeof contentType !== "string" ||
    typeof contentLengthBytes !== "number" ||
    typeof originalFileUrl !== "string" ||
    typeof uploadUrl !== "string" ||
    !isStringRecord(requiredHeaders) ||
    typeof status !== "string" ||
    typeof expiresAt !== "string" ||
    typeof createdAt !== "string"
  ) {
    throw new Error("Media upload request response has an invalid shape");
  }

  return {
    uploadRequestId,
    ownershipRecordId,
    ownerId,
    mediaType,
    originalFilename,
    contentType,
    contentLengthBytes,
    originalFileUrl,
    uploadUrl,
    requiredHeaders,
    status,
    expiresAt,
    createdAt,
  };
}

function parseCompleteMediaUploadResponse(value: unknown): CompleteMediaUploadResponse {
  if (!isRecord(value)) {
    throw new Error("Complete media upload response must be an object");
  }

  const { uploadRequestId, mediaAsset, alreadyCompleted } = value;
  if (
    typeof uploadRequestId !== "string" ||
    typeof alreadyCompleted !== "boolean"
  ) {
    throw new Error("Complete media upload response has an invalid shape");
  }

  return {
    uploadRequestId,
    mediaAsset: parseMediaAsset(mediaAsset),
    alreadyCompleted,
  };
}

function isNullableString(value: unknown): value is string | null {
  return typeof value === "string" || value === null;
}

function isNullableNumber(value: unknown): value is number | null {
  return typeof value === "number" || value === null;
}

function isStringRecord(value: unknown): value is Record<string, string> {
  if (!isRecord(value)) {
    return false;
  }

  return Object.values(value).every((item) => typeof item === "string");
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
