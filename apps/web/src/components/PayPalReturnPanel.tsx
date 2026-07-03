"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { capturePayPalOrder, type PayPalCaptureResponse } from "@/lib/purchase";

type CaptureState =
  | { status: "idle" }
  | { status: "capturing" }
  | { status: "captured"; result: PayPalCaptureResponse }
  | { status: "error"; message: string };

export function PayPalReturnPanel() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token");
  const startedTokenRef = useRef<string | null>(null);
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

  return (
    <PaymentStatusShell title="Payment captured">
      <p>Waiting for provider confirmation.</p>
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
  tone?: "neutral" | "error";
  children: React.ReactNode;
}) {
  return (
    <main className="flex min-h-screen items-center justify-center bg-neutral-950 px-6 py-16 text-neutral-100">
      <section className="grid w-full max-w-sm gap-4 border border-neutral-800 p-5">
        <h1
          className={`text-sm uppercase ${
            tone === "error" ? "text-red-300" : "text-neutral-100"
          }`}
        >
          {title}
        </h1>
        <div className="grid gap-3 text-sm leading-6 text-neutral-400">{children}</div>
      </section>
    </main>
  );
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
