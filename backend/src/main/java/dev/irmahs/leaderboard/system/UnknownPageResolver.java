package dev.irmahs.leaderboard.system;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * A browser opening an address nobody serves, such as {@code /nope}, gets the Angular app and its
 * Not found page, with a 404, instead of the API's JSON error. API calls, sign-in addresses and
 * files (an address with a dot) keep their JSON problem details.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class UnknownPageResolver implements HandlerExceptionResolver {

    private static final List<String> NOT_PAGES = List.of("/api/", "/actuator", "/oauth2/", "/login");

    @Override
    public ModelAndView resolveException(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        if (!(exception instanceof NoResourceFoundException) || !isPage(request)) {
            return null;
        }
        ModelAndView page = new ModelAndView(SpaController.APP);
        page.setStatus(HttpStatus.NOT_FOUND);
        return page;
    }

    /** A page a browser asked for: a GET wanting HTML, outside the API, not a file. */
    static boolean isPage(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        String last = path.substring(path.lastIndexOf('/') + 1);
        return "GET".equals(request.getMethod())
                && accept != null
                && accept.contains(MediaType.TEXT_HTML_VALUE)
                && NOT_PAGES.stream().noneMatch(path::startsWith)
                && !last.contains(".");
    }
}
