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
    
    // Durées - TRAJET 3X PLUS LONG
    private val preparationDuration = 6f
    private val pushStartDuration = 8f
    private val controlDescentDuration = 90f // 3X PLUS LONG (était 30f)
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
    
    // NOUVEAU : Variables pour la piste sprite
    private var trackScrollOffset = 0f
    private var landscapeOffset = 0f

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
        trackScrollOffset = 0f
        landscapeOffset = 0f
        
        generateTrackCurves()
    }
    
    private fun generateTrackCurves() {
        trackCurves.clear()
        
        if (practiceMode) {
            // MODE PRATIQUE : Toujours le même circuit aléatoire mais fixe
            kotlin.random.Random(12345).let { fixedRandom ->
                generateRandomTrack(fixedRandom)
            }
        } else {
            // MODE TOURNOI : Même circuit pour tous les joueurs du même événement
            // Utilise eventIndex comme seed pour que tous les joueurs aient le même circuit
            kotlin.random.Random(eventIndex.toLong()).let { tournamentRandom ->
                generateRandomTrack(tournamentRandom)
            }
        }
    }
    
    private fun generateRandomTrack(random: kotlin.random.Random) {
        // Circuit 3X plus long avec virages MOYENS et FORTS bien définis
        val trackLength = 75 // 3X plus long (était 25 segments)
        
        trackCurves.add(0f) // Départ toujours droit
        trackCurves.add(0f)
        trackCurves.add(0f)
        
        var lastCurve = 0f // Pour éviter les changements trop brusques
        
        for (i in 3 until trackLength - 3) {
            val newCurve = when (random.nextInt(10)) {
                // Virages FORTS (90 degrés requis) - Valeurs entre -0.8 et -1.0 ou 0.8 et 1.0
                0 -> -0.9f + random.nextFloat() * 0.1f // Virage FORT gauche
                1 -> 0.8f + random.nextFloat() * 0.2f  // Virage FORT droite
                
                // Virages MOYENS (45 degrés requis) - Valeurs entre -0.5 et -0.7 ou 0.5 et 0.7
                2, 3 -> -0.6f + random.nextFloat() * 0.1f // Virage MOYEN gauche
                4, 5 -> 0.5f + random.nextFloat() * 0.2f  // Virage MOYEN droite
                
                // Transitions douces et lignes droites
                6, 7 -> lastCurve * 0.6f // Transition douce (60% de l'ancien)
                else -> 0f               // Ligne droite
            }
            
            // Éviter les changements trop brusques entre FORT et MOYEN
            val smoothedCurve = if (abs(newCurve - lastCurve) > 0.7f) {
                lastCurve + (newCurve - lastCurve) * 0.6f // Transition plus progressive
            } else {
                newCurve
            }
            
            trackCurves.add(smoothedCurve)
            lastCurve = smoothedCurve
        }
        
        // Fin toujours droite
        trackCurves.add(0f)
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
            raceTime += 0.025f // Le timer s'arrête à la ligne d'arrivée
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
        pushPower = pushPower.coerceAtMost(150f) // Permet de dépasser 100% jusqu'à 150%
        pushRhythm = pushRhythm.coerceAtMost(150f) // Permet de dépasser 100% jusqu'à 150%
        
        if (phaseTimer >= pushStartDuration) {
            pushQuality = (pushPower * 0.6f + pushRhythm * 0.4f) / 150f // Ajusté pour 150% max
            baseSpeed = 60f + (pushQuality * 90f) // 60-150 km/h de base possible
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
        
        // NOUVEAU : Mise à jour des offsets pour la piste sprite
        updateTrackScrolling()
        
        if (trackPosition >= 1f) {
            gameState = GameState.FINISH_LINE
            phaseTimer = 0f
            cameraShake = 0.8f
            // ARRÊTER LE TIMER DE COURSE ICI
            // raceTime reste figé à cette valeur
        }
    }
    
    // Variables pour la gestion des frames (ajoutées au début de la classe)
    private var currentFrameIndex = 0
    private var frameTimer = 0f
    private var isReversing = false
    private var trackSection = TrackSection.STRAIGHT
    
    enum class TrackSection {
        STRAIGHT, LEFT_TURN, RIGHT_TURN, LEFT_RETURN, RIGHT_RETURN
    }
    
    // NOUVEAU : Fonction pour gérer le défilement des paysages (Winter Games style)
    private fun updateTrackScrolling() {
        // Défilement du paysage seulement (plus de trackScrollOffset pour les sprites)
        val scrollSpeed = speed * 0.02f
        landscapeOffset += scrollSpeed
        
        // Reset pour éviter les valeurs trop grandes
        if (landscapeOffset > 1000f) landscapeOffset -= 1000f
    }
    
    private fun updateTrackProgress() {
        val progressSpeed = speed / 8000f // RALENTI pour virages plus longs (était 6000f)
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
        // Calcul de la réaction idéale selon le TYPE de virage
        val curveType = getCurveType(currentCurveIntensity)
        val idealReaction = when (curveType) {
            CurveType.STRONG_LEFT -> -1.5f  // 90 degrés gauche
            CurveType.MEDIUM_LEFT -> -0.8f  // 45 degrés gauche
            CurveType.STRAIGHT -> 0f        // Rester droit
            CurveType.MEDIUM_RIGHT -> 0.8f  // 45 degrés droite
            CurveType.STRONG_RIGHT -> 1.5f  // 90 degrés droite
        }
        
        // Comparaison avec la réaction du joueur
        val reactionError = abs(tiltZ - idealReaction)
        playerReactionAccuracy = (1f - reactionError / 3f).coerceIn(0.2f, 1f) // Ajusté pour les nouveaux angles
        
        // Bonus pour les virages parfaits (tolérance selon le type de virage)
        val perfectThreshold = when (curveType) {
            CurveType.STRONG_LEFT, CurveType.STRONG_RIGHT -> 0.4f // Plus de tolérance pour virages forts
            CurveType.MEDIUM_LEFT, CurveType.MEDIUM_RIGHT -> 0.3f // Tolérance normale pour virages moyens
            CurveType.STRAIGHT -> 0.2f // Peu de tolérance pour ligne droite
        }
        
        if (reactionError < perfectThreshold && curveType != CurveType.STRAIGHT) {
            perfectTurns++
        }
        
        // Détection des impacts avec les murs
        if (reactionError > 1.2f && curveType != CurveType.STRAIGHT) {
            wallHits++
            cameraShake = 0.3f
        }
    }
    
    // Nouvelle fonction pour déterminer le type de virage
    private fun getCurveType(intensity: Float): CurveType {
        return when {
            intensity <= -0.75f -> CurveType.STRONG_LEFT
            intensity <= -0.4f -> CurveType.MEDIUM_LEFT
            intensity >= 0.75f -> CurveType.STRONG_RIGHT
            intensity >= 0.4f -> CurveType.MEDIUM_RIGHT
            else -> CurveType.STRAIGHT
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

    // Enum pour les types de virages
    enum class CurveType {
        STRONG_LEFT, MEDIUM_LEFT, STRAIGHT, MEDIUM_RIGHT, STRONG_RIGHT
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
        
        // NOUVEAU : Images de la piste sprite
        private var bobtrackLeftSpriteBitmap: Bitmap? = null
        private var bobtrackStraightBitmap: Bitmap? = null // Pour ligne droite (ou on prend les 2 premières du left)
        
        // Images des drapeaux
        private var flagCanadaBitmap: Bitmap? = null
        private var flagUsaBitmap: Bitmap? = null
        private var flagFranceBitmap: Bitmap? = null
        private var flagNorvegeBitmap: Bitmap? = null
        private var flagJapanBitmap: Bitmap? = null
        
        // NOUVEAU : Variables pour découper le sprite-sheet
        private var spriteFrameWidth = 0
        private var spriteFrameHeight = 0
        private var totalFrames = 9 // Nombre d'images dans le sprite-sheet
        
        init {
            try {
                bobsledPreparationBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobsled_preparation)
                bobPushBitmap = BitmapFactory.decodeResource(resources, R.drawable.bob_push)
                bobStraightBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobnv_straight)
                bobLeftBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobnv_left)
                bobRightBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobnv_right)
                bobFinishLineBitmap = BitmapFactory.decodeResource(resources, R.drawable.bob_finish_line)
                bobCelebrationBitmap = BitmapFactory.decodeResource(resources, R.drawable.bob_celebration)
                
                // NOUVEAU : Charger le sprite-sheet de la piste
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

        // NOUVEAU : Fonction pour extraire une frame du sprite-sheet
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

        // GESTION DU TACTILE POUR LA POUSSÉE - ZONE QUI SUIT LE BOBSLEIGH
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (gameState == GameState.PUSH_START) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        // Zone qui suit le bobsleigh qui bouge
                        val pushProgress = (pushPower / 100f).coerceIn(0f, 1f)
                        val bobX = 150f + pushProgress * (width - 300f)
                        val bobY = height * 0.65f // PLUS HAUT (était 0.7f)
                        val touchRadius = 92f // 15% plus grand (était 80f)
                        
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
                            
                            pushPower = pushPower.coerceAtMost(150f) // Permet de dépasser 100%
                            pushRhythm = pushRhythm.coerceAtMost(150f) // Permet de dépasser 100%
                            
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
                GameState.CONTROL_DESCENT -> drawNewTrackSystem(canvas, w, h)
                GameState.FINISH_LINE -> drawFinishLine(canvas, w, h)
                GameState.CELEBRATION -> drawCelebration(canvas, w, h)
                GameState.RESULTS -> drawResults(canvas, w, h)
                GameState.FINISHED -> drawResults(canvas, w, h)
            }
            
            if (cameraShake > 0f) {
                canvas.restore()
            }
        }
        
        // NOUVEAU : Système de rendu de piste avec sprites
        private fun drawNewTrackSystem(canvas: Canvas, w: Int, h: Int) {
            val horizonY = h * 0.5f // PLUS BAS - moitié de l'écran pour le paysage
            
            // 1. PAYSAGE DÉFILANT DYNAMIQUE - MOITIÉ SUPÉRIEURE DE L'ÉCRAN
            drawScrollingLandscape(canvas, w, horizonY.toInt())
            
            // 2. RENDU DE LA PISTE AVEC SPRITES - MOITIÉ INFÉRIEURE
            drawTrackWithSprites(canvas, w, h, horizonY)
            
            // 3. BOBSLEIGH AMÉLIORÉ AVEC BANKING
            drawImprovedBobsled(canvas, w, h)
            
            // 4. INTERFACE
            drawGameInterface(canvas, w, h)
        }
        
        // NOUVEAU : Paysage défilant réaliste dans la moitié supérieure
        private fun drawScrollingLandscape(canvas: Canvas, w: Int, horizonHeight: Int) {
            // Ciel hivernal avec dégradé blanc-bleu
            val skyGradient = LinearGradient(
                0f, 0f, 0f, horizonHeight.toFloat(),
                Color.rgb(240, 248, 255), // Blanc Alice en haut (hivernal)
                Color.rgb(220, 230, 255),  // Bleu très pâle vers l'horizon
                Shader.TileMode.CLAMP
            )
            paint.shader = skyGradient
            canvas.drawRect(0f, 0f, w.toFloat(), horizonHeight.toFloat(), paint)
            paint.shader = null
            
            // MONTAGNES ENNEIGÉES QUI BOUGENT SELON LES VIRAGES
            val curveRotation = currentCurveIntensity * 20f
            val mountainShift = landscapeOffset + currentCurveIntensity * w * 0.4f
            
            // Montagnes arrière (plus lentes, effet parallaxe) - Tons bleus pour distance
            paint.color = Color.rgb(180, 190, 210)
            val backMountains = Path().apply {
                moveTo(-mountainShift * 0.3f, horizonHeight.toFloat())
                lineTo(w * 0.2f - mountainShift * 0.3f, horizonHeight * 0.3f)
                lineTo(w * 0.5f - mountainShift * 0.3f, horizonHeight * 0.4f)
                lineTo(w * 0.8f - mountainShift * 0.3f, horizonHeight * 0.25f)
                lineTo(w + 200f - mountainShift * 0.3f, horizonHeight * 0.35f)
                lineTo(w + 200f, horizonHeight.toFloat())
                close()
            }
            canvas.drawPath(backMountains, paint)
            
            // Montagnes moyennes - Gris clair enneigé
            paint.color = Color.rgb(200, 210, 220)
            val midMountains = Path().apply {
                moveTo(-mountainShift * 0.6f, horizonHeight.toFloat())
                lineTo(w * 0.15f - mountainShift * 0.6f, horizonHeight * 0.5f)
                lineTo(w * 0.45f - mountainShift * 0.6f, horizonHeight * 0.3f)
                lineTo(w * 0.75f - mountainShift * 0.6f, horizonHeight * 0.45f)
                lineTo(w + 100f - mountainShift * 0.6f, horizonHeight * 0.4f)
                lineTo(w + 100f, horizonHeight.toFloat())
                close()
            }
            canvas.drawPath(midMountains, paint)
            
            // Montagnes proches (bougent le plus) - Blanc neigeux
            paint.color = Color.rgb(230, 235, 245)
            val frontMountains = Path().apply {
                moveTo(-mountainShift, horizonHeight.toFloat())
                lineTo(w * 0.25f - mountainShift, horizonHeight * 0.6f)
                lineTo(w * 0.6f - mountainShift, horizonHeight * 0.5f)
                lineTo(w * 0.9f - mountainShift, horizonHeight * 0.7f)
                lineTo(w.toFloat() - mountainShift, horizonHeight * 0.65f)
                lineTo(w.toFloat(), horizonHeight.toFloat())
                close()
            }
            canvas.drawPath(frontMountains, paint)
            
            // SAPINS ENNEIGÉS QUI DÉFILENT
            paint.color = Color.rgb(20, 60, 20) // Vert foncé pour les sapins
            val treeShift = landscapeOffset * 1.5f
            for (i in 0..12) {
                val treeX = (w * i / 8f - treeShift + currentCurveIntensity * w * 0.5f) % (w + 200f) - 100f
                val treeY = horizonHeight * (0.8f + sin(i.toFloat()) * 0.08f)
                val treeHeight = 35f + (i % 3) * 15f
                
                // Tronc brun
                paint.color = Color.rgb(100, 50, 20)
                canvas.drawRect(treeX - 2f, treeY, treeX + 2f, treeY + treeHeight, paint)
                
                // Sapin vert avec neige
                paint.color = Color.rgb(20, 60, 20)
                for (layer in 0..2) {
                    val layerY = treeY + layer * treeHeight / 3f
                    val layerWidth = treeHeight * (0.6f - layer * 0.15f)
                    canvas.drawRect(treeX - layerWidth/2f, layerY, treeX + layerWidth/2f, layerY + treeHeight/3f, paint)
                }
                
                // Neige sur les branches
                paint.color = Color.WHITE
                for (layer in 0..2) {
                    val layerY = treeY + layer * treeHeight / 3f
                    val layerWidth = treeHeight * (0.5f - layer * 0.12f)
                    canvas.drawRect(treeX - layerWidth/2f, layerY, treeX + layerWidth/2f, layerY + 3f, paint)
                }
            }
            
            // NUAGES BLANCS D'HIVER
            paint.color = Color.argb(200, 255, 255, 255)
            val cloudShift = landscapeOffset * 0.15f
            for (i in 0..5) {
                val cloudX = (w * i / 3f - cloudShift) % (w + 300f) - 150f
                val cloudY = horizonHeight * (0.1f + i * 0.1f)
                val cloudSize = 25f + i * 4f
                canvas.drawCircle(cloudX, cloudY, cloudSize, paint)
                canvas.drawCircle(cloudX + cloudSize * 0.8f, cloudY, cloudSize * 0.8f, paint)
                canvas.drawCircle(cloudX - cloudSize * 0.6f, cloudY, cloudSize * 0.7f, paint)
            }
        }
        
        // NOUVEAU : Fonction pour dessiner la piste avec les sprites
        private fun drawTrackWithSprites(canvas: Canvas, w: Int, h: Int, horizonY: Float) {
            val curveType = getCurveType(currentCurveIntensity)
            
            for (screenY in horizonY.toInt() until h step 4) { // Step plus petit pour plus de détails
                val lineProgress = (screenY - horizonY) / (h - horizonY)
                val z = 200f * (1f - lineProgress * 0.95f)
                val scaleFactor = 1f / z
                
                // Déterminer quel sprite utiliser selon le type de virage
                val spriteFrame = when (curveType) {
                    CurveType.STRAIGHT -> {
                        // Ligne droite : alterner entre frame 0 et 1
                        val frameIndex = ((trackScrollOffset + screenY * 0.1f).toInt() % 2)
                        getTrackSpriteFrame(frameIndex, false, false)
                    }
                    CurveType.MEDIUM_LEFT, CurveType.STRONG_LEFT -> {
                        // Virage gauche : utiliser la séquence normale
                        val frameIndex = ((trackScrollOffset + screenY * 0.05f).toInt() % totalFrames)
                        getTrackSpriteFrame(frameIndex, false, false)
                    }
                    CurveType.MEDIUM_RIGHT, CurveType.STRONG_RIGHT -> {
                        // Virage droite : utiliser la séquence en miroir
                        val frameIndex = ((trackScrollOffset + screenY * 0.05f).toInt() % totalFrames)
                        getTrackSpriteFrame(frameIndex, true, false)
                    }
                }
                
                // PISTE BEAUCOUP PLUS GROSSE - près de la moitié de l'écran
                val trackWidth = (w * 8f * scaleFactor).coerceAtMost(w * 12f) // ÉNORMÉMENT plus large
                val lookAheadDistance = (1f - lineProgress) * 5f
                val futurePosition = (trackPosition + lookAheadDistance / trackCurves.size.toFloat()) % 1f
                val futureIndex = (futurePosition * (trackCurves.size - 1)).toInt()
                val futureProgress = (futurePosition * (trackCurves.size - 1)) - futureIndex
                
                val futureCurve = if (futureIndex < trackCurves.size) {
                    val curve1 = trackCurves[futureIndex]
                    val curve2 = trackCurves[(futureIndex + 1) % trackCurves.size]
                    curve1 + (curve2 - curve1) * futureProgress
                } else {
                    0f
                }
                
                val trackCenterX = w/2f + futureCurve * w * 0.6f * scaleFactor // Réduction du déplacement horizontal
                
                // Dessiner le sprite de piste
                spriteFrame?.let { sprite ->
                    val spriteHeight = 6f // Hauteur fixe pour éviter la déformation
                    val dstRect = RectF(
                        trackCenterX - trackWidth/2f,
                        screenY.toFloat(),
                        trackCenterX + trackWidth/2f,
                        screenY + spriteHeight
                    )
                    canvas.drawBitmap(sprite, null, dstRect, paint)
                } ?: run {
                    // Fallback si le sprite ne charge pas
                    paint.color = Color.WHITE
                    canvas.drawRect(trackCenterX - trackWidth/2f, screenY.toFloat(), trackCenterX + trackWidth/2f, screenY + 6f, paint)
                }
            }
        }
        
        // NOUVEAU : Bobsleigh amélioré avec banking (montée sur les bords)
        private fun drawImprovedBobsled(canvas: Canvas, w: Int, h: Int) {
            val bobBaseX = w / 2f
            val baseBobY = h * 0.82f
            val bobScale = 0.16f
            
            var bobHorizontalOffset = 0f
            var bobVerticalOffset = 0f
            var bobRotation = 0f
            var bankingHeight = 0f // Déclaration de la variable
            
            val curveType = getCurveType(currentCurveIntensity)
            
            if (curveType != CurveType.STRAIGHT) {
                val centrifugalForce = abs(currentCurveIntensity)
                val speedFactor = (speed / maxSpeed).coerceIn(0.3f, 1f)
                
                // Déplacement horizontal (comme avant)
                bobHorizontalOffset = currentCurveIntensity * speedFactor * 80f
                
                // NOUVEAU : Banking - montée sur les bords
                bankingHeight = centrifugalForce * speedFactor * 60f // Plus prononcé
                bobVerticalOffset = -bankingHeight // Négatif = vers le haut
                
                // Rotation du bobsleigh
                val targetAngle = when (curveType) {
                    CurveType.STRONG_LEFT, CurveType.STRONG_RIGHT -> 75f // Virage fort = 75° max
                    CurveType.MEDIUM_LEFT, CurveType.MEDIUM_RIGHT -> 45f // Virage moyen = 45° max
                    else -> 0f
                }
                
                val climbAngle = (targetAngle + (centrifugalForce * speedFactor * 15f)).coerceAtMost(targetAngle + 15f)
                val additionalRotation = climbAngle - targetAngle
                bobRotation = if (currentCurveIntensity < 0f) -additionalRotation else additionalRotation
            }
            
            val bobX = bobBaseX + bobHorizontalOffset
            val bobY = baseBobY + bobVerticalOffset
            
            // Choisir le sprite du bobsleigh
            val bobSprite = when (curveType) {
                CurveType.STRONG_LEFT, CurveType.MEDIUM_LEFT -> bobLeftBitmap
                CurveType.STRONG_RIGHT, CurveType.MEDIUM_RIGHT -> bobRightBitmap
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
                    canvas.save()
                    canvas.rotate(bobRotation, bobX, bobY)
                    canvas.drawBitmap(bmp, null, dstRect, paint)
                    canvas.restore()
                } else {
                    canvas.drawBitmap(bmp, null, dstRect, paint)
                }
                
                // NOUVEAU : Ombre portée du bobsleigh sur la piste
                paint.color = Color.argb(100, 0, 0, 0)
                val shadowOffset = bankingHeight * 0.3f
                canvas.drawOval(bobX - 30f, bobY + 20f + shadowOffset, bobX + 30f, bobY + 35f + shadowOffset, paint)
                
            } ?: run {
                // Fallback
                paint.color = when (curveType) {
                    CurveType.STRONG_LEFT, CurveType.MEDIUM_LEFT -> Color.GREEN
                    CurveType.STRONG_RIGHT, CurveType.MEDIUM_RIGHT -> Color.BLUE
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
        }
        
        // Interface de jeu (identique à avant)
        private fun drawGameInterface(canvas: Canvas, w: Int, h: Int) {
            val curveType = getCurveType(currentCurveIntensity)
            
            // Vitesse
            paint.color = Color.BLACK
            paint.textSize = 120f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("${speed.toInt()} KM/H", 30f, 150f, paint)
            
            // Instructions de virage avec symboles colorés
            if (curveType != CurveType.STRAIGHT) {
                paint.color = Color.argb(220, 0, 0, 0)
                canvas.drawRoundRect(w/8f, 200f, w*7f/8f, 420f, 25f, 25f, paint)
                
                paint.textSize = 200f
                paint.textAlign = Paint.Align.CENTER
                
                val directionSymbol = when (curveType) {
                    CurveType.STRONG_LEFT -> "⬅️🔴"
                    CurveType.MEDIUM_LEFT -> "⬅️🟡"
                    CurveType.STRONG_RIGHT -> "🔴➡️"
                    CurveType.MEDIUM_RIGHT -> "🟡➡️"
                    else -> ""
                }
                
                canvas.drawText(directionSymbol, w/2f, 320f, paint)
                
                paint.textSize = 100f
                paint.color = when {
                    playerReactionAccuracy > 0.8f -> Color.GREEN
                    playerReactionAccuracy > 0.6f -> Color.YELLOW
                    else -> Color.RED
                }
                canvas.drawText("${(playerReactionAccuracy * 100).toInt()}%", w/2f, 390f, paint)
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
            
            // Rectangle blanc pour le drapeau
            paint.color = Color.WHITE
            val flagRect = RectF(50f, 50f, 300f, 200f)
            canvas.drawRect(flagRect, paint)
            
            paint.color = Color.BLACK
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawRect(flagRect, paint)
            paint.style = Paint.Style.FILL
            
            // Chargement et affichage du drapeau selon le pays ou mode pratique
            val playerCountry = if (practiceMode) {
                "CANADA" // Toujours Canada en mode pratique
            } else {
                tournamentData.playerCountries[currentPlayerIndex]
            }
            
            val flagBitmap = when (playerCountry.uppercase()) {
                "CANADA" -> flagCanadaBitmap
                "USA" -> flagUsaBitmap
                "FRANCE" -> flagFranceBitmap
                "NORVÈGE" -> flagNorvegeBitmap
                "JAPON" -> flagJapanBitmap
                else -> flagCanadaBitmap // Fallback vers Canada
            }
            
            // Afficher l'image du drapeau dans le rectangle - VRAIMENT CENTRÉ
            flagBitmap?.let { flag ->
                // Calculer pour centrer parfaitement le drapeau
                val flagWidth = flagRect.width() - 10f  // Largeur disponible
                val flagHeight = flagRect.height() - 10f // Hauteur disponible
                
                // Ratio de l'image pour garder les proportions
                val imageRatio = flag.width.toFloat() / flag.height.toFloat()
                val rectRatio = flagWidth / flagHeight
                
                val finalWidth: Float
                val finalHeight: Float
                
                if (imageRatio > rectRatio) {
                    // L'image est plus large, on limite par la largeur
                    finalWidth = flagWidth
                    finalHeight = flagWidth / imageRatio
                } else {
                    // L'image est plus haute, on limite par la hauteur
                    finalHeight = flagHeight
                    finalWidth = flagHeight * imageRatio
                }
                
                // Centrer dans le rectangle
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
                // Fallback si l'image ne charge pas - afficher emoji au centre
                val flag = getCountryFlag(playerCountry)
                paint.color = Color.BLACK
                paint.textSize = 120f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(flag, flagRect.centerX(), flagRect.centerY() + 40f, paint)
            }
            
            // Nom du pays sous le rectangle (plus d'emoji ici)
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
            val trackY = h * 0.65f // PLUS HAUT (était 0.7f)
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
            
            // BOBSLEIGH QUI BOUGE DE GAUCHE À DROITE SELON LA POUSSÉE - PLUS HAUT
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
            canvas.drawRoundRect(w/2f - 400f, 120f, w/2f + 400f, 220f, 10f, 10f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 80f // RÉDUIT de 20% (était 100f)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("TAPEZ SUR LE BOBSLEIGH POUR LE POUSSER!", w/2f, 180f, paint)
            
            paint.color = Color.argb(200, 0, 0, 0)
            canvas.drawRoundRect(w/2f - 200f, h - 150f, w/2f + 200f, h - 40f, 10f, 10f, paint)
            
            paint.color = Color.GRAY
            canvas.drawRect(w/2f - 180f, h - 120f, w/2f + 180f, h - 80f, paint)
            
            paint.color = Color.GREEN
            val powerWidth = (pushPower.coerceAtMost(150f) / 150f) * 360f // Permet de dépasser 100% jusqu'à 150%
            canvas.drawRect(w/2f - 180f, h - 120f, w/2f - 180f + powerWidth, h - 80f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 80f // ENCORE PLUS GROS
            canvas.drawText("PUISSANCE: ${pushPower.toInt()}% | Coups: ${pushCount}", w/2f, h - 50f, paint)
            
            paint.color = Color.argb(200, 255, 0, 0)
            canvas.drawRoundRect(w - 140f, 60f, w - 20f, 160f, 10f, 10f, paint)
            
            paint.textSize = 56f // BEAUCOUP PLUS GROS
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${(pushStartDuration - phaseTimer).toInt() + 1}s", w - 80f, 130f, paint)
        }
        
        private fun drawFinishLine(canvas: Canvas, w: Int, h: Int) {
            drawNewTrackSystem(canvas, w, h)
            
            // Ligne d'arrivée simple
            paint.color = Color.YELLOW
            paint.textSize = 80f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🏁 FINISH! 🏁", w/2f, h * 0.3f, paint)
        }
        
        private fun drawCelebration(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            val progress = phaseTimer / celebrationDuration
            
            // Bobsleigh qui arrive de gauche et ralentit au centre
            val bobCelebrationX = if (progress < 0.7f) {
                // Première partie : mouvement rapide de gauche vers centre
                val moveProgress = (progress / 0.7f) // 0 à 1 sur les premiers 70% du temps
                val easedProgress = 1f - (1f - moveProgress) * (1f - moveProgress) // Ralentissement
                -200f + easedProgress * (w/2f + 200f) // De -200 à center
            } else {
                // Deuxième partie : immobile au centre
                w/2f
            }
            
            val bobCelebrationY = h/2f
            
            // Particules qui tournent
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
            paint.textSize = 120f // ÉNORME
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore}", w/2f, h * 0.25f, paint)
            
            paint.textSize = 50f // PLUS GROS
            canvas.drawText("POINTS", w/2f, h * 0.35f, paint)
            
            paint.color = Color.parseColor("#001122")
            paint.textSize = 48f // BEAUCOUP PLUS GROS
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
