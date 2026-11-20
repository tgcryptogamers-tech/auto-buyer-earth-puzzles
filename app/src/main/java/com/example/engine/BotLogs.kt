package com.example.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BotLogs {
    private const val MAX_LINES = 400
    private const val LOG_FILE_NAME = "bot_logs.txt"

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val fileLock = Any()

    @Volatile
    private var logFile: File? = null

    fun init(context: Context) {
        synchronized(fileLock) {
            if (logFile != null) return
            try {
                val target = File(context.applicationContext.filesDir, LOG_FILE_NAME)
                logFile = target
                if (target.exists()) {
                    val savedLines = target.readLines().takeLast(MAX_LINES)
                    if (savedLines.isNotEmpty() && _logs.value.isEmpty()) {
                        _logs.value = savedLines
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    fun addLog(message: String) {
        val stamp = dateFormat.format(Date())
        val line = "[$stamp] $message"
        var snapshot: List<String> = emptyList()
        _logs.update { current ->
            val next = if (current.size >= MAX_LINES) current.drop(current.size - (MAX_LINES - 1)) else current
            val updated = next + line
            snapshot = updated
            updated
        }
        persistLines(snapshot)
    }

    fun clearLogs() {
        _logs.value = emptyList()
        synchronized(fileLock) {
            try {
                logFile?.writeText("")
            } catch (_: Throwable) {
            }
        }
    }

    private fun persistLines(lines: List<String>) {
        synchronized(fileLock) {
            val f = logFile ?: return
            try {
                f.writeText(lines.joinToString("\n"))
            } catch (_: Throwable) {
            }
        }
    }

    fun getAllLogsText(): String = _logs.value.joinToString("\n")

    private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
        is android.app.Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    fun copyToClipboard(context: Context, header: String = ""): Boolean {
        init(context)
        val body = getAllLogsText()
        if (body.isBlank() && header.isBlank()) return false
        val payload = if (header.isNotBlank()) "$header\n$body" else body
        val safe = payload.takeLast(120_000)

        // When invoked from an Activity (in-app), write directly and verify.
        val activity = context.findActivity()
        if (activity != null) {
            return com.example.ClipboardCopyActivity.writeClipboardDirect(activity, safe)
        }

        // When invoked from a background Service (Overlay), Android 10+ blocks Service
        // clipboard writes unless a focused foreground Activity performs the write.
        return try {
            com.example.ClipboardCopyActivity.launch(context, safe)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun shareText(context: Context, title: String, content: String) {
        val safeContent = content.ifBlank { getAllLogsText() }.ifBlank { "Логи пока пусты" }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, safeContent.takeLast(250_000))
            type = "text/plain"
        }
        val chooser = Intent.createChooser(sendIntent, title).apply {
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        try {
            context.startActivity(chooser)
        } catch (_: Throwable) {
        }
    }
}
