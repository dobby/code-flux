CREATE TABLE repository (
  repo_id TEXT PRIMARY KEY,
  display_name TEXT NOT NULL,
  clone_url TEXT NOT NULL,
  product_code TEXT,
  enabled INTEGER NOT NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE author (
  author_id TEXT PRIMARY KEY,
  display_name TEXT NOT NULL,
  cohort TEXT,
  active INTEGER NOT NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE author_alias (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  author_id TEXT NOT NULL REFERENCES author(author_id),
  alias_email TEXT,
  alias_name TEXT,
  UNIQUE(author_id, alias_email, alias_name)
);

CREATE TABLE sync_run (
  sync_run_id INTEGER PRIMARY KEY AUTOINCREMENT,
  started_at TEXT NOT NULL,
  finished_at TEXT,
  status TEXT NOT NULL,
  message TEXT
);

CREATE TABLE repo_sync_state (
  repo_id TEXT PRIMARY KEY REFERENCES repository(repo_id),
  last_successful_sync_run_id INTEGER,
  last_successful_synced_at TEXT,
  last_seen_commit_sha TEXT,
  last_error_message TEXT
);

CREATE TABLE commit_fact (
  repo_id TEXT NOT NULL REFERENCES repository(repo_id),
  commit_sha TEXT NOT NULL,
  author_id TEXT REFERENCES author(author_id),
  author_name TEXT NOT NULL,
  author_email TEXT,
  authored_at TEXT NOT NULL,
  committed_at TEXT NOT NULL,
  subject TEXT,
  patch_id TEXT,
  is_merge_commit INTEGER NOT NULL,
  is_duplicate_patch INTEGER NOT NULL,
  canonical_commit_sha TEXT,
  sync_run_id INTEGER REFERENCES sync_run(sync_run_id),
  created_at TEXT NOT NULL,
  PRIMARY KEY (repo_id, commit_sha)
);

CREATE TABLE commit_file_fact (
  repo_id TEXT NOT NULL,
  commit_sha TEXT NOT NULL,
  file_path TEXT NOT NULL,
  old_path TEXT,
  extension TEXT,
  language TEXT,
  category TEXT,
  subtype TEXT,
  product_code TEXT,
  lines_added INTEGER NOT NULL,
  lines_removed INTEGER NOT NULL,
  net_lines INTEGER NOT NULL,
  is_binary INTEGER NOT NULL,
  is_generated INTEGER NOT NULL,
  is_canonical_patch INTEGER NOT NULL,
  PRIMARY KEY (repo_id, commit_sha, file_path),
  FOREIGN KEY (repo_id, commit_sha) REFERENCES commit_fact(repo_id, commit_sha)
);
