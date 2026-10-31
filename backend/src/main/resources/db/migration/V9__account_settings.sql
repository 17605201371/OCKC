ALTER TABLE app_user ADD COLUMN profile_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN password_changed_at VARCHAR(40);
ALTER TABLE app_user ADD COLUMN password_failures INT NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN password_locked_until VARCHAR(40);
