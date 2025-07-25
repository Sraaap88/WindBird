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

    // Images du skieur et drapeaux
    private var skierNoTurn: Bitmap? = null
    private var skierLeftTurn: Bitmap? = null
    private var skierRightTurn: Bitmap? = null
    private var flagRed: Bitmap? = null
    private var flagBlue: Bitmap? = null

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
    private var courseProgress = 0f // Progression sur le parcours
    private var totalCourseLength = 2000f
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
    
    // Système de portes de slalom REFAIT POUR PERSPECTIVE
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

        // Charger les images
        loadImages()

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

    private fun loadImages() {
        try {
            skierNoTurn = BitmapFactory.decodeResource(resources, R.drawable.slalom_no_turn)
            skierLeftTurn = BitmapFactory.decodeResource(resources, R.drawable.slalom_left_turn)
            skierRightTurn = BitmapFactory.decodeResource(resources, R.drawable.slalom_right_turn)
            flagRed = BitmapFactory.decodeResource(resources, R.drawable.slalom_flag_red)
            flagBlue = BitmapFactory.decodeResource(resources, R.drawable.slalom_flag_bleu)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun initializeGame() {
        gameState = GameState.PREPARATION
        phaseTimer = 0f
        speed = 0f
        skierX = 0.5f
        courseProgress = 0f
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
        
        generateSlalomCourse()
    }
    
    private fun generateSlalomCourse() {
        // Générer des PAIRES de drapeaux avec VRAIE variabilité et portes plus larges
        var currentPosition = 200f // Commencer plus loin
        
        repeat(15) { i -> // Moins de portes mais mieux espacées
            // Positions vraiment variées avec plus d'amplitude
            val baseX = 0.25f + (i % 3) * 0.25f // 3 couloirs au lieu de 2
            val randomOffset = (kotlin.random.Random.nextFloat() - 0.5f) * 0.3f // Plus de variation
            val gateCenter = (baseX + randomOffset).coerceIn(0.2f, 0.8f)
            
            // Portes PLUS LARGES et largeur encore plus variable
            val gateWidth = 0.18f + kotlin.random.Random.nextFloat() * 0.12f // Entre 0.18f et 0.30f (beaucoup plus large)
            
            // Paire de drapeaux (rouge à gauche, bleu à droite)
            gates.add(SlalomGate(
                leftX = gateCenter - gateWidth,
                rightX = gateCenter + gateWidth,
                position = currentPosition,
                number = i + 1,
                passed = false
            ))
            
            // Espacement VRAIMENT variable et plus étalé
            val baseSpacing = 120f + i * 15f // Espacement qui augmente progressivement
            val randomSpacing = kotlin.random.Random.nextFloat() * 80f // Variation énorme (0-80f)
            currentPosition += baseSpacing + randomSpacing // Entre 120f et 200f+ selon la progression
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
        if (courseProgress >= totalCourseLength) {
            calculateFinalScore()
            gameState = GameState.RESULTS
            phaseTimer = 0f
        }
    }
    
    private fun handleSkierMovement() {
        // Mouvement latéral basé sur l'inclinaison gauche/droite
        val steeringInput = tiltX * 0.8f
        val previousX = skierX
        skierX += steeringInput * 0.015f
        skierX = skierX.coerceIn(0.1f, 0.9f)
        
        // NOUVEAU - Calcul de la G-Force pour les virages
        val turnRate = abs(skierX - previousX)
        gForce = turnRate * speed * 2f
        
        // Vitesse basée sur l'inclinaison avant/arrière
        when {
            tiltY < -0.4f -> {
                // Incliner vers l'avant = accélération
                speed += 1.2f
                technique += 0.015f
            }
            tiltY > 0.4f -> {
                // Incliner vers l'arrière = freinage
                speed -= 1f
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
            carvingBonusTimer = 60f
            speed += 0.3f
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
        courseProgress += speed * 0.08f
        
        // Effets de caméra selon la vitesse
        if (speed > 45f) {
            cameraShake = 0.15f
        }
    }
    
    private fun checkGatePassage() {
        if (nextGateIndex >= gates.size) return
        
        val currentGate = gates[nextGateIndex]
        val relativePosition = currentGate.position - courseProgress
        
        // Vérifier si on traverse la porte
        if (relativePosition <= 30f && relativePosition >= -30f) {
            // Vérifier si on passe entre les drapeaux
            if (skierX > currentGate.leftX && skierX < currentGate.rightX && !currentGate.passed) {
                // Porte passée correctement !
                currentGate.passed = true
                gatesPassed++
                
                // Évaluation de la précision
                val gateCenter = (currentGate.leftX + currentGate.rightX) / 2f
                val distanceFromCenter = abs(skierX - gateCenter)
                val gateWidth = currentGate.rightX - currentGate.leftX
                
                if (distanceFromCenter < gateWidth * 0.2f) {
                    perfectGates++
                    consecutivePerfectTurns++
                    generatePerfectGateEffect()
                } else {
                    consecutivePerfectTurns = 0
                }
                
                updateRhythm()
                nextGateIndex++
                
            } else if (relativePosition < -30f && !currentGate.passed) {
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
            val carvingBonusPoints = (carvingBonus * 5).toInt()
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
            GameState.RACE -> "⛷️ ${tournamentData.playerNames[currentPlayerIndex]} | Portes: $gatesPassed/15 | ${speed.toInt()} km/h | Carving: ${(carvingQuality * 100).toInt()}%"
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
            pistePath.moveTo(w * 0.45f, 0f) // Étroit en haut
            pistePath.lineTo(w * 0.55f, 0f)
            pistePath.lineTo(w * 0.85f, h.toFloat()) // Large en bas
            pistePath.lineTo(w * 0.15f, h.toFloat())
            pistePath.close()
            canvas.drawPath(pistePath, paint)
            
            // Instructions
            paint.color = Color.parseColor("#000033")
            paint.textSize = 44f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("⛷️ SLALOM GÉANT ⛷️", w/2f, h * 0.15f, paint)
            
            paint.textSize = 28f
            paint.color = Color.parseColor("#0066CC")
            canvas.drawText("Zigzaguez entre les portes qui arrivent", w/2f, h * 0.8f, paint)
            
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
            
            // Portes de slalom avec vraie perspective
            drawSlalomGates(canvas, w, h)
            
            // Skieur fixe en bas
            drawSkier(canvas, w, h)
            
            // Traces et effets
            drawSkiTrails(canvas, w, h)
            
            // Interface de course
            drawRaceInterface(canvas, w, h)
            
            // Barre de carving
            drawCarvingIndicator(canvas, w, h)
            
            // Bonus de carving
            if (carvingBonusTimer > 0f) {
                paint.color = Color.parseColor("#FFD700")
                paint.textSize = 32f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("🏆 CARVING PARFAIT! 🏆", w/2f, h * 0.3f, paint)
            }
        }
        
        private fun drawSlopePerspective(canvas: Canvas, w: Int, h: Int) {
            // Piste en perspective avec point de fuite plus haut pour effet sommet de colline
            paint.color = Color.WHITE
            
            val slopePath = Path()
            // Point de fuite plus haut pour effet "sommet de colline"
            val vanishingPointX = w / 2f
            val vanishingPointY = h * 0.25f // Plus haut (était 0.4f)
            
            // Piste qui converge vers le point de fuite plus haut
            slopePath.moveTo(vanishingPointX - 6f, vanishingPointY) // Très étroit au sommet
            slopePath.lineTo(vanishingPointX + 6f, vanishingPointY)
            slopePath.lineTo(w * 0.88f, h.toFloat()) // Plus large en bas pour effet cylindre
            slopePath.lineTo(w * 0.12f, h.toFloat())
            slopePath.close()
            canvas.drawPath(slopePath, paint)
            
            // Lignes de perspective avec effet sommet de colline
            paint.color = Color.parseColor("#EEEEEE")
            paint.strokeWidth = 2f
            paint.style = Paint.Style.STROKE
            
            // Espacement des lignes basé sur la progression du parcours
            val lineSpacing = 60f // Distance entre les lignes sur le parcours
            val startPosition = (courseProgress / lineSpacing).toInt() * lineSpacing
            
            for (i in 0..20) { // Plus de lignes pour effet continu
                val linePosition = startPosition + i * lineSpacing - courseProgress
                
                if (linePosition > 0f && linePosition < 1000f) {
                    // Calcul avec courbe pour effet colline/cylindre
                    val distanceRatio = linePosition / 1000f
                    val curveFactor = 1f - cos(distanceRatio * Math.PI / 2f).toFloat()
                    val lineY = vanishingPointY + curveFactor * (h - vanishingPointY)
                    
                    if (lineY >= vanishingPointY && lineY <= h) {
                        // Largeur de la ligne avec perspective cylindrique
                        val perspectiveFactor = (lineY - vanishingPointY) / (h - vanishingPointY)
                        val lineLeft = vanishingPointX - (w * 0.38f * perspectiveFactor) // Plus large
                        val lineRight = vanishingPointX + (w * 0.38f * perspectiveFactor)
                        
                        // Épaisseur et transparence selon la distance
                        paint.strokeWidth = perspectiveFactor * 4f + 0.5f
                        paint.alpha = (perspectiveFactor * 120f + 60f).toInt()
                        
                        canvas.drawLine(lineLeft, lineY, lineRight, lineY, paint)
                    }
                }
            }
            
            paint.alpha = 255
            paint.style = Paint.Style.FILL
            
            // Effet de vitesse - flou sur les bords
            if (speed > 35f) {
                paint.color = Color.parseColor("#66E6F3FF")
                val blurIntensity = (speed - 35f) / 20f
                
                for (i in 1..3) {
                    paint.alpha = (blurIntensity * 30f).toInt()
                    canvas.drawRect(0f, 0f, w * 0.08f * i * blurIntensity, h.toFloat(), paint)
                    canvas.drawRect(w - w * 0.08f * i * blurIntensity, 0f, w.toFloat(), h.toFloat(), paint)
                }
                paint.alpha = 255
            }
        }
        
        private fun drawSlalomGates(canvas: Canvas, w: Int, h: Int) {
            // Point de fuite au centre pour cohérence avec la piste
            val vanishingPointX = w / 2f
            val vanishingPointY = h * 0.25f // Plus haut pour effet sommet de colline
            
            // Dessiner les DRAPEAUX FIXES plantés sur le parcours
            for (gate in gates) {
                val distanceToGate = gate.position - courseProgress
                
                // Drapeaux visibles BEAUCOUP PLUS TÔT (apparaissent de loin)
                if (distanceToGate > 0f && distanceToGate < 1200f) { // Distance doublée (était 600f)
                    // Logique perspective avec effet sommet de colline plus prononcé
                    val distanceRatio = distanceToGate / 1200f // 1.0 = très loin, 0.0 = très proche
                    
                    // Courbe plus prononcée pour effet colline/cylindre
                    val curveFactor = 1f - cos(distanceRatio * Math.PI / 2f).toFloat() // Courbe cosinus
                    val screenY = vanishingPointY + curveFactor * (h - vanishingPointY)
                    
                    if (screenY >= vanishingPointY && screenY <= h) {
                        // Facteur de perspective : plus proche = beaucoup plus gros
                        val perspectiveFactor = (screenY - vanishingPointY) / (h - vanishingPointY)
                        
                        // Taille des drapeaux avec progression plus douce
                        val flagScale = perspectiveFactor * 1.5f + 0.1f // Plus de variation
                        
                        // Positions X des drapeaux avec perspective correcte
                        val gateLeftRelative = gate.leftX - 0.5f
                        val gateRightRelative = gate.rightX - 0.5f
                        
                        // Perspective plus large pour effet cylindre
                        val perspectiveWidth = 0.45f * perspectiveFactor // Plus large
                        val leftScreenX = vanishingPointX + (gateLeftRelative * w * perspectiveWidth)
                        val rightScreenX = vanishingPointX + (gateRightRelative * w * perspectiveWidth)
                        
                        // Dessiner le drapeau ROUGE (gauche)
                        flagRed?.let { bitmap ->
                            val scaledWidth = bitmap.width * flagScale * 0.20f // Légèrement plus gros
                            val scaledHeight = bitmap.height * flagScale * 0.20f
                            
                            canvas.drawBitmap(
                                bitmap,
                                Rect(0, 0, bitmap.width, bitmap.height),
                                RectF(
                                    leftScreenX - scaledWidth / 2,
                                    screenY - scaledHeight,
                                    leftScreenX + scaledWidth / 2,
                                    screenY
                                ),
                                paint
                            )
                        }
                        
                        // Dessiner le drapeau BLEU (droite)
                        flagBlue?.let { bitmap ->
                            val scaledWidth = bitmap.width * flagScale * 0.20f
                            val scaledHeight = bitmap.height * flagScale * 0.20f
                            
                            canvas.drawBitmap(
                                bitmap,
                                Rect(0, 0, bitmap.width, bitmap.height),
                                RectF(
                                    rightScreenX - scaledWidth / 2,
                                    screenY - scaledHeight,
                                    rightScreenX + scaledWidth / 2,
                                    screenY
                                ),
                                paint
                            )
                        }
                        
                        // Zone de passage pour la prochaine porte
                        if (gate == gates.getOrNull(nextGateIndex)) {
                            paint.color = Color.parseColor("#44FFFFFF")
                            canvas.drawRect(
                                leftScreenX, screenY - flagScale * 35f,
                                rightScreenX, screenY,
                                paint
                            )
                        }
                        
                        // Numéro de porte (visible de plus loin)
                        if (perspectiveFactor > 0.15f) { // Seuil plus bas
                            paint.color = Color.BLACK
                            paint.textSize = 14f * flagScale
                            paint.textAlign = Paint.Align.CENTER
                            val centerX = (leftScreenX + rightScreenX) / 2f
                            canvas.drawText("${gate.number}", centerX, screenY + 20f * flagScale, paint)
                        }
                        
                        // Effet pour porte passée
                        if (gate.passed) {
                            paint.color = Color.parseColor("#44FFFF00")
                            val centerX = (leftScreenX + rightScreenX) / 2f
                            canvas.drawCircle(centerX, screenY - flagScale * 15f, flagScale * 25f, paint)
                        }
                    }
                }
            }
        }
        
        private fun drawSkier(canvas: Canvas, w: Int, h: Int) {
            val skierScreenX = skierX * w
            
            // Position Y variable selon la vitesse - PLAGE DE 1CM (environ 38 pixels)
            val baseY = h * 0.8f // Position de base
            val speedOffset = (speed - 35f) * 1.2f // Augmenté pour 1cm de plage (environ ±19px)
            val skierScreenY = baseY - speedOffset.coerceIn(-19f, 19f) // Plage d'environ 1cm
            
            // Choisir l'image selon la direction
            val currentSkierImage = when {
                tiltX < -0.3f -> skierLeftTurn  // Virage à gauche
                tiltX > 0.3f -> skierRightTurn  // Virage à droite
                else -> skierNoTurn              // Tout droit
            }
            
            // Dessiner l'image du skieur - ENCORE PLUS PETIT
            currentSkierImage?.let { bitmap ->
                val scaleFactor = 0.3f // Réduit de 0.4f à 0.3f
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
                canvas.drawCircle(skierScreenX, skierScreenY, 15f, paint) // Plus petit aussi
            }
            
            // MEILLEUR EFFET DE VITESSE - Traces plus nombreuses et dynamiques
            if (speed > 25f) {
                paint.color = Color.parseColor("#88FFFFFF")
                val trailCount = ((speed - 25f) / 5f).toInt().coerceIn(1, 8)
                
                for (i in 1..trailCount) {
                    val alpha = (255 * (1f - i.toFloat() / trailCount)).toInt()
                    paint.alpha = alpha
                    val trailSize = (20f - i * 2f) * (speed / 50f)
                    canvas.drawCircle(
                        skierScreenX + (kotlin.random.Random.nextFloat() - 0.5f) * 8f, 
                        skierScreenY + i * 10f, 
                        trailSize.coerceAtLeast(2f), 
                        paint
                    )
                }
                paint.alpha = 255
                
                // Particules de neige supplémentaires selon vitesse
                if (speed > 40f) {
                    paint.color = Color.WHITE
                    repeat((speed / 10f).toInt()) {
                        val particleX = skierScreenX + (kotlin.random.Random.nextFloat() - 0.5f) * 50f
                        val particleY = skierScreenY + kotlin.random.Random.nextFloat() * 25f
                        paint.alpha = kotlin.random.Random.nextInt(80, 180)
                        canvas.drawCircle(particleX, particleY, kotlin.random.Random.nextFloat() * 3f + 1f, paint)
                    }
                    paint.alpha = 255
                }
            }
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
            canvas.drawText("🚪 Portes passées: $gatesPassed/20", w/2f, h * 0.55f, paint)
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
            
            // Speedomètre analogique
            drawAnalogSpeedometer(canvas, w - 120f, 120f, speed, maxSpeed)
            
            // Progression des portes
            paint.color = Color.parseColor("#000033")
            paint.textSize = 20f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Portes: $gatesPassed/15", 30f, baseY, paint)
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
                carvingQuality > 0.8f -> Color.parseColor("#00FF00")
                carvingQuality > 0.5f -> Color.parseColor("#FFFF00")
                else -> Color.parseColor("#FF0000")
            }
            
            paint.color = carvingColor
            canvas.drawRect(barX, barY + barHeight - carvingLevel, 
                           barX + barWidth, barY + barHeight, paint)
            
            // Label
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("CARVING", barX + barWidth/2f, barY - 10f, paint)
        }
        
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
        val leftX: Float,     // Position X du drapeau rouge (gauche)
        val rightX: Float,    // Position X du drapeau bleu (droite)
        val position: Float,  // Position sur le parcours
        val number: Int,      // Numéro de la porte
        var passed: Boolean   // Porte franchie ou non
    )
    
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
