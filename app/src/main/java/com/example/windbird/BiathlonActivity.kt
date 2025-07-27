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
    var distance = 0f
    val screenDistance = 600f // Distance pour traverser un écran
    var backgroundOffset = 0f
    var currentScreen = 0 // 0=PREPARATION, 1-6 (Écran 1, 2, TIR, 4, 5, STATS)
    var skierX = 0.1f // Position relative du skieur (0.0 à 1.0)
    
    // NOUVEAU - Variables pour la préparation
    var preparationTimer = 0L
    var preparationStarted = false
    
    // NOUVEAU - Système de poussées rythmées avec performance ET GLISSE
    var pushDirection = 0 // -1=gauche, 0=neutre, 1=droite
    var lastPushTime = 0L
    var previousPushTime = 0L
    var rhythmBonus = 1f
    var pushCount = 0
    
    // NOUVEAU - Variables pour la glisse fluide
    var currentSpeed = 0f // Vitesse actuelle du skieur
    var targetSpeed = 0f // Vitesse cible après une poussée
    var isGliding = false // En train de glisser
    var lastGlideUpdate = 0L
    
    // NOUVEAU - Variables pour la bande de performance
    var currentPushQuality = 0f
    var currentRhythmQuality = 0f
    var performanceHistory = mutableListOf<Float>()
    var averageRhythm = 0f
    
    // Sprite animation AMÉLIORÉE avec animation fluide
    lateinit var spriteSheet: Bitmap
    lateinit var neutralFrame: Bitmap      // Position neutre
    lateinit var prepLeftFrame: Bitmap     // Préparation poussée gauche
    lateinit var pushLeftFrame: Bitmap     // Poussée gauche active
    lateinit var glideLeftFrame: Bitmap    // Glisse gauche
    lateinit var prepRightFrame: Bitmap    // Préparation poussée droite
    lateinit var pushRightFrame: Bitmap    // Poussée droite active
    lateinit var glideRightFrame: Bitmap   // Glisse droite
    var happyFrame: Bitmap? = null         // Image pour l'écran final
    var currentFrame: Bitmap? = null
    var animationTimer = 0L
    var animationState = AnimationState.NEUTRAL

    // Variables de tir AMÉLIORÉES
    var gameState = GameState.SKIING
    var targetsHit = 0
    var shotsFired = 0
    var totalScore = 0
    var crosshair = PointF(0.5f, 0.4f)
    val targetPositions = List(5) { PointF(0.15f + it * 0.175f, 0.4f) }
    val targetHitStatus = BooleanArray(5) { false }
    val targetScores = IntArray(5) { 0 }

    // Variables pour l'écran final
    var finalScreenTimer = 0L
    var autoMovement = false
    var movementSpeed = 0.02f  // Vitesse variable pour ralentissement

    lateinit var tournamentData: TournamentData
    var eventIndex: Int = 0
    var numberOfPlayers: Int = 1
    var currentPlayerIndex: Int = 0
    var practiceMode: Boolean = false

    enum class GameState {
        SKIING, SHOOTING, FINAL_SKIING, FINISHED
    }
    
    enum class AnimationState {
        NEUTRAL, PREP_LEFT, PUSH_LEFT, GLIDE_LEFT, PREP_RIGHT, PUSH_RIGHT, GLIDE_RIGHT
    }

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
        }

        gameView = BiathlonView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        
        // NOUVEAU - Démarrer la phase de préparation
        preparationTimer = System.currentTimeMillis()
        preparationStarted = true
    }

    fun getScreenName(): String {
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

    // NOUVEAU - Système d'animation fluide
    fun updateAnimation() {
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

    fun calculateScore(): Int {
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
}
