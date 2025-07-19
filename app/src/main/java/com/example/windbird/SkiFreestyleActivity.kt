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

class SkiFreestyleActivity : Activity(), SensorEventListener {

    private lateinit var gameView: SkiFreestyleView
    private lateinit var statusText: TextView

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null
    private var accelerometer: Sensor? = null

    // Variables de gameplay SKI FREESTYLE
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Phases avec durées accessibles
    private val preparationDuration = 5f
    private val courseRunDuration = 30f  // Course accessible de 30 secondes
    private val resultsDuration = 6f
    
    // Variables de ski freestyle
    private var skierX = 0.5f // Position horizontale
    private var skierY = 0.8f // Position verticale
    private var speed = 0f
    private var isInAir = false
    private var airTime = 0f
    private var jumpHeight = 0f
    private var lastJumpTime = 0L
    
    // Système de sauts et obstacles
    private val jumps = mutableListOf<FreestyleJump>()
    private var nextJumpIndex = 0
    private var jumpsHit = 0
    private var perfectLandings = 0
    
    // Système de tricks avancé
    private var currentTrick = FreestyleTrick.NONE
    private var trickProgress = 0f
    private var trickRotation = 0f
    private var tricksLanded = 0
    private var comboMultiplier = 1f
    private var comboActive = false
    private var comboCount = 0
    
    // Contrôles gyroscope/accéléromètre
    private var tiltX = 0f
    private var tiltY = 0f
    private var tiltZ = 0f
    private var accelX = 0f
    private var accelY = 0f
    private var accelZ = 0f
    private var shakeDetected = false
    
    // Performance et scoring
    private var distance = 0f
    private var totalDistance = 1500f
    private var style = 100f
    private var technique = 100f
    private var creativity = 0f
    private var amplitude = 0f
    private var totalScore = 0f
    private var finalScore = 0
    private var scoreCalculated = false
    
    // Effets visuels spectaculaires
    private var cameraShake = 0f
    private var backgroundTilt = 0f
    private val snowClouds = mutableListOf<SnowCloud>()
    private val trickParticles = mutableListOf<TrickParticle>()
    private val landingEffects = mutableListOf<LandingEffect>()
    private val speedLines = mutableListOf<SpeedLine>()

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
            text = "🎿 SKI FREESTYLE - ${tournamentData.playerNames[currentPlayerIndex]}"
            setTextColor(Color.WHITE)
            textSize = 18f
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(20, 15, 20, 15)
        }

        gameView = SkiFreestyleView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        
        initializeGame()
    }
    
    private fun initializeGame() {
        gameState = GameState.PREPARATION
        phaseTimer = 0f
        skierX = 0.5f
        skierY = 0.8f
        speed = 0f
        isInAir = false
        airTime = 0f
        jumpHeight = 0f
        lastJumpTime = 0L
        nextJumpIndex = 0
        jumpsHit = 0
        perfectLandings = 0
        currentTrick = FreestyleTrick.NONE
        trickProgress = 0f
        trickRotation = 0f
        tricksLanded = 0
        comboMultiplier = 1f
        comboActive = false
        comboCount = 0
        distance = 0f
        style = 100f
        technique = 100f
        creativity = 0f
        amplitude = 0f
        totalScore = 0f
        finalScore = 0
        scoreCalculated = false
        cameraShake = 0f
        backgroundTilt = 0f
        shakeDetected = false
        
        jumps.clear()
        snowClouds.clear()
        trickParticles.clear()
        landingEffects.clear()
        speedLines.clear()
        
        generateFreestyleCourse()
    }
    
    private fun generateFreestyleCourse() {
        // Générer un parcours avec 8 sauts de différentes tailles
        var currentDistance = 200f
        
        repeat(8) { i ->
            val jumpSize = when (i % 3) {
                0 -> FreestyleJump.Size.SMALL
                1 -> FreestyleJump.Size.MEDIUM
                else -> FreestyleJump.Size.LARGE
            }
            
            jumps.add(FreestyleJump(
                distance = currentDistance,
                size = jumpSize,
                x = 0.3f + kotlin.random.Random.nextFloat() * 0.4f, // Position variée
                hit = false
            ))
            
            currentDistance += 150f + kotlin.random.Random.nextFloat() * 50f
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
                
                // Détection de secousses pour tricks spéciaux
                val totalAccel = sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)
                shakeDetected = totalAccel > 15f
            }
        }

        // Progression du jeu
        phaseTimer += 0.03f

        when (gameState) {
            GameState.PREPARATION -> handlePreparation()
            GameState.SKIING -> handleSkiing()
            GameState.RESULTS -> handleResults()
            GameState.FINISHED -> {}
        }

        updateEffects()
        updateStatus()
        gameView.invalidate()
    }
    
    private fun handlePreparation() {
        if (phaseTimer >= preparationDuration) {
            gameState = GameState.SKIING
            phaseTimer = 0f
            speed = 20f // Vitesse de départ confortable
        }
    }
    
    private fun handleSkiing() {
        // Mouvement du skieur
        handleSkierMovement()
        
        // Système de sauts
        handleJumpSystem()
        
        // Système de tricks
        handleTrickSystem()
        
        // Progression de la course
        updateCourseProgress()
        
        // Gestion de la physique
        updatePhysics()
        
        // Fin de course
        if (distance >= totalDistance) {
            calculateFinalScore()
            gameState = GameState.RESULTS
            phaseTimer = 0f
        }
    }
    
    private fun handleSkierMovement() {
        // Mouvement horizontal basé sur l'inclinaison
        val steeringInput = tiltX * 0.7f
        skierX += steeringInput * 0.01f
        skierX = skierX.coerceIn(0.1f, 0.9f)
        
        // Contrôle de vitesse avec inclinaison avant/arrière
        when {
            tiltY < -0.3f -> {
                // Incliner vers l'avant = accélération
                speed += 1.2f
                style += 0.02f
            }
            tiltY > 0.3f -> {
                // Incliner vers l'arrière = freinage/contrôle
                speed -= 0.8f
                technique += 0.01f
            }
            else -> {
                // Position neutre
                speed += 0.3f
            }
        }
        
        speed = speed.coerceIn(15f, 45f) // Vitesse modérée et contrôlable
        
        // Génération d'effets selon la vitesse
        if (speed > 30f) {
            generateSpeedEffects()
        }
    }
    
    private fun handleJumpSystem() {
        // Vérifier les sauts à proximité
        if (nextJumpIndex < jumps.size) {
            val nextJump = jumps[nextJumpIndex]
            val jumpDistance = nextJump.distance - distance
            
            // Approche du saut
            if (jumpDistance < 50f && jumpDistance > -20f) {
                val distanceToJump = abs(skierX - nextJump.x)
                
                if (jumpDistance < 10f && !nextJump.hit && distanceToJump < 0.2f) {
                    // Saut touché !
                    hitJump(nextJump)
                }
            }
        }
        
        // Physique de vol
        if (isInAir) {
            airTime += 0.03f
            skierY -= 0.004f // Retombée progressive
            
            // Atterrissage
            if (skierY >= 0.8f) {
                landFromJump()
            }
        } else {
            skierY = 0.8f // Sur le sol
        }
    }
    
    private fun hitJump(jump: FreestyleJump) {
        jump.hit = true
        isInAir = true
        jumpsHit++
        nextJumpIndex++
        
        // Hauteur selon la taille du saut et la vitesse
        val baseHeight = when (jump.size) {
            FreestyleJump.Size.SMALL -> 0.15f
            FreestyleJump.Size.MEDIUM -> 0.25f
            FreestyleJump.Size.LARGE -> 0.35f
        }
        
        jumpHeight = baseHeight + (speed / 45f) * 0.15f
        skierY = 0.8f - jumpHeight
        amplitude = maxOf(amplitude, jumpHeight)
        
        // Effets visuels
        cameraShake = jumpHeight * 1.5f
        generateJumpExplosion()
        
        // Bonus de score
        totalScore += when (jump.size) {
            FreestyleJump.Size.SMALL -> 15f
            FreestyleJump.Size.MEDIUM -> 25f
            FreestyleJump.Size.LARGE -> 40f
        }
        
        lastJumpTime = System.currentTimeMillis()
    }
    
    private fun landFromJump() {
        isInAir = false
        airTime = 0f
        skierY = 0.8f
        
        // Évaluation de l'atterrissage
        val landingQuality = evaluateLanding()
        
        if (landingQuality > 0.8f) {
            perfectLandings++
            generatePerfectLandingEffect()
            style += 5f
        } else if (landingQuality > 0.5f) {
            generateGoodLandingEffect()
            style += 2f
        } else {
            // Atterrissage raté
            style -= 3f
            cameraShake = 0.3f
        }
        
        // Finaliser le trick actuel
        if (currentTrick != FreestyleTrick.NONE) {
            finalizeTrick()
        }
    }
    
    private fun evaluateLanding(): Float {
        // Évaluation basée sur la stabilité au moment de l'atterrissage
        val stabilityScore = 1f - (abs(tiltX) + abs(tiltY) + abs(tiltZ)) / 3f
        val timingScore = if (skierY > 0.75f) 1f else 0.5f
        return (stabilityScore * timingScore).coerceIn(0f, 1f)
    }
    
    private fun handleTrickSystem() {
        if (!isInAir) return
        
        // Détection de nouveaux tricks
        if (currentTrick == FreestyleTrick.NONE) {
            detectNewTrick()
        } else {
            // Progression du trick en cours
            updateTrickProgress()
        }
    }
    
    private fun detectNewTrick() {
        val rotationThreshold = 1.0f
        val flipThreshold = 1.5f
        
        when {
            abs(tiltZ) > rotationThreshold && abs(tiltX) < 0.6f -> {
                startTrick(FreestyleTrick.SPIN_360)
            }
            abs(tiltY) > flipThreshold && abs(tiltX) < 0.6f -> {
                startTrick(FreestyleTrick.BACKFLIP)
            }
            abs(tiltX) > rotationThreshold && shakeDetected -> {
                startTrick(FreestyleTrick.GRAB_TRICK)
            }
            abs(tiltX) > 1.5f && abs(tiltZ) > 1.5f -> {
                startTrick(FreestyleTrick.CORK_SCREW)
            }
            shakeDetected && airTime > 0.3f -> {
                startTrick(FreestyleTrick.WILD_TRICK)
            }
        }
    }
    
    private fun startTrick(trick: FreestyleTrick) {
        currentTrick = trick
        trickProgress = 0f
        trickRotation = 0f
        
        // Effets visuels selon le trick
        when (trick) {
            FreestyleTrick.SPIN_360 -> generateSpinEffect()
            FreestyleTrick.BACKFLIP -> generateFlipEffect()
            FreestyleTrick.GRAB_TRICK -> generateGrabEffect()
            FreestyleTrick.CORK_SCREW -> generateCorkscrewEffect()
            FreestyleTrick.WILD_TRICK -> generateWildEffect()
            else -> {}
        }
        
        creativity += trick.creativityPoints
    }
    
    private fun updateTrickProgress() {
        when (currentTrick) {
            FreestyleTrick.SPIN_360 -> {
                trickProgress += abs(tiltZ) * 0.025f
                trickRotation += tiltZ * 3f
                backgroundTilt = trickRotation * 0.5f
            }
            FreestyleTrick.BACKFLIP -> {
                trickProgress += abs(tiltY) * 0.02f
                trickRotation += tiltY * 2f
            }
            FreestyleTrick.GRAB_TRICK -> {
                trickProgress += (abs(tiltX) + if (shakeDetected) 0.5f else 0f) * 0.03f
            }
            FreestyleTrick.CORK_SCREW -> {
                trickProgress += (abs(tiltX) + abs(tiltZ)) * 0.02f
                trickRotation += (tiltX + tiltZ) * 1.5f
            }
            FreestyleTrick.WILD_TRICK -> {
                trickProgress += (abs(tiltX) + abs(tiltY) + abs(tiltZ) + if (shakeDetected) 1f else 0f) * 0.02f
                trickRotation += (tiltX + tiltY + tiltZ) * 1f
            }
            else -> {}
        }
        
        trickProgress = trickProgress.coerceIn(0f, 1f)
    }
    
    private fun finalizeTrick() {
        if (trickProgress > 0.4f) {
            // Trick réussi !
            tricksLanded++
            val trickScore = calculateTrickScore()
            totalScore += trickScore
            
            // Système de combo
            if (comboActive) {
                comboCount++
                comboMultiplier += 0.3f
            } else {
                comboActive = true
                comboCount = 1
                comboMultiplier = 1.3f
            }
            
            // Effets selon la qualité
            if (trickProgress > 0.9f) {
                generateAmazingTrickEffect()
                technique += 8f
            } else if (trickProgress > 0.7f) {
                generateGreatTrickEffect()
                technique += 5f
            } else {
                generateGoodTrickEffect()
                technique += 2f
            }
            
        } else {
            // Trick raté
            comboActive = false
            comboCount = 0
            comboMultiplier = 1f
            technique -= 5f
        }
        
        currentTrick = FreestyleTrick.NONE
        trickProgress = 0f
        trickRotation = 0f
    }
    
    private fun calculateTrickScore(): Float {
        val baseScore = currentTrick.baseScore
        val progressBonus = trickProgress * 20f
        val airBonus = airTime * 15f
        val amplitudeBonus = amplitude * 25f
        
        return (baseScore + progressBonus + airBonus + amplitudeBonus) * comboMultiplier
    }
    
    private fun updateCourseProgress() {
        distance += speed * 0.08f
    }
    
    private fun updatePhysics() {
        // Dégradation naturelle
        style -= 0.04f
        technique -= 0.03f
        
        // Bonus pour vitesse et fluidité
        if (speed > 25f && !isInAir) {
            style += 0.05f
        }
        
        // Contraintes
        style = style.coerceIn(70f, 130f)
        technique = technique.coerceIn(70f, 130f)
    }
    
    private fun generateSpeedEffects() {
        speedLines.add(SpeedLine(
            x = kotlin.random.Random.nextFloat() * 800f + 100f,
            y = kotlin.random.Random.nextFloat() * 600f + 100f,
            speed = speed * 0.5f
        ))
        
        if (speedLines.size > 15) {
            speedLines.removeFirst()
        }
    }
    
    private fun generateJumpExplosion() {
        repeat(15) {
            snowClouds.add(SnowCloud(
                x = kotlin.random.Random.nextFloat() * 300f + 350f,
                y = kotlin.random.Random.nextFloat() * 150f + 500f,
                vx = (kotlin.random.Random.nextFloat() - 0.5f) * 8f,
                vy = kotlin.random.Random.nextFloat() * -6f - 3f,
                life = 1.5f
            ))
        }
    }
    
    private fun generateSpinEffect() {
        repeat(8) {
            trickParticles.add(TrickParticle(
                x = kotlin.random.Random.nextFloat() * 400f + 300f,
                y = kotlin.random.Random.nextFloat() * 300f + 250f,
                color = Color.CYAN,
                type = "SPIN",
                life = 1.2f
            ))
        }
    }
    
    private fun generateFlipEffect() {
        cameraShake = 0.4f
        repeat(10) {
            trickParticles.add(TrickParticle(
                x = kotlin.random.Random.nextFloat() * 400f + 300f,
                y = kotlin.random.Random.nextFloat() * 300f + 250f,
                color = Color.YELLOW,
                type = "FLIP",
                life = 1f
            ))
        }
    }
    
    private fun generateGrabEffect() {
        repeat(6) {
            trickParticles.add(TrickParticle(
                x = kotlin.random.Random.nextFloat() * 200f + 400f,
                y = kotlin.random.Random.nextFloat() * 200f + 300f,
                color = Color.GREEN,
                type = "GRAB",
                life = 0.8f
            ))
        }
    }
    
    private fun generateCorkscrewEffect() {
        cameraShake = 0.5f
        repeat(12) {
            trickParticles.add(TrickParticle(
                x = kotlin.random.Random.nextFloat() * 500f + 250f,
                y = kotlin.random.Random.nextFloat() * 400f + 200f,
                color = Color.MAGENTA,
                type = "CORK",
                life = 1.3f
            ))
        }
    }
    
    private fun generateWildEffect() {
        cameraShake = 0.7f
        repeat(20) {
            trickParticles.add(TrickParticle(
                x = kotlin.random.Random.nextFloat() * 600f + 200f,
                y = kotlin.random.Random.nextFloat() * 500f + 150f,
                color = Color.parseColor("#FF6600"),
                type = "WILD",
                life = 1.8f
            ))
        }
    }
    
    private fun generatePerfectLandingEffect() {
        repeat(25) {
            landingEffects.add(LandingEffect(
                x = kotlin.random.Random.nextFloat() * 600f + 200f,
                y = kotlin.random.Random.nextFloat() * 200f + 500f,
                color = Color.parseColor("#FFD700"),
                type = "PERFECT",
                life = 2f
            ))
        }
    }
    
    private fun generateGoodLandingEffect() {
        repeat(12) {
            landingEffects.add(LandingEffect(
                x = kotlin.random.Random.nextFloat() * 400f + 300f,
                y = kotlin.random.Random.nextFloat() * 150f + 550f,
                color = Color.parseColor("#00FF00"),
                type = "GOOD",
                life = 1.2f
            ))
        }
    }
    
    private fun generateAmazingTrickEffect() {
        cameraShake = 0.8f
        repeat(30) {
            trickParticles.add(TrickParticle(
                x = kotlin.random.Random.nextFloat() * 800f + 100f,
                y = kotlin.random.Random.nextFloat() * 600f + 100f,
                color = Color.parseColor("#FFD700"),
                type = "AMAZING",
                life = 2.5f
            ))
        }
    }
    
    private fun generateGreatTrickEffect() {
        repeat(15) {
            trickParticles.add(TrickParticle(
                x = kotlin.random.Random.nextFloat() * 500f + 250f,
                y = kotlin.random.Random.nextFloat() * 400f + 200f,
                color = Color.parseColor("#00FFFF"),
                type = "GREAT",
                life = 1.5f
            ))
        }
    }
    
    private fun generateGoodTrickEffect() {
        repeat(8) {
            trickParticles.add(TrickParticle(
                x = kotlin.random.Random.nextFloat() * 300f + 350f,
                y = kotlin.random.Random.nextFloat() * 250f + 300f,
                color = Color.parseColor("#00FF00"),
                type = "GOOD",
                life = 1f
            ))
        }
    }
    
    private fun updateEffects() {
        // Mise à jour des nuages de neige
        snowClouds.removeAll { cloud ->
            cloud.x += cloud.vx
            cloud.y += cloud.vy
            cloud.life -= 0.02f
            cloud.life <= 0f || cloud.y > 1000f
        }
        
        // Mise à jour des particules de tricks
        trickParticles.removeAll { particle ->
            particle.y -= 2f
            particle.life -= 0.025f
            particle.life <= 0f
        }
        
        // Mise à jour des effets d'atterrissage
        landingEffects.removeAll { effect ->
            effect.y -= 1.5f
            effect.life -= 0.02f
            effect.life <= 0f
        }
        
        // Mise à jour des lignes de vitesse
        speedLines.removeAll { line ->
            line.x -= line.speed
            line.x < -100f
        }
        
        cameraShake = maxOf(0f, cameraShake - 0.03f)
        backgroundTilt *= 0.92f
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
            val tricksBonus = totalScore.toInt()
            val styleBonus = ((style - 100f) * 3).toInt()
            val techniqueBonus = ((technique - 100f) * 2).toInt()
            val creativityBonus = creativity.toInt()
            val amplitudeBonus = (amplitude * 100).toInt()
            val perfectLandingBonus = perfectLandings * 20
            val comboBonus = if (comboCount > 2) comboCount * 25 else 0
            
            finalScore = maxOf(70, tricksBonus + styleBonus + techniqueBonus + creativityBonus + amplitudeBonus + perfectLandingBonus + comboBonus)
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
                val aiScore = (100..195).random()
                tournamentData.addScore(nextPlayer, eventIndex, aiScore)
                proceedToNextPlayerOrEvent()
            }
        } else {
            if (tournamentData.isTournamentComplete()) {
                val resultIntent = Intent(this, ScoreboardActivity::class.java).apply {
                    putExtra("tournament_data", tournamentData)
                    putExtra("tournament_final", true)
                }
                startActivity(intent)
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
            GameState.PREPARATION -> "🎿 ${tournamentData.playerNames[currentPlayerIndex]} | Préparation... ${(preparationDuration - phaseTimer).toInt() + 1}s"
            GameState.SKIING -> {
                val trickText = if (currentTrick != FreestyleTrick.NONE) " | ${currentTrick.displayName}" else ""
                val comboText = if (comboActive) " | COMBO x$comboCount" else ""
                "🎿 ${tournamentData.playerNames[currentPlayerIndex]} | Sauts: $jumpsHit/8 | Tricks: $tricksLanded$trickText$comboText"
            }
            GameState.RESULTS -> "🏆 ${tournamentData.playerNames[currentPlayerIndex]} | Score: ${finalScore} | Tricks: $tricksLanded"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Run terminé!"
        }
    }

    inner class SkiFreestyleView(context: Context) : View(context) {
        private val paint = Paint()

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            
            // Appliquer effets de caméra
            canvas.save()
            if (cameraShake > 0f) {
                canvas.translate(
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 20f,
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 20f
                )
            }
            canvas.rotate(backgroundTilt * 0.2f, w/2f, h/2f)
            
            when (gameState) {
                GameState.PREPARATION -> drawPreparation(canvas, w, h)
                GameState.SKIING -> drawSkiing(canvas, w, h)
                GameState.RESULTS -> drawResults(canvas, w, h)
                GameState.FINISHED -> drawResults(canvas, w, h)
            }
            
            drawAllEffects(canvas, w, h)
            canvas.restore()
        }
        
        private fun drawPreparation(canvas: Canvas, w: Int, h: Int) {
            // Fond montagneux spectaculaire
            paint.color = Color.parseColor("#E6F3FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Montagnes avec dégradé
            drawMountainBackground(canvas, w, h)
            
            // Piste de freestyle en perspective
            drawFreestylePiste(canvas, w, h)
            
            // Sauts visibles au loin
            drawPreviewJumps(canvas, w, h)
            
            // Instructions spectaculaires
            paint.color = Color.parseColor("#001122")
            paint.textSize = 36f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🎿 SKI FREESTYLE 🎿", w/2f, h * 0.15f, paint)
            
            paint.textSize = 22f
            paint.color = Color.parseColor("#0066CC")
            canvas.drawText("Préparez-vous pour les figures...", w/2f, h * 0.85f, paint)
            
            paint.textSize = 16f
            paint.color = Color.parseColor("#666666")
            canvas.drawText("📱 Inclinez pour diriger, bougez en l'air pour les tricks!", w/2f, h * 0.9f, paint)
            canvas.drawText("📱 Secouez pour des tricks sauvages!", w/2f, h * 0.95f, paint)
        }
        
        private fun drawSkiing(canvas: Canvas, w: Int, h: Int) {
            // Fond dynamique
            val bgColor = if (isInAir) Color.parseColor("#F0F8FF") else Color.parseColor("#E6F3FF")
            paint.color = bgColor
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Montagnes
            drawMountainBackground(canvas, w, h)
            
            // Piste avec perspective
            drawFreestylePiste(canvas, w, h)
            
            // Sauts du parcours
            drawCourseJumps(canvas, w, h)
            
            // Skieur
            drawFreestyleSkier(canvas, w, h)
            
            // Interface de jeu
            drawGameInterface(canvas, w, h)
            
            // Instructions dynamiques
            if (isInAir) {
                paint.color = Color.parseColor("#FF6600")
                paint.textSize = 26f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("🌟 TRICKS TIME! BOUGEZ! 🌟", w/2f, 50f, paint)
            } else {
                paint.color = Color.parseColor("#001122")
                paint.textSize = 18f
                canvas.drawText("📱 Dirigez-vous vers les sauts!", w/2f, 40f, paint)
            }
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            // Fond festif
            paint.color = Color.parseColor("#FFF8DC")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Bandeau doré
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
            
            // Score final
            paint.color = Color.parseColor("#001122")
            paint.textSize = 72f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore}", w/2f, h * 0.2f, paint)
            
            paint.textSize = 28f
            canvas.drawText("POINTS", w/2f, h * 0.3f, paint)
            
            // Détails performance
            paint.color = Color.parseColor("#333333")
            paint.textSize = 18f
            canvas.drawText("🎿 Tricks réussis: $tricksLanded", w/2f, h * 0.5f, paint)
            canvas.drawText("🎯 Sauts touchés: $jumpsHit/8", w/2f, h * 0.55f, paint)
            canvas.drawText("⭐ Style: ${style.toInt()}%", w/2f, h * 0.6f, paint)
            canvas.drawText("🛠️ Technique: ${technique.toInt()}%", w/2f, h * 0.65f, paint)
            canvas.drawText("🎨 Créativité: ${creativity.toInt()}", w/2f, h * 0.7f, paint)
            canvas.drawText("🏅 Atterrissages parfaits: $perfectLandings", w/2f, h * 0.75f, paint)
            
            if (comboCount > 1) {
                paint.color = Color.parseColor("#FF6600")
                canvas.drawText("🔥 Meilleur combo: x$comboCount", w/2f, h * 0.8f, paint)
            }
        }
        
        private fun drawMountainBackground(canvas: Canvas, w: Int, h: Int) {
            // Montagnes avec dégradé
            paint.color = Color.parseColor("#CCDDEE")
            val mountainPath = Path()
            mountainPath.moveTo(0f, h * 0.25f)
            mountainPath.lineTo(w * 0.3f, h * 0.1f)
            mountainPath.lineTo(w * 0.6f, h * 0.2f)
            mountainPath.lineTo(w * 0.9f, h * 0.05f)
            mountainPath.lineTo(w.toFloat(), h * 0.15f)
            mountainPath.lineTo(w.toFloat(), h.toFloat())
            mountainPath.lineTo(0f, h.toFloat())
            mountainPath.close()
            canvas.drawPath(mountainPath, paint)
        }
        
        private fun drawFreestylePiste(canvas: Canvas, w: Int, h: Int) {
            // Piste en perspective avec effet de profondeur
            paint.color = Color.WHITE
            val pisteWidth = w * 0.7f
            val perspectiveOffset = speed * 0.6f
            
            val pistePath = Path()
            pistePath.moveTo((w - pisteWidth) / 2f - perspectiveOffset, 0f)
            pistePath.lineTo((w + pisteWidth) / 2f + perspectiveOffset, 0f)
            pistePath.lineTo(w * 0.9f, h.toFloat())
            pistePath.lineTo(w * 0.1f, h.toFloat())
            pistePath.close()
            canvas.drawPath(pistePath, paint)
            
            // Lignes de perspective
            paint.color = Color.parseColor("#EEEEEE")
            paint.strokeWidth = 3f
            paint.style = Paint.Style.STROKE
            
            for (i in 1..6) {
                val lineY = (i * h / 7f + (distance * 1.5f) % (h / 7f))
                val lineLeft = w * 0.1f + (i * 0.05f * w)
                val lineRight = w * 0.9f - (i * 0.05f * w)
                canvas.drawLine(lineLeft, lineY, lineRight, lineY, paint)
            }
            
            paint.style = Paint.Style.FILL
        }
        
        private fun drawPreviewJumps(canvas: Canvas, w: Int, h: Int) {
            // Aperçu des sauts dans la préparation
            for (i in 0..2) {
                val jumpX = w * (0.2f + i * 0.3f)
                val jumpY = h * (0.4f + i * 0.15f)
                val jumpSize = 15f + i * 5f
                
                paint.color = Color.parseColor("#DDDDDD")
                canvas.drawRoundRect(jumpX - jumpSize, jumpY, jumpX + jumpSize, jumpY + jumpSize/2, 8f, 8f, paint)
            }
        }
        
        private fun drawCourseJumps(canvas: Canvas, w: Int, h: Int) {
            // Dessiner les sauts du parcours
            for (jump in jumps) {
                val jumpScreenDistance = jump.distance - distance
                
                if (jumpScreenDistance > -100f && jumpScreenDistance < 500f) {
                    val screenY = h * 0.3f + (jumpScreenDistance * 1.2f)
                    val perspectiveFactor = 1f - (jumpScreenDistance / 500f)
                    val jumpSize = when (jump.size) {
                        FreestyleJump.Size.SMALL -> 20f
                        FreestyleJump.Size.MEDIUM -> 35f
                        FreestyleJump.Size.LARGE -> 50f
                    } * perspectiveFactor.coerceIn(0.2f, 1f)
                    
                    val screenX = jump.x * w
                    
                    // Couleur selon le statut
                    paint.color = if (jump.hit) {
                        Color.parseColor("#00AA00")
                    } else {
                        Color.parseColor("#FFFFFF")
                    }
                    
                    // Dessiner le saut
                    canvas.drawRoundRect(
                        screenX - jumpSize, screenY,
                        screenX + jumpSize, screenY + jumpSize/2,
                        8f, 8f, paint
                    )
                    
                    // Indicateur de taille
                    if (perspectiveFactor > 0.5f) {
                        paint.color = Color.BLACK
                        paint.textSize = 12f * perspectiveFactor
                        paint.textAlign = Paint.Align.CENTER
                        val sizeText = when (jump.size) {
                            FreestyleJump.Size.SMALL -> "S"
                            FreestyleJump.Size.MEDIUM -> "M"
                            FreestyleJump.Size.LARGE -> "L"
                        }
                        canvas.drawText(sizeText, screenX, screenY + jumpSize/4, paint)
                    }
                }
            }
        }
        
        private fun drawFreestyleSkier(canvas: Canvas, w: Int, h: Int) {
            val skierScreenX = skierX * w
            val skierScreenY = skierY * h
            
            canvas.save()
            canvas.translate(skierScreenX, skierScreenY)
            
            // Rotation selon les tricks
            when (currentTrick) {
                FreestyleTrick.SPIN_360 -> canvas.rotate(trickRotation)
                FreestyleTrick.BACKFLIP -> canvas.rotate(trickRotation, 1f, 0f)
                FreestyleTrick.CORK_SCREW -> {
                    canvas.rotate(trickRotation * 0.7f)
                    canvas.scale(1f + trickProgress * 0.2f, 1f + trickProgress * 0.2f)
                }
                FreestyleTrick.WILD_TRICK -> {
                    canvas.rotate(trickRotation * 1.5f)
                    canvas.scale(1f + trickProgress * 0.4f, 1f + trickProgress * 0.4f)
                }
                else -> {}
            }
            
            // Corps du skieur
            paint.color = Color.parseColor("#FF6600")
            canvas.drawCircle(0f, 0f, 22f, paint)
            
            // Skis avec couleur selon le trick
            paint.color = if (currentTrick != FreestyleTrick.NONE) {
                Color.parseColor("#FFD700")
            } else {
                Color.YELLOW
            }
            paint.strokeWidth = 10f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(-20f, 25f, -20f, 50f, paint)
            canvas.drawLine(20f, 25f, 20f, 50f, paint)
            
            // Bâtons selon le trick
            paint.color = Color.parseColor("#8B4513")
            paint.strokeWidth = 6f
            
            if (currentTrick == FreestyleTrick.GRAB_TRICK) {
                // Position grab
                canvas.drawLine(-15f, -15f, -25f, 15f, paint)
                canvas.drawLine(15f, -15f, 25f, 15f, paint)
            } else {
                // Position normale
                canvas.drawLine(-18f, -10f, -30f, -25f, paint)
                canvas.drawLine(18f, -10f, 30f, -25f, paint)
            }
            
            paint.style = Paint.Style.FILL
            canvas.restore()
            
            // Ombre si au sol
            if (!isInAir) {
                paint.color = Color.parseColor("#33000000")
                canvas.drawOval(skierScreenX - 35f, h * 0.85f, skierScreenX + 35f, h * 0.9f, paint)
            }
            
            // Aura spéciale selon le trick
            if (currentTrick != FreestyleTrick.NONE && trickProgress > 0.5f) {
                paint.color = Color.parseColor("#44FFD700")
                canvas.drawCircle(skierScreenX, skierScreenY, 40f + trickProgress * 20f, paint)
            }
        }
        
        private fun drawGameInterface(canvas: Canvas, w: Int, h: Int) {
            val baseY = h - 160f
            
            // Score en temps réel
            paint.color = Color.parseColor("#001122")
            paint.textSize = 20f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Score: ${totalScore.toInt()}", 20f, baseY, paint)
            canvas.drawText("Sauts: $jumpsHit/8", 20f, baseY + 25f, paint)
            canvas.drawText("Tricks: $tricksLanded", 20f, baseY + 50f, paint)
            
            // Trick en cours
            if (currentTrick != FreestyleTrick.NONE) {
                paint.color = Color.parseColor("#FF6600")
                paint.textSize = 28f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("${currentTrick.displayName}: ${(trickProgress * 100).toInt()}%", w/2f, baseY, paint)
                
                // Barre de progression du trick
                paint.color = Color.parseColor("#333333")
                canvas.drawRect(w/2f - 120f, baseY + 15f, w/2f + 120f, baseY + 35f, paint)
                
                val progressColor = when {
                    trickProgress > 0.8f -> Color.parseColor("#FFD700")
                    trickProgress > 0.5f -> Color.parseColor("#00FF00")
                    else -> Color.parseColor("#FFAA00")
                }
                paint.color = progressColor
                val progressWidth = trickProgress * 240f
                canvas.drawRect(w/2f - 120f, baseY + 15f, w/2f - 120f + progressWidth, baseY + 35f, paint)
            }
            
            // Métriques de performance
            drawMeter(canvas, w - 220f, baseY, 180f, style / 130f, "STYLE", Color.parseColor("#FF44AA"))
            drawMeter(canvas, w - 220f, baseY + 30f, 180f, technique / 130f, "TECH", Color.parseColor("#44AAFF"))
            drawMeter(canvas, w - 220f, baseY + 60f, 180f, (creativity / 50f).coerceAtMost(1f), "CRÉA", Color.parseColor("#AA44FF"))
            
            // Combo actif
            if (comboActive) {
                paint.color = Color.parseColor("#FFD700")
                paint.textSize = 22f
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("🔥 COMBO x$comboCount", w - 20f, baseY + 90f, paint)
            }
            
            // Air time et amplitude
            if (isInAir) {
                paint.color = Color.parseColor("#00FFFF")
                paint.textSize = 18f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("⏱️ ${airTime.toString().take(4)}s", w/2f, h - 80f, paint)
                canvas.drawText("📏 ${(amplitude * 100).toInt()}%", w/2f, h - 60f, paint)
            }
        }
        
        private fun drawMeter(canvas: Canvas, x: Float, y: Float, width: Float, 
                             value: Float, label: String, color: Int) {
            // Fond
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(x, y, x + width, y + 18f, paint)
            
            // Barre
            paint.color = color
            val filledWidth = value.coerceIn(0f, 1f) * width
            canvas.drawRect(x, y, x + filledWidth, y + 18f, paint)
            
            // Label
            paint.color = Color.WHITE
            paint.textSize = 14f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("$label: ${(value * 100).toInt()}%", x, y - 3f, paint)
        }
        
        private fun drawAllEffects(canvas: Canvas, w: Int, h: Int) {
            // Lignes de vitesse
            paint.color = Color.parseColor("#AACCCCCC")
            paint.strokeWidth = 4f
            paint.style = Paint.Style.STROKE
            for (line in speedLines) {
                canvas.drawLine(line.x, line.y, line.x + 40f, line.y, paint)
            }
            paint.style = Paint.Style.FILL
            
            // Nuages de neige
            paint.color = Color.WHITE
            for (cloud in snowClouds) {
                paint.alpha = (cloud.life * 255).toInt()
                canvas.drawCircle(cloud.x, cloud.y, cloud.life * 12f, paint)
            }
            paint.alpha = 255
            
            // Particules de tricks
            for (particle in trickParticles) {
                paint.alpha = (particle.life * 255).toInt()
                paint.color = particle.color
                canvas.drawCircle(particle.x, particle.y, particle.life * 8f, paint)
            }
            paint.alpha = 255
            
            // Effets d'atterrissage
            for (effect in landingEffects) {
                paint.alpha = (effect.life * 255).toInt()
                paint.color = effect.color
                canvas.drawCircle(effect.x, effect.y, effect.life * 10f, paint)
            }
            paint.alpha = 255
        }
    }

    enum class FreestyleTrick(val displayName: String, val baseScore: Float, val creativityPoints: Float) {
        NONE("", 0f, 0f),
        SPIN_360("360°", 20f, 2f),
        BACKFLIP("BACKFLIP", 30f, 4f),
        GRAB_TRICK("GRAB", 25f, 3f),
        CORK_SCREW("CORK", 40f, 6f),
        WILD_TRICK("WILD", 50f, 8f)
    }
    
    data class FreestyleJump(
        val distance: Float,
        val size: Size,
        val x: Float,
        var hit: Boolean
    ) {
        enum class Size { SMALL, MEDIUM, LARGE }
    }
    
    data class SnowCloud(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float
    )
    
    data class TrickParticle(
        val x: Float,
        var y: Float,
        val color: Int,
        val type: String,
        var life: Float
    )
    
    data class LandingEffect(
        val x: Float,
        var y: Float,
        val color: Int,
        val type: String,
        var life: Float
    )
    
    data class SpeedLine(
        var x: Float,
        val y: Float,
        val speed: Float
    )

    enum class GameState {
        PREPARATION, SKIING, RESULTS, FINISHED
    }
}
