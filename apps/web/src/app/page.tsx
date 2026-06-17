export default function Home() {
  return (
    <main className="min-h-dvh bg-neutral-950 text-neutral-50">
      <section className="grid min-h-dvh grid-rows-[1fr_auto]">
        <div className="relative flex items-center justify-center overflow-hidden bg-neutral-950">
          <div className="relative flex h-full w-full max-w-6xl flex-col justify-between px-5 py-5 sm:px-8 sm:py-7">
            <header className="flex items-center justify-between text-xs uppercase text-neutral-400">
              <span>Time Archive</span>
              <span>00:00:00</span>
            </header>

            <div className="mx-auto flex w-full max-w-3xl flex-1 items-center justify-center text-center">
              <div>
                <p className="text-sm uppercase text-neutral-500">Public timeline</p>
                <h1 className="mt-4 text-balance text-4xl font-semibold text-neutral-50 sm:text-6xl">
                  Time Archive
                </h1>
              </div>
            </div>

            <footer className="grid gap-3 text-xs text-neutral-500 sm:grid-cols-[1fr_auto] sm:items-end">
              <div>
                <div className="h-1 overflow-hidden bg-neutral-800">
                  <div className="h-full w-0 bg-neutral-100" />
                </div>
                <div className="mt-2 flex justify-between tabular-nums">
                  <span>00:00:00</span>
                  <span>24:00:00</span>
                </div>
              </div>
              <span className="text-right">Waiting for approved media</span>
            </footer>
          </div>
        </div>
      </section>
    </main>
  );
}
