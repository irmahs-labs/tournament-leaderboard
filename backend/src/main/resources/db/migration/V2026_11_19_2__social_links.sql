-- Branding: each organisation's social links, shown in the footer of its pages. Empty hides one.
-- The app checks that each is an https address on its own site (any site for the website).

ALTER TABLE organizations
  ADD COLUMN discord_url    varchar(200) CHECK (discord_url ~ '^https://'),
  ADD COLUMN instagram_url  varchar(200) CHECK (instagram_url ~ '^https://'),
  ADD COLUMN twitch_url     varchar(200) CHECK (twitch_url ~ '^https://'),
  ADD COLUMN youtube_url    varchar(200) CHECK (youtube_url ~ '^https://'),
  ADD COLUMN tiktok_url     varchar(200) CHECK (tiktok_url ~ '^https://'),
  ADD COLUMN website_url    varchar(200) CHECK (website_url ~ '^https://');
