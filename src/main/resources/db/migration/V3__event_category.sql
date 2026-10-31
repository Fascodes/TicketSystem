CREATE TYPE event_category AS ENUM ('ROCK', 'POP', 'JAZZ', 'CLASSICAL', 'ELECTRONIC', 'HIP_HOP', 'METAL', 'OTHER');

-- Existing rows get OTHER, new rows must state the category explicitly
ALTER TABLE "events" ADD COLUMN "category" event_category NOT NULL DEFAULT 'OTHER';
ALTER TABLE "events" ALTER COLUMN "category" DROP DEFAULT;

CREATE INDEX idx_events_category ON events(category);
CREATE INDEX idx_events_start_date ON events(start_date);
