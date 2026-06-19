"use client";

import {
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type FormEvent,
} from "react";
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
import {
  authenticate,
  fetchCurrentUser,
  logout,
  type AuthMode,
  type CurrentUser,
} from "@/lib/auth";
import {
  fetchOwnedRangeMediaAssets,
  uploadOwnedRangeMedia,
  type MediaAsset,
} from "@/lib/owned-media";
import { fetchOwnedRanges, type OwnedRange } from "@/lib/owned-ranges";
import {
  checkAvailability,
  completeFakePrimaryPurchase,
  createCheckout,
  findMaxAvailableDuration,
  reserveTimeRange,
  type ReservationResponse,
} from "@/lib/purchase";
import {
  buildDurationPresets,
  clampDuration,
} from "@/lib/purchase-duration";

type TimelineStatus = "loading" | "ready" | "empty" | "error";
type MediaStatus = "loading" | "ready" | "error";
type AuthStatus = "loading" | "guest" | "authenticated" | "error";
type UploadStatus = "idle" | "uploading" | "complete" | "error";
type PurchaseStatus =
  | "idle"
  | "loadingMaxDuration"
  | "checkingAvailability"
  | "available"
  | "unavailable"
  | "reserving"
  | "reserved"
  | "completing"
  | "complete"
  | "error";

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
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(null);
  const [authStatus, setAuthStatus] = useState<AuthStatus>("loading");
  const [authPanelOpen, setAuthPanelOpen] = useState(false);
  const [ownedRangesRefreshToken, setOwnedRangesRefreshToken] = useState(0);

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

  useEffect(() => {
    const controller = new AbortController();

    fetchCurrentUser(controller.signal)
      .then((user) => {
        setCurrentUser(user);
        setAuthStatus(user ? "authenticated" : "guest");
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        console.error(error);
        setCurrentUser(null);
        setAuthStatus("error");
      });

    return () => controller.abort();
  }, []);

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
              <div className="flex items-center gap-3">
                <span className="tabular-nums">
                  {formatArchiveSecond(currentSecond)}
                </span>
                <AuthControl
                  status={authStatus}
                  currentUser={currentUser}
                  panelOpen={authPanelOpen}
                  ownedRangesRefreshToken={ownedRangesRefreshToken}
                  onTogglePanel={() => setAuthPanelOpen((value) => !value)}
                  onAuthenticated={(user) => {
                    setCurrentUser(user);
                    setAuthStatus("authenticated");
                    setAuthPanelOpen(false);
                    setOwnedRangesRefreshToken((value) => value + 1);
                  }}
                  onLogout={async () => {
                    await logout();
                    setCurrentUser(null);
                    setAuthStatus("guest");
                    setAuthPanelOpen(false);
                  }}
                />
              </div>
            </header>

            <PlayerCenter
              status={status}
              activeSegment={activeSegment}
              currentSecond={currentSecond}
              currentUser={currentUser}
              onPurchaseComplete={() => {
                setOwnedRangesRefreshToken((value) => value + 1);
                retryTimeline();
              }}
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

function AuthControl({
  status,
  currentUser,
  panelOpen,
  ownedRangesRefreshToken,
  onTogglePanel,
  onAuthenticated,
  onLogout,
}: {
  status: AuthStatus;
  currentUser: CurrentUser | null;
  panelOpen: boolean;
  ownedRangesRefreshToken: number;
  onTogglePanel: () => void;
  onAuthenticated: (user: CurrentUser) => void;
  onLogout: () => Promise<void>;
}) {
  const label =
    status === "loading"
      ? "Checking"
      : status === "authenticated"
        ? currentUser?.displayName ?? "Account"
        : status === "error"
          ? "Retry sign in"
          : "Sign in";

  return (
    <div className="relative">
      <button
        type="button"
        className="border border-neutral-800 bg-neutral-950/80 px-3 py-1.5 text-xs uppercase text-neutral-200 transition hover:border-neutral-600 focus:outline-none focus:ring-2 focus:ring-neutral-300"
        onClick={onTogglePanel}
      >
        {label}
      </button>
      {panelOpen ? (
        <AuthPanel
          currentUser={currentUser}
          ownedRangesRefreshToken={ownedRangesRefreshToken}
          onAuthenticated={onAuthenticated}
          onLogout={onLogout}
        />
      ) : null}
    </div>
  );
}

function AuthPanel({
  currentUser,
  onAuthenticated,
  onLogout,
  ownedRangesRefreshToken,
}: {
  currentUser: CurrentUser | null;
  onAuthenticated: (user: CurrentUser) => void;
  onLogout: () => Promise<void>;
  ownedRangesRefreshToken: number;
}) {
  const [mode, setMode] = useState<AuthMode>("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (currentUser) {
    return (
      <div className="absolute right-0 top-9 z-20 w-72 border border-neutral-800 bg-neutral-950 p-4 text-left normal-case shadow-2xl shadow-black/40">
        <p className="text-xs uppercase text-neutral-500">Signed in</p>
        <p className="mt-2 truncate text-sm font-medium text-neutral-100">
          {currentUser.displayName}
        </p>
        <p className="mt-1 truncate text-xs text-neutral-500">
          {currentUser.email}
        </p>
        <OwnedRangesList
          currentUserId={currentUser.userId}
          refreshToken={ownedRangesRefreshToken}
        />
        <button
          type="button"
          className="mt-4 w-full border border-neutral-700 px-3 py-2 text-xs uppercase text-neutral-100 transition hover:border-neutral-500 focus:outline-none focus:ring-2 focus:ring-neutral-300"
          onClick={() => {
            setSubmitting(true);
            setError(null);
            onLogout()
              .catch((logoutError: unknown) => {
                console.error(logoutError);
                setError("Logout failed");
              })
              .finally(() => setSubmitting(false));
          }}
          disabled={submitting}
        >
          {submitting ? "Signing out" : "Sign out"}
        </button>
        {error ? <p className="mt-3 text-xs text-red-300">{error}</p> : null}
      </div>
    );
  }

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);

    try {
      const user = await authenticate(mode, {
        email,
        password,
        displayName: mode === "register" ? displayName : undefined,
      });
      onAuthenticated(user);
    } catch (authError: unknown) {
      console.error(authError);
      setError(
        authError instanceof Error
          ? authError.message
          : "Authentication failed",
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="absolute right-0 top-9 z-20 w-80 border border-neutral-800 bg-neutral-950 p-4 text-left normal-case shadow-2xl shadow-black/40">
      <div className="grid grid-cols-2 border border-neutral-800 text-xs uppercase">
        <button
          type="button"
          className={`px-3 py-2 ${
            mode === "login" ? "bg-neutral-100 text-neutral-950" : "text-neutral-400"
          }`}
          onClick={() => setMode("login")}
        >
          Login
        </button>
        <button
          type="button"
          className={`px-3 py-2 ${
            mode === "register" ? "bg-neutral-100 text-neutral-950" : "text-neutral-400"
          }`}
          onClick={() => setMode("register")}
        >
          Register
        </button>
      </div>
      <form className="mt-4 grid gap-3" onSubmit={submit}>
        <label className="grid gap-1 text-xs uppercase text-neutral-500">
          Email
          <input
            className="border border-neutral-800 bg-neutral-900 px-3 py-2 text-sm normal-case text-neutral-100 outline-none focus:border-neutral-500"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            required
          />
        </label>
        <label className="grid gap-1 text-xs uppercase text-neutral-500">
          Password
          <input
            className="border border-neutral-800 bg-neutral-900 px-3 py-2 text-sm normal-case text-neutral-100 outline-none focus:border-neutral-500"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            minLength={8}
            required
          />
        </label>
        {mode === "register" ? (
          <label className="grid gap-1 text-xs uppercase text-neutral-500">
            Display name
            <input
              className="border border-neutral-800 bg-neutral-900 px-3 py-2 text-sm normal-case text-neutral-100 outline-none focus:border-neutral-500"
              type="text"
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              required
            />
          </label>
        ) : null}
        <button
          type="submit"
          className="mt-1 border border-neutral-700 px-3 py-2 text-xs uppercase text-neutral-100 transition hover:border-neutral-500 focus:outline-none focus:ring-2 focus:ring-neutral-300 disabled:text-neutral-600"
          disabled={submitting}
        >
          {submitting ? "Submitting" : mode === "register" ? "Create account" : "Sign in"}
        </button>
        {error ? <p className="text-xs text-red-300">{error}</p> : null}
      </form>
    </div>
  );
}

function OwnedRangesList({
  currentUserId,
  refreshToken,
}: {
  currentUserId: string;
  refreshToken: number;
}) {
  const [ranges, setRanges] = useState<OwnedRange[]>([]);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");

  useEffect(() => {
    const controller = new AbortController();

    fetchOwnedRanges(controller.signal)
      .then((result) => {
        setRanges(result);
        setStatus("ready");
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        console.error(error);
        setRanges([]);
        setStatus("error");
      });

    return () => controller.abort();
  }, [currentUserId, refreshToken]);

  return (
    <div className="mt-4 border-t border-neutral-800 pt-4">
      <div className="flex items-center justify-between gap-3">
        <p className="text-xs uppercase text-neutral-500">Owned seconds</p>
        {status === "ready" ? (
          <span className="text-xs tabular-nums text-neutral-600">
            {ranges.length}
          </span>
        ) : null}
      </div>
      {status === "loading" ? (
        <p className="mt-3 text-xs text-neutral-500">Loading</p>
      ) : status === "error" ? (
        <p className="mt-3 text-xs text-red-300">Owned ranges unavailable</p>
      ) : ranges.length === 0 ? (
        <p className="mt-3 text-xs leading-5 text-neutral-500">
          No owned seconds yet.
        </p>
      ) : (
        <ul className="mt-3 grid max-h-72 gap-2 overflow-y-auto pr-1">
          {ranges.map((range) => (
            <OwnedRangeItem key={range.ownershipRecordId} range={range} />
          ))}
        </ul>
      )}
    </div>
  );
}

function OwnedRangeItem({ range }: { range: OwnedRange }) {
  const [mediaAssets, setMediaAssets] = useState<MediaAsset[]>([]);
  const [mediaStatus, setMediaStatus] = useState<"loading" | "ready" | "error">(
    "loading",
  );
  const [modalOpen, setModalOpen] = useState(false);

  useEffect(() => {
    const controller = new AbortController();

    fetchOwnedRangeMediaAssets(range.ownershipRecordId, controller.signal)
      .then((assets) => {
        setMediaAssets(assets);
        setMediaStatus("ready");
      })
      .catch((error: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        console.error(error);
        setMediaAssets([]);
        setMediaStatus("error");
      });

    return () => controller.abort();
  }, [range.ownershipRecordId]);

  const addMediaAsset = (asset: MediaAsset) => {
    setMediaAssets((assets) => [asset, ...assets]);
    setMediaStatus("ready");
  };

  return (
    <li className="border border-neutral-800 px-3 py-2">
      <div className="grid gap-2">
        <div className="flex min-w-0 items-center justify-between gap-3">
          <span className="min-w-0 truncate text-xs tabular-nums text-neutral-100">
            {formatArchiveSecond(range.startSecond)}-
            {formatArchiveSecond(range.endSecond)}
          </span>
          <span className="shrink-0 text-[10px] uppercase text-neutral-500">
            {range.status}
          </span>
        </div>
        <div className="flex min-w-0 items-center justify-between gap-3">
          <OwnedRangeMediaSummary
            status={mediaStatus}
            mediaAssets={mediaAssets}
          />
          <button
            type="button"
            className="shrink-0 border border-neutral-700 px-2.5 py-1.5 text-[10px] uppercase text-neutral-100 transition hover:border-neutral-500 focus:outline-none focus:ring-2 focus:ring-neutral-300"
            onClick={() => setModalOpen(true)}
          >
            Manage media
          </button>
        </div>
      </div>
      {modalOpen ? (
        <OwnedRangeMediaModal
          range={range}
          mediaAssets={mediaAssets}
          mediaStatus={mediaStatus}
          onMediaUploaded={addMediaAsset}
          onClose={() => setModalOpen(false)}
        />
      ) : null}
    </li>
  );
}

function OwnedRangeMediaModal({
  range,
  mediaAssets,
  mediaStatus,
  onMediaUploaded,
  onClose,
}: {
  range: OwnedRange;
  mediaAssets: MediaAsset[];
  mediaStatus: "loading" | "ready" | "error";
  onMediaUploaded: (asset: MediaAsset) => void;
  onClose: () => void;
}) {
  const fileInputId = useId();
  const titleId = useId();
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploadStatus, setUploadStatus] = useState<UploadStatus>("idle");
  const [uploadError, setUploadError] = useState<string | null>(null);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onClose();
      }
    };

    window.addEventListener("keydown", handleKeyDown);

    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  const upload = async () => {
    if (!selectedFile || uploadStatus === "uploading") {
      return;
    }

    setUploadStatus("uploading");
    setUploadError(null);

    try {
      const result = await uploadOwnedRangeMedia(
        range.ownershipRecordId,
        selectedFile,
      );
      onMediaUploaded(result.mediaAsset);
      setSelectedFile(null);
      setUploadStatus("complete");
    } catch (error: unknown) {
      console.error(error);
      setUploadStatus("error");
      setUploadError(error instanceof Error ? error.message : "Upload failed");
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4 py-6"
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
    >
      <div className="max-h-full w-full max-w-md overflow-y-auto border border-neutral-800 bg-neutral-950 p-4 text-left shadow-2xl shadow-black/50">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <p className="text-xs uppercase text-neutral-500">Owned media</p>
            <h2
              id={titleId}
              className="mt-1 truncate text-sm font-medium text-neutral-100"
            >
              {formatArchiveSecond(range.startSecond)}-
              {formatArchiveSecond(range.endSecond)}
            </h2>
          </div>
          <button
            type="button"
            className="shrink-0 border border-neutral-800 px-2.5 py-1.5 text-[10px] uppercase text-neutral-400 transition hover:border-neutral-600 focus:outline-none focus:ring-2 focus:ring-neutral-300"
            onClick={onClose}
          >
            Close
          </button>
        </div>

        <div className="mt-4 grid gap-3 border-t border-neutral-800 pt-4">
          <p className="text-xs uppercase text-neutral-500">Upload</p>
          <input
            id={fileInputId}
            className="sr-only"
            type="file"
            accept="image/*,video/*"
            onChange={(event) => {
              setSelectedFile(event.target.files?.[0] ?? null);
              setUploadStatus("idle");
              setUploadError(null);
            }}
            disabled={uploadStatus === "uploading"}
          />
          <div className="grid grid-cols-[auto_1fr] items-center gap-3">
            <label
              htmlFor={fileInputId}
              className="cursor-pointer border border-neutral-700 px-3 py-2 text-xs uppercase text-neutral-100 transition hover:border-neutral-500 focus-within:outline-none focus-within:ring-2 focus-within:ring-neutral-300"
            >
              Choose file
            </label>
            <span className="min-w-0 truncate text-xs text-neutral-500">
              {selectedFile ? selectedFile.name : "No file selected"}
            </span>
          </div>
          <button
            type="button"
            className="border border-neutral-700 px-3 py-2 text-xs uppercase text-neutral-100 transition hover:border-neutral-500 focus:outline-none focus:ring-2 focus:ring-neutral-300 disabled:text-neutral-600"
            onClick={upload}
            disabled={!selectedFile || uploadStatus === "uploading"}
          >
            {uploadStatus === "uploading" ? "Uploading" : "Upload"}
          </button>
          {uploadStatus === "complete" ? (
            <p className="text-xs text-neutral-400">Pending moderation</p>
          ) : null}
          {uploadError ? (
            <p className="break-words text-xs text-red-300">{uploadError}</p>
          ) : null}
        </div>

        <div className="mt-4 border-t border-neutral-800 pt-4">
          <p className="text-xs uppercase text-neutral-500">Media assets</p>
          <OwnedRangeMediaDetail
            status={mediaStatus}
            mediaAssets={mediaAssets}
          />
        </div>
      </div>
    </div>
  );
}

function OwnedRangeMediaSummary({
  status,
  mediaAssets,
}: {
  status: "loading" | "ready" | "error";
  mediaAssets: MediaAsset[];
}) {
  if (status === "loading") {
    return <p className="min-w-0 text-xs text-neutral-600">Checking media</p>;
  }
  if (status === "error") {
    return (
      <p className="min-w-0 truncate text-xs text-red-300">
        Media unavailable
      </p>
    );
  }
  if (mediaAssets.length === 0) {
    return <p className="min-w-0 text-xs text-neutral-600">No media</p>;
  }

  const latest = mediaAssets[0];

  return (
    <p className="min-w-0 truncate text-xs text-neutral-500">
      {mediaAssets.length} media - {latest.moderationStatus}
    </p>
  );
}

function OwnedRangeMediaDetail({
  status,
  mediaAssets,
}: {
  status: "loading" | "ready" | "error";
  mediaAssets: MediaAsset[];
}) {
  if (status === "loading") {
    return <p className="mt-3 text-xs text-neutral-600">Checking media</p>;
  }
  if (status === "error") {
    return <p className="mt-3 text-xs text-red-300">Media list unavailable</p>;
  }
  if (mediaAssets.length === 0) {
    return <p className="mt-3 text-xs text-neutral-600">No media uploaded</p>;
  }

  return (
    <ul className="mt-3 grid gap-1">
      {mediaAssets.slice(0, 3).map((asset) => (
        <li
          key={asset.mediaAssetId}
          className="flex items-center justify-between gap-3 text-xs"
        >
          <span className="truncate text-neutral-400">
            {asset.mediaType}
          </span>
          <span className="shrink-0 uppercase text-neutral-500">
            {asset.moderationStatus}
          </span>
        </li>
      ))}
    </ul>
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
  currentUser,
  onPurchaseComplete,
  onRetry,
}: {
  status: TimelineStatus;
  activeSegment: PublicTimelineSegment | null;
  currentSecond: number;
  currentUser: CurrentUser | null;
  onPurchaseComplete: () => void;
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
        ) : status === "empty" || status === "ready" ? (
          <PurchaseCurrentSecondPanel
            key={currentUser?.userId ?? "guest"}
            currentSecond={currentSecond}
            currentUser={currentUser}
            onComplete={onPurchaseComplete}
          />
        ) : null}
      </div>
    </div>
  );
}

function PurchaseCurrentSecondPanel({
  currentSecond,
  currentUser,
  onComplete,
}: {
  currentSecond: number;
  currentUser: CurrentUser | null;
  onComplete: () => void;
}) {
  const [status, setStatus] = useState<PurchaseStatus>("idle");
  const [draftStartSecond, setDraftStartSecond] = useState<number | null>(null);
  const [maxDuration, setMaxDuration] = useState<number | null>(null);
  const [duration, setDuration] = useState(1);
  const [reservation, setReservation] = useState<ReservationResponse | null>(null);
  const [checkoutUrl, setCheckoutUrl] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [durationNotice, setDurationNotice] = useState<string | null>(null);
  const availabilityRequestId = useRef(0);
  const selectedSecond = Math.min(currentSecond, ARCHIVE_TOTAL_SECONDS - 1);
  const selectedStartSecond = draftStartSecond ?? selectedSecond;
  const selectedEndSecond = selectedStartSecond + duration;
  const durationPresets = maxDuration === null
    ? []
    : buildDurationPresets(maxDuration);

  const checkSelectedAvailability = async (
    startSecond: number,
    nextDuration: number,
  ) => {
    const requestId = availabilityRequestId.current + 1;
    availabilityRequestId.current = requestId;
    setStatus("checkingAvailability");
    setMessage(null);

    try {
      const availability = await checkAvailability(
        startSecond,
        startSecond + nextDuration,
      );
      if (availabilityRequestId.current !== requestId) {
        return;
      }
      if (availability.available) {
        setStatus("available");
        setMessage(null);
      } else {
        setStatus("unavailable");
        setMessage("Some seconds in this range are already claimed.");
      }
    } catch (error: unknown) {
      if (availabilityRequestId.current !== requestId) {
        return;
      }
      console.error(error);
      setStatus("error");
      setMessage(
        error instanceof Error ? error.message : "Availability check failed",
      );
    }
  };

  const beginDraft = async () => {
    if (status !== "idle" && status !== "error") {
      return;
    }
    if (!currentUser) {
      return;
    }

    const nextStartSecond = selectedSecond;
    setStatus("loadingMaxDuration");
    setMessage(null);
    setDurationNotice(null);
    setCheckoutUrl(null);
    setReservation(null);
    setDraftStartSecond(nextStartSecond);
    setMaxDuration(null);
    setDuration(1);

    try {
      const nextMaxDuration = await findMaxAvailableDuration(nextStartSecond);
      if (nextMaxDuration < 1) {
        setStatus("error");
        setMessage("This second is already unavailable.");
        return;
      }

      setMaxDuration(nextMaxDuration);
      await checkSelectedAvailability(nextStartSecond, 1);
    } catch (error: unknown) {
      console.error(error);
      setStatus("error");
      setMessage(error instanceof Error ? error.message : "Availability check failed");
    }
  };

  const updateDuration = (nextDuration: number) => {
    if (maxDuration === null) {
      return;
    }

    const result = clampDuration(nextDuration, maxDuration);
    setDuration(result.duration);
    setDurationNotice(
      result.wasClamped && nextDuration > maxDuration
        ? `Maximum available duration from this second is ${maxDuration}s.`
        : null,
    );
    if (draftStartSecond !== null && !reservation) {
      void checkSelectedAvailability(draftStartSecond, result.duration);
    }
  };

  const reserve = async () => {
    if (
      status !== "available" ||
      !currentUser ||
      draftStartSecond === null ||
      maxDuration === null
    ) {
      return;
    }

    setStatus("reserving");
    setMessage(null);
    setDurationNotice(null);
    setCheckoutUrl(null);

    try {
      const nextReservation = await reserveTimeRange(
        draftStartSecond,
        draftStartSecond + duration,
      );
      const checkout = await createCheckout(nextReservation.reservationId);
      setReservation(nextReservation);
      setCheckoutUrl(checkout.checkoutUrl);
      setStatus("reserved");
    } catch (error: unknown) {
      console.error(error);
      setStatus("error");
      setMessage(error instanceof Error ? error.message : "Purchase failed");
    }
  };

  const resetDraft = () => {
    setStatus("idle");
    setDraftStartSecond(null);
    setMaxDuration(null);
    setDuration(1);
    setReservation(null);
    setCheckoutUrl(null);
    setMessage(null);
    setDurationNotice(null);
  };

  const complete = async () => {
    if (!reservation || status === "completing") {
      return;
    }

    setStatus("completing");
    setMessage(null);

    try {
      const result = await completeFakePrimaryPurchase(reservation.reservationId);
      if (!result.ownershipRecordId) {
        throw new Error("Purchase completed without ownership record");
      }
      setStatus("complete");
      setMessage("Owned range added.");
      onComplete();
    } catch (error: unknown) {
      console.error(error);
      setStatus("error");
      setMessage(error instanceof Error ? error.message : "Payment completion failed");
    }
  };

  if (!currentUser) {
    return (
      <p className="mx-auto mt-6 max-w-xs text-xs leading-5 text-neutral-500">
        Sign in to buy this second.
      </p>
    );
  }

  return (
    <div className="mx-auto mt-6 grid w-full max-w-sm gap-3">
      {draftStartSecond === null ? (
        <button
          type="button"
          className="border border-neutral-700 px-4 py-2 text-xs uppercase text-neutral-100 transition hover:border-neutral-500 focus:outline-none focus:ring-2 focus:ring-neutral-300 disabled:text-neutral-600"
          onClick={beginDraft}
          disabled={status === "loadingMaxDuration" || status === "complete"}
        >
          {status === "loadingMaxDuration"
            ? "Checking range"
            : status === "complete"
              ? "Owned"
              : "Buy this second"}
        </button>
      ) : null}
      {draftStartSecond !== null && maxDuration !== null && !reservation ? (
        <div className="border border-neutral-800 bg-neutral-950/80 p-3 text-left">
          <div className="flex items-center justify-between gap-3 text-xs">
            <span className="uppercase text-neutral-500">Start</span>
            <span className="tabular-nums text-neutral-100">
              {formatArchiveSecond(draftStartSecond)}
            </span>
          </div>
          <div className="mt-3 grid grid-cols-5 gap-1">
            {durationPresets.map((preset) => (
              <button
                key={preset}
                type="button"
                className={`border px-2 py-1.5 text-xs tabular-nums transition focus:outline-none focus:ring-2 focus:ring-neutral-300 ${
                  duration === preset
                    ? "border-neutral-100 bg-neutral-100 text-neutral-950"
                    : "border-neutral-800 text-neutral-300 hover:border-neutral-600"
                }`}
                onClick={() => {
                  setDuration(preset);
                  setDurationNotice(null);
                  if (draftStartSecond !== null) {
                    void checkSelectedAvailability(draftStartSecond, preset);
                  }
                }}
              >
                {preset}s
              </button>
            ))}
          </div>
          <label className="mt-3 grid gap-1 text-xs uppercase text-neutral-500">
            Duration
            <input
              className="border border-neutral-800 bg-neutral-900 px-3 py-2 text-sm normal-case tabular-nums text-neutral-100 outline-none focus:border-neutral-500"
              type="number"
              min={1}
              max={maxDuration}
              step={1}
              value={duration}
              onChange={(event) => updateDuration(Number(event.target.value))}
            />
          </label>
          <div className="mt-3 flex items-center justify-between gap-3 text-xs">
            <span className="uppercase text-neutral-500">Range</span>
            <span className="tabular-nums text-neutral-100">
              {formatArchiveSecond(selectedStartSecond)}-
              {formatArchiveSecond(selectedEndSecond)}
            </span>
          </div>
          <p className="mt-2 text-xs text-neutral-600">
            Maximum available duration from this second is {maxDuration}s.
          </p>
          {durationNotice ? (
            <p className="mt-2 text-xs text-neutral-400">{durationNotice}</p>
          ) : null}
          <div className="mt-3 grid grid-cols-[1fr_auto] gap-2">
            <button
              type="button"
              className="border border-neutral-700 px-3 py-2 text-xs uppercase text-neutral-100 transition hover:border-neutral-500 focus:outline-none focus:ring-2 focus:ring-neutral-300 disabled:text-neutral-600"
              onClick={reserve}
              disabled={status !== "available"}
            >
              {status === "checkingAvailability"
                ? "Checking"
                : status === "reserving"
                  ? "Reserving"
                  : "Reserve"}
            </button>
            <button
              type="button"
              className="border border-neutral-800 px-3 py-2 text-xs uppercase text-neutral-400 transition hover:border-neutral-600 focus:outline-none focus:ring-2 focus:ring-neutral-300"
              onClick={resetDraft}
            >
              Change
            </button>
          </div>
        </div>
      ) : null}
      {draftStartSecond !== null && maxDuration === null ? (
        <div className="grid gap-2">
          <p className="text-xs text-neutral-500">Checking available duration</p>
          {status === "error" ? (
            <button
              type="button"
              className="border border-neutral-800 px-3 py-2 text-xs uppercase text-neutral-400 transition hover:border-neutral-600 focus:outline-none focus:ring-2 focus:ring-neutral-300"
              onClick={resetDraft}
            >
              Change
            </button>
          ) : null}
        </div>
      ) : null}
      {status === "reserved" || status === "completing" ? (
        <button
          type="button"
          className="border border-neutral-700 px-4 py-2 text-xs uppercase text-neutral-100 transition hover:border-neutral-500 focus:outline-none focus:ring-2 focus:ring-neutral-300 disabled:text-neutral-600"
          onClick={complete}
          disabled={status === "completing"}
        >
          {status === "completing" ? "Completing" : "Complete local payment"}
        </button>
      ) : null}
      {checkoutUrl ? (
        <p className="truncate text-xs text-neutral-600">{checkoutUrl}</p>
      ) : null}
      {message ? (
        <p
          className={`text-xs ${
            status === "error" || status === "unavailable"
              ? "text-red-300"
              : "text-neutral-400"
          }`}
        >
          {message}
        </p>
      ) : null}
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
