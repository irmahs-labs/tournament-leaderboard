package dev.irmahs.leaderboard.tenancy;

import dev.irmahs.leaderboard.organizations.Organization;
import dev.irmahs.leaderboard.organizations.OrganizationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts a request for /api/orgs/{slug}/… in that organisation's scope. An unknown or suspended
 * slug gets no scope: the controller answers 404 for it anyway.
 */
@Component
class OrganizationScopeFilter extends OncePerRequestFilter {

    private static final Pattern ORGANIZATION_ADDRESS = Pattern.compile("^/api/orgs/([^/]+)(/.*)?$");

    private final OrganizationService organizations;

    OrganizationScopeFilter(OrganizationService organizations) {
        this.organizations = organizations;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Matcher address = ORGANIZATION_ADDRESS.matcher(request.getRequestURI());
        if (address.matches()) {
            organizations.findActive(address.group(1)).map(Organization::getId).ifPresent(OrganizationScope::enter);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            OrganizationScope.leave();
        }
    }
}
