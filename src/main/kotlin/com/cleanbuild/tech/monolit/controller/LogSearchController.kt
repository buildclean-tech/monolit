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
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.sql.DataSource
import kotlin.math.pow
import java.time.LocalDate
// Add imports
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/log-search")
class LogSearchController(
    private val dataSource: DataSource,
    private val luceneIngestionService: LuceneIngestionService
) {
    private val sshLogWatcherCrud = CRUDOperation(dataSource, SSHLogWatcher::class)

    private fun getAllSSHLogWatcherNames(): List<SSHLogWatcher> =
        sshLogWatcherCrud.findAll()
   
    val timezones =
         ZoneId.getAvailableZoneIds()
            .sorted()
            .map { zoneId -> mapOf("id" to zoneId, "displayName" to zoneId) }

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
        @RequestParam(required = false) contentQuery: String?,
        @RequestParam(required = false) timestampQuery: String?,
        @RequestParam(required = false) logPathQuery: String?,
        @RequestParam(required = false) operator: String?,
        @RequestParam(required = false) startDate: String?,       // no defaultValue here
        @RequestParam(required = false) endDate: String?,         // no defaultValue here
        @RequestParam(required = false, defaultValue = "") timezone: String,
        response: HttpServletResponse
    ) {
        response.contentType = MediaType.TEXT_HTML_VALUE
        response.characterEncoding = "UTF-8"
        val showOnlyTopResults = 10000

        BufferedWriter(OutputStreamWriter(response.outputStream, Charsets.UTF_8)).use { writer ->

            val logWatchers = getAllSSHLogWatcherNames()

            val timezoneResolved =
                if (!watcherName.isNullOrBlank() && timezone.isBlank()) {
                    logWatchers.first { it.name == watcherName }.name
                } else {
                    timezone
                }

            val zone = runCatching { ZoneId.of(timezoneResolved.ifBlank { "UTC" }) }.getOrDefault(ZoneId.of("UTC"))
            val today = LocalDate.now(zone)
            val dtFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

            val effectiveStartDate = startDate?.takeIf { it.isNotBlank() } ?: today.atStartOfDay().format(dtFmt)
            val effectiveEndDate = endDate?.takeIf { it.isNotBlank() } ?: today.atTime(23, 59).format(dtFmt)

            val (totalHitCount, resultSeq) = if (watcherName != null) {
                searchLogs(
                    watcherName,
                    contentQuery,
                    timestampQuery,
                    logPathQuery,
                    operator ?: "AND",
                    effectiveStartDate,
                    effectiveEndDate,
                    timezoneResolved,
                    showOnlyTopResults
                )
            } else 0 to emptySequence()

            // Inside the controller class, add a small HTML-escape helper
            fun escAttr(s: String?): String =
                s?.replace("&", "&amp;")
                  ?.replace("<", "&lt;")
                  ?.replace(">", "&gt;")
                  ?.replace("\"", "&quot;")
                  ?.replace("'", "&#39;")
                  ?: ""

            // Before writing HTML, prepare safe values
            val contentQueryEsc = escAttr(contentQuery)
            val timestampQueryEsc = escAttr(timestampQuery)
            val logPathQueryEsc = escAttr(logPathQuery)

            // For the download link, URL-encode parameter values
            val contentQueryUrl = URLEncoder.encode(contentQuery ?: "", StandardCharsets.UTF_8)
            val timestampQueryUrl = URLEncoder.encode(timestampQuery ?: "", StandardCharsets.UTF_8)
            val logPathQueryUrl = URLEncoder.encode(logPathQuery ?: "", StandardCharsets.UTF_8)

            // Write header
            writer.write(
                """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Log Search</title>
            <link href="https://cdn.jsdelivr.net/npm/select2@4.1.0-rc.0/dist/css/select2.min.css" rel="stylesheet" />
            <script src="https://code.jquery.com/jquery-3.6.0.min.js"></script>
            <script src="https://cdn.jsdelivr.net/npm/select2@4.1.0-rc.0/dist/js/select2.min.js"></script>
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

                /* Fix overlap between timezone select (Select2) and Search button */
                .form-group .select2-container { width: 100% !important; display: block; margin-bottom: 6px; }
                .search-form form { display: flex; flex-direction: column; gap: 6px; }
                .select2-container--default .select2-selection--single {
                    height: 38px;
                    padding: 5px;
                    border: 1px solid #ddd;
                }
                .select2-container--default .select2-selection--single .select2-selection__arrow {
                    height: 36px;
                }
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
    """.trimIndent()
            )
            logWatchers.forEach {
                writer.write("<option value=\"${it.name}\" ${if (it.name == watcherName) "selected" else ""}>${it.name}</option>")
            }

            // Use the escaped values in inputs
            writer.write(
                """
                            </select>
                        </div>
                        <div class="form-group"><label for="contentQuery">Content Search:</label>
                            <input type="text" id="contentQuery" name="contentQuery" value="$contentQueryEsc">
                        </div>
                        <div class="form-group"><label for="timestampQuery">Timestamp Search:</label>
                            <input type="text" id="timestampQuery" name="timestampQuery" value="$timestampQueryEsc">
                        </div>
                        <div class="form-group"><label for="logPathQuery">Log Path Search:</label>
                            <input type="text" id="logPathQuery" name="logPathQuery" value="$logPathQueryEsc">
                        </div>
                        <div class="form-group"><label for="operator">Search Operator:</label>
                            <select id="operator" name="operator">
                                <option value="AND" ${if (operator != "OR") "selected" else ""}>AND</option>
                                <option value="OR" ${if (operator == "OR") "selected" else ""}>OR</option>
                            </select>
                        </div>
                        <div class="form-group">
                            <label for="timezone">Timezone:</label>
                                <select id="timezone" name="timezone" required class="timezone-select">
                                    ${timezones.joinToString("\n") { 
                                        """<option value="${it["id"]}" ${if (it["id"] == timezone) "selected" else ""}>${it["displayName"]}</option>""" 
                                    }}
                                </select>
                                <script>
                                    ${'$'}('.timezone-select').select2({
                                    });
                                </script>
                        </div>
                        <div class="form-group" style="display: flex; gap: 5px;">
                            <div style="flex: 1;"><label for="startDate">Start Date:</label>
                                <input type="datetime-local" id="startDate" name="startDate" value="$effectiveStartDate">
                            </div>
                            <div style="flex: 1;"><label for="endDate">End Date:</label>
                                <input type="datetime-local" id="endDate" name="endDate" value="$effectiveEndDate">
                            </div>
                        </div>
                        <div class="form-group">
                            <button type="submit">Search</button>
                        </div>
                    </form>
                </div>
                <div class="search-results">
    """.trimIndent()
            )

            // And the encoded values in the download link
            writer.write(
                """
                    <div style="margin-bottom:5px;">
                        <a href="/log-search/download?watcherName=${watcherName ?: ""}&contentQuery=$contentQueryUrl&timestampQuery=$timestampQueryUrl&logPathQuery=$logPathQueryUrl&operator=${operator ?: "AND"}&startDate=$effectiveStartDate&endDate=$effectiveEndDate&timezone=$timezoneResolved">Download Full ($totalHitCount) Results</a>
                    </div>
                    <h2>Search Results ${if (totalHitCount > 0) "(Total: $totalHitCount hits but showing only top $showOnlyTopResults )" else ""}</h2>
    """.trimIndent()
            )

            writer.flush()

            if (watcherName.isNullOrBlank()) {
                writer.write("""<div class="no-results"><p>Please select a watcher.</p></div>""")
            } else if (totalHitCount == 0) {
                writer.write("""<div class="no-results"><p>No results found.</p></div>""")
            } else {
                resultSeq.forEach { result ->
                    writer.write(
                        """
                <div class="log-entry">
                    <div class="log-entry-header"><strong>${result.timestamp}</strong> | ${result.logPath}</div>
                    <div class="log-entry-content">${result.content}</div>
                </div>
            """.trimIndent()
                    )
                    writer.flush() // flush after each entry so browser renders progressively
                }
            }

            writer.write(
                """
                </div>
            </div>
        </body>
        </html>
    """.trimIndent()
            )

            writer.flush()
        }
    }


    @GetMapping("/download", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun downloadLogs(
        @RequestParam watcherName: String,
        @RequestParam(required = false) contentQuery: String?,
        @RequestParam(required = false) timestampQuery: String?,
        @RequestParam(required = false) logPathQuery: String?,
        @RequestParam(required = false) operator: String?,
        @RequestParam(required = false) startDate: String?,   // no defaultValue here
        @RequestParam(required = false) endDate: String?,     // no defaultValue here
        @RequestParam(required = false, defaultValue = "UTC") timezone: String,
        response: HttpServletResponse
    ) {
        val zone = runCatching { ZoneId.of(timezone.ifBlank { "UTC" }) }.getOrDefault(ZoneId.of("UTC"))
        val today = LocalDate.now(zone)
        val dtFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

        val effectiveStartDate = startDate?.takeIf { it.isNotBlank() } ?: today.atStartOfDay().format(dtFmt)
        val effectiveEndDate = endDate?.takeIf { it.isNotBlank() } ?: today.atTime(23, 59).format(dtFmt)

        val results = searchLogs(
            watcherName, contentQuery, timestampQuery, logPathQuery,
            operator ?: "AND", effectiveStartDate, effectiveEndDate, timezone
        )

        response.contentType = "text/plain"
        response.setHeader("Content-Disposition", "attachment; filename=\"logs.txt\"")

        response.writer.buffered().use { writer ->
            writer.write("watcherName=${watcherName}-contentQuery=${contentQuery}-timestampQuery=${timestampQuery}-logPathQuery=${logPathQuery}-operator=${operator}-startDate=${effectiveStartDate}-endDate=${effectiveEndDate}-timezone=${timezone}\n")
            writer.write("Total Results: ${results.first}\n\n")
            results.second.forEach {
                writer.write("${it.timestamp} | ${it.logPath}\n${it.content}\n\n")
            }
        }
    }

    data class SearchResult(val timestamp: String, val logPath: String, val content: String)

    fun searchLogs(
        watcherName: String,
        contentQuery: String?,
        timestampQuery: String?,
        logPathQuery: String?,
        operator: String,
        startDate: String?,
        endDate: String?,
        timezone: String,
        limitResults: Int = Integer.MAX_VALUE
    ): Pair<Int, Sequence<SearchResult>>{
        val results = mutableListOf<SearchResult>()
        try {
            val indexDir = luceneIngestionService.getBaseIndexDir().resolve(watcherName)
            if (!Files.exists(indexDir)) return Pair(0, emptySequence())
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

            val builtQuery = queryBuilder.build()

            val hitCount = FSDirectory.open(indexDir).use { directory ->

                if (DirectoryReader.indexExists(directory)) {
                    return@use DirectoryReader.open(directory).use { reader ->

                        val searcher = IndexSearcher(reader)
                        val hitCount = searcher.count(builtQuery)

                        return@use hitCount
                    }
                }
                else
                    return@use 0
            }

            val searchSequence = sequence {
                FSDirectory.open(indexDir).use { directory ->

                    if (DirectoryReader.indexExists(directory)) {
                        return@use DirectoryReader.open(directory).use { reader ->

                            val searcher = IndexSearcher(reader)
                            var maxDocs = limitResults
                            val batchSize = 10.0.pow(6).toInt()

                            var topDocs = searcher.search(
                                builtQuery, batchSize,
                                Sort(SortField("logLongTimestamp", SortField.Type.LONG, true)),
                                false
                            )

                            while (topDocs.totalHits.value>0 && maxDocs>0) {
                                topDocs.scoreDocs.forEach {
                                    val doc = searcher.storedFields().document(it.doc)
                                    if(--maxDocs>0)
                                    yield(
                                        SearchResult(
                                            timestamp = doc.get("logStrTimestamp") ?: "",
                                            logPath = doc.get("logPath") ?: "",
                                            content = doc.get("content") ?: ""
                                        )
                                    )
                                }
                                
                                // Only continue if we have results
                                if (topDocs.scoreDocs.isNotEmpty()) {
                                    topDocs = searcher.searchAfter(topDocs.scoreDocs.last(), builtQuery, batchSize,
                                        Sort(SortField("logLongTimestamp", SortField.Type.LONG, true)), false)
                                } else {
                                    // Break the loop if no more results
                                    break
                                }
                            }
                        }
                    }
                }
            }

            return Pair(hitCount, searchSequence)

        } catch (e: Exception) {
            println("Error searching logs: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}