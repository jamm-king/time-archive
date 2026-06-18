"use client";

import { useEffect, useMemo, useState } from "react";
import {
  ARCHIVE_TOTAL_SECONDS,
  fetchPublicTimeline,
  findActiveSegment,
  formatArchiveSecond,
  getSecondOfDay,
  getTimelineWindow,
  type PublicTimelineResponse,
  type PublicTimelineSegment,
  type TimelineWindow,
} from "@/lib/timeline";

type TimelineStatus = "loading" | "ready" | "empty" | "error";
type MediaStatus = "loading" | "ready" | "error";

export function PublicTimelinePlayer() {
  const [now, setNow] = useState(() => new Date());
  const currentSecond = getSecondOfDay(now);
  const timelineWindow = useMemo(
    () => getTimelineWindow(currentSecond),
    [currentSecond],
  );
  const [timeline, setTimeline] = useState<PublicTimelineResponse | null>(null);
  const [loadedWindow, setLoadedWindow] = useState<TimelineWindow | null>(null);
  const [errorWindow, setErrorWindow] = useState<TimelineWindow | null>(null);
  const [retryToken, setRetryToken] = useState(0);

  useEffect(() => {
    const timerId = window.setInterval(() => {
      setNow(new Date());
    }, 1000);

    return () => window.clearInterval(timerId);
  }, []);

  useEffect(() => {
    const controller = new AbortController();

    fetchPublicTimeline(timelineWindow, controller.signal)
      .then((result) => {
        setTimeline(result);
        setLoadedWindow({ from: result.from, to: result.to });
        setErrorWindow(null);
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        console.error(error);
        setTimeline(null);
        setLoadedWindow(null);
        setErrorWindow(timelineWindow);
      });

    return () => controller.abort();
  }, [retryToken, timelineWindow]);

  const loadedCurrentWindow =
    loadedWindow?.from === timelineWindow.from && loadedWindow.to === timelineWindow.to;
  const erroredCurrentWindow =
    errorWindow?.from === timelineWindow.from && errorWindow.to === timelineWindow.to;
  const status: TimelineStatus = !loadedCurrentWindow
    ? erroredCurrentWindow
      ? "error"
      : "loading"
    : timeline?.segments.length
      ? "ready"
      : "empty";
  const activeSegment = useMemo(
    () =>
      loadedCurrentWindow
        ? findActiveSegment(timeline?.segments ?? [], currentSecond)
        : null,
    [currentSecond, loadedCurrentWindow, timeline],
  );
  const progressPercent = (currentSecond / ARCHIVE_TOTAL_SECONDS) * 100;
  const rangeLabel = loadedWindow
    ? `${formatArchiveSecond(loadedWindow.from)}-${formatArchiveSecond(
        loadedWindow.to,
      )}`
    : `${formatArchiveSecond(timelineWindow.from)}-${formatArchiveSecond(
        timelineWindow.to,
      )}`;
  const retryTimeline = () => {
    setTimeline(null);
    setLoadedWindow(null);
    setErrorWindow(null);
    setRetryToken((value) => value + 1);
  };

  return (
    <main className="min-h-dvh bg-neutral-950 text-neutral-50">
      <section className="grid min-h-dvh grid-rows-[1fr_auto]">
        <div className="relative flex items-center justify-center overflow-hidden bg-neutral-950">
          <ActiveMedia segment={activeSegment} />
          <div className="relative flex h-full w-full max-w-6xl flex-col justify-between px-5 py-5 sm:px-8 sm:py-7">
            <header className="flex items-center justify-between text-xs uppercase text-neutral-400">
              <span>Time Archive</span>
              <span className="tabular-nums">
                {formatArchiveSecond(currentSecond)}
              </span>
            </header>

            <PlayerCenter
              status={status}
              activeSegment={activeSegment}
              currentSecond={currentSecond}
              onRetry={retryTimeline}
            />

            <footer className="grid gap-3 text-xs text-neutral-500 sm:grid-cols-[1fr_auto] sm:items-end">
              <div>
                <div className="h-1 overflow-hidden bg-neutral-800">
                  <div
                    className="h-full bg-neutral-100 transition-[width] duration-500"
                    style={{ width: `${progressPercent}%` }}
                  />
                </div>
                <div className="mt-2 flex justify-between tabular-nums">
                  <span>00:00:00</span>
                  <span>24:00:00</span>
                </div>
              </div>
              <span className="text-right tabular-nums">{rangeLabel}</span>
            </footer>
          </div>
        </div>
      </section>
    </main>
  );
}

function ActiveMedia({ segment }: { segment: PublicTimelineSegment | null }) {
  if (!segment) {
    return (
      <div className="absolute inset-0 bg-neutral-950" aria-hidden="true" />
    );
  }

  return <MediaElement key={segment.mediaAssetId} segment={segment} />;
}

function MediaElement({ segment }: { segment: PublicTimelineSegment }) {
  const [mediaStatus, setMediaStatus] = useState<MediaStatus>("loading");

  if (segment.mediaType === "VIDEO") {
    return (
      <>
        <video
          key={segment.mediaAssetId}
          className="absolute inset-0 h-full w-full object-cover"
          src={segment.mediaUrl}
          poster={segment.thumbnailUrl ?? undefined}
          autoPlay
          muted
          loop
          playsInline
          onCanPlay={() => setMediaStatus("ready")}
          onError={() => setMediaStatus("error")}
        />
        <MediaOverlay status={mediaStatus} />
      </>
    );
  }

  return (
    <>
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        key={segment.mediaAssetId}
        className="absolute inset-0 h-full w-full object-cover"
        src={segment.mediaUrl}
        alt=""
        onLoad={() => setMediaStatus("ready")}
        onError={() => setMediaStatus("error")}
      />
      <MediaOverlay status={mediaStatus} />
    </>
  );
}

function PlayerCenter({
  status,
  activeSegment,
  currentSecond,
  onRetry,
}: {
  status: TimelineStatus;
  activeSegment: PublicTimelineSegment | null;
  currentSecond: number;
  onRetry: () => void;
}) {
  if (activeSegment) {
    return <div className="flex-1" />;
  }

  const label =
    status === "loading"
      ? "Loading"
      : status === "error"
        ? "Timeline unavailable"
        : "Unclaimed second";
  const detail =
    status === "loading"
      ? "Syncing the current archive window."
      : status === "error"
        ? "The public timeline could not be loaded."
        : `${formatArchiveSecond(currentSecond)} is still available.`;

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-1 items-center justify-center text-center">
      <div>
        <p className="text-sm uppercase text-neutral-500">Public timeline</p>
        <h1 className="mt-4 text-balance text-4xl font-semibold text-neutral-50 sm:text-6xl">
          {label}
        </h1>
        <p className="mx-auto mt-4 max-w-sm text-sm leading-6 text-neutral-400">
          {detail}
        </p>
        {status === "error" ? (
          <button
            type="button"
            className="mt-6 border border-neutral-700 px-4 py-2 text-sm text-neutral-100 transition hover:border-neutral-500 focus:outline-none focus:ring-2 focus:ring-neutral-300"
            onClick={onRetry}
          >
            Retry
          </button>
        ) : null}
      </div>
    </div>
  );
}

function MediaOverlay({ status }: { status: MediaStatus }) {
  if (status === "ready") {
    return null;
  }

  const label =
    status === "loading" ? "Loading media" : "Media unavailable";

  return (
    <div className="absolute inset-0 flex items-center justify-center bg-neutral-950/80 text-center">
      <div>
        <p className="text-xs uppercase text-neutral-500">Archive media</p>
        <p className="mt-3 text-2xl font-semibold text-neutral-100 sm:text-4xl">
          {label}
        </p>
      </div>
    </div>
  );
}
