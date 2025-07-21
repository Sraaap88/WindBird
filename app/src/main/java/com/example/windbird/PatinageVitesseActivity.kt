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
import android.os.SystemClock
import kotlin.math.*

class PatinageVitesseActivity : Activity(), SensorEventListener {

    private lateinit var gameView: PatinageVitesseView
    private lateinit var statusText: TextView

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null

    // Variables de gameplay PATINAGE - RALENTI
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Phases avec durées PLUS LONGUES et accessibles
    private val preparationDuration = 8f
    private val raceDuration = 35f
    private val sprintDuration = 15f
    private val resultsDuration = 8f
    
    // Variables de course
    private var speed = 0f
    private var maxSpeed = 45f
    private var distance = 0f
    private var totalDistance = 1000f
    private var rhythm = 0f
    private var energy = 100f
    private var technique = 100f
    
    // Contrôles gyroscope pour patinage alterné - SENSIBILITÉ RÉDUITE
    private var tiltX = 0f
    private var tiltY = 0f
    private var tiltZ = 0f
    private var lastStroke = 0L
    private var strokeCount = 0
    private var leftStroke = true
    private var perfectStrokes = 0
    
    // Performance et résultats
    private var raceTime = 0f
    private var finalScore = 0
    private var scoreCalculated = false
    
    // Effets visuels
    private var cameraShake = 0f
    private val iceTrails = mutableListOf<IceTrail>()
    private val sparkles = mutableListOf<IceSparkle>()

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
            text = "⛸️ PATINAGE VITESSE - ${tournamentData.playerNames[currentPlayerIndex]}"
            setTextColor(Color.WHITE)
            textSize = 22f
            setBackgroundColor(Color.parseColor("#001133"))
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
        speed = 0f
        distance = 0f
        rhythm = 0f
        energy = 100f
        technique = 100f
        tiltX = 0f
        tiltY = 0f
        tiltZ = 0f
        lastStroke = 0L
        strokeCount = 0
        leftStroke = true
        perfectStrokes = 0
        raceTime = 0f
        finalScore = 0
        scoreCalculated = false
        cameraShake = 0f
        iceTrails.clear()
        sparkles.clear()
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
        tiltY = event.values[1]
        tiltZ = event.values[2]

        // Progression du jeu PLUS LENTE
        phaseTimer += 0.025f
        if (gameState == GameState.RACE || gameState == GameState.SPRINT) {
            raceTime += 0.025f
        }

        when (gameState) {
            GameState.PREPARATION -> handlePreparation()
            GameState.RACE -> handleRace()
            GameState.SPRINT -> handleSprint()
            GameState.RESULTS -> handleResults()
            GameState.FINISHED -> {}
        }

        updateEffects()
        updateStatus()
        gameView.invalidate()
    }
    
    private fun handlePreparation() {
        if (phaseTimer >= preparationDuration) {
            gameState = GameState.RACE
            phaseTimer = 0f
        }
    }
    
    private fun handleRace() {
        // PATINAGE ALTERNÉ - Le cœur du gameplay !
        handleSkatingMovement()
        
        // Progression sur la piste
        updateRaceProgress()
        
        // Gestion de l'énergie et technique
        updatePerformanceStats()
        
        // Transition vers sprint final
        if (distance >= totalDistance * 0.8f) {
            gameState = GameState.SPRINT
            phaseTimer = 0f
            cameraShake = 0.3f
        }
    }
    
    private fun handleSkatingMovement() {
        val currentTime = System.currentTimeMillis()
        val minStrokeInterval = 400L
        
        // Détection des mouvements de patinage alternés - SENSIBILITÉ RÉDUITE
        if (currentTime - lastStroke > minStrokeInterval) {
            var strokeDetected = false
            var strokePower = 0f
            
            if (leftStroke && tiltX < -0.6f && abs(tiltY) < 0.4f) {
                // Poussée pied gauche
                strokeDetected = true
                strokePower = calculateStrokePower(tiltX, tiltY, tiltZ)
                leftStroke = false
                
            } else if (!leftStroke && tiltX > 0.6f && abs(tiltY) < 0.4f) {
                // Poussée pied droit
                strokeDetected = true
                strokePower = calculateStrokePower(tiltX, tiltY, tiltZ)
                leftStroke = true
            }
            
            if (strokeDetected) {
                lastStroke = currentTime
                strokeCount++
                
                // Augmentation de vitesse basée sur la technique - PLUS MODÉRÉE
                speed += strokePower * (technique / 100f) * 2f
                
                // Bonus pour rythme régulier
                updateRhythm()
                
                // Évaluation de la technique
                if (strokePower > 0.7f) {
                    perfectStrokes++
                    generateSparkles()
                }
                
                // Traces sur la glace
                addIceTrail()
                
                // Coût en énergie - RÉDUIT
                energy -= 1f
            }
        }
        
        // Décélération naturelle PLUS LENTE
        speed *= 0.985f
        speed = speed.coerceIn(0f, maxSpeed)
        
        // Pénalité pour manque d'énergie MOINS SÉVÈRE
        if (energy < 20f) {
            speed *= 0.97f
        }
    }
    
    private fun calculateStrokePower(x: Float, y: Float, z: Float): Float {
        val tiltPower = abs(x).coerceIn(0.6f, 1.5f) / 1.5f
        val stabilityBonus = 1f - (abs(y) + abs(z)) / 3f
        return (tiltPower * stabilityBonus.coerceIn(0.4f, 1f))
    }
    
    private fun updateRhythm() {
        val currentTime = System.currentTimeMillis()
        val idealInterval = 500L
        val actualInterval = currentTime - lastStroke
        
        val rhythmAccuracy = 1f - abs(actualInterval - idealInterval) / idealInterval.toFloat()
        rhythm = (rhythm * 0.85f + rhythmAccuracy.coerceIn(0f, 1f) * 0.15f)
        
        // Bonus de vitesse pour bon rythme - PLUS GÉNÉREUX
        if (rhythm > 0.6f) {
            speed += 0.8f
        }
    }
    
    private fun updateRaceProgress() {
        // Progression linéaire de la distance
        val progressSpeed = speed * 0.15f // Ajustée pour vue de profil
        distance += progressSpeed
        
        distance = distance.coerceAtMost(totalDistance)
    }
    
    private fun updatePerformanceStats() {
        // Récupération d'énergie progressive - PLUS RAPIDE
        energy += 0.15f
        energy = energy.coerceIn(0f, 100f)
        
        // Amélioration technique avec coups parfaits - PLUS TOLÉRANT
        if (perfectStrokes > strokeCount * 0.5f) {
            technique += 0.08f
        } else {
            technique -= 0.015f
        }
        technique = technique.coerceIn(70f, 110f)
    }
    
    private fun handleSprint() {
        // Sprint final - plus intense !
        handleSkatingMovement()
        updateRaceProgress()
        
        // Bonus de vitesse pour sprint - RÉDUIT
        speed += 0.3f
        
        // Consommation d'énergie accrue - MOINS SÉVÈRE
        energy -= 0.3f
        
        if (distance >= totalDistance) {
            calculateFinalScore()
            gameState = GameState.RESULTS
            phaseTimer = 0f
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
            val timeBonus = maxOf(0, 140 - raceTime.toInt()) * 2
            val speedBonus = (speed / maxSpeed * 70).toInt()
            val rhythmBonus = (rhythm * 50).toInt()
            val techniqueBonus = ((technique - 100f) * 2.5f).toInt()
            val perfectStrokeBonus = perfectStrokes * 4
            
            finalScore = maxOf(60, timeBonus + speedBonus + rhythmBonus + techniqueBonus + perfectStrokeBonus)
            scoreCalculated = true
        }
    }
    
    private fun addIceTrail() {
        val trailX = kotlin.random.Random.nextFloat() * 600f + 200f
        val trailY = kotlin.random.Random.nextFloat() * 100f + 400f // Ajusté pour vue de profil
        iceTrails.add(IceTrail(trailX, trailY, System.currentTimeMillis()))
        
        if (iceTrails.size > 15) {
            iceTrails.removeFirst()
        }
    }
    
    private fun generateSparkles() {
        repeat(5) {
            sparkles.add(IceSparkle(
                x = kotlin.random.Random.nextFloat() * 200f + 300f, // Autour du patineur
                y = kotlin.random.Random.nextFloat() * 100f + 300f,
                life = 1.5f
            ))
        }
    }
    
    private fun updateEffects() {
        // Mise à jour des traces de glace
        val currentTime = System.currentTimeMillis()
        iceTrails.removeAll { currentTime - it.timestamp > 3000 }
        
        // Mise à jour des étincelles - PLUS LENTES
        sparkles.removeAll { sparkle ->
            sparkle.life -= 0.015f
            sparkle.y -= 1.5f
            sparkle.life <= 0f
        }
        
        cameraShake = maxOf(0f, cameraShake - 0.015f)
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
                val aiScore = (85..175).random()
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
            GameState.RACE -> "⛸️ ${tournamentData.playerNames[currentPlayerIndex]} | ${distance.toInt()}m/${totalDistance.toInt()}m | ${speed.toInt()} km/h | Rythme: ${(rhythm * 100).toInt()}%"
            GameState.SPRINT -> "🏃 ${tournamentData.playerNames[currentPlayerIndex]} | SPRINT FINAL! | ${speed.toInt()} km/h | Énergie: ${energy.toInt()}%"
            GameState.RESULTS -> "🏆 ${tournamentData.playerNames[currentPlayerIndex]} | Temps: ${raceTime.toInt()}s | Score: ${finalScore}"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Course terminée!"
        }
    }

    inner class PatinageVitesseView(context: Context) : View(context) {
        private val paint = Paint()
        private var currentFrame = 0
        private var lastFrameTime = SystemClock.uptimeMillis()
        private val frameDuration = 80L
        private var backgroundOffset = 0f

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            
            // Appliquer camera shake RÉDUIT
            if (cameraShake > 0f) {
                canvas.save()
                canvas.translate(
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 5f,
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 5f
                )
            }
            
            when (gameState) {
                GameState.PREPARATION -> drawPreparation(canvas, w, h)
                GameState.RACE -> drawRace(canvas, w, h)
                GameState.SPRINT -> drawSprint(canvas, w, h)
                GameState.RESULTS -> drawResults(canvas, w, h)
                GameState.FINISHED -> drawResults(canvas, w, h)
            }
            
            drawEffects(canvas, w, h)
            
            if (cameraShake > 0f) {
                canvas.restore()
            }
        }
        
        private fun drawPreparation(canvas: Canvas, w: Int, h: Int) {
            // Fond de patinoire
            paint.color = Color.parseColor("#E6F3FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste de patinage vue de profil
            drawIceTrack(canvas, w, h)
            
            // Instructions - TEXTE PLUS GRAND ET VISIBLE
            paint.color = Color.parseColor("#001133")
            paint.textSize = 42f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("⛸️ PATINAGE VITESSE ⛸️", w/2f, h * 0.15f, paint)
            
            paint.textSize = 28f
            paint.color = Color.parseColor("#0066CC")
            canvas.drawText("Préparez-vous pour la course...", w/2f, h * 0.22f, paint)
            
            paint.textSize = 22f
            paint.color = Color.parseColor("#333333")
            canvas.drawText("📱 Inclinez FRANCHEMENT gauche-droite", w/2f, h * 0.78f, paint)
            canvas.drawText("📱 Alternez LENTEMENT et régulièrement", w/2f, h * 0.84f, paint)
            canvas.drawText("📱 Économisez énergie pour le sprint!", w/2f, h * 0.9f, paint)
        }
        
        private fun drawRace(canvas: Canvas, w: Int, h: Int) {
            // Fond de patinoire en action
            paint.color = Color.parseColor("#F0F8FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste de patinage avec défilement
            drawIceTrack(canvas, w, h)
            drawScrollingBackground(canvas, w, h)
            
            // Patineur en action (animation par frames)
            drawSkater(canvas, w, h)
            
            // Indicateurs de performance
            drawPerformanceIndicators(canvas, w, h)
            
            // Instructions de course - PLUS VISIBLES
            paint.color = Color.parseColor("#001133")
            paint.textSize = 26f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("📱 INCLINEZ FRANCHEMENT ← GAUCHE ↔ DROITE →", w/2f, 50f, paint)
            
            // Indication du prochain mouvement
            val nextMove = if (leftStroke) "← GAUCHE" else "DROITE →"
            paint.color = Color.parseColor("#FF6600")
            paint.textSize = 32f
            canvas.drawText("PROCHAIN: $nextMove", w/2f, h - 40f, paint)
            
            updateAnimation()
        }
        
        private fun drawSprint(canvas: Canvas, w: Int, h: Int) {
            // Fond intense pour sprint
            paint.color = Color.parseColor("#FFE6E6")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste avec effet de vitesse
            drawIceTrack(canvas, w, h)
            drawScrollingBackground(canvas, w, h)
            drawSpeedEffect(canvas, w, h)
            
            // Patineur en sprint (animation plus rapide)
            drawSkater(canvas, w, h)
            
            // Indicateurs sprint
            drawSprintIndicators(canvas, w, h)
            
            // Instructions sprint - PLUS VISIBLES
            paint.color = Color.RED
            paint.textSize = 36f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🔥 SPRINT FINAL! DONNEZ TOUT! 🔥", w/2f, 60f, paint)
            
            updateAnimation()
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            // Fond doré pour résultats
            paint.color = Color.parseColor("#FFF8DC")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Ligne d'arrivée
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
            
            // Patineur en célébration (image fixe de victoire)
            drawCelebrationSkater(canvas, w, h)
            
            // Score final - PLUS GRAND
            paint.color = Color.parseColor("#001133")
            paint.textSize = 84f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore}", w/2f, h * 0.2f, paint)
            
            paint.textSize = 32f
            canvas.drawText("POINTS", w/2f, h * 0.3f, paint)
            
            // Détails performance - PLUS LISIBLES
            paint.color = Color.parseColor("#333333")
            paint.textSize = 24f
            canvas.drawText("⏱️ Temps: ${raceTime.toInt()}s", w/2f, h * 0.5f, paint)
            canvas.drawText("⚡ Vitesse max: ${speed.toInt()} km/h", w/2f, h * 0.55f, paint)
            canvas.drawText("🎵 Rythme: ${(rhythm * 100).toInt()}%", w/2f, h * 0.6f, paint)
            canvas.drawText("⭐ Coups parfaits: $perfectStrokes", w/2f, h * 0.65f, paint)
            canvas.drawText("🎯 Technique: ${technique.toInt()}%", w/2f, h * 0.7f, paint)
        }
        
        private fun drawIceTrack(canvas: Canvas, w: Int, h: Int) {
            // Surface de glace
            paint.color = Color.WHITE
            canvas.drawRect(0f, h * 0.65f, w.toFloat(), h * 0.7f, paint)
            
            // Bordures de piste
            paint.color = Color.parseColor("#CCCCCC")
            paint.strokeWidth = 4f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(0f, h * 0.65f, w.toFloat(), h * 0.65f, paint)
            canvas.drawLine(0f, h * 0.7f, w.toFloat(), h * 0.7f, paint)
            paint.style = Paint.Style.FILL
            
            // Marques de distance sur la piste
            paint.color = Color.parseColor("#BBBBBB")
            for (i in 0 until w step 100) {
                val x = i.toFloat()
                canvas.drawLine(x, h * 0.65f, x, h * 0.7f, paint)
            }
        }
        
        private fun drawScrollingBackground(canvas: Canvas, w: Int, h: Int) {
            // Arrière-plan qui défile pour donner impression de mouvement
            paint.color = Color.parseColor("#D0E8F9")
            val backgroundSpeed = speed * 0.3f
            backgroundOffset = (backgroundOffset + backgroundSpeed) % 60f
            
            for (i in 0..w step 60) {
                val x = (i - backgroundOffset).toFloat()
                // Éléments de décor qui défilent
                canvas.drawRect(x, h * 0.7f, x + 20f, h.toFloat(), paint)
                
                // Arbres ou structures en arrière-plan
                paint.color = Color.parseColor("#228B22")
                canvas.drawRect(x + 30f, h * 0.3f, x + 40f, h * 0.65f, paint)
                paint.color = Color.parseColor("#D0E8F9")
            }
        }
        
        private fun drawSkater(canvas: Canvas, w: Int, h: Int) {
            // Position du patineur (fixe au centre de l'écran)
            val skaterX = w * 0.4f
            val skaterY = h * 0.65f - 100f // Au-dessus de la glace
            
            // Animation simple avec des cercles colorés (à remplacer par sprite-sheet)
            // Frame 0-3: poussée gauche, Frame 4-7: poussée droite
            val frameGroup = currentFrame / 4
            val isLeftPush = frameGroup % 2 == 0
            
            // Corps du patineur
            paint.color = Color.parseColor("#0066CC")
            canvas.drawCircle(skaterX, skaterY, 25f, paint)
            
            // Animation des bras et jambes selon la frame
            paint.strokeWidth = 8f
            paint.style = Paint.Style.STROKE
            paint.color = Color.parseColor("#004499")
            
            if (isLeftPush) {
                // Position poussée gauche
                canvas.drawLine(skaterX - 20f, skaterY, skaterX - 40f, skaterY + 20f, paint) // Jambe gauche
                canvas.drawLine(skaterX + 10f, skaterY, skaterX + 30f, skaterY + 10f, paint) // Jambe droite
                canvas.drawLine(skaterX - 15f, skaterY - 10f, skaterX - 35f, skaterY - 30f, paint) // Bras gauche
                canvas.drawLine(skaterX + 15f, skaterY - 10f, skaterX + 25f, skaterY + 5f, paint) // Bras droit
            } else {
                // Position poussée droite
                canvas.drawLine(skaterX + 20f, skaterY, skaterX + 40f, skaterY + 20f, paint) // Jambe droite
                canvas.drawLine(skaterX - 10f, skaterY, skaterX - 30f, skaterY + 10f, paint) // Jambe gauche
                canvas.drawLine(skaterX + 15f, skaterY - 10f, skaterX + 35f, skaterY - 30f, paint) // Bras droit
                canvas.drawLine(skaterX - 15f, skaterY - 10f, skaterX - 25f, skaterY + 5f, paint) // Bras gauche
            }
            
            paint.style = Paint.Style.FILL
            
            // Effet de vitesse derrière le patineur
            if (speed > 15f) {
                paint.color = Color.parseColor("#66FFFFFF")
                for (i in 1..3) {
                    canvas.drawCircle(skaterX - i * 15f, skaterY, 20f - i * 5f, paint)
                }
            }
        }
        
        private fun drawCelebrationSkater(canvas: Canvas, w: Int, h: Int) {
            // Patineur en position de victoire (bras levés)
            val skaterX = w * 0.4f
            val skaterY = h * 0.65f - 100f
            
            // Corps
            paint.color = Color.parseColor("#FFD700") // Doré pour la victoire
            canvas.drawCircle(skaterX, skaterY, 30f, paint)
            
            // Bras levés en victoire
            paint.strokeWidth = 10f
            paint.style = Paint.Style.STROKE
            paint.color = Color.parseColor("#FF8C00")
            canvas.drawLine(skaterX - 20f, skaterY - 15f, skaterX - 40f, skaterY - 50f, paint) // Bras gauche levé
            canvas.drawLine(skaterX + 20f, skaterY - 15f, skaterX + 40f, skaterY - 50f, paint) // Bras droit levé
            
            // Jambes stables
            canvas.drawLine(skaterX - 15f, skaterY + 15f, skaterX - 20f, skaterY + 40f, paint) // Jambe gauche
            canvas.drawLine(skaterX + 15f, skaterY + 15f, skaterX + 20f, skaterY + 40f, paint) // Jambe droite
            
            paint.style = Paint.Style.FILL
            
            // Effets de célébration
            paint.color = Color.parseColor("#FFD700")
            for (i in 0..8) {
                val angle = i * PI / 4
                val x = skaterX + cos(angle).toFloat() * 60f
                val y = skaterY + sin(angle).toFloat() * 60f
                canvas.drawCircle(x, y, 8f, paint)
            }
        }
        
        private fun updateAnimation() {
            val now = SystemClock.uptimeMillis()
            val animationSpeed = if (gameState == GameState.SPRINT) 60L else frameDuration
            
            if (now - lastFrameTime > animationSpeed && speed > 5f) { // Animation uniquement si vitesse suffisante
                currentFrame = (currentFrame + 1) % 8 // 8 frames d'animation
                lastFrameTime = now
            }
        }
        
        private fun drawPerformanceIndicators(canvas: Canvas, w: Int, h: Int) {
            val baseY = h - 180f
            
            // Distance parcourus - NOUVELLE BARRE
            val distanceProgress = distance / totalDistance
            drawMeter(canvas, 50f, baseY - 40f, 160f, distanceProgress, "DISTANCE", Color.parseColor("#FF4500"))
            
            // Vitesse
            drawMeter(canvas, 50f, baseY, 160f, speed / maxSpeed, "VITESSE", Color.GREEN)
            
            // Énergie
            drawMeter(canvas, 240f, baseY, 160f, energy / 100f, "ÉNERGIE", Color.YELLOW)
            
            // Rythme
            drawMeter(canvas, 430f, baseY, 160f, rhythm, "RYTHME", Color.CYAN)
            
            // Technique
            drawMeter(canvas, 620f, baseY, 160f, (technique - 70f) / 40f, "TECHNIQUE", Color.MAGENTA)
            
            // Informations course
            paint.color = Color.parseColor("#001133")
            paint.textSize = 24f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Distance: ${distance.toInt()}m / ${totalDistance.toInt()}m", 50f, baseY - 65f, paint)
            canvas.drawText("Coups parfaits: $perfectStrokes", 350f, baseY - 65f, paint)
        }
        
        private fun drawMeter(canvas: Canvas, x: Float, y: Float, width: Float, 
                             value: Float, label: String, color: Int) {
            // Fond de la barre
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(x, y, x + width, y + 30f, paint)
            
            // Barre de valeur
            paint.color = color
            val filledWidth = value.coerceIn(0f, 1f) * width
            canvas.drawRect(x, y, x + filledWidth, y + 30f, paint)
            
            // Label
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(label, x, y - 8f, paint)
            
            // Valeur
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 14f
            canvas.drawText("${(value * 100).toInt()}%", x + width/2, y + 22f, paint)
        }
        
        private fun drawSprintIndicators(canvas: Canvas, w: Int, h: Int) {
            // Indicateurs spéciaux pour le sprint
            paint.color = Color.RED
            paint.textSize = 32f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("ÉNERGIE: ${energy.toInt()}%", w/2f, h - 120f, paint)
            
            if (energy < 30f) {
                paint.color = Color.parseColor("#FF0000")
                paint.textSize = 28f
                canvas.drawText("⚠️ FATIGUE! ⚠️", w/2f, h - 80f, paint)
            }
        }
        
        private fun drawSpeedEffect(canvas: Canvas, w: Int, h: Int) {
            // Lignes de vitesse pour effet dynamique
            paint.color = Color.parseColor("#AACCCCCC")
            paint.strokeWidth = 4f
            paint.style = Paint.Style.STROKE
            
            for (i in 1..6) {
                val lineX = (i * w / 6f) + (phaseTimer * speed * 2f) % (w / 6f)
                canvas.drawLine(lineX, 0f, lineX, h.toFloat(), paint)
            }
            
            paint.style = Paint.Style.FILL
        }
        
        private fun drawEffects(canvas: Canvas, w: Int, h: Int) {
            // Traces sur la glace
            paint.color = Color.parseColor("#AAEEEEFF")
            for (trail in iceTrails) {
                val alpha = ((3000 - (System.currentTimeMillis() - trail.timestamp)) / 3000f * 200).toInt()
                paint.alpha = maxOf(0, alpha)
                canvas.drawCircle(trail.x, trail.y, 12f, paint)
            }
            paint.alpha = 255
            
            // Étincelles de performance
            paint.color = Color.parseColor("#FFFFDD")
            for (sparkle in sparkles) {
                paint.alpha = (sparkle.life * 255 / 1.5f).toInt()
                canvas.drawCircle(sparkle.x, sparkle.y, sparkle.life * 8f, paint)
            }
            paint.alpha = 255
        }
    }

    data class IceTrail(
        val x: Float,
        val y: Float,
        val timestamp: Long
    )
    
    data class IceSparkle(
        val x: Float,
        var y: Float,
        var life: Float
    )

    enum class GameState {
        PREPARATION, RACE, SPRINT, RESULTS, FINISHED
    }
}
