package com.cleanbuild.tech.monolit.examples

/**
 * This file demonstrates the concept of inline functions in Kotlin.
 * 
 * An inline function in Kotlin is a function marked with the 'inline' keyword that 
 * instructs the compiler to copy the function's bytecode directly into the call site,
 * rather than creating a function call.
 */

// Basic inline function example
inline fun executeWithLogging(action: () -> Unit) {
    println("Before execution")
    action()
    println("After execution")
}

// Example of inline function with a return value
inline fun <T> measureTimeMillis(function: () -> T): Pair<T, Long> {
    val start = System.currentTimeMillis()
    val result = function()
    val end = System.currentTimeMillis()
    return Pair(result, end - start)
}

// Example of using crossinline to prevent non-local returns
inline fun runInThread(crossinline action: () -> Unit) {
    Thread {
        action()
    }.start()
}

// Example of using noinline to prevent a specific parameter from being inlined
inline fun executeWithCallback(action: () -> Unit, noinline callback: () -> Unit) {
    action()
    // Store callback for later use or pass it to another function
    storeCallback(callback)
}

// Regular function to store a callback
fun storeCallback(callback: () -> Unit) {
    // Implementation
}

// Example of reified type parameters (only possible with inline functions)
inline fun <reified T> isType(value: Any): Boolean {
    return value is T
}

/**
 * Benefits of inline functions:
 * 
 * 1. Performance Improvement: Eliminates function call overhead
 * 2. Lambda Performance: Avoids creating anonymous class instances for lambdas
 * 3. Non-local Returns: Allows using 'return' inside lambdas to return from the enclosing function
 * 4. Reified Type Parameters: Enables access to actual type information at runtime
 * 
 * When to use inline functions:
 * - When the function takes lambda parameters
 * - When the function is small
 * - When the function is called frequently
 * - When you need non-local returns or reified type parameters
 * 
 * When NOT to use inline functions:
 * - When the function is large (increases code size)
 * - When the function doesn't take lambda parameters
 * - When the function is part of a public API that might change frequently
 */

// Example of how inline functions could be used in the project context
class DatabaseExample(private val dataSource: javax.sql.DataSource) {
    
    // Inline function to handle database connections
    // Making this function private to match the visibility of dataSource
    private inline fun <T> withConnection(crossinline action: (java.sql.Connection) -> T): T {
        dataSource.connection.use { connection ->
            return action(connection)
        }
    }
    
    // Using the inline function to simplify database operations
    fun findAllRecords(tableName: String): List<Map<String, Any>> {
        return withConnection { connection ->
            val results = mutableListOf<Map<String, Any>>()
            val query = "SELECT * FROM $tableName"
            
            connection.prepareStatement(query).use { statement ->
                val resultSet = statement.executeQuery()
                
                while (resultSet.next()) {
                    val row = mutableMapOf<String, Any>()
                    val metaData = resultSet.metaData
                    
                    for (i in 1..metaData.columnCount) {
                        val columnName = metaData.getColumnName(i)
                        val value = resultSet.getObject(i)
                        if (value != null) {
                            row[columnName] = value
                        }
                    }
                    
                    results.add(row)
                }
            }
            
            results
        }
    }
}