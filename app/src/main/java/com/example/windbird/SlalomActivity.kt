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

class SlalomActivity : Activity(), SensorEventListener {

    private lateinit var gameView: SlalomView
    private lateinit var statusText: TextView

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null

    // Variables de gameplay SLALOM - RALLENTI
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Phases avec durées TRÈS réalistes
    private val preparationDuration = 8f // AUGMENTÉ de 4f
    private val raceDuration = 40f // AUGMENTÉ de 25f
    private val resultsDuration = 8f // AUGMENTÉ de 5f
    
    // Variables de course
    private var speed = 0f
    private var maxSpeed = 50f // RÉDUIT de 65 km/h à 50 km/h
    private var skierX = 0.5f // Position horizontale (0.0 = gauche, 1.0 = droite)
    private var distance = 0f
    private var totalDistance = 1400f
    private var raceTime = 0f
    
    // Contrôles gyroscope - MOINS SENSIBLE
    private var tiltX = 0f
    private var tiltY = 0f
    private var tiltZ = 0f
    
    // Système de portes de slalom
    private val gates = mutableListOf<SlalomGate>()
    private var nextGateIndex = 0
    private var gatesPassed = 0
    private var gatesMissed = 0
    private var perfectGates = 0
    private var timePenalty = 0f
    
    // Performance et style
    private var technique = 100f
    private var rhythm = 0f
    private var lastTurn = 0L
    private var consecutivePerfectTurns = 0
    
    // Effets visuels
    private var cameraShake = 0f
    private val snowSpray = mutableListOf<SnowParticle>()
    private val skiTrails = mutableListOf<SkiTrail>()
    private var lastTrailTime = 0L
    
    // Score et résultats
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
            text = "⛷️ SLALOM - ${tournamentData.playerNames[currentPlayerIndex]}"
            setTextColor(Color.WHITE)
            textSize = 22f // AUGMENTÉ de 18f
            setBackgroundColor(Color.parseColor("#000033"))
            setPadding(25, 20, 25, 20) // AUGMENTÉ
        }

        gameView = SlalomView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        
        initializeGame()
    }
    
    private fun initializeGame() {
        gameState = GameState.PREPARATION
        phaseTimer = 0f
        speed = 0f
        skierX = 0.5f
        distance = 0f
        raceTime = 0f
        tiltX = 0f
        tiltY = 0f
        tiltZ = 0f
        nextGateIndex = 0
        gatesPassed = 0
        gatesMissed = 0
        perfectGates = 0
        timePenalty = 0f
        technique = 100f
        rhythm = 0f
        lastTurn = 0L
        consecutivePerfectTurns = 0
        cameraShake = 0f
        finalScore = 0
        scoreCalculated = false
        lastTrailTime = 0L
        
        gates.clear()
        snowSpray.clear()
        skiTrails.clear()
        
        generateSlalomCourse()
    }
    
    private fun generateSlalomCourse() {
        // Générer un parcours de slalom avec 25 portes (nombre réduit)
        var currentDistance = 120f
        var currentSide = true // true = droite, false = gauche
        
        repeat(25) { i -> // 25 portes au lieu de 28
            val gateX = if (currentSide) 0.7f else 0.3f
            val gateType = if (currentSide) SlalomGate.Type.RED else SlalomGate.Type.BLUE
            
            gates.add(SlalomGate(
                x = gateX,
                distance = currentDistance,
                type = gateType,
                number = i + 1,
                passed = false
            ))
            
            currentDistance += 55f + kotlin.random.Random.nextFloat() * 10f // AUGMENTÉ l'espacement
            currentSide = !currentSide
        }
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

        // Progression du jeu - PLUS LENT
        phaseTimer += 0.025f // RÉDUIT de 0.05f
        if (gameState == GameState.RACE) {
            raceTime += 0.025f
        }

        when (gameState) {
            GameState.PREPARATION -> handlePreparation()
            GameState.RACE -> handleRace()
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
            speed = 20f // VITESSE DE DÉPART PLUS LENTE
        }
    }
    
    private fun handleRace() {
        // Contrôle du skieur avec gyroscope
        handleSkierMovement()
        
        // Progression de la course
        updateRaceProgress()
        
        // Vérification des portes
        checkGatePassage()
        
        // Gestion de la technique et du rythme
        updateTechnique()
        
        // Fin de course
        if (distance >= totalDistance) {
            calculateFinalScore()
            gameState = GameState.RESULTS
            phaseTimer = 0f
        }
    }
    
    private fun handleSkierMovement() {
        // Mouvement latéral basé sur l'inclinaison gauche/droite - MOINS SENSIBLE
        val steeringInput = tiltX * 0.5f // RÉDUIT de 0.8f
        skierX += steeringInput * 0.008f // RÉDUIT de 0.015f
        skierX = skierX.coerceIn(0.1f, 0.9f)
        
        // Vitesse basée sur l'inclinaison avant/arrière - MOINS SENSIBLE
        when {
            tiltY < -0.5f -> { // AUGMENTÉ de -0.3f
                // Incliner vers l'avant = accélération
                speed += 1f // RÉDUIT de 1.5f
                technique += 0.01f // RÉDUIT de 0.02f
            }
            tiltY > 0.5f -> { // AUGMENTÉ de 0.3f
                // Incliner vers l'arrière = freinage
                speed -= 0.8f // RÉDUIT de 1.2f
                technique -= 0.005f // RÉDUIT de 0.01f
            }
            else -> {
                // Position neutre = maintien vitesse
                speed += 0.1f // RÉDUIT de 0.2f
            }
        }
        
        // Limites de vitesse
        speed = speed.coerceIn(15f, maxSpeed) // VITESSE MIN AUGMENTÉE
        
        // Pénalité pour mouvement excessif - MOINS SENSIBLE
        if (abs(steeringInput) > 1.5f) { // AUGMENTÉ de 1.2f
            speed *= 0.99f // RÉDUIT de 0.98f
            technique -= 0.03f // RÉDUIT de 0.05f
        }
        
        // Bonus de stabilité - MOINS SENSIBLE
        if (abs(tiltZ) < 0.3f) { // AUGMENTÉ de 0.2f
            technique += 0.005f // RÉDUIT de 0.01f
        }
        
        technique = technique.coerceIn(60f, 120f)
        
        // Génération d'effets selon la vitesse
        if (speed > 35f) { // AUGMENTÉ de 50f
            generateSnowSpray()
        }
        
        // Traces de ski
        addSkiTrail()
    }
    
    private fun updateRaceProgress() {
        // Progression basée sur la vitesse - PLUS LENT
        distance += speed * 0.06f // RÉDUIT de 0.10f
        
        // Effets de caméra selon la vitesse
        if (speed > 45f) { // AUGMENTÉ de 60f
            cameraShake = 0.15f // RÉDUIT de 0.2f
        }
    }
    
    private fun checkGatePassage() {
        if (nextGateIndex >= gates.size) return
        
        val currentGate = gates[nextGateIndex]
        val gateDistance = currentGate.distance
        
        // Vérifier si on approche de la porte
        if (distance >= gateDistance - 30f && distance <= gateDistance + 30f) { // ZONE ÉLARGIE
            val distanceToGate = abs(skierX - currentGate.x)
            
            if (distanceToGate < 0.2f && !currentGate.passed) { // ZONE ÉLARGIE de 0.15f
                // Porte passée correctement !
                currentGate.passed = true
                gatesPassed++
                
                // Évaluation de la précision
                if (distanceToGate < 0.1f) { // ZONE ÉLARGIE de 0.08f
                    perfectGates++
                    consecutivePerfectTurns++
                    generatePerfectGateEffect()
                } else {
                    consecutivePerfectTurns = 0
                }
                
                updateRhythm()
                nextGateIndex++
                
            } else if (distance > gateDistance + 30f && !currentGate.passed) {
                // Porte manquée !
                gatesMissed++
                timePenalty += 2f
                speed *= 0.85f // MOINS DE PÉNALITÉ de 0.8f
                consecutivePerfectTurns = 0
                nextGateIndex++
                
                cameraShake = 0.4f // RÉDUIT de 0.5f
            }
        }
    }
    
    private fun updateTechnique() {
        // Dégradation naturelle de la technique - PLUS LENTE
        technique -= 0.005f // RÉDUIT de 0.008f
        
        // Bonus pour vitesse optimale
        if (speed > 30f && speed < 45f) { // ADAPTÉ aux nouvelles vitesses
            technique += 0.015f // RÉDUIT de 0.02f
        }
        
        // Bonus pour enchaînement parfait
        if (consecutivePerfectTurns > 3) {
            technique += 0.03f // RÉDUIT de 0.05f
        }
    }
    
    private fun updateRhythm() {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastTurn = currentTime - lastTurn
        
        if (lastTurn > 0) {
            val idealInterval = 3000L // AUGMENTÉ de 2500L - Temps idéal entre portes
            val rhythmAccuracy = 1f - abs(timeSinceLastTurn - idealInterval) / idealInterval.toFloat()
            rhythm = (rhythm * 0.7f + rhythmAccuracy.coerceIn(0f, 1f) * 0.3f)
        }
        
        lastTurn = currentTime
    }
    
    private fun generateSnowSpray() {
        repeat(2) { // RÉDUIT de 3
            snowSpray.add(SnowParticle(
                x = kotlin.random.Random.nextFloat() * 800f + 100f,
                y = kotlin.random.Random.nextFloat() * 600f + 400f,
                vx = (kotlin.random.Random.nextFloat() - 0.5f) * 4f, // RÉDUIT de 6f
                vy = kotlin.random.Random.nextFloat() * -3f - 1f, // RÉDUIT de -4f -2f
                life = 1f
            ))
        }
    }
    
    private fun generatePerfectGateEffect() {
        repeat(6) { // RÉDUIT de 8
            snowSpray.add(SnowParticle(
                x = kotlin.random.Random.nextFloat() * 200f + 300f,
                y = kotlin.random.Random.nextFloat() * 100f + 300f,
                vx = (kotlin.random.Random.nextFloat() - 0.5f) * 6f, // RÉDUIT de 8f
                vy = kotlin.random.Random.nextFloat() * -4f - 2f, // RÉDUIT de -6f -3f
                life = 1.5f
            ))
        }
    }
    
    private fun addSkiTrail() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTrailTime > 80) { // AUGMENTÉ de 50
            skiTrails.add(SkiTrail(
                x = kotlin.random.Random.nextFloat() * 800f + 100f,
                y = kotlin.random.Random.nextFloat() * 600f + 200f,
                timestamp = currentTime
            ))
            lastTrailTime = currentTime
        }
    }
    
    private fun updateEffects() {
        // Mise à jour des particules de neige
        snowSpray.removeAll { particle ->
            particle.x += particle.vx
            particle.y += particle.vy
            particle.life -= 0.015f // RÉDUIT de 0.02f
            particle.life <= 0f || particle.y > 1000f
        }
        
        // Mise à jour des traces de ski
        val currentTime = System.currentTimeMillis()
        skiTrails.removeAll { currentTime - it.timestamp > 4000 } // AUGMENTÉ de 3000
        
        cameraShake = maxOf(0f, cameraShake - 0.02f) // RÉDUIT de 0.03f
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
            val timeBonus = maxOf(0, 150 - raceTime.toInt()) * 2 // AUGMENTÉ de 100
            val speedBonus = (speed / maxSpeed * 40).toInt()
            val gatesBonus = gatesPassed * 15
            val perfectBonus = perfectGates * 25
            val techniqueBonus = ((technique - 100f) * 2).toInt()
            val rhythmBonus = (rhythm * 30).toInt()
            val penalty = (gatesMissed * 30) + (timePenalty * 10).toInt()
            
            finalScore = maxOf(50, timeBonus + speedBonus + gatesBonus + perfectBonus + techniqueBonus + rhythmBonus - penalty)
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
                val aiScore = (90..185).random()
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
            GameState.PREPARATION -> "⛷️ ${tournamentData.playerNames[currentPlayerIndex]} | Préparation... ${(preparationDuration - phaseTimer).toInt() + 1}s"
            GameState.RACE -> "⛷️ ${tournamentData.playerNames[currentPlayerIndex]} | Portes: $gatesPassed/25 | ${speed.toInt()} km/h | Parfaites: $perfectGates"
            GameState.RESULTS -> "🏆 ${tournamentData.playerNames[currentPlayerIndex]} | Temps: ${raceTime.toInt()}s | Score: ${finalScore}"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Course terminée!"
        }
    }

    inner class SlalomView(context: Context) : View(context) {
        private val paint = Paint()

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            
            // Appliquer camera shake
            if (cameraShake > 0f) {
                canvas.save()
                canvas.translate(
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 15f,
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 15f
                )
            }
            
            when (gameState) {
                GameState.PREPARATION -> drawPreparation(canvas, w, h)
                GameState.RACE -> drawRace(canvas, w, h)
                GameState.RESULTS -> drawResults(canvas, w, h)
                GameState.FINISHED -> drawResults(canvas, w, h)
            }
            
            drawEffects(canvas, w, h)
            
            if (cameraShake > 0f) {
                canvas.restore()
            }
        }
        
        private fun drawPreparation(canvas: Canvas, w: Int, h: Int) {
            // Fond montagneux enneigé
            paint.color = Color.parseColor("#E6F3FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Montagnes en arrière-plan
            paint.color = Color.parseColor("#CCDDEE")
            val mountainPath = Path()
            mountainPath.moveTo(0f, h * 0.3f)
            mountainPath.lineTo(w * 0.2f, h * 0.1f)
            mountainPath.lineTo(w * 0.5f, h * 0.25f)
            mountainPath.lineTo(w * 0.8f, h * 0.05f)
            mountainPath.lineTo(w.toFloat(), h * 0.2f)
            mountainPath.lineTo(w.toFloat(), h.toFloat())
            mountainPath.lineTo(0f, h.toFloat())
            mountainPath.close()
            canvas.drawPath(mountainPath, paint)
            
            // Piste de ski en perspective
            paint.color = Color.WHITE
            val pistePath = Path()
            pistePath.moveTo(w * 0.3f, 0f)
            pistePath.lineTo(w * 0.7f, 0f)
            pistePath.lineTo(w * 0.8f, h.toFloat())
            pistePath.lineTo(w * 0.2f, h.toFloat())
            pistePath.close()
            canvas.drawPath(pistePath, paint)
            
            // Aperçu des portes de slalom - PLUS GROSSES
            paint.color = Color.RED
            canvas.drawRect(w * 0.6f, h * 0.2f, w * 0.64f, h * 0.35f, paint) // PLUS LARGE
            paint.color = Color.BLUE
            canvas.drawRect(w * 0.36f, h * 0.4f, w * 0.4f, h * 0.55f, paint)
            paint.color = Color.RED
            canvas.drawRect(w * 0.65f, h * 0.6f, w * 0.69f, h * 0.75f, paint)
            
            // Instructions - TEXTE PLUS GROS
            paint.color = Color.parseColor("#000033")
            paint.textSize = 44f // AUGMENTÉ de 32f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("⛷️ SLALOM GÉANT ⛷️", w/2f, h * 0.15f, paint)
            
            paint.textSize = 28f // AUGMENTÉ de 20f
            paint.color = Color.parseColor("#0066CC")
            canvas.drawText("Préparez-vous pour la descente...", w/2f, h * 0.8f, paint)
            
            paint.textSize = 22f // AUGMENTÉ de 16f
            paint.color = Color.parseColor("#666666")
            canvas.drawText("📱 Inclinez gauche/droite pour tourner", w/2f, h * 0.85f, paint)
            canvas.drawText("📱 Inclinez avant/arrière pour vitesse", w/2f, h * 0.9f, paint)
        }
        
        private fun drawRace(canvas: Canvas, w: Int, h: Int) {
            // Fond de course en perspective
            paint.color = Color.parseColor("#F0F8FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste avec perspective dynamique
            drawSlopePerspective(canvas, w, h)
            
            // Portes de slalom
            drawSlalomGates(canvas, w, h)
            
            // Skieur
            drawSkier(canvas, w, h)
            
            // Traces et effets
            drawSkiTrails(canvas, w, h)
            
            // Interface de course
            drawRaceInterface(canvas, w, h)
            
            // Instructions en temps réel - TEXTE PLUS GROS
            paint.color = Color.parseColor("#000033")
            paint.textSize = 24f // AUGMENTÉ de 18f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("📱 INCLINEZ POUR ZIGZAGUER ENTRE LES PORTES", w/2f, 50f, paint)
            
            // Indication de direction pour la prochaine porte - TEXTE PLUS GROS
            if (nextGateIndex < gates.size) {
                val nextGate = gates[nextGateIndex]
                val direction = if (nextGate.x > 0.5f) "DROITE →" else "← GAUCHE"
                val gateColor = if (nextGate.type == SlalomGate.Type.RED) Color.RED else Color.BLUE
                
                paint.color = gateColor
                paint.textSize = 32f // AUGMENTÉ de 24f
                canvas.drawText("PROCHAINE: $direction", w/2f, h - 40f, paint)
            }
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            // Fond doré pour résultats
            paint.color = Color.parseColor("#FFF8DC")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Bandeau doré
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
            
            // Score final énorme - TEXTE PLUS GROS
            paint.color = Color.parseColor("#000033")
            paint.textSize = 80f // AUGMENTÉ de 64f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore}", w/2f, h * 0.2f, paint)
            
            paint.textSize = 32f // AUGMENTÉ de 24f
            canvas.drawText("POINTS", w/2f, h * 0.3f, paint)
            
            // Détails de performance - TEXTE PLUS GROS
            paint.color = Color.parseColor("#333333")
            paint.textSize = 24f // AUGMENTÉ de 18f
            canvas.drawText("⏱️ Temps: ${raceTime.toInt()}s", w/2f, h * 0.5f, paint)
            canvas.drawText("🚪 Portes passées: $gatesPassed/25", w/2f, h * 0.55f, paint)
            canvas.drawText("⭐ Portes parfaites: $perfectGates", w/2f, h * 0.6f, paint)
            canvas.drawText("❌ Portes manquées: $gatesMissed", w/2f, h * 0.65f, paint)
            canvas.drawText("⚡ Vitesse max: ${speed.toInt()} km/h", w/2f, h * 0.7f, paint)
            canvas.drawText("🎯 Technique: ${technique.toInt()}%", w/2f, h * 0.75f, paint)
            
            if (timePenalty > 0f) {
                paint.color = Color.RED
                canvas.drawText("⏰ Pénalité: +${timePenalty.toInt()}s", w/2f, h * 0.8f, paint)
            }
        }
        
        private fun drawSlopePerspective(canvas: Canvas, w: Int, h: Int) {
            // Piste en perspective avec effet de vitesse
            paint.color = Color.WHITE
            val slopeWidth = w * 0.6f
            val perspectiveOffset = speed * 0.4f // RÉDUIT de 0.8f
            
            val slopePath = Path()
            slopePath.moveTo((w - slopeWidth) / 2f - perspectiveOffset, 0f)
            slopePath.lineTo((w + slopeWidth) / 2f + perspectiveOffset, 0f)
            slopePath.lineTo(w * 0.85f, h.toFloat())
            slopePath.lineTo(w * 0.15f, h.toFloat())
            slopePath.close()
            canvas.drawPath(slopePath, paint)
            
            // Lignes de perspective pour effet de vitesse - PLUS LENTES
            paint.color = Color.parseColor("#EEEEEE")
            paint.strokeWidth = 2f
            paint.style = Paint.Style.STROKE
            
            for (i in 1..4) {
                val lineY = (i * h / 5f + (distance * 1f) % (h / 5f)) // RÉDUIT de 2f
                val lineLeft = w * 0.15f + (i * 0.1f * w)
                val lineRight = w * 0.85f - (i * 0.1f * w)
                canvas.drawLine(lineLeft, lineY, lineRight, lineY, paint)
            }
            
            paint.style = Paint.Style.FILL
        }
        
        private fun drawSlalomGates(canvas: Canvas, w: Int, h: Int) {
            // Dessiner les portes visibles
            for (gate in gates) {
                val gateScreenDistance = gate.distance - distance
                
                // Seulement dessiner les portes proches
                if (gateScreenDistance > -50f && gateScreenDistance < 500f) { // ZONE ÉLARGIE
                    val screenY = h * 0.2f + (gateScreenDistance * 1f) // RÉDUIT de 1.5f
                    val perspectiveFactor = 1f - (gateScreenDistance / 500f) // ADAPTÉ
                    val gateWidth = 20f * perspectiveFactor.coerceIn(0.3f, 1f) // PLUS LARGE de 15f
                    val gateHeight = 100f * perspectiveFactor.coerceIn(0.3f, 1f) // PLUS HAUT de 80f
                    
                    // Position X basée sur la perspective
                    val screenX = w * gate.x
                    
                    // Couleur de la porte
                    paint.color = if (gate.type == SlalomGate.Type.RED) Color.RED else Color.BLUE
                    
                    // Dessiner le piquet - PLUS GROS
                    canvas.drawRect(
                        screenX - gateWidth/2, screenY,
                        screenX + gateWidth/2, screenY + gateHeight,
                        paint
                    )
                    
                    // Numéro de porte - TEXTE PLUS GROS
                    if (perspectiveFactor > 0.6f) {
                        paint.color = Color.WHITE
                        paint.textSize = 20f * perspectiveFactor // AUGMENTÉ de 16f
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText("${gate.number}", screenX, screenY + gateHeight/2 + 8f, paint)
                    }
                    
                    // Effet spécial pour porte passée
                    if (gate.passed) {
                        paint.color = Color.parseColor("#44FFFF00")
                        canvas.drawCircle(screenX, screenY + gateHeight/2, gateHeight/2 + 15f, paint) // PLUS GROS
                    }
                    
                    // Surbrillance pour prochaine porte
                    if (gate == gates.getOrNull(nextGateIndex)) {
                        paint.color = Color.parseColor("#44FFFFFF")
                        paint.strokeWidth = 6f // PLUS ÉPAIS de 4f
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(
                            screenX - gateWidth/2 - 8f, screenY - 8f,
                            screenX + gateWidth/2 + 8f, screenY + gateHeight + 8f,
                            paint
                        )
                        paint.style = Paint.Style.FILL
                    }
                }
            }
        }
        
        private fun drawSkier(canvas: Canvas, w: Int, h: Int) {
            val skierScreenX = skierX * w
            val skierScreenY = h * 0.75f
            
            // Corps du skieur - PLUS GROS
            paint.color = Color.parseColor("#FF6600")
            canvas.drawCircle(skierScreenX, skierScreenY, 25f, paint) // AUGMENTÉ de 20f
            
            // Skis avec angle selon l'inclinaison - PLUS GROS
            paint.color = Color.YELLOW
            paint.strokeWidth = 10f // AUGMENTÉ de 8f
            paint.style = Paint.Style.STROKE
            
            canvas.save()
            canvas.translate(skierScreenX, skierScreenY)
            canvas.rotate(tiltX * 20f) // RÉDUIT de 30f - Rotation moins intense
            
            // Skis
            canvas.drawLine(-20f, 30f, -20f, 55f, paint) // PLUS LONG
            canvas.drawLine(20f, 30f, 20f, 55f, paint)
            
            // Bâtons - PLUS GROS
            paint.color = Color.parseColor("#8B4513")
            paint.strokeWidth = 6f // AUGMENTÉ de 4f
            canvas.drawLine(-30f, -15f, -40f, 25f, paint)
            canvas.drawLine(30f, -15f, 40f, 25f, paint)
            
            canvas.restore()
            paint.style = Paint.Style.FILL
            
            // Effet de vitesse derrière le skieur
            if (speed > 30f) { // ADAPTÉ
                paint.color = Color.parseColor("#66FFFFFF")
                for (i in 1..3) {
                    canvas.drawCircle(skierScreenX, skierScreenY + i * 20f, 20f - i * 4f, paint) // PLUS GROS
                }
            }
        }
        
        private fun drawSkiTrails(canvas: Canvas, w: Int, h: Int) {
            // Traces de ski
            paint.color = Color.parseColor("#AACCCCCC")
            val currentTime = System.currentTimeMillis()
            
            for (trail in skiTrails) {
                val alpha = ((4000 - (currentTime - trail.timestamp)) / 4000f * 150).toInt() // ADAPTÉ
                paint.alpha = maxOf(0, alpha)
                canvas.drawCircle(trail.x, trail.y, 6f, paint) // PLUS GROS de 4f
            }
            paint.alpha = 255
        }
        
        private fun drawRaceInterface(canvas: Canvas, w: Int, h: Int) {
            val baseY = h - 180f // PLUS BAS
            
            // Vitesse
            drawSpeedometer(canvas, w - 150f, 100f, speed, maxSpeed) // PLUS GROS
            
            // Progression des portes - TEXTE PLUS GROS
            paint.color = Color.parseColor("#000033")
            paint.textSize = 20f // AUGMENTÉ de 16f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Portes: $gatesPassed/25", 30f, baseY, paint)
            canvas.drawText("Parfaites: $perfectGates", 30f, baseY + 30f, paint)
            canvas.drawText("Manquées: $gatesMissed", 30f, baseY + 60f, paint)
            
            // Barre de technique
            drawProgressBar(canvas, 250f, baseY, 220f, technique / 120f, "TECHNIQUE", Color.parseColor("#9966CC"))
            
            // Indicateur de rythme
            drawProgressBar(canvas, 250f, baseY + 40f, 220f, rhythm, "RYTHME", Color.parseColor("#00AAAA"))
            
            // Enchaînement parfait - TEXTE PLUS GROS
            if (consecutivePerfectTurns > 2) {
                paint.color = Color.parseColor("#FF6600")
                paint.textSize = 26f // AUGMENTÉ de 20f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("🔥 COMBO x$consecutivePerfectTurns 🔥", w/2f, baseY - 40f, paint)
            }
        }
        
        private fun drawSpeedometer(canvas: Canvas, centerX: Float, centerY: Float, currentSpeed: Float, maxSpeed: Float) {
            // Cadran de vitesse - PLUS GROS
            paint.color = Color.parseColor("#333333")
            canvas.drawCircle(centerX, centerY, 50f, paint) // AUGMENTÉ de 40f
            
            paint.color = Color.WHITE
            canvas.drawCircle(centerX, centerY, 45f, paint) // AUGMENTÉ de 35f
            
            // Aiguille
            val speedAngle = (currentSpeed / maxSpeed) * 180f - 90f
            paint.color = Color.RED
            paint.strokeWidth = 5f // AUGMENTÉ de 4f
            paint.style = Paint.Style.STROKE
            
            val needleX = centerX + cos(Math.toRadians(speedAngle.toDouble())).toFloat() * 40f // ADAPTÉ
            val needleY = centerY + sin(Math.toRadians(speedAngle.toDouble())).toFloat() * 40f
            canvas.drawLine(centerX, centerY, needleX, needleY, paint)
            
            paint.style = Paint.Style.FILL
            
            // Valeur numérique - TEXTE PLUS GROS
            paint.color = Color.BLACK
            paint.textSize = 16f // AUGMENTÉ de 12f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${currentSpeed.toInt()}", centerX, centerY + 60f, paint)
            canvas.drawText("km/h", centerX, centerY + 80f, paint)
        }
        
        private fun drawProgressBar(canvas: Canvas, x: Float, y: Float, width: Float, 
                                   value: Float, label: String, color: Int) {
            // Fond
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(x, y, x + width, y + 25f, paint) // PLUS HAUT de 20f
            
            // Barre de progression
            paint.color = color
            val filledWidth = value.coerceIn(0f, 1f) * width
            canvas.drawRect(x, y, x + filledWidth, y + 25f, paint)
            
            // Label - TEXTE PLUS GROS
            paint.color = Color.WHITE
            paint.textSize = 16f // AUGMENTÉ de 12f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("$label: ${(value * 100).toInt()}%", x, y - 8f, paint)
        }
        
        private fun drawEffects(canvas: Canvas, w: Int, h: Int) {
            // Particules de neige
            paint.color = Color.WHITE
            for (particle in snowSpray) {
                paint.alpha = (particle.life * 255).toInt()
                canvas.drawCircle(particle.x, particle.y, particle.life * 5f, paint) // PLUS GROS de 4f
            }
            paint.alpha = 255
        }
    }

    data class SlalomGate(
        val x: Float,
        val distance: Float,
        val type: Type,
        val number: Int,
        var passed: Boolean
    ) {
        enum class Type { RED, BLUE }
    }
    
    data class SnowParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float
    )
    
    data class SkiTrail(
        val x: Float,
        val y: Float,
        val timestamp: Long
    )

    enum class GameState {
        PREPARATION, RACE, RESULTS, FINISHED
    }
}
