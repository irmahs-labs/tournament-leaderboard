import { HttpErrorResponse } from "@angular/common/http";
import { signal } from "@angular/core";

/** What the last change did, for an admin page's status line. */
export interface Outcome {
  ok: boolean;
  text: string;
}

/**
 * One change at a time on an admin page: `busy` while it's on its way, then `outcome` says how it
 * went. A failure is explained from the answer's status, in the language on screen.
 */
export class AdminAction {
  readonly busy = signal(false);
  readonly outcome = signal<Outcome | null>(null);

  readonly #explain: (status: number) => string;

  /** @param explain words for a failure, from its HTTP status (0 when the request never got an answer) */
  constructor(explain: (status: number) => string) {
    this.#explain = explain;
  }

  /** Runs a change unless one is already running; `request` returns what to say when it worked. */
  async run(request: () => Promise<string>): Promise<boolean> {
    if (this.busy()) {
      return false;
    }

    this.busy.set(true);
    this.outcome.set(null);

    try {
      this.outcome.set({ ok: true, text: await request() });

      return true;
    } catch (error) {
      const status = error instanceof HttpErrorResponse ? error.status : 0;
      this.outcome.set({ ok: false, text: this.#explain(status) });

      return false;
    } finally {
      this.busy.set(false);
    }
  }

  /** Says what's wrong before anything is sent. */
  refuse(text: string): void {
    this.outcome.set({ ok: false, text });
  }
}
