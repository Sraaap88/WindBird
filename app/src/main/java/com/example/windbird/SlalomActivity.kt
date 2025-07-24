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

    // Images du skieur
    private var skierNoTurn: Bitmap? = null
    private var skierLeftTurn: Bitmap? = null
    private var skierRightTurn: Bitmap? = null

    // Variables de gameplay SLALOM
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Phases avec durées réalistes
    private val preparationDuration = 8f
    private val raceDuration = 40f
    private val resultsDuration = 8f
    
    // Variables de course
    private var speed = 0f
    private var maxSpeed = 55f // Zone rouge à partir de 50
    private var skierX = 0.5f // Position horizontale (0.0 = gauche, 1.0 = droite)
    private var distance = 0f
    private var totalDistance = 1400f
    private var raceTime = 0f
    
    // Contrôles gyroscope - LÉGERS AJUSTEMENTS
    private var tiltX = 0f
    private var tiltY = 0f
    private var tiltZ = 0f
    
    // NOUVEAU - Système de carving/dérapage
    private var carvingQuality = 1f // 1.0 = parfait carving, 0.0 = pur dérapage
    private var lastTurnAngle = 0f
    private var turnSmoothness = 1f
    private var gForce = 0f // Intensité du virage
    
    // Système de portes de slalom AMÉLIORÉ
    private val gates = mutableListOf<SlalomGate>()
    private var nextGateIndex = 0
    private var gatesPassed = 0
    private var gatesMissed = 0
    private var perfectGates = 0
    private var timePenalty = 0f
    
    // NOUVEAU - Ligne fantôme (trajectoire optimale)
    private val optimalPath = mutableListOf<PathPoint>()
    private var showGhostLine = true
    
    // Performance et style
    private var technique = 100f
    private var rhythm = 0f
    private var lastTurn = 0L
    private var consecutivePerfectTurns = 0
    
    // NOUVEAU - Bonus/malus de carving
    private var carvingBonus = 0f
    private var carvingBonusTimer = 0f
    
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

        // Charger les images du skieur
        loadSkierImages()

        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        statusText = TextView(this).apply {
            text = "⛷️ SLALOM - ${tournamentData.playerNames[currentPlayerIndex]}"
            setTextColor(Color.WHITE)
            textSize = 22f
            setBackgroundColor(Color.parseColor("#000033"))
            setPadding(25, 20, 25, 20)
        }

        gameView = SlalomView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        
        initializeGame()
    }

    private fun loadSkierImages() {
        try {
            skierNoTurn = BitmapFactory.decodeResource(resources, R.drawable.slalom_no_turn)
            skierLeftTurn = BitmapFactory.decodeResource(resources, R.drawable.slalom_left_turn)
            skierRightTurn = BitmapFactory.decodeResource(resources, R.drawable.slalom_right_turn)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        
        // NOUVEAU - Variables de carving
        carvingQuality = 1f
        lastTurnAngle = 0f
        turnSmoothness = 1f
        gForce = 0f
        carvingBonus = 0f
        carvingBonusTimer = 0f
        
        gates.clear()
        snowSpray.clear()
        skiTrails.clear()
        optimalPath.clear()
        
        generateSlalomCourse()
        generateOptimalPath()
    }
    
    private fun generateSlalomCourse() {
        // Générer des PAIRES de drapeaux (vraies portes de slalom)
        var currentDistance = 120f
        
        repeat(15) { i -> // Moins de portes mais plus réalistes
            val gateX = 0.3f + (i % 2) * 0.4f // Alternance gauche-droite
            
            // PAIRE de drapeaux (porte complète)
            gates.add(SlalomGate(
                x = gateX - 0.1f, // Drapeau gauche
                distance = currentDistance,
                type = SlalomGate.Type.RED,
                number = i + 1,
                passed = false,
                side = "GAUCHE",
                isPair = true,
                pairIndex = 0
            ))
            
            gates.add(SlalomGate(
                x = gateX + 0.1f, // Drapeau droite  
                distance = currentDistance,
                type = SlalomGate.Type.BLUE,
                number = i + 1,
                passed = false,
                side = "DROITE", 
                isPair = true,
                pairIndex = 1
            ))
            
            currentDistance += 80f + kotlin.random.Random.nextFloat() * 20f
        }
    }
    
    // NOUVEAU - Génération de la ligne fantôme
    private fun generateOptimalPath() {
        optimalPath.clear()
        
        // Créer une trajectoire optimale entre les portes
        for (i in 0 until gates.size - 1) {
            val currentGate = gates[i]
            val nextGate = gates[i + 1]
            
            // Points intermédiaires pour une courbe lisse
            val steps = 10
            for (j in 0..steps) {
                val t = j.toFloat() / steps
                val x = lerp(currentGate.x, nextGate.x, t)
                val distance = lerp(currentGate.distance, nextGate.distance, t)
                
                optimalPath.add(PathPoint(x, distance))
            }
        }
    }
    
    private fun lerp(start: Float, end: Float, t: Float): Float {
        return start + (end - start) * t
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
        phaseTimer += 0.025f
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
            speed = 20f
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
        
        // NOUVEAU - Mise à jour du carving
        updateCarving()
        
        // Fin de course
        if (distance >= totalDistance) {
            calculateFinalScore()
            gameState = GameState.RESULTS
            phaseTimer = 0f
        }
    }
    
    private fun handleSkierMovement() {
        // Mouvement latéral basé sur l'inclinaison gauche/droite - CONTRÔLES LÉGERS
        val steeringInput = tiltX * 0.6f // Légèrement plus réactif
        val previousX = skierX
        skierX += steeringInput * 0.01f // Légèrement plus fluide
        skierX = skierX.coerceIn(0.1f, 0.9f)
        
        // NOUVEAU - Calcul de la G-Force pour les virages
        val turnRate = abs(skierX - previousX)
        gForce = turnRate * speed * 2f // Plus on va vite, plus les virages sont intenses
        
        // Vitesse basée sur l'inclinaison avant/arrière - CONTRÔLES LÉGERS
        when {
            tiltY < -0.4f -> { // Légèrement plus sensible
                // Incliner vers l'avant = accélération
                speed += 1.2f // Légèrement plus réactif
                technique += 0.015f
            }
            tiltY > 0.4f -> { // Légèrement plus sensible
                // Incliner vers l'arrière = freinage
                speed -= 1f // Légèrement plus réactif
                technique -= 0.008f
            }
            else -> {
                // Position neutre = maintien vitesse
                speed += 0.15f
            }
        }
        
        // Limites de vitesse avec ZONE ROUGE
        speed = speed.coerceIn(15f, maxSpeed)
        
        // Pénalité pour mouvement excessif
        if (abs(steeringInput) > 1.5f) {
            speed *= 0.99f
            technique -= 0.03f
        }
        
        // Bonus de stabilité
        if (abs(tiltZ) < 0.3f) {
            technique += 0.005f
        }
        
        technique = technique.coerceIn(60f, 120f)
        
        // Génération d'effets selon la vitesse
        if (speed > 35f) {
            generateSnowSpray()
        }
        
        // Traces de ski
        addSkiTrail()
    }
    
    // NOUVEAU - Système de carving
    private fun updateCarving() {
        val turnAngle = tiltX
        val angleChange = abs(turnAngle - lastTurnAngle)
        
        // Calculer la qualité du carving
        // Bon carving = changements d'angle fluides et progressifs
        if (angleChange < 0.5f) {
            // Virage fluide
            turnSmoothness = min(1f, turnSmoothness + 0.02f)
            carvingQuality = min(1f, carvingQuality + 0.01f)
        } else {
            // Virage brusque = dérapage
            turnSmoothness = max(0f, turnSmoothness - 0.05f)
            carvingQuality = max(0f, carvingQuality - 0.03f)
        }
        
        // Bonus/malus basé sur le carving
        if (carvingQuality > 0.8f && gForce > 1f) {
            // Excellent carving dans un virage intense
            carvingBonus += 0.5f
            carvingBonusTimer = 60f // 60 frames d'affichage
            speed += 0.3f // Bonus de vitesse
        } else if (carvingQuality < 0.3f) {
            // Dérapage = perte de vitesse
            speed *= 0.98f
        }
        
        // Réduction du timer du bonus
        if (carvingBonusTimer > 0f) {
            carvingBonusTimer -= 1f
        }
        
        lastTurnAngle = turnAngle
    }
    
    private fun updateRaceProgress() {
        // Progression basée sur la vitesse
        distance += speed * 0.06f
        
        // Effets de caméra selon la vitesse
        if (speed > 45f) {
            cameraShake = 0.15f
        }
    }
    
    private fun checkGatePassage() {
        if (nextGateIndex >= gates.size) return
        
        val currentGate = gates[nextGateIndex]
        val gateDistance = currentGate.distance
        
        // Vérifier si on approche de la porte
        if (distance >= gateDistance - 30f && distance <= gateDistance + 30f) {
            val distanceToGate = abs(skierX - currentGate.x)
            
            if (distanceToGate < 0.2f && !currentGate.passed) {
                // Porte passée correctement !
                currentGate.passed = true
                gatesPassed++
                
                // Évaluation de la précision
                if (distanceToGate < 0.1f) {
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
                speed *= 0.85f
                consecutivePerfectTurns = 0
                nextGateIndex++
                
                cameraShake = 0.4f
            }
        }
    }
    
    private fun updateTechnique() {
        // Dégradation naturelle de la technique
        technique -= 0.005f
        
        // Bonus pour vitesse optimale
        if (speed > 30f && speed < 45f) {
            technique += 0.015f
        }
        
        // Bonus pour enchaînement parfait
        if (consecutivePerfectTurns > 3) {
            technique += 0.03f
        }
    }
    
    private fun updateRhythm() {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastTurn = currentTime - lastTurn
        
        if (lastTurn > 0) {
            val idealInterval = 3000L
            val rhythmAccuracy = 1f - abs(timeSinceLastTurn - idealInterval) / idealInterval.toFloat()
            rhythm = (rhythm * 0.7f + rhythmAccuracy.coerceIn(0f, 1f) * 0.3f)
        }
        
        lastTurn = currentTime
    }
    
    private fun generateSnowSpray() {
        repeat(2) {
            snowSpray.add(SnowParticle(
                x = kotlin.random.Random.nextFloat() * 800f + 100f,
                y = kotlin.random.Random.nextFloat() * 600f + 400f,
                vx = (kotlin.random.Random.nextFloat() - 0.5f) * 4f,
                vy = kotlin.random.Random.nextFloat() * -3f - 1f,
                life = 1f
            ))
        }
    }
    
    private fun generatePerfectGateEffect() {
        repeat(6) {
            snowSpray.add(SnowParticle(
                x = kotlin.random.Random.nextFloat() * 200f + 300f,
                y = kotlin.random.Random.nextFloat() * 100f + 300f,
                vx = (kotlin.random.Random.nextFloat() - 0.5f) * 6f,
                vy = kotlin.random.Random.nextFloat() * -4f - 2f,
                life = 1.5f
            ))
        }
    }
    
    private fun addSkiTrail() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTrailTime > 80) {
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
            particle.life -= 0.015f
            particle.life <= 0f || particle.y > 1000f
        }
        
        // Mise à jour des traces de ski
        val currentTime = System.currentTimeMillis()
        skiTrails.removeAll { currentTime - it.timestamp > 4000 }
        
        cameraShake = maxOf(0f, cameraShake - 0.02f)
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
            val timeBonus = maxOf(0, 150 - raceTime.toInt()) * 2
            val speedBonus = (speed / maxSpeed * 40).toInt()
            val gatesBonus = gatesPassed * 15
            val perfectBonus = perfectGates * 25
            val techniqueBonus = ((technique - 100f) * 2).toInt()
            val rhythmBonus = (rhythm * 30).toInt()
            val carvingBonusPoints = (carvingBonus * 5).toInt() // NOUVEAU
            val penalty = (gatesMissed * 30) + (timePenalty * 10).toInt()
            
            finalScore = maxOf(50, timeBonus + speedBonus + gatesBonus + perfectBonus + 
                             techniqueBonus + rhythmBonus + carvingBonusPoints - penalty)
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
            GameState.RACE -> "⛷️ ${tournamentData.playerNames[currentPlayerIndex]} | Portes: $gatesPassed/25 | ${speed.toInt()} km/h | Carving: ${(carvingQuality * 100).toInt()}%"
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
            
            // Piste de ski en perspective - INVERSÉE (descend du bas vers le haut)
            paint.color = Color.WHITE
            val pistePath = Path()
            pistePath.moveTo(w * 0.4f, h.toFloat()) // Plus large en bas
            pistePath.lineTo(w * 0.6f, h.toFloat())
            pistePath.lineTo(w * 0.45f, 0f) // Plus étroit en haut
            pistePath.lineTo(w * 0.55f, 0f)
            pistePath.close()
            canvas.drawPath(pistePath, paint)
            
            // Aperçu des portes de slalom AVEC COULEURS ALTERNÉES
            paint.color = Color.RED
            canvas.drawRect(w * 0.6f, h * 0.8f, w * 0.64f, h * 0.65f, paint)
            paint.color = Color.BLUE
            canvas.drawRect(w * 0.36f, h * 0.6f, w * 0.4f, h * 0.45f, paint)
            paint.color = Color.RED
            canvas.drawRect(w * 0.65f, h * 0.4f, w * 0.69f, h * 0.25f, paint)
            
            // Instructions
            paint.color = Color.parseColor("#000033")
            paint.textSize = 44f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("⛷️ SLALOM GÉANT ⛷️", w/2f, h * 0.15f, paint)
            
            paint.textSize = 28f
            paint.color = Color.parseColor("#0066CC")
            canvas.drawText("Suivez la ligne fantôme pour la trajectoire optimale", w/2f, h * 0.8f, paint)
            
            paint.textSize = 22f
            paint.color = Color.parseColor("#666666")
            canvas.drawText("📱 Tournez comme un volant", w/2f, h * 0.85f, paint)
            canvas.drawText("📱 Avant/arrière pour vitesse", w/2f, h * 0.9f, paint)
        }
        
        private fun drawRace(canvas: Canvas, w: Int, h: Int) {
            // Fond de course en perspective
            paint.color = Color.parseColor("#F0F8FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste avec perspective dynamique
            drawSlopePerspective(canvas, w, h)
            
            // NOUVEAU - Ligne fantôme
            drawOptimalPath(canvas, w, h)
            
            // Portes de slalom AMÉLIORÉES
            drawSlalomGates(canvas, w, h)
            
            // Skieur avec sprites
            drawSkier(canvas, w, h)
            
            // Traces et effets
            drawSkiTrails(canvas, w, h)
            
            // Interface de course AMÉLIORÉE
            drawRaceInterface(canvas, w, h)
            
            // NOUVEAU - Barre de carving et flèche directionnelle
            drawCarvingIndicator(canvas, w, h)
            drawDirectionalArrow(canvas, w, h)
            
            // Bonus de carving
            if (carvingBonusTimer > 0f) {
                paint.color = Color.parseColor("#FFD700")
                paint.textSize = 32f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("🏆 CARVING PARFAIT! 🏆", w/2f, h * 0.3f, paint)
            }
        }
        
        // NOUVEAU - Ligne fantôme
        private fun drawOptimalPath(canvas: Canvas, w: Int, h: Int) {
            if (!showGhostLine) return
            
            paint.color = Color.parseColor("#88FFFF00") // Jaune transparent
            paint.strokeWidth = 8f
            paint.style = Paint.Style.STROKE
            
            var lastScreenX = 0f
            var lastScreenY = 0f
            
            for (i in optimalPath.indices) {
                val point = optimalPath[i]
                val pointDistance = point.distance - distance
                
                if (pointDistance > -50f && pointDistance < 300f) {
                    // PERSPECTIVE INVERSÉE : plus on est loin, plus on est vers le haut
                    val screenY = h * 0.8f - (pointDistance * 1f) // Inversé
                    val screenX = w * point.x
                    
                    if (i > 0 && lastScreenY > 0) {
                        canvas.drawLine(lastScreenX, lastScreenY, screenX, screenY, paint)
                    }
                    
                    lastScreenX = screenX
                    lastScreenY = screenY
                }
            }
            
            paint.style = Paint.Style.FILL
        }
        
        // NOUVEAU - Indicateur de carving
        private fun drawCarvingIndicator(canvas: Canvas, w: Int, h: Int) {
            val barX = w - 200f
            val barY = h - 300f
            val barWidth = 30f
            val barHeight = 120f
            
            // Fond de la barre
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(barX, barY, barX + barWidth, barY + barHeight, paint)
            
            // Niveau de carving
            val carvingLevel = carvingQuality * barHeight
            val carvingColor = when {
                carvingQuality > 0.8f -> Color.parseColor("#00FF00") // Vert
                carvingQuality > 0.5f -> Color.parseColor("#FFFF00") // Jaune
                else -> Color.parseColor("#FF0000") // Rouge
            }
            
            paint.color = carvingColor
            canvas.drawRect(barX, barY + barHeight - carvingLevel, 
                           barX + barWidth, barY + barHeight, paint)
            
            // Label
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("CARVING", barX + barWidth/2f, barY - 10f, paint)
            
            // Zones
            paint.color = Color.WHITE
            paint.strokeWidth = 1f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(barX, barY + barHeight * 0.2f, barX + barWidth, barY + barHeight * 0.2f, paint) // Zone verte
            canvas.drawLine(barX, barY + barHeight * 0.5f, barX + barWidth, barY + barHeight * 0.5f, paint) // Zone jaune
            paint.style = Paint.Style.FILL
        }
        
        // NOUVEAU - Flèche directionnelle
        private fun drawDirectionalArrow(canvas: Canvas, w: Int, h: Int) {
            if (nextGateIndex >= gates.size) return
            
            val nextGate = gates[nextGateIndex]
            val direction = if (nextGate.x > skierX) 1f else -1f
            val arrowX = w/2f + direction * 100f
            val arrowY = h * 0.4f
            
            // Couleur selon l'approche
            val approachQuality = 1f - abs(skierX - nextGate.x) * 2f
            val arrowColor = when {
                approachQuality > 0.7f -> Color.parseColor("#00FF00")
                approachQuality > 0.4f -> Color.parseColor("#FFFF00")
                else -> Color.parseColor("#FF0000")
            }
            
            paint.color = arrowColor
            
            // Dessiner la flèche
            val arrowPath = Path()
            arrowPath.moveTo(arrowX, arrowY - 20f)
            arrowPath.lineTo(arrowX + direction * 30f, arrowY)
            arrowPath.lineTo(arrowX, arrowY + 20f)
            arrowPath.lineTo(arrowX + direction * 10f, arrowY)
            arrowPath.close()
            canvas.drawPath(arrowPath, paint)
            
            // Texte d'indication
            paint.textSize = 18f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("PORTE ${nextGate.number}", arrowX, arrowY + 50f, paint)
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            // Fond doré pour résultats
            paint.color = Color.parseColor("#FFF8DC")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Bandeau doré
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
            
            // Score final énorme
            paint.color = Color.parseColor("#000033")
            paint.textSize = 80f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore}", w/2f, h * 0.2f, paint)
            
            paint.textSize = 32f
            canvas.drawText("POINTS", w/2f, h * 0.3f, paint)
            
            // Détails de performance
            paint.color = Color.parseColor("#333333")
            paint.textSize = 24f
            canvas.drawText("⏱️ Temps: ${raceTime.toInt()}s", w/2f, h * 0.5f, paint)
            canvas.drawText("🚪 Portes passées: $gatesPassed/25", w/2f, h * 0.55f, paint)
            canvas.drawText("⭐ Portes parfaites: $perfectGates", w/2f, h * 0.6f, paint)
            canvas.drawText("❌ Portes manquées: $gatesMissed", w/2f, h * 0.65f, paint)
            canvas.drawText("⚡ Vitesse max: ${speed.toInt()} km/h", w/2f, h * 0.7f, paint)
            canvas.drawText("🏂 Carving: ${(carvingQuality * 100).toInt()}%", w/2f, h * 0.75f, paint)
            canvas.drawText("🏆 Bonus carving: ${carvingBonus.toInt()}", w/2f, h * 0.8f, paint)
            
            if (timePenalty > 0f) {
                paint.color = Color.RED
                canvas.drawText("⏰ Pénalité: +${timePenalty.toInt()}s", w/2f, h * 0.85f, paint)
            }
        }
        
        private fun drawSlopePerspective(canvas: Canvas, w: Int, h: Int) {
            // Piste en perspective avec effet de vitesse - PERSPECTIVE INVERSÉE
            paint.color = Color.WHITE
            val slopeWidth = w * 0.6f
            val perspectiveOffset = speed * 0.4f
            
            val slopePath = Path()
            // Plus large en bas (proche), plus étroit en haut (loin)
            slopePath.moveTo(w * 0.15f, h.toFloat()) // Large en bas
            slopePath.lineTo(w * 0.85f, h.toFloat())
            slopePath.lineTo((w + slopeWidth) / 2f + perspectiveOffset, 0f) // Étroit en haut
            slopePath.lineTo((w - slopeWidth) / 2f - perspectiveOffset, 0f)
            slopePath.close()
            canvas.drawPath(slopePath, paint)
            
            // Lignes de perspective pour effet de vitesse - INVERSÉES
            paint.color = Color.parseColor("#EEEEEE")
            paint.strokeWidth = 2f
            paint.style = Paint.Style.STROKE
            
            for (i in 1..4) {
                val lineY = h - (i * h / 5f + (distance * 1f) % (h / 5f)) // Inversé
                if (lineY > 0 && lineY < h) {
                    val perspectiveFactor = (h - lineY) / h.toFloat() // Plus étroit vers le haut
                    val lineLeft = w/2f - (w * 0.35f * perspectiveFactor)
                    val lineRight = w/2f + (w * 0.35f * perspectiveFactor)
                    canvas.drawLine(lineLeft, lineY, lineRight, lineY, paint)
                }
            }
            
            paint.style = Paint.Style.FILL
        }
        
        private fun drawSlalomGates(canvas: Canvas, w: Int, h: Int) {
            // Dessiner les PAIRES de drapeaux (vraies portes de slalom)
            val gatePairs = gates.chunked(2) // Grouper par paires
            
            for (gatePair in gatePairs) {
                if (gatePair.size == 2) {
                    val leftGate = gatePair[0]
                    val rightGate = gatePair[1]
                    val gateScreenDistance = leftGate.distance - distance
                    
                    // Seulement dessiner les portes proches
                    if (gateScreenDistance > -50f && gateScreenDistance < 500f) {
                        val screenY = h * 0.8f - (gateScreenDistance * 1f)
                        val perspectiveFactor = (h - screenY) / h.toFloat()
                        val gateWidth = 15f * perspectiveFactor.coerceIn(0.3f, 1f)
                        val gateHeight = 80f * perspectiveFactor.coerceIn(0.3f, 1f)
                        
                        // Drapeau ROUGE (gauche)
                        val leftScreenX = w * leftGate.x
                        paint.color = Color.parseColor("#FF0000")
                        canvas.drawRect(
                            leftScreenX - gateWidth/2, screenY - gateHeight,
                            leftScreenX + gateWidth/2, screenY,
                            paint
                        )
                        
                        // Drapeau BLEU (droite)
                        val rightScreenX = w * rightGate.x
                        paint.color = Color.parseColor("#0044FF")
                        canvas.drawRect(
                            rightScreenX - gateWidth/2, screenY - gateHeight,
                            rightScreenX + gateWidth/2, screenY,
                            paint
                        )
                        
                        // Zone de passage entre les drapeaux
                        if (leftGate.number == nextGateIndex / 2 + 1) {
                            paint.color = Color.parseColor("#44FFFFFF")
                            val centerX = (leftScreenX + rightScreenX) / 2f
                            canvas.drawRect(
                                leftScreenX + gateWidth/2, screenY - gateHeight/2 - 20f,
                                rightScreenX - gateWidth/2, screenY - gateHeight/2 + 20f,
                                paint
                            )
                        }
                        
                        // Numéro de porte
                        if (perspectiveFactor > 0.6f) {
                            paint.color = Color.WHITE
                            paint.textSize = 20f * perspectiveFactor
                            paint.textAlign = Paint.Align.CENTER
                            val centerX = (leftScreenX + rightScreenX) / 2f
                            canvas.drawText("${leftGate.number}", centerX, screenY + 25f, paint)
                        }
                        
                        // Effet spécial pour porte passée
                        if (leftGate.passed && rightGate.passed) {
                            paint.color = Color.parseColor("#44FFFF00")
                            val centerX = (leftScreenX + rightScreenX) / 2f
                            canvas.drawCircle(centerX, screenY - gateHeight/2, gateHeight/2 + 15f, paint)
                        }
                    }
                }
            }
        }
        
        private fun drawSkier(canvas: Canvas, w: Int, h: Int) {
            val skierScreenX = skierX * w
            val skierScreenY = h * 0.85f // Position du skieur vers le bas de l'écran
            
            // Choisir l'image selon la direction
            val currentSkierImage = when {
                tiltX < -0.3f -> skierLeftTurn  // Virage à gauche
                tiltX > 0.3f -> skierRightTurn  // Virage à droite
                else -> skierNoTurn              // Tout droit
            }
            
            // Dessiner l'image du skieur si elle est chargée
            currentSkierImage?.let { bitmap ->
                val scaleFactor = 0.5f // Réduit la taille des sprites
                val scaledWidth = bitmap.width * scaleFactor
                val scaledHeight = bitmap.height * scaleFactor
                
                canvas.drawBitmap(
                    bitmap,
                    Rect(0, 0, bitmap.width, bitmap.height),
                    RectF(
                        skierScreenX - scaledWidth / 2,
                        skierScreenY - scaledHeight / 2,
                        skierScreenX + scaledWidth / 2,
                        skierScreenY + scaledHeight / 2
                    ),
                    paint
                )
            } ?: run {
                // Fallback si les images ne sont pas chargées
                paint.color = Color.parseColor("#FF6600")
                canvas.drawCircle(skierScreenX, skierScreenY, 25f, paint)
            }
            
            // Effet de vitesse derrière le skieur
            if (speed > 30f) {
                paint.color = Color.parseColor("#66FFFFFF")
                for (i in 1..3) {
                    canvas.drawCircle(skierScreenX, skierScreenY + i * 20f, 20f - i * 4f, paint)
                }
            }
        }
        
        private fun drawSkiTrails(canvas: Canvas, w: Int, h: Int) {
            // Traces de ski
            paint.color = Color.parseColor("#AACCCCCC")
            val currentTime = System.currentTimeMillis()
            
            for (trail in skiTrails) {
                val alpha = ((4000 - (currentTime - trail.timestamp)) / 4000f * 150).toInt()
                paint.alpha = maxOf(0, alpha)
                canvas.drawCircle(trail.x, trail.y, 6f, paint)
            }
            paint.alpha = 255
        }
        
        private fun drawRaceInterface(canvas: Canvas, w: Int, h: Int) {
            val baseY = h - 180f
            
            // NOUVEAU - Speedomètre analogique avec zone rouge
            drawAnalogSpeedometer(canvas, w - 120f, 120f, speed, maxSpeed)
            
            // Progression des portes
            paint.color = Color.parseColor("#000033")
            paint.textSize = 20f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Portes: $gatesPassed/25", 30f, baseY, paint)
            canvas.drawText("Parfaites: $perfectGates", 30f, baseY + 30f, paint)
            canvas.drawText("Manquées: $gatesMissed", 30f, baseY + 60f, paint)
            
            // Barre de technique
            drawProgressBar(canvas, 250f, baseY, 220f, technique / 120f, "TECHNIQUE", Color.parseColor("#9966CC"))
            
            // Indicateur de rythme
            drawProgressBar(canvas, 250f, baseY + 40f, 220f, rhythm, "RYTHME", Color.parseColor("#00AAAA"))
            
            // Enchaînement parfait
            if (consecutivePerfectTurns > 2) {
                paint.color = Color.parseColor("#FF6600")
                paint.textSize = 26f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("🔥 COMBO x$consecutivePerfectTurns 🔥", w/2f, baseY - 40f, paint)
            }
        }
        
        // NOUVEAU - Speedomètre analogique avec zone rouge
        private fun drawAnalogSpeedometer(canvas: Canvas, centerX: Float, centerY: Float, 
                                        currentSpeed: Float, maxSpeed: Float) {
            val radius = 60f
            
            // Cadran
            paint.color = Color.parseColor("#333333")
            canvas.drawCircle(centerX, centerY, radius, paint)
            
            paint.color = Color.WHITE
            canvas.drawCircle(centerX, centerY, radius - 5f, paint)
            
            // ZONE ROUGE (50-55 km/h)
            paint.color = Color.parseColor("#FFAAAA")
            val redZoneStart = 240f + (50f / maxSpeed) * 240f
            val redZoneEnd = 240f + (maxSpeed / maxSpeed) * 240f
            
            val redZonePath = Path()
            redZonePath.addArc(centerX - radius + 10f, centerY - radius + 10f, 
                              centerX + radius - 10f, centerY + radius - 10f, 
                              redZoneStart, redZoneEnd - redZoneStart)
            paint.strokeWidth = 20f
            paint.style = Paint.Style.STROKE
            canvas.drawPath(redZonePath, paint)
            paint.style = Paint.Style.FILL
            
            // Graduations
            paint.color = Color.BLACK
            paint.strokeWidth = 2f
            paint.style = Paint.Style.STROKE
            
            for (i in 0..6) {
                val angle = 240f + i * 40f
                val startRadius = radius - 15f
                val endRadius = radius - 5f
                
                val startX = centerX + cos(Math.toRadians(angle.toDouble())).toFloat() * startRadius
                val startY = centerY + sin(Math.toRadians(angle.toDouble())).toFloat() * startRadius
                val endX = centerX + cos(Math.toRadians(angle.toDouble())).toFloat() * endRadius
                val endY = centerY + sin(Math.toRadians(angle.toDouble())).toFloat() * endRadius
                
                canvas.drawLine(startX, startY, endX, endY, paint)
            }
            
            // Aiguille
            val speedAngle = 240f + (currentSpeed / maxSpeed) * 240f
            val needleColor = if (currentSpeed >= 50f) Color.RED else Color.parseColor("#00AA00")
            paint.color = needleColor
            paint.strokeWidth = 6f
            
            val needleX = centerX + cos(Math.toRadians(speedAngle.toDouble())).toFloat() * (radius - 10f)
            val needleY = centerY + sin(Math.toRadians(speedAngle.toDouble())).toFloat() * (radius - 10f)
            canvas.drawLine(centerX, centerY, needleX, needleY, paint)
            
            paint.style = Paint.Style.FILL
            
            // Centre de l'aiguille
            paint.color = Color.BLACK
            canvas.drawCircle(centerX, centerY, 8f, paint)
            
            // Valeur numérique
            paint.color = if (currentSpeed >= 50f) Color.RED else Color.BLACK
            paint.textSize = 18f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${currentSpeed.toInt()}", centerX, centerY + 80f, paint)
            
            paint.textSize = 12f
            canvas.drawText("km/h", centerX, centerY + 95f, paint)
        }
        
        private fun drawProgressBar(canvas: Canvas, x: Float, y: Float, width: Float, 
                                   value: Float, label: String, color: Int) {
            // Fond
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(x, y, x + width, y + 25f, paint)
            
            // Barre de progression
            paint.color = color
            val filledWidth = value.coerceIn(0f, 1f) * width
            canvas.drawRect(x, y, x + filledWidth, y + 25f, paint)
            
            // Label
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("$label: ${(value * 100).toInt()}%", x, y - 8f, paint)
        }
        
        private fun drawEffects(canvas: Canvas, w: Int, h: Int) {
            // Particules de neige
            paint.color = Color.WHITE
            for (particle in snowSpray) {
                paint.alpha = (particle.life * 255).toInt()
                canvas.drawCircle(particle.x, particle.y, particle.life * 5f, paint)
            }
            paint.alpha = 255
        }
    }

    data class SlalomGate(
        val x: Float,
        val distance: Float,
        val type: Type,
        val number: Int,
        var passed: Boolean,
        val side: String,
        val isPair: Boolean = false, // NOUVEAU - fait partie d'une paire
        val pairIndex: Int = 0       // NOUVEAU - 0=gauche, 1=droite
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
    
    // NOUVEAU
    data class PathPoint(
        val x: Float,
        val distance: Float
    )

    enum class GameState {
        PREPARATION, RACE, RESULTS, FINISHED
    }
}
