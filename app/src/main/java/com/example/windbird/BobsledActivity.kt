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
    
    // Animation sprite sheet bobsleigh
    private lateinit var bobsledSpriteSheet: Bitmap
    private val frameWidth = 80
    private val frameHeight = 120
    private val frameSpacing = 5
    private val totalFrames = 5
    private var currentFrame = 2
    
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
        
        currentPlayerIndex = intent.getIntExtra("current_player_index", 0)
        
        // Validation du currentPlayerIndex
        if (currentPlayerIndex == -1 || currentPlayerIndex >= tournamentData.playerNames.size) {
            currentPlayerIndex = 0
        }

        // Initialiser les capteurs
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        
        // Charger le sprite sheet du bobsleigh
        bobsledSpriteSheet = BitmapFactory.decodeResource(resources, R.drawable.bobsled_sprite)

        // Créer l'interface
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        statusText = TextView(this).apply {
            text = "Joueur: ${tournamentData.playerNames[currentPlayerIndex]} | Phase: Poussée de départ"
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
        currentFrame = 2
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
        
        if (abs(x) > shakeThreshold || abs(y) > shakeThreshold) {
            if (currentTime - lastShakeTime > 300) {
                pushCount++
                speed += 8f
                lastShakeTime = currentTime
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
        steeringAngle = x * 0.5f
        steeringAngle = steeringAngle.coerceIn(-1f, 1f)
        
        // Mise à jour de la frame selon le braquage
        updateBobsledFrame()
        
        // Incliner avant/arrière = vitesse
        if (y < -0.5f) {
            speed += 1.5f
            accelerationEffect = 1f
            brakingEffect = 0f
            generateAccelerationParticles()
        } else if (y > 0.5f) {
            brakingForce = y * 0.3f
            speed -= brakingForce * 3f
            brakingEffect = 1f
            accelerationEffect = 0f
            generateBrakingParticles()
        } else {
            brakingForce = 0f
            accelerationEffect = maxOf(0f, accelerationEffect - 0.05f)
            brakingEffect = maxOf(0f, brakingEffect - 0.05f)
        }
        
        speed = speed.coerceIn(0f, maxSpeed)
        
        trackPosition += steeringAngle * 0.02f
        trackPosition = trackPosition.coerceIn(0f, 1f)
        
        if (trackPosition <= 0.1f || trackPosition >= 0.9f) {
            wallContacts++
            speed *= 0.85f
            trackPosition = trackPosition.coerceIn(0.15f, 0.85f)
        }
        
        checkTurnPerformance()
        
        distance += speed * 0.1f
        trackProgress = distance / totalDistance
        
        updateParticles()
        
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
    
    private fun updateBobsledFrame() {
        currentFrame = when {
            steeringAngle < -0.6f -> 0
            steeringAngle < -0.3f -> 1
            steeringAngle > 0.6f  -> 4
            steeringAngle > 0.3f  -> 3
            else                  -> 2
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
        val timeBonus = maxOf(0, 100 - raceTime.toInt())
        val speedBonus = (speed / maxSpeed * 50).toInt()
        val wallPenalty = wallContacts * 10
        val turnBonus = perfectTurns * 15
        
        return maxOf(50, timeBonus + speedBonus - wallPenalty + turnBonus)
    }
    
    private fun proceedToNextPlayerOrEvent() {
        try {
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
                    val aiScore = generateAIScore()
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
        } catch (e: Exception) {
            val intent = Intent(this, EventsMenuActivity::class.java).apply {
                putExtra("practice_mode", practiceMode)
                putExtra("tournament_data", tournamentData)
                putStringArrayListExtra("player_names", tournamentData.playerNames)
                putStringArrayListExtra("player_countries", tournamentData.playerCountries)
                putExtra("number_of_players", numberOfPlayers)
            }
            startActivity(intent)
            finish()
        }
    }
    
    private fun generateAIScore(): Int {
        return 80 + kotlin.random.Random.nextInt(91)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    
    private fun updateStatus() {
        try {
            val playerName = if (currentPlayerIndex < tournamentData.playerNames.size) {
                tournamentData.playerNames[currentPlayerIndex]
            } else {
                "Joueur ${currentPlayerIndex + 1}"
            }
            
            statusText.text = when (gameState) {
                GameState.START_PUSH -> "🚀 $playerName | Poussée: ${pushCount} (secouez pour pousser!)"
                GameState.RACING -> "🛷 $playerName | Vitesse: ${speed.toInt()} km/h | ${distance.toInt()}/${totalDistance.toInt()}m"
                GameState.FINISHED -> "🏁 $playerName | Temps: ${raceTime.toInt()}s | Score: ${calculateScore()}"
            }
        } catch (e: Exception) {
            statusText.text = "Bobsleigh - Phase: ${gameState.name}"
        }
    }

    inner class BobsledView(context: Context) : View(context) {
        private val paint = Paint()

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            
            when (gameState) {
                GameState.START_PUSH -> drawStartPush(canvas, w, h)
                GameState.RACING -> drawRacing(canvas, w, h)
                GameState.FINISHED -> drawFinished(canvas, w, h)
            }
        }
        
        private fun drawStartPush(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#87CEEB")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            paint.color = Color.WHITE
            paint.strokeWidth = 10f
            canvas.drawLine(w * 0.8f, 0f, w * 0.8f, h.toFloat(), paint)
            
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(0f, h * 0.4f, w.toFloat(), h * 0.8f, paint)
            
            val bobX = w * 0.6f + (pushTime / maxPushTime) * w * 0.15f
            val bobY = h * 0.6f
            
            paint.color = Color.parseColor("#FF4444")
            canvas.drawRect(bobX - 40f, bobY - 15f, bobX + 40f, bobY + 15f, paint)
            
            paint.color = Color.parseColor("#0066CC")
            for (i in 0..3) {
                val memberX = bobX - 60f - i * 20f
                canvas.drawCircle(memberX, bobY, 12f, paint)
            }
            
            paint.color = Color.WHITE
            paint.textSize = 24f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🚀 SECOUEZ POUR POUSSER ! 🚀", w/2f, h * 0.3f, paint)
            
            paint.textSize = 20f
            canvas.drawText("Poussées: $pushCount", w/2f, h * 0.35f, paint)
            
            paint.color = Color.YELLOW
            val timeProgress = pushTime / maxPushTime
            canvas.drawRect(w * 0.2f, h * 0.9f, w * 0.2f + timeProgress * w * 0.6f, h * 0.95f, paint)
        }
        
        private fun drawRacing(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#334455")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            drawTrack(canvas, w, h)
            drawBobsled(canvas, w, h)
            drawRaceUI(canvas, w, h)
        }
        
        private fun drawTrack(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#AAAAAA")
            canvas.drawRect(w * 0.1f, 0f, w * 0.1f + 40f, h.toFloat(), paint)
            canvas.drawRect(w * 0.9f - 40f, 0f, w * 0.9f, h.toFloat(), paint)
            
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(w * 0.1f + 40f, 0f, w * 0.9f - 40f, h.toFloat(), paint)
            
            paint.color = Color.parseColor("#CCCCCC")
            paint.strokeWidth = 3f
            for (i in 1..3) {
                val lineX = w * 0.1f + 40f + i * (w * 0.8f - 80f) / 4f
                canvas.drawLine(lineX, h.toFloat(), lineX, 0f, paint)
            }
            
            if (abs(currentTurn) > 0.2f) {
                paint.color = Color.parseColor("#66FF0000")
                paint.style = Paint.Style.FILL
                
                val turnOffset = currentTurn * turnIntensity * 50f
                canvas.drawRect(w/2f + turnOffset - 30f, h * 0.2f, 
                               w/2f + turnOffset + 30f, h * 0.4f, paint)
                paint.style = Paint.Style.FILL
            }
        }
        
        private fun drawBobsled(canvas: Canvas, w: Int, h: Int) {
            val bobX = w * 0.1f + 40f + trackPosition * (w * 0.8f - 80f)
            val bobY = h * 0.8f
            
            drawParticles(canvas, bobX, bobY)
            
            if (accelerationEffect > 0f) {
                paint.color = Color.parseColor("#66FF6600")
                paint.alpha = (accelerationEffect * 150).toInt()
                canvas.drawCircle(bobX, bobY, 50f, paint)
                paint.alpha = 255
            }
            
            if (brakingEffect > 0f) {
                paint.color = Color.parseColor("#660066FF")
                paint.alpha = (brakingEffect * 150).toInt()
                canvas.drawCircle(bobX, bobY, 45f, paint)
                paint.alpha = 255
            }
            
            paint.color = Color.parseColor("#66000000")
            canvas.drawRect(bobX - 35f, bobY + 5f, bobX + 35f, bobY + 25f, paint)
            
            drawBobsledSprite(canvas, bobX, bobY)
            
            if (speed > 60f) {
                paint.color = Color.parseColor("#44FFFFFF")
                val speedLines = (speed / 20f).toInt().coerceAtMost(8)
                for (i in 1..speedLines) {
                    val alpha = (255 * (1f - i * 0.15f)).toInt().coerceAtLeast(50)
                    paint.alpha = alpha
                    canvas.drawLine(bobX - 40f - i * 15f, bobY, bobX - 30f - i * 8f, bobY, paint)
                }
                paint.alpha = 255
            }
            
            if (abs(steeringAngle) > 0.2f) {
                paint.color = if (steeringAngle > 0) Color.parseColor("#66FF0000") else Color.parseColor("#660000FF")
                val arrowX = bobX + steeringAngle * 50f
                canvas.drawCircle(arrowX, bobY - 40f, 12f, paint)
                
                paint.color = Color.WHITE
                paint.textSize = 16f
                paint.textAlign = Paint.Align.CENTER
                val arrow = if (steeringAngle > 0) "➤" else "⬅"
                canvas.drawText(arrow, arrowX, bobY - 30f, paint)
            }
        }
        
        private fun drawBobsledSprite(canvas: Canvas, x: Float, y: Float) {
            if (!::bobsledSpriteSheet.isInitialized) {
                // Fallback si le sprite sheet n'est pas chargé
                paint.color = Color.parseColor("#FF4444")
                canvas.drawRect(x - 30f, y - 20f, x + 30f, y + 20f, paint)
                return
            }
            
            val srcX = frameSpacing + currentFrame * (frameWidth + frameSpacing)
            val srcY = frameSpacing
            val srcRect = Rect(srcX, srcY, srcX + frameWidth, srcY + frameHeight)
            
            val displayWidth = frameWidth * 0.8f
            val displayHeight = frameHeight * 0.8f
            
            val dstRect = RectF(
                x - displayWidth/2f,
                y - displayHeight/2f,
                x + displayWidth/2f,
                y + displayHeight/2f
            )
            
            canvas.save()
            
            // Retourner verticalement l'image (flip Y)
            canvas.scale(1f, -1f, x, y)
            canvas.rotate(steeringAngle * 10f, x, y)
            
            if (accelerationEffect > 0f) {
                val colorFilter = PorterDuffColorFilter(
                    Color.parseColor("#44FF6600"), 
                    PorterDuff.Mode.SRC_ATOP
                )
                paint.colorFilter = colorFilter
            } else if (brakingEffect > 0f) {
                val colorFilter = PorterDuffColorFilter(
                    Color.parseColor("#440066FF"), 
                    PorterDuff.Mode.SRC_ATOP
                )
                paint.colorFilter = colorFilter
            }
            
            canvas.drawBitmap(bobsledSpriteSheet, srcRect, dstRect, paint)
            paint.colorFilter = null
            
            canvas.restore()
            
            paint.color = Color.WHITE
            paint.textSize = 14f
            paint.textAlign = Paint.Align.CENTER
            val playerNumber = if (currentPlayerIndex < 4) currentPlayerIndex + 1 else 1
            canvas.drawText("$playerNumber", x, y + 5f, paint)
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
                        canvas.drawLine(particleX, particleY, particleX - 3f, particleY - 3f, paint)
                    }
                }
            }
            paint.alpha = 255
        }
        
        private fun drawRaceUI(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#333333")
            canvas.drawCircle(w - 80f, 80f, 60f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 12f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("VITESSE", w - 80f, 60f, paint)
            
            paint.textSize = 20f
            canvas.drawText("${speed.toInt()}", w - 80f, 85f, paint)
            
            paint.textSize = 10f
            canvas.drawText("km/h", w - 80f, 100f, paint)
            
            paint.color = Color.parseColor("#666666")
            canvas.drawRect(50f, 50f, 250f, 70f, paint)
            
            val posX = 50f + trackPosition * 200f
            paint.color = if (trackPosition < 0.2f || trackPosition > 0.8f) Color.RED else Color.GREEN
            canvas.drawCircle(posX, 60f, 8f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 12f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Position sur piste", 50f, 40f, paint)
            
            paint.color = Color.parseColor("#666666")
            canvas.drawRect(50f, h - 50f, w - 50f, h - 30f, paint)
            
            paint.color = Color.YELLOW
            canvas.drawRect(50f, h - 50f, 50f + trackProgress * (w - 100f), h - 30f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 14f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("${distance.toInt()}m / ${totalDistance.toInt()}m", 50f, h - 55f, paint)
            
            paint.textSize = 12f
            canvas.drawText("Contacts murs: $wallContacts", 50f, h - 100f, paint)
            canvas.drawText("Virages parfaits: $perfectTurns", 50f, h - 85f, paint)
            canvas.drawText("Temps: ${raceTime.toInt()}s", 50f, h - 70f, paint)
        }
        
        private fun drawFinished(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.3f, paint)
            
            paint.color = Color.BLACK
            paint.strokeWidth = 8f
            for (i in 0..10) {
                val y = h * 0.3f + i * 20f
                val color = if (i % 2 == 0) Color.BLACK else Color.WHITE
                paint.color = color
                canvas.drawRect(0f, y, w.toFloat(), y + 20f, paint)
            }
            
            paint.color = Color.BLACK
            paint.textSize = 28f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🏁 ARRIVÉE ! 🏁", w/2f, h * 0.15f, paint)
            
            paint.textSize = 20f
            canvas.drawText("Temps final: ${raceTime.toInt()}s", w/2f, h * 0.25f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 18f
            canvas.drawText("Vitesse moyenne: ${(distance/raceTime).toInt()} km/h", w/2f, h * 0.7f, paint)
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
