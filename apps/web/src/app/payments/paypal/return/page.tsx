import { Suspense } from "react";
import { PayPalReturnPanel } from "@/components/PayPalReturnPanel";

export default function PayPalReturnPage() {
  return (
    <Suspense fallback={<PaymentReturnFallback />}>
      <PayPalReturnPanel />
    </Suspense>
  );
}

function PaymentReturnFallback() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-neutral-950 px-6 py-16 text-neutral-100">
      <section className="grid w-full max-w-sm gap-4 border border-neutral-800 p-5">
        <h1 className="text-sm uppercase text-neutral-100">Confirming approval</h1>
        <p className="text-sm leading-6 text-neutral-400">Finalizing PayPal approval.</p>
      </section>
    </main>
  );
}
