package com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.repository

import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.DbEntity.Generated
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.DbEntity.PrimaryKey
import com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.DbEntity.SqlTable
import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.Timestamp
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test class for CRUDOperation
 * 
 * This class contains tests for all CRUD operations:
 * - insert: Tests for inserting records into the database
 * - update: Tests for updating existing records
 * - delete: Tests for deleting records
 * - findAll: Tests for retrieving all records of a given entity type
 * - findByPrimaryKey: Tests for retrieving a specific record by its primary key
 * 
 * Each operation is tested for both success and error cases, including:
 * - Successful operation with valid data
 * - Error handling for invalid entities (missing annotations, etc.)
 * - Edge cases (empty tables, non-existent records, etc.)
 * 
 * Assumptions:
 * - The database is empty at the start of each test
 * - The TestEntity class has a single primary key field (id)
 * - The database supports standard SQL operations
 */
class CRUDOperationTest {
    private lateinit var dataSource: JdbcDataSource
    private lateinit var connection: Connection
    private lateinit var crudOperation: CRUDOperation<TestEntity>

    @BeforeEach
    fun setUp() {
        // Set up in-memory H2 database
        dataSource = JdbcDataSource()
        dataSource.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
        dataSource.setUser("sa")
        dataSource.setPassword("")

        // Create connection and initialize database
        connection = dataSource.connection
        
        // Create test table
        val createTableSQL = """
            CREATE TABLE IF NOT EXISTS TestEntity (
                id INT PRIMARY KEY,
                name VARCHAR(255),
                itemValue DOUBLE,
                createdAt TIMESTAMP
            )
        """.trimIndent()
        
        connection.createStatement().use { statement ->
            statement.execute(createTableSQL)
        }
        
        // Initialize CRUDOperation with the test dataSource and entity class
        crudOperation = CRUDOperation(dataSource, TestEntity::class)
    }

    @AfterEach
    fun tearDown() {
        // Drop test table
        connection.createStatement().use { statement ->
            statement.execute("DROP TABLE IF EXISTS TestEntity")
        }
        
        // Close connection
        if (!connection.isClosed) {
            connection.close()
        }
    }

    @Test
    fun testInsert() {
        // Create test entities
        val testEntities = listOf(
            TestEntity(id = 1, name = "Test 1", itemValue = 10.5, createdAt = Timestamp(System.currentTimeMillis())),
            TestEntity(id = 2, name = "Test 2", itemValue = 20.5, createdAt = Timestamp(System.currentTimeMillis()))
        )
        
        // Insert entities
        val result = crudOperation.insert(testEntities)
        
        // Verify result
        assertEquals(testEntities.size, result.size)
        
        // Verify data in database
        connection.createStatement().use { statement ->
            val resultSet = statement.executeQuery("SELECT COUNT(*) FROM TestEntity")
            assertTrue(resultSet.next())
            assertEquals(testEntities.size, resultSet.getInt(1))
        }
    }

    @Test
    fun testUpdate() {
        // Insert test entity
        val testEntity = TestEntity(id = 1, name = "Original Name", itemValue = 10.5, createdAt = Timestamp(System.currentTimeMillis()))
        crudOperation.insert(listOf(testEntity))
        
        // Update entity
        val updatedEntity = testEntity.copy(name = "Updated Name", itemValue = 15.5)
        crudOperation.update(listOf(updatedEntity))
        
        // Verify update in database
        connection.createStatement().use { statement ->
            val resultSet = statement.executeQuery("SELECT name, itemValue FROM TestEntity WHERE id = 1")
            assertTrue(resultSet.next())
            assertEquals("Updated Name", resultSet.getString("name"))
            assertEquals(15.5, resultSet.getDouble("itemValue"), 0.001)
        }
    }

    @Test
    fun testDelete() {
        // Insert test entities
        val testEntities = listOf(
            TestEntity(id = 1, name = "Test 1", itemValue = 10.5, createdAt = Timestamp(System.currentTimeMillis())),
            TestEntity(id = 2, name = "Test 2", itemValue = 20.5, createdAt = Timestamp(System.currentTimeMillis()))
        )
        crudOperation.insert(testEntities)
        
        // Delete one entity
        crudOperation.delete(listOf(testEntities[0]))
        
        // Verify deletion
        connection.createStatement().use { statement ->
            val resultSet = statement.executeQuery("SELECT COUNT(*) FROM TestEntity")
            assertTrue(resultSet.next())
            assertEquals(1, resultSet.getInt(1))
            
            val idResultSet = statement.executeQuery("SELECT id FROM TestEntity")
            assertTrue(idResultSet.next())
            assertEquals(2, idResultSet.getInt("id"))
        }
    }

    @Test
    fun testInsertWithInvalidEntity() {
        // Test with entity that doesn't have @SqlTable annotation
        class InvalidEntity(val id: Int, val name: String)
        
        val invalidEntities = listOf(InvalidEntity(1, "Invalid"))
        
        // This should throw an IllegalArgumentException
        assertFailsWith<IllegalArgumentException> {
            val invalidCrudOperation = CRUDOperation<InvalidEntity>(dataSource, InvalidEntity::class)
            invalidCrudOperation.insert(invalidEntities)
        }
    }

    @Test
    fun testUpdateWithMissingPrimaryKey() {

        // Create a class without primary key
        @SqlTable(tableName = "EntityWithoutPrimaryKey")
        data class EntityWithoutPrimaryKey(val id: Int, val name: String)
        
        val entities = listOf(EntityWithoutPrimaryKey(1, "No Primary Key"))
        
        // This should throw an IllegalStateException
        assertFailsWith<IllegalStateException> {
            val invalidCrudOperation = CRUDOperation<EntityWithoutPrimaryKey>(dataSource, EntityWithoutPrimaryKey::class)
            invalidCrudOperation.update(entities)
        }
    }
    
    @Test
    fun testDeleteWithMissingPrimaryKey() {

        // Create a class without primary key
        @SqlTable(tableName = "EntityWithoutPrimaryKey")
        data class EntityWithoutPrimaryKey(val id: Int, val name: String)
        
        val entities = listOf(EntityWithoutPrimaryKey(1, "No Primary Key"))
        
        // This should throw an IllegalStateException
        assertFailsWith<IllegalStateException> {
            val invalidCrudOperation = CRUDOperation<EntityWithoutPrimaryKey>(dataSource, EntityWithoutPrimaryKey::class)
            invalidCrudOperation.delete(entities)
        }
    }
    
    @Test
    fun testDeleteWithInvalidEntity() {
        // Test with entity that doesn't have @SqlTable annotation
        class InvalidEntity(val id: Int, val name: String)
        
        val invalidEntities = listOf(InvalidEntity(1, "Invalid"))
        
        // This should throw an IllegalArgumentException
        assertFailsWith<IllegalArgumentException> {
            val invalidCrudOperation = CRUDOperation<InvalidEntity>(dataSource, InvalidEntity::class)
            invalidCrudOperation.delete(invalidEntities)
        }
    }
    
    /**
     * Tests the findAll() method for successful retrieval of multiple records.
     * 
     * This test:
     * 1. Inserts multiple test entities into the database
     * 2. Calls the findAll() method
     * 3. Verifies that all inserted entities are retrieved correctly
     * 4. Checks that the properties of the retrieved entities match the original entities
     */
    @Test
    fun testFindAll() {
        // Create and insert test entities
        val testEntities = listOf(
            TestEntity(id = 1, name = "Test 1", itemValue = 10.5, createdAt = Timestamp(System.currentTimeMillis())),
            TestEntity(id = 2, name = "Test 2", itemValue = 20.5, createdAt = Timestamp(System.currentTimeMillis())),
            TestEntity(id = 3, name = "Test 3", itemValue = 30.5, createdAt = Timestamp(System.currentTimeMillis()))
        )
        
        crudOperation.insert(testEntities)
        
        // Call findAll() method
        val results = crudOperation.findAll()
        
        // Verify results
        assertEquals(testEntities.size, results.size, "Should return all inserted entities")
        
        // Verify each entity was retrieved correctly
        val resultIds = results.map { it.id }.toSet()
        val expectedIds = testEntities.map { it.id }.toSet()
        assertEquals(expectedIds, resultIds, "Retrieved entity IDs should match inserted entity IDs")
        
        // Verify entity properties
        for (entity in testEntities) {
            val result = results.find { it.id == entity.id }
            assertNotNull(result, "Entity with ID ${entity.id} should be found")
            assertEquals(entity.name, result.name, "Name should match")
            assertEquals(entity.itemValue, result.itemValue, 0.001, "Item value should match")
        }
    }
    
    /**
     * Tests the findAll() method when the table is empty.
     * 
     * This test:
     * 1. Ensures the table is empty by deleting all records
     * 2. Calls the findAll() method
     * 3. Verifies that an empty list is returned
     */
    @Test
    fun testFindAllEmptyTable() {
        // Ensure table is empty
        connection.createStatement().use { statement ->
            statement.execute("DELETE FROM TestEntity")
        }
        
        // Call findAll() method
        val results = crudOperation.findAll()
        
        // Verify results
        assertTrue(results.isEmpty(), "Should return empty list for empty table")
    }
    
    /**
     * Tests the findAll() method with an invalid entity class that doesn't have the @SqlTable annotation.
     * 
     * This test:
     * 1. Creates an invalid entity class without the @SqlTable annotation
     * 2. Attempts to call the findAll() method with this entity class
     * 3. Verifies that an IllegalArgumentException is thrown
     */
    @Test
    fun testFindAllWithInvalidEntity() {
        // Test with entity that doesn't have @SqlTable annotation
        class InvalidEntity(val id: Int, val name: String)
        
        // This should throw an IllegalArgumentException
        assertFailsWith<IllegalArgumentException>("Should throw exception for entity without @SqlTable annotation") {
            val invalidCrudOperation = CRUDOperation<InvalidEntity>(dataSource, InvalidEntity::class)
            invalidCrudOperation.findAll()
        }
    }
    
    /**
     * Tests the findByPrimaryKey() method for successful retrieval of a record.
     * 
     * This test:
     * 1. Inserts a test entity into the database
     * 2. Calls the findByPrimaryKey() method with the entity's primary key
     * 3. Verifies that the correct entity is retrieved
     * 4. Checks that the properties of the retrieved entity match the original entity
     */
    @Test
    fun testFindByPrimaryKey() {
        // Create and insert a test entity
        val testEntity = TestEntity(id = 1, name = "Test Entity", itemValue = 15.5, createdAt = Timestamp(System.currentTimeMillis()))
        crudOperation.insert(listOf(testEntity))
        
        // Call findByPrimaryKey() method
        val result = crudOperation.findByPrimaryKey(testEntity.id)
        
        // Verify result
        assertNotNull(result, "Should return an entity for existing primary key")
        assertEquals(testEntity.id, result.id, "ID should match")
        assertEquals(testEntity.name, result.name, "Name should match")
        assertEquals(testEntity.itemValue, result.itemValue, 0.001, "Item value should match")
    }
    
    /**
     * Tests the findByPrimaryKey() method when the record is not found.
     * 
     * This test:
     * 1. Ensures the table is empty by deleting all records
     * 2. Calls the findByPrimaryKey() method with a non-existent primary key
     * 3. Verifies that null is returned
     */
    @Test
    fun testFindByPrimaryKeyNotFound() {
        // Ensure table is empty
        connection.createStatement().use { statement ->
            statement.execute("DELETE FROM TestEntity")
        }
        
        // Call findByPrimaryKey() with a non-existent ID
        val result = crudOperation.findByPrimaryKey(999)
        
        // Verify result
        assertNull(result, "Should return null for non-existent primary key")
    }
    
    /**
     * Tests the findByPrimaryKey() method with an invalid entity class that doesn't have the @SqlTable annotation.
     * 
     * This test:
     * 1. Creates an invalid entity class without the @SqlTable annotation
     * 2. Attempts to call the findByPrimaryKey() method with this entity class
     * 3. Verifies that an IllegalArgumentException is thrown
     */
    @Test
    fun testFindByPrimaryKeyWithInvalidEntity() {
        // Test with entity that doesn't have @SqlTable annotation
        class InvalidEntity(val id: Int, val name: String)
        
        // This should throw an IllegalArgumentException
        assertFailsWith<IllegalArgumentException>("Should throw exception for entity without @SqlTable annotation") {
            val invalidCrudOperation = CRUDOperation<InvalidEntity>(dataSource, InvalidEntity::class)
            invalidCrudOperation.findByPrimaryKey(1)
        }
    }
    
    /**
     * Tests the findByPrimaryKey() method with an entity class that doesn't have any property
     * annotated with @PrimaryKey.
     * 
     * This test:
     * 1. Creates an entity class with the @SqlTable annotation but without any @PrimaryKey annotation
     * 2. Attempts to call the findByPrimaryKey() method with this entity class
     * 3. Verifies that an IllegalStateException is thrown
     */
    @Test
    fun testFindByPrimaryKeyWithMissingPrimaryKey() {
        // Create a class without primary key
        @SqlTable(tableName = "EntityWithoutPrimaryKey")
        data class EntityWithoutPrimaryKey(val id: Int, val name: String)
        
        // This should throw an IllegalStateException
        assertFailsWith<IllegalStateException>("Should throw exception for entity without @PrimaryKey annotation") {
            val invalidCrudOperation = CRUDOperation<EntityWithoutPrimaryKey>(dataSource, EntityWithoutPrimaryKey::class)
            invalidCrudOperation.findByPrimaryKey(1)
        }
    }
    
    /**
     * Tests the findByPrimaryKey() method with an entity class that has multiple properties
     * annotated with @PrimaryKey.
     * 
     * This test:
     * 1. Creates an entity class with the @SqlTable annotation and multiple properties annotated with @PrimaryKey
     * 2. Attempts to call the findByPrimaryKey() method with this entity class
     * 3. Verifies that an IllegalStateException is thrown because the method only supports entities with a single primary key
     */
    @Test
    fun testFindByPrimaryKeyWithMultiplePrimaryKeys() {
        // Create a class with multiple primary keys
        @SqlTable(tableName = "EntityWithMultiplePrimaryKeys")
        data class EntityWithMultiplePrimaryKeys(
            @PrimaryKey val id1: Int,
            @PrimaryKey val id2: Int,
            val name: String
        )
        
        // This should throw an IllegalStateException
        assertFailsWith<IllegalStateException>("Should throw exception for entity with multiple primary keys") {
            val invalidCrudOperation = CRUDOperation<EntityWithMultiplePrimaryKeys>(dataSource, EntityWithMultiplePrimaryKeys::class)
            invalidCrudOperation.findByPrimaryKey(1)
        }
    }
    
    /**
     * Tests that fields with @Generated annotation are excluded during insert operations.
     * 
     * This test:
     * 1. Creates a test entity class with a field marked with @Generated
     * 2. Creates a test table for this entity
     * 3. Inserts an entity into the database
     * 4. Verifies that the @Generated field is excluded from the SQL query
     */
    @Test
    fun testInsertWithGeneratedField() {
        // Create a class with a @Generated field
        @SqlTable(tableName = "EntityWithGeneratedField")
        data class EntityWithGeneratedField(
            @PrimaryKey val id: Int,
            val name: String,
            @Generated val generatedValue: String = "auto-generated"
        )
        
        // Create table for the test entity
        val createTableSQL = """
            CREATE TABLE IF NOT EXISTS EntityWithGeneratedField (
                id INT PRIMARY KEY,
                name VARCHAR(255),
                generatedValue VARCHAR(255)
            )
        """.trimIndent()
        
        connection.createStatement().use { statement ->
            statement.execute(createTableSQL)
        }
        
        // Create and insert test entity
        val testEntity = EntityWithGeneratedField(id = 1, name = "Test Entity")
        val crudOp = CRUDOperation(dataSource, EntityWithGeneratedField::class)
        crudOp.insert(listOf(testEntity))
        
        // Verify that the entity was inserted correctly
        connection.createStatement().use { statement ->
            val resultSet = statement.executeQuery("SELECT * FROM EntityWithGeneratedField WHERE id = 1")
            assertTrue(resultSet.next(), "Entity should be inserted")
            assertEquals(1, resultSet.getInt("id"), "ID should match")
            assertEquals("Test Entity", resultSet.getString("name"), "Name should match")
            
            // The generatedValue field should be NULL in the database since it was excluded from the insert
            assertNull(resultSet.getString("generatedValue"), "Generated field should be NULL in database")
        }
        
        // Clean up
        connection.createStatement().use { statement ->
            statement.execute("DROP TABLE IF EXISTS EntityWithGeneratedField")
        }
    }
    
    /**
     * Tests that fields with @Generated annotation are excluded during update operations.
     * 
     * This test:
     * 1. Creates a test entity class with a field marked with @Generated
     * 2. Creates a test table for this entity
     * 3. Inserts an entity into the database with an initial value for the @Generated field
     * 4. Updates the entity
     * 5. Verifies that the @Generated field is not updated
     */
    @Test
    fun testUpdateWithGeneratedField() {
        // Create a class with a @Generated field
        @SqlTable(tableName = "EntityWithGeneratedField")
        data class EntityWithGeneratedField(
            @PrimaryKey val id: Int,
            var name: String,
            @Generated val generatedValue: String = "auto-generated"
        )
        
        // Create table for the test entity
        val createTableSQL = """
            CREATE TABLE IF NOT EXISTS EntityWithGeneratedField (
                id INT PRIMARY KEY,
                name VARCHAR(255),
                generatedValue VARCHAR(255)
            )
        """.trimIndent()
        
        connection.createStatement().use { statement ->
            statement.execute(createTableSQL)
        }
        
        // Insert a test entity with an initial value for the generatedValue field
        connection.createStatement().use { statement ->
            statement.execute("""
                INSERT INTO EntityWithGeneratedField (id, name, generatedValue)
                VALUES (1, 'Original Name', 'initial-value')
            """.trimIndent())
        }
        
        // Create and update test entity
        val testEntity = EntityWithGeneratedField(id = 1, name = "Updated Name", generatedValue = "updated-value")
        val crudOp = CRUDOperation(dataSource, EntityWithGeneratedField::class)
        crudOp.update(listOf(testEntity))
        
        // Verify that the entity was updated correctly
        connection.createStatement().use { statement ->
            val resultSet = statement.executeQuery("SELECT * FROM EntityWithGeneratedField WHERE id = 1")
            assertTrue(resultSet.next(), "Entity should exist")
            assertEquals(1, resultSet.getInt("id"), "ID should match")
            assertEquals("Updated Name", resultSet.getString("name"), "Name should be updated")
            
            // The generatedValue field should still have its initial value since it was excluded from the update
            assertEquals("initial-value", resultSet.getString("generatedValue"), "Generated field should not be updated")
        }
        
        // Clean up
        connection.createStatement().use { statement ->
            statement.execute("DROP TABLE IF EXISTS EntityWithGeneratedField")
        }
    }
}