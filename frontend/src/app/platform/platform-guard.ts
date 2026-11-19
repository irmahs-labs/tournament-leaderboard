import { inject } from "@angular/core";
import type { CanActivateFn } from "@angular/router";
import { Router } from "@angular/router";

import { SessionService } from "../shared/session";

/**
 * Lets platform admins into the platform pages. Anyone signed out is sent to sign in and brought
 * back; anyone else sees Not found. The API checks every request again.
 */
export const platformGuard: CanActivateFn = async (_route, state) => {
  const router = inject(Router);

  const me = await inject(SessionService)
    .known()
    .catch(() => null);

  if (!me) {
    return router.createUrlTree(["/sign-in"], {
      queryParams: { from: state.url },
    });
  }

  return me.platformAdmin ? true : router.createUrlTree(["/not-found"]);
};
