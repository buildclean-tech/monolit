package com.cleanbuild.tech.monolit.repository

import com.cleanbuild.tech.monolit.DbEntity.Generated
import com.cleanbuild.tech.monolit.DbEntity.PrimaryKey
import com.cleanbuild.tech.monolit.DbEntity.SqlTable
import java.io.Serializable
import javax.sql.DataSource
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.use


open class CRUDOperation<T:Any>(private val dataSource: DataSource, private val kClass: KClass<T>) {

    fun insert(records: List<T>): List<T> {

        if(records.isEmpty())
            return emptyList()

        val tableName = records.first()::class.annotations
            .filterIsInstance<SqlTable>() // Filter for @SqlTable annotations
            .firstOrNull()?.tableName     // Extract the table name property
            ?: throw IllegalArgumentException("Entity class must be annotated with @SqlTable")

        // Use Kotlin reflection to get properties, excluding those marked with @Generated
        val members = records.firstOrNull()!!::class.memberProperties
            .filter { !it.annotations.any { it is Generated } }
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

        val tableName = records.first()::class.annotations
            .filterIsInstance<SqlTable>() // Filter for @SqlTable annotations
            .firstOrNull()?.tableName     // Extract the table name property
            ?: throw IllegalArgumentException("Entity class must be annotated with @SqlTable")

        // Use Kotlin reflection to get non-primary key properties, excluding those marked with @Generated
        val nonKeyMembers = records.firstOrNull()!!::class.memberProperties
            .filter { !it.annotations.any { it is PrimaryKey } && !it.annotations.any { it is Generated } }
        val keyMembers = records.firstOrNull()!!::class.memberProperties
            .filter { it.annotations.any { it is PrimaryKey } }

        if(keyMembers.size==0)
            throw IllegalStateException("Entity class must have at least one property annotated with @PrimaryKey")

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

        val tableName = records.first()::class.annotations
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

    fun findAll(): List<T> {
        // Get table name from annotation
        val tableName = kClass.annotations
            .filterIsInstance<SqlTable>()
            .firstOrNull()?.tableName
            ?: throw IllegalArgumentException("Entity class must be annotated with @SqlTable")
    
        val query = "SELECT * FROM $tableName"
        val results = mutableListOf<T>()
    
        dataSource.connection.use { connection ->
            connection.prepareStatement(query).use { statement ->
                val resultSet = statement.executeQuery()
            
                // Get constructor and properties
                val constructor = kClass.primaryConstructor
                    ?: throw IllegalArgumentException("Entity class must have a primary constructor")

                while (resultSet.next()) {
                    val params = constructor.parameters.associateWith { param ->
                        val propName = param.name ?: throw IllegalArgumentException("Constructor parameter must have a name")
                        val columnValue = resultSet.getObject(propName)
                        
                        // Handle type conversion for primitive types
                        when (param.type.classifier) {
                            Int::class -> if (columnValue != null) (columnValue as Number).toInt() else 0
                            Long::class -> if (columnValue != null) (columnValue as Number).toLong() else 0L
                            Float::class -> if (columnValue != null) (columnValue as Number).toFloat() else 0.0f
                            Double::class -> if (columnValue != null) (columnValue as Number).toDouble() else 0.0
                            Boolean::class -> if (columnValue != null) columnValue as Boolean else false
                            else -> columnValue
                        }
                    }
                
                    val instance = constructor.callBy(params)
                    results.add(instance)
                }
            }
        }
    
        return results
    }

    fun findByPrimaryKey(primaryKeyValue: Any): T? {
        // Ensure entityClass is provided
        val clazz = kClass
    
        // Get table name from annotation
        val tableName = clazz.annotations
            .filterIsInstance<SqlTable>()
            .firstOrNull()?.tableName
            ?: throw IllegalArgumentException("Entity class must be annotated with @SqlTable")
    
        // Get primary key property
        val keyMembers = clazz.memberProperties.filter { it.annotations.any { it is PrimaryKey } }
    
        if (keyMembers.isEmpty())
            throw IllegalStateException("Entity class must have at least one property annotated with @PrimaryKey")
    
        if (keyMembers.size > 1)
            throw IllegalStateException("This method only supports entities with a single primary key")
    
        val primaryKeyName = keyMembers.first().name
    
        val query = "SELECT * FROM $tableName WHERE $primaryKeyName = ?"
    
        dataSource.connection.use { connection ->
            connection.prepareStatement(query).use { statement ->
                statement.setObject(1, primaryKeyValue)
                val resultSet = statement.executeQuery()
            
                if (resultSet.next()) {
                    // Get constructor and properties
                    val constructor = clazz.primaryConstructor 
                        ?: throw IllegalArgumentException("Entity class must have a primary constructor")
                
                    val params = constructor.parameters.associateWith { param ->
                        val propName = param.name ?: throw IllegalArgumentException("Constructor parameter must have a name")
                        val columnValue = resultSet.getObject(propName)
                        
                        // Handle type conversion for primitive types
                        when (param.type.classifier) {
                            Int::class -> if (columnValue != null) (columnValue as Number).toInt() else 0
                            Long::class -> if (columnValue != null) (columnValue as Number).toLong() else 0L
                            Float::class -> if (columnValue != null) (columnValue as Number).toFloat() else 0.0f
                            Double::class -> if (columnValue != null) (columnValue as Number).toDouble() else 0.0
                            Boolean::class -> if (columnValue != null) columnValue as Boolean else false
                            else -> columnValue
                        }
                    }
                
                    return constructor.callBy(params)
                }
            }
        }
    
        return null
    }
    
    fun <R> findByColumnValues(whereColumns: Map<KProperty1<T, R>, Any>): List<T> where R : Comparable<*>, R : Serializable {
        if (whereColumns.isEmpty()) {
            return findAll()
        }
        
        // Get table name from annotation
        val tableName = kClass.annotations
            .filterIsInstance<SqlTable>()
            .firstOrNull()?.tableName
            ?: throw IllegalArgumentException("Entity class must be annotated with @SqlTable")
        
        // Build WHERE clause from the provided column-value map
        val whereClause = whereColumns.keys.joinToString(" AND ") { "${it.name} = ?" }
        val query = "SELECT * FROM $tableName WHERE $whereClause"
        
        val results = mutableListOf<T>()
        
        dataSource.connection.use { connection ->
            connection.prepareStatement(query).use { statement ->
                // Set parameter values for the WHERE clause
                whereColumns.entries.forEachIndexed { index, entry ->
                    statement.setObject(index + 1, entry.value)
                }
                
                val resultSet = statement.executeQuery()
                
                // Get constructor and properties
                val constructor = kClass.primaryConstructor
                    ?: throw IllegalArgumentException("Entity class must have a primary constructor")
                
                while (resultSet.next()) {
                    val params = constructor.parameters.associateWith { param ->
                        val propName = param.name ?: throw IllegalArgumentException("Constructor parameter must have a name")
                        val columnValue = resultSet.getObject(propName)
                        
                        // Handle type conversion for primitive types
                        when (param.type.classifier) {
                            Int::class -> if (columnValue != null) (columnValue as Number).toInt() else 0
                            Long::class -> if (columnValue != null) (columnValue as Number).toLong() else 0L
                            Float::class -> if (columnValue != null) (columnValue as Number).toFloat() else 0.0f
                            Double::class -> if (columnValue != null) (columnValue as Number).toDouble() else 0.0
                            Boolean::class -> if (columnValue != null) columnValue as Boolean else false
                            else -> columnValue
                        }
                    }
                    
                    val instance = constructor.callBy(params)
                    results.add(instance)
                }
            }
        }
        
        return results
    }
}