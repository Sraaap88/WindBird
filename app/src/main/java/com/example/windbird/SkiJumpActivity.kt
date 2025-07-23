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

    // Variables de gameplay
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Phases avec durées AJUSTÉES
    private val preparationDuration = 4f
    private val approachDuration = 15f  // AUGMENTÉ pour le nouveau système
    private val takeoffDuration = 6f
    private val flightDuration = 12f
    private val landingDuration = 5.5f
    private val resultsDuration = 8f
    
    // Variables de jeu - NOUVEAU SYSTÈME
    private var speed = 0f
    private var maxSpeed = 120f  // AUGMENTÉ - pas de limite artificielle
    private var takeoffPower = 0f
    private var jumpDistance = 0f
    private var stability = 1f
    private var landingBonus = 0f
    
    // NOUVEAU - Variables pour l'approche améliorée
    private var pushCount = 0  // Compteur de poussées
    private var isLeaningForward = false  // Est en train de pencher vers l'avant
    private var maintainAngle = false  // Phase de maintien d'angle
    private var currentLeanAngle = 0f  // Angle actuel
    private var targetLeanAngle = 1.2f  // ~70 degrés en radians
    private var speedDecayTimer = 0f  // Pour perte de vitesse
    
    // Variables pour le vent - AMÉLIORÉ
    private var windDirection = 0f
    private var windStrength = 0f
    private var windTimer = 0f
    private var windTransition = 0f  // Pour transitions fluides
    
    // Variables pour l'atterrissage - AMÉLIORÉ
    private var landingPhase = 0  // 0=préparer, 1=impact, 2=stabiliser
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

        gameView = SkiJumpView(this, this) // Passe l'activité en paramètre

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
        
        // NOUVEAU - Reset variables d'approche
        pushCount = 0
        isLeaningForward = false
        maintainAngle = false
        currentLeanAngle = 0f
        speedDecayTimer = 0f
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
        
        when {
            // Phase 1: 2 poussées pour démarrer (0-3s)
            approachProgress < 0.2f && pushCount < 2 -> {
                if (tiltY < -0.3f) { // Poussée vers soi détectée
                    if (speedDecayTimer <= 0f) { // Éviter double comptage
                        pushCount++
                        speed += 25f // Boost de vitesse par poussée
                        speedDecayTimer = 0.5f // Cooldown
                        cameraShake = 0.3f
                    }
                }
            }
            
            // Phase 2: Pencher progressivement vers l'avant (3-8s)
            approachProgress < 0.53f && pushCount >= 2 -> {
                currentLeanAngle = abs(tiltY.coerceAtLeast(0f)) // Pencher vers l'avant = positif
                
                if (currentLeanAngle >= targetLeanAngle * 0.8f) { // 80% de l'angle cible
                    isLeaningForward = true
                    speed += 2f
                    
                    if (currentLeanAngle >= targetLeanAngle) {
                        maintainAngle = true
                    }
                } else {
                    speed -= 1f // Perte de vitesse si pas assez penché
                    isLeaningForward = false
                }
            }
            
            // Phase 3: Maintenir l'angle (8-15s)
            else -> {
                currentLeanAngle = abs(tiltY.coerceAtLeast(0f))
                
                if (currentLeanAngle >= targetLeanAngle * 0.7f) { // Tolérance
                    speed += 3f // Accélération continue
                } else {
                    speed -= 4f // Perte rapide si on lâche
                    maintainAngle = false
                }
                
                // Transition automatique quand prêt
                if (approachProgress >= 1f && speed >= 60f) {
                    gameState = GameState.TAKEOFF
                    phaseTimer = 0f
                    cameraShake = 0.5f
                }
            }
        }
        
        // Décrémente les timers
        if (speedDecayTimer > 0f) speedDecayTimer -= 0.025f
        
        // Limites de vitesse
        speed = speed.coerceIn(0f, maxSpeed)
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
        if (windTimer > 4f) { // Change moins souvent
            generateWind()
            windTimer = 0f
        }
        
        // Transition fluide du vent
        windTransition = (windTransition + 0.01f).coerceAtMost(1f)
        
        // Contrôle d'angle de vol AMÉLIORÉ
        val targetFlightAngle = tiltY * 0.5f // Contrôle de montée/descente
        val windCompensation = -windDirection * windStrength * windTransition
        
        // Stabilité basée sur tous les axes + angle de vol optimal
        val optimalAngle = 0.1f // Légèrement en descente
        val angleError = abs(targetFlightAngle - optimalAngle)
        val tiltXError = abs(tiltX - windCompensation)
        val tiltZError = abs(tiltZ)
        
        val currentStability = 1f - (angleError + tiltXError + tiltZError) / 4f
        stability = (stability * 0.95f + currentStability.coerceIn(0f, 1f) * 0.05f)
        
        // Bonus distance pour bon angle de vol
        if (angleError < 0.2f && tiltXError < 0.3f) {
            jumpDistance += stability * 0.4f + 0.2f // Bonus pour bon vol
        } else {
            jumpDistance += stability * 0.2f // Distance normale
        }
        
        if (phaseTimer >= flightDuration) {
            gameState = GameState.LANDING
            phaseTimer = 0f
            cameraShake = 1f
            landingPhase = 0 // Reset pour atterrissage
        }
    }
    
    private fun handleLanding() {
        val landingProgress = phaseTimer / landingDuration
        
        // NOUVEAU système d'atterrissage en 3 phases
        when {
            landingProgress < 0.3f -> {
                landingPhase = 0 // PRÉPARER
                // Redresser le téléphone avant l'impact
                if (abs(tiltY) < 0.15f && abs(tiltX) < 0.15f) {
                    landingStability += 0.5f
                } else {
                    landingStability -= 0.3f
                }
            }
            
            landingProgress < 0.82f -> {
                landingPhase = 1 // IMPACT
                // Pencher vers soi au moment de l'impact
                if (tiltY < -0.1f && tiltY > -0.4f && abs(tiltX) < 0.2f) {
                    landingBonus += 1.2f // Bonus augmenté
                    landingStability += 0.3f
                } else {
                    landingBonus -= 0.4f
                    landingStability -= 0.2f
                }
            }
            
            else -> {
                landingPhase = 2 // STABILISER
                // Garder l'équilibre final
                if (abs(tiltX) < 0.1f && abs(tiltY) < 0.1f && abs(tiltZ) < 0.1f) {
                    landingStability += 0.4f
                    landingBonus += 0.5f // Bonus de finition
                } else {
                    landingStability -= 0.3f
                }
            }
        }
        
        landingBonus = landingBonus.coerceIn(0f, 40f) // Augmenté
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
    
    // Calcul de score AMÉLIORÉ
    private fun calculateFinalScore() {
        if (!scoreCalculated) {
            val speedBonus = (speed / maxSpeed * 80).toInt() // Ajusté pour nouvelle vitesse max
            val distanceBonus = (jumpDistance * 1.8f).toInt() // Augmenté
            val stabilityBonus = (stability * 50).toInt() // Augmenté
            val landingBonusScore = (landingBonus * 12).toInt() // Augmenté
            val landingStabilityBonus = (landingStability * 20).toInt() // Nouveau
            
            finalScore = maxOf(60, speedBonus + distanceBonus + stabilityBonus + landingBonusScore + landingStabilityBonus)
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
        windDirection = (kotlin.random.Random.nextFloat() - 0.5f) * 1.5f // Moins extrême
        windStrength = 0.4f + kotlin.random.Random.nextFloat() * 0.5f // Plus prévisible
        windTransition = 0f // Reset transition
        
        // Si changement drastique, transition plus lente
        if (abs(windDirection - oldDirection) > 1f) {
            windTransition = -0.5f // Démarre en négatif pour transition plus longue
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
                    pushCount < 2 -> "⛷️ ${tournamentData.playerNames[currentPlayerIndex]} | Poussées: ${pushCount}/2"
                    !maintainAngle -> "⛷️ ${tournamentData.playerNames[currentPlayerIndex]} | Penchez vers l'avant: ${((currentLeanAngle/targetLeanAngle)*100).toInt()}%"
                    else -> "⛷️ ${tournamentData.playerNames[currentPlayerIndex]} | Maintenez: ${speed.toInt()} km/h"
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
    fun getPushCount() = pushCount
    fun getIsLeaningForward() = isLeaningForward
    fun getMaintainAngle() = maintainAngle
    fun getCurrentLeanAngle() = currentLeanAngle
    fun getTargetLeanAngle() = targetLeanAngle
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
