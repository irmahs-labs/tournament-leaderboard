import { cookies } from 'next/headers';
import { DEFAULT_LOCALE, LOCALE_COOKIE, type Locale } from './dictionary';

/**
 * Reads the visitor's chosen language from the `lang` cookie. Defaults to English.
 * Server-only (uses `next/headers`) — import `dictionary.ts` directly from client
 * components instead, so they don't pull this in.
 */
export async function getLocale(): Promise<Locale> {
  const store = await cookies();
  const value = store.get(LOCALE_COOKIE)?.value;
  return value === 'fr' ? 'fr' : DEFAULT_LOCALE;
}
