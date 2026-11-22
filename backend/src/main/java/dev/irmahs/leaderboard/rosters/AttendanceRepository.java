package dev.irmahs.leaderboard.rosters;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AttendanceRepository extends JpaRepository<Attendance, Attendance.Key> {

    Optional<Attendance> findByMatchIdAndPlayerId(Integer matchId, UUID playerId);

    List<Attendance> findByMatchId(Integer matchId);

    List<Attendance> findByMatchIdIn(Collection<Integer> matchIds);

    long countByMatchIdAndStatus(Integer matchId, AttendanceStatus status);
}
