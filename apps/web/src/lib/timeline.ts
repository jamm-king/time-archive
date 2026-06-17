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

  return response.json();
}
