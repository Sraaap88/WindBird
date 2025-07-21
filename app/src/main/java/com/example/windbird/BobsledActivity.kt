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
    private val controlDescentDuration = 30f // Plus long pour un vrai parcours
    private val finishLineDuration = 4f
    private val celebrationDuration = 8f
    private val resultsDuration = 5f
    
    // Variables de jeu principales
    private var speed = 0f
    private var baseSpeed = 50f // Vitesse de base après poussée
    private var maxSpeed = 150f
    private var pushPower = 0f
    private var distance = 0f
    private var totalDistance = 2000f
    
    // Variables de performance
    private var wallHits = 0
    private var perfectTurns = 0
    private var raceTime = 0f
    private var pushQuality = 0f
    
    // Circuit prédéfini
    private var trackProgress = 0f // 0.0 à 1.0
    private val trackData = mutableListOf<TrackSegment>()
    private var currentSegmentIndex = 0
    private var segmentProgress = 0f
    
    // Contrôles gyroscopiques pour le timing
    private var tiltZ = 0f
    private var playerReactionAccuracy = 1f
    private var reactionBonus = 0f
    
    // Système de poussée
    private var pushCount = 0
    private var lastPushTime = 0L
    private var pushRhythm = 0f
    
    // Score et résultats
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
        numberOfPlayers = intent.getIntInteger("number_of_players", 1)
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
        trackProgress = 0f
        wallHits = 0
        perfectTurns = 0
        raceTime = 0f
        pushQuality = 0f
        pushCount = 0
        pushRhythm = 0f
        currentSegmentIndex = 0
        segmentProgress = 0f
        tiltZ = 0f
        playerReactionAccuracy = 1f
        reactionBonus = 0f
        finalScore = 0
        scoreCalculated = false
        cameraShake = 0f
        lastPushTime = 0L
        
        generateTrack()
    }
    
    private fun generateTrack() {
        trackData.clear()
        // Circuit prédéfini avec différents types de segments
        trackData.add(TrackSegment(TrackType.STRAIGHT, 3f))
        trackData.add(TrackSegment(TrackType.LEFT_CURVE, 4f))
        trackData.add(TrackSegment(TrackType.STRAIGHT, 2f))
        trackData.add(TrackSegment(TrackType.RIGHT_CURVE, 5f))
        trackData.add(TrackSegment(TrackType.STRAIGHT, 3f))
        trackData.add(TrackSegment(TrackType.LEFT_CURVE, 3f))
        trackData.add(TrackSegment(TrackType.RIGHT_CURVE, 4f))
        trackData.add(TrackSegment(TrackType.STRAIGHT, 2f))
        trackData.add(TrackSegment(TrackType.LEFT_CURVE, 6f))
        trackData.add(TrackSegment(TrackType.STRAIGHT, 4f))
        trackData.add(TrackSegment(TrackType.FINISH_LINE, 2f))
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
            baseSpeed = 50f + (pushQuality * 50f) // 50-100 km/h de base
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
        val speedMultiplier = 0.8f + (playerReactionAccuracy * 0.4f) // 0.8x à 1.2x
        speed = baseSpeed * speedMultiplier
        speed = speed.coerceAtMost(maxSpeed)
        
        if (trackProgress >= 1f) {
            gameState = GameState.FINISH_LINE
            phaseTimer = 0f
            cameraShake = 0.8f
        }
    }
    
    private fun updateTrackProgress() {
        val progressSpeed = speed / 1000f // Conversion vitesse en progression
        trackProgress += progressSpeed * 0.025f
        trackProgress = trackProgress.coerceAtMost(1f)
        
        // Calcul segment actuel
        val totalSegments = trackData.size
        val currentPos = trackProgress * totalSegments
        currentSegmentIndex = currentPos.toInt().coerceAtMost(totalSegments - 1)
        segmentProgress = currentPos - currentSegmentIndex
    }
    
    private fun updatePlayerReaction() {
        if (currentSegmentIndex < trackData.size) {
            val currentSegment = trackData[currentSegmentIndex]
            
            // Calcul de la réaction idéale selon le type de virage
            val idealReaction = when (currentSegment.type) {
                TrackType.LEFT_CURVE -> -0.8f // Incliner à gauche
                TrackType.RIGHT_CURVE -> 0.8f // Incliner à droite
                TrackType.STRAIGHT, TrackType.FINISH_LINE -> 0f // Rester droit
            }
            
            // Comparaison avec la réaction du joueur
            val reactionError = abs(tiltZ - idealReaction)
            playerReactionAccuracy = (1f - reactionError / 2f).coerceIn(0.3f, 1f)
            
            // Bonus pour les virages parfaits
            if (reactionError < 0.2f && currentSegment.type != TrackType.STRAIGHT) {
                perfectTurns++
                reactionBonus += 5f
            }
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
        // [Same logic as before for tournament progression]
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
        private val paint = Paint()
        
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

        // GESTION DU TACTILE POUR LA POUSSÉE
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (gameState == GameState.PUSH_START) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        // Zone du bobsleigh pour taper dessus
                        val bobX = width/2f
                        val bobY = height * 0.7f
                        val touchRadius = 80f
                        
                        val touchX = event.x
                        val touchY = event.y
                        val distance = sqrt((touchX - bobX).pow(2) + (touchY - bobY).pow(2))
                        
                        if (distance <= touchRadius) {
                            // POUSSER !
                            pushPower += 4f
                            pushCount++
                            
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastPushTime > 150 && currentTime - lastPushTime < 600) {
                                pushRhythm += 5f // Excellent rythme
                            } else {
                                pushRhythm += 2f // Rythme moyen
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
                GameState.CONTROL_DESCENT -> drawBeautifulTunnel(canvas, w, h)
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
            // Fond avec l'image de préparation ou fallback
            bobsledPreparationBitmap?.let { bmp ->
                val dstRect = RectF(0f, 0f, w.toFloat(), h.toFloat())
                canvas.drawBitmap(bmp, null, dstRect, paint)
            } ?: run {
                paint.color = Color.parseColor("#E0F6FF")
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            
            // Rectangle pour drapeau
            paint.color = Color.WHITE
            val flagRect = RectF(50f, 50f, 300f, 200f)
            canvas.drawRect(flagRect, paint)
            
            paint.color = Color.BLACK
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawRect(flagRect, paint)
            paint.style = Paint.Style.FILL
            
            // Drapeau du pays
            val playerCountry = tournamentData.playerCountries[currentPlayerIndex]
            val flag = getCountryFlag(playerCountry)
            
            paint.color = Color.BLACK
            paint.textSize = 120f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(flag, flagRect.centerX(), flagRect.centerY() + 40f, paint)
            
            paint.textSize = 28f
            canvas.drawText(playerCountry.uppercase(), flagRect.centerX(), flagRect.bottom + 40f, paint)
            
            // Instructions
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
            // Fond simple 
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
            
            // Piste
            paint.color = Color.WHITE
            val trackY = h * 0.7f
            canvas.drawRect(50f, trackY - 40f, w - 50f, trackY + 40f, paint)
            
            // BOBSLEIGH AU CENTRE (image bob_push.png)
            val bobX = w/2f
            val bobY = trackY
            
            // Zone tactile visible
            paint.color = Color.argb(100, 255, 255, 0)
            canvas.drawCircle(bobX, bobY, 80f, paint)
            
            paint.color = Color.YELLOW
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawCircle(bobX, bobY, 80f, paint)
            paint.style = Paint.Style.FILL
            
            // Bobsleigh sprite
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
                // Fallback
                paint.color = Color.RED
                canvas.drawRoundRect(bobX - 40f, bobY - 20f, bobX + 40f, bobY + 20f, 8f, 8f, paint)
            }
            
            // Instructions
            paint.color = Color.argb(200, 0, 0, 0)
            canvas.drawRoundRect(w/2f - 200f, 120f, w/2f + 200f, 180f, 10f, 10f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 28f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("TAPEZ SUR LE BOBSLEIGH POUR LE POUSSER!", w/2f, 155f, paint)
            
            // Barre de puissance
            paint.color = Color.argb(200, 0, 0, 0)
            canvas.drawRoundRect(w/2f - 160f, h - 110f, w/2f + 160f, h - 40f, 10f, 10f, paint)
            
            paint.color = Color.GRAY
            canvas.drawRect(w/2f - 150f, h - 100f, w/2f + 150f, h - 70f, paint)
            
            paint.color = Color.GREEN
            val powerWidth = (pushPower / 100f) * 300f
            canvas.drawRect(w/2f - 150f, h - 100f, w/2f - 150f + powerWidth, h - 70f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 20f
            canvas.drawText("PUISSANCE: ${pushPower.toInt()}% | Coups: ${pushCount}", w/2f, h - 50f, paint)
            
            // Temps restant
            paint.color = Color.argb(200, 255, 0, 0)
            canvas.drawRoundRect(w - 100f, 60f, w - 20f, 120f, 10f, 10f, paint)
            
            paint.textSize = 24f
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${(pushStartDuration - phaseTimer).toInt() + 1}s", w - 60f, 100f, paint)
        }

        private fun drawBeautifulTunnel(canvas: Canvas, w: Int, h: Int) {
            // TUNNEL MAGNIFIQUE STYLE WINTER GAMES
            val centerX = w / 2f
            val horizonY = h * 0.25f
            
            // 1. CIEL VIOLET RÉTRO
            paint.color = Color.rgb(170, 140, 255)
            canvas.drawRect(0f, 0f, w.toFloat(), horizonY, paint)
            
            // Montagnes qui bougent selon les virages
            paint.color = Color.rgb(100, 100, 100)
            val mountainOffset = if (currentSegmentIndex < trackData.size) {
                when (trackData[currentSegmentIndex].type) {
                    TrackType.LEFT_CURVE -> -segmentProgress * 50f
                    TrackType.RIGHT_CURVE -> segmentProgress * 50f
                    else -> 0f
                }
            } else 0f
            
            val mountain = Path().apply {
                moveTo(-mountainOffset, horizonY)
                lineTo(w * 0.3f - mountainOffset, horizonY - 80f)
                lineTo(w * 0.7f - mountainOffset, horizonY - 50f)
                lineTo(w.toFloat() - mountainOffset, horizonY)
                close()
            }
            canvas.drawPath(mountain, paint)
            
            // 2. PISTE EN DEMI-TUNNEL QUI SERPENTE
            val segments = 20
            val currentCurve = if (currentSegmentIndex < trackData.size) {
                when (trackData[currentSegmentIndex].type) {
                    TrackType.LEFT_CURVE -> -0.6f * segmentProgress
                    TrackType.RIGHT_CURVE -> 0.6f * segmentProgress
                    else -> 0f
                }
            } else 0f
            
            val nextCurve = if (currentSegmentIndex + 1 < trackData.size) {
                when (trackData[currentSegmentIndex + 1].type) {
                    TrackType.LEFT_CURVE -> -0.6f
                    TrackType.RIGHT_CURVE -> 0.6f
                    else -> 0f
                }
            } else 0f
            
            // Animation du défilement selon la vitesse
            val scrollOffset = (phaseTimer * speed * 0.5f) % 100f
            
            for (i in 0 until segments) {
                val t = i.toFloat() / segments
                val tNext = (i + 1).toFloat() / segments
                val y1 = horizonY + t * (h - horizonY) + scrollOffset
                val y2 = horizonY + tNext * (h - horizonY) + scrollOffset
                
                if (y1 > h) continue
                
                val roadW1 = w * (0.1f + t * 0.8f)
                val roadW2 = w * (0.1f + tNext * 0.8f)
                val curve1 = currentCurve + (nextCurve - currentCurve) * t
                val curve2 = currentCurve + (nextCurve - currentCurve) * tNext
                val cx1 = centerX + curve1 * w * 0.3f
                val cx2 = centerX + curve2 * w * 0.3f
                
                // Mur gauche
                paint.color = Color.DKGRAY
                val wallL = Path().apply {
                    moveTo(cx1 - roadW1 / 2f - 15f, y1)
                    lineTo(cx1 - roadW1 / 2f, y1)
                    lineTo(cx2 - roadW2 / 2f, y2)
                    lineTo(cx2 - roadW2 / 2f - 15f, y2)
                    close()
                }
                canvas.drawPath(wallL, paint)
                
                // Mur droit
                val wallR = Path().apply {
                    moveTo(cx1 + roadW1 / 2f + 15f, y1)
                    lineTo(cx1 + roadW1 / 2f, y1)
                    lineTo(cx2 + roadW2 / 2f, y2)
                    lineTo(cx2 + roadW2 / 2f + 15f, y2)
                    close()
                }
                canvas.drawPath(wallR, paint)
                
                // Surface de piste
                paint.color = Color.WHITE
                val track = Path().apply {
                    moveTo(cx1 - roadW1 / 2f, y1)
                    lineTo(cx1 + roadW1 / 2f, y1)
                    lineTo(cx2 + roadW2 / 2f, y2)
                    lineTo(cx2 - roadW2 / 2f, y2)
                    close()
                }
                canvas.drawPath(track, paint)
                
                // Lignes de vitesse
                if (i % 3 == 0) {
                    paint.color = Color.LTGRAY
                    val stripe = Path().apply {
                        moveTo(cx1 - roadW1 * 0.05f, y1)
                        lineTo(cx1 + roadW1 * 0.05f, y1)
                        lineTo(cx2 + roadW2 * 0.05f, y2)
                        lineTo(cx2 - roadW2 * 0.05f, y2)
                        close()
                    }
                    canvas.drawPath(stripe, paint)
                }
            }
            
            // 3. BOBSLEIGH FIXE QUI S'INCLINE
            val bobX = w / 2f
            val bobY = h * 0.75f
            val scale = 0.4f
            
            // Choix du sprite selon le virage actuel
            val bobSprite = if (currentSegmentIndex < trackData.size) {
                when (trackData[currentSegmentIndex].type) {
                    TrackType.LEFT_CURVE -> bobLeftBitmap
                    TrackType.RIGHT_CURVE -> bobRightBitmap
                    else -> bobStraightBitmap
                }
            } else bobStraightBitmap
            
            // Effet d'inclinaison selon le virage
            val bobTilt = currentCurve * 15f // Inclinaison visuelle
            
            canvas.save()
            canvas.rotate(bobTilt, bobX, bobY)
            
            bobSprite?.let { bmp ->
                val dstRect = RectF(
                    bobX - bmp.width * scale / 2f,
                    bobY - bmp.height * scale / 2f,
                    bobX + bmp.width * scale / 2f,
                    bobY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            } ?: run {
                // Fallback coloré selon direction
                paint.color = when {
                    currentCurve < -0.2f -> Color.GREEN // Gauche
                    currentCurve > 0.2f -> Color.BLUE   // Droite
                    else -> Color.RED                   // Droit
                }
                canvas.drawRoundRect(bobX - 40f, bobY - 25f, bobX + 40f, bobY + 25f, 10f, 10f, paint)
            }
            
            canvas.restore()
            
            // 4. INTERFACE
            paint.color = Color.BLACK
            paint.textSize = 32f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("${speed.toInt()} km/h", 30f, 60f, paint)
            
            // Instructions de timing
            if (currentSegmentIndex < trackData.size) {
                val segment = trackData[currentSegmentIndex]
                if (segment.type != TrackType.STRAIGHT) {
                    paint.color = Color.YELLOW
                    paint.textSize = 36f
                    paint.textAlign = Paint.Align.CENTER
                    val instruction = when (segment.type) {
                        TrackType.LEFT_CURVE -> "← INCLINEZ GAUCHE"
                        TrackType.RIGHT_CURVE -> "INCLINEZ DROITE →"
                        else -> ""
                    }
                    canvas.drawText(instruction, w/2f, 120f, paint)
                    
                    // Indicateur de précision
                    paint.textSize = 24f
                    paint.color = when {
                        playerReactionAccuracy > 0.8f -> Color.GREEN
                        playerReactionAccuracy > 0.6f -> Color.YELLOW
                        else -> Color.RED
                    }
                    canvas.drawText("Précision: ${(playerReactionAccuracy * 100).toInt()}%", w/2f, 150f, paint)
                }
            }
        }
        
        private fun drawFinishLine(canvas: Canvas, w: Int, h: Int) {
            // Même fond tunnel mais avec ligne d'arrivée
            drawBeautifulTunnel(canvas, w, h)
            
            // LIGNE D'ARRIVÉE qui arrive
            val lineProgress = phaseTimer / finishLineDuration
            val lineY = h * (1f - lineProgress)
            
            if (lineY > 0f && lineY < h) {
                // Damier noir et blanc
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
                
                // Texte FINISH
                paint.color = Color.YELLOW
                paint.textSize = 60f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("🏁 FINISH! 🏁", w/2f, lineY + 10f, paint)
            }
        }
        
        private fun drawCelebration(canvas: Canvas, w: Int, h: Int) {
            // Fond doré de célébration
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            val centerX = w/2f
            val centerY = h/2f
            
            // Particules de célébration
            val progress = phaseTimer / celebrationDuration
            for (i in 0..15) {
                val angle = (2.0 * PI / 15 * i + progress * 6).toFloat()
                val radius = progress * 200f
                val particleX = centerX + cos(angle) * radius
                val particleY = centerY + sin(angle) * radius
                
                paint.color = Color.WHITE
                canvas.drawCircle(particleX, particleY, 6f, paint)
            }
            
            // Bobsleigh final
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
            
            // Textes de félicitations
            paint.color = Color.BLACK
            paint.textSize = 60f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🎉 BRAVO! 🎉", centerX, 150f, paint)
            
            paint.textSize = 40f
            canvas.drawText("Temps: ${raceTime.toInt()}s", centerX, h - 100f, paint)
            canvas.drawText("Vitesse moy: ${speed.toInt()} km/h", centerX, h - 50f, paint)
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            // Fond résultats
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
            
            // Score final
            paint.color = Color.BLACK
            paint.textSize = 80f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore}", w/2f, h * 0.25f, paint)
            
            paint.textSize = 30f
            canvas.drawText("POINTS", w/2f, h * 0.35f, paint)
            
            // Détails de performance
            paint.color = Color.parseColor("#001122")
            paint.textSize = 24f
            canvas.drawText("🚀 Poussée: ${(pushQuality * 100).toInt()}%", w/2f, h * 0.5f, paint)
            canvas.drawText("🎮 Réflexes: ${(playerReactionAccuracy * 100).toInt()}%", w/2f, h * 0.55f, paint)
            canvas.drawText("🏆 Virages parfaits: ${perfectTurns}", w/2f, h * 0.6f, paint)
            canvas.drawText("🕒 Temps: ${raceTime.toInt()}s", w/2f, h * 0.65f, paint)
            canvas.drawText("⚡ Vitesse moy: ${speed.toInt()} km/h", w/2f, h * 0.7f, paint)
        }
    }

    // Classes de données pour le circuit
    data class TrackSegment(
        val type: TrackType,
        val duration: Float // Durée en secondes
    )

    enum class TrackType {
        STRAIGHT,
        LEFT_CURVE,
        RIGHT_CURVE,
        FINISH_LINE
    }

    enum class GameState {
        PREPARATION, PUSH_START, CONTROL_DESCENT, FINISH_LINE, CELEBRATION, RESULTS, FINISHED
    }
}
