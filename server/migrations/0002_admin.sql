-- users.created_at already exists since 0001.
ALTER TABLE users ADD COLUMN is_admin INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN disabled INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN last_seen_at INTEGER;
CREATE TABLE events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  at INTEGER NOT NULL,
  user_id TEXT,
  profile_id TEXT,
  device TEXT,
  kind TEXT NOT NULL,
  detail TEXT,                   -- JSON
  ip_country TEXT,
  ua TEXT,
  app_version TEXT
);
CREATE INDEX events_at ON events(at);
CREATE INDEX events_kind_at ON events(kind, at);
CREATE INDEX events_user_at ON events(user_id, at);
ALTER TABLE sessions ADD COLUMN last_used_at INTEGER;
ALTER TABLE sessions ADD COLUMN device TEXT;
ALTER TABLE sessions ADD COLUMN ua TEXT;
-- Open watch-together rooms, mirrored by the Room DO on create/join/leave so admin can list them.
CREATE TABLE rooms (
  code TEXT PRIMARY KEY,
  title TEXT,
  members TEXT NOT NULL DEFAULT '[]', -- JSON [{name, avatar}]
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
