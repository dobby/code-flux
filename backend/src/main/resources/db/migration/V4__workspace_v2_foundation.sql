CREATE TABLE IF NOT EXISTS dashboard_pages (
  id TEXT PRIMARY KEY,
  slug TEXT NOT NULL UNIQUE,
  title TEXT NOT NULL,
  description TEXT,
  icon TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0,
  archived INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS widget_definitions (
  id TEXT PRIMARY KEY,
  slug TEXT NOT NULL UNIQUE,
  kind TEXT NOT NULL,
  title TEXT NOT NULL,
  description TEXT,
  dataset_key TEXT,
  tags_json TEXT NOT NULL DEFAULT '[]',
  query_spec_json TEXT,
  viz_spec_json TEXT NOT NULL,
  is_system INTEGER NOT NULL DEFAULT 0,
  archived INTEGER NOT NULL DEFAULT 0,
  version INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS page_widget_instances (
  id TEXT PRIMARY KEY,
  page_id TEXT NOT NULL REFERENCES dashboard_pages(id) ON DELETE CASCADE,
  widget_definition_id TEXT REFERENCES widget_definitions(id),
  kind TEXT NOT NULL,
  title_override TEXT,
  description_override TEXT,
  query_override_json TEXT,
  viz_override_json TEXT,
  layout_json TEXT NOT NULL,
  locked INTEGER NOT NULL DEFAULT 0,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS annotations_v2 (
  id TEXT PRIMARY KEY,
  target_kind TEXT NOT NULL,
  page_widget_instance_id TEXT REFERENCES page_widget_instances(id) ON DELETE CASCADE,
  scope_date TEXT,
  x_value TEXT,
  y_value REAL,
  title TEXT NOT NULL,
  body TEXT,
  color TEXT,
  scope_json TEXT NOT NULL DEFAULT '{}',
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS page_filter_presets (
  id TEXT PRIMARY KEY,
  page_id TEXT NOT NULL REFERENCES dashboard_pages(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  is_default INTEGER NOT NULL DEFAULT 0,
  filters_json TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS jira_connection_settings (
  singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1),
  enabled INTEGER NOT NULL DEFAULT 0,
  base_url TEXT,
  auth_mode TEXT NOT NULL DEFAULT 'pat_bearer',
  verify_tls INTEGER NOT NULL DEFAULT 1,
  issue_key_regex TEXT NOT NULL DEFAULT '[A-Z][A-Z0-9]+-[0-9]+',
  project_keys_json TEXT NOT NULL DEFAULT '[]',
  last_validated_at TEXT,
  updated_at TEXT NOT NULL
);

INSERT INTO jira_connection_settings (
  singleton_id,
  enabled,
  auth_mode,
  verify_tls,
  issue_key_regex,
  project_keys_json,
  updated_at
)
SELECT 1, 0, 'pat_bearer', 1, '[A-Z][A-Z0-9]+-[0-9]+', '[]', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1 FROM jira_connection_settings WHERE singleton_id = 1
);

CREATE TABLE IF NOT EXISTS jira_issue_cache (
  issue_key TEXT PRIMARY KEY,
  issue_id TEXT,
  project_key TEXT,
  summary TEXT,
  issue_type TEXT,
  status TEXT,
  priority TEXT,
  assignee_display_name TEXT,
  reporter_display_name TEXT,
  labels_json TEXT NOT NULL DEFAULT '[]',
  components_json TEXT NOT NULL DEFAULT '[]',
  created_at_remote TEXT,
  updated_at_remote TEXT,
  resolved_at_remote TEXT,
  browse_url TEXT,
  last_fetched_at TEXT NOT NULL,
  raw_json TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS commit_issue_links (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  repo_id TEXT NOT NULL,
  commit_sha TEXT NOT NULL,
  issue_key TEXT NOT NULL REFERENCES jira_issue_cache(issue_key),
  link_rank INTEGER NOT NULL,
  is_primary INTEGER NOT NULL DEFAULT 0,
  source TEXT NOT NULL DEFAULT 'commit_message',
  UNIQUE(repo_id, commit_sha, issue_key)
);

CREATE TABLE IF NOT EXISTS daily_throughput_issue_fact (
  activity_date TEXT NOT NULL,
  repo_id TEXT NOT NULL,
  author_identity TEXT,
  language TEXT,
  category TEXT,
  subtype TEXT,
  product_code TEXT,
  primary_issue_key TEXT,
  primary_issue_type TEXT,
  primary_issue_status TEXT,
  primary_issue_priority TEXT,
  jira_project_key TEXT,
  lines_added INTEGER NOT NULL,
  lines_removed INTEGER NOT NULL,
  net_lines INTEGER NOT NULL,
  commits_count INTEGER NOT NULL,
  files_changed_count INTEGER NOT NULL,
  PRIMARY KEY (
    activity_date,
    repo_id,
    author_identity,
    language,
    category,
    subtype,
    product_code,
    primary_issue_key
  )
);

CREATE TABLE IF NOT EXISTS repo_state_snapshots (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  repo_id TEXT NOT NULL,
  snapshot_date TEXT NOT NULL,
  ref_name TEXT NOT NULL,
  commit_sha TEXT NOT NULL,
  total_files INTEGER NOT NULL,
  total_lines INTEGER NOT NULL,
  production_files INTEGER NOT NULL,
  production_lines INTEGER NOT NULL,
  test_files INTEGER NOT NULL,
  test_lines INTEGER NOT NULL,
  docs_files INTEGER NOT NULL,
  docs_lines INTEGER NOT NULL,
  generated_files INTEGER NOT NULL,
  generated_lines INTEGER NOT NULL,
  unknown_files INTEGER NOT NULL,
  unknown_lines INTEGER NOT NULL,
  created_at TEXT NOT NULL,
  UNIQUE(repo_id, snapshot_date, ref_name)
);

CREATE TABLE IF NOT EXISTS repo_state_snapshot_breakdowns (
  snapshot_id INTEGER NOT NULL REFERENCES repo_state_snapshots(id) ON DELETE CASCADE,
  dimension_kind TEXT NOT NULL,
  dimension_value TEXT NOT NULL,
  files_count INTEGER NOT NULL,
  lines_count INTEGER NOT NULL,
  PRIMARY KEY (snapshot_id, dimension_kind, dimension_value)
);

CREATE TABLE IF NOT EXISTS file_inventory_current (
  repo_id TEXT NOT NULL,
  ref_name TEXT NOT NULL,
  commit_sha TEXT NOT NULL,
  file_path TEXT NOT NULL,
  language TEXT,
  category TEXT,
  subtype TEXT,
  product_code TEXT,
  line_count INTEGER,
  is_binary INTEGER NOT NULL DEFAULT 0,
  updated_at TEXT NOT NULL,
  PRIMARY KEY (repo_id, ref_name, file_path)
);

CREATE TABLE IF NOT EXISTS snapshot_backfill_state (
  repo_id TEXT NOT NULL,
  ref_name TEXT NOT NULL,
  next_snapshot_date TEXT NOT NULL,
  status TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  PRIMARY KEY (repo_id, ref_name)
);

CREATE INDEX IF NOT EXISTS idx_page_widget_instances_page_id
  ON page_widget_instances(page_id);

CREATE INDEX IF NOT EXISTS idx_annotations_v2_widget
  ON annotations_v2(page_widget_instance_id);

CREATE INDEX IF NOT EXISTS idx_commit_issue_links_repo_sha
  ON commit_issue_links(repo_id, commit_sha);

CREATE INDEX IF NOT EXISTS idx_daily_issue_fact_date_repo
  ON daily_throughput_issue_fact(activity_date, repo_id);

CREATE INDEX IF NOT EXISTS idx_repo_state_snapshots_repo_date
  ON repo_state_snapshots(repo_id, snapshot_date);

CREATE INDEX IF NOT EXISTS idx_repo_state_snapshot_breakdowns_lookup
  ON repo_state_snapshot_breakdowns(dimension_kind, dimension_value);

INSERT INTO annotations_v2 (
  id,
  target_kind,
  page_widget_instance_id,
  scope_date,
  x_value,
  y_value,
  title,
  body,
  color,
  scope_json,
  created_at,
  updated_at
)
SELECT
  'legacy-' || CAST(annotation_id AS TEXT),
  'global_date',
  NULL,
  day,
  day,
  NULL,
  title,
  description,
  color_token,
  '{}',
  created_at,
  updated_at
FROM annotation
WHERE NOT EXISTS (
  SELECT 1
  FROM annotations_v2
  WHERE annotations_v2.id = 'legacy-' || CAST(annotation.annotation_id AS TEXT)
);
