/** Where a player stands with a season. */
export type RegistrationStatus = "REGISTERED" | "WITHDRAWN" | "REMOVED";

/** A season not over yet, as a member sees it. */
export interface MySeason {
  number: number;
  name: string;
  startsOn: string | null;
  endsOn: string | null;
  /** Whether they can sign up themselves today. */
  takesSignUps: boolean;
  /** The last day to sign up; nothing: until an admin closes sign-ups. */
  signUpClosesOn: string | null;
  /** Theirs; nothing when they never signed up. */
  status: RegistrationStatus | null;
}

/** `GET /api/orgs/{slug}/me/seasons`. */
export interface MySeasons {
  /** Whether they have a player profile here, needed to sign up. */
  player: boolean;
  seasons: MySeason[];
}

/** Someone signed up for a season, or who was. */
export interface SignUp {
  playerId: string;
  riotId: string | null;
  /** Whether the player is linked to an account, so answers matches themselves. */
  account: boolean;
  status: RegistrationStatus;
  registeredAt: string;
  byAdmin: boolean;
  /** The admin who signed them up, while that admin has an account. */
  addedBy: string | null;
}

/** `GET …/admin/seasons/{n}/registrations`: a season's sign-ups, those signed up first. */
export interface SignUps {
  number: number;
  name: string;
  finished: boolean;
  /** The admins' switch. */
  open: boolean;
  closesOn: string | null;
  /** Whether players can sign up themselves today. */
  takesSignUps: boolean;
  registrations: SignUp[];
}
