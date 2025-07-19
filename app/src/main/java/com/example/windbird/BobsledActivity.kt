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

    // Variables de gameplay TRÈS LENT et VISUEL
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Phases avec durées TRÈS longues
    private val preparationDuration = 8f // AUGMENTÉ de 5f
    private val pushDuration = 12f // AUGMENTÉ de 8f
    private val earlyRaceDuration = 15f // AUGMENTÉ de 10f
    private val fastRaceDuration = 12f // AUGMENTÉ de 8f
    private val finishDuration = 5f // AUGMENTÉ de 3f
    private val resultsDuration = 8f // AUGMENTÉ de 5f
    
    // Variables de jeu
    private var speed = 0f
    private var maxSpeed = 90f // RÉDUIT de 120f
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
    
    // Contrôles gyroscope - MOINS SENSIBLE
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
            textSize = 22f // AUGMENTÉ de 18f
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(25, 20, 25, 20) // AUGMENTÉ
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

        // Progression TRÈS lente du jeu
        phaseTimer += 0.025f // RÉDUIT de 0.05f
        raceTime += 0.025f

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
        val shakeThreshold = 2.0f // AUGMENTÉ de 1.5f - MOINS SENSIBLE
        
        // Détection des secousses pour la poussée - MOINS SENSIBLE
        if (abs(tiltX) > shakeThreshold || abs(tiltY) > shakeThreshold || abs(tiltZ) > shakeThreshold) {
            if (currentTime - lastShakeTime > 400) { // AUGMENTÉ de 250ms
                pushCount++
                pushPower += 8f // RÉDUIT de 12f
                speed += 4f // RÉDUIT de 6f
                lastShakeTime = currentTime
                generateIceParticles()
            }
        }
        
        pushPower = pushPower.coerceAtMost(100f)
        speed = speed.coerceAtMost(45f) // RÉDUIT de 60f
        
        if (phaseTimer >= pushDuration) {
            gameState = GameState.EARLY_RACE
            phaseTimer = 0f
            speed = 30f + (pushPower * 0.2f) // RÉDUIT de 40f et 0.3f
        }
    }
    
    private fun handleEarlyRace() {
        // Phase de course lente avec apprentissage des virages
        updateRacing(0.8f) // RÉDUIT de 1f
        
        if (phaseTimer >= earlyRaceDuration) {
            gameState = GameState.FAST_RACE
            phaseTimer = 0f
            cameraShake = 0.2f // RÉDUIT de 0.3f
        }
    }
    
    private fun handleFastRace() {
        // Phase de course rapide et intense
        updateRacing(1.4f) // RÉDUIT de 1.8f
        
        if (phaseTimer >= fastRaceDuration) {
            gameState = GameState.FINISH
            phaseTimer = 0f
            cameraShake = 0.6f // RÉDUIT de 1f
        }
    }
    
    private fun handleFinish() {
        // Phase finale
        updateRacing(0.4f) // RÉDUIT de 0.5f
        
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
        // Direction basée sur l'inclinaison gauche/droite - MOINS SENSIBLE
        val steering = tiltX * 0.25f * intensity // RÉDUIT de 0.4f
        trackPosition += steering * 0.02f // RÉDUIT de 0.03f
        trackPosition = trackPosition.coerceIn(0f, 1f)
        
        // Vitesse basée sur l'inclinaison avant/arrière - MOINS SENSIBLE
        if (tiltY < -0.5f) { // AUGMENTÉ de -0.3f
            speed += 1.5f * intensity // RÉDUIT de 2f
        } else if (tiltY > 0.5f) { // AUGMENTÉ de 0.3f
            speed -= 1f * intensity // RÉDUIT de 1.5f
        }
        
        speed = speed.coerceIn(15f, maxSpeed) // VITESSE MIN AUGMENTÉE
        
        // Gestion des collisions avec les murs - MOINS SENSIBLE
        if (trackPosition <= 0.05f || trackPosition >= 0.95f) { // SEUILS ÉLARGIS
            wallHits++
            speed *= 0.8f // MOINS DE PÉNALITÉ de 0.7f
            trackPosition = trackPosition.coerceIn(0.1f, 0.9f) // ZONE ÉLARGIE
            cameraShake += 0.15f // RÉDUIT de 0.2f
        }
        
        // Vérification des virages parfaits
        checkTurnPerformance()
        
        // Progression - PLUS LENTE
        distance += speed * 0.05f * intensity // RÉDUIT de 0.08f
        avgSpeed = (avgSpeed + speed) / 2f
        
        // Génération de nouveaux virages
        if (distance % 200f < 1f) { // AUGMENTÉ de 150f
            generateTrackSection()
        }
        
        // Effets visuels
        if (speed > 60f) { // ADAPTÉ
            generateSpeedLines()
        }
    }
    
    private fun checkTurnPerformance() {
        val idealPosition = 0.5f - (currentTurn * turnIntensity * 0.3f) // RÉDUIT de 0.4f
        val positionError = abs(trackPosition - idealPosition)
        
        if (positionError < 0.15f && abs(currentTurn) > 0.3f) { // ZONE ÉLARGIE de 0.12f
            perfectTurns++
            speed += 0.8f // RÉDUIT de 1f
        }
    }
    
    private fun generateTrackSection() {
        currentTurn = -1f + kotlin.random.Random.nextFloat() * 2f
        turnIntensity = 0.3f + kotlin.random.Random.nextFloat() * 0.5f // RÉDUIT de 0.4f et 0.6f
    }
    
    private fun generateSpeedLines() {
        speedLines.add(SpeedLine(
            x = kotlin.random.Random.nextFloat() * 1000f,
            y = kotlin.random.Random.nextFloat() * 800f,
            speed = 5f + kotlin.random.Random.nextFloat() * 3f // RÉDUIT de 8f et 4f
        ))
        
        if (speedLines.size > 15) { // RÉDUIT de 20
            speedLines.removeFirst()
        }
    }
    
    private fun generateIceParticles() {
        repeat(12) { // RÉDUIT de 15
            iceParticles.add(IceParticle(
                x = kotlin.random.Random.nextFloat() * 1000f,
                y = kotlin.random.Random.nextFloat() * 800f,
                speed = 1.5f + kotlin.random.Random.nextFloat() * 2f, // RÉDUIT de 2f et 3f
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
            particle.x += sin(particle.y * 0.015f) * 0.2f // RÉDUIT de 0.02f et 0.3f
            particle.y > 1000f
        }
        
        if (iceParticles.size < 8) { // RÉDUIT de 10
            generateIceParticles()
        }
        
        cameraShake = maxOf(0f, cameraShake - 0.015f) // RÉDUIT de 0.02f
    }
    
    private fun calculateFinalScore() {
        if (!scoreCalculated) {
            val timeBonus = maxOf(0, 150 - raceTime.toInt()) // AUGMENTÉ de 120
            val speedBonus = (avgSpeed / maxSpeed * 80).toInt()
            val wallPenalty = wallHits * 10 // RÉDUIT de 12
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
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 10f, // RÉDUIT de 12f
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 10f
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
            
            // Équipe qui se place - PLUS GROSSE
            paint.color = Color.parseColor("#0066CC")
            for (i in 0..3) {
                val memberX = w * 0.35f + i * 50f // PLUS ESPACÉ
                val memberY = h * 0.7f
                canvas.drawCircle(memberX, memberY, 18f, paint) // PLUS GROS de 15f
                
                // Numéros - TEXTE PLUS GROS
                paint.color = Color.WHITE
                paint.textSize = 20f // AUGMENTÉ de 16f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("${i+1}", memberX, memberY + 6f, paint)
                paint.color = Color.parseColor("#0066CC")
            }
            
            // Bobsleigh en attente - PLUS GROS
            paint.color = Color.parseColor("#FF4444")
            canvas.drawRoundRect(w * 0.4f, h * 0.5f, w * 0.6f, h * 0.6f, 12f, 12f, paint) // PLUS LARGE
            
            // Instructions - TEXTE PLUS GROS
            paint.color = Color.BLACK
            paint.textSize = 44f // AUGMENTÉ de 32f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🛷 BOBSLEIGH 🛷", w/2f, h * 0.2f, paint)
            
            paint.textSize = 32f // AUGMENTÉ de 24f
            canvas.drawText("L'équipe se prépare...", w/2f, h * 0.3f, paint)
            
            paint.textSize = 28f // AUGMENTÉ de 20f
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
            
            // Bobsleigh qui avance - PLUS GROS
            val bobProgress = pushPower / 100f
            val bobX = w * 0.2f + bobProgress * w * 0.5f
            val bobY = h * 0.65f
            
            paint.color = Color.parseColor("#FF4444")
            canvas.drawRoundRect(bobX - 40f, bobY - 20f, bobX + 60f, bobY + 20f, 10f, 10f, paint) // PLUS GROS
            
            // Équipe qui court et pousse - PLUS GROSSE
            paint.color = Color.parseColor("#0066CC")
            for (i in 0..3) {
                val memberX = bobX - 50f - i * 30f // PLUS ESPACÉ
                val memberY = bobY
                
                // Animation de course
                val runOffset = sin((phaseTimer + i) * 2f) * 4f // PLUS LENT
                canvas.drawCircle(memberX, memberY + runOffset, 15f, paint) // PLUS GROS de 12f
                
                // Lignes de mouvement
                paint.color = Color.parseColor("#660066CC")
                for (j in 1..3) {
                    canvas.drawCircle(memberX - j * 10f, memberY + runOffset, 10f - j * 2, paint) // PLUS GROS
                }
                paint.color = Color.parseColor("#0066CC")
            }
            
            // Barre de puissance de poussée ÉNORME
            drawPushPowerMeter(canvas, w, h)
            
            // Instructions - TEXTE PLUS GROS
            paint.color = Color.RED
            paint.textSize = 36f // AUGMENTÉ de 28f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🚀 SECOUEZ POUR POUSSER! 🚀", w/2f, h * 0.2f, paint)
            
            paint.color = Color.BLACK
            paint.textSize = 28f // AUGMENTÉ de 20f
            canvas.drawText("Poussées: $pushCount", w/2f, h * 0.25f, paint)
        }
        
        private fun drawEarlyRace(canvas: Canvas, w: Int, h: Int) {
            // VUE DE HAUT - Piste avec virages
            paint.color = Color.parseColor("#334455")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste vue de haut
            drawTrackFromAbove(canvas, w, h)
            
            // Bobsleigh sur la piste - PLUS GROS
            val bobX = w * 0.2f + trackPosition * (w * 0.6f)
            val bobY = h * 0.7f
            
            paint.color = Color.parseColor("#FF4444")
            canvas.save()
            canvas.translate(bobX, bobY)
            canvas.rotate(tiltX * 15f) // RÉDUIT de 20f
            canvas.drawRoundRect(-25f, -12f, 25f, 12f, 6f, 6f, paint) // PLUS GROS
            canvas.restore()
            
            // Indicateurs de performance
            drawRaceIndicators(canvas, w, h)
            
            // Instructions - TEXTE PLUS GROS
            paint.color = Color.WHITE
            paint.textSize = 28f // AUGMENTÉ de 20f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("📱 INCLINEZ POUR DIRIGER", w/2f, 50f, paint)
            canvas.drawText("AVANT/ARRIÈRE POUR VITESSE", w/2f, 80f, paint)
        }
        
        private fun drawFastRace(canvas: Canvas, w: Int, h: Int) {
            // VUE DE BORD - ON EST DANS LE BOBSLEIGH!
            paint.color = Color.parseColor("#001122")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Effet de vitesse - lignes qui défilent
            drawSpeedLines(canvas, w, h)
            
            // Piste qui défile rapidement - PLUS LENT
            paint.color = Color.parseColor("#E0F6FF")
            val trackOffset = (phaseTimer * speed * 1.2f) % 200f // RÉDUIT de 2f
            for (i in -1..10) {
                val lineY = i * 60f - trackOffset
                val lineWidth = 20f + sin((lineY + trackOffset) * 0.015f) * currentTurn * 25f // RÉDUIT
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
            
            // Compteur de vitesse ÉNORME - TEXTE PLUS GROS
            paint.color = Color.parseColor("#FF0000")
            paint.textSize = 56f // AUGMENTÉ de 48f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${speed.toInt()} KM/H", w/2f, h - 60f, paint)
            
            // Instructions urgentes - TEXTE PLUS GROS
            paint.color = Color.YELLOW
            paint.textSize = 32f // AUGMENTÉ de 24f
            canvas.drawText("⚡ VITESSE MAXIMUM! ⚡", w/2f, 60f, paint)
            canvas.drawText("ATTENTION AUX VIRAGES!", w/2f, 100f, paint)
        }
        
        private fun drawFinish(canvas: Canvas, w: Int, h: Int) {
            // VUE DE FACE - Ligne d'arrivée dramatique
            paint.color = Color.parseColor("#87CEEB")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Ligne d'arrivée en damier - PLUS GROSSE
            for (i in 0..20) {
                val color = if (i % 2 == 0) Color.BLACK else Color.WHITE
                paint.color = color
                canvas.drawRect(i * (w / 20f), h * 0.3f, (i + 1) * (w / 20f), h * 0.45f, paint) // PLUS HAUTE
            }
            
            // Bobsleigh qui arrive de face - PLUS GROS
            val approachProgress = phaseTimer / finishDuration
            val bobSize = 25f + approachProgress * 80f // PLUS GROS
            val bobX = w / 2f
            val bobY = h * 0.6f - approachProgress * h * 0.2f
            
            paint.color = Color.parseColor("#FF4444")
            canvas.drawOval(bobX - bobSize, bobY - bobSize/2f, bobX + bobSize, bobY + bobSize/2f, paint)
            
            // Équipe visible - PLUS GROSSE
            paint.color = Color.parseColor("#0066CC")
            for (i in 0..3) {
                val memberX = bobX + (i - 1.5f) * bobSize * 0.3f
                canvas.drawCircle(memberX, bobY, bobSize * 0.25f, paint) // PLUS GROS
            }
            
            // Explosion d'effets à l'arrivée
            if (approachProgress > 0.8f) {
                paint.color = Color.YELLOW
                for (i in 1..12) {
                    val angle = i * 30f
                    val effectX = bobX + cos(Math.toRadians(angle.toDouble())).toFloat() * 100f // PLUS GROS
                    val effectY = bobY + sin(Math.toRadians(angle.toDouble())).toFloat() * 50f
                    canvas.drawCircle(effectX, effectY, 15f, paint) // PLUS GROS
                }
            }
            
            paint.color = Color.BLACK
            paint.textSize = 40f // AUGMENTÉ de 32f
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
            
            // SCORE ÉNORME ET LISIBLE - TEXTE PLUS GROS
            paint.color = Color.BLACK
            paint.textSize = 80f // AUGMENTÉ de 72f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore}", w/2f, h * 0.25f, paint)
            
            paint.textSize = 36f // AUGMENTÉ de 28f
            canvas.drawText("POINTS", w/2f, h * 0.35f, paint)
            
            // Détails du score - TEXTE PLUS GROS
            paint.color = Color.parseColor("#001122")
            paint.textSize = 26f // AUGMENTÉ de 20f
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
                canvas.drawRect(confettiX, confettiY, confettiX + 10f, confettiY + 10f, paint) // PLUS GROS
            }
        }
        
        private fun drawTrackFromAbove(canvas: Canvas, w: Int, h: Int) {
            // Murs de la piste - PLUS LARGES
            paint.color = Color.parseColor("#AAAAAA")
            canvas.drawRect(w * 0.12f, 0f, w * 0.2f, h.toFloat(), paint) // PLUS LARGE
            canvas.drawRect(w * 0.8f, 0f, w * 0.88f, h.toFloat(), paint)
            
            // Surface de course
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(w * 0.2f, 0f, w * 0.8f, h.toFloat(), paint)
            
            // Virage actuel si présent
            if (abs(currentTurn) > 0.2f) {
                paint.color = Color.parseColor("#44FF0000")
                val turnOffset = currentTurn * turnIntensity * 80f // RÉDUIT de 100f
                canvas.drawOval(w/2f + turnOffset - 100f, h * 0.2f, 
                               w/2f + turnOffset + 100f, h * 0.4f, paint) // PLUS GROS
                
                paint.color = Color.WHITE
                paint.textSize = 24f // AUGMENTÉ de 18f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("VIRAGE", w/2f + turnOffset, h * 0.32f, paint)
            }
            
            // Lignes de guidage
            paint.color = Color.parseColor("#CCCCCC")
            paint.strokeWidth = 3f // AUGMENTÉ de 2f
            for (i in 1..3) {
                val lineX = w * 0.2f + i * (w * 0.6f / 4f)
                canvas.drawLine(lineX, 0f, lineX, h.toFloat(), paint)
            }
        }
        
        private fun drawRaceIndicators(canvas: Canvas, w: Int, h: Int) {
            val baseY = h - 150f // PLUS BAS
            
            // Position sur piste - PLUS GROSSE
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(60f, baseY, 300f, baseY + 30f, paint) // PLUS LARGE ET HAUTE
            
            val posX = 60f + trackPosition * 240f
            paint.color = when {
                trackPosition < 0.15f || trackPosition > 0.85f -> Color.RED // ADAPTÉ aux nouveaux seuils
                trackPosition < 0.25f || trackPosition > 0.75f -> Color.YELLOW
                else -> Color.GREEN
            }
            canvas.drawCircle(posX, baseY + 15f, 15f, paint) // PLUS GROS
            
            paint.color = Color.WHITE
            paint.textSize = 20f // AUGMENTÉ de 16f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Position sur piste", 60f, baseY - 10f, paint)
            
            // Compteur de vitesse - PLUS GROS
            paint.color = Color.parseColor("#333333")
            canvas.drawCircle(w - 100f, 100f, 60f, paint) // PLUS GROS
            
            paint.color = Color.WHITE
            paint.textSize = 20f // AUGMENTÉ de 16f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${speed.toInt()}", w - 100f, 105f, paint)
            canvas.drawText("km/h", w - 100f, 125f, paint)
        }
        
        private fun drawPushPowerMeter(canvas: Canvas, w: Int, h: Int) {
            // Barre de puissance énorme - PLUS GROSSE
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(150f, h - 120f, w - 150f, h - 60f, paint) // PLUS HAUTE ET LARGE
            
            paint.color = if (pushPower > 70f) Color.GREEN else if (pushPower > 50f) Color.YELLOW else Color.RED
            val powerWidth = (pushPower / 100f) * (w - 300f)
            canvas.drawRect(150f, h - 115f, 150f + powerWidth, h - 65f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 28f // AUGMENTÉ de 20f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("PUISSANCE DE POUSSÉE: ${pushPower.toInt()}%", w/2f, h - 130f, paint)
        }
        
        private fun drawSpeedLines(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.WHITE
            paint.alpha = 120
            paint.strokeWidth = 5f // PLUS ÉPAIS
            for (line in speedLines) {
                canvas.drawLine(line.x, line.y, line.x + 40f, line.y, paint) // PLUS LONG
            }
            paint.alpha = 255
        }
        
        private fun drawCockpitView(canvas: Canvas, w: Int, h: Int) {
            // Vue depuis l'intérieur du bobsleigh - PLUS VISIBLE
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(0f, h * 0.75f, w.toFloat(), h.toFloat(), paint) // PLUS HAUT
            
            // Bord du bobsleigh - PLUS GROS
            paint.color = Color.parseColor("#FF4444")
            canvas.drawRect(0f, h * 0.7f, w * 0.15f, h * 0.85f, paint) // PLUS LARGE
            canvas.drawRect(w * 0.85f, h * 0.7f, w.toFloat(), h * 0.85f, paint)
            
            // Indication de direction - TEXTE PLUS GROS
            if (abs(tiltX) > 0.3f) { // SEUIL ADAPTÉ
                paint.color = if (tiltX > 0) Color.RED else Color.BLUE
                val arrow = if (tiltX > 0) "➤➤➤" else "⬅⬅⬅"
                paint.textSize = 32f // AUGMENTÉ de 24f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(arrow, w/2f, h * 0.82f, paint)
            }
        }
        
        private fun drawIceParticles(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#CCEEEE")
            paint.alpha = 180
            for (particle in iceParticles) {
                canvas.drawCircle(particle.x, particle.y, particle.size * 1.5f, paint) // PLUS GROS
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
