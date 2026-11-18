-- The site never shows a Google profile picture, so it no longer keeps its address
-- (data minimisation, GDPR article 5(1)(c)).
ALTER TABLE app_users DROP COLUMN avatar_url;
