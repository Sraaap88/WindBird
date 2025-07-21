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
    private val descent1Duration = 15f      // Animation automatique
    private val cockpit1Duration = 12f      // Contrôle joueur
    private val descent2Duration = 12f      // Animation automatique
    private val cockpit2Duration = 12f      // Contrôle joueur
    private val finalDescentDuration = 8f   // Animation finale
    private val finishDuration = 5f
    private val resultsDuration = 8f
    
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
    private var cockpitPerformance1 = 0f
    private var cockpitPerformance2 = 0f
    private var steeringAccuracy = 1f
    private var idealDirection = 0f
    
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
        cockpitPerformance1 = 0f
        cockpitPerformance2 = 0f
        steeringAccuracy = 1f
        idealDirection = 0f
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
            GameState.DESCENT_1 -> handleDescent1()
            GameState.COCKPIT_1 -> handleCockpit1()
            GameState.DESCENT_2 -> handleDescent2()
            GameState.COCKPIT_2 -> handleCockpit2()
            GameState.FINAL_DESCENT -> handleFinalDescent()
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
            speed = 25f + (pushQuality * 35f)
            
            gameState = GameState.DESCENT_1
            phaseTimer = 0f
            cameraShake = 0.3f
        }
    }
    
    private fun handleDescent1() {
        // Animation automatique basée sur la qualité de poussée
        updateDescentAnimation(pushQuality)
        
        if (phaseTimer >= descent1Duration) {
            gameState = GameState.COCKPIT_1
            phaseTimer = 0f
            generateNewTurn()
        }
    }
    
    private fun handleCockpit1() {
        // Contrôle cockpit - rotation du téléphone comme volant
        updateCockpitControl()
        
        // Accumulation de performance
        val directionError = abs(tiltZ - idealDirection)
        val currentAccuracy = 1f - (directionError / 2f).coerceIn(0f, 1f)
        cockpitPerformance1 = (cockpitPerformance1 * 0.95f + currentAccuracy * 0.05f)
        
        if (phaseTimer >= cockpit1Duration) {
            // Ajuster vitesse selon performance cockpit
            speed += (cockpitPerformance1 - 0.5f) * 20f
            speed = speed.coerceIn(20f, maxSpeed)
            
            gameState = GameState.DESCENT_2
            phaseTimer = 0f
        }
    }
    
    private fun handleDescent2() {
        // Animation automatique basée sur performance cockpit 1
        updateDescentAnimation(cockpitPerformance1)
        
        if (phaseTimer >= descent2Duration) {
            gameState = GameState.COCKPIT_2
            phaseTimer = 0f
            generateNewTurn()
        }
    }
    
    private fun handleCockpit2() {
        // Deuxième phase de contrôle cockpit
        updateCockpitControl()
        
        val directionError = abs(tiltZ - idealDirection)
        val currentAccuracy = 1f - (directionError / 2f).coerceIn(0f, 1f)
        cockpitPerformance2 = (cockpitPerformance2 * 0.95f + currentAccuracy * 0.05f)
        
        if (phaseTimer >= cockpit2Duration) {
            // Ajuster vitesse finale
            speed += (cockpitPerformance2 - 0.5f) * 25f
            speed = speed.coerceIn(20f, maxSpeed)
            
            gameState = GameState.FINAL_DESCENT
            phaseTimer = 0f
        }
    }
    
    private fun handleFinalDescent() {
        // Descente finale vers la ligne d'arrivée
        updateDescentAnimation(cockpitPerformance2)
        
        if (phaseTimer >= finalDescentDuration) {
            gameState = GameState.FINISH
            phaseTimer = 0f
            cameraShake = 1f
        }
    }
    
    private fun handleFinish() {
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
    
    private fun updateDescentAnimation(performanceMultiplier: Float) {
        // Gestion des virages avec vitesse variable
        turnTimer += 0.025f * (1f + performanceMultiplier)
        if (turnTimer > 3f) {
            generateNewTurn()
            turnTimer = 0f
        }
        
        // Progression selon performance
        distance += speed * 0.06f * (0.5f + performanceMultiplier * 0.5f)
        avgSpeed = (avgSpeed + speed) / 2f
    }
    
    private fun updateCockpitControl() {
        // Génération de direction idéale qui change
        turnTimer += 0.025f
        if (turnTimer > 2f) {
            idealDirection = -1f + kotlin.random.Random.nextFloat() * 2f
            turnTimer = 0f
        }
        
        // Calcul de précision du steering
        val directionError = abs(tiltZ - idealDirection)
        steeringAccuracy = 1f - (directionError / 2f).coerceIn(0f, 1f)
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
            val timeBonus = maxOf(0, 200 - raceTime.toInt())
            val speedBonus = (avgSpeed / maxSpeed * 100).toInt()
            val pushBonus = (pushQuality * 50).toInt()
            val cockpit1Bonus = (cockpitPerformance1 * 30).toInt()
            val cockpit2Bonus = (cockpitPerformance2 * 40).toInt()
            val wallPenalty = wallHits * 15
            
            finalScore = maxOf(50, timeBonus + speedBonus + pushBonus + cockpit1Bonus + cockpit2Bonus - wallPenalty)
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
            GameState.DESCENT_1 -> "🛷 ${tournamentData.playerNames[currentPlayerIndex]} | Première descente: ${speed.toInt()} km/h"
            GameState.COCKPIT_1 -> "🎮 ${tournamentData.playerNames[currentPlayerIndex]} | Cockpit 1: Précision ${(cockpitPerformance1 * 100).toInt()}%"
            GameState.DESCENT_2 -> "🛷 ${tournamentData.playerNames[currentPlayerIndex]} | Deuxième descente: ${speed.toInt()} km/h"
            GameState.COCKPIT_2 -> "🎮 ${tournamentData.playerNames[currentPlayerIndex]} | Cockpit 2: Précision ${(cockpitPerformance2 * 100).toInt()}%"
            GameState.FINAL_DESCENT -> "⚡ ${tournamentData.playerNames[currentPlayerIndex]} | Descente finale: ${speed.toInt()} km/h"
            GameState.FINISH -> "🏁 ${tournamentData.playerNames[currentPlayerIndex]} | Ligne d'arrivée!"
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
                GameState.DESCENT_1 -> drawDescent(canvas, w, h, "PREMIÈRE DESCENTE")
                GameState.COCKPIT_1 -> drawCockpit(canvas, w, h, "COCKPIT 1")
                GameState.DESCENT_2 -> drawDescent(canvas, w, h, "DEUXIÈME DESCENTE")
                GameState.COCKPIT_2 -> drawCockpit(canvas, w, h, "COCKPIT 2")
                GameState.FINAL_DESCENT -> drawDescent(canvas, w, h, "DESCENTE FINALE")
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
            
            // Bobsleigh qui avance selon la poussée (TAILLE RÉDUITE)
            val pushProgress = pushPower / 100f
            val bobX = w * 0.1f + pushProgress * w * 0.6f
            val bobY = h * 0.775f
            val scale = 0.15f  // RÉDUIT de 0.8f à 0.15f
            
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
        
        private fun drawDescent(canvas: Canvas, w: Int, h: Int, title: String) {
            // Interface comme dans l'image de référence
            // Piste à gauche utilisant bobsled_preparation comme fond
            val trackRect = RectF(0f, 0f, w * 0.65f, h.toFloat())
            
            bobsledPreparationBitmap?.let { bmp ->
                canvas.drawBitmap(bmp, null, trackRect, paint)
            } ?: run {
                paint.color = Color.parseColor("#334455")
                canvas.drawRect(trackRect, paint)
            }
            
            // Piste qui défile selon la vitesse
            val speedMultiplier = (speed / maxSpeed).coerceIn(0.3f, 2f)
            val scrollOffset = (phaseTimer * 30f * speedMultiplier) % 100f
            
            // Piste améliorée avec perspective
            paint.color = Color.parseColor("#E0F6FF")
            paint.alpha = 200
            for (i in -2..15) {
                val segmentY = i * 50f - scrollOffset
                val segmentWidth = 150f + sin((segmentY + scrollOffset) * 0.02f) * currentTurn * 40f
                val segmentX = trackRect.width()/2f
                canvas.drawRect(segmentX - segmentWidth/2f, segmentY, segmentX + segmentWidth/2f, segmentY + 40f, paint)
            }
            paint.alpha = 255
            
            // Bobsleigh sur la piste (petit)
            val bobX = trackRect.width()/2f
            val bobY = h * 0.6f
            val scale = 0.08f
            
            val currentBitmap = when {
                currentTurn < -0.3f -> bobLeftBitmap
                currentTurn > 0.3f -> bobRightBitmap
                else -> bobDownBitmap
            }
            
            currentBitmap?.let { bmp ->
                val dstRect = RectF(
                    bobX - bmp.width * scale / 2f,
                    bobY - bmp.height * scale / 2f,
                    bobX + bmp.width * scale / 2f,
                    bobY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            // Panneau de contrôle à droite
            val controlRect = RectF(w * 0.65f, 0f, w.toFloat(), h * 0.6f)
            paint.color = Color.parseColor("#001122")
            canvas.drawRect(controlRect, paint)
            
            // Image cockpit en haut à droite
            bobCockpitBitmap?.let { bmp ->
                val cockpitRect = RectF(
                    w * 0.67f,
                    20f,
                    w * 0.98f,
                    h * 0.35f
                )
                canvas.drawBitmap(bmp, null, cockpitRect, paint)
            }
            
            // Indicateur de direction sous le cockpit
            val directionY = h * 0.4f
            val directionX = w * 0.825f
            
            // Flèche indiquant la direction à prendre
            paint.color = Color.YELLOW
            paint.textSize = 60f
            paint.textAlign = Paint.Align.CENTER
            val directionArrow = when {
                currentTurn < -0.3f -> "⬅️"
                currentTurn > 0.3f -> "➡️"
                else -> "⬆️"
            }
            canvas.drawText(directionArrow, directionX, directionY, paint)
            
            // Titre en bas
            paint.color = Color.WHITE
            paint.textSize = 40f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(title, w/2f, h - 100f, paint)
            
            // Vitesse en gros en bas
            paint.textSize = 60f
            paint.color = Color.YELLOW
            canvas.drawText("${speed.toInt()} KM/H", w/2f, h - 40f, paint)
        }
        
        private fun drawCockpit(canvas: Canvas, w: Int, h: Int, title: String) {
            // Interface similaire à la descente : piste à gauche, cockpit à droite
            // Piste à gauche utilisant bobsled_preparation comme fond
            val trackRect = RectF(0f, 0f, w * 0.65f, h.toFloat())
            
            bobsledPreparationBitmap?.let { bmp ->
                canvas.drawBitmap(bmp, null, trackRect, paint)
            } ?: run {
                paint.color = Color.parseColor("#334455")
                canvas.drawRect(trackRect, paint)
            }
            
            // Piste qui défile avec contrôle plus précis
            val pipeScrollSpeed = (1f + steeringAccuracy) * 8f
            val pipeOffset = (phaseTimer * pipeScrollSpeed) % 80f
            
            paint.color = Color.parseColor("#E0F6FF")
            paint.alpha = 180
            for (i in -3..12) {
                val pipeY = i * 60f - pipeOffset
                val pipeX = trackRect.width()/2f + sin((pipeY + pipeOffset) * 0.03f) * idealDirection * 60f
                canvas.drawRect(pipeX - 30f, pipeY, pipeX + 30f, pipeY + 50f, paint)
            }
            paint.alpha = 255
            
            // Bobsleigh sur la piste
            val bobX = trackRect.width()/2f
            val bobY = h * 0.6f
            val scale = 0.08f
            
            bobCockpitBitmap?.let { bmp ->
                val dstRect = RectF(
                    bobX - bmp.width * scale / 2f,
                    bobY - bmp.height * scale / 2f,
                    bobX + bmp.width * scale / 2f,
                    bobY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            // ZONE COCKPIT À DROITE
            val cockpitRect = RectF(w * 0.65f, 0f, w.toFloat(), h.toFloat())
            
            // D'abord dessiner la vue première personne DERRIÈRE
            canvas.save()
            canvas.clipRect(cockpitRect)
            
            // VUE PREMIÈRE PERSONNE dans la zone cockpit
            val cockpitW = cockpitRect.width().toInt()
            val cockpitH = cockpitRect.height().toInt()
            val offsetX = cockpitRect.left
            val offsetY = cockpitRect.top
            
            // Ciel nuageux qui défile (partie haute)
            val skyHeight = cockpitH * 0.35f
            paint.color = Color.parseColor("#87CEEB")
            canvas.drawRect(offsetX, offsetY, offsetX + cockpitW, offsetY + skyHeight, paint)
            
            // Nuages qui défilent selon la vitesse
            val cloudScrollSpeed = speed * 0.1f
            val cloudOffset = (phaseTimer * cloudScrollSpeed) % (cockpitW * 1.5f)
            
            paint.color = Color.WHITE
            paint.alpha = 180
            for (i in 0..3) {
                val cloudX = offsetX + i * (cockpitW / 2f) - cloudOffset
                val cloudY = offsetY + skyHeight * (0.2f + i * 0.2f)
                canvas.drawCircle(cloudX, cloudY, 25f + i * 5f, paint)
                canvas.drawCircle(cloudX + 20f, cloudY, 20f + i * 4f, paint)
            }
            paint.alpha = 255
            
            // PISTE EN PERSPECTIVE qui défile vers nous
            val trackStartY = offsetY + skyHeight
            val scrollSpeed = speed * 2f
            val trackOffset = (phaseTimer * scrollSpeed) % 40f
            
            // Position horizontale basée sur la rotation du téléphone
            val steeringOffset = tiltZ * cockpitW * 0.3f
            val centerX = offsetX + cockpitW / 2f + steeringOffset
            
            // Dessiner la piste segment par segment (effet tunnel)
            for (i in 0..12) {
                val segmentProgress = (i * 40f - trackOffset) / (cockpitH - skyHeight)
                val segmentY = trackStartY + segmentProgress * (cockpitH - skyHeight)
                
                if (segmentY > offsetY + cockpitH) continue
                
                // Perspective : plus c'est loin, plus c'est étroit
                val perspective = 1f - (segmentProgress * 0.8f).coerceAtMost(0.9f)
                val segmentWidth = cockpitW * perspective * 0.6f
                
                // Virage selon idealDirection et distance
                val turnInfluence = idealDirection * perspective * cockpitW * 0.2f
                val segmentCenterX = centerX + turnInfluence
                
                // Murs de la piste (gris)
                paint.color = Color.parseColor("#CCCCCC")
                if (perspective > 0.1f) {
                    // Mur gauche
                    canvas.drawRect(
                        segmentCenterX - segmentWidth * 0.7f,
                        segmentY,
                        segmentCenterX - segmentWidth * 0.5f,
                        segmentY + 40f * perspective,
                        paint
                    )
                    // Mur droit
                    canvas.drawRect(
                        segmentCenterX + segmentWidth * 0.5f,
                        segmentY,
                        segmentCenterX + segmentWidth * 0.7f,
                        segmentY + 40f * perspective,
                        paint
                    )
                }
                
                // Piste centrale (blanche/glace)
                paint.color = Color.parseColor("#E0F6FF")
                if (perspective > 0.1f) {
                    canvas.drawRect(
                        segmentCenterX - segmentWidth * 0.5f,
                        segmentY,
                        segmentCenterX + segmentWidth * 0.5f,
                        segmentY + 40f * perspective,
                        paint
                    )
                }
            }
            
            canvas.restore()
            
            // MAINTENANT dessiner votre image cockpit PAR-DESSUS
            bobCockpitBitmap?.let { bmp ->
                canvas.drawBitmap(bmp, null, cockpitRect, paint)
            }
            
            // Interface minimaliste par-dessus l'image cockpit
            // Vitesse en bas à droite
            paint.color = Color.YELLOW
            paint.textSize = 35f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${speed.toInt()}", cockpitRect.centerX(), cockpitRect.bottom - 60f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 18f
            canvas.drawText("KM/H", cockpitRect.centerX(), cockpitRect.bottom - 35f, paint)
            
            // Indicateur de direction
            paint.textSize = 40f
            paint.color = Color.YELLOW
            paint.textAlign = Paint.Align.CENTER
            val directionArrow = when {
                idealDirection < -0.3f -> "⬅️"
                idealDirection > 0.3f -> "➡️" 
                else -> "⬆️"
            }
            canvas.drawText(directionArrow, cockpitRect.centerX(), cockpitRect.top + 50f, paint)
            
            // Titre en bas
            paint.color = Color.WHITE
            paint.textSize = 40f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(title, w/2f, h - 100f, paint)
            
            paint.textSize = 60f
            paint.color = Color.YELLOW
            canvas.drawText("${speed.toInt()} KM/H", w/2f, h - 40f, paint)
        }
        
        private fun drawFinish(canvas: Canvas, w: Int, h: Int) {
            // Fond ciel
            paint.color = Color.parseColor("#87CEEB")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Ligne d'arrivée en damier
            for (i in 0..20) {
                val color = if (i % 2 == 0) Color.BLACK else Color.WHITE
                paint.color = color
                canvas.drawRect(i * (w / 20f), h * 0.4f, (i + 1) * (w / 20f), h * 0.6f, paint)
            }
            
            // Bobsleigh qui traverse (TAILLE RÉDUITE)
            val finishProgress = phaseTimer / finishDuration
            val bobX = w * (-0.1f + finishProgress * 1.2f)
            val bobY = h * 0.7f
            val scale = 0.2f  // RÉDUIT
            
            bobFinishLineBitmap?.let { bmp ->
                val dstRect = RectF(
                    bobX - bmp.width * scale / 2f,
                    bobY - bmp.height * scale / 2f,
                    bobX + bmp.width * scale / 2f,
                    bobY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            paint.color = Color.BLACK
            paint.textSize = 60f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🏁 LIGNE D'ARRIVÉE! 🏁", w/2f, h * 0.15f, paint)
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            // Fond doré
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
            
            // Bobsleigh de célébration (TAILLE RÉDUITE)
            val celebrationProgress = phaseTimer / resultsDuration
            val bobX = w * (-0.1f + celebrationProgress * 1.2f)
            val bobY = h * 0.75f
            val scale = 0.18f  // RÉDUIT
            
            bobCelebrationBitmap?.let { bmp ->
                val dstRect = RectF(
                    bobX - bmp.width * scale / 2f,
                    bobY - bmp.height * scale / 2f,
                    bobX + bmp.width * scale / 2f,
                    bobY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
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
            canvas.drawText("🎮 Cockpit 1: ${(cockpitPerformance1 * 100).toInt()}%", w/2f, h * 0.5f, paint)
            canvas.drawText("🎮 Cockpit 2: ${(cockpitPerformance2 * 100).toInt()}%", w/2f, h * 0.55f, paint)
            canvas.drawText("⚡ Vitesse moy: ${avgSpeed.toInt()} km/h", w/2f, h * 0.6f, paint)
            canvas.drawText("🕒 Temps: ${raceTime.toInt()}s", w/2f, h * 0.65f, paint)
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
        PREPARATION, PUSH_START, DESCENT_1, COCKPIT_1, DESCENT_2, COCKPIT_2, FINAL_DESCENT, FINISH, RESULTS, FINISHED
    }
}
