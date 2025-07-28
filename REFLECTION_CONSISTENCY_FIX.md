# Reflection Consistency Fix

## Issue Description

The CRUDOperation class was failing with the following error when trying to insert an entity:

```
org.h2.jdbc.JdbcSQLDataException: Parameter "#4" is not set [90012-224]
```

This error occurred in the `insert` method when calling `statement.addBatch()` on a prepared statement. It indicates that a parameter in the SQL statement was not set before executing the batch.

## Root Cause

After investigating the code, we found inconsistencies in how reflection was used across different methods in the CRUDOperation class:

1. In the `insert` method, Java reflection was used to get field names for the SQL query:
   ```kotlin
   val fields = entities.firstOrNull()?.javaClass?.declaredFields?.map { it.name } ?: emptyList()
   ```

2. In the `setPreparedStatementParameters` method, Kotlin reflection was used to get properties for setting parameters:
   ```kotlin
   val kClass = item!!::class
   val properties = kClass.members.filterIsInstance<kotlin.reflect.KProperty1<T, *>>()
   ```

These two different reflection approaches can return different sets of fields/properties or in a different order, especially for Kotlin data classes. This mismatch caused a discrepancy between:
- The number of placeholders (`?`) in the SQL query (based on Java reflection)
- The number of parameters actually set in the prepared statement (based on Kotlin reflection)

Specifically, for the TestEntity class with 4 properties (id, name, itemValue, createdAt), the Java reflection might have returned additional synthetic fields or in a different order than the Kotlin reflection, leading to the "Parameter #4 is not set" error.

## Changes Made

We updated the `insert` method to use Kotlin reflection consistently with the `setPreparedStatementParameters` method:

```kotlin
// Before
val fields = entities.firstOrNull()?.javaClass?.declaredFields?.map { it.name } ?: emptyList()

// After
val kClass = entities.firstOrNull()!!::class
val properties = kClass.members.filterIsInstance<kotlin.reflect.KProperty1<T, *>>()
val fields = properties.map { it.name }
```

## Why This Fixes the Issue

With these changes:

1. Both the `insert` method and the `setPreparedStatementParameters` method now use the same Kotlin reflection approach
2. The field names in the SQL query now exactly match the properties used for setting parameters
3. The number of placeholders in the SQL query now matches the number of parameters set in the prepared statement

This ensures that all parameters in the prepared statement are properly set before calling `addBatch()`, fixing the "Parameter #4 is not set" error.

## Recommendations for Future Development

1. **Use consistent reflection approaches**: When using reflection to access fields and properties, use a consistent approach across all methods.
2. **Prefer Kotlin reflection for Kotlin classes**: For Kotlin classes, especially data classes, Kotlin reflection provides more accurate and consistent results than Java reflection.
3. **Consider extracting common reflection logic**: To avoid duplication and inconsistencies, consider extracting common reflection logic into helper methods.
4. **Add more validation**: Consider adding validation to ensure that the number of placeholders in SQL queries matches the number of parameters being set.
5. **Add comprehensive tests**: Ensure that all edge cases are covered by tests, including cases with different types of entities and properties.