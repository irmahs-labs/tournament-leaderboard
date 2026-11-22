/** A season not over yet, as a member sees it. */
export interface MySeason {
  number: number;
  name: string;
  startsOn: string | null;
  endsOn: string | null;
  /** Whether they can sign up themselves: sign-ups are open. */
  takesSignUps: boolean;
  signedUp: boolean;
}

/** `GET /api/orgs/{slug}/me/seasons`. */
export interface MySeasons {
  /** Whether they have a player profile here, needed to sign up. */
  player: boolean;
  seasons: MySeason[];
}

/** Someone signed up for a season. */
export interface SignUp {
  playerId: string;
  riotId: string | null;
  /** Whether the player is linked to an account, so answers matches themselves. */
  account: boolean;
  registeredAt: string;
  byAdmin: boolean;
  /** The admin who signed them up, while that admin has an account. */
  addedBy: string | null;
}

/** `GET …/admin/seasons/{n}/registrations`: who's signed up, in the order they came. */
export interface SignUps {
  number: number;
  name: string;
  finished: boolean;
  /** Whether players can sign up themselves. */
  open: boolean;
  registrations: SignUp[];
}

/** An upcoming match of a season they're signed up for. */
export interface MyMatch {
  id: number;
  seasonNumber: number;
  seasonName: string;
  number: number;
  date: string | null;
  startTime: string | null;
  location: string | null;
  replyBy: string | null;
  places: number;
  confirmed: number;
  cancelled: boolean;
  locked: boolean;
  /** Their answer; null until they answer. */
  status: "CONFIRMED" | "WAITING" | "DECLINED" | null;
  /** Their place on the waiting list, from 1. */
  position: number | null;
}

/** `GET /api/orgs/{slug}/me/matches`. */
export interface MyMatches {
  matches: MyMatch[];
}
