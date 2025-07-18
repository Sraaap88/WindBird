// BiathlonActivity.kt (version modifiée avec skieur pixelisé et déplacement manuel)
package com.example.windbird

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import kotlin.math.*

class BiathlonActivity : Activity(), SensorEventListener {

    private lateinit var gameView: BiathlonView
    private lateinit var moveButton: Button
    private lateinit var fireButton: Button
    private lateinit var statusText: TextView

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null

    private var playerOffset = 0f
    private var distance = 0f
    private var speed = 15f // vitesse de base
    private val totalDistance = 5000f

    private lateinit var skierBitmap: Bitmap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        skierBitmap = BitmapFactory.decodeResource(resources, R.drawable.skieur_pixel)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        statusText = TextView(this).apply {
            text = "Distance: 0m"
            setTextColor(Color.WHITE)
        }

        gameView = BiathlonView(this)

        moveButton = Button(this).apply {
            text = "AVANCER"
            setOnClickListener {
                distance += speed
                gameView.invalidate()
                updateStatus()
            }
        }

        fireButton = Button(this).apply {
            text = "TIRER"
        }

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        layout.addView(moveButton)
        layout.addView(fireButton)

        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        gyroscope?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            val y = event.values[1]
            playerOffset += y * 0.1f
            playerOffset = playerOffset.coerceIn(-1f, 1f)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateStatus() {
        statusText.text = "Distance: ${distance.toInt()} m"
    }

    inner class BiathlonView(context: Context) : View(context) {
        private val paint = Paint()
        private val bgPaint = Paint().apply { color = Color.parseColor("#87CEEB") }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val width = canvas.width
            val height = canvas.height

            // ciel
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // sol
            paint.color = Color.WHITE
            canvas.drawRect(0f, height * 0.6f, width.toFloat(), height.toFloat(), paint)

            // piste
            paint.color = Color.LTGRAY
            canvas.drawRect(0f, height * 0.75f, width.toFloat(), height.toFloat(), paint)

            // skieur
            val skierX = width / 2f + playerOffset * 200f - skierBitmap.width / 2f
            val skierY = height * 0.75f - skierBitmap.height
            canvas.drawBitmap(skierBitmap, skierX, skierY, null)
        }
    }
}
