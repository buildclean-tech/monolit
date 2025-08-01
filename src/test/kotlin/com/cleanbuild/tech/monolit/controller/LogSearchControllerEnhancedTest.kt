package com.cleanbuild.tech.monolit.controller

import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.nio.file.Path
import java.lang.reflect.Method
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import javax.sql.DataSource

/**
 * Enhanced test cases for LogSearchController without mocking the dependencies
 * but creating real usable instances for Lucene search functionality.
 */
class LogSearchControllerEnhancedTest {

    private lateinit var dataSource: DataSource
    private lateinit var controller: LogSearchController
    private lateinit var mockMvc: MockMvc
    private lateinit var searchLogsMethod: Method

    @TempDir
    lateinit var tempDir: Path

    private lateinit var indexDir: Path

    @BeforeEach
    fun setup() {
        // Set up the temporary Lucene index directory
        indexDir = tempDir.resolve("lucene-indexes")
        indexDir.toFile().mkdir()
        
        // Set the system property for the Lucene index directory
        System.setProperty("lucene.index.dir", indexDir.toString())
        
        // Create a minimal mock for DataSource since we're focusing on testing Lucene search
        dataSource = Mockito.mock(DataSource::class.java)
        
        // Create controller with the DataSource
        controller = LogSearchController(dataSource)
        
        // Set up MockMvc for testing web endpoints
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
        
        // Get access to the private searchLogs method using reflection
        searchLogsMethod = LogSearchController::class.java.getDeclaredMethod(
            "searchLogs",
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Int::class.java,
            Int::class.java
        )
        searchLogsMethod.isAccessible = true
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
                    "content" -> doc.add(TextField(field, value, Field.Store.YES))
                    else -> doc.add(StringField(field, value, Field.Store.YES))
                }
            }
            writer.addDocument(doc)
        }
        
        writer.close()
        directory.close()
    }

    // Note: We're not testing getAllSSHLogWatcherNames since it requires a real database connection
    // and we're focusing on testing the Lucene search functionality
    
    // Note: We're not testing getUniqueFilePaths directly since it's tested indirectly
    // through the searchLogs tests

    @Test
    fun `searchLogs returns results when content query matches`() {
        // Create test index with sample documents
        val watcherName = "test-watcher"
        val testDocuments = listOf(
            mapOf(
                "content" to "This is a test log entry with error message",
                "logStrTimestamp" to "2025-08-01 12:00:00",
                "logPath" to "/logs/app.log"
            ),
            mapOf(
                "content" to "Another log entry with warning",
                "logStrTimestamp" to "2025-08-01 12:05:00",
                "logPath" to "/logs/app.log"
            ),
            mapOf(
                "content" to "Normal log entry",
                "logStrTimestamp" to "2025-08-01 12:10:00",
                "logPath" to "/logs/app.log"
            )
        )
        createTestIndex(watcherName, testDocuments)
        
        // Invoke the private searchLogs method
        val results = searchLogsMethod.invoke(
            controller,
            watcherName,
            null,
            "error",
            null,
            null,
            "AND",
            null,
            null,
            1,
            10
        ) as List<*>
        
        // Verify results
        assertEquals(1, results.size)
        val result = results[0]!!
        val timestampField = result.javaClass.getDeclaredField("timestamp")
        val contentField = result.javaClass.getDeclaredField("content")
        val filePathField = result.javaClass.getDeclaredField("filePath")
        
        timestampField.isAccessible = true
        contentField.isAccessible = true
        filePathField.isAccessible = true
        
        assertEquals("2025-08-01 12:00:00", timestampField.get(result))
        assertTrue(contentField.get(result).toString().contains("error"))
        assertEquals("/logs/app.log", filePathField.get(result))
    }

    @Test
    fun `searchLogs returns results when timestamp query matches`() {
        // Create test index with sample documents
        val watcherName = "test-watcher"
        val testDocuments = listOf(
            mapOf(
                "content" to "This is a test log entry",
                "logStrTimestamp" to "2025-08-01 12:00:00",
                "logPath" to "/logs/app.log"
            ),
            mapOf(
                "content" to "Another log entry",
                "logStrTimestamp" to "2025-08-01 12:05:00",
                "logPath" to "/logs/app.log"
            ),
            mapOf(
                "content" to "Normal log entry",
                "logStrTimestamp" to "2025-08-01 12:10:00",
                "logPath" to "/logs/app.log"
            )
        )
        createTestIndex(watcherName, testDocuments)
        
        // Invoke the private searchLogs method
        val results = searchLogsMethod.invoke(
            controller,
            watcherName,
            null,
            null,
            "12:05",
            null,
            "AND",
            null,
            null,
            1,
            10
        ) as List<*>
        
        // Verify results
        assertEquals(1, results.size)
        val result = results[0]!!
        val timestampField = result.javaClass.getDeclaredField("timestamp")
        timestampField.isAccessible = true
        assertTrue(timestampField.get(result).toString().contains("12:05"))
    }

    @Test
    fun `searchLogs returns results when logPath query matches`() {
        // Create test index with sample documents
        val watcherName = "test-watcher"
        val testDocuments = listOf(
            mapOf(
                "content" to "This is a test log entry",
                "logStrTimestamp" to "2025-08-01 12:00:00",
                "logPath" to "/logs/app.log"
            ),
            mapOf(
                "content" to "Another log entry",
                "logStrTimestamp" to "2025-08-01 12:05:00",
                "logPath" to "/logs/system.log"
            )
        )
        createTestIndex(watcherName, testDocuments)
        
        // Invoke the private searchLogs method
        val results = searchLogsMethod.invoke(
            controller,
            watcherName,
            null,
            null,
            null,
            "system",
            "AND",
            null,
            null,
            1,
            10
        ) as List<*>
        
        // Verify results
        assertEquals(1, results.size)
        val result = results[0]!!
        val filePathField = result.javaClass.getDeclaredField("filePath")
        filePathField.isAccessible = true
        assertTrue(filePathField.get(result).toString().contains("system"))
    }

    @Test
    fun `searchLogs uses OR operator correctly`() {
        // Create test index with sample documents
        val watcherName = "test-watcher"
        val testDocuments = listOf(
            mapOf(
                "content" to "This is a test log entry with error",
                "logStrTimestamp" to "2025-08-01 12:00:00",
                "logPath" to "/logs/app.log"
            ),
            mapOf(
                "content" to "Another log entry",
                "logStrTimestamp" to "2025-08-01 12:05:00",
                "logPath" to "/logs/system.log"
            )
        )
        createTestIndex(watcherName, testDocuments)
        
        // Invoke the private searchLogs method with OR operator
        val results = searchLogsMethod.invoke(
            controller,
            watcherName,
            null,
            "error",
            null,
            "system",
            "OR",
            null,
            null,
            1,
            10
        ) as List<*>
        
        // Verify results - should match both documents
        assertEquals(2, results.size)
    }

    @Test
    fun `searchLogs uses AND operator correctly`() {
        // Create test index with sample documents
        val watcherName = "test-watcher"
        val testDocuments = listOf(
            mapOf(
                "content" to "This is a test log entry with error",
                "logStrTimestamp" to "2025-08-01 12:00:00",
                "logPath" to "/logs/app.log"
            ),
            mapOf(
                "content" to "Another log entry with error",
                "logStrTimestamp" to "2025-08-01 12:05:00",
                "logPath" to "/logs/system.log"
            ),
            mapOf(
                "content" to "Log entry with both error and warning",
                "logStrTimestamp" to "2025-08-01 12:10:00",
                "logPath" to "/logs/combined.log"
            )
        )
        createTestIndex(watcherName, testDocuments)
        
        // Invoke the private searchLogs method with AND operator
        val results = searchLogsMethod.invoke(
            controller,
            watcherName,
            null,
            "error",
            null,
            "combined",
            "AND",
            null,
            null,
            1,
            10
        ) as List<*>
        
        // Verify results - should match only the document that has both "error" and "combined"
        assertEquals(1, results.size)
        val result = results[0]!!
        val contentField = result.javaClass.getDeclaredField("content")
        val filePathField = result.javaClass.getDeclaredField("filePath")
        contentField.isAccessible = true
        filePathField.isAccessible = true
        assertTrue(contentField.get(result).toString().contains("error"))
        assertTrue(filePathField.get(result).toString().contains("combined"))
    }

    @Test
    fun `searchLogs handles filePath filter correctly`() {
        // Create test index with sample documents
        val watcherName = "test-watcher"
        val testDocuments = listOf(
            mapOf(
                "content" to "This is a test log entry",
                "logStrTimestamp" to "2025-08-01 12:00:00",
                "logPath" to "/logs/app.log"
            ),
            mapOf(
                "content" to "Another log entry",
                "logStrTimestamp" to "2025-08-01 12:05:00",
                "logPath" to "/logs/system.log"
            )
        )
        createTestIndex(watcherName, testDocuments)
        
        // Invoke the private searchLogs method with specific filePath
        val results = searchLogsMethod.invoke(
            controller,
            watcherName,
            "/logs/app.log",
            "test",
            null,
            null,
            "AND",
            null,
            null,
            1,
            10
        ) as List<*>
        
        // Verify results
        assertEquals(1, results.size)
        val result = results[0]!!
        val filePathField = result.javaClass.getDeclaredField("filePath")
        filePathField.isAccessible = true
        assertEquals("/logs/app.log", filePathField.get(result))
    }

    @Test
    fun `searchLogs handles pagination correctly`() {
        // Create test index with sample documents
        val watcherName = "test-watcher"
        val testDocuments = listOf(
            mapOf(
                "content" to "Log entry 1",
                "logStrTimestamp" to "2025-08-01 12:01:00",
                "logPath" to "/logs/app.log"
            ),
            mapOf(
                "content" to "Log entry 2",
                "logStrTimestamp" to "2025-08-01 12:02:00",
                "logPath" to "/logs/app.log"
            ),
            mapOf(
                "content" to "Log entry 3",
                "logStrTimestamp" to "2025-08-01 12:03:00",
                "logPath" to "/logs/app.log"
            ),
            mapOf(
                "content" to "Log entry 4",
                "logStrTimestamp" to "2025-08-01 12:04:00",
                "logPath" to "/logs/app.log"
            )
        )
        createTestIndex(watcherName, testDocuments)
        
        // Invoke the private searchLogs method with pagination - 2 items per page
        val page1Results = searchLogsMethod.invoke(
            controller,
            watcherName,
            null,
            "entry",  // This should match all our test documents
            null,
            null,
            "AND",
            null,
            null,
            1,
            2
        ) as List<*>
        
        val page2Results = searchLogsMethod.invoke(
            controller,
            watcherName,
            null,
            "entry",  // This should match all our test documents
            null,
            null,
            "AND",
            null,
            null,
            2,
            2
        ) as List<*>
        
        // Verify results - we should have 2 results per page
        assertEquals(2, page1Results.size, "Expected 2 results on page 1")
        assertEquals(2, page2Results.size, "Expected 2 results on page 2")
        
        // Extract content from all results for verification
        val contentField = page1Results[0]!!.javaClass.getDeclaredField("content")
        contentField.isAccessible = true
        
        val page1Contents = page1Results.map { 
            contentField.get(it!!).toString() 
        }
        val page2Contents = page2Results.map { 
            contentField.get(it!!).toString() 
        }
        
        // Verify that page 1 and page 2 have different contents
        assertTrue(page1Contents.none { it in page2Contents }, 
            "Expected different results on different pages, but found overlap: Page1=$page1Contents, Page2=$page2Contents")
    }

    @Test
    fun `searchLogs returns empty list when no matches found`() {
        // Create test index with sample documents
        val watcherName = "test-watcher"
        val testDocuments = listOf(
            mapOf(
                "content" to "This is a test log entry",
                "logStrTimestamp" to "2025-08-01 12:00:00",
                "logPath" to "/logs/app.log"
            )
        )
        createTestIndex(watcherName, testDocuments)
        
        // Invoke the private searchLogs method with non-matching query
        val results = searchLogsMethod.invoke(
            controller,
            watcherName,
            null,
            "nonexistent",
            null,
            null,
            "AND",
            null,
            null,
            1,
            10
        ) as List<*>
        
        // Verify results
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchLogs returns empty list when index directory doesn't exist`() {
        // Invoke the private searchLogs method with non-existent watcher
        val results = searchLogsMethod.invoke(
            controller,
            "nonexistent-watcher",
            null,
            "test",
            null,
            null,
            "AND",
            null,
            null,
            1,
            10
        ) as List<*>
        
        // Verify results
        assertTrue(results.isEmpty())
    }

    @Test
    fun `searchLogs returns empty list when no search criteria provided`() {
        // Create test index with sample documents
        val watcherName = "test-watcher"
        val testDocuments = listOf(
            mapOf(
                "content" to "This is a test log entry",
                "logStrTimestamp" to "2025-08-01 12:00:00",
                "logPath" to "/logs/app.log"
            )
        )
        createTestIndex(watcherName, testDocuments)
        
        // Invoke the private searchLogs method with no search criteria
        val results = searchLogsMethod.invoke(
            controller,
            watcherName,
            null,
            null,
            null,
            null,
            "AND",
            null,
            null,
            1,
            10
        ) as List<*>
        
        // Verify results
        assertTrue(results.isEmpty())
    }

    // Note: We're not testing the searchPage endpoint since it requires a real database connection
    // and we're focusing on testing the Lucene search functionality directly through the searchLogs method
}