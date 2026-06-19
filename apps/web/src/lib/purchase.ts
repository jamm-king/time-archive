import { fetchCsrfToken } from "@/lib/auth";
import { ARCHIVE_TOTAL_SECONDS } from "@/lib/timeline";

export type AvailabilityResponse = {
  startSecond: number;
  endSecond: number;
  available: boolean;
  conflicts: Array<{
    type: string;
    startSecond: number;
    endSecond: number;
  }>;
};

export type ReservationResponse = {
  reservationId: string;
  buyerId: string;
  startSecond: number;
  endSecond: number;
  amountCents: number;
  currency: string;
  status: string;
  expiresAt: string;
};

export type CheckoutSessionResponse = {
  provider: string;
  providerReference: string;
  checkoutUrl: string;
};

export type FakePaymentCompletionResponse = {
  purchaseId: string;
  ownershipRecordId: string | null;
  alreadyProcessed: boolean;
};

export async function checkAvailability(
  startSecond: number,
  endSecond: number,
): Promise<AvailabilityResponse> {
  const url = new URL("/api/archive/availability", window.location.origin);
  url.searchParams.set("startSecond", startSecond.toString());
  url.searchParams.set("endSecond", endSecond.toString());

  const response = await fetch(url, {
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  return parseAvailabilityResponse(await response.json());
}

export async function findMaxAvailableDuration(
  startSecond: number,
): Promise<number> {
  const maxCandidate = ARCHIVE_TOTAL_SECONDS - startSecond;
  if (maxCandidate < 1) {
    return 0;
  }

  let low = 0;
  let high = maxCandidate;

  while (low < high) {
    const candidate = Math.ceil((low + high) / 2);
    const availability = await checkAvailability(
      startSecond,
      startSecond + candidate,
    );

    if (availability.available) {
      low = candidate;
    } else {
      high = candidate - 1;
    }
  }

  return low;
}

export async function reserveTimeRange(
  startSecond: number,
  endSecond: number,
): Promise<ReservationResponse> {
  const csrfToken = await fetchCsrfToken();
  const response = await fetch("/api/purchase/reservations", {
    method: "POST",
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": csrfToken,
    },
    body: JSON.stringify({
      startSecond,
      endSecond,
    }),
  });

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  return parseReservationResponse(await response.json());
}

export async function createCheckout(
  reservationId: string,
): Promise<CheckoutSessionResponse> {
  const csrfToken = await fetchCsrfToken();
  const response = await fetch(`/api/purchase/reservations/${reservationId}/checkout`, {
    method: "POST",
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
      "X-XSRF-TOKEN": csrfToken,
    },
  });

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  return parseCheckoutSessionResponse(await response.json());
}

export async function completeFakePrimaryPurchase(
  reservationId: string,
): Promise<FakePaymentCompletionResponse> {
  const csrfToken = await fetchCsrfToken();
  const requestId = crypto.randomUUID();
  const response = await fetch(
    "/api/internal/payments/fake/webhooks/primary-purchase-completed",
    {
      method: "POST",
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": csrfToken,
      },
      body: JSON.stringify({
        providerEventId: `evt_web_${requestId}`,
        eventType: "payment_intent.succeeded",
        payloadHash: `sha256-web-${requestId}`,
        reservationId,
        paymentReference: `pi_web_${requestId}`,
        requestId: `web-${requestId}`,
      }),
    },
  );

  if (!response.ok) {
    throw new Error(await getErrorMessage(response));
  }

  return parseFakePaymentCompletionResponse(await response.json());
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

function parseAvailabilityResponse(value: unknown): AvailabilityResponse {
  if (!isRecord(value)) {
    throw new Error("Availability response must be an object");
  }

  const { startSecond, endSecond, available, conflicts } = value;
  if (
    typeof startSecond !== "number" ||
    typeof endSecond !== "number" ||
    typeof available !== "boolean" ||
    !Array.isArray(conflicts)
  ) {
    throw new Error("Availability response has an invalid shape");
  }

  return {
    startSecond,
    endSecond,
    available,
    conflicts: conflicts.map(parseAvailabilityConflict),
  };
}

function parseAvailabilityConflict(value: unknown): AvailabilityResponse["conflicts"][number] {
  if (!isRecord(value)) {
    throw new Error("Availability conflict must be an object");
  }

  const { type, startSecond, endSecond } = value;
  if (
    typeof type !== "string" ||
    typeof startSecond !== "number" ||
    typeof endSecond !== "number"
  ) {
    throw new Error("Availability conflict has an invalid shape");
  }

  return {
    type,
    startSecond,
    endSecond,
  };
}

function parseReservationResponse(value: unknown): ReservationResponse {
  if (!isRecord(value)) {
    throw new Error("Reservation response must be an object");
  }

  const {
    reservationId,
    buyerId,
    startSecond,
    endSecond,
    amountCents,
    currency,
    status,
    expiresAt,
  } = value;

  if (
    typeof reservationId !== "string" ||
    typeof buyerId !== "string" ||
    typeof startSecond !== "number" ||
    typeof endSecond !== "number" ||
    typeof amountCents !== "number" ||
    typeof currency !== "string" ||
    typeof status !== "string" ||
    typeof expiresAt !== "string"
  ) {
    throw new Error("Reservation response has an invalid shape");
  }

  return {
    reservationId,
    buyerId,
    startSecond,
    endSecond,
    amountCents,
    currency,
    status,
    expiresAt,
  };
}

function parseCheckoutSessionResponse(value: unknown): CheckoutSessionResponse {
  if (!isRecord(value)) {
    throw new Error("Checkout session response must be an object");
  }

  const { provider, providerReference, checkoutUrl } = value;
  if (
    typeof provider !== "string" ||
    typeof providerReference !== "string" ||
    typeof checkoutUrl !== "string"
  ) {
    throw new Error("Checkout session response has an invalid shape");
  }

  return {
    provider,
    providerReference,
    checkoutUrl,
  };
}

function parseFakePaymentCompletionResponse(
  value: unknown,
): FakePaymentCompletionResponse {
  if (!isRecord(value)) {
    throw new Error("Payment completion response must be an object");
  }

  const { purchaseId, ownershipRecordId, alreadyProcessed } = value;
  if (
    typeof purchaseId !== "string" ||
    !(typeof ownershipRecordId === "string" || ownershipRecordId === null) ||
    typeof alreadyProcessed !== "boolean"
  ) {
    throw new Error("Payment completion response has an invalid shape");
  }

  return {
    purchaseId,
    ownershipRecordId,
    alreadyProcessed,
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
