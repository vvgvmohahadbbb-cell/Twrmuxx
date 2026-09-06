package com.ishhf.screenfighters

import android.animation.ObjectAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.hypot
import kotlin.math.roundToInt

class OverlayService : Service() {

    companion object {
        const val ACTION_SPAWN = "com.ishhf.screenfighters.SPAWN"
        const val ACTION_GIVE_ITEM = "com.ishhf.screenfighters.GIVE_ITEM"
        const val ACTION_STOP_ALL = "com.ishhf.screenfighters.STOP_ALL"
        const val EXTRA_CHARACTER_ID = "characterId"
        const val EXTRA_WEAPON = "weapon"
        const val EXTRA_ITEM = "item"

        private const val CHANNEL_ID = "screen_fighters_channel"
        private const val NOTIF_ID = 1
        private const val TICK_MS = 200L
        private const val DEFAULT_ATTACK_RANGE = 130
        private const val CHAR_WIDTH = 150
        private const val CHAR_HEIGHT = 220
    }

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val characters = HashMap<Int, Fighter>()
    private var loopRunning = false
    private var isForeground = false

    inner class Fighter(
        val id: Int,
        val view: View,
        val params: WindowManager.LayoutParams,
        var health: Int = 100,
        val maxHealth: Int = 100,
        var damage: Int = 8,
        var range: Int = DEFAULT_ATTACK_RANGE,
        var speed: Int = 6,
        var defenseMul: Double = 1.0,
        var alive: Boolean = true,
        var beingDragged: Boolean = false,
        var idleAnimator: ObjectAnimator? = null
    ) {
        val healthBar: ProgressBar = view.findViewById(R.id.healthBar)

        fun updateHealthBar() {
            healthBar.progress = ((health.toFloat() / maxHealth) * 100).roundToInt().coerceIn(0, 100)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SPAWN -> {
                startForegroundIfNeeded()
                val id = intent.getIntExtra(EXTRA_CHARACTER_ID, 1)
                val weapon = intent.getStringExtra(EXTRA_WEAPON) ?: "سيف"
                spawnCharacter(id, weapon)
                startLoop()
            }
            ACTION_GIVE_ITEM -> {
                val id = intent.getIntExtra(EXTRA_CHARACTER_ID, 1)
                val item = intent.getStringExtra(EXTRA_ITEM) ?: ""
                applyItem(id, item)
            }
            ACTION_STOP_ALL -> {
                stopEverything()
            }
        }
        return START_NOT_STICKY
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID, "مقاتلو الشاشة", NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(channel)
        }
    }

    private fun startForegroundIfNeeded() {
        if (isForeground) return
        val notification: Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("مقاتلو الشاشة يعملون")
                .setContentText("الشخصيات نشطة فوق التطبيقات")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("مقاتلو الشاشة يعملون")
                .setContentText("الشخصيات نشطة فوق التطبيقات")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build()
        }
        startForeground(NOTIF_ID, notification)
        isForeground = true
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

    private fun spawnCharacter(id: Int, weapon: String) {
        removeCharacter(id)

        val root = FrameLayout(this)
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_character, root, false)

        val label = view.findViewById<TextView>(R.id.charLabel)
        val image = view.findViewById<ImageView>(R.id.charImage)
        if (id == 1) {
            label.text = "الأخضر"
            image.setImageResource(R.drawable.robot_green)
        } else {
            label.text = "الأحمر"
            image.setImageResource(R.drawable.robot_red)
        }

        val params = WindowManager.LayoutParams(
            CHAR_WIDTH, CHAR_HEIGHT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = if (id == 1) 100 else 600
        params.y = 400

        val fighter = Fighter(id = id, view = view, params = params)
        applyWeapon(fighter, weapon)
        fighter.updateHealthBar()

        setupDrag(fighter)

        windowManager.addView(view, params)
        characters[id] = fighter
        startIdleAnimation(fighter)
    }

    private fun startIdleAnimation(fighter: Fighter) {
        val bob = ObjectAnimator.ofFloat(fighter.view, "translationY", 0f, -10f, 0f)
        bob.duration = 700
        bob.repeatCount = ObjectAnimator.INFINITE
        bob.start()
        fighter.idleAnimator = bob
    }

    private fun playAttackPulse(fighter: Fighter) {
        fighter.view.animate()
            .scaleX(1.2f).scaleY(1.2f)
            .setDuration(70)
            .withEndAction {
                fighter.view.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
            }
            .start()
    }

    private fun applyWeapon(fighter: Fighter, weapon: String) {
        when (weapon) {
            "سيف" -> { fighter.damage = 8; fighter.range = 120; fighter.speed = 7 }
            "مسدس" -> { fighter.damage = 14; fighter.range = 260; fighter.speed = 5 }
            "درع" -> { fighter.damage = 4; fighter.range = 100; fighter.speed = 4; fighter.defenseMul = 0.5 }
            else -> { fighter.damage = 8; fighter.range = 120; fighter.speed = 6 }
        }
    }

    private fun applyItem(id: Int, item: String) {
        val f = characters[id] ?: return
        when (item) {
            "HEAL" -> {
                f.health = (f.health + 30).coerceAtMost(f.maxHealth)
                f.updateHealthBar()
                toast("تم العلاج ❤️")
            }
            "SPEED" -> {
                f.speed = (f.speed * 1.5).roundToInt()
                toast("تعزيز السرعة ⚡")
            }
        }
    }

    private fun setupDrag(fighter: Fighter) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        fighter.view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    fighter.beingDragged = true
                    initialX = fighter.params.x
                    initialY = fighter.params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    fighter.params.x = initialX + (event.rawX - touchX).roundToInt()
                    fighter.params.y = initialY + (event.rawY - touchY).roundToInt()
                    safeUpdateLayout(fighter)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    fighter.beingDragged = false
                    true
                }
                else -> false
            }
        }
    }

    private fun startLoop() {
        if (loopRunning) return
        loopRunning = true
        handler.post(gameTick)
    }

    private val gameTick = object : Runnable {
        override fun run() {
            tick()
            if (loopRunning) handler.postDelayed(this, TICK_MS)
        }
    }

    private fun tick() {
        val f1 = characters[1]
        val f2 = characters[2]
        if (f1 == null || f2 == null || !f1.alive || !f2.alive) return

        if (!f1.beingDragged) moveTowards(f1, f2)
        if (!f2.beingDragged) moveTowards(f2, f1)

        val dx = (f1.params.x - f2.params.x).toDouble()
        val dy = (f1.params.y - f2.params.y).toDouble()
        val distance = hypot(dx, dy)

        if (distance <= f1.range) applyDamage(attacker = f1, defender = f2)
        if (f2.alive && distance <= f2.range) applyDamage(attacker = f2, defender = f1)

        safeUpdateLayout(f1)
        safeUpdateLayout(f2)
    }

    private fun moveTowards(mover: Fighter, target: Fighter) {
        val dx = (target.params.x - mover.params.x).toDouble()
        val dy = (target.params.y - mover.params.y).toDouble()
        val dist = hypot(dx, dy)
        if (dist < 5) return
        mover.params.x += (dx / dist * mover.speed).roundToInt()
        mover.params.y += (dy / dist * mover.speed).roundToInt()
    }

    private fun applyDamage(attacker: Fighter, defender: Fighter) {
        val dmg = (attacker.damage * defender.defenseMul).roundToInt().coerceAtLeast(1)
        defender.health -= dmg
        defender.updateHealthBar()
        playAttackPulse(attacker)
        if (defender.health <= 0 && defender.alive) {
            defender.alive = false
            toast("${labelOf(attacker.id)} فاز على ${labelOf(defender.id)} 🏆")
            removeCharacter(defender.id)
        }
    }

    private fun labelOf(id: Int) = if (id == 1) "الأخضر" else "الأحمر"

    private fun safeUpdateLayout(fighter: Fighter) {
        try {
            if (fighter.alive) windowManager.updateViewLayout(fighter.view, fighter.params)
        } catch (e: Exception) {
        }
    }

    private fun removeCharacter(id: Int) {
        val f = characters[id] ?: return
        f.idleAnimator?.cancel()
        try {
            windowManager.removeView(f.view)
        } catch (e: Exception) {
        }
        characters.remove(id)
    }

    private fun toast(msg: String) {
        handler.post { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
    }

    private fun stopEverything() {
        loopRunning = false
        handler.removeCallbacks(gameTick)
        for (id in characters.keys.toList()) {
            removeCharacter(id)
        }
        if (isForeground) {
            stopForeground(true)
            isForeground = false
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopEverything()
    }
}
