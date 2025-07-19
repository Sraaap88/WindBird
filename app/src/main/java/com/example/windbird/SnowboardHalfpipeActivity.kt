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

    // Variables de gameplay HALFPIPE
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Phases avec durées accessibles
    private val preparationDuration = 5f
    private val runDuration = 35f  // Course longue et accessible
    private val resultsDuration = 6f
    
    // Variables de snowboard
    private var riderX = 0.5f // Position sur le halfpipe (0.0 = gauche, 1.0 = droite)
    private var riderY = 0.8f // Hauteur dans le halfpipe
    private var speed = 0f
    private var airTime = 0f
    private var isInAir = false
    private var lastWallHit = 0L
    
    // Système de tricks
    private var currentTrick = TrickType.NONE
    private var trickProgress = 0f
    private var tricksLanded = 0
    private var trickMultiplier = 1f
    private var comboActive = false
    private var comboCount = 0
    
    // Contrôles gyroscope/accéléromètre
    private var tiltX = 0f
    private var tiltY = 0f
    private var tiltZ = 0f
    private var accelX = 0f
    private var accelY = 0f
    private var accelZ = 0f
    
    // Performance et score
    private var amplitude = 0f // Hauteur des sauts
    private var style = 100f
    private var flow = 100f
    private var difficulty = 0f
    private var totalScore = 0f
    private var finalScore = 0
    private var scoreCalculated = false
    
    // Effets visuels spectaculaires
    private var cameraShake = 0f
    private val snowExplosions = mutableListOf<SnowExplosion>()
    private val trickTrails = mutableListOf<TrickTrail>()
    private val sparkles = mutableListOf<Sparkle>()
    private var backgroundRotation = 0f

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

        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        statusText = TextView(this).apply {
            text = "🏂 SNOWBOARD HALFPIPE - ${tournamentData.playerNames[currentPlayerIndex]}"
            setTextColor(Color.WHITE)
            textSize = 18f
            setBackgroundColor(Color.parseColor("#001144"))
            setPadding(20, 15, 20, 15)
        }

        gameView = SnowboardHalfpipeView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        
        initializeGame()
    }
    
    private fun initializeGame() {
        gameState = GameState.PREPARATION
        phaseTimer = 0f
        riderX = 0.5f
        riderY = 0.8f
        speed = 0f
        airTime = 0f
        isInAir = false
        lastWallHit = 0L
        currentTrick = TrickType.NONE
        trickProgress = 0f
        tricksLanded = 0
        trickMultiplier = 1f
        comboActive = false
        comboCount = 0
        tiltX = 0f
        tiltY = 0f
        tiltZ = 0f
        accelX = 0f
        accelY = 0f
        accelZ = 0f
        amplitude = 0f
        style = 100f
        flow = 100f
        difficulty = 0f
        totalScore = 0f
        finalScore = 0
        scoreCalculated = false
        cameraShake = 0f
        backgroundRotation = 0f
        
        snowExplosions.clear()
        trickTrails.clear()
        sparkles.clear()
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
                tiltX = event.values[0]
                tiltY = event.values[1]
                tiltZ = event.values[2]
            }
            Sensor.TYPE_ACCELEROMETER -> {
                accelX = event.values[0]
                accelY = event.values[1]
                accelZ = event.values[2]
            }
        }

        // Progression du jeu
        phaseTimer += 0.03f // Rythme plus lent

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
            speed = 15f // Vitesse de départ douce
        }
    }
    
    private fun handleRiding() {
        // Mouvement du rider dans le halfpipe
        handleRiderMovement()
        
        // Système de tricks
        handleTrickSystem()
        
        // Gestion de la physique
        updatePhysics()
        
        // Mise à jour des scores
        updatePerformanceMetrics()
        
        // Fin de run
        if (phaseTimer >= runDuration) {
            calculateFinalScore()
            gameState = GameState.RESULTS
            phaseTimer = 0f
        }
    }
    
    private fun handleRiderMovement() {
        // Mouvement horizontal basé sur l'inclinaison gauche/droite
        val horizontalInput = tiltX * 0.6f
        riderX += horizontalInput * 0.008f // Mouvement plus doux
        riderX = riderX.coerceIn(0.1f, 0.9f)
        
        // Détection des murs du halfpipe
        val currentTime = System.currentTimeMillis()
        if ((riderX <= 0.15f || riderX >= 0.85f) && currentTime - lastWallHit > 800) {
            // Impact avec le mur = envol !
            hitWall()
            lastWallHit = currentTime
        }
        
        // Mouvement vertical (gravité et envol)
        if (isInAir) {
            riderY -= 0.006f // Retombée progressive
            airTime += 0.03f
            
            if (riderY >= 0.8f) {
                // Atterrissage
                landTrick()
                isInAir = false
                airTime = 0f
                riderY = 0.8f
            }
        } else {
            // Sur la rampe
            riderY = 0.8f + abs(riderX - 0.5f) * 0.3f // Courbure du halfpipe
        }
        
        // Vitesse basée sur l'inclinaison avant/arrière
        if (tiltY < -0.2f) {
            speed += 0.8f // Accélération
        } else if (tiltY > 0.2f) {
            speed -= 0.6f // Freinage
        }
        
        speed = speed.coerceIn(8f, 35f) // Vitesse modérée
    }
    
    private fun hitWall() {
        // Envol depuis le mur !
        isInAir = true
        riderY = 0.4f // Hauteur de saut
        amplitude = maxOf(amplitude, 0.4f)
        
        // Effets visuels
        cameraShake = 0.4f
        generateSnowExplosion()
        
        // Bonus de flow pour bon timing
        if (speed > 20f) {
            flow += 2f
        }
    }
    
    private fun handleTrickSystem() {
        if (!isInAir) return
        
        // Détection des tricks basée sur les mouvements
        val rotationThreshold = 0.8f
        val flipThreshold = 1.2f
        
        if (currentTrick == TrickType.NONE) {
            // Nouveau trick
            when {
                abs(tiltZ) > rotationThreshold && abs(tiltX) < 0.5f -> {
                    startTrick(TrickType.SPIN)
                }
                abs(tiltY) > flipThreshold && abs(tiltX) < 0.5f -> {
                    startTrick(TrickType.FLIP)
                }
                abs(tiltX) > rotationThreshold && abs(accelZ) > 8f -> {
                    startTrick(TrickType.GRAB)
                }
                abs(tiltX) > 1.5f && abs(tiltZ) > 1.5f -> {
                    startTrick(TrickType.COMBO)
                }
            }
        } else {
            // Progression du trick en cours
            updateTrickProgress()
        }
    }
    
    private fun startTrick(type: TrickType) {
        currentTrick = type
        trickProgress = 0f
        
        // Effets visuels selon le trick
        when (type) {
            TrickType.SPIN -> generateSpinTrail()
            TrickType.FLIP -> generateFlipEffect()
            TrickType.GRAB -> generateGrabSparkles()
            TrickType.COMBO -> generateComboExplosion()
            else -> {}
        }
    }
    
    private fun updateTrickProgress() {
        when (currentTrick) {
            TrickType.SPIN -> {
                trickProgress += abs(tiltZ) * 0.03f
                backgroundRotation += tiltZ * 2f
            }
            TrickType.FLIP -> {
                trickProgress += abs(tiltY) * 0.02f
            }
            TrickType.GRAB -> {
                trickProgress += (abs(tiltX) + abs(accelZ) / 10f) * 0.02f
            }
            TrickType.COMBO -> {
                trickProgress += (abs(tiltX) + abs(tiltZ) + abs(tiltY)) * 0.015f
            }
            else -> {}
        }
        
        trickProgress = trickProgress.coerceIn(0f, 1f)
    }
    
    private fun landTrick() {
        if (currentTrick != TrickType.NONE && trickProgress > 0.3f) {
            // Trick réussi !
            val trickScore = calculateTrickScore()
            totalScore += trickScore
            tricksLanded++
            
            // Système de combo
            if (comboActive) {
                comboCount++
                trickMultiplier += 0.2f
            } else {
                comboActive = true
                comboCount = 1
                trickMultiplier = 1.2f
            }
            
            // Effets selon la qualité
            if (trickProgress > 0.8f) {
                generatePerfectLanding()
                style += 5f
            } else {
                generateGoodLanding()
                style += 2f
            }
            
        } else {
            // Trick raté
            comboActive = false
            comboCount = 0
            trickMultiplier = 1f
            style -= 3f
        }
        
        currentTrick = TrickType.NONE
        trickProgress = 0f
    }
    
    private fun calculateTrickScore(): Float {
        val baseScore = when (currentTrick) {
            TrickType.SPIN -> 15f
            TrickType.FLIP -> 20f
            TrickType.GRAB -> 18f
            TrickType.COMBO -> 30f
            else -> 0f
        }
        
        val progressBonus = trickProgress * 10f
        val airBonus = airTime * 5f
        val difficultyBonus = difficulty * 2f
        
        difficulty += when (currentTrick) {
            TrickType.SPIN -> 1f
            TrickType.FLIP -> 2f
            TrickType.GRAB -> 1.5f
            TrickType.COMBO -> 3f
            else -> 0f
        }
        
        return (baseScore + progressBonus + airBonus + difficultyBonus) * trickMultiplier
    }
    
    private fun updatePhysics() {
        // Dégradation naturelle
        style -= 0.05f
        flow -= 0.03f
        
        // Bonus pour fluidité
        if (speed > 18f && !isInAir) {
            flow += 0.1f
        }
        
        // Contraintes
        style = style.coerceIn(60f, 120f)
        flow = flow.coerceIn(60f, 120f)
    }
    
    private fun updatePerformanceMetrics() {
        // Mise à jour continue des métriques
        if (airTime > 0.5f) {
            amplitude = maxOf(amplitude, airTime * 0.3f)
        }
    }
    
    private fun generateSnowExplosion() {
        repeat(12) {
            snowExplosions.add(SnowExplosion(
                x = kotlin.random.Random.nextFloat() * 800f + 100f,
                y = kotlin.random.Random.nextFloat() * 200f + 500f,
                vx = (kotlin.random.Random.nextFloat() - 0.5f) * 12f,
                vy = kotlin.random.Random.nextFloat() * -8f - 4f,
                life = 1.5f
            ))
        }
    }
    
    private fun generateSpinTrail() {
        repeat(6) {
            trickTrails.add(TrickTrail(
                x = kotlin.random.Random.nextFloat() * 800f + 100f,
                y = kotlin.random.Random.nextFloat() * 400f + 200f,
                type = TrickType.SPIN,
                life = 1f
            ))
        }
    }
    
    private fun generateFlipEffect() {
        cameraShake = 0.3f
        repeat(8) {
            sparkles.add(Sparkle(
                x = kotlin.random.Random.nextFloat() * 400f + 300f,
                y = kotlin.random.Random.nextFloat() * 300f + 250f,
                color = Color.CYAN,
                life = 1.2f
            ))
        }
    }
    
    private fun generateGrabSparkles() {
        repeat(10) {
            sparkles.add(Sparkle(
                x = kotlin.random.Random.nextFloat() * 300f + 350f,
                y = kotlin.random.Random.nextFloat() * 200f + 300f,
                color = Color.YELLOW,
                life = 1f
            ))
        }
    }
    
    private fun generateComboExplosion() {
        cameraShake = 0.6f
        repeat(15) {
            sparkles.add(Sparkle(
                x = kotlin.random.Random.nextFloat() * 600f + 200f,
                y = kotlin.random.Random.nextFloat() * 400f + 200f,
                color = Color.MAGENTA,
                life = 1.5f
            ))
        }
    }
    
    private fun generatePerfectLanding() {
        repeat(20) {
            sparkles.add(Sparkle(
                x = kotlin.random.Random.nextFloat() * 800f + 100f,
                y = kotlin.random.Random.nextFloat() * 200f + 500f,
                color = Color.parseColor("#FFD700"),
                life = 2f
            ))
        }
    }
    
    private fun generateGoodLanding() {
        repeat(8) {
            sparkles.add(Sparkle(
                x = kotlin.random.Random.nextFloat() * 400f + 300f,
                y = kotlin.random.Random.nextFloat() * 150f + 550f,
                color = Color.parseColor("#00FF00"),
                life = 1f
            ))
        }
    }
    
    private fun updateEffects() {
        // Mise à jour des explosions de neige
        snowExplosions.removeAll { explosion ->
            explosion.x += explosion.vx
            explosion.y += explosion.vy
            explosion.life -= 0.02f
            explosion.life <= 0f || explosion.y > 1000f
        }
        
        // Mise à jour des trails de tricks
        trickTrails.removeAll { trail ->
            trail.life -= 0.03f
            trail.life <= 0f
        }
        
        // Mise à jour des sparkles
        sparkles.removeAll { sparkle ->
            sparkle.y -= 1f
            sparkle.life -= 0.02f
            sparkle.life <= 0f
        }
        
        cameraShake = maxOf(0f, cameraShake - 0.02f)
        backgroundRotation *= 0.95f // Ralentissement progressif
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
            val amplitudeBonus = (amplitude * 100).toInt()
            val styleBonus = ((style - 100f) * 3).toInt()
            val flowBonus = ((flow - 100f) * 2).toInt()
            val tricksBonus = totalScore.toInt()
            val comboBonus = if (comboCount > 3) comboCount * 15 else 0
            val difficultyBonus = (difficulty * 2).toInt()
            
            finalScore = maxOf(60, amplitudeBonus + styleBonus + flowBonus + tricksBonus + comboBonus + difficultyBonus)
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
                val aiScore = (95..190).random()
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
            GameState.PREPARATION -> "🏂 ${tournamentData.playerNames[currentPlayerIndex]} | Préparation... ${(preparationDuration - phaseTimer).toInt() + 1}s"
            GameState.RIDING -> {
                val trickText = if (currentTrick != TrickType.NONE) " | ${currentTrick.displayName}" else ""
                val comboText = if (comboActive) " | COMBO x$comboCount" else ""
                "🏂 ${tournamentData.playerNames[currentPlayerIndex]} | Tricks: $tricksLanded$trickText$comboText"
            }
            GameState.RESULTS -> "🏆 ${tournamentData.playerNames[currentPlayerIndex]} | Score: ${finalScore} | Tricks: $tricksLanded"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Run terminé!"
        }
    }

    inner class SnowboardHalfpipeView(context: Context) : View(context) {
        private val paint = Paint()

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            
            // Appliquer effets de caméra
            canvas.save()
            if (cameraShake > 0f) {
                canvas.translate(
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 12f,
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 12f
                )
            }
            canvas.rotate(backgroundRotation * 0.1f, w/2f, h/2f)
            
            when (gameState) {
                GameState.PREPARATION -> drawPreparation(canvas, w, h)
                GameState.RIDING -> drawRiding(canvas, w, h)
                GameState.RESULTS -> drawResults(canvas, w, h)
                GameState.FINISHED -> drawResults(canvas, w, h)
            }
            
            drawEffects(canvas, w, h)
            canvas.restore()
        }
        
        private fun drawPreparation(canvas: Canvas, w: Int, h: Int) {
            // Fond de halfpipe
            paint.color = Color.parseColor("#E6F0FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Halfpipe en perspective
            drawHalfpipeStructure(canvas, w, h)
            
            // Instructions spectaculaires
            paint.color = Color.parseColor("#001144")
            paint.textSize = 36f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🏂 SNOWBOARD HALFPIPE 🏂", w/2f, h * 0.15f, paint)
            
            paint.textSize = 22f
            paint.color = Color.parseColor("#0066CC")
            canvas.drawText("Préparez-vous pour les tricks...", w/2f, h * 0.85f, paint)
            
            paint.textSize = 16f
            paint.color = Color.parseColor("#666666")
            canvas.drawText("📱 Inclinez gauche/droite pour bouger", w/2f, h * 0.9f, paint)
            canvas.drawText("📱 Faites des mouvements en l'air pour les tricks!", w/2f, h * 0.95f, paint)
        }
        
        private fun drawRiding(canvas: Canvas, w: Int, h: Int) {
            // Fond dynamique
            val bgColor = if (isInAir) Color.parseColor("#F0F8FF") else Color.parseColor("#E6F0FF")
            paint.color = bgColor
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Halfpipe
            drawHalfpipeStructure(canvas, w, h)
            
            // Rider
            drawSnowboarder(canvas, w, h)
            
            // Interface de jeu
            drawGameInterface(canvas, w, h)
            
            // Instructions dynamiques
            if (isInAir) {
                paint.color = Color.parseColor("#FF6600")
                paint.textSize = 24f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("🌟 BOUGEZ POUR LES TRICKS! 🌟", w/2f, 50f, paint)
            } else {
                paint.color = Color.parseColor("#001144")
                paint.textSize = 18f
                canvas.drawText("📱 Touchez les bords pour décoller!", w/2f, 40f, paint)
            }
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            // Fond festif
            paint.color = Color.parseColor("#FFF8DC")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Bandeau doré
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
            
            // Score final
            paint.color = Color.parseColor("#001144")
            paint.textSize = 72f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore}", w/2f, h * 0.2f, paint)
            
            paint.textSize = 28f
            canvas.drawText("POINTS", w/2f, h * 0.3f, paint)
            
            // Détails performance
            paint.color = Color.parseColor("#333333")
            paint.textSize = 20f
            canvas.drawText("🏂 Tricks réussis: $tricksLanded", w/2f, h * 0.5f, paint)
            canvas.drawText("⭐ Style: ${style.toInt()}%", w/2f, h * 0.55f, paint)
            canvas.drawText("🌊 Flow: ${flow.toInt()}%", w/2f, h * 0.6f, paint)
            canvas.drawText("🎯 Difficulté: ${difficulty.toInt()}", w/2f, h * 0.65f, paint)
            canvas.drawText("📏 Amplitude: ${(amplitude * 100).toInt()}%", w/2f, h * 0.7f, paint)
            
            if (comboCount > 1) {
                paint.color = Color.parseColor("#FF6600")
                canvas.drawText("🔥 Meilleur combo: x$comboCount", w/2f, h * 0.75f, paint)
            }
        }
        
        private fun drawHalfpipeStructure(canvas: Canvas, w: Int, h: Int) {
            // Structure du halfpipe en U
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            
            // Partie centrale (fond)
            canvas.drawRect(w * 0.2f, h * 0.7f, w * 0.8f, h.toFloat(), paint)
            
            // Rampes gauche et droite
            val leftRampPath = Path()
            leftRampPath.moveTo(w * 0.2f, h * 0.7f)
            leftRampPath.quadTo(w * 0.1f, h * 0.5f, w * 0.05f, h * 0.3f)
            leftRampPath.lineTo(w * 0.15f, h * 0.25f)
            leftRampPath.quadTo(w * 0.25f, h * 0.45f, w * 0.3f, h * 0.65f)
            leftRampPath.close()
            canvas.drawPath(leftRampPath, paint)
            
            val rightRampPath = Path()
            rightRampPath.moveTo(w * 0.8f, h * 0.7f)
            rightRampPath.quadTo(w * 0.9f, h * 0.5f, w * 0.95f, h * 0.3f)
            rightRampPath.lineTo(w * 0.85f, h * 0.25f)
            rightRampPath.quadTo(w * 0.75f, h * 0.45f, w * 0.7f, h * 0.65f)
            rightRampPath.close()
            canvas.drawPath(rightRampPath, paint)
            
            // Bords du halfpipe
            paint.color = Color.parseColor("#DDDDDD")
            paint.strokeWidth = 6f
            paint.style = Paint.Style.STROKE
            canvas.drawPath(leftRampPath, paint)
            canvas.drawPath(rightRampPath, paint)
            
            paint.style = Paint.Style.FILL
        }
        
        private fun drawSnowboarder(canvas: Canvas, w: Int, h: Int) {
            val riderScreenX = riderX * w
            val riderScreenY = riderY * h
            
            canvas.save()
            canvas.translate(riderScreenX, riderScreenY)
            
            // Rotation selon les tricks
            when (currentTrick) {
                TrickType.SPIN -> canvas.rotate(trickProgress * 720f + backgroundRotation)
                TrickType.FLIP -> canvas.rotate(trickProgress * 360f, 1f, 0f)
                TrickType.GRAB -> canvas.scale(1f + trickProgress * 0.3f, 1f + trickProgress * 0.3f)
                TrickType.COMBO -> {
                    canvas.rotate(trickProgress * 540f)
                    canvas.scale(1f + trickProgress * 0.5f, 1f + trickProgress * 0.5f)
                }
                else -> {}
            }
            
            // Corps du snowboarder
            paint.color = Color.parseColor("#FF6600")
            canvas.drawCircle(0f, 0f, 18f, paint)
            
            // Snowboard
            paint.color = Color.parseColor("#4400FF")
            canvas.drawRoundRect(-25f, -8f, 25f, 8f, 8f, 8f, paint)
            
            // Bras selon le trick
            paint.color = Color.parseColor("#FF6600")
            paint.strokeWidth = 6f
            paint.style = Paint.Style.STROKE
            
            if (currentTrick == TrickType.GRAB) {
                // Position grab
                canvas.drawLine(-15f, -10f, -20f, 5f, paint)
                canvas.drawLine(15f, -10f, 20f, 5f, paint)
            } else {
                // Position normale
                canvas.drawLine(-12f, -5f, -20f, -15f, paint)
                canvas.drawLine(12f, -5f, 20f, -15f, paint)
            }
            
            paint.style = Paint.Style.FILL
            canvas.restore()
            
            // Ombre si au sol
            if (!isInAir) {
                paint.color = Color.parseColor("#33000000")
                canvas.drawOval(riderScreenX - 30f, h * 0.82f, riderScreenX + 30f, h * 0.86f, paint)
            }
        }
        
        private fun drawGameInterface(canvas: Canvas, w: Int, h: Int) {
            val baseY = h - 140f
            
            // Score en temps réel
            paint.color = Color.parseColor("#001144")
            paint.textSize = 20f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Score: ${totalScore.toInt()}", 20f, baseY, paint)
            canvas.drawText("Tricks: $tricksLanded", 20f, baseY + 25f, paint)
            
            // Trick en cours
            if (currentTrick != TrickType.NONE) {
                paint.color = Color.parseColor("#FF6600")
                paint.textSize = 24f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("${currentTrick.displayName}: ${(trickProgress * 100).toInt()}%", w/2f, baseY, paint)
                
                // Barre de progression
                paint.color = Color.parseColor("#333333")
                canvas.drawRect(w/2f - 100f, baseY + 10f, w/2f + 100f, baseY + 25f, paint)
                
                paint.color = Color.parseColor("#00FF00")
                val progressWidth = trickProgress * 200f
                canvas.drawRect(w/2f - 100f, baseY + 10f, w/2f - 100f + progressWidth, baseY + 25f, paint)
            }
            
            // Métriques de performance
            drawMeter(canvas, w - 200f, baseY, 150f, style / 120f, "STYLE", Color.parseColor("#FF44AA"))
            drawMeter(canvas, w - 200f, baseY + 30f, 150f, flow / 120f, "FLOW", Color.parseColor("#44AAFF"))
            
            // Combo actif
            if (comboActive) {
                paint.color = Color.parseColor("#FFD700")
                paint.textSize = 18f
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("🔥 COMBO x$comboCount", w - 20f, baseY + 60f, paint)
            }
            
            // Air time
            if (isInAir) {
                paint.color = Color.parseColor("#00FFFF")
                paint.textSize = 16f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("⏱️ Air time: ${airTime.toString().take(4)}s", w/2f, h - 50f, paint)
            }
        }
        
        private fun drawMeter(canvas: Canvas, x: Float, y: Float, width: Float, 
                             value: Float, label: String, color: Int) {
            // Fond
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(x, y, x + width, y + 15f, paint)
            
            // Barre
            paint.color = color
            val filledWidth = value.coerceIn(0f, 1f) * width
            canvas.drawRect(x, y, x + filledWidth, y + 15f, paint)
            
            // Label
            paint.color = Color.WHITE
            paint.textSize = 12f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("$label: ${(value * 100).toInt()}%", x, y - 3f, paint)
        }
        
        private fun drawEffects(canvas: Canvas, w: Int, h: Int) {
            // Explosions de neige
            paint.color = Color.WHITE
            for (explosion in snowExplosions) {
                paint.alpha = (explosion.life * 255).toInt()
                canvas.drawCircle(explosion.x, explosion.y, explosion.life * 8f, paint)
            }
            paint.alpha = 255
            
            // Trails de tricks
            for (trail in trickTrails) {
                val alpha = (trail.life * 180).toInt()
                paint.alpha = alpha
                paint.color = when (trail.type) {
                    TrickType.SPIN -> Color.CYAN
                    TrickType.FLIP -> Color.YELLOW
                    TrickType.GRAB -> Color.GREEN
                    TrickType.COMBO -> Color.MAGENTA
                    else -> Color.WHITE
                }
                canvas.drawCircle(trail.x, trail.y, trail.life * 12f, paint)
            }
            paint.alpha = 255
            
            // Sparkles
            for (sparkle in sparkles) {
                paint.alpha = (sparkle.life * 255).toInt()
                paint.color = sparkle.color
                canvas.drawCircle(sparkle.x, sparkle.y, sparkle.life * 6f, paint)
            }
            paint.alpha = 255
        }
    }

    enum class TrickType(val displayName: String) {
        NONE(""),
        SPIN("360°"),
        FLIP("FLIP"),
        GRAB("GRAB"),
        COMBO("COMBO")
    }
    
    data class SnowExplosion(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float
    )
    
    data class TrickTrail(
        val x: Float,
        val y: Float,
        val type: TrickType,
        var life: Float
    )
    
    data class Sparkle(
        val x: Float,
        var y: Float,
        val color: Int,
        var life: Float
    )

    enum class GameState {
        PREPARATION, RIDING, RESULTS, FINISHED
    }
}
