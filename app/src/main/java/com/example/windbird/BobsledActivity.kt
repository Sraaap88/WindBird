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

    // Variables de jeu
    private var gameState = GameState.START_PUSH
    private var speed = 0f
    private var maxSpeed = 120f
    private var distance = 0f
    private var totalDistance = 2000f
    private var trackProgress = 0f
    
    // Variables de départ
    private var pushTime = 0f
    private var maxPushTime = 3f
    private var pushCount = 0
    private var lastShakeTime = 0L
    
    // Variables de pilotage
    private var steeringAngle = 0f
    private var brakingForce = 0f
    private var wallContacts = 0
    private var perfectTurns = 0
    
    // Variables de piste
    private var currentTurn = 0f
    private var turnIntensity = 0f
    private var trackPosition = 0.5f
    
    // Variables de temps et score
    private var raceTime = 0f
    private var lapStartTime = 0L
    
    // Effets visuels pour accélération/freinage
    private var accelerationEffect = 0f
    private var brakingEffect = 0f
    private val particles = mutableListOf<Particle>()
    
    // Données du tournoi
    private lateinit var tournamentData: TournamentData
    private var eventIndex: Int = 0
    private var numberOfPlayers: Int = 1
    private var currentPlayerIndex: Int = 0
    private var practiceMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // Récupérer les données du tournoi
        tournamentData = intent.getSerializableExtra("tournament_data") as TournamentData
        eventIndex = intent.getIntExtra("event_index", 0)
        numberOfPlayers = intent.getIntExtra("number_of_players", 1)
        practiceMode = intent.getBooleanExtra("practice_mode", false)
        currentPlayerIndex = intent.getIntExtra("current_player_index", tournamentData.getNextPlayer(eventIndex))

        // Initialiser les capteurs
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // Créer l'interface
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        statusText = TextView(this).apply {
            text = "🛷 Joueur: ${tournamentData.playerNames[currentPlayerIndex]} | Phase: Poussée de départ"
            setTextColor(Color.WHITE)
            textSize = 16f
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(20, 10, 20, 10)
        }

        gameView = BobsledView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        
        // Initialiser le jeu
        initializeRace()
        lapStartTime = System.currentTimeMillis()
    }
    
    private fun initializeRace() {
        speed = 0f
        distance = 0f
        trackProgress = 0f
        pushTime = 0f
        pushCount = 0
        wallContacts = 0
        perfectTurns = 0
        trackPosition = 0.5f
        steeringAngle = 0f
        raceTime = 0f
        gameState = GameState.START_PUSH
        accelerationEffect = 0f
        brakingEffect = 0f
        particles.clear()
        generateTrackSection()
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

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        when (gameState) {
            GameState.START_PUSH -> handleStartPush(x, y, z)
            GameState.RACING -> handleRacing(x, y, z)
            GameState.FINISHED -> {}
        }

        updateStatus()
        gameView.invalidate()
    }
    
    private fun handleStartPush(x: Float, y: Float, z: Float) {
        pushTime += 0.05f
        
        val currentTime = System.currentTimeMillis()
        val shakeThreshold = 2.0f
        
        // Détection des secousses pour la poussée
        if (abs(x) > shakeThreshold || abs(y) > shakeThreshold || abs(z) > shakeThreshold) {
            if (currentTime - lastShakeTime > 300) {
                pushCount++
                speed += 8f
                lastShakeTime = currentTime
                generateAccelerationParticles()
            }
        }
        
        if (pushTime >= maxPushTime) {
            gameState = GameState.RACING
            speed += pushCount * 2f
            speed = speed.coerceAtMost(60f)
        }
    }
    
    private fun handleRacing(x: Float, y: Float, z: Float) {
        raceTime += 0.05f
        
        // Incliner gauche/droite = direction
        steeringAngle = x * 0.8f
        steeringAngle = steeringAngle.coerceIn(-1f, 1f)
        
        // Incliner avant/arrière = vitesse/freinage
        if (y < -0.5f) {
            // Incliner vers l'avant = accélération
            speed += 2f
            accelerationEffect = 1f
            brakingEffect = 0f
            generateAccelerationParticles()
        } else if (y > 0.5f) {
            // Incliner vers l'arrière = freinage
            brakingForce = y * 0.4f
            speed -= brakingForce * 4f
            brakingEffect = 1f
            accelerationEffect = 0f
            generateBrakingParticles()
        } else {
            brakingForce = 0f
            accelerationEffect = maxOf(0f, accelerationEffect - 0.05f)
            brakingEffect = maxOf(0f, brakingEffect - 0.05f)
        }
        
        speed = speed.coerceIn(0f, maxSpeed)
        
        // Mise à jour position sur piste
        trackPosition += steeringAngle * 0.025f
        trackPosition = trackPosition.coerceIn(0f, 1f)
        
        // Collision avec les murs
        if (trackPosition <= 0.1f || trackPosition >= 0.9f) {
            wallContacts++
            speed *= 0.8f
            trackPosition = trackPosition.coerceIn(0.15f, 0.85f)
        }
        
        checkTurnPerformance()
        
        distance += speed * 0.1f
        trackProgress = distance / totalDistance
        
        updateParticles()
        
        // Génération de nouvelles sections de piste
        if (distance % 200f < 1f) {
            generateTrackSection()
        }
        
        if (distance >= totalDistance) {
            gameState = GameState.FINISHED
            raceTime = (System.currentTimeMillis() - lapStartTime) / 1000f
            
            if (!practiceMode) {
                tournamentData.addScore(currentPlayerIndex, eventIndex, calculateScore())
            }
            
            statusText.postDelayed({
                proceedToNextPlayerOrEvent()
            }, 3000)
        }
    }
    
    private fun generateAccelerationParticles() {
        repeat(3) {
            particles.add(Particle(
                x = -50f - kotlin.random.Random.nextFloat() * 30f,
                y = kotlin.random.Random.nextFloat() * 20f - 10f,
                color = if (kotlin.random.Random.nextBoolean()) Color.parseColor("#FF6600") else Color.parseColor("#FF9900"),
                life = 1f,
                type = ParticleType.ACCELERATION
            ))
        }
    }
    
    private fun generateBrakingParticles() {
        repeat(2) {
            particles.add(Particle(
                x = kotlin.random.Random.nextFloat() * 40f - 20f,
                y = 15f + kotlin.random.Random.nextFloat() * 10f,
                color = if (kotlin.random.Random.nextBoolean()) Color.WHITE else Color.parseColor("#CCCCCC"),
                life = 0.8f,
                type = ParticleType.BRAKING
            ))
        }
    }
    
    private fun updateParticles() {
        particles.removeAll { particle ->
            particle.life -= 0.05f
            particle.x += when (particle.type) {
                ParticleType.ACCELERATION -> -5f
                ParticleType.BRAKING -> 0f
            }
            particle.y += when (particle.type) {
                ParticleType.ACCELERATION -> kotlin.random.Random.nextFloat() * 2f - 1f
                ParticleType.BRAKING -> 2f
            }
            particle.life <= 0f
        }
    }
    
    private fun generateTrackSection() {
        currentTurn = -1f + kotlin.random.Random.nextFloat() * 2f
        turnIntensity = 0.3f + kotlin.random.Random.nextFloat() * 0.7f
    }
    
    private fun checkTurnPerformance() {
        val idealPosition = 0.5f - (currentTurn * turnIntensity * 0.3f)
        val positionError = abs(trackPosition - idealPosition)
        
        if (positionError < 0.15f) {
            speed += 0.5f
            if (positionError < 0.08f) {
                perfectTurns++
            }
        } else if (positionError > 0.3f) {
            speed *= 0.95f
        }
    }
    
    private fun calculateScore(): Int {
        val timeBonus = maxOf(0, 150 - raceTime.toInt())
        val speedBonus = (speed / maxSpeed * 50).toInt()
        val wallPenalty = wallContacts * 15
        val turnBonus = perfectTurns * 20
        
        return maxOf(50, timeBonus + speedBonus - wallPenalty + turnBonus)
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
                val aiScore = (80..170).random()
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
            GameState.START_PUSH -> "🚀 ${tournamentData.playerNames[currentPlayerIndex]} | Poussée: ${pushCount} (secouez pour pousser!)"
            GameState.RACING -> "🛷 ${tournamentData.playerNames[currentPlayerIndex]} | Vitesse: ${speed.toInt()} km/h | ${distance.toInt()}/${totalDistance.toInt()}m"
            GameState.FINISHED -> "🏁 ${tournamentData.playerNames[currentPlayerIndex]} | Temps: ${raceTime.toInt()}s | Score: ${calculateScore()}"
        }
    }

    inner class BobsledView(context: Context) : View(context) {
        private val paint = Paint()

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            
            // AJOUTÉ : Zone de visualisation du bobsleigh (35% à gauche)
            val bobsledZoneWidth = (w * 0.35f).toInt()
            drawBobsledVisualization(canvas, bobsledZoneWidth, h)
            
            // Zone principale (65% à droite)
            canvas.save()
            canvas.translate(bobsledZoneWidth.toFloat(), 0f)
            val mainWidth = w - bobsledZoneWidth
            
            when (gameState) {
                GameState.START_PUSH -> drawStartPush(canvas, mainWidth, h)
                GameState.RACING -> drawRacing(canvas, mainWidth, h)
                GameState.FINISHED -> drawFinished(canvas, mainWidth, h)
            }
            
            canvas.restore()
        }
        
        // AJOUTÉ : Visualisation du bobsleigh avec indicateurs de contrôle
        private fun drawBobsledVisualization(canvas: Canvas, w: Int, h: Int) {
            // Fond de la zone du bobsleigh
            paint.color = Color.parseColor("#002244")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Ligne de séparation
            paint.color = Color.WHITE
            paint.strokeWidth = 3f
            canvas.drawLine(w.toFloat(), 0f, w.toFloat(), h.toFloat(), paint)
            
            val centerX = w / 2f
            val centerY = h / 2f
            
            when (gameState) {
                GameState.START_PUSH -> {
                    // Phase de poussée - Montrer l'équipe qui pousse
                    paint.color = Color.WHITE
                    paint.textSize = 16f
                    paint.textAlign = Paint.Align.CENTER
                    
                    canvas.drawText("🚀 POUSSÉE", centerX, centerY - 60f, paint)
                    canvas.drawText("Secouez le", centerX, centerY - 30f, paint)
                    canvas.drawText("téléphone !", centerX, centerY, paint)
                    
                    // Bobsleigh statique
                    paint.color = Color.parseColor("#FF4444")
                    canvas.drawRoundRect(centerX - 40f, centerY + 20f, centerX + 40f, centerY + 50f, 10f, 10f, paint)
                    
                    // Équipe qui pousse (animation selon pushCount)
                    paint.color = Color.parseColor("#0066CC")
                    for (i in 0..3) {
                        val memberX = centerX - 60f - i * 25f
                        val memberY = centerY + 35f
                        
                        // Animation de poussée
                        val pushOffset = if (pushCount > i * 2) sin(pushCount * 0.5f) * 3f else 0f
                        canvas.drawCircle(memberX + pushOffset, memberY, 12f, paint)
                    }
                    
                    // Compteur de poussées
                    paint.color = Color.YELLOW
                    paint.textSize = 20f
                    canvas.drawText("Poussées: $pushCount", centerX, centerY + 80f, paint)
                }
                
                GameState.RACING -> {
                    // Phase de course - Montrer le bobsleigh avec direction
                    paint.color = Color.WHITE
                    paint.textSize = 14f
                    paint.textAlign = Paint.Align.CENTER
                    canvas.drawText("🛷 PILOTAGE", centerX, 30f, paint)
                    
                    // Piste miniature
                    paint.color = Color.parseColor("#AAAAAA")
                    canvas.drawRoundRect(centerX - 60f, centerY - 80f, centerX + 60f, centerY + 80f, 15f, 15f, paint)
                    
                    paint.color = Color.parseColor("#E0F6FF")
                    canvas.drawRoundRect(centerX - 50f, centerY - 70f, centerX + 50f, centerY + 70f, 10f, 10f, paint)
                    
                    // Position du bobsleigh sur la piste
                    val bobX = centerX - 40f + trackPosition * 80f
                    val bobY = centerY
                    
                    // Bobsleigh avec rotation selon steering
                    canvas.save()
                    canvas.translate(bobX, bobY)
                    canvas.rotate(steeringAngle * 30f)
                    
                    // Corps du bobsleigh
                    paint.color = Color.parseColor("#FF4444")
                    canvas.drawRoundRect(-15f, -8f, 15f, 8f, 4f, 4f, paint)
                    
                    // Effets visuels
                    if (accelerationEffect > 0f) {
                        paint.color = Color.parseColor("#66FF6600")
                        paint.alpha = (accelerationEffect * 200).toInt()
                        canvas.drawCircle(0f, 0f, 20f, paint)
                        paint.alpha = 255
                    }
                    
                    if (brakingEffect > 0f) {
                        paint.color = Color.parseColor("#660066FF")
                        paint.alpha = (brakingEffect * 200).toInt()
                        canvas.drawCircle(0f, 0f, 18f, paint)
                        paint.alpha = 255
                    }
                    
                    canvas.restore()
                    
                    // Indicateurs de contrôle
                    drawControlIndicators(canvas, w, h)
                }
                
                GameState.FINISHED -> {
                    paint.color = Color.YELLOW
                    paint.textSize = 18f
                    paint.textAlign = Paint.Align.CENTER
                    canvas.drawText("🏁 ARRIVÉE !", centerX, centerY - 20f, paint)
                    
                    paint.color = Color.WHITE
                    paint.textSize = 16f
                    canvas.drawText("Temps: ${raceTime.toInt()}s", centerX, centerY + 10f, paint)
                    canvas.drawText("Score: ${calculateScore()}", centerX, centerY + 40f, paint)
                }
            }
        }
        
        // AJOUTÉ : Indicateurs de contrôle avec codes couleur
        private fun drawControlIndicators(canvas: Canvas, w: Int, h: Int) {
            val startY = h - 200f
            
            // Indicateur de direction
            paint.style = Paint.Style.FILL
            paint.color = when {
                abs(steeringAngle) < 0.2f -> Color.GREEN
                abs(steeringAngle) < 0.6f -> Color.YELLOW
                else -> Color.RED
            }
            canvas.drawRect(10f, startY, 10f + abs(steeringAngle) * 60f, startY + 15f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 12f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Direction: ${(steeringAngle * 100).toInt()}%", 10f, startY + 30f, paint)
            
            // Indicateur de vitesse
            paint.color = when {
                speed < 40f -> Color.RED
                speed < 80f -> Color.YELLOW
                else -> Color.GREEN
            }
            canvas.drawRect(10f, startY + 40f, 10f + (speed / maxSpeed) * 80f, startY + 55f, paint)
            
            paint.color = Color.WHITE
            canvas.drawText("Vitesse: ${speed.toInt()} km/h", 10f, startY + 70f, paint)
            
            // Position sur piste
            paint.color = when {
                trackPosition < 0.2f || trackPosition > 0.8f -> Color.RED
                trackPosition < 0.3f || trackPosition > 0.7f -> Color.YELLOW
                else -> Color.GREEN
            }
            canvas.drawRect(10f, startY + 80f, 10f + trackPosition * 60f, startY + 95f, paint)
            
            paint.color = Color.WHITE
            canvas.drawText("Position piste", 10f, startY + 110f, paint)
            
            // Instructions de contrôle
            paint.color = Color.CYAN
            paint.textSize = 10f
            when {
                gameState == GameState.RACING && speed < 30f -> 
                    canvas.drawText("⬇️ Inclinez avant", 10f, startY + 125f, paint)
                abs(steeringAngle) > 0.8f -> 
                    canvas.drawText("⚖️ Stabilisez direction", 10f, startY + 125f, paint)
                trackPosition < 0.2f -> 
                    canvas.drawText("➡️ Inclinez droite", 10f, startY + 125f, paint)
                trackPosition > 0.8f -> 
                    canvas.drawText("⬅️ Inclinez gauche", 10f, startY + 125f, paint)
                else -> {
                    paint.color = Color.GREEN
                    canvas.drawText("✅ Bon pilotage !", 10f, startY + 125f, paint)
                }
            }
            
            // Stats de course
            paint.color = Color.WHITE
            paint.textSize = 11f
            canvas.drawText("Murs: $wallContacts", 10f, startY + 140f, paint)
            canvas.drawText("Virages parfaits: $perfectTurns", 10f, startY + 155f, paint)
        }
        
        private fun drawStartPush(canvas: Canvas, w: Int, h: Int) {
            // Fond ciel
            paint.color = Color.parseColor("#87CEEB")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Ligne d'arrivée
            paint.color = Color.WHITE
            paint.strokeWidth = 10f
            canvas.drawLine(w * 0.8f, 0f, w * 0.8f, h.toFloat(), paint)
            
            // Piste de départ
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(0f, h * 0.4f, w.toFloat(), h * 0.8f, paint)
            
            // Instructions
            paint.color = Color.WHITE
            paint.textSize = 20f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🚀 SECOUEZ POUR POUSSER ! 🚀", w/2f, h * 0.2f, paint)
            
            // Barre de progression du temps
            paint.color = Color.parseColor("#666666")
            canvas.drawRect(w * 0.2f, h * 0.9f, w * 0.8f, h * 0.95f, paint)
            
            paint.color = Color.YELLOW
            val timeProgress = pushTime / maxPushTime
            canvas.drawRect(w * 0.2f, h * 0.9f, w * 0.2f + timeProgress * w * 0.6f, h * 0.95f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 16f
            canvas.drawText("Temps restant: ${(maxPushTime - pushTime).toInt()}s", w/2f, h * 0.87f, paint)
        }
        
        private fun drawRacing(canvas: Canvas, w: Int, h: Int) {
            // Fond de course
            paint.color = Color.parseColor("#334455")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            drawTrack(canvas, w, h)
            drawBobsled(canvas, w, h)
            drawRaceUI(canvas, w, h)
        }
        
        private fun drawTrack(canvas: Canvas, w: Int, h: Int) {
            // Murs de la piste
            paint.color = Color.parseColor("#AAAAAA")
            canvas.drawRect(w * 0.1f, 0f, w * 0.1f + 30f, h.toFloat(), paint)
            canvas.drawRect(w * 0.9f - 30f, 0f, w * 0.9f, h.toFloat(), paint)
            
            // Surface de la piste
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(w * 0.1f + 30f, 0f, w * 0.9f - 30f, h.toFloat(), paint)
            
            // Lignes de guidage
            paint.color = Color.parseColor("#CCCCCC")
            paint.strokeWidth = 2f
            for (i in 1..3) {
                val lineX = w * 0.1f + 30f + i * (w * 0.8f - 60f) / 4f
                canvas.drawLine(lineX, h.toFloat(), lineX, 0f, paint)
            }
            
            // Indication de virage si présent
            if (abs(currentTurn) > 0.3f) {
                paint.color = Color.parseColor("#44FF0000")
                paint.style = Paint.Style.FILL
                
                val turnOffset = currentTurn * turnIntensity * 30f
                canvas.drawRect(w/2f + turnOffset - 20f, h * 0.1f, 
                               w/2f + turnOffset + 20f, h * 0.3f, paint)
                
                paint.color = Color.WHITE
                paint.textSize = 16f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("VIRAGE", w/2f + turnOffset, h * 0.25f, paint)
            }
        }
        
        private fun drawBobsled(canvas: Canvas, w: Int, h: Int) {
            val bobX = w * 0.1f + 30f + trackPosition * (w * 0.8f - 60f)
            val bobY = h * 0.8f
            
            // Particules d'effet
            drawParticles(canvas, bobX, bobY)
            
            // Ombre
            paint.color = Color.parseColor("#66000000")
            canvas.drawOval(bobX - 25f, bobY + 5f, bobX + 25f, bobY + 20f, paint)
            
            // Bobsleigh principal
            paint.color = Color.parseColor("#FF4444")
            canvas.save()
            canvas.translate(bobX, bobY)
            canvas.rotate(steeringAngle * 15f)
            canvas.drawRoundRect(-20f, -10f, 20f, 10f, 5f, 5f, paint)
            canvas.restore()
            
            // Numéro du joueur
            paint.color = Color.WHITE
            paint.textSize = 14f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${currentPlayerIndex + 1}", bobX, bobY + 5f, paint)
        }
        
        private fun drawParticles(canvas: Canvas, bobX: Float, bobY: Float) {
            for (particle in particles) {
                paint.color = particle.color
                paint.alpha = (particle.life * 255).toInt()
                
                val particleX = bobX + particle.x
                val particleY = bobY + particle.y
                
                when (particle.type) {
                    ParticleType.ACCELERATION -> {
                        canvas.drawCircle(particleX, particleY, 3f + particle.life * 2f, paint)
                    }
                    ParticleType.BRAKING -> {
                        canvas.drawCircle(particleX, particleY, 2f + particle.life * 1f, paint)
                    }
                }
            }
            paint.alpha = 255
        }
        
        private fun drawRaceUI(canvas: Canvas, w: Int, h: Int) {
            // Barre de progression de la course
            paint.color = Color.parseColor("#666666")
            canvas.drawRect(50f, h - 50f, w - 50f, h - 30f, paint)
            
            paint.color = Color.YELLOW
            canvas.drawRect(50f, h - 50f, 50f + trackProgress * (w - 100f), h - 30f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 14f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("${distance.toInt()}m / ${totalDistance.toInt()}m", 50f, h - 55f, paint)
            
            // Instructions selon la phase
            paint.color = Color.CYAN
            paint.textSize = 16f
            paint.textAlign = Paint.Align.CENTER
            
            val instruction = when {
                speed < 20f -> "⬇️ Inclinez vers l'avant pour accélérer"
                abs(steeringAngle) > 0.8f -> "⚖️ Stabilisez la direction"
                trackPosition < 0.2f || trackPosition > 0.8f -> "⚠️ Attention aux murs !"
                else -> "🛷 Regardez le bobsleigh à gauche pour le pilotage"
            }
            
            canvas.drawText(instruction, w/2f, h - 70f, paint)
        }
        
        private fun drawFinished(canvas: Canvas, w: Int, h: Int) {
            // Drapeau à damier
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.3f, paint)
            
            // Pattern damier
            paint.color = Color.BLACK
            for (i in 0..10) {
                val y = h * 0.3f + i * 20f
                val color = if (i % 2 == 0) Color.BLACK else Color.WHITE
                paint.color = color
                canvas.drawRect(0f, y, w.toFloat(), y + 20f, paint)
            }
            
            paint.color = Color.BLACK
            paint.textSize = 24f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🏁 ARRIVÉE ! 🏁", w/2f, h * 0.15f, paint)
            
            paint.textSize = 18f
            canvas.drawText("Temps final: ${raceTime.toInt()}s", w/2f, h * 0.25f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 16f
            canvas.drawText("Vitesse moyenne: ${if (raceTime > 0) (distance/raceTime).toInt() else 0} km/h", w/2f, h * 0.7f, paint)
            canvas.drawText("Contacts murs: $wallContacts", w/2f, h * 0.75f, paint)
            canvas.drawText("Virages parfaits: $perfectTurns", w/2f, h * 0.8f, paint)
            canvas.drawText("Score final: ${calculateScore()} points", w/2f, h * 0.85f, paint)
        }
    }

    // Classes pour les particules et leurs types
    data class Particle(
        var x: Float,
        var y: Float,
        val color: Int,
        var life: Float,
        val type: ParticleType
    )
    
    enum class ParticleType {
        ACCELERATION, BRAKING
    }

    enum class GameState {
        START_PUSH, RACING, FINISHED
    }
}
