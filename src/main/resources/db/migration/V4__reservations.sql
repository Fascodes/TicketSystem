-- Seat pool: available_seats drops only when a reservation is PAID
ALTER TABLE "events" ADD COLUMN "capacity" int NOT NULL DEFAULT 0;
ALTER TABLE "events" ADD COLUMN "available_seats" int NOT NULL DEFAULT 0;
ALTER TABLE "events" ALTER COLUMN "capacity" DROP DEFAULT;
ALTER TABLE "events" ALTER COLUMN "available_seats" DROP DEFAULT;
ALTER TABLE "events" ADD CONSTRAINT chk_events_seats
    CHECK ("available_seats" >= 0 AND "available_seats" <= "capacity");

CREATE TYPE reservation_status AS ENUM ('PENDING', 'PAID', 'FAILED', 'CANCELLED');

CREATE TABLE "reservations"(
    "id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "event_id" UUID NOT NULL REFERENCES "events"("id"),  -- RESTRICT: an event with reservations cannot be deleted
    "user_id" UUID NOT NULL REFERENCES "users"("id"),
    "quantity" int NOT NULL CHECK ("quantity" BETWEEN 1 AND 6),
    "status" reservation_status NOT NULL DEFAULT 'PENDING',
    -- Columns are timestamp without time zone holding UTC (Hibernate writes Instant as UTC), so the default must be UTC too
    "created_at" timestamp NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    "paid_at" timestamp,
    "cancelled_at" timestamp
);

-- At most one open (PENDING) reservation per user and event - guards double-click / client retries
CREATE UNIQUE INDEX uq_reservations_open ON reservations(user_id, event_id) WHERE status = 'PENDING';
CREATE INDEX idx_reservations_event_id ON reservations(event_id);
CREATE INDEX idx_reservations_user_id ON reservations(user_id);
