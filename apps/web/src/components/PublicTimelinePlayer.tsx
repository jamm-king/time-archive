"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
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
import { fetchOwnedRanges, type OwnedRange } from "@/lib/owned-ranges";

type TimelineStatus = "loading" | "ready" | "empty" | "error";
type MediaStatus = "loading" | "ready" | "error";
type AuthStatus = "loading" | "guest" | "authenticated" | "error";

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
                  onTogglePanel={() => setAuthPanelOpen((value) => !value)}
                  onAuthenticated={(user) => {
                    setCurrentUser(user);
                    setAuthStatus("authenticated");
                    setAuthPanelOpen(false);
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
  onTogglePanel,
  onAuthenticated,
  onLogout,
}: {
  status: AuthStatus;
  currentUser: CurrentUser | null;
  panelOpen: boolean;
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
}: {
  currentUser: CurrentUser | null;
  onAuthenticated: (user: CurrentUser) => void;
  onLogout: () => Promise<void>;
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
        <OwnedRangesList currentUserId={currentUser.userId} />
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

function OwnedRangesList({ currentUserId }: { currentUserId: string }) {
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
  }, [currentUserId]);

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
        <ul className="mt-3 grid max-h-44 gap-2 overflow-y-auto pr-1">
          {ranges.map((range) => (
            <li
              key={range.ownershipRecordId}
              className="border border-neutral-800 px-3 py-2"
            >
              <div className="flex items-center justify-between gap-3">
                <span className="text-xs tabular-nums text-neutral-100">
                  {formatArchiveSecond(range.startSecond)}-
                  {formatArchiveSecond(range.endSecond)}
                </span>
                <span className="text-[10px] uppercase text-neutral-500">
                  {range.status}
                </span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
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
