package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppConfiguration
import com.example.data.AppDatabase
import com.example.data.MarketTab
import com.example.engine.BotLogs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class OverlayControlService : Service() {

    companion object {
        const val ACTION_START_PROJECTION = "ACTION_START_PROJECTION"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_DATA = "EXTRA_DATA"

        var instance: OverlayControlService? = null
            private set
    }

    fun isControlPanelVisible(): Boolean = controlPanel != null

    private lateinit var windowManager: WindowManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var logsJob: Job? = null

    private var floatingBubble: FrameLayout? = null
    private var controlPanel: FrameLayout? = null
    private var calibrationOverlay: FrameLayout? = null

    private var autoBuyActive = false
    private var currentConfig: AppConfiguration? = null

    private var calibrationStep = 1
    private var tempPuzzlesX = -1f
    private var tempPuzzlesY = -1f
    private var tempLandsX = -1f
    private var tempLandsY = -1f
    private var lastTapTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForegroundSafe(useMediaProjectionType = MediaProjectionHelper.hasProjection())

        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.configurationDao().getConfigurationFlow().onEach { config ->
                if (config != null) {
                    val wasCalibration = currentConfig?.isCalibrationEnabled ?: false
                    currentConfig = config
                    autoBuyActive = config.autoBuyEnabled
                    updatePanelUi()
                    updateBubbleColor()

                    if (config.isCalibrationEnabled && !wasCalibration) {
                        BotLogs.addLog("🎯 Режим калибровки включен. Перейдите в игру и нажмите на жёлтый кружок!")
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                applicationContext,
                                "Калибровка готова! Откройте игру и нажмите жёлтый кружок.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else if (!config.isCalibrationEnabled && wasCalibration) {
                        stopCalibrationFlow()
                    }
                }
            }.launchIn(serviceScope)
        }

        if (Settings.canDrawOverlays(this)) {
            createFloatingBubble()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_PROJECTION) {
            startForegroundSafe(useMediaProjectionType = true)
        } else if (MediaProjectionHelper.hasProjection()) {
            startForegroundSafe(useMediaProjectionType = true)
        }
        if (floatingBubble == null && controlPanel == null && Settings.canDrawOverlays(this)) {
            createFloatingBubble()
        }
        return START_STICKY
    }

    private fun startForegroundSafe(useMediaProjectionType: Boolean) {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val type = if (useMediaProjectionType) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            startForeground(101, notification, type)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (useMediaProjectionType) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                0
            }
            startForeground(101, notification, type)
        } else {
            startForeground(101, notification)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, "OverlayServiceChannel")
            .setContentTitle("Earth Puzzles Overlay")
            .setContentText("Плавающий кружок управления ботом (Puzzles ↔ Lands)")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "OverlayServiceChannel",
                "Earth Puzzles Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateBubbleColor() {
        val bubble = floatingBubble ?: return
        val size = (52 * resources.displayMetrics.density).toInt()
        val strokeColor = when {
            currentConfig?.isCalibrationEnabled == true -> "#FFEB3B" // Yellow: calibration ready
            autoBuyActive -> "#28C76F" // Emerald green: active
            else -> "#448AFF" // Blue: idle
        }
        bubble.background = GradientDrawable().apply {
            setColor(Color.parseColor("#111B17"))
            setStroke((3 * resources.displayMetrics.density).toInt(), Color.parseColor(strokeColor))
            cornerRadius = size / 2f
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingBubble() {
        if (floatingBubble != null || !Settings.canDrawOverlays(this)) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 260
        }

        val size = (52 * resources.displayMetrics.density).toInt()
        val bubble = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(size, size)
            val icon = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_media_play)
                setColorFilter(Color.WHITE)
                val iconSize = (24 * resources.displayMetrics.density).toInt()
                layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
            }
            addView(icon)
        }

        bubble.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isMoving = false
            private val handler = Handler(Looper.getMainLooper())
            private var isLongPressed = false

            private val longPressRunnable = Runnable {
                isLongPressed = true
                showControlPanel()
            }

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isMoving = false
                        isLongPressed = false
                        handler.postDelayed(longPressRunnable, 750)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            handler.removeCallbacks(longPressRunnable)
                            params.x = (initialX + dx).toInt()
                            params.y = (initialY + dy).toInt()
                            windowManager.updateViewLayout(bubble, params)
                            isMoving = true
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longPressRunnable)
                        if (event.action == MotionEvent.ACTION_UP && !isMoving && !isLongPressed) {
                            val cfg = currentConfig
                            if (cfg?.isCalibrationEnabled == true) {
                                if (calibrationOverlay == null) startCalibrationFlow()
                            } else {
                                toggleAutoBuy()
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })

        floatingBubble = bubble
        windowManager.addView(bubble, params)
        updateBubbleColor()
    }

    private fun removeFloatingBubble() {
        floatingBubble?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
            floatingBubble = null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showControlPanel() {
        if (controlPanel != null || !Settings.canDrawOverlays(this)) return
        removeFloatingBubble()

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val width = (280 * resources.displayMetrics.density).toInt()
        val height = (360 * resources.displayMetrics.density).toInt()

        val params = WindowManager.LayoutParams(
            width,
            height,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val panel = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#111B17"))
                cornerRadius = 16 * resources.displayMetrics.density
                setStroke((1 * resources.displayMetrics.density).toInt(), Color.parseColor("#334B40"))
            }
            setPadding(24, 24, 24, 24)
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "Earth Puzzles Bot"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title)

        val closeBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.WHITE)
            setPadding(8, 8, 8, 8)
            setOnClickListener {
                removeControlPanel()
                createFloatingBubble()
            }
        }
        header.addView(closeBtn)

        header.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = (initialX + (event.rawX - initialTouchX)).toInt()
                        params.y = (initialY + (event.rawY - initialTouchY)).toInt()
                        windowManager.updateViewLayout(panel, params)
                        return true
                    }
                }
                return false
            }
        })
        rootLayout.addView(header)

        val configText = TextView(this).apply {
            setTextColor(Color.parseColor("#F5C443"))
            textSize = 12f
            setPadding(0, 12, 0, 12)
        }
        rootLayout.addView(configText)

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val toggleBtn = Button(this).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 8
            }
            setOnClickListener { toggleAutoBuy() }
        }
        buttonsRow.addView(toggleBtn)

        val settingsBtn = Button(this).apply {
            text = "НАСТРОЙКИ"
            setBackgroundColor(Color.parseColor("#21332B"))
            setTextColor(Color.WHITE)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                startActivity(
                    Intent(this@OverlayControlService, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                removeControlPanel()
                createFloatingBubble()
            }
        }
        buttonsRow.addView(settingsBtn)
        rootLayout.addView(buttonsRow)

        val logHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 12, 0, 6)
        }
        logHeaderRow.addView(
            TextView(this).apply {
                text = "Логи:"
                setTextColor(Color.LTGRAY)
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        logHeaderRow.addView(
            Button(this).apply {
                text = "КОПИЯ"
                textSize = 10f
                setBackgroundColor(Color.parseColor("#144D31"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    val ok = BotLogs.copyToClipboard(this@OverlayControlService)
                    Toast.makeText(
                        this@OverlayControlService,
                        if (ok) "Логи скопированы" else "Ошибка копирования",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
        logHeaderRow.addView(
            Button(this).apply {
                text = "ЭКСПОРТ"
                textSize = 10f
                setBackgroundColor(Color.parseColor("#21332B"))
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { leftMargin = 8 }
                setOnClickListener {
                    val content = BotLogs.getAllLogsText().ifBlank { "Логи пока пусты" }
                    BotLogs.shareText(this@OverlayControlService, "Логи Earth Puzzles", content)
                }
            }
        )
        rootLayout.addView(logHeaderRow)

        val logScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(Color.parseColor("#0A110E"))
            setPadding(10, 10, 10, 10)
        }
        val logContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        logScroll.addView(logContainer)
        rootLayout.addView(logScroll)

        panel.addView(rootLayout)
        controlPanel = panel
        windowManager.addView(panel, params)

        logsJob = serviceScope.launch {
            BotLogs.logs.collect { list ->
                logContainer.removeAllViews()
                list.takeLast(40).forEach { line ->
                    logContainer.addView(
                        TextView(this@OverlayControlService).apply {
                            text = line
                            setTextColor(Color.parseColor("#28C76F"))
                            textSize = 10f
                            typeface = android.graphics.Typeface.MONOSPACE
                        }
                    )
                }
                logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
            }
        }

        updatePanelUi()
    }

    private fun removeControlPanel() {
        controlPanel?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
            controlPanel = null
        }
        logsJob?.cancel()
        logsJob = null
    }

    private fun updatePanelUi() {
        val panel = controlPanel ?: return
        val layout = panel.getChildAt(0) as? LinearLayout ?: return
        val configText = layout.getChildAt(1) as? TextView ?: return
        val buttonsRow = layout.getChildAt(2) as? LinearLayout ?: return
        val toggleBtn = buttonsRow.getChildAt(0) as? Button ?: return

        val cfg = currentConfig ?: return
        val target = MarketTab.fromString(cfg.buyTarget)
        val switchLabel = if (cfg.switchBetweenTabs) "Puzzles ↔ Lands" else target.tabLabel
        val buyLabel = if (cfg.enableActualBuying) "Покупка: ВКЛ" else "Только лог"
        configText.text = "Цель: ТОЛЬКО ${target.tabLabel} ($buyLabel)\nВкладки: $switchLabel | Порог: ${cfg.priceThreshold}"

        if (cfg.autoBuyEnabled) {
            toggleBtn.text = "ПАУЗА"
            toggleBtn.setBackgroundColor(Color.parseColor("#C62828"))
        } else {
            toggleBtn.text = "СТАРТ"
            toggleBtn.setBackgroundColor(Color.parseColor("#28C76F"))
        }
    }

    private fun toggleAutoBuy() {
        val cfg = currentConfig ?: AppConfiguration()
        val service = LootBuyerAccessibilityService.instance
        if (service == null) {
            BotLogs.addLog("⚠️ Включите Accessibility Service 'Earth Puzzles' в настройках Android!")
            Toast.makeText(
                this,
                "Сначала включите 'Earth Puzzles' в Специальных возможностях (Accessibility)!",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val nextState = !cfg.autoBuyEnabled
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.configurationDao().saveConfiguration(cfg.copy(autoBuyEnabled = nextState))
            withContext(Dispatchers.Main) {
                if (nextState) {
                    service.startAutomation()
                    if (controlPanel != null) {
                        removeControlPanel()
                        createFloatingBubble()
                    }
                } else {
                    service.stopAutomation()
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun startCalibrationFlow() {
        if (calibrationOverlay != null || !Settings.canDrawOverlays(this)) return
        calibrationStep = 1
        lastTapTime = 0L

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#33000000"))
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#111B17"))
                cornerRadius = 16 * resources.displayMetrics.density
                setStroke(2, Color.parseColor("#F5C443"))
            }
            setPadding(24, 24, 24, 24)
        }

        val stepText = TextView(this).apply {
            text = "Шаг 1 из 3: Нажмите на вкладку 'Puzzles' в игре"
            setTextColor(Color.parseColor("#F5C443"))
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        box.addView(stepText)

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val skipBtn = Button(this).apply {
            text = "Автопоиск (пропустить)"
            setBackgroundColor(Color.parseColor("#144D31"))
            setTextColor(Color.WHITE)
            visibility = View.GONE
            setOnClickListener {
                saveCalibrationCoords(confirmX = -1f, confirmY = -1f)
            }
        }
        actionsRow.addView(
            skipBtn,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = 16
            }
        )

        val cancelBtn = Button(this).apply {
            text = "Отмена"
            setBackgroundColor(Color.parseColor("#C62828"))
            setTextColor(Color.WHITE)
            setOnClickListener { disableCalibrationInDb() }
        }
        actionsRow.addView(cancelBtn)

        box.addView(
            actionsRow,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 16
            }
        )

        overlay.addView(
            box,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP
                topMargin = 110
                leftMargin = 48
                rightMargin = 48
            }
        )

        overlay.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                handleCalibrationTap(event.rawX, event.rawY, stepText, skipBtn)
            }
            true
        }

        windowManager.addView(overlay, params)
        calibrationOverlay = overlay
    }

    private fun handleCalibrationTap(x: Float, y: Float, stepView: TextView, skipBtn: Button) {
        val now = System.currentTimeMillis()
        if (now - lastTapTime < 450) return
        lastTapTime = now

        LootBuyerAccessibilityService.instance?.clickAt(x, y)

        when (calibrationStep) {
            1 -> {
                tempPuzzlesX = x
                tempPuzzlesY = y
                calibrationStep = 2
                stepView.text = "Шаг 2 из 3: Нажмите на вкладку 'Lands' в игре"
                BotLogs.addLog("📍 Сохранена вкладка Puzzles: (${x.toInt()}, ${y.toInt()})")
            }
            2 -> {
                tempLandsX = x
                tempLandsY = y
                calibrationStep = 3
                stepView.text = "Шаг 3 (опционально): Откройте любой лот и нажмите кнопку 'Buy/Confirm' — либо нажмите 'Автопоиск'"
                skipBtn.visibility = View.VISIBLE
                BotLogs.addLog("📍 Сохранена вкладка Lands: (${x.toInt()}, ${y.toInt()})")
            }
            else -> {
                BotLogs.addLog("📍 Сохранена кнопка подтверждения Buy/Confirm: (${x.toInt()}, ${y.toInt()}). Калибровка завершена!")
                saveCalibrationCoords(confirmX = x, confirmY = y)
            }
        }
    }

    private fun saveCalibrationCoords(confirmX: Float, confirmY: Float) {
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val current = db.configurationDao().getConfiguration() ?: AppConfiguration()
            db.configurationDao().saveConfiguration(
                current.copy(
                    isCalibrationEnabled = false,
                    calibratedPuzzlesX = tempPuzzlesX,
                    calibratedPuzzlesY = tempPuzzlesY,
                    calibratedLandsX = tempLandsX,
                    calibratedLandsY = tempLandsY,
                    calibratedConfirmX = confirmX,
                    calibratedConfirmY = confirmY
                )
            )
            withContext(Dispatchers.Main) {
                stopCalibrationFlow()
                val msg = if (confirmX != -1f) {
                    "Координаты Puzzles, Lands и Buy сохранены!"
                } else {
                    "Координаты Puzzles и Lands сохранены (кнопка Buy — автопоиск)!"
                }
                BotLogs.addLog("✅ $msg")
                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun disableCalibrationInDb() {
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val current = db.configurationDao().getConfiguration() ?: AppConfiguration()
            db.configurationDao().saveConfiguration(current.copy(isCalibrationEnabled = false))
            withContext(Dispatchers.Main) { stopCalibrationFlow() }
        }
    }

    private fun stopCalibrationFlow() {
        calibrationOverlay?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
            calibrationOverlay = null
        }
    }

    override fun onDestroy() {
        instance = null
        stopCalibrationFlow()
        removeControlPanel()
        removeFloatingBubble()
        serviceScope.cancel()
        super.onDestroy()
    }
}
