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
    private val preparationDuration = 4f
    private val approachDuration = 15f
    private val takeoffDuration = 6f
    private val flightDuration = 12f
    private val landingDuration = 5.5f
    private val resultsDuration = 8f
    
    // Variables de jeu - NOUVEAU SYSTÈME
    private var speed = 0f
    private var maxSpeed = 120f
    private var takeoffPower = 0f
    private var jumpDistance = 0f
    private var stability = 1f
    private var landingBonus = 0f
    
    // NOUVEAU - Variables pour l'approche améliorée avec taps
    private var tapCount = 0
    private var firstTapTime = 0f
    private var tapBonus = 0f
    private var currentTiltAngle = 0f  // Angle actuel du téléphone
    private var targetZoneCenter = 0f  // Centre de la zone verte qui bouge
    private var targetZoneSize = 20f   // Taille de la zone verte (en degrés)
    private var inTargetZone = false
    private var zoneProgress = 0f      // Progression de la zone (0 à 1)
    
    // Variables pour le vent - AMÉLIORÉ
    private var windDirection = 0f
    private var windStrength = 0f
    private var windTimer = 0f
    private var windTransition = 0f
    
    // Variables pour l'atterrissage - AMÉLIORÉ
    private var landingPhase = 0
    private var landingStability = 1f
    
    // Contrôles gyroscope
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
        
        // NOUVEAU - Reset variables d'approche avec taps
        tapCount = 0
        firstTapTime = 0f
        tapBonus = 0f
        currentTiltAngle = 0f
        targetZoneCenter = 5f  // Commence en haut (presque droit)
        zoneProgress = 0f
        inTargetZone = false
        
        landingPhase = 0
        landingStability = 1f
        
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
        
        // Calculer l'angle d'inclinaison (en degrés)
        // tiltY négatif = penché vers l'avant
        currentTiltAngle = (-tiltY * 57.3f).coerceIn(-60f, 60f) // Conversion en degrés

        // Progression du jeu - vitesse constante
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
        val approachProgress = phaseTimer / approachDuration
        
        // Calcul de la progression de la zone cible
        zoneProgress = approachProgress
        
        // La zone verte descend progressivement
        targetZoneCenter = 5f + (zoneProgress * 35f)  // De 5° à 40°
        
        // Vérifier si on est dans la zone cible
        val zoneMin = targetZoneCenter - targetZoneSize / 2f
        val zoneMax = targetZoneCenter + targetZoneSize / 2f
        inTargetZone = currentTiltAngle >= zoneMin && currentTiltAngle <= zoneMax
        
        // Ajustement de vitesse selon la position dans la zone
        if (inTargetZone) {
            // Dans la zone = accélération
            speed += 3f + tapBonus * 0.5f
        } else {
            // Hors zone = décélération
            val distance = minOf(abs(currentTiltAngle - zoneMin), abs(currentTiltAngle - zoneMax))
            val speedLoss = (distance / 10f).coerceAtMost(2f)
            speed -= speedLoss
        }
        
        // Limites de vitesse
        speed = speed.coerceIn(0f, maxSpeed)
        
        // Transition automatique quand temps écoulé
        if (approachProgress >= 1f) {
            gameState = GameState.TAKEOFF
            phaseTimer = 0f
            cameraShake = 0.5f
        }
    }
    
    private fun handleTakeoff() {
        // Phase de takeoff FLUIDE - le skieur descend en continu
        val takeoffProgress = phaseTimer / 6f
        
        // Zone critique: 2 secondes avant la fin (4-6 secondes)
        val criticalZone = takeoffProgress >= 0.67f
        
        if (criticalZone) {
            // Calcul de puissance SEULEMENT dans la zone critique
            val timeInCriticalZone = (takeoffProgress - 0.67f) / 0.33f
            
            // Bonus de timing: maximum au milieu de la zone critique
            val timingBonus = if (timeInCriticalZone <= 0.5f) {
                timeInCriticalZone * 2f
            } else {
                2f - (timeInCriticalZone * 2f)
            }
            
            // Force du mouvement
            val tiltStrength = abs(tiltY.coerceAtMost(0f))
            
            if (tiltY < -0.15f) {
                val powerGain = (tiltStrength * 200f) * (1f + timingBonus)
                takeoffPower += powerGain
            }
        }
        
        takeoffPower = takeoffPower.coerceIn(0f, 120f)
        
        if (phaseTimer >= 6f) {
            jumpDistance = (speed * 1.2f) + (takeoffPower * 0.9f)
            gameState = GameState.FLIGHT
            phaseTimer = 0f
            generateMoreSnowParticles()
            generateWind()
        }
    }
    
    private fun handleFlight() {
        // Gestion du vent - AMÉLIORÉ avec transitions fluides
        windTimer += 0.025f
        if (windTimer > 4f) {
            generateWind()
            windTimer = 0f
        }
        
        // Transition fluide du vent
        windTransition = (windTransition + 0.01f).coerceAtMost(1f)
        
        // Contrôle d'angle de vol AMÉLIORÉ
        val targetFlightAngle = tiltY * 0.5f
        val windCompensation = -windDirection * windStrength * windTransition
        
        // Stabilité basée sur tous les axes + angle de vol optimal
        val optimalAngle = 0.1f
        val angleError = abs(targetFlightAngle - optimalAngle)
        val tiltXError = abs(tiltX - windCompensation)
        val tiltZError = abs(tiltZ)
        
        val currentStability = 1f - (angleError + tiltXError + tiltZError) / 4f
        stability = (stability * 0.95f + currentStability.coerceIn(0f, 1f) * 0.05f)
        
        // Bonus distance pour bon angle de vol
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
                landingPhase = 0 // PRÉPARER
                if (abs(tiltY) < 0.15f && abs(tiltX) < 0.15f) {
                    landingStability += 0.5f
                } else {
                    landingStability -= 0.3f
                }
            }
            
            landingProgress < 0.82f -> {
                landingPhase = 1 // IMPACT
                if (tiltY < -0.1f && tiltY > -0.4f && abs(tiltX) < 0.2f) {
                    landingBonus += 1.2f
                    landingStability += 0.3f
                } else {
                    landingBonus -= 0.4f
                    landingStability -= 0.2f
                }
            }
            
            else -> {
                landingPhase = 2 // STABILISER
                if (abs(tiltX) < 0.1f && abs(tiltY) < 0.1f && abs(tiltZ) < 0.1f) {
                    landingStability += 0.4f
                    landingBonus += 0.5f
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
    
    // Fonction pour gérer les taps sur l'écran
    fun handleScreenTap() {
        if (gameState == GameState.APPROACH && tapCount < 2) {
            val currentTime = phaseTimer
            
            if (tapCount == 0) {
                // Premier tap
                firstTapTime = currentTime
                tapCount++
                speed += 15f // Boost de base
                cameraShake = 0.3f
            } else if (tapCount == 1) {
                // Deuxième tap - calculer le bonus de timing
                val timeBetweenTaps = currentTime - firstTapTime
                
                tapBonus = when {
                    timeBetweenTaps < 0.3f -> 1.5f // Taps très rapides = bonus max
                    timeBetweenTaps < 0.7f -> 1.0f // Taps moyens = bonus normal
                    else -> 0.5f // Taps lents = bonus minimal
                }
                
                tapCount++
                speed += 15f + (tapBonus * 10f) // Boost avec bonus
                cameraShake = 0.5f
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
            val tapBonusScore = (tapBonus * 15).toInt() // Nouveau bonus pour les taps
            
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
                    0 -> "PRÉPARER"
                    1 -> "IMPACT"
                    2 -> "STABILISER"
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
    fun getTakeoffPower() = takeoffPower
    fun getJumpDistance() = jumpDistance
    fun getStability() = stability
    fun getLandingBonus() = landingBonus
    fun getTapCount() = tapCount
    fun getTapBonus() = tapBonus
    fun getCurrentTiltAngle() = currentTiltAngle
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
