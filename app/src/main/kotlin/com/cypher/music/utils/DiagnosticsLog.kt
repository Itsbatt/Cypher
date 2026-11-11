package com.cypher.music.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.cypher.music.BuildConfig
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Small in-memory journal of the playback and download problems the app already
 * reports to logcat. It exists so the user can hand a readable report over when
 * something goes wrong without needing adb or a rooted device.
 *
 * Everything is kept in memory (bounded) and never leaves the device on its own:
 * the report is only produced when the user taps "Exportar log" in Settings.
 */
object DiagnosticsLog {

    private const val MAX_ENTRIES = 400
    private const val TAG = "DiagnosticsLog"
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    data class Entry(
        val timestamp: Long,
        val level: Char,
        val tag: String,
        val message: String
    )

    private val entries = ArrayDeque<Entry>()
    private val lock = Any()

    fun record(tag: String, level: Char, message: String, error: Throwable? = null) {
        val full = if (error == null) {
            message
        } else {
            "$message · ${error::class.java.simpleName}: ${error.message}"
        }
        synchronized(lock) {
            entries.addLast(Entry(System.currentTimeMillis(), level, tag, full.take(600)))
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
        }
    }

    fun info(tag: String, message: String) = record(tag, 'I', message)

    fun warn(tag: String, message: String, error: Throwable? = null) =
        record(tag, 'W', message, error)

    fun error(tag: String, message: String, error: Throwable? = null) =
        record(tag, 'E', message, error)

    /** Keeps a copy of crashes so an exported report explains a sudden close. */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                record(
                    "Crash",
                    'E',
                    "Excepción no controlada en ${thread.name}",
                    error
                )
                writeCrashSnapshot(error)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun snapshot(): List<Entry> = synchronized(lock) { entries.toList() }

    /**
     * Full text report: app/build identity, device, the journal above and, when the
     * system allows reading it, the last lines of this process' own logcat.
     */
    fun buildReport(context: Context): String = buildString {
        appendLine("Cypher · informe de errores")
        appendLine("Generado: ${dateFormat.format(Date())}")
        appendLine("Versión: ${BuildConfig.VERSION_NAME} (versionCode ${BuildConfig.VERSION_CODE})")
        appendLine("Build ID: ${BuildConfig.BUILD_ID}")
        appendLine("Paquete: ${context.packageName}")
        appendLine("Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}, ${Build.VERSION.SECURITY_PATCH})")
        appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine()
        appendLine("== Eventos registrados (${snapshot().size}) ==")
        val list = snapshot()
        if (list.isEmpty()) {
            appendLine("(sin eventos en esta sesión)")
        } else {
            list.forEach { entry ->
                append(timeFormat.format(Date(entry.timestamp)))
                append(" ${entry.level}/${entry.tag}: ")
                appendLine(entry.message)
            }
        }
        appendLine()
        appendLine("== Logcat del proceso ==")
        appendLine(readOwnLogcat() ?: "(el sistema no permite leer logcat desde la app)")
    }

    private fun readOwnLogcat(): String? = runCatching {
        val process = ProcessBuilder(
            "/system/bin/logcat",
            "-d",
            "-v",
            "threadtime",
            "-t",
            "1200",
            "--pid=${android.os.Process.myPid()}"
        ).redirectErrorStream(true).start()

        val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
        process.waitFor(4, TimeUnit.SECONDS)
        if (output.isBlank()) null else output
    }.getOrNull()

    /** Writes the last journal next to the app's private files after a crash. */
    private fun writeCrashSnapshot(error: Throwable) {
        runCatching {
            val context = crashContext ?: return
            val file = File(context.filesDir, "diagnostics/last_crash.txt")
            file.parentFile?.mkdirs()
            file.writeText(
                buildString {
                    appendLine(dateFormat.format(Date()))
                    appendLine(error.stackTraceToString())
                    appendLine()
                    snapshot().takeLast(120).forEach { entry ->
                        appendLine("${timeFormat.format(Date(entry.timestamp))} ${entry.level}/${entry.tag}: ${entry.message}")
                    }
                }
            )
        }
    }

    /** Set once from the Application so the crash snapshot has a directory. */
    @Volatile
    private var crashContext: Context? = null

    fun attach(context: Context) {
        crashContext = context.applicationContext
    }

    fun logTag() = TAG
}
