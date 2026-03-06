CREATE TABLE IF NOT EXISTS users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    username      VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
    role          VARCHAR(50)  NOT NULL DEFAULT 'BUYER',
    created_at    TIMESTAMP    NOT NULL
);
