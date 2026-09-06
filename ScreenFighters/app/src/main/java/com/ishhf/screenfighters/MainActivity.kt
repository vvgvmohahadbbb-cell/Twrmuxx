package com.ishhf.screenfighters

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var spinnerWeapon1: Spinner
    private lateinit var spinnerWeapon2: Spinner
    private lateinit var spinnerTarget: Spinner
    private lateinit var spinnerItem: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val weapons = arrayOf("سيف", "مسدس", "درع")

        spinnerWeapon1 = findViewById(R.id.spinnerWeapon1)
        spinnerWeapon2 = findViewById(R.id.spinnerWeapon2)
        spinnerWeapon1.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, weapons)
        spinnerWeapon2.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, weapons)

        spinnerTarget = findViewById(R.id.spinnerTarget)
        spinnerTarget.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("الشخصية الخضراء", "الشخصية الحمراء")
        )

        spinnerItem = findViewById(R.id.spinnerItem)
        spinnerItem.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("علاج (+30 صحة)", "تعزيز سرعة")
        )

        findViewById<Button>(R.id.btnPermission).setOnClickListener { requestOverlayPermission() }

        findViewById<Button>(R.id.btnSpawn1).setOnClickListener {
            spawn(1, spinnerWeapon1.selectedItem.toString())
        }
        findViewById<Button>(R.id.btnSpawn2).setOnClickListener {
            spawn(2, spinnerWeapon2.selectedItem.toString())
        }

        findViewById<Button>(R.id.btnGiveItem).setOnClickListener {
            val targetId = if (spinnerTarget.selectedItemPosition == 0) 1 else 2
            val item = if (spinnerItem.selectedItemPosition == 0) "HEAL" else "SPEED"
            giveItem(targetId, item)
        }

        findViewById<Button>(R.id.btnStopAll).setOnClickListener { stopAll() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            Toast.makeText(this, "الصلاحية موجودة أصلاً ✅", Toast.LENGTH_SHORT).show()
        }
    }

    private fun spawn(characterId: Int, weapon: String) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "لازم تعطي صلاحية الظهور فوق التطبيقات أولاً", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SPAWN
            putExtra(OverlayService.EXTRA_CHARACTER_ID, characterId)
            putExtra(OverlayService.EXTRA_WEAPON, weapon)
        }
        ContextCompat.startForegroundService(this, intent)
        Toast.makeText(this, "تم إطلاق الشخصية 🤖", Toast.LENGTH_SHORT).show()
    }

    private fun giveItem(characterId: Int, item: String) {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_GIVE_ITEM
            putExtra(OverlayService.EXTRA_CHARACTER_ID, characterId)
            putExtra(OverlayService.EXTRA_ITEM, item)
        }
        startService(intent)
    }

    private fun stopAll() {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP_ALL
        }
        startService(intent)
    }
}
