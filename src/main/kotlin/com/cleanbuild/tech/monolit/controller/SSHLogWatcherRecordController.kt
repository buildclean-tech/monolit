package com.cleanbuild.tech.monolit.controller

import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcher
import com.cleanbuild.tech.monolit.DbRecord.SSHLogWatcherRecord
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.repository.CRUDOperation
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import java.sql.Timestamp
import java.time.format.DateTimeFormatter
import javax.sql.DataSource
import kotlin.reflect.KProperty1

@RestController
@RequestMapping("/sshlogwatcher-records")
class SSHLogWatcherRecordController(private val dataSource: DataSource) {

    private val recordCrudOperation = CRUDOperation<SSHLogWatcherRecord>(dataSource, SSHLogWatcherRecord::class)
    private val watcherCrudOperation = CRUDOperation<SSHLogWatcher>(dataSource, SSHLogWatcher::class)
    
    // Format timestamp for display in HTML
    private fun formatTimestamp(timestamp: Timestamp): String {
        val localDateTime = timestamp.toLocalDateTime()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return localDateTime.format(formatter)
    }
    
    // Get all SSHLogWatcher names for the dropdown
    private fun getAllSSHLogWatcherNames(): List<String> {
        return watcherCrudOperation.findAll().map { it.name }
    }
    
    // Get records for a specific SSHLogWatcher name
    private fun getRecordsByWatcherName(watcherName: String): List<SSHLogWatcherRecord> {
        val whereColumns = mapOf(
            SSHLogWatcherRecord::sshLogWatcherName to watcherName
        )
        return recordCrudOperation.findByColumnValues<String>(whereColumns)
    }
    
    @GetMapping(produces = [MediaType.TEXT_HTML_VALUE])
    fun viewRecords(@RequestParam(required = false) watcherName: String?): String {
        val watcherNames = getAllSSHLogWatcherNames()
        
        // Get records for the selected watcher, or empty list if none selected
        val records = if (watcherName != null) {
            getRecordsByWatcherName(watcherName)
        } else {
            emptyList()
        }
        
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>SSH Log Watcher Records</title>
            <style>
                body {
                    font-family: Arial, sans-serif;
                    line-height: 1.6;
                    margin: 0;
                    padding: 20px;
                    color: #333;
                    background-color: #f5f5f5;
                }
                .container {
                    max-width: 1200px;
                    margin: 0 auto;
                    padding: 20px;
                    background-color: #fff;
                    border-radius: 5px;
                    box-shadow: 0 2px 5px rgba(0,0,0,0.1);
                }
                h1 {
                    color: #2c3e50;
                    border-bottom: 2px solid #3498db;
                    padding-bottom: 10px;
                }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    margin-bottom: 20px;
                }
                th, td {
                    padding: 12px 15px;
                    text-align: left;
                    border-bottom: 1px solid #ddd;
                }
                th {
                    background-color: #f2f2f2;
                    font-weight: bold;
                }
                tr:hover {
                    background-color: #f5f5f5;
                }
                .form-group {
                    margin-bottom: 20px;
                }
                select, button {
                    padding: 8px 12px;
                    border: 1px solid #ddd;
                    border-radius: 4px;
                    font-size: 14px;
                }
                button {
                    background-color: #3498db;
                    color: white;
                    cursor: pointer;
                    border: none;
                }
                button:hover {
                    background-color: #2980b9;
                }
                .no-records {
                    padding: 20px;
                    text-align: center;
                    color: #7f8c8d;
                    font-style: italic;
                }
                .nav-links {
                    margin-bottom: 20px;
                }
                .nav-links a {
                    margin-right: 15px;
                    color: #3498db;
                    text-decoration: none;
                }
                .nav-links a:hover {
                    text-decoration: underline;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="nav-links">
                    <a href="/">Home</a>
                    <a href="/dashboard">SSH Configs</a>
                    <a href="/ssh-log-watcher">SSH Log Watchers</a>
                    <a href="/sshlogwatcher-records">SSH Log Watcher Records</a>
                </div>
                
                <h1>SSH Log Watcher Records</h1>
                
                <div class="form-group">
                    <form action="/sshlogwatcher-records" method="get">
                        <label for="watcherName">Select SSH Log Watcher:</label>
                        <select name="watcherName" id="watcherName">
                            <option value="">-- Select a watcher --</option>
                            ${watcherNames.joinToString("") { 
                                "<option value=\"$it\"${if (it == watcherName) " selected" else ""}>${it}</option>"
                            }}
                        </select>
                        <button type="submit">View Records</button>
                    </form>
                </div>
                
                ${if (watcherName != null) {
                    if (records.isNotEmpty()) {
                        """
                        <h2>Records for ${watcherName}</h2>
                        <table>
                            <thead>
                                <tr>
                                    <th>ID</th>
                                    <th>File Path</th>
                                    <th>File Size</th>
                                    <th>File Time</th>
                                    <th>File Hash</th>
                                    <th>Created Time</th>
                                    <th>Updated Time</th>
                                    <th>Status</th>
                                    <th>Duplicated File</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${records.joinToString("") { record ->
                                    """
                                    <tr>
                                        <td>${record.id}</td>
                                        <td>${record.fullFilePath}</td>
                                        <td>${record.fileSize}</td>
                                        <td>${formatTimestamp(record.cTime)}</td>
                                        <td>${record.fileHash}</td>
                                        <td>${formatTimestamp(record.createdTime)}</td>
                                        <td>${formatTimestamp(record.updatedTime)}</td>
                                        <td>${record.consumptionStatus}</td>
                                        <td>${record.duplicatedFile ?: "-"}</td>
                                    </tr>
                                    """
                                }}
                            </tbody>
                        </table>
                        """
                    } else {
                        """
                        <div class="no-records">
                            <p>No records found for SSH Log Watcher: ${watcherName}</p>
                        </div>
                        """
                    }
                } else {
                    """
                    <div class="no-records">
                        <p>Please select an SSH Log Watcher to view its records</p>
                    </div>
                    """
                }}
            </div>
        </body>
        </html>
        """
    }
}