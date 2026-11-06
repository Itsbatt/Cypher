package com.cypher.music.network.innertube

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** One InnerTube response: the items it contains plus the token that leads to the next page. */
data class PageSlice<T>(
    val items: List<T> = emptyList(),
    val continuation: String? = null,
    val totalCount: Int? = null
)

/** Result of walking every page of a playlist or collection. */
data class PagedResult<T>(
    val items: List<T> = emptyList(),
    val totalCount: Int? = null,
    val pagesFetched: Int = 0,
    val complete: Boolean = true,
    val error: String? = null
)

/**
 * Follows InnerTube continuation tokens until YouTube Music stops returning one.
 *
 * The loop has no item or page cap: it ends when the API omits the continuation token,
 * so playlists of any size are imported in full. It is still bounded against hostile
 * or broken responses:
 * - a continuation token that repeats ends the walk as incomplete instead of looping;
 * - every page is retried a small number of times before the sync is reported as partial;
 * - items are de-duplicated by [idOf] while keeping the first position seen, which keeps
 *   the original playlist order and tolerates overlap between pages.
 */
class InnerTubePager(
    private val maxAttemptsPerPage: Int = DEFAULT_MAX_ATTEMPTS,
    private val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
    private val sleep: suspend (Long) -> Unit = { delay(it) }
) {
    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val DEFAULT_RETRY_DELAY_MS = 600L
    }

    suspend fun <T> paginate(
        fetchPage: suspend (continuation: String?) -> PageSlice<T>,
        idOf: (T) -> String,
        onProgress: (processed: Int, totalCount: Int?) -> Unit = { _, _ -> }
    ): PagedResult<T> {
        val attemptsPerPage = maxAttemptsPerPage.coerceAtLeast(1)
        val items = ArrayList<T>()
        val seenIds = HashSet<String>()
        val seenTokens = HashSet<String>()
        var continuation: String? = null
        var pagesFetched = 0
        var totalCount: Int? = null

        while (true) {
            var page: PageSlice<T>? = null
            var lastFailure: Throwable? = null

            for (attempt in 1..attemptsPerPage) {
                if (attempt > 1) sleep(retryDelayMs * (attempt - 1))
                try {
                    page = fetchPage(continuation)
                    break
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    lastFailure = failure
                }
            }

            val loadedPage = page ?: return PagedResult(
                items = items,
                totalCount = totalCount,
                pagesFetched = pagesFetched,
                complete = false,
                error = describe(lastFailure)
            )

            pagesFetched++
            loadedPage.totalCount?.takeIf { it > 0 }?.let { totalCount = it }
            for (item in loadedPage.items) {
                if (seenIds.add(idOf(item))) items.add(item)
            }
            onProgress(items.size, totalCount)

            val next = loadedPage.continuation?.takeIf { it.isNotBlank() }
                ?: return PagedResult(items, totalCount, pagesFetched)

            if (!seenTokens.add(next)) {
                return PagedResult(
                    items = items,
                    totalCount = totalCount,
                    pagesFetched = pagesFetched,
                    complete = false,
                    error = "YouTube Music devolvió un token de continuación repetido"
                )
            }
            continuation = next
        }
    }

    private fun describe(failure: Throwable?): String =
        failure?.message?.takeIf { it.isNotBlank() } ?: failure?.javaClass?.simpleName ?: "No se pudo obtener la página"
}
