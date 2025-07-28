# CRUDOperation Test Suite

This directory contains test cases for the `CRUDOperation` class, which provides generic CRUD (Create, Read, Update, Delete) operations for database entities.

## Test Structure

The test suite consists of:

1. `TestEntity.kt` - A sample entity class used for testing
2. `CRUDOperationTest.kt` - The main test class containing all test cases

## Test Cases

The test suite covers the following scenarios:

### Insert Operation
- `testInsert()` - Tests successful insertion of entities
- `testInsertWithInvalidEntity()` - Tests error case with an entity missing the @SqlTable annotation

### Update Operation
- `testUpdate()` - Tests successful update of an entity
- `testUpdateWithMissingPrimaryKey()` - Tests error case with an entity missing the @PrimaryKey annotation

### Delete Operation
- `testDelete()` - Tests successful deletion of an entity
- `testDeleteWithMissingPrimaryKey()` - Tests error case with an entity missing the @PrimaryKey annotation
- `testDeleteWithInvalidEntity()` - Tests error case with an entity missing the @SqlTable annotation

## Running the Tests

To run the tests, you can use Maven:

```bash
mvn test
```

Or run the tests directly from your IDE by right-clicking on the `CRUDOperationTest` class and selecting "Run".

## Test Environment

The tests use an in-memory H2 database, which is set up in the `setUp()` method and torn down in the `tearDown()` method. This ensures that each test runs in isolation with a clean database state.

## Requirements

- Java 21 or higher
- Kotlin 2.2.0 or higher
- H2 Database
- kotlin-test library