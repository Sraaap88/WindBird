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
    private val controlDescentDuration = 90f
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
    
    // Circuit simple
    private var trackPosition = 0f // Position sur le circuit (0.0 à 1.0)
    private val trackCurves = mutableListOf<Float>() // Séquence de virages
    
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
    
    // Variables pour sprite-sheet Winter Games
    private var currentFrameIndex = 0
    private var frameTimer = 0f
    private var isReversing = false
    private var trackSection = TrackSection.STRAIGHT
    private var landscapeOffset = 0f
    
    enum class TrackSection {
        STRAIGHT, LEFT_TURN, RIGHT_TURN, LEFT_RETURN, RIGHT_RETURN
    }

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
        currentFrameIndex = 0
        frameTimer = 0f
        isReversing = false
        trackSection = TrackSection.STRAIGHT
        landscapeOffset = 0f
        
        generateTrackCurves()
    }
    
    private fun generateTrackCurves() {
        trackCurves.clear()
        
        if (practiceMode) {
            kotlin.random.Random(12345).let { fixedRandom ->
                generateRandomTrack(fixedRandom)
            }
        } else {
            kotlin.random.Random(eventIndex.toLong()).let { tournamentRandom ->
                generateRandomTrack(tournamentRandom)
            }
        }
    }
    
    private fun generateRandomTrack(random: kotlin.random.Random) {
        val trackLength = 75
        
        trackCurves.add(0f) // Départ droit
        trackCurves.add(0f)
        trackCurves.add(0f)
        
        var lastCurve = 0f
        
        for (i in 3 until trackLength - 3) {
            val newCurve = when (random.nextInt(10)) {
                0 -> -0.9f + random.nextFloat() * 0.1f // Virage FORT gauche
                1 -> 0.8f + random.nextFloat() * 0.2f  // Virage FORT droite
                2, 3 -> -0.6f + random.nextFloat() * 0.1f // Virage MOYEN gauche
                4, 5 -> 0.5f + random.nextFloat() * 0.2f  // Virage MOYEN droite
                6, 7 -> lastCurve * 0.6f // Transition douce
                else -> 0f               // Ligne droite
            }
            
            val smoothedCurve = if (abs(newCurve - lastCurve) > 0.7f) {
                lastCurve + (newCurve - lastCurve) * 0.6f
            } else {
                newCurve
            }
            
            trackCurves.add(smoothedCurve)
            lastCurve = smoothedCurve
        }
        
        trackCurves.add(0f) // Fin droite
        trackCurves.add(0f)
        trackCurves.add(0f)
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
        if (gameState != GameState.PREPARATION && gameState != GameState.FINISH_LINE && gameState != GameState.CELEBRATION && gameState != GameState.RESULTS) {
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
        pushPower = pushPower.coerceAtMost(150f)
        pushRhythm = pushRhythm.coerceAtMost(150f)
        
        if (phaseTimer >= pushStartDuration) {
            pushQuality = (pushPower * 0.6f + pushRhythm * 0.4f) / 150f
            baseSpeed = 60f + (pushQuality * 90f)
            speed = baseSpeed
            
            gameState = GameState.CONTROL_DESCENT
            phaseTimer = 0f
            cameraShake = 0.5f
        }
    }
    
    private fun handleControlDescent() {
        updateTrackProgress()
        updatePlayerReaction()
        updateLandscapeScrolling()
        
        val speedMultiplier = 0.7f + (playerReactionAccuracy * 0.6f)
        speed = baseSpeed * speedMultiplier
        speed = speed.coerceAtMost(maxSpeed)
        
        if (trackPosition >= 1f) {
            gameState = GameState.FINISH_LINE
            phaseTimer = 0f
            cameraShake = 0.8f
        }
    }
    
    private fun updateTrackProgress() {
        val progressSpeed = speed / 8000f
        trackPosition += progressSpeed * 0.025f
        trackPosition = trackPosition.coerceAtMost(1f)
    }
    
    private fun updatePlayerReaction() {
        // Réaction basée sur le virage actuel du parcours
        val trackIndex = (trackPosition * (trackCurves.size - 1)).toInt()
        val currentTrackCurve = if (trackIndex < trackCurves.size) trackCurves[trackIndex] else 0f
        
        val idealReaction = when {
            currentTrackCurve < -0.75f -> -1.5f  // Fort gauche
            currentTrackCurve < -0.4f -> -0.8f   // Moyen gauche
            currentTrackCurve > 0.75f -> 1.5f    // Fort droite
            currentTrackCurve > 0.4f -> 0.8f     // Moyen droite
            else -> 0f                           // Droit
        }
        
        val reactionError = abs(tiltZ - idealReaction)
        playerReactionAccuracy = (1f - reactionError / 3f).coerceIn(0.2f, 1f)
        
        val perfectThreshold = when {
            abs(currentTrackCurve) > 0.75f -> 0.4f
            abs(currentTrackCurve) > 0.4f -> 0.3f
            else -> 0.2f
        }
        
        if (reactionError < perfectThreshold && abs(currentTrackCurve) > 0.2f) {
            perfectTurns++
        }
        
        if (reactionError > 1.2f && abs(currentTrackCurve) > 0.2f) {
            wallHits++
            cameraShake = 0.3f
        }
    }
    
    private fun updateLandscapeScrolling() {
        val scrollSpeed = speed * 0.02f
        landscapeOffset += scrollSpeed
        if (landscapeOffset > 1000f) landscapeOffset -= 1000f
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
            GameState.PUSH_START -> "🚀 ${tournamentData.playerNames[currentPlayerIndex]} | Puissance: ${pushPower.toInt()}% | Coups: ${pushCount} | ${(pushStartDuration - phaseTimer).toInt() + 1}s"
            GameState.CONTROL_DESCENT -> "🎮 ${tournamentData.playerNames[currentPlayerIndex]} | Réflexes: ${(playerReactionAccuracy * 100).toInt()}% | ${speed.toInt()} km/h"
            GameState.FINISH_LINE -> "🏁 ${tournamentData.playerNames[currentPlayerIndex]} | Ligne d'arrivée: ${speed.toInt()} km/h!"
            GameState.CELEBRATION -> "🎉 ${tournamentData.playerNames[currentPlayerIndex]} | Temps: ${raceTime.toInt()}s!"
            GameState.RESULTS -> "🏆 ${tournamentData.playerNames[currentPlayerIndex]} | Score: ${finalScore}"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Course terminée!"
        }
    }

    private fun getCountryFlag(country: String): String {
        return when (country.uppercase()) {
            "CANADA" -> "🇨🇦"
            "FRANCE" -> "🇫🇷"
            "USA" -> "🇺🇸"
            "NORVÈGE" -> "🇳🇴"
            "JAPON" -> "🇯🇵"
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
        
        // Images de la piste sprite
        private var bobtrackLeftSpriteBitmap: Bitmap? = null
        
        // Images des drapeaux
        private var flagCanadaBitmap: Bitmap? = null
        private var flagUsaBitmap: Bitmap? = null
        private var flagFranceBitmap: Bitmap? = null
        private var flagNorvegeBitmap: Bitmap? = null
        private var flagJapanBitmap: Bitmap? = null
        
        // Variables pour découper le sprite-sheet
        private var spriteFrameWidth = 0
        private var spriteFrameHeight = 0
        private val totalFrames = 9 // Nombre d'images dans le sprite-sheet
        
        init {
            try {
                bobsledPreparationBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobsled_preparation)
                bobPushBitmap = BitmapFactory.decodeResource(resources, R.drawable.bob_push)
                bobStraightBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobnv_straight)
                bobLeftBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobnv_left)
                bobRightBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobnv_right)
                bobFinishLineBitmap = BitmapFactory.decodeResource(resources, R.drawable.bob_finish_line)
                bobCelebrationBitmap = BitmapFactory.decodeResource(resources, R.drawable.bob_celebration)
                
                // Charger le sprite-sheet de la piste
                bobtrackLeftSpriteBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobtrack_left_sprite)
                bobtrackLeftSpriteBitmap?.let { sprite ->
                    spriteFrameWidth = sprite.width / totalFrames
                    spriteFrameHeight = sprite.height
                }
                
                // Charger les drapeaux
                flagCanadaBitmap = BitmapFactory.decodeResource(resources, R.drawable.flag_canada)
                flagUsaBitmap = BitmapFactory.decodeResource(resources, R.drawable.flag_usa)
                flagFranceBitmap = BitmapFactory.decodeResource(resources, R.drawable.flag_france)
                flagNorvegeBitmap = BitmapFactory.decodeResource(resources, R.drawable.flag_norvege)
                flagJapanBitmap = BitmapFactory.decodeResource(resources, R.drawable.flag_japan)
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

        // Fonction pour extraire une frame du sprite-sheet
        private fun getTrackSpriteFrame(frameIndex: Int, mirrorHorizontal: Boolean = false, reverse: Boolean = false): Bitmap? {
            return bobtrackLeftSpriteBitmap?.let { sprite ->
                val actualFrameIndex = if (reverse) {
                    (totalFrames - 1 - frameIndex).coerceIn(0, totalFrames - 1)
                } else {
                    frameIndex.coerceIn(0, totalFrames - 1)
                }
                
                val sourceRect = Rect(
                    actualFrameIndex * spriteFrameWidth,
                    0,
                    (actualFrameIndex + 1) * spriteFrameWidth,
                    spriteFrameHeight
                )
                
                val frameBitmap = Bitmap.createBitmap(sprite, sourceRect.left, sourceRect.top, sourceRect.width(), sourceRect.height())
                
                if (mirrorHorizontal) {
                    val matrix = Matrix().apply { postScale(-1f, 1f) }
                    Bitmap.createBitmap(frameBitmap, 0, 0, frameBitmap.width, frameBitmap.height, matrix, false)
                } else {
                    frameBitmap
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (gameState == GameState.PUSH_START) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        val pushProgress = (pushPower / 100f).coerceIn(0f, 1f)
                        val bobX = 150f + pushProgress * (width - 300f)
                        val bobY = height * 0.65f
                        val touchRadius = 92f
                        
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
                            
                            pushPower = pushPower.coerceAtMost(150f)
                            pushRhythm = pushRhythm.coerceAtMost(150f)
                            
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
                GameState.CONTROL_DESCENT -> drawWinterGamesSystem(canvas, w, h)
                GameState.FINISH_LINE -> drawFinishLine(canvas, w, h)
                GameState.CELEBRATION -> drawCelebration(canvas, w, h)
                GameState.RESULTS -> drawResults(canvas, w, h)
                GameState.FINISHED -> drawResults(canvas, w, h)
            }
            
            if (cameraShake > 0f) {
                canvas.restore()
            }
        }
        
        // VRAI SYSTÈME WINTER GAMES 1985
        private fun drawWinterGamesSystem(canvas: Canvas, w: Int, h: Int) {
            val midHeight = h / 2f
            
            // 1. PAYSAGE HIVERNAL EN HAUT (moitié haute)
            drawWinterLandscape(canvas, w, midHeight.toInt())
            
            // 2. FOND SPRITE-SHEET EN BAS (moitié basse)
            drawTrackSpriteBackground(canvas, w, h, midHeight)
            
            // 3. BOBSLEIGH CENTRÉ PAR-DESSUS
            drawCenteredBobsled(canvas, w, h, midHeight)
            
            // 4. Interface
            drawInterface(canvas, w, h)
        }
        
        private fun drawWinterLandscape(canvas: Canvas, w: Int, horizonHeight: Int) {
            // Ciel hivernal
            val skyGradient = LinearGradient(
                0f, 0f, 0f, horizonHeight.toFloat(),
                Color.rgb(240, 248, 255),
                Color.rgb(200, 220, 245),
                Shader.TileMode.CLAMP
            )
            paint.shader = skyGradient
            canvas.drawRect(0f, 0f, w.toFloat(), horizonHeight.toFloat(), paint)
            paint.shader = null
            
            // Montagnes qui bougent selon les virages
            val trackIndex = (trackPosition * (trackCurves.size - 1)).toInt()
            val currentTrackCurve = if (trackIndex < trackCurves.size) trackCurves[trackIndex] else 0f
            val mountainShift = landscapeOffset + currentTrackCurve * w * 0.4f
            
            // Montagnes arrière
            paint.color = Color.rgb(180, 190, 210)
            val backMountains = Path().apply {
                moveTo(-mountainShift * 0.2f, horizonHeight.toFloat())
                lineTo(w * 0.3f - mountainShift * 0.2f, horizonHeight * 0.2f)
                lineTo(w * 0.7f - mountainShift * 0.2f, horizonHeight * 0.3f)
                lineTo(w + 100f - mountainShift * 0.2f, horizonHeight * 0.25f)
                lineTo(w + 100f, horizonHeight.toFloat())
                close()
            }
            canvas.drawPath(backMountains, paint)
            
            // Montagnes proches
            paint.color = Color.rgb(220, 230, 240)
            val frontMountains = Path().apply {
                moveTo(-mountainShift * 0.6f, horizonHeight.toFloat())
                lineTo(w * 0.2f - mountainShift * 0.6f, horizonHeight * 0.5f)
                lineTo(w * 0.8f - mountainShift * 0.6f, horizonHeight * 0.4f)
                lineTo(w.toFloat() - mountainShift * 0.6f, horizonHeight * 0.6f)
                lineTo(w.toFloat(), horizonHeight.toFloat())
                close()
            }
            canvas.drawPath(frontMountains, paint)
            
            // Sapins
            val treeShift = landscapeOffset * 1.5f + currentTrackCurve * w * 0.5f
            for (i in 0..10) {
                val treeX = (w * i / 6f - treeShift) % (w + 150f) - 75f
                val treeY = horizonHeight * (0.7f + sin(i.toFloat()) * 0.1f)
                
                paint.color = Color.rgb(20, 60, 20)
                val treeSize = 20f + (i % 3) * 8f
                canvas.drawRect(treeX - 1.5f, treeY, treeX + 1.5f, treeY + treeSize, paint)
                
                for (layer in 0..2) {
                    val layerY = treeY + layer * treeSize / 4f
                    val layerWidth = treeSize * (0.7f - layer * 0.15f)
                    val trianglePath = Path().apply {
                        moveTo(treeX, layerY - layerWidth/3f)
                        lineTo(treeX - layerWidth/2f, layerY + layerWidth/3f)
                        lineTo(treeX + layerWidth/2f, layerY + layerWidth/3f)
                        close()
                    }
                    canvas.drawPath(trianglePath, paint)
                    
                    paint.color = Color.WHITE
                    canvas.drawRect(treeX - layerWidth/2f, layerY - 1f, treeX + layerWidth/2f, layerY + 1f, paint)
                    paint.color = Color.rgb(20, 60, 20)
                }
            }
        }
        
        private fun drawTrackSpriteBackground(canvas: Canvas, w: Int, h: Int, startY: Float) {
            updateTrackFrame()
            
            val currentFrame = getCurrentTrackFrame()
            
            // DESSINER LE SPRITE COMME FOND FIXE
            currentFrame?.let { frame ->
                val dstRect = RectF(0f, startY, w.toFloat(), h.toFloat())
                canvas.drawBitmap(frame, null, dstRect, paint)
            } ?: run {
                // Fallback
                paint.color = Color.WHITE
                canvas.drawRect(0f, startY, w.toFloat(), h.toFloat(), paint)
                
                paint.color = Color.rgb(200, 200, 200)
                canvas.drawRect(0f, startY, w * 0.15f, h.toFloat(), paint)
                canvas.drawRect(w * 0.85f, startY, w.toFloat(), h.toFloat(), paint)
            }
        }
        
        private fun updateTrackFrame() {
            val frameSpeed = when {
                speed > 120f -> 0.03f
                speed > 80f -> 0.05f
                speed > 40f -> 0.08f
                else -> 0.12f
            }
            
            frameTimer += frameSpeed
            
            if (frameTimer >= 1f) {
                frameTimer = 0f
                
                val trackIndex = (trackPosition * (trackCurves.size - 1)).toInt()
                val currentTrackCurve = if (trackIndex < trackCurves.size) trackCurves[trackIndex] else 0f
                
                when {
                    abs(currentTrackCurve) < 0.3f -> {
                        trackSection = TrackSection.STRAIGHT
                        currentFrameIndex = if (currentFrameIndex == 0) 1 else 0
                        isReversing = false
                    }
                    
                    currentTrackCurve < -0.3f -> {
                        if (trackSection != TrackSection.LEFT_TURN && trackSection != TrackSection.LEFT_RETURN) {
                            trackSection = TrackSection.LEFT_TURN
                            currentFrameIndex = 2
                            isReversing = false
                        }
                        
                        if (trackSection == TrackSection.LEFT_TURN && !isReversing) {
                            currentFrameIndex++
                            if (currentFrameIndex >= totalFrames - 1) {
                                trackSection = TrackSection.LEFT_RETURN
                                isReversing = true
                            }
                        } else if (trackSection == TrackSection.LEFT_RETURN && isReversing) {
                            currentFrameIndex--
                            if (currentFrameIndex <= 1) {
                                trackSection = TrackSection.STRAIGHT
                                currentFrameIndex = 0
                                isReversing = false
                            }
                        }
                    }
                    
                    currentTrackCurve > 0.3f -> {
                        if (trackSection != TrackSection.RIGHT_TURN && trackSection != TrackSection.RIGHT_RETURN) {
                            trackSection = TrackSection.RIGHT_TURN
                            currentFrameIndex = 2
                            isReversing = false
                        }
                        
                        if (trackSection == TrackSection.RIGHT_TURN && !isReversing) {
                            currentFrameIndex++
                            if (currentFrameIndex >= totalFrames - 1) {
                                trackSection = TrackSection.RIGHT_RETURN
                                isReversing = true
                            }
                        } else if (trackSection == TrackSection.RIGHT_RETURN && isReversing) {
                            currentFrameIndex--
                            if (currentFrameIndex <= 1) {
                                trackSection = TrackSection.STRAIGHT
                                currentFrameIndex = 0
                                isReversing = false
                            }
                        }
                    }
                }
            }
        }
        
        private fun getCurrentTrackFrame(): Bitmap? {
            return when (trackSection) {
                TrackSection.STRAIGHT -> {
                    getTrackSpriteFrame(currentFrameIndex, false, false)
                }
                TrackSection.LEFT_TURN, TrackSection.LEFT_RETURN -> {
                    getTrackSpriteFrame(currentFrameIndex, false, false)
                }
                TrackSection.RIGHT_TURN, TrackSection.RIGHT_RETURN -> {
                    getTrackSpriteFrame(currentFrameIndex, true, false)
                }
            }
        }
        
        private fun drawCenteredBobsled(canvas: Canvas, w: Int, h: Int, trackStartY: Float) {
            val baseBobX = w / 2f
            val baseBobY = trackStartY + (h - trackStartY) * 0.6f
            val bobScale = 0.25f
            
            val trackIndex = (trackPosition * (trackCurves.size - 1)).toInt()
            val currentTrackCurve = if (trackIndex < trackCurves.size) trackCurves[trackIndex] else 0f
            
            var bobOffsetX = 0f
            var bobRotation = 0f
            
            when {
                currentTrackCurve < -0.6f -> {
                    bobOffsetX = w * 0.08f
                    bobRotation = -20f
                }
                currentTrackCurve < -0.3f -> {
                    bobOffsetX = w * 0.05f
                    bobRotation = -10f
                }
                currentTrackCurve > 0.6f -> {
                    bobOffsetX = -w * 0.08f
                    bobRotation = 20f
                }
                currentTrackCurve > 0.3f -> {
                    bobOffsetX = -w * 0.05f
                    bobRotation = 10f
                }
                else -> {
                    bobOffsetX = 0f
                    bobRotation = 0f
                }
            }
            
            bobRotation += (tiltZ * 10f).coerceIn(-30f, 30f)
            
            val bobX = baseBobX + bobOffsetX
            val bobY = baseBobY
            
            val bobSprite = when {
                currentTrackCurve < -0.3f -> bobLeftBitmap
                currentTrackCurve > 0.3f -> bobRightBitmap
                else -> bobStraightBitmap
            }
            
            bobSprite?.let { bmp ->
                val dstRect = RectF(
                    bobX - bmp.width * bobScale / 2f,
                    bobY - bmp.height * bobScale / 2f,
                    bobX + bmp.width * bobScale / 2f,
                    bobY + bmp.height * bobScale / 2f
                )
                
                if (abs(bobRotation) > 3f) {
                    canvas.save()
                    canvas.rotate(bobRotation, bobX, bobY)
                    canvas.drawBitmap(bmp, null, dstRect, paint)
                    canvas.restore()
                } else {
                    canvas.drawBitmap(bmp, null, dstRect, paint)
                }
            } ?: run {
                paint.color = when {
                    currentTrackCurve < -0.3f -> Color.GREEN
                    currentTrackCurve > 0.3f -> Color.BLUE
                    else -> Color.YELLOW
                }
                
                if (abs(bobRotation) > 3f) {
                    canvas.save()
                    canvas.rotate(bobRotation, bobX, bobY)
                }
                
                canvas.drawRoundRect(bobX - 25f, bobY - 15f, bobX + 25f, bobY + 15f, 8f, 8f, paint)
                
                if (abs(bobRotation) > 3f) {
                    canvas.restore()
                }
            }
            
            paint.color = Color.argb(120, 0, 0, 0)
            canvas.drawOval(bobX - 20f, bobY + 15f, bobX + 20f, bobY + 25f, paint)
        }
        
        private fun drawInterface(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.BLACK
            paint.textSize = 60f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("${speed.toInt()} KM/H", 30f, 80f, paint)
        }
        
        private fun drawFinishLine(canvas: Canvas, w: Int, h: Int) {
            drawWinterGamesSystem(canvas, w, h)
            
            paint.color = Color.YELLOW
            paint.textSize = 80f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🏁 FINISH! 🏁", w/2f, h * 0.3f, paint)
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
            
            val playerCountry = if (practiceMode) {
                "CANADA"
            } else {
                tournamentData.playerCountries[currentPlayerIndex]
            }
            
            val flagBitmap = when (playerCountry.uppercase()) {
                "CANADA" -> flagCanadaBitmap
                "USA" -> flagUsaBitmap
                "FRANCE" -> flagFranceBitmap
                "NORVÈGE" -> flagNorvegeBitmap
                "JAPON" -> flagJapanBitmap
                else -> flagCanadaBitmap
            }
            
            flagBitmap?.let { flag ->
                val flagWidth = flagRect.width() - 10f
                val flagHeight = flagRect.height() - 10f
                
                val imageRatio = flag.width.toFloat() / flag.height.toFloat()
                val rectRatio = flagWidth / flagHeight
                
                val finalWidth: Float
                val finalHeight: Float
                
                if (imageRatio > rectRatio) {
                    finalWidth = flagWidth
                    finalHeight = flagWidth / imageRatio
                } else {
                    finalHeight = flagHeight
                    finalWidth = flagHeight * imageRatio
                }
                
                val centerX = flagRect.centerX()
                val centerY = flagRect.centerY()
                
                val flagImageRect = RectF(
                    centerX - finalWidth / 2f,
                    centerY - finalHeight / 2f,
                    centerX + finalWidth / 2f,
                    centerY + finalHeight / 2f
                )
                canvas.drawBitmap(flag, null, flagImageRect, paint)
            } ?: run {
                val flag = getCountryFlag(playerCountry)
                paint.color = Color.BLACK
                paint.textSize = 120f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(flag, flagRect.centerX(), flagRect.centerY() + 40f, paint)
            }
            
            paint.color = Color.BLACK
            paint.textSize = 28f
            paint.textAlign = Paint.Align.CENTER
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
            val trackY = h * 0.65f
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
            
            val pushProgress = (pushPower / 100f).coerceIn(0f, 1f)
            val bobX = 150f + pushProgress * (w - 300f)
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
            canvas.drawRoundRect(w/2f - 400f, 120f, w/2f + 400f, 220f, 10f, 10f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 80f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("TAPEZ SUR LE BOBSLEIGH POUR LE POUSSER!", w/2f, 180f, paint)
            
            paint.color = Color.argb(200, 0, 0, 0)
            canvas.drawRoundRect(w/2f - 200f, h - 150f, w/2f + 200f, h - 40f, 10f, 10f, paint)
            
            paint.color = Color.GRAY
            canvas.drawRect(w/2f - 180f, h - 120f, w/2f + 180f, h - 80f, paint)
            
            paint.color = Color.GREEN
            val powerWidth = (pushPower.coerceAtMost(150f) / 150f) * 360f
            canvas.drawRect(w/2f - 180f, h - 120f, w/2f - 180f + powerWidth, h - 80f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 80f
            canvas.drawText("PUISSANCE: ${pushPower.toInt()}% | Coups: ${pushCount}", w/2f, h - 50f, paint)
            
            paint.color = Color.argb(200, 255, 0, 0)
            canvas.drawRoundRect(w - 140f, 60f, w - 20f, 160f, 10f, 10f, paint)
            
            paint.textSize = 56f
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${(pushStartDuration - phaseTimer).toInt() + 1}s", w - 80f, 130f, paint)
        }
        
        private fun drawCelebration(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            val progress = phaseTimer / celebrationDuration
            
            val bobCelebrationX = if (progress < 0.7f) {
                val moveProgress = (progress / 0.7f)
                val easedProgress = 1f - (1f - moveProgress) * (1f - moveProgress)
                -200f + easedProgress * (w/2f + 200f)
            } else {
                w/2f
            }
            
            val bobCelebrationY = h/2f
            
            for (i in 0..15) {
                val angle = (2.0 * PI / 15 * i + progress * 6).toFloat()
                val radius = progress * 200f
                val particleX = bobCelebrationX + cos(angle) * radius
                val particleY = bobCelebrationY + sin(angle) * radius
                
                paint.color = Color.WHITE
                canvas.drawCircle(particleX, particleY, 6f, paint)
            }
            
            bobCelebrationBitmap?.let { bmp ->
                val scale = 0.4f
                val dstRect = RectF(
                    bobCelebrationX - bmp.width * scale / 2f,
                    bobCelebrationY - bmp.height * scale / 2f,
                    bobCelebrationX + bmp.width * scale / 2f,
                    bobCelebrationY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            paint.color = Color.BLACK
            paint.textSize = 60f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🎉 BRAVO! 🎉", w/2f, 150f, paint)
            
            paint.textSize = 40f
            canvas.drawText("Temps: ${raceTime.toInt()}s", w/2f, h - 100f, paint)
            canvas.drawText("Vitesse moy: ${speed.toInt()} km/h", w/2f, h - 50f, paint)
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
            
            paint.color = Color.BLACK
            paint.textSize = 120f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore}", w/2f, h * 0.25f, paint)
            
            paint.textSize = 50f
            canvas.drawText("POINTS", w/2f, h * 0.35f, paint)
            
            paint.color = Color.parseColor("#001122")
            paint.textSize = 48f
            canvas.drawText("🚀 Poussée: ${(pushQuality * 100).toInt()}%", w/2f, h * 0.5f, paint)
            canvas.drawText("🎮 Réflexes: ${(playerReactionAccuracy * 100).toInt()}%", w/2f, h * 0.56f, paint)
            canvas.drawText("🏆 Virages parfaits: ${perfectTurns}", w/2f, h * 0.62f, paint)
            canvas.drawText("🕒 Temps: ${raceTime.toInt()}s", w/2f, h * 0.68f, paint)
            canvas.drawText("⚡ Vitesse moy: ${speed.toInt()} km/h", w/2f, h * 0.74f, paint)
            canvas.drawText("💥 Impacts murs: ${wallHits}", w/2f, h * 0.8f, paint)
        }
    }

    enum class GameState {
        PREPARATION, PUSH_START, CONTROL_DESCENT, FINISH_LINE, CELEBRATION, RESULTS, FINISHED
    }
}
