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
    
    // Variables de course - Style Winter Games
    private var playerDistance = 0f
    private var aiDistance = 0f
    private val totalDistance = 250f // 250 mètres exactement comme Winter Games
    private var playerSpeed = 0f
    private var aiSpeed = 0f
    
    // Animation et rythme - CLÉ DE L'EXPÉRIENCE WINTER GAMES
    private var playerAnimFrame = 0
    private var aiAnimFrame = 0
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
    private var aiFinished = false
    private var finalScore = 0
    private var scoreCalculated = false
    
    // IA du patineur adverse
    private var aiStrokeTimer = 0f
    private val aiStrokeInterval = 0.4f // Rythme IA

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
        aiDistance = 0f
        playerSpeed = 0f
        aiSpeed = 0f
        playerAnimFrame = 0
        aiAnimFrame = 0
        lastStrokeTime = 0L
        playerRhythm = 0f
        strokeCount = 0
        perfectStrokes = 0
        tiltX = 0f
        lastTiltDirection = 0
        expectingLeft = true
        raceTime = 0f
        playerFinished = false
        aiFinished = false
        finalScore = 0
        scoreCalculated = false
        aiStrokeTimer = 0f
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
        if (playerFinished && aiFinished) {
            calculateFinalScore()
            gameState = GameState.RESULTS
            phaseTimer = 0f
            return
        }
        
        // Système de patinage rythmé - EXACTEMENT comme Winter Games
        handleRhythmicSkating()
        
        // IA de l'adversaire
        updateAI()
        
        // Mise à jour des positions
        updatePositions()
        
        // Vérifier les arrivées
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
        val actualInterval = currentTime - lastStrokeTime
        
        val rhythmAccuracy = 1f - abs(actualInterval - idealInterval) / idealInterval.toFloat()
        playerRhythm = (playerRhythm * 0.7f + rhythmAccuracy.coerceIn(0f, 1f) * 0.3f)
        
        return playerRhythm.coerceIn(0.3f, 1f)
    }
    
    private fun updateAI() {
        // IA qui patine avec un rythme constant (comme dans Winter Games)
        aiStrokeTimer += 0.025f
        
        if (aiStrokeTimer >= aiStrokeInterval) {
            aiStrokeTimer = 0f
            aiAnimFrame = (aiAnimFrame + 1) % 8
            
            // IA avec performance légèrement variable
            val aiPerformance = 0.7f + kotlin.random.Random.nextFloat() * 0.3f
            aiSpeed += aiPerformance * 1.1f
            aiSpeed = aiSpeed.coerceAtMost(7.5f)
        }
        
        // Décélération IA
        aiSpeed *= 0.95f
    }
    
    private fun updatePositions() {
        if (!playerFinished) {
            playerDistance += playerSpeed * 0.025f * 60f // 60fps reference
        }
        
        if (!aiFinished) {
            aiDistance += aiSpeed * 0.025f * 60f
        }
    }
    
    private fun checkFinishLine() {
        if (playerDistance >= totalDistance && !playerFinished) {
            playerFinished = true
        }
        
        if (aiDistance >= totalDistance && !aiFinished) {
            aiFinished = true
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
            val timeBonus = maxOf(0, 200 - raceTime.toInt()) * 2
            val rhythmBonus = (playerRhythm * 80).toInt()
            val perfectStrokeBonus = perfectStrokes * 8
            val positionBonus = if (playerDistance >= aiDistance) 50 else 20
            
            finalScore = maxOf(80, timeBonus + rhythmBonus + perfectStrokeBonus + positionBonus)
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

    inner class PatinageVitesseView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        // Images exactement comme Winter Games
        private var skaterSpriteSheet: Bitmap? = null
        
        init {
            // Utiliser directement les sprites créés en code pour voir le jeu de jambes
            createFallbackSprites()
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
            // Arrière-plan bleu ciel
            paint.color = Color.parseColor("#87CEEB")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste de glace
            paint.color = Color.WHITE
            canvas.drawRect(0f, h * 0.3f, w.toFloat(), h.toFloat(), paint)
            
            // Séparation des couloirs (ligne du milieu)
            paint.color = Color.BLACK
            paint.strokeWidth = 4f
            canvas.drawLine(0f, h * 0.65f, w.toFloat(), h * 0.65f, paint)
            
            // Marques de distance
            paint.textSize = 16f
            paint.textAlign = Paint.Align.CENTER
            for (i in 0..5) {
                val x = w * 0.1f + i * (w * 0.8f / 5f)
                canvas.drawLine(x, h * 0.3f, x, h * 0.35f, paint)
                canvas.drawText("${i * 50}m", x, h * 0.28f, paint)
            }
            
            // Instructions
            paint.color = Color.parseColor("#000066")
            paint.textSize = 28f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("⛸️ PATINAGE VITESSE 250M ⛸️", w/2f, h * 0.1f, paint)
            
            paint.textSize = 20f
            paint.color = Color.parseColor("#003399")
            canvas.drawText("Inclinez gauche-droite en RYTHME avec les jambes", w/2f, h * 0.15f, paint)
            canvas.drawText("Suivez l'animation du patineur!", w/2f, h * 0.19f, paint)
            
            // Décompte
            val countdown = (preparationDuration - phaseTimer).toInt() + 1
            paint.textSize = 48f
            paint.color = Color.RED
            canvas.drawText("Prêt dans ${countdown}", w/2f, h * 0.25f, paint)
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
            
            // Ligne de séparation des couloirs (EXACTEMENT comme Winter Games)
            paint.color = Color.parseColor("#CCCCCC")
            paint.strokeWidth = 3f
            canvas.drawLine(0f, h * 0.65f, w.toFloat(), h * 0.65f, paint)
            
            // Marques de progression qui défilent
            val scrollOffset = ((playerDistance + aiDistance) / 2f) % 50f
            paint.color = Color.parseColor("#DDDDDD")
            paint.strokeWidth = 2f
            
            for (i in -2..12) {
                val x = w * 0.1f + i * (w * 0.8f / 10f) - scrollOffset * (w * 0.8f / 50f)
                if (x > -20f && x < w + 20f) {
                    canvas.drawLine(x, h * 0.3f, x, h.toFloat(), paint)
                    
                    // Numéro de distance
                    val distance = ((playerDistance + aiDistance) / 2f + i * 25f).toInt()
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
            // Position selon la distance parcourue
            val playerX = w * 0.1f + (playerDistance / totalDistance) * (w * 0.8f)
            val aiX = w * 0.1f + (aiDistance / totalDistance) * (w * 0.8f)
            
            // IA en haut (comme Winter Games)
            drawSkater(canvas, aiX, h * 0.48f, aiAnimFrame, Color.parseColor("#FF6600"), false)
            
            // Joueur en bas
            drawSkater(canvas, playerX, h * 0.82f, playerAnimFrame, Color.parseColor("#0066FF"), true)
            
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
            // Indicateur de rythme comme Winter Games
            val indicatorX = 50f
            val indicatorY = h - 100f
            
            // Fond
            paint.color = Color.parseColor("#333333")
            paint.style = Paint.Style.FILL
            canvas.drawRect(indicatorX - 30f, indicatorY - 30f, indicatorX + 30f, indicatorY + 30f, paint)
            
            // Barre de rythme
            paint.color = if (playerRhythm > 0.7f) Color.GREEN else if (playerRhythm > 0.4f) Color.YELLOW else Color.RED
            val rhythmHeight = playerRhythm * 50f
            canvas.drawRect(indicatorX - 25f, indicatorY + 25f - rhythmHeight, indicatorX + 25f, indicatorY + 25f, paint)
            
            // Label
            paint.color = Color.WHITE
            paint.textSize = 12f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("RYTHME", indicatorX, indicatorY - 35f, paint)
            canvas.drawText("${(playerRhythm * 100).toInt()}%", indicatorX, indicatorY + 45f, paint)
            
            // Indication du prochain mouvement attendu
            val nextMove = if (expectingLeft) "⬅️" else "➡️"
            paint.textSize = 20f
            paint.color = Color.YELLOW
            canvas.drawText(nextMove, indicatorX, indicatorY, paint)
        }
        
        private fun drawHUD(canvas: Canvas, w: Int, h: Int) {
            // Distance restante
            paint.color = Color.WHITE
            paint.textSize = 18f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Distance: ${(totalDistance - playerDistance).toInt()}m", w - 20f, 30f, paint)
            
            // Temps
            canvas.drawText("Temps: ${raceTime.toInt()}s", w - 20f, 55f, paint)
            
            // Coups parfaits
            canvas.drawText("Parfaits: $perfectStrokes", w - 20f, 80f, paint)
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            // Fond doré
            val gradient = LinearGradient(0f, 0f, 0f, h.toFloat(),
                Color.parseColor("#FFD700"), Color.parseColor("#FFF8DC"), Shader.TileMode.CLAMP)
            paint.shader = gradient
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
            
            // Résultat de la course
            paint.color = Color.parseColor("#8B0000")
            paint.textSize = 48f
            paint.textAlign = Paint.Align.CENTER
            
            val won = playerDistance >= aiDistance
            val resultText = if (won) "🥇 VICTOIRE!" else "🥈 2ème place"
            canvas.drawText(resultText, w/2f, h * 0.2f, paint)
            
            // Score final
            paint.textSize = 64f
            canvas.drawText("${finalScore} POINTS", w/2f, h * 0.35f, paint)
            
            // Détails
            paint.textSize = 20f
            paint.color = Color.parseColor("#333333")
            canvas.drawText("Temps final: ${raceTime.toInt()}s", w/2f, h * 0.6f, paint)
            canvas.drawText("Distance: ${playerDistance.toInt()}m vs ${aiDistance.toInt()}m", w/2f, h * 0.65f, paint)
            canvas.drawText("Rythme moyen: ${(playerRhythm * 100).toInt()}%", w/2f, h * 0.7f, paint)
            canvas.drawText("Coups parfaits: $perfectStrokes", w/2f, h * 0.75f, paint)
        }
    }

    enum class GameState {
        PREPARATION, COUNTDOWN, RACE, RESULTS, FINISHED
    }
}
