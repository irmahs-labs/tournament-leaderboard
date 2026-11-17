package dev.irmahs.leaderboard.system;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Angular app for the addresses its router owns, so a link or a reload on one of them
 * opens the page instead of a server 404. Addresses with a dot, such as {@code main.js}, are files.
 */
@Controller
public class SpaController {

    static final String APP = "forward:/index.html";

    @GetMapping({"/o/*", "/o/*/**", "/not-found", "/sign-in"})
    public String app() {
        return APP;
    }
}
