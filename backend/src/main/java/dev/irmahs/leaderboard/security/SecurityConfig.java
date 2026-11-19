package dev.irmahs.leaderboard.security;

import dev.irmahs.leaderboard.accounts.AccessProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Who can reach what. Sign-in is with Google only, and the session lives in a cookie.
 *
 * <ul>
 *   <li>Public: the result pages' API, the version and the health check. Nobody needs to sign
 *       in to read results.
 *   <li>Every other /api address needs someone signed in, and answers 401 otherwise. An
 *       organisation's /admin addresses also need its admin: see {@link OrgAccess}.
 *   <li>Signing in starts at /oauth2/authorization/google and ends on the Angular /sign-in page;
 *       Spring's own login page is off.
 *   <li>Changes need the CSRF token the Angular app sends back from the XSRF-TOKEN cookie.
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(AccessProperties.class)
@EnableMethodSecurity
class SecurityConfig {

    /**
     * The Angular sign-in page. Google sends people back to it, signed in or with an error, and it
     * takes them on to the page they started from.
     */
    static final String SIGN_IN = "/sign-in";

    @Bean
    SecurityFilterChain security(HttpSecurity http, GoogleUserService googleUsers) throws Exception {
        http.authorizeHttpRequests(requests -> requests.requestMatchers("/api/orgs/*/admin/**")
                        .authenticated()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/version",
                                "/api/online",
                                "/api/ranks",
                                "/api/orgs/**",
                                "/api/invites/*")
                        .permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/api/**", "/actuator/**")
                        .authenticated()
                        .anyRequest()
                        .permitAll())
                .oauth2Login(login -> login.loginPage(SIGN_IN)
                        .defaultSuccessUrl(SIGN_IN, true)
                        .failureUrl(SIGN_IN + "?error")
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(googleUsers)))
                .logout(logout -> logout.logoutUrl("/api/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .csrf(csrf -> csrf.spa())
                .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.withDefaults().matcher("/api/**")));
        return http.build();
    }
}
