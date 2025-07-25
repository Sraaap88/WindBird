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
import android.view.MotionEvent
import android.view.View
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

    // Structure de jeu
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Durées
    private val preparationDuration = 6f
    private val pushStartDuration = 8f
    private val controlDescentDuration = 90f
    private val finishLineDuration = 4f
    private val celebrationDuration = 8f
    private val resultsDuration = 5f
    
    // Variables de jeu principales
    private var speed = 0f
    private var baseSpeed = 50f
    private var maxSpeed = 150f
    private var pushPower = 0f
    private var distance = 0f
    
    // Variables de performance
    private var wallHits = 0
    private var perfectTurns = 0
    private var raceTime = 0f
    private var pushQuality = 0f
    
    // Circuit simple
    private var trackPosition = 0f // Position sur le circuit (0.0 à 1.0)
    private val trackCurves = mutableListOf<Float>() // Séquence de virages
    
    // Contrôles gyroscopiques
    private var tiltZ = 0f
    private var playerReactionAccuracy = 1f
    
    // Système de poussée
    private var pushCount = 0
    private var lastPushTime = 0L
    private var pushRhythm = 0f
    
    // Score
    private var finalScore = 0
    private var scoreCalculated = false
    
    // Effets visuels
    private var cameraShake = 0f

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

        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        statusText = TextView(this).apply {
            text = "🛷 BOBSLEIGH - ${tournamentData.playerNames[currentPlayerIndex]}"
            setTextColor(Color.WHITE)
            textSize = 30f
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(35, 30, 35, 30)
        }

        gameView = BobsledView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        
        initializeGame()
    }
    
    private fun initializeGame() {
        gameState = GameState.PREPARATION
        phaseTimer = 0f
        speed = 0f
        baseSpeed = 50f
        pushPower = 0f
        distance = 0f
        trackPosition = 0f
        wallHits = 0
        perfectTurns = 0
        raceTime = 0f
        pushQuality = 0f
        pushCount = 0
        pushRhythm = 0f
        tiltZ = 0f
        playerReactionAccuracy = 1f
        finalScore = 0
        scoreCalculated = false
        cameraShake = 0f
        lastPushTime = 0L
        
        generateTrackCurves()
    }
    
    private fun generateTrackCurves() {
        trackCurves.clear()
        
        if (practiceMode) {
            kotlin.random.Random(12345).let { fixedRandom ->
                generateRandomTrack(fixedRandom)
            }
        } else {
            kotlin.random.Random(eventIndex.toLong()).let { tournamentRandom ->
                generateRandomTrack(tournamentRandom)
            }
        }
    }
    
    private fun generateRandomTrack(random: kotlin.random.Random) {
        val trackLength = 75
        
        trackCurves.add(0f) // Départ droit
        trackCurves.add(0f)
        trackCurves.add(0f)
        
        var lastCurve = 0f
        
        for (i in 3 until trackLength - 3) {
            val newCurve = when (random.nextInt(10)) {
                0 -> -0.9f + random.nextFloat() * 0.1f // Virage FORT gauche
                1 -> 0.8f + random.nextFloat() * 0.2f  // Virage FORT droite
                2, 3 -> -0.6f + random.nextFloat() * 0.1f // Virage MOYEN gauche
                4, 5 -> 0.5f + random.nextFloat() * 0.2f  // Virage MOYEN droite
                6, 7 -> lastCurve * 0.6f // Transition douce
                else -> 0f               // Ligne droite
            }
            
            val smoothedCurve = if (abs(newCurve - lastCurve) > 0.7f) {
                lastCurve + (newCurve - lastCurve) * 0.6f
            } else {
                newCurve
            }
            
            trackCurves.add(smoothedCurve)
            lastCurve = smoothedCurve
        }
        
        trackCurves.add(0f) // Fin droite
        trackCurves.add(0f)
        trackCurves.add(0f)
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

        tiltZ = event.values[2]

        phaseTimer += 0.025f
        if (gameState != GameState.PREPARATION && gameState != GameState.FINISH_LINE && gameState != GameState.CELEBRATION && gameState != GameState.RESULTS) {
            raceTime += 0.025f
        }

        when (gameState) {
            GameState.PREPARATION -> handlePreparation()
            GameState.PUSH_START -> handlePushStart()
            GameState.CONTROL_DESCENT -> handleControlDescent()
            GameState.FINISH_LINE -> handleFinishLine()
            GameState.CELEBRATION -> handleCelebration()
            GameState.RESULTS -> handleResults()
            GameState.FINISHED -> {}
        }

        updateEffects()
        updateStatus()
        gameView.invalidate()
    }
    
    private fun handlePreparation() {
        if (phaseTimer >= preparationDuration) {
            gameState = GameState.PUSH_START
            phaseTimer = 0f
        }
    }
    
    private fun handlePushStart() {
        pushPower = pushPower.coerceAtMost(150f)
        pushRhythm = pushRhythm.coerceAtMost(150f)
        
        if (phaseTimer >= pushStartDuration) {
            pushQuality = (pushPower * 0.6f + pushRhythm * 0.4f) / 150f
            baseSpeed = 60f + (pushQuality * 90f)
            speed = baseSpeed
            
            gameState = GameState.CONTROL_DESCENT
            phaseTimer = 0f
            cameraShake = 0.5f
        }
    }
    
    private fun handleControlDescent() {
        updateTrackProgress()
        updatePlayerReaction()
        
        val speedMultiplier = 0.7f + (playerReactionAccuracy * 0.6f)
        speed = baseSpeed * speedMultiplier
        speed = speed.coerceAtMost(maxSpeed)
        
        if (trackPosition >= 1f) {
            gameState = GameState.FINISH_LINE
            phaseTimer = 0f
            cameraShake = 0.8f
        }
    }
    
    private fun updateTrackProgress() {
        val progressSpeed = speed / 8000f
        trackPosition += progressSpeed * 0.025f
        trackPosition = trackPosition.coerceAtMost(1f)
    }
    
    private fun updatePlayerReaction() {
        // Réaction basée sur le virage actuel du parcours
        val trackIndex = (trackPosition * (trackCurves.size - 1)).toInt()
        val currentTrackCurve = if (trackIndex < trackCurves.size) trackCurves[trackIndex] else 0f
        
        val idealReaction = when {
            currentTrackCurve < -0.75f -> -1.5f  // Fort gauche
            currentTrackCurve < -0.4f -> -0.8f   // Moyen gauche
            currentTrackCurve > 0.75f -> 1.5f    // Fort droite
            currentTrackCurve > 0.4f -> 0.8f     // Moyen droite
            else -> 0f                           // Droit
        }
        
        val reactionError = abs(tiltZ - idealReaction)
        playerReactionAccuracy = (1f - reactionError / 3f).coerceIn(0.2f, 1f)
        
        val perfectThreshold = when {
            abs(currentTrackCurve) > 0.75f -> 0.4f
            abs(currentTrackCurve) > 0.4f -> 0.3f
            else -> 0.2f
        }
        
        if (reactionError < perfectThreshold && abs(currentTrackCurve) > 0.2f) {
            perfectTurns++
        }
        
        if (reactionError > 1.2f && abs(currentTrackCurve) > 0.2f) {
            wallHits++
            cameraShake = 0.3f
        }
    }
    
    private fun handleFinishLine() {
        if (phaseTimer >= finishLineDuration) {
            gameState = GameState.CELEBRATION
            phaseTimer = 0f
            speed *= 0.9f
        }
    }
    
    private fun handleCelebration() {
        speed = maxOf(20f, speed * 0.98f)
        
        if (phaseTimer >= celebrationDuration) {
            calculateFinalScore()
            gameState = GameState.RESULTS
            phaseTimer = 0f
        }
    }
    
    private fun handleResults() {
        if (phaseTimer >= resultsDuration) {
            gameState = GameState.FINISHED
            
            if (!practiceMode) {
                tournamentData.addScore(currentPlayerIndex, eventIndex, finalScore)
            }
            
            statusText.postDelayed({
                proceedToNextPlayerOrEvent()
            }, 3000)
        }
    }
    
    private fun updateEffects() {
        cameraShake = maxOf(0f, cameraShake - 0.02f)
    }
    
    private fun calculateFinalScore() {
        if (!scoreCalculated) {
            val timeBonus = maxOf(0, 300 - raceTime.toInt())
            val speedBonus = (speed / maxSpeed * 100).toInt()
            val pushBonus = (pushQuality * 80).toInt()
            val reactionBonus = (playerReactionAccuracy * 150).toInt()
            val perfectBonus = perfectTurns * 25
            val wallPenalty = wallHits * 30
            
            finalScore = maxOf(100, timeBonus + speedBonus + pushBonus + reactionBonus + perfectBonus - wallPenalty)
            scoreCalculated = true
        }
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
                val aiScore = (150..250).random()
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
            GameState.PREPARATION -> "🛷 ${tournamentData.playerNames[currentPlayerIndex]} | Préparation... ${(preparationDuration - phaseTimer).toInt() + 1}s"
            GameState.PUSH_START -> "🚀 ${tournamentData.playerNames[currentPlayerIndex]} | Puissance: ${pushPower.toInt()}% | Coups: ${pushCount} | ${(pushStartDuration - phaseTimer).toInt() + 1}s"
            GameState.CONTROL_DESCENT -> "🎮 ${tournamentData.playerNames[currentPlayerIndex]} | Réflexes: ${(playerReactionAccuracy * 100).toInt()}% | ${speed.toInt()} km/h"
            GameState.FINISH_LINE -> "🏁 ${tournamentData.playerNames[currentPlayerIndex]} | Ligne d'arrivée: ${speed.toInt()} km/h!"
            GameState.CELEBRATION -> "🎉 ${tournamentData.playerNames[currentPlayerIndex]} | Temps: ${raceTime.toInt()}s!"
            GameState.RESULTS -> "🏆 ${tournamentData.playerNames[currentPlayerIndex]} | Score: ${finalScore}"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Course terminée!"
        }
    }

    private fun getCountryFlag(country: String): String {
        return when (country.uppercase()) {
            "CANADA" -> "🇨🇦"
            "FRANCE" -> "🇫🇷"
            "USA" -> "🇺🇸"
            "NORVÈGE" -> "🇳🇴"
            "JAPON" -> "🇯🇵"
            else -> "🏴"
        }
    }

    // Fonctions d'accès pour le renderer
    fun getGameData(): GameData {
        return GameData(
            gameState = gameState,
            phaseTimer = phaseTimer,
            speed = speed,
            trackPosition = trackPosition,
            trackCurves = trackCurves,
            tiltZ = tiltZ,
            playerReactionAccuracy = playerReactionAccuracy,
            pushPower = pushPower,
            pushCount = pushCount,
            cameraShake = cameraShake,
            finalScore = finalScore,
            raceTime = raceTime,
            pushQuality = pushQuality,
            perfectTurns = perfectTurns,
            wallHits = wallHits,
            maxSpeed = maxSpeed,
            tournamentData = tournamentData,
            currentPlayerIndex = currentPlayerIndex,
            practiceMode = practiceMode,
            preparationDuration = preparationDuration,
            pushStartDuration = pushStartDuration,
            finishLineDuration = finishLineDuration,
            celebrationDuration = celebrationDuration
        )
    }

    fun updatePushPower(increment: Float) {
        pushPower += increment
        pushCount++
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPushTime > 150 && currentTime - lastPushTime < 600) {
            pushRhythm += 5f
        } else {
            pushRhythm += 2f
        }
        lastPushTime = currentTime
        
        pushPower = pushPower.coerceAtMost(150f)
        pushRhythm = pushRhythm.coerceAtMost(150f)
        
        cameraShake = 0.2f
    }

    inner class BobsledView(context: Context) : View(context) {
        private lateinit var bobsledRenderer: BobsledRenderer
        
        init {
            bobsledRenderer = BobsledRenderer(context, this@BobsledActivity)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            return bobsledRenderer.handleTouch(event, width, height)
        }

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            
            if (cameraShake > 0f) {
                canvas.save()
                canvas.translate(
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 10f,
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 10f
                )
            }
            
            bobsledRenderer.render(canvas, w, h)
            
            if (cameraShake > 0f) {
                canvas.restore()
            }
        }
    }

    enum class GameState {
        PREPARATION, PUSH_START, CONTROL_DESCENT, FINISH_LINE, CELEBRATION, RESULTS, FINISHED
    }
}

// Classe de données pour transférer l'état du jeu
data class GameData(
    val gameState: BobsledActivity.GameState,
    val phaseTimer: Float,
    val speed: Float,
    val trackPosition: Float,
    val trackCurves: List<Float>,
    val tiltZ: Float,
    val playerReactionAccuracy: Float,
    val pushPower: Float,
    val pushCount: Int,
    val cameraShake: Float,
    val finalScore: Int,
    val raceTime: Float,
    val pushQuality: Float,
    val perfectTurns: Int,
    val wallHits: Int,
    val maxSpeed: Float,
    val tournamentData: TournamentData,
    val currentPlayerIndex: Int,
    val practiceMode: Boolean,
    val preparationDuration: Float,
    val pushStartDuration: Float,
    val finishLineDuration: Float,
    val celebrationDuration: Float
)
