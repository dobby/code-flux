package com.company.throughput.v2.service

import com.company.throughput.v2.model.CodebaseBreakdownRow
import com.company.throughput.v2.model.CodebaseStructureRequest
import com.company.throughput.v2.model.CodebaseStructureResponse
import com.company.throughput.v2.model.CodebaseSummary
import com.company.throughput.v2.model.CodebaseTreeNode
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class CodebaseService(
    private val jdbcClient: JdbcClient,
) {
    fun structure(request: CodebaseStructureRequest): CodebaseStructureResponse {
        require(!request.dateFrom.isAfter(request.dateTo)) {
            "INVALID_CODEBASE_REQUEST: dateFrom must be on or before dateTo"
        }

        val repoSummary = loadRepoSummary(request.repoId)
        val inventoryRows = loadInventoryRows(request)
        val activityByFile = loadActivityByFile(request)
        val tree = buildTree(request.repoId, inventoryRows, activityByFile)

        val visibleLines = inventoryRows.sumOf { it.lineCount.toLong() }
        val netLinesInRange = inventoryRows.sumOf { activityByFile[it.filePath] ?: 0L }

        return CodebaseStructureResponse(
            summary = CodebaseSummary(
                repoId = request.repoId,
                refName = repoSummary.refName,
                commitSha = repoSummary.commitSha,
                lastSnapshotDate = loadLastSnapshotDate(request.repoId),
                repoFiles = repoSummary.repoFiles,
                repoLines = repoSummary.repoLines,
                visibleFiles = inventoryRows.size,
                visibleLines = visibleLines,
                languagesCount = inventoryRows.map { it.language }.toSet().size,
                categoriesCount = inventoryRows.map { it.category }.toSet().size,
                activeFilesInRange = inventoryRows.count { (activityByFile[it.filePath] ?: 0L) != 0L },
                netLinesInRange = netLinesInRange,
            ),
            tree = tree,
            languages = buildBreakdown(inventoryRows, activityByFile) { it.language },
            categories = buildBreakdown(inventoryRows, activityByFile) { it.category },
            topDirectories = buildBreakdown(inventoryRows, activityByFile) { topDirectoryLabel(it.filePath) },
            filterSemantics = listOf(
                "Date range changes growth context and the Net activity size mode.",
                "Language, category, and product filters narrow the current structure and all structural breakdowns.",
                "Author filters do not alter the current tree; they only change Net activity sizing.",
                "Net activity size uses absolute magnitude for tile area while signed net lines remain visible in labels and tooltips.",
            ),
        )
    }

    private fun loadRepoSummary(repoId: String): RepoSummary {
        val inventorySummary = jdbcClient.sql(
            """
            SELECT
              MAX(ref_name) AS ref_name,
              MAX(commit_sha) AS commit_sha,
              COUNT(*) AS files_count,
              COALESCE(SUM(COALESCE(line_count, 0)), 0) AS lines_count
            FROM file_inventory_current
            WHERE repo_id = :repoId
            """.trimIndent(),
        )
            .param("repoId", repoId)
            .query { rs, _ ->
                RepoSummary(
                    refName = rs.getString("ref_name"),
                    commitSha = rs.getString("commit_sha"),
                    repoFiles = rs.getInt("files_count"),
                    repoLines = rs.getLong("lines_count"),
                )
            }
            .single()

        return inventorySummary
    }

    private fun loadLastSnapshotDate(repoId: String): LocalDate? =
        jdbcClient.sql(
            """
            SELECT MAX(snapshot_date) AS latest_snapshot_date
            FROM repo_state_snapshots
            WHERE repo_id = :repoId
            """.trimIndent(),
        )
            .param("repoId", repoId)
            .query { rs, _ -> rs.getString("latest_snapshot_date")?.let(LocalDate::parse) }
            .optional()
            .orElse(null)

    private fun loadInventoryRows(request: CodebaseStructureRequest): List<InventoryRow> {
        val where = mutableListOf<String>()
        val params = linkedMapOf<String, Any?>(
            "repoId" to request.repoId,
        )

        where += "i.repo_id = :repoId"
        addInClause(where, params, "COALESCE(i.language, 'unknown')", "language", request.languages)
        addInClause(where, params, "COALESCE(i.category, 'unknown')", "category", request.categories)
        addInClause(where, params, "COALESCE(NULLIF(i.product_code, ''), 'unknown')", "product", request.productCodes)

        val sql = buildString {
            append(
                """
                SELECT
                  i.file_path,
                  COALESCE(i.language, 'unknown') AS language,
                  COALESCE(i.category, 'unknown') AS category,
                  COALESCE(i.subtype, 'unknown') AS subtype,
                  COALESCE(NULLIF(i.product_code, ''), 'unknown') AS product_code,
                  COALESCE(i.line_count, 0) AS line_count
                FROM file_inventory_current i
                """.trimIndent(),
            )
            if (where.isNotEmpty()) {
                append(" WHERE ${where.joinToString(" AND ")}")
            }
            append(" ORDER BY i.file_path ASC")
        }

        return applyParams(jdbcClient.sql(sql), params)
            .query { rs, _ ->
                InventoryRow(
                    filePath = rs.getString("file_path"),
                    language = rs.getString("language"),
                    category = rs.getString("category"),
                    subtype = rs.getString("subtype"),
                    productCode = rs.getString("product_code"),
                    lineCount = rs.getInt("line_count"),
                )
            }
            .list()
    }

    private fun loadActivityByFile(request: CodebaseStructureRequest): Map<String, Long> {
        val where = mutableListOf<String>()
        val params = linkedMapOf<String, Any?>(
            "repoId" to request.repoId,
            "dateFrom" to request.dateFrom.toString(),
            "dateTo" to request.dateTo.toString(),
        )

        where += "c.repo_id = :repoId"
        where += "DATE(c.authored_at) BETWEEN :dateFrom AND :dateTo"
        addInClause(where, params, "c.author_id", "author", request.authorIds)
        addInClause(where, params, "COALESCE(f.language, 'unknown')", "language", request.languages)
        addInClause(where, params, "COALESCE(f.category, 'unknown')", "category", request.categories)
        addInClause(where, params, "COALESCE(NULLIF(f.product_code, ''), 'unknown')", "product", request.productCodes)

        val sql = buildString {
            append(
                """
                SELECT
                  f.file_path,
                  COALESCE(SUM(f.net_lines), 0) AS net_lines
                FROM commit_fact c
                JOIN commit_file_fact f
                  ON f.repo_id = c.repo_id
                 AND f.commit_sha = c.commit_sha
                """.trimIndent(),
            )
            if (where.isNotEmpty()) {
                append(" WHERE ${where.joinToString(" AND ")}")
            }
            append(" GROUP BY f.file_path")
        }

        return applyParams(jdbcClient.sql(sql), params)
            .query { rs, _ -> rs.getString("file_path") to rs.getLong("net_lines") }
            .list()
            .toMap()
    }

    private fun buildTree(
        repoId: String,
        inventoryRows: List<InventoryRow>,
        activityByFile: Map<String, Long>,
    ): CodebaseTreeNode {
        val root = MutableTreeNode(
            key = repoId,
            label = repoId,
            path = "",
            kind = "directory",
        )

        inventoryRows.forEach { row ->
            val fileNetLines = activityByFile[row.filePath] ?: 0L
            root.filesCount += 1
            root.linesCount += row.lineCount.toLong()
            root.netLines += fileNetLines

            val parts = row.filePath.split('/').filter(String::isNotBlank)
            var current = root
            parts.dropLast(1).forEachIndexed { index, segment ->
                val path = parts.take(index + 1).joinToString("/")
                val child = current.children.getOrPut(segment) {
                    MutableTreeNode(
                        key = path,
                        label = segment,
                        path = path,
                        kind = "directory",
                    )
                }
                child.filesCount += 1
                child.linesCount += row.lineCount.toLong()
                child.netLines += fileNetLines
                current = child
            }

            val fileLabel = parts.lastOrNull() ?: row.filePath
            val fileNode = current.children.getOrPut(fileLabel) {
                MutableTreeNode(
                    key = row.filePath,
                    label = fileLabel,
                    path = row.filePath,
                    kind = "file",
                )
            }
            fileNode.filesCount += 1
            fileNode.linesCount += row.lineCount.toLong()
            fileNode.netLines += fileNetLines
        }

        return root.freeze()
    }

    private fun buildBreakdown(
        inventoryRows: List<InventoryRow>,
        activityByFile: Map<String, Long>,
        keySelector: (InventoryRow) -> String,
    ): List<CodebaseBreakdownRow> =
        inventoryRows
            .groupBy(keySelector)
            .map { (key, rows) ->
                CodebaseBreakdownRow(
                    key = key,
                    label = key,
                    filesCount = rows.size,
                    linesCount = rows.sumOf { it.lineCount.toLong() },
                    netLines = rows.sumOf { activityByFile[it.filePath] ?: 0L },
                )
            }
            .sortedWith(compareByDescending<CodebaseBreakdownRow> { it.linesCount }.thenBy { it.label })
            .take(8)

    private fun topDirectoryLabel(filePath: String): String =
        filePath.substringBefore('/', missingDelimiterValue = "(root)").ifBlank { "(root)" }

    private fun addInClause(
        where: MutableList<String>,
        params: MutableMap<String, Any?>,
        expression: String,
        keyPrefix: String,
        values: List<String>,
    ) {
        if (values.isEmpty()) {
            return
        }

        val placeholders = values.mapIndexed { index, value ->
            val key = "${keyPrefix}_$index"
            params[key] = value
            ":$key"
        }
        where += "$expression IN (${placeholders.joinToString(", ")})"
    }

    private fun applyParams(spec: JdbcClient.StatementSpec, params: Map<String, Any?>): JdbcClient.StatementSpec {
        var current = spec
        params.forEach { (key, value) -> current = current.param(key, value) }
        return current
    }

    private data class RepoSummary(
        val refName: String?,
        val commitSha: String?,
        val repoFiles: Int,
        val repoLines: Long,
    )

    private data class InventoryRow(
        val filePath: String,
        val language: String,
        val category: String,
        val subtype: String,
        val productCode: String,
        val lineCount: Int,
    )

    private class MutableTreeNode(
        val key: String,
        val label: String,
        val path: String,
        val kind: String,
        var filesCount: Int = 0,
        var linesCount: Long = 0,
        var netLines: Long = 0,
        val children: LinkedHashMap<String, MutableTreeNode> = linkedMapOf(),
    ) {
        fun freeze(): CodebaseTreeNode =
            CodebaseTreeNode(
                key = key,
                label = label,
                path = path,
                kind = kind,
                filesCount = filesCount,
                linesCount = linesCount,
                netLines = netLines,
                children = children.values.map { it.freeze() }.sortedBy { it.label.lowercase() },
            )
    }
}
