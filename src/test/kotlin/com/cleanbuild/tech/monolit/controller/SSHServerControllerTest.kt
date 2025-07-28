package com.cleanbuild.tech.monolit.controller

import com.cleanbuild.tech.monolit.DbRecord.SSHConfig
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.repository.CRUDOperation
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.config.LocalSSHServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.sql.Timestamp
import javax.sql.DataSource
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

@WebMvcTest(SSHServerController::class)
class SSHServerControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var sshServerService: LocalSSHServer

    @MockBean
    private lateinit var dataSource: DataSource

    @MockBean
    private lateinit var crudOperation: CRUDOperation<SSHConfig>

    private val objectMapper = ObjectMapper().registerKotlinModule()

    private val testConfig = SSHConfig(
        name = "test-config",
        serverHost = "localhost",
        port = 2222,
        password = "testpassword",
        createdAt = Timestamp(System.currentTimeMillis()),
        updatedAt = Timestamp(System.currentTimeMillis())
    )

    @BeforeEach
    fun setUp() {
        // Mock the SSHServerService methods
        whenever(sshServerService.createDefaultConfig()).thenReturn("localhost-test")
        whenever(sshServerService.startServer("test-config")).thenReturn(true)
        whenever(sshServerService.startServer("localhost-test")).thenReturn(true)
        whenever(sshServerService.startServer("invalid-config")).thenReturn(false)
        whenever(sshServerService.stopServer()).thenReturn(true)
        whenever(sshServerService.isServerRunning()).thenReturn(true)

        // Mock the CRUDOperation methods
        whenever(crudOperation.findByPrimaryKey("test-config")).thenReturn(testConfig)
        whenever(crudOperation.findByPrimaryKey("localhost-test")).thenReturn(testConfig)
        whenever(crudOperation.findAll()).thenReturn(listOf(testConfig))
        whenever(crudOperation.insert(any())).thenReturn(listOf(testConfig))
    }

    @AfterEach
    fun tearDown() {
        // No specific teardown needed
    }

    @Test
    fun `test start server with default config`() {
        mockMvc.perform(post("/api/ssh/start"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.message").value("SSH server started successfully"))
            .andExpect(jsonPath("$.configName").value("localhost-test"))

        verify(sshServerService).createDefaultConfig()
        verify(sshServerService).startServer("localhost-test")
    }

    @Test
    fun `test start server with specific config`() {
        mockMvc.perform(post("/api/ssh/start")
            .param("configName", "test-config"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.message").value("SSH server started successfully"))
            .andExpect(jsonPath("$.configName").value("test-config"))

        verify(sshServerService).startServer("test-config")
    }

    @Test
    fun `test start server with invalid config`() {
        mockMvc.perform(post("/api/ssh/start")
            .param("configName", "invalid-config"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").value("Failed to start SSH server"))

        verify(sshServerService).startServer("invalid-config")
    }

    @Test
    fun `test stop server`() {
        mockMvc.perform(post("/api/ssh/stop"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.message").value("SSH server stopped successfully"))

        verify(sshServerService).stopServer()
    }

    @Test
    fun `test get server status`() {
        mockMvc.perform(get("/api/ssh/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.running").value(true))
            .andExpect(jsonPath("$.message").value("SSH server is running"))

        verify(sshServerService).isServerRunning()
    }

    @Test
    fun `test create config`() {
        val configJson = objectMapper.writeValueAsString(testConfig)

        mockMvc.perform(post("/api/ssh/config")
            .contentType(MediaType.APPLICATION_JSON)
            .content(configJson))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.message").value("SSH configuration created successfully"))

        verify(crudOperation).insert(any())
    }

    @Test
    fun `test get all configs`() {
        mockMvc.perform(get("/api/ssh/config"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.configs").isArray)
            .andExpect(jsonPath("$.configs[0].name").value("test-config"))

        verify(crudOperation).findAll()
    }

    @Test
    fun `test get config by name`() {
        mockMvc.perform(get("/api/ssh/config/test-config"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.config.name").value("test-config"))
            .andExpect(jsonPath("$.config.serverHost").value("localhost"))
            .andExpect(jsonPath("$.config.port").value(2222))

        verify(crudOperation).findByPrimaryKey("test-config")
    }

    @Test
    fun `test get config by name not found`() {
        whenever(crudOperation.findByPrimaryKey("non-existent")).thenReturn(null)

        mockMvc.perform(get("/api/ssh/config/non-existent"))
            .andExpect(status().isNotFound)

        verify(crudOperation).findByPrimaryKey("non-existent")
    }

    @Test
    fun `test create default config`() {
        mockMvc.perform(post("/api/ssh/config/default"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.message").value("Default SSH configuration created or already exists"))
            .andExpect(jsonPath("$.config.name").value("test-config"))

        verify(sshServerService).createDefaultConfig()
        verify(crudOperation).findByPrimaryKey("localhost-test")
    }
}