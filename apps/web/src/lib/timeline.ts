export const ARCHIVE_TOTAL_SECONDS = 86_400;
export const TIMELINE_WINDOW_SECONDS = 300;

export type PublicTimelineMediaType = "IMAGE" | "VIDEO";

export type PublicTimelineSegment = {
  startSecond: number;
  endSecond: number;
  mediaAssetId: string;
  mediaType: PublicTimelineMediaType;
  mediaUrl: string;
  thumbnailUrl: string | null;
  externalLink: string | null;
};

export type PublicTimelineResponse = {
  from: number;
  to: number;
  segments: PublicTimelineSegment[];
};

export type TimelineWindow = {
  from: number;
  to: number;
};

export function getSecondOfDay(date: Date): number {
  return (
    date.getHours() * 60 * 60 +
    date.getMinutes() * 60 +
    date.getSeconds()
  );
}

export function formatArchiveSecond(second: number): string {
  const normalizedSecond = Math.max(
    0,
    Math.min(ARCHIVE_TOTAL_SECONDS, Math.floor(second)),
  );
  const hours = Math.floor(normalizedSecond / 3600);
  const minutes = Math.floor((normalizedSecond % 3600) / 60);
  const seconds = normalizedSecond % 60;

  return [hours, minutes, seconds]
    .map((part) => part.toString().padStart(2, "0"))
    .join(":");
}

export function getTimelineWindow(second: number): TimelineWindow {
  const safeSecond = Math.max(
    0,
    Math.min(ARCHIVE_TOTAL_SECONDS - 1, Math.floor(second)),
  );
  const from =
    Math.floor(safeSecond / TIMELINE_WINDOW_SECONDS) * TIMELINE_WINDOW_SECONDS;
  const to = Math.min(from + TIMELINE_WINDOW_SECONDS, ARCHIVE_TOTAL_SECONDS);

  return { from, to };
}

export function findActiveSegment(
  segments: PublicTimelineSegment[],
  second: number,
): PublicTimelineSegment | null {
  return (
    segments.find(
      (segment) => second >= segment.startSecond && second < segment.endSecond,
    ) ?? null
  );
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isNullableString(value: unknown): value is string | null {
  return typeof value === "string" || value === null;
}

function isTimelineSegment(value: unknown): value is PublicTimelineSegment {
  if (!isRecord(value)) {
    return false;
  }

  return (
    typeof value.startSecond === "number" &&
    Number.isInteger(value.startSecond) &&
    typeof value.endSecond === "number" &&
    Number.isInteger(value.endSecond) &&
    typeof value.mediaAssetId === "string" &&
    (value.mediaType === "IMAGE" || value.mediaType === "VIDEO") &&
    typeof value.mediaUrl === "string" &&
    isNullableString(value.thumbnailUrl) &&
    isNullableString(value.externalLink)
  );
}

function parsePublicTimelineResponse(value: unknown): PublicTimelineResponse {
  if (!isRecord(value)) {
    throw new Error("Timeline response must be an object");
  }

  const { from, to, segments } = value;

  if (
    typeof from !== "number" ||
    !Number.isInteger(from) ||
    typeof to !== "number" ||
    !Number.isInteger(to)
  ) {
    throw new Error("Timeline response has an invalid shape");
  }

  if (!Array.isArray(segments)) {
    throw new Error("Timeline response segments must be an array");
  }

  const parsedSegments = segments.map((segment) => {
    if (!isTimelineSegment(segment)) {
      throw new Error("Timeline response segment has an invalid shape");
    }

    return segment;
  });

  return {
    from,
    to,
    segments: parsedSegments,
  };
}

export async function fetchPublicTimeline(
  timelineWindow: TimelineWindow,
  signal?: AbortSignal,
): Promise<PublicTimelineResponse> {
  const url = new URL("/api/timeline", window.location.origin);
  url.searchParams.set("from", timelineWindow.from.toString());
  url.searchParams.set("to", timelineWindow.to.toString());

  const response = await fetch(url, {
    headers: {
      Accept: "application/json",
    },
    signal,
  });

  if (!response.ok) {
    throw new Error(`Timeline request failed with HTTP ${response.status}`);
  }

  return parsePublicTimelineResponse(await response.json());
}
