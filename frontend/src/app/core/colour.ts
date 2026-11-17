/** Colour arithmetic for the organisation accent: WCAG contrast and mixing, on #RRGGBB values. */

const HEX = /^#[0-9a-f]{6}$/iu;

/** The template's red, used when an organisation has no accent colour of its own. */
export const DEFAULT_ACCENT = "#F0484E";

const channels = (hex: string): [number, number, number] => [
  Number.parseInt(hex.slice(1, 3), 16),
  Number.parseInt(hex.slice(3, 5), 16),
  Number.parseInt(hex.slice(5, 7), 16),
];

const luminance = (hex: string): number => {
  const [r, g, b] = channels(hex).map((channel) => {
    const c = channel / 255;

    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });

  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};

/** The WCAG contrast ratio of two colours, from 1 to 21. */
export const contrast = (a: string, b: string): number => {
  const x = luminance(a);
  const y = luminance(b);

  return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
};

/** The colour `amount` of the way from `from` to `to`, 0 to 1. */
export const mix = (from: string, to: string, amount: number): string => {
  const a = channels(from);
  const b = channels(to);

  return `#${a
    .map((value, i) =>
      Math.round(value + (b[i] - value) * amount)
        .toString(16)
        .padStart(2, "0")
    )
    .join("")}`;
};

/** The organisation's accent, or the template's red when it has none or it isn't #RRGGBB. */
export const accentOrDefault = (accent: string | null | undefined): string =>
  accent && HEX.test(accent) ? accent : DEFAULT_ACCENT;

/** White or near-black, whichever reads better on the accent. */
export const onAccent = (accent: string): string =>
  contrast("#ffffff", accent) >= contrast("#0F141B", accent)
    ? "#ffffff"
    : "#0F141B";

/**
 * The accent darkened or lightened towards the text colour until it reads at 4.5:1 on the
 * surface, for accent-coloured text such as totals and eyebrows.
 */
export const readableAccent = (
  accent: string,
  surface: string,
  text: string
): string => {
  let colour = accent;

  for (let step = 1; step <= 10 && contrast(colour, surface) < 4.5; step += 1) {
    colour = mix(accent, text, step / 10);
  }

  return colour;
};

/**
 * A rank's colour made readable as text on the pills' surface, in each theme: the same mixing as
 * the accent, against `--surface-2`.
 */
export const rankText = (
  colour: string | null | undefined
): { dark: string; light: string } | null =>
  colour && /^#[\da-f]{6}$/iu.test(colour)
    ? {
        dark: readableAccent(colour, "#1e2733", "#ece8e1"),
        light: readableAccent(colour, "#e2ddd3", "#0f141b"),
      }
    : null;
