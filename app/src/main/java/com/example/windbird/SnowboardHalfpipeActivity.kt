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

class SnowboardHalfpipeActivity : Activity(), SensorEventListener {

    private lateinit var gameView: SnowboardHalfpipeView
    private lateinit var statusText: TextView

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null
    private var accelerometer: Sensor? = null

    // États du jeu
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Durées des phases
    private val preparationDuration = 5f
    private val rideDuration = 60f  // 1 minute de ride
    private val resultsDuration = 8f
    
    // Variables de physique réaliste du halfpipe
    private var riderPosition = 0.5f      // Position sur le halfpipe (0.0 = gauche max, 1.0 = droite max)
    private var riderHeight = 0.8f        // Hauteur dans le halfpipe (0.8 = fond, 0.2 = coping)
    private var speed = 8f                // Vitesse actuelle (démarrage avec vitesse de base)
    private var momentum = 0f             // Momentum pour les oscillations
    private var pipeDistance = 0f         // Distance parcourue dans le pipe
    private var verticalVelocity = 0f     // Vélocité verticale (gravity)
    private var direction = 1f            // Direction du mouvement (-1 = gauche, 1 = droite)
    
    // État physique du rider avec animations
    private var isInAir = false
    private var airTime = 0f
    private var lastWallHit = 0L
    private var goingLeft = false         // Direction du mouvement
    private var energy = 100f             // Énergie totale (conservation)
    private var currentSide = RiderSide.CENTER // Côté actuel du rider
    private var lastSide = RiderSide.CENTER    // Dernier côté pour savoir d'où il vient
    private var isLanding = false              // En phase d'atterrissage
    private var landingTimer = 0f              // Timer pour l'animation de landing
    
    // Système de pumping réaliste
    private var pumpEnergy = 0f
    private var pumpTiming = 0f           // Qualité du timing de pump (0-1)
    private var pumpCombo = 0
    private var lastPumpTime = 0L
    private var pumpWindow = false        // Fenêtre de pumping optimal
    private var pumpEfficiency = 0f      // Efficacité du pump actuel
    
    // Contrôles gyroscope/accéléromètre
    private var tiltX = 0f    // Inclinaison gauche/droite (balance)
    private var tiltY = 0f    // Inclinaison avant/arrière (pumping)
    private var tiltZ = 0f    // Rotation (spins)
    private var accelX = 0f   // Accélération X (grabs)
    private var accelY = 0f   // Accélération Y 
    private var accelZ = 0f   // Accélération Z (grabs)
    
    // Système de tricks complet avec phases
    private var currentTrick = TrickType.NONE
    private var trickPhase = TrickPhase.NONE
    private var trickProgress = 0f
    private var trickRotation = 0f
    private var trickFlip = 0f
    private var trickGrab = false
    private var tricksCompleted = 0
    private var trickCombo = 0
    private var lastTrickType = TrickType.NONE
    private var trickSetupTime = 0f
    private var landingBalance = 0.5f     // Balance pour landing (0-1)
    
    // Système de scoring réaliste
    private var amplitude = 0f            // Hauteur des airs
    private var technicality = 0f         // Difficulté technique
    private var variety = 0f              // Variété des tricks
    private var flow = 100f               // Fluidité et transitions
    private var style = 100f              // Style et landing quality
    private var consistency = 100f        // Régularité
    private var totalScore = 0f
    private var finalScore = 0
    private var scoreCalculated = false
    
    // Métriques de performance
    private var perfectLandings = 0
    private var maxAirTime = 0f
    private var maxHeight = 0f
    private var trickVariety = mutableSetOf<TrickType>()
    private var speedHistory = mutableListOf<Float>()
    
    // Images du snowboarder avec sprite-sheets
    private var snowLeftSpriteBitmap: Bitmap? = null
    private var snowRightSpriteBitmap: Bitmap? = null
    private var snowLeftLandingBitmap: Bitmap? = null
    private var snowRightLandingBitmap: Bitmap? = null
    private var snowLeftRotationBitmap: Bitmap? = null
    private var snowRightRotationBitmap: Bitmap? = null
    private var snowLeftGrabBitmap: Bitmap? = null
    private var snowRightGrabBitmap: Bitmap? = null
    private var snowLeftSpinBitmap: Bitmap? = null
    private var snowRightSpinBitmap: Bitmap? = null
    
    // Sprite-sheet de la piste
    private var snowTrackSpriteBitmap: Bitmap? = null
    
    // Cache pour les frames découpées intelligemment
    private val snowboarderFrameCache = mutableMapOf<String, List<Rect>>()
    private var trackFrames = mutableListOf<Rect>() // 12 frames de piste (3x4)
    
    // Effets visuels
    private var pipeScroll = 0f           // Défilement de la piste
    private var wallBounceEffect = 0f     // Effet visuel des murs
    private var backgroundPerspective = 0f// Perspective du pipe
    
    // Air awareness et feedback
    private var altimeter = 0f            // Hauteur actuelle au-dessus du pipe
    private var landingZone = 0.5f        // Zone de landing optimale

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

        // Charger les images
        loadSnowboarderImages()

        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        statusText = TextView(this).apply {
            text = "🏂 SNOWBOARD HALFPIPE - ${tournamentData.playerNames[currentPlayerIndex]}"
            setTextColor(Color.WHITE)
            textSize = 22f
            setBackgroundColor(Color.parseColor("#001144"))
            setPadding(25, 20, 25, 20)
        }
        
        private fun getCacheKey(bitmap: Bitmap): String {
            return when (bitmap) {
                this@SnowboardHalfpipeActivity.snowLeftSpriteBitmap -> "left_sprite"
                this@SnowboardHalfpipeActivity.snowRightSpriteBitmap -> "right_sprite"
                this@SnowboardHalfpipeActivity.snowLeftLandingBitmap -> "left_landing"
                this@SnowboardHalfpipeActivity.snowRightLandingBitmap -> "right_landing"
                this@SnowboardHalfpipeActivity.snowLeftRotationBitmap -> "left_rotation"
                this@SnowboardHalfpipeActivity.snowRightRotationBitmap -> "right_rotation"
                this@SnowboardHalfpipeActivity.snowLeftGrabBitmap -> "left_grab"
                this@SnowboardHalfpipeActivity.snowRightGrabBitmap -> "right_grab"
                this@SnowboardHalfpipeActivity.snowLeftSpinBitmap -> "left_spin"
                this@SnowboardHalfpipeActivity.snowRightSpinBitmap -> "right_spin"
                else -> "unknown"
            }
        }

        gameView = SnowboardHalfpipeView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        
        initializeGame()
    }
    
    private fun loadSnowboarderImages() {
        try {
            snowLeftSpriteBitmap = BitmapFactory.decodeResource(resources, R.drawable.snow_left_sprite)
            snowRightSpriteBitmap = BitmapFactory.decodeResource(resources, R.drawable.snow_right_sprite)
            snowLeftLandingBitmap = BitmapFactory.decodeResource(resources, R.drawable.snow_left_landing)
            snowRightLandingBitmap = BitmapFactory.decodeResource(resources, R.drawable.snow_right_landing)
            snowLeftRotationBitmap = BitmapFactory.decodeResource(resources, R.drawable.snow_left_rotation)
            snowRightRotationBitmap = BitmapFactory.decodeResource(resources, R.drawable.snow_right_rotation)
            snowLeftGrabBitmap = BitmapFactory.decodeResource(resources, R.drawable.snow_left_grab)
            snowRightGrabBitmap = BitmapFactory.decodeResource(resources, R.drawable.snow_right_grab)
            snowLeftSpinBitmap = BitmapFactory.decodeResource(resources, R.drawable.snow_left_spin)
            snowRightSpinBitmap = BitmapFactory.decodeResource(resources, R.drawable.snow_right_spin)
            
            // Charger le sprite-sheet de la piste
            snowTrackSpriteBitmap = BitmapFactory.decodeResource(resources, R.drawable.snow_track_sprite)
            
            // Découper intelligemment les frames
            analyzeAndCacheFrames()
            
        } catch (e: Exception) {
            // Les bitmaps resteront null, le fallback sera utilisé
        }
    }
    
    private fun analyzeAndCacheFrames() {
        // Découpage intelligent des snowboarders (5 frames par sprite-sheet)
        snowLeftSpriteBitmap?.let { 
            snowboarderFrameCache["left_sprite"] = analyzeFrameBounds(it, 5, 1)
        }
        snowRightSpriteBitmap?.let { 
            snowboarderFrameCache["right_sprite"] = analyzeFrameBounds(it, 5, 1)
        }
        snowLeftLandingBitmap?.let { 
            snowboarderFrameCache["left_landing"] = analyzeFrameBounds(it, 5, 1)
        }
        snowRightLandingBitmap?.let { 
            snowboarderFrameCache["right_landing"] = analyzeFrameBounds(it, 5, 1)
        }
        snowLeftRotationBitmap?.let { 
            snowboarderFrameCache["left_rotation"] = analyzeFrameBounds(it, 5, 1)
        }
        snowRightRotationBitmap?.let { 
            snowboarderFrameCache["right_rotation"] = analyzeFrameBounds(it, 5, 1)
        }
        snowLeftGrabBitmap?.let { 
            snowboarderFrameCache["left_grab"] = analyzeFrameBounds(it, 5, 1)
        }
        snowRightGrabBitmap?.let { 
            snowboarderFrameCache["right_grab"] = analyzeFrameBounds(it, 5, 1)
        }
        snowLeftSpinBitmap?.let { 
            snowboarderFrameCache["left_spin"] = analyzeFrameBounds(it, 5, 1)
        }
        snowRightSpinBitmap?.let { 
            snowboarderFrameCache["right_spin"] = analyzeFrameBounds(it, 5, 1)
        }
        
        // Découpage de la piste (3x4 = 12 frames)
        snowTrackSpriteBitmap?.let {
            trackFrames = analyzeFrameBounds(it, 3, 4).toMutableList()
        }
    }
    
    private fun analyzeFrameBounds(bitmap: Bitmap, cols: Int, rows: Int): List<Rect> {
        val frames = mutableListOf<Rect>()
        val frameWidth = bitmap.width / cols
        val frameHeight = bitmap.height / rows
        
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val left = col * frameWidth
                val top = row * frameHeight
                
                // Analyser les pixels pour trouver les vraies limites
                val bounds = findActualBounds(bitmap, left, top, frameWidth, frameHeight)
                frames.add(bounds)
            }
        }
        
        return frames
    }
    
    private fun findActualBounds(bitmap: Bitmap, startX: Int, startY: Int, maxWidth: Int, maxHeight: Int): Rect {
        var left = startX + maxWidth
        var top = startY + maxHeight
        var right = startX
        var bottom = startY
        
        // Scanner les pixels pour trouver les limites réelles (pixels non-transparents)
        for (y in startY until (startY + maxHeight)) {
            for (x in startX until (startX + maxWidth)) {
                if (x < bitmap.width && y < bitmap.height) {
                    val pixel = bitmap.getPixel(x, y)
                    val alpha = (pixel shr 24) and 0xFF
                    
                    if (alpha > 10) { // Pixel non-transparent
                        left = minOf(left, x)
                        top = minOf(top, y)
                        right = maxOf(right, x)
                        bottom = maxOf(bottom, y)
                    }
                }
            }
        }
        
        // Si aucun pixel trouvé, utiliser la frame complète
        if (left > right) {
            left = startX
            top = startY
            right = startX + maxWidth
            bottom = startY + maxHeight
        }
        
        return Rect(left, top, right + 1, bottom + 1)
    }
    
    private fun initializeGame() {
        gameState = GameState.PREPARATION
        phaseTimer = 0f
        riderPosition = 0.5f
        riderHeight = 0.8f
        speed = 8f
        momentum = 0f
        pipeDistance = 0f
        verticalVelocity = 0f
        
        isInAir = false
        airTime = 0f
        lastWallHit = 0L
        goingLeft = false
        energy = 100f
        
        pumpEnergy = 0f
        pumpTiming = 0f
        pumpCombo = 0
        lastPumpTime = 0L
        pumpWindow = false
        pumpEfficiency = 0f
        
        tiltX = 0f
        tiltY = 0f
        tiltZ = 0f
        accelX = 0f
        accelY = 0f
        accelZ = 0f
        
        currentTrick = TrickType.NONE
        trickPhase = TrickPhase.NONE
        trickProgress = 0f
        trickRotation = 0f
        trickFlip = 0f
        trickGrab = false
        tricksCompleted = 0
        trickCombo = 0
        lastTrickType = TrickType.NONE
        trickSetupTime = 0f
        landingBalance = 0.5f
        
        amplitude = 0f
        technicality = 0f
        variety = 0f
        flow = 100f
        style = 100f
        consistency = 100f
        totalScore = 0f
        finalScore = 0
        scoreCalculated = false
        
        perfectLandings = 0
        maxAirTime = 0f
        maxHeight = 0f
        trickVariety.clear()
        speedHistory.clear()
        
        pipeScroll = 0f
        wallBounceEffect = 0f
        backgroundPerspective = 0f
        altimeter = 0f
        landingZone = 0.5f
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
                // Filtrage du bruit
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

        phaseTimer += 0.016f // ~60 FPS

        when (gameState) {
            GameState.PREPARATION -> handlePreparation()
            GameState.RIDING -> handleRiding()
            GameState.RESULTS -> handleResults()
            GameState.FINISHED -> {}
        }

        updateEffects()
        updateStatus()
        gameView.invalidate()
    }
    
    private fun handlePreparation() {
        if (phaseTimer >= preparationDuration) {
            gameState = GameState.RIDING
            phaseTimer = 0f
            // Démarrage avec une poussée initiale
            speed = 12f
            momentum = 5f
        }
    }
    
    private fun handleRiding() {
        // Physique réaliste du halfpipe
        updateHalfpipePhysics()
        
        // Système de pumping
        handlePumping()
        
        // Mouvement du rider
        updateRiderMovement()
        
        // Système de tricks avec phases
        handleTrickSystem()
        
        // Mise à jour des métriques
        updatePerformanceMetrics()
        
        // Conservation d'énergie et friction
        applyPhysicsConstraints()
        
        // Fin de run
        if (phaseTimer >= rideDuration) {
            calculateFinalScore()
            gameState = GameState.RESULTS
            phaseTimer = 0f
        }
    }
    
    private fun updateHalfpipePhysics() {
        // Mouvement de pendule dans le halfpipe
        if (!isInAir) {
            // Calcul de la hauteur selon la position (forme en U)
            riderHeight = 0.8f - abs(riderPosition - 0.5f) * 0.6f // Plus on s'éloigne du centre, plus on monte
            
            // Mouvement oscillatoire de gauche à droite
            momentum += direction * 0.008f * speed / 15f
            riderPosition += momentum * 0.02f
            
            // Mise à jour du côté actuel selon la position
            val newSide = when {
                riderPosition < 0.3f -> RiderSide.LEFT
                riderPosition > 0.7f -> RiderSide.RIGHT
                else -> RiderSide.CENTER
            }
            
            if (newSide != currentSide) {
                lastSide = currentSide
                currentSide = newSide
            }
            
            // Rebond sur les bords avec changement de direction
            if (riderPosition <= 0.1f) {
                riderPosition = 0.1f
                direction = 1f // Va vers la droite
                if (speed > 12f && momentum < -0.3f) {
                    takeoff() // Envol du mur gauche
                }
            } else if (riderPosition >= 0.9f) {
                riderPosition = 0.9f
                direction = -1f // Va vers la gauche
                if (speed > 12f && momentum > 0.3f) {
                    takeoff() // Envol du mur droit
                }
            }
            
            // Friction naturelle
            momentum *= 0.98f
            
            // Gestion du timer de landing
            if (isLanding) {
                landingTimer += 0.016f
                if (landingTimer > 1f) { // Landing animation dure 1 seconde
                    isLanding = false
                    landingTimer = 0f
                }
            }
            
        } else {
            // En l'air : gravité pure
            verticalVelocity += 0.015f // Gravité vers le bas
            riderHeight += verticalVelocity
            airTime += 0.016f
            altimeter = max(0f, (0.8f - riderHeight) * 100f) // Hauteur au-dessus du pipe
            
            // Mouvement horizontal continue en l'air
            riderPosition += momentum * 0.01f
            riderPosition = riderPosition.coerceIn(0.05f, 0.95f)
            
            // Atterrissage sur la rampe
            val expectedHeight = 0.8f - abs(riderPosition - 0.5f) * 0.6f
            if (riderHeight >= expectedHeight) {
                landTrick()
            }
        }
        
        // Mise à jour distance parcourue pour progression
        pipeDistance += speed * 0.016f
        pipeScroll = pipeDistance * 0.05f
    }
    
    private fun handlePumping() {
        val currentTime = System.currentTimeMillis()
        
        // Calcul de la fenêtre de pumping optimal
        val pipeBottomZone = riderHeight > 0.75f && riderHeight < 0.85f
        val transitionZone = abs(riderPosition - 0.5f) > 0.2f
        pumpWindow = pipeBottomZone && transitionZone
        
        // Détection du mouvement de pump (avant)
        if (tiltY < -0.4f && currentTime - lastPumpTime > 200L) {
            if (pumpWindow) {
                // Pump parfait !
                pumpEfficiency = 1f
                pumpTiming = 1f
                speed += 3f
                pumpCombo++
                flow += 2f
                
            } else {
                // Pump mal timé
                pumpEfficiency = 0.3f
                pumpTiming = 0.3f
                speed += 0.5f
                pumpCombo = 0
                flow -= 1f
            }
            
            lastPumpTime = currentTime
            pumpEnergy = pumpEfficiency
        }
        
        // Dégradation du pump
        pumpEnergy *= 0.95f
        pumpTiming *= 0.98f
    }
    
    private fun updateRiderMovement() {
        // Contrôle horizontal (balance)
        val horizontalInput = tiltX * 0.5f
        momentum += horizontalInput * 0.008f
        
        // Amortissement du momentum
        momentum *= 0.98f
        
        // Application du mouvement
        riderPosition += momentum * 0.01f
        riderPosition = riderPosition.coerceIn(0.05f, 0.95f)
        
        // Détection des murs et envol
        val currentTime = System.currentTimeMillis()
        val wallThreshold = 0.15f
        
        if ((riderPosition <= wallThreshold || riderPosition >= 1f - wallThreshold) 
            && !isInAir && currentTime - lastWallHit > 800L) {
            
            takeoff()
            lastWallHit = currentTime
        }
        
        // Direction du mouvement
        goingLeft = momentum < 0f
    }
    
    private fun takeoff() {
        isInAir = true
        verticalVelocity = -(speed * 0.04f + pumpEnergy * 0.03f) // Vitesse vers le haut
        airTime = 0f
        wallBounceEffect = 0.5f
        
        // Conserver le momentum horizontal
        // momentum reste inchangé pour continuer le mouvement en l'air
        
        // Métriques d'amplitude
        amplitude = max(amplitude, abs(momentum) * speed * 0.1f)
        
        // Préparation pour tricks
        trickPhase = TrickPhase.TAKEOFF
        trickSetupTime = 0f
    }
    
    private fun handleTrickSystem() {
        if (!isInAir) {
            currentTrick = TrickType.NONE
            trickPhase = TrickPhase.NONE
            return
        }
        
        trickSetupTime += 0.016f
        
        when (trickPhase) {
            TrickPhase.TAKEOFF -> {
                // Phase de setup (courte fenêtre pour initier)
                if (trickSetupTime > 0.1f) {
                    detectTrickInitiation()
                }
            }
            TrickPhase.SETUP -> {
                // Continuer le setup du trick
                continueTrickSetup()
            }
            TrickPhase.EXECUTION -> {
                // Exécution du trick
                executeTrick()
            }
            TrickPhase.LANDING -> {
                // Préparation du landing
                prepareLanding()
            }
            else -> {}
        }
    }
    
    private fun detectTrickInitiation() {
        val rotationThreshold = 1.0f
        val flipThreshold = 1.2f
        val grabThreshold = 8f
        
        when {
            abs(tiltZ) > rotationThreshold && currentTrick == TrickType.NONE -> {
                initiateTrick(TrickType.SPIN)
            }
            abs(tiltY) > flipThreshold && currentTrick == TrickType.NONE -> {
                initiateTrick(TrickType.FLIP)
            }
            abs(accelZ) > grabThreshold && currentTrick == TrickType.NONE -> {
                initiateTrick(TrickType.GRAB)
            }
            abs(tiltZ) > rotationThreshold && abs(tiltY) > flipThreshold -> {
                initiateTrick(TrickType.COMBO)
            }
        }
    }
    
    private fun initiateTrick(type: TrickType) {
        currentTrick = type
        trickPhase = TrickPhase.SETUP
        trickProgress = 0f
        trickRotation = 0f
        trickFlip = 0f
        trickGrab = false
        
        // Difficulté technique
        val difficulty = when (type) {
            TrickType.SPIN -> 1f
            TrickType.FLIP -> 2f
            TrickType.GRAB -> 1.5f
            TrickType.COMBO -> 3f
            else -> 0f
        }
        technicality += difficulty
    }
    
    private fun continueTrickSetup() {
        // Fenêtre de setup pour construire le trick
        if (trickSetupTime > 0.3f) {
            trickPhase = TrickPhase.EXECUTION
        }
    }
    
    private fun executeTrick() {
        when (currentTrick) {
            TrickType.SPIN -> {
                trickRotation += abs(tiltZ) * 0.02f
                trickProgress = (trickRotation / 360f).coerceIn(0f, 3f) // Max 1080°
            }
            TrickType.FLIP -> {
                trickFlip += abs(tiltY) * 0.015f
                trickProgress = (trickFlip / 180f).coerceIn(0f, 2f) // Max double flip
            }
            TrickType.GRAB -> {
                if (abs(accelZ) > 6f) trickGrab = true
                trickProgress = if (trickGrab) min(1f, trickProgress + 0.03f) else trickProgress * 0.95f
            }
            TrickType.COMBO -> {
                trickRotation += abs(tiltZ) * 0.015f
                trickFlip += abs(tiltY) * 0.01f
                if (abs(accelZ) > 6f) trickGrab = true
                trickProgress = ((trickRotation + trickFlip) / 400f + if (trickGrab) 0.3f else 0f).coerceIn(0f, 2f)
            }
            else -> {}
        }
        
        // Préparation landing si temps en l'air suffisant
        if (airTime > 0.5f && verticalVelocity < 0f) {
            trickPhase = TrickPhase.LANDING
        }
    }
    
    private fun prepareLanding() {
        // Balance pour le landing
        landingBalance = 0.5f + tiltX * 0.1f
        landingBalance = landingBalance.coerceIn(0f, 1f)
    }
    
    private fun landTrick() {
        isInAir = false
        airTime = 0f
        verticalVelocity = 0f
        
        // Déclencher l'animation de landing selon le côté
        isLanding = true
        landingTimer = 0f
        
        // Remettre à la bonne hauteur sur la rampe
        riderHeight = 0.8f - abs(riderPosition - 0.5f) * 0.6f
        maxAirTime = max(maxAirTime, airTime)
        
        if (currentTrick != TrickType.NONE && trickProgress > 0.3f) {
            // Trick réussi !
            val trickScore = calculateTrickScore()
            totalScore += trickScore
            tricksCompleted++
            trickVariety.add(currentTrick)
            
            // Système de combo pour variété
            if (lastTrickType != currentTrick) {
                trickCombo++
                variety += 2f
            } else {
                variety -= 1f // Pénalité répétition
            }
            
            // Quality du landing basée sur l'équilibre
            val landingQuality = 1f - abs(tiltX) * 0.5f - abs(tiltY) * 0.3f
            if (landingQuality > 0.8f) {
                perfectLandings++
                style += 3f
            } else if (landingQuality > 0.5f) {
                style += 1f
            } else {
                style -= 2f // Mauvais landing
                speed *= 0.9f // Perte de vitesse
            }
            
            lastTrickType = currentTrick
            
        } else if (currentTrick != TrickType.NONE) {
            // Trick raté
            style -= 3f
            flow -= 2f
            trickCombo = 0
            speed *= 0.85f
        }
        
        currentTrick = TrickType.NONE
        trickPhase = TrickPhase.NONE
        trickProgress = 0f
    }
    
    private fun calculateTrickScore(): Float {
        val baseScore = when (currentTrick) {
            TrickType.SPIN -> when {
                trickRotation >= 1080f -> 50f
                trickRotation >= 720f -> 35f
                trickRotation >= 540f -> 25f
                trickRotation >= 360f -> 15f
                else -> 8f
            }
            TrickType.FLIP -> when {
                trickFlip >= 360f -> 60f // Double flip
                trickFlip >= 180f -> 30f
                else -> 10f
            }
            TrickType.GRAB -> 20f * trickProgress
            TrickType.COMBO -> 40f * trickProgress
            else -> 0f
        }
        
        val airTimeBonus = airTime * 5f
        val heightBonus = altimeter * 0.5f
        val comboBonus = trickCombo * 3f
        
        return baseScore + airTimeBonus + heightBonus + comboBonus
    }
    
    private fun updatePerformanceMetrics() {
        // Flow basé sur la fluidité des transitions
        val speedVariation = if (speedHistory.size > 10) {
            val recent = speedHistory.takeLast(10)
            recent.maxOrNull()!! - recent.minOrNull()!!
        } else 0f
        
        if (speedVariation < 3f) flow += 0.1f else flow -= 0.1f
        
        // Consistency basée sur la régularité
        if (speed > 10f && !isInAir) consistency += 0.05f
        if (speed < 6f) consistency -= 0.1f
        
        // Contraintes
        flow = flow.coerceIn(60f, 120f)
        style = style.coerceIn(60f, 120f)
        consistency = consistency.coerceIn(60f, 120f)
    }
    
    private fun applyPhysicsConstraints() {
        // Friction naturelle
        speed *= 0.998f
        
        // Vitesse minimale et maximale
        speed = speed.coerceIn(4f, 30f)
        
        // Conservation d'énergie
        val totalEnergy = speed + (1f - riderHeight) * 20f
        energy = totalEnergy * 0.99f // Perte d'énergie graduelle
    }
    
    private fun updateEffects() {
        wallBounceEffect = max(0f, wallBounceEffect - 0.02f)
        backgroundPerspective += speed * 0.001f
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
            val amplitudePoints = (amplitude * 2).toInt()
            val tricksPoints = totalScore.toInt()
            val varietyPoints = trickVariety.size * 15
            val flowPoints = ((flow - 100f) * 1.5f).toInt()
            val stylePoints = ((style - 100f) * 2f).toInt()
            val consistencyPoints = ((consistency - 100f) * 1f).toInt()
            val perfectLandingBonus = perfectLandings * 10
            
            finalScore = maxOf(80, 
                amplitudePoints + tricksPoints + varietyPoints + 
                flowPoints + stylePoints + consistencyPoints + perfectLandingBonus
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
                val aiScore = (120..220).random()
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

    override fun onDestroy() {
        super.onDestroy()
        // Libérer les bitmaps
        snowLeftSpriteBitmap?.recycle()
        snowRightSpriteBitmap?.recycle()
        snowLeftLandingBitmap?.recycle()
        snowRightLandingBitmap?.recycle()
        snowLeftRotationBitmap?.recycle()
        snowRightRotationBitmap?.recycle()
        snowLeftGrabBitmap?.recycle()
        snowRightGrabBitmap?.recycle()
        snowLeftSpinBitmap?.recycle()
        snowRightSpinBitmap?.recycle()
        snowTrackSpriteBitmap?.recycle()
    }

    private fun updateStatus() {
        statusText.text = when (gameState) {
            GameState.PREPARATION -> "🏂 ${tournamentData.playerNames[currentPlayerIndex]} | Préparation... ${(preparationDuration - phaseTimer).toInt() + 1}s"
            GameState.RIDING -> {
                val trickText = if (currentTrick != TrickType.NONE) " | ${currentTrick.displayName}" else ""
                val speedText = "Speed: ${speed.toInt()}km/h"
                "🏂 ${tournamentData.playerNames[currentPlayerIndex]} | $speedText | Tricks: $tricksCompleted$trickText"
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

    inner class SnowboardHalfpipeView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        // Cache des objets réutilisables
        private val reusableRectF = RectF()
        private val reusablePath = Path()
        
        // Images du snowboarder
        private var snowFrontBitmap: Bitmap? = null
        private var snowTrickBitmap: Bitmap? = null
        
        init {
            loadBitmaps()
        }
        
        private fun loadBitmaps() {
            try {
                snowFrontBitmap = BitmapFactory.decodeResource(resources, R.drawable.snow_front)
                snowTrickBitmap = BitmapFactory.decodeResource(resources, R.drawable.snow_trick)
            } catch (e: Exception) {
                // Les bitmaps resteront null, le fallback sera utilisé
            }
        }
        
        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            snowFrontBitmap?.recycle()
            snowTrickBitmap?.recycle()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width
            val h = height
            
            when (gameState) {
                GameState.PREPARATION -> drawPreparation(canvas, w, h)
                GameState.RIDING -> drawRiding(canvas, w, h)
                GameState.RESULTS -> drawResults(canvas, w, h)
                GameState.FINISHED -> drawResults(canvas, w, h)
            }
        }
        
        private fun drawPreparation(canvas: Canvas, w: Int, h: Int) {
            // Fond dégradé ciel
            val skyGradient = LinearGradient(0f, 0f, 0f, h.toFloat(),
                Color.parseColor("#87CEEB"), Color.parseColor("#E0F6FF"), Shader.TileMode.CLAMP)
            paint.shader = skyGradient
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
            
            // Vue de dessus du halfpipe (perspective)
            drawHalfpipePerspective(canvas, w, h)
            
            // Drapeau du pays
            val playerCountry = if (practiceMode) "CANADA" else tournamentData.playerCountries[currentPlayerIndex]
            val flagText = getCountryFlag(playerCountry)
            
            paint.color = Color.parseColor("#FFFFFF")
            paint.style = Paint.Style.FILL
            reusableRectF.set(50f, 50f, 250f, 170f)
            canvas.drawRoundRect(reusableRectF, 15f, 15f, paint)
            
            paint.color = Color.parseColor("#001144")
            paint.textSize = 60f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(flagText, 150f, 130f, paint)
            
            paint.textSize = 20f
            canvas.drawText(playerCountry.uppercase(), 150f, 160f, paint)
            
            // Titre de l'épreuve
            paint.color = Color.parseColor("#001144")
            paint.textSize = 48f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🏂 SNOWBOARD HALFPIPE 🏂", w/2f, h * 0.15f, paint)
            
            // Timer de préparation
            val countdown = (preparationDuration - phaseTimer).toInt() + 1
            paint.textSize = 80f
            paint.color = Color.parseColor("#FF0000")
            canvas.drawText("${countdown}", w/2f, h * 0.7f, paint)
            
            paint.textSize = 32f
            paint.color = Color.parseColor("#0066CC")
            canvas.drawText("Préparation du run...", w/2f, h * 0.8f, paint)
            
            // Instructions
            paint.textSize = 24f
            paint.color = Color.parseColor("#333333")
            canvas.drawText("📱 Inclinez vers l'avant pour pomper", w/2f, h * 0.85f, paint)
            canvas.drawText("📱 Mouvements en l'air = tricks", w/2f, h * 0.9f, paint)
        }
        
        private fun drawHalfpipePerspective(canvas: Canvas, w: Int, h: Int) {
            // Vue en perspective du halfpipe depuis le haut
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            
            // Halfpipe en perspective (forme d'ellipse allongée)
            reusableRectF.set(w * 0.2f, h * 0.3f, w * 0.8f, h * 0.6f)
            canvas.drawOval(reusableRectF, paint)
            
            // Bords du halfpipe
            paint.color = Color.parseColor("#CCCCCC")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 8f
            canvas.drawOval(reusableRectF, paint)
            
            // Lignes de perspective
            paint.strokeWidth = 3f
            for (i in 1..4) {
                val y = h * (0.3f + i * 0.075f)
                canvas.drawLine(w * 0.25f, y, w * 0.75f, y, paint)
            }
            
            paint.style = Paint.Style.FILL
        }
        
        private fun drawRiding(canvas: Canvas, w: Int, h: Int) {
            // Vue depuis l'intérieur du halfpipe
            drawHalfpipeInterior(canvas, w, h)
            
            // Snowboarder vu de derrière
            drawSnowboarderFromBehind(canvas, w, h)
            
            // Interface de jeu
            drawGameInterface(canvas, w, h)
            
            // Barre de rythme de pumping
            drawPumpRhythmBar(canvas, w, h)
            
            // Altimètre si en l'air
            if (isInAir) {
                drawAltimeter(canvas, w, h)
            }
        }
        
        private fun drawHalfpipeInterior(canvas: Canvas, w: Int, h: Int) {
            // Décor hivernal en arrière-plan
            drawWinterBackground(canvas, w, h)
            
            // Piste animée avec le sprite-sheet
            drawAnimatedTrack(canvas, w, h)
        }
        
        private fun drawWinterBackground(canvas: Canvas, w: Int, h: Int) {
            // Ciel hivernal dégradé
            val skyGradient = LinearGradient(0f, 0f, 0f, h * 0.6f,
                Color.parseColor("#E6F3FF"), Color.parseColor("#B8E0FF"), Shader.TileMode.CLAMP)
            paint.shader = skyGradient
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.6f, paint)
            paint.shader = null
            
            // Montagnes enneigées en arrière-plan
            paint.color = Color.parseColor("#F0F8FF")
            val mountainPath = Path()
            mountainPath.moveTo(0f, h * 0.3f)
            mountainPath.lineTo(w * 0.15f, h * 0.1f)
            mountainPath.lineTo(w * 0.3f, h * 0.25f)
            mountainPath.lineTo(w * 0.5f, h * 0.05f)
            mountainPath.lineTo(w * 0.7f, h * 0.2f)
            mountainPath.lineTo(w * 0.85f, h * 0.08f)
            mountainPath.lineTo(w.toFloat(), h * 0.25f)
            mountainPath.lineTo(w.toFloat(), h * 0.6f)
            mountainPath.lineTo(0f, h * 0.6f)
            mountainPath.close()
            canvas.drawPath(mountainPath, paint)
            
            // Sapins sur les côtés
            drawWinterTrees(canvas, w, h)
            
            // Flocons de neige qui tombent
            drawSnowflakes(canvas, w, h)
        }
        
        private fun drawWinterTrees(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#2D5016") // Vert sapin foncé
            
            // Sapins à gauche
            for (i in 0..3) {
                val treeX = w * (0.05f + i * 0.08f)
                val treeY = h * (0.35f + i * 0.1f)
                val treeSize = 30f + i * 10f
                
                // Tronc
                paint.color = Color.parseColor("#8B4513")
                canvas.drawRect(treeX - 5f, treeY, treeX + 5f, treeY + treeSize * 0.3f, paint)
                
                // Branches (3 étages)
                paint.color = Color.parseColor("#2D5016")
                for (j in 0..2) {
                    val branchY = treeY - j * treeSize * 0.2f
                    val branchSize = treeSize * (1f - j * 0.2f)
                    val trianglePath = Path()
                    trianglePath.moveTo(treeX, branchY - branchSize * 0.4f)
                    trianglePath.lineTo(treeX - branchSize * 0.5f, branchY)
                    trianglePath.lineTo(treeX + branchSize * 0.5f, branchY)
                    trianglePath.close()
                    canvas.drawPath(trianglePath, paint)
                }
            }
            
            // Sapins à droite (miroir)
            for (i in 0..3) {
                val treeX = w * (0.95f - i * 0.08f)
                val treeY = h * (0.35f + i * 0.1f)
                val treeSize = 30f + i * 10f
                
                // Tronc
                paint.color = Color.parseColor("#8B4513")
                canvas.drawRect(treeX - 5f, treeY, treeX + 5f, treeY + treeSize * 0.3f, paint)
                
                // Branches
                paint.color = Color.parseColor("#2D5016")
                for (j in 0..2) {
                    val branchY = treeY - j * treeSize * 0.2f
                    val branchSize = treeSize * (1f - j * 0.2f)
                    val trianglePath = Path()
                    trianglePath.moveTo(treeX, branchY - branchSize * 0.4f)
                    trianglePath.lineTo(treeX - branchSize * 0.5f, branchY)
                    trianglePath.lineTo(treeX + branchSize * 0.5f, branchY)
                    trianglePath.close()
                    canvas.drawPath(trianglePath, paint)
                }
            }
        }
        
        private fun drawSnowflakes(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.WHITE
            val currentTime = System.currentTimeMillis()
            
            // Flocons animés qui tombent
            for (i in 0..15) {
                val flakeX = (w * 0.1f + (i * 67f) % (w * 0.8f)) + 
                            sin((currentTime + i * 1000L) / 2000.0) * 30f
                val flakeY = ((currentTime / 50L + i * 100L) % (h * 1.2f)).toFloat() - h * 0.2f
                
                if (flakeY > 0 && flakeY < h) {
                    val flakeSize = 2f + (i % 3) * 1f
                    canvas.drawCircle(flakeX.toFloat(), flakeY, flakeSize, paint)
                }
            }
        }
        
        private fun drawAnimatedTrack(canvas: Canvas, w: Int, h: Int) {
            snowTrackSpriteBitmap?.let { trackBitmap ->
                if (trackFrames.isEmpty()) return
                
                val scrollSpeed = speed * 0.8f
                val scrollOffset = (pipeScroll * scrollSpeed) % (trackFrames.size * 100f)
                
                // Dessiner plusieurs segments de piste pour couvrir l'écran
                val trackWidth = w * 0.5f // La piste prend 50% de la largeur
                val trackX = w * 0.25f    // Centrée horizontalement
                
                for (i in -2..8) { // Segments qui couvrent tout l'écran
                    val segmentY = i * 120f - scrollOffset
                    
                    if (segmentY < h + 120f && segmentY > -120f) {
                        // Sélectionner la frame selon la position (cycle à travers les 12 frames)
                        val frameIndex = ((scrollOffset / 100f + i).toInt() % trackFrames.size + trackFrames.size) % trackFrames.size
                        val frame = trackFrames[frameIndex]
                        
                        // Perspective : plus proche = plus grand
                        val perspective = (segmentY + 120f) / (h + 240f)
                        val scale = 0.3f + perspective * 0.7f // De 30% à 100% de taille
                        
                        val scaledWidth = trackWidth * scale
                        val scaledHeight = 120f * scale
                        val scaledX = trackX + (trackWidth - scaledWidth) / 2f
                        
                        reusableRectF.set(
                            scaledX,
                            segmentY,
                            scaledX + scaledWidth,
                            segmentY + scaledHeight
                        )
                        
                        canvas.drawBitmap(trackBitmap, frame, reusableRectF, paint)
                    }
                }
            }
        }
        
        private fun drawSnowboarderFromBehind(canvas: Canvas, w: Int, h: Int) {
            // Position du snowboarder qui suit la forme du halfpipe
            val riderScreenX = w * riderPosition
            val riderScreenY = h * riderHeight
            
            canvas.save()
            canvas.translate(riderScreenX, riderScreenY)
            
            // Rotation selon la position sur la rampe (inclinaison naturelle)
            val slopeAngle = (riderPosition - 0.5f) * 30f
            canvas.rotate(slopeAngle)
            
            // Sélection de l'image et rotations selon l'état
            val (snowboarderImage, additionalRotation) = getSnowboarderImageAndRotation()
            
            // Appliquer la rotation additionnelle pour les tricks
            canvas.rotate(additionalRotation)
            
            // Échelle selon si en l'air ou pas
            val scale = if (isInAir) 1.1f else 1f
            canvas.scale(scale, scale)
            
            // Dessiner l'image du snowboarder avec découpage intelligent
            snowboarderImage?.let { image ->
                drawSnowboarderFromSpriteSheet(canvas, image, getCacheKey(image))
            } ?: run {
                // Fallback si pas d'image
                paint.color = Color.parseColor("#FF6600")
                canvas.drawCircle(0f, 0f, 16f, paint) // Aussi réduit de 20%
                
                // Snowboard
                paint.color = Color.parseColor("#4400FF")
                canvas.drawRoundRect(-20f, 12f, 20f, 20f, 4f, 4f, paint) // Réduit de 20%
            }
            
            canvas.restore()
            
            // Trail de mouvement selon la direction
            if (abs(momentum) > 0.1f) {
                paint.color = Color.parseColor("#60FFFFFF")
                for (i in 1..3) {
                    val trailX = riderScreenX - momentum * i * 30f
                    val trailAlpha = (255 * (1f - i * 0.3f)).toInt()
                    paint.alpha = trailAlpha
                    canvas.drawCircle(trailX, riderScreenY, (4f - i) * 6f, paint)
                }
                paint.alpha = 255
            }
        }
        
        private fun getSnowboarderImageAndRotation(): Pair<Bitmap?, Float> {
            return when {
                // Phase de landing
                isLanding -> {
                    val sideLanding = if (lastSide == RiderSide.LEFT) RiderSide.LEFT else RiderSide.RIGHT
                    val image = if (sideLanding == RiderSide.LEFT) this@SnowboardHalfpipeActivity.snowLeftLandingBitmap else this@SnowboardHalfpipeActivity.snowRightLandingBitmap
                    Pair(image, 0f)
                }
                
                // Tricks en l'air
                isInAir && currentTrick != TrickType.NONE -> {
                    when (currentTrick) {
                        TrickType.SPIN -> {
                            val side = if (riderPosition < 0.5f) RiderSide.LEFT else RiderSide.RIGHT
                            val image = if (side == RiderSide.LEFT) this@SnowboardHalfpipeActivity.snowLeftRotationBitmap else this@SnowboardHalfpipeActivity.snowRightRotationBitmap
                            Pair(image, trickRotation * 0.8f) // Rotation selon progression
                        }
                        TrickType.FLIP -> {
                            val side = if (riderPosition < 0.5f) RiderSide.LEFT else RiderSide.RIGHT
                            val image = if (side == RiderSide.LEFT) this@SnowboardHalfpipeActivity.snowLeftSpinBitmap else this@SnowboardHalfpipeActivity.snowRightSpinBitmap
                            Pair(image, trickFlip * 0.6f)
                        }
                        TrickType.GRAB -> {
                            val side = if (riderPosition < 0.5f) RiderSide.LEFT else RiderSide.RIGHT
                            val image = if (side == RiderSide.LEFT) this@SnowboardHalfpipeActivity.snowLeftGrabBitmap else this@SnowboardHalfpipeActivity.snowRightGrabBitmap
                            Pair(image, 0f)
                        }
                        TrickType.COMBO -> {
                            val side = if (riderPosition < 0.5f) RiderSide.LEFT else RiderSide.RIGHT
                            val image = if (side == RiderSide.LEFT) this@SnowboardHalfpipeActivity.snowLeftRotationBitmap else this@SnowboardHalfpipeActivity.snowRightRotationBitmap
                            Pair(image, (trickRotation + trickFlip) * 0.5f)
                        }
                        else -> {
                            val side = if (riderPosition < 0.5f) RiderSide.LEFT else RiderSide.RIGHT
                            val image = if (side == RiderSide.LEFT) this@SnowboardHalfpipeActivity.snowLeftSpriteBitmap else this@SnowboardHalfpipeActivity.snowRightSpriteBitmap
                            Pair(image, 0f)
                        }
                    }
                }
                
                // Montée/descente normale
                else -> {
                    when (currentSide) {
                        RiderSide.LEFT -> Pair(this@SnowboardHalfpipeActivity.snowLeftSpriteBitmap, 0f)
                        RiderSide.RIGHT -> Pair(this@SnowboardHalfpipeActivity.snowRightSpriteBitmap, 0f)
                        RiderSide.CENTER -> {
                            // Au centre, utiliser l'image selon d'où on vient
                            when (lastSide) {
                                RiderSide.LEFT -> Pair(this@SnowboardHalfpipeActivity.snowLeftSpriteBitmap, 0f)
                                RiderSide.RIGHT -> Pair(this@SnowboardHalfpipeActivity.snowRightSpriteBitmap, 0f)
                                else -> Pair(this@SnowboardHalfpipeActivity.snowLeftSpriteBitmap, 0f) // Par défaut
                            }
                        }
                    }
                }
            }
        }
        
        private fun drawSnowboarderFromSpriteSheet(canvas: Canvas, spriteSheet: Bitmap, cacheKey: String) {
            val frames = snowboarderFrameCache[cacheKey]
            if (frames == null || frames.isEmpty()) {
                // Fallback : découpage simple si pas de cache
                val frameWidth = spriteSheet.width / 5
                val frameHeight = spriteSheet.height
                val frameIndex = 2 // Frame du milieu par défaut
                
                val srcLeft = frameIndex * frameWidth
                val srcRect = Rect(srcLeft, 0, srcLeft + frameWidth, frameHeight)
                
                val imageScale = 0.48f // Réduit de 20% (0.6f -> 0.48f)
                val imageWidth = frameWidth * imageScale
                val imageHeight = frameHeight * imageScale
                
                reusableRectF.set(
                    -imageWidth/2f,
                    -imageHeight/2f,
                    imageWidth/2f,
                    imageHeight/2f
                )
                
                canvas.drawBitmap(spriteSheet, srcRect, reusableRectF, paint)
                return
            }
            
            // Sélectionner la frame selon l'état avec découpage intelligent
            val frameIndex = when {
                isLanding -> {
                    // Animation de landing (0 à 4)
                    (landingTimer * 8f).toInt().coerceIn(0, frames.size - 1)
                }
                isInAir -> (frames.size / 2).coerceAtMost(frames.size - 1) // Frame du milieu en l'air
                currentSide == RiderSide.CENTER -> (frames.size / 2).coerceAtMost(frames.size - 1) // Frame "tout droit"
                else -> {
                    // Animation de montée selon momentum
                    val animSpeed = abs(momentum) * 8f + 1f
                    ((System.currentTimeMillis() / 200L * animSpeed).toInt() % frames.size)
                }
            }
            
            val frame = frames[frameIndex]
            
            // Taille réduite de 20%
            val imageScale = 0.48f // 0.6f * 0.8f = 0.48f
            val frameWidth = frame.width()
            val frameHeight = frame.height()
            val imageWidth = frameWidth * imageScale
            val imageHeight = frameHeight * imageScale
            
            reusableRectF.set(
                -imageWidth/2f,
                -imageHeight/2f,
                imageWidth/2f,
                imageHeight/2f
            )
            
            canvas.drawBitmap(spriteSheet, frame, reusableRectF, paint)
        }
        
        private fun drawGameInterface(canvas: Canvas, w: Int, h: Int) {
            val baseY = h - 140f
            
            // Score et métriques
            paint.color = Color.parseColor("#001144")
            paint.textSize = 22f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Score: ${totalScore.toInt()}", 20f, baseY, paint)
            canvas.drawText("Tricks: $tricksCompleted", 20f, baseY + 25f, paint)
            canvas.drawText("Speed: ${speed.toInt()} km/h", 20f, baseY + 50f, paint)
            
            // Trick en cours
            if (currentTrick != TrickType.NONE) {
                paint.color = Color.parseColor("#FF6600")
                paint.textSize = 28f
                paint.textAlign = Paint.Align.CENTER
                
                val trickText = when (currentTrick) {
                    TrickType.SPIN -> "${(trickRotation).toInt()}° SPIN"
                    TrickType.FLIP -> "FLIP ${(trickFlip).toInt()}°"
                    TrickType.GRAB -> "GRAB ${(trickProgress * 100).toInt()}%"
                    TrickType.COMBO -> "COMBO ${(trickProgress * 100).toInt()}%"
                    else -> ""
                }
                
                canvas.drawText(trickText, w/2f, baseY, paint)
                
                // Phase du trick
                paint.textSize = 18f
                canvas.drawText("Phase: ${trickPhase.name}", w/2f, baseY + 25f, paint)
            }
            
            // Métriques de performance
            drawPerformanceMeter(canvas, w - 180f, baseY - 20f, 160f, amplitude / 10f, "AMPLITUDE", Color.parseColor("#FF4444"))
            drawPerformanceMeter(canvas, w - 180f, baseY + 5f, 160f, flow / 120f, "FLOW", Color.parseColor("#44AAFF"))
            drawPerformanceMeter(canvas, w - 180f, baseY + 30f, 160f, style / 120f, "STYLE", Color.parseColor("#44FF44"))
            
            // Combo actuel
            if (trickCombo > 1) {
                paint.color = Color.parseColor("#FFD700")
                paint.textSize = 20f
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("🔥 COMBO x$trickCombo", w - 20f, baseY + 55f, paint)
            }
        }
        
        private fun drawPumpRhythmBar(canvas: Canvas, w: Int, h: Int) {
            val barX = 50f
            val barY = 120f
            val barWidth = w * 0.4f
            val barHeight = 30f
            
            // Fond de la barre
            paint.color = Color.parseColor("#333333")
            reusableRectF.set(barX, barY, barX + barWidth, barY + barHeight)
            canvas.drawRect(reusableRectF, paint)
            
            // Zone de pumping optimal
            if (pumpWindow) {
                paint.color = Color.parseColor("#00FF00")
                val optimalWidth = barWidth * 0.3f
                val optimalX = barX + barWidth * 0.35f
                reusableRectF.set(optimalX, barY, optimalX + optimalWidth, barY + barHeight)
                canvas.drawRect(reusableRectF, paint)
            }
            
            // Indicateur de timing actuel
            val currentX = barX + (riderHeight - 0.6f) * barWidth / 0.4f
            paint.color = Color.parseColor("#FFFF00")
            canvas.drawLine(currentX, barY, currentX, barY + barHeight, paint)
            
            // Efficacité du pump
            if (pumpEnergy > 0f) {
                paint.color = Color.parseColor("#FF6600")
                paint.alpha = (pumpEnergy * 255).toInt()
                val pumpWidth = barWidth * pumpEfficiency
                reusableRectF.set(barX, barY, barX + pumpWidth, barY + barHeight)
                canvas.drawRect(reusableRectF, paint)
                paint.alpha = 255
            }
            
            // Label
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("PUMP RHYTHM", barX, barY - 5f, paint)
            
            if (pumpCombo > 0) {
                paint.color = Color.parseColor("#00FF00")
                canvas.drawText("Perfect x$pumpCombo", barX, barY + barHeight + 20f, paint)
            }
        }
        
        private fun drawPerformanceMeter(canvas: Canvas, x: Float, y: Float, width: Float, 
                                       value: Float, label: String, color: Int) {
            // Fond
            paint.color = Color.parseColor("#333333")
            reusableRectF.set(x, y, x + width, y + 15f)
            canvas.drawRect(reusableRectF, paint)
            
            // Barre
            paint.color = color
            val filledWidth = value.coerceIn(0f, 1f) * width
            reusableRectF.set(x, y, x + filledWidth, y + 15f)
            canvas.drawRect(reusableRectF, paint)
            
            // Label
            paint.color = Color.WHITE
            paint.textSize = 12f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("$label: ${(value * 100).toInt()}%", x, y - 3f, paint)
        }
        
        private fun drawAltimeter(canvas: Canvas, w: Int, h: Int) {
            val altX = w - 120f
            val altY = 200f
            
            // Fond de l'altimètre
            paint.color = Color.parseColor("#000000")
            paint.alpha = 180
            reusableRectF.set(altX, altY, altX + 100f, altY + 120f)
            canvas.drawRoundRect(reusableRectF, 10f, 10f, paint)
            paint.alpha = 255
            
            // Hauteur actuelle
            paint.color = Color.parseColor("#00FF00")
            paint.textSize = 24f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${altimeter.toInt()}m", altX + 50f, altY + 40f, paint)
            
            // Air time
            paint.textSize = 16f
            canvas.drawText("Air: ${airTime.toString().take(4)}s", altX + 50f, altY + 60f, paint)
            
            // Barre de hauteur
            val maxBarHeight = 80f
            val currentHeight = (altimeter / 15f).coerceIn(0f, 1f) * maxBarHeight
            
            paint.color = Color.parseColor("#333333")
            reusableRectF.set(altX + 10f, altY + 80f, altX + 30f, altY + 80f + maxBarHeight)
            canvas.drawRect(reusableRectF, paint)
            
            paint.color = Color.parseColor("#00FFFF")
            reusableRectF.set(altX + 10f, altY + 80f + maxBarHeight - currentHeight, altX + 30f, altY + 80f + maxBarHeight)
            canvas.drawRect(reusableRectF, paint)
            
            // Label
            paint.color = Color.WHITE
            paint.textSize = 14f
            canvas.drawText("ALTITUDE", altX + 50f, altY + 100f, paint)
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            // Fond festif avec dégradé
            val resultGradient = LinearGradient(0f, 0f, 0f, h.toFloat(),
                Color.parseColor("#FFD700"), Color.parseColor("#FFF8DC"), Shader.TileMode.CLAMP)
            paint.shader = resultGradient
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
            
            // Score final
            paint.color = Color.parseColor("#001144")
            paint.textSize = 72f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore}", w/2f, h * 0.2f, paint)
            
            paint.textSize = 32f
            canvas.drawText("POINTS", w/2f, h * 0.28f, paint)
            
            // Détails de performance
            paint.color = Color.parseColor("#333333")
            paint.textSize = 24f
            
            val startY = h * 0.4f
            val lineHeight = 35f
            
            canvas.drawText("🏂 Tricks réussis: $tricksCompleted", w/2f, startY, paint)
            canvas.drawText("⭐ Variété: ${trickVariety.size} types", w/2f, startY + lineHeight, paint)
            canvas.drawText("📏 Amplitude max: ${maxHeight.toInt()}m", w/2f, startY + lineHeight * 2, paint)
            canvas.drawText("⏱️ Air time max: ${maxAirTime.toString().take(4)}s", w/2f, startY + lineHeight * 3, paint)
            canvas.drawText("🎯 Landings parfaits: $perfectLandings", w/2f, startY + lineHeight * 4, paint)
            canvas.drawText("🌊 Flow: ${flow.toInt()}%", w/2f, startY + lineHeight * 5, paint)
            canvas.drawText("💎 Style: ${style.toInt()}%", w/2f, startY + lineHeight * 6, paint)
            
            // Message d'encouragement
            val encouragement = when {
                finalScore >= 300 -> "🏆 PERFORMANCE LÉGENDAIRE!"
                finalScore >= 250 -> "🥇 EXCELLENT RUN!"
                finalScore >= 200 -> "🥈 TRÈS BON STYLE!"
                finalScore >= 150 -> "🥉 BIEN JOUÉ!"
                else -> "💪 CONTINUE À T'ENTRAÎNER!"
            }
            
            paint.color = Color.parseColor("#FF6600")
            paint.textSize = 28f
            canvas.drawText(encouragement, w/2f, h * 0.9f, paint)
        }
    }

    enum class TrickType(val displayName: String) {
        NONE(""),
        SPIN("SPIN"),
        FLIP("FLIP"), 
        GRAB("GRAB"),
        COMBO("COMBO")
    }
    
    enum class TrickPhase {
        NONE, TAKEOFF, SETUP, EXECUTION, LANDING
    }
    
    enum class RiderSide {
        LEFT, CENTER, RIGHT
    }

    enum class GameState {
        PREPARATION, RIDING, RESULTS, FINISHED
    }
}
