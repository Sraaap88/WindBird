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

    // Variables de gameplay EXACTEMENT comme Winter Games 1985
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Phases comme Winter Games
    private val preparationDuration = 5f
    private val countdownDuration = 3f
    private val raceDuration = 30f // Course de 250m
    private val resultsDuration = 6f
    
    // Variables de course - Style Winter Games SOLO
    private var playerDistance = 0f
    private val totalDistance = 250f // 250 mètres exactement comme Winter Games
    private var playerSpeed = 0f
    
    // Animation et rythme - CLÉ DE L'EXPÉRIENCE WINTER GAMES
    private var playerAnimFrame = 0
    private var lastStrokeTime = 0L
    private var playerRhythm = 0f
    private var strokeCount = 0
    private var perfectStrokes = 0
    
    // Contrôles gyroscope - rythme gauche/droite
    private var tiltX = 0f
    private var lastTiltDirection = 0 // -1 = gauche, 1 = droite, 0 = neutre
    private var expectingLeft = true // alternance obligatoire
    private var currentTiltState = TiltState.CENTER // État d'inclinaison actuel
    
    // Performance et résultats
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
            text = "⛸️ PATINAGE VITESSE 250M - ${tournamentData.playerNames[currentPlayerIndex]}"
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
        playerRhythm = 0f
        strokeCount = 0
        perfectStrokes = 0
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

        // Progression du jeu
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
        
        // Système de patinage rythmé - EXACTEMENT comme Winter Games
        handleRhythmicSkating()
        
        // Mise à jour des positions
        updatePositions()
        
        // Vérifier l'arrivée
        checkFinishLine()
    }
    
    private fun handleRhythmicSkating() {
        val currentTime = System.currentTimeMillis()
        val minInterval = 300L // Minimum entre coups
        
        // Détection du mouvement rythmé gauche-droite
        if (currentTime - lastStrokeTime > minInterval) {
            var strokeDetected = false
            var strokeQuality = 0f
            
            // Gauche attendu
            if (expectingLeft && tiltX < -0.8f) {
                strokeDetected = true
                strokeQuality = calculateStrokeQuality(tiltX)
                expectingLeft = false
                lastTiltDirection = -1
                
            // Droite attendu  
            } else if (!expectingLeft && tiltX > 0.8f) {
                strokeDetected = true
                strokeQuality = calculateStrokeQuality(tiltX)
                expectingLeft = true
                lastTiltDirection = 1
            }
            
            if (strokeDetected) {
                lastStrokeTime = currentTime
                strokeCount++
                
                // Animation
                playerAnimFrame = (playerAnimFrame + 1) % 8
                
                // Calcul de la vitesse selon le rythme - CRITIQUE
                val rhythmBonus = updateRhythm(currentTime)
                val speedGain = strokeQuality * rhythmBonus * 1.2f
                
                playerSpeed += speedGain
                playerSpeed = playerSpeed.coerceAtMost(8f) // Vitesse max réaliste
                
                // Bonus pour coups parfaits
                if (strokeQuality > 0.8f && rhythmBonus > 0.8f) {
                    perfectStrokes++
                }
            }
        }
        
        // Décélération naturelle
        playerSpeed *= 0.95f
    }
    
    private fun calculateStrokeQuality(tilt: Float): Float {
        // Qualité basée sur l'amplitude du mouvement
        val amplitude = abs(tilt)
        return (amplitude - 0.8f).coerceIn(0f, 0.7f) / 0.7f
    }
    
    private fun updateRhythm(currentTime: Long): Float {
        val idealInterval = 450L // Rythme idéal Winter Games
        
        if (lastStrokeTime == 0L) {
            // Premier coup - initialiser
            lastStrokeTime = currentTime
            playerRhythm = 0.5f
            return 0.5f
        }
        
        val actualInterval = currentTime - lastStrokeTime
        
        // Calcul plus généreux du rythme
        val rhythmAccuracy = when {
            actualInterval < 200L -> 0.2f // Trop rapide
            actualInterval < 350L -> 0.6f + (350L - actualInterval) / 150f * 0.3f // Bon
            actualInterval <= 550L -> 1f - abs(actualInterval - idealInterval) / 100f // Parfait
            actualInterval < 800L -> 0.6f - (actualInterval - 550L) / 250f * 0.4f // Acceptable
            else -> 0.1f // Trop lent
        }.coerceIn(0f, 1f)
        
        // Mise à jour plus responsive du rythme
        playerRhythm = (playerRhythm * 0.3f + rhythmAccuracy * 0.7f).coerceIn(0f, 1f)
        
        return playerRhythm
    }
    
    private fun updatePositions() {
        if (!playerFinished) {
            playerDistance += playerSpeed * 0.025f * 60f // 60fps reference
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
            // Score basé sur la performance du temps
            val timeBonus = maxOf(0, 150 - raceTime.toInt()) * 3 // Bonus pour temps rapide
            val rhythmBonus = (playerRhythm * 100).toInt()
            val perfectStrokeBonus = perfectStrokes * 10
            val completionBonus = if (playerFinished) 50 else 0
            
            finalScore = maxOf(100, timeBonus + rhythmBonus + perfectStrokeBonus + completionBonus)
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
            GameState.RACE -> "⛸️ ${tournamentData.playerNames[currentPlayerIndex]} | ${playerDistance.toInt()}m/${totalDistance.toInt()}m | Rythme: ${(playerRhythm * 100).toInt()}%"
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
        
        // Images du patineur - TES 4 NOUVELLES IMAGES
        private var preparationBackground: Bitmap? = null
        
        // Images des drapeaux
        private var flagCanadaBitmap: Bitmap? = null
        private var flagUsaBitmap: Bitmap? = null
        private var flagFranceBitmap: Bitmap? = null
        private var flagNorvegeBitmap: Bitmap? = null
        private var flagJapanBitmap: Bitmap? = null
        
        // Nouvelles images du patineur (tes 4 images)
        private var speedskatingLeftBitmap: Bitmap? = null
        private var speedskatingRightBitmap: Bitmap? = null
        private var speedskatingFrontLeftBitmap: Bitmap? = null
        private var speedskatingFrontRightBitmap: Bitmap? = null
        
        init {
            // Charger toutes les images
            try {
                preparationBackground = BitmapFactory.decodeResource(resources, R.drawable.speedskating_preparation)
                
                // Charger tes 4 nouvelles images du patineur
                speedskatingLeftBitmap = BitmapFactory.decodeResource(resources, R.drawable.speedskating_left)
                speedskatingRightBitmap = BitmapFactory.decodeResource(resources, R.drawable.speedskating_right)
                speedskatingFrontLeftBitmap = BitmapFactory.decodeResource(resources, R.drawable.speedskating_front_left)
                speedskatingFrontRightBitmap = BitmapFactory.decodeResource(resources, R.drawable.speedskating_front_right)
                
                // Charger les drapeaux
                flagCanadaBitmap = BitmapFactory.decodeResource(resources, R.drawable.flag_canada)
                flagUsaBitmap = BitmapFactory.decodeResource(resources, R.drawable.flag_usa)
                flagFranceBitmap = BitmapFactory.decodeResource(resources, R.drawable.flag_france)
                flagNorvegeBitmap = BitmapFactory.decodeResource(resources, R.drawable.flag_norvege)
                flagJapanBitmap = BitmapFactory.decodeResource(resources, R.drawable.flag_japan)
            } catch (e: Exception) {
                // Les images ne sont pas trouvées, utiliser le fallback
                preparationBackground = null
            }
        }

        private fun getPlayerFlagBitmap(): Bitmap? {
            // En mode pratique, toujours prendre le drapeau du Canada
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
                else -> flagCanadaBitmap // Drapeau par défaut
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
            // Utiliser l'image de fond si disponible
            preparationBackground?.let { bg ->
                val dstRect = RectF(0f, 0f, w.toFloat(), h.toFloat())
                canvas.drawBitmap(bg, null, dstRect, paint)
            } ?: run {
                // Fallback : arrière-plan simple
                paint.color = Color.parseColor("#87CEEB")
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                
                // Piste de glace
                paint.color = Color.WHITE
                canvas.drawRect(0f, h * 0.3f, w.toFloat(), h.toFloat(), paint)
                
                // Séparation des couloirs
                paint.color = Color.BLACK
                paint.strokeWidth = 4f
                canvas.drawLine(0f, h * 0.65f, w.toFloat(), h * 0.65f, paint)
            }
            
            // GROS DRAPEAU DU PAYS dans le coin supérieur gauche
            val playerCountry = if (practiceMode) "CANADA" else tournamentData.playerCountries[currentPlayerIndex]
            val flagBitmap = getPlayerFlagBitmap()
            
            flagBitmap?.let { flag ->
                val flagWidth = 180f // GROS drapeau
                val flagHeight = 120f
                val flagX = 30f
                val flagY = 30f
                
                val flagRect = RectF(flagX, flagY, flagX + flagWidth, flagY + flagHeight)
                canvas.drawBitmap(flag, null, flagRect, paint)
                
                // Bordure dorée autour du drapeau
                paint.color = Color.parseColor("#FFD700")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f
                canvas.drawRect(flagRect, paint)
                paint.style = Paint.Style.FILL
                
                // Nom du pays sous le drapeau
                paint.color = Color.parseColor("#000066")
                paint.textSize = 24f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(playerCountry.uppercase(), flagX + flagWidth/2f, flagY + flagHeight + 35f, paint)
            } ?: run {
                // Fallback emoji si pas d'image
                val flag = getCountryFlag(playerCountry)
                paint.color = Color.parseColor("#FFD700")
                canvas.drawRect(30f, 30f, 210f, 150f, paint)
                
                paint.color = Color.BLACK
                paint.textSize = 80f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(flag, 120f, 110f, paint)
                
                paint.textSize = 24f
                canvas.drawText(playerCountry.uppercase(), 120f, 185f, paint)
            }
            
            // TITRE PRINCIPAL
            paint.color = Color.parseColor("#000066")
            paint.textSize = 44f // PLUS GROS
            paint.textAlign = Paint.Align.CENTER
            paint.style = Paint.Style.FILL
            canvas.drawText("⛸️ PATINAGE VITESSE 250M ⛸️", w/2f, h * 0.08f, paint)
            
            // INSTRUCTIONS PRINCIPALES - BEAUCOUP PLUS GROSSES
            paint.color = Color.parseColor("#FF0000") // ROUGE pour attirer l'attention
            paint.textSize = 32f // ÉNORME
            canvas.drawText("📱 INCLINEZ GAUCHE-DROITE", w/2f, h * 0.15f, paint)
            canvas.drawText("EN RYTHME AVEC LES JAMBES!", w/2f, h * 0.20f, paint)
            
            // DÉTAILS EN BLEU - PLUS GROS
            paint.color = Color.parseColor("#0000FF")
            paint.textSize = 26f // PLUS GROS
            canvas.drawText("⬅️ GAUCHE → ➡️ DROITE → ⬅️ GAUCHE", w/2f, h * 0.27f, paint)
            canvas.drawText("ALTERNEZ en suivant l'animation!", w/2f, h * 0.32f, paint)
            
            // EXEMPLE VISUEL - MOUVEMENT SIMULÉ
            val demoTime = (phaseTimer * 2f) % 2f
            val demoPhase = if (demoTime < 1f) "⬅️ GAUCHE" else "➡️ DROITE"
            paint.color = Color.parseColor("#FF6600") // ORANGE
            paint.textSize = 38f // TRÈS GROS
            canvas.drawText("MAINTENANT: $demoPhase", w/2f, h * 0.42f, paint)
            
            // Décompte PLUS VISIBLE
            val countdown = (preparationDuration - phaseTimer).toInt() + 1
            paint.textSize = 60f // ÉNORME
            paint.color = Color.RED
            canvas.drawText("DÉBUT DANS ${countdown}s", w/2f, h * 0.52f, paint)
            
            // CONSEILS SUPPLÉMENTAIRES
            paint.color = Color.parseColor("#006600") // VERT FONCÉ
            paint.textSize = 22f // LISIBLE
            canvas.drawText("✓ Regardez la vue de face en haut à droite", w/2f, h * 0.60f, paint)
            canvas.drawText("✓ Suivez le mouvement des jambes", w/2f, h * 0.64f, paint)
            canvas.drawText("✓ Rythme régulier = Plus rapide", w/2f, h * 0.68f, paint)
        }
        
        private fun drawCountdown(canvas: Canvas, w: Int, h: Int) {
            // Même fond que la course
            drawRaceBackground(canvas, w, h)
            
            // Décompte géant
            val count = (countdownDuration - phaseTimer).toInt() + 1
            paint.textSize = 120f
            paint.color = Color.RED
            paint.textAlign = Paint.Align.CENTER
            
            val countText = if (count > 0) count.toString() else "GO!"
            canvas.drawText(countText, w/2f, h/2f, paint)
        }
        
        private fun drawRace(canvas: Canvas, w: Int, h: Int) {
            drawRaceBackground(canvas, w, h)
            drawSkaterOnTrack(canvas, w, h)  // Vue de profil PETITE sur la piste
            drawFrontView(canvas, w, h)      // Vue de face GRANDE en haut à droite
            drawHUD(canvas, w, h)
        }
        
        private fun drawRaceBackground(canvas: Canvas, w: Int, h: Int) {
            // Ciel
            paint.color = Color.parseColor("#87CEEB")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.3f, paint)
            
            // Piste de glace blanche avec effet brillant
            val iceGradient = LinearGradient(0f, h * 0.3f, 0f, h.toFloat(),
                Color.WHITE, Color.parseColor("#F0F8FF"), Shader.TileMode.CLAMP)
            paint.shader = iceGradient
            canvas.drawRect(0f, h * 0.3f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
            
            // Couloir unique centré
            paint.color = Color.parseColor("#CCCCCC")
            paint.strokeWidth = 3f
            val laneTop = h * 0.45f
            val laneBottom = h * 0.85f
            canvas.drawLine(0f, laneTop, w.toFloat(), laneTop, paint)
            canvas.drawLine(0f, laneBottom, w.toFloat(), laneBottom, paint)
            
            // Marques de progression qui défilent
            val scrollOffset = playerDistance % 50f
            paint.color = Color.parseColor("#DDDDDD")
            paint.strokeWidth = 2f
            
            for (i in -2..12) {
                val x = w * 0.1f + i * (w * 0.8f / 10f) - scrollOffset * (w * 0.8f / 50f)
                if (x > -20f && x < w + 20f) {
                    canvas.drawLine(x, h * 0.3f, x, h.toFloat(), paint)
                    
                    // Numéro de distance
                    val distance = (playerDistance + i * 25f).toInt()
                    if (distance >= 0 && distance <= 300) {
                        paint.textSize = 12f
                        paint.color = Color.BLACK
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText("${distance}m", x, h * 0.28f, paint)
                    }
                }
            }
        }
        
        private fun drawSkaterOnTrack(canvas: Canvas, w: Int, h: Int) {
            // Position selon la distance parcourue - PATINEUR PETIT sur la piste
            val playerX = w * 0.1f + (playerDistance / totalDistance) * (w * 0.8f)
            val playerY = h * 0.65f
            
            // Choisir l'image selon l'inclinaison (vue de PROFIL)
            val skaterImage = when (currentTiltState) {
                TiltState.LEFT -> speedskatingLeftBitmap
                TiltState.RIGHT -> speedskatingRightBitmap
                else -> speedskatingLeftBitmap // Image par défaut
            }
            
            // Dessiner le patineur de profil PETIT sur la piste
            skaterImage?.let { image ->
                val scale = 0.4f // PETIT pour la piste
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
                // Fallback si pas d'image
                paint.color = Color.parseColor("#0066FF")
                paint.style = Paint.Style.FILL
                canvas.drawCircle(playerX, playerY, 20f, paint)
            }
            
            // Ombre sur la glace
            paint.color = Color.parseColor("#40000000")
            paint.style = Paint.Style.FILL
            canvas.drawOval(playerX - 15f, playerY + 25f, playerX + 15f, playerY + 30f, paint)
        }
        
        private fun drawFrontView(canvas: Canvas, w: Int, h: Int) {
            // GRANDE fenêtre en haut à droite pour la vue de face
            val viewWidth = w * 0.35f  // 35% de l'écran
            val viewHeight = h * 0.45f // 45% de l'écran
            val viewX = w - viewWidth - 20f
            val viewY = 20f
            
            // Fond de la fenêtre
            paint.color = Color.parseColor("#000066")
            paint.style = Paint.Style.FILL
            canvas.drawRect(viewX, viewY, viewX + viewWidth, viewY + viewHeight, paint)
            
            // Bordure de la fenêtre
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawRect(viewX, viewY, viewX + viewWidth, viewY + viewHeight, paint)
            paint.style = Paint.Style.FILL
            
            // Titre de la fenêtre
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("VUE DE FACE", viewX + viewWidth/2f, viewY + 20f, paint)
            
            // Choisir l'image selon l'inclinaison (vue de FACE)
            val frontImage = when (currentTiltState) {
                TiltState.LEFT -> speedskatingFrontLeftBitmap
                TiltState.RIGHT -> speedskatingFrontRightBitmap
                else -> speedskatingFrontLeftBitmap // Image par défaut
            }
            
            // Dessiner le patineur de face GRAND dans la fenêtre
            frontImage?.let { image ->
                val imageMargin = 20f
                val availableWidth = viewWidth - imageMargin * 2f
                val availableHeight = viewHeight - 50f // Espace pour le titre
                
                // Calculer le scale pour que l'image remplisse bien la fenêtre
                val scaleX = availableWidth / image.width
                val scaleY = availableHeight / image.height
                val scale = minOf(scaleX, scaleY) * 0.8f // 80% pour laisser un peu d'espace
                
                val imageWidth = image.width * scale
                val imageHeight = image.height * scale
                
                // Centrer l'image dans la fenêtre
                val imageX = viewX + (viewWidth - imageWidth) / 2f
                val imageY = viewY + 40f + (availableHeight - imageHeight) / 2f
                
                val dstRect = RectF(imageX, imageY, imageX + imageWidth, imageY + imageHeight)
                canvas.drawBitmap(image, null, dstRect, paint)
            } ?: run {
                // Fallback si pas d'image
                paint.color = Color.YELLOW
                paint.style = Paint.Style.FILL
                canvas.drawCircle(viewX + viewWidth/2f, viewY + viewHeight/2f, 30f, paint)
            }
            
            // Indicateur de direction attendue
            paint.color = Color.YELLOW
            paint.textSize = 24f
            paint.textAlign = Paint.Align.CENTER
            val nextMove = if (expectingLeft) "⬅️ GAUCHE" else "➡️ DROITE"
            canvas.drawText(nextMove, viewX + viewWidth/2f, viewY + viewHeight - 10f, paint)
            
            // Indicateur de rythme dans la fenêtre
            paint.color = if (playerRhythm > 0.7f) Color.GREEN 
                         else if (playerRhythm > 0.4f) Color.YELLOW 
                         else Color.RED
            paint.textSize = 14f
            canvas.drawText("RYTHME: ${(playerRhythm * 100).toInt()}%", viewX + viewWidth/2f, viewY + viewHeight - 30f, paint)
        }
        
        private fun drawHUD(canvas: Canvas, w: Int, h: Int) {
            // HUD adapté pour laisser la place à la vue de face
            
            // Distance restante - EN BAS À GAUCHE
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            paint.textSize = 28f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("${(totalDistance - playerDistance).toInt()}m restants", 30f, h - 100f, paint)
            
            // Temps - EN BAS À GAUCHE
            paint.textSize = 24f
            canvas.drawText("Temps: ${raceTime.toInt()}s", 30f, h - 70f, paint)
            
            // Coups parfaits - EN BAS À GAUCHE
            paint.textSize = 20f
            canvas.drawText("Parfaits: $perfectStrokes", 30f, h - 40f, paint)
            
            // BARRE DE PROGRESSION EN BAS
            val progressBarX = 50f
            val progressBarY = h - 180f
            val progressBarWidth = w * 0.5f // Plus petite pour laisser la place
            val progressBarHeight = 25f
            
            // Fond de la barre
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(progressBarX, progressBarY, progressBarX + progressBarWidth, 
                           progressBarY + progressBarHeight, paint)
            
            // Progression
            val progress = (playerDistance / totalDistance).coerceIn(0f, 1f)
            paint.color = Color.parseColor("#00FF00")
            canvas.drawRect(progressBarX, progressBarY, 
                           progressBarX + progressBarWidth * progress, 
                           progressBarY + progressBarHeight, paint)
            
            // Bordure
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawRect(progressBarX, progressBarY, progressBarX + progressBarWidth, 
                           progressBarY + progressBarHeight, paint)
            paint.style = Paint.Style.FILL
            
            // Label de la barre
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("PROGRESSION: ${playerDistance.toInt()}m / ${totalDistance.toInt()}m", 
                           progressBarX, progressBarY - 10f, paint)
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            // Fond selon la performance
            val timeQuality = when {
                raceTime < 25f -> "EXCELLENT"
                raceTime < 30f -> "BON"
                raceTime < 35f -> "MOYEN"
                else -> "LENT"
            }
            
            val bgColor = when (timeQuality) {
                "EXCELLENT" -> Color.parseColor("#FFD700") // Or
                "BON" -> Color.parseColor("#C0C0C0") // Argent
                "MOYEN" -> Color.parseColor("#CD7F32") // Bronze
                else -> Color.parseColor("#808080") // Gris
            }
            
            val gradient = LinearGradient(0f, 0f, 0f, h.toFloat(),
                bgColor, Color.parseColor("#FFF8DC"), Shader.TileMode.CLAMP)
            paint.shader = gradient
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
            
            // Résultat de la course - ÉNORME
            paint.color = Color.parseColor("#8B0000")
            paint.textSize = 48f // BEAUCOUP PLUS GROS
            paint.textAlign = Paint.Align.CENTER
            
            val resultText = "TEMPS: ${raceTime.toInt()}s - $timeQuality"
            canvas.drawText(resultText, w/2f, h * 0.15f, paint)
            
            // Score final - ÉNORME
            paint.textSize = 80f // TRÈS GROS
            canvas.drawText("${finalScore} POINTS", w/2f, h * 0.28f, paint)
            
            // Détails - BEAUCOUP PLUS GROS ET ESPACÉS
            paint.textSize = 32f // GROS
            paint.color = Color.parseColor("#333333")
            
            canvas.drawText("📏 Distance: ${playerDistance.toInt()}m / ${totalDistance.toInt()}m", w/2f, h * 0.45f, paint)
            
            canvas.drawText("🎵 Rythme moyen: ${(playerRhythm * 100).toInt()}%", w/2f, h * 0.52f, paint)
            
            canvas.drawText("⭐ Coups parfaits: $perfectStrokes", w/2f, h * 0.59f, paint)
            
            canvas.drawText("🏃 Coups totaux: $strokeCount", w/2f, h * 0.66f, paint)
            
            // Classification du temps - PLUS GROS
            paint.textSize = 24f // LISIBLE
            paint.color = Color.parseColor("#666666")
            canvas.drawText("< 25s: Excellent | 25-30s: Bon", w/2f, h * 0.78f, paint)
            canvas.drawText("30-35s: Moyen | > 35s: Lent", w/2f, h * 0.83f, paint)
            
            // Encouragement basé sur la performance
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
            canvas.drawText(encouragement, w/2f, h * 0.92f, paint)
        }
    }

    enum class GameState {
        PREPARATION, COUNTDOWN, RACE, RESULTS, FINISHED
    }
    
    enum class TiltState {
        LEFT, RIGHT, CENTER
    }
}
