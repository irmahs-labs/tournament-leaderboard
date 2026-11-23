package dev.irmahs.leaderboard.team;

/** Whether an invite can still be used, and if not, why. */
public enum InviteState {
    VALID,
    EXPIRED,
    USED,
    REVOKED
}
