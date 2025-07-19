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

class BobsledActivity : Activity(), SensorEventListener {

    private lateinit var gameView: BobsledView
    private lateinit var statusText: TextView

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null

    // Variables de gameplay LENT et VISUEL
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Phases avec durées longues
    private val preparationDuration = 5f
    private val pushDuration = 8f
    private val earlyRaceDuration = 10f
    private val fastRaceDuration = 8f
    private val finishDuration = 3f
    private val resultsDuration = 5f
    
    // Variables de jeu
    private var speed = 0f
    private var maxSpeed = 120f
    private var pushPower = 0f
    private var trackPosition = 0.5f // 0 = gauche, 1 = droite
    private var currentTurn = 0f
    private var turnIntensity = 0f
    private var distance = 0f
    private var totalDistance = 1500f
    
    // Performance
    private var wallHits = 0
    private var perfectTurns = 0
    private var avgSpeed = 0f
    private var raceTime = 0f
    
    // Contrôles gyroscope
    private var tiltX = 0f
    private var tiltY = 0f
    private var tiltZ = 0f
    
    // Score et résultats
    private var finalScore = 0
    private var scoreCalculated = false
    
    // Effets visuels
    private var cameraShake = 0f
    private val speedLines = mutableListOf<SpeedLine>()
    private val iceParticles = mutableListOf<IceParticle>()
    
    // Variables de poussée
    private var pushCount = 0
    private var lastShakeTime = 0L

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
            text = "🛷 BOBSLEIGH - ${tournamentData.playerNames[currentPlayerIndex]}"
            setTextColor(Color.WHITE)
            textSize = 18f
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(20, 15, 20, 15)
        }

        gameView = BobsledView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        
        initializeGame()
    }
    
    private fun initializeGame() {
        gameState = GameState.PREPARATION
        phaseTimer = 0f
        speed = 0f
        pushPower = 0f
        trackPosition = 0.5f
        distance = 0f
        wallHits = 0
        perfectTurns = 0
        avgSpeed = 0f
        raceTime = 0f
        pushCount = 0
        tiltX = 0f
        tiltY = 0f
        tiltZ = 0f
        finalScore = 0
        scoreCalculated = false
        cameraShake = 0f
        speedLines.clear()
        iceParticles.clear()
        lastShakeTime = 0L
        generateTrackSection()
        generateIceParticles()
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

        // Progression lente du jeu
        phaseTimer += 0.05f
        raceTime += 0.05f

        when (gameState) {
            GameState.PREPARATION -> handlePreparation()
            GameState.PUSH -> handlePush()
            GameState.EARLY_RACE -> handleEarlyRace()
            GameState.FAST_RACE -> handleFastRace()
            GameState.FINISH -> handleFinish()
            GameState.RESULTS -> handleResults()
            GameState.FINISHED -> {}
        }

        updateEffects()
        updateStatus()
        gameView.invalidate()
    }
    
    private fun handlePreparation() {
        if (phaseTimer >= preparationDuration) {
            gameState = GameState.PUSH
            phaseTimer = 0f
        }
    }
    
    private fun handlePush() {
        val currentTime = System.currentTimeMillis()
        val shakeThreshold = 1.5f
        
        // Détection des secousses pour la poussée
        if (abs(tiltX) > shakeThreshold || abs(tiltY) > shakeThreshold || abs(tiltZ) > shakeThreshold) {
            if (currentTime - lastShakeTime > 250) {
                pushCount++
                pushPower += 12f
                speed += 6f
                lastShakeTime = currentTime
                generateIceParticles()
            }
        }
        
        pushPower = pushPower.coerceAtMost(100f)
        speed = speed.coerceAtMost(60f)
        
        if (phaseTimer >= pushDuration) {
            gameState = GameState.EARLY_RACE
            phaseTimer = 0f
            speed = 40f + (pushPower * 0.3f)
        }
    }
    
    private fun handleEarlyRace() {
        // Phase de course lente avec apprentissage des virages
        updateRacing(1f)
        
        if (phaseTimer >= earlyRaceDuration) {
            gameState = GameState.FAST_RACE
            phaseTimer = 0f
            cameraShake = 0.3f
        }
    }
    
    private fun handleFastRace() {
        // Phase de course rapide et intense
        updateRacing(1.8f)
        
        if (phaseTimer >= fastRaceDuration) {
            gameState = GameState.FINISH
            phaseTimer = 0f
            cameraShake = 1f
        }
    }
    
    private fun handleFinish() {
        // Phase finale
        updateRacing(0.5f)
        
        if (phaseTimer >= finishDuration) {
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
    
    private fun updateRacing(intensity: Float) {
        // Direction basée sur l'inclinaison gauche/droite
        val steering = tiltX * 0.4f * intensity
        trackPosition += steering * 0.03f
        trackPosition = trackPosition.coerceIn(0f, 1f)
        
        // Vitesse basée sur l'inclinaison avant/arrière
        if (tiltY < -0.3f) {
            speed += 2f * intensity
        } else if (tiltY > 0.3f) {
            speed -= 1.5f * intensity
        }
        
        speed = speed.coerceIn(20f, maxSpeed)
        
        // Gestion des collisions avec les murs
        if (trackPosition <= 0.1f || trackPosition >= 0.9f) {
            wallHits++
            speed *= 0.7f
            trackPosition = trackPosition.coerceIn(0.15f, 0.85f)
            cameraShake += 0.2f
        }
        
        // Vérification des virages parfaits
        checkTurnPerformance()
        
        // Progression
        distance += speed * 0.08f * intensity
        avgSpeed = (avgSpeed + speed) / 2f
        
        // Génération de nouveaux virages
        if (distance % 150f < 1f) {
            generateTrackSection()
        }
        
        // Effets visuels
        if (speed > 80f) {
            generateSpeedLines()
        }
    }
    
    private fun checkTurnPerformance() {
        val idealPosition = 0.5f - (currentTurn * turnIntensity * 0.4f)
        val positionError = abs(trackPosition - idealPosition)
        
        if (positionError < 0.12f && abs(currentTurn) > 0.3f) {
            perfectTurns++
            speed += 1f
        }
    }
    
    private fun generateTrackSection() {
        currentTurn = -1f + kotlin.random.Random.nextFloat() * 2f
        turnIntensity = 0.4f + kotlin.random.Random.nextFloat() * 0.6f
    }
    
    private fun generateSpeedLines() {
        speedLines.add(SpeedLine(
            x = kotlin.random.Random.nextFloat() * 1000f,
            y = kotlin.random.Random.nextFloat() * 800f,
            speed = 8f + kotlin.random.Random.nextFloat() * 4f
        ))
        
        if (speedLines.size > 20) {
            speedLines.removeFirst()
        }
    }
    
    private fun generateIceParticles() {
        repeat(15) {
            iceParticles.add(IceParticle(
                x = kotlin.random.Random.nextFloat() * 1000f,
                y = kotlin.random.Random.nextFloat() * 800f,
                speed = 2f + kotlin.random.Random.nextFloat() * 3f,
                size = 1f + kotlin.random.Random.nextFloat() * 2f
            ))
        }
    }
    
    private fun updateEffects() {
        // Mettre à jour les lignes de vitesse
        speedLines.removeAll { line ->
            line.x -= line.speed
            line.x < -50f
        }
        
        // Mettre à jour les particules de glace
        iceParticles.removeAll { particle ->
            particle.y += particle.speed
            particle.x += sin(particle.y * 0.02f) * 0.3f
            particle.y > 1000f
        }
        
        if (iceParticles.size < 10) {
            generateIceParticles()
        }
        
        cameraShake = maxOf(0f, cameraShake - 0.02f)
    }
    
    private fun calculateFinalScore() {
        if (!scoreCalculated) {
            val timeBonus = maxOf(0, 120 - raceTime.toInt())
            val speedBonus = (avgSpeed / maxSpeed * 80).toInt()
            val wallPenalty = wallHits * 12
            val turnBonus = perfectTurns * 15
            val pushBonus = (pushPower * 0.4f).toInt()
            
            finalScore = maxOf(50, timeBonus + speedBonus - wallPenalty + turnBonus + pushBonus)
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
                val aiScore = (90..190).random()
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
            GameState.PREPARATION -> "🛷 ${tournamentData.playerNames[currentPlayerIndex]} | Préparation... ${(preparationDuration - phaseTimer).toInt() + 1}s"
            GameState.PUSH -> "🚀 ${tournamentData.playerNames[currentPlayerIndex]} | Poussée: ${pushCount} (secouez!) | ${(pushDuration - phaseTimer).toInt() + 1}s"
            GameState.EARLY_RACE -> "🛷 ${tournamentData.playerNames[currentPlayerIndex]} | Course: ${speed.toInt()} km/h | ${(earlyRaceDuration - phaseTimer).toInt() + 1}s"
            GameState.FAST_RACE -> "⚡ ${tournamentData.playerNames[currentPlayerIndex]} | VITESSE MAX: ${speed.toInt()} km/h | ${(fastRaceDuration - phaseTimer).toInt() + 1}s"
            GameState.FINISH -> "🏁 ${tournamentData.playerNames[currentPlayerIndex]} | Sprint final! | ${(finishDuration - phaseTimer).toInt() + 1}s"
            GameState.RESULTS -> "🏆 ${tournamentData.playerNames[currentPlayerIndex]} | Temps: ${raceTime.toInt()}s | Score: ${finalScore}"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Course terminée!"
        }
    }

    inner class BobsledView(context: Context) : View(context) {
        private val paint = Paint()

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            
            // Appliquer camera shake si présent
            if (cameraShake > 0f) {
                canvas.save()
                canvas.translate(
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 12f,
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 12f
                )
            }
            
            when (gameState) {
                GameState.PREPARATION -> drawPreparation(canvas, w, h)
                GameState.PUSH -> drawPush(canvas, w, h)
                GameState.EARLY_RACE -> drawEarlyRace(canvas, w, h)
                GameState.FAST_RACE -> drawFastRace(canvas, w, h)
                GameState.FINISH -> drawFinish(canvas, w, h)
                GameState.RESULTS -> drawResults(canvas, w, h)
                GameState.FINISHED -> drawResults(canvas, w, h)
            }
            
            drawIceParticles(canvas, w, h)
            
            if (cameraShake > 0f) {
                canvas.restore()
            }
        }
        
        private fun drawPreparation(canvas: Canvas, w: Int, h: Int) {
            // Fond neigeux
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste de départ
            paint.color = Color.parseColor("#AAAAAA")
            canvas.drawRect(w * 0.3f, 0f, w * 0.7f, h.toFloat(), paint)
            
            paint.color = Color.parseColor("#DDDDDD")
            canvas.drawRect(w * 0.35f, 0f, w * 0.65f, h.toFloat(), paint)
            
            // Équipe qui se place
            paint.color = Color.parseColor("#0066CC")
            for (i in 0..3) {
                val memberX = w * 0.4f + i * 40f
                val memberY = h * 0.7f
                canvas.drawCircle(memberX, memberY, 15f, paint)
                
                // Numéros
                paint.color = Color.WHITE
                paint.textSize = 16f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("${i+1}", memberX, memberY + 5f, paint)
                paint.color = Color.parseColor("#0066CC")
            }
            
            // Bobsleigh en attente
            paint.color = Color.parseColor("#FF4444")
            canvas.drawRoundRect(w * 0.42f, h * 0.5f, w * 0.58f, h * 0.6f, 10f, 10f, paint)
            
            // Instructions
            paint.color = Color.BLACK
            paint.textSize = 32f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🛷 BOBSLEIGH 🛷", w/2f, h * 0.2f, paint)
            
            paint.textSize = 24f
            canvas.drawText("L'équipe se prépare...", w/2f, h * 0.3f, paint)
            
            paint.textSize = 20f
            paint.color = Color.parseColor("#0066CC")
            canvas.drawText("Dans ${(preparationDuration - phaseTimer).toInt() + 1} secondes", w/2f, h * 0.35f, paint)
        }
        
        private fun drawPush(canvas: Canvas, w: Int, h: Int) {
            // VUE DE CÔTÉ - Équipe qui pousse
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste de départ avec perspective
            paint.color = Color.parseColor("#AAAAAA")
            val trackPath = Path()
            trackPath.moveTo(0f, h * 0.6f)
            trackPath.lineTo(w.toFloat(), h * 0.7f)
            trackPath.lineTo(w.toFloat(), h * 0.8f)
            trackPath.lineTo(0f, h * 0.8f)
            trackPath.close()
            canvas.drawPath(trackPath, paint)
            
            // Bobsleigh qui avance
            val bobProgress = pushPower / 100f
            val bobX = w * 0.2f + bobProgress * w * 0.5f
            val bobY = h * 0.65f
            
            paint.color = Color.parseColor("#FF4444")
            canvas.drawRoundRect(bobX - 30f, bobY - 15f, bobX + 50f, bobY + 15f, 8f, 8f, paint)
            
            // Équipe qui court et pousse
            paint.color = Color.parseColor("#0066CC")
            for (i in 0..3) {
                val memberX = bobX - 40f - i * 25f
                val memberY = bobY
                
                // Animation de course
                val runOffset = sin((phaseTimer + i) * 3f) * 3f
                canvas.drawCircle(memberX, memberY + runOffset, 12f, paint)
                
                // Lignes de mouvement
                paint.color = Color.parseColor("#660066CC")
                for (j in 1..3) {
                    canvas.drawCircle(memberX - j * 8f, memberY + runOffset, 8f - j, paint)
                }
                paint.color = Color.parseColor("#0066CC")
            }
            
            // Barre de puissance de poussée ÉNORME
            drawPushPowerMeter(canvas, w, h)
            
            // Instructions
            paint.color = Color.RED
            paint.textSize = 28f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🚀 SECOUEZ POUR POUSSER! 🚀", w/2f, h * 0.2f, paint)
            
            paint.color = Color.BLACK
            paint.textSize = 20f
            canvas.drawText("Poussées: $pushCount", w/2f, h * 0.25f, paint)
        }
        
        private fun drawEarlyRace(canvas: Canvas, w: Int, h: Int) {
            // VUE DE HAUT - Piste avec virages
            paint.color = Color.parseColor("#334455")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste vue de haut
            drawTrackFromAbove(canvas, w, h)
            
            // Bobsleigh sur la piste
            val bobX = w * 0.2f + trackPosition * (w * 0.6f)
            val bobY = h * 0.7f
            
            paint.color = Color.parseColor("#FF4444")
            canvas.save()
            canvas.translate(bobX, bobY)
            canvas.rotate(tiltX * 20f)
            canvas.drawRoundRect(-20f, -10f, 20f, 10f, 5f, 5f, paint)
            canvas.restore()
            
            // Indicateurs de performance
            drawRaceIndicators(canvas, w, h)
            
            // Instructions
            paint.color = Color.WHITE
            paint.textSize = 20f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("📱 INCLINEZ POUR DIRIGER | AVANT/ARRIÈRE POUR VITESSE", w/2f, 40f, paint)
        }
        
        private fun drawFastRace(canvas: Canvas, w: Int, h: Int) {
            // VUE DE BORD - ON EST DANS LE BOBSLEIGH!
            paint.color = Color.parseColor("#001122")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Effet de vitesse - lignes qui défilent
            drawSpeedLines(canvas, w, h)
            
            // Piste qui défile rapidement
            paint.color = Color.parseColor("#E0F6FF")
            val trackOffset = (phaseTimer * speed * 2f) % 200f
            for (i in -1..10) {
                val lineY = i * 60f - trackOffset
                val lineWidth = 20f + sin((lineY + trackOffset) * 0.02f) * currentTurn * 30f
                canvas.drawRect(w * 0.3f - lineWidth, lineY, w * 0.7f + lineWidth, lineY + 40f, paint)
            }
            
            // Murs qui défilent
            paint.color = Color.parseColor("#AAAAAA")
            for (i in -1..15) {
                val wallY = i * 40f - trackOffset
                canvas.drawRect(0f, wallY, w * 0.3f, wallY + 20f, paint)
                canvas.drawRect(w * 0.7f, wallY, w.toFloat(), wallY + 20f, paint)
            }
            
            // Vue du bobsleigh (nous sommes dedans)
            drawCockpitView(canvas, w, h)
            
            // Compteur de vitesse ÉNORME
            paint.color = Color.parseColor("#FF0000")
            paint.textSize = 48f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${speed.toInt()} KM/H", w/2f, h - 50f, paint)
            
            // Instructions urgentes
            paint.color = Color.YELLOW
            paint.textSize = 24f
            canvas.drawText("⚡ VITESSE MAXIMUM! ATTENTION AUX VIRAGES! ⚡", w/2f, 50f, paint)
        }
        
        private fun drawFinish(canvas: Canvas, w: Int, h: Int) {
            // VUE DE FACE - Ligne d'arrivée dramatique
            paint.color = Color.parseColor("#87CEEB")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Ligne d'arrivée en damier
            for (i in 0..20) {
                val color = if (i % 2 == 0) Color.BLACK else Color.WHITE
                paint.color = color
                canvas.drawRect(i * (w / 20f), h * 0.3f, (i + 1) * (w / 20f), h * 0.4f, paint)
            }
            
            // Bobsleigh qui arrive de face
            val approachProgress = phaseTimer / finishDuration
            val bobSize = 20f + approachProgress * 60f
            val bobX = w / 2f
            val bobY = h * 0.6f - approachProgress * h * 0.2f
            
            paint.color = Color.parseColor("#FF4444")
            canvas.drawOval(bobX - bobSize, bobY - bobSize/2f, bobX + bobSize, bobY + bobSize/2f, paint)
            
            // Équipe visible
            paint.color = Color.parseColor("#0066CC")
            for (i in 0..3) {
                val memberX = bobX + (i - 1.5f) * bobSize * 0.3f
                canvas.drawCircle(memberX, bobY, bobSize * 0.2f, paint)
            }
            
            // Explosion d'effets à l'arrivée
            if (approachProgress > 0.8f) {
                paint.color = Color.YELLOW
                for (i in 1..12) {
                    val angle = i * 30f
                    val effectX = bobX + cos(Math.toRadians(angle.toDouble())).toFloat() * 80f
                    val effectY = bobY + sin(Math.toRadians(angle.toDouble())).toFloat() * 40f
                    canvas.drawCircle(effectX, effectY, 12f, paint)
                }
            }
            
            paint.color = Color.BLACK
            paint.textSize = 32f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🏁 LIGNE D'ARRIVÉE! 🏁", w/2f, h * 0.15f, paint)
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            // VUE PANORAMIQUE - Résultats avec effet doré
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Fond doré pour les résultats
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.5f, paint)
            
            // SCORE ÉNORME ET LISIBLE
            paint.color = Color.BLACK
            paint.textSize = 72f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore}", w/2f, h * 0.25f, paint)
            
            paint.textSize = 28f
            canvas.drawText("POINTS", w/2f, h * 0.35f, paint)
            
            // Détails du score
            paint.color = Color.parseColor("#001122")
            paint.textSize = 20f
            canvas.drawText("🕒 Temps: ${raceTime.toInt()}s", w/2f, h * 0.55f, paint)
            canvas.drawText("⚡ Vitesse moy: ${avgSpeed.toInt()} km/h", w/2f, h * 0.6f, paint)
            canvas.drawText("🚀 Poussée: ${pushPower.toInt()}%", w/2f, h * 0.65f, paint)
            canvas.drawText("💥 Contacts murs: $wallHits", w/2f, h * 0.7f, paint)
            canvas.drawText("🎯 Virages parfaits: $perfectTurns", w/2f, h * 0.75f, paint)
            
            // Confettis
            paint.color = Color.parseColor("#FF6600")
            for (i in 1..15) {
                val confettiX = kotlin.random.Random.nextFloat() * w
                val confettiY = kotlin.random.Random.nextFloat() * h * 0.5f
                canvas.drawRect(confettiX, confettiY, confettiX + 8f, confettiY + 8f, paint)
            }
        }
        
        private fun drawTrackFromAbove(canvas: Canvas, w: Int, h: Int) {
            // Murs de la piste
            paint.color = Color.parseColor("#AAAAAA")
            canvas.drawRect(w * 0.15f, 0f, w * 0.2f, h.toFloat(), paint)
            canvas.drawRect(w * 0.8f, 0f, w * 0.85f, h.toFloat(), paint)
            
            // Surface de course
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(w * 0.2f, 0f, w * 0.8f, h.toFloat(), paint)
            
            // Virage actuel si présent
            if (abs(currentTurn) > 0.2f) {
                paint.color = Color.parseColor("#44FF0000")
                val turnOffset = currentTurn * turnIntensity * 100f
                canvas.drawOval(w/2f + turnOffset - 80f, h * 0.2f, 
                               w/2f + turnOffset + 80f, h * 0.4f, paint)
                
                paint.color = Color.WHITE
                paint.textSize = 18f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("VIRAGE", w/2f + turnOffset, h * 0.32f, paint)
            }
            
            // Lignes de guidage
            paint.color = Color.parseColor("#CCCCCC")
            paint.strokeWidth = 2f
            for (i in 1..3) {
                val lineX = w * 0.2f + i * (w * 0.6f / 4f)
                canvas.drawLine(lineX, 0f, lineX, h.toFloat(), paint)
            }
        }
        
        private fun drawRaceIndicators(canvas: Canvas, w: Int, h: Int) {
            val baseY = h - 120f
            
            // Position sur piste
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(50f, baseY, 250f, baseY + 20f, paint)
            
            val posX = 50f + trackPosition * 200f
            paint.color = when {
                trackPosition < 0.2f || trackPosition > 0.8f -> Color.RED
                trackPosition < 0.3f || trackPosition > 0.7f -> Color.YELLOW
                else -> Color.GREEN
            }
            canvas.drawCircle(posX, baseY + 10f, 12f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Position sur piste", 50f, baseY - 5f, paint)
            
            // Compteur de vitesse
            paint.color = Color.parseColor("#333333")
            canvas.drawCircle(w - 80f, 80f, 50f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${speed.toInt()}", w - 80f, 85f, paint)
            canvas.drawText("km/h", w - 80f, 100f, paint)
        }
        
        private fun drawPushPowerMeter(canvas: Canvas, w: Int, h: Int) {
            // Barre de puissance énorme
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(100f, h - 100f, w - 100f, h - 50f, paint)
            
            paint.color = if (pushPower > 80f) Color.GREEN else if (pushPower > 50f) Color.YELLOW else Color.RED
            val powerWidth = (pushPower / 100f) * (w - 200f)
            canvas.drawRect(100f, h - 95f, 100f + powerWidth, h - 55f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 20f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("PUISSANCE DE POUSSÉE: ${pushPower.toInt()}%", w/2f, h - 110f, paint)
        }
        
        private fun drawSpeedLines(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.WHITE
            paint.alpha = 120
            for (line in speedLines) {
                canvas.drawLine(line.x, line.y, line.x + 30f, line.y, paint)
            }
            paint.alpha = 255
        }
        
        private fun drawCockpitView(canvas: Canvas, w: Int, h: Int) {
            // Vue depuis l'intérieur du bobsleigh
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(0f, h * 0.8f, w.toFloat(), h.toFloat(), paint)
            
            // Bord du bobsleigh
            paint.color = Color.parseColor("#FF4444")
            canvas.drawRect(0f, h * 0.75f, w * 0.1f, h * 0.85f, paint)
            canvas.drawRect(w * 0.9f, h * 0.75f, w.toFloat(), h * 0.85f, paint)
            
            // Indication de direction
            if (abs(tiltX) > 0.2f) {
                paint.color = if (tiltX > 0) Color.RED else Color.BLUE
                val arrow = if (tiltX > 0) "➤➤➤" else "⬅⬅⬅"
                paint.textSize = 24f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(arrow, w/2f, h * 0.82f, paint)
            }
        }
        
        private fun drawIceParticles(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#CCEEEE")
            paint.alpha = 180
            for (particle in iceParticles) {
                canvas.drawCircle(particle.x, particle.y, particle.size, paint)
            }
            paint.alpha = 255
        }
    }

    data class SpeedLine(
        var x: Float,
        var y: Float,
        val speed: Float
    )
    
    data class IceParticle(
        var x: Float,
        var y: Float,
        val speed: Float,
        val size: Float
    )

    enum class GameState {
        PREPARATION, PUSH, EARLY_RACE, FAST_RACE, FINISH, RESULTS, FINISHED
    }
}
