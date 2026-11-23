package dev.irmahs.leaderboard.security;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.serializer.support.DeserializingConverter;
import org.springframework.core.serializer.support.SerializingConverter;

/**
 * Sessions live in the database (Spring Session JDBC, tables made by Flyway), so a deploy or a
 * restart doesn't sign anyone out, and two versions of the app can serve the same people while a
 * deploy switches from one to the other.
 */
@Configuration
class SessionConfig {

    private static final Logger log = LoggerFactory.getLogger(SessionConfig.class);

    /**
     * The cookie the privacy page lists, JSESSIONID rather than Spring Session's SESSION, never
     * readable by scripts, however the app is run, and kept by the browser as long as the session
     * may last instead of until it closes. Secure comes from server.servlet.session.cookie.secure,
     * set in production.
     */
    @Bean
    DefaultCookieSerializerCustomizer sessionCookie(@Value("${spring.session.timeout}") Duration timeout) {
        return cookie -> {
            cookie.setCookieName("JSESSIONID");
            cookie.setUseHttpOnlyCookie(true);
            cookie.setCookieMaxAge((int) timeout.toSeconds());
        };
    }

    /**
     * Stores session attributes the usual Java way, but reads one the running version can't
     * understand, such as a sign-in saved before a Spring Security upgrade, as missing: that
     * person is signed out instead of getting an error on every page.
     */
    @Bean
    ConversionService springSessionConversionService() {
        GenericConversionService conversions = new GenericConversionService();
        ClassLoader classLoader = SessionConfig.class.getClassLoader();
        conversions.addConverter(Object.class, byte[].class, new SerializingConverter());
        conversions.addConverter(byte[].class, Object.class, new Lenient(new DeserializingConverter(classLoader)));
        return conversions;
    }

    /** A stored attribute, or nothing when it no longer reads. */
    record Lenient(Converter<byte[], Object> reader) implements Converter<byte[], Object> {

        @Override
        public Object convert(byte[] bytes) {
            try {
                return reader.convert(bytes);
            } catch (RuntimeException unreadable) {
                log.info("Dropped a session attribute this version can't read: {}", unreadable.getMessage());
                return null;
            }
        }
    }
}
