-- Organisation logos can also be JPEG images (.jpg or .jpeg).
ALTER TABLE organization_logos DROP CONSTRAINT organization_logos_content_type_check;
ALTER TABLE organization_logos ADD CONSTRAINT organization_logos_content_type_check
  CHECK (content_type IN ('image/png', 'image/jpeg', 'image/webp', 'image/svg+xml'));
