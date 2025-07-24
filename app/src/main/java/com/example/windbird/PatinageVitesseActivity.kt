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

class PatinageVitesseActivity : Activity(), SensorEventListener {

    private lateinit var gameView: PatinageVitesseView
    private lateinit var statusText: TextView

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null

    // Variables de gameplay
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    private val preparationDuration = 5f
    private val countdownDuration = 3f
    private val raceDuration = 30f
    private val resultsDuration = 6f
    
    // Variables de course
    private var playerDistance = 0f
    private val totalDistance = 1500f
    private var playerSpeed = 0f
    
    // Animation et rythme - OPTIMISÉ
    private var playerAnimFrame = 0
    private var lastStrokeTime = 0L
    private var previousStrokeTime = 0L
    private var playerRhythm = 0f
    private var strokeCount = 0
    private var perfectStrokes = 0
    
    // Variables pour la bande de performance
    private var currentStrokeQuality = 0f
    private var currentRhythmQuality = 0f
    private var performanceHistory = mutableListOf<Float>()
    
    // Variables pour l'animation de victoire
    private var victoryAnimationProgress = 0f
    private var victoryAnimationStarted = false
    
    // Contrôles gyroscope - DÉTECTION AMÉLIORÉE
    private var tiltX = 0f
    private var lastTiltDirection = 0
    private var expectingLeft = true
    private var currentTiltState = TiltState.CENTER
    
    // OPTIMISATION : Seuils unifiés et calibration
    private val TILT_THRESHOLD = 0.5f  // Seuil unifié pour détection et état
    private val DETECTION_THRESHOLD = 0.6f  // Seuil plus élevé pour validation du mouvement
    private val MIN_STROKE_INTERVAL = 200L  // Minimum entre deux coups
    
    // OPTIMISATION : Variables pour réduire les calculs
    private var lastFrameTime = 0L
    private val TARGET_FRAME_INTERVAL = 40L // ~25 FPS pour les animations
    private var needsRedraw = true
    
    private var raceTime = 0f
    private var playerFinished = false
    private var finalScore = 0
    private var scoreCalculated = false

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
            text = "⛸️ PATINAGE VITESSE 1500M - ${tournamentData.playerNames[currentPlayerIndex]}"
            setTextColor(Color.WHITE)
            textSize = 24f
            setBackgroundColor(Color.parseColor("#000066"))
            setPadding(25, 20, 25, 20)
        }

        gameView = PatinageVitesseView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        
        initializeGame()
    }
    
    private fun initializeGame() {
        gameState = GameState.PREPARATION
        phaseTimer = 0f
        playerDistance = 0f
        playerSpeed = 0f
        playerAnimFrame = 0
        lastStrokeTime = 0L
        previousStrokeTime = 0L
        playerRhythm = 0f
        strokeCount = 0
        perfectStrokes = 0
        currentStrokeQuality = 0f
        currentRhythmQuality = 0f
        performanceHistory.clear()
        victoryAnimationProgress = 0f
        victoryAnimationStarted = false
        tiltX = 0f
        lastTiltDirection = 0
        expectingLeft = true
        currentTiltState = TiltState.CENTER
        raceTime = 0f
        playerFinished = false
        finalScore = 0
        scoreCalculated = false
        lastFrameTime = 0L
        needsRedraw = true
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

        // OPTIMISATION : Contrôle de framerate
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFrameTime < TARGET_FRAME_INTERVAL) {
            return
        }
        lastFrameTime = currentTime

        // AMÉLIORATION DÉTECTION : Filtrage du bruit gyroscope
        val filteredTiltX = if (abs(event.values[0]) > 0.1f) event.values[0] else 0f
        tiltX = tiltX * 0.8f + filteredTiltX * 0.2f // Lissage
        
        // AMÉLIORATION : Mise à jour de l'état d'inclinaison avec seuils unifiés
        val newTiltState = when {
            tiltX < -TILT_THRESHOLD -> TiltState.LEFT
            tiltX > TILT_THRESHOLD -> TiltState.RIGHT
            else -> TiltState.CENTER
        }
        
        // OPTIMISATION : Ne redessiner que si l'état change
        if (newTiltState != currentTiltState) {
            currentTiltState = newTiltState
            needsRedraw = true
        }

        phaseTimer += 0.025f
        if (gameState == GameState.RACE) {
            raceTime += 0.025f
        }

        when (gameState) {
            GameState.PREPARATION -> handlePreparation()
            GameState.COUNTDOWN -> handleCountdown() 
            GameState.RACE -> handleRace()
            GameState.RESULTS -> {
                handleResults()
                updateVictoryAnimation()
            }
            GameState.FINISHED -> {}
        }

        updateStatus()
        
        // OPTIMISATION : Redessiner seulement si nécessaire
        if (needsRedraw) {
            gameView.invalidate()
            needsRedraw = false
        }
    }
    
    private fun handlePreparation() {
        if (phaseTimer >= preparationDuration) {
            gameState = GameState.PREPARATION
            phaseTimer = 0f
            needsRedraw = true
        }
    }
    
    private fun handleCountdown() {
        if (phaseTimer >= countdownDuration) {
            gameState = GameState.RACE
            phaseTimer = 0f
            raceTime = 0f
            needsRedraw = true
        }
    }
    
    private fun handleRace() {
        if (playerFinished) {
            calculateFinalScore()
            gameState = GameState.RESULTS
            phaseTimer = 0f
            needsRedraw = true
            return
        }
        
        handleRhythmicSkating()
        updatePositions()
        checkFinishLine()
    }
    
    // LOGIQUE AMÉLIORÉE du rythme avec détection optimisée
    private fun handleRhythmicSkating() {
        val currentTime = System.currentTimeMillis()
        
        // AMÉLIORATION : Vérification d'intervalle minimum plus robuste
        if (currentTime - lastStrokeTime <= MIN_STROKE_INTERVAL) {
            return
        }
        
        var strokeDetected = false
        var strokeQuality = 0f
        
        // AMÉLIORATION DÉTECTION : Seuils plus précis et validation croisée
        if (expectingLeft && tiltX < -DETECTION_THRESHOLD && currentTiltState == TiltState.LEFT) {
            strokeDetected = true
            strokeQuality = calculateStrokeQuality(tiltX)
            expectingLeft = false
            lastTiltDirection = -1
            
        } else if (!expectingLeft && tiltX > DETECTION_THRESHOLD && currentTiltState == TiltState.RIGHT) {
            strokeDetected = true
            strokeQuality = calculateStrokeQuality(tiltX)
            expectingLeft = true
            lastTiltDirection = 1
        }
        
        if (strokeDetected) {
            // CORRECTION : Calcul d'intervalle fixé
            val intervalSinceLastStroke = if (previousStrokeTime > 0) {
                currentTime - previousStrokeTime
            } else {
                500L // Premier coup - intervalle de référence
            }
            
            // Mettre à jour les temps CORRECTEMENT
            previousStrokeTime = lastStrokeTime
            lastStrokeTime = currentTime
            strokeCount++
            
            // Animation
            playerAnimFrame = (playerAnimFrame + 1) % 8
            
            // CALCUL AMÉLIORÉ du rythme
            val rhythmQuality = calculateRhythmQuality(intervalSinceLastStroke)
            currentStrokeQuality = strokeQuality
            currentRhythmQuality = rhythmQuality
            
            // Mise à jour du rythme global avec pondération améliorée
            playerRhythm = (playerRhythm * 0.75f + rhythmQuality * 0.25f).coerceIn(0f, 1f)
            
            // Gain de vitesse optimisé
            val combinedQuality = (strokeQuality * 0.6f + rhythmQuality * 0.4f)
            val speedGain = combinedQuality * 1.5f
            
            playerSpeed += speedGain
            playerSpeed = playerSpeed.coerceAtMost(7f)
            
            // OPTIMISATION : Gérer l'historique plus efficacement
            performanceHistory.add((strokeQuality + rhythmQuality) / 2f)
            if (performanceHistory.size > 20) {
                performanceHistory.removeFirst() // Plus efficace que removeAt(0)
            }
            
            // Bonus pour coups parfaits
            if (strokeQuality > 0.7f && rhythmQuality > 0.7f) {
                perfectStrokes++
            }
            
            needsRedraw = true
        }
        
        // Décélération naturelle
        playerSpeed *= 0.995f
    }
    
    // AMÉLIORATION : Calcul de qualité plus précis
    private fun calculateStrokeQuality(tilt: Float): Float {
        val amplitude = abs(tilt)
        return when {
            amplitude >= 1.2f -> 1f        // Excellent - mouvement très ample
            amplitude >= 1.0f -> 0.9f      // Très bon
            amplitude >= 0.8f -> 0.75f     // Bon
            amplitude >= 0.6f -> 0.6f      // Correct (seuil de détection)
            amplitude >= 0.4f -> 0.4f      // Faible
            else -> 0.2f                   // Très faible
        }.coerceIn(0f, 1f)
    }
    
    // AMÉLIORATION : Calcul du rythme avec tolérances ajustées
    private fun calculateRhythmQuality(intervalMs: Long): Float {
        // Rythme idéal : 400-600ms entre les coups avec tolérance élargie
        return when {
            intervalMs < 200L -> 0.1f      // Beaucoup trop rapide
            intervalMs < 300L -> 0.4f      // Trop rapide
            intervalMs < 350L -> 0.7f      // Un peu rapide mais acceptable
            intervalMs in 350L..650L -> 1f  // PARFAIT
            intervalMs < 800L -> 0.8f      // Un peu lent mais bon
            intervalMs < 1000L -> 0.5f     // Lent
            intervalMs < 1500L -> 0.3f     // Trop lent
            else -> 0.1f                   // Beaucoup trop lent
        }.coerceIn(0f, 1f)
    }
    
    private fun updatePositions() {
        if (!playerFinished) {
            playerDistance += playerSpeed * 0.025f * 20f
            needsRedraw = true
        }
    }
    
    private fun checkFinishLine() {
        if (playerDistance >= totalDistance && !playerFinished) {
            playerFinished = true
            needsRedraw = true
        }
    }
    
    private fun handleResults() {
        if (!victoryAnimationStarted) {
            victoryAnimationStarted = true
            victoryAnimationProgress = 0f
        }
        
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
    
    // OPTIMISATION : Animation de victoire avec contrôle de framerate
    private fun updateVictoryAnimation() {
        if (victoryAnimationStarted && victoryAnimationProgress < 1f) {
            victoryAnimationProgress += 0.015f
            victoryAnimationProgress = victoryAnimationProgress.coerceAtMost(1f)
            needsRedraw = true
        }
    }
    
    private fun calculateFinalScore() {
        if (!scoreCalculated) {
            val timeBonus = maxOf(0, 450 - raceTime.toInt()) * 1
            val rhythmBonus = (playerRhythm * 120).toInt()
            val perfectStrokeBonus = perfectStrokes * 8
            val completionBonus = if (playerFinished) 80 else 0
            
            finalScore = maxOf(120, timeBonus + rhythmBonus + perfectStrokeBonus + completionBonus)
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
                val aiScore = (90..160).random()
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
            GameState.PREPARATION -> "⛸️ ${tournamentData.playerNames[currentPlayerIndex]} | Préparation... ${(preparationDuration - phaseTimer).toInt() + 1}s"
            GameState.COUNTDOWN -> "🚨 ${tournamentData.playerNames[currentPlayerIndex]} | Décompte... ${(countdownDuration - phaseTimer).toInt() + 1}"
            GameState.RACE -> "⛸️ ${tournamentData.playerNames[currentPlayerIndex]} | ${playerDistance.toInt()}m/${totalDistance.toInt()}m | Rythme: ${(playerRhythm * 100).toInt()}% | Coups: ${strokeCount}"
            GameState.RESULTS -> "🏆 ${tournamentData.playerNames[currentPlayerIndex]} | Temps: ${raceTime.toInt()}s | Score: ${finalScore}"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Course terminée!"
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

    inner class PatinageVitesseView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        // OPTIMISATION : Cache des bitmaps pour éviter les fuites mémoire
        private var preparationBackground: Bitmap? = null
        private var flagCanadaBitmap: Bitmap? = null
        private var flagUsaBitmap: Bitmap? = null
        private var flagFranceBitmap: Bitmap? = null
        private var flagNorvegeBitmap: Bitmap? = null
        private var flagJapanBitmap: Bitmap? = null
        private var speedskatingLeftBitmap: Bitmap? = null
        private var speedskatingRightBitmap: Bitmap? = null
        private var speedskatingFrontLeftBitmap: Bitmap? = null
        private var speedskatingFrontRightBitmap: Bitmap? = null
        private var speedskateHappy1Bitmap: Bitmap? = null
        private var speedskateHappy2Bitmap: Bitmap? = null
        
        // OPTIMISATION : Cache des objets réutilisables
        private val reusableRectF = RectF()
        private val reusableRect = Rect()
        
        init {
            loadBitmaps()
        }
        
        // OPTIMISATION : Chargement des bitmaps séparé et avec gestion d'erreur
        private fun loadBitmaps() {
            try {
                preparationBackground = BitmapFactory.decodeResource(resources, R.drawable.speekskating_preparation)
                speedskatingLeftBitmap = BitmapFactory.decodeResource(resources, R.drawable.speedskating_left)
                speedskatingRightBitmap = BitmapFactory.decodeResource(resources, R.drawable.speedskating_right)
                speedskatingFrontLeftBitmap = BitmapFactory.decodeResource(resources, R.drawable.speedskating_front_left)
                speedskatingFrontRightBitmap = BitmapFactory.decodeResource(resources, R.drawable.speedskating_front_right)
                flagCanadaBitmap = BitmapFactory.decodeResource(resources, R.drawable.flag_canada)
                flagUsaBitmap = BitmapFactory.decodeResource(resources, R.drawable.flag_usa)
                flagFranceBitmap = BitmapFactory.decodeResource(resources, R.drawable.flag_france)
                flagNorvegeBitmap = BitmapFactory.decodeResource(resources, R.drawable.flag_norvege)
                flagJapanBitmap = BitmapFactory.decodeResource(resources, R.drawable.flag_japan)
                speedskateHappy1Bitmap = BitmapFactory.decodeResource(resources, R.drawable.speedskate_happy1)
                speedskateHappy2Bitmap = BitmapFactory.decodeResource(resources, R.drawable.speedskate_happy2)
            } catch (e: Exception) {
                // Les bitmaps resteront null, le fallback sera utilisé
            }
        }
        
        // OPTIMISATION : Nettoyage des ressources
        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            preparationBackground?.recycle()
            flagCanadaBitmap?.recycle()
            flagUsaBitmap?.recycle()
            flagFranceBitmap?.recycle()
            flagNorvegeBitmap?.recycle()
            flagJapanBitmap?.recycle()
            speedskatingLeftBitmap?.recycle()
            speedskatingRightBitmap?.recycle()
            speedskatingFrontLeftBitmap?.recycle()
            speedskatingFrontRightBitmap?.recycle()
            speedskateHappy1Bitmap?.recycle()
            speedskateHappy2Bitmap?.recycle()
        }

        private fun getPlayerFlagBitmap(): Bitmap? {
            if (practiceMode) {
                return flagCanadaBitmap
            }
            
            val playerCountry = tournamentData.playerCountries[currentPlayerIndex]
            return when (playerCountry.uppercase()) {
                "CANADA" -> flagCanadaBitmap
                "USA", "ÉTATS-UNIS", "ETATS-UNIS" -> flagUsaBitmap
                "FRANCE" -> flagFranceBitmap
                "NORVÈGE", "NORWAY" -> flagNorvegeBitmap
                "JAPON", "JAPAN" -> flagJapanBitmap
                else -> flagCanadaBitmap
            }
        }

        override fun onDraw(canvas: Canvas) {
            val w = width
            val h = height
            
            when (gameState) {
                GameState.PREPARATION -> drawPreparation(canvas, w, h)
                GameState.COUNTDOWN -> drawCountdown(canvas, w, h)
                GameState.RACE -> drawRace(canvas, w, h)
                GameState.RESULTS -> drawResults(canvas, w, h)
                GameState.FINISHED -> drawResults(canvas, w, h)
            }
        }
        
        private fun drawPreparation(canvas: Canvas, w: Int, h: Int) {
            preparationBackground?.let { bg ->
                reusableRectF.set(0f, 0f, w.toFloat(), h.toFloat())
                canvas.drawBitmap(bg, null, reusableRectF, paint)
            } ?: run {
                paint.color = Color.parseColor("#87CEEB")
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.color = Color.WHITE
                canvas.drawRect(0f, h.toFloat() * 0.3f, w.toFloat(), h.toFloat(), paint)
                paint.color = Color.BLACK
                paint.strokeWidth = 4f
                canvas.drawLine(0f, h.toFloat() * 0.65f, w.toFloat(), h.toFloat() * 0.65f, paint)
            }
            
            // Drapeau et infos pays
            val playerCountry = if (practiceMode) "CANADA" else tournamentData.playerCountries[currentPlayerIndex]
            val flagBitmap = getPlayerFlagBitmap()
            
            flagBitmap?.let { flag ->
                val flagWidth = 180f
                val flagHeight = 120f
                val flagX = 30f
                val flagY = 30f
                
                reusableRectF.set(flagX, flagY, flagX + flagWidth, flagY + flagHeight)
                canvas.drawBitmap(flag, null, reusableRectF, paint)
                
                paint.color = Color.parseColor("#FFD700")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f
                canvas.drawRect(reusableRectF, paint)
                paint.style = Paint.Style.FILL
                
                paint.color = Color.parseColor("#000066")
                paint.textSize = 24f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(playerCountry.uppercase(), flagX + flagWidth/2f, flagY + flagHeight + 35f, paint)
            }
            
            // Instructions
            paint.color = Color.parseColor("#000066")
            paint.textSize = 44f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("⛸️ PATINAGE VITESSE 1500M ⛸️", w.toFloat()/2f, h.toFloat() * 0.08f, paint)
            
            paint.color = Color.parseColor("#FF0000")
            paint.textSize = 36f
            canvas.drawText("📱 ALTERNEZ GAUCHE-DROITE", w.toFloat()/2f, h.toFloat() * 0.15f, paint)
            
            paint.color = Color.parseColor("#0000FF")
            paint.textSize = 28f
            canvas.drawText("⏱️ RYTHME IDÉAL: 1 poussée toutes les 0.5 secondes", w.toFloat()/2f, h.toFloat() * 0.22f, paint)
            canvas.drawText("💪 FORT + RÉGULIER = Plus rapide!", w.toFloat()/2f, h.toFloat() * 0.28f, paint)
            
            paint.color = Color.parseColor("#FF6600")
            paint.textSize = 32f
            canvas.drawText("TESTEZ VOTRE RYTHME:", w.toFloat()/2f, h.toFloat() * 0.36f, paint)
            
            val countdown = (preparationDuration - phaseTimer).toInt() + 1
            paint.textSize = 60f
            paint.color = Color.RED
            canvas.drawText("DÉBUT DANS ${countdown}s", w.toFloat()/2f, h.toFloat() * 0.55f, paint)
        }
        
        private fun drawCountdown(canvas: Canvas, w: Int, h: Int) {
            drawRaceBackground(canvas, w, h)
            
            val count = (countdownDuration - phaseTimer).toInt() + 1
            paint.textSize = 120f
            paint.color = Color.RED
            paint.textAlign = Paint.Align.CENTER
            
            val countText = if (count > 0) count.toString() else "GO!"
            canvas.drawText(countText, w.toFloat()/2f, h.toFloat()/2f, paint)
        }
        
        private fun drawRace(canvas: Canvas, w: Int, h: Int) {
            drawRaceBackground(canvas, w, h)
            drawSkaterOnTrack(canvas, w, h)
            drawFrontView(canvas, w, h)
            drawHUD(canvas, w, h)
            drawPerformanceBand(canvas, 50f, h.toFloat() - 200f, w.toFloat() * 0.5f, 40f)
        }
        
        // OPTIMISATION : Barre de performance optimisée
        private fun drawPerformanceBand(canvas: Canvas, x: Float, y: Float, width: Float, height: Float) {
            // Fond de la bande
            paint.color = Color.parseColor("#333333")
            paint.style = Paint.Style.FILL
            reusableRectF.set(x, y, x + width, y + height)
            canvas.drawRect(reusableRectF, paint)
            
            // Performance en temps réel optimisée
            val halfWidth = width / 2f
            
            // Qualité du coup actuel (moitié gauche)
            paint.color = getPerformanceColor(currentStrokeQuality)
            reusableRectF.set(x, y, x + halfWidth, y + height)
            canvas.drawRect(reusableRectF, paint)
            
            // Qualité du rythme actuel (moitié droite)
            paint.color = getPerformanceColor(currentRhythmQuality)
            reusableRectF.set(x + halfWidth, y, x + width, y + height)
            canvas.drawRect(reusableRectF, paint)
            
            // Labels et valeurs
            paint.color = Color.WHITE
            paint.textSize = 14f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("COUP", x + halfWidth/2f, y - 5f, paint)
            canvas.drawText("RYTHME", x + halfWidth + halfWidth/2f, y - 5f, paint)
            
            paint.color = Color.BLACK
            paint.textSize = 12f
            canvas.drawText("${(currentStrokeQuality * 100).toInt()}%", x + halfWidth/2f, y + height/2f + 4f, paint)
            canvas.drawText("${(currentRhythmQuality * 100).toInt()}%", x + halfWidth + halfWidth/2f, y + height/2f + 4f, paint)
            
            // Bordure
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            reusableRectF.set(x, y, x + width, y + height)
            canvas.drawRect(reusableRectF, paint)
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
        
        private fun drawRaceBackground(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#87CEEB")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat() * 0.3f, paint)
            
            val iceGradient = LinearGradient(0f, h.toFloat() * 0.3f, 0f, h.toFloat(),
                Color.WHITE, Color.parseColor("#F0F8FF"), Shader.TileMode.CLAMP)
            paint.shader = iceGradient
            canvas.drawRect(0f, h.toFloat() * 0.3f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
            
            paint.color = Color.parseColor("#CCCCCC")
            paint.strokeWidth = 3f
            val laneTop = h.toFloat() * 0.45f
            val laneBottom = h.toFloat() * 0.85f
            canvas.drawLine(0f, laneTop, w.toFloat(), laneTop, paint)
            canvas.drawLine(0f, laneBottom, w.toFloat(), laneBottom, paint)
            
            val scrollOffset = playerDistance % 50f
            paint.color = Color.parseColor("#DDDDDD")
            paint.strokeWidth = 2f
            
            for (i in -2..12) {
                val x = w.toFloat() * 0.1f + i * (w.toFloat() * 0.8f / 10f) - scrollOffset * (w.toFloat() * 0.8f / 50f)
                if (x > -20f && x < w + 20f) {
                    canvas.drawLine(x, h.toFloat() * 0.3f, x, h.toFloat(), paint)
                    
                    val distance = (playerDistance + i * 150f).toInt()
                    if (distance >= 0 && distance <= 1600) {
                        paint.textSize = 12f
                        paint.color = Color.BLACK
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText("${distance}m", x, h.toFloat() * 0.28f, paint)
                    }
                }
            }
        }
        
        private fun drawSkaterOnTrack(canvas: Canvas, w: Int, h: Int) {
            val playerX = w.toFloat() * 0.1f + (playerDistance / totalDistance) * (w.toFloat() * 0.8f)
            val playerY = h.toFloat() * 0.65f
            
            val skaterImage = when (currentTiltState) {
                TiltState.LEFT -> speedskatingLeftBitmap
                TiltState.RIGHT -> speedskatingRightBitmap
                else -> speedskatingLeftBitmap
            }
            
            skaterImage?.let { image ->
                val scale = 0.4f
                val imageWidth = image.width * scale
                val imageHeight = image.height * scale
                
                reusableRectF.set(
                    playerX - imageWidth/2f,
                    playerY - imageHeight/2f,
                    playerX + imageWidth/2f,
                    playerY + imageHeight/2f
                )
                canvas.drawBitmap(image, null, reusableRectF, paint)
            } ?: run {
                paint.color = Color.parseColor("#0066FF")
                paint.style = Paint.Style.FILL
                canvas.drawCircle(playerX, playerY, 20f, paint)
            }
            
            paint.color = Color.parseColor("#40000000")
            paint.style = Paint.Style.FILL
            canvas.drawOval(playerX - 15f, playerY + 25f, playerX + 15f, playerY + 30f, paint)
        }
        
        private fun drawFrontView(canvas: Canvas, w: Int, h: Int) {
            val viewWidth = w.toFloat() * 0.35f
            val viewHeight = h.toFloat() * 0.45f
            val viewX = w.toFloat() - viewWidth - 20f
            val viewY = 20f
            
            paint.color = Color.parseColor("#000066")
            paint.style = Paint.Style.FILL
            reusableRectF.set(viewX, viewY, viewX + viewWidth, viewY + viewHeight)
            canvas.drawRect(reusableRectF, paint)
            
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawRect(reusableRectF, paint)
            paint.style = Paint.Style.FILL
            
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("VUE DE FACE", viewX + viewWidth/2f, viewY + 20f, paint)
            
            val frontImage = when (currentTiltState) {
                TiltState.LEFT -> speedskatingFrontLeftBitmap
                TiltState.RIGHT -> speedskatingFrontRightBitmap
                else -> speedskatingFrontLeftBitmap
            }
            
            frontImage?.let { image ->
                val imageMargin = 20f
                val availableWidth = viewWidth - imageMargin * 2f
                val availableHeight = viewHeight - 50f
                
                val scaleX = availableWidth / image.width
                val scaleY = availableHeight / image.height
                val scale = minOf(scaleX, scaleY) * 0.8f
                
                val imageWidth = image.width * scale
                val imageHeight = image.height * scale
                
                val imageX = viewX + (viewWidth - imageWidth) / 2f
                val imageY = viewY + 40f + (availableHeight - imageHeight) / 2f
                
                reusableRectF.set(imageX, imageY, imageX + imageWidth, imageY + imageHeight)
                canvas.drawBitmap(image, null, reusableRectF, paint)
            } ?: run {
                paint.color = Color.YELLOW
                paint.style = Paint.Style.FILL
                canvas.drawCircle(viewX + viewWidth/2f, viewY + viewHeight/2f, 30f, paint)
            }
            
            // Prochaine direction avec code couleur
            paint.textSize = 20f
            paint.textAlign = Paint.Align.CENTER
            paint.color = if (expectingLeft) Color.parseColor("#FF6666") else Color.parseColor("#66FF66")
            val nextMove = if (expectingLeft) "⬅️ GAUCHE" else "➡️ DROITE"
            canvas.drawText(nextMove, viewX + viewWidth/2f, viewY + viewHeight - 15f, paint)
            
            // Rythme avec code couleur
            paint.color = getPerformanceColor(playerRhythm)
            paint.textSize = 14f
            canvas.drawText("RYTHME: ${(playerRhythm * 100).toInt()}%", viewX + viewWidth/2f, viewY + viewHeight - 35f, paint)
        }
        
        private fun drawHUD(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            paint.textSize = 28f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("${(totalDistance - playerDistance).toInt()}m restants", 30f, h.toFloat() - 250f, paint)
            
            paint.textSize = 24f
            canvas.drawText("Temps: ${raceTime.toInt()}s", 30f, h.toFloat() - 220f, paint)
            
            paint.textSize = 20f
            canvas.drawText("Parfaits: $perfectStrokes", 30f, h.toFloat() - 120f, paint)
            canvas.drawText("Total coups: $strokeCount", 30f, h.toFloat() - 100f, paint)
            
            // Barre de progression
            val progressBarX = 50f
            val progressBarY = h.toFloat() - 160f
            val progressBarWidth = w.toFloat() * 0.5f
            val progressBarHeight = 25f
            
            paint.color = Color.parseColor("#333333")
            reusableRectF.set(progressBarX, progressBarY, progressBarX + progressBarWidth, progressBarY + progressBarHeight)
            canvas.drawRect(reusableRectF, paint)
            
            val progress = (playerDistance / totalDistance).coerceIn(0f, 1f)
            paint.color = Color.parseColor("#00FF00")
            reusableRectF.set(progressBarX, progressBarY, progressBarX + progressBarWidth * progress, progressBarY + progressBarHeight)
            canvas.drawRect(reusableRectF, paint)
            
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            reusableRectF.set(progressBarX, progressBarY, progressBarX + progressBarWidth, progressBarY + progressBarHeight)
            canvas.drawRect(reusableRectF, paint)
            paint.style = Paint.Style.FILL
            
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("PROGRESSION: ${playerDistance.toInt()}m / ${totalDistance.toInt()}m", 
                           progressBarX, progressBarY - 10f, paint)
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            val timeQuality = when {
                raceTime < 90f -> "EXCELLENT"
                raceTime < 120f -> "BON"
                raceTime < 150f -> "MOYEN"
                else -> "LENT"
            }
            
            val bgColor = when (timeQuality) {
                "EXCELLENT" -> Color.parseColor("#FFD700")
                "BON" -> Color.parseColor("#C0C0C0")
                "MOYEN" -> Color.parseColor("#CD7F32")
                else -> Color.parseColor("#808080")
            }
            
            val gradient = LinearGradient(0f, 0f, 0f, h.toFloat(),
                bgColor, Color.parseColor("#FFF8DC"), Shader.TileMode.CLAMP)
            paint.shader = gradient
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
            
            paint.color = Color.parseColor("#8B0000")
            paint.textSize = 48f
            paint.textAlign = Paint.Align.CENTER
            
            val resultText = "TEMPS: ${raceTime.toInt()}s - $timeQuality"
            canvas.drawText(resultText, w.toFloat()/2f, h.toFloat() * 0.15f, paint)
            
            paint.textSize = 80f
            canvas.drawText("${finalScore} POINTS", w.toFloat()/2f, h.toFloat() * 0.28f, paint)
            
            paint.textSize = 32f
            paint.color = Color.parseColor("#333333")
            
            canvas.drawText("📏 Distance: ${playerDistance.toInt()}m / ${totalDistance.toInt()}m", w.toFloat()/2f, h.toFloat() * 0.45f, paint)
            canvas.drawText("🎵 Rythme moyen: ${(playerRhythm * 100).toInt()}%", w.toFloat()/2f, h.toFloat() * 0.52f, paint)
            canvas.drawText("⭐ Coups parfaits: $perfectStrokes", w.toFloat()/2f, h.toFloat() * 0.59f, paint)
            canvas.drawText("🏃 Coups totaux: $strokeCount", w.toFloat()/2f, h.toFloat() * 0.66f, paint)
            
            paint.textSize = 24f
            paint.color = Color.parseColor("#666666")
            canvas.drawText("< 1.5min: Excellent | 1.5-2min: Bon", w.toFloat()/2f, h.toFloat() * 0.78f, paint)
            canvas.drawText("2-2.5min: Moyen | > 2.5min: Lent", w.toFloat()/2f, h.toFloat() * 0.83f, paint)
            
            paint.textSize = 28f
            paint.color = when (timeQuality) {
                "EXCELLENT" -> Color.parseColor("#FFD700")
                "BON" -> Color.parseColor("#32CD32")
                "MOYEN" -> Color.parseColor("#FF8C00")
                else -> Color.parseColor("#FF4500")
            }
            
            val encouragement = when (timeQuality) {
                "EXCELLENT" -> "🏆 PERFORMANCE OLYMPIQUE!"
                "BON" -> "💪 TRÈS BIEN JOUÉ!"
                "MOYEN" -> "👍 PAS MAL, CONTINUEZ!"
                else -> "🔥 ENTRAÎNEZ-VOUS ENCORE!"
            }
            canvas.drawText(encouragement, w.toFloat()/2f, h.toFloat() * 0.92f, paint)
            
            drawVictoryAnimation(canvas, w, h)
        }
        
        private fun drawVictoryAnimation(canvas: Canvas, w: Int, h: Int) {
            if (!victoryAnimationStarted) return
            
            val centerX = w.toFloat() / 2f
            val centerY = h.toFloat() * 0.7f
            
            when {
                victoryAnimationProgress < 0.6f -> {
                    val progress1 = victoryAnimationProgress / 0.6f
                    val skaterX = -100f + progress1 * (w.toFloat() * 0.25f + 100f)
                    
                    speedskateHappy1Bitmap?.let { image ->
                        val scale = 0.8f
                        val imageWidth = image.width * scale
                        val imageHeight = image.height * scale
                        
                        reusableRectF.set(
                            skaterX - imageWidth/2f,
                            centerY - imageHeight/2f,
                            skaterX + imageWidth/2f,
                            centerY + imageHeight/2f
                        )
                        canvas.drawBitmap(image, null, reusableRectF, paint)
                    }
                }
                else -> {
                    val progress2 = (victoryAnimationProgress - 0.6f) / 0.4f
                    val startX = w.toFloat() * 0.25f
                    val skaterX = startX + progress2 * (centerX - startX)
                    
                    speedskateHappy2Bitmap?.let { image ->
                        val scale = 0.8f
                        val imageWidth = image.width * scale
                        val imageHeight = image.height * scale
                        
                        reusableRectF.set(
                            skaterX - imageWidth/2f,
                            centerY - imageHeight/2f,
                            skaterX + imageWidth/2f,
                            centerY + imageHeight/2f
                        )
                        canvas.drawBitmap(image, null, reusableRectF, paint)
                    }
                }
            }
        }
    }

    enum class GameState {
        PREPARATION, COUNTDOWN, RACE, RESULTS, FINISHED
    }
    
    enum class TiltState {
        LEFT, RIGHT, CENTER
    }
}
