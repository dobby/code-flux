CREATE TABLE daily_fact (
  day TEXT NOT NULL,
  repo_id TEXT NOT NULL,
  author_id TEXT NOT NULL,
  language TEXT NOT NULL,
  category TEXT NOT NULL,
  subtype TEXT NOT NULL,
  product_code TEXT NOT NULL DEFAULT '',
  lines_added INTEGER NOT NULL,
  lines_removed INTEGER NOT NULL,
  net_lines INTEGER NOT NULL,
  commit_count INTEGER NOT NULL,
  file_count INTEGER NOT NULL,
  PRIMARY KEY (day, repo_id, author_id, language, category, subtype, product_code)
);

CREATE INDEX idx_commit_fact_repo_authored_at ON commit_fact(repo_id, authored_at);
CREATE INDEX idx_commit_fact_repo_patch_id ON commit_fact(repo_id, patch_id);
CREATE INDEX idx_commit_fact_author_id ON commit_fact(author_id);
CREATE INDEX idx_commit_file_fact_repo_category ON commit_file_fact(repo_id, category);
CREATE INDEX idx_commit_file_fact_repo_language ON commit_file_fact(repo_id, language);
CREATE INDEX idx_daily_fact_day ON daily_fact(day);
CREATE INDEX idx_daily_fact_repo_day ON daily_fact(repo_id, day);
CREATE INDEX idx_daily_fact_author_day ON daily_fact(author_id, day);
CREATE INDEX idx_daily_fact_category_day ON daily_fact(category, day);
CREATE INDEX idx_daily_fact_product_day ON daily_fact(product_code, day);
