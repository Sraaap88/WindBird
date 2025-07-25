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
    private val preparationDuration = 6f // Durée augmentée à 6 secondes
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
    
    // Images pour l'étape de préparation
    private var halfpipePreparationBitmap: Bitmap? = null
    private var countryFlagBitmap: Bitmap? = null
    
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

        gameView = SnowboardHalfpipeView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        
        initializeGame()
    }
    
    fun getCacheKey(bitmap: Bitmap): String {
        return when (bitmap) {
            this.snowLeftSpriteBitmap -> "left_sprite"
            this.snowRightSpriteBitmap -> "right_sprite"
            this.snowLeftLandingBitmap -> "left_landing"
            this.snowRightLandingBitmap -> "right_landing"
            this.snowLeftRotationBitmap -> "left_rotation"
            this.snowRightRotationBitmap -> "right_rotation"
            this.snowLeftGrabBitmap -> "left_grab"
            this.snowRightGrabBitmap -> "right_grab"
            this.snowLeftSpinBitmap -> "left_spin"
            this.snowRightSpinBitmap -> "right_spin"
            else -> "unknown"
        }
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
            
            // Charger l'image de préparation du halfpipe
            halfpipePreparationBitmap = BitmapFactory.decodeResource(resources, R.drawable.halfpipe_preparation)
            
            // Charger le drapeau du pays du joueur
            val playerCountry = getPlayerCountry()
            val flagResourceName = "flag_${playerCountry.lowercase()}"
            val flagResourceId = resources.getIdentifier(flagResourceName, "drawable", packageName)
            if (flagResourceId != 0) {
                countryFlagBitmap = BitmapFactory.decodeResource(resources, flagResourceId)
            }
            
            // Découper intelligemment les frames
            analyzeAndCacheFrames()
            
        } catch (e: Exception) {
            // Les bitmaps resteront null, le fallback sera utilisé
        }
    }
    
    private fun getPlayerCountry(): String {
        // Récupérer le pays du joueur
        return if (practiceMode) {
            "ca" // Canada par défaut
        } else {
            val playerCountry = tournamentData.playerCountries[currentPlayerIndex]
            when (playerCountry.uppercase()) {
                "FRANCE" -> "fr"
                "CANADA" -> "ca"
                "USA", "ÉTATS-UNIS", "ETATS-UNIS" -> "us"
                "ALLEMAGNE", "GERMANY" -> "de"
                "ITALIE", "ITALY" -> "it"
                "SUISSE", "SWITZERLAND" -> "ch"
                "AUTRICHE", "AUSTRIA" -> "at"
                "NORVÈGE", "NORWAY" -> "no"
                "SUÈDE", "SWEDEN" -> "se"
                "FINLANDE", "FINLAND" -> "fi"
                "JAPON", "JAPAN" -> "jp"
                "CORÉE", "KOREA" -> "kr"
                "RUSSIE", "RUSSIA" -> "ru"
                "POLOGNE", "POLAND" -> "pl"
                "SLOVÉNIE", "SLOVENIA" -> "si"
                "RÉPUBLIQUE TCHÈQUE", "CZECH REPUBLIC" -> "cz"
                else -> "ca"
            }
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
        halfpipePreparationBitmap?.recycle()
        countryFlagBitmap?.recycle()
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
