# H2 Reserved Keyword Fix

## Issue Description

The test suite was encountering issues because the `value` property in the `TestEntity` class was using a reserved keyword in H2 database. In SQL databases, using reserved keywords as column names can lead to syntax errors or unexpected behavior unless they are properly escaped.

## Changes Made

1. **Renamed the property in TestEntity.kt**:
   - Changed from: `var value: Double`
   - To: `var itemValue: Double`

2. **Updated the CREATE TABLE statement in CRUDOperationTest.kt**:
   - Changed from: `"value" DOUBLE`
   - To: `itemValue DOUBLE`

3. **Updated all TestEntity constructor calls to use named parameters**:
   - Changed from: `TestEntity(1, "Test 1", 10.5, LocalDateTime.now())`
   - To: `TestEntity(id = 1, name = "Test 1", itemValue = 10.5, createdAt = LocalDateTime.now())`

4. **Updated the copy method call**:
   - Changed from: `testEntity.copy(name = "Updated Name", value = 15.5)`
   - To: `testEntity.copy(name = "Updated Name", itemValue = 15.5)`

5. **Updated SQL queries**:
   - Changed from: `SELECT name, value FROM TestEntity WHERE id = 1`
   - To: `SELECT name, itemValue FROM TestEntity WHERE id = 1`

6. **Updated assertions**:
   - Changed from: `assertEquals(15.5, resultSet.getDouble("value"), 0.001)`
   - To: `assertEquals(15.5, resultSet.getDouble("itemValue"), 0.001)`

## Why This Fixes the Issue

H2 database treats `value` as a reserved keyword, which can cause issues when used as a column name without proper escaping. By renaming the property to `itemValue`, which is not a reserved keyword, we avoid these issues.

In the original code, the CREATE TABLE statement was escaping the `value` column name with double quotes (`"value"`), but the SQL query in the test was not escaping it. This inconsistency could lead to errors.

By using a non-reserved name consistently throughout the code, we ensure that the tests will run correctly without any issues related to reserved keywords.

## H2 Reserved Keywords

H2 database has a list of reserved keywords that should not be used as identifiers (table names, column names, etc.) without proper escaping. Some common reserved keywords include:

- `VALUE`
- `TABLE`
- `INDEX`
- `KEY`
- `ORDER`
- `GROUP`
- `BY`
- `HAVING`
- `WHERE`

For a complete list of H2 reserved keywords, refer to the [H2 Database Keywords documentation](http://www.h2database.com/html/grammar.html#keywords).

## Best Practices

1. **Avoid using reserved keywords as identifiers**: When designing database schemas, avoid using SQL reserved keywords as table or column names.

2. **Use consistent naming conventions**: Follow a consistent naming convention for database identifiers that avoids potential conflicts with reserved keywords.

3. **If you must use a reserved keyword, always escape it properly**: In H2, you can escape identifiers using double quotes (`"keyword"`), but it's better to avoid using reserved keywords altogether.