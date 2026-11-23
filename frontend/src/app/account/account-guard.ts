import type { CanActivateFn } from "@angular/router";
import { UrlTree } from "@angular/router";

import { meOrSignIn } from "../shared/session";

/** My account is for whoever is signed in; anyone else signs in first and comes back. */
export const accountGuard: CanActivateFn = async (_route, state) => {
  const me = await meOrSignIn(state.url);

  return me instanceof UrlTree ? me : true;
};
