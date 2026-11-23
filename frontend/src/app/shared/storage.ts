/**
 * The browser's storage, which can be missing or blocked (private windows, strict privacy
 * settings). Reading then gives nothing, and writing is skipped: a pick lasts until the page
 * is closed.
 */
type Area = "localStorage" | "sessionStorage";

export const readStored = (
  key: string,
  area: Area = "localStorage"
): string | null => {
  try {
    return globalThis[area]?.getItem(key) ?? null;
  } catch {
    return null;
  }
};

/** Stores a value, or forgets the key when the value is null. */
export const store = (
  key: string,
  value: string | null,
  area: Area = "localStorage"
): void => {
  try {
    if (value === null) {
      globalThis[area]?.removeItem(key);
    } else {
      globalThis[area]?.setItem(key, value);
    }
  } catch {
    // Blocked storage: nothing to remember the value in.
  }
};
