package com.cleanbuild.tech.monolit.DbRecord
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.DbEntity.PrimaryKey
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.DbEntity.SqlTable
import java.time.LocalDateTime

@SqlTable(tableName ="sshConfig")
data class SSHConfig (
    @PrimaryKey
    val name: String,
    val serverHost: String,
    val port: Int,
    val password: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    override fun toString(): String {
        return "SSHConfig(name=$name, serverHost='$serverHost', port=$port, createdAt=$createdAt, updatedAt=$updatedAt)"
    }
}