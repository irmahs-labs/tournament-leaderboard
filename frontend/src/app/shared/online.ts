import { DestroyRef, Injectable, inject, signal } from "@angular/core";

/** The event `GET /api/online` sends, with the count as its data. */
const EVENT = "online";

/**
 * How many pages of the site are open right now, for the header. The server counts the open
 * streams and sends the number whenever it changes; nothing about the visitor is stored. Nothing
 * while the count hasn't arrived, or when the stream has closed for good.
 */
@Injectable({ providedIn: "root" })
export class OnlineService {
  readonly #count = signal<number | null>(null);

  readonly count = this.#count.asReadonly();

  constructor() {
    // Not in every environment, such as the tests' DOM.
    if (typeof EventSource === "undefined") {
      return;
    }

    const source = new EventSource("/api/online");

    source.addEventListener(EVENT, (event) => {
      const count = Number(event.data);

      if (Number.isInteger(count) && count > 0) {
        this.#count.set(count);
      }
    });
    // The browser reconnects by itself; the count only goes when it gives up.
    source.addEventListener("error", () => {
      if (source.readyState === EventSource.CLOSED) {
        this.#count.set(null);
      }
    });
    inject(DestroyRef).onDestroy(() => source.close());
  }
}
