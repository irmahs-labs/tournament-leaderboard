package dev.irmahs.leaderboard.accounts;

import dev.irmahs.leaderboard.members.MemberRole;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Who gets access without being invited: the platform admins, and the first members of an
 * organisation. Set on the server, never in the repository, since they are people's emails.
 *
 * @param platformAdmins Google emails, in any letter case
 * @param initialMembers each as {@code slug:OWNER:email} or {@code slug:ADMIN:email}
 */
@ConfigurationProperties("leaderboard.access")
public record AccessProperties(List<String> platformAdmins, List<String> initialMembers) {

    public AccessProperties {
        platformAdmins = platformAdmins == null
                ? List.of()
                : platformAdmins.stream()
                        .map(String::strip)
                        .filter(email -> !email.isEmpty())
                        .toList();
        initialMembers = initialMembers == null
                ? List.of()
                : initialMembers.stream()
                        .map(String::strip)
                        .filter(entry -> !entry.isEmpty())
                        .toList();
        initialMembers.forEach(InitialMember::parse);
    }

    boolean isPlatformAdmin(String email) {
        return platformAdmins.stream().anyMatch(admin -> admin.equalsIgnoreCase(email));
    }

    /** The organisations this email should be a member of, from the configuration. */
    List<InitialMember> initialMembershipsOf(String email) {
        return initialMembers.stream()
                .map(InitialMember::parse)
                .filter(member -> member.email().equalsIgnoreCase(email))
                .toList();
    }

    /**
     * @param owner whether the entry said OWNER; an owner is always an admin
     */
    record InitialMember(String slug, boolean owner, String email) {

        static InitialMember parse(String entry) {
            String[] parts = entry.split(":", 3);
            if (parts.length != 3 || parts[0].isBlank() || parts[2].isBlank()) {
                throw new IllegalArgumentException("leaderboard.access.initial-members: \"" + entry
                        + "\" isn't slug:OWNER:email or slug:ADMIN:email");
            }
            String role = parts[1].strip().toUpperCase(Locale.ROOT);
            if (!role.equals("OWNER") && !role.equals(MemberRole.ADMIN.name())) {
                throw new IllegalArgumentException(
                        "leaderboard.access.initial-members: \"" + entry + "\" must say OWNER or ADMIN");
            }
            return new InitialMember(parts[0].strip(), role.equals("OWNER"), parts[2].strip());
        }
    }
}
