/** An upcoming match of a season under way, with the member's answer. */
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
  /** Whether they have a player profile here, needed to answer. */
  player: boolean;
  matches: MyMatch[];
}
