package com.cleanbuild.tech.monolit.DbRecord
import com.cleanbuild.tech.monolit.DbEntity.Generated
import com.cleanbuild.tech.monolit.DbEntity.PrimaryKey
import com.cleanbuild.tech.monolit.DbEntity.SqlTable
import java.sql.Timestamp

@SqlTable(tableName = "SSHLogWatcherRecord")
data class SSHLogWatcherRecord(
    @PrimaryKey
    @Generated
    val id: Long? = null,
    val sshLogWatcherName: String,
    val fullFilePath: String,
    val fileSize: Long,
    val cTime: Timestamp,
    val fileHash: String,
    val createdTime: Timestamp = Timestamp(System.currentTimeMillis()),
    val updatedTime: Timestamp = Timestamp(System.currentTimeMillis()),
    val consumptionStatus: String,
    val fileName: String,
    val noOfIndexedDocuments: Long,
    val duplicatedFile: String? = null,
) {
    override fun toString(): String {
        return "SSHLogWatcherRecord(id=$id, sshLogWatcherName='$sshLogWatcherName', " +
               "fullFilePath='$fullFilePath', fileSize=$fileSize, cTime=$cTime, " +
               "fileHash='$fileHash', createdTime=$createdTime, updatedTime=$updatedTime, " +
               "consumptionStatus='$consumptionStatus', duplicatedFile=${duplicatedFile?.let { "'$it'" } ?: "null"}, " +
               "fileName=${fileName?.let { "'$it'" } ?: "null"}, noOfIndexedDocuments=$noOfIndexedDocuments)"
    }
}