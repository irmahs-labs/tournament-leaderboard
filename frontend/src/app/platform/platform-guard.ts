import { inject } from "@angular/core";
import type { CanActivateFn } from "@angular/router";
import { Router, UrlTree } from "@angular/router";

import { meOrSignIn } from "../shared/session";

/**
 * Lets platform admins into the platform pages. Anyone signed out is sent to sign in and brought
 * back; anyone else sees Not found. The API checks every request again.
 */
export const platformGuard: CanActivateFn = async (_route, state) => {
  const router = inject(Router);
  const me = await meOrSignIn(state.url);

  if (me instanceof UrlTree) {
    return me;
  }

  return me.platformAdmin ? true : router.createUrlTree(["/not-found"]);
};
