package com.example.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BotLogs {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun addLog(message: String) {
        val stamp = dateFormat.format(Date())
        val line = "[$stamp] $message"
        _logs.update { current ->
            val next = if (current.size >= 400) current.drop(current.size - 399) else current
            next + line
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun getAllLogsText(): String = _logs.value.joinToString("\n")

    fun copyToClipboard(context: Context, header: String = ""): Boolean {
        val body = getAllLogsText()
        val payload = if (header.isNotBlank()) "$header\n$body" else body
        val safe = payload.takeLast(250_000)
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Earth Puzzles Bot Logs", safe))
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun shareText(context: Context, title: String, content: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, content.takeLast(250_000))
            type = "text/plain"
        }
        val chooser = Intent.createChooser(sendIntent, title)
        if (context !is android.app.Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
