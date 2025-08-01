package com.cleanbuild.tech.monolit.controller

import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.WildcardQuery
import org.apache.lucene.store.FSDirectory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import java.nio.file.Path
import javax.sql.DataSource
import org.junit.jupiter.api.Assertions.*

/**
 * Test specifically designed to verify case sensitivity behavior in Lucene search
 */
class LogSearchCaseSensitivityTest {

    @TempDir
    lateinit var indexDir: Path
    
    private lateinit var dataSource: DataSource
    
    @BeforeEach
    fun setup() {
        // Set up the temporary directory for Lucene indexes
        System.setProperty("lucene.index.dir", indexDir.toString())
        
        // Mock the data source
        dataSource = mock()
    }
    
    @AfterEach
    fun cleanup() {
        // Reset the system property
        System.clearProperty("lucene.index.dir")
    }
    
    private fun createTestIndex(watcherName: String, documents: List<Map<String, String>>) {
        val watcherDir = indexDir.resolve(watcherName)
        watcherDir.toFile().mkdir()
        
        val directory = FSDirectory.open(watcherDir)
        val config = IndexWriterConfig()
        val writer = IndexWriter(directory, config)
        
        documents.forEach { docFields ->
            val doc = Document()
            docFields.forEach { (field, value) ->
                when (field) {
                    "content" -> doc.add(TextField(field, value.lowercase(), Field.Store.YES))
                    "logPath" -> doc.add(TextField(field, value.lowercase(), Field.Store.YES))
                    else -> doc.add(StringField(field, value, Field.Store.YES))
                }
            }
            writer.addDocument(doc)
        }
        
        writer.close()
        directory.close()
    }
    
    /**
     * Test to verify if Lucene search is case sensitive or insensitive
     */
    @Test
    fun `verify case sensitivity in Lucene search`() {
        // Create test index with sample documents
        val watcherName = "test-watcher"
        val testDocuments = listOf(
            mapOf(
                "content" to "This is a test log entry with ERROR message",
                "logStrTimestamp" to "2025-08-01 12:00:00",
                "logPath" to "/logs/app.log"
            ),
            mapOf(
                "content" to "Another log entry with Warning",
                "logStrTimestamp" to "2025-08-01 12:05:00",
                "logPath" to "/logs/app.log"
            ),
            mapOf(
                "content" to "Normal log entry with debug info",
                "logStrTimestamp" to "2025-08-01 12:10:00",
                "logPath" to "/logs/app.log"
            )
        )
        createTestIndex(watcherName, testDocuments)
        
        // Search with lowercase terms
        val lowercaseResults = performSearch(watcherName, "error")
        println("[DEBUG_LOG] Lowercase search 'error' results: ${lowercaseResults.size}")
        
        // Search with uppercase terms
        val uppercaseResults = performSearch(watcherName, "ERROR")
        println("[DEBUG_LOG] Uppercase search 'ERROR' results: ${uppercaseResults.size}")
        
        // Search with mixed case terms
        val mixedCaseResults = performSearch(watcherName, "Error")
        println("[DEBUG_LOG] Mixed case search 'Error' results: ${mixedCaseResults.size}")
        
        // If all searches return the same document, then search is case insensitive
        // If they return different results, then search is case sensitive
        val isCaseInsensitive = lowercaseResults.size == uppercaseResults.size && 
                               uppercaseResults.size == mixedCaseResults.size &&
                               lowercaseResults.size > 0
        
        println("[DEBUG_LOG] Is search case insensitive? $isCaseInsensitive")
        
        // Add assertions to verify case sensitivity
        if (isCaseInsensitive) {
            // If search is case insensitive, all searches should return the same document
            assertEquals(1, lowercaseResults.size, "Lowercase search should find 1 document")
            assertEquals(1, uppercaseResults.size, "Uppercase search should find 1 document")
            assertEquals(1, mixedCaseResults.size, "Mixed case search should find 1 document")
        } else {
            // If search is case sensitive, only the exact case match should return results
            // In our test data, we have "ERROR" in uppercase
            assertTrue(uppercaseResults.size > 0, "Uppercase search should find documents")
            
            // This assertion will fail if search is case sensitive
            assertTrue(lowercaseResults.size > 0, "Lowercase search should find documents if case insensitive")
            assertTrue(mixedCaseResults.size > 0, "Mixed case search should find documents if case insensitive")
        }
    }
    
    /**
     * Helper method to perform search using WildcardQuery
     */
    private fun performSearch(watcherName: String, contentQuery: String): List<Map<String, String>> {
        val results = mutableListOf<Map<String, String>>()
        
        try {
            val watcherDir = indexDir.resolve(watcherName)
            val directory = FSDirectory.open(watcherDir)
            
            if (DirectoryReader.indexExists(directory)) {
                val reader = DirectoryReader.open(directory)
                val searcher = IndexSearcher(reader)
                
                // Build the query
                val queryBuilder = BooleanQuery.Builder()
                
                // Add content search using wildcard query with lowercase for case insensitivity
                val contentTerm = Term("content", "*${contentQuery.lowercase()}*")
                val contentWildcardQuery = WildcardQuery(contentTerm)
                queryBuilder.add(contentWildcardQuery, BooleanClause.Occur.MUST)
                
                val query = queryBuilder.build()
                
                // Execute search
                val topDocs = searcher.search(query, 10)
                
                // Process results
                for (i in 0 until topDocs.scoreDocs.size) {
                    val scoreDoc = topDocs.scoreDocs[i]
                    val doc = searcher.doc(scoreDoc.doc)
                    
                    results.add(
                        mapOf(
                            "timestamp" to (doc.get("logStrTimestamp") ?: ""),
                            "filePath" to (doc.get("logPath") ?: ""),
                            "content" to (doc.get("content") ?: "")
                        )
                    )
                }
                
                reader.close()
            }
            
            directory.close()
        } catch (e: Exception) {
            println("[DEBUG_LOG] Error searching logs: ${e.message}")
            e.printStackTrace()
        }
        
        return results
    }
}