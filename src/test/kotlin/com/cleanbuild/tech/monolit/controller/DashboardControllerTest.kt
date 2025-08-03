package com.cleanbuild.tech.monolit.controller

import com.cleanbuild.tech.monolit.DbRecord.SSHConfig
import com.cleanbuild.tech.monolit.repository.CRUDOperation
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
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
import org.springframework.test.util.ReflectionTestUtils
import kotlin.reflect.KClass

@WebMvcTest(DashboardController::class)
class DashboardControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var dashboardController: DashboardController

    @MockBean
    private lateinit var dataSource: DataSource
    
    private lateinit var mockCrudOperation: CRUDOperation<SSHConfig>
    
    @BeforeEach
    fun setup() {
        // Create mock CRUDOperation
        mockCrudOperation = mock()
        
        // Inject mock CRUDOperation into controller
        ReflectionTestUtils.setField(dashboardController, "crudOperation", mockCrudOperation)
    }

    @Test
    fun `dashboard page returns HTML content with SSH configs table`() {
        // Create a test SSH config
        val now = Timestamp(System.currentTimeMillis())
        val testConfig = SSHConfig(
            name = "test-server",
            serverHost = "example.com",
            port = 22,
            username = "testuser",
            password = "password123",
            createdAt = now,
            updatedAt = now
        )
        
        // Mock the CRUDOperation to return our test config
        `when`(mockCrudOperation.findAll()).thenReturn(listOf(testConfig))

        // Perform the test
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.TEXT_HTML_VALUE + ";charset=UTF-8"))
            .andExpect(content().string(containsString("SSH Configs Dashboard")))
            .andExpect(content().string(containsString("Create New SSH Config")))
            .andExpect(content().string(containsString("test-server")))
            .andExpect(content().string(containsString("example.com")))
            .andExpect(content().string(containsString("22")))
    }

    @Test
    fun `getAllSSHConfigs returns list of configs`() {
        // Create a test SSH config
        val now = Timestamp(System.currentTimeMillis())
        val testConfig = SSHConfig(
            name = "test-server",
            serverHost = "example.com",
            port = 22,
            username = "testuser",
            password = "password123",
            createdAt = now,
            updatedAt = now
        )
        
        // Mock the CRUDOperation to return our test config
        `when`(mockCrudOperation.findAll()).thenReturn(listOf(testConfig))

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
        // Create a test SSH config
        val now = Timestamp(System.currentTimeMillis())
        val testConfig = SSHConfig(
            name = "test-server",
            serverHost = "example.com",
            port = 22,
            username = "testuser",
            password = "password123",
            createdAt = now,
            updatedAt = now
        )
        
        // Mock the CRUDOperation to return our test config
        `when`(mockCrudOperation.findByPrimaryKey("test-server")).thenReturn(testConfig)

        // Perform the test
        mockMvc.perform(get("/dashboard/ssh-configs/test-server"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.name").value("test-server"))
            .andExpect(jsonPath("$.serverHost").value("example.com"))
            .andExpect(jsonPath("$.port").value(22))
    }
}
