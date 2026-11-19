import { inject } from "@angular/core";
import type { CanActivateFn } from "@angular/router";
import { Router } from "@angular/router";

import { canAdmin, SessionService } from "../shared/session";

/**
 * Lets an organisation's admins, and platform admins, into its admin pages. Anyone signed out is
 * sent to sign in and brought back; anyone else goes to the organisation's public pages. The API
 * checks every request again: this only spares people pages they can't use.
 */
export const adminGuard: CanActivateFn = async (route, state) => {
  const router = inject(Router);
  const slug = route.parent?.paramMap.get("slug") ?? "";

  const me = await inject(SessionService)
    .known()
    .catch(() => null);

  if (!me) {
    return router.createUrlTree(["/sign-in"], {
      queryParams: { from: state.url },
    });
  }

  return canAdmin(me, slug) ? true : router.createUrlTree(["/org", slug]);
};
