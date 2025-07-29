package com.cleanbuild.tech.monolit.DbRecord
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.DbEntity.PrimaryKey
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.DbEntity.SqlTable
import java.sql.Timestamp
import java.time.LocalDateTime

@SqlTable(tableName = "SSHLogWatcher")
data class SSHLogWatcher(
    @PrimaryKey
    val name: String,
    val sshConfigName: String,
    val watchDir: String,
    val recurDepth: Int,
    val filePrefix: String,
    val fileContains: String,
    val filePostfix: String,
    val archivedLogs: Boolean = true,
    val enabled: Boolean = true,
    val createdAt: Timestamp = Timestamp(System.currentTimeMillis()),
    val updatedAt: Timestamp = Timestamp(System.currentTimeMillis())
) {
    override fun toString(): String {
        return "SSHLogWatcher(name='$name', sshConfigName='$sshConfigName', watchDir='$watchDir', recurDepth=$recurDepth, " +
               "filePrefix='$filePrefix', fileContains='$fileContains', filePostfix='$filePostfix', " +
               "archivedLogs='$archivedLogs', enabled=$enabled, createdAt=$createdAt, updatedAt=$updatedAt)"
    }
}