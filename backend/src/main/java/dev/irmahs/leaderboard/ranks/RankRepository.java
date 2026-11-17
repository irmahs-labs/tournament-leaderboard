package dev.irmahs.leaderboard.ranks;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RankRepository extends JpaRepository<Rank, Integer> {

    List<Rank> findAllByOrderByRankValueAscIdAsc();
}
