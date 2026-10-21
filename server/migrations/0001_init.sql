CREATE TABLE users (
  id TEXT PRIMARY KEY,
  username TEXT NOT NULL UNIQUE,
  pass TEXT NOT NULL,            -- iterations:salt:hash (base64)
  created_at INTEGER NOT NULL
);
CREATE TABLE sessions (
  token_hash TEXT PRIMARY KEY,   -- SHA-256 hex of the bearer token
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at INTEGER NOT NULL
);
CREATE INDEX sessions_user ON sessions(user_id);
CREATE TABLE profiles (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  avatar TEXT NOT NULL,
  kids INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
);
CREATE INDEX profiles_user ON profiles(user_id);
CREATE TABLE history (
  profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  key TEXT NOT NULL,
  payload TEXT NOT NULL,         -- opaque JSON string
  position INTEGER NOT NULL,
  duration INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,   -- client clock, decides last-write-wins
  synced_at INTEGER NOT NULL,    -- server clock, drives ?since=
  deleted INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (profile_id, key)
);
CREATE INDEX history_synced ON history(profile_id, synced_at);
CREATE TABLE pairs (
  code TEXT PRIMARY KEY,
  expires_at INTEGER NOT NULL,
  user_id TEXT                   -- set when a phone claims it
);
