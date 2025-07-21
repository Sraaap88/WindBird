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
    
    // Position sur la piste ovale
    private var lapProgress = 0f
    private var currentLap = 1
    private val totalLaps = 4
    
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
        lapProgress = 0f
        currentLap = 1
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
        
        // Transition vers sprint final - PLUS TARD
        if (currentLap >= totalLaps - 1 && lapProgress > 0.8f) {
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
                
                // Évaluation de la technique - SEUIL PLUS BAS
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
        // Progression sur la piste ovale - PLUS LENTE
        val progressSpeed = speed * 0.006f
        lapProgress += progressSpeed
        
        if (lapProgress >= 1f) {
            lapProgress = 0f
            currentLap++
            
            if (currentLap <= totalLaps) {
                // Récupération légère entre les tours - PLUS GÉNÉREUSE
                energy += 8f
                energy = energy.coerceIn(0f, 100f)
            }
        }
        
        distance = ((currentLap - 1) + lapProgress) * 250f // 250m par tour
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
        
        if (currentLap > totalLaps) {
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
        val trailX = kotlin.random.Random.nextFloat() * 400f + 300f
        val trailY = kotlin.random.Random.nextFloat() * 50f + 450f
        iceTrails.add(IceTrail(trailX, trailY, System.currentTimeMillis()))
        
        if (iceTrails.size > 15) {
            iceTrails.removeFirst()
        }
    }
    
    private fun generateSparkles() {
        repeat(5) {
            sparkles.add(IceSparkle(
                x = kotlin.random.Random.nextFloat() * 100f + 350f,
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
            GameState.RACE -> "⛸️ ${tournamentData.playerNames[currentPlayerIndex]} | Tour $currentLap/$totalLaps | ${speed.toInt()} km/h | Rythme: ${(rhythm * 100).toInt()}%"
            GameState.SPRINT -> "🏃 ${tournamentData.playerNames[currentPlayerIndex]} | SPRINT FINAL! | ${speed.toInt()} km/h | Énergie: ${energy.toInt()}%"
            GameState.RESULTS -> "🏆 ${tournamentData.playerNames[currentPlayerIndex]} | Temps: ${raceTime.toInt()}s | Score: ${finalScore}"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Course terminée!"
        }
    }

    inner class PatinageVitesseView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var currentFrame = 0
        private var lastFrameTime = SystemClock.uptimeMillis()
        private val frameDuration = 100L
        private var backgroundOffset = 0f

        override fun onDraw(canvas: Canvas) {
            val w = width
            val h = height
            
            // Appliquer camera shake
            if (cameraShake > 0f) {
                canvas.save()
                canvas.translate(
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 8f,
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 8f
                )
            }
            
            when (gameState) {
                GameState.PREPARATION -> drawPreparation(canvas, w, h)
                GameState.RACE -> drawRace(canvas, w, h)
                GameState.SPRINT -> drawSprint(canvas, w, h)
                GameState.RESULTS -> drawResults(canvas, w, h)
                GameState.FINISHED -> drawResults(canvas, w, h)
            }
            
            if (cameraShake > 0f) {
                canvas.restore()
            }
        }
        
        private fun drawPreparation(canvas: Canvas, w: Int, h: Int) {
            // Fond ciel
            val gradient = LinearGradient(0f, 0f, 0f, h.toFloat(),
                Color.parseColor("#87CEEB"), Color.parseColor("#E0F6FF"), Shader.TileMode.CLAMP)
            paint.shader = gradient
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
            
            // Sol/piste de glace
            paint.color = Color.WHITE
            canvas.drawRect(0f, h * 0.7f, w.toFloat(), h.toFloat(), paint)
            
            // Bordures de piste
            paint.color = Color.parseColor("#CCCCCC")
            paint.strokeWidth = 6f
            canvas.drawLine(0f, h * 0.7f, w.toFloat(), h * 0.7f, paint)
            
            // Marques sur la glace
            paint.color = Color.parseColor("#EEEEEE")
            for (i in 0 until w step 80) {
                canvas.drawLine(i.toFloat(), h * 0.7f, i.toFloat(), h.toFloat(), paint)
            }
            
            // Instructions
            paint.color = Color.parseColor("#003366")
            paint.textSize = 32f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("⛸️ PATINAGE VITESSE ⛸️", w/2f, h * 0.2f, paint)
            
            paint.textSize = 20f
            paint.color = Color.parseColor("#0066CC")
            canvas.drawText("Inclinez le téléphone gauche-droite", w/2f, h * 0.3f, paint)
            canvas.drawText("Alternez régulièrement pour garder le rythme", w/2f, h * 0.35f, paint)
            
            val countdown = (preparationDuration - phaseTimer).toInt() + 1
            paint.textSize = 48f
            paint.color = Color.RED
            canvas.drawText("${countdown}", w/2f, h * 0.5f, paint)
        }
        
        private fun drawRace(canvas: Canvas, w: Int, h: Int) {
            drawRaceBackground(canvas, w, h)
            drawSkaterProfile(canvas, w, h)
            drawPerformanceHUD(canvas, w, h)
            updateAnimation()
        }
        
        private fun drawSprint(canvas: Canvas, w: Int, h: Int) {
            drawRaceBackground(canvas, w, h)
            drawSprintEffects(canvas, w, h)
            drawSkaterProfile(canvas, w, h)
            drawPerformanceHUD(canvas, w, h)
            updateAnimation()
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            // Fond victoire
            val gradient = LinearGradient(0f, 0f, 0f, h.toFloat(),
                Color.parseColor("#FFD700"), Color.parseColor("#FFF8DC"), Shader.TileMode.CLAMP)
            paint.shader = gradient
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
            
            // Patineur en célébration au centre
            drawCelebrationSkater(canvas, w/2f, h * 0.6f)
            
            // Score
            paint.color = Color.parseColor("#8B0000")
            paint.textSize = 64f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore} POINTS", w/2f, h * 0.3f, paint)
            
            // Détails
            paint.textSize = 18f
            paint.color = Color.parseColor("#333333")
            canvas.drawText("Temps: ${raceTime.toInt()}s | Vitesse max: ${speed.toInt()} km/h", w/2f, h * 0.8f, paint)
            canvas.drawText("Coups parfaits: $perfectStrokes | Technique: ${technique.toInt()}%", w/2f, h * 0.85f, paint)
        }
        
        private fun drawRaceBackground(canvas: Canvas, w: Int, h: Int) {
            // Ciel avec dégradé
            val gradient = LinearGradient(0f, 0f, 0f, h * 0.7f,
                Color.parseColor("#87CEEB"), Color.parseColor("#B0E0E6"), Shader.TileMode.CLAMP)
            paint.shader = gradient
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.7f, paint)
            paint.shader = null
            
            // Piste de glace
            paint.color = Color.WHITE
            canvas.drawRect(0f, h * 0.7f, w.toFloat(), h.toFloat(), paint)
            
            // Défilement du décor arrière (arbres, bâtiments)
            backgroundOffset = (backgroundOffset + speed * 0.5f) % 120f
            paint.color = Color.parseColor("#228B22")
            for (i in 0 until w + 120 step 120) {
                val x = i - backgroundOffset
                // Arbres stylisés
                canvas.drawRect(x, h * 0.4f, x + 20f, h * 0.7f, paint)
                paint.color = Color.parseColor("#32CD32")
                canvas.drawCircle(x + 10f, h * 0.4f, 25f, paint)
                paint.color = Color.parseColor("#228B22")
            }
            
            // Bordures et marques de piste
            paint.color = Color.parseColor("#DDDDDD")
            paint.strokeWidth = 4f
            canvas.drawLine(0f, h * 0.7f, w.toFloat(), h * 0.7f, paint)
            
            // Traces sur la glace
            drawIceEffects(canvas, w, h)
        }
        
        private fun drawSkaterProfile(canvas: Canvas, w: Int, h: Int) {
            val skaterX = w * 0.4f
            val skaterY = h * 0.7f
            
            // Corps principal (ovale vertical)
            paint.color = Color.parseColor("#0066CC")
            canvas.drawOval(skaterX - 15f, skaterY - 50f, skaterX + 15f, skaterY - 10f, paint)
            
            // Tête
            paint.color = Color.parseColor("#FFDBAC")
            canvas.drawCircle(skaterX, skaterY - 50f, 12f, paint)
            
            // Casque
            paint.color = Color.parseColor("#FF0000")
            canvas.drawCircle(skaterX, skaterY - 50f, 14f, paint)
            paint.color = Color.parseColor("#FFDBAC")
            canvas.drawCircle(skaterX, skaterY - 45f, 10f, paint)
            
            // Animation des membres selon la frame
            val frameInCycle = currentFrame % 8
            val isLeftStroke = frameInCycle < 4
            
            paint.strokeWidth = 6f
            paint.color = Color.parseColor("#003366")
            
            if (isLeftStroke) {
                // Poussée jambe gauche
                canvas.drawLine(skaterX - 8f, skaterY - 15f, skaterX - 30f, skaterY + 20f, paint)
                canvas.drawLine(skaterX + 8f, skaterY - 15f, skaterX + 15f, skaterY + 10f, paint)
                // Bras équilibrent
                canvas.drawLine(skaterX - 12f, skaterY - 35f, skaterX - 35f, skaterY - 45f, paint)
                canvas.drawLine(skaterX + 12f, skaterY - 35f, skaterX + 25f, skaterY - 25f, paint)
                
                // Patins
                paint.color = Color.BLACK
                canvas.drawRect(skaterX - 35f, skaterY + 18f, skaterX - 25f, skaterY + 25f, paint)
                canvas.drawRect(skaterX + 10f, skaterY + 8f, skaterX + 20f, skaterY + 15f, paint)
            } else {
                // Poussée jambe droite
                canvas.drawLine(skaterX + 8f, skaterY - 15f, skaterX + 30f, skaterY + 20f, paint)
                canvas.drawLine(skaterX - 8f, skaterY - 15f, skaterX - 15f, skaterY + 10f, paint)
                // Bras équilibrent
                canvas.drawLine(skaterX + 12f, skaterY - 35f, skaterX + 35f, skaterY - 45f, paint)
                canvas.drawLine(skaterX - 12f, skaterY - 35f, skaterX - 25f, skaterY - 25f, paint)
                
                // Patins
                paint.color = Color.BLACK
                canvas.drawRect(skaterX + 25f, skaterY + 18f, skaterX + 35f, skaterY + 25f, paint)
                canvas.drawRect(skaterX - 20f, skaterY + 8f, skaterX - 10f, skaterY + 15f, paint)
            }
            
            // Effet de vitesse
            if (speed > 10f) {
                paint.color = Color.parseColor("#80FFFFFF")
                for (i in 1..3) {
                    canvas.drawOval(skaterX - i * 20f - 10f, skaterY - 45f, 
                                   skaterX - i * 20f + 10f, skaterY - 15f, paint)
                }
            }
        }
        
        private fun drawCelebrationSkater(canvas: Canvas, x: Float, y: Float) {
            // Corps en or pour la victoire
            paint.color = Color.parseColor("#FFD700")
            canvas.drawOval(x - 18f, y - 60f, x + 18f, y - 15f, paint)
            
            // Tête
            paint.color = Color.parseColor("#FFDBAC")
            canvas.drawCircle(x, y - 60f, 15f, paint)
            
            // Bras levés en victoire
            paint.strokeWidth = 8f
            paint.color = Color.parseColor("#B8860B")
            canvas.drawLine(x - 15f, y - 45f, x - 40f, y - 80f, paint)
            canvas.drawLine(x + 15f, y - 45f, x + 40f, y - 80f, paint)
            
            // Jambes stables
            canvas.drawLine(x - 10f, y - 20f, x - 15f, y + 10f, paint)
            canvas.drawLine(x + 10f, y - 20f, x + 15f, y + 10f, paint)
            
            // Patins
            paint.color = Color.BLACK
            canvas.drawRect(x - 25f, y + 8f, x - 5f, y + 15f, paint)
            canvas.drawRect(x + 5f, y + 8f, x + 25f, y + 15f, paint)
            
            // Effet d'étoiles autour
            paint.color = Color.parseColor("#FFD700")
            for (i in 0..7) {
                val angle = i * PI / 4
                val starX = x + cos(angle).toFloat() * 50f
                val starY = y - 40f + sin(angle).toFloat() * 30f
                canvas.drawCircle(starX, starY, 6f, paint)
            }
        }
        
        private fun drawPerformanceHUD(canvas: Canvas, w: Int, h: Int) {
            val hudY = 80f
            val barWidth = 120f
            val barHeight = 20f
            
            // Vitesse
            drawHUDBar(canvas, 20f, hudY, barWidth, barHeight, speed / maxSpeed, "VITESSE", Color.GREEN)
            
            // Énergie
            drawHUDBar(canvas, 160f, hudY, barWidth, barHeight, energy / 100f, "ÉNERGIE", Color.YELLOW)
            
            // Rythme
            drawHUDBar(canvas, 300f, hudY, barWidth, barHeight, rhythm, "RYTHME", Color.CYAN)
            
            // Tour actuel
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Tour: $currentLap/$totalLaps", 450f, hudY + 15f, paint)
        }
        
        private fun drawHUDBar(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, 
                               value: Float, label: String, color: Int) {
            // Fond
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(x, y, x + width, y + height, paint)
            
            // Barre de valeur
            paint.color = color
            canvas.drawRect(x, y, x + width * value.coerceIn(0f, 1f), y + height, paint)
            
            // Label
            paint.color = Color.WHITE
            paint.textSize = 12f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(label, x, y - 5f, paint)
        }
        
        private fun drawSprintEffects(canvas: Canvas, w: Int, h: Int) {
            // Lignes de vitesse
            paint.color = Color.parseColor("#FFFF00")
            paint.strokeWidth = 3f
            for (i in 0..5) {
                val lineY = h * 0.1f + i * h * 0.1f
                canvas.drawLine(0f, lineY, w.toFloat(), lineY, paint)
            }
        }
        
        private fun drawIceEffects(canvas: Canvas, w: Int, h: Int) {
            // Traces sur la glace
            paint.color = Color.parseColor("#E0F6FF")
            for (trail in iceTrails) {
                val alpha = ((3000 - (System.currentTimeMillis() - trail.timestamp)) / 3000f * 100).toInt()
                paint.alpha = maxOf(0, alpha)
                canvas.drawCircle(trail.x, trail.y, 8f, paint)
            }
            paint.alpha = 255
            
            // Étincelles de performance
            paint.color = Color.parseColor("#FFFFFF")
            for (sparkle in sparkles) {
                paint.alpha = (sparkle.life * 200 / 1.5f).toInt()
                canvas.drawCircle(sparkle.x, sparkle.y, sparkle.life * 5f, paint)
            }
            paint.alpha = 255
        }
        
        private fun updateAnimation() {
            val now = SystemClock.uptimeMillis()
            if (speed > 3f && now - lastFrameTime > frameDuration) {
                currentFrame++
                lastFrameTime = now
            }
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
