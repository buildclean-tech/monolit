package com.cleanbuild.tech.monolit.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * Controller for the home page.
 * This controller handles HTTP requests for the root URL.
 */
@Controller
class HomeController {
    
    /**
     * Display the home page.
     *
     * @return the name of the view to render
     */
    @GetMapping("/")
    fun home(): String {
        return "index"
    }
}