export const BASE_DURATION_PRESETS = [1, 5, 10, 15, 30] as const;

const SHORT_DURATION_PRESETS = [1, 5, 10, 15] as const;

export function buildDurationPresets(maxDuration: number): number[] {
  const safeMaxDuration = Math.floor(maxDuration);
  if (safeMaxDuration < 1) {
    return [];
  }

  const presets: number[] = BASE_DURATION_PRESETS.filter(
    (preset) => preset <= safeMaxDuration,
  );

  if (
    safeMaxDuration < 30 &&
    !SHORT_DURATION_PRESETS.includes(
      safeMaxDuration as (typeof SHORT_DURATION_PRESETS)[number],
    )
  ) {
    presets.push(safeMaxDuration);
  }

  return Array.from(new Set(presets)).sort((a, b) => a - b);
}

export function clampDuration(
  duration: number,
  maxDuration: number,
): { duration: number; wasClamped: boolean } {
  const safeMaxDuration = Math.max(0, Math.floor(maxDuration));
  const safeDuration = Number.isFinite(duration) ? Math.floor(duration) : 1;

  if (safeMaxDuration < 1) {
    return { duration: 0, wasClamped: safeDuration !== 0 };
  }

  const clampedDuration = Math.max(1, Math.min(safeDuration, safeMaxDuration));

  return {
    duration: clampedDuration,
    wasClamped: clampedDuration !== safeDuration,
  };
}
