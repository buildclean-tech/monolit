package com.cleanbuild.tech.monolit.controller

import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcher
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.repository.CRUDOperation
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.nio.file.Path
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource
import java.lang.reflect.Method

@WebMvcTest(LogSearchController::class)
class LogSearchControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var dataSource: DataSource

    @TempDir
    lateinit var tempDir: Path

    private lateinit var indexDir: Path
    private lateinit var searchLogsMethod: Method

    @BeforeEach
    fun setup() {
        // Set up the temporary Lucene index directory
        indexDir = tempDir.resolve("lucene-indexes")
        indexDir.toFile().mkdir()
        
        // Set the system property for the Lucene index directory
        System.setProperty("lucene.index.dir", indexDir.toString())
        
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

    // Note: We're focusing on testing the searchLogs method directly as per the issue description
    // The UI integration test requires more complex Spring context setup

    @Test
    fun `searchLogs returns results when content query matches`() {
        // Create a controller instance with mocked dependencies
        val controller = LogSearchController(dataSource)
        
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
        assert(results.size == 1)
        val result = results[0]!!
        val timestampField = result.javaClass.getDeclaredField("timestamp")
        val contentField = result.javaClass.getDeclaredField("content")
        val filePathField = result.javaClass.getDeclaredField("filePath")
        
        timestampField.isAccessible = true
        contentField.isAccessible = true
        filePathField.isAccessible = true
        
        assert(timestampField.get(result) == "2025-08-01 12:00:00")
        assert(contentField.get(result).toString().contains("error"))
        assert(filePathField.get(result) == "/logs/app.log")
    }

    @Test
    fun `searchLogs returns results when timestamp query matches`() {
        // Create a controller instance with mocked dependencies
        val controller = LogSearchController(dataSource)
        
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
        assert(results.size == 1)
        val result = results[0]!!
        val timestampField = result.javaClass.getDeclaredField("timestamp")
        timestampField.isAccessible = true
        assert(timestampField.get(result).toString().contains("12:05"))
    }

    @Test
    fun `searchLogs returns results when logPath query matches`() {
        // Create a controller instance with mocked dependencies
        val controller = LogSearchController(dataSource)
        
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
        assert(results.size == 1)
        val result = results[0]!!
        val filePathField = result.javaClass.getDeclaredField("filePath")
        filePathField.isAccessible = true
        assert(filePathField.get(result).toString().contains("system"))
    }

    @Test
    fun `searchLogs uses OR operator correctly`() {
        // Create a controller instance with mocked dependencies
        val controller = LogSearchController(dataSource)
        
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
        assert(results.size == 2)
    }

    @Test
    fun `searchLogs uses AND operator correctly`() {
        // Create a controller instance with mocked dependencies
        val controller = LogSearchController(dataSource)
        
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
        assert(results.size == 1)
        val result = results[0]!!
        val contentField = result.javaClass.getDeclaredField("content")
        val filePathField = result.javaClass.getDeclaredField("filePath")
        contentField.isAccessible = true
        filePathField.isAccessible = true
        assert(contentField.get(result).toString().contains("error"))
        assert(filePathField.get(result).toString().contains("combined"))
    }

    @Test
    fun `searchLogs handles filePath filter correctly`() {
        // Create a controller instance with mocked dependencies
        val controller = LogSearchController(dataSource)
        
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
        assert(results.size == 1)
        val result = results[0]!!
        val filePathField = result.javaClass.getDeclaredField("filePath")
        filePathField.isAccessible = true
        assert(filePathField.get(result) == "/logs/app.log")
    }

    @Test
    fun `searchLogs handles pagination correctly`() {
        // Create a controller instance with mocked dependencies
        val controller = LogSearchController(dataSource)
        
        // Create test index with sample documents - using a smaller set for more predictable results
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
        // Using an empty string for content query to match all documents
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
        assert(page1Results.size == 2) { "Expected 2 results on page 1, but got ${page1Results.size}" }
        assert(page2Results.size == 2) { "Expected 2 results on page 2, but got ${page2Results.size}" }
        
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
        assert(page1Contents.none { it in page2Contents }) { 
            "Expected different results on different pages, but found overlap: Page1=$page1Contents, Page2=$page2Contents" 
        }
    }

    @Test
    fun `searchLogs returns empty list when no matches found`() {
        // Create a controller instance with mocked dependencies
        val controller = LogSearchController(dataSource)
        
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
        assert(results.isEmpty())
    }

    @Test
    fun `searchLogs returns empty list when index directory doesn't exist`() {
        // Create a controller instance with mocked dependencies
        val controller = LogSearchController(dataSource)
        
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
        assert(results.isEmpty())
    }

    @Test
    fun `searchLogs returns empty list when no search criteria provided`() {
        // Create a controller instance with mocked dependencies
        val controller = LogSearchController(dataSource)
        
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
        assert(results.isEmpty())
    }
}