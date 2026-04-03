CREATE TABLE annotation (
  annotation_id INTEGER PRIMARY KEY AUTOINCREMENT,
  day TEXT NOT NULL,
  title TEXT NOT NULL,
  description TEXT,
  type TEXT NOT NULL,
  color_token TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
