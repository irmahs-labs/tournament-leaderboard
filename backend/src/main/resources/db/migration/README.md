Flyway migrations live here, named by date so two branches never clash:
`V2026_11_05_1__short_description.sql`.

A migration that has been merged is never edited: the live database has already run it, and
Flyway refuses to start when its checksum changes. Add a new one instead.

Hibernate runs with `ddl-auto: validate`, so an entity that doesn't match these tables stops
the app at startup.
