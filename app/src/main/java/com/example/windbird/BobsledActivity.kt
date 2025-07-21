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
        
        // NOUVELLES FONCTIONS POUR LE DÉCOR QUI DÉFILE
        
        private fun drawScrollingBackground(canvas: Canvas, w: Int, horizonY: Int) {
            val mountainOffset = currentCurveIntensity * w * 0.4f
            val speedScroll = (phaseTimer * speed * 0.5f) % w.toFloat()
            
            when {
                trackPosition < 0.3f -> {
                    // HAUTE MONTAGNE - Pics enneigés
                    paint.color = Color.rgb(80, 90, 120) // Montagnes sombres
                    
                    // Pic principal
                    val mountain1 = Path().apply {
                        moveTo(-mountainOffset - speedScroll * 0.3f, horizonY.toFloat())
                        lineTo(w * 0.1f - mountainOffset - speedScroll * 0.3f, horizonY - 180f)
                        lineTo(w * 0.3f - mountainOffset - speedScroll * 0.3f, horizonY - 150f)
                        lineTo(w * 0.5f - mountainOffset - speedScroll * 0.3f, horizonY - 200f)
                        lineTo(w * 0.7f - mountainOffset - speedScroll * 0.3f, horizonY - 120f)
                        lineTo(w.toFloat() - mountainOffset - speedScroll * 0.3f, horizonY.toFloat())
                        close()
                    }
                    canvas.drawPath(mountain1, paint)
                    
                    // Pic arrière-plan
                    paint.color = Color.rgb(100, 110, 140)
                    val mountain2 = Path().apply {
                        moveTo(w * 0.3f - mountainOffset - speedScroll * 0.2f, horizonY.toFloat())
                        lineTo(w * 0.6f - mountainOffset - speedScroll * 0.2f, horizonY - 160f)
                        lineTo(w * 0.9f - mountainOffset - speedScroll * 0.2f, horizonY - 140f)
                        lineTo(w * 1.2f - mountainOffset - speedScroll * 0.2f, horizonY.toFloat())
                        close()
                    }
                    canvas.drawPath(mountain2, paint)
                    
                    // Neige sur les pics
                    paint.color = Color.WHITE
                    canvas.drawCircle(w * 0.1f - mountainOffset - speedScroll * 0.3f, horizonY - 170f, 15f, paint)
                    canvas.drawCircle(w * 0.5f - mountainOffset - speedScroll * 0.3f, horizonY - 190f, 20f, paint)
                }
                
                trackPosition < 0.7f -> {
                    // FORÊT - Sapins denses
                    paint.color = Color.rgb(60, 80, 40) // Vert sombre forêt
                    
                    // Collines boisées
                    val forest1 = Path().apply {
                        moveTo(-speedScroll * 0.8f, horizonY.toFloat())
                        lineTo(w * 0.2f - speedScroll * 0.8f, horizonY - 100f)
                        lineTo(w * 0.4f - speedScroll * 0.8f, horizonY - 80f)
                        lineTo(w * 0.6f - speedScroll * 0.8f, horizonY - 120f)
                        lineTo(w * 0.8f - speedScroll * 0.8f, horizonY - 90f)
                        lineTo(w.toFloat() - speedScroll * 0.8f, horizonY.toFloat())
                        close()
                    }
                    canvas.drawPath(forest1, paint)
                    
                    // Sapins individuels qui défilent
                    paint.color = Color.rgb(40, 60, 20)
                    for (i in 0..8) {
                        val treeX = (i * w / 4f - speedScroll * 1.2f) % (w * 1.5f) - w * 0.25f
                        val treeY = horizonY - 60f + kotlin.random.Random(i).nextFloat() * 40f
                        
                        // Tronc
                        paint.color = Color.rgb(80, 50, 30)
                        canvas.drawRect(treeX - 3f, treeY, treeX + 3f, treeY + 20f, paint)
                        
                        // Branches
                        paint.color = Color.rgb(40, 60, 20)
                        canvas.drawCircle(treeX, treeY - 5f, 12f, paint)
                        canvas.drawCircle(treeX, treeY - 15f, 8f, paint)
                    }
                }
                
                else -> {
                    // VILLAGE - Chalets et maisons
                    paint.color = Color.rgb(100, 120, 80) // Collines vertes
                    
                    val village1 = Path().apply {
                        moveTo(-speedScroll, horizonY.toFloat())
                        lineTo(w * 0.3f - speedScroll, horizonY - 60f)
                        lineTo(w * 0.7f - speedScroll, horizonY - 80f)
                        lineTo(w.toFloat() - speedScroll, horizonY.toFloat())
                        close()
                    }
                    canvas.drawPath(village1, paint)
                    
                    // Chalets qui défilent
                    for (i in 0..6) {
                        val chaletX = (i * w / 3f - speedScroll * 1.5f) % (w * 1.8f) - w * 0.4f
                        val chaletY = horizonY - 40f + kotlin.random.Random(i + 100).nextFloat() * 20f
                        
                        // Maison
                        paint.color = Color.rgb(150, 100, 80)
                        canvas.drawRect(chaletX - 15f, chaletY, chaletX + 15f, chaletY + 25f, paint)
                        
                        // Toit
                        paint.color = Color.rgb(120, 80, 60)
                        val roof = Path().apply {
                            moveTo(chaletX - 18f, chaletY)
                            lineTo(chaletX, chaletY - 15f)
                            lineTo(chaletX + 18f, chaletY)
                            close()
                        }
                        canvas.drawPath(roof, paint)
                        
                        // Fenêtre
                        paint.color = Color.YELLOW
                        canvas.drawRect(chaletX - 8f, chaletY + 8f, chaletX - 2f, chaletY + 15f, paint)
                    }
                }
            }
            
            // Nuages qui bougent selon la zone
            paint.color = Color.argb(180, 220, 220, 255)
            val cloudSpeed = speedScroll * 0.1f
            canvas.drawCircle((w * 0.2f - cloudSpeed) % (w * 1.3f) - w * 0.15f, horizonY * 0.3f, 30f, paint)
            canvas.drawCircle((w * 0.7f - cloudSpeed) % (w * 1.3f) - w * 0.15f, horizonY * 0.2f, 45f, paint)
            canvas.drawCircle((w * 0.9f - cloudSpeed) % (w * 1.3f) - w * 0.15f, horizonY * 0.4f, 25f, paint)
        }
        
        private fun drawOverheadElements(canvas: Canvas, w: Int, h: Int) {
            val elementScroll = (phaseTimer * speed * 2f) % (w * 3f)
            
            when {
                trackPosition < 0.3f -> {
                    // TÉLÉPHÉRIQUES dans la montagne
                    if ((elementScroll / w).toInt() % 3 == 0) {
                        paint.color = Color.rgb(100, 100, 100)
                        paint.strokeWidth = 8f
                        paint.style = Paint.Style.STROKE
                        
                        // Câble
                        canvas.drawLine(
                            -100f + elementScroll % (w * 1.5f),
                            h * 0.3f,
                            w + 100f + elementScroll % (w * 1.5f),
                            h * 0.4f,
                            paint
                        )
                        
                        // Cabine
                        paint.style = Paint.Style.FILL
                        paint.color = Color.RED
                        val cabinX = w * 0.3f + elementScroll % (w * 1.2f) - w * 0.1f
                        canvas.drawRect(cabinX - 20f, h * 0.35f, cabinX + 20f, h * 0.38f, paint)
                    }
                }
                
                trackPosition < 0.7f -> {
                    // PONTS EN BOIS dans la forêt
                    if ((elementScroll / w).toInt() % 4 == 1) {
                        paint.color = Color.rgb(120, 80, 40)
                        paint.style = Paint.Style.FILL
                        
                        // Pont
                        canvas.drawRect(
                            w * 0.1f,
                            h * 0.45f,
                            w * 0.9f,
                            h * 0.48f,
                            paint
                        )
                        
                        // Piliers
                        paint.color = Color.rgb(80, 60, 30)
                        canvas.drawRect(w * 0.2f, h * 0.45f, w * 0.22f, h * 0.55f, paint)
                        canvas.drawRect(w * 0.8f, h * 0.45f, w * 0.82f, h * 0.55f, paint)
                    }
                }
                
                else -> {
                    // BANDEROLLES dans le village
                    if ((elementScroll / w).toInt() % 2 == 0) {
                        paint.color = Color.rgb(255, 200, 0)
                        paint.style = Paint.Style.FILL
                        
                        // Banderole
                        canvas.drawRect(
                            w * 0.15f,
                            h * 0.4f,
                            w * 0.85f,
                            h * 0.42f,
                            paint
                        )
                        
                        // Texte sur banderole
                        paint.color = Color.RED
                        paint.textSize = 24f
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText("ALLEZ! BRAVO!", w * 0.5f, h * 0.41f + 8f, paint)
                    }
                }
            }
        }
        
        private fun drawSideDecor(canvas: Canvas, w: Int, h: Int, horizonY: Float) {
            val sideScroll = (phaseTimer * speed * 3f) % 200f
            
            // CÔTÉ GAUCHE
            for (y in horizonY.toInt() until h step 40) {
                val sideProgress = (y - horizonY) / (h - horizonY)
                val sideY = y.toFloat() + sideScroll * (1f + sideProgress)
                
                when {
                    trackPosition < 0.3f -> {
                        // Rochers de montagne
                        paint.color = Color.rgb(80, 80, 100)
                        canvas.drawCircle(w * 0.05f, sideY % h.toFloat(), 15f + sideProgress * 20f, paint)
                    }
                    
                    trackPosition < 0.7f -> {
                        // Barrières de sécurité
                        paint.color = Color.rgb(255, 50, 50)
                        canvas.drawRect(
                            w * 0.02f,
                            sideY % h.toFloat(),
                            w * 0.08f,
                            (sideY + 8f) % h.toFloat(),
                            paint
                        )
                    }
                    
                    else -> {
                        // Spectateurs qui applaudissent
                        paint.color = Color.rgb(200, 150, 100) // Peau
                        canvas.drawCircle(w * 0.06f, sideY % h.toFloat(), 8f + sideProgress * 10f, paint)
                        
                        // Corps
                        paint.color = Color.rgb(100, 100, 200) // Vêtements
                        canvas.drawRect(
                            w * 0.04f,
                            (sideY + 8f) % h.toFloat(),
                            w * 0.08f,
                            (sideY + 25f) % h.toFloat(),
                            paint
                        )
                    }
                }
            }
            
            // CÔTÉ DROIT (symétrique)
            for (y in horizonY.toInt() until h step 45) {
                val sideProgress = (y - horizonY) / (h - horizonY)
                val sideY = y.toFloat() + sideScroll * (1.2f + sideProgress)
                
                when {
                    trackPosition < 0.3f -> {
                        // Rochers de montagne
                        paint.color = Color.rgb(70, 70, 90)
                        canvas.drawCircle(w * 0.95f, sideY % h.toFloat(), 12f + sideProgress * 18f, paint)
                    }
                    
                    trackPosition < 0.7f -> {
                        // Barrières de sécurité
                        paint.color = Color.rgb(255, 255, 255)
                        canvas.drawRect(
                            w * 0.92f,
                            sideY % h.toFloat(),
                            w * 0.98f,
                            (sideY + 8f) % h.toFloat(),
                            paint
                        )
                    }
                    
                    else -> {
                        // Spectateurs qui applaudissent
                        paint.color = Color.rgb(220, 170, 120) // Peau
                        canvas.drawCircle(w * 0.94f, sideY % h.toFloat(), 9f + sideProgress * 12f, paint)
                        
                        // Corps
                        paint.color = Color.rgb(200, 100, 100) // Vêtements
                        canvas.drawRect(
                            w * 0.92f,
                            (sideY + 8f) % h.toFloat(),
                            w * 0.96f,
                            (sideY + 25f) % h.toFloat(),
                            paint
                        )
                    }
                }
            }
            
            // EFFETS DE PARTICULES selon la zone
            paint.color = Color.argb(150, 255, 255, 255)
            for (i in 0..20) {
                val particleX = kotlin.random.Random(i).nextFloat() * w
                val particleY = (kotlin.random.Random(i + 50).nextFloat() * h + sideScroll * 2f) % h.toFloat()
                
                when {
                    trackPosition < 0.3f -> {
                        // Flocons de neige
                        canvas.drawCircle(particleX, particleY, 2f, paint)
                    }
                    trackPosition >= 0.7f -> {
                        // Confettis dans le village
                        paint.color = Color.argb(150, 255, 200, 0)
                        canvas.drawCircle(particleX, particleY, 3f, paint)
                        paint.color = Color.argb(150, 255, 255, 255)
                    }
                }
            }
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
        // Circuit 3X plus long avec virages aléatoires
        val trackLength = 75 // 3X plus long (était 25 segments)
        
        trackCurves.add(0f) // Départ toujours droit
        trackCurves.add(0f)
        trackCurves.add(0f)
        
        for (i in 3 until trackLength - 3) {
            when (random.nextInt(10)) {
                0, 1 -> trackCurves.add(-0.9f + random.nextFloat() * 0.3f) // Virage gauche serré
                2, 3 -> trackCurves.add(0.6f + random.nextFloat() * 0.3f)  // Virage droite serré
                4 -> trackCurves.add(-0.5f + random.nextFloat() * 0.2f)      // Virage gauche moyen
                5 -> trackCurves.add(0.3f + random.nextFloat() * 0.2f)       // Virage droite moyen
                6 -> trackCurves.add(-0.3f + random.nextFloat() * 0.1f)      // Virage gauche léger
                7 -> trackCurves.add(0.2f + random.nextFloat() * 0.1f)       // Virage droite léger
                else -> trackCurves.add(0f)                                  // Ligne droite
            }
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
        
        if (trackPosition >= 1f) {
            gameState = GameState.FINISH_LINE
            phaseTimer = 0f
            cameraShake = 0.8f
            // ARRÊTER LE TIMER DE COURSE ICI
            // raceTime reste figé à cette valeur
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
        
        // Images des drapeaux
        private var flagCanadaBitmap: Bitmap? = null
        private var flagUsaBitmap: Bitmap? = null
        private var flagFranceBitmap: Bitmap? = null
        private var flagNorvegeBitmap: Bitmap? = null
        private var flagJapanBitmap: Bitmap? = null
        
        init {
            try {
                bobsledPreparationBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobsled_preparation)
                bobPushBitmap = BitmapFactory.decodeResource(resources, R.drawable.bob_push)
                bobStraightBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobnv_straight)
                bobLeftBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobnv_left)
                bobRightBitmap = BitmapFactory.decodeResource(resources, R.drawable.bobnv_right)
                bobFinishLineBitmap = BitmapFactory.decodeResource(resources, R.drawable.bob_finish_line)
                bobCelebrationBitmap = BitmapFactory.decodeResource(resources, R.drawable.bob_celebration)
                
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

        // GESTION DU TACTILE POUR LA POUSSÉE - ZONE QUI SUIT LE BOBSLEIGH
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (gameState == GameState.PUSH_START) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        // Zone qui suit le bobsleigh qui bouge
                        val pushProgress = (pushPower / 100f).coerceIn(0f, 1f)
                        val bobX = 150f + pushProgress * (width - 300f)
                        val bobY = height * 0.7f
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
                "USA", "ÉTATS-UNIS", "ETATS-UNIS" -> flagUsaBitmap
                "FRANCE" -> flagFranceBitmap
                "NORVÈGE", "NORWAY" -> flagNorvegeBitmap
                "JAPON", "JAPAN" -> flagJapanBitmap
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
            canvas.drawRoundRect(w/2f - 400f, 120f, w/2f + 400f, 220f, 10f, 10f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 100f // ÉNORME
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

        private fun drawWinterGamesTunnel(canvas: Canvas, w: Int, h: Int) {
            // VRAI DEMI-TUNNEL AVEC DÉCOR QUI DÉFILE - MONTAGNE VERS VILLAGE !
            
            val horizonY = h * 0.55f // PLUS DE CIEL - 30% moins de vue perspective
            val roadDistance = 200f
            
            // 1. CIEL QUI CHANGE SELON LA PROGRESSION
            val skyColor = when {
                trackPosition < 0.3f -> Color.rgb(140, 160, 255) // Ciel montagne (bleu-gris)
                trackPosition < 0.7f -> Color.rgb(160, 180, 255) // Ciel forêt (bleu plus clair)
                else -> Color.rgb(180, 200, 255) // Ciel village (bleu clair)
            }
            paint.color = skyColor
            canvas.drawRect(0f, 0f, w.toFloat(), horizonY, paint)
            
            // 2. DÉCOR D'ARRIÈRE-PLAN QUI DÉFILE
            drawScrollingBackground(canvas, w, horizonY.toInt())
            
            // 3. ÉLÉMENTS AU-DESSUS DE LA PISTE (téléphériques, ponts, banderolles)
            drawOverheadElements(canvas, w, h)
            
            // 4. DÉCOR DES CÔTÉS QUI DÉFILE
            drawSideDecor(canvas, w, h, horizonY)
            
            // 5. DEMI-TUNNEL COMPLET LIGNE PAR LIGNE - 2-3X PLUS LARGE
            val roadScroll = (phaseTimer * speed * 3f) % 100f
            
            for (screenY in horizonY.toInt() until h) {
                val lineProgress = (screenY - horizonY) / (h - horizonY)
                val z = roadDistance * (1f - lineProgress * 0.95f)
                val scaleFactor = 1f / z
                
                // LARGEUR DE LA PISTE 2-3X PLUS LARGE
                val roadWidth = (w * 15f * scaleFactor).coerceAtMost(w * 20f)
                val wallHeight = (1500f * scaleFactor).coerceAtMost(2000f)
                
                // ANTICIPATION DES VIRAGES CORRIGÉE
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
                
                val roadCenterX = w/2f + futureCurve * w * 0.8f * scaleFactor
                
                // DESSINER LE DEMI-TUNNEL PLUS PRONONCÉ
                
                // 1. SOL DE LA PISTE (BLANC)
                paint.color = Color.WHITE
                canvas.drawRect(
                    roadCenterX - roadWidth/2f,
                    screenY.toFloat(),
                    roadCenterX + roadWidth/2f,
                    screenY + 3f,
                    paint
                )
                
                // 2. MURS LATÉRAUX PLUS ÉPAIS ET PRONONCÉS
                paint.color = Color.rgb(120, 120, 120)
                
                val wallThickness = 200f * scaleFactor
                
                // Mur gauche vertical
                canvas.drawRect(
                    roadCenterX - roadWidth/2f - wallThickness,
                    screenY.toFloat(),
                    roadCenterX - roadWidth/2f,
                    screenY + 3f,
                    paint
                )
                
                // Mur droit vertical
                canvas.drawRect(
                    roadCenterX + roadWidth/2f,
                    screenY.toFloat(),
                    roadCenterX + roadWidth/2f + wallThickness,
                    screenY + 3f,
                    paint
                )
                
                // 3. COURBE DU DEMI-TUNNEL BEAUCOUP PLUS PRONONCÉE
                paint.color = Color.rgb(140, 140, 140)
                
                val curveHeight = wallHeight * (1f - lineProgress * 0.2f)
                val curveRadius = roadWidth * 0.9f
                
                // Courbe gauche (quart de cercle) - PLUS DÉTAILLÉE
                for (angle in 90..180 step 2) {
                    val radian = Math.toRadians(angle.toDouble()).toFloat()
                    val curveX = roadCenterX - roadWidth/2f + cos(radian) * curveRadius * 0.8f
                    val curveY = screenY + sin(radian) * curveHeight * 0.8f
                    
                    paint.color = Color.rgb(130 - (angle-90)/5, 130 - (angle-90)/5, 130 - (angle-90)/5)
                    canvas.drawRect(curveX - 30f, curveY, curveX + 30f, curveY + 3f, paint)
                }
                
                // Courbe droite (quart de cercle) - PLUS DÉTAILLÉE
                for (angle in 0..90 step 2) {
                    val radian = Math.toRadians(angle.toDouble()).toFloat()
                    val curveX = roadCenterX + roadWidth/2f + cos(radian) * curveRadius * 0.8f
                    val curveY = screenY + sin(radian) * curveHeight * 0.8f
                    
                    paint.color = Color.rgb(130 - angle/5, 130 - angle/5, 130 - angle/5)
                    canvas.drawRect(curveX - 30f, curveY, curveX + 30f, curveY + 3f, paint)
                }
                
                // 4. OMBRES PLUS PRONONCÉES
                paint.color = Color.rgb(80, 80, 80)
                
                canvas.drawRect(
                    roadCenterX - roadWidth/2f,
                    screenY.toFloat(),
                    roadCenterX - roadWidth/4f,
                    screenY + 2f,
                    paint
                )
                
                canvas.drawRect(
                    roadCenterX + roadWidth/4f,
                    screenY.toFloat(),
                    roadCenterX + roadWidth/2f,
                    screenY + 2f,
                    paint
                )
                
                // 5. LIGNES DE VITESSE
                val texturePosition = (roadScroll + lineProgress * 20f) % 6f
                if (texturePosition < 1f) {
                    paint.color = Color.rgb(240, 240, 240)
                    canvas.drawRect(
                        roadCenterX - roadWidth * 0.08f,
                        screenY.toFloat(),
                        roadCenterX + roadWidth * 0.08f,
                        screenY + 2f,
                        paint
                    )
                }
            }
            
            // 4. BOBSLEIGH AVEC PHYSIQUE DE VIRAGES - FORCE CENTRIFUGE VISIBLE
            val bobBaseX = w / 2f
            val baseBobY = h * 0.82f
            val bobScale = 0.16f
            
            // CALCUL DE LA FORCE CENTRIFUGE - Position horizontale ET verticale
            var bobHorizontalOffset = 0f
            var bobVerticalOffset = 0f
            var bobRotation = 0f
            
            if (abs(currentCurveIntensity) > 0.3f) {
                // Force centrifuge : virage gauche = pousse vers la droite, virage droite = pousse vers la gauche
                val centrifugalForce = abs(currentCurveIntensity)
                val speedFactor = (speed / maxSpeed).coerceIn(0.3f, 1f)
                
                // DÉPLACEMENT HORIZONTAL (force centrifuge)
                bobHorizontalOffset = currentCurveIntensity * speedFactor * 100f // Pousse vers l'extérieur
                
                // MONTÉE SUR LES PAROIS
                val climbAngle = (45f + (centrifugalForce * speedFactor * 30f)).coerceAtMost(75f)
                val climbRadians = Math.toRadians(climbAngle.toDouble()).toFloat()
                val climbDistance = centrifugalForce * speedFactor * 60f
                
                bobVerticalOffset = -sin(climbRadians) * climbDistance
                
                // Rotation supplémentaire
                val additionalRotation = climbAngle - 45f
                bobRotation = if (currentCurveIntensity < 0f) -additionalRotation else additionalRotation
            }
            
            val bobX = bobBaseX + bobHorizontalOffset
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
            
            // 5. INTERFACE AVEC TEXTE ÉNORME ET INSTRUCTIONS CLAIRES
            paint.color = Color.BLACK
            paint.textSize = 120f // ÉNORME
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("${speed.toInt()} KM/H", 30f, 150f, paint)
            
            if (abs(currentCurveIntensity) > 0.3f) {
                // FOND SEMI-TRANSPARENT POUR RENDRE LES INSTRUCTIONS PLUS VISIBLES
                paint.color = Color.argb(180, 0, 0, 0)
                canvas.drawRoundRect(w/4f, 200f, w*3f/4f, 400f, 20f, 20f, paint)
                
                paint.color = Color.YELLOW
                paint.textSize = 150f // GIGANTESQUE
                paint.textAlign = Paint.Align.CENTER
                val instruction = if (currentCurveIntensity < 0f) "⬅️ PENCHEZ GAUCHE" else "PENCHEZ DROITE ➡️"
                canvas.drawText(instruction, w/2f, 280f, paint)
                
                paint.textSize = 100f // TRÈS GROS
                paint.color = when {
                    playerReactionAccuracy > 0.8f -> Color.GREEN
                    playerReactionAccuracy > 0.6f -> Color.YELLOW
                    else -> Color.RED
                }
                canvas.drawText("${(playerReactionAccuracy * 100).toInt()}%", w/2f, 360f, paint)
                
                // INDICATION PLUS CLAIRE
                paint.color = Color.WHITE
                paint.textSize = 60f
                canvas.drawText("INCLINEZ VOTRE TÉLÉPHONE!", w/2f, 420f, paint)
                
                // DEBUG: Afficher l'angle de montée
                paint.color = Color.WHITE
                paint.textSize = 50f
                canvas.drawText("Angle: ${((45f + (abs(currentCurveIntensity) * (speed / maxSpeed).coerceIn(0.3f, 1f) * 30f)).coerceAtMost(75f)).toInt()}°", 30f, h - 80f, paint)
            }
        }
        
        private fun drawFinishLine(canvas: Canvas, w: Int, h: Int) {
            drawWinterGamesTunnel(canvas, w, h)
            
            // Ligne d'arrivée qui suit la piste (pas juste verticale)
            val lineProgress = phaseTimer / finishLineDuration
            val lineDistance = h * 0.4f + lineProgress * (h * 0.6f) // De l'horizon vers nous
            
            if (lineDistance < h) {
                // Calculer la courbe à cette distance pour que la ligne suive la piste
                val lineScreenProgress = (lineDistance - h * 0.35f) / (h * 0.65f)
                val lineCurvePosition = (trackPosition + lineScreenProgress * 0.1f) % 1f
                val lineCurveIndex = (lineCurvePosition * (trackCurves.size - 1)).toInt()
                val lineCurveProgress = (lineCurvePosition * (trackCurves.size - 1)) - lineCurveIndex
                
                val lineCurve = if (lineCurveIndex < trackCurves.size) {
                    val curve1 = trackCurves[lineCurveIndex]
                    val curve2 = trackCurves[(lineCurveIndex + 1) % trackCurves.size]
                    curve1 + (curve2 - curve1) * lineCurveProgress
                } else {
                    0f
                }
                
                val lineScaleFactor = 1f / (200f * (1f - lineScreenProgress * 0.95f))
                val lineWidth = (w * 5f * lineScaleFactor).coerceAtMost(w * 7f)
                val lineCenterX = w/2f + lineCurve * w * 0.8f * lineScaleFactor
                
                // Dessiner la ligne d'arrivée qui suit la largeur de la piste
                val segments = 20
                val segmentWidth = lineWidth / segments
                
                for (i in 0 until segments) {
                    val color = if (i % 2 == 0) Color.WHITE else Color.BLACK
                    paint.color = color
                    canvas.drawRect(
                        lineCenterX - lineWidth/2f + i * segmentWidth,
                        lineDistance - 30f,
                        lineCenterX - lineWidth/2f + (i + 1) * segmentWidth,
                        lineDistance + 30f,
                        paint
                    )
                }
                
                paint.color = Color.YELLOW
                paint.textSize = 80f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("🏁 FINISH! 🏁", w/2f, lineDistance + 10f, paint)
            }
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
