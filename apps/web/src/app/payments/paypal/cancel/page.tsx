import Link from "next/link";

export default function PayPalCancelPage() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-neutral-950 px-6 py-16 text-neutral-100">
      <section className="grid w-full max-w-sm gap-4 border border-neutral-800 p-5">
        <h1 className="text-sm uppercase text-neutral-100">Payment cancelled</h1>
        <p className="text-sm leading-6 text-neutral-400">No ownership was created.</p>
        <Link
          className="inline-flex w-fit border border-neutral-700 px-3 py-2 text-xs uppercase text-neutral-100 transition hover:border-neutral-500 focus:outline-none focus:ring-2 focus:ring-neutral-300"
          href="/"
        >
          Return to timeline
        </Link>
      </section>
    </main>
  );
}
