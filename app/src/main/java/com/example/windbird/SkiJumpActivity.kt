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

class SkiJumpActivity : Activity(), SensorEventListener {

    private lateinit var gameView: SkiJumpView
    private lateinit var statusText: TextView

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null

    // Variables de gameplay
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Phases avec durées
    private val preparationDuration = 6f
    private val approachDuration = 15f
    private val takeoffDuration = 2.2f
    private val flightDuration = 12f
    private val landingDuration = 5.5f
    private val resultsDuration = 8f
    
    // Variables de jeu
    private var speed = 0f
    private var maxSpeed = 120f
    private var takeoffPower = 0f
    private var jumpDistance = 0f
    private var stability = 1f
    private var landingBonus = 0f
    
    // Variables pour la vitesse progressive
    private var maxAchievableSpeed = 60f
    private var performanceScore = 1f
    
    // Variables pour l'approche avec taps
    private var tapCount = 0
    private var firstTapTime = 0f
    private var tapBonus = 0f
    
    // Angle intégré du gyroscope
    private var integratedTiltY = 0f
    private var baselineTiltY = 0f
    private var hasBaseline = false
    
    private var targetZoneCenter = 0f
    private var targetZoneSize = 20f
    private var inTargetZone = false
    private var zoneProgress = 0f
    
    // Variables pour le vent
    private var windDirection = 0f
    private var windStrength = 0f
    private var windTimer = 0f
    private var windTransition = 0f
    
    // Variables pour l'atterrissage
    private var landingPhase = 0
    private var landingStability = 1f
    
    // Pour éviter que l'image rechage
    private var hasUsedJumpImage = false
    
    // Contrôles gyroscope bruts
    private var tiltX = 0f
    private var tiltY = 0f
    private var tiltZ = 0f
    
    // Score et résultats
    private var finalScore = 0
    private var scoreCalculated = false
    
    // Effets visuels
    private var cameraShake = 0f
    private val particles = mutableListOf<SnowParticle>()

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
            text = "🎿 SAUT À SKI - ${tournamentData.playerNames[currentPlayerIndex]}"
            setTextColor(Color.WHITE)
            textSize = 30f
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(35, 30, 35, 30)
        }

        gameView = SkiJumpView(this, this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        
        initializeGame()
    }
    
    private fun initializeGame() {
        gameState = GameState.PREPARATION
        phaseTimer = 0f
        speed = 0f
        takeoffPower = 0f
        jumpDistance = 0f
        stability = 1f
        landingBonus = 0f
        tiltX = 0f
        tiltY = 0f
        tiltZ = 0f
        finalScore = 0
        scoreCalculated = false
        cameraShake = 0f
        windDirection = 0f
        windStrength = 0f
        windTransition = 0f
        
        // Reset variables d'approche
        tapCount = 0
        firstTapTime = 0f
        tapBonus = 0f
        
        // Reset angle intégré
        integratedTiltY = 0f
        baselineTiltY = 0f
        hasBaseline = false
        
        targetZoneCenter = 5f
        zoneProgress = 0f
        inTargetZone = false
        
        landingPhase = 0
        landingStability = 1f
        
        // Reset variables de vitesse progressive
        maxAchievableSpeed = 60f
        performanceScore = 1f
        
        // Reset image de saut
        hasUsedJumpImage = false
        
        particles.clear()
        generateSnowParticles()
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

        tiltX = event.values[0]
        tiltY = event.values[1]
        tiltZ = event.values[2]
        
        // Calcul de l'angle intégré
        if (!hasBaseline && gameState == GameState.APPROACH && tapCount >= 2) {
            integratedTiltY = 0f
            hasBaseline = true
        }
        
        if (hasBaseline) {
            val deltaTime = 0.025f
            val angularVelocity = tiltY
            
            integratedTiltY += angularVelocity * deltaTime * 57.3f
            integratedTiltY *= 0.995f
            integratedTiltY = integratedTiltY.coerceIn(-60f, 60f)
        }

        // Progression du jeu
        phaseTimer += 0.025f

        when (gameState) {
            GameState.PREPARATION -> handlePreparation()
            GameState.APPROACH -> handleApproach()
            GameState.TAKEOFF -> handleTakeoff()
            GameState.FLIGHT -> handleFlight()
            GameState.LANDING -> handleLanding()
            GameState.RESULTS -> handleResults()
            GameState.FINISHED -> {}
        }

        updateParticles()
        updateStatus()
        gameView.invalidate()
    }
    
    private fun handlePreparation() {
        if (phaseTimer >= preparationDuration) {
            gameState = GameState.APPROACH
            phaseTimer = 0f
        }
    }
    
    private fun handleApproach() {
        if (tapCount < 2) {
            speed = 0f
            return
        }
        
        val approachProgress = phaseTimer / approachDuration
        
        zoneProgress = approachProgress
        
        targetZoneCenter = 5f + (zoneProgress * 38.5f)
        
        val zoneMin = targetZoneCenter - targetZoneSize / 2f
        val zoneMax = targetZoneCenter + targetZoneSize / 2f
        inTargetZone = integratedTiltY >= zoneMin && integratedTiltY <= zoneMax
        
        // Système de vitesse progressive basé sur la performance cumulée
        if (inTargetZone) {
            performanceScore = (performanceScore + 0.008f).coerceAtMost(1f)
            maxAchievableSpeed = 60f + (performanceScore * 60f)
            
            if (speed < maxAchievableSpeed) {
                speed += 2.5f + tapBonus * 0.2f
            }
        } else {
            performanceScore = (performanceScore - 0.015f).coerceAtLeast(0.5f)
            maxAchievableSpeed = 60f + (performanceScore * 60f)
            speed -= 1.2f
        }
        
        speed = speed.coerceIn(0f, maxAchievableSpeed)
        
        if (approachProgress >= 1f) {
            gameState = GameState.TAKEOFF
            phaseTimer = 0f
            cameraShake = 0.5f
        }
    }
    
    private fun handleTakeoff() {
        val takeoffProgress = phaseTimer / takeoffDuration
        val criticalZone = takeoffProgress >= 0.85f
        
        if (criticalZone) {
            val timeInCriticalZone = (takeoffProgress - 0.85f) / 0.15f
            
            val timingBonus = if (timeInCriticalZone <= 0.5f) {
                timeInCriticalZone * 2f
            } else {
                2f - (timeInCriticalZone * 2f)
            }
            
            val tiltStrength = abs(tiltY.coerceAtMost(0f))
            
            if (tiltY < -0.15f) {
                val powerGain = (tiltStrength * 200f) * (1f + timingBonus)
                takeoffPower += powerGain
                hasUsedJumpImage = true
            }
        }
        
        takeoffPower = takeoffPower.coerceIn(0f, 120f)
        
        if (phaseTimer >= takeoffDuration) {
            jumpDistance = (speed * 1.2f) + (takeoffPower * 0.9f)
            gameState = GameState.FLIGHT
            phaseTimer = 0f
            generateMoreSnowParticles()
            generateWind()
        }
    }
    
    private fun handleFlight() {
        windTimer += 0.025f
        if (windTimer > 4f) {
            generateWind()
            windTimer = 0f
        }
        
        windTransition = (windTransition + 0.01f).coerceAtMost(1f)
        
        val targetFlightAngle = tiltY * 0.5f
        val windCompensation = -windDirection * windStrength * windTransition
        
        val optimalAngle = 0.1f
        val angleError = abs(targetFlightAngle - optimalAngle)
        val tiltXError = abs(tiltX - windCompensation)
        val tiltZError = abs(tiltZ)
        
        val currentStability = 1f - (angleError + tiltXError + tiltZError) / 4f
        stability = (stability * 0.95f + currentStability.coerceIn(0f, 1f) * 0.05f)
        
        if (angleError < 0.2f && tiltXError < 0.3f) {
            jumpDistance += stability * 0.4f + 0.2f
        } else {
            jumpDistance += stability * 0.2f
        }
        
        if (phaseTimer >= flightDuration) {
            gameState = GameState.LANDING
            phaseTimer = 0f
            cameraShake = 1f
            landingPhase = 0
        }
    }
    
    private fun handleLanding() {
        val landingProgress = phaseTimer / landingDuration
        
        when {
            landingProgress < 0.3f -> {
                landingPhase = 0
                val stability = abs(tiltY) + abs(tiltX) + abs(tiltZ)
                if (stability < 0.3f) {
                    landingStability += 0.5f
                    landingBonus += 0.3f
                } else {
                    landingStability -= 0.3f
                }
            }
            
            landingProgress < 0.82f -> {
                landingPhase = 1
                if (tiltY < -0.2f && tiltY > -0.6f && abs(tiltX) < 0.3f && abs(tiltZ) < 0.3f) {
                    landingBonus += 1.5f
                    landingStability += 0.4f
                } else {
                    landingBonus -= 0.4f
                    landingStability -= 0.2f
                }
            }
            
            else -> {
                landingPhase = 2
                val finalStability = abs(tiltX) + abs(tiltY) + abs(tiltZ)
                if (finalStability < 0.2f) {
                    landingStability += 0.6f
                    landingBonus += 0.8f
                } else {
                    landingStability -= 0.3f
                }
            }
        }
        
        landingBonus = landingBonus.coerceIn(0f, 40f)
        landingStability = landingStability.coerceIn(0f, 2f)
        
        if (phaseTimer >= landingDuration) {
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
    
    fun handleScreenTap() {
        if (gameState == GameState.APPROACH && tapCount < 2) {
            val currentTime = phaseTimer
            
            if (tapCount == 0) {
                firstTapTime = currentTime
                tapCount++
                speed += 15f
                cameraShake = 0.3f
            } else if (tapCount == 1) {
                val timeBetweenTaps = currentTime - firstTapTime
                
                tapBonus = when {
                    timeBetweenTaps < 0.3f -> 1.5f
                    timeBetweenTaps < 0.7f -> 1.0f
                    else -> 0.5f
                }
                
                tapCount++
                speed += 15f + (tapBonus * 10f)
                cameraShake = 0.5f
                
                integratedTiltY = 0f
                hasBaseline = false
            }
        }
    }
    
    private fun calculateFinalScore() {
        if (!scoreCalculated) {
            val speedBonus = (speed / maxSpeed * 80).toInt()
            val distanceBonus = (jumpDistance * 1.8f).toInt()
            val stabilityBonus = (stability * 50).toInt()
            val landingBonusScore = (landingBonus * 12).toInt()
            val landingStabilityBonus = (landingStability * 20).toInt()
            val tapBonusScore = (tapBonus * 15).toInt()
            
            finalScore = maxOf(60, speedBonus + distanceBonus + stabilityBonus + landingBonusScore + landingStabilityBonus + tapBonusScore)
            scoreCalculated = true
        }
    }
    
    private fun generateSnowParticles() {
        repeat(20) {
            particles.add(SnowParticle(
                x = kotlin.random.Random.nextFloat() * 1000f,
                y = kotlin.random.Random.nextFloat() * 800f,
                speed = 1f + kotlin.random.Random.nextFloat() * 2f,
                size = 2f + kotlin.random.Random.nextFloat() * 3f
            ))
        }
    }
    
    private fun generateWind() {
        val oldDirection = windDirection
        windDirection = (kotlin.random.Random.nextFloat() - 0.5f) * 1.5f
        windStrength = 0.4f + kotlin.random.Random.nextFloat() * 0.5f
        windTransition = 0f
        
        if (abs(windDirection - oldDirection) > 1f) {
            windTransition = -0.5f
        }
    }
    
    private fun generateMoreSnowParticles() {
        repeat(30) {
            particles.add(SnowParticle(
                x = kotlin.random.Random.nextFloat() * 1000f,
                y = -20f,
                speed = 3f + kotlin.random.Random.nextFloat() * 5f,
                size = 3f + kotlin.random.Random.nextFloat() * 4f
            ))
        }
    }
    
    private fun updateParticles() {
        particles.removeAll { particle ->
            particle.y += particle.speed
            particle.x += sin(particle.y * 0.01f) * 0.5f
            particle.y > 1000f
        }
        
        if (particles.size < 15) {
            generateSnowParticles()
        }
        
        cameraShake = maxOf(0f, cameraShake - 0.015f)
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
                val aiScore = (80..180).random()
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
            GameState.PREPARATION -> "🎿 ${tournamentData.playerNames[currentPlayerIndex]} | Préparation... ${(preparationDuration - phaseTimer).toInt() + 1}s"
            GameState.APPROACH -> {
                when {
                    tapCount < 2 -> "⛷️ ${tournamentData.playerNames[currentPlayerIndex]} | Tappez l'écran: ${tapCount}/2"
                    inTargetZone -> "⛷️ ${tournamentData.playerNames[currentPlayerIndex]} | PARFAIT! ${speed.toInt()} km/h"
                    else -> "⛷️ ${tournamentData.playerNames[currentPlayerIndex]} | Suivez la zone verte! ${speed.toInt()} km/h"
                }
            }
            GameState.TAKEOFF -> "🚀 ${tournamentData.playerNames[currentPlayerIndex]} | SAUT! Puissance: ${takeoffPower.toInt()}%"
            GameState.FLIGHT -> "✈️ ${tournamentData.playerNames[currentPlayerIndex]} | Vol: ${jumpDistance.toInt()}m | Stabilité: ${(stability * 100).toInt()}%"
            GameState.LANDING -> {
                val phaseText = when (landingPhase) {
                    0 -> "PRÉPARER (stable)"
                    1 -> "IMPACT (vers soi)"
                    2 -> "STABILISER (tout stable)"
                    else -> "ATTERRISSAGE"
                }
                "🎯 ${tournamentData.playerNames[currentPlayerIndex]} | $phaseText | ${jumpDistance.toInt()}m"
            }
            GameState.RESULTS -> "🏆 ${tournamentData.playerNames[currentPlayerIndex]} | Distance: ${jumpDistance.toInt()}m | Score: ${finalScore}"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Saut terminé!"
        }
    }

    // Getters pour la View
    fun getGameState() = gameState
    fun getPhaseTimer() = phaseTimer
    fun getSpeed() = speed
    fun getMaxSpeed() = maxSpeed
    fun getMaxAchievableSpeed() = maxAchievableSpeed
    fun getPerformanceScore() = performanceScore
    fun getTakeoffPower() = takeoffPower
    fun getJumpDistance() = jumpDistance
    fun getStability() = stability
    fun getLandingBonus() = landingBonus
    fun getTapCount() = tapCount
    fun getTapBonus() = tapBonus
    fun getIntegratedTiltY() = integratedTiltY
    fun getTargetZoneCenter() = targetZoneCenter
    fun getTargetZoneSize() = targetZoneSize
    fun getInTargetZone() = inTargetZone
    fun getZoneProgress() = zoneProgress
    fun getWindDirection() = windDirection
    fun getWindStrength() = windStrength
    fun getWindTransition() = windTransition
    fun getLandingPhase() = landingPhase
    fun getLandingStability() = landingStability
    fun getTiltX() = tiltX
    fun getTiltY() = tiltY
    fun getTiltZ() = tiltZ
    fun getFinalScore() = finalScore
    fun getCameraShake() = cameraShake
    fun getParticles() = particles
    fun getTournamentData() = tournamentData
    fun getCurrentPlayerIndex() = currentPlayerIndex
    fun getPracticeMode() = practiceMode
    fun getPreparationDuration() = preparationDuration
    fun getApproachDuration() = approachDuration
    fun getTakeoffDuration() = takeoffDuration
    fun getFlightDuration() = flightDuration
    fun getLandingDuration() = landingDuration
    fun getHasUsedJumpImage() = hasUsedJumpImage

    data class SnowParticle(
        var x: Float,
        var y: Float,
        val speed: Float,
        val size: Float
    )

    enum class GameState {
        PREPARATION, APPROACH, TAKEOFF, FLIGHT, LANDING, RESULTS, FINISHED
    }
}
