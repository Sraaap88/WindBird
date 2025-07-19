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

    // Variables de gameplay PATINAGE
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Phases avec durées réalistes
    private val preparationDuration = 4f
    private val raceDuration = 20f
    private val sprintDuration = 8f
    private val resultsDuration = 5f
    
    // Variables de course
    private var speed = 0f
    private var maxSpeed = 60f
    private var distance = 0f
    private var totalDistance = 1000f
    private var rhythm = 0f
    private var energy = 100f
    private var technique = 100f
    
    // Contrôles gyroscope pour patinage alterné
    private var tiltX = 0f
    private var tiltY = 0f
    private var tiltZ = 0f
    private var lastStroke = 0L
    private var strokeCount = 0
    private var leftStroke = true
    private var perfectStrokes = 0
    
    // Position sur la piste ovale
    private var lapProgress = 0f
    private var currentLap = 1
    private val totalLaps = 4
    
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
            textSize = 18f
            setBackgroundColor(Color.parseColor("#001133"))
            setPadding(20, 15, 20, 15)
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

        // Progression du jeu
        phaseTimer += 0.05f
        if (gameState == GameState.RACE || gameState == GameState.SPRINT) {
            raceTime += 0.05f
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
        if (currentLap >= totalLaps - 1 && lapProgress > 0.7f) {
            gameState = GameState.SPRINT
            phaseTimer = 0f
            cameraShake = 0.5f
        }
    }
    
    private fun handleSkatingMovement() {
        val currentTime = System.currentTimeMillis()
        val minStrokeInterval = 200L
        
        // Détection des mouvements de patinage alternés
        if (currentTime - lastStroke > minStrokeInterval) {
            var strokeDetected = false
            var strokePower = 0f
            
            if (leftStroke && tiltX < -0.4f && abs(tiltY) < 0.3f) {
                // Poussée pied gauche
                strokeDetected = true
                strokePower = calculateStrokePower(tiltX, tiltY, tiltZ)
                leftStroke = false
                
            } else if (!leftStroke && tiltX > 0.4f && abs(tiltY) < 0.3f) {
                // Poussée pied droit
                strokeDetected = true
                strokePower = calculateStrokePower(tiltX, tiltY, tiltZ)
                leftStroke = true
            }
            
            if (strokeDetected) {
                lastStroke = currentTime
                strokeCount++
                
                // Augmentation de vitesse basée sur la technique
                speed += strokePower * (technique / 100f) * 3f
                
                // Bonus pour rythme régulier
                updateRhythm()
                
                // Évaluation de la technique
                if (strokePower > 0.8f) {
                    perfectStrokes++
                    generateSparkles()
                }
                
                // Traces sur la glace
                addIceTrail()
                
                // Coût en énergie
                energy -= 1.5f
            }
        }
        
        // Décélération naturelle
        speed *= 0.98f
        speed = speed.coerceIn(0f, maxSpeed)
        
        // Pénalité pour manque d'énergie
        if (energy < 20f) {
            speed *= 0.95f
        }
    }
    
    private fun calculateStrokePower(x: Float, y: Float, z: Float): Float {
        val tiltPower = abs(x).coerceIn(0.4f, 1.2f) / 1.2f
        val stabilityBonus = 1f - (abs(y) + abs(z)) / 2f
        return (tiltPower * stabilityBonus.coerceIn(0.3f, 1f))
    }
    
    private fun updateRhythm() {
        val currentTime = System.currentTimeMillis()
        val idealInterval = 300L
        val actualInterval = currentTime - lastStroke
        
        val rhythmAccuracy = 1f - abs(actualInterval - idealInterval) / idealInterval.toFloat()
        rhythm = (rhythm * 0.8f + rhythmAccuracy.coerceIn(0f, 1f) * 0.2f)
        
        // Bonus de vitesse pour bon rythme
        if (rhythm > 0.7f) {
            speed += 1f
        }
    }
    
    private fun updateRaceProgress() {
        // Progression sur la piste ovale
        val progressSpeed = speed * 0.008f
        lapProgress += progressSpeed
        
        if (lapProgress >= 1f) {
            lapProgress = 0f
            currentLap++
            
            if (currentLap <= totalLaps) {
                // Récupération légère entre les tours
                energy += 5f
                energy = energy.coerceIn(0f, 100f)
            }
        }
        
        distance = ((currentLap - 1) + lapProgress) * 250f // 250m par tour
    }
    
    private fun updatePerformanceStats() {
        // Récupération d'énergie progressive
        energy += 0.1f
        energy = energy.coerceIn(0f, 100f)
        
        // Amélioration technique avec coups parfaits
        if (perfectStrokes > strokeCount * 0.6f) {
            technique += 0.05f
        } else {
            technique -= 0.02f
        }
        technique = technique.coerceIn(70f, 110f)
    }
    
    private fun handleSprint() {
        // Sprint final - plus intense !
        handleSkatingMovement()
        updateRaceProgress()
        
        // Bonus de vitesse pour sprint
        speed += 0.5f
        
        // Consommation d'énergie accrue
        energy -= 0.5f
        
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
            val timeBonus = maxOf(0, 120 - raceTime.toInt()) * 2
            val speedBonus = (speed / maxSpeed * 60).toInt()
            val rhythmBonus = (rhythm * 40).toInt()
            val techniqueBonus = ((technique - 100f) * 2).toInt()
            val perfectStrokeBonus = perfectStrokes * 3
            
            finalScore = maxOf(50, timeBonus + speedBonus + rhythmBonus + techniqueBonus + perfectStrokeBonus)
            scoreCalculated = true
        }
    }
    
    private fun addIceTrail() {
        val trailX = kotlin.random.Random.nextFloat() * 800f + 100f
        val trailY = kotlin.random.Random.nextFloat() * 600f + 100f
        iceTrails.add(IceTrail(trailX, trailY, System.currentTimeMillis()))
        
        if (iceTrails.size > 20) {
            iceTrails.removeFirst()
        }
    }
    
    private fun generateSparkles() {
        repeat(8) {
            sparkles.add(IceSparkle(
                x = kotlin.random.Random.nextFloat() * 800f + 100f,
                y = kotlin.random.Random.nextFloat() * 600f + 100f,
                life = 1f
            ))
        }
    }
    
    private fun updateEffects() {
        // Mise à jour des traces de glace
        val currentTime = System.currentTimeMillis()
        iceTrails.removeAll { currentTime - it.timestamp > 2000 }
        
        // Mise à jour des étincelles
        sparkles.removeAll { sparkle ->
            sparkle.life -= 0.02f
            sparkle.y -= 2f
            sparkle.life <= 0f
        }
        
        cameraShake = maxOf(0f, cameraShake - 0.02f)
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
        private val paint = Paint()

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            
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
            
            drawEffects(canvas, w, h)
            
            if (cameraShake > 0f) {
                canvas.restore()
            }
        }
        
        private fun drawPreparation(canvas: Canvas, w: Int, h: Int) {
            // Fond de patinoire
            paint.color = Color.parseColor("#E6F3FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste ovale vue de dessus
            paint.color = Color.parseColor("#FFFFFF")
            val centerX = w / 2f
            val centerY = h / 2f
            val ovalWidth = w * 0.7f
            val ovalHeight = h * 0.5f
            
            val oval = RectF(centerX - ovalWidth/2, centerY - ovalHeight/2, 
                             centerX + ovalWidth/2, centerY + ovalHeight/2)
            canvas.drawOval(oval, paint)
            
            // Lignes de couloir
            paint.color = Color.parseColor("#DDDDDD")
            paint.strokeWidth = 3f
            paint.style = Paint.Style.STROKE
            for (i in 1..3) {
                val innerOval = RectF(oval.left + i * 30f, oval.top + i * 20f,
                                     oval.right - i * 30f, oval.bottom - i * 20f)
                canvas.drawOval(innerOval, paint)
            }
            paint.style = Paint.Style.FILL
            
            // Instructions
            paint.color = Color.parseColor("#001133")
            paint.textSize = 32f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("⛸️ PATINAGE VITESSE ⛸️", w/2f, h * 0.15f, paint)
            
            paint.textSize = 20f
            paint.color = Color.parseColor("#0066CC")
            canvas.drawText("Préparez-vous pour la course...", w/2f, h * 0.25f, paint)
            
            paint.textSize = 16f
            paint.color = Color.parseColor("#666666")
            canvas.drawText("📱 Inclinez gauche-droite alternativement pour patiner", w/2f, h * 0.8f, paint)
            canvas.drawText("📱 Gardez un rythme régulier", w/2f, h * 0.85f, paint)
            canvas.drawText("📱 Économisez votre énergie pour le sprint final", w/2f, h * 0.9f, paint)
        }
        
        private fun drawRace(canvas: Canvas, w: Int, h: Int) {
            // Fond de patinoire en action
            paint.color = Color.parseColor("#F0F8FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste ovale avec perspective dynamique
            drawOvalTrack(canvas, w, h)
            
            // Patineur en action
            drawSkater(canvas, w, h)
            
            // Indicateurs de performance
            drawPerformanceIndicators(canvas, w, h)
            
            // Instructions de course
            paint.color = Color.parseColor("#001133")
            paint.textSize = 18f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("📱 INCLINEZ GAUCHE ← DROITE → ALTERNATIVEMENT", w/2f, 40f, paint)
            
            // Indication du prochain mouvement
            val nextMove = if (leftStroke) "← GAUCHE" else "DROITE →"
            paint.color = Color.parseColor("#FF6600")
            paint.textSize = 24f
            canvas.drawText("PROCHAIN: $nextMove", w/2f, h - 30f, paint)
        }
        
        private fun drawSprint(canvas: Canvas, w: Int, h: Int) {
            // Fond intense pour sprint
            paint.color = Color.parseColor("#FFE6E6")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste avec effet de vitesse
            drawOvalTrack(canvas, w, h)
            drawSpeedEffect(canvas, w, h)
            
            // Patineur en sprint
            drawSkater(canvas, w, h)
            
            // Indicateurs sprint
            drawSprintIndicators(canvas, w, h)
            
            // Instructions sprint
            paint.color = Color.RED
            paint.textSize = 28f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🔥 SPRINT FINAL! DONNEZ TOUT! 🔥", w/2f, 50f, paint)
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            // Fond doré pour résultats
            paint.color = Color.parseColor("#FFF8DC")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Podium d'arrivée
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
            
            // Score final
            paint.color = Color.parseColor("#001133")
            paint.textSize = 64f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore}", w/2f, h * 0.2f, paint)
            
            paint.textSize = 24f
            canvas.drawText("POINTS", w/2f, h * 0.3f, paint)
            
            // Détails performance
            paint.color = Color.parseColor("#333333")
            paint.textSize = 18f
            canvas.drawText("⏱️ Temps: ${raceTime.toInt()}s", w/2f, h * 0.5f, paint)
            canvas.drawText("⚡ Vitesse max: ${speed.toInt()} km/h", w/2f, h * 0.55f, paint)
            canvas.drawText("🎵 Rythme: ${(rhythm * 100).toInt()}%", w/2f, h * 0.6f, paint)
            canvas.drawText("⭐ Coups parfaits: $perfectStrokes", w/2f, h * 0.65f, paint)
            canvas.drawText("🎯 Technique: ${technique.toInt()}%", w/2f, h * 0.7f, paint)
        }
        
        private fun drawOvalTrack(canvas: Canvas, w: Int, h: Int) {
            val centerX = w / 2f
            val centerY = h / 2f
            val ovalWidth = w * 0.8f
            val ovalHeight = h * 0.6f
            
            // Piste principale
            paint.color = Color.WHITE
            val trackOval = RectF(centerX - ovalWidth/2, centerY - ovalHeight/2,
                                 centerX + ovalWidth/2, centerY + ovalHeight/2)
            canvas.drawOval(trackOval, paint)
            
            // Lignes de couloir
            paint.color = Color.parseColor("#CCCCCC")
            paint.strokeWidth = 2f
            paint.style = Paint.Style.STROKE
            for (i in 1..2) {
                val laneOval = RectF(trackOval.left + i * 40f, trackOval.top + i * 25f,
                                   trackOval.right - i * 40f, trackOval.bottom - i * 25f)
                canvas.drawOval(laneOval, paint)
            }
            paint.style = Paint.Style.FILL
            
            // Ligne d'arrivée
            paint.color = Color.parseColor("#FF0000")
            paint.strokeWidth = 6f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(centerX, trackOval.top, centerX, trackOval.top + 50f, paint)
            paint.style = Paint.Style.FILL
        }
        
        private fun drawSkater(canvas: Canvas, w: Int, h: Int) {
            // Position du patineur sur la piste ovale
            val angle = lapProgress * 2 * PI
            val centerX = w / 2f
            val centerY = h / 2f
            val radiusX = w * 0.3f
            val radiusY = h * 0.2f
            
            val skaterX = centerX + cos(angle).toFloat() * radiusX
            val skaterY = centerY + sin(angle).toFloat() * radiusY
            
            // Corps du patineur
            paint.color = Color.parseColor("#0066CC")
            canvas.drawCircle(skaterX, skaterY, 15f, paint)
            
            // Animation des bras selon le mouvement
            paint.strokeWidth = 6f
            paint.style = Paint.Style.STROKE
            
            if (leftStroke) {
                // Bras gauche en avant
                canvas.drawLine(skaterX - 10f, skaterY, skaterX - 25f, skaterY - 10f, paint)
                canvas.drawLine(skaterX + 10f, skaterY, skaterX + 20f, skaterY + 5f, paint)
            } else {
                // Bras droit en avant
                canvas.drawLine(skaterX + 10f, skaterY, skaterX + 25f, skaterY - 10f, paint)
                canvas.drawLine(skaterX - 10f, skaterY, skaterX - 20f, skaterY + 5f, paint)
            }
            
            paint.style = Paint.Style.FILL
            
            // Effet de vitesse derrière le patineur
            if (speed > 20f) {
                paint.color = Color.parseColor("#66FFFFFF")
                for (i in 1..3) {
                    canvas.drawCircle(skaterX - cos(angle).toFloat() * i * 8f,
                                    skaterY - sin(angle).toFloat() * i * 8f, 
                                    12f - i * 2f, paint)
                }
            }
        }
        
        private fun drawPerformanceIndicators(canvas: Canvas, w: Int, h: Int) {
            val baseY = h - 150f
            
            // Vitesse
            drawMeter(canvas, 50f, baseY, 150f, speed / maxSpeed, "VITESSE", Color.GREEN)
            
            // Énergie
            drawMeter(canvas, 220f, baseY, 150f, energy / 100f, "ÉNERGIE", Color.YELLOW)
            
            // Rythme
            drawMeter(canvas, 390f, baseY, 150f, rhythm, "RYTHME", Color.CYAN)
            
            // Technique
            drawMeter(canvas, 560f, baseY, 150f, (technique - 70f) / 40f, "TECHNIQUE", Color.MAGENTA)
            
            // Informations tour
            paint.color = Color.parseColor("#001133")
            paint.textSize = 20f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Tour: $currentLap / $totalLaps", 50f, baseY - 20f, paint)
            canvas.drawText("Coups parfaits: $perfectStrokes", 300f, baseY - 20f, paint)
        }
        
        private fun drawMeter(canvas: Canvas, x: Float, y: Float, width: Float, 
                             value: Float, label: String, color: Int) {
            // Fond de la barre
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(x, y, x + width, y + 25f, paint)
            
            // Barre de valeur
            paint.color = color
            val filledWidth = value.coerceIn(0f, 1f) * width
            canvas.drawRect(x, y, x + filledWidth, y + 25f, paint)
            
            // Label
            paint.color = Color.WHITE
            paint.textSize = 12f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText(label, x, y - 5f, paint)
            
            // Valeur
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${(value * 100).toInt()}%", x + width/2, y + 18f, paint)
        }
        
        private fun drawSprintIndicators(canvas: Canvas, w: Int, h: Int) {
            // Indicateurs spéciaux pour le sprint
            paint.color = Color.RED
            paint.textSize = 24f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("ÉNERGIE: ${energy.toInt()}%", w/2f, h - 100f, paint)
            
            if (energy < 30f) {
                paint.color = Color.parseColor("#FF0000")
                canvas.drawText("⚠️ FATIGUE! ⚠️", w/2f, h - 70f, paint)
            }
        }
        
        private fun drawSpeedEffect(canvas: Canvas, w: Int, h: Int) {
            // Lignes de vitesse
            paint.color = Color.parseColor("#AACCCCCC")
            paint.strokeWidth = 3f
            paint.style = Paint.Style.STROKE
            
            for (i in 1..8) {
                val lineX = (i * w / 8f) + (phaseTimer * speed * 2f) % (w / 8f)
                canvas.drawLine(lineX, 0f, lineX, h.toFloat(), paint)
            }
            
            paint.style = Paint.Style.FILL
        }
        
        private fun drawEffects(canvas: Canvas, w: Int, h: Int) {
            // Traces sur la glace
            paint.color = Color.parseColor("#AAEEEEFF")
            for (trail in iceTrails) {
                val alpha = ((2000 - (System.currentTimeMillis() - trail.timestamp)) / 2000f * 170).toInt()
                paint.alpha = maxOf(0, alpha)
                canvas.drawCircle(trail.x, trail.y, 8f, paint)
            }
            paint.alpha = 255
            
            // Étincelles de performance
            paint.color = Color.parseColor("#FFFFDD")
            for (sparkle in sparkles) {
                paint.alpha = (sparkle.life * 255).toInt()
                canvas.drawCircle(sparkle.x, sparkle.y, sparkle.life * 6f, paint)
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
