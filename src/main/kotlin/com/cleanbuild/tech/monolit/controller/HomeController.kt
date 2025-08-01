package com.cleanbuild.tech.monolit.controller

import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HomeController {

    @GetMapping("/", produces = [MediaType.TEXT_HTML_VALUE])
    fun home(): String {
        return """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Monolit Home Page</title>
        <style>
            body {
                font-family: Arial, sans-serif;
                line-height: 1.6;
                margin: 0;
                padding: 0; /* Remove default margin/padding to make it full-page */
                color: #333;
                background-color: #f9f9f9; /* Optional background color */
            }
            .container {
                width: 100%; /* Use the full width of the page */
                height: 100vh; /* Vertically span the entire viewport height */
                display: flex;
                flex-direction: column;
                justify-content: center;
                align-items: center;
                padding: 20px;
                box-sizing: border-box; /* Include padding in height/width calculations */
                background-color: #f9f9f9;
            }
            h1 {
                color: #2c3e50;
                border-bottom: 2px solid #3498db;
                padding-bottom: 10px;
            }
            .info {
                background-color: #e8f4f8;
                padding: 15px;
                border-radius: 4px;
                text-align: center;
                width: 80%;
                max-width: 800px;
                box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1);
                margin-top: 20px;
            }
            .footer {
                margin-top: 30px;
                text-align: center;
                font-size: 0.9em;
                color: #7f8c8d;
            }
        </style>
    </head>
    <body>
        <div class="container">
            <h1>Welcome to Monolit</h1>
            <p>This is a simple home page rendered directly from the controller.</p>
            
            <div class="nav-links" style="margin-bottom: 20px; text-align: center;">
                <a href="/" style="margin-right: 15px; color: #3498db; text-decoration: none;">Home</a>
                <a href="/dashboard" style="margin-right: 15px; color: #3498db; text-decoration: none;">SSH Configs</a>
                <a href="/ssh-log-watcher" style="margin-right: 15px; color: #3498db; text-decoration: none;">SSH Log Watchers</a>
                <a href="/sshlogwatcher-records" style="margin-right: 15px; color: #3498db; text-decoration: none;">SSH Log Watcher Records</a>
                <a href="/log-search" style="margin-right: 15px; color: #3498db; text-decoration: none;">Log Search</a>
            </div>
            
            <div class="info">
                <h2>About This Page</h2>
                <p>This HTML is being returned directly from the HomeController without using Thymeleaf or a model-view approach.</p>
                <p>Current time: ${java.time.LocalDateTime.now()}</p>
            </div>
            
            <div class="footer">
                <p>&copy; 2025 Monolit Application</p>
            </div>
        </div>
    </body>
    </html>
""".trimIndent()
    }
}