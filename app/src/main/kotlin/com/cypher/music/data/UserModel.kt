package com.cypher.music.data

data class User(
    val username: String,
    val displayName: String,
    val avatarColor: Long = 0xFFFA243C,
    val createdAt: Long = System.currentTimeMillis()
)
