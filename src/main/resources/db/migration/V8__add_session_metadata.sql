ALTER TABLE refresh_tokens
    ADD COLUMN device_info VARCHAR(255),
    ADD COLUMN ip_address  VARCHAR(45);
