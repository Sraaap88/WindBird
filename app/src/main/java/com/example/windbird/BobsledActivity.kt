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
import android.view.MotionEvent
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

    // Structure de jeu SIMPLIFIÉE
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Durées ajustées
    private val preparationDuration = 6f
    private val pushStartDuration = 8f
    private val controlDescentDuration = 25f
    private val finishLineDuration = 4f
    private val celebrationDuration = 8f
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
        // Plus de secouage gyroscope - maintenant c'est tactile uniquement
        pushPower = pushPower.coerceAtMost(100f)
        pushRhythm = pushRhythm.coerceAtMost(100f)
        
        if (phaseTimer >= pushStartDuration) {
            pushQuality = (pushPower * 0.6f + pushRhythm * 0.4f) / 100f
            speed = 45f + (pushQuality * 35f)
            
            gameState = GameState.CONTROL_DESCENT
            phaseTimer = 0f
            cameraShake = 0.5f
            generateNewTurn()
        }
    }
    
    private fun handleControlDescent() {
        updateControlPhase()
        
        val accelerationBonus = steeringAccuracy * 0.8f
        speed += (0.3f + accelerationBonus)
        maxSpeed = 180f
        speed = speed.coerceAtMost(maxSpeed)
        
        trackProgress += 0.025f / controlDescentDuration
        trackProgress = trackProgress.coerceAtMost(1f)
        
        if (phaseTimer >= controlDescentDuration) {
            gameState = GameState.FINISH_LINE
            phaseTimer = 0f
            cameraShake = 0.8f
        }
    }
    
    private fun handleFinishLine() {
        if (phaseTimer >= finishLineDuration) {
            gameState = GameState.CELEBRATION
            phaseTimer = 0f
            speed *= 0.8f
        }
    }
    
    private fun handleCelebration() {
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
        turnTimer += 0.025f
        if (turnTimer > 2.5f) {
            idealDirection = -1f + kotlin.random.Random.nextFloat() * 2f
            turnTimer = 0f
        }
        
        val directionError = abs(tiltZ - idealDirection)
        steeringAccuracy = 1f - (directionError / 2f).coerceIn(0f, 1f)
        
        controlPerformance = (controlPerformance * 0.98f + steeringAccuracy * 0.02f)
        
        if (steeringAccuracy < 0.5f) {
            speed = maxOf(speed * 0.995f, 40f)
        }
    }
    
    private fun generateNewTurn() {
        currentTurn = -1f + kotlin.random.Random.nextFloat() * 2f
        turnIntensity = 0.4f + kotlin.random.Random.nextFloat() * 0.6f
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
            GameState.PUSH_START -> "🚀 ${tournamentData.playerNames[currentPlayerIndex]} | Poussée: ${pushCount} (${(pushQuality * 100).toInt()}%) | ${(pushStartDuration - phaseTimer).toInt() + 1}s"
            GameState.CONTROL_DESCENT -> "🎮 ${tournamentData.playerNames[currentPlayerIndex]} | Contrôle: ${(steeringAccuracy * 100).toInt()}% | ${speed.toInt()} km/h"
            GameState.FINISH_LINE -> "🏁 ${tournamentData.playerNames[currentPlayerIndex]} | Ligne d'arrivée: ${speed.toInt()} km/h!"
            GameState.CELEBRATION -> "🎉 ${tournamentData.playerNames[currentPlayerIndex]} | Vitesse finale: ${speed.toInt()} km/h!"
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
            bobsledPreparationBitmap = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888)
            val canvas1 = Canvas(bobsledPreparationBitmap!!)
            val tempPaint = Paint().apply {
                color = Color.parseColor("#FF4444")
                style = Paint.Style.FILL
            }
            canvas1.drawRoundRect(20f, 40f, 180f, 80f, 15f, 15f, tempPaint)
            
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

        // GESTION DU TACTILE POUR LA POUSSÉE
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (gameState == GameState.PUSH_START) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        val pushProgress = (pushPower / 100f).coerceIn(0f, 1f)
                        val bobX = 120f + pushProgress * (width - 200f)
                        val bobY = height * 0.7f
                        
                        // Vérifier si on touche dans la zone du bobsleigh
                        val touchX = event.x
                        val touchY = event.y
                        
                        if (touchX >= bobX - 60f && touchX <= bobX + 60f && 
                            touchY >= bobY - 40f && touchY <= bobY + 40f) {
                            
                            // POUSSER ! Ajouter de la puissance
                            pushPower += 3f
                            pushCount++
                            
                            // Calcul du rythme selon la fréquence des touchés
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastPushTime > 100 && currentTime - lastPushTime < 500) {
                                pushRhythm += 4f // Bon rythme
                            } else {
                                pushRhythm += 2f // Rythme moyen
                            }
                            lastPushTime = currentTime
                            
                            pushPower = pushPower.coerceAtMost(100f)
                            pushRhythm = pushRhythm.coerceAtMost(100f)
                            
                            invalidate()
                            return true
                        }
                    }
                }
            }
            return super.onTouchEvent(event)
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
                GameState.CONTROL_DESCENT -> drawFullScreenTunnel(canvas, w, h)
                GameState.FINISH_LINE -> drawBeautifulFinishLine(canvas, w, h)
                GameState.CELEBRATION -> drawBeautifulCelebration(canvas, w, h)
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
                paint.color = Color.parseColor("#E0F6FF")
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            
            paint.color = Color.WHITE
            val flagRect = RectF(50f, 50f, 300f, 200f)
            canvas.drawRect(flagRect, paint)
            
            paint.color = Color.BLACK
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawRect(flagRect, paint)
            paint.style = Paint.Style.FILL
            
            val playerCountry = tournamentData.playerCountries[currentPlayerIndex]
            val flag = getCountryFlag(playerCountry)
            
            paint.color = Color.BLACK
            paint.textSize = 120f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(flag, flagRect.centerX(), flagRect.centerY() + 40f, paint)
            
            paint.textSize = 28f
            canvas.drawText(playerCountry.uppercase(), flagRect.centerX(), flagRect.bottom + 40f, paint)
            
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
            // POUSSÉE TACTILE AVEC IMAGE DE PRÉPARATION EN ARRIÈRE-PLAN
            
            // Fond avec l'image de poussée ou fallback
            bobPushBitmap?.let { bmp ->
                val dstRect = RectF(0f, 0f, w.toFloat(), h.toFloat())
                canvas.drawBitmap(bmp, null, dstRect, paint)
                
                // Overlay semi-transparent pour voir les éléments par-dessus
                paint.color = Color.argb(100, 255, 255, 255)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            } ?: run {
                // Fond ciel simple comme Winter Games
                paint.color = Color.rgb(150, 200, 255)
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                
                // Montagne en arrière-plan
                paint.color = Color.rgb(100, 100, 100)
                val mountainPath = Path().apply {
                    moveTo(0f, h * 0.4f)
                    lineTo(w * 0.3f, h * 0.2f)
                    lineTo(w * 0.7f, h * 0.25f)
                    lineTo(w.toFloat(), h * 0.35f)
                    lineTo(w.toFloat(), h * 0.4f)
                    close()
                }
                canvas.drawPath(mountainPath, paint)
                
                // Piste de départ droite et simple
                paint.color = Color.WHITE
                val trackY = h * 0.7f
                canvas.drawRect(50f, trackY - 40f, w - 50f, trackY + 40f, paint)
                
                // Bordures de piste
                paint.color = Color.GRAY
                paint.strokeWidth = 4f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(50f, trackY - 40f, w - 50f, trackY - 40f, paint)
                canvas.drawLine(50f, trackY + 40f, w - 50f, trackY + 40f, paint)
                paint.style = Paint.Style.FILL
                
                // Ligne de départ
                paint.color = Color.RED
                paint.strokeWidth = 6f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(100f, trackY - 50f, 100f, trackY + 50f, paint)
                paint.style = Paint.Style.FILL
            }
            
            // BOBSLEIGH qui avance selon les touchés tactiles (par-dessus l'image)
            val pushProgress = (pushPower / 100f).coerceIn(0f, 1f)
            val trackY = h * 0.7f
            val bobX = 120f + pushProgress * (w - 200f)
            val bobY = trackY
            
            // Zone tactile visible (rectangle autour du bobsleigh)
            paint.color = Color.YELLOW
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawRoundRect(bobX - 60f, bobY - 40f, bobX + 60f, bobY + 40f, 10f, 10f, paint)
            paint.style = Paint.Style.FILL
            
            // Bobsleigh simple mais visible par-dessus l'image
            paint.color = Color.RED
            canvas.drawRoundRect(bobX - 30f, bobY - 12f, bobX + 30f, bobY + 12f, 6f, 6f, paint)
            
            // Pilotes qui poussent (points bleus)
            paint.color = Color.BLUE
            canvas.drawCircle(bobX - 50f, bobY - 5f, 8f, paint)
            canvas.drawCircle(bobX - 50f, bobY + 5f, 8f, paint)
            
            // INSTRUCTIONS TACTILES CLAIRES avec fond semi-transparent
            paint.color = Color.argb(180, 0, 0, 0)
            canvas.drawRoundRect(w/2f - 250f, 120f, w/2f + 250f, 180f, 10f, 10f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 32f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("👈 TAPEZ SUR LE BOBSLEIGH POUR LE POUSSER! 👉", w/2f, 155f, paint)
            
            // Barre de puissance avec fond semi-transparent
            paint.color = Color.argb(180, 0, 0, 0)
            canvas.drawRoundRect(w/2f - 160f, h - 110f, w/2f + 160f, h - 40f, 10f, 10f, paint)
            
            paint.color = Color.GRAY
            canvas.drawRect(w/2f - 150f, h - 100f, w/2f + 150f, h - 70f, paint)
            
            paint.color = Color.GREEN
            val powerWidth = (pushPower / 100f) * 300f
            canvas.drawRect(w/2f - 150f, h - 100f, w/2f - 150f + powerWidth, h - 70f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 24f
            canvas.drawText("PUISSANCE: ${pushPower.toInt()}%", w/2f, h - 50f, paint)
            
            // Temps restant avec fond
            paint.color = Color.argb(180, 255, 0, 0)
            canvas.drawRoundRect(w - 120f, 60f, w - 20f, 120f, 10f, 10f, paint)
            
            paint.textSize = 30f
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${(pushStartDuration - phaseTimer).toInt() + 1}s", w - 70f, 100f, paint)
        }

        private fun drawFullScreenTunnel(canvas: Canvas, w: Int, h: Int) {
            // VOTRE SUPERBE CODE DE PISTE 3D !
            val centerX = w / 2f
            val horizonY = h * 0.25f
            
            // 1. CIEL VIOLET + MONTAGNES FAÇON C64
            paint.color = Color.rgb(170, 140, 255) // violet rétro
            canvas.drawRect(0f, 0f, w.toFloat(), horizonY, paint)
            paint.color = Color.rgb(100, 100, 100) // montagne grise
            val mountain = Path().apply {
                moveTo(0f, horizonY)
                lineTo(w * 0.3f, horizonY - 80f)
                lineTo(w * 0.7f, horizonY - 50f)
                lineTo(w.toFloat(), horizonY)
                close()
            }
            canvas.drawPath(mountain, paint)
            
            // 2. PISTE VUE DE L'ARRIÈRE (style Winter Games)
            val segments = 18
            val curve = sin(phaseTimer * 0.7f) * 0.6f  // virages oscillants
            val curveNext = sin(phaseTimer * 0.7f + 0.5f) * 0.6f
            for (i in 0 until segments) {
                val t = i.toFloat() / segments
                val tNext = (i + 1).toFloat() / segments
                val y1 = horizonY + t * (h - horizonY)
                val y2 = horizonY + tNext * (h - horizonY)
                val roadW1 = w * (0.1f + t * 0.8f)
                val roadW2 = w * (0.1f + tNext * 0.8f)
                val curve1 = curve * (1 - t)
                val curve2 = curveNext * (1 - tNext)
                val cx1 = centerX + curve1 * w * 0.4f
                val cx2 = centerX + curve2 * w * 0.4f
                
                // mur gauche
                paint.color = Color.DKGRAY
                val wallL = Path().apply {
                    moveTo(cx1 - roadW1 / 2f - 12f, y1)
                    lineTo(cx1 - roadW1 / 2f, y1)
                    lineTo(cx2 - roadW2 / 2f, y2)
                    lineTo(cx2 - roadW2 / 2f - 12f, y2)
                    close()
                }
                canvas.drawPath(wallL, paint)
                
                // mur droit
                val wallR = Path().apply {
                    moveTo(cx1 + roadW1 / 2f + 12f, y1)
                    lineTo(cx1 + roadW1 / 2f, y1)
                    lineTo(cx2 + roadW2 / 2f, y2)
                    lineTo(cx2 + roadW2 / 2f + 12f, y2)
                    close()
                }
                canvas.drawPath(wallR, paint)
                
                // piste centrale
                paint.color = Color.WHITE
                val track = Path().apply {
                    moveTo(cx1 - roadW1 / 2f, y1)
                    lineTo(cx1 + roadW1 / 2f, y1)
                    lineTo(cx2 + roadW2 / 2f, y2)
                    lineTo(cx2 - roadW2 / 2f, y2)
                    close()
                }
                canvas.drawPath(track, paint)
                
                // ligne décorative
                if (i % 2 == 0) {
                    paint.color = Color.LTGRAY
                    val stripe = Path().apply {
                        moveTo(cx1 - roadW1 * 0.1f, y1)
                        lineTo(cx1 + roadW1 * 0.1f, y1)
                        lineTo(cx2 + roadW2 * 0.1f, y2)
                        lineTo(cx2 - roadW2 * 0.1f, y2)
                        close()
                    }
                    canvas.drawPath(stripe, paint)
                }
            }
            
            // 3. BOBSLEIGH EN VUE ARRIÈRE
            val bobY = h * 0.75f
            val bobScale = 1.2f + sin(phaseTimer * 3f) * 0.05f
            val bobW = 60f * bobScale
            val bobH = 80f * bobScale
            
            // ombre du bob
            paint.color = Color.BLACK
            paint.alpha = 80
            canvas.drawOval(centerX - bobW/2f, bobY + bobH * 0.1f, centerX + bobW/2f, bobY + bobH * 0.3f, paint)
            paint.alpha = 255
            
            // corps du bob
            paint.color = Color.rgb(255, 100, 0)
            canvas.drawRoundRect(centerX - bobW/2f, bobY - bobH/2f, centerX + bobW/2f, bobY + bobH/2f, 20f, 20f, paint)
            
            // têtes des pilotes
            paint.color = Color.YELLOW
            for (i in 0..2) {
                val offset = (i - 1) * 18f
                canvas.drawCircle(centerX + offset, bobY - bobH * 0.3f, 10f, paint)
            }
            
            // 4. VITESSE
            paint.textSize = 36f
            paint.color = Color.BLACK
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("${speed.toInt()} km/h", 30f, 50f, paint)
            
            // Instructions de virage selon l'inclinaison du téléphone
            if (abs(tiltZ) > 0.3f) {
                paint.color = Color.YELLOW
                paint.textSize = 40f
                paint.textAlign = Paint.Align.CENTER
                val direction = if (tiltZ < 0f) "← INCLINEZ GAUCHE" else "INCLINEZ DROITE →"
                canvas.drawText(direction, w/2f, 120f, paint)
            }
        }
        
        private fun drawBeautifulFinishLine(canvas: Canvas, w: Int, h: Int) {
            val lineProgress = phaseTimer / finishLineDuration
            val approachSpeed = speed * 8f
            
            paint.color = Color.rgb(20, 30, 60)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            val centerX = w/2f
            val centerY = h/2f
            
            // Bandes quadrillées qui foncent vers nous
            for (i in 0..8) {
                val bandDistance = (i * 150f - lineProgress * approachSpeed) % (h + 300f)
                val bandY = h - bandDistance
                
                if (bandY > -50f && bandY < h + 50f) {
                    val perspective = (h - bandDistance) / h.toFloat()
                    val bandWidth = w * (0.1f + perspective * 0.9f)
                    val bandHeight = 20f + perspective * 60f
                    
                    val segments = 8
                    val segmentWidth = bandWidth / segments
                    
                    for (j in 0 until segments) {
                        val color = if ((i + j) % 2 == 0) Color.WHITE else Color.BLACK
                        paint.color = color
                        paint.alpha = (255 * perspective.coerceAtMost(1f)).toInt()
                        
                        canvas.drawRect(
                            centerX - bandWidth/2f + j * segmentWidth,
                            bandY - bandHeight/2f,
                            centerX - bandWidth/2f + (j + 1) * segmentWidth,
                            bandY + bandHeight/2f,
                            paint
                        )
                    }
                    paint.alpha = 255
                }
            }
            
            val textScale = 1f + lineProgress * 0.5f
            paint.color = Color.YELLOW
            paint.textSize = 80f * textScale
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🏁", centerX, centerY - 30f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 40f * textScale
            canvas.drawText("FINISH LINE!", centerX, centerY + 60f, paint)
            
            val blinkIntensity = (sin(phaseTimer * 8f) * 0.5f + 0.5f)
            paint.textSize = 60f
            paint.color = Color.argb((255 * blinkIntensity).toInt(), 0, 255, 0)
            canvas.drawText("${speed.toInt()} KM/H", centerX, h - 80f, paint)
        }
        
        private fun drawBeautifulCelebration(canvas: Canvas, w: Int, h: Int) {
            val progress = phaseTimer / celebrationDuration
            
            val bgColor = if (progress < 0.3f) {
                Color.rgb(10, 20, 40)
            } else {
                val transitionProgress = (progress - 0.3f) / 0.7f
                val r = (10 + transitionProgress * 240).toInt().coerceIn(0, 255)
                val g = (20 + transitionProgress * 195).toInt().coerceIn(0, 255) 
                val b = (40 + transitionProgress * 0).toInt().coerceIn(0, 255)
                Color.rgb(r, g, b)
            }
            
            paint.color = bgColor
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            val centerX = w/2f
            val centerY = h/2f
            
            if (progress >= 0.4f) {
                val celebProgress = (progress - 0.4f) / 0.6f
                
                for (i in 0..20) {
                    val angle = (2.0 * PI / 20 * i + celebProgress * 4).toFloat()
                    val radius = celebProgress * 300f
                    val particleX = centerX + cos(angle) * radius
                    val particleY = centerY + sin(angle) * radius
                    
                    paint.color = Color.YELLOW
                    paint.alpha = ((1f - celebProgress) * 255).toInt()
                    canvas.drawCircle(particleX, particleY, 8f, paint)
                }
                paint.alpha = 255
            }
            
            paint.color = Color.WHITE
            paint.textSize = 80f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🎉", centerX, 150f, paint)
            
            paint.textSize = 50f
            canvas.drawText("FÉLICITATIONS!", centerX, 220f, paint)
            
            paint.textSize = 60f
            paint.color = if (speed >= 150f) Color.GREEN else Color.YELLOW
            canvas.drawText("${speed.toInt()} KM/H", centerX, h - 150f, paint)
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
            
            paint.color = Color.BLACK
            paint.textSize = 80f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore}", w/2f, h * 0.2f, paint)
            
            paint.textSize = 40f
            canvas.drawText("POINTS", w/2f, h * 0.3f, paint)
            
            paint.color = Color.parseColor("#001122")
            paint.textSize = 28f
            canvas.drawText("🚀 Poussée: ${(pushQuality * 100).toInt()}%", w/2f, h * 0.45f, paint)
            canvas.drawText("🎮 Contrôle: ${(controlPerformance * 100).toInt()}%", w/2f, h * 0.5f, paint)
            canvas.drawText("⚡ Vitesse max: ${speed.toInt()} km/h", w/2f, h * 0.55f, paint)
            canvas.drawText("🕒 Temps: ${raceTime.toInt()}s", w/2f, h * 0.6f, paint)
        }
        
        private fun drawIceParticles(canvas: Canvas, w: Int, h: Int) {
            // ON N'AFFICHE LES FLOCONS QUE PENDANT LA PRÉPARATION ET LA POUSSÉE
            if (gameState == GameState.PREPARATION || gameState == GameState.PUSH_START) {
                paint.color = Color.parseColor("#CCEEEE")
                paint.alpha = 200
                for (particle in iceParticles) {
                    canvas.drawCircle(particle.x, particle.y, particle.size, paint)
                }
                paint.alpha = 255
            }
        }
    }

    data class IceParticle(
        var x: Float,
        var y: Float,
        val speed: Float,
        val size: Float
    )

    enum class GameState {
        PREPARATION, PUSH_START, CONTROL_DESCENT, FINISH_LINE, CELEBRATION, RESULTS, FINISHED
    }
}
