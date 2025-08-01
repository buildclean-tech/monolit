package com.cleanbuild.tech.monolit.controller

import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcher
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.repository.CRUDOperation
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TermQuery
import org.apache.lucene.search.WildcardQuery
import org.apache.lucene.store.FSDirectory
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.sql.DataSource

@RestController
@RequestMapping("/log-search")
class LogSearchController(private val dataSource: DataSource) {

    private val sshLogWatcherCrud = CRUDOperation(dataSource, SSHLogWatcher::class)
    private val baseIndexDir = Paths.get(System.getProperty("lucene.index.dir", "lucene-indexes"))
    private val analyzer = StandardAnalyzer()

    // Get all SSHLogWatcher names for the dropdown
    private fun getAllSSHLogWatcherNames(): List<String> {
        return sshLogWatcherCrud.findAll().map { it.name }
    }
    
    // Get all unique file paths for a specific watcher
    private fun getUniqueFilePaths(watcherName: String): List<String> {
        val filePaths = mutableSetOf<String>()
        
        try {
            val indexDir = baseIndexDir.resolve(watcherName)
            if (!indexDir.toFile().exists()) {
                return emptyList()
            }
            
            val directory = FSDirectory.open(indexDir)
            
            // Only proceed if the index exists and can be read
            if (DirectoryReader.indexExists(directory)) {
                val reader = DirectoryReader.open(directory)
                val searcher = IndexSearcher(reader)
                
                // Get all documents
                val query = BooleanQuery.Builder().build() // Empty query to match all docs
                val topDocs = searcher.search(query, Integer.MAX_VALUE)
                
                // Extract unique file paths
                for (scoreDoc in topDocs.scoreDocs) {
                    val doc = searcher.doc(scoreDoc.doc)
                    val filePath = doc.get("logPath")
                    if (filePath != null) {
                        filePaths.add(filePath)
                    }
                }
                
                reader.close()
            }
            
            directory.close()
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
        @RequestParam(required = false, defaultValue = "20") pageSize: Int
    ): String {
        val watcherNames = getAllSSHLogWatcherNames()
        
        // Get file paths for the selected watcher
        val filePaths = if (!watcherName.isNullOrBlank()) {
            getUniqueFilePaths(watcherName)
        } else {
            emptyList()
        }
        
        // Search results
        val searchResults = if ((!contentQuery.isNullOrBlank() || !timestampQuery.isNullOrBlank() || !logPathQuery.isNullOrBlank()) && !watcherName.isNullOrBlank()) {
            searchLogs(watcherName, filePath, contentQuery, timestampQuery, logPathQuery, operator ?: "AND", startDate, endDate, page, pageSize)
        } else {
            emptyList()
        }
        
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Log Search</title>
            <style>
                body {
                    font-family: Arial, sans-serif;
                    line-height: 1.6;
                    margin: 0;
                    padding: 20px;
                    color: #333;
                    background-color: #f5f5f5;
                }
                .container {
                    max-width: 1200px;
                    margin: 0 auto;
                    padding: 20px;
                    background-color: #fff;
                    border-radius: 5px;
                    box-shadow: 0 2px 5px rgba(0,0,0,0.1);
                }
                h1, h2 {
                    color: #2c3e50;
                }
                .search-form {
                    margin-bottom: 20px;
                    padding: 15px;
                    background-color: #f8f9fa;
                    border-radius: 4px;
                }
                .form-group {
                    margin-bottom: 15px;
                }
                label {
                    display: block;
                    margin-bottom: 5px;
                    font-weight: bold;
                }
                select, input, button {
                    padding: 8px;
                    border: 1px solid #ddd;
                    border-radius: 4px;
                    width: 100%;
                    box-sizing: border-box;
                }
                button {
                    background-color: #3498db;
                    color: white;
                    border: none;
                    cursor: pointer;
                    font-weight: bold;
                }
                button:hover {
                    background-color: #2980b9;
                }
                .search-results {
                    margin-top: 20px;
                }
                .log-entry {
                    margin-bottom: 15px;
                    padding: 15px;
                    background-color: #f8f9fa;
                    border-left: 4px solid #3498db;
                    border-radius: 4px;
                    white-space: pre-wrap;
                    word-break: break-word;
                }
                .log-entry-header {
                    margin-bottom: 10px;
                    font-size: 0.9em;
                    color: #7f8c8d;
                }
                .log-entry-content {
                    font-family: monospace;
                }
                .pagination {
                    display: flex;
                    justify-content: center;
                    margin-top: 20px;
                }
                .pagination a {
                    margin: 0 5px;
                    padding: 8px 12px;
                    background-color: #f8f9fa;
                    border: 1px solid #ddd;
                    border-radius: 4px;
                    text-decoration: none;
                    color: #333;
                }
                .pagination a.active {
                    background-color: #3498db;
                    color: white;
                    border-color: #3498db;
                }
                .nav-links {
                    margin-bottom: 20px;
                    text-align: center;
                }
                .nav-links a {
                    margin-right: 15px;
                    color: #3498db;
                    text-decoration: none;
                }
                .no-results {
                    padding: 15px;
                    background-color: #f8f9fa;
                    border-radius: 4px;
                    text-align: center;
                    color: #7f8c8d;
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
                                ${watcherNames.joinToString("") { 
                                    "<option value=\"$it\" ${if (it == watcherName) "selected" else ""}>${it}</option>"
                                }}
                            </select>
                        </div>
                        <div class="form-group">
                            <label for="filePath">File Path:</label>
                            <select id="filePath" name="filePath">
                                <option value="">All Files</option>
                                ${filePaths.joinToString("") { 
                                    "<option value=\"$it\" ${if (it == filePath) "selected" else ""}>${it}</option>"
                                }}
                            </select>
                        </div>
                        <div class="form-group">
                            <label for="contentQuery">Content Search:</label>
                            <input type="text" id="contentQuery" name="contentQuery" value="${contentQuery ?: ""}" placeholder="Search in log content..." 
                                   style="${if (!contentQuery.isNullOrBlank()) "background-color: #e6ffe6;" else ""}">
                        </div>
                        <div class="form-group">
                            <label for="timestampQuery">Timestamp Search:</label>
                            <input type="text" id="timestampQuery" name="timestampQuery" value="${timestampQuery ?: ""}" placeholder="Search in timestamps..." 
                                   style="${if (!timestampQuery.isNullOrBlank()) "background-color: #e6ffe6;" else ""}">
                        </div>
                        <div class="form-group">
                            <label for="logPathQuery">Log Path Search:</label>
                            <input type="text" id="logPathQuery" name="logPathQuery" value="${logPathQuery ?: ""}" placeholder="Search in log paths..." 
                                   style="${if (!logPathQuery.isNullOrBlank()) "background-color: #e6ffe6;" else ""}">
                        </div>
                        <div class="form-group">
                            <label for="operator">Search Operator:</label>
                            <select id="operator" name="operator">
                                <option value="AND" ${if (operator != "OR") "selected" else ""}>AND - All fields must match</option>
                                <option value="OR" ${if (operator == "OR") "selected" else ""}>OR - Any field can match</option>
                            </select>
                        </div>
                        <div class="form-group" style="display: flex; gap: 10px;">
                            <div style="flex: 1;">
                                <label for="startDate">Start Date:</label>
                                <input type="datetime-local" id="startDate" name="startDate" value="${startDate ?: ""}">
                            </div>
                            <div style="flex: 1;">
                                <label for="endDate">End Date:</label>
                                <input type="datetime-local" id="endDate" name="endDate" value="${endDate ?: ""}">
                            </div>
                        </div>
                        <button type="submit">Search</button>
                    </form>
                </div>
                
                <div class="search-results">
                    <h2>Search Results</h2>
                    
                    ${if ((contentQuery.isNullOrBlank() && timestampQuery.isNullOrBlank() && logPathQuery.isNullOrBlank()) || watcherName.isNullOrBlank()) {
                        """
                        <div class="no-results">
                            <p>Please select a watcher and enter at least one search term to see results.</p>
                        </div>
                        """
                    } else if (searchResults.isEmpty()) {
                        """
                        <div class="no-results">
                            <p>No results found for your search criteria.</p>
                        </div>
                        """
                    } else {
                        searchResults.joinToString("\n") { result ->
                            """
                            <div class="log-entry">
                                <div class="log-entry-header">
                                    <strong>Timestamp:</strong> ${result.timestamp} | <strong>File:</strong> ${result.filePath}
                                </div>
                                <div class="log-entry-content">
                                    ${result.content}
                                </div>
                            </div>
                            """
                        }
                    }}
                    
                    ${if (searchResults.isNotEmpty()) {
                        """
                        <div class="pagination">
                            ${if (page > 1) {
                                """<a href="/log-search?watcherName=${watcherName}&filePath=${filePath ?: ""}&contentQuery=${contentQuery ?: ""}&timestampQuery=${timestampQuery ?: ""}&logPathQuery=${logPathQuery ?: ""}&operator=${operator ?: "AND"}&startDate=${startDate ?: ""}&endDate=${endDate ?: ""}&page=${page - 1}&pageSize=${pageSize}">Previous</a>"""
                            } else ""}
                            <a href="#" class="active">${page}</a>
                            <a href="/log-search?watcherName=${watcherName}&filePath=${filePath ?: ""}&contentQuery=${contentQuery ?: ""}&timestampQuery=${timestampQuery ?: ""}&logPathQuery=${logPathQuery ?: ""}&operator=${operator ?: "AND"}&startDate=${startDate ?: ""}&endDate=${endDate ?: ""}&page=${page + 1}&pageSize=${pageSize}">Next</a>
                        </div>
                        """
                    } else ""}
                </div>
            </div>
        </body>
        </html>
        """.trimIndent()
    }
    
    private data class SearchResult(
        val timestamp: String,
        val filePath: String,
        val content: String
    )
    
    private fun searchLogs(
        watcherName: String,
        filePath: String?,
        contentQuery: String?,
        timestampQuery: String?,
        logPathQuery: String?,
        operator: String,
        startDate: String?,
        endDate: String?,
        page: Int,
        pageSize: Int
    ): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        
        try {
            val indexDir = baseIndexDir.resolve(watcherName)
            if (!indexDir.toFile().exists()) {
                return emptyList()
            }
            
            val directory = FSDirectory.open(indexDir)
            
            // Only proceed if the index exists and can be read
            if (DirectoryReader.indexExists(directory)) {
                val reader = DirectoryReader.open(directory)
                val searcher = IndexSearcher(reader)
                
                // Build the query
                val queryBuilder = BooleanQuery.Builder()
                
                // Determine the boolean operator to use
                val booleanOperator = if (operator.equals("OR", ignoreCase = true)) {
                    BooleanClause.Occur.SHOULD
                } else {
                    BooleanClause.Occur.MUST
                }
                
                // Add content search using wildcard query if provided
                if (!contentQuery.isNullOrBlank()) {
                    // Convert to lowercase for case-insensitive search
                    val contentTerm = Term("content", "*${contentQuery.lowercase()}*")
                    val contentWildcardQuery = WildcardQuery(contentTerm)
                    queryBuilder.add(contentWildcardQuery, booleanOperator)
                }
                
                // Add timestamp search if provided
                if (!timestampQuery.isNullOrBlank()) {
                    // Convert to lowercase for case-insensitive search
                    val timestampTerm = Term("logStrTimestamp", "*${timestampQuery.lowercase()}*")
                    val timestampWildcardQuery = WildcardQuery(timestampTerm)
                    queryBuilder.add(timestampWildcardQuery, booleanOperator)
                }
                
                // Add log path search if provided
                if (!logPathQuery.isNullOrBlank()) {
                    // Convert to lowercase for case-insensitive search
                    val logPathTerm = Term("logPath", "*${logPathQuery.lowercase()}*")
                    val logPathWildcardQuery = WildcardQuery(logPathTerm)
                    queryBuilder.add(logPathWildcardQuery, booleanOperator)
                }
                
                // Add file path filter if provided (this is always a MUST condition)
                if (!filePath.isNullOrBlank()) {
                    // Convert to lowercase for case-insensitive search
                    val filePathTerm = Term("logPath", filePath.lowercase())
                    val filePathQuery = TermQuery(filePathTerm)
                    queryBuilder.add(filePathQuery, BooleanClause.Occur.MUST)
                }
                
                // Add date range if provided
                if (!startDate.isNullOrBlank() || !endDate.isNullOrBlank()) {
                    // TODO: Implement date range filtering
                    // This would require parsing the dates and converting to timestamp longs
                    // Then using a NumericRangeQuery on the logLongTimestamp field
                }
                
                // If no search criteria were added, return empty results
                if (queryBuilder.build().clauses().isEmpty()) {
                    return emptyList()
                }
                
                val query = queryBuilder.build()
                
                // Calculate pagination
                val startIndex = (page - 1) * pageSize
                
                // Execute search
                val topDocs = searcher.search(query, startIndex + pageSize)
                
                // Process results
                val endIndex = minOf(startIndex + pageSize, topDocs.totalHits.value.toInt())
                for (i in startIndex until endIndex) {
                    if (i < topDocs.scoreDocs.size) {
                        val scoreDoc = topDocs.scoreDocs[i]
                        val doc = searcher.doc(scoreDoc.doc)
                        
                        results.add(
                            SearchResult(
                                timestamp = doc.get("logStrTimestamp") ?: "",
                                filePath = doc.get("logPath") ?: "",
                                content = doc.get("content") ?: ""
                            )
                        )
                    }
                }
                
                reader.close()
            }
            
            directory.close()
        } catch (e: Exception) {
            // Log the error but return empty results
            println("Error searching logs: ${e.message}")
            e.printStackTrace()
        }
        
        return results
    }
}