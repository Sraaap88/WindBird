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

class SkiFreestyleActivity : Activity(), SensorEventListener {

    private lateinit var gameView: SkiFreestyleView
    private lateinit var statusText: TextView

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null
    private var accelerometer: Sensor? = null

    // États du jeu
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Durées des phases
    private val preparationDuration = 5f
    private val runDuration = 60f
    private val resultsDuration = 8f
    
    // Physique réaliste du skieur
    private var skierX = 0.5f              // Position horizontale (0-1) 
    private var skierY = 0.75f             // Position fixe sur l'écran (skieur reste là)
    private var speed = 0f                 // Vitesse actuelle
    private var baseSpeed = 15f            // Vitesse de base
    private var momentum = 0f              // Momentum de descente
    private var distanceTraveled = 0f      // Distance parcourue
    private var pisteScrollSpeed = 0f      // Vitesse de défilement de la piste
    
    // Système de vol avec physique réaliste
    private var isInAir = false
    private var airTime = 0f
    private var jumpStartSpeed = 0f        // Vitesse au décollage
    private var verticalVelocity = 0f      // Vélocité verticale
    private var horizontalVelocity = 0f    // Vélocité horizontale
    private var trajectoryPeak = 0f        // Point culminant du saut
    private var landingZone = 0f           // Zone d'atterrissage prévue
    
    // Système de pumping pour vitesse
    private var pumpEnergy = 0f
    private var pumpTiming = 0f
    private var pumpWindow = false         // Fenêtre de pumping optimal
    private var lastPumpTime = 0L
    private var pumpCombo = 0
    
    // Contrôles
    private var tiltX = 0f
    private var tiltY = 0f
    private var tiltZ = 0f
    private var accelX = 0f
    private var accelY = 0f
    private var accelZ = 0f
    
    // Système de tricks avec phases
    private var currentTrick = FreestyleTrick.NONE
    private var trickPhase = TrickPhase.NONE
    private var trickProgress = 0f
    private var trickSetupTime = 0f
    private var trickHoldTime = 0f         // Temps de maintien pour grabs
    private var trickRotation = 0f
    private var tricksCompleted = 0
    private var lastTrickType = FreestyleTrick.NONE
    
    // Parcours et sauts
    private val kickers = mutableListOf<Kicker>()
    private var nextKickerIndex = 0
    private var kickersHit = 0
    private var currentKicker: Kicker? = null
    
    // Scoring réaliste
    private var amplitude = 0f
    private var difficulty = 0f
    private var variety = 0f
    private var execution = 100f
    private var progression = 0f
    private var overallImpression = 100f
    private var totalScore = 0f
    private var finalScore = 0
    private var scoreCalculated = false
    
    // Progression et qualité
    private var perfectLandings = 0
    private var tricksUsed = mutableSetOf<FreestyleTrick>()
    private var biggestJump = 0f
    private var progressionPenalty = 0f
    private var repetitionPenalty = 0f
    
    // Effets visuels
    private var pisteScroll = 0f           // Défilement de la piste
    private var cameraShake = 0f
    private val snowSpray = mutableListOf<SnowParticle>()
    private val speedLines = mutableListOf<SpeedLine>()

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
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        statusText = TextView(this).apply {
            text = "🎿 SKI FREESTYLE - ${tournamentData.playerNames[currentPlayerIndex]}"
            setTextColor(Color.WHITE)
            textSize = 22f
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(25, 20, 25, 20)
        }

        gameView = SkiFreestyleView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        
        initializeGame()
    }
    
    private fun initializeGame() {
        gameState = GameState.PREPARATION
        phaseTimer = 0f
        skierX = 0.5f
        skierY = 0.9f
        speed = 0f
        baseSpeed = 15f
        momentum = 0f
        distanceTraveled = 0f
        
        isInAir = false
        airTime = 0f
        jumpStartSpeed = 0f
        verticalVelocity = 0f
        horizontalVelocity = 0f
        trajectoryPeak = 0f
        landingZone = 0f
        
        pumpEnergy = 0f
        pumpTiming = 0f
        pumpWindow = false
        lastPumpTime = 0L
        pumpCombo = 0
        
        tiltX = 0f
        tiltY = 0f
        tiltZ = 0f
        accelX = 0f
        accelY = 0f
        accelZ = 0f
        
        currentTrick = FreestyleTrick.NONE
        trickPhase = TrickPhase.NONE
        trickProgress = 0f
        trickSetupTime = 0f
        trickHoldTime = 0f
        trickRotation = 0f
        tricksCompleted = 0
        lastTrickType = FreestyleTrick.NONE
        
        nextKickerIndex = 0
        kickersHit = 0
        currentKicker = null
        
        amplitude = 0f
        difficulty = 0f
        variety = 0f
        execution = 100f
        progression = 0f
        overallImpression = 100f
        totalScore = 0f
        finalScore = 0
        scoreCalculated = false
        
        perfectLandings = 0
        tricksUsed.clear()
        biggestJump = 0f
        progressionPenalty = 0f
        repetitionPenalty = 0f
        
        pisteScroll = 0f
        cameraShake = 0f
        snowSpray.clear()
        speedLines.clear()
        
        generateFreestyleCourse()
    }
    
    private fun generateFreestyleCourse() {
        kickers.clear()
        var currentDistance = 100f
        
        // 6 kickers de difficulté progressive
        repeat(6) { i ->
            val size = when {
                i < 2 -> KickerSize.SMALL      // Warm-up
                i < 4 -> KickerSize.MEDIUM     // Building
                else -> KickerSize.LARGE       // Money booters
            }
            
            kickers.add(Kicker(
                distance = currentDistance,
                size = size,
                x = 0.3f + kotlin.random.Random.nextFloat() * 0.4f,
                hit = false,
                approach = KickerApproach.STRAIGHT
            ))
            
            currentDistance += 120f + i * 20f // Espacement progressif
        }
    }

    override fun onResume() {
        super.onResume()
        gyroscope?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        accelerometer?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                tiltX = if (abs(event.values[0]) > 0.1f) event.values[0] else 0f
                tiltY = if (abs(event.values[1]) > 0.1f) event.values[1] else 0f
                tiltZ = if (abs(event.values[2]) > 0.1f) event.values[2] else 0f
            }
            Sensor.TYPE_ACCELEROMETER -> {
                accelX = event.values[0]
                accelY = event.values[1]
                accelZ = event.values[2]
            }
        }

        phaseTimer += 0.016f

        when (gameState) {
            GameState.PREPARATION -> handlePreparation()
            GameState.SKIING -> handleSkiing()
            GameState.RESULTS -> handleResults()
            GameState.FINISHED -> {}
        }

        updateEffects()
        updateStatus()
        gameView.invalidate()
    }
    
    private fun handlePreparation() {
        if (phaseTimer >= preparationDuration) {
            gameState = GameState.SKIING
            phaseTimer = 0f
            speed = baseSpeed
        }
    }
    
    private fun handleSkiing() {
        // Système de pumping pour la vitesse
        handlePumping()
        
        // Mouvement du skieur
        handleSkierMovement()
        
        // Système de kickers et sauts
        handleKickerSystem()
        
        // Système de tricks avec phases
        handleTrickSystem()
        
        // Progression de course
        updateCourseProgress()
        
        // Physics et contraintes
        applyPhysicsConstraints()
        
        // Fin de run
        if (phaseTimer >= runDuration) {
            calculateFinalScore()
            gameState = GameState.RESULTS
            phaseTimer = 0f
        }
    }
    
    private fun handlePumping() {
        val currentTime = System.currentTimeMillis()
        
        // Détection de la fenêtre de pumping optimal
        val approachingKicker = getApproachingKicker()
        pumpWindow = approachingKicker != null && 
                    approachingKicker.distance - distanceTraveled in 30f..80f
        
        // Détection du mouvement de pump (incliner vers l'avant)
        if (tiltY < -0.4f && currentTime - lastPumpTime > 300L) {
            if (pumpWindow) {
                // Pump parfait dans la fenêtre !
                pumpEnergy = 1f
                pumpTiming = 1f
                speed += 5f
                pumpCombo++
                execution += 2f
                
            } else {
                // Pump en dehors de la fenêtre
                pumpEnergy = 0.3f
                pumpTiming = 0.3f
                speed += 1f
                pumpCombo = 0
                execution -= 1f
            }
            
            lastPumpTime = currentTime
            generatePumpEffect()
        }
        
        // Dégradation du pump
        pumpEnergy *= 0.95f
        pumpTiming *= 0.98f
    }
    
    private fun handleSkierMovement() {
        // Mouvement horizontal (steering) - skieur reste sur l'écran
        val horizontalInput = tiltX * 0.5f
        skierX += horizontalInput * 0.008f
        skierX = skierX.coerceIn(0.15f, 0.85f) // Garder le skieur visible
        
        // Le skieur reste à position Y fixe, c'est la piste qui bouge
        // skierY reste à 0.75f
        
        // Momentum naturel de descente - affecte le défilement
        momentum += 0.1f
        speed += momentum * 0.02f
        
        // Friction naturelle
        speed *= 0.998f
        
        // Vitesse de défilement de la piste basée sur la vitesse du skieur
        pisteScrollSpeed = speed * 3f // Plus la vitesse est élevée, plus la piste défile vite
        
        // Effets de vitesse
        if (speed > 20f) {
            generateSpeedEffects()
        }
    }
    
    private fun updateAirPhysics() {
        // Physique réaliste : trajectoire parabolique
        val gravity = 0.4f
        
        airTime += 0.016f
        
        // Mouvement vertical (parabole) - skieur bouge un peu en Y quand en l'air
        verticalVelocity -= gravity * 0.016f
        skierY += verticalVelocity * 0.016f
        
        // Mouvement horizontal minimal - le skieur reste principalement centré
        skierX += horizontalVelocity * 0.008f // Réduit pour garder contrôle
        skierX = skierX.coerceIn(0.15f, 0.85f)
        
        // Calcul du pic de trajectoire
        if (verticalVelocity <= 0f && trajectoryPeak == 0f) {
            trajectoryPeak = skierY
            amplitude = max(amplitude, 0.75f - trajectoryPeak)
        }
        
        // Atterrissage - retour à position fixe
        if (skierY >= 0.75f) {
            landFromJump()
        }
    }
    
    private fun calculateLandingHeight(): Float {
        // Hauteur de la piste selon la position X (pente du kicker)
        return 0.9f - abs(skierX - 0.5f) * 0.1f
    }
    
    private fun getApproachingKicker(): Kicker? {
        return if (nextKickerIndex < kickers.size) {
            val kicker = kickers[nextKickerIndex]
            val distance = kicker.distance - distanceTraveled
            if (distance > 0f && distance < 150f) kicker else null
        } else null
    }
    
    private fun handleKickerSystem() {
        val approachingKicker = getApproachingKicker()
        
        if (approachingKicker != null) {
            val kickerDistance = approachingKicker.distance - distanceTraveled
            val distanceToKicker = abs(skierX - approachingKicker.x)
            
            // Hit du kicker
            if (kickerDistance < 5f && !approachingKicker.hit && distanceToKicker < 0.2f) {
                hitKicker(approachingKicker)
            }
        }
    }
    
    private fun hitKicker(kicker: Kicker) {
        kicker.hit = true
        currentKicker = kicker
        isInAir = true
        airTime = 0f
        jumpStartSpeed = speed
        kickersHit++
        nextKickerIndex++
        
        // Calcul de la trajectoire selon vitesse et taille du kicker
        val kickerPower = when (kicker.size) {
            KickerSize.SMALL -> 0.15f
            KickerSize.MEDIUM -> 0.25f
            KickerSize.LARGE -> 0.35f
        }
        
        // Plus de vitesse = trajectoire plus haute et longue
        val speedFactor = (speed / 30f).coerceIn(0.5f, 1.5f)
        verticalVelocity = -(kickerPower * speedFactor + pumpEnergy * 0.1f)
        horizontalVelocity = (skierX - 0.5f) * 0.02f // Légère dérive horizontale
        trajectoryPeak = 0f
        
        // Vérification progression
        val jumpSize = kickerPower * speedFactor
        if (jumpSize < biggestJump) {
            progressionPenalty += 10f // Pénalité si pas de progression
        }
        biggestJump = max(biggestJump, jumpSize)
        
        // Effets visuels
        cameraShake = kickerPower * 0.8f
        generateKickerHitEffect()
        
        // Score de base du saut
        val baseJumpScore = when (kicker.size) {
            KickerSize.SMALL -> 10f
            KickerSize.MEDIUM -> 20f
            KickerSize.LARGE -> 35f
        }
        
        totalScore += baseJumpScore * speedFactor
    }
    
    private fun handleTrickSystem() {
        if (!isInAir) {
            // Reset sur le sol
            if (currentTrick != FreestyleTrick.NONE) {
                finalizeTrick()
            }
            return
        }
        
        trickSetupTime += 0.016f
        
        when (trickPhase) {
            TrickPhase.NONE -> {
                // Fenêtre pour initier un trick
                if (trickSetupTime > 0.1f) {
                    detectTrickInitiation()
                }
            }
            TrickPhase.SETUP -> {
                // Phase de setup du trick
                if (trickSetupTime > 0.3f) {
                    trickPhase = TrickPhase.EXECUTION
                }
            }
            TrickPhase.EXECUTION -> {
                // Exécution du trick
                executeTrick()
            }
            TrickPhase.LANDING -> {
                // Préparation du landing
                prepareLanding()
            }
        }
    }
    
    private fun detectTrickInitiation() {
        val rotationThreshold = 1.2f
        val flipThreshold = 1.5f
        val grabThreshold = 8f
        
        when {
            abs(tiltZ) > rotationThreshold && currentTrick == FreestyleTrick.NONE -> {
                initiateTrick(FreestyleTrick.SPIN_360)
            }
            abs(tiltY) > flipThreshold && currentTrick == FreestyleTrick.NONE -> {
                initiateTrick(FreestyleTrick.BACKFLIP)
            }
            abs(accelZ) > grabThreshold && currentTrick == FreestyleTrick.NONE -> {
                initiateTrick(FreestyleTrick.INDY_GRAB)
            }
            abs(tiltZ) > rotationThreshold && abs(accelZ) > grabThreshold -> {
                initiateTrick(FreestyleTrick.SPIN_GRAB)
            }
        }
    }
    
    private fun initiateTrick(trick: FreestyleTrick) {
        currentTrick = trick
        trickPhase = TrickPhase.SETUP
        trickProgress = 0f
        trickRotation = 0f
        trickHoldTime = 0f
        
        difficulty += trick.difficultyPoints
        
        // Vérification répétition
        if (trick == lastTrickType) {
            repetitionPenalty += 20f // Grosse pénalité pour répétition
        }
        
        tricksUsed.add(trick)
    }
    
    private fun executeTrick() {
        when (currentTrick) {
            FreestyleTrick.SPIN_360 -> {
                trickProgress += abs(tiltZ) * 0.02f
                trickRotation += tiltZ * 2f
                trickProgress = (abs(trickRotation) / 360f).coerceIn(0f, 3f) // Max 1080°
            }
            FreestyleTrick.BACKFLIP -> {
                trickProgress += abs(tiltY) * 0.015f
                trickRotation += tiltY * 1.5f
                trickProgress = (abs(trickRotation) / 360f).coerceIn(0f, 2f)
            }
            FreestyleTrick.INDY_GRAB -> {
                if (abs(accelZ) > 6f) {
                    trickHoldTime += 0.016f
                    trickProgress = (trickHoldTime / 0.8f).coerceIn(0f, 1f)
                } else {
                    trickProgress *= 0.95f // Decay si pas tenu
                }
            }
            FreestyleTrick.SPIN_GRAB -> {
                trickRotation += abs(tiltZ) * 1.5f
                if (abs(accelZ) > 6f) {
                    trickHoldTime += 0.016f
                }
                val spinScore = abs(trickRotation) / 360f
                val grabScore = trickHoldTime / 0.6f
                trickProgress = (spinScore * 0.6f + grabScore * 0.4f).coerceIn(0f, 2f)
            }
            else -> {}
        }
        
        // Préparation landing si proche du sol
        if (verticalVelocity > 0f && skierY > 0.7f) {
            trickPhase = TrickPhase.LANDING
        }
    }
    
    private fun prepareLanding() {
        // Le trick doit être "fermé" pour un bon landing
        // Plus de mouvement = moins bonne préparation
        val stability = 1f - (abs(tiltX) + abs(tiltY) + abs(tiltZ)) / 6f
        trickProgress *= 1f + stability * 0.1f
    }
    
    private fun landFromJump() {
        isInAir = false
        airTime = 0f
        skierY = calculateLandingHeight()
        verticalVelocity = 0f
        horizontalVelocity = 0f
        
        // Évaluation de la qualité d'atterrissage
        val landingQuality = evaluateLanding()
        
        when {
            landingQuality > 0.9f -> {
                perfectLandings++
                execution += 8f
                generatePerfectLandingEffect()
            }
            landingQuality > 0.7f -> {
                execution += 4f
                generateGoodLandingEffect()
            }
            landingQuality > 0.4f -> {
                execution += 1f
            }
            else -> {
                execution -= 5f
                speed *= 0.8f // Perte de vitesse sur bad landing
                cameraShake = 0.4f
            }
        }
        
        if (currentTrick != FreestyleTrick.NONE) {
            finalizeTrick()
        }
        
        currentKicker = null
    }
    
    private fun evaluateLanding(): Float {
        val stabilityScore = 1f - (abs(tiltX) + abs(tiltY)) / 4f
        val speedScore = (speed / 25f).coerceIn(0.5f, 1f)
        val angleScore = 1f - abs(skierY - calculateLandingHeight()) * 5f
        
        return (stabilityScore * 0.5f + speedScore * 0.3f + angleScore * 0.2f).coerceIn(0f, 1f)
    }
    
    private fun finalizeTrick() {
        if (trickProgress > 0.4f) {
            // Trick réussi !
            tricksCompleted++
            val trickScore = calculateTrickScore()
            totalScore += trickScore
            
            variety = tricksUsed.size * 15f // Points pour variété
            
            lastTrickType = currentTrick
            
        } else {
            // Trick raté
            execution -= 8f
            overallImpression -= 5f
        }
        
        currentTrick = FreestyleTrick.NONE
        trickPhase = TrickPhase.NONE
        trickProgress = 0f
        trickSetupTime = 0f
    }
    
    private fun calculateTrickScore(): Float {
        val baseScore = currentTrick.baseScore
        val progressBonus = trickProgress * 30f
        val airTimeBonus = airTime * 10f
        val holdBonus = if (currentTrick.isGrab) trickHoldTime * 25f else 0f
        val amplitudeBonus = amplitude * 20f
        
        return baseScore + progressBonus + airTimeBonus + holdBonus + amplitudeBonus
    }
    
    private fun updateCourseProgress() {
        distanceTraveled += speed * 0.03f
        pisteScroll += pisteScrollSpeed * 0.08f // La piste défile selon la vitesse
    }
    
    private fun applyPhysicsConstraints() {
        // Contraintes de vitesse
        speed = speed.coerceIn(8f, 35f)
        
        // Dégradation naturelle
        execution = max(70f, execution - 0.01f)
        overallImpression = max(70f, overallImpression - 0.005f)
    }
    
    private fun generatePumpEffect() {
        repeat(5) {
            snowSpray.add(SnowParticle(
                x = kotlin.random.Random.nextFloat() * 200f + 400f,
                y = kotlin.random.Random.nextFloat() * 100f + 600f,
                vx = (kotlin.random.Random.nextFloat() - 0.5f) * 6f,
                vy = kotlin.random.Random.nextFloat() * -4f - 2f,
                life = 1f,
                color = Color.WHITE
            ))
        }
    }
    
    private fun generateSpeedEffects() {
        speedLines.add(SpeedLine(
            x = kotlin.random.Random.nextFloat() * 800f + 100f,
            y = kotlin.random.Random.nextFloat() * 600f + 100f,
            speed = speed * 0.4f
        ))
        
        if (speedLines.size > 12) {
            speedLines.removeFirst()
        }
    }
    
    private fun generateKickerHitEffect() {
        repeat(12) {
            snowSpray.add(SnowParticle(
                x = kotlin.random.Random.nextFloat() * 300f + 350f,
                y = kotlin.random.Random.nextFloat() * 150f + 500f,
                vx = (kotlin.random.Random.nextFloat() - 0.5f) * 8f,
                vy = kotlin.random.Random.nextFloat() * -6f - 3f,
                life = 1.5f,
                color = Color.WHITE
            ))
        }
    }
    
    private fun generatePerfectLandingEffect() {
        repeat(20) {
            snowSpray.add(SnowParticle(
                x = kotlin.random.Random.nextFloat() * 600f + 200f,
                y = kotlin.random.Random.nextFloat() * 200f + 500f,
                vx = (kotlin.random.Random.nextFloat() - 0.5f) * 10f,
                vy = kotlin.random.Random.nextFloat() * -8f - 2f,
                life = 2f,
                color = Color.parseColor("#FFD700")
            ))
        }
    }
    
    private fun generateGoodLandingEffect() {
        repeat(10) {
            snowSpray.add(SnowParticle(
                x = kotlin.random.Random.nextFloat() * 400f + 300f,
                y = kotlin.random.Random.nextFloat() * 150f + 550f,
                vx = (kotlin.random.Random.nextFloat() - 0.5f) * 6f,
                vy = kotlin.random.Random.nextFloat() * -5f - 2f,
                life = 1.2f,
                color = Color.parseColor("#00FF00")
            ))
        }
    }
    
    private fun updateEffects() {
        // Particules de neige
        snowSpray.removeAll { particle ->
            particle.x += particle.vx
            particle.y += particle.vy
            particle.life -= 0.015f
            particle.life <= 0f || particle.y > 1000f
        }
        
        // Lignes de vitesse
        speedLines.removeAll { line ->
            line.x -= line.speed
            line.x < -100f
        }
        
        cameraShake = max(0f, cameraShake - 0.02f)
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
    
    private fun calculateFinalScore() {
        if (!scoreCalculated) {
            // Système de notation réaliste
            val amplitudePoints = (amplitude * 25f).toInt()     // 25%
            val difficultyPoints = (difficulty * 20f).toInt()   // 20%
            val varietyPoints = variety.toInt()                  // 15%
            val executionPoints = ((execution - 100f) * 10f).toInt() // 10%
            val impressionPoints = ((overallImpression - 100f) * 10f).toInt() // 10%
            val tricksPoints = totalScore.toInt()                // 20%
            
            val bonusPoints = perfectLandings * 15
            val penaltyPoints = (progressionPenalty + repetitionPenalty).toInt()
            
            finalScore = maxOf(80, 
                amplitudePoints + difficultyPoints + varietyPoints + 
                executionPoints + impressionPoints + tricksPoints + 
                bonusPoints - penaltyPoints
            )
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
                val aiScore = (120..250).random()
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
            GameState.SKIING -> {
                val trickText = if (currentTrick != FreestyleTrick.NONE) " | ${currentTrick.displayName}" else ""
                val speedText = "Speed: ${speed.toInt()}km/h"
                "🎿 ${tournamentData.playerNames[currentPlayerIndex]} | $speedText | Kickers: $kickersHit/6$trickText"
            }
            GameState.RESULTS -> "🏆 ${tournamentData.playerNames[currentPlayerIndex]} | Score: ${finalScore} | Tricks: $tricksCompleted"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Run terminé!"
        }
    }

    private fun getCountryFlag(country: String): String {
        return when (country.uppercase()) {
            "FRANCE" -> "🇫🇷"
            "CANADA" -> "🇨🇦"
            "USA", "ÉTATS-UNIS", "ETATS-UNIS" -> "🇺🇸"
            "ALLEMAGNE", "GERMANY" -> "🇩🇪"
            "ITALIE", "ITALY" -> "🇮🇹"
            "SUISSE", "SWITZERLAND" -> "🇨🇭"
            "AUTRICHE", "AUSTRIA" -> "🇦🇹"
            "NORVÈGE", "NORWAY" -> "🇳🇴"
            "SUÈDE", "SWEDEN" -> "🇸🇪"
            "FINLANDE", "FINLAND" -> "🇫🇮"
            "JAPON", "JAPAN" -> "🇯🇵"
            "CORÉE", "KOREA" -> "🇰🇷"
            "RUSSIE", "RUSSIA" -> "🇷🇺"
            "POLOGNE", "POLAND" -> "🇵🇱"
            "SLOVÉNIE", "SLOVENIA" -> "🇸🇮"
            "RÉPUBLIQUE TCHÈQUE", "CZECH REPUBLIC" -> "🇨🇿"
            else -> "🏴"
        }
    }

    inner class SkiFreestyleView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val reusableRectF = RectF()
        private val reusablePath = Path()

        override fun onDraw(canvas: Canvas) {
            val w = width
            val h = height
            
            // Appliquer shake de caméra
            canvas.save()
            if (cameraShake > 0f) {
                canvas.translate(
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 15f,
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 15f
                )
            }
            
            when (gameState) {
                GameState.PREPARATION -> drawPreparation(canvas, w, h)
                GameState.SKIING -> drawSkiing(canvas, w, h)
                GameState.RESULTS -> drawResults(canvas, w, h)
                GameState.FINISHED -> drawResults(canvas, w, h)
            }
            
            drawEffects(canvas, w, h)
            canvas.restore()
        }
        
        private fun drawPreparation(canvas: Canvas, w: Int, h: Int) {
            // Fond de montagne
            val skyGradient = LinearGradient(0f, 0f, 0f, h.toFloat(),
                Color.parseColor("#87CEEB"), Color.parseColor("#E0F6FF"), Shader.TileMode.CLAMP)
            paint.shader = skyGradient
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
            
            // Vue de dessus de la piste en perspective
            drawPisteOverview(canvas, w, h)
            
            // Drapeau du pays
            val playerCountry = if (practiceMode) "CANADA" else tournamentData.playerCountries[currentPlayerIndex]
            val flagText = getCountryFlag(playerCountry)
            
            paint.color = Color.WHITE
            reusableRectF.set(50f, 50f, 250f, 170f)
            canvas.drawRoundRect(reusableRectF, 15f, 15f, paint)
            
            paint.color = Color.parseColor("#001122")
            paint.textSize = 60f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(flagText, 150f, 130f, paint)
            
            paint.textSize = 20f
            canvas.drawText(playerCountry.uppercase(), 150f, 160f, paint)
            
            // Titre
            paint.textSize = 48f
            canvas.drawText("🎿 SKI FREESTYLE 🎿", w/2f, h * 0.15f, paint)
            
            // Countdown
            val countdown = (preparationDuration - phaseTimer).toInt() + 1
            paint.textSize = 80f
            paint.color = Color.RED
            canvas.drawText("${countdown}", w/2f, h * 0.7f, paint)
            
            // Instructions
            paint.textSize = 24f
            paint.color = Color.parseColor("#333333")
            canvas.drawText("📱 Inclinez vers l'avant pour pumper", w/2f, h * 0.85f, paint)
            canvas.drawText("📱 Mouvements en l'air = tricks", w/2f, h * 0.9f, paint)
        }
        
        private fun drawPisteOverview(canvas: Canvas, w: Int, h: Int) {
            // Piste vue de dessus avec perspective
            paint.color = Color.WHITE
            
            reusablePath.reset()
            reusablePath.moveTo(w * 0.3f, h * 0.3f)
            reusablePath.lineTo(w * 0.7f, h * 0.3f)
            reusablePath.lineTo(w * 0.6f, h * 0.7f)
            reusablePath.lineTo(w * 0.4f, h * 0.7f)
            reusablePath.close()
            canvas.drawPath(reusablePath, paint)
            
            // Kickers prévisualisés
            for (i in 0..2) {
                val y = h * (0.35f + i * 0.1f)
                val kickerWidth = 40f - i * 8f
                paint.color = Color.parseColor("#DDDDDD")
                reusableRectF.set(w/2f - kickerWidth/2f, y, w/2f + kickerWidth/2f, y + kickerWidth/3f)
                canvas.drawRoundRect(reusableRectF, 5f, 5f, paint)
            }
        }
        
        private fun drawSkiing(canvas: Canvas, w: Int, h: Int) {
            // Vue depuis derrière le skieur en perspective
            drawPisteFromBehind(canvas, w, h)
            
            // Kickers sur la piste
            drawKickers(canvas, w, h)
            
            // Skieur vu de dos
            drawSkierFromBehind(canvas, w, h)
            
            // Interface de jeu
            drawGameInterface(canvas, w, h)
            
            // Barre de pump rhythm
            drawPumpBar(canvas, w, h)
            
            // Trajectoire si en l'air
            if (isInAir) {
                drawTrajectory(canvas, w, h)
            }
        }
        
        private fun drawPisteFromBehind(canvas: Canvas, w: Int, h: Int) {
            // Fond ciel
            val skyGradient = LinearGradient(0f, 0f, 0f, h * 0.4f,
                Color.parseColor("#87CEEB"), Color.parseColor("#E0F6FF"), Shader.TileMode.CLAMP)
            paint.shader = skyGradient
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
            paint.shader = null
            
            // Piste qui défile de HAUT vers BAS avec perspective (comme le slalom)
            val scrollOffset = pisteScroll % 150f
            
            paint.color = Color.WHITE
            
            // Piste en perspective qui se rétrécit vers le haut
            reusablePath.reset()
            reusablePath.moveTo(w * 0.45f, 0f)           // Haut étroit (loin)
            reusablePath.lineTo(w * 0.55f, 0f)           
            reusablePath.lineTo(w * 0.85f, h.toFloat())   // Bas large (proche)
            reusablePath.lineTo(w * 0.15f, h.toFloat())   
            reusablePath.close()
            canvas.drawPath(reusablePath, paint)
            
            // Lignes de défilement qui descendent pour effet de mouvement
            paint.color = Color.parseColor("#EEEEEE")
            paint.strokeWidth = 2f
            paint.style = Paint.Style.STROKE
            
            for (i in 0..15) {
                val lineY = i * 80f - scrollOffset // Les lignes descendent
                if (lineY >= 0f && lineY <= h.toFloat()) {
                    val perspective = lineY / h.toFloat()
                    val leftX = w * (0.45f + perspective * 0.05f)
                    val rightX = w * (0.55f - perspective * 0.05f)
                    canvas.drawLine(leftX, lineY, rightX, lineY, paint)
                }
            }
            
            paint.style = Paint.Style.FILL
        }
        
        private fun drawKickers(canvas: Canvas, w: Int, h: Int) {
            for (kicker in kickers) {
                val kickerScreenDistance = kicker.distance - distanceTraveled
                
                if (kickerScreenDistance > -50f && kickerScreenDistance < 300f) {
                    // Position sur l'écran - les kickers descendent vers le skieur
                    val screenY = (kickerScreenDistance / 300f) * h // Plus proche = plus bas sur l'écran
                    val perspective = (h - screenY) / h.toFloat()
                    
                    if (screenY >= 0f && screenY < h.toFloat()) {
                        val kickerSize = when (kicker.size) {
                            KickerSize.SMALL -> 25f
                            KickerSize.MEDIUM -> 40f
                            KickerSize.LARGE -> 60f
                        } * perspective.coerceIn(0.3f, 1f)
                        
                        // Position sur la piste en perspective
                        val pisteLeft = w * (0.45f + perspective * 0.05f)
                        val pisteRight = w * (0.55f - perspective * 0.05f)
                        val pisteWidth = pisteRight - pisteLeft
                        val screenX = pisteLeft + kicker.x * pisteWidth
                        
                        // Couleur selon statut
                        paint.color = if (kicker.hit) {
                            Color.parseColor("#00AA00")
                        } else {
                            Color.parseColor("#FFFFFF")
                        }
                        
                        // Forme du kicker
                        reusablePath.reset()
                        reusablePath.moveTo(screenX - kickerSize, screenY + kickerSize/2f)
                        reusablePath.lineTo(screenX - kickerSize/3f, screenY)
                        reusablePath.lineTo(screenX + kickerSize/3f, screenY)
                        reusablePath.lineTo(screenX + kickerSize, screenY + kickerSize/2f)
                        reusablePath.close()
                        canvas.drawPath(reusablePath, paint)
                        
                        // Indicateur de taille
                        if (perspective > 0.4f) {
                            paint.color = Color.BLACK
                            paint.textSize = 12f * perspective
                            paint.textAlign = Paint.Align.CENTER
                            val sizeText = when (kicker.size) {
                                KickerSize.SMALL -> "S"
                                KickerSize.MEDIUM -> "M"
                                KickerSize.LARGE -> "L"
                            }
                            canvas.drawText(sizeText, screenX, screenY + kickerSize/4f, paint)
                        }
                    }
                }
            }
        }
        
        private fun drawSkierFromBehind(canvas: Canvas, w: Int, h: Int) {
            val skierScreenX = w * (0.15f + skierX * 0.7f)
            val skierScreenY = h * skierY
            
            canvas.save()
            canvas.translate(skierScreenX, skierScreenY)
            
            // Rotation selon tricks
            when (currentTrick) {
                FreestyleTrick.SPIN_360 -> canvas.rotate(trickRotation * 0.5f)
                FreestyleTrick.BACKFLIP -> canvas.rotate(trickRotation * 0.3f) // Utiliser rotate normal
                FreestyleTrick.SPIN_GRAB -> {
                    canvas.rotate(trickRotation * 0.4f)
                    canvas.scale(1f + trickProgress * 0.1f, 1f + trickProgress * 0.1f)
                }
                else -> {}
            }
            
            // Corps du skieur (vu de dos)
            paint.color = Color.parseColor("#FF6600") // Combinaison
            canvas.drawRect(-12f, -25f, 12f, 15f, paint)
            
            // Casque
            paint.color = Color.parseColor("#FFFFFF")
            canvas.drawCircle(0f, -30f, 10f, paint)
            
            // Bras
            paint.color = Color.parseColor("#FF6600")
            paint.strokeWidth = 6f
            paint.style = Paint.Style.STROKE
            
            if (currentTrick == FreestyleTrick.INDY_GRAB || currentTrick == FreestyleTrick.SPIN_GRAB) {
                // Position grab
                canvas.drawLine(-8f, -8f, -15f, 20f, paint)
                canvas.drawLine(8f, -8f, 15f, 20f, paint)
            } else {
                // Position normale
                canvas.drawLine(-10f, -12f, -18f, -5f, paint)
                canvas.drawLine(10f, -12f, 18f, -5f, paint)
            }
            
            // Jambes
            canvas.drawLine(-6f, 10f, -10f, 30f, paint)
            canvas.drawLine(6f, 10f, 10f, 30f, paint)
            
            // Skis
            paint.color = Color.YELLOW
            paint.strokeWidth = 8f
            canvas.drawLine(-15f, 25f, -15f, 45f, paint)
            canvas.drawLine(15f, 25f, 15f, 45f, paint)
            
            // Bâtons
            paint.color = Color.parseColor("#8B4513")
            paint.strokeWidth = 4f
            canvas.drawLine(-20f, -8f, -25f, -20f, paint)
            canvas.drawLine(20f, -8f, 25f, -20f, paint)
            
            paint.style = Paint.Style.FILL
            canvas.restore()
            
            // Ombre si au sol
            if (!isInAir) {
                paint.color = Color.parseColor("#33000000")
                canvas.drawOval(skierScreenX - 25f, h * 0.92f, skierScreenX + 25f, h * 0.95f, paint)
            }
        }
        
        private fun drawGameInterface(canvas: Canvas, w: Int, h: Int) {
            val baseY = h - 160f
            
            // Scores et stats
            paint.color = Color.parseColor("#001122")
            paint.textSize = 22f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Score: ${totalScore.toInt()}", 20f, baseY, paint)
            canvas.drawText("Speed: ${speed.toInt()} km/h", 20f, baseY + 25f, paint)
            canvas.drawText("Kickers: $kickersHit/6", 20f, baseY + 50f, paint)
            
            // Trick en cours
            if (currentTrick != FreestyleTrick.NONE) {
                paint.color = Color.parseColor("#FF6600")
                paint.textSize = 28f
                paint.textAlign = Paint.Align.CENTER
                
                val trickText = when (currentTrick) {
                    FreestyleTrick.SPIN_360 -> "${abs(trickRotation).toInt()}° SPIN"
                    FreestyleTrick.BACKFLIP -> "BACKFLIP ${(trickProgress * 100).toInt()}%"
                    FreestyleTrick.INDY_GRAB -> "INDY ${(trickHoldTime * 100).toInt()}%"
                    FreestyleTrick.SPIN_GRAB -> "${abs(trickRotation).toInt()}° GRAB"
                    else -> currentTrick.displayName
                }
                
                canvas.drawText(trickText, w/2f, baseY, paint)
                
                // Phase du trick
                paint.textSize = 16f
                canvas.drawText("Phase: ${trickPhase.name}", w/2f, baseY + 25f, paint)
            }
            
            // Métriques de performance
            drawPerformanceMeter(canvas, w - 200f, baseY - 30f, 180f, amplitude / 0.4f, "AMPLITUDE", Color.parseColor("#FF4444"))
            drawPerformanceMeter(canvas, w - 200f, baseY - 5f, 180f, execution / 120f, "EXECUTION", Color.parseColor("#44FF44"))
            drawPerformanceMeter(canvas, w - 200f, baseY + 20f, 180f, (variety / 90f).coerceAtMost(1f), "VARIETY", Color.parseColor("#4444FF"))
        }
        
        private fun drawPumpBar(canvas: Canvas, w: Int, h: Int) {
            val barX = 50f
            val barY = 100f
            val barWidth = w * 0.4f
            val barHeight = 25f
            
            // Fond
            paint.color = Color.parseColor("#333333")
            reusableRectF.set(barX, barY, barX + barWidth, barY + barHeight)
            canvas.drawRect(reusableRectF, paint)
            
            // Zone de pump optimal
            if (pumpWindow) {
                paint.color = Color.parseColor("#00FF00")
                val optimalStart = barWidth * 0.3f
                val optimalWidth = barWidth * 0.4f
                reusableRectF.set(barX + optimalStart, barY, barX + optimalStart + optimalWidth, barY + barHeight)
                canvas.drawRect(reusableRectF, paint)
            }
            
            // Indicateur de vitesse actuelle
            val speedRatio = (speed / 35f).coerceIn(0f, 1f)
            val indicatorX = barX + speedRatio * barWidth
            paint.color = Color.parseColor("#FFFF00")
            paint.strokeWidth = 4f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(indicatorX, barY, indicatorX, barY + barHeight, paint)
            paint.style = Paint.Style.FILL
            
            // Effet de pump
            if (pumpEnergy > 0f) {
                paint.color = Color.parseColor("#FF6600")
                paint.alpha = (pumpEnergy * 180).toInt()
                val pumpWidth = barWidth * pumpTiming
                reusableRectF.set(barX, barY, barX + pumpWidth, barY + barHeight)
                canvas.drawRect(reusableRectF, paint)
                paint.alpha = 255
            }
            
            // Label
            paint.color = Color.WHITE
            paint.textSize = 14f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("PUMP RHYTHM - VITESSE", barX, barY - 5f, paint)
            
            if (pumpCombo > 0) {
                paint.color = Color.parseColor("#00FF00")
                canvas.drawText("Perfect Pumps x$pumpCombo", barX, barY + barHeight + 18f, paint)
            }
        }
        
        private fun drawTrajectory(canvas: Canvas, w: Int, h: Int) {
            // Arc de trajectoire prévisionnelle
            val startX = w * (0.15f + skierX * 0.7f)
            val startY = h * skierY
            
            // Calcul de la trajectoire restante
            val remainingTime = (-verticalVelocity / 0.4f).coerceAtLeast(0f)
            val landingX = startX + horizontalVelocity * remainingTime * 60f
            val peakY = startY + verticalVelocity * remainingTime * 30f - 0.5f * 0.4f * remainingTime * remainingTime * 900f
            
            // Dessiner l'arc
            paint.color = Color.parseColor("#AAFFFFFF")
            paint.strokeWidth = 3f
            paint.style = Paint.Style.STROKE
            
            reusablePath.reset()
            reusablePath.moveTo(startX, startY)
            reusablePath.quadTo(
                (startX + landingX) / 2f, 
                peakY,
                landingX, 
                h * 0.9f
            )
            canvas.drawPath(reusablePath, paint)
            
            paint.style = Paint.Style.FILL
            
            // Point d'atterrissage prévu
            paint.color = Color.parseColor("#FFFF00")
            canvas.drawCircle(landingX, h * 0.9f, 8f, paint)
        }
        
        private fun drawPerformanceMeter(canvas: Canvas, x: Float, y: Float, width: Float, 
                                       value: Float, label: String, color: Int) {
            // Fond
            paint.color = Color.parseColor("#333333")
            reusableRectF.set(x, y, x + width, y + 18f)
            canvas.drawRect(reusableRectF, paint)
            
            // Barre
            paint.color = color
            val filledWidth = value.coerceIn(0f, 1f) * width
            reusableRectF.set(x, y, x + filledWidth, y + 18f)
            canvas.drawRect(reusableRectF, paint)
            
            // Label
            paint.color = Color.WHITE
            paint.textSize = 12f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("$label: ${(value * 100).toInt()}%", x, y - 3f, paint)
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            // Fond avec dégradé
            val resultGradient = LinearGradient(0f, 0f, 0f, h.toFloat(),
                Color.parseColor("#FFD700"), Color.parseColor("#FFF8DC"), Shader.TileMode.CLAMP)
            paint.shader = resultGradient
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
            
            // Score final
            paint.color = Color.parseColor("#001122")
            paint.textSize = 72f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore}", w/2f, h * 0.2f, paint)
            
            paint.textSize = 32f
            canvas.drawText("POINTS", w/2f, h * 0.28f, paint)
            
            // Breakdown détaillé
            paint.color = Color.parseColor("#333333")
            paint.textSize = 22f
            
            val startY = h * 0.4f
            val lineHeight = 30f
            
            canvas.drawText("🎿 Kickers touchés: $kickersHit/6", w/2f, startY, paint)
            canvas.drawText("🎪 Tricks réussis: $tricksCompleted", w/2f, startY + lineHeight, paint)
            canvas.drawText("📏 Amplitude max: ${(amplitude * 250).toInt()}cm", w/2f, startY + lineHeight * 2, paint)
            canvas.drawText("🎯 Atterrissages parfaits: $perfectLandings", w/2f, startY + lineHeight * 3, paint)
            canvas.drawText("🌈 Variété: ${tricksUsed.size} tricks différents", w/2f, startY + lineHeight * 4, paint)
            canvas.drawText("⚡ Vitesse max: ${speed.toInt()} km/h", w/2f, startY + lineHeight * 5, paint)
            
            // Message selon performance
            val message = when {
                finalScore >= 300 -> "🏆 RUN LÉGENDAIRE!"
                finalScore >= 250 -> "🥇 EXCELLENT STYLE!"
                finalScore >= 200 -> "🥈 TRÈS BON RUN!"
                finalScore >= 150 -> "🥉 BIEN JOUÉ!"
                else -> "💪 CONTINUE À PROGRESSER!"
            }
            
            paint.color = Color.parseColor("#FF6600")
            paint.textSize = 28f
            canvas.drawText(message, w/2f, h * 0.9f, paint)
        }
        
        private fun drawEffects(canvas: Canvas, w: Int, h: Int) {
            // Particules de neige
            for (particle in snowSpray) {
                paint.alpha = (particle.life * 255).toInt()
                paint.color = particle.color
                canvas.drawCircle(particle.x, particle.y, particle.life * 6f, paint)
            }
            paint.alpha = 255
            
            // Lignes de vitesse verticales
            paint.color = Color.parseColor("#66FFFFFF")
            paint.strokeWidth = 3f
            paint.style = Paint.Style.STROKE
            for (line in speedLines) {
                canvas.drawLine(line.x, line.y, line.x, line.y + 25f, paint) // Lignes verticales
            }
            paint.style = Paint.Style.FILL
        }
    }

    enum class FreestyleTrick(val displayName: String, val baseScore: Float, val difficultyPoints: Float, val isGrab: Boolean) {
        NONE("", 0f, 0f, false),
        SPIN_360("360°", 25f, 2f, false),
        BACKFLIP("BACKFLIP", 35f, 3f, false),
        INDY_GRAB("INDY GRAB", 30f, 2.5f, true),
        SPIN_GRAB("SPIN GRAB", 45f, 4f, true)
    }
    
    enum class TrickPhase {
        NONE, SETUP, EXECUTION, LANDING
    }
    
    enum class KickerSize {
        SMALL, MEDIUM, LARGE
    }
    
    enum class KickerApproach {
        STRAIGHT, LEFT, RIGHT
    }
    
    data class Kicker(
        val distance: Float,
        val size: KickerSize,
        val x: Float,
        var hit: Boolean,
        val approach: KickerApproach
    )
    
    data class SnowParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        val color: Int
    )
    
    data class SpeedLine(
        var x: Float,
        val y: Float,
        val speed: Float
    )

    enum class GameState {
        PREPARATION, SKIING, RESULTS, FINISHED
    }
}
