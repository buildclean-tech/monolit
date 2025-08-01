package com.cleanbuild.tech.monolit.controller

import com.cleanbuild.tech.monolit.service.LuceneIngestionService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.`when`
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.nio.file.Path
import java.nio.file.Paths
import javax.sql.DataSource

/**
 * Simple test to verify that LogSearchController uses LuceneIngestionService for index paths
 */
class LogSearchControllerPathTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `LogSearchController uses LuceneIngestionService for index paths`() {
        // Create mocks
        val dataSource = mock<DataSource>()
        val luceneIngestionService = mock<LuceneIngestionService>()
        
        // Set up the mock to return a specific path
        val testIndexDir = tempDir.resolve("test-indexes")
        `when`(luceneIngestionService.getBaseIndexDir()).thenReturn(testIndexDir)
        
        // Create the controller with the mocks
        val controller = LogSearchController(dataSource, luceneIngestionService)
        
        // Use reflection to access the private methods
        val getUniqueFilePathsMethod = LogSearchController::class.java.getDeclaredMethod(
            "getUniqueFilePaths",
            String::class.java
        )
        getUniqueFilePathsMethod.isAccessible = true
        
        // Call the method - it should use luceneIngestionService.getBaseIndexDir()
        getUniqueFilePathsMethod.invoke(controller, "test-watcher")
        
        // Verify that the luceneIngestionService.getBaseIndexDir() was called
        verify(luceneIngestionService).getBaseIndexDir()
    }
    
    @Test
    fun `searchLogs uses LuceneIngestionService for index paths`() {
        // Create mocks
        val dataSource = mock<DataSource>()
        val luceneIngestionService = mock<LuceneIngestionService>()
        
        // Set up the mock to return a specific path
        val testIndexDir = tempDir.resolve("test-indexes")
        `when`(luceneIngestionService.getBaseIndexDir()).thenReturn(testIndexDir)
        
        // Create the controller with the mocks
        val controller = LogSearchController(dataSource, luceneIngestionService)
        
        // Use reflection to access the private methods
        val searchLogsMethod = LogSearchController::class.java.getDeclaredMethod(
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
        
        // Call the method - it should use luceneIngestionService.getBaseIndexDir()
        searchLogsMethod.invoke(
            controller,
            "test-watcher",
            null,
            "test",
            null,
            null,
            "AND",
            null,
            null,
            1,
            10
        )
        
        // Verify that the luceneIngestionService.getBaseIndexDir() was called
        verify(luceneIngestionService).getBaseIndexDir()
    }
}