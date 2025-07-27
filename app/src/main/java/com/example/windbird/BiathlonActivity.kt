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

class BiathlonActivity : Activity(), SensorEventListener {

    private lateinit var gameView: BiathlonView
    private lateinit var statusText: TextView

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null
    private var accelerometer: Sensor? = null

    // Variables de gameplay AMÉLIORÉES avec écrans multiples
    private var distance = 0f
    private val screenDistance = 600f // Distance pour traverser un écran
    private var backgroundOffset = 0f
    private var currentScreen = 0 // 0=PREPARATION, 1-6 (Écran 1, 2, TIR, 4, 5, STATS)
    private var skierX = 0.1f // Position relative du skieur (0.0 à 1.0)
    
    // NOUVEAU - Variables pour la préparation
    private var preparationTimer = 0L
    private var preparationStarted = false
    
    // NOUVEAU - Système de poussées rythmées avec performance ET GLISSE
    private var pushDirection = 0 // -1=gauche, 0=neutre, 1=droite
    private var lastPushTime = 0L
    private var previousPushTime = 0L
    private var rhythmBonus = 1f
    private var pushCount = 0
    
    // NOUVEAU - Variables pour la glisse fluide
    private var currentSpeed = 0f // Vitesse actuelle du skieur
    private var targetSpeed = 0f // Vitesse cible après une poussée
    private var isGliding = false // En train de glisser
    private var lastGlideUpdate = 0L
    
    // NOUVEAU - Variables pour la bande de performance
    private var currentPushQuality = 0f
    private var currentRhythmQuality = 0f
    private var performanceHistory = mutableListOf<Float>()
    private var averageRhythm = 0f
    
    // Sprite animation AMÉLIORÉE avec animation fluide
    private lateinit var spriteSheet: Bitmap
    private lateinit var neutralFrame: Bitmap      // Position neutre
    private lateinit var prepLeftFrame: Bitmap     // Préparation poussée gauche
    private lateinit var pushLeftFrame: Bitmap     // Poussée gauche active
    private lateinit var glideLeftFrame: Bitmap    // Glisse gauche
    private lateinit var prepRightFrame: Bitmap    // Préparation poussée droite
    private lateinit var pushRightFrame: Bitmap    // Poussée droite active
    private lateinit var glideRightFrame: Bitmap   // Glisse droite
    private var happyFrame: Bitmap? = null         // Image pour l'écran final
    private var currentFrame: Bitmap? = null
    private var animationTimer = 0L
    private var animationState = AnimationState.NEUTRAL

    // Variables de tir AMÉLIORÉES
    private var gameState = GameState.SKIING
    private var targetsHit = 0
    private var shotsFired = 0
    private var totalScore = 0
    private var crosshair = PointF(0.5f, 0.4f)
    private val targetPositions = List(5) { PointF(0.15f + it * 0.175f, 0.4f) }
    private val targetHitStatus = BooleanArray(5) { false }
    private val targetScores = IntArray(5) { 0 }

    // Variables pour l'écran final
    private var finalScreenTimer = 0L
    private var autoMovement = false
    private var movementSpeed = 0.02f  // Vitesse variable pour ralentissement

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
        
        loadSpriteSheet()

        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        statusText = TextView(this).apply {
            text = "🎿 ${tournamentData.playerNames[currentPlayerIndex]} | ${getScreenName()} | Distance: 0m"
            setTextColor(Color.WHITE)
            textSize = 18f
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(20, 15, 20, 15)
        // NOUVEAU - Système d'animation fluide
    private fun updateAnimation() {
        val currentTime = System.currentTimeMillis()
        
        when (animationState) {
            AnimationState.NEUTRAL -> {
                currentFrame = neutralFrame
                if (currentSpeed > 0.001f) {
                    // Commencer l'animation de glisse si on bouge
                    animationState = if (pushDirection == -1) AnimationState.GLIDE_LEFT else AnimationState.GLIDE_RIGHT
                    animationTimer = currentTime
                }
            }
            
            AnimationState.PREP_LEFT -> {
                currentFrame = prepLeftFrame
                if (currentTime - animationTimer > 100) { // 100ms de préparation
                    animationState = AnimationState.PUSH_LEFT
                    animationTimer = currentTime
                }
            }
            
            AnimationState.PUSH_LEFT -> {
                currentFrame = pushLeftFrame
                if (currentTime - animationTimer > 200) { // 200ms de poussée
                    animationState = AnimationState.GLIDE_LEFT
                    animationTimer = currentTime
                }
            }
            
            AnimationState.GLIDE_LEFT -> {
                currentFrame = glideLeftFrame
                if (currentSpeed < 0.001f) {
                    animationState = AnimationState.NEUTRAL
                }
            }
            
            AnimationState.PREP_RIGHT -> {
                currentFrame = prepRightFrame
                if (currentTime - animationTimer > 100) {
                    animationState = AnimationState.PUSH_RIGHT
                    animationTimer = currentTime
                }
            }
            
            AnimationState.PUSH_RIGHT -> {
                currentFrame = pushRightFrame
                if (currentTime - animationTimer > 200) {
                    animationState = AnimationState.GLIDE_RIGHT
                    animationTimer = currentTime
                }
            }
            
            AnimationState.GLIDE_RIGHT -> {
                currentFrame = glideRightFrame
                if (currentSpeed < 0.001f) {
                    animationState = AnimationState.NEUTRAL
                }
            }
        }
    }

        gameView = BiathlonView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        
        // NOUVEAU - Démarrer la phase de préparation
        preparationTimer = System.currentTimeMillis()
        preparationStarted = true
    }

    private fun getScreenName(): String {
        return when (currentScreen) {
            0 -> "🏁 Préparation"
            1 -> "🌲 Forêt - Écran 1"
            2 -> "🏔️ Montagne - Écran 2" 
            3 -> "🎯 Zone de tir"
            4 -> "🏞️ Vallée - Écran 4"
            5 -> "🏁 Sprint final - Écran 5"
            6 -> "📊 Résultats"
            else -> "Ski"
        }
    }

    private fun loadSpriteSheet() {
        try {
            spriteSheet = BitmapFactory.decodeResource(resources, R.drawable.skidefond_sprite)
            
            val totalWidth = spriteSheet.width
            val totalHeight = spriteSheet.height
            
            // Calculer les dimensions d'une cellule (10 colonnes x 14 rangées environ)
            val cellWidth = totalWidth / 10
            val cellHeight = totalHeight / 14
            
            // Taille finale réduite pour l'échelle
            val finalWidth = cellWidth / 2
            val finalHeight = cellHeight / 2
            
            // EXTRAIRE LES BONNES IMAGES DU SPRITE SHEET
            // Position neutre - Rangée du bas, centre
            val neutralBitmap = Bitmap.createBitmap(spriteSheet, cellWidth * 4, cellHeight * 13, cellWidth, cellHeight)
            neutralFrame = Bitmap.createScaledBitmap(neutralBitmap, finalWidth, finalHeight, true)
            
            // Préparation poussée gauche - Rangée 12
            val prepLeftBitmap = Bitmap.createBitmap(spriteSheet, cellWidth * 2, cellHeight * 12, cellWidth, cellHeight)
            prepLeftFrame = Bitmap.createScaledBitmap(prepLeftBitmap, finalWidth, finalHeight, true)
            
            // Poussée gauche active - Rangée 11  
            val pushLeftBitmap = Bitmap.createBitmap(spriteSheet, cellWidth * 1, cellHeight * 11, cellWidth, cellHeight)
            pushLeftFrame = Bitmap.createScaledBitmap(pushLeftBitmap, finalWidth, finalHeight, true)
            
            // Glisse gauche - Rangée 10
            val glideLeftBitmap = Bitmap.createBitmap(spriteSheet, cellWidth * 0, cellHeight * 10, cellWidth, cellHeight)
            glideLeftFrame = Bitmap.createScaledBitmap(glideLeftBitmap, finalWidth, finalHeight, true)
            
            // Préparation poussée droite - Rangée 12, côté droit
            val prepRightBitmap = Bitmap.createBitmap(spriteSheet, cellWidth * 7, cellHeight * 12, cellWidth, cellHeight)
            prepRightFrame = Bitmap.createScaledBitmap(prepRightBitmap, finalWidth, finalHeight, true)
            
            // Poussée droite active - Rangée 11, côté droit
            val pushRightBitmap = Bitmap.createBitmap(spriteSheet, cellWidth * 8, cellHeight * 11, cellWidth, cellHeight)
            pushRightFrame = Bitmap.createScaledBitmap(pushRightBitmap, finalWidth, finalHeight, true)
            
            // Glisse droite - Rangée 10, côté droit
            val glideRightBitmap = Bitmap.createBitmap(spriteSheet, cellWidth * 9, cellHeight * 10, cellWidth, cellHeight)
            glideRightFrame = Bitmap.createScaledBitmap(glideRightBitmap, finalWidth, finalHeight, true)
            
            // NOUVEAU - Charger l'image happy pour l'écran final (plus petite)
            try {
                val happyBitmap = BitmapFactory.decodeResource(resources, R.drawable.skidefond_happy)
                val happyWidth = happyBitmap.width / 4
                val happyHeight = happyBitmap.height / 4
                happyFrame = Bitmap.createScaledBitmap(happyBitmap, happyWidth, happyHeight, true)
            } catch (e: Exception) {
                val smallWidth = neutralFrame.width * 3 / 4
                val smallHeight = neutralFrame.height * 3 / 4
                happyFrame = Bitmap.createScaledBitmap(neutralFrame, smallWidth, smallHeight, true)
            }
            
            currentFrame = neutralFrame
            animationState = AnimationState.NEUTRAL
            
        } catch (e: Exception) {
            val fallback = BitmapFactory.decodeResource(resources, R.drawable.skieur_pixel)
            val scaledWidth = fallback.width / 3
            val scaledHeight = fallback.height / 3
            currentFrame = Bitmap.createScaledBitmap(fallback, scaledWidth, scaledHeight, true)
            happyFrame = currentFrame
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
        // NOUVEAU - Pas de contrôles pendant la préparation
        if (currentScreen == 0) {
            handleScreenTransitions()
            updateStatus()
            gameView.invalidate()
            return
        }
        
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                val x = event.values[0]
                val z = event.values[2]

                if (gameState == GameState.SKIING || gameState == GameState.FINAL_SKIING) {
                    handleSkiingInput(x, z)
                }
            }
            
            Sensor.TYPE_ACCELEROMETER -> {
                if (gameState == GameState.SHOOTING) {
                    val x = event.values[0]
                    val y = event.values[1]
                    
                    crosshair.x += y * 0.015f
                    crosshair.y += x * 0.015f
                    
                    crosshair.x = crosshair.x.coerceIn(0.05f, 0.95f)
                    crosshair.y = crosshair.y.coerceIn(0.25f, 0.65f)
                }
            }
        }
        
        // NOUVELLE logique de transitions d'écrans
        handleScreenTransitions()
        
        // NOUVEAU - Mise à jour de la glisse fluide
        updateGliding()
        
        // NOUVEAU - Mise à jour de l'animation fluide
        updateAnimation()
        
        updateStatus()
        gameView.invalidate()
    }

    // NOUVEAU - Système de glisse fluide et réaliste
    private fun updateGliding() {
        val currentTime = System.currentTimeMillis()
        
        if (isGliding && currentTime - lastGlideUpdate > 16) { // 60 FPS
            // Décélération progressive de la glisse ULTRA FORTE
            currentSpeed *= 0.95f // BEAUCOUP PLUS de friction (au lieu de 0.975f)
            
            if (currentSpeed > 0.0002f) { // SEUIL encore plus élevé pour arrêter plus tôt
                skierX += currentSpeed
                distance += currentSpeed * screenDistance
                backgroundOffset -= currentSpeed * 200f
            } else {
                isGliding = false
                currentSpeed = 0f
            }
            
            lastGlideUpdate = currentTime
        }
    }

    private fun handleScreenTransitions() {
        // NOUVEAU - Gestion de l'écran de préparation - 7 SECONDES
        if (currentScreen == 0 && preparationStarted) {
            if (System.currentTimeMillis() - preparationTimer > 7000) { // 7 secondes au lieu de 5
                currentScreen = 1 // Passer au premier écran de ski
                preparationStarted = false
                gameState = GameState.SKIING
            }
            return
        }
        
        when (gameState) {
            GameState.SKIING -> {
                // Écrans 1 et 2 : passer au tir après écran 2
                if (currentScreen == 2 && skierX >= 1.0f) {
                    gameState = GameState.SHOOTING
                    currentScreen = 3
                    skierX = 0.5f // Centrer pour le tir
                }
                // Écran 1 : passer à l'écran 2
                else if (currentScreen == 1 && skierX >= 1.0f) {
                    currentScreen = 2
                    skierX = 0.1f // Réapparaître à gauche
                }
            }
            
            GameState.SHOOTING -> {
                if (shotsFired >= 5) {
                    statusText.postDelayed({
                        gameState = GameState.FINAL_SKIING
                        currentScreen = 4
                        skierX = 0.1f // Réapparaître à gauche
                    }, 1500)
                }
            }
            
            GameState.FINAL_SKIING -> {
                // Écran 4 : passer à l'écran 5
                if (currentScreen == 4 && skierX >= 1.0f) {
                    currentScreen = 5
                    skierX = 0.1f
                }
                // Écran 5 : passer aux stats
                else if (currentScreen == 5 && skierX >= 1.0f) {
                    gameState = GameState.FINISHED
                    currentScreen = 6
                    skierX = 0.1f
                    autoMovement = true
                    movementSpeed = 0.02f  // Vitesse initiale
                    finalScreenTimer = System.currentTimeMillis()
                    // NOUVEAU - Utiliser l'image happy dès l'entrée dans l'écran final
                    currentFrame = happyFrame
                }
            }
            
            GameState.FINISHED -> {
                // Mouvement automatique vers le centre avec ralentissement progressif
                if (autoMovement && skierX < 0.5f) {
                    skierX += movementSpeed
                    // Ralentissement progressif : plus on approche du centre, plus on ralentit
                    val distanceToCenter = 0.5f - skierX
                    movementSpeed = (distanceToCenter * 0.04f).coerceAtLeast(0.003f)
                } else if (autoMovement) {
                    autoMovement = false
                    // Calculer le rythme moyen
                    averageRhythm = if (performanceHistory.isNotEmpty()) {
                        performanceHistory.average().toFloat()
                    } else 0f
                }
                
                // Attendre 5 secondes puis continuer
                if (System.currentTimeMillis() - finalScreenTimer > 5000) {
                    if (!practiceMode) {
                        tournamentData.addScore(currentPlayerIndex, eventIndex, calculateScore())
                    }
                    proceedToNextPlayerOrEvent()
                }
            }
        }
    }

    private fun handleSkiingInput(tiltX: Float, rotationZ: Float) {
        val currentTime = System.currentTimeMillis()
        
        val newDirection = when {
            rotationZ > 1.5f -> 1
            rotationZ < -1.5f -> -1
            else -> 0
        }
        
        if (newDirection != 0 && newDirection != pushDirection) {
            val intervalSinceLastPush = if (previousPushTime > 0) {
                currentTime - previousPushTime
            } else {
                600L
            }
            
            val pushAmplitude = abs(rotationZ)
            currentPushQuality = calculatePushQuality(pushAmplitude)
            currentRhythmQuality = calculateRhythmQuality(intervalSinceLastPush)
            
            // NOUVEAU - Rythme plus strict : si on pousse trop vite sans avoir ralenti, pénalité
            val speedPenalty = if (currentSpeed > 0.003f && intervalSinceLastPush < 400L) { // AJUSTÉ pour vitesses ultra lentes
                0.5f // Grosse pénalité si on pousse avant d'avoir ralenti
            } else if (currentSpeed > 0.002f && intervalSinceLastPush < 600L) { // AJUSTÉ
                0.7f // Pénalité moyenne
            } else {
                1f // Pas de pénalité
            }
            
            rhythmBonus = when {
                intervalSinceLastPush in 400..800 -> 1.5f
                intervalSinceLastPush in 300..1000 -> 1.2f
                else -> 0.8f
            } * speedPenalty
            
            // NOUVEAU - Système de poussée avec glisse réaliste ULTRA LENTE
            val combinedQuality = (currentPushQuality * 0.4f + currentRhythmQuality * 0.6f) * speedPenalty
            val pushStrength = 0.002f + (combinedQuality * 0.003f) // ULTRA RÉDUIT : 0.002 à 0.005 par poussée
            
            // Ajouter la force de poussée à la vitesse actuelle
            currentSpeed += pushStrength
            currentSpeed = currentSpeed.coerceAtMost(0.01f) // ULTRA RÉDUIT : Vitesse max très faible
            
            // Démarrer la glisse
            isGliding = true
            lastGlideUpdate = currentTime
            
            previousPushTime = lastPushTime
            lastPushTime = currentTime
            pushCount++
            
            val overallPerformance = combinedQuality
            performanceHistory.add(overallPerformance)
            if (performanceHistory.size > 15) {
                performanceHistory.removeAt(0)
            }
            
            // NOUVEAU - Animation selon la direction avec timing
            if (newDirection == -1) {
                animationState = AnimationState.PREP_LEFT
                animationTimer = currentTime
            } else {
                animationState = AnimationState.PREP_RIGHT
                animationTimer = currentTime
            }
        }
        
        pushDirection = newDirection
    }
    
    private fun calculatePushQuality(amplitude: Float): Float {
        return when {
            amplitude >= 3.0f -> 1f
            amplitude >= 2.5f -> 0.85f
            amplitude >= 2.0f -> 0.7f
            amplitude >= 1.5f -> 0.5f
            else -> 0.3f
        }.coerceIn(0f, 1f)
    }
    
    private fun calculateRhythmQuality(intervalMs: Long): Float {
        return when {
            intervalMs < 300L -> 0.2f
            intervalMs < 400L -> 0.6f
            intervalMs in 400L..800L -> 1f
            intervalMs < 1200L -> 0.7f
            intervalMs < 1600L -> 0.4f
            else -> 0.1f
        }.coerceIn(0f, 1f)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && gameState == GameState.SHOOTING && shotsFired < 5) {
            shotsFired++
            
            for (i in targetPositions.indices) {
                if (!targetHitStatus[i]) {
                    val dx = crosshair.x - targetPositions[i].x
                    val dy = crosshair.y - targetPositions[i].y
                    val distance = sqrt(dx * dx + dy * dy)
                    
                    val score = when {
                        distance < 0.02f -> 10 // CENTRE ROUGE - 10 points
                        distance < 0.04f -> 9  // Cercle 2 - 9 points  
                        distance < 0.06f -> 8  // Cercle 3 - 8 points
                        distance < 0.08f -> 7  // Cercle 4 - 7 points
                        distance < 0.10f -> 6  // Cercle 5 - 6 points
                        distance < 0.12f -> 5  // Cercle 6 - 5 points
                        distance < 0.14f -> 4  // Cercle 7 - 4 points
                        distance < 0.16f -> 3  // Cercle 8 - 3 points
                        distance < 0.18f -> 2  // Cercle 9 - 2 points
                        distance < 0.20f -> 1  // Cercle extérieur - 1 point
                        else -> 0 // RATÉ - 0 point
                    }
                    
                    if (score > 0) {
                        targetHitStatus[i] = true
                        targetScores[i] = score
                        targetsHit++
                        totalScore += score
                        break
                    }
                }
            }
            
            updateStatus()
            gameView.invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun updateStatus() {
        statusText.text = when {
            currentScreen == 0 -> "🏁 ${tournamentData.playerNames[currentPlayerIndex]} | Préparation - ${7 - (System.currentTimeMillis() - preparationTimer) / 1000}s" // 7 secondes
            gameState == GameState.SKIING -> "🎿 ${tournamentData.playerNames[currentPlayerIndex]} | ${getScreenName()} | Rythme: ${(rhythmBonus * 100).toInt()}%"
            gameState == GameState.SHOOTING -> "🎯 ${tournamentData.playerNames[currentPlayerIndex]} | Tir ${shotsFired}/5 | Score: ${totalScore} pts"
            gameState == GameState.FINAL_SKIING -> "🏁 ${tournamentData.playerNames[currentPlayerIndex]} | ${getScreenName()} | Sprint final!"
            gameState == GameState.FINISHED -> "📊 ${tournamentData.playerNames[currentPlayerIndex]} | Analyse des performances..."
            else -> "🎿 ${tournamentData.playerNames[currentPlayerIndex]} | ${getScreenName()}"
        }
    }

    private fun calculateScore(): Int {
        val shootingScore = totalScore
        val distanceBonus = 200 // AUGMENTÉ : Bonus pour avoir terminé (était 100)
        val rhythmBonus = (averageRhythm * 150).toInt().coerceAtMost(100) // AUGMENTÉ : Plus de points pour le rythme
        val pushBonus = (pushCount * 5).coerceAtMost(100) // NOUVEAU : Bonus pour chaque poussée
        val speedBonus = if (pushCount > 0) {
            val avgPushesPerScreen = pushCount / 5f // 5 écrans de ski
            when {
                avgPushesPerScreen >= 15f -> 100 // Très rapide
                avgPushesPerScreen >= 12f -> 75  // Rapide
                avgPushesPerScreen >= 10f -> 50  // Moyen
                else -> 25 // Lent mais bonus quand même
            }
        } else 0
        val penaltyForMissedShots = (5 - targetsHit) * 10 // RÉDUIT : Moins de pénalité (était 15)
        
        return maxOf(100, shootingScore + distanceBonus + rhythmBonus + pushBonus + speedBonus - penaltyForMissedShots)
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
        return maxOf(80, (150..250).random())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    inner class BiathlonView(context: Context) : View(context) {
        private val paint = Paint()

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            
            when (currentScreen) {
                0 -> drawPreparationScreen(canvas, w, h)
                1 -> drawForestScreen(canvas, w, h)
                2 -> drawMountainScreen(canvas, w, h) 
                3 -> drawShootingScreen(canvas, w, h)
                4 -> drawValleyScreen(canvas, w, h)
                5 -> drawFinishScreen(canvas, w, h)
                6 -> drawStatsScreen(canvas, w, h)
            }
        }
        
        // NOUVEAU - Écran de préparation avec image en ratio original et texte sur les côtés
        private fun drawPreparationScreen(canvas: Canvas, w: Int, h: Int) {
            // Fond blanc pour les côtés
            paint.color = Color.WHITE
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Charger et afficher l'image biathlon_track.png EN RATIO ORIGINAL
            try {
                val trackImage = BitmapFactory.decodeResource(context.resources, R.drawable.biathlon_track)
                val originalWidth = trackImage.width
                val originalHeight = trackImage.height
                val originalRatio = originalWidth.toFloat() / originalHeight.toFloat()
                
                // Calculer les dimensions pour garder le ratio et centrer
                val imageHeight = h
                val imageWidth = (imageHeight * originalRatio).toInt()
                val imageX = (w - imageWidth) / 2f // Centrer horizontalement
                
                val scaledTrack = Bitmap.createScaledBitmap(trackImage, imageWidth, imageHeight, true)
                canvas.drawBitmap(scaledTrack, imageX, 0f, null)
            } catch (e: Exception) {
                // Si l'image n'existe pas, fond bleu hivernal centré
                paint.color = Color.parseColor("#87CEEB")
                canvas.drawRect(w * 0.2f, 0f, w * 0.8f, h.toFloat(), paint)
            }
            
            // DRAPEAU DU PAYS en haut à gauche SUR LA ZONE BLANCHE - 4 FOIS PLUS GROS
            val flagSize = 480f  // 4 fois plus gros (était 120f)
            val flagX = 30f
            val flagY = 50f
            
            // Dessiner le drapeau selon le pays du joueur
            val playerCountry = tournamentData.playerCountries[currentPlayerIndex]
            drawCountryFlag(canvas, flagX, flagY, flagSize, playerCountry)
            
            // TEXTE SUR LES CÔTÉS BLANCS - CÔTÉ GAUCHE (ajusté pour le gros drapeau)
            val leftTextX = w * 0.1f
            
            // Temps restant en très gros (plus bas à cause du gros drapeau)
            val timeLeft = 7 - (System.currentTimeMillis() - preparationTimer) / 1000  // 7 secondes
            paint.color = Color.parseColor("#FF0000") // ROUGE
            paint.textSize = 120f
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            canvas.drawText("$timeLeft", leftTextX, h * 0.45f, paint)  // Plus bas (était 0.3f)
            
            // TITRE PRINCIPAL SUR LE CÔTÉ DROIT
            val rightTextX = w * 0.9f
            paint.color = Color.parseColor("#FF0000")
            paint.textSize = 60f
            canvas.drawText("🎿 BIATHLON", rightTextX, h * 0.2f, paint)
            canvas.drawText("🎯", rightTextX, h * 0.3f, paint)
            
            // INSTRUCTIONS SUR LES CÔTÉS
            paint.color = Color.parseColor("#FF0000")
            paint.textSize = 40f
            paint.isFakeBoldText = true
            
            // Instructions côté gauche (ajustées pour le gros drapeau)
            canvas.drawText("📱 TOURNEZ", leftTextX, h * 0.6f, paint)  // Plus bas
            canvas.drawText("LE TÉLÉPHONE", leftTextX, h * 0.65f, paint)
            
            canvas.drawText("🔄 ALTERNEZ", leftTextX, h * 0.75f, paint)  // Plus bas
            canvas.drawText("GAUCHE-DROITE", leftTextX, h * 0.8f, paint)
            
            // Instructions côté droit
            canvas.drawText("🎯 VISEZ LE", rightTextX, h * 0.5f, paint)
            canvas.drawText("CENTRE ROUGE", rightTextX, h * 0.55f, paint)
            
            canvas.drawText("🏃 GARDEZ", rightTextX, h * 0.65f, paint)
            canvas.drawText("LE RYTHME!", rightTextX, h * 0.7f, paint)
            
            // Message de départ EN BAS AU CENTRE
            paint.color = Color.parseColor("#FF0000")
            paint.textSize = 60f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("PRÉPAREZ-VOUS!", w/2f, h * 0.9f, paint)
            
            paint.isFakeBoldText = false
        }
        
        // NOUVEAU - Fonction pour dessiner les drapeaux de pays
        private fun drawCountryFlag(canvas: Canvas, x: Float, y: Float, size: Float, country: String) {
            when (country.uppercase()) {
                "FRANCE", "FR" -> {
                    // Drapeau français
                    paint.color = Color.parseColor("#0055A4") // Bleu
                    canvas.drawRect(x, y, x + size/3, y + size * 0.7f, paint)
                    paint.color = Color.WHITE // Blanc
                    canvas.drawRect(x + size/3, y, x + 2*size/3, y + size * 0.7f, paint)
                    paint.color = Color.parseColor("#EF4135") // Rouge
                    canvas.drawRect(x + 2*size/3, y, x + size, y + size * 0.7f, paint)
                }
                "CANADA", "CA" -> {
                    // Drapeau canadien
                    paint.color = Color.parseColor("#FF0000") // Rouge
                    canvas.drawRect(x, y, x + size/4, y + size * 0.7f, paint)
                    canvas.drawRect(x + 3*size/4, y, x + size, y + size * 0.7f, paint)
                    paint.color = Color.WHITE // Blanc
                    canvas.drawRect(x + size/4, y, x + 3*size/4, y + size * 0.7f, paint)
                    // Feuille d'érable simplifiée
                    paint.color = Color.parseColor("#FF0000")
                    canvas.drawCircle(x + size/2, y + size * 0.35f, 15f, paint)
                }
                "USA", "US" -> {
                    // Drapeau américain simplifié
                    paint.color = Color.parseColor("#B22234") // Rouge
                    canvas.drawRect(x, y, x + size, y + size * 0.7f, paint)
                    paint.color = Color.WHITE // Bandes blanches
                    for (i in 1..6) {
                        canvas.drawRect(x, y + i * size * 0.7f / 13 * 2, x + size, y + (i * 2 + 1) * size * 0.7f / 13, paint)
                    }
                    paint.color = Color.parseColor("#3C3B6E") // Bleu
                    canvas.drawRect(x, y, x + size * 0.4f, y + size * 0.35f, paint)
                }
                "GERMANY", "DE" -> {
                    // Drapeau allemand
                    paint.color = Color.BLACK
                    canvas.drawRect(x, y, x + size, y + size * 0.7f / 3, paint)
                    paint.color = Color.parseColor("#DD0000") // Rouge
                    canvas.drawRect(x, y + size * 0.7f / 3, x + size, y + 2 * size * 0.7f / 3, paint)
                    paint.color = Color.parseColor("#FFCE00") // Jaune
                    canvas.drawRect(x, y + 2 * size * 0.7f / 3, x + size, y + size * 0.7f, paint)
                }
                else -> {
                    // Drapeau générique (bleu-blanc-rouge)
                    paint.color = Color.parseColor("#0055A4")
                    canvas.drawRect(x, y, x + size/3, y + size * 0.7f, paint)
                    paint.color = Color.WHITE
                    canvas.drawRect(x + size/3, y, x + 2*size/3, y + size * 0.7f, paint)
                    paint.color = Color.parseColor("#EF4135")
                    canvas.drawRect(x + 2*size/3, y, x + size, y + size * 0.7f, paint)
                }
            }
            
            // Bordure du drapeau
            paint.color = Color.BLACK
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawRect(x, y, x + size, y + size * 0.7f, paint)
            paint.style = Paint.Style.FILL
        }
        
        private fun drawForestScreen(canvas: Canvas, w: Int, h: Int) {
            // Ciel de forêt
            paint.color = Color.parseColor("#87CEEB")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.5f, paint)
            
            // Sol neigeux
            paint.color = Color.parseColor("#F0F8FF")
            canvas.drawRect(0f, h * 0.5f, w.toFloat(), h.toFloat(), paint)
            
            // Montagnes au loin
            paint.color = Color.parseColor("#B0C4DE")
            val mountainPath = Path()
            mountainPath.moveTo(0f, h * 0.15f)
            mountainPath.lineTo(w * 0.3f, h * 0.05f)
            mountainPath.lineTo(w * 0.6f, h * 0.12f)
            mountainPath.lineTo(w.toFloat(), h * 0.08f)
            mountainPath.lineTo(w.toFloat(), h * 0.5f)
            mountainPath.lineTo(0f, h * 0.5f)
            mountainPath.close()
            canvas.drawPath(mountainPath, paint)
            
            // Arbres de forêt
            for (i in 0..15) {
                val treeX = (backgroundOffset * 0.5f + i * 60) % (w + 120) - 120
                val treeHeight = 80f + (i % 3) * 20f
                
                // Tronc
                paint.color = Color.parseColor("#8B4513")
                canvas.drawRect(treeX - 5f, h * 0.5f - treeHeight, treeX + 5f, h * 0.5f, paint)
                
                // Feuillage
                paint.color = Color.parseColor("#228B22")
                canvas.drawCircle(treeX, h * 0.5f - treeHeight, 25f, paint)
            }
            
            drawSkierAndUI(canvas, w, h)
        }
        
        private fun drawMountainScreen(canvas: Canvas, w: Int, h: Int) {
            // Ciel de montagne
            paint.color = Color.parseColor("#B0C4DE")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.5f, paint)
            
            // Sol neigeux
            paint.color = Color.parseColor("#FFFAFA")
            canvas.drawRect(0f, h * 0.5f, w.toFloat(), h.toFloat(), paint)
            
            // Chaîne de montagnes
            paint.color = Color.parseColor("#4682B4")
            val mountainPath = Path()
            mountainPath.moveTo(0f, h * 0.5f)
            mountainPath.lineTo(w * 0.2f, h * 0.05f)
            mountainPath.lineTo(w * 0.4f, h * 0.15f)
            mountainPath.lineTo(w * 0.6f, h * 0.02f)
            mountainPath.lineTo(w * 0.8f, h * 0.12f)
            mountainPath.lineTo(w.toFloat(), h * 0.08f)
            mountainPath.lineTo(w.toFloat(), h * 0.5f)
            mountainPath.close()
            canvas.drawPath(mountainPath, paint)
            
            // Sommets enneigés
            paint.color = Color.WHITE
            for (i in arrayOf(0.2f, 0.6f)) {
                val snowPath = Path()
                snowPath.moveTo(w * (i - 0.03f), h * 0.1f)
                snowPath.lineTo(w * i, h * 0.02f)
                snowPath.lineTo(w * (i + 0.03f), h * 0.1f)
                canvas.drawPath(snowPath, paint)
            }
            
            // Rochers
            paint.color = Color.parseColor("#696969")
            for (i in 0..8) {
                val rockX = (backgroundOffset * 0.4f + i * 100) % (w + 200) - 200
                canvas.drawCircle(rockX, h * 0.45f, 12f + (i % 3) * 5f, paint)
            }
            
            drawSkierAndUI(canvas, w, h)
        }
        
        private fun drawShootingScreen(canvas: Canvas, w: Int, h: Int) {
            // NOUVEAU - Fond hivernal au lieu de noir
            // Ciel hivernal
            paint.color = Color.parseColor("#E6F3FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Montagnes enneigées au loin
            paint.color = Color.parseColor("#B0C4DE")
            val mountainPath = Path()
            mountainPath.moveTo(0f, h * 0.4f)
            mountainPath.lineTo(w * 0.25f, h * 0.2f)
            mountainPath.lineTo(w * 0.5f, h * 0.3f)
            mountainPath.lineTo(w * 0.75f, h * 0.15f)
            mountainPath.lineTo(w.toFloat(), h * 0.35f)
            mountainPath.lineTo(w.toFloat(), h.toFloat())
            mountainPath.lineTo(0f, h.toFloat())
            mountainPath.close()
            canvas.drawPath(mountainPath, paint)
            
            // Sol neigeux
            paint.color = Color.parseColor("#FFFAFA")
            canvas.drawRect(0f, h * 0.6f, w.toFloat(), h.toFloat(), paint)
            
            // Clôtures pour les cibles
            paint.color = Color.parseColor("#8B4513")
            canvas.drawRect(0f, h * 0.5f, w.toFloat(), h * 0.55f, paint)
            
            // Poteaux de clôture
            for (i in 0..6) {
                val postX = i * w / 6f
                canvas.drawRect(postX - 5f, h * 0.45f, postX + 5f, h * 0.6f, paint)
            }
            
            // Arbres hivernaux en arrière-plan
            paint.color = Color.parseColor("#228B22")
            for (i in 0..8) {
                val treeX = (i * 120f) % w
                val treeHeight = 60f + (i % 3) * 15f
                
                // Tronc
                paint.color = Color.parseColor("#8B4513")
                canvas.drawRect(treeX - 3f, h * 0.4f - treeHeight, treeX + 3f, h * 0.4f, paint)
                
                // Feuillage enneigé
                paint.color = Color.parseColor("#228B22")
                canvas.drawCircle(treeX, h * 0.4f - treeHeight, 15f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(treeX, h * 0.4f - treeHeight, 12f, paint)
            }
            
            // Instructions
            paint.color = Color.parseColor("#003366")
            paint.textSize = 50f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🎯 ZONE DE TIR 🎯", w/2f, 80f, paint)
            
            paint.textSize = 30f
            paint.color = Color.parseColor("#CC0000")
            canvas.drawText("TÉLÉPHONE À PLAT - VISEZ LE CENTRE ROUGE!", w/2f, 130f, paint)
            
            if (shotsFired >= 5) {
                paint.color = Color.parseColor("#006600")
                paint.textSize = 40f
                canvas.drawText("TIR TERMINÉ - Continuez le parcours...", w/2f, 180f, paint)
            }
            
            // Cibles sur les clôtures
            for (i in targetPositions.indices) {
                val px = targetPositions[i].x * w
                val py = h * 0.4f // Positionnées sur la clôture
                
                drawTarget(canvas, px, py, i)
            }
            
            // Mire
            drawCrosshair(canvas, w, h)
            
            // Interface de tir
            drawShootingUI(canvas, w, h)
        }
        
        private fun drawValleyScreen(canvas: Canvas, w: Int, h: Int) {
            // Ciel de vallée
            paint.color = Color.parseColor("#87CEFA")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.5f, paint)
            
            // Sol neigeux
            paint.color = Color.parseColor("#F5FFFA")
            canvas.drawRect(0f, h * 0.5f, w.toFloat(), h.toFloat(), paint)
            
            // Collines douces
            paint.color = Color.parseColor("#90EE90")
            val hillPath = Path()
            hillPath.moveTo(0f, h * 0.35f)
            hillPath.quadTo(w * 0.3f, h * 0.25f, w * 0.6f, h * 0.3f)
            hillPath.quadTo(w * 0.8f, h * 0.35f, w.toFloat(), h * 0.25f)
            hillPath.lineTo(w.toFloat(), h * 0.5f)
            hillPath.lineTo(0f, h * 0.5f)
            hillPath.close()
            canvas.drawPath(hillPath, paint)
            
            // Rivière serpentante
            paint.color = Color.parseColor("#4682B4")
            val riverPath = Path()
            riverPath.moveTo(0f, h * 0.42f)
            riverPath.quadTo(w * 0.3f, h * 0.38f, w * 0.6f, h * 0.45f)
            riverPath.lineTo(w.toFloat(), h * 0.43f)
            riverPath.lineTo(w.toFloat(), h * 0.47f)
            riverPath.quadTo(w * 0.6f, h * 0.49f, w * 0.3f, h * 0.42f)
            riverPath.lineTo(0f, h * 0.46f)
            riverPath.close()
            canvas.drawPath(riverPath, paint)
            
            // Bouleaux
            for (i in 0..12) {
                val treeX = (backgroundOffset * 0.3f + i * 80) % (w + 160) - 160
                val treeHeight = 70f
                
                // Tronc de bouleau
                paint.color = Color.parseColor("#F5F5DC")
                canvas.drawRect(treeX - 3f, h * 0.5f - treeHeight, treeX + 3f, h * 0.5f, paint)
                
                // Marques noires
                paint.color = Color.BLACK
                for (j in 1..3) {
                    val markY = h * 0.5f - treeHeight * j / 4f
                    canvas.drawRect(treeX - 3f, markY, treeX + 3f, markY + 2f, paint)
                }
                
                // Feuillage
                paint.color = Color.parseColor("#90EE90")
                canvas.drawCircle(treeX, h * 0.5f - treeHeight, 18f, paint)
            }
            
            drawSkierAndUI(canvas, w, h)
        }
        
        private fun drawFinishScreen(canvas: Canvas, w: Int, h: Int) {
            // Ciel doré de victoire
            val gradient = LinearGradient(0f, 0f, 0f, h * 0.5f,
                Color.parseColor("#FFD700"), Color.parseColor("#FFA500"), Shader.TileMode.CLAMP)
            paint.shader = gradient
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.5f, paint)
            paint.shader = null
            
            // Sol neigeux doré
            paint.color = Color.parseColor("#FFFAF0")
            canvas.drawRect(0f, h * 0.5f, w.toFloat(), h.toFloat(), paint)
            
            // Montagnes dorées
            paint.color = Color.parseColor("#DAA520")
            val goldenMountain = Path()
            goldenMountain.moveTo(0f, h * 0.3f)
            goldenMountain.lineTo(w * 0.3f, h * 0.15f)
            goldenMountain.lineTo(w * 0.7f, h * 0.1f)
            goldenMountain.lineTo(w.toFloat(), h * 0.2f)
            goldenMountain.lineTo(w.toFloat(), h * 0.5f)
            goldenMountain.lineTo(0f, h * 0.5f)
            goldenMountain.close()
            canvas.drawPath(goldenMountain, paint)
            
            // Drapeaux de victoire
            for (i in 0..10) {
                val flagX = (backgroundOffset * 0.5f + i * 80) % (w + 160) - 160
                val flagHeight = 60f
                
                // Mât
                paint.color = Color.parseColor("#8B4513")
                canvas.drawRect(flagX - 2f, h * 0.5f - flagHeight, flagX + 2f, h * 0.5f, paint)
                
                // Drapeau coloré
                val flagColors = arrayOf("#FF0000", "#FFFFFF", "#0000FF")
                paint.color = Color.parseColor(flagColors[i % 3])
                canvas.drawRect(flagX + 2f, h * 0.5f - flagHeight, flagX + 30f, h * 0.5f - flagHeight + 20f, paint)
            }
            
            // Ligne d'arrivée si proche
            if (skierX > 0.8f) {
                paint.color = Color.BLACK
                for (i in 0..20) {
                    val y = h * 0.75f + i * 6f
                    val color = if (i % 2 == 0) Color.BLACK else Color.WHITE
                    paint.color = color
                    canvas.drawRect(w * 0.9f, y, w.toFloat(), y + 6f, paint)
                }
            }
            
            drawSkierAndUI(canvas, w, h)
        }
        
        private fun drawStatsScreen(canvas: Canvas, w: Int, h: Int) {
            // FOND JAUNE COMPLET
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Skieur au centre (utilise happyFrame automatiquement) - PLUS PETIT ET PLUS HAUT
            val skierScreenX = w * skierX
            val skierY = h * 0.6f  // Plus haut (était 0.7f)
            
            currentFrame?.let { frame ->
                val destX = skierScreenX - frame.width / 2f
                canvas.drawBitmap(frame, destX, skierY, null)
            }
            
            // TITRE PRINCIPAL
            paint.color = Color.parseColor("#8B0000")
            paint.textSize = 60f
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            canvas.drawText("📊 RÉSULTATS 📊", w/2f, 100f, paint)
            
            // STATISTIQUES EN GROS
            paint.textSize = 45f
            paint.color = Color.parseColor("#000080")
            
            val stats = listOf(
                "🎯 Tirs réussis: $targetsHit/5",
                "💥 Score tir: $totalScore pts",
                "🏃 Poussées: $pushCount",
                "⚡ Rythme moyen: ${(averageRhythm * 100).toInt()}%"
            )
            
            var yPosition = 200f
            for (stat in stats) {
                canvas.drawText(stat, w/2f, yPosition, paint)
                yPosition += 70f
            }
            
            // SCORE FINAL EN DOUBLE GROSSEUR
            paint.color = Color.parseColor("#8B0000")
            paint.textSize = 90f  // DOUBLE (était 45f)
            paint.isFakeBoldText = true
            canvas.drawText("🏆 SCORE FINAL: ${calculateScore()} pts", w/2f, yPosition, paint)
            
            // Message de fin
            paint.color = Color.parseColor("#006400")
            paint.textSize = 35f
            if (!autoMovement && skierX >= 0.5f) {
                canvas.drawText("Analyse terminée dans ${5 - (System.currentTimeMillis() - finalScreenTimer) / 1000}s", 
                    w/2f, h - 100f, paint)
            }
            
            paint.isFakeBoldText = false
        }
        
        private fun drawSkierAndUI(canvas: Canvas, w: Int, h: Int) {
            // Piste
            paint.color = Color.parseColor("#DCDCDC")
            canvas.drawRect(0f, h * 0.75f, w.toFloat(), h.toFloat(), paint)
            
            // Skieur
            val skierScreenX = w * skierX
            val skierY = h * 0.7f
            
            currentFrame?.let { frame ->
                val destX = skierScreenX - frame.width / 2f
                canvas.drawBitmap(frame, destX, skierY, null)
            }
            
            // Instructions au début - DÉCENTRÉES VERS LA GAUCHE
            if (pushCount < 3 && (gameState == GameState.SKIING || gameState == GameState.FINAL_SKIING)) {
                paint.color = Color.BLACK
                paint.textSize = 50f
                paint.textAlign = Paint.Align.LEFT // Alignement à gauche
                canvas.drawText("🎿 TOURNEZ COMME UN VOLANT", 50f, h * 0.15f, paint)
                
                paint.color = Color.RED
                paint.textSize = 40f
                canvas.drawText("+ ALTERNEZ GAUCHE-DROITE!", 50f, h * 0.22f, paint)
                
                paint.color = Color.parseColor("#FF6600")
                paint.textSize = 30f
                canvas.drawText("ATTENDEZ QUE ÇA RALENTISSE!", 50f, h * 0.28f, paint)
            }
            
            // Barre de performance - DÉCENTRÉE VERS LA DROITE
            if (gameState == GameState.SKIING || gameState == GameState.FINAL_SKIING) {
                drawPerformanceBand(canvas, w * 0.55f, 80f, w * 0.4f, 60f) // Décalée vers la droite
            }
            
            // Barre de progression de l'écran
            drawScreenProgress(canvas, w, h)
        }
        
        private fun drawPerformanceBand(canvas: Canvas, x: Float, y: Float, width: Float, height: Float) {
            // Fond
            paint.color = Color.parseColor("#333333")
            paint.style = Paint.Style.FILL
            canvas.drawRect(x, y, x + width, y + height, paint)
            
            if (pushCount < 2) {
                // Zones d'explication
                val zoneWidth = width / 5f
                val colors = arrayOf("#FF0000", "#FF8800", "#FFFF00", "#88FF00", "#00FF00")
                val labels = arrayOf("Lent", "Faible", "Moyen", "Bon", "Parfait")
                
                for (i in 0..4) {
                    paint.color = Color.parseColor(colors[i])
                    canvas.drawRect(x + i * zoneWidth, y, x + (i + 1) * zoneWidth, y + height, paint)
                    
                    paint.color = Color.BLACK
                    paint.textSize = 24f
                    paint.textAlign = Paint.Align.CENTER
                    canvas.drawText(labels[i], x + i * zoneWidth + zoneWidth/2f, y + height/2f + 8f, paint)
                }
                
                paint.color = Color.WHITE
                paint.textSize = 32f
                canvas.drawText("QUALITÉ POUSSÉES", x + width/2f, y - 20f, paint)
            } else {
                // Performance en temps réel
                val pushColor = getPerformanceColor(currentPushQuality)
                paint.color = pushColor
                canvas.drawRect(x, y, x + width/2f, y + height, paint)
                
                val rhythmColor = getPerformanceColor(currentRhythmQuality)
                paint.color = rhythmColor
                canvas.drawRect(x + width/2f, y, x + width, y + height, paint)
                
                paint.color = Color.WHITE
                paint.textSize = 28f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("FORCE", x + width/4f, y - 15f, paint)
                canvas.drawText("RYTHME", x + 3*width/4f, y - 15f, paint)
                
                paint.color = Color.BLACK
                paint.textSize = 32f
                canvas.drawText("${(currentPushQuality * 100).toInt()}%", x + width/4f, y + height/2f + 12f, paint)
                canvas.drawText("${(currentRhythmQuality * 100).toInt()}%", x + 3*width/4f, y + height/2f + 12f, paint)
            }
            
            // Bordure
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawRect(x, y, x + width, y + height, paint)
            paint.style = Paint.Style.FILL
        }
        
        private fun getPerformanceColor(performance: Float): Int {
            return when {
                performance >= 0.8f -> Color.parseColor("#00FF00")
                performance >= 0.6f -> Color.parseColor("#88FF00")
                performance >= 0.4f -> Color.parseColor("#FFFF00")
                performance >= 0.2f -> Color.parseColor("#FF8800")
                else -> Color.parseColor("#FF0000")
            }
        }
        
        private fun drawScreenProgress(canvas: Canvas, w: Int, h: Int) {
            // Barre de progression de l'écran
            paint.color = Color.BLACK
            canvas.drawRect(w * 0.1f, 20f, w * 0.9f, 35f, paint)
            
            // Progression actuelle sur l'écran
            paint.color = Color.CYAN
            canvas.drawRect(w * 0.1f, 22f, w * 0.1f + (skierX.coerceIn(0f, 1f) * w * 0.8f), 33f, paint)
            
            // Indicateur d'écran
            paint.color = Color.WHITE
            paint.textSize = 20f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("Écran $currentScreen/6", w * 0.5f, 50f, paint)
        }
        
        private fun drawTarget(canvas: Canvas, px: Float, py: Float, index: Int) {
            if (targetHitStatus[index]) {
                paint.color = Color.parseColor("#00aa00")
                canvas.drawCircle(px, py, 60f, paint)
                
                paint.color = Color.WHITE
                paint.textSize = 30f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("+${targetScores[index]}", px, py + 10f, paint)
                
                paint.textSize = 20f
                canvas.drawText("TOUCHÉ", px, py + 70f, paint)
            } else {
                // VRAIE CIBLE DE BIATHLON avec cercles concentriques et points différents
                
                // Cercle extérieur - 1 point (blanc)
                paint.color = Color.parseColor("#FFFFFF")
                canvas.drawCircle(px, py, 60f, paint)
                
                // Cercle 9 - 2 points (noir)
                paint.color = Color.parseColor("#000000")
                canvas.drawCircle(px, py, 54f, paint)
                
                // Cercle 8 - 3 points (blanc)
                paint.color = Color.parseColor("#FFFFFF")
                canvas.drawCircle(px, py, 48f, paint)
                
                // Cercle 7 - 4 points (noir)
                paint.color = Color.parseColor("#000000")
                canvas.drawCircle(px, py, 42f, paint)
                
                // Cercle 6 - 5 points (blanc)
                paint.color = Color.parseColor("#FFFFFF")
                canvas.drawCircle(px, py, 36f, paint)
                
                // Cercle 5 - 6 points (noir)
                paint.color = Color.parseColor("#000000")
                canvas.drawCircle(px, py, 30f, paint)
                
                // Cercle 4 - 7 points (blanc)
                paint.color = Color.parseColor("#FFFFFF")
                canvas.drawCircle(px, py, 24f, paint)
                
                // Cercle 3 - 8 points (noir)
                paint.color = Color.parseColor("#000000")
                canvas.drawCircle(px, py, 18f, paint)
                
                // Cercle 2 - 9 points (blanc)
                paint.color = Color.parseColor("#FFFFFF")
                canvas.drawCircle(px, py, 12f, paint)
                
                // CENTRE ROUGE - 10 points (le jackpot !)
                paint.color = Color.parseColor("#FF0000")
                canvas.drawCircle(px, py, 6f, paint)
                
                // Numéro de la cible
                paint.color = Color.WHITE
                paint.textSize = 16f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("${index + 1}", px, py + 80f, paint)
            }
        }
        
        private fun drawCrosshair(canvas: Canvas, w: Int, h: Int) {
            val crossX = crosshair.x * w
            val crossY = crosshair.y * h + 100
            
            paint.color = Color.BLACK
            paint.strokeWidth = 6f
            canvas.drawLine(crossX - 25, crossY, crossX + 25, crossY, paint)
            canvas.drawLine(crossX, crossY - 25, crossX, crossY + 25, paint)
            
            paint.color = Color.RED
            paint.strokeWidth = 2f
            canvas.drawLine(crossX - 20, crossY, crossX + 20, crossY, paint)
            canvas.drawLine(crossX, crossY - 20, crossX, crossY + 20, paint)
            
            paint.style = Paint.Style.FILL
            paint.color = Color.YELLOW
            canvas.drawCircle(crossX, crossY, 3f, paint)
        }
        
        private fun drawShootingUI(canvas: Canvas, w: Int, h: Int) {
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#003366")
            paint.textSize = 30f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("🔫 Munitions: ${5-shotsFired}/5", 30f, h - 200f, paint) // Plus haut pour faire place aux gros rectangles
            canvas.drawText("🎯 Score: $totalScore pts", 30f, h - 160f, paint)
            
            // RECTANGLES DE MUNITIONS 5 FOIS PLUS GROS
            val rectWidth = 200f  // 5 fois plus large (était 40f)
            val rectHeight = 100f // 5 fois plus haut (était 30f)
            val rectSpacing = 220f // Espacement ajusté
            
            for (i in 0 until 5) {
                paint.color = if (i < shotsFired) Color.GRAY else Color.YELLOW
                val rectX = w - 1200f + i * rectSpacing // Ajusté pour les gros rectangles
                canvas.drawRect(rectX, h - 120f, rectX + rectWidth, h - 20f, paint)
                
                // Numéro sur chaque rectangle
                paint.color = Color.BLACK
                paint.textSize = 50f // Texte plus gros aussi
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("${i+1}", rectX + rectWidth/2f, h - 60f, paint)
            }
        }
    }

    enum class GameState {
        SKIING, SHOOTING, FINAL_SKIING, FINISHED
    }
    
    enum class AnimationState {
        NEUTRAL, PREP_LEFT, PUSH_LEFT, GLIDE_LEFT, PREP_RIGHT, PUSH_RIGHT, GLIDE_RIGHT
    }
}
