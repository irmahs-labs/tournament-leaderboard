package dev.irmahs.leaderboard.accounts;

/**
 * What Google tells us about someone at sign-in.
 *
 * @param sub Google's stable id for the account
 * @param email always one Google has verified
 */
public record GoogleProfile(String sub, String email, String name, String picture) {}
