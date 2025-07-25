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
            text = "⛸️ PATINAGE VITESSE 1500M"
            setTextColor(Color.WHITE)
            textSize = 22f  // Réduit de 28f à 22f (20% plus petit)
            setTypeface(null, Typeface.BOLD)
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
    
    // CORRECTION : Fonction corrigée pour passer directement à la course
    private fun handlePreparation() {
        if (phaseTimer >= preparationDuration) {
            gameState = GameState.RACE  // CORRECTION : Passer directement à la course
            phaseTimer = 0f
            raceTime = 0f
            needsRedraw = true
        }
    }
    
    private fun handleCountdown() {
        // Cette fonction n'est plus utilisée car on passe directement à la course
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
                putExtra("tournament_data", tournamentData as java.io.Serializable)
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
                    putExtra("tournament_data", tournamentData as java.io.Serializable)
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
                    putExtra("tournament_data", tournamentData as java.io.Serializable)
                    putExtra("tournament_final", true)
                }
                startActivity(resultIntent)
                finish()
            } else {
                val resultIntent = Intent(this, ScoreboardActivity::class.java).apply {
                    putExtra("tournament_data", tournamentData as java.io.Serializable)
                    putExtra("event_completed", eventIndex)
                }
                startActivity(resultIntent)
                finish()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateStatus() {
        val playerCountry = if (practiceMode) "CANADA" else tournamentData.playerCountries[currentPlayerIndex]
        statusText.text = when (gameState) {
            GameState.PREPARATION -> "⛸️ $playerCountry | Préparation... ${(preparationDuration - phaseTimer).toInt() + 1}s"
            GameState.RACE -> "⛸️ $playerCountry | ${playerDistance.toInt()}m/${totalDistance.toInt()}m | Rythme: ${(playerRhythm * 100).toInt()}%"
            GameState.RESULTS -> "🏆 $playerCountry | Temps: ${raceTime.toInt()}s | Score: ${finalScore}"
            GameState.FINISHED -> "✅ $playerCountry | Course terminée!"
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
            
            // Drapeau et infos pays - PLUS GROS
            val playerCountry = if (practiceMode) "CANADA" else tournamentData.playerCountries[currentPlayerIndex]
            val flagBitmap = getPlayerFlagBitmap()
            
            flagBitmap?.let { flag ->
                val flagWidth = 280f  // Augmenté de 200f à 280f
                val flagHeight = 190f  // Augmenté de 140f à 190f
                val flagX = 50f
                val flagY = 50f
                
                reusableRectF.set(flagX, flagY, flagX + flagWidth, flagY + flagHeight)
                canvas.drawBitmap(flag, null, reusableRectF, paint)
                
                paint.color = Color.parseColor("#FFD700")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 10f  // Bordure plus épaisse
                canvas.drawRect(reusableRectF, paint)
                paint.style = Paint.Style.FILL
                
                paint.color = Color.parseColor("#000066")
                paint.textSize = 44f  // Augmenté de 32f à 44f
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(playerCountry.uppercase(), flagX + flagWidth/2f, flagY + flagHeight + 55f, paint)
            }
            
            // Instructions - TEXTE ENCORE PLUS GROS
            paint.color = Color.parseColor("#000066")
            paint.textSize = 62f  // Augmenté de 52f à 62f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("⛸️ PATINAGE VITESSE 1500M ⛸️", w.toFloat()/2f, h.toFloat() * 0.08f, paint)
            
            paint.color = Color.parseColor("#FF0000")
            paint.textSize = 50f  // Augmenté de 42f à 50f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("📱 ALTERNEZ GAUCHE-DROITE", w.toFloat()/2f, h.toFloat() * 0.15f, paint)
            
            paint.color = Color.parseColor("#0000FF")
            paint.textSize = 38f  // Augmenté de 32f à 38f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("⏱️ RYTHME IDÉAL: 1 poussée toutes les 0.5 secondes", w.toFloat()/2f, h.toFloat() * 0.22f, paint)
            canvas.drawText("💪 FORT + RÉGULIER = Plus rapide!", w.toFloat()/2f, h.toFloat() * 0.28f, paint)
            
            paint.color = Color.parseColor("#FF6600")
            paint.textSize = 46f  // Augmenté de 38f à 46f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("TESTEZ VOTRE RYTHME:", w.toFloat()/2f, h.toFloat() * 0.36f, paint)
        }
        
        private fun drawRace(canvas: Canvas, w: Int, h: Int) {
            drawRaceBackground(canvas, w, h)
            drawSkaterOnTrack(canvas, w, h)
            drawFrontView(canvas, w, h)
            drawHUD(canvas, w, h)
            // Barres repositionnées plus bas et centrées
            val barsY = h.toFloat() - 160f  // Plus bas (était à -220f)
            val barsX = w.toFloat() * 0.25f  // Centrées (25% depuis la gauche)
            val barsWidth = w.toFloat() * 0.5f  // 50% de la largeur de l'écran
            
            // Barre de progression juste au-dessus de la barre de performance
            val progressBarHeight = 25f
            val progressBarY = barsY - progressBarHeight - 15f  // 15px d'espacement
            drawProgressBar(canvas, barsX, progressBarY, barsWidth, progressBarHeight)
            
            drawPerformanceBand(canvas, barsX, barsY, barsWidth, 50f)
        }
        
        // Nouvelle fonction pour la barre de progression séparée
        private fun drawProgressBar(canvas: Canvas, x: Float, y: Float, width: Float, height: Float) {
            paint.color = Color.parseColor("#333333")
            reusableRectF.set(x, y, x + width, y + height)
            canvas.drawRect(reusableRectF, paint)
            
            val progress = (playerDistance / totalDistance).coerceIn(0f, 1f)
            paint.color = Color.parseColor("#00FF00")
            reusableRectF.set(x, y, x + width * progress, y + height)
            canvas.drawRect(reusableRectF, paint)
            
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            reusableRectF.set(x, y, x + width, y + height)
            canvas.drawRect(reusableRectF, paint)
            paint.style = Paint.Style.FILL
            
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${playerDistance.toInt()}m / ${totalDistance.toInt()}m", 
                           x + width/2f, y - 8f, paint)
        }
        
        // OPTIMISATION : Barre de performance optimisée avec texte plus gros
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
            
            // Labels et valeurs - TEXTE PLUS GROS ET GRAS
            paint.color = Color.WHITE
            paint.textSize = 20f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("COUP", x + halfWidth/2f, y - 8f, paint)
            canvas.drawText("RYTHME", x + halfWidth + halfWidth/2f, y - 8f, paint)
            
            paint.color = Color.BLACK
            paint.textSize = 18f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("${(currentStrokeQuality * 100).toInt()}%", x + halfWidth/2f, y + height/2f + 6f, paint)
            canvas.drawText("${(currentRhythmQuality * 100).toInt()}%", x + halfWidth + halfWidth/2f, y + height/2f + 6f, paint)
            
            // Bordure
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
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
            paint.strokeWidth = 4f
            val laneTop = h.toFloat() * 0.45f
            val laneBottom = h.toFloat() * 0.85f
            canvas.drawLine(0f, laneTop, w.toFloat(), laneTop, paint)
            canvas.drawLine(0f, laneBottom, w.toFloat(), laneBottom, paint)
            
            val scrollOffset = playerDistance % 50f
            paint.color = Color.parseColor("#DDDDDD")
            paint.strokeWidth = 3f
            
            for (i in -2..12) {
                val x = w.toFloat() * 0.1f + i * (w.toFloat() * 0.8f / 10f) - scrollOffset * (w.toFloat() * 0.8f / 50f)
                if (x > -20f && x < w + 20f) {
                    canvas.drawLine(x, h.toFloat() * 0.3f, x, h.toFloat(), paint)
                    
                    val distance = (playerDistance + i * 150f).toInt()
                    if (distance >= 0 && distance <= 1600) {
                        paint.textSize = 16f
                        paint.color = Color.BLACK
                        paint.typeface = Typeface.DEFAULT_BOLD
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText("${distance}m", x, h.toFloat() * 0.28f, paint)
                    }
                }
            }
        }
        
        private fun drawSkaterOnTrack(canvas: Canvas, w: Int, h: Int) {
            val playerX = w.toFloat() * 0.1f + (playerDistance / totalDistance) * (w.toFloat() * 0.8f)
            val playerY = h.toFloat() * 0.58f  // 15% plus haut (0.65 -> 0.58)
            
            val skaterImage = when (currentTiltState) {
                TiltState.LEFT -> speedskatingLeftBitmap
                TiltState.RIGHT -> speedskatingRightBitmap
                else -> speedskatingLeftBitmap
            }
            
            skaterImage?.let { image ->
                val scale = 0.5f
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
                canvas.drawCircle(playerX, playerY, 25f, paint)
            }
            
            paint.color = Color.parseColor("#40000000")
            paint.style = Paint.Style.FILL
            canvas.drawOval(playerX - 18f, playerY + 30f, playerX + 18f, playerY + 35f, paint)
        }
        
        private fun drawFrontView(canvas: Canvas, w: Int, h: Int) {
            // Case 2 fois moins large
            val viewWidth = w.toFloat() * 0.15f  // Encore plus petit pour suivre le patineur
            val viewHeight = h.toFloat() * 0.35f  // Plus petit aussi en hauteur
            
            // Position qui suit le patineur
            val playerX = w.toFloat() * 0.1f + (playerDistance / totalDistance) * (w.toFloat() * 0.8f)
            val progress = playerDistance / totalDistance
            
            val viewX = if (progress < 0.5f) {
                // Première moitié : à droite du patineur
                (playerX + 60f).coerceAtMost(w.toFloat() - viewWidth - 10f)
            } else {
                // Deuxième moitié : derrière le patineur (à gauche)
                (playerX - viewWidth - 60f).coerceAtLeast(10f)
            }
            val viewY = h.toFloat() * 0.25f  // Plus haut pour suivre le patineur
            
            paint.color = Color.parseColor("#000066")
            paint.style = Paint.Style.FILL
            reusableRectF.set(viewX, viewY, viewX + viewWidth, viewY + viewHeight)
            canvas.drawRect(reusableRectF, paint)
            
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawRect(reusableRectF, paint)
            paint.style = Paint.Style.FILL
            
            // Plus de texte, juste l'image du patineur
            val frontImage = when (currentTiltState) {
                TiltState.LEFT -> speedskatingFrontLeftBitmap
                TiltState.RIGHT -> speedskatingFrontRightBitmap
                else -> speedskatingFrontLeftBitmap
            }
            
            frontImage?.let { image ->
                val imageMargin = 8f  // Réduit pour la case plus petite
                val availableWidth = viewWidth - imageMargin * 2f
                val availableHeight = viewHeight - imageMargin * 2f
                
                val scaleX = availableWidth / image.width
                val scaleY = availableHeight / image.height
                val scale = minOf(scaleX, scaleY) * 0.9f
                
                val imageWidth = image.width * scale
                val imageHeight = image.height * scale
                
                val imageX = viewX + (viewWidth - imageWidth) / 2f
                val imageY = viewY + (viewHeight - imageHeight) / 2f
                
                reusableRectF.set(imageX, imageY, imageX + imageWidth, imageY + imageHeight)
                canvas.drawBitmap(image, null, reusableRectF, paint)
            } ?: run {
                paint.color = Color.YELLOW
                paint.style = Paint.Style.FILL
                canvas.drawCircle(viewX + viewWidth/2f, viewY + viewHeight/2f, 25f, paint)
            }
        }
        
        private fun drawHUD(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            paint.textSize = 34f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("${(totalDistance - playerDistance).toInt()}m restants", 40f, h.toFloat() - 320f, paint)
            
            paint.textSize = 28f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("Temps: ${raceTime.toInt()}s", 40f, h.toFloat() - 285f, paint)
            
            paint.textSize = 24f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("Parfaits: $perfectStrokes", 40f, h.toFloat() - 250f, paint)
            canvas.drawText("Total coups: $strokeCount", 40f, h.toFloat() - 225f, paint)
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
            
            // DESSIN DU SKIEUR EN ARRIÈRE-PLAN (avant le texte)
            drawVictoryAnimation(canvas, w, h)
            
            // TEXTE ENCORE PLUS GRAND ET GRAS
            paint.color = Color.parseColor("#8B0000")
            paint.textSize = 64f  // Augmenté de 56f à 64f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            
            val resultText = "TEMPS: ${raceTime.toInt()}s - $timeQuality"
            canvas.drawText(resultText, w.toFloat()/2f, h.toFloat() * 0.15f, paint)
            
            paint.textSize = 104f  // Augmenté de 92f à 104f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("${finalScore} POINTS", w.toFloat()/2f, h.toFloat() * 0.28f, paint)
            
            paint.textSize = 44f  // Augmenté de 38f à 44f
            paint.color = Color.parseColor("#333333")
            paint.typeface = Typeface.DEFAULT_BOLD
            
            canvas.drawText("📏 Distance: ${playerDistance.toInt()}m / ${totalDistance.toInt()}m", w.toFloat()/2f, h.toFloat() * 0.45f, paint)
            canvas.drawText("🎵 Rythme moyen: ${(playerRhythm * 100).toInt()}%", w.toFloat()/2f, h.toFloat() * 0.52f, paint)
            canvas.drawText("⭐ Coups parfaits: $perfectStrokes", w.toFloat()/2f, h.toFloat() * 0.59f, paint)
            canvas.drawText("🏃 Coups totaux: $strokeCount", w.toFloat()/2f, h.toFloat() * 0.66f, paint)
            
            paint.textSize = 36f  // Augmenté de 32f à 36f
            paint.typeface = Typeface.DEFAULT_BOLD
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
            canvas.drawText(encouragement, w.toFloat()/2f, h.toFloat() * 0.78f, paint)
        }
        
        private fun drawVictoryAnimation(canvas: Canvas, w: Int, h: Int) {
            if (!victoryAnimationStarted) return
            
            val finalX = w.toFloat() * 0.75f  // 3/4 de l'écran au lieu de 1/2
            val centerY = h.toFloat() * 0.56f  // 20% plus haut (0.7 -> 0.56)
            
            when {
                victoryAnimationProgress < 0.6f -> {
                    val progress1 = victoryAnimationProgress / 0.6f
                    val skaterX = -100f + progress1 * (w.toFloat() * 0.25f + 100f)
                    
                    speedskateHappy1Bitmap?.let { image ->
                        val scale = 0.72f  // 20% plus petit (0.9 -> 0.72)
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
                    val skaterX = startX + progress2 * (finalX - startX)  // S'arrête aux 3/4
                    
                    speedskateHappy2Bitmap?.let { image ->
                        val scale = 0.72f  // 20% plus petit (0.9 -> 0.72)
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
        PREPARATION, RACE, RESULTS, FINISHED
    }
    
    enum class TiltState {
        LEFT, RIGHT, CENTER
    }
}
