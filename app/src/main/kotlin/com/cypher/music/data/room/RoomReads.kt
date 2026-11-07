package com.cypher.music.data.room

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Media3 library callbacks are synchronous, so their database reads have to block.
 * Room no longer allows main-thread queries; this keeps the Android Auto / media
 * browser callbacks working while the query runs on the IO dispatcher.
 */
fun <T> dbRead(block: () -> T): T = runBlocking(Dispatchers.IO) { block() }
