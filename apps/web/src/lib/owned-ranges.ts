export type OwnedRange = {
  ownershipRecordId: string;
  startSecond: number;
  endSecond: number;
  status: "ACTIVE" | string;
  acquiredAt: string;
  createdAt: string;
  updatedAt: string;
};

export async function fetchOwnedRanges(signal?: AbortSignal): Promise<OwnedRange[]> {
  const response = await fetch("/api/me/owned-ranges", {
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
    },
    signal,
  });

  if (response.status === 401) {
    return [];
  }
  if (!response.ok) {
    throw new Error(`Owned ranges request failed with HTTP ${response.status}`);
  }

  return parseOwnedRanges(await response.json());
}

function parseOwnedRanges(value: unknown): OwnedRange[] {
  if (!Array.isArray(value)) {
    throw new Error("Owned ranges response must be an array");
  }

  return value.map(parseOwnedRange);
}

function parseOwnedRange(value: unknown): OwnedRange {
  if (!isRecord(value)) {
    throw new Error("Owned range response item must be an object");
  }

  const {
    ownershipRecordId,
    startSecond,
    endSecond,
    status,
    acquiredAt,
    createdAt,
    updatedAt,
  } = value;

  if (
    typeof ownershipRecordId !== "string" ||
    typeof startSecond !== "number" ||
    typeof endSecond !== "number" ||
    typeof status !== "string" ||
    typeof acquiredAt !== "string" ||
    typeof createdAt !== "string" ||
    typeof updatedAt !== "string"
  ) {
    throw new Error("Owned range response item has an invalid shape");
  }

  return {
    ownershipRecordId,
    startSecond,
    endSecond,
    status,
    acquiredAt,
    createdAt,
    updatedAt,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
