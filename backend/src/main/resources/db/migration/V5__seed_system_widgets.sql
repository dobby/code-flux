INSERT OR IGNORE INTO widget_definitions (
  id, slug, kind, title, description, dataset_key,
  tags_json, query_spec_json, viz_spec_json,
  is_system, archived, version, created_at, updated_at
)
SELECT id, slug, kind, title, description, dataset_key,
       tags_json, query_spec_json, viz_spec_json,
       1, 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
  SELECT
    'widget-system-daily-lines-added' AS id,
    'daily-lines-added' AS slug,
    'time_series' AS kind,
    'Daily lines added' AS title,
    'Lines of code added per day across all repositories.' AS description,
    'throughput_daily' AS dataset_key,
    '["throughput","daily","lines"]' AS tags_json,
    '{"dataset":"throughput_daily","timeBucket":"day","measure":{"field":"lines_added","aggregation":"sum"},"groupBy":[],"filters":[],"sort":[]}' AS query_spec_json,
    '{"chartType":"line","showLegend":true,"showAnnotations":true}' AS viz_spec_json
  UNION ALL
  SELECT
    'widget-system-daily-lines-by-repo',
    'daily-lines-by-repo',
    'time_series',
    'Daily lines by repository',
    'Lines added per day broken down by repository.',
    'throughput_daily',
    '["throughput","daily","lines","repo"]',
    '{"dataset":"throughput_daily","timeBucket":"day","measure":{"field":"lines_added","aggregation":"sum"},"groupBy":["repo"],"filters":[],"sort":[]}',
    '{"chartType":"area","showLegend":true,"showAnnotations":true}'
  UNION ALL
  SELECT
    'widget-system-weekly-commits',
    'weekly-commits',
    'time_series',
    'Weekly commit count',
    'Total commits authored per week.',
    'throughput_daily',
    '["throughput","weekly","commits"]',
    '{"dataset":"throughput_daily","timeBucket":"week","measure":{"field":"commits_count","aggregation":"sum"},"groupBy":[],"filters":[],"sort":[]}',
    '{"chartType":"bar","showLegend":false,"showAnnotations":true}'
  UNION ALL
  SELECT
    'widget-system-current-language-mix',
    'current-language-mix',
    'distribution',
    'Current language mix',
    'Distribution of files across programming languages in the current snapshot.',
    'file_inventory_current',
    '["snapshot","language","inventory"]',
    '{"dataset":"file_inventory_current","measure":{"field":"files_count","aggregation":"sum"},"groupBy":["language"],"filters":[],"sort":[{"field":"value","direction":"desc"}],"limit":12}',
    '{"chartType":"donut","showLegend":true}'
  UNION ALL
  SELECT
    'widget-system-top-authors',
    'top-authors',
    'distribution',
    'Top contributors by lines added',
    'Authors ranked by total lines added.',
    'throughput_daily',
    '["throughput","authors","contributors"]',
    '{"dataset":"throughput_daily","measure":{"field":"lines_added","aggregation":"sum"},"groupBy":["author"],"filters":[],"sort":[{"field":"value","direction":"desc"}],"limit":10}',
    '{"chartType":"bar","showLegend":false}'
  UNION ALL
  SELECT
    'widget-system-total-lines-card',
    'total-lines-card',
    'metric_card',
    'Total lines added',
    'Cumulative lines of code added across the sync window.',
    'throughput_daily',
    '["throughput","metric","lines"]',
    '{"dataset":"throughput_daily","measure":{"field":"lines_added","aggregation":"sum"},"groupBy":[],"filters":[],"sort":[]}',
    '{"showLegend":false}'
  UNION ALL
  SELECT
    'widget-system-commit-heatmap',
    'commit-heatmap',
    'calendar_heatmap',
    'Commit activity heatmap',
    'Calendar view of daily commit volume.',
    'throughput_daily',
    '["throughput","calendar","commits"]',
    '{"dataset":"throughput_daily","timeBucket":"day","measure":{"field":"commits_count","aggregation":"sum"},"groupBy":[],"filters":[],"sort":[]}',
    '{"showLegend":true}'
  UNION ALL
  SELECT
    'widget-system-day-explorer',
    'day-explorer',
    'day_explorer',
    'Day activity explorer',
    'Click a time-series point to inspect commits, files, and contributors for that day.',
    'day_activity',
    '["drilldown","explorer","detail"]',
    '{"dataset":"day_activity","groupBy":[],"filters":[],"sort":[]}',
    '{"emptyStateMessage":"Click a time-series point or heatmap cell to load the day detail."}'
);
