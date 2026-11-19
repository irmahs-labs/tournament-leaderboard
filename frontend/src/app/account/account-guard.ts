import { inject } from "@angular/core";
import type { CanActivateFn } from "@angular/router";
import { Router } from "@angular/router";

import { SessionService } from "../shared/session";

/** My account is for whoever is signed in; anyone else signs in first and comes back. */
export const accountGuard: CanActivateFn = async (_route, state) => {
  // Taken before waiting: `inject` only works before the first `await`.
  const router = inject(Router);

  const me = await inject(SessionService)
    .known()
    .catch(() => null);

  return me
    ? true
    : router.createUrlTree(["/sign-in"], {
        queryParams: { from: state.url },
      });
};
