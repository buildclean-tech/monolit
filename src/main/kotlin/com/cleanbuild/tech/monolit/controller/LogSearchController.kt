package com.cleanbuild.tech.monolit.controller

import org.apache.lucene.queryparser.classic.QueryParser
import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcher
import com.cleanbuild.tech.monolit.repository.CRUDOperation
import com.cleanbuild.tech.monolit.service.LuceneIngestionService
import jakarta.servlet.http.HttpServletResponse
import org.apache.lucene.document.LongPoint
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.store.FSDirectory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import java.nio.file.Files
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.sql.DataSource

@RestController
@RequestMapping("/log-search")
class LogSearchController(
    private val dataSource: DataSource,
    private val luceneIngestionService: LuceneIngestionService
) {
    private val sshLogWatcherCrud = CRUDOperation(dataSource, SSHLogWatcher::class)

    private fun getAllSSHLogWatcherNames(): List<String> =
        sshLogWatcherCrud.findAll().map { it.name }

    private fun getUniqueFilePaths(watcherName: String): List<String> {
        val filePaths = mutableSetOf<String>()
        try {
            val indexDir = luceneIngestionService.getBaseIndexDir().resolve(watcherName)
            if (!Files.exists(indexDir)) return emptyList()

            FSDirectory.open(indexDir).use { directory ->
                if (DirectoryReader.indexExists(directory)) {
                    DirectoryReader.open(directory).use { reader ->
                        val searcher = IndexSearcher(reader)
                        val query = BooleanQuery.Builder().build()
                        val topDocs = searcher.search(query, Int.MAX_VALUE)
                        for (scoreDoc in topDocs.scoreDocs) {
                            searcher.doc(scoreDoc.doc).get("logPath")?.let { filePaths.add(it) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("Error retrieving file paths: ${e.message}")
            e.printStackTrace()
        }
        return filePaths.toList().sorted()
    }

    @GetMapping(produces = [MediaType.TEXT_HTML_VALUE])
    fun searchPage(
        @RequestParam(required = false) watcherName: String?,
        @RequestParam(required = false) filePath: String?,
        @RequestParam(required = false) contentQuery: String?,
        @RequestParam(required = false) timestampQuery: String?,
        @RequestParam(required = false) logPathQuery: String?,
        @RequestParam(required = false) operator: String?,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(required = false, defaultValue = "100000") pageSize: Int,
        @RequestParam(required = false, defaultValue = "UTC") timezone: String
    ): String {
        val watcherNames = getAllSSHLogWatcherNames()
        val filePaths = if (!watcherName.isNullOrBlank()) getUniqueFilePaths(watcherName) else emptyList()

        val totalResultsCount = if ((!contentQuery.isNullOrBlank() || !timestampQuery.isNullOrBlank() || !logPathQuery.isNullOrBlank()) && !watcherName.isNullOrBlank()) {
            searchLogs(watcherName, contentQuery, timestampQuery, logPathQuery, operator ?: "AND", startDate, endDate, timezone, 1, Int.MAX_VALUE).size
        } else 0

        val limitedPageSize = minOf(pageSize, 100000)
        val searchResults = if (totalResultsCount > 0 && !watcherName.isNullOrBlank()) {
            searchLogs(watcherName, contentQuery, timestampQuery, logPathQuery, operator ?: "AND", startDate, endDate, timezone, page, limitedPageSize)
        } else emptyList()

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Log Search</title>
            <style>
                body { font-family: Arial, sans-serif; line-height: 1.3; margin: 0; padding: 10px; color: #333; background-color: #fff; font-size: 12px; }
                .container { max-width: 100%; margin: 0 auto; }
                h1, h2 { color: #2c3e50; margin: 5px 0; font-size: 14px; }
                .search-form { margin-bottom: 10px; padding: 8px; background-color: #f0f0f0; border-radius: 3px; }
                .form-group { margin-bottom: 6px; }
                label { display: block; margin-bottom: 2px; font-weight: bold; font-size: 11px; }
                select, input, button { padding: 4px; border: 1px solid #ddd; border-radius: 2px; width: 100%; font-size: 11px; box-sizing: border-box; }
                button { background-color: #3498db; color: white; border: none; cursor: pointer; font-weight: bold; padding: 5px; font-size: 11px; }
                button:hover { background-color: #2980b9; }
                .search-results { margin-top: 8px; font-size: 11px; }
                .log-entry { margin-bottom: 2px; padding: 2px 4px; background-color: #fff; border-left: 2px solid #3498db; border-radius: 2px; white-space: pre-wrap; word-break: break-word; }
                .log-entry-header { margin-bottom: 1px; font-size: 10px; color: #555; }
                .log-entry-content { font-family: monospace; font-size: 10px; line-height: 1.2; }
                .pagination { display: flex; justify-content: center; margin-top: 8px; }
                .pagination a { margin: 0 2px; padding: 3px 6px; background-color: #f8f9fa; border: 1px solid #ddd; border-radius: 2px; text-decoration: none; color: #333; font-size: 10px; }
                .pagination a.active { background-color: #3498db; color: white; border-color: #3498db; }
                .nav-links { margin-bottom: 6px; text-align: center; font-size: 11px; }
                .nav-links a { margin-right: 8px; color: #3498db; text-decoration: none; }
                .no-results { padding: 5px; background-color: #f8f9fa; border-radius: 2px; text-align: center; color: #7f8c8d; font-size: 11px; }
            </style>
        </head>
        <body>
            <div class="container">
                <h1>Log Search</h1>
                <div class="nav-links">
                    <a href="/">Home</a>
                    <a href="/dashboard">SSH Configs</a>
                    <a href="/ssh-log-watcher">SSH Log Watchers</a>
                    <a href="/sshlogwatcher-records">SSH Log Watcher Records</a>
                    <a href="/log-search">Log Search</a>
                </div>
                <div class="search-form">
                    <form action="/log-search" method="get">
                        <div class="form-group">
                            <label for="watcherName">SSH Log Watcher:</label>
                            <select id="watcherName" name="watcherName" required onchange="this.form.submit()">
                                <option value="">Select a watcher</option>
                                ${watcherNames.joinToString("") { "<option value=\"$it\" ${if (it == watcherName) "selected" else ""}>${it}</option>" }}
                            </select>
                        </div>
                        <div class="form-group">
                            <label for="filePath">File Path:</label>
                            <select id="filePath" name="filePath">
                                <option value="">All Files</option>
                                ${filePaths.joinToString("") { "<option value=\"$it\" ${if (it == filePath) "selected" else ""}>${it}</option>" }}
                            </select>
                        </div>
                        <div class="form-group"><label for="contentQuery">Content Search:</label>
                            <input type="text" id="contentQuery" name="contentQuery" value="${contentQuery ?: ""}">
                        </div>
                        <div class="form-group"><label for="timestampQuery">Timestamp Search:</label>
                            <input type="text" id="timestampQuery" name="timestampQuery" value="${timestampQuery ?: ""}">
                        </div>
                        <div class="form-group"><label for="logPathQuery">Log Path Search:</label>
                            <input type="text" id="logPathQuery" name="logPathQuery" value="${logPathQuery ?: ""}">
                        </div>
                        <div class="form-group"><label for="operator">Search Operator:</label>
                            <select id="operator" name="operator">
                                <option value="AND" ${if (operator != "OR") "selected" else ""}>AND</option>
                                <option value="OR" ${if (operator == "OR") "selected" else ""}>OR</option>
                            </select>
                        </div>
                        <div class="form-group" style="display: flex; gap: 5px;">
                            <div style="flex: 1;"><label for="startDate">Start Date:</label>
                                <input type="datetime-local" id="startDate" name="startDate" value="${startDate ?: ""}">
                            </div>
                            <div style="flex: 1;"><label for="endDate">End Date:</label>
                                <input type="datetime-local" id="endDate" name="endDate" value="${endDate ?: ""}">
                            </div>
                        </div>
                        <div class="form-group"><label for="timezone">Timezone:</label>
                            <input type="text" id="timezone" name="timezone" value="${timezone}">
                        </div>
                        <button type="submit">Search</button>
                    </form>
                </div>
                <div class="search-results">
                    <div style="margin-bottom:5px;">
                        <a href="/log-search/download?watcherName=${watcherName ?: ""}&contentQuery=${contentQuery ?: ""}&timestampQuery=${timestampQuery ?: ""}&logPathQuery=${logPathQuery ?: ""}&operator=${operator ?: "AND"}&startDate=${startDate ?: ""}&endDate=${endDate ?: ""}&timezone=${timezone}">Download Results</a>
                    </div>
                    <h2>Search Results</h2>
                    ${if ((contentQuery.isNullOrBlank() && timestampQuery.isNullOrBlank() && logPathQuery.isNullOrBlank()) || watcherName.isNullOrBlank()) {
            """<div class="no-results"><p>Please select a watcher and enter at least one search term.</p></div>"""
        } else if (totalResultsCount == 0) {
            """<div class="no-results"><p>No results found.</p></div>"""
        } else {
            searchResults.joinToString("\n") { result ->
                """<div class="log-entry"><div class="log-entry-header"><strong>${result.timestamp}</strong> | ${result.logPath}</div><div class="log-entry-content">${result.content}</div></div>"""
            }
        }}
                </div>
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    @GetMapping("/download", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun downloadLogs(
        @RequestParam watcherName: String,
        @RequestParam(required = false) contentQuery: String?,
        @RequestParam(required = false) timestampQuery: String?,
        @RequestParam(required = false) logPathQuery: String?,
        @RequestParam(required = false) operator: String?,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(required = false, defaultValue = "UTC") timezone: String,
        response: HttpServletResponse
    ) {
        response.contentType = "text/plain"
        response.setHeader("Content-Disposition", "attachment; filename=\"logs.txt\"")

        val writer = response.writer
        val results = searchLogs(watcherName, contentQuery, timestampQuery, logPathQuery, operator ?: "AND", startDate, endDate, timezone, 1, Int.MAX_VALUE)

        results.forEach { r ->
            writer.write("${r.timestamp} | ${r.logPath} | ${r.content}\n")
        }
        writer.flush()
    }

    private data class SearchResult(val timestamp: String, val logPath: String, val content: String)

    private fun searchLogs(
        watcherName: String,
        contentQuery: String?,
        timestampQuery: String?,
        logPathQuery: String?,
        operator: String,
        startDate: String?,
        endDate: String?,
        timezone: String,
        page: Int,
        pageSize: Int
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        try {
            val indexDir = luceneIngestionService.getBaseIndexDir().resolve(watcherName)
            if (!Files.exists(indexDir)) return emptyList()

            FSDirectory.open(indexDir).use { directory ->
                if (DirectoryReader.indexExists(directory)) {
                    DirectoryReader.open(directory).use { reader ->
                        val searcher = IndexSearcher(reader)
                        val analyzer = luceneIngestionService.getAnalyzer()
                        val queryBuilder = BooleanQuery.Builder()
                        val booleanOperator = if (operator.equals("OR", true)) BooleanClause.Occur.SHOULD else BooleanClause.Occur.MUST

                        if (!contentQuery.isNullOrBlank()) {
                            queryBuilder.add(QueryParser("content", analyzer).parse(contentQuery.trim()), booleanOperator)
                        }
                        if (!timestampQuery.isNullOrBlank()) {
                            val tq = timestampQuery.trim()
                            queryBuilder.add(if (tq.contains("*") || tq.contains("?")) WildcardQuery(Term("logStrTimestamp", tq)) else TermQuery(Term("logStrTimestamp", tq)), booleanOperator)
                        }
                        if (!logPathQuery.isNullOrBlank()) {
                            val lpq = logPathQuery.trim()
                            queryBuilder.add(if (lpq.contains("*") || lpq.contains("?")) WildcardQuery(Term("logPath", lpq)) else TermQuery(Term("logPath", lpq)), booleanOperator)
                        }
                        if (!startDate.isNullOrBlank() || !endDate.isNullOrBlank()) {
                            val timeZone = ZoneId.of(timezone)
                            var startTs = Long.MIN_VALUE
                            var endTs = Long.MAX_VALUE
                            if (!startDate.isNullOrBlank()) startTs = LocalDateTime.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(timeZone).toInstant().toEpochMilli()
                            if (!endDate.isNullOrBlank()) endTs = LocalDateTime.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(timeZone).toInstant().toEpochMilli()
                            queryBuilder.add(LongPoint.newRangeQuery("logLongTimestamp", startTs, endTs), BooleanClause.Occur.MUST)
                        }

                        if (queryBuilder.build().clauses().isEmpty()) return emptyList()

                        val query = queryBuilder.build()
                        val startIndex = (page - 1) * pageSize
                        val topDocs = searcher.search(query, startIndex + pageSize)
                        val endIndex = minOf(startIndex + pageSize, topDocs.totalHits.value.toInt())

                        for (i in startIndex until endIndex) {
                            if (i < topDocs.scoreDocs.size) {
                                val doc = searcher.doc(topDocs.scoreDocs[i].doc)
                                results.add(SearchResult(
                                    timestamp = doc.get("logStrTimestamp") ?: "",
                                    logPath = doc.get("logPath") ?: "",
                                    content = doc.get("content") ?: ""
                                ))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("Error searching logs: ${e.message}")
            e.printStackTrace()
        }
        return results
    }
}
