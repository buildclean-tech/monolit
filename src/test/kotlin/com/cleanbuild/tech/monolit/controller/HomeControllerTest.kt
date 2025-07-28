package com.cleanbuild.tech.monolit.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(HomeController::class)
class HomeControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `home page returns HTML content`() {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.TEXT_HTML_VALUE))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Welcome to Monolit")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("This HTML is being returned directly from the HomeController")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Current time:")))
    }
}