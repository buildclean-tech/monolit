package com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.repository

import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.DbEntity.PrimaryKey
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.DbEntity.SqlTable
import java.sql.PreparedStatement
import javax.sql.DataSource
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.use


open class CRUDOperation<T>(private val dataSource: DataSource) {

    fun insert(records: List<T>): List<T> {

        if(records.isEmpty())
            return emptyList()

        val tableName = records.first()!!::class.annotations
            .filterIsInstance<SqlTable>() // Filter for @SqlTable annotations
            .firstOrNull()?.tableName     // Extract the table name property
            ?: throw IllegalArgumentException("Entity class must be annotated with @SqlTable")

        // Use Kotlin reflection to get properties
        val members = records.firstOrNull()!!::class.memberProperties
        val columns = members.map { it.name }
        val placeholders = columns.joinToString(", ") { "?" }

        val insertQuery = "INSERT INTO $tableName (${columns.joinToString(", ")}) VALUES ($placeholders)"

        dataSource.connection.use { connection ->
            connection.autoCommit = false // Start transaction
            val insertedCount = connection.prepareStatement(insertQuery).use { statement ->
                records.forEach { record ->
                    members.forEachIndexed { index, member ->
                        val value = member.call(record)
                        statement.setObject(index + 1, value)
                    }
                    statement.addBatch()
                }

                return@use statement.executeBatch().sum()
            }

            if (insertedCount != records.size) {
                connection.rollback() // Rollback transaction if not all records were inserted
                throw IllegalStateException("Transaction Rolledback. Not all records were inserted successfully. Expected: ${records.size}, Inserted: $insertedCount")
            }
            connection.commit() // Commit transaction
        }
        return records
    }

    fun update(records: List<T>): List<T> {

        if(records.isEmpty())
            return emptyList()

        val tableName = records.first()!!::class.annotations
            .filterIsInstance<SqlTable>() // Filter for @SqlTable annotations
            .firstOrNull()?.tableName     // Extract the table name property
            ?: throw IllegalArgumentException("Entity class must be annotated with @SqlTable")

        // Use Kotlin reflection to get non-primary key properties
        val nonKeyMembers = records.firstOrNull()!!::class.memberProperties.filter{ !it.annotations.any { it is PrimaryKey } }
        val keyMembers = records.firstOrNull()!!::class.memberProperties.filter { it.annotations.any { it is PrimaryKey } }

        if(keyMembers.size==0)
            throw IllegalStateException("Entity class must have at least one property annotated with @PrimaryKey")

        val placeholders = nonKeyMembers.joinToString(", ") { "?" }

        // Build SQL query
        val updateQuery = "UPDATE $tableName SET ${nonKeyMembers.map { it.name }
            .joinToString(",") { "$it = ?" }} WHERE ${keyMembers.map { it.name }
            .joinToString(" AND ") { "$it = ?" }}"

        dataSource.connection.use { connection ->
            connection.autoCommit = false // Start transaction
            val updatesCount = connection.prepareStatement(updateQuery).use { statement ->
                records.forEach { record ->
                    (nonKeyMembers+keyMembers).forEachIndexed { index, member ->
                        val value = member.call(record)
                        statement.setObject(index + 1, value)
                    }
                    statement.addBatch()
                }
                return@use statement.executeBatch().sum()
            }

            if (updatesCount != records.size) {
                connection.rollback() // Rollback transaction if not all records were inserted
                throw IllegalStateException("Transaction Rolledback. Not all records were updated successfully. Expected: ${records.size}, Updated: $updatesCount")
            }
            connection.commit() // Commit transaction
        }

        return records
    }

    fun delete(records: List<T>): List<T> {

        if(records.isEmpty())
            return emptyList()

        val tableName = records.first()!!::class.annotations
            .filterIsInstance<SqlTable>() // Filter for @SqlTable annotations
            .firstOrNull()?.tableName     // Extract the table name property
            ?: throw IllegalArgumentException("Entity class must be annotated with @SqlTable")

        val keyMembers = records.firstOrNull()!!::class.memberProperties.filter { it.annotations.any { it is PrimaryKey } }

        if(keyMembers.size==0)
            throw IllegalStateException("Entity class must have at least one property annotated with @PrimaryKey")

        // Build SQL query
        val deleteQuery = "DELETE FROM $tableName WHERE ${keyMembers.map { it.name }
            .joinToString(" AND ") { "$it = ?" }}"

        dataSource.connection.use { connection ->
            connection.autoCommit = false // Start transaction
            val deletedCount =  connection.prepareStatement(deleteQuery).use { statement ->
                records.forEach { item ->
                    keyMembers.forEachIndexed { index, member ->
                        val value = member.call(item)
                        statement.setObject(index + 1, value)
                    }
                    statement.addBatch()
                }
                return@use statement.executeBatch().sum()
            }

            if (deletedCount != records.size) {
                connection.rollback() // Rollback transaction if not all records were inserted
                throw IllegalStateException("Transaction Rolledback. Not all records were deleted successfully. Expected: ${records.size}, Deleted: $deletedCount")
            }
            connection.commit() // Commit transaction
        }

        return records
    }
}