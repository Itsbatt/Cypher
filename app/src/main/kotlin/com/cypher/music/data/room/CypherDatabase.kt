package com.cypher.music.data.room

import android.content.Context

/**
 * Cypher Database Kotlin Extensions & Helper Accessors.
 * Provides unified, idiomatic Kotlin access to the underlying native Room database.
 */
val Context.cypherDatabase: CypherDatabase
    get() = CypherDatabase.getInstance(this)

inline fun <T> Context.withCypherDatabase(block: CypherDatabase.() -> T): T {
    return cypherDatabase.block()
}
