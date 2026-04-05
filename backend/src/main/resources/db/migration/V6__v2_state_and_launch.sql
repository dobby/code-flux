ALTER TABLE dashboard_pages ADD COLUMN time_range_json TEXT;
ALTER TABLE dashboard_pages ADD COLUMN filters_json TEXT NOT NULL DEFAULT '[]';

UPDATE dashboard_pages
SET filters_json = COALESCE(NULLIF(filters_json, ''), '[]');

ALTER TABLE annotations_v2 ADD COLUMN annotation_type TEXT NOT NULL DEFAULT 'note';
ALTER TABLE annotations_v2 ADD COLUMN name TEXT;
ALTER TABLE annotations_v2 ADD COLUMN description TEXT;
ALTER TABLE annotations_v2 ADD COLUMN tags_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE annotations_v2 ADD COLUMN commit_refs_json TEXT NOT NULL DEFAULT '[]';

UPDATE annotations_v2
SET
  annotation_type = COALESCE(NULLIF(annotation_type, ''), 'note'),
  name = COALESCE(NULLIF(name, ''), title),
  description = COALESCE(NULLIF(description, ''), body);

CREATE TABLE IF NOT EXISTS sync_run_events (
  event_key TEXT PRIMARY KEY,
  sync_run_id INTEGER NOT NULL REFERENCES sync_run(sync_run_id) ON DELETE CASCADE,
  sort_order INTEGER NOT NULL,
  source_kind TEXT NOT NULL,
  repo_id TEXT,
  label TEXT NOT NULL,
  detail TEXT,
  status TEXT NOT NULL,
  started_at TEXT,
  finished_at TEXT,
  progress_percent INTEGER,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_sync_run_events_run_id
  ON sync_run_events(sync_run_id, sort_order);
