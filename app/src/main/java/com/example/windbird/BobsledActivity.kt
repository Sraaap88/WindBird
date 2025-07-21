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

    // Structure de jeu
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Durées
    private val preparationDuration = 6f
    private val pushStartDuration = 8f
    private val controlDescentDuration = 30f
    private val finishLineDuration = 4f
    private val celebrationDuration = 8f
    private val resultsDuration = 5f
    
    // Variables de jeu principales
    private var speed = 0f
    private var baseSpeed = 50f
    private var maxSpeed = 150f
    private var pushPower = 0f
    private var distance = 0f
    
    // Variables de performance
    private var wallHits = 0
    private var perfectTurns = 0
    private var raceTime = 0f
    private var pushQuality = 0f
    
    // Circuit et virages
    private var trackPosition = 0f // Position sur le circuit (0.0 à 1.0)
    private var currentCurveIntensity = 0f // -1.0 à 1.0 (gauche/droite)
    private val trackCurves = mutableListOf<Float>() // Séquence de virages
    private var curveIndex = 0
    
    // Contrôles gyroscopiques
    private var tiltZ = 0f
    private var playerReactionAccuracy = 1f
    
    // Système de poussée
    private var pushCount = 0
    private var lastPushTime = 0L
    private var pushRhythm = 0f
    
    // Score
    private var finalScore = 0
    private var scoreCalculated = false
    
    // Effets visuels
    private var cameraShake = 0f

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
        baseSpeed = 50f
        pushPower = 0f
        distance = 0f
        trackPosition = 0f
        currentCurveIntensity = 0f
        curveIndex = 0
        wallHits = 0
        perfectTurns = 0
        raceTime = 0f
        pushQuality = 0f
        pushCount = 0
        pushRhythm = 0f
        tiltZ = 0f
        playerReactionAccuracy = 1f
        finalScore = 0
        scoreCalculated = false
        cameraShake = 0f
        lastPushTime = 0L
        
        generateTrackCurves()
    }
    
    private fun generateTrackCurves() {
        trackCurves.clear()
        // Circuit de bobsleigh réaliste avec différents virages
        trackCurves.addAll(listOf(
            0f,    // Départ droit
            0f, 0f, 0f,
            -0.8f, // Virage gauche
            -0.6f, -0.4f, -0.2f,
            0f,    // Ligne droite
            0f, 0f,
            0.9f,  // Virage droite serré
            0.7f, 0.5f, 0.3f,
            0f,    // Ligne droite
            0f,
            -0.5f, // Virage gauche moyen
            -0.3f, -0.1f,
            0f,    // Ligne droite
            0.6f,  // Virage droite
            0.4f, 0.2f,
            0f,    // Arrivée droite
            0f, 0f, 0f
        ))
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
        pushPower = pushPower.coerceAtMost(100f)
        pushRhythm = pushRhythm.coerceAtMost(100f)
        
        if (phaseTimer >= pushStartDuration) {
            pushQuality = (pushPower * 0.6f + pushRhythm * 0.4f) / 100f
            baseSpeed = 60f + (pushQuality * 60f) // 60-120 km/h de base
            speed = baseSpeed
            
            gameState = GameState.CONTROL_DESCENT
            phaseTimer = 0f
            cameraShake = 0.5f
        }
    }
    
    private fun handleControlDescent() {
        updateTrackProgress()
        updatePlayerReaction()
        
        // Ajustement vitesse selon performance
        val speedMultiplier = 0.7f + (playerReactionAccuracy * 0.6f) // 0.7x à 1.3x
        speed = baseSpeed * speedMultiplier
        speed = speed.coerceAtMost(maxSpeed)
        
        if (trackPosition >= 1f) {
            gameState = GameState.FINISH_LINE
            phaseTimer = 0f
            cameraShake = 0.8f
        }
    }
    
    private fun updateTrackProgress() {
        val progressSpeed = speed / 2000f // Vitesse de progression
        trackPosition += progressSpeed * 0.025f
        trackPosition = trackPosition.coerceAtMost(1f)
        
        // Calculer le virage actuel selon la position
        val trackIndex = (trackPosition * (trackCurves.size - 1)).toInt()
        val nextIndex = (trackIndex + 1).coerceAtMost(trackCurves.size - 1)
        val progress = (trackPosition * (trackCurves.size - 1)) - trackIndex
        
        // Interpolation entre les virages
        currentCurveIntensity = if (trackIndex < trackCurves.size) {
            val current = trackCurves[trackIndex]
            val next = trackCurves[nextIndex]
            current + (next - current) * progress
        } else {
            0f
        }
    }
    
    private fun updatePlayerReaction() {
        // Calcul de la réaction idéale selon le virage
        val idealReaction = when {
            currentCurveIntensity < -0.3f -> -0.8f // Incliner à gauche
            currentCurveIntensity > 0.3f -> 0.8f   // Incliner à droite
            else -> 0f                             // Rester droit
        }
        
        // Comparaison avec la réaction du joueur
        val reactionError = abs(tiltZ - idealReaction)
        playerReactionAccuracy = (1f - reactionError / 2f).coerceIn(0.3f, 1f)
        
        // Bonus pour les virages parfaits
        if (reactionError < 0.2f && abs(currentCurveIntensity) > 0.3f) {
            perfectTurns++
        }
    }
    
    private fun handleFinishLine() {
        if (phaseTimer >= finishLineDuration) {
            gameState = GameState.CELEBRATION
            phaseTimer = 0f
            speed *= 0.9f
        }
    }
    
    private fun handleCelebration() {
        speed = maxOf(20f, speed * 0.98f)
        
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
    
    private fun updateEffects() {
        cameraShake = maxOf(0f, cameraShake - 0.02f)
    }
    
    private fun calculateFinalScore() {
        if (!scoreCalculated) {
            val timeBonus = maxOf(0, 300 - raceTime.toInt())
            val speedBonus = (speed / maxSpeed * 100).toInt()
            val pushBonus = (pushQuality * 80).toInt()
            val reactionBonus = (playerReactionAccuracy * 150).toInt()
            val perfectBonus = perfectTurns * 25
            val wallPenalty = wallHits * 30
            
            finalScore = maxOf(100, timeBonus + speedBonus + pushBonus + reactionBonus + perfectBonus - wallPenalty)
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
                val aiScore = (150..250).random()
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
            GameState.CONTROL_DESCENT -> "🎮 ${tournamentData.playerNames[currentPlayerIndex]} | Réflexes: ${(playerReactionAccuracy * 100).toInt()}% | ${speed.toInt()} km/h"
            GameState.FINISH_LINE -> "🏁 ${tournamentData.playerNames[currentPlayerIndex]} | Ligne d'arrivée: ${speed.toInt()} km/h!"
            GameState.CELEBRATION -> "🎉 ${tournamentData.playerNames[currentPlayerIndex]} | Temps: ${raceTime.toInt()}s!"
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
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        // Images du bobsleigh
        private var bobsledPreparationBitmap: Bitmap? = null
        private var bobPushBitmap: Bitmap? = null
        private var bobStraightBitmap: Bitmap? = null
        private var bobLeftBitmap: Bitmap? = null
        private var bobRightBitmap: Bitmap? = null
        private var bobFinishLineBitmap: Bitmap? = null
        private var bobCelebrationBitmap: Bitmap? = null
        
        init {
            try {
                bobsledPreparationBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobsled_preparation)
                bobPushBitmap = BitmapFactory.decodeResource(resources, R.drawable.bob_push)
                bobStraightBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobnv_straight)
                bobLeftBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobnv_left)
                bobRightBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobnv_right)
                bobFinishLineBitmap = BitmapFactory.decodeResource(resources, R.drawable.bob_finish_line)
                bobCelebrationBitmap = BitmapFactory.decodeResource(resources, R.drawable.bob_celebration)
            } catch (e: Exception) {
                createFallbackBobsledBitmaps()
            }
        }
        
        private fun createFallbackBobsledBitmaps() {
            bobsledPreparationBitmap = createSubstituteBitmap(Color.parseColor("#FF4444"))
            bobPushBitmap = createSubstituteBitmap(Color.parseColor("#FF6644"))
            bobStraightBitmap = createSubstituteBitmap(Color.parseColor("#FFB444"))
            bobLeftBitmap = createSubstituteBitmap(Color.parseColor("#44FF44"))
            bobRightBitmap = createSubstituteBitmap(Color.parseColor("#4444FF"))
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

        // GESTION DU TACTILE POUR LA POUSSÉE - ZONE QUI SUIT LE BOBSLEIGH
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (gameState == GameState.PUSH_START) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        // Zone qui suit le bobsleigh qui bouge
                        val pushProgress = (pushPower / 100f).coerceIn(0f, 1f)
                        val bobX = 150f + pushProgress * (width - 300f)
                        val bobY = height * 0.7f
                        val touchRadius = 80f
                        
                        val touchX = event.x
                        val touchY = event.y
                        val distance = sqrt((touchX - bobX).pow(2) + (touchY - bobY).pow(2))
                        
                        if (distance <= touchRadius) {
                            pushPower += 4f
                            pushCount++
                            
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastPushTime > 150 && currentTime - lastPushTime < 600) {
                                pushRhythm += 5f
                            } else {
                                pushRhythm += 2f
                            }
                            lastPushTime = currentTime
                            
                            pushPower = pushPower.coerceAtMost(100f)
                            pushRhythm = pushRhythm.coerceAtMost(100f)
                            
                            cameraShake = 0.2f
                            
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
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 10f,
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 10f
                )
            }
            
            when (gameState) {
                GameState.PREPARATION -> drawPreparation(canvas, w, h)
                GameState.PUSH_START -> drawPushStart(canvas, w, h)
                GameState.CONTROL_DESCENT -> drawWinterGamesTunnel(canvas, w, h)
                GameState.FINISH_LINE -> drawFinishLine(canvas, w, h)
                GameState.CELEBRATION -> drawCelebration(canvas, w, h)
                GameState.RESULTS -> drawResults(canvas, w, h)
                GameState.FINISHED -> drawResults(canvas, w, h)
            }
            
            if (cameraShake > 0f) {
                canvas.restore()
            }
        }
        
        private fun drawPreparation(canvas: Canvas, w: Int, h: Int) {
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
            paint.color = Color.rgb(150, 200, 255)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
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
            
            paint.color = Color.WHITE
            val trackY = h * 0.7f
            canvas.drawRect(50f, trackY - 40f, w - 50f, trackY + 40f, paint)
            
            paint.color = Color.GRAY
            paint.strokeWidth = 4f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(50f, trackY - 40f, w - 50f, trackY - 40f, paint)
            canvas.drawLine(50f, trackY + 40f, w - 50f, trackY + 40f, paint)
            paint.style = Paint.Style.FILL
            
            paint.color = Color.RED
            paint.strokeWidth = 6f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(100f, trackY - 50f, 100f, trackY + 50f, paint)
            paint.style = Paint.Style.FILL
            
            // BOBSLEIGH QUI BOUGE DE GAUCHE À DROITE SELON LA POUSSÉE
            val pushProgress = (pushPower / 100f).coerceIn(0f, 1f)
            val bobX = 150f + pushProgress * (w - 300f) // De gauche à droite
            val bobY = trackY
            
            paint.color = Color.argb(100, 255, 255, 0)
            canvas.drawCircle(bobX, bobY, 80f, paint)
            
            paint.color = Color.YELLOW
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawCircle(bobX, bobY, 80f, paint)
            paint.style = Paint.Style.FILL
            
            bobPushBitmap?.let { bmp ->
                val scale = 0.3f
                val dstRect = RectF(
                    bobX - bmp.width * scale / 2f,
                    bobY - bmp.height * scale / 2f,
                    bobX + bmp.width * scale / 2f,
                    bobY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            } ?: run {
                paint.color = Color.RED
                canvas.drawRoundRect(bobX - 40f, bobY - 20f, bobX + 40f, bobY + 20f, 8f, 8f, paint)
            }
            
            paint.color = Color.argb(200, 0, 0, 0)
            canvas.drawRoundRect(w/2f - 300f, 120f, w/2f + 300f, 200f, 10f, 10f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 40f // PLUS GROS
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("TAPEZ SUR LE BOBSLEIGH POUR LE POUSSER!", w/2f, 170f, paint)
            
            paint.color = Color.argb(200, 0, 0, 0)
            canvas.drawRoundRect(w/2f - 200f, h - 150f, w/2f + 200f, h - 40f, 10f, 10f, paint)
            
            paint.color = Color.GRAY
            canvas.drawRect(w/2f - 180f, h - 120f, w/2f + 180f, h - 80f, paint)
            
            paint.color = Color.GREEN
            val powerWidth = (pushPower / 100f) * 360f
            canvas.drawRect(w/2f - 180f, h - 120f, w/2f - 180f + powerWidth, h - 80f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 28f // PLUS GROS
            canvas.drawText("PUISSANCE: ${pushPower.toInt()}% | Coups: ${pushCount}", w/2f, h - 50f, paint)
            
            paint.color = Color.argb(200, 255, 0, 0)
            canvas.drawRoundRect(w - 120f, 60f, w - 20f, 140f, 10f, 10f, paint)
            
            paint.textSize = 36f // PLUS GROS
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${(pushStartDuration - phaseTimer).toInt() + 1}s", w - 70f, 110f, paint)
        }

        private fun drawWinterGamesTunnel(canvas: Canvas, w: Int, h: Int) {
            // VRAI DEMI-TUNNEL AVEC MURS COURBES COMME WINTER GAMES !
            
            val horizonY = h * 0.35f
            val roadDistance = 200f
            
            // 1. CIEL VIOLET RÉTRO
            paint.color = Color.rgb(170, 140, 255)
            canvas.drawRect(0f, 0f, w.toFloat(), horizonY, paint)
            
            // 2. MONTAGNES QUI BOUGENT SELON LES VIRAGES
            val mountainOffset = currentCurveIntensity * w * 0.3f
            paint.color = Color.rgb(100, 100, 100)
            
            val mountain1 = Path().apply {
                moveTo(-mountainOffset, horizonY)
                lineTo(w * 0.3f - mountainOffset, horizonY - 60f)
                lineTo(w * 0.7f - mountainOffset, horizonY - 40f)
                lineTo(w.toFloat() - mountainOffset, horizonY)
                close()
            }
            canvas.drawPath(mountain1, paint)
            
            val mountain2 = Path().apply {
                moveTo(w - mountainOffset, horizonY)
                lineTo(w * 1.3f - mountainOffset, horizonY - 60f)
                lineTo(w * 1.7f - mountainOffset, horizonY - 40f)
                lineTo(w * 2f - mountainOffset, horizonY)
                close()
            }
            canvas.drawPath(mountain2, paint)
            
            // 3. DEMI-TUNNEL COMPLET LIGNE PAR LIGNE AVEC ANTICIPATION
            val roadScroll = (phaseTimer * speed * 3f) % 100f
            
            for (screenY in horizonY.toInt() until h) {
                val lineProgress = (screenY - horizonY) / (h - horizonY)
                val z = roadDistance * (1f - lineProgress * 0.95f)
                val scaleFactor = 1f / z
                
                // LARGEUR DE LA PISTE SELON LA DISTANCE
                val roadWidth = (w * 0.5f * scaleFactor).coerceAtMost(w * 0.7f)
                val wallHeight = (60f * scaleFactor).coerceAtMost(80f)
                
                // ANTICIPATION DES VIRAGES - Plus on regarde loin, plus on voit les virages futurs
                val lookAheadDistance = (1f - lineProgress) * 2f // 2 virages d'avance au maximum
                val futurePosition = (trackPosition * 50f + lineProgress * 10f + roadScroll * 0.1f + lookAheadDistance) % trackCurves.size
                val curveIndex = futurePosition.toInt()
                val curveProgress = futurePosition - curveIndex
                
                val currentCurve = if (curveIndex < trackCurves.size) {
                    val curve1 = trackCurves[curveIndex]
                    val curve2 = trackCurves[(curveIndex + 1) % trackCurves.size]
                    curve1 + (curve2 - curve1) * curveProgress
                } else {
                    0f
                }
                
                val roadCenterX = w/2f + currentCurve * w * 0.3f * scaleFactor
                
                // DESSINER LE DEMI-TUNNEL COMPLET
                
                // 1. SOL DE LA PISTE (BLANC)
                paint.color = Color.WHITE
                canvas.drawRect(
                    roadCenterX - roadWidth/2f,
                    screenY.toFloat(),
                    roadCenterX + roadWidth/2f,
                    screenY + 2f,
                    paint
                )
                
                // 2. MURS LATÉRAUX (GRIS FONCÉ)
                paint.color = Color.rgb(120, 120, 120)
                
                // Mur gauche vertical
                canvas.drawRect(
                    roadCenterX - roadWidth/2f - 8f * scaleFactor,
                    screenY.toFloat(),
                    roadCenterX - roadWidth/2f,
                    screenY + 2f,
                    paint
                )
                
                // Mur droit vertical
                canvas.drawRect(
                    roadCenterX + roadWidth/2f,
                    screenY.toFloat(),
                    roadCenterX + roadWidth/2f + 8f * scaleFactor,
                    screenY + 2f,
                    paint
                )
                
                // 3. MURS COURBES QUI REMONTENT (DEMI-TUNNEL)
                paint.color = Color.rgb(140, 140, 140)
                
                // Progression de la courbe selon la hauteur
                val curveHeight = wallHeight * (1f - lineProgress * 0.3f)
                val curveRadius = roadWidth * 0.7f
                
                // Points pour la courbe gauche (quart de cercle)
                for (angle in 90..180 step 5) {
                    val radian = Math.toRadians(angle.toDouble()).toFloat()
                    val curveX = roadCenterX - roadWidth/2f + cos(radian) * curveRadius * 0.5f
                    val curveY = screenY + sin(radian) * curveHeight * 0.5f
                    
                    paint.color = Color.rgb(130, 130, 130)
                    canvas.drawRect(curveX - 2f, curveY, curveX + 2f, curveY + 2f, paint)
                }
                
                // Points pour la courbe droite (quart de cercle)
                for (angle in 0..90 step 5) {
                    val radian = Math.toRadians(angle.toDouble()).toFloat()
                    val curveX = roadCenterX + roadWidth/2f + cos(radian) * curveRadius * 0.5f
                    val curveY = screenY + sin(radian) * curveHeight * 0.5f
                    
                    paint.color = Color.rgb(130, 130, 130)
                    canvas.drawRect(curveX - 2f, curveY, curveX + 2f, curveY + 2f, paint)
                }
                
                // 4. OMBRES DANS LE TUNNEL
                paint.color = Color.rgb(100, 100, 100)
                
                // Ombre gauche
                canvas.drawRect(
                    roadCenterX - roadWidth/2f,
                    screenY.toFloat(),
                    roadCenterX - roadWidth/3f,
                    screenY + 1f,
                    paint
                )
                
                // Ombre droite
                canvas.drawRect(
                    roadCenterX + roadWidth/3f,
                    screenY.toFloat(),
                    roadCenterX + roadWidth/2f,
                    screenY + 1f,
                    paint
                )
                
                // 5. LIGNES DE VITESSE (TEXTURE)
                val texturePosition = (roadScroll + lineProgress * 20f) % 8f
                if (texturePosition < 1f) {
                    paint.color = Color.rgb(220, 220, 220)
                    canvas.drawRect(
                        roadCenterX - roadWidth * 0.05f,
                        screenY.toFloat(),
                        roadCenterX + roadWidth * 0.05f,
                        screenY + 1f,
                        paint
                    )
                }
            }
            
            // 4. BOBSLEIGH AVEC PHYSIQUE DE VIRAGES - Monte sur les parois !
            val bobX = w / 2f
            val baseBobY = h * 0.82f // Position de base au fond de la piste
            val bobScale = 0.2f
            
            // CALCUL DE LA MONTÉE SUR LES PAROIS
            var bobVerticalOffset = 0f
            var bobRotation = 0f
            
            if (abs(currentCurveIntensity) > 0.3f) {
                // Force centrifuge : virage gauche = monte à droite, virage droite = monte à gauche
                val centrifugalForce = abs(currentCurveIntensity)
                val speedFactor = (speed / maxSpeed).coerceIn(0.3f, 1f) // Plus de vitesse = monte plus haut
                
                // Calcul de l'angle de montée (45° à 75° selon l'intensité)
                val climbAngle = (45f + (centrifugalForce * speedFactor * 30f)).coerceAtMost(75f)
                val climbRadians = Math.toRadians(climbAngle.toDouble()).toFloat()
                
                // Distance de montée sur la paroi inclinée
                val climbDistance = centrifugalForce * speedFactor * 60f // Jusqu'à 60px de montée
                
                // Position verticale (monte vers le haut)
                bobVerticalOffset = -sin(climbRadians) * climbDistance
                
                // Rotation supplémentaire pour aller de 45° (sprite) vers 75° max
                val additionalRotation = climbAngle - 45f
                bobRotation = if (currentCurveIntensity < 0f) -additionalRotation else additionalRotation
            }
            
            val bobY = baseBobY + bobVerticalOffset
            
            // Choix du sprite selon la direction du virage
            val bobSprite = when {
                currentCurveIntensity < -0.3f -> bobLeftBitmap
                currentCurveIntensity > 0.3f -> bobRightBitmap
                else -> bobStraightBitmap
            }
            
            // Dessiner le bobsleigh avec rotation si nécessaire
            bobSprite?.let { bmp ->
                val dstRect = RectF(
                    bobX - bmp.width * bobScale / 2f,
                    bobY - bmp.height * bobScale / 2f,
                    bobX + bmp.width * bobScale / 2f,
                    bobY + bmp.height * bobScale / 2f
                )
                
                if (abs(bobRotation) > 1f) {
                    // Appliquer une rotation supplémentaire
                    canvas.save()
                    canvas.rotate(bobRotation, bobX, bobY)
                    canvas.drawBitmap(bmp, null, dstRect, paint)
                    canvas.restore()
                } else {
                    canvas.drawBitmap(bmp, null, dstRect, paint)
                }
            } ?: run {
                // Fallback coloré selon l'état
                paint.color = when {
                    currentCurveIntensity < -0.3f -> Color.GREEN
                    currentCurveIntensity > 0.3f -> Color.BLUE
                    else -> Color.YELLOW
                }
                
                if (abs(bobRotation) > 1f) {
                    canvas.save()
                    canvas.rotate(bobRotation, bobX, bobY)
                }
                
                canvas.drawRoundRect(bobX - 25f, bobY - 15f, bobX + 25f, bobY + 15f, 8f, 8f, paint)
                
                if (abs(bobRotation) > 1f) {
                    canvas.restore()
                }
            }
            
            // 5. INTERFACE AVEC TEXTE PLUS GROS
            paint.color = Color.BLACK
            paint.textSize = 40f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("${speed.toInt()} KM/H", 30f, 80f, paint)
            
            if (abs(currentCurveIntensity) > 0.3f) {
                paint.color = Color.YELLOW
                paint.textSize = 48f
                paint.textAlign = Paint.Align.CENTER
                val instruction = if (currentCurveIntensity < 0f) "← GAUCHE" else "DROITE →"
                canvas.drawText(instruction, w/2f, 140f, paint)
                
                paint.textSize = 32f
                paint.color = when {
                    playerReactionAccuracy > 0.8f -> Color.GREEN
                    playerReactionAccuracy > 0.6f -> Color.YELLOW
                    else -> Color.RED
                }
                canvas.drawText("${(playerReactionAccuracy * 100).toInt()}%", w/2f, 180f, paint)
                
                // DEBUG: Afficher l'angle de montée
                paint.color = Color.WHITE
                paint.textSize = 20f
                canvas.drawText("Angle: ${((45f + (abs(currentCurveIntensity) * (speed / maxSpeed).coerceIn(0.3f, 1f) * 30f)).coerceAtMost(75f)).toInt()}°", 30f, h - 30f, paint)
            }
        }
        
        private fun drawFinishLine(canvas: Canvas, w: Int, h: Int) {
            drawWinterGamesTunnel(canvas, w, h)
            
            val lineProgress = phaseTimer / finishLineDuration
            val lineY = h * (1f - lineProgress)
            
            if (lineY > 0f && lineY < h) {
                val segments = 10
                val segmentWidth = w.toFloat() / segments
                
                for (i in 0 until segments) {
                    val color = if (i % 2 == 0) Color.WHITE else Color.BLACK
                    paint.color = color
                    canvas.drawRect(
                        i * segmentWidth,
                        lineY - 30f,
                        (i + 1) * segmentWidth,
                        lineY + 30f,
                        paint
                    )
                }
                
                paint.color = Color.YELLOW
                paint.textSize = 60f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("🏁 FINISH! 🏁", w/2f, lineY + 10f, paint)
            }
        }
        
        private fun drawCelebration(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            val centerX = w/2f
            val centerY = h/2f
            
            val progress = phaseTimer / celebrationDuration
            for (i in 0..15) {
                val angle = (2.0 * PI / 15 * i + progress * 6).toFloat()
                val radius = progress * 200f
                val particleX = centerX + cos(angle) * radius
                val particleY = centerY + sin(angle) * radius
                
                paint.color = Color.WHITE
                canvas.drawCircle(particleX, particleY, 6f, paint)
            }
            
            bobCelebrationBitmap?.let { bmp ->
                val scale = 0.4f
                val dstRect = RectF(
                    centerX - bmp.width * scale / 2f,
                    centerY - bmp.height * scale / 2f,
                    centerX + bmp.width * scale / 2f,
                    centerY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            paint.color = Color.BLACK
            paint.textSize = 60f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🎉 BRAVO! 🎉", centerX, 150f, paint)
            
            paint.textSize = 40f
            canvas.drawText("Temps: ${raceTime.toInt()}s", centerX, h - 100f, paint)
            canvas.drawText("Vitesse moy: ${speed.toInt()} km/h", centerX, h - 50f, paint)
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
            
            paint.color = Color.BLACK
            paint.textSize = 80f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore}", w/2f, h * 0.25f, paint)
            
            paint.textSize = 30f
            canvas.drawText("POINTS", w/2f, h * 0.35f, paint)
            
            paint.color = Color.parseColor("#001122")
            paint.textSize = 24f
            canvas.drawText("🚀 Poussée: ${(pushQuality * 100).toInt()}%", w/2f, h * 0.5f, paint)
            canvas.drawText("🎮 Réflexes: ${(playerReactionAccuracy * 100).toInt()}%", w/2f, h * 0.55f, paint)
            canvas.drawText("🏆 Virages parfaits: ${perfectTurns}", w/2f, h * 0.6f, paint)
            canvas.drawText("🕒 Temps: ${raceTime.toInt()}s", w/2f, h * 0.65f, paint)
            canvas.drawText("⚡ Vitesse moy: ${speed.toInt()} km/h", w/2f, h * 0.7f, paint)
        }
    }

    enum class GameState {
        PREPARATION, PUSH_START, CONTROL_DESCENT, FINISH_LINE, CELEBRATION, RESULTS, FINISHED
    }
}
