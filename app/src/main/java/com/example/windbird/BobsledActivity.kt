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

    // Nouvelle structure de jeu
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Durées ajustées pour la nouvelle structure
    private val preparationDuration = 6f
    private val pushStartDuration = 8f
    private val firstDescentDuration = 5f      // Première descente diagonale
    private val secondDescentDuration = 4f     // Deuxième descente plus rapide
    private val controlDescentDuration = 20f   // Phase de contrôle cockpit
    private val finishLineDuration = 3f        // Passage ligne d'arrivée
    private val celebrationDuration = 8f       // Animation célébration
    private val resultsDuration = 5f
    
    // Variables de jeu principales
    private var speed = 0f
    private var maxSpeed = 120f
    private var pushPower = 0f
    private var trackPosition = 0.5f
    private var distance = 0f
    private var totalDistance = 1200f
    
    // Variables de performance
    private var wallHits = 0
    private var perfectTurns = 0
    private var avgSpeed = 0f
    private var raceTime = 0f
    private var pushQuality = 0f
    
    // Variables pour les virages
    private var currentTurn = 0f
    private var turnIntensity = 0f
    private var turnTimer = 0f
    
    // Contrôles gyroscopiques
    private var tiltX = 0f
    private var tiltY = 0f
    private var tiltZ = 0f
    
    // Système de poussée
    private var pushCount = 0
    private var lastPushTime = 0L
    private var pushRhythm = 0f
    
    // Variables cockpit
    private var steeringAccuracy = 1f
    private var idealDirection = 0f
    private var controlPerformance = 0f
    
    // Position sur la piste (pour la carte)
    private var trackProgress = 0f
    
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
        steeringAccuracy = 1f
        idealDirection = 0f
        controlPerformance = 0f
        trackProgress = 0f
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
            GameState.FIRST_DESCENT -> handleFirstDescent()
            GameState.SECOND_DESCENT -> handleSecondDescent()
            GameState.CONTROL_DESCENT -> handleControlDescent()
            GameState.FINISH_LINE -> handleFinishLine()
            GameState.CELEBRATION -> handleCelebration()
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
        val currentTime = System.currentTimeMillis()
        val shakeThreshold = 1.5f
        
        val totalShake = abs(tiltX) + abs(tiltY) + abs(tiltZ)
        if (totalShake > shakeThreshold) {
            if (currentTime - lastPushTime > 300) {
                pushCount++
                
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
            pushQuality = (pushPower * 0.7f + pushRhythm * 0.3f) / 100f
            speed = 35f + (pushQuality * 25f)
            
            gameState = GameState.FIRST_DESCENT
            phaseTimer = 0f
            cameraShake = 0.3f
        }
    }
    
    private fun handleFirstDescent() {
        // Première descente - vitesse modérée
        if (phaseTimer >= firstDescentDuration) {
            speed += 15f  // Accélération pour la deuxième descente
            gameState = GameState.SECOND_DESCENT
            phaseTimer = 0f
        }
    }
    
    private fun handleSecondDescent() {
        // Deuxième descente - plus rapide
        if (phaseTimer >= secondDescentDuration) {
            speed += 20f  // Encore plus de vitesse pour le contrôle
            speed = speed.coerceAtMost(maxSpeed)
            gameState = GameState.CONTROL_DESCENT
            phaseTimer = 0f
            generateNewTurn()
        }
    }
    
    private fun handleControlDescent() {
        // Phase de contrôle avec gyroscope
        updateControlPhase()
        
        // Progression sur la piste
        trackProgress += 0.025f / controlDescentDuration
        trackProgress = trackProgress.coerceAtMost(1f)
        
        if (phaseTimer >= controlDescentDuration) {
            gameState = GameState.FINISH_LINE
            phaseTimer = 0f
            cameraShake = 0.5f
        }
    }
    
    private fun handleFinishLine() {
        // Passage de la ligne d'arrivée
        if (phaseTimer >= finishLineDuration) {
            gameState = GameState.CELEBRATION
            phaseTimer = 0f
            speed *= 0.8f  // Ralentissement pour la célébration
        }
    }
    
    private fun handleCelebration() {
        // Ralentissement progressif
        speed = maxOf(10f, speed * 0.98f)
        
        if (phaseTimer >= celebrationDuration) {
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
    
    private fun updateControlPhase() {
        // Génération de direction idéale qui change
        turnTimer += 0.025f
        if (turnTimer > 2.5f) {
            idealDirection = -1f + kotlin.random.Random.nextFloat() * 2f
            turnTimer = 0f
        }
        
        // Calcul de précision du steering
        val directionError = abs(tiltZ - idealDirection)
        steeringAccuracy = 1f - (directionError / 2f).coerceIn(0f, 1f)
        
        // Accumulation de performance
        controlPerformance = (controlPerformance * 0.98f + steeringAccuracy * 0.02f)
        
        // Ajustement de vitesse selon performance
        if (steeringAccuracy < 0.5f) {
            speed = maxOf(speed * 0.995f, 40f)  // Ralentissement si mauvais contrôle
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
        speedLines.removeAll { line ->
            line.x -= line.speed
            line.x < -50f
        }
        
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
            val timeBonus = maxOf(0, 250 - raceTime.toInt())
            val speedBonus = (avgSpeed / maxSpeed * 100).toInt()
            val pushBonus = (pushQuality * 80).toInt()
            val controlBonus = (controlPerformance * 120).toInt()
            val wallPenalty = wallHits * 20
            
            finalScore = maxOf(80, timeBonus + speedBonus + pushBonus + controlBonus - wallPenalty)
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
                val aiScore = (120..220).random()
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
            GameState.FIRST_DESCENT -> "🛷 ${tournamentData.playerNames[currentPlayerIndex]} | Première descente: ${speed.toInt()} km/h"
            GameState.SECOND_DESCENT -> "⚡ ${tournamentData.playerNames[currentPlayerIndex]} | Accélération: ${speed.toInt()} km/h"
            GameState.CONTROL_DESCENT -> "🎮 ${tournamentData.playerNames[currentPlayerIndex]} | Contrôle: ${(steeringAccuracy * 100).toInt()}% | ${speed.toInt()} km/h"
            GameState.FINISH_LINE -> "🏁 ${tournamentData.playerNames[currentPlayerIndex]} | Ligne d'arrivée!"
            GameState.CELEBRATION -> "🎉 ${tournamentData.playerNames[currentPlayerIndex]} | Célébration!"
            GameState.RESULTS -> "🏆 ${tournamentData.playerNames[currentPlayerIndex]} | Score: ${finalScore}"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Course terminée!"
        }
    }

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
        
        // Images du bobsleigh
        private var bobsledPreparationBitmap: Bitmap? = null
        private var bobPushBitmap: Bitmap? = null
        private var bobRightBitmap: Bitmap? = null
        private var bobLeftBitmap: Bitmap? = null
        private var bobDownBitmap: Bitmap? = null
        private var bobCockpitBitmap: Bitmap? = null
        private var bobFinishLineBitmap: Bitmap? = null
        private var bobCelebrationBitmap: Bitmap? = null
        
        init {
            try {
                bobsledPreparationBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobsled_preparation)
                bobPushBitmap = BitmapFactory.decodeResource(resources, R.drawable.bob_push)
                bobRightBitmap = BitmapFactory.decodeResource(resources, R.drawable.bob_right)
                bobLeftBitmap = BitmapFactory.decodeResource(resources, R.drawable.bob_left)
                bobDownBitmap = BitmapFactory.decodeResource(resources, R.drawable.bob_down)
                bobCockpitBitmap = BitmapFactory.decodeResource(resources, R.drawable.bob_cockpit)
                bobFinishLineBitmap = BitmapFactory.decodeResource(resources, R.drawable.bob_finish_line)
                bobCelebrationBitmap = BitmapFactory.decodeResource(resources, R.drawable.bob_celebration)
            } catch (e: Exception) {
                createFallbackBobsledBitmaps()
            }
        }
        
        private fun createFallbackBobsledBitmaps() {
            // Créer des images de substitution si les vraies images ne sont pas trouvées
            bobsledPreparationBitmap = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888)
            val canvas1 = Canvas(bobsledPreparationBitmap!!)
            val tempPaint = Paint().apply {
                color = Color.parseColor("#FF4444")
                style = Paint.Style.FILL
            }
            canvas1.drawRoundRect(20f, 40f, 180f, 80f, 15f, 15f, tempPaint)
            
            // Autres images de substitution
            bobPushBitmap = createSubstituteBitmap(Color.parseColor("#FF6644"))
            bobRightBitmap = createSubstituteBitmap(Color.parseColor("#4444FF"))
            bobLeftBitmap = createSubstituteBitmap(Color.parseColor("#44FF44"))
            bobDownBitmap = createSubstituteBitmap(Color.parseColor("#FFFF44"))
            bobCockpitBitmap = createSubstituteBitmap(Color.parseColor("#FF44FF"))
            bobFinishLineBitmap = createSubstituteBitmap(Color.parseColor("#44FFFF"))
            bobCelebrationBitmap = createSubstituteBitmap(Color.parseColor("#FFB444"))
        }
        
        private fun createSubstituteBitmap(color: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val tempPaint = Paint().apply {
                this.color = color
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(10f, 20f, 110f, 60f, 10f, 10f, tempPaint)
            return bitmap
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
                GameState.FIRST_DESCENT -> drawFirstDescent(canvas, w, h)
                GameState.SECOND_DESCENT -> drawSecondDescent(canvas, w, h)
                GameState.CONTROL_DESCENT -> drawControlDescent(canvas, w, h)
                GameState.FINISH_LINE -> drawFinishLine(canvas, w, h)
                GameState.CELEBRATION -> drawCelebration(canvas, w, h)
                GameState.RESULTS -> drawResults(canvas, w, h)
                GameState.FINISHED -> drawResults(canvas, w, h)
            }
            
            drawIceParticles(canvas, w, h)
            
            if (cameraShake > 0f) {
                canvas.restore()
            }
        }
        
        private fun drawPreparation(canvas: Canvas, w: Int, h: Int) {
            // Fond avec l'image de préparation
            bobsledPreparationBitmap?.let { bmp ->
                val dstRect = RectF(0f, 0f, w.toFloat(), h.toFloat())
                canvas.drawBitmap(bmp, null, dstRect, paint)
            } ?: run {
                // Fond de substitution
                paint.color = Color.parseColor("#E0F6FF")
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            
            // Rectangle blanc pour le drapeau (comme dans l'image de référence)
            paint.color = Color.WHITE
            val flagRect = RectF(50f, 50f, 300f, 200f)
            canvas.drawRect(flagRect, paint)
            
            // Bordure noire du rectangle
            paint.color = Color.BLACK
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawRect(flagRect, paint)
            paint.style = Paint.Style.FILL
            
            // Gros drapeau emoji dans le rectangle blanc
            val playerCountry = tournamentData.playerCountries[currentPlayerIndex]
            val flag = getCountryFlag(playerCountry)
            
            paint.color = Color.BLACK
            paint.textSize = 120f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(flag, flagRect.centerX(), flagRect.centerY() + 40f, paint)
            
            // Nom du pays sous le rectangle
            paint.textSize = 28f
            canvas.drawText(playerCountry.uppercase(), flagRect.centerX(), flagRect.bottom + 40f, paint)
            
            // Instructions au centre
            paint.textSize = 56f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🛷 BOBSLEIGH 🛷", w/2f, h * 0.4f, paint)
            
            paint.textSize = 40f
            canvas.drawText("L'équipe se prépare...", w/2f, h * 0.47f, paint)
            
            paint.textSize = 36f
            paint.color = Color.YELLOW
            canvas.drawText("Dans ${(preparationDuration - phaseTimer).toInt() + 1} secondes", w/2f, h * 0.55f, paint)
        }
        
        private fun drawPushStart(canvas: Canvas, w: Int, h: Int) {
            // Fond bleu ciel
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste simple
            paint.color = Color.parseColor("#CCCCCC")
            canvas.drawRect(0f, h * 0.7f, w.toFloat(), h * 0.85f, paint)
            
            // Bobsleigh qui traverse TOUT l'écran selon la poussée
            val pushProgress = (pushPower / 100f) * 0.7f + (phaseTimer / pushStartDuration) * 0.3f
            val bobX = w * (-0.1f + pushProgress * 1.2f)  // Va de -10% à +110% (sort complètement)
            val bobY = h * 0.775f
            val scale = 0.15f
            
            bobPushBitmap?.let { bmp ->
                val dstRect = RectF(
                    bobX - bmp.width * scale / 2f,
                    bobY - bmp.height * scale / 2f,
                    bobX + bmp.width * scale / 2f,
                    bobY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            // Instructions
            paint.color = Color.RED
            paint.textSize = 60f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🚀 SECOUEZ POUR POUSSER! 🚀", w/2f, h * 0.15f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 45f
            canvas.drawText("Poussées: ${pushCount} | Puissance: ${pushPower.toInt()}%", w/2f, h * 0.25f, paint)
            
            // Barre de puissance
            drawPushPowerMeter(canvas, w, h)
        }
        
        private fun drawFirstDescent(canvas: Canvas, w: Int, h: Int) {
            // Fond montagne/ciel
            paint.color = Color.parseColor("#87CEEB")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Animation diagonale : haut-gauche vers bas-droite
            val progress = phaseTimer / firstDescentDuration
            val startX = -200f
            val startY = h * 0.1f
            val endX = w + 200f
            val endY = h * 0.9f
            
            val bobX = startX + (endX - startX) * progress
            val bobY = startY + (endY - startY) * progress
            val scale = 0.2f
            
            bobFinishLineBitmap?.let { bmp ->
                val dstRect = RectF(
                    bobX - bmp.width * scale / 2f,
                    bobY - bmp.height * scale / 2f,
                    bobX + bmp.width * scale / 2f,
                    bobY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            // Effet de traînée
            for (i in 1..5) {
                val trailProgress = progress - (i * 0.1f)
                if (trailProgress > 0f) {
                    val trailX = startX + (endX - startX) * trailProgress
                    val trailY = startY + (endY - startY) * trailProgress
                    
                    paint.color = Color.WHITE
                    paint.alpha = (255 * (1f - i * 0.2f)).toInt()
                    canvas.drawCircle(trailX, trailY, 8f - i * 1.5f, paint)
                    paint.alpha = 255
                }
            }
            
            // Vitesse et titre
            paint.color = Color.BLACK
            paint.textSize = 50f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("⛷️ PREMIÈRE DESCENTE", w/2f, 80f, paint)
            
            paint.textSize = 60f
            paint.color = Color.YELLOW
            canvas.drawText("${speed.toInt()} KM/H", w/2f, h - 60f, paint)
        }
        
        private fun drawSecondDescent(canvas: Canvas, w: Int, h: Int) {
            // Fond plus sombre (plus rapide)
            paint.color = Color.parseColor("#6495ED")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Animation diagonale PLUS RAPIDE
            val progress = phaseTimer / secondDescentDuration
            val startX = -200f
            val startY = h * 0.1f
            val endX = w + 200f
            val endY = h * 0.9f
            
            val bobX = startX + (endX - startX) * progress
            val bobY = startY + (endY - startY) * progress
            val scale = 0.22f  // Légèrement plus gros
            
            bobFinishLineBitmap?.let { bmp ->
                val dstRect = RectF(
                    bobX - bmp.width * scale / 2f,
                    bobY - bmp.height * scale / 2f,
                    bobX + bmp.width * scale / 2f,
                    bobY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            // Effet de vitesse plus intense
            for (i in 1..8) {
                val trailProgress = progress - (i * 0.05f)
                if (trailProgress > 0f) {
                    val trailX = startX + (endX - startX) * trailProgress
                    val trailY = startY + (endY - startY) * trailProgress
                    
                    paint.color = Color.CYAN
                    paint.alpha = (255 * (1f - i * 0.125f)).toInt()
                    canvas.drawCircle(trailX, trailY, 12f - i * 1.2f, paint)
                    paint.alpha = 255
                }
            }
            
            // Lignes de vitesse
            for (i in 0..10) {
                paint.color = Color.WHITE
                paint.alpha = 150
                paint.strokeWidth = 3f
                val lineY = (i * h / 10f + phaseTimer * speed * 3f) % (h + 100f)
                canvas.drawLine(0f, lineY, w.toFloat(), lineY + 50f, paint)
                paint.alpha = 255
            }
            
            paint.color = Color.BLACK
            paint.textSize = 50f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("⚡ ACCÉLÉRATION MAXIMALE", w/2f, 80f, paint)
            
            paint.textSize = 70f
            paint.color = Color.RED
            canvas.drawText("${speed.toInt()} KM/H", w/2f, h - 60f, paint)
        }
        
        private fun drawControlDescent(canvas: Canvas, w: Int, h: Int) {
            // CARTE À GAUCHE (65% de l'écran)
            val mapRect = RectF(0f, 0f, w * 0.65f, h.toFloat())
            
            // Fond de carte
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(mapRect, paint)
            
            // Dessiner la piste de bobsleigh (serpentine)
            paint.color = Color.parseColor("#CCCCCC")
            paint.strokeWidth = 8f
            paint.style = Paint.Style.STROKE
            
            val trackPath = Path()
            val startX = mapRect.width() * 0.5f
            val startY = 50f
            trackPath.moveTo(startX, startY)
            
            // Piste serpentine avec courbes
            for (i in 1..20) {
                val y = startY + i * (mapRect.height() - 100f) / 20f
                val wiggle = sin(i * 0.8f) * mapRect.width() * 0.2f
                val x = startX + wiggle
                trackPath.lineTo(x, y)
            }
            canvas.drawPath(trackPath, paint)
            paint.style = Paint.Style.FILL
            
            // Point rouge montrant la position actuelle
            val currentY = 50f + trackProgress * (mapRect.height() - 100f)
            val currentWiggle = sin(trackProgress * 16f) * mapRect.width() * 0.2f
            val currentX = startX + currentWiggle
            
            paint.color = Color.RED
            canvas.drawCircle(currentX, currentY, 12f, paint)
            
            // Ligne d'arrivée
            paint.color = Color.parseColor("#FFD700")
            paint.strokeWidth = 6f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(mapRect.left + 50f, mapRect.bottom - 50f, mapRect.right - 50f, mapRect.bottom - 50f, paint)
            paint.style = Paint.Style.FILL
            
            // Titre de la carte
            paint.color = Color.BLACK
            paint.textSize = 25f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("CARTE DU PARCOURS", mapRect.centerX(), 30f, paint)
            
            // TUNNEL IMMERSIF À DROITE (35% de l'écran)
            val tunnelRect = RectF(w * 0.65f, 0f, w.toFloat(), h.toFloat())
            drawImmersiveTunnel(canvas, tunnelRect)
        }
        
        private fun drawImmersiveTunnel(canvas: Canvas, tunnelRect: RectF) {
            // Fond noir profond
            paint.color = Color.parseColor("#000000")
            canvas.drawRect(tunnelRect, paint)
            
            // TUNNEL CYLINDRIQUE ULTRA RÉALISTE
            val scrollSpeed = speed * 2.5f
            val tunnelOffset = (phaseTimer * scrollSpeed) % 60f
            val centerX = tunnelRect.centerX() + (tiltZ * tunnelRect.width() * 0.15f)
            
            // Générer segments de tunnel avec perspective parfaite
            for (i in 0..8) {
                val depth = (i * 60f - tunnelOffset) / tunnelRect.height()
                val segmentY = tunnelRect.top + depth * tunnelRect.height()
                
                if (segmentY > tunnelRect.bottom || depth < 0f) continue
                
                // Perspective exponentielle pour plus de réalisme
                val perspective = 1f / (1f + depth * 2f)
                
                // Virage selon la direction avec anticipation
                val turnInfluence = idealDirection * depth * tunnelRect.width() * 0.25f
                val segmentCenterX = centerX + turnInfluence
                
                // Rayon du tunnel selon perspective
                val tunnelRadius = tunnelRect.width() * perspective * 0.4f
                val segmentHeight = 50f * perspective
                
                // VOÛTE DU TUNNEL (demi-cercle supérieur)
                paint.color = Color.parseColor("#444444")
                paint.style = Paint.Style.FILL
                
                val vaultRect = RectF(
                    segmentCenterX - tunnelRadius,
                    segmentY - tunnelRadius * 0.7f,
                    segmentCenterX + tunnelRadius,
                    segmentY + tunnelRadius * 0.3f
                )
                canvas.drawArc(vaultRect, 180f, 180f, true, paint)
                
                // REFLETS SUR LA VOÛTE
                if (perspective > 0.3f) {
                    paint.color = Color.parseColor("#666666")
                    paint.alpha = (200 * perspective).toInt()
                    val reflectRect = RectF(
                        segmentCenterX - tunnelRadius * 0.4f,
                        segmentY - tunnelRadius * 0.6f,
                        segmentCenterX + tunnelRadius * 0.4f,
                        segmentY - tunnelRadius * 0.2f
                    )
                    canvas.drawArc(reflectRect, 180f, 180f, true, paint)
                    paint.alpha = 255
                }
                
                // SOL DU TUNNEL (trapèze perspectif)
                val nextDepth = ((i + 1) * 60f - tunnelOffset) / tunnelRect.height()
                val nextPerspective = 1f / (1f + nextDepth * 2f)
                val nextRadius = tunnelRect.width() * nextPerspective * 0.4f
                val nextY = tunnelRect.top + nextDepth * tunnelRect.height()
                val nextCenterX = centerX + idealDirection * nextDepth * tunnelRect.width() * 0.25f
                
                paint.color = Color.parseColor("#FFFFFF")
                val floorPath = Path().apply {
                    moveTo(segmentCenterX - tunnelRadius * 0.9f, segmentY)
                    lineTo(segmentCenterX + tunnelRadius * 0.9f, segmentY)
                    if (nextDepth <= 3f && nextY <= tunnelRect.bottom) {
                        lineTo(nextCenterX + nextRadius * 0.9f, nextY)
                        lineTo(nextCenterX - nextRadius * 0.9f, nextY)
                    } else {
                        lineTo(segmentCenterX + tunnelRadius * 0.7f, segmentY + segmentHeight)
                        lineTo(segmentCenterX - tunnelRadius * 0.7f, segmentY + segmentHeight)
                    }
                    close()
                }
                canvas.drawPath(floorPath, paint)
                
                // LIGNES DE GUIDAGE (effet de vitesse)
                if (i % 2 == 0 && perspective > 0.25f) {
                    paint.color = Color.parseColor("#E0E0E0")
                    paint.strokeWidth = 4f * perspective
                    paint.style = Paint.Style.STROKE
                    canvas.drawLine(
                        segmentCenterX - tunnelRadius * 0.15f,
                        segmentY,
                        segmentCenterX + tunnelRadius * 0.15f,
                        segmentY,
                        paint
                    )
                    paint.style = Paint.Style.FILL
                }
                
                // ÉCLAIRAGE DYNAMIQUE
                if (perspective > 0.4f) {
                    paint.color = Color.parseColor("#FFFFFF")
                    paint.alpha = (100 * perspective).toInt()
                    canvas.drawCircle(segmentCenterX, segmentY - tunnelRadius * 0.3f, tunnelRadius * 0.1f, paint)
                    paint.alpha = 255
                }
            }
            
            // EFFET DE PARTICULES DE GLACE
            for (i in 0..15) {
                val particleX = tunnelRect.centerX() + kotlin.random.Random.nextFloat() * tunnelRect.width() - tunnelRect.width()/2f
                val particleY = tunnelRect.top + (phaseTimer * speed * 4f + i * 30f) % tunnelRect.height()
                val particleSize = 2f + kotlin.random.Random.nextFloat() * 3f
                
                paint.color = Color.parseColor("#E0F6FF")
                paint.alpha = 180
                canvas.drawCircle(particleX, particleY, particleSize, paint)
                paint.alpha = 255
            }
            
            // Interface cockpit overlay
            bobCockpitBitmap?.let { bmp ->
                val cockpitScale = 0.35f
                val cockpitImageRect = RectF(
                    tunnelRect.left + 10f,
                    tunnelRect.bottom - (bmp.height * cockpitScale) - 10f,
                    tunnelRect.left + 10f + (bmp.width * cockpitScale),
                    tunnelRect.bottom - 10f
                )
                canvas.drawBitmap(bmp, null, cockpitImageRect, paint)
            }
            
            // Indicateurs de contrôle
            paint.color = Color.YELLOW
            paint.textSize = 40f
            paint.textAlign = Paint.Align.CENTER
            
            val directionArrow = when {
                idealDirection < -0.3f -> "⬅️"
                idealDirection > 0.3f -> "➡️" 
                else -> "⬆️"
            }
            canvas.drawText(directionArrow, tunnelRect.centerX(), tunnelRect.top + 50f, paint)
            
            // Vitesse
            paint.color = Color.WHITE
            paint.textSize = 25f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${speed.toInt()}", tunnelRect.right - 15f, tunnelRect.bottom - 40f, paint)
            canvas.drawText("KM/H", tunnelRect.right - 15f, tunnelRect.bottom - 15f, paint)
            
            // Performance
            paint.textSize = 20f
            paint.color = if (steeringAccuracy > 0.8f) Color.GREEN else if (steeringAccuracy > 0.5f) Color.YELLOW else Color.RED
            canvas.drawText("${(steeringAccuracy * 100).toInt()}%", tunnelRect.right - 15f, tunnelRect.bottom - 65f, paint)
        }
        
        private fun drawFinishLine(canvas: Canvas, w: Int, h: Int) {
            // Même layout que control descent mais avec ligne d'arrivée qui passe
            val mapRect = RectF(0f, 0f, w * 0.65f, h.toFloat())
            val tunnelRect = RectF(w * 0.65f, 0f, w.toFloat(), h.toFloat())
            
            // Carte à gauche (simplifiée)
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(mapRect, paint)
            
            paint.color = Color.parseColor("#FFD700")
            paint.textSize = 40f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🏁 LIGNE D'ARRIVÉE!", mapRect.centerX(), mapRect.centerY(), paint)
            
            // Tunnel à droite avec ligne d'arrivée qui passe
            paint.color = Color.parseColor("#000000")
            canvas.drawRect(tunnelRect, paint)
            
            // Ligne d'arrivée qui descend depuis le haut
            val lineProgress = phaseTimer / finishLineDuration
            val lineY = tunnelRect.top - 100f + lineProgress * (tunnelRect.height() + 200f)
            
            // Damier de la ligne d'arrivée
            for (i in 0..10) {
                val color = if (i % 2 == 0) Color.BLACK else Color.WHITE
                paint.color = color
                val segmentWidth = tunnelRect.width() / 10f
                canvas.drawRect(
                    tunnelRect.left + i * segmentWidth,
                    lineY - 30f,
                    tunnelRect.left + (i + 1) * segmentWidth,
                    lineY + 30f,
                    paint
                )
            }
            
            paint.color = Color.YELLOW
            paint.textSize = 50f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🏁", tunnelRect.centerX(), tunnelRect.centerY(), paint)
        }
        
        private fun drawCelebration(canvas: Canvas, w: Int, h: Int) {
            // Fond doré de célébration
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Animation de célébration : haut-gauche vers centre puis glisse
            val totalProgress = phaseTimer / celebrationDuration
            val descentProgress = (totalProgress * 2f).coerceAtMost(1f)  // Première moitié : descente
            val slideProgress = maxOf(0f, totalProgress * 2f - 1f)       // Deuxième moitié : glissade
            
            val startX = -150f
            val startY = h * 0.1f
            val centerX = w * 0.5f
            val centerY = h * 0.6f
            val endX = w * 0.8f
            
            var bobX: Float
            var bobY: Float
            
            if (descentProgress < 1f) {
                // Phase de descente vers le centre
                bobX = startX + (centerX - startX) * descentProgress
                bobY = startY + (centerY - startY) * descentProgress
            } else {
                // Phase de glissade lente
                bobX = centerX + (endX - centerX) * slideProgress
                bobY = centerY
            }
            
            val scale = 0.25f
            
            bobCelebrationBitmap?.let { bmp ->
                val dstRect = RectF(
                    bobX - bmp.width * scale / 2f,
                    bobY - bmp.height * scale / 2f,
                    bobX + bmp.width * scale / 2f,
                    bobY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            // Effets de célébration
            paint.color = Color.WHITE
            paint.textSize = 60f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🎉 FÉLICITATIONS! 🎉", w/2f, h * 0.3f, paint)
            
            paint.color = Color.parseColor("#FF6600")
            paint.textSize = 80f
            canvas.drawText("${speed.toInt()} KM/H", w/2f, h * 0.85f, paint)
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            // Fond doré
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
            
            // Détails de performance
            paint.color = Color.parseColor("#001122")
            paint.textSize = 28f
            canvas.drawText("🚀 Poussée: ${(pushQuality * 100).toInt()}%", w/2f, h * 0.45f, paint)
            canvas.drawText("🎮 Contrôle: ${(controlPerformance * 100).toInt()}%", w/2f, h * 0.5f, paint)
            canvas.drawText("⚡ Vitesse max: ${speed.toInt()} km/h", w/2f, h * 0.55f, paint)
            canvas.drawText("🕒 Temps: ${raceTime.toInt()}s", w/2f, h * 0.6f, paint)
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
            canvas.drawText("PUISSANCE: ${pushPower.toInt()}%", w/2f, h - 130f, paint)
        }
        
        private fun drawIceParticles(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#CCEEEE")
            paint.alpha = 200
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
        PREPARATION, PUSH_START, FIRST_DESCENT, SECOND_DESCENT, CONTROL_DESCENT, FINISH_LINE, CELEBRATION, RESULTS, FINISHED
    }
}
