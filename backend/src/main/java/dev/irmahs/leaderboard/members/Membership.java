package dev.irmahs.leaderboard.members;

/**
 * An organisation someone belongs to, and what they can do there.
 *
 * @param owner whether they own it; an owner is always an admin
 */
public record Membership(String slug, String name, MemberRole role, boolean owner) {}
