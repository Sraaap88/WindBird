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
    
    // Animation et rythme - CORRIGÉ
    private var playerAnimFrame = 0
    private var lastStrokeTime = 0L
    private var previousStrokeTime = 0L // AJOUTÉ pour calculer l'intervalle
    private var playerRhythm = 0f
    private var strokeCount = 0
    private var perfectStrokes = 0
    
    // NOUVEAU - Variables pour la bande de performance
    private var currentStrokeQuality = 0f
    private var currentRhythmQuality = 0f
    private var performanceHistory = mutableListOf<Float>() // Historique des 20 dernières performances
    
    // Contrôles gyroscope - LOGIQUE CORRIGÉE
    private var tiltX = 0f
    private var lastTiltDirection = 0
    private var expectingLeft = true
    private var currentTiltState = TiltState.CENTER
    
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
        tiltX = 0f
        lastTiltDirection = 0
        expectingLeft = true
        currentTiltState = TiltState.CENTER
        raceTime = 0f
        playerFinished = false
        finalScore = 0
        scoreCalculated = false
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
        
        // Mise à jour de l'état d'inclinaison
        currentTiltState = when {
            tiltX < -0.4f -> TiltState.LEFT
            tiltX > 0.4f -> TiltState.RIGHT
            else -> TiltState.CENTER
        }

        phaseTimer += 0.025f
        if (gameState == GameState.RACE) {
            raceTime += 0.025f
        }

        when (gameState) {
            GameState.PREPARATION -> handlePreparation()
            GameState.COUNTDOWN -> handleCountdown() 
            GameState.RACE -> handleRace()
            GameState.RESULTS -> handleResults()
            GameState.FINISHED -> {}
        }

        updateStatus()
        gameView.invalidate()
    }
    
    private fun handlePreparation() {
        if (phaseTimer >= preparationDuration) {
            gameState = GameState.COUNTDOWN
            phaseTimer = 0f
        }
    }
    
    private fun handleCountdown() {
        if (phaseTimer >= countdownDuration) {
            gameState = GameState.RACE
            phaseTimer = 0f
            raceTime = 0f
        }
    }
    
    private fun handleRace() {
        if (playerFinished) {
            calculateFinalScore()
            gameState = GameState.RESULTS
            phaseTimer = 0f
            return
        }
        
        handleRhythmicSkating()
        updatePositions()
        checkFinishLine()
    }
    
    // LOGIQUE CORRIGÉE du rythme
    private fun handleRhythmicSkating() {
        val currentTime = System.currentTimeMillis()
        val minInterval = 200L
        
        if (currentTime - lastStrokeTime > minInterval) {
            var strokeDetected = false
            var strokeQuality = 0f
            
            // Détection des poussées avec seuils corrigés
            if (expectingLeft && tiltX < -0.6f) {
                strokeDetected = true
                strokeQuality = calculateStrokeQuality(tiltX)
                expectingLeft = false
                lastTiltDirection = -1
                
            } else if (!expectingLeft && tiltX > 0.6f) {
                strokeDetected = true
                strokeQuality = calculateStrokeQuality(tiltX)
                expectingLeft = true
                lastTiltDirection = 1
            }
            
            if (strokeDetected) {
                // CORRECTION : Calculer l'intervalle avec le coup PRÉCÉDENT
                val intervalSinceLastStroke = if (previousStrokeTime > 0) {
                    currentTime - previousStrokeTime
                } else {
                    500L // Premier coup - intervalle par défaut
                }
                
                // Mettre à jour les temps
                previousStrokeTime = lastStrokeTime
                lastStrokeTime = currentTime
                strokeCount++
                
                // Animation
                playerAnimFrame = (playerAnimFrame + 1) % 8
                
                // CALCUL CORRIGÉ du rythme
                val rhythmQuality = calculateRhythmQuality(intervalSinceLastStroke)
                currentStrokeQuality = strokeQuality
                currentRhythmQuality = rhythmQuality
                
                // Mise à jour du rythme global avec pondération
                playerRhythm = (playerRhythm * 0.7f + rhythmQuality * 0.3f).coerceIn(0f, 1f)
                
                // Gain de vitesse
                val combinedQuality = (strokeQuality * 0.6f + rhythmQuality * 0.4f)
                val speedGain = combinedQuality * 1.5f // AUGMENTÉ de 1.2f à 1.5f
                
                playerSpeed += speedGain
                playerSpeed = playerSpeed.coerceAtMost(7f) // AUGMENTÉ de 6f à 7f
                
                // Historique de performance pour la bande
                val overallPerformance = (strokeQuality + rhythmQuality) / 2f
                performanceHistory.add(overallPerformance)
                if (performanceHistory.size > 20) {
                    performanceHistory.removeAt(0)
                }
                
                // Bonus pour coups parfaits
                if (strokeQuality > 0.7f && rhythmQuality > 0.7f) {
                    perfectStrokes++
                }
            }
        }
        
        // Décélération naturelle
        playerSpeed *= 0.995f // MOINS de décélération (était 0.98f)
    }
    
    private fun calculateStrokeQuality(tilt: Float): Float {
        val amplitude = abs(tilt)
        return when {
            amplitude >= 1.0f -> 1f        // Excellent - gros mouvement
            amplitude >= 0.8f -> 0.85f     // Très bon
            amplitude >= 0.6f -> 0.7f      // Bon
            amplitude >= 0.4f -> 0.5f      // Moyen
            else -> 0.3f                   // Faible
        }.coerceIn(0f, 1f)
    }
    
    // NOUVEAU calcul du rythme plus clair
    private fun calculateRhythmQuality(intervalMs: Long): Float {
        // Rythme idéal : 400-600ms entre les coups
        return when {
            intervalMs < 250L -> 0.2f      // Trop rapide
            intervalMs < 350L -> 0.6f      // Un peu rapide
            intervalMs in 350L..650L -> 1f  // PARFAIT
            intervalMs < 900L -> 0.7f      // Un peu lent
            intervalMs < 1200L -> 0.4f     // Trop lent
            else -> 0.1f                   // Beaucoup trop lent
        }.coerceIn(0f, 1f)
    }
    
    private fun updatePositions() {
        if (!playerFinished) {
            // CORRECTION : 25% plus rapide pour éviter l'ennui (9 épreuves au total)
            playerDistance += playerSpeed * 0.025f * 20f // AUGMENTÉ de 15f à 20f
        }
    }
    
    private fun checkFinishLine() {
        if (playerDistance >= totalDistance && !playerFinished) {
            playerFinished = true
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
    
    private fun calculateFinalScore() {
        if (!scoreCalculated) {
            val timeBonus = maxOf(0, 450 - raceTime.toInt()) * 1 // AJUSTÉ : 450s (1.5-2min optimal)
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
        
        init {
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
            } catch (e: Exception) {
                preparationBackground = null
            }
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
                val dstRect = RectF(0f, 0f, w.toFloat(), h.toFloat())
                canvas.drawBitmap(bg, null, dstRect, paint)
            } ?: run {
                paint.color = Color.parseColor("#87CEEB")
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.color = Color.WHITE
                canvas.drawRect(0f, h * 0.3f, w.toFloat(), h.toFloat(), paint)
                paint.color = Color.BLACK
                paint.strokeWidth = 4f
                canvas.drawLine(0f, h * 0.65f, w.toFloat(), h * 0.65f, paint)
            }
            
            // Drapeau et infos pays
            val playerCountry = if (practiceMode) "CANADA" else tournamentData.playerCountries[currentPlayerIndex]
            val flagBitmap = getPlayerFlagBitmap()
            
            flagBitmap?.let { flag ->
                val flagWidth = 180f
                val flagHeight = 120f
                val flagX = 30f
                val flagY = 30f
                
                val flagRect = RectF(flagX, flagY, flagX + flagWidth, flagY + flagHeight)
                canvas.drawBitmap(flag, null, flagRect, paint)
                
                paint.color = Color.parseColor("#FFD700")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f
                canvas.drawRect(flagRect, paint)
                paint.style = Paint.Style.FILL
                
                paint.color = Color.parseColor("#000066")
                paint.textSize = 24f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(playerCountry.uppercase(), flagX + flagWidth/2f, flagY + flagHeight + 35f, paint)
            }
            
            // INSTRUCTIONS AMÉLIORÉES avec explication du rythme
            paint.color = Color.parseColor("#000066")
            paint.textSize = 44f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("⛸️ PATINAGE VITESSE 1500M ⛸️", w/2f, h * 0.08f, paint)
            
            paint.color = Color.parseColor("#FF0000")
            paint.textSize = 36f
            canvas.drawText("📱 ALTERNEZ GAUCHE-DROITE", w/2f, h * 0.15f, paint)
            
            paint.color = Color.parseColor("#0000FF")
            paint.textSize = 28f
            canvas.drawText("⏱️ RYTHME IDÉAL: 1 poussée toutes les 0.5 secondes", w/2f, h * 0.22f, paint)
            canvas.drawText("💪 FORT + RÉGULIER = Plus rapide!", w/2f, h * 0.28f, paint)
            
            // Indicateur de rythme en temps réel pendant la préparation
            paint.color = Color.parseColor("#FF6600")
            paint.textSize = 32f
            canvas.drawText("TESTEZ VOTRE RYTHME:", w/2f, h * 0.36f, paint)
            
            // Bande de test pendant la préparation
            drawPerformanceBand(canvas, w, h * 0.42f, w * 0.6f, 30f, true)
            
            val countdown = (preparationDuration - phaseTimer).toInt() + 1
            paint.textSize = 60f
            paint.color = Color.RED
            canvas.drawText("DÉBUT DANS ${countdown}s", w/2f, h * 0.55f, paint)
        }
        
        private fun drawCountdown(canvas: Canvas, w: Int, h: Int) {
            drawRaceBackground(canvas, w, h)
            
            val count = (countdownDuration - phaseTimer).toInt() + 1
            paint.textSize = 120f
            paint.color = Color.RED
            paint.textAlign = Paint.Align.CENTER
            
            val countText = if (count > 0) count.toString() else "GO!"
            canvas.drawText(countText, w/2f, h/2f, paint)
        }
        
        private fun drawRace(canvas: Canvas, w: Int, h: Int) {
            drawRaceBackground(canvas, w, h)
            drawSkaterOnTrack(canvas, w, h)
            drawFrontView(canvas, w, h)
            drawHUD(canvas, w, h)
            
            // NOUVELLE bande de performance en temps réel
            drawPerformanceBand(canvas, 50f, h - 200f, w * 0.5f, 40f, false)
        }
        
        // NOUVELLE fonction pour la bande de performance en temps réel - SANS HISTORIQUE
        private fun drawPerformanceBand(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, isPrep: Boolean) {
            // Fond de la bande
            paint.color = Color.parseColor("#333333")
            paint.style = Paint.Style.FILL
            canvas.drawRect(x, y, x + width, y + height, paint)
            
            if (isPrep) {
                // Pendant la préparation - montrer les zones
                val zoneWidth = width / 5f
                val colors = arrayOf("#FF0000", "#FF8800", "#FFFF00", "#88FF00", "#00FF00") // Rouge à vert
                val labels = arrayOf("Très lent", "Lent", "Moyen", "Bon", "Parfait")
                
                for (i in 0..4) {
                    paint.color = Color.parseColor(colors[i])
                    canvas.drawRect(x + i * zoneWidth, y, x + (i + 1) * zoneWidth, y + height, paint)
                    
                    paint.color = Color.BLACK
                    paint.textSize = 12f
                    paint.textAlign = Paint.Align.CENTER
                    canvas.drawText(labels[i], x + i * zoneWidth + zoneWidth/2f, y + height/2f + 4f, paint)
                }
                
                paint.color = Color.WHITE
                paint.textSize = 16f
                canvas.drawText("ZONES DE PERFORMANCE", x + width/2f, y - 10f, paint)
                
            } else {
                // Pendant la course - performance en temps réel SEULEMENT
                
                // Performance actuelle - on utilise toute la largeur maintenant
                val currentWidth = width
                val currentX = x
                
                // Qualité du coup actuel (moitié gauche)
                val strokeColor = getPerformanceColor(currentStrokeQuality)
                paint.color = strokeColor
                canvas.drawRect(currentX, y, currentX + currentWidth/2f, y + height, paint)
                
                // Qualité du rythme actuel (moitié droite)
                val rhythmColor = getPerformanceColor(currentRhythmQuality)
                paint.color = rhythmColor
                canvas.drawRect(currentX + currentWidth/2f, y, currentX + currentWidth, y + height, paint)
                
                // Labels
                paint.color = Color.WHITE
                paint.textSize = 14f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("COUP", currentX + currentWidth/4f, y - 5f, paint)
                canvas.drawText("RYTHME", currentX + 3*currentWidth/4f, y - 5f, paint)
                
                // Valeurs numériques
                paint.color = Color.BLACK
                paint.textSize = 12f
                canvas.drawText("${(currentStrokeQuality * 100).toInt()}%", currentX + currentWidth/4f, y + height/2f + 4f, paint)
                canvas.drawText("${(currentRhythmQuality * 100).toInt()}%", currentX + 3*currentWidth/4f, y + height/2f + 4f, paint)
            }
            
            // Bordure
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            canvas.drawRect(x, y, x + width, y + height, paint)
            paint.style = Paint.Style.FILL
        }
        
        private fun getPerformanceColor(performance: Float): Int {
            return when {
                performance >= 0.8f -> Color.parseColor("#00FF00") // Vert - Excellent
                performance >= 0.6f -> Color.parseColor("#88FF00") // Vert clair - Bon  
                performance >= 0.4f -> Color.parseColor("#FFFF00") // Jaune - Moyen
                performance >= 0.2f -> Color.parseColor("#FF8800") // Orange - Faible
                else -> Color.parseColor("#FF0000") // Rouge - Très faible
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
                
                val dstRect = RectF(
                    playerX - imageWidth/2f,
                    playerY - imageHeight/2f,
                    playerX + imageWidth/2f,
                    playerY + imageHeight/2f
                )
                canvas.drawBitmap(image, null, dstRect, paint)
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
            canvas.drawRect(viewX, viewY, viewX + viewWidth, viewY + viewHeight, paint)
            
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawRect(viewX, viewY, viewX + viewWidth, viewY + viewHeight, paint)
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
                
                val dstRect = RectF(imageX, imageY, imageX + imageWidth, imageY + imageHeight)
                canvas.drawBitmap(image, null, dstRect, paint)
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
            canvas.drawRect(progressBarX, progressBarY, progressBarX + progressBarWidth, 
                           progressBarY + progressBarHeight, paint)
            
            val progress = (playerDistance / totalDistance).coerceIn(0f, 1f)
            paint.color = Color.parseColor("#00FF00")
            canvas.drawRect(progressBarX, progressBarY, 
                           progressBarX + progressBarWidth * progress, 
                           progressBarY + progressBarHeight, paint)
            
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawRect(progressBarX, progressBarY, progressBarX + progressBarWidth, 
                           progressBarY + progressBarHeight, paint)
            paint.style = Paint.Style.FILL
            
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("PROGRESSION: ${playerDistance.toInt()}m / ${totalDistance.toInt()}m", 
                           progressBarX, progressBarY - 10f, paint)
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            val timeQuality = when {
                raceTime < 90f -> "EXCELLENT"   // Moins de 1.5 minutes
                raceTime < 120f -> "BON"        // 1.5-2 minutes  
                raceTime < 150f -> "MOYEN"      // 2-2.5 minutes
                else -> "LENT"                  // Plus de 2.5 minutes
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
        }
    }

    enum class GameState {
        PREPARATION, COUNTDOWN, RACE, RESULTS, FINISHED
    }
    
    enum class TiltState {
        LEFT, RIGHT, CENTER
    }
}
