"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import {
  capturePayPalOrder,
  getPayPalOrderConfirmationStatus,
  type PayPalCaptureResponse,
  type PayPalOrderConfirmationStatusResponse,
} from "@/lib/purchase";

type CaptureState =
  | { status: "idle" }
  | { status: "capturing" }
  | { status: "captured"; result: PayPalCaptureResponse }
  | {
      status: "confirmed";
      result: PayPalCaptureResponse;
      confirmation: PayPalOrderConfirmationStatusResponse;
    }
  | {
      status: "delayed";
      result: PayPalCaptureResponse;
      confirmation: PayPalOrderConfirmationStatusResponse | null;
    }
  | { status: "error"; message: string };

const CONFIRMATION_POLL_INTERVAL_MS = 2000;
const CONFIRMATION_POLL_ATTEMPTS = 12;

export function PayPalReturnPanel() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token");
  const startedTokenRef = useRef<string | null>(null);
  const pollingTokenRef = useRef<string | null>(null);
  const [state, setState] = useState<CaptureState>(
    token ? { status: "capturing" } : { status: "idle" },
  );

  useEffect(() => {
    if (!token || startedTokenRef.current === token) {
      return;
    }

    let cancelled = false;
    startedTokenRef.current = token;

    capturePayPalOrder(token)
      .then((result) => {
        if (!cancelled) {
          setState({ status: "captured", result });
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setState({
            status: "error",
            message: error instanceof Error ? error.message : "Payment capture failed",
          });
        }
      });

    return () => {
      cancelled = true;
    };
  }, [token]);

  useEffect(() => {
    if (state.status !== "captured" || pollingTokenRef.current === state.result.orderId) {
      return;
    }

    let cancelled = false;
    let timeoutId: ReturnType<typeof setTimeout> | null = null;
    pollingTokenRef.current = state.result.orderId;

    const poll = async (remainingAttempts: number) => {
      try {
        const confirmation = await getPayPalOrderConfirmationStatus(state.result.orderId);
        if (cancelled) {
          return;
        }

        if (confirmation.status === "OWNERSHIP_GRANTED") {
          setState({
            status: "confirmed",
            result: state.result,
            confirmation,
          });
          return;
        }

        if (confirmation.terminal) {
          setState({
            status: "error",
            message: confirmationStatusMessage(confirmation.status),
          });
          return;
        }

        if (remainingAttempts <= 1) {
          setState({
            status: "delayed",
            result: state.result,
            confirmation,
          });
          return;
        }

        timeoutId = setTimeout(
          () => poll(remainingAttempts - 1),
          CONFIRMATION_POLL_INTERVAL_MS,
        );
      } catch {
        if (cancelled) {
          return;
        }
        if (remainingAttempts <= 1) {
          setState({
            status: "delayed",
            result: state.result,
            confirmation: null,
          });
          return;
        }
        timeoutId = setTimeout(
          () => poll(remainingAttempts - 1),
          CONFIRMATION_POLL_INTERVAL_MS,
        );
      }
    };

    poll(CONFIRMATION_POLL_ATTEMPTS);

    return () => {
      cancelled = true;
      if (timeoutId) {
        clearTimeout(timeoutId);
      }
    };
  }, [state]);

  if (!token) {
    return (
      <PaymentStatusShell title="Payment review" tone="error">
        <p>Missing PayPal order token.</p>
        <TimelineLink />
      </PaymentStatusShell>
    );
  }

  if (state.status === "capturing" || state.status === "idle") {
    return (
      <PaymentStatusShell title="Confirming approval">
        <p>Finalizing PayPal approval.</p>
      </PaymentStatusShell>
    );
  }

  if (state.status === "error") {
    return (
      <PaymentStatusShell title="Payment capture failed" tone="error">
        <p>{state.message}</p>
        <TimelineLink />
      </PaymentStatusShell>
    );
  }

  if (state.status === "confirmed") {
    return (
      <PaymentStatusShell title="Payment confirmed" tone="success">
        <p>Your second is now owned and ready for media upload.</p>
        <p className="break-all text-neutral-500">
          Ownership {state.confirmation.ownershipRecordId}
        </p>
        <TimelineLink />
      </PaymentStatusShell>
    );
  }

  if (state.status === "delayed") {
    return (
      <PaymentStatusShell title="Confirmation delayed">
        <p>
          PayPal captured the payment, but ownership confirmation is still
          processing. Return to the timeline in a moment to check your owned
          seconds.
        </p>
        <p className="break-all text-neutral-500">
          Capture {state.result.captureReference}
        </p>
        <TimelineLink />
      </PaymentStatusShell>
    );
  }

  return (
    <PaymentStatusShell title="Payment captured">
      <p>Waiting for verified provider confirmation.</p>
      <p className="break-all text-neutral-500">
        Capture {state.result.captureReference}
      </p>
      <TimelineLink />
    </PaymentStatusShell>
  );
}

function PaymentStatusShell({
  title,
  tone = "neutral",
  children,
}: {
  title: string;
  tone?: "neutral" | "error" | "success";
  children: React.ReactNode;
}) {
  return (
    <main className="flex min-h-screen items-center justify-center bg-neutral-950 px-6 py-16 text-neutral-100">
      <section className="grid w-full max-w-sm gap-4 border border-neutral-800 p-5">
        <h1
          className={`text-sm uppercase ${
            tone === "error"
              ? "text-red-300"
              : tone === "success"
                ? "text-emerald-300"
                : "text-neutral-100"
          }`}
        >
          {title}
        </h1>
        <div className="grid gap-3 text-sm leading-6 text-neutral-400">{children}</div>
      </section>
    </main>
  );
}

function confirmationStatusMessage(
  status: PayPalOrderConfirmationStatusResponse["status"],
): string {
  switch (status) {
    case "CAPTURE_FAILED":
      return "PayPal capture failed. The purchase was not completed.";
    case "EXPIRED":
      return "The reservation expired before confirmation completed.";
    case "FAILED":
      return "Payment confirmation failed. The purchase was not completed.";
    case "CAPTURE_NOT_STARTED":
    case "CAPTURE_PENDING_WEBHOOK":
    case "OWNERSHIP_GRANTED":
      return "Payment confirmation did not complete.";
  }
}

function TimelineLink() {
  return (
    <Link
      className="mt-2 inline-flex w-fit border border-neutral-700 px-3 py-2 text-xs uppercase text-neutral-100 transition hover:border-neutral-500 focus:outline-none focus:ring-2 focus:ring-neutral-300"
      href="/"
    >
      Return to timeline
    </Link>
  );
}
