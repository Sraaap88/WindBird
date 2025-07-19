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
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.view.ViewGroup
import kotlin.math.*

class CurlingActivity : Activity(), SensorEventListener {

    private lateinit var gameView: CurlingView
    private lateinit var statusText: TextView

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null
    private var accelerometer: Sensor? = null

    // Variables de gameplay CURLING - RALLENTI
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Phases avec durées TRÈS accessibles
    private val preparationDuration = 8f // AUGMENTÉ de 5f
    private val aimingDuration = 25f // AUGMENTÉ de 15f - Temps pour viser et lancer
    private val sweepingDuration = 30f // AUGMENTÉ de 20f - Temps pour balayer
    private val resultsDuration = 10f // AUGMENTÉ de 6f
    
    // Variables de curling
    private var stonePosition = PointF(0.5f, 0.95f) // Position de la pierre (x, y)
    private var stoneVelocity = PointF(0f, 0f)
    private var stoneRotation = 0f
    private var isStoneMoving = false
    private var stoneDirection = 0f // Direction initiale
    private var stonePower = 0f
    
    // Système de lancer
    private var aimingX = 0.5f // Visée horizontale
    private var launchPower = 50f // Puissance de lancer
    private var hasLaunched = false
    
    // Système de balayage
    private var sweepingActive = false
    private var sweepingIntensity = 0f
    private var sweepingCount = 0
    private var lastSweepTime = 0L
    private var totalSweepingTime = 0f
    
    // Contrôles gyroscope/accéléromètre - MOINS SENSIBLE
    private var tiltX = 0f
    private var tiltY = 0f
    private var tiltZ = 0f
    private var accelX = 0f
    private var accelY = 0f
    private var accelZ = 0f
    private var sweepingMotionDetected = false
    
    // Cibles et scoring
    private val targetCenter = PointF(0.5f, 0.15f)
    private val ringRadii = arrayOf(0.15f, 0.10f, 0.06f) // AUGMENTÉ - anneaux plus grands
    private var finalDistance = 0f
    private var ringScore = 0
    private var technique = 100f
    private var precision = 100f
    private var strategy = 100f
    
    // Effets visuels
    private var cameraShake = 0f
    private val iceTrails = mutableListOf<IceTrail>()
    private val sweepingEffects = mutableListOf<SweepingEffect>()
    private val targetRipples = mutableListOf<TargetRipple>()
    private val stoneSparkles = mutableListOf<StoneSparkle>()
    
    // Score et résultats
    private var finalScore = 0
    private var scoreCalculated = false

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
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        statusText = TextView(this).apply {
            text = "🥌 CURLING - ${tournamentData.playerNames[currentPlayerIndex]}"
            setTextColor(Color.WHITE)
            textSize = 22f // AUGMENTÉ de 18f
            setBackgroundColor(Color.parseColor("#001144"))
            setPadding(25, 20, 25, 20) // AUGMENTÉ
        }

        gameView = CurlingView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        
        initializeGame()
    }
    
    private fun initializeGame() {
        gameState = GameState.PREPARATION
        phaseTimer = 0f
        stonePosition = PointF(0.5f, 0.95f)
        stoneVelocity = PointF(0f, 0f)
        stoneRotation = 0f
        isStoneMoving = false
        stoneDirection = 0f
        stonePower = 0f
        aimingX = 0.5f
        launchPower = 50f
        hasLaunched = false
        sweepingActive = false
        sweepingIntensity = 0f
        sweepingCount = 0
        lastSweepTime = 0L
        totalSweepingTime = 0f
        tiltX = 0f
        tiltY = 0f
        tiltZ = 0f
        accelX = 0f
        accelY = 0f
        accelZ = 0f
        sweepingMotionDetected = false
        finalDistance = 0f
        ringScore = 0
        technique = 100f
        precision = 100f
        strategy = 100f
        cameraShake = 0f
        finalScore = 0
        scoreCalculated = false
        
        iceTrails.clear()
        sweepingEffects.clear()
        targetRipples.clear()
        stoneSparkles.clear()
    }

    override fun onResume() {
        super.onResume()
        gyroscope?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        accelerometer?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                tiltX = event.values[0]
                tiltY = event.values[1]
                tiltZ = event.values[2]
            }
            Sensor.TYPE_ACCELEROMETER -> {
                accelX = event.values[0]
                accelY = event.values[1]
                accelZ = event.values[2]
                
                // Détection de mouvement de balayage - MOINS SENSIBLE
                val totalAccel = sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)
                sweepingMotionDetected = totalAccel > 15f && abs(accelX) > 4f // AUGMENTÉ de 12f et 3f
            }
        }

        // Progression du jeu - PLUS LENT
        phaseTimer += 0.016f // RÉDUIT de 0.03f

        when (gameState) {
            GameState.PREPARATION -> handlePreparation()
            GameState.AIMING -> handleAiming()
            GameState.SWEEPING -> handleSweeping()
            GameState.RESULTS -> handleResults()
            GameState.FINISHED -> {}
        }

        updateEffects()
        updateStatus()
        gameView.invalidate()
    }
    
    private fun handlePreparation() {
        if (phaseTimer >= preparationDuration) {
            gameState = GameState.AIMING
            phaseTimer = 0f
        }
    }
    
    private fun handleAiming() {
        if (!hasLaunched) {
            // Visée avec gyroscope - MOINS SENSIBLE
            aimingX += tiltX * 0.005f // RÉDUIT de 0.008f
            aimingX = aimingX.coerceIn(0.1f, 0.9f)
            
            // Puissance avec inclinaison avant/arrière - MOINS SENSIBLE
            when {
                tiltY < -0.6f -> { // AUGMENTÉ de -0.4f
                    launchPower += 1.5f // RÉDUIT de 2f
                    technique += 0.03f // RÉDUIT de 0.05f
                }
                tiltY > 0.6f -> { // AUGMENTÉ de 0.4f
                    launchPower -= 1f // RÉDUIT de 1.5f
                }
                else -> {
                    // Position stable = bonus de précision
                    precision += 0.015f // RÉDUIT de 0.02f
                }
            }
            
            launchPower = launchPower.coerceIn(20f, 100f)
        } else {
            // Pierre lancée - attendre qu'elle s'arrête
            updateStoneMovement()
            
            if (!isStoneMoving) {
                // Pierre arrêtée - passage au balayage ou résultats
                if (stonePosition.y > 0.4f) { // AUGMENTÉ de 0.3f - pierre encore plus loin
                    // Pierre encore loin - possibilité de balayer
                    gameState = GameState.SWEEPING
                    phaseTimer = 0f
                } else {
                    // Pierre arrivée - calcul des résultats
                    calculateFinalScore()
                    gameState = GameState.RESULTS
                    phaseTimer = 0f
                }
            }
        }
        
        if (phaseTimer >= aimingDuration && !hasLaunched) {
            // Temps écoulé - lancer automatique
            launchStone()
        }
    }
    
    private fun handleSweeping() {
        if (isStoneMoving) {
            updateStoneMovement()
            
            // Balayage avec mouvement du téléphone OU balayage tactile
            if (sweepingMotionDetected || sweepingActive) {
                performSweeping()
            }
            
            if (!isStoneMoving) {
                // Pierre arrêtée
                calculateFinalScore()
                gameState = GameState.RESULTS
                phaseTimer = 0f
            }
        }
        
        if (phaseTimer >= sweepingDuration) {
            // Temps de balayage écoulé
            if (isStoneMoving) {
                // Arrêter la pierre
                stoneVelocity = PointF(0f, 0f)
                isStoneMoving = false
            }
            calculateFinalScore()
            gameState = GameState.RESULTS
            phaseTimer = 0f
        }
    }
    
    private fun launchStone() {
        hasLaunched = true
        isStoneMoving = true
        
        // Calcul de la direction et puissance
        stoneDirection = (aimingX - 0.5f) * 0.25f // RÉDUIT de 0.3f - direction moins extrême
        stonePower = launchPower
        
        // Vitesse initiale - PLUS LENTE
        val powerFactor = stonePower / 100f
        stoneVelocity.x = stoneDirection * powerFactor * 0.01f // RÉDUIT de 0.015f
        stoneVelocity.y = -powerFactor * 0.018f // RÉDUIT de 0.025f - vers le haut plus lent
        
        // Rotation de la pierre - PLUS LENTE
        stoneRotation = stoneDirection * 1.5f // RÉDUIT de 2f
        
        // Effets visuels
        cameraShake = powerFactor * 0.2f // RÉDUIT de 0.3f
        generateLaunchEffect()
        
        // Score technique
        val aimingAccuracy = 1f - abs(aimingX - 0.5f) * 2f
        technique += aimingAccuracy * 8f // RÉDUIT de 10f
    }
    
    private fun updateStoneMovement() {
        if (!isStoneMoving) return
        
        // Mise à jour position
        stonePosition.x += stoneVelocity.x
        stonePosition.y += stoneVelocity.y
        
        // Friction et ralentissement - PLUS LENT
        stoneVelocity.x *= 0.998f // AUGMENTÉ de 0.995f - moins de friction
        stoneVelocity.y *= 0.998f
        
        // Rotation continue - PLUS LENTE
        stoneRotation += stoneDirection * 1f // RÉDUIT de 2f
        
        // Génération de traînée
        generateIceTrail()
        
        // Vérification des limites - REBONDS PLUS DOUX
        if (stonePosition.x < 0.05f || stonePosition.x > 0.95f) {
            stoneVelocity.x *= -0.3f // RÉDUIT de -0.5f - rebond plus doux
            stonePosition.x = stonePosition.x.coerceIn(0.05f, 0.95f)
        }
        
        if (stonePosition.y < 0.05f) {
            stonePosition.y = 0.05f
            stoneVelocity.y = 0f
        }
        
        // Arrêt si vitesse trop faible - SEUIL PLUS BAS
        val totalVelocity = sqrt(stoneVelocity.x * stoneVelocity.x + stoneVelocity.y * stoneVelocity.y)
        if (totalVelocity < 0.0005f) { // RÉDUIT de 0.001f
            isStoneMoving = false
            stoneVelocity = PointF(0f, 0f)
        }
    }
    
    private fun performSweeping() {
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastSweepTime > 300) { // AUGMENTÉ de 200ms
            sweepingCount++
            lastSweepTime = currentTime
            totalSweepingTime += 0.3f // ADAPTÉ
            
            // Effet du balayage sur la pierre - PLUS EFFICACE
            if (isStoneMoving) {
                // Réduction de la friction = pierre va plus loin
                stoneVelocity.x *= 1.003f // AUGMENTÉ de 1.002f
                stoneVelocity.y *= 1.004f // AUGMENTÉ de 1.003f
                
                // Légère correction de trajectoire - PLUS EFFICACE
                val targetDirection = (targetCenter.x - stonePosition.x) * 0.0015f // AUGMENTÉ de 0.001f
                stoneVelocity.x += targetDirection
                
                strategy += 1.5f // AUGMENTÉ de 1f
            }
            
            // Effets visuels
            generateSweepingEffect()
        }
        
        sweepingIntensity = if (sweepingMotionDetected || sweepingActive) {
            (sweepingIntensity + 0.08f).coerceAtMost(1f) // RÉDUIT de 0.1f
        } else {
            (sweepingIntensity - 0.04f).coerceAtLeast(0f) // RÉDUIT de 0.05f
        }
    }
    
    private fun generateLaunchEffect() {
        repeat(8) { // RÉDUIT de 10
            stoneSparkles.add(StoneSparkle(
                x = stonePosition.x + (kotlin.random.Random.nextFloat() - 0.5f) * 0.1f,
                y = stonePosition.y + (kotlin.random.Random.nextFloat() - 0.5f) * 0.1f,
                color = Color.CYAN,
                life = 1.5f
            ))
        }
    }
    
    private fun generateIceTrail() {
        iceTrails.add(IceTrail(
            x = stonePosition.x,
            y = stonePosition.y,
            timestamp = System.currentTimeMillis()
        ))
        
        if (iceTrails.size > 40) { // AUGMENTÉ de 30
            iceTrails.removeFirst()
        }
    }
    
    private fun generateSweepingEffect() {
        repeat(4) { // RÉDUIT de 5
            sweepingEffects.add(SweepingEffect(
                x = stonePosition.x + (kotlin.random.Random.nextFloat() - 0.5f) * 0.15f,
                y = stonePosition.y + (kotlin.random.Random.nextFloat() - 0.5f) * 0.08f,
                life = 1f
            ))
        }
        
        if (sweepingEffects.size > 25) { // AUGMENTÉ de 20
            sweepingEffects.removeFirst()
        }
    }
    
    private fun generateTargetHitEffect() {
        repeat(12) { // RÉDUIT de 15
            targetRipples.add(TargetRipple(
                x = targetCenter.x,
                y = targetCenter.y,
                radius = 0f,
                maxRadius = 0.4f, // AUGMENTÉ de 0.3f
                life = 2f
            ))
        }
    }
    
    private fun updateEffects() {
        // Mise à jour des traînées de glace
        val currentTime = System.currentTimeMillis()
        iceTrails.removeAll { currentTime - it.timestamp > 6000 } // AUGMENTÉ de 4000
        
        // Mise à jour des effets de balayage
        sweepingEffects.removeAll { effect ->
            effect.life -= 0.02f // RÉDUIT de 0.03f
            effect.life <= 0f
        }
        
        // Mise à jour des ondulations de cible
        targetRipples.removeAll { ripple ->
            ripple.radius += 0.008f // RÉDUIT de 0.01f
            ripple.life -= 0.015f // RÉDUIT de 0.02f
            ripple.life <= 0f || ripple.radius > ripple.maxRadius
        }
        
        // Mise à jour des étincelles de pierre
        stoneSparkles.removeAll { sparkle ->
            sparkle.life -= 0.015f // RÉDUIT de 0.02f
            sparkle.life <= 0f
        }
        
        cameraShake = maxOf(0f, cameraShake - 0.015f) // RÉDUIT de 0.02f
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
    
    private fun calculateFinalScore() {
        if (!scoreCalculated) {
            // Calcul de la distance à la cible
            finalDistance = sqrt(
                (stonePosition.x - targetCenter.x) * (stonePosition.x - targetCenter.x) +
                (stonePosition.y - targetCenter.y) * (stonePosition.y - targetCenter.y)
            )
            
            // Détermination de l'anneau touché - ANNEAUX PLUS GRANDS
            ringScore = when {
                finalDistance <= ringRadii[2] -> 50 // Centre (or)
                finalDistance <= ringRadii[1] -> 35 // Anneau intérieur (argent)
                finalDistance <= ringRadii[0] -> 20 // Anneau extérieur (bronze)
                else -> 0 // Hors cible
            }
            
            // Bonus de performance
            val techniqueBonus = ((technique - 100f) * 0.5f).toInt()
            val precisionBonus = ((precision - 100f) * 0.3f).toInt()
            val strategyBonus = ((strategy - 100f) * 0.2f).toInt()
            val sweepingBonus = (sweepingCount * 3).coerceAtMost(25) // AUGMENTÉ de 2
            
            finalScore = maxOf(30, ringScore + techniqueBonus + precisionBonus + strategyBonus + sweepingBonus)
            scoreCalculated = true
            
            // Effet visuel si dans la cible
            if (ringScore > 0) {
                generateTargetHitEffect()
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (gameState == GameState.AIMING && !hasLaunched) {
                    // Lancer la pierre
                    launchStone()
                    return true
                } else if (gameState == GameState.SWEEPING) {
                    // Commencer le balayage tactile
                    sweepingActive = true
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (gameState == GameState.SWEEPING && sweepingActive) {
                    // Balayage en cours
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (gameState == GameState.SWEEPING) {
                    // Arrêter le balayage tactile
                    sweepingActive = false
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
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
                val aiScore = (70..170).random()
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
            GameState.PREPARATION -> "🥌 ${tournamentData.playerNames[currentPlayerIndex]} | Préparation... ${(preparationDuration - phaseTimer).toInt() + 1}s"
            GameState.AIMING -> {
                if (!hasLaunched) {
                    "🥌 ${tournamentData.playerNames[currentPlayerIndex]} | Visez et tapez pour lancer | Puissance: ${launchPower.toInt()}%"
                } else {
                    "🥌 ${tournamentData.playerNames[currentPlayerIndex]} | Pierre en mouvement..."
                }
            }
            GameState.SWEEPING -> "🥌 ${tournamentData.playerNames[currentPlayerIndex]} | Balayez! Compteur: $sweepingCount | Intensité: ${(sweepingIntensity * 100).toInt()}%"
            GameState.RESULTS -> "🏆 ${tournamentData.playerNames[currentPlayerIndex]} | ${getRingText()} | Score: ${finalScore}"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Tir terminé!"
        }
    }
    
    private fun getRingText(): String {
        return when (ringScore) {
            50 -> "🥇 CENTRE!"
            35 -> "🥈 Anneau intérieur"
            20 -> "🥉 Anneau extérieur"
            else -> "❌ Hors cible"
        }
    }

    inner class CurlingView(context: Context) : View(context) {
        private val paint = Paint()

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            
            // Appliquer camera shake
            if (cameraShake > 0f) {
                canvas.save()
                canvas.translate(
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 8f, // RÉDUIT de 10f
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 8f
                )
            }
            
            when (gameState) {
                GameState.PREPARATION -> drawPreparation(canvas, w, h)
                GameState.AIMING -> drawAiming(canvas, w, h)
                GameState.SWEEPING -> drawSweeping(canvas, w, h)
                GameState.RESULTS -> drawResults(canvas, w, h)
                GameState.FINISHED -> drawResults(canvas, w, h)
            }
            
            drawAllEffects(canvas, w, h)
            
            if (cameraShake > 0f) {
                canvas.restore()
            }
        }
        
        private fun drawPreparation(canvas: Canvas, w: Int, h: Int) {
            // Fond de patinoire de curling
            paint.color = Color.parseColor("#F0F8FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste de curling
            drawCurlingRink(canvas, w, h)
            
            // Instructions - TEXTE PLUS GROS
            paint.color = Color.parseColor("#001144")
            paint.textSize = 48f // AUGMENTÉ de 36f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🥌 CURLING STRATÉGIQUE 🥌", w/2f, h * 0.15f, paint)
            
            paint.textSize = 30f // AUGMENTÉ de 22f
            paint.color = Color.parseColor("#0066CC")
            canvas.drawText("Préparez votre tir de précision...", w/2f, h * 0.8f, paint)
            
            paint.textSize = 22f // AUGMENTÉ de 16f
            paint.color = Color.parseColor("#666666")
            canvas.drawText("📱 Inclinez pour viser, tapez pour lancer", w/2f, h * 0.85f, paint)
            canvas.drawText("📱 Balayez l'écran ou le téléphone pour aider", w/2f, h * 0.9f, paint)
        }
        
        private fun drawAiming(canvas: Canvas, w: Int, h: Int) {
            // Fond
            paint.color = Color.parseColor("#F0F8FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste
            drawCurlingRink(canvas, w, h)
            
            // Ligne de visée
            if (!hasLaunched) {
                drawAimingLine(canvas, w, h)
            }
            
            // Pierre
            drawStone(canvas, w, h)
            
            // Interface de visée
            drawAimingInterface(canvas, w, h)
            
            // Instructions - TEXTE PLUS GROS
            if (!hasLaunched) {
                paint.color = Color.parseColor("#001144")
                paint.textSize = 28f // AUGMENTÉ de 20f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("📱 INCLINEZ POUR VISER • TAPEZ POUR LANCER", w/2f, 60f, paint)
            } else {
                paint.color = Color.parseColor("#FF6600")
                paint.textSize = 24f // AUGMENTÉ de 18f
                canvas.drawText("🥌 Pierre en mouvement...", w/2f, 50f, paint)
            }
        }
        
        private fun drawSweeping(canvas: Canvas, w: Int, h: Int) {
            // Fond avec effet de mouvement
            paint.color = Color.parseColor("#F0F8FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste
            drawCurlingRink(canvas, w, h)
            
            // Pierre en mouvement
            drawStone(canvas, w, h)
            
            // Interface de balayage
            drawSweepingInterface(canvas, w, h)
            
            // Instructions de balayage - TEXTE PLUS GROS
            paint.color = Color.parseColor("#FF6600")
            paint.textSize = 32f // AUGMENTÉ de 24f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🔥 BALAYEZ POUR AIDER LA PIERRE! 🔥", w/2f, 60f, paint)
            
            paint.textSize = 24f // AUGMENTÉ de 18f
            paint.color = Color.parseColor("#001144")
            canvas.drawText("📱 Balayez l'écran ou bougez le téléphone", w/2f, h - 40f, paint)
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            // Fond
            paint.color = Color.parseColor("#F8F8FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Bandeau selon le résultat
            val bannerColor = when (ringScore) {
                50 -> Color.parseColor("#FFD700") // Or
                35 -> Color.parseColor("#C0C0C0") // Argent
                20 -> Color.parseColor("#CD7F32") // Bronze
                else -> Color.parseColor("#DDDDDD") // Gris
            }
            paint.color = bannerColor
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
            
            // Score final - TEXTE PLUS GROS
            paint.color = Color.parseColor("#001144")
            paint.textSize = 80f // AUGMENTÉ de 72f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore}", w/2f, h * 0.2f, paint)
            
            paint.textSize = 36f // AUGMENTÉ de 28f
            canvas.drawText("POINTS", w/2f, h * 0.3f, paint)
            
            // Résultat détaillé - TEXTE PLUS GROS
            paint.color = Color.parseColor("#333333")
            paint.textSize = 26f // AUGMENTÉ de 20f
            canvas.drawText(getRingText(), w/2f, h * 0.5f, paint)
            canvas.drawText("Distance: ${(finalDistance * 1000).toInt()}cm", w/2f, h * 0.55f, paint)
            canvas.drawText("🎯 Technique: ${technique.toInt()}%", w/2f, h * 0.6f, paint)
            canvas.drawText("📏 Précision: ${precision.toInt()}%", w/2f, h * 0.65f, paint)
            canvas.drawText("🧠 Stratégie: ${strategy.toInt()}%", w/2f, h * 0.7f, paint)
            canvas.drawText("🧹 Balayages: $sweepingCount", w/2f, h * 0.75f, paint)
        }
        
        private fun drawCurlingRink(canvas: Canvas, w: Int, h: Int) {
            // Surface de glace
            paint.color = Color.parseColor("#FFFFFF")
            canvas.drawRect(w * 0.1f, 0f, w * 0.9f, h.toFloat(), paint)
            
            // Ligne centrale
            paint.color = Color.parseColor("#DDDDDD")
            paint.strokeWidth = 4f // AUGMENTÉ de 3f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(w/2f, 0f, w/2f, h.toFloat(), paint)
            
            // Cible (house) en haut
            val targetScreenX = targetCenter.x * w
            val targetScreenY = targetCenter.y * h
            
            // Trois anneaux concentriques - PLUS GROS
            val ringColors = arrayOf(Color.BLUE, Color.WHITE, Color.RED)
            for (i in ringRadii.indices) {
                paint.color = ringColors[i]
                paint.style = Paint.Style.FILL
                val radius = ringRadii[i] * w * 0.5f
                canvas.drawCircle(targetScreenX, targetScreenY, radius, paint)
                
                paint.color = Color.BLACK
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 3f // AUGMENTÉ de 2f
                canvas.drawCircle(targetScreenX, targetScreenY, radius, paint)
            }
            
            // Ligne de lancer en bas
            paint.color = Color.parseColor("#FF0000")
            paint.strokeWidth = 6f // AUGMENTÉ de 4f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(w * 0.1f, h * 0.9f, w * 0.9f, h * 0.9f, paint)
            
            paint.style = Paint.Style.FILL
        }
        
        private fun drawAimingLine(canvas: Canvas, w: Int, h: Int) {
            // Ligne de visée en pointillés - PLUS VISIBLE
            paint.color = Color.parseColor("#AA00FF00")
            paint.strokeWidth = 4f // AUGMENTÉ de 3f
            paint.style = Paint.Style.STROKE
            
            val startX = stonePosition.x * w
            val startY = stonePosition.y * h
            val endX = aimingX * w
            val endY = targetCenter.y * h
            
            // Ligne en pointillés
            val dashLength = 25f // AUGMENTÉ de 20f
            val distance = sqrt((endX - startX) * (endX - startX) + (endY - startY) * (endY - startY))
            val steps = (distance / dashLength).toInt()
            
            for (i in 0 until steps step 2) {
                val t1 = i.toFloat() / steps
                val t2 = ((i + 1).toFloat() / steps).coerceAtMost(1f)
                
                val x1 = startX + (endX - startX) * t1
                val y1 = startY + (endY - startY) * t1
                val x2 = startX + (endX - startX) * t2
                val y2 = startY + (endY - startY) * t2
                
                canvas.drawLine(x1, y1, x2, y2, paint)
            }
            
            paint.style = Paint.Style.FILL
        }
        
        private fun drawStone(canvas: Canvas, w: Int, h: Int) {
            val stoneScreenX = stonePosition.x * w
            val stoneScreenY = stonePosition.y * h
            
            canvas.save()
            canvas.translate(stoneScreenX, stoneScreenY)
            canvas.rotate(stoneRotation)
            
            // Pierre de curling - PLUS GROSSE
            paint.color = Color.parseColor("#666666")
            canvas.drawCircle(0f, 0f, 30f, paint) // AUGMENTÉ de 25f
            
            paint.color = Color.parseColor("#444444")
            canvas.drawCircle(0f, 0f, 25f, paint) // AUGMENTÉ de 20f
            
            // Poignée - PLUS GROSSE
            paint.color = Color.parseColor("#FFAA00")
            canvas.drawCircle(0f, 0f, 10f, paint) // AUGMENTÉ de 8f
            
            // Marque de rotation - PLUS VISIBLE
            paint.color = Color.WHITE
            paint.strokeWidth = 4f // AUGMENTÉ de 3f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(0f, -18f, 0f, -8f, paint) // PLUS LONG
            
            paint.style = Paint.Style.FILL
            canvas.restore()
            
            // Ombre si au sol
            if (!isStoneMoving) {
                paint.color = Color.parseColor("#33000000")
                canvas.drawOval(stoneScreenX - 40f, h * 0.85f, stoneScreenX + 40f, h * 0.9f, paint) // PLUS GROSSE
            }
            
            // Aura spéciale si en mouvement
            if (isStoneMoving) {
                paint.color = Color.parseColor("#3300FFFF")
                canvas.drawCircle(stoneScreenX, stoneScreenY, 45f, paint) // PLUS GROSSE de 35f
            }
        }
        
        private fun drawAimingInterface(canvas: Canvas, w: Int, h: Int) {
            val baseY = h - 140f // PLUS BAS
            
            // Indicateur de visée - TEXTE PLUS GROS
            paint.color = Color.parseColor("#001144")
            paint.textSize = 22f // AUGMENTÉ de 18f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Visée:", 30f, baseY, paint)
            
            // Barre de visée - PLUS GROSSE
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(120f, baseY - 18f, 450f, baseY, paint) // PLUS LARGE ET HAUTE
            
            paint.color = Color.GREEN
            val aimPos = 120f + (aimingX * 330f)
            canvas.drawRect(aimPos - 12f, baseY - 18f, aimPos + 12f, baseY, paint) // PLUS LARGE
            
            // Indicateur de puissance - TEXTE PLUS GROS
            canvas.drawText("Puissance:", 30f, baseY + 35f, paint)
            
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(120f, baseY + 17f, 450f, baseY + 35f, paint) // PLUS GROSSE
            
            val powerColor = when {
                launchPower > 80f -> Color.RED
                launchPower > 60f -> Color.YELLOW
                else -> Color.GREEN
            }
            paint.color = powerColor
            val powerWidth = (launchPower / 100f) * 330f
            canvas.drawRect(120f, baseY + 17f, 120f + powerWidth, baseY + 35f, paint)
            
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${launchPower.toInt()}%", 450f, baseY + 30f, paint)
        }
        
        private fun drawSweepingInterface(canvas: Canvas, w: Int, h: Int) {
            val baseY = h - 120f // PLUS BAS
            
            // Indicateur de balayage - TEXTE PLUS GROS
            paint.color = Color.parseColor("#001144")
            paint.textSize = 24f // AUGMENTÉ de 20f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Balayage:", 30f, baseY, paint)
            canvas.drawText("Compteur: $sweepingCount", 30f, baseY + 30f, paint)
            
            // Barre d'intensité - PLUS GROSSE
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(230f, baseY - 18f, 580f, baseY, paint) // PLUS LARGE ET HAUTE
            
            paint.color = Color.parseColor("#FF6600")
            val intensityWidth = sweepingIntensity * 350f
            canvas.drawRect(230f, baseY - 18f, 230f + intensityWidth, baseY, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 18f // AUGMENTÉ de 14f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("INTENSITÉ: ${(sweepingIntensity * 100).toInt()}%", 405f, baseY - 5f, paint)
            
            // Indication tactile
            if (!sweepingActive && !sweepingMotionDetected) {
                paint.color = Color.parseColor("#666666")
                paint.textSize = 20f // AUGMENTÉ de 16f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("👆 Balayez l'écran ou 📱 bougez le téléphone", w/2f, baseY + 60f, paint)
            }
        }
        
        private fun drawAllEffects(canvas: Canvas, w: Int, h: Int) {
            // Traînées de glace
            paint.color = Color.parseColor("#AACCCCFF")
            val currentTime = System.currentTimeMillis()
            for (trail in iceTrails) {
                val alpha = ((6000 - (currentTime - trail.timestamp)) / 6000f * 180).toInt() // ADAPTÉ
                paint.alpha = maxOf(0, alpha)
                canvas.drawCircle(trail.x * w, trail.y * h, 8f, paint) // PLUS GROS de 6f
            }
            paint.alpha = 255
            
            // Effets de balayage
            paint.color = Color.parseColor("#FFAA00")
            for (effect in sweepingEffects) {
                paint.alpha = (effect.life * 255).toInt()
                canvas.drawCircle(effect.x * w, effect.y * h, effect.life * 10f, paint) // PLUS GROS de 8f
            }
            paint.alpha = 255
            
            // Ondulations de cible
            paint.color = Color.parseColor("#6600FF00")
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5f // AUGMENTÉ de 4f
            for (ripple in targetRipples) {
                paint.alpha = (ripple.life * 150).toInt()
                canvas.drawCircle(ripple.x * w, ripple.y * h, ripple.radius * w * 0.5f, paint)
            }
            paint.alpha = 255
            paint.style = Paint.Style.FILL
            
            // Étincelles de pierre
            for (sparkle in stoneSparkles) {
                paint.alpha = (sparkle.life * 255).toInt()
                paint.color = sparkle.color
                canvas.drawCircle(sparkle.x * w, sparkle.y * h, sparkle.life * 7f, paint) // PLUS GROS de 5f
            }
            paint.alpha = 255
        }
    }

    data class IceTrail(
        val x: Float,
        val y: Float,
        val timestamp: Long
    )
    
    data class SweepingEffect(
        val x: Float,
        val y: Float,
        var life: Float
    )
    
    data class TargetRipple(
        val x: Float,
        val y: Float,
        var radius: Float,
        val maxRadius: Float,
        var life: Float
    )
    
    data class StoneSparkle(
        val x: Float,
        val y: Float,
        val color: Int,
        var life: Float
    )

    enum class GameState {
        PREPARATION, AIMING, SWEEPING, RESULTS, FINISHED
    }
}
