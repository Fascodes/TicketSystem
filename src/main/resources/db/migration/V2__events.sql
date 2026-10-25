CREATE TABLE "venues"(
    "id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "name" varchar(255) NOT NULL
);

CREATE TABLE "events"(
    "id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "owner_id" UUID NOT NULL REFERENCES "users"("id"),
    "venue_id" UUID NOT NULL REFERENCES "venues"("id"),
    "name" varchar(255) NOT NULL,
    "start_date" timestamp NOT NULL,
    "end_date" timestamp NOT NULL,
    "ticket_sale_date" timestamp NOT NULL,
    "published" boolean NOT NULL DEFAULT false,
    CONSTRAINT chk_events_dates CHECK ("end_date" >= "start_date")
);

CREATE INDEX idx_events_owner_id ON events(owner_id);
CREATE INDEX idx_events_venue_id ON events(venue_id);
