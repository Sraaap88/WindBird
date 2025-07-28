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
    var gameState = GameState.PREPARATION
        private set
    private var phaseTimer = 0f
    
    // Durées des phases
    private val preparationDuration = 5f
    private val runDuration = 60f
    private val resultsDuration = 8f
    
    // Physique réaliste du skieur
    var skierX = 0.5f              // Position horizontale (0-1) 
        private set
    var skierY = 0.75f             // Position fixe sur l'écran (skieur reste là)
        private set
    var speed = 0f                 // Vitesse actuelle
        private set
    private var baseSpeed = 15f    // Vitesse de base
    private var momentum = 0f      // Momentum de descente
    var distanceTraveled = 0f      // Distance parcourue
        private set
    var pisteScrollSpeed = 0f      // Vitesse de défilement de la piste
        private set
    
    // Système de vol avec physique réaliste
    var isInAir = false
        private set
    var airTime = 0f
        private set
    private var jumpStartSpeed = 0f        // Vitesse au décollage
    var verticalVelocity = 0f      // Vélocité verticale
        private set
    var horizontalVelocity = 0f    // Vélocité horizontale
        private set
    private var trajectoryPeak = 0f        // Point culminant du saut
    private var landingZone = 0f           // Zone d'atterrissage prévue
    
    // Système de pumping pour vitesse
    var pumpEnergy = 0f
        private set
    var pumpTiming = 0f
        private set
    var pumpWindow = false         // Fenêtre de pumping optimal
        private set
    private var lastPumpTime = 0L
    var pumpCombo = 0
        private set
    
    // Contrôles
    private var tiltX = 0f
    private var tiltY = 0f
    private var tiltZ = 0f
    private var accelX = 0f
    private var accelY = 0f
    private var accelZ = 0f
    
    // Système de tricks avec phases
    var currentTrick = FreestyleTrick.NONE
        private set
    var trickPhase = TrickPhase.NONE
        private set
    var trickProgress = 0f
        private set
    private var trickSetupTime = 0f
    var trickHoldTime = 0f         // Temps de maintien pour grabs
        private set
    var trickRotation = 0f
        private set
    var tricksCompleted = 0
        private set
    private var lastTrickType = FreestyleTrick.NONE
    
    // Parcours et sauts
    val kickers = mutableListOf<Kicker>()
    private var nextKickerIndex = 0
    var kickersHit = 0
        private set
    var currentKicker: Kicker? = null
        private set
    
    // Scoring réaliste
    var amplitude = 0f
        private set
    private var difficulty = 0f
    var variety = 0f
        private set
    var execution = 100f
        private set
    private var progression = 0f
    private var overallImpression = 100f
    var totalScore = 0f
        private set
    var finalScore = 0
        private set
    private var scoreCalculated = false
    
    // Progression et qualité
    var perfectLandings = 0
        private set
    var tricksUsed = mutableSetOf<FreestyleTrick>()
        private set
    private var biggestJump = 0f
    private var progressionPenalty = 0f
    private var repetitionPenalty = 0f
    
    // Effets visuels
    var pisteScroll = 0f           // Défilement de la piste
        private set
    var cameraShake = 0f
        private set
    val snowSpray = mutableListOf<SnowParticle>()
    val speedLines = mutableListOf<SpeedLine>()

    private lateinit var tournamentData: TournamentData
    private var eventIndex: Int = 0
    private var numberOfPlayers: Int = 1
    private var currentPlayerIndex: Int = 0
    var practiceMode: Boolean = false
        private set

    // Image du skieur
    var skierBitmap: Bitmap? = null
        private set

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

        // Charger les images
        loadSkierImage()
        loadPreparationImage()

        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        statusText = TextView(this).apply {
            text = "🎿 SKI FREESTYLE - ${tournamentData.playerNames[currentPlayerIndex]}"
            setTextColor(Color.WHITE)
            textSize = 22f
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(25, 20, 25, 20)
        }

        gameView = SkiFreestyleView(this, this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        
        initializeGame()
    }

    private fun loadSkierImage() {
        try {
            skierBitmap = BitmapFactory.decodeResource(resources, R.drawable.skifreestyle)
        } catch (e: Exception) {
            // Garder null si l'image n'existe pas, on utilisera le dessin par défaut
            e.printStackTrace()
        }
    }
    
    // Variable pour l'image de préparation
    var preparationBitmap: Bitmap? = null
        private set
    
    private fun loadPreparationImage() {
        try {
            // Tu as pas montré skifreestyle_preparation.png mais je suppose qu'elle existe
            preparationBitmap = BitmapFactory.decodeResource(resources, R.drawable.skifreestyle_preparation)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
            
            currentDistance += 200f + i * 30f // Espacement plus grand
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
                    approachingKicker.distance - distanceTraveled in 50f..120f
        
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
        
        // Vitesse de défilement de la piste basée sur la vitesse du skieur (RÉDUITE)
        pisteScrollSpeed = speed * 1.2f // Réduit de 3f à 1.2f
        
        // Effets de vitesse
        if (speed > 20f) {
            generateSpeedEffects()
        }
    }
    
    fun updateAirPhysics() {
        // Physique réaliste : trajectoire parabolique MAIS PLUS LENTE
        val gravity = 0.2f // RÉDUIT de 0.4f à 0.2f pour rester plus longtemps en l'air
        
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
        
        // Atterrissage - retour à position fixe (PLUS BAS pour atterrir plus tard)
        if (skierY >= 0.85f) { // Était 0.75f
            landFromJump()
        }
    }
    
    private fun calculateLandingHeight(): Float {
        // Hauteur de la piste selon la position X (pente du kicker)
        return 0.9f - abs(skierX - 0.5f) * 0.1f
    }
    
    fun getApproachingKicker(): Kicker? {
        return if (nextKickerIndex < kickers.size) {
            val kicker = kickers[nextKickerIndex]
            val distance = kicker.distance - distanceTraveled
            if (distance > 0f && distance < 250f) kicker else null
        } else null
    }
    
    private fun handleKickerSystem() {
        val approachingKicker = getApproachingKicker()
        
        if (approachingKicker != null) {
            val kickerDistance = approachingKicker.distance - distanceTraveled
            val distanceToKicker = abs(skierX - approachingKicker.x)
            
            // Hit du kicker
            if (kickerDistance < 8f && !approachingKicker.hit && distanceToKicker < 0.15f) {
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
        
        // SAUTS VRAIMENT LONGS - JE TRIPLE ENCORE!
        val kickerPower = when (kicker.size) {
            KickerSize.SMALL -> 0.60f      // Était 0.20f
            KickerSize.MEDIUM -> 0.90f     // Était 0.35f  
            KickerSize.LARGE -> 1.20f      // Était 0.50f
        }
        
        // Plus de vitesse = trajectoire plus haute et longue
        val speedFactor = (speed / 30f).coerceIn(0.5f, 1.5f)
        verticalVelocity = -(kickerPower * speedFactor + pumpEnergy * 0.25f) // Plus de power encore
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
                if (trickSetupTime > 0.2f) { // Augmenté de 0.1f à 0.2f
                    detectTrickInitiation()
                }
            }
            TrickPhase.SETUP -> {
                // Phase de setup du trick
                if (trickSetupTime > 0.5f) { // Augmenté de 0.3f à 0.5f
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
                trickProgress += abs(tiltZ) * 0.01f // Réduit de 0.02f à 0.01f
                trickRotation += tiltZ * 1f // Réduit de 2f à 1f
                trickProgress = (abs(trickRotation) / 360f).coerceIn(0f, 3f)
            }
            FreestyleTrick.BACKFLIP -> {
                trickProgress += abs(tiltY) * 0.008f // Réduit de 0.015f à 0.008f
                trickRotation += tiltY * 0.8f // Réduit de 1.5f à 0.8f
                trickProgress = (abs(trickRotation) / 360f).coerceIn(0f, 2f)
            }
            FreestyleTrick.INDY_GRAB -> {
                if (abs(accelZ) > 6f) {
                    trickHoldTime += 0.016f
                    trickProgress = (trickHoldTime / 1.2f).coerceIn(0f, 1f) // Augmenté de 0.8f à 1.2f
                } else {
                    trickProgress *= 0.98f // Moins de decay (était 0.95f)
                }
            }
            FreestyleTrick.SPIN_GRAB -> {
                trickRotation += abs(tiltZ) * 0.8f // Réduit de 1.5f à 0.8f
                if (abs(accelZ) > 6f) {
                    trickHoldTime += 0.016f
                }
                val spinScore = abs(trickRotation) / 360f
                val grabScore = trickHoldTime / 1f // Augmenté de 0.6f à 1f
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
        distanceTraveled += speed * 0.02f // Réduit de 0.03f à 0.02f
        pisteScroll += pisteScrollSpeed * 0.05f // Réduit de 0.08f à 0.05f
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

    fun getCountryFlag(country: String): String {
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

    fun getTournamentData() = tournamentData
    fun getCurrentPlayerIndex() = currentPlayerIndex
}
