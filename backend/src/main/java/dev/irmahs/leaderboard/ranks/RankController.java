package dev.irmahs.leaderboard.ranks;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Valorant ranks, lowest first, for the rank picker. The same for every organisation. */
@RestController
public class RankController {

    private final RankRepository ranks;

    RankController(RankRepository ranks) {
        this.ranks = ranks;
    }

    @GetMapping("/api/ranks")
    @Transactional(readOnly = true)
    public List<RankView> ranks() {
        return ranks.findAllByOrderByRankValueAscIdAsc().stream()
                .map(RankView::of)
                .toList();
    }

    public record RankView(int id, String name, String color) {

        public static RankView of(Rank rank) {
            return new RankView(rank.getId(), rank.getRankName(), rank.getColor());
        }
    }
}
