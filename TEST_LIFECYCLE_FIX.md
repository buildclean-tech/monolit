# Test Lifecycle Annotation Fix

## Issue Description

The test `testDeleteWithInvalidEntity` in `CRUDOperationTest` was failing with the following error:

```
java.lang.AssertionError: Expected an exception of class java.lang.IllegalArgumentException to be thrown, but was kotlin.UninitializedPropertyAccessException: lateinit property dataSource has not been initialized
```

## Root Cause

After investigating the test class, we found that it was using Spring's test context annotations (`@BeforeTestClass` and `@AfterTestClass`) instead of the standard JUnit 5 lifecycle annotations. These Spring-specific annotations were not being recognized by the JUnit 5 test runner, which meant that:

1. The `setUp()` method annotated with `@BeforeTestClass` was not being executed before the tests
2. As a result, the `dataSource` property was not being initialized
3. When the `testDeleteWithInvalidEntity` method tried to use the `dataSource` property, it threw a `UninitializedPropertyAccessException`

## Changes Made

We updated the test class to use the standard JUnit 5 lifecycle annotations:

1. Replaced the imports:
   ```kotlin
   // Removed
   import org.springframework.test.context.event.annotation.AfterTestClass
   import org.springframework.test.context.event.annotation.BeforeTestClass
   
   // Added
   import org.junit.jupiter.api.AfterEach
   import org.junit.jupiter.api.BeforeEach
   ```

2. Updated the annotations on the methods:
   ```kotlin
   // Before
   @BeforeTestClass
   fun setUp() { ... }
   
   @AfterTestClass
   fun tearDown() { ... }
   
   // After
   @BeforeEach
   fun setUp() { ... }
   
   @AfterEach
   fun tearDown() { ... }
   ```

## Why This Fixes the Issue

With these changes:

1. The `setUp()` method is now executed before each test method, ensuring that the `dataSource` property is properly initialized
2. The `tearDown()` method is now executed after each test method, ensuring proper cleanup
3. When the `testDeleteWithInvalidEntity` method runs, it can now create a `CRUDOperation` instance with the initialized `dataSource`
4. The test can now proceed to the point where it expects an `IllegalArgumentException` to be thrown when trying to delete an entity without the `@SqlTable` annotation

## JUnit 5 Lifecycle Annotations

For reference, here are the standard JUnit 5 lifecycle annotations:

- `@BeforeAll`: Executed once before all test methods in the class
- `@AfterAll`: Executed once after all test methods in the class
- `@BeforeEach`: Executed before each test method
- `@AfterEach`: Executed after each test method

We chose to use `@BeforeEach` and `@AfterEach` because each test needs a fresh database setup and cleanup.

## Recommendations for Future Development

1. **Use standard JUnit 5 annotations**: Avoid using framework-specific annotations for standard test lifecycle events
2. **Consider using a test fixture**: For more complex test setups, consider using JUnit 5's test fixture features
3. **Add explicit test documentation**: Add comments or documentation explaining the test setup requirements