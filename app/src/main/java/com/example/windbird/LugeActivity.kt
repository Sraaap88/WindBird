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

class LugeActivity : Activity(), SensorEventListener {

    private lateinit var gameView: LugeView
    private lateinit var statusText: TextView

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null
    private var accelerometer: Sensor? = null

    // Variables de gameplay LUGE - RALENTI
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Phases avec durées PLUS LONGUES et accessibles
    private val preparationDuration = 8f  // 4f -> 8f
    private val rideDuration = 45f  // 28f -> 45f - Descente plus longue
    private val resultsDuration = 8f  // 5f -> 8f
    
    // Variables de luge
    private var lugerX = 0.5f // Position horizontale sur la piste (0.0 = gauche, 1.0 = droite)
    private var speed = 0f
    private var maxSpeed = 80f  // 120f -> 80f - Vitesse max réduite
    private var distance = 0f
    private var totalDistance = 1400f
    private var raceTime = 0f
    
    // Système de piste avec virages
    private val trackCurves = mutableListOf<TrackCurve>()
    private var nextCurveIndex = 0
    private var currentCurveStrength = 0f
    private var curveDirection = 0f // -1 = gauche, +1 = droite
    
    // Contrôles gyroscope/accéléromètre - SENSIBILITÉ RÉDUITE
    private var tiltX = 0f
    private var tiltY = 0f
    private var tiltZ = 0f
    private var accelX = 0f
    private var accelY = 0f
    private var accelZ = 0f
    
    // Performance et physique
    private var wallHits = 0
    private var perfectCurves = 0
    private var aerodynamics = 100f
    private var precision = 100f
    private var momentum = 0f
    private var gForce = 0f
    private var topSpeed = 0f
    
    // Système de freinage aux pieds
    private var brakingPower = 0f
    private var lastBrakeTime = 0L
    private var brakingUsed = false
    
    // Effets visuels de vitesse
    private var cameraShake = 0f
    private var speedBlur = 0f
    private val iceChips = mutableListOf<IceChip>()
    private val speedStreaks = mutableListOf<SpeedStreak>()
    private val wallSparks = mutableListOf<WallSpark>()
    private val curveIndicators = mutableListOf<CurveIndicator>()
    
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
            text = "🛷 LUGE - ${tournamentData.playerNames[currentPlayerIndex]}"
            setTextColor(Color.WHITE)
            textSize = 22f  // 18f -> 22f - Texte plus grand
            setBackgroundColor(Color.parseColor("#001133"))
            setPadding(25, 20, 25, 20)  // Plus de padding
        }

        gameView = LugeView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        
        initializeGame()
    }
    
    private fun initializeGame() {
        gameState = GameState.PREPARATION
        phaseTimer = 0f
        lugerX = 0.5f
        speed = 0f
        distance = 0f
        raceTime = 0f
        nextCurveIndex = 0
        currentCurveStrength = 0f
        curveDirection = 0f
        tiltX = 0f
        tiltY = 0f
        tiltZ = 0f
        accelX = 0f
        accelY = 0f
        accelZ = 0f
        wallHits = 0
        perfectCurves = 0
        aerodynamics = 100f
        precision = 100f
        momentum = 0f
        gForce = 0f
        topSpeed = 0f
        brakingPower = 0f
        lastBrakeTime = 0L
        brakingUsed = false
        cameraShake = 0f
        speedBlur = 0f
        finalScore = 0
        scoreCalculated = false
        
        trackCurves.clear()
        iceChips.clear()
        speedStreaks.clear()
        wallSparks.clear()
        curveIndicators.clear()
        
        generateLugeTrack()
    }
    
    private fun generateLugeTrack() {
        // Générer une piste avec virages moins fréquents
        var currentDistance = 200f  // 150f -> 200f
        
        repeat(8) { i ->  // 12 -> 8 virages
            val curveType = when (i % 3) {  // Plus de variété réduite
                0 -> TrackCurve.Type.GENTLE
                1 -> TrackCurve.Type.MEDIUM
                else -> TrackCurve.Type.SHARP
            }
            
            val direction = if (kotlin.random.Random.nextBoolean()) -1f else 1f
            
            trackCurves.add(TrackCurve(
                distance = currentDistance,
                type = curveType,
                direction = direction,
                length = when (curveType) {
                    TrackCurve.Type.GENTLE -> 120f  // 80f -> 120f
                    TrackCurve.Type.MEDIUM -> 100f  // 60f -> 100f
                    TrackCurve.Type.SHARP -> 80f   // 40f -> 80f
                    TrackCurve.Type.CHICANE -> 140f // 100f -> 140f
                }
            ))
            
            currentDistance += when (curveType) {
                TrackCurve.Type.GENTLE -> 180f  // 120f -> 180f
                TrackCurve.Type.MEDIUM -> 160f  // 100f -> 160f
                TrackCurve.Type.SHARP -> 140f   // 80f -> 140f
                TrackCurve.Type.CHICANE -> 200f // 140f -> 200f
            }
        }
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
            }
        }

        // Progression du jeu PLUS LENTE
        phaseTimer += 0.015f  // 0.03f -> 0.015f
        if (gameState == GameState.RIDING) {
            raceTime += 0.015f  // 0.03f -> 0.015f
        }

        when (gameState) {
            GameState.PREPARATION -> handlePreparation()
            GameState.RIDING -> handleRiding()
            GameState.RESULTS -> handleResults()
            GameState.FINISHED -> {}
        }

        updateEffects()
        updateStatus()
        gameView.invalidate()
    }
    
    private fun handlePreparation() {
        if (phaseTimer >= preparationDuration) {
            gameState = GameState.RIDING
            phaseTimer = 0f
            speed = 15f // 25f -> 15f - Vitesse de départ réduite
        }
    }
    
    private fun handleRiding() {
        // Mouvement de la luge
        handleLugeMovement()
        
        // Système de piste et virages
        handleTrackCurves()
        
        // Freinage aux pieds
        handleFootBraking()
        
        // Physique et performance
        updatePhysics()
        
        // Progression de la course
        updateRaceProgress()
        
        // Fin de course
        if (distance >= totalDistance) {
            calculateFinalScore()
            gameState = GameState.RESULTS
            phaseTimer = 0f
        }
    }
    
    private fun handleLugeMovement() {
        // Mouvement horizontal basé sur l'inclinaison gauche/droite - SENSIBILITÉ RÉDUITE
        val steeringInput = tiltX * 0.4f  // 0.8f -> 0.4f
        
        // Applier la courbe actuelle si elle existe
        val curveEffect = currentCurveStrength * curveDirection * 0.2f  // 0.3f -> 0.2f
        val totalSteering = steeringInput + curveEffect
        
        lugerX += totalSteering * 0.008f  // 0.012f -> 0.008f
        lugerX = lugerX.coerceIn(0.05f, 0.95f)
        
        // Contrôle de vitesse avec inclinaison avant/arrière - PLUS PROGRESSIF
        when {
            tiltY < -0.6f -> {  // -0.4f -> -0.6f - Seuil plus élevé
                // Incliner vers l'avant = position aérodynamique
                speed += 1.5f  // 2.5f -> 1.5f
                aerodynamics += 0.05f  // 0.08f -> 0.05f
            }
            tiltY > 0.6f -> {  // 0.4f -> 0.6f
                // Incliner vers l'arrière = résistance
                speed -= 1.2f  // 1.8f -> 1.2f
                aerodynamics -= 0.03f  // 0.05f -> 0.03f
            }
            else -> {
                // Position neutre
                speed += 0.8f  // 1.2f -> 0.8f
            }
        }
        
        // Gestion des murs
        if (lugerX <= 0.1f || lugerX >= 0.9f) {
            handleWallContact()
        }
        
        // Momentum basé sur la vitesse
        momentum = speed / maxSpeed
        
        // G-Force basé sur les changements de direction - RÉDUIT
        gForce = abs(steeringInput) * speed * 0.01f  // 0.02f -> 0.01f
        
        speed = speed.coerceIn(10f, maxSpeed)  // 15f -> 10f
        topSpeed = maxOf(topSpeed, speed)
        
        // Effets visuels selon la vitesse - SEUILS PLUS ÉLEVÉS
        speedBlur = (speed / maxSpeed) * 0.6f  // 0.8f -> 0.6f
        
        if (speed > 40f) {  // 60f -> 40f
            generateSpeedEffects()
        }
        
        if (speed > 60f) {  // 90f -> 60f
            cameraShake = (speed - 60f) / 20f * 0.2f  // 0.3f -> 0.2f
        }
    }
    
    private fun handleWallContact() {
        wallHits++
        speed *= 0.85f // 0.75f -> 0.85f - Perte de vitesse moins sévère
        precision -= 5f  // 8f -> 5f
        cameraShake = 0.3f  // 0.5f -> 0.3f
        
        // Correction automatique vers le centre
        lugerX = if (lugerX <= 0.1f) 0.2f else 0.8f  // Plus de marge
        
        // Effets visuels
        generateWallSparks()
    }
    
    private fun handleTrackCurves() {
        // Vérifier les virages à proximité
        while (nextCurveIndex < trackCurves.size) {
            val curve = trackCurves[nextCurveIndex]
            val curveDistance = curve.distance - distance
            
            if (curveDistance < -curve.length) {
                // Virage terminé
                nextCurveIndex++
                currentCurveStrength = 0f
                curveDirection = 0f
                continue
            }
            
            if (curveDistance <= 80f && curveDistance >= -curve.length) {  // 50f -> 80f - Plus de préavis
                // Dans le virage
                val curveProgress = 1f - (curveDistance + curve.length) / (curve.length + 80f)
                currentCurveStrength = sin(curveProgress * PI).toFloat() * curve.intensity * 0.7f  // Réduit l'intensité
                curveDirection = curve.direction
                
                // Évaluation de la prise de virage
                evaluateCurvePerformance(curve, curveProgress)
                
                // Indicateur visuel du virage - PLUS TÔT
                if (curveDistance > 0f && curveDistance < 60f) {  // 30f -> 60f
                    addCurveIndicator(curve)
                }
                
                break
            } else {
                break
            }
        }
    }
    
    private fun evaluateCurvePerformance(curve: TrackCurve, progress: Float) {
        if (progress > 0.2f && progress < 0.8f) {  // Plus de tolérance
            // Milieu du virage - évaluation critique
            val idealPosition = 0.5f + curve.direction * curve.intensity * 0.25f  // 0.3f -> 0.25f
            val positionError = abs(lugerX - idealPosition)
            
            if (positionError < 0.2f) {  // 0.15f -> 0.2f - Plus tolérant
                // Virage parfait !
                if (progress > 0.3f && progress < 0.7f) {  // Plus large
                    perfectCurves++
                    precision += 2f  // 3f -> 2f
                    generatePerfectCurveEffect()
                }
            }
        }
    }
    
    private fun handleFootBraking() {
        val currentTime = System.currentTimeMillis()
        
        // Détection du freinage avec rotation Z ou secousses - SEUIL PLUS ÉLEVÉ
        val brakeInput = abs(tiltZ) > 1.8f || sqrt(accelX*accelX + accelY*accelY + accelZ*accelZ) > 15f  // Plus difficile à déclencher
        
        if (brakeInput && currentTime - lastBrakeTime > 500) {  // 300 -> 500ms
            brakingPower += 10f  // 15f -> 10f
            speed *= 0.95f // 0.92f -> 0.95f - Freinage moins agressif
            brakingUsed = true
            lastBrakeTime = currentTime
            
            // Effets visuels du freinage
            generateBrakingEffect()
        }
        
        brakingPower = brakingPower.coerceIn(0f, 100f)
        brakingPower *= 0.97f // 0.95f -> 0.97f - Décroissance plus lente
    }
    
    private fun updatePhysics() {
        // Dégradation naturelle PLUS LENTE
        aerodynamics -= 0.01f  // 0.02f -> 0.01f
        precision -= 0.02f     // 0.03f -> 0.02f
        
        // Bonus pour vitesse et stabilité
        if (speed > 50f && abs(tiltX) < 0.4f) {  // 80f -> 50f, 0.3f -> 0.4f
            aerodynamics += 0.03f  // 0.05f -> 0.03f
        }
        
        if (gForce < 0.4f) {  // 0.3f -> 0.4f
            precision += 0.015f  // 0.02f -> 0.015f
        }
        
        // Contraintes
        aerodynamics = aerodynamics.coerceIn(70f, 130f)
        precision = precision.coerceIn(60f, 130f)
    }
    
    private fun updateRaceProgress() {
        distance += speed * 0.06f  // 0.09f -> 0.06f - Progression plus lente
    }
    
    private fun generateSpeedEffects() {
        // Copeaux de glace - MOINS FRÉQUENTS
        if (kotlin.random.Random.nextFloat() < 0.7f) {  // Pas à chaque frame
            iceChips.add(IceChip(
                x = kotlin.random.Random.nextFloat() * 800f + 100f,
                y = kotlin.random.Random.nextFloat() * 600f + 200f,
                vx = (kotlin.random.Random.nextFloat() - 0.5f) * 3f,  // 4f -> 3f
                vy = kotlin.random.Random.nextFloat() * 2f + 1.5f,    // Plus lent
                life = 1.2f  // 1f -> 1.2f - Plus longue durée
            ))
        }
        
        // Traînées de vitesse
        if (kotlin.random.Random.nextFloat() < 0.5f) {
            speedStreaks.add(SpeedStreak(
                x = kotlin.random.Random.nextFloat() * 800f + 100f,
                y = kotlin.random.Random.nextFloat() * 600f + 100f,
                speed = speed * 0.6f,  // 0.8f -> 0.6f
                life = 1f  // 0.8f -> 1f
            ))
        }
        
        if (iceChips.size > 15) iceChips.removeFirst()  // 25 -> 15
        if (speedStreaks.size > 12) speedStreaks.removeFirst()  // 20 -> 12
    }
    
    private fun generateWallSparks() {
        repeat(5) {  // 8 -> 5
            wallSparks.add(WallSpark(
                x = lugerX * 800f + kotlin.random.Random.nextFloat() * 80f,  // 100f -> 80f
                y = kotlin.random.Random.nextFloat() * 150f + 400f,  // 200f -> 150f
                vx = (kotlin.random.Random.nextFloat() - 0.5f) * 6f,  // 8f -> 6f
                vy = kotlin.random.Random.nextFloat() * -4f - 1.5f,   // Moins violent
                life = 1f  // 1.2f -> 1f
            ))
        }
    }
    
    private fun generatePerfectCurveEffect() {
        repeat(8) {  // 12 -> 8
            iceChips.add(IceChip(
                x = kotlin.random.Random.nextFloat() * 300f + 350f,  // 400f -> 300f
                y = kotlin.random.Random.nextFloat() * 250f + 375f,  // 300f -> 250f
                vx = (kotlin.random.Random.nextFloat() - 0.5f) * 4f,  // 6f -> 4f
                vy = kotlin.random.Random.nextFloat() * -3f - 1.5f,   // Moins violent
                life = 1.3f  // 1.5f -> 1.3f
            ))
        }
    }
    
    private fun generateBrakingEffect() {
        repeat(4) {  // 6 -> 4
            iceChips.add(IceChip(
                x = kotlin.random.Random.nextFloat() * 150f + 425f,  // 200f -> 150f
                y = kotlin.random.Random.nextFloat() * 80f + 520f,   // 100f -> 80f
                vx = (kotlin.random.Random.nextFloat() - 0.5f) * 3f,  // 5f -> 3f
                vy = kotlin.random.Random.nextFloat() * 3f + 0.8f,    // Plus lent
                life = 1.1f  // 1f -> 1.1f
            ))
        }
    }
    
    private fun addCurveIndicator(curve: TrackCurve) {
        curveIndicators.add(CurveIndicator(
            direction = curve.direction,
            intensity = curve.intensity,
            type = curve.type,
            life = 3f  // 2f -> 3f - Plus longue durée
        ))
        
        if (curveIndicators.size > 2) {  // 3 -> 2
            curveIndicators.removeFirst()
        }
    }
    
    private fun updateEffects() {
        // Mise à jour des copeaux de glace - PLUS LENTE
        iceChips.removeAll { chip ->
            chip.x += chip.vx
            chip.y += chip.vy
            chip.life -= 0.015f  // 0.02f -> 0.015f
            chip.life <= 0f || chip.y > 800f
        }
        
        // Mise à jour des traînées de vitesse
        speedStreaks.removeAll { streak ->
            streak.x -= streak.speed
            streak.life -= 0.02f  // 0.025f -> 0.02f
            streak.life <= 0f || streak.x < -100f
        }
        
        // Mise à jour des étincelles de mur
        wallSparks.removeAll { spark ->
            spark.x += spark.vx
            spark.y += spark.vy
            spark.life -= 0.02f  // 0.03f -> 0.02f
            spark.life <= 0f || spark.y > 800f
        }
        
        // Mise à jour des indicateurs de virage
        curveIndicators.removeAll { indicator ->
            indicator.life -= 0.015f  // 0.02f -> 0.015f
            indicator.life <= 0f
        }
        
        cameraShake = maxOf(0f, cameraShake - 0.015f)  // 0.02f -> 0.015f
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
            val timeBonus = maxOf(0, 150 - raceTime.toInt()) * 2  // Plus généreux
            val speedBonus = (topSpeed / maxSpeed * 70).toInt()   // 80 -> 70
            val precisionBonus = ((precision - 100f) * 1.5f).toInt()  // 2f -> 1.5f
            val aerodynamicsBonus = ((aerodynamics - 100f) * 1.2f).toInt()  // 1.5f -> 1.2f
            val perfectCurveBonus = perfectCurves * 20  // 25 -> 20
            val wallPenalty = wallHits * 12  // 15 -> 12
            val brakingPenalty = if (brakingUsed) 8 else 0  // 10 -> 8
            
            finalScore = maxOf(70, timeBonus + speedBonus + precisionBonus + aerodynamicsBonus + perfectCurveBonus - wallPenalty - brakingPenalty)  // 60 -> 70
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
                val aiScore = (95..185).random()
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
            GameState.PREPARATION -> "🛷 ${tournamentData.playerNames[currentPlayerIndex]} | Position de départ... ${(preparationDuration - phaseTimer).toInt() + 1}s"
            GameState.RIDING -> "🛷 ${tournamentData.playerNames[currentPlayerIndex]} | ${speed.toInt()} km/h | Virages parfaits: $perfectCurves | Murs: $wallHits"
            GameState.RESULTS -> "🏆 ${tournamentData.playerNames[currentPlayerIndex]} | Temps: ${raceTime.toInt()}s | Score: ${finalScore}"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Descente terminée!"
        }
    }

    inner class LugeView(context: Context) : View(context) {
        private val paint = Paint()

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            
            // Appliquer effets de caméra
            canvas.save()
            if (cameraShake > 0f) {
                canvas.translate(
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 15f,  // 25f -> 15f
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 15f
                )
            }
            
            // Effet de flou de vitesse RÉDUIT
            if (speedBlur > 0.4f) {  // 0.3f -> 0.4f
                paint.alpha = (speedBlur * 80).toInt()  // 100 -> 80
                paint.color = Color.parseColor("#CCFFFFFF")
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                paint.alpha = 255
            }
            
            when (gameState) {
                GameState.PREPARATION -> drawPreparation(canvas, w, h)
                GameState.RIDING -> drawRiding(canvas, w, h)
                GameState.RESULTS -> drawResults(canvas, w, h)
                GameState.FINISHED -> drawResults(canvas, w, h)
            }
            
            drawAllEffects(canvas, w, h)
            canvas.restore()
        }
        
        private fun drawPreparation(canvas: Canvas, w: Int, h: Int) {
            // Fond glacé
            paint.color = Color.parseColor("#E6F8FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste de luge en perspective
            drawLugeTrack(canvas, w, h, true)
            
            // Instructions - TEXTE PLUS GRAND ET VISIBLE
            paint.color = Color.parseColor("#001133")
            paint.textSize = 48f  // 36f -> 48f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🛷 LUGE RAPIDE 🛷", w/2f, h * 0.15f, paint)
            
            paint.textSize = 32f  // 22f -> 32f
            paint.color = Color.parseColor("#0066CC")
            canvas.drawText("Position de départ...", w/2f, h * 0.8f, paint)
            
            paint.textSize = 24f  // 16f -> 24f
            paint.color = Color.parseColor("#333333")  // Plus visible
            canvas.drawText("📱 Inclinez DOUCEMENT pour diriger", w/2f, h * 0.87f, paint)
            canvas.drawText("📱 Secouez FORT pour freiner", w/2f, h * 0.92f, paint)
        }
        
        private fun drawRiding(canvas: Canvas, w: Int, h: Int) {
            // Fond dynamique
            val bgColor = when {
                speed > 60f -> Color.parseColor("#F0F0FF")  // 100f -> 60f
                speed > 40f -> Color.parseColor("#F5F5FF")  // 70f -> 40f
                else -> Color.parseColor("#E6F8FF")
            }
            paint.color = bgColor
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste avec virages
            drawLugeTrack(canvas, w, h, false)
            
            // Indicateurs de virages
            drawCurveIndicators(canvas, w, h)
            
            // Luge et lugeur
            drawLuger(canvas, w, h)
            
            // Interface de course
            drawRaceInterface(canvas, w, h)
            
            // Instructions dynamiques - PLUS VISIBLES
            paint.color = Color.parseColor("#001133")
            paint.textSize = 28f  // 18f -> 28f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("📱 INCLINEZ DOUCEMENT DANS LES VIRAGES!", w/2f, 50f, paint)
            
            // Indicateur de vitesse critique
            if (speed > 65f) {  // 100f -> 65f
                paint.color = Color.RED
                paint.textSize = 32f  // 24f -> 32f
                canvas.drawText("⚡ VITESSE ÉLEVÉE! ⚡", w/2f, h - 40f, paint)
            }
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            // Fond métallique
            paint.color = Color.parseColor("#F8F8FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Bandeau argenté
            paint.color = Color.parseColor("#C0C0C0")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
            
            // Score final - PLUS GRAND
            paint.color = Color.parseColor("#001133")
            paint.textSize = 96f  // 72f -> 96f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore}", w/2f, h * 0.2f, paint)
            
            paint.textSize = 36f  // 28f -> 36f
            canvas.drawText("POINTS", w/2f, h * 0.3f, paint)
            
            // Détails performance - PLUS LISIBLES
            paint.color = Color.parseColor("#333333")
            paint.textSize = 24f  // 18f -> 24f
            canvas.drawText("⏱️ Temps: ${raceTime.toInt()}s", w/2f, h * 0.5f, paint)
            canvas.drawText("⚡ Vitesse max: ${topSpeed.toInt()} km/h", w/2f, h * 0.55f, paint)
            canvas.drawText("🎯 Virages parfaits: $perfectCurves", w/2f, h * 0.6f, paint)
            canvas.drawText("💨 Aérodynamisme: ${aerodynamics.toInt()}%", w/2f, h * 0.65f, paint)
            canvas.drawText("🎪 Précision: ${precision.toInt()}%", w/2f, h * 0.7f, paint)
            
            if (wallHits > 0) {
                paint.color = Color.RED
                canvas.drawText("💥 Contacts murs: $wallHits", w/2f, h * 0.75f, paint)
            }
            
            if (brakingUsed) {
                paint.color = Color.parseColor("#666666")
                canvas.drawText("🦶 Freinage utilisé", w/2f, h * 0.8f, paint)
            }
        }
        
        private fun drawLugeTrack(canvas: Canvas, w: Int, h: Int, isPreparation: Boolean) {
            // Piste de luge avec murs hauts
            paint.color = Color.WHITE
            
            val trackWidth = w * 0.6f
            val wallHeight = if (isPreparation) 0f else currentCurveStrength * 40f  // 50f -> 40f
            val curveOffset = if (isPreparation) 0f else currentCurveStrength * curveDirection * 80f  // 100f -> 80f
            
            // Piste centrale avec courbure
            val trackPath = Path()
            trackPath.moveTo((w - trackWidth) / 2f + curveOffset * 0.3f, 0f)
            trackPath.lineTo((w + trackWidth) / 2f + curveOffset * 0.3f, 0f)
            trackPath.lineTo(w * 0.85f + curveOffset, h.toFloat())
            trackPath.lineTo(w * 0.15f + curveOffset, h.toFloat())
            trackPath.close()
            canvas.drawPath(trackPath, paint)
            
            // Murs de la piste
            paint.color = Color.parseColor("#CCCCCC")
            paint.strokeWidth = 8f
            paint.style = Paint.Style.STROKE
            
            // Mur gauche
            canvas.drawLine(w * 0.15f + curveOffset, 0f, w * 0.15f + curveOffset, h.toFloat(), paint)
            // Mur droit
            canvas.drawLine(w * 0.85f + curveOffset, 0f, w * 0.85f + curveOffset, h.toFloat(), paint)
            
            paint.style = Paint.Style.FILL
            
            // Lignes de glace pour l'effet de vitesse - PLUS LENTES
            if (!isPreparation) {
                paint.color = Color.parseColor("#EEEEFF")
                paint.strokeWidth = 2f
                paint.style = Paint.Style.STROKE
                
                for (i in 1..8) {
                    val lineY = (i * h / 9f + (distance * 1.5f) % (h / 9f))  // 2f -> 1.5f
                    val lineLeft = w * 0.2f + curveOffset * 0.7f
                    val lineRight = w * 0.8f + curveOffset * 0.7f
                    canvas.drawLine(lineLeft, lineY, lineRight, lineY, paint)
                }
                
                paint.style = Paint.Style.FILL
            }
        }
        
        private fun drawCurveIndicators(canvas: Canvas, w: Int, h: Int) {
            for (indicator in curveIndicators) {
                val alpha = (indicator.life * 255 / 3f).toInt()  // Ajusté pour la nouvelle durée
                paint.alpha = alpha
                
                val arrowColor = when (indicator.type) {
                    TrackCurve.Type.GENTLE -> Color.GREEN
                    TrackCurve.Type.MEDIUM -> Color.YELLOW
                    TrackCurve.Type.SHARP -> Color.RED
                    TrackCurve.Type.CHICANE -> Color.MAGENTA
                }
                paint.color = arrowColor
                
                val centerX = w / 2f
                val arrowY = h * 0.2f
                val arrowSize = 40f * indicator.intensity  // 30f -> 40f - Plus grande
                
                // Flèche directionnelle - PLUS ÉPAISSE
                paint.strokeWidth = 8f  // Ajouté
                paint.style = Paint.Style.STROKE
                
                if (indicator.direction < 0) {
                    // Flèche gauche
                    canvas.drawLine(centerX - arrowSize, arrowY, centerX, arrowY - arrowSize/2, paint)
                    canvas.drawLine(centerX - arrowSize, arrowY, centerX, arrowY + arrowSize/2, paint)
                } else {
                    // Flèche droite
                    canvas.drawLine(centerX + arrowSize, arrowY, centerX, arrowY - arrowSize/2, paint)
                    canvas.drawLine(centerX + arrowSize, arrowY, centerX, arrowY + arrowSize/2, paint)
                }
                
                paint.style = Paint.Style.FILL
                
                // Texte du type de virage - PLUS GRAND
                paint.textSize = 20f  // 14f -> 20f
                paint.textAlign = Paint.Align.CENTER
                val typeText = when (indicator.type) {
                    TrackCurve.Type.GENTLE -> "DOUX"
                    TrackCurve.Type.MEDIUM -> "MOYEN"
                    TrackCurve.Type.SHARP -> "SERRÉ"
                    TrackCurve.Type.CHICANE -> "CHICANE"
                }
                canvas.drawText(typeText, centerX, arrowY + 50f, paint)
            }
            paint.alpha = 255
        }
        
        private fun drawLuger(canvas: Canvas, w: Int, h: Int) {
            val lugerScreenX = lugerX * w
            val lugerScreenY = h * 0.75f
            
            canvas.save()
            canvas.translate(lugerScreenX, lugerScreenY)
            
            // Inclinaison selon les G-forces - RÉDUITE
            canvas.rotate(gForce * 10f)  // 15f -> 10f
            
            // Luge - PLUS GRANDE
            paint.color = Color.parseColor("#444444")
            canvas.drawRoundRect(-40f, 0f, 40f, 50f, 10f, 10f, paint)  // Plus grande
            
            // Lugeur allongé
            paint.color = Color.parseColor("#FF6600")
            canvas.drawOval(-35f, -20f, 35f, 20f, paint)  // Plus grand
            
            // Casque
            paint.color = Color.parseColor("#0066CC")
            canvas.drawCircle(0f, -25f, 15f, paint)  // Plus grand
            
            // Patins de la luge
            paint.color = Color.parseColor("#AAAAAA")
            paint.strokeWidth = 6f  // 4f -> 6f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(-25f, 45f, -25f, 55f, paint)
            canvas.drawLine(25f, 45f, 25f, 55f, paint)
            
            paint.style = Paint.Style.FILL
            canvas.restore()
            
            // Traînée de vitesse derrière la luge
            if (speed > 35f) {  // 60f -> 35f
                paint.color = Color.parseColor("#66FFFFFF")
                for (i in 1..3) {  // 4 -> 3
                    canvas.drawOval(
                        lugerScreenX - 40f, lugerScreenY + i * 25f,  // Plus grande
                        lugerScreenX + 40f, lugerScreenY + i * 25f + 18f,
                        paint
                    )
                }
            }
        }
        
        private fun drawRaceInterface(canvas: Canvas, w: Int, h: Int) {
            val baseY = h - 200f  // 160f -> 200f - Plus d'espace
            
            // Compteur de vitesse énorme
            drawSpeedometer(canvas, w - 160f, 120f, speed, maxSpeed)  // Plus grand
            
            // Métriques de performance - PLUS VISIBLES
            paint.color = Color.parseColor("#001133")
            paint.textSize = 20f  // 16f -> 20f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Virages parfaits: $perfectCurves", 20f, baseY, paint)
            canvas.drawText("Contacts murs: $wallHits", 20f, baseY + 30f, paint)  // +25f -> +30f
            canvas.drawText("Temps: ${raceTime.toInt()}s", 20f, baseY + 60f, paint)
            
            // Barres de performance - PLUS GRANDES
            drawMeter(canvas, 200f, baseY, 200f, precision / 130f, "PRÉCISION", Color.parseColor("#00AA00"))  // 180f -> 200f
            drawMeter(canvas, 200f, baseY + 30f, 200f, aerodynamics / 130f, "AÉRO", Color.parseColor("#0066CC"))
            drawMeter(canvas, 200f, baseY + 60f, 200f, momentum, "ÉLAN", Color.parseColor("#FF6600"))
            
            // G-Force
            if (gForce > 0.15f) {  // 0.2f -> 0.15f
                paint.color = Color.parseColor("#FF0000")
                paint.textSize = 22f  // 18f -> 22f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("G-FORCE: ${(gForce * 100).toInt()}%", w/2f, baseY + 90f, paint)
            }
            
            // Indicateur de freinage
            if (brakingPower > 0f) {
                paint.color = Color.parseColor("#FF4444")
                paint.textSize = 20f  // 16f -> 20f
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("🦶 FREINAGE: ${brakingPower.toInt()}%", w - 20f, baseY + 120f, paint)
            }
        }
        
        private fun drawSpeedometer(canvas: Canvas, centerX: Float, centerY: Float, currentSpeed: Float, maxSpeed: Float) {
            // Cadran - PLUS GRAND
            paint.color = Color.parseColor("#333333")
            canvas.drawCircle(centerX, centerY, 60f, paint)  // 50f -> 60f
            
            paint.color = Color.WHITE
            canvas.drawCircle(centerX, centerY, 55f, paint)  // 45f -> 55f
            
            // Aiguille - PLUS ÉPAISSE
            val speedAngle = (currentSpeed / maxSpeed) * 270f - 135f
            paint.color = Color.RED
            paint.strokeWidth = 8f  // 6f -> 8f
            paint.style = Paint.Style.STROKE
            
            val needleX = centerX + cos(Math.toRadians(speedAngle.toDouble())).toFloat() * 45f  // 35f -> 45f
            val needleY = centerY + sin(Math.toRadians(speedAngle.toDouble())).toFloat() * 45f
            canvas.drawLine(centerX, centerY, needleX, needleY, paint)
            
            paint.style = Paint.Style.FILL
            
            // Valeur numérique - PLUS GRANDE
            paint.color = Color.BLACK
            paint.textSize = 20f  // 16f -> 20f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${currentSpeed.toInt()}", centerX, centerY + 70f, paint)  // +60f -> +70f
            canvas.drawText("km/h", centerX, centerY + 90f, paint)  // +80f -> +90f
        }
        
        private fun drawMeter(canvas: Canvas, x: Float, y: Float, width: Float, 
                             value: Float, label: String, color: Int) {
            // Fond - PLUS HAUT
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(x, y, x + width, y + 22f, paint)  // 18f -> 22f
            
            // Barre
            paint.color = color
            val filledWidth = value.coerceIn(0f, 1f) * width
            canvas.drawRect(x, y, x + filledWidth, y + 22f, paint)
            
            // Label - PLUS GRAND
            paint.color = Color.WHITE
            paint.textSize = 16f  // 12f -> 16f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("$label: ${(value * 100).toInt()}%", x, y - 5f, paint)
        }
        
        private fun drawAllEffects(canvas: Canvas, w: Int, h: Int) {
            // Copeaux de glace
            paint.color = Color.parseColor("#CCEEFF")
            for (chip in iceChips) {
                paint.alpha = (chip.life * 255 / 1.2f).toInt()  // Ajusté pour nouvelle durée
                canvas.drawCircle(chip.x, chip.y, chip.life * 5f, paint)  // 4f -> 5f - Plus grands
            }
            paint.alpha = 255
            
            // Traînées de vitesse
            paint.color = Color.parseColor("#AACCCCFF")
            paint.strokeWidth = 4f  // 3f -> 4f - Plus épaisses
            paint.style = Paint.Style.STROKE
            for (streak in speedStreaks) {
                paint.alpha = (streak.life * 180).toInt()
                canvas.drawLine(streak.x, streak.y, streak.x + 40f, streak.y, paint)  // 30f -> 40f
            }
            paint.alpha = 255
            paint.style = Paint.Style.FILL
            
            // Étincelles de mur
            paint.color = Color.parseColor("#FFAA00")
            for (spark in wallSparks) {
                paint.alpha = (spark.life * 255).toInt()
                canvas.drawCircle(spark.x, spark.y, spark.life * 6f, paint)  // 5f -> 6f
            }
            paint.alpha = 255
        }
    }

    data class TrackCurve(
        val distance: Float,
        val type: Type,
        val direction: Float, // -1 = gauche, +1 = droite
        val length: Float
    ) {
        val intensity: Float = when (type) {
            Type.GENTLE -> 0.25f  // 0.3f -> 0.25f
            Type.MEDIUM -> 0.45f  // 0.6f -> 0.45f
            Type.SHARP -> 0.7f    // 0.9f -> 0.7f
            Type.CHICANE -> 0.4f  // 0.5f -> 0.4f
        }
        
        enum class Type { GENTLE, MEDIUM, SHARP, CHICANE }
    }
    
    data class IceChip(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float
    )
    
    data class SpeedStreak(
        var x: Float,
        val y: Float,
        val speed: Float,
        var life: Float
    )
    
    data class WallSpark(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float
    )
    
    data class CurveIndicator(
        val direction: Float,
        val intensity: Float,
        val type: TrackCurve.Type,
        var life: Float
    )

    enum class GameState {
        PREPARATION, RIDING, RESULTS, FINISHED
    }
}
