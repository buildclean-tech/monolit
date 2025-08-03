package com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.repository

import com.cleanbuild.tech.monolit.DbEntity.PrimaryKey
import com.cleanbuild.tech.monolit.DbEntity.SqlTable
import java.sql.Timestamp

/**
 * Test entity class for CRUDOperation tests
 */
@SqlTable(tableName = "TestEntity")
data class TestEntity(
    @PrimaryKey
    val id: Int,
    val name: String,
    val itemValue: Double,
    val createdAt: Timestamp = Timestamp(System.currentTimeMillis())
)
