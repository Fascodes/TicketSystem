CREATE TABLE "users"(
    "id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "email" varchar(50) NOT NULL,
    "password_hash" varchar(255) NOT NULL,
    "enabled" boolean NOT NULL DEFAULT false,
    "account_non_locked" boolean NOT NULL DEFAULT true,
    "locked_until" timestamp,
    "failed_login_attempts" INT NOT NULL DEFAULT 0,
    "created_at" timestamp NOT NULL,
    "last_login_at" timestamp NOT NULL
--     "role_id" UUID NOT NULL REFERENCES "user_roles"("id")
);



CREATE TABLE "roles"(
    "id" BIGSERIAL PRIMARY KEY,
    "name" varchar(50) NOT NULL UNIQUE
);

CREATE TABLE "user_roles"(
    "user_id" UUID NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
    "role_id" BIGSERIAL NOT NULL REFERENCES "roles"("id") ON DELETE CASCADE,
    PRIMARY KEY(user_id, role_id)
);

CREATE TABLE "refresh_tokens"(
    "id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "user_id" UUID NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
    "token_hash" varchar(255) NOT NULL UNIQUE,
    "expires_at" timestamp NOT NULL,
    "revoked" boolean NOT NULL DEFAULT false,
    "replaced_by_token_id" UUID REFERENCES "refresh_tokens"("id"),
    "created_at" timestamp NOT NULL,
    "user_agent" varchar(255),
    "ip_address" varchar(45)
);

INSERT INTO roles (name) VALUES ('ROLE_USER'), ('ROLE_ADMIN');

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);