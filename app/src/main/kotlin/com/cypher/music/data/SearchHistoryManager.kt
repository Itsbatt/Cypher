package com.cypher.music.data

import android.content.Context
import com.cypher.music.data.room.CypherDatabase
import com.cypher.music.data.room.SearchQueryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Reactive and persistent manager for recent search queries in Cypher.
 * Migrated from SharedPreferences to Room Database (CypherDatabase).
 *
 * Removes the strict 15-item limit and exposes a reactive Flow<List<String>> for the UI.
 */
class SearchHistoryManager(
    context: Context,
    private val db: CypherDatabase = CypherDatabase.getInstance(context),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val dao = db.searchQueryDao()

    private val _recentSearches = MutableStateFlow<List<String>>(emptyList())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    init {
        reloadFromDatabase()
    }

    private fun reloadFromDatabase() {
        scope.launch {
            try {
                val list = dao.getRecentQueries()
                _recentSearches.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Inserts or updates a search query in Room with a fresh timestamp.
     * Enforces LIFO ordering and prevents duplicate records without a strict cap.
     */
    fun addQuery(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return

        scope.launch {
            try {
                dao.insertQuery(SearchQueryEntity(cleanQuery, System.currentTimeMillis()))
                val updated = dao.getRecentQueries()
                _recentSearches.value = updated
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Removes an individual search query from the database.
     */
    fun removeQuery(query: String) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return

        scope.launch {
            try {
                dao.deleteQuery(cleanQuery)
                val updated = dao.getRecentQueries()
                _recentSearches.value = updated
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Clears all recorded search history queries in Room.
     */
    fun clearAll() {
        scope.launch {
            try {
                dao.clearAll()
                _recentSearches.value = emptyList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
