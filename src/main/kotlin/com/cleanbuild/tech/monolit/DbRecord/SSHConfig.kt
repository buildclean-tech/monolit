package com.cleanbuild.tech.monolit.DbRecord
import com.cleanbuild.tech.monolit.DbEntity.PrimaryKey
import com.cleanbuild.tech.monolit.DbEntity.SqlTable
import java.sql.Timestamp
import java.time.LocalDateTime

@SqlTable(tableName ="sshConfig")
data class SSHConfig (
    @PrimaryKey
    val name: String,
    val serverHost: String,
    val port: Int,
    val username: String = "",
    val password: String,
    val createdAt: Timestamp = Timestamp(System.currentTimeMillis()),
    val updatedAt: Timestamp = Timestamp(System.currentTimeMillis())
) {
    override fun toString(): String {
        return "SSHConfig(name=$name, serverHost='$serverHost', port=$port, username='$username', createdAt=$createdAt, updatedAt=$updatedAt)"
    }
}