ALTER TABLE refresh_tokens
    ADD COLUMN family_id   UUID,
    ADD COLUMN revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN revoked_at  TIMESTAMP;

-- backfill existing rows so family_id is never null
UPDATE refresh_tokens SET family_id = id WHERE family_id IS NULL;

ALTER TABLE refresh_tokens ALTER COLUMN family_id SET NOT NULL;
