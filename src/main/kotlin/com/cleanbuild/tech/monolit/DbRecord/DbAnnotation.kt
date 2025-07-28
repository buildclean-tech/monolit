package com.cleanbuild.tech.monolit.com.cleanbuild.tech.monolit.DbEntity

/**
 * Marks a data class as a database table entity.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class SqlTable(val tableName: String)

/**
 * Marks a field as part of the primary key.
 * Multiple fields can be marked with this annotation to create a composite primary key.
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class PrimaryKey