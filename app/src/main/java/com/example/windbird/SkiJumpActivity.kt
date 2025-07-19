// SkiJumpActivity.kt — COPIE EXACTE du Biathlon fonctionnel
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

class SkiJumpActivity : Activity(), SensorEventListener {

    private lateinit var gameView: SkiJumpView
    private lateinit var statusText: TextView

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null

    private var playerOffset = 0f
    private var distance = 0f
    private val totalDistance = 3000f
    private var previousGyroDirection = 0
    private var backgroundOffset = 0f

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
    private var practiceMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        tournamentData = intent.getSerializableExtra("tournament_data") as TournamentData
        eventIndex = intent.getIntExtra("event_index", 0)
        numberOfPlayers = intent.getIntExtra("number_of_players", 1)
        practiceMode = intent.getBooleanExtra("practice_mode", false)
        currentPlayerIndex = intent.getIntExtra("current_player_index", tournamentData.getNextPlayer(eventIndex))

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        skierBitmap = BitmapFactory.decodeResource(resources, R.drawable.skieur_pixel)

        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        statusText = TextView(this).apply {
            text = "🎿 SAUT À SKI - Joueur: ${tournamentData.playerNames[currentPlayerIndex]} | Distance: 0m"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(20, 10, 20, 10)
        }

        gameView = SkiJumpView(this)

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

        if (gameState == GameState.SKIING) {
            playerOffset += x * 0.1f
            playerOffset = playerOffset.coerceIn(-1f, 1f)

            val rotationDirection = when {
                z > 1.0f -> 1
                z < -1.0f -> -1
                else -> 0
            }
            if (rotationDirection != 0 && rotationDirection != previousGyroDirection) {
                distance += 25f
                backgroundOffset -= 10f
                previousGyroDirection = rotationDirection
            }
            
            if (distance >= totalDistance * 0.5f) {
                gameState = GameState.SHOOTING
            }
        }

        if (gameState == GameState.SHOOTING) {
            crosshair.x += z * 0.005f
            crosshair.y += x * 0.005f
            crosshair.x = crosshair.x.coerceIn(0.1f, 0.9f)
            crosshair.y = crosshair.y.coerceIn(0.2f, 0.6f)
        }

        if (gameState == GameState.FINAL_SKIING) {
            playerOffset += x * 0.1f
            playerOffset = playerOffset.coerceIn(-1f, 1f)

            val rotationDirection = when {
                z > 1.0f -> 1
                z < -1.0f -> -1
                else -> 0
            }
            if (rotationDirection != 0 && rotationDirection != previousGyroDirection) {
                distance += 25f
                backgroundOffset -= 10f
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
        
        if (gameState == GameState.SHOOTING && shotsFired >= 5) {
            statusText.postDelayed({
                distance = totalDistance * 0.5f
                gameState = GameState.FINAL_SKIING
            }, 1000)
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
        val aiAccuracy = (1..4).random()
        val aiDistance = (4000..5000).random()
        val accuracyBonus = aiAccuracy * 50
        val distanceBonus = (aiDistance / totalDistance * 100).toInt()
        val penalty = (5 - aiAccuracy) * 20
        return maxOf(50, accuracyBonus + distanceBonus - penalty)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && gameState == GameState.SHOOTING && shotsFired < 5) {
            shotsFired++
            
            for (i in targetPositions.indices) {
                val dx = crosshair.x - targetPositions[i].x
                val dy = crosshair.y - targetPositions[i].y
                if (!targetHitStatus[i] && sqrt(dx * dx + dy * dy) < 0.08f) {
                    targetHitStatus[i] = true
                    targetsHit++
                    break
                }
            }
            
            updateStatus()
            gameView.invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun updateStatus() {
        statusText.text = when (gameState) {
            GameState.SKIING -> "🎿 SAUT À SKI - ${tournamentData.playerNames[currentPlayerIndex]} | Distance: ${distance.toInt()}m / ${totalDistance.toInt()}m"
            GameState.SHOOTING -> "🎯 SAUT À SKI - ${tournamentData.playerNames[currentPlayerIndex]} | Tir ${shotsFired}/5 — Touchés: $targetsHit"
            GameState.FINAL_SKIING -> "🎿 SAUT À SKI - ${tournamentData.playerNames[currentPlayerIndex]} | Sprint final: ${distance.toInt()}m / ${totalDistance.toInt()}m"
            GameState.FINISHED -> "✅ SAUT À SKI - ${tournamentData.playerNames[currentPlayerIndex]} | Score final: ${calculateScore()} points"
        }
    }

    private fun calculateScore(): Int {
        val accuracyBonus = targetsHit * 50
        val distanceBonus = (distance / totalDistance * 100).toInt()
        val penaltyForMissedShots = (5 - targetsHit) * 20
        return maxOf(0, accuracyBonus + distanceBonus - penaltyForMissedShots)
    }

    inner class SkiJumpView(context: Context) : View(context) {
        private val paint = Paint()
        private val bgPaint = Paint().apply { color = Color.parseColor("#87CEEB") }
        private val snowPaint = Paint().apply { color = Color.WHITE }
        private val trackPaint = Paint().apply { color = Color.LTGRAY }

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
            
            for (i in 0..10) {
                val lineX = (backgroundOffset + i * 100) % (w + 200)
                snowPaint.alpha = 50
                canvas.drawRect(lineX, 0f, lineX + 2, h.toFloat(), snowPaint)
            }
            
            snowPaint.alpha = 255
            canvas.drawRect(0f, h * 0.6f, w.toFloat(), h.toFloat(), snowPaint)
            
            canvas.drawRect(0f, h * 0.75f, w.toFloat(), h.toFloat(), trackPaint)

            val progressRatio = distance / totalDistance
            val skierX = (w * 0.1f) + (progressRatio * w * 0.6f) + playerOffset * 100f - skierBitmap.width / 2f
            val skierY = h * 0.75f - skierBitmap.height
            canvas.drawBitmap(skierBitmap, skierX, skierY, null)

            if (gameState == GameState.SHOOTING || gameState == GameState.FINISHED) {
                paint.color = Color.parseColor("#1a1a2e")
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                
                paint.color = Color.WHITE
                paint.textSize = 40f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("🎿 ZONE SAUT À SKI 🎿", w/2f, 60f, paint)
                
                paint.textSize = 24f
                canvas.drawText("Pivotez pour viser • TAPEZ l'écran pour tirer", w/2f, 100f, paint)
                
                if (shotsFired >= 5) {
                    paint.color = Color.GREEN
                    paint.textSize = 30f
                    canvas.drawText("TIR TERMINÉ - Passage au ski final...", w/2f, 140f, paint)
                }
                
                for (i in targetPositions.indices) {
                    val px = targetPositions[i].x * w
                    val py = targetPositions[i].y * h + 50
                    
                    if (targetHitStatus[i]) {
                        paint.color = Color.parseColor("#00ff00")
                        canvas.drawCircle(px, py, 35f, paint)
                        paint.color = Color.parseColor("#004400")
                        canvas.drawCircle(px, py, 30f, paint)
                        paint.color = Color.GREEN
                        paint.strokeWidth = 8f
                        paint.style = Paint.Style.STROKE
                        canvas.drawLine(px-15, py-15, px+15, py+15, paint)
                        canvas.drawLine(px+15, py-15, px-15, py+15, paint)
                        paint.style = Paint.Style.FILL
                        
                        paint.color = Color.WHITE
                        paint.textSize = 16f
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText("+50", px, py + 60f, paint)
                    } else {
                        paint.color = Color.WHITE
                        canvas.drawCircle(px, py, 35f, paint)
                        paint.color = Color.BLACK
                        canvas.drawCircle(px, py, 28f, paint)
                        paint.color = Color.WHITE
                        canvas.drawCircle(px, py, 21f, paint)
                        paint.color = Color.BLACK
                        canvas.drawCircle(px, py, 14f, paint)
                        paint.color = Color.RED
                        canvas.drawCircle(px, py, 7f, paint)
                        
                        paint.color = Color.WHITE
                        paint.textSize = 20f
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText("${i+1}", px, py + 60f, paint)
                    }
                }
                
                val crossX = crosshair.x * w
                val crossY = crosshair.y * h + 50
                
                paint.color = Color.BLACK
                paint.strokeWidth = 8f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(crossX - 30, crossY, crossX + 30, crossY, paint)
                canvas.drawLine(crossX, crossY - 30, crossX, crossY + 30, paint)
                canvas.drawCircle(crossX, crossY, 20f, paint)
                
                paint.color = Color.RED
                paint.strokeWidth = 4f
                canvas.drawLine(crossX - 25, crossY, crossX + 25, crossY, paint)
                canvas.drawLine(crossX, crossY - 25, crossX, crossY + 25, paint)
                
                paint.color = Color.YELLOW
                paint.style = Paint.Style.FILL
                canvas.drawCircle(crossX, crossY, 6f, paint)
                paint.color = Color.RED
                canvas.drawCircle(crossX, crossY, 12f, paint)
                paint.style = Paint.Style.FILL
                
                paint.color = Color.WHITE
                paint.textSize = 24f
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText("🔫 Munitions: ${5-shotsFired}/5", 30f, h - 80f, paint)
                canvas.drawText("🎯 Touchés: $targetsHit/5", 30f, h - 50f, paint)
                
                for (i in 0 until 5) {
                    paint.color = if (i < shotsFired) Color.GRAY else Color.YELLOW
                    canvas.drawRect(w - 200f + i * 35f, h - 60f, w - 175f + i * 35f, h - 40f, paint)
                }
            }
            
            paint.color = Color.BLACK
            canvas.drawRect(w * 0.1f, 20f, w * 0.9f, 40f, paint)
            paint.color = Color.GREEN
            canvas.drawRect(w * 0.1f, 20f, w * 0.1f + (progressRatio * w * 0.8f), 40f, paint)
        }
    }

    enum class GameState {
        SKIING, SHOOTING, FINAL_SKIING, FINISHED
    }
}
