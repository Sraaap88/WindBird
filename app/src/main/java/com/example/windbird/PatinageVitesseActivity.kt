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
        
        // Images exactement comme Winter Games + image de préparation
        private var skaterSpriteSheet: Bitmap? = null
        private var preparationBackground: Bitmap? = null
        
        // Images des drapeaux
        private var flagCanadaBitmap: Bitmap? = null
        private var flagUsaBitmap: Bitmap? = null
        private var flagFranceBitmap: Bitmap? = null
        private var flagNorvegeBitmap: Bitmap? = null
        private var flagJapanBitmap: Bitmap? = null
        
        init {
            // Utiliser directement les sprites créés en code pour voir le jeu de jambes
            createFallbackSprites()
            
            // Charger l'image de préparation et les drapeaux
            try {
                preparationBackground = BitmapFactory.decodeResource(resources, R.drawable.speekskating_preparation)
                
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
        
        private fun createFallbackSprites() {
            // Créer une spritesheet de fallback simple
            skaterSpriteSheet = Bitmap.createBitmap(320, 80, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(skaterSpriteSheet!!)
            val tempPaint = Paint().apply {
                color = Color.BLUE
                style = Paint.Style.FILL
            }
            
            // 8 frames d'animation simple
            for (frame in 0..7) {
                val x = frame * 40f
                // Corps
                canvas.drawRect(x + 15f, 20f, x + 25f, 50f, tempPaint)
                // Tête
                canvas.drawCircle(x + 20f, 15f, 8f, tempPaint)
                // Jambes selon la frame
                val legOffset = if (frame % 2 == 0) -8f else 8f
                canvas.drawLine(x + 20f, 50f, x + 20f + legOffset, 70f, tempPaint)
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
            canvas.drawText("✓ Regardez les jambes du patineur", w/2f, h * 0.60f, paint)
            canvas.drawText("✓ Rythme régulier = Plus rapide", w/2f, h * 0.64f, paint)
            canvas.drawText("✓ Pas trop vite, pas trop lent!", w/2f, h * 0.68f, paint)
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
            drawSkaters(canvas, w, h)
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
        
        private fun drawSkaters(canvas: Canvas, w: Int, h: Int) {
            // Position selon la distance parcourue - UN SEUL PATINEUR AU CENTRE
            val playerX = w * 0.1f + (playerDistance / totalDistance) * (w * 0.8f)
            
            // Joueur au centre de son couloir
            drawSkater(canvas, playerX, h * 0.65f, playerAnimFrame, Color.parseColor("#0066FF"), true)
            
            // Indicateur de rythme pour le joueur
            if (gameState == GameState.RACE) {
                drawRhythmIndicator(canvas, w, h)
            }
        }
        
        private fun drawSkater(canvas: Canvas, x: Float, y: Float, frame: Int, color: Int, isPlayer: Boolean) {
            skaterSpriteSheet?.let { sprite ->
                // Extraire la frame d'animation de la spritesheet
                val frameWidth = sprite.width / 8
                val srcRect = Rect(frame * frameWidth, 0, (frame + 1) * frameWidth, sprite.height)
                val dstRect = RectF(x - 30f, y - 25f, x + 30f, y + 25f)
                canvas.drawBitmap(sprite, srcRect, dstRect, paint)
            } ?: run {
                // Fallback : patineur simplifié avec animation
                val legPhase = sin(frame * PI / 4).toFloat()
                
                // Corps
                paint.color = color
                paint.style = Paint.Style.FILL
                canvas.drawOval(x - 12f, y - 20f, x + 12f, y + 5f, paint)
                
                // Tête
                paint.color = Color.parseColor("#FFDBAC")
                canvas.drawCircle(x, y - 22f, 8f, paint)
                
                // Jambes en mouvement
                paint.color = Color.BLACK
                paint.strokeWidth = 6f
                
                // Jambe qui pousse (alternance)
                val pushLegX = x + legPhase * 25f
                canvas.drawLine(x, y + 5f, pushLegX, y + 30f, paint)
                
                // Jambe de glisse
                val glideLegX = x - legPhase * 10f
                canvas.drawLine(x, y + 5f, glideLegX, y + 25f, paint)
                
                // Bras en équilibre
                paint.strokeWidth = 4f
                canvas.drawLine(x - 15f, y - 10f, x - 25f + legPhase * 8f, y - 5f, paint)
                canvas.drawLine(x + 15f, y - 10f, x + 25f - legPhase * 8f, y - 5f, paint)
                
                // Patins
                paint.color = Color.BLACK
                paint.strokeWidth = 3f
                canvas.drawLine(pushLegX - 8f, y + 30f, pushLegX + 8f, y + 30f, paint)
                canvas.drawLine(glideLegX - 8f, y + 25f, glideLegX + 8f, y + 25f, paint)
            }
            
            // Ombre sur la glace
            paint.color = Color.parseColor("#40000000")
            paint.style = Paint.Style.FILL
            canvas.drawOval(x - 15f, y + 25f, x + 15f, y + 30f, paint)
        }
        
        private fun drawRhythmIndicator(canvas: Canvas, w: Int, h: Int) {
            // Indicateur de rythme BEAUCOUP PLUS GROS
            val indicatorX = w * 0.15f // Plus à droite
            val indicatorY = h - 180f // Plus haut
            val size = 80f // PLUS GROS
            
            // Fond noir
            paint.color = Color.parseColor("#000000")
            paint.style = Paint.Style.FILL
            canvas.drawRect(indicatorX - size/2f, indicatorY - size/2f, 
                           indicatorX + size/2f, indicatorY + size/2f, paint)
            
            // Bordure
            paint.color = Color.WHITE
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawRect(indicatorX - size/2f, indicatorY - size/2f, 
                           indicatorX + size/2f, indicatorY + size/2f, paint)
            paint.style = Paint.Style.FILL
            
            // Barre de rythme
            paint.color = if (playerRhythm > 0.7f) Color.GREEN 
                         else if (playerRhythm > 0.4f) Color.YELLOW 
                         else Color.RED
            val rhythmHeight = playerRhythm * (size - 20f)
            canvas.drawRect(indicatorX - size/2f + 10f, indicatorY + size/2f - 10f - rhythmHeight, 
                           indicatorX + size/2f - 10f, indicatorY + size/2f - 10f, paint)
            
            // Label PLUS GROS
            paint.color = Color.WHITE
            paint.textSize = 16f // PLUS GROS
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("RYTHME", indicatorX, indicatorY - size/2f - 10f, paint)
            canvas.drawText("${(playerRhythm * 100).toInt()}%", indicatorX, indicatorY + size/2f + 25f, paint)
            
            // Indication du prochain mouvement attendu - ÉNORME
            val nextMove = if (expectingLeft) "⬅️" else "➡️"
            paint.textSize = 48f // ÉNORME
            paint.color = Color.YELLOW
            canvas.drawText(nextMove, indicatorX, indicatorY + 10f, paint)
            
            // INSTRUCTIONS EN TEMPS RÉEL - PLUS GROSSES
            paint.color = Color.CYAN
            paint.textSize = 28f // BEAUCOUP PLUS GROS
            paint.textAlign = Paint.Align.LEFT
            val instruction = if (expectingLeft) "INCLINEZ À GAUCHE!" else "INCLINEZ À DROITE!"
            canvas.drawText(instruction, indicatorX + size/2f + 20f, indicatorY - 20f, paint)
            
            paint.textSize = 22f
            paint.color = Color.WHITE
            canvas.drawText("Suivez le rythme des jambes", indicatorX + size/2f + 20f, indicatorY + 10f, paint)
            
            // Indicateur de qualité du dernier coup
            if (strokeCount > 0) {
                val lastStrokeQuality = if (perfectStrokes > strokeCount * 0.7f) "PARFAIT!" 
                                       else if (playerRhythm > 0.6f) "BIEN" 
                                       else "AMÉLIORE"
                paint.textSize = 20f
                paint.color = if (lastStrokeQuality == "PARFAIT!") Color.GREEN 
                             else if (lastStrokeQuality == "BIEN") Color.YELLOW 
                             else Color.RED
                canvas.drawText(lastStrokeQuality, indicatorX + size/2f + 20f, indicatorY + 40f, paint)
            }
        }
        
        private fun drawHUD(canvas: Canvas, w: Int, h: Int) {
            // HUD BEAUCOUP PLUS VISIBLE
            
            // Distance restante - PLUS GROS
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            paint.textSize = 32f // BEAUCOUP PLUS GROS
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${(totalDistance - playerDistance).toInt()}m restants", w - 30f, 50f, paint)
            
            // Temps - PLUS GROS
            paint.textSize = 28f
            canvas.drawText("Temps: ${raceTime.toInt()}s", w - 30f, 90f, paint)
            
            // Coups parfaits - PLUS GROS
            paint.textSize = 24f
            canvas.drawText("Parfaits: $perfectStrokes", w - 30f, 125f, paint)
            
            // BARRE DE PROGRESSION VISUELLE - NOUVELLE
            val progressBarX = 50f
            val progressBarY = 50f
            val progressBarWidth = w * 0.6f
            val progressBarHeight = 30f
            
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
            paint.textSize = 20f
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
}
