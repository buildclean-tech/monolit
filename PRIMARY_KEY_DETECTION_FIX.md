# Primary Key Detection Fix

## Issue Description

The CRUDOperation class was failing with the following error when trying to delete an entity:

```
java.lang.IllegalStateException: No PrimaryKey field exists in the entity.

	at com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.repository.CRUDOperation.delete(CRUDOperation.kt:70)
	at com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.repository.CRUDOperationTest.testDelete(CRUDOperationTest.kt:115)
```

## Root Cause

After investigating the code, we found inconsistencies in how the primary key field was being detected across different methods in the CRUDOperation class:

1. In the `update` method, it was trying to find the primary key field by checking if the field name's Java class had the `@PrimaryKey` annotation:
   ```kotlin
   val fields = entities.firstOrNull()?.javaClass?.declaredFields?.map { it.name } ?: emptyList()
   val primaryKeyField = fields.firstOrNull {
       it.javaClass.getAnnotation(PrimaryKey::class.java) != null
   } ?: throw IllegalStateException("No PrimaryKey field exists in the entity.")
   ```

2. In the `delete` method, it was trying to find the primary key field by checking if the field itself had the `@PrimaryKey` annotation, but using `getAnnotation` instead of `isAnnotationPresent`:
   ```kotlin
   val primaryKeyField = entities.firstOrNull()?.javaClass?.declaredFields?.firstOrNull {
       it.getAnnotation(PrimaryKey::class.java) != null
   } ?: throw IllegalStateException("No PrimaryKey field exists in the entity.")
   ```

3. In the helper methods (`setPreparedStatementParameters` and `setPrimaryKeyParameter`), it was using `isAnnotationPresent` to check for the `@PrimaryKey` annotation:
   ```kotlin
   val primaryKeyField = fields.firstOrNull { it.isAnnotationPresent(PrimaryKey::class.java) }
   ```

The issue in the `update` method was that it was incorrectly trying to get the annotation from the field name's Java class (`it.javaClass.getAnnotation`) instead of from the field itself. Field names are strings, and strings don't have the `@PrimaryKey` annotation.

The issue in the `delete` method was that it was using `getAnnotation` instead of `isAnnotationPresent`, which could lead to inconsistencies in how annotations are detected.

## Changes Made

We updated both the `update` and `delete` methods to use a consistent approach for finding the primary key field:

1. In the `update` method:
   ```kotlin
   val declaredFields = entities.firstOrNull()?.javaClass?.declaredFields ?: emptyArray()
   val primaryKeyField = declaredFields.firstOrNull {
       it.isAnnotationPresent(PrimaryKey::class.java)
   }?.name ?: throw IllegalStateException("No PrimaryKey field exists in the entity.")
   
   val fields = declaredFields.map { it.name }
   ```

2. In the `delete` method:
   ```kotlin
   val primaryKeyField = entities.firstOrNull()?.javaClass?.declaredFields?.firstOrNull {
       it.isAnnotationPresent(PrimaryKey::class.java)
   }?.name ?: throw IllegalStateException("No PrimaryKey field exists in the entity.")
   ```

## Why This Fixes the Issue

With these changes:

1. Both methods now use `isAnnotationPresent` to check for the `@PrimaryKey` annotation, which is consistent with the helper methods.
2. Both methods now correctly find the field with the annotation and then use its name in the SQL query.
3. The `update` method now correctly works with field objects instead of field names.

This ensures that the primary key field is correctly detected in all methods, fixing the "No PrimaryKey field exists in the entity" error.

## Recommendations for Future Development

1. **Use consistent approaches for reflection**: When using reflection to access fields and annotations, use a consistent approach across all methods.
2. **Prefer isAnnotationPresent over getAnnotation != null**: Using `isAnnotationPresent` is more direct and less error-prone than checking if `getAnnotation` returns a non-null value.
3. **Consider extracting common reflection logic**: To avoid duplication and inconsistencies, consider extracting common reflection logic into helper methods.
4. **Add more validation**: Consider adding more validation to ensure that entities have the required annotations before processing them.
5. **Add comprehensive tests**: Ensure that all edge cases are covered by tests, including cases where annotations might be missing or incorrectly applied.