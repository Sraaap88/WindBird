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
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.view.ViewGroup
import kotlin.math.*

class BobsledActivity : Activity(), SensorEventListener {

    private lateinit var gameView: BobsledView
    private lateinit var statusText: TextView

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null

    private var playerOffset = 0f
    private var distance = 0f
    private val totalDistance = 2000f
    private var previousGyroDirection = 0
    private var backgroundOffset = 0f

    private var gameState = GameState.RACING
    private var speed = 0f

    private lateinit var tournamentData: TournamentData
    private var eventIndex: Int = 0
    private var numberOfPlayers: Int = 1
    private var currentPlayerIndex: Int = 0
    private var practiceMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        tournamentData = intent.getSerializableExtra("tournament_data") as TournamentData
        eventIndex = intent.getIntExtra("event_index", 0)
        numberOfPlayers = intent.getIntExtra("number_of_players", 1)
        practiceMode = intent.getBooleanExtra("practice_mode", false)
        
        // CORRECTION : Gestion du mode pratique
        currentPlayerIndex = if (practiceMode) {
            0 // En mode pratique, toujours joueur 0
        } else {
            intent.getIntExtra("current_player_index", tournamentData.getNextPlayer(eventIndex))
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        statusText = TextView(this).apply {
            text = "Joueur: ${tournamentData.playerNames[currentPlayerIndex]} | Distance: 0m"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(20, 10, 20, 10)
        }

        gameView = BobsledView(this)

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
        val z = event.values[2]

        if (gameState == GameState.RACING) {
            playerOffset += x * 0.1f
            playerOffset = playerOffset.coerceIn(-1f, 1f)

            val rotationDirection = when {
                z > 1.0f -> 1
                z < -1.0f -> -1
                else -> 0
            }
            if (rotationDirection != 0 && rotationDirection != previousGyroDirection) {
                distance += 30f
                speed += 8f
                backgroundOffset -= 15f
                previousGyroDirection = rotationDirection
            }
            
            if (distance >= totalDistance) {
                gameState = GameState.FINISHED
                
                if (!practiceMode) {
                    tournamentData.addScore(currentPlayerIndex, eventIndex, calculateScore())
                }
                
                statusText.postDelayed({
                    proceedToNextPlayerOrEvent()
                }, 2000)
            }
        }

        updateStatus()
        gameView.invalidate()
    }

    private fun proceedToNextPlayerOrEvent() {
        if (practiceMode) {
            val intent = Intent(this, EventsMenuActivity::class.java).apply {
                putExtra("practice_mode", true)
                putExtra("tournament_data", tournamentData)
                putStringArrayListExtra("player_names", tournamentData.playerNames)
                putStringArrayListExtra("player_countries", tournamentData.playerCountries)
                putExtra("number_of_players", numberOfPlayers)
            }
            startActivity(intent)
            finish()
            return
        }
        
        val nextPlayer = tournamentData.getNextPlayer(eventIndex)
        
        if (nextPlayer != -1) {
            if (nextPlayer < numberOfPlayers) {
                val intent = Intent(this, PlayerTransitionActivity::class.java).apply {
                    putExtra("tournament_data", tournamentData)
                    putExtra("event_index", eventIndex)
                    putExtra("number_of_players", numberOfPlayers)
                    putExtra("next_player_index", nextPlayer)
                }
                startActivity(intent)
                finish()
            } else {
                val aiScore = generateAIScore()
                tournamentData.addScore(nextPlayer, eventIndex, aiScore)
                proceedToNextPlayerOrEvent()
            }
        } else {
            if (tournamentData.isTournamentComplete()) {
                val resultIntent = Intent(this, ScoreboardActivity::class.java).apply {
                    putExtra("tournament_data", tournamentData)
                    putExtra("tournament_final", true)
                }
                startActivity(resultIntent)
                finish()
            } else {
                val resultIntent = Intent(this, ScoreboardActivity::class.java).apply {
                    putExtra("tournament_data", tournamentData)
                    putExtra("event_completed", eventIndex)
                }
                startActivity(resultIntent)
                finish()
            }
        }
    }
    
    private fun generateAIScore(): Int {
        val aiSpeed = (60..120).random()
        val aiDistance = (1500..2200).random()
        val speedBonus = aiSpeed
        val distanceBonus = (aiDistance / totalDistance * 100).toInt()
        return maxOf(50, speedBonus + distanceBonus)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateStatus() {
        statusText.text = when (gameState) {
            GameState.RACING -> "🛷 ${tournamentData.playerNames[currentPlayerIndex]} | Distance: ${distance.toInt()}m / ${totalDistance.toInt()}m | Vitesse: ${speed.toInt()}"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Score final: ${calculateScore()} points"
        }
    }

    private fun calculateScore(): Int {
        val speedBonus = speed.toInt()
        val distanceBonus = (distance / totalDistance * 100).toInt()
        return maxOf(0, speedBonus + distanceBonus)
    }

    inner class BobsledView(context: Context) : View(context) {
        private val paint = Paint()
        private val bgPaint = Paint().apply { color = Color.parseColor("#334455") }
        private val snowPaint = Paint().apply { color = Color.WHITE }
        private val trackPaint = Paint().apply { color = Color.parseColor("#E0F6FF") }

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
            
            for (i in 0..10) {
                val lineX = (backgroundOffset + i * 100) % (w + 200)
                snowPaint.alpha = 50
                canvas.drawRect(lineX, 0f, lineX + 2, h.toFloat(), snowPaint)
            }
            
            paint.color = Color.parseColor("#AAAAAA")
            canvas.drawRect(w * 0.1f, 0f, w * 0.1f + 40f, h.toFloat(), paint)
            canvas.drawRect(w * 0.9f - 40f, 0f, w * 0.9f, h.toFloat(), paint)
            
            canvas.drawRect(w * 0.1f + 40f, 0f, w * 0.9f - 40f, h.toFloat(), trackPaint)

            val progressRatio = distance / totalDistance
            val bobX = (w * 0.1f + 60f) + (progressRatio * w * 0.5f) + playerOffset * 100f
            val bobY = h * 0.8f
            
            paint.color = Color.parseColor("#FF4444")
            canvas.drawRect(bobX - 25f, bobY - 15f, bobX + 25f, bobY + 15f, paint)

            if (gameState == GameState.FINISHED) {
                paint.color = Color.parseColor("#FFD700")
                canvas.drawRect(0f, 0f, w.toFloat(), h * 0.2f, paint)
                
                paint.color = Color.BLACK
                paint.textSize = 24f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("🏁 ARRIVÉE ! 🏁", w/2f, h * 0.1f, paint)
                
                paint.textSize = 20f
                canvas.drawText("Score: ${calculateScore()} points", w/2f, h * 0.4f, paint)
            }
            
            paint.color = Color.BLACK
            canvas.drawRect(w * 0.1f, 20f, w * 0.9f, 40f, paint)
            paint.color = Color.YELLOW
            canvas.drawRect(w * 0.1f, 20f, w * 0.1f + (progressRatio * w * 0.8f), 40f, paint)
        }
    }

    enum class GameState {
        RACING, FINISHED
    }
}
