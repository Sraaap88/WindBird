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

    // Variables de gameplay - Durées équilibrées
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    private val preparationDuration = 6f
    private val pushStartDuration = 8f
    private val raceSlowDuration = 15f
    private val raceFastDuration = 12f
    private val finishDuration = 5f
    private val resultsDuration = 8f
    
    // Variables de jeu principales
    private var speed = 0f
    private var maxSpeed = 120f
    private var pushPower = 0f
    private var trackPosition = 0.5f // 0 = gauche, 1 = droite
    private var distance = 0f
    private var totalDistance = 1200f
    
    // Variables de performance
    private var wallHits = 0
    private var perfectTurns = 0
    private var avgSpeed = 0f
    private var raceTime = 0f
    private var pushQuality = 0f
    
    // Variables pour les virages (comme le vent dans le ski)
    private var currentTurn = 0f
    private var turnIntensity = 0f
    private var turnTimer = 0f
    
    // Contrôles gyroscopiques
    private var tiltX = 0f
    private var tiltY = 0f
    private var tiltZ = 0f
    
    // Système de poussée amélioré
    private var pushCount = 0
    private var lastPushTime = 0L
    private var pushRhythm = 0f
    
    // Score et résultats
    private var finalScore = 0
    private var scoreCalculated = false
    
    // Effets visuels
    private var cameraShake = 0f
    private val speedLines = mutableListOf<SpeedLine>()
    private val iceParticles = mutableListOf<IceParticle>()

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
            textSize = 30f
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(35, 30, 35, 30)
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
        pushQuality = 0f
        pushCount = 0
        pushRhythm = 0f
        currentTurn = 0f
        turnIntensity = 0f
        turnTimer = 0f
        tiltX = 0f
        tiltY = 0f
        tiltZ = 0f
        finalScore = 0
        scoreCalculated = false
        cameraShake = 0f
        lastPushTime = 0L
        speedLines.clear()
        iceParticles.clear()
        generateIceParticles()
        generateNewTurn()
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

        phaseTimer += 0.025f
        if (gameState != GameState.PREPARATION) {
            raceTime += 0.025f
        }

        when (gameState) {
            GameState.PREPARATION -> handlePreparation()
            GameState.PUSH_START -> handlePushStart()
            GameState.RACE_SLOW -> handleRaceSlow()
            GameState.RACE_FAST -> handleRaceFast()
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
            gameState = GameState.PUSH_START
            phaseTimer = 0f
        }
    }
    
    private fun handlePushStart() {
        // Système de poussée rythmée amélioré
        val currentTime = System.currentTimeMillis()
        val shakeThreshold = 1.5f
        
        // Détection de secousses avec rythme
        val totalShake = abs(tiltX) + abs(tiltY) + abs(tiltZ)
        if (totalShake > shakeThreshold) {
            if (currentTime - lastPushTime > 300) { // Rythme optimal
                pushCount++
                
                // Bonus si bon rythme (entre 300-600ms)
                val timeDiff = currentTime - lastPushTime
                if (timeDiff > 300 && timeDiff < 600) {
                    pushRhythm += 2f
                    pushPower += 12f
                } else {
                    pushPower += 8f
                }
                
                lastPushTime = currentTime
                cameraShake += 0.2f
                generateIceParticles()
            }
        }
        
        pushPower = pushPower.coerceAtMost(100f)
        pushRhythm = pushRhythm.coerceAtMost(100f)
        
        if (phaseTimer >= pushStartDuration) {
            // Calculer vitesse initiale selon la qualité de la poussée
            pushQuality = (pushPower * 0.7f + pushRhythm * 0.3f) / 100f
            speed = 25f + (pushQuality * 35f) // Entre 25 et 60 km/h
            
            gameState = GameState.RACE_SLOW
            phaseTimer = 0f
            cameraShake = 0.3f
        }
    }
    
    private fun handleRaceSlow() {
        updateRacing(1.0f, false)
        
        if (phaseTimer >= raceSlowDuration) {
            gameState = GameState.RACE_FAST
            phaseTimer = 0f
            cameraShake = 0.5f
            generateSpeedLines()
        }
    }
    
    private fun handleRaceFast() {
        updateRacing(1.8f, true)
        
        if (phaseTimer >= raceFastDuration) {
            gameState = GameState.FINISH
            phaseTimer = 0f
            cameraShake = 0.8f
        }
    }
    
    private fun handleFinish() {
        updateRacing(0.6f, false)
        
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
    
    private fun updateRacing(intensity: Float, isFastPhase: Boolean) {
        // Gestion des virages (comme le vent dans le ski)
        turnTimer += 0.025f
        if (turnTimer > 3f) {
            generateNewTurn()
            turnTimer = 0f
        }
        
        // Direction - pencher gauche/droite pour tourner
        val steering = tiltX * 0.3f * intensity
        trackPosition += steering * 0.025f
        trackPosition = trackPosition.coerceIn(0f, 1f)
        
        // Vitesse - pencher avant/arrière 
        if (tiltY < -0.3f) { // Pencher vers l'avant = accélérer
            speed += 2f * intensity
        } else if (tiltY > 0.3f) { // Pencher vers l'arrière = freiner
            speed -= 1.5f * intensity
        }
        
        // Bonus de vitesse pour les virages parfaits (comme stabilité dans le ski)
        val idealPosition = 0.5f + (currentTurn * turnIntensity * 0.4f)
        val positionError = abs(trackPosition - idealPosition)
        
        if (positionError < 0.2f && abs(currentTurn) > 0.3f) {
            perfectTurns++
            speed += 2f // Bonus vitesse
            
            // Effet visuel de réussite
            if (isFastPhase) {
                generateSpeedLines()
            }
        }
        
        // Gestion des collisions murs
        if (trackPosition <= 0.1f || trackPosition >= 0.9f) {
            wallHits++
            speed *= 0.75f // Pénalité
            trackPosition = trackPosition.coerceIn(0.15f, 0.85f)
            cameraShake += 0.3f
        }
        
        speed = speed.coerceIn(15f, maxSpeed)
        
        // Progression
        distance += speed * 0.06f * intensity
        avgSpeed = (avgSpeed + speed) / 2f
        
        // Effets selon la vitesse
        if (speed > 80f && isFastPhase) {
            generateSpeedLines()
        }
    }
    
    private fun generateNewTurn() {
        currentTurn = -1f + kotlin.random.Random.nextFloat() * 2f
        turnIntensity = 0.4f + kotlin.random.Random.nextFloat() * 0.6f
    }
    
    private fun generateSpeedLines() {
        speedLines.add(SpeedLine(
            x = kotlin.random.Random.nextFloat() * 1000f,
            y = kotlin.random.Random.nextFloat() * 800f,
            speed = 8f + kotlin.random.Random.nextFloat() * 4f
        ))
        
        if (speedLines.size > 25) {
            speedLines.removeFirst()
        }
    }
    
    private fun generateIceParticles() {
        repeat(15) {
            iceParticles.add(IceParticle(
                x = kotlin.random.Random.nextFloat() * 1000f,
                y = kotlin.random.Random.nextFloat() * 800f,
                speed = 2f + kotlin.random.Random.nextFloat() * 3f,
                size = 2f + kotlin.random.Random.nextFloat() * 3f
            ))
        }
    }
    
    private fun updateEffects() {
        // Lignes de vitesse
        speedLines.removeAll { line ->
            line.x -= line.speed
            line.x < -50f
        }
        
        // Particules de glace
        iceParticles.removeAll { particle ->
            particle.y += particle.speed
            particle.x += sin(particle.y * 0.01f) * 0.3f
            particle.y > 1000f
        }
        
        if (iceParticles.size < 10) {
            generateIceParticles()
        }
        
        cameraShake = maxOf(0f, cameraShake - 0.02f)
    }
    
    private fun calculateFinalScore() {
        if (!scoreCalculated) {
            val timeBonus = maxOf(0, 200 - raceTime.toInt())
            val speedBonus = (avgSpeed / maxSpeed * 100).toInt()
            val pushBonus = (pushQuality * 50).toInt()
            val wallPenalty = wallHits * 15
            val turnBonus = perfectTurns * 20
            
            finalScore = maxOf(50, timeBonus + speedBonus + pushBonus - wallPenalty + turnBonus)
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
            GameState.PUSH_START -> "🚀 ${tournamentData.playerNames[currentPlayerIndex]} | Poussée: ${pushCount} (${pushPower.toInt()}%) | ${(pushStartDuration - phaseTimer).toInt() + 1}s"
            GameState.RACE_SLOW -> "🛷 ${tournamentData.playerNames[currentPlayerIndex]} | Course: ${speed.toInt()} km/h | Virages: ${perfectTurns}"
            GameState.RACE_FAST -> "⚡ ${tournamentData.playerNames[currentPlayerIndex]} | VITESSE MAX: ${speed.toInt()} km/h | Murs: ${wallHits}"
            GameState.FINISH -> "🏁 ${tournamentData.playerNames[currentPlayerIndex]} | Sprint final! ${speed.toInt()} km/h"
            GameState.RESULTS -> "🏆 ${tournamentData.playerNames[currentPlayerIndex]} | Temps: ${raceTime.toInt()}s | Score: ${finalScore}"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Course terminée!"
        }
    }

    // Fonction pour obtenir l'emoji du drapeau selon le pays
    private fun getCountryFlag(country: String): String {
        return when (country.uppercase()) {
            "FRANCE" -> "🇫🇷"
            "CANADA" -> "🇨🇦"
            "USA", "ÉTATS-UNIS", "ETATS-UNIS" -> "🇺🇸"
            "ALLEMAGNE", "GERMANY" -> "🇩🇪"
            "ITALIE", "ITALY" -> "🇮🇹"
            "SUISSE", "SWITZERLAND" -> "🇨🇭"
            "AUTRICHE", "AUSTRIA" -> "🇦🇹"
            "NORVÈGE", "NORWAY" -> "🇳🇴"
            "SUÈDE", "SWEDEN" -> "🇸🇪"
            "FINLANDE", "FINLAND" -> "🇫🇮"
            "JAPON", "JAPAN" -> "🇯🇵"
            "CORÉE", "KOREA" -> "🇰🇷"
            "RUSSIE", "RUSSIA" -> "🇷🇺"
            "POLOGNE", "POLAND" -> "🇵🇱"
            "SLOVÉNIE", "SLOVENIA" -> "🇸🇮"
            "RÉPUBLIQUE TCHÈQUE", "CZECH REPUBLIC" -> "🇨🇿"
            else -> "🏴"
        }
    }

    inner class BobsledView(context: Context) : View(context) {
        private val paint = Paint()
        
        // Variables pour les images du bobsleigh (structure prête)
        private var bobsledPreparationBitmap: Bitmap? = null
        private var bobsledPushBitmap: Bitmap? = null
        private var bobsledRaceSlowBitmap: Bitmap? = null
        private var bobsledRaceFastBitmap: Bitmap? = null
        private var bobsledFinishBitmap: Bitmap? = null
        
        init {
            // Structure prête pour vos futures images
            // En attendant vos images, utiliser des substituts
            createFallbackBobsledBitmaps()
            
            /* Quand vous aurez vos images, décommentez ceci :
            try {
                bobsledPreparationBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobsled_preparation)
                bobsledPushBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobsled_push)
                bobsledRaceSlowBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobsled_race_slow)
                bobsledRaceFastBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobsled_race_fast)
                bobsledFinishBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobsled_finish)
            } catch (e: Exception) {
                createFallbackBobsledBitmaps()
            }
            */
        }
        
        private fun createFallbackBobsledBitmaps() {
            // Bitmap de substitution pour la préparation
            bobsledPreparationBitmap = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
            val canvas1 = Canvas(bobsledPreparationBitmap!!)
            val tempPaint = Paint().apply {
                color = Color.parseColor("#FF4444")
                style = Paint.Style.FILL
            }
            canvas1.drawRoundRect(10f, 20f, 90f, 40f, 8f, 8f, tempPaint)
            
            // Équipe debout
            tempPaint.color = Color.parseColor("#0066CC")
            for (i in 0..3) {
                canvas1.drawCircle(20f + i * 15f, 15f, 8f, tempPaint)
            }
            
            // Autres bitmaps avec variations
            bobsledPushBitmap = Bitmap.createBitmap(120, 70, Bitmap.Config.ARGB_8888)
            val canvas2 = Canvas(bobsledPushBitmap!!)
            tempPaint.color = Color.parseColor("#FF4444")
            canvas2.drawRoundRect(20f, 30f, 100f, 50f, 8f, 8f, tempPaint)
            
            bobsledRaceSlowBitmap = Bitmap.createBitmap(140, 50, Bitmap.Config.ARGB_8888)
            val canvas3 = Canvas(bobsledRaceSlowBitmap!!)
            canvas3.drawRoundRect(20f, 15f, 120f, 35f, 10f, 10f, tempPaint)
            
            bobsledRaceFastBitmap = Bitmap.createBitmap(160, 45, Bitmap.Config.ARGB_8888)
            val canvas4 = Canvas(bobsledRaceFastBitmap!!)
            canvas4.drawRoundRect(20f, 10f, 140f, 35f, 12f, 12f, tempPaint)
            
            bobsledFinishBitmap = Bitmap.createBitmap(180, 60, Bitmap.Config.ARGB_8888)
            val canvas5 = Canvas(bobsledFinishBitmap!!)
            canvas5.drawRoundRect(30f, 20f, 150f, 40f, 15f, 15f, tempPaint)
        }

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            
            if (cameraShake > 0f) {
                canvas.save()
                canvas.translate(
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 15f,
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 15f
                )
            }
            
            when (gameState) {
                GameState.PREPARATION -> drawPreparation(canvas, w, h)
                GameState.PUSH_START -> drawPushStart(canvas, w, h)
                GameState.RACE_SLOW -> drawRaceSlow(canvas, w, h)
                GameState.RACE_FAST -> drawRaceFast(canvas, w, h)
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
            // Fond montagne enneigée
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Montagnes en arrière-plan
            paint.color = Color.parseColor("#DDDDDD")
            val path = Path()
            path.moveTo(0f, h * 0.5f)
            path.lineTo(w * 0.3f, h * 0.2f)
            path.lineTo(w * 0.7f, h * 0.3f)
            path.lineTo(w.toFloat(), h * 0.1f)
            path.lineTo(w.toFloat(), h.toFloat())
            path.lineTo(0f, h.toFloat())
            path.close()
            canvas.drawPath(path, paint)
            
            // Piste de départ
            paint.color = Color.parseColor("#CCCCCC")
            val trackPath = Path()
            trackPath.moveTo(w * 0.25f, h * 0.9f)
            trackPath.lineTo(w * 0.75f, h * 0.9f)
            trackPath.lineTo(w * 0.6f, h * 0.4f)
            trackPath.lineTo(w * 0.4f, h * 0.4f)
            trackPath.close()
            canvas.drawPath(trackPath, paint)
            
            // Drapeau du pays - ÉNORME
            val playerCountry = tournamentData.playerCountries[currentPlayerIndex]
            val flag = getCountryFlag(playerCountry)
            
            paint.color = Color.WHITE
            paint.textSize = 180f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(flag, w/2f, h * 0.18f, paint)
            
            paint.textSize = 48f
            canvas.drawText(playerCountry.uppercase(), w/2f, h * 0.25f, paint)
            
            // Bobsleigh et équipe en position
            val bobX = w/2f
            val bobY = h * 0.7f
            val scale = 1.0f
            
            bobsledPreparationBitmap?.let { bmp ->
                val dstRect = RectF(
                    bobX - bmp.width * scale / 2f,
                    bobY - bmp.height * scale / 2f,
                    bobX + bmp.width * scale / 2f,
                    bobY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            // Instructions
            paint.color = Color.BLACK
            paint.textSize = 56f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🛷 BOBSLEIGH 🛷", w/2f, h * 0.35f, paint)
            
            paint.textSize = 40f
            canvas.drawText("L'équipe se prépare...", w/2f, h * 0.42f, paint)
            
            paint.textSize = 36f
            paint.color = Color.YELLOW
            canvas.drawText("Dans ${(preparationDuration - phaseTimer).toInt() + 1} secondes", w/2f, h * 0.5f, paint)
        }
        
        private fun drawPushStart(canvas: Canvas, w: Int, h: Int) {
            // Vue de côté - Phase de poussée
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste avec perspective
            paint.color = Color.parseColor("#CCCCCC")
            val trackPath = Path()
            trackPath.moveTo(0f, h * 0.7f)
            trackPath.lineTo(w.toFloat(), h * 0.8f)
            trackPath.lineTo(w.toFloat(), h * 0.9f)
            trackPath.lineTo(0f, h * 0.85f)
            trackPath.close()
            canvas.drawPath(trackPath, paint)
            
            // Position du bobsleigh selon la puissance
            val pushProgress = pushPower / 100f
            val bobX = w * 0.2f + pushProgress * w * 0.4f
            val bobY = h * 0.78f
            val scale = 0.8f
            
            bobsledPushBitmap?.let { bmp ->
                val dstRect = RectF(
                    bobX - bmp.width * scale / 2f,
                    bobY - bmp.height * scale / 2f,
                    bobX + bmp.width * scale / 2f,
                    bobY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            // Équipe qui pousse (effet visuel de mouvement)
            paint.color = Color.parseColor("#0066CC")
            for (i in 0..3) {
                val memberX = bobX - 40f - i * 25f
                val memberY = bobY
                val runOffset = sin((phaseTimer + i) * 3f) * 8f
                
                canvas.drawCircle(memberX, memberY + runOffset, 12f, paint)
                
                // Trail de mouvement
                paint.alpha = 100
                for (j in 1..3) {
                    canvas.drawCircle(memberX - j * 8f, memberY + runOffset, 8f - j, paint)
                }
                paint.alpha = 255
            }
            
            // Barre de puissance
            drawPushPowerMeter(canvas, w, h)
            
            // Instructions
            paint.color = Color.RED
            paint.textSize = 60f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🚀 SECOUEZ POUR POUSSER! 🚀", w/2f, h * 0.15f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 45f
            canvas.drawText("Poussées: ${pushCount} | Rythme: ${pushRhythm.toInt()}%", w/2f, h * 0.25f, paint)
        }
        
        private fun drawRaceSlow(canvas: Canvas, w: Int, h: Int) {
            // Vue de haut - Apprentissage des virages
            paint.color = Color.parseColor("#334455")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste vue de haut
            drawTrackFromAbove(canvas, w, h)
            
            // Bobsleigh sur la piste
            val bobX = w * 0.2f + trackPosition * (w * 0.6f)
            val bobY = h * 0.7f
            val scale = 0.6f
            
            canvas.save()
            canvas.translate(bobX, bobY)
            canvas.rotate(tiltX * 10f)
            
            bobsledRaceSlowBitmap?.let { bmp ->
                val dstRect = RectF(
                    -bmp.width * scale / 2f,
                    -bmp.height * scale / 2f,
                    bmp.width * scale / 2f,
                    bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            canvas.restore()
            
            // Indicateurs de virage
            drawTurnIndicator(canvas, w, h)
            
            // Indicateurs de performance
            drawRaceIndicators(canvas, w, h)
            
            // Instructions
            paint.color = Color.WHITE
            paint.textSize = 55f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("📱 INCLINEZ POUR DIRIGER", w/2f, 70f, paint)
            
            paint.textSize = 40f
            paint.color = Color.CYAN
            canvas.drawText("Suivez la ligne idéale des virages!", w/2f, h - 60f, paint)
        }
        
        private fun drawRaceFast(canvas: Canvas, w: Int, h: Int) {
            // Vue embarquée - On est dans le bobsleigh!
            paint.color = Color.parseColor("#001122")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Effet de vitesse
            drawSpeedLines(canvas, w, h)
            
            // Piste qui défile rapidement
            paint.color = Color.parseColor("#E0F6FF")
            val trackOffset = (phaseTimer * speed * 2f) % 150f
            for (i in -2..15) {
                val lineY = i * 50f - trackOffset
                val lineWidth = 30f + sin((lineY + trackOffset) * 0.02f) * currentTurn * 40f
                canvas.drawRect(w * 0.25f - lineWidth, lineY, w * 0.75f + lineWidth, lineY + 30f, paint)
            }
            
            // Murs qui défilent
            paint.color = Color.parseColor("#AAAAAA")
            for (i in -2..20) {
                val wallY = i * 30f - trackOffset
                canvas.drawRect(0f, wallY, w * 0.25f, wallY + 15f, paint)
                canvas.drawRect(w * 0.75f, wallY, w.toFloat(), wallY + 15f, paint)
            }
            
            // Vue du cockpit
            drawCockpitView(canvas, w, h)
            
            // Compteur de vitesse ÉNORME
            paint.color = Color.parseColor("#FF0000")
            paint.textSize = 80f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${speed.toInt()} KM/H", w/2f, h - 80f, paint)
            
            // Instructions d'urgence
            paint.color = Color.YELLOW
            paint.textSize = 55f
            canvas.drawText("⚡ VITESSE MAXIMUM! ⚡", w/2f, 70f, paint)
        }
        
        private fun drawFinish(canvas: Canvas, w: Int, h: Int) {
            // Vue de face - Ligne d'arrivée
            paint.color = Color.parseColor("#87CEEB")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Ligne d'arrivée en damier
            for (i in 0..20) {
                val color = if (i % 2 == 0) Color.BLACK else Color.WHITE
                paint.color = color
                canvas.drawRect(i * (w / 20f), h * 0.3f, (i + 1) * (w / 20f), h * 0.5f, paint)
            }
            
            // Bobsleigh qui arrive
            val approachProgress = phaseTimer / finishDuration
            val bobSize = 30f + approachProgress * 100f
            val bobX = w / 2f
            val bobY = h * 0.7f - approachProgress * h * 0.3f
            val scale = 0.5f + approachProgress * 0.8f
            
            bobsledFinishBitmap?.let { bmp ->
                val dstRect = RectF(
                    bobX - bmp.width * scale / 2f,
                    bobY - bmp.height * scale / 2f,
                    bobX + bmp.width * scale / 2f,
                    bobY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            // Effet d'arrivée
            if (approachProgress > 0.7f) {
                paint.color = Color.YELLOW
                for (i in 1..15) {
                    val angle = i * 24f
                    val effectX = bobX + cos(Math.toRadians(angle.toDouble())).toFloat() * 120f
                    val effectY = bobY + sin(Math.toRadians(angle.toDouble())).toFloat() * 60f
                    canvas.drawCircle(effectX, effectY, 18f, paint)
                }
            }
            
            paint.color = Color.BLACK
            paint.textSize = 60f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🏁 LIGNE D'ARRIVÉE! 🏁", w/2f, h * 0.15f, paint)
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            // Fond doré pour les résultats
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
            
            // Score final
            paint.color = Color.BLACK
            paint.textSize = 80f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore}", w/2f, h * 0.2f, paint)
            
            paint.textSize = 40f
            canvas.drawText("POINTS", w/2f, h * 0.3f, paint)
            
            // Détails
            paint.color = Color.parseColor("#001122")
            paint.textSize = 32f
            canvas.drawText("🕒 Temps: ${raceTime.toInt()}s", w/2f, h * 0.5f, paint)
            canvas.drawText("⚡ Vitesse moy: ${avgSpeed.toInt()} km/h", w/2f, h * 0.55f, paint)
            canvas.drawText("🚀 Qualité poussée: ${(pushQuality * 100).toInt()}%", w/2f, h * 0.6f, paint)
            canvas.drawText("🎯 Virages parfaits: ${perfectTurns}", w/2f, h * 0.65f, paint)
            canvas.drawText("💥 Contacts murs: ${wallHits}", w/2f, h * 0.7f, paint)
            
            // Étoiles d'effet
            paint.color = Color.YELLOW
            for (i in 1..12) {
                val starX = kotlin.random.Random.nextFloat() * w
                val starY = kotlin.random.Random.nextFloat() * h * 0.4f
                drawStar(canvas, starX, starY, 15f)
            }
        }
        
        private fun drawTrackFromAbove(canvas: Canvas, w: Int, h: Int) {
            // Murs de la piste
            paint.color = Color.parseColor("#AAAAAA")
            canvas.drawRect(w * 0.15f, 0f, w * 0.25f, h.toFloat(), paint)
            canvas.drawRect(w * 0.75f, 0f, w * 0.85f, h.toFloat(), paint)
            
            // Surface de course
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(w * 0.25f, 0f, w * 0.75f, h.toFloat(), paint)
            
            // Virage actuel
            if (abs(currentTurn) > 0.2f) {
                paint.color = Color.parseColor("#44FFFF00")
                val turnOffset = currentTurn * turnIntensity * 80f
                canvas.drawOval(w/2f + turnOffset - 120f, h * 0.2f, 
                               w/2f + turnOffset + 120f, h * 0.5f, paint)
            }
        }
        
        private fun drawTurnIndicator(canvas: Canvas, w: Int, h: Int) {
            // Indicateur de virage comme le vent dans le ski
            val turnX = w - 150f
            val turnY = 150f
            
            paint.color = Color.parseColor("#333333")
            paint.style = Paint.Style.FILL
            canvas.drawRect(turnX - 80f, turnY - 60f, turnX + 80f, turnY + 60f, paint)
            
            paint.color = Color.YELLOW
            paint.textSize = 48f
            paint.textAlign = Paint.Align.CENTER
            
            val turnText = if (currentTurn < -0.2f) "⬅️" else if (currentTurn > 0.2f) "➡️" else "⏸️"
            canvas.drawText(turnText, turnX, turnY - 10f, paint)
            
            paint.textSize = 24f
            paint.color = Color.WHITE
            canvas.drawText("VIRAGE", turnX, turnY - 35f, paint)
            canvas.drawText("${(turnIntensity * 100).toInt()}%", turnX, turnY + 25f, paint)
        }
        
        private fun drawRaceIndicators(canvas: Canvas, w: Int, h: Int) {
            val baseY = h - 180f
            
            // Position sur piste
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(80f, baseY, 340f, baseY + 40f, paint)
            
            val posX = 80f + trackPosition * 260f
            paint.color = when {
                trackPosition < 0.2f || trackPosition > 0.8f -> Color.RED
                trackPosition < 0.3f || trackPosition > 0.7f -> Color.YELLOW
                else -> Color.GREEN
            }
            canvas.drawCircle(posX, baseY + 20f, 18f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 30f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Position sur piste", 80f, baseY - 15f, paint)
            
            // Compteur vitesse
            paint.color = Color.parseColor("#333333")
            canvas.drawCircle(w - 120f, 120f, 80f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 32f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${speed.toInt()}", w - 120f, 120f, paint)
            canvas.drawText("km/h", w - 120f, 150f, paint)
        }
        
        private fun drawPushPowerMeter(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(150f, h - 120f, w - 150f, h - 50f, paint)
            
            paint.color = if (pushPower > 70f) Color.GREEN else if (pushPower > 40f) Color.YELLOW else Color.RED
            val powerWidth = (pushPower / 100f) * (w - 300f)
            canvas.drawRect(150f, h - 115f, 150f + powerWidth, h - 55f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 30f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("PUISSANCE: ${pushPower.toInt()}% | RYTHME: ${pushRhythm.toInt()}%", w/2f, h - 130f, paint)
        }
        
        private fun drawSpeedLines(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.WHITE
            paint.alpha = 150
            paint.strokeWidth = 6f
            for (line in speedLines) {
                canvas.drawLine(line.x, line.y, line.x + 60f, line.y, paint)
            }
            paint.alpha = 255
        }
        
        private fun drawCockpitView(canvas: Canvas, w: Int, h: Int) {
            // Vue depuis l'intérieur
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(0f, h * 0.7f, w.toFloat(), h.toFloat(), paint)
            
            paint.color = Color.parseColor("#FF4444")
            canvas.drawRect(0f, h * 0.65f, w * 0.2f, h * 0.8f, paint)
            canvas.drawRect(w * 0.8f, h * 0.65f, w.toFloat(), h * 0.8f, paint)
            
            // Indication direction
            if (abs(tiltX) > 0.4f) {
                paint.color = if (tiltX > 0) Color.RED else Color.BLUE
                val arrow = if (tiltX > 0) "➤➤➤" else "⬅⬅⬅"
                paint.textSize = 40f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(arrow, w/2f, h * 0.8f, paint)
            }
        }
        
        private fun drawIceParticles(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#CCEEEE")
            paint.alpha = 200
            for (particle in iceParticles) {
                canvas.drawCircle(particle.x, particle.y, particle.size, paint)
            }
            paint.alpha = 255
        }
        
        private fun drawStar(canvas: Canvas, x: Float, y: Float, size: Float) {
            val path = Path()
            for (i in 0..4) {
                val angle = i * 72f - 90f
                val outerX = x + cos(Math.toRadians(angle.toDouble())).toFloat() * size
                val outerY = y + sin(Math.toRadians(angle.toDouble())).toFloat() * size
                
                if (i == 0) path.moveTo(outerX, outerY) else path.lineTo(outerX, outerY)
                
                val innerAngle = angle + 36f
                val innerX = x + cos(Math.toRadians(innerAngle.toDouble())).toFloat() * size * 0.4f
                val innerY = y + sin(Math.toRadians(innerAngle.toDouble())).toFloat() * size * 0.4f
                path.lineTo(innerX, innerY)
            }
            path.close()
            canvas.drawPath(path, paint)
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
        PREPARATION, PUSH_START, RACE_SLOW, RACE_FAST, FINISH, RESULTS, FINISHED
    }
}
