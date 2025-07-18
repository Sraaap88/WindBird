// BiathlonActivity.kt — version complète avec enchaînement des joueurs et des épreuves
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
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.view.ViewGroup
import kotlin.math.*

class BiathlonActivity : Activity(), SensorEventListener {

    private lateinit var gameView: BiathlonView
    private lateinit var statusText: TextView

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null

    private var playerOffset = 0f
    private var distance = 0f
    private val totalDistance = 5000f
    private var previousGyroDirection = 0

    private lateinit var skierBitmap: Bitmap

    private var gameState = GameState.SKIING
    private var targetsHit = 0
    private var shotsFired = 0
    private var crosshair = PointF(0.5f, 0.4f)
    private val targetPositions = List(5) { PointF(0.2f + it * 0.15f, 0.4f) }
    private val targetHitStatus = BooleanArray(5) { false }

    private lateinit var tournamentData: TournamentData
    private var eventIndex: Int = 0
    private var numberOfPlayers: Int = 1
    private var currentPlayerIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        tournamentData = intent.getSerializableExtra("tournament_data") as TournamentData
        eventIndex = intent.getIntExtra("event_index", 0)
        numberOfPlayers = intent.getIntExtra("number_of_players", 1)
        currentPlayerIndex = tournamentData.getNextPlayer(eventIndex)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        skierBitmap = BitmapFactory.decodeResource(resources, R.drawable.skieur_pixel)

        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        statusText = TextView(this).apply {
            text = "Distance: 0m"
            setTextColor(Color.WHITE)
        }

        gameView = BiathlonView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        gyroscope?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GYROSCOPE) return

        val y = event.values[1]
        val x = event.values[0]

        if (gameState == GameState.SKIING) {
            playerOffset += y * 0.1f
            playerOffset = playerOffset.coerceIn(-1f, 1f)

            val direction = when {
                y > 0.5f -> 1
                y < -0.5f -> -1
                else -> 0
            }
            if (direction != 0 && direction != previousGyroDirection) {
                distance += 25f
                previousGyroDirection = direction
            }
            if (distance >= totalDistance * 0.5f) {
                gameState = GameState.SHOOTING
            }
        }

        if (gameState == GameState.SHOOTING) {
            crosshair.x += y * 0.01f
            crosshair.y += x * 0.01f
            crosshair.x = crosshair.x.coerceIn(0.1f, 0.9f)
            crosshair.y = crosshair.y.coerceIn(0.2f, 0.6f)

            if (shotsFired < 5) {
                shotsFired++
                for (i in targetPositions.indices) {
                    val dx = crosshair.x - targetPositions[i].x
                    val dy = crosshair.y - targetPositions[i].y
                    if (!targetHitStatus[i] && sqrt(dx * dx + dy * dy) < 0.05f) {
                        targetHitStatus[i] = true
                        targetsHit++
                        break
                    }
                }
            }
            if (shotsFired >= 5) {
                gameState = GameState.FINISHED
                tournamentData.addScore(currentPlayerIndex, eventIndex, calculateScore())
                proceedToNextPlayerOrEvent()
            }
        }

        updateStatus()
        gameView.invalidate()
    }

    private fun proceedToNextPlayerOrEvent() {
        val nextPlayer = tournamentData.getNextPlayer(eventIndex)
        if (nextPlayer != -1) {
            val intent = Intent(this, BiathlonActivity::class.java).apply {
                putExtra("tournament_data", tournamentData)
                putExtra("event_index", eventIndex)
                putExtra("number_of_players", numberOfPlayers)
            }
            startActivity(intent)
            finish()
        } else {
            val resultIntent = Intent().apply {
                putExtra("tournament_data", tournamentData)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateStatus() {
        statusText.text = when (gameState) {
            GameState.SKIING -> "Distance: ${distance.toInt()} m"
            GameState.SHOOTING -> "🎯 Tir ${shotsFired}/5 — Touchés: $targetsHit"
            GameState.FINISHED -> "✅ Score final: ${calculateScore()}"
        }
    }

    private fun calculateScore(): Int {
        val accuracyBonus = targetsHit * 50
        val distanceBonus = (distance / totalDistance * 100).toInt()
        return accuracyBonus + distanceBonus
    }

    inner class BiathlonView(context: Context) : View(context) {
        private val paint = Paint()
        private val bgPaint = Paint().apply { color = Color.parseColor("#87CEEB") }

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
            paint.color = Color.WHITE
            canvas.drawRect(0f, h * 0.6f, w.toFloat(), h.toFloat(), paint)
            paint.color = Color.LTGRAY
            canvas.drawRect(0f, h * 0.75f, w.toFloat(), h.toFloat(), paint)

            val skierX = w / 2f + playerOffset * 200f - skierBitmap.width / 2f
            val skierY = h * 0.75f - skierBitmap.height
            canvas.drawBitmap(skierBitmap, skierX, skierY, null)

            if (gameState == GameState.SHOOTING || gameState == GameState.FINISHED) {
                for (i in targetPositions.indices) {
                    val px = targetPositions[i].x * w
                    val py = targetPositions[i].y * h
                    paint.color = if (targetHitStatus[i]) Color.GREEN else Color.WHITE
                    canvas.drawCircle(px, py, 20f, paint)
                    paint.color = Color.BLACK
                    paint.style = Paint.Style.STROKE
                    canvas.drawCircle(px, py, 20f, paint)
                    paint.style = Paint.Style.FILL
                }
                paint.color = Color.RED
                canvas.drawLine(crosshair.x * w - 10, crosshair.y * h, crosshair.x * w + 10, crosshair.y * h, paint)
                canvas.drawLine(crosshair.x * w, crosshair.y * h - 10, crosshair.x * w, crosshair.y * h + 10, paint)
            }
        }
    }

    enum class GameState {
        SKIING, SHOOTING, FINISHED
    }
} 
