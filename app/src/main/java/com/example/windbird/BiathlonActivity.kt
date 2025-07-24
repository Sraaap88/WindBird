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
    
    // NOUVEAU - Système de poussées rythmées avec performance
    private var pushDirection = 0 // -1=gauche, 0=neutre, 1=droite
    private var lastPushTime = 0L
    private var previousPushTime = 0L
    private var rhythmBonus = 1f
    private var pushCount = 0
    
    // NOUVEAU - Variables pour la bande de performance
    private var currentPushQuality = 0f
    private var currentRhythmQuality = 0f
    private var performanceHistory = mutableListOf<Float>()
    private var averageRhythm = 0f
    
    // Sprite animation AMÉLIORÉE
    private lateinit var spriteSheet: Bitmap
    private lateinit var leftFrame: Bitmap
    private lateinit var rightFrame: Bitmap
    private var happyFrame: Bitmap? = null  // Image pour l'écran final
    private var currentFrame: Bitmap? = null
    private var animationTimer = 0L
    private var useLeftFrame = true

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
            
            // CORRECTION : Éliminer complètement le cadre noir - encore 2 pixels supplémentaires
            val frameWidth = (totalWidth - 25) / 2  // -25 = cadres + marges supplémentaires
            val frameHeight = totalHeight - 18      // -18 = cadres haut/bas + marges
            
            // Extraire les frames en évitant les bordures noires - décalage encore augmenté
            leftFrame = Bitmap.createBitmap(spriteSheet, 9, 9, frameWidth, frameHeight)
            rightFrame = Bitmap.createBitmap(spriteSheet, 16 + frameWidth, 9, frameWidth, frameHeight)
            
            // Redimensionner
            val newWidth = frameWidth / 3
            val newHeight = frameHeight / 3
            
            leftFrame = Bitmap.createScaledBitmap(leftFrame, newWidth, newHeight, true)
            rightFrame = Bitmap.createScaledBitmap(rightFrame, newWidth, newHeight, true)
            
            // NOUVEAU - Charger l'image happy pour l'écran final (plus petite)
            try {
                val happyBitmap = BitmapFactory.decodeResource(resources, R.drawable.skidefond_happy)
                val happyWidth = happyBitmap.width / 4  // Plus petit que les autres (÷4 au lieu de ÷3)
                val happyHeight = happyBitmap.height / 4
                happyFrame = Bitmap.createScaledBitmap(happyBitmap, happyWidth, happyHeight, true)
            } catch (e: Exception) {
                // Si l'image happy n'existe pas, utiliser leftFrame mais plus petit
                val smallWidth = leftFrame.width * 3 / 4  // 75% de la taille normale
                val smallHeight = leftFrame.height * 3 / 4
                happyFrame = Bitmap.createScaledBitmap(leftFrame, smallWidth, smallHeight, true)
            }
            
            currentFrame = leftFrame
        } catch (e: Exception) {
            val fallback = BitmapFactory.decodeResource(resources, R.drawable.skieur_pixel)
            val scaledWidth = fallback.width / 3
            val scaledHeight = fallback.height / 3
            currentFrame = Bitmap.createScaledBitmap(fallback, scaledWidth, scaledHeight, true)
            happyFrame = currentFrame  // Assigner la même image de fallback
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
        updateStatus()
        gameView.invalidate()
    }

    private fun handleScreenTransitions() {
        // NOUVEAU - Gestion de l'écran de préparation
        if (currentScreen == 0 && preparationStarted) {
            if (System.currentTimeMillis() - preparationTimer > 5000) {
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
            
            rhythmBonus = when {
                intervalSinceLastPush in 400..800 -> 1.5f
                intervalSinceLastPush in 300..1000 -> 1.2f
                else -> 0.8f
            }
            
            // NOUVEAU : Avancement horizontal au lieu de distance totale
            val combinedQuality = (currentPushQuality * 0.4f + currentRhythmQuality * 0.6f)
            val advancement = 0.04f + (combinedQuality * 0.03f) // 0.04 à 0.07 par poussée
            skierX += advancement
            
            distance += advancement * screenDistance // Pour les stats
            backgroundOffset -= advancement * 200f
            
            previousPushTime = lastPushTime
            lastPushTime = currentTime
            pushCount++
            
            val overallPerformance = (currentPushQuality + currentRhythmQuality) / 2f
            performanceHistory.add(overallPerformance)
            if (performanceHistory.size > 15) {
                performanceHistory.removeAt(0)
            }
            
            currentFrame = if (newDirection == -1) leftFrame else rightFrame
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
                        distance < 0.03f -> 10
                        distance < 0.05f -> 8
                        distance < 0.07f -> 6
                        distance < 0.09f -> 4
                        distance < 0.11f -> 2
                        else -> 0
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
            currentScreen == 0 -> "🏁 ${tournamentData.playerNames[currentPlayerIndex]} | Préparation - ${5 - (System.currentTimeMillis() - preparationTimer) / 1000}s"
            gameState == GameState.SKIING -> "🎿 ${tournamentData.playerNames[currentPlayerIndex]} | ${getScreenName()} | Rythme: ${(rhythmBonus * 100).toInt()}%"
            gameState == GameState.SHOOTING -> "🎯 ${tournamentData.playerNames[currentPlayerIndex]} | Tir ${shotsFired}/5 | Score: ${totalScore} pts"
            gameState == GameState.FINAL_SKIING -> "🏁 ${tournamentData.playerNames[currentPlayerIndex]} | ${getScreenName()} | Sprint final!"
            gameState == GameState.FINISHED -> "📊 ${tournamentData.playerNames[currentPlayerIndex]} | Analyse des performances..."
            else -> "🎿 ${tournamentData.playerNames[currentPlayerIndex]} | ${getScreenName()}"
        }
    }

    private fun calculateScore(): Int {
        val shootingScore = totalScore
        val distanceBonus = 100 // Bonus pour avoir terminé
        val rhythmBonus = (averageRhythm * 100).toInt().coerceAtMost(50)
        val penaltyForMissedShots = (5 - targetsHit) * 15
        return maxOf(50, shootingScore + distanceBonus + rhythmBonus - penaltyForMissedShots)
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
        
        // NOUVEAU - Écran de préparation avec image et instructions
        private fun drawPreparationScreen(canvas: Canvas, w: Int, h: Int) {
            // Fond blanc
            paint.color = Color.WHITE
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Charger et afficher l'image biathlon_track.png
            try {
                val trackImage = BitmapFactory.decodeResource(context.resources, R.drawable.biathlon_track)
                val scaledTrack = Bitmap.createScaledBitmap(trackImage, w, h, true)
                canvas.drawBitmap(scaledTrack, 0f, 0f, null)
            } catch (e: Exception) {
                // Si l'image n'existe pas, fond bleu hivernal
                paint.color = Color.parseColor("#87CEEB")
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            
            // DRAPEAU DU PAYS en haut à gauche (GROS)
            val flagSize = 120f
            val flagX = 30f
            val flagY = 50f
            
            // Dessiner le drapeau selon le pays du joueur
            val playerCountry = tournamentData.playerCountries[currentPlayerIndex]
            drawCountryFlag(canvas, flagX, flagY, flagSize, playerCountry)
            
            // Temps restant en très gros
            val timeLeft = 5 - (System.currentTimeMillis() - preparationTimer) / 1000
            paint.color = Color.parseColor("#FF0000")
            paint.textSize = 120f
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            canvas.drawText("$timeLeft", w/2f, h * 0.25f, paint)
            
            // TITRE PRINCIPAL
            paint.color = Color.parseColor("#000080")
            paint.textSize = 60f
            canvas.drawText("🎿 BIATHLON 🎯", w/2f, h * 0.4f, paint)
            
            // INSTRUCTIONS EN GROS ET GRAS
            paint.color = Color.parseColor("#8B0000")
            paint.textSize = 45f
            paint.isFakeBoldText = true
            
            val instructions = listOf(
                "📱 TOURNEZ LE TÉLÉPHONE",
                "🔄 ALTERNEZ GAUCHE-DROITE",
                "🎯 VISEZ LE CENTRE ROUGE",
                "🏃 GARDEZ LE RYTHME!"
            )
            
            var yPos = h * 0.55f
            for (instruction in instructions) {
                canvas.drawText(instruction, w/2f, yPos, paint)
                yPos += 60f
            }
            
            // Message de départ
            paint.color = Color.parseColor("#006400")
            paint.textSize = 50f
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
                "⚡ Rythme moyen: ${(averageRhythm * 100).toInt()}%",
                "🏆 SCORE FINAL: ${calculateScore()} pts"
            )
            
            var yPosition = 200f
            for (stat in stats) {
                canvas.drawText(stat, w/2f, yPosition, paint)
                yPosition += 70f
            }
            
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
            
            // Instructions au début
            if (pushCount < 3 && (gameState == GameState.SKIING || gameState == GameState.FINAL_SKIING)) {
                paint.color = Color.BLACK
                paint.textSize = 50f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("🎿 TOURNEZ COMME UN VOLANT", w/2f, h * 0.15f, paint)
                
                paint.color = Color.RED
                paint.textSize = 40f
                canvas.drawText("+ ALTERNEZ GAUCHE-DROITE!", w/2f, h * 0.22f, paint)
            }
            
            // Barre de performance
            if (gameState == GameState.SKIING || gameState == GameState.FINAL_SKIING) {
                drawPerformanceBand(canvas, w * 0.1f, 80f, w * 0.4f, 60f)
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
                canvas.drawCircle(px, py, 50f, paint)
                
                paint.color = Color.WHITE
                paint.textSize = 30f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("+${targetScores[index]}", px, py + 10f, paint)
                
                paint.textSize = 20f
                canvas.drawText("TOUCHÉ", px, py + 70f, paint)
            } else {
                // Cercles concentriques
                paint.color = Color.parseColor("#FFFFFF")
                canvas.drawCircle(px, py, 50f, paint)
                
                paint.color = Color.parseColor("#000000")
                canvas.drawCircle(px, py, 40f, paint)
                
                paint.color = Color.parseColor("#FFFFFF")
                canvas.drawCircle(px, py, 30f, paint)
                
                paint.color = Color.parseColor("#000000")
                canvas.drawCircle(px, py, 20f, paint)
                
                paint.color = Color.parseColor("#FF0000")
                canvas.drawCircle(px, py, 10f, paint)
                
                paint.color = Color.WHITE
                paint.textSize = 16f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("${index + 1}", px, py + 70f, paint)
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
            canvas.drawText("🔫 Munitions: ${5-shotsFired}/5", 30f, h - 100f, paint)
            canvas.drawText("🎯 Score: $totalScore pts", 30f, h - 60f, paint)
            
            for (i in 0 until 5) {
                paint.color = if (i < shotsFired) Color.GRAY else Color.YELLOW
                canvas.drawRect(w - 250f + i * 40f, h - 70f, w - 220f + i * 40f, h - 40f, paint)
            }
        }
    }

    enum class GameState {
        SKIING, SHOOTING, FINAL_SKIING, FINISHED
    }
}
