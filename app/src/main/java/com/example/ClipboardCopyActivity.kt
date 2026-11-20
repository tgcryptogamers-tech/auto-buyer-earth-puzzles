package com.example

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.example.engine.BotLogs

/**
 * Transparent 1-frame foreground Activity used when copying logs from the background
 * OverlayControlService, because Android 10+ silently blocks ClipboardManager writes
 * from non-focused Services while a game/Mini-App is in the foreground.
 */
class ClipboardCopyActivity : Activity() {
    private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!handled && !isFinishing) {
                handled = true
                val text = intent?.getStringExtra(EXTRA_TEXT)?.ifBlank { null }
                    ?: BotLogs.getAllLogsText()
                val ok = writeClipboardDirect(this, text)
                Toast.makeText(
                    applicationContext,
                    if (ok) "✅ Логи скопированы в буфер обмена" else "⚠️ Не удалось записать в буфер — откройте «ТЕКСТ» в боте",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }, 350L)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !handled) {
            handled = true
            val text = intent?.getStringExtra(EXTRA_TEXT)?.ifBlank { null }
                ?: BotLogs.getAllLogsText()
            val ok = writeClipboardDirect(this, text)
            Toast.makeText(
                applicationContext,
                if (ok) "✅ Логи скопированы в буфер обмена" else "⚠️ Не удалось записать в буфер — откройте «ТЕКСТ» в боте",
                Toast.LENGTH_SHORT
            ).show()
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        private const val EXTRA_TEXT = "extra_copy_text"

        fun launch(context: Context, text: String) {
            val intent = Intent(context, ClipboardCopyActivity::class.java).apply {
                putExtra(EXTRA_TEXT, text.takeLast(120_000))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
        }

        fun writeClipboardDirect(context: Context, text: String): Boolean {
            if (text.isBlank()) return false
            val safe = text.takeLast(120_000)
            return try {
                val cm = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                    ?: (context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                    ?: return false
                cm.setPrimaryClip(ClipData.newPlainText("Earth Puzzles Bot Logs", safe))
                val readBack = cm.primaryClip?.getItemAt(0)?.text?.toString()
                !readBack.isNullOrEmpty()
            } catch (_: Throwable) {
                false
            }
        }
    }
}
