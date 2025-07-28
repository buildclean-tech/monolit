package com.cleanbuild.tech.monolit.controller

import com.cleanbuild.tech.monolit.DbRecord.SSHConfig
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.repository.CRUDOperation
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
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
import javax.sql.DataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp

@WebMvcTest(DashboardController::class)
class DashboardControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var dataSource: DataSource

    @Test
    fun `dashboard page returns HTML content with SSH configs table`() {
        // Mock the database connection and result set
        val mockConnection = mock<Connection>()
        val mockStatement = mock<PreparedStatement>()
        val mockResultSet = mock<ResultSet>()

        `when`(dataSource.connection).thenReturn(mockConnection)
        `when`(mockConnection.prepareStatement(any())).thenReturn(mockStatement)
        `when`(mockStatement.executeQuery()).thenReturn(mockResultSet)
        
        // Mock one row in the result set
        `when`(mockResultSet.next()).thenReturn(true, false) // Return true once, then false
        `when`(mockResultSet.getString("name")).thenReturn("test-server")
        `when`(mockResultSet.getString("serverHost")).thenReturn("example.com")
        `when`(mockResultSet.getInt("port")).thenReturn(22)
        `when`(mockResultSet.getString("password")).thenReturn("password123")
        
        val now = Timestamp(System.currentTimeMillis())
        `when`(mockResultSet.getTimestamp("createdAt")).thenReturn(now)
        `when`(mockResultSet.getTimestamp("updatedAt")).thenReturn(now)

        // Perform the test
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.TEXT_HTML_VALUE))
            .andExpect(content().string(containsString("SSH Configs Dashboard")))
            .andExpect(content().string(containsString("Create New SSH Config")))
            .andExpect(content().string(containsString("test-server")))
            .andExpect(content().string(containsString("example.com")))
            .andExpect(content().string(containsString("22")))
    }

    @Test
    fun `getAllSSHConfigs returns list of configs`() {
        // Mock the database connection and result set
        val mockConnection = mock<Connection>()
        val mockStatement = mock<PreparedStatement>()
        val mockResultSet = mock<ResultSet>()

        `when`(dataSource.connection).thenReturn(mockConnection)
        `when`(mockConnection.prepareStatement(any())).thenReturn(mockStatement)
        `when`(mockStatement.executeQuery()).thenReturn(mockResultSet)
        
        // Mock one row in the result set
        `when`(mockResultSet.next()).thenReturn(true, false) // Return true once, then false
        `when`(mockResultSet.getString("name")).thenReturn("test-server")
        `when`(mockResultSet.getString("serverHost")).thenReturn("example.com")
        `when`(mockResultSet.getInt("port")).thenReturn(22)
        `when`(mockResultSet.getString("password")).thenReturn("password123")
        
        val now = Timestamp(System.currentTimeMillis())
        `when`(mockResultSet.getTimestamp("createdAt")).thenReturn(now)
        `when`(mockResultSet.getTimestamp("updatedAt")).thenReturn(now)

        // Perform the test
        mockMvc.perform(get("/dashboard/ssh-configs"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$[0].name").value("test-server"))
            .andExpect(jsonPath("$[0].serverHost").value("example.com"))
            .andExpect(jsonPath("$[0].port").value(22))
    }

    @Test
    fun `getSSHConfig returns specific config`() {
        // Mock the database connection and result set
        val mockConnection = mock<Connection>()
        val mockStatement = mock<PreparedStatement>()
        val mockResultSet = mock<ResultSet>()

        `when`(dataSource.connection).thenReturn(mockConnection)
        `when`(mockConnection.prepareStatement(any())).thenReturn(mockStatement)
        `when`(mockStatement.executeQuery()).thenReturn(mockResultSet)
        
        // Mock one row in the result set
        `when`(mockResultSet.next()).thenReturn(true)
        `when`(mockResultSet.getString("name")).thenReturn("test-server")
        `when`(mockResultSet.getString("serverHost")).thenReturn("example.com")
        `when`(mockResultSet.getInt("port")).thenReturn(22)
        `when`(mockResultSet.getString("password")).thenReturn("password123")
        
        val now = Timestamp(System.currentTimeMillis())
        `when`(mockResultSet.getTimestamp("createdAt")).thenReturn(now)
        `when`(mockResultSet.getTimestamp("updatedAt")).thenReturn(now)

        // Perform the test
        mockMvc.perform(get("/dashboard/ssh-configs/test-server"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.name").value("test-server"))
            .andExpect(jsonPath("$.serverHost").value("example.com"))
            .andExpect(jsonPath("$.port").value(22))
    }
}