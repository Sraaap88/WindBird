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

class SkiJumpActivity : Activity(), SensorEventListener {

    private lateinit var gameView: SkiJumpView
    private lateinit var statusText: TextView

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null

    // Variables de jeu
    private var gameState = GameState.APPROACH
    private var speed = 0f
    private var maxSpeed = 100f
    private var jumpDistance = 0f
    private var jumpHeight = 0f
    private var takeoffTiming = 0f
    
    // Variables de vol
    private var pitch = 0f
    private var roll = 0f
    private var yaw = 0f
    
    // Variables de temps
    private var approachTime = 0f
    private var flightTime = 0f
    private var totalFlightTime = 3.0f
    
    // Variables d'animation
    private var skierX = 0f
    private var skierY = 0f
    
    // Données du tournoi
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
        currentPlayerIndex = intent.getIntExtra("current_player_index", 0)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        statusText = TextView(this).apply {
            text = "Joueur: ${tournamentData.playerNames[currentPlayerIndex]} | Phase: Élan"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(20, 10, 20, 10)
        }

        gameView = SkiJumpView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        
        initializeGame()
    }
    
    private fun initializeGame() {
        speed = 0f
        jumpDistance = 0f
        jumpHeight = 0f
        pitch = 0f
        roll = 0f
        yaw = 0f
        approachTime = 0f
        flightTime = 0f
        takeoffTiming = 0f
        gameState = GameState.APPROACH
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

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        when (gameState) {
            GameState.APPROACH -> handleApproach(x, y, z)
            GameState.TAKEOFF -> handleTakeoff(x, y, z)
            GameState.FLIGHT -> handleFlight(x, y, z)
            GameState.LANDING -> handleLanding(x, y, z)
            GameState.FINISHED -> {}
        }

        updateStatus()
        gameView.invalidate()
    }
    
    private fun handleApproach(x: Float, y: Float, z: Float) {
        approachTime += 0.05f
        
        if (y < -0.5f) {
            speed += 2f
        }
        
        if (abs(x) > 0.3f) {
            speed -= 0.5f
        }
        
        speed = speed.coerceIn(0f, maxSpeed)
        
        if (approachTime >= 4f || speed >= 80f) {
            gameState = GameState.TAKEOFF
            takeoffTiming = 0f
        }
    }
    
    private fun handleTakeoff(x: Float, y: Float, z: Float) {
        takeoffTiming += 0.05f
        
        if (y > 1.0f && takeoffTiming < 1.5f) {
            jumpHeight = speed * 0.3f
            jumpDistance = speed * 1.2f
            gameState = GameState.FLIGHT
            flightTime = 0f
        } else if (takeoffTiming >= 1.5f) {
            jumpHeight = speed * 0.2f
            jumpDistance = speed * 1.0f
            gameState = GameState.FLIGHT
            flightTime = 0f
        }
    }
    
    private fun handleFlight(x: Float, y: Float, z: Float) {
        flightTime += 0.05f
        
        pitch += y * 0.03f
        roll += x * 0.03f
        yaw += z * 0.03f
        
        pitch = pitch.coerceIn(-30f, 30f)
        roll = roll.coerceIn(-20f, 20f)
        yaw = yaw.coerceIn(-15f, 15f)
        
        val stability = 1f - (abs(pitch) + abs(roll) + abs(yaw)) / 65f
        jumpDistance += stability * 0.5f
        
        if (flightTime >= totalFlightTime) {
            gameState = GameState.LANDING
        }
    }
    
    private fun handleLanding(x: Float, y: Float, z: Float) {
        if (y < -0.8f) {
            jumpDistance += 10f
        }
        
        if (abs(x) < 0.3f && abs(z) < 0.3f) {
            jumpDistance += 5f
        }
        
        gameState = GameState.FINISHED
        
        if (!practiceMode) {
            tournamentData.addScore(currentPlayerIndex, eventIndex, calculateScore())
        }
        
        statusText.postDelayed({
            proceedToNextPlayerOrEvent()
        }, 3000)
    }
    
    private fun calculateScore(): Int {
        val speedBonus = (speed / maxSpeed * 100).toInt()
        val distanceBonus = (jumpDistance * 2).toInt()
        val stabilityBonus = 50
        
        return maxOf(50, speedBonus + distanceBonus + stabilityBonus)
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
                val aiScore = 100 + kotlin.random.Random.nextInt(151)
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    
    private fun updateStatus() {
        statusText.text = when (gameState) {
            GameState.APPROACH -> "🎿 ${tournamentData.playerNames[currentPlayerIndex]} | Élan: ${speed.toInt()} km/h"
            GameState.TAKEOFF -> "🚀 ${tournamentData.playerNames[currentPlayerIndex]} | Décollage!"
            GameState.FLIGHT -> "✈️ ${tournamentData.playerNames[currentPlayerIndex]} | Vol: ${jumpDistance.toInt()}m"
            GameState.LANDING -> "🎯 ${tournamentData.playerNames[currentPlayerIndex]} | Atterrissage"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Distance: ${jumpDistance.toInt()}m | Score: ${calculateScore()}"
        }
    }

    inner class SkiJumpView(context: Context) : View(context) {
        private val paint = Paint()

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            
            canvas.drawColor(Color.parseColor("#87CEEB"))
            
            when (gameState) {
                GameState.APPROACH -> drawApproach(canvas, w, h)
                GameState.TAKEOFF -> drawTakeoff(canvas, w, h)
                GameState.FLIGHT -> drawFlight(canvas, w, h)
                GameState.LANDING -> drawLanding(canvas, w, h)
                GameState.FINISHED -> drawFinished(canvas, w, h)
            }
        }
        
        private fun drawApproach(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.LTGRAY
            canvas.drawRect(0f, h * 0.8f, w.toFloat(), h.toFloat(), paint)
            
            paint.color = Color.WHITE
            val path = Path()
            path.moveTo(0f, h * 0.8f)
            path.lineTo(w.toFloat(), h * 0.6f)
            path.lineTo(w.toFloat(), h.toFloat())
            path.lineTo(0f, h.toFloat())
            path.close()
            canvas.drawPath(path, paint)
            
            skierX = w * 0.3f + (speed / maxSpeed) * w * 0.4f
            skierY = h * 0.8f - ((speed / maxSpeed) * h * 0.2f)
            
            paint.color = Color.BLUE
            canvas.drawCircle(skierX, skierY - 20f, 15f, paint)
            
            paint.color = Color.BLACK
            canvas.drawRect(50f, 50f, 250f, 80f, paint)
            paint.color = if (speed >= 80f) Color.GREEN else Color.YELLOW
            canvas.drawRect(50f, 50f, 50f + (speed / maxSpeed) * 200f, 80f, paint)
        }
        
        private fun drawTakeoff(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.LTGRAY
            canvas.drawRect(w * 0.6f, h * 0.6f, w.toFloat(), h.toFloat(), paint)
            
            paint.color = Color.BLUE
            canvas.drawCircle(w * 0.7f, h * 0.6f - 30f, 15f, paint)
            
            paint.color = Color.YELLOW
            paint.textSize = 24f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("REDRESSEZ MAINTENANT!", w/2f, h * 0.3f, paint)
        }
        
        private fun drawFlight(canvas: Canvas, w: Int, h: Int) {
            val flightProgress = flightTime / totalFlightTime
            skierX = w * 0.2f + flightProgress * w * 0.6f
            skierY = h * 0.3f + (sin(flightProgress * PI) * jumpHeight).toFloat()
            
            paint.color = Color.BLUE
            canvas.drawCircle(skierX, skierY, 15f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Tangage: ${pitch.toInt()}°", 30f, h - 120f, paint)
            canvas.drawText("Roulis: ${roll.toInt()}°", 30f, h - 100f, paint)
            canvas.drawText("Lacet: ${yaw.toInt()}°", 30f, h - 80f, paint)
        }
        
        private fun drawLanding(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.WHITE
            canvas.drawRect(0f, h * 0.7f, w.toFloat(), h.toFloat(), paint)
            
            paint.color = Color.BLUE
            canvas.drawCircle(w * 0.8f, h * 0.7f - 20f, 15f, paint)
            
            paint.color = Color.GREEN
            paint.textSize = 32f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${jumpDistance.toInt()}m", w/2f, h * 0.5f, paint)
        }
        
        private fun drawFinished(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.2f, paint)
            
            paint.color = Color.BLACK
            paint.textSize = 24f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🏆 RÉSULTAT FINAL 🏆", w/2f, h * 0.1f, paint)
            
            paint.textSize = 20f
            canvas.drawText("Distance: ${jumpDistance.toInt()}m", w/2f, h * 0.3f, paint)
            canvas.drawText("Score: ${calculateScore()} points", w/2f, h * 0.4f, paint)
        }
    }

    enum class GameState {
        APPROACH, TAKEOFF, FLIGHT, LANDING, FINISHED
    }
}
