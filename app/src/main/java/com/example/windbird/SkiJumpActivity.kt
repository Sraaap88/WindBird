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

class SkiJumpActivity : Activity(), SensorEventListener {

    private lateinit var gameView: SkiJumpView
    private lateinit var statusText: TextView

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null

    // Variables de gameplay TRÈS LENT et VISUEL
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Phases avec durées AJUSTÉES
    private val preparationDuration = 6f
    private val approachDuration = 13f
    private val takeoffDuration = 8f
    private val flightDuration = 12f
    private val landingDuration = 5f
    private val resultsDuration = 8f
    
    // Variables de jeu
    private var speed = 0f
    private var maxSpeed = 80f
    private var takeoffPower = 0f
    private var jumpDistance = 0f
    private var stability = 1f
    private var landingBonus = 0f
    
    // NOUVEAU: Variables pour le système de "coup de fouet"
    private var previousTiltY = 0f
    private var whipPower = 0f
    private var whipSpeed = 0f
    private var takeoffTriggered = false
    
    // Variables pour le vent
    private var windDirection = 0f
    private var windStrength = 0f
    private var windTimer = 0f
    
    // Contrôles gyroscope
    private var tiltX = 0f
    private var tiltY = 0f
    private var tiltZ = 0f
    
    // Score et résultats
    private var finalScore = 0
    private var scoreCalculated = false
    
    // Effets visuels
    private var cameraShake = 0f
    private val particles = mutableListOf<SnowParticle>()

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
            text = "🎿 SAUT À SKI - ${tournamentData.playerNames[currentPlayerIndex]}"
            setTextColor(Color.WHITE)
            textSize = 30f
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(35, 30, 35, 30)
        }

        gameView = SkiJumpView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        
        initializeGame()
    }
    
    private fun initializeGame() {
        gameState = GameState.PREPARATION
        phaseTimer = 0f
        speed = 0f
        takeoffPower = 0f
        jumpDistance = 0f
        stability = 1f
        landingBonus = 0f
        tiltX = 0f
        tiltY = 0f
        tiltZ = 0f
        previousTiltY = 0f
        whipPower = 0f
        whipSpeed = 0f
        takeoffTriggered = false
        finalScore = 0
        scoreCalculated = false
        cameraShake = 0f
        windDirection = 0f
        windStrength = 0f
        speedHoldTimer = 0f
        particles.clear()
        generateSnowParticles()
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

        // Stocker la valeur précédente pour calculer la vitesse
        previousTiltY = tiltY
        
        tiltX = event.values[0]
        tiltY = event.values[1]
        tiltZ = event.values[2]

        // Progression TRÈS lente du jeu
        phaseTimer += 0.025f

        when (gameState) {
            GameState.PREPARATION -> handlePreparation()
            GameState.APPROACH -> handleApproach()
            GameState.TAKEOFF -> handleTakeoff()
            GameState.FLIGHT -> handleFlight()
            GameState.LANDING -> handleLanding()
            GameState.RESULTS -> handleResults()
            GameState.FINISHED -> {}
        }

        updateParticles()
        updateStatus()
        gameView.invalidate()
    }
    
    private fun handlePreparation() {
        if (phaseTimer >= preparationDuration) {
            gameState = GameState.APPROACH
            phaseTimer = 0f
        }
    }
    
    private var speedHoldTimer = 0f
    
    private fun handleApproach() {
        // Incliner vers l'avant (téléphone penché vers soi)
        if (tiltY > 0.1f) {
            speed += 1.0f
        } else if (tiltY < -0.1f) {
            speed -= 0.6f
        }
        
        // Pénalité pour mouvement latéral
        if (abs(tiltX) > 0.6f) {
            speed -= 0.2f
        }
        
        speed = speed.coerceIn(0f, maxSpeed)
        
        // Logic pour maintenir 80 km/h pendant 1 seconde
        if (speed >= maxSpeed) {
            speedHoldTimer += 0.025f
            if (speedHoldTimer >= 1f) {
                gameState = GameState.TAKEOFF
                phaseTimer = 0f
                cameraShake = 0.5f
                speedHoldTimer = 0f
            }
        } else {
            speedHoldTimer = 0f
        }
        
        // Fallback: si ça prend trop de temps
        if (phaseTimer >= approachDuration + 5f) {
            gameState = GameState.TAKEOFF
            phaseTimer = 0f
            cameraShake = 0.5f
        }
    }
    
    private fun handleTakeoff() {
        // NOUVEAU SYSTÈME DE "COUP DE FOUET"
        
        // Phase 1: Accumulation de puissance (pencher vers l'avant)
        if (!takeoffTriggered && tiltY < -0.15f) {
            takeoffPower += 2.0f
            takeoffPower = takeoffPower.coerceIn(0f, 80f) // Max 80% en accumulation
        }
        
        // Phase 2: Détection du "coup de fouet" (ramener rapidement vers soi)
        if (!takeoffTriggered && tiltY > 0.1f && previousTiltY < -0.1f) {
            // Calculer la vitesse du mouvement de retour
            whipSpeed = abs(tiltY - previousTiltY) / 0.025f // Vitesse du changement
            
            if (whipSpeed > 3.0f) { // Seuil pour détecter un "coup de fouet"
                whipPower = minOf(100f, takeoffPower + (whipSpeed * 5f)) // Bonus selon la vitesse
                takeoffTriggered = true
                
                // Calculer la distance avec le nouveau système
                calculateJumpDistance()
                
                // Transition immédiate vers le vol
                gameState = GameState.FLIGHT
                phaseTimer = 0f
                generateMoreSnowParticles()
                generateWind()
            }
        }
        
        // Fallback temporel (si pas de coup de fouet après 8 secondes)
        if (phaseTimer >= takeoffDuration) {
            whipPower = takeoffPower // Pas de bonus
            calculateJumpDistance()
            gameState = GameState.FLIGHT
            phaseTimer = 0f
            generateMoreSnowParticles()
            generateWind()
        }
    }
    
    // NOUVEAU: Calcul de distance amélioré
    private fun calculateJumpDistance() {
        // Distance de base (vitesse = 70% de l'influence)
        val baseDistance = speed * 2.0f
        
        // Bonus de hauteur du coup de fouet (20% de l'influence)
        val heightBonus = whipPower * 0.4f
        
        jumpDistance = baseDistance + heightBonus
    }
    
    private fun handleFlight() {
        // Gestion du vent - change toutes les 2 secondes
        windTimer += 0.025f
        if (windTimer > 2f) {
            generateWind()
            windTimer = 0f
        }
        
        // Calculer la position idéale pour compenser le vent
        val idealTiltX = -windDirection * windStrength * 0.5f
        
        // Stabilité critique - compenser le vent
        val tiltXError = abs(tiltX - idealTiltX)
        val tiltYError = abs(tiltY)
        val tiltZError = abs(tiltZ)
        
        val currentStability = 1f - (tiltXError + tiltYError + tiltZError) / 3f
        stability = (stability * 0.9f + currentStability.coerceIn(0f, 1f) * 0.1f)
        
        // La stabilité affecte l'efficacité du vol (conservation de la distance)
        val stabilityEffect = 0.7f + stability * 0.3f // Entre 70% et 100% d'efficacité
        jumpDistance *= stabilityEffect.coerceIn(0.7f, 1.0f)
        
        if (phaseTimer >= flightDuration) {
            gameState = GameState.LANDING
            phaseTimer = 0f
            cameraShake = 1f
        }
    }
    
    private fun handleLanding() {
        // Atterrissage - pencher légèrement vers l'avant pour un bon atterrissage
        if (tiltY > 0.1f && tiltY < 0.5f && abs(tiltX) < 0.3f) {
            landingBonus += 1.0f
        } else {
            landingBonus -= 0.5f
        }
        
        landingBonus = landingBonus.coerceIn(0f, 30f)
        
        if (phaseTimer >= landingDuration) {
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
    
    // NOUVEAU: Calcul de score rééquilibré
    private fun calculateFinalScore() {
        if (!scoreCalculated) {
            val speedBonus = (speed * 1.5f).toInt() // AUGMENTÉ - vitesse plus importante
            val distanceBonus = (jumpDistance * 1.0f).toInt() // RÉDUIT - moins dominant
            val whipBonus = (whipPower * 0.8f).toInt() // NOUVEAU - bonus technique
            val stabilityBonus = (stability * 20).toInt() // RÉDUIT
            val landingScore = when {
                landingBonus > 15f -> 20 // Bon atterrissage
                landingBonus > 5f -> 10 // Atterrissage moyen
                else -> -5 // Mauvais atterrissage
            }
            
            finalScore = maxOf(50, speedBonus + distanceBonus + whipBonus + stabilityBonus + landingScore)
            scoreCalculated = true
        }
    }
    
    private fun generateSnowParticles() {
        repeat(20) {
            particles.add(SnowParticle(
                x = kotlin.random.Random.nextFloat() * 1000f,
                y = kotlin.random.Random.nextFloat() * 800f,
                speed = 1f + kotlin.random.Random.nextFloat() * 2f,
                size = 2f + kotlin.random.Random.nextFloat() * 3f
            ))
        }
    }
    
    private fun generateWind() {
        windDirection = (kotlin.random.Random.nextFloat() - 0.5f) * 2f
        windStrength = 0.3f + kotlin.random.Random.nextFloat() * 0.7f
    }
    
    private fun generateMoreSnowParticles() {
        repeat(30) {
            particles.add(SnowParticle(
                x = kotlin.random.Random.nextFloat() * 1000f,
                y = -20f,
                speed = 3f + kotlin.random.Random.nextFloat() * 5f,
                size = 3f + kotlin.random.Random.nextFloat() * 4f
            ))
        }
    }
    
    private fun updateParticles() {
        particles.removeAll { particle ->
            particle.y += particle.speed
            particle.x += sin(particle.y * 0.01f) * 0.5f
            particle.y > 1000f
        }
        
        if (particles.size < 15) {
            generateSnowParticles()
        }
        
        cameraShake = maxOf(0f, cameraShake - 0.015f)
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
                val aiScore = (80..180).random()
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
            GameState.PREPARATION -> "🎿 ${tournamentData.playerNames[currentPlayerIndex]} | Préparation... ${(preparationDuration - phaseTimer).toInt() + 1}s"
            GameState.APPROACH -> {
                if (speed >= maxSpeed) {
                    "⛷️ ${tournamentData.playerNames[currentPlayerIndex]} | 80 KM/H ATTEINT! Maintenez ${(1f - speedHoldTimer).toInt() + 1}s"
                } else {
                    "⛷️ ${tournamentData.playerNames[currentPlayerIndex]} | Élan: ${speed.toInt()} km/h | Atteignez 80 km/h!"
                }
            }
            GameState.TAKEOFF -> {
                if (takeoffTriggered) {
                    "🚀 ${tournamentData.playerNames[currentPlayerIndex]} | COUP DE FOUET! Puissance: ${whipPower.toInt()}% (Vitesse: ${whipSpeed.toInt()})"
                } else {
                    "🚀 ${tournamentData.playerNames[currentPlayerIndex]} | Accumulation: ${takeoffPower.toInt()}% | COUP DE FOUET vers vous!"
                }
            }
            GameState.FLIGHT -> "✈️ ${tournamentData.playerNames[currentPlayerIndex]} | Vol: ${jumpDistance.toInt()}m | Stabilité: ${(stability * 100).toInt()}% | ${(flightDuration - phaseTimer).toInt() + 1}s"
            GameState.LANDING -> "🎯 ${tournamentData.playerNames[currentPlayerIndex]} | Atterrissage! Distance: ${jumpDistance.toInt()}m | ${(landingDuration - phaseTimer).toInt() + 1}s"
            GameState.RESULTS -> "🏆 ${tournamentData.playerNames[currentPlayerIndex]} | Distance finale: ${jumpDistance.toInt()}m | Score: ${finalScore}"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Saut terminé!"
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

    inner class SkiJumpView(context: Context) : View(context) {
        private val paint = Paint()
        
        private var skierBitmap: Bitmap? = null
        private var skierJumpBitmap: Bitmap? = null
        private var skierFlightBitmap: Bitmap? = null
        private var skierLand1Bitmap: Bitmap? = null
        private var skierLand2Bitmap: Bitmap? = null
        private var skierLand3Bitmap: Bitmap? = null
        
        init {
            try {
                skierBitmap = BitmapFactory.decodeResource(resources, R.drawable.skier_approach)
                skierJumpBitmap = BitmapFactory.decodeResource(resources, R.drawable.skier_jump)
                skierFlightBitmap = BitmapFactory.decodeResource(resources, R.drawable.skier_flight)
                skierLand1Bitmap = BitmapFactory.decodeResource(resources, R.drawable.skier_land1)
                skierLand2Bitmap = BitmapFactory.decodeResource(resources, R.drawable.skier_land2)
                skierLand3Bitmap = BitmapFactory.decodeResource(resources, R.drawable.skier_land3)
            } catch (e: Exception) {
                createFallbackSkierBitmaps()
            }
        }
        
        private fun createFallbackSkierBitmaps() {
            skierBitmap = Bitmap.createBitmap(60, 80, Bitmap.Config.ARGB_8888)
            val canvas1 = Canvas(skierBitmap!!)
            val tempPaint = Paint().apply {
                color = Color.parseColor("#FF4444")
                style = Paint.Style.FILL
            }
            
            canvas1.drawRect(20f, 20f, 40f, 60f, tempPaint)
            canvas1.drawCircle(30f, 15f, 10f, tempPaint)
            
            tempPaint.color = Color.YELLOW
            canvas1.drawRect(15f, 55f, 18f, 75f, tempPaint)
            canvas1.drawRect(42f, 55f, 45f, 75f, tempPaint)
            
            skierJumpBitmap = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
            val canvas2 = Canvas(skierJumpBitmap!!)
            tempPaint.color = Color.parseColor("#FF4444")
            canvas2.drawRect(20f, 20f, 80f, 40f, tempPaint)
            canvas2.drawCircle(15f, 30f, 10f, tempPaint)
            
            skierFlightBitmap = Bitmap.createBitmap(120, 50, Bitmap.Config.ARGB_8888)
            val canvas3 = Canvas(skierFlightBitmap!!)
            tempPaint.color = Color.parseColor("#FF4444")
            canvas3.drawRect(30f, 15f, 90f, 35f, tempPaint)
            canvas3.drawCircle(25f, 25f, 10f, tempPaint)
            
            skierLand1Bitmap = Bitmap.createBitmap(80, 70, Bitmap.Config.ARGB_8888)
            val canvas4 = Canvas(skierLand1Bitmap!!)
            tempPaint.color = Color.parseColor("#FF4444")
            canvas4.drawRect(20f, 30f, 60f, 50f, tempPaint)
            canvas4.drawCircle(15f, 35f, 10f, tempPaint)
            
            skierLand2Bitmap = Bitmap.createBitmap(90, 80, Bitmap.Config.ARGB_8888)
            val canvas5 = Canvas(skierLand2Bitmap!!)
            canvas5.drawRect(25f, 40f, 65f, 70f, tempPaint)
            canvas5.drawCircle(45f, 30f, 10f, tempPaint)
            
            skierLand3Bitmap = Bitmap.createBitmap(70, 90, Bitmap.Config.ARGB_8888)
            val canvas6 = Canvas(skierLand3Bitmap!!)
            canvas6.drawRect(25f, 30f, 45f, 70f, tempPaint)
            canvas6.drawCircle(35f, 20f, 10f, tempPaint)
            canvas6.drawCircle(15f, 25f, 5f, tempPaint)
            canvas6.drawCircle(55f, 25f, 5f, tempPaint)
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
                GameState.APPROACH -> drawApproach(canvas, w, h)
                GameState.TAKEOFF -> drawTakeoff(canvas, w, h)
                GameState.FLIGHT -> drawFlight(canvas, w, h)
                GameState.LANDING -> drawLanding(canvas, w, h)
                GameState.RESULTS -> drawResults(canvas, w, h)
                GameState.FINISHED -> drawResults(canvas, w, h)
            }
            
            drawSnowParticles(canvas, w, h)
            
            if (cameraShake > 0f) {
                canvas.restore()
            }
        }
        
        private fun drawPreparation(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#87CEEB")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            paint.color = Color.parseColor("#DDDDDD")
            val path = Path()
            path.moveTo(0f, h * 0.4f)
            path.lineTo(w * 0.3f, h * 0.2f)
            path.lineTo(w * 0.7f, h * 0.3f)
            path.lineTo(w.toFloat(), h * 0.1f)
            path.lineTo(w.toFloat(), h.toFloat())
            path.lineTo(0f, h.toFloat())
            path.close()
            canvas.drawPath(path, paint)
            
            drawTrees(canvas, w, h)
            drawCrowd(canvas, w, h)
            
            val playerCountry = tournamentData.playerCountries[currentPlayerIndex]
            val flag = getCountryFlag(playerCountry)
            
            paint.color = Color.WHITE
            paint.textSize = 180f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(flag, w/2f, h * 0.18f, paint)
            
            paint.textSize = 48f
            canvas.drawText(playerCountry.uppercase(), w/2f, h * 0.25f, paint)
            
            paint.textSize = 56f
            canvas.drawText("🎿 SAUT À SKI 🎿", w/2f, h * 0.35f, paint)
            
            paint.textSize = 40f
            canvas.drawText("Préparez-vous...", w/2f, h * 0.42f, paint)
            
            paint.textSize = 36f
            paint.color = Color.YELLOW
            canvas.drawText("Dans ${(preparationDuration - phaseTimer).toInt() + 1} secondes", w/2f, h * 0.5f, paint)
            
            paint.textSize = 40f
            paint.color = Color.CYAN
            canvas.drawText("📱 Penchez vers VOUS pour accélérer", w/2f, h * 0.7f, paint)
            canvas.drawText("📱 Penchez vers l'AVANT puis COUP DE FOUET", w/2f, h * 0.75f, paint)
            canvas.drawText("📱 Compensez le vent en vol", w/2f, h * 0.8f, paint)
        }
        
        private fun drawTrees(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#228B22")
            
            for (i in 1..3) {
                val treeX = w * 0.1f
                val treeY = h * (0.4f + i * 0.15f)
                drawTree(canvas, treeX, treeY, 60f)
            }
            
            for (i in 1..3) {
                val treeX = w * 0.9f
                val treeY = h * (0.4f + i * 0.15f)
                drawTree(canvas, treeX, treeY, 60f)
            }
        }
        
        private fun drawTree(canvas: Canvas, x: Float, y: Float, size: Float) {
            paint.color = Color.parseColor("#8B4513")
            canvas.drawRect(x - size/4, y, x + size/4, y + size/2, paint)
            
            paint.color = Color.parseColor("#228B22")
            val path = Path()
            path.moveTo(x, y - size/2)
            path.lineTo(x - size/1.5f, y)
            path.lineTo(x + size/1.5f, y)
            path.close()
            canvas.drawPath(path, paint)
        }
        
        private fun drawCrowd(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#444444")
            
            for (i in 1..15) {
                val crowdX = w * 0.15f + i * (w * 0.7f / 15f)
                val crowdY = h * 0.9f
                
                canvas.drawCircle(crowdX, crowdY - 30f, 15f, paint)
                canvas.drawRect(crowdX - 12f, crowdY - 15f, crowdX + 12f, crowdY, paint)
                
                if (i % 2 == 0) {
                    canvas.drawCircle(crowdX - 20f, crowdY - 40f, 8f, paint)
                    canvas.drawCircle(crowdX + 20f, crowdY - 40f, 8f, paint)
                }
            }
        }
        
        private fun drawApproach(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#87CEEB")
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            paint.color = Color.WHITE
            val jumpPath = Path()
            jumpPath.moveTo(w * 0.2f, h * 0.95f)
            jumpPath.lineTo(w * 0.8f, h * 0.95f)
            jumpPath.lineTo(w * 0.45f, h * 0.05f)
            jumpPath.lineTo(w * 0.55f, h * 0.05f)
            jumpPath.close()
            canvas.drawPath(jumpPath, paint)
            
            paint.color = Color.parseColor("#CCCCCC")
            paint.strokeWidth = 4f
            paint.style = Paint.Style.STROKE
            for (i in 1..12) {
                val progress = i / 12f
                val lineY = h * (0.95f - progress * 0.9f)
                val leftX = w * (0.2f + progress * 0.25f)
                val rightX = w * (0.8f - progress * 0.25f)
                canvas.drawLine(leftX, lineY, rightX, lineY, paint)
            }
            
            paint.style = Paint.Style.FILL
            
            val speedProgress = if (maxSpeed > 0) speed / maxSpeed else 0f
            val timeProgress = phaseTimer / approachDuration
            
            val combinedProgress = (speedProgress * 0.8f + timeProgress * 0.2f).coerceIn(0f, 1f)
            
            val skierY = h * (0.9f - combinedProgress * 0.85f)
            val skierX = w / 2f
            
            val scale = 0.08f + combinedProgress * 0.02f
            
            skierBitmap?.let { bmp ->
                val dstRect = RectF(
                    skierX - bmp.width * scale / 2f,
                    skierY - bmp.height * scale / 2f,
                    skierX + bmp.width * scale / 2f,
                    skierY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            drawSpeedMeter(canvas, w, h)
            
            paint.color = Color.WHITE
            paint.textSize = 60f
            paint.textAlign = Paint.Align.CENTER
            
            if (speed >= maxSpeed) {
                paint.color = Color.GREEN
                canvas.drawText("✅ MAINTENEZ 80 KM/H!", w/2f, 80f, paint)
                paint.textSize = 45f
                paint.color = Color.YELLOW
                canvas.drawText("Encore ${(1f - speedHoldTimer).toInt() + 1} seconde", w/2f, 140f, paint)
            } else {
                canvas.drawText("📱 PENCHEZ VERS VOUS", w/2f, 80f, paint)
                paint.textSize = 45f
                paint.color = Color.CYAN
                canvas.drawText("Atteignez 80 km/h et maintenez!", w/2f, 140f, paint)
            }
        }
        
        private fun drawTakeoff(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#87CEEB")
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            paint.color = Color.WHITE
            val rampPath = Path()
            rampPath.moveTo(0f, h * 0.9f)
            rampPath.quadTo(w * 0.6f, h * 0.7f, w * 0.8f, h * 0.5f)
            rampPath.lineTo(w * 0.85f, h * 0.52f)
            rampPath.lineTo(w * 0.85f, h.toFloat())
            rampPath.lineTo(0f, h.toFloat())
            rampPath.close()
            canvas.drawPath(rampPath, paint)
            
            if (!takeoffTriggered) {
                // Phase d'accumulation
                val approachProgress = phaseTimer / 3f
                val skierX = w * (0.2f + approachProgress * 0.6f)
                val skierY = h * (0.9f - approachProgress * 0.4f)
                
                val scale = 0.3f
                
                skierJumpBitmap?.let { bmp ->
                    val dstRect = RectF(
                        skierX - bmp.width * scale / 2f,
                        skierY - bmp.height * scale / 2f,
                        skierX + bmp.width * scale / 2f,
                        skierY + bmp.height * scale / 2f
                    )
                    canvas.drawBitmap(bmp, null, dstRect, paint)
                }
                
                paint.color = Color.YELLOW
                paint.textSize = 60f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("🚀 PENCHEZ VERS L'AVANT! 🚀", w/2f, h * 0.15f, paint)
                
                paint.color = Color.WHITE
                paint.textSize = 45f
                canvas.drawText("Puissance: ${takeoffPower.toInt()}%", w/2f, h * 0.25f, paint)
                
                paint.textSize = 35f
                paint.color = Color.CYAN
                canvas.drawText("Puis COUP DE FOUET rapide vers vous!", w/2f, h * 0.32f, paint)
                
            } else {
                // Phase de saut déclenchée
                val skierX = w * 0.8f + (phaseTimer * 50f)
                val skierY = h * (0.5f - phaseTimer * 30f)
                
                canvas.save()
                canvas.translate(skierX, skierY)
                canvas.rotate((whipPower / 100f) * 20f - 10f)
                
                val scale = 0.4f + (whipSpeed / 20f) * 0.2f // Taille selon la puissance du fouet
                
                skierJumpBitmap?.let { bmp ->
                    val dstRect = RectF(
                        -bmp.width * scale / 2f,
                        -bmp.height * scale / 2f,
                        bmp.width * scale / 2f,
                        bmp.height * scale / 2f
                    )
                    canvas.drawBitmap(bmp, null, dstRect, paint)
                }
                
                canvas.restore()
                
                // Trail d'effet selon la puissance
                paint.color = Color.WHITE
                paint.alpha = 150
                for (i in 1..(whipSpeed.toInt() / 2 + 3)) {
                    val trailX = skierX - i * 25f
                    val trailY = skierY + i * 5f
                    canvas.drawCircle(trailX, trailY, 8f, paint)
                }
                paint.alpha = 255
                
                paint.color = Color.GREEN
                paint.textSize = 70f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("🛫 COUP DE FOUET! 🛫", w/2f, h * 0.15f, paint)
                
                paint.textSize = 50f
                paint.color = Color.YELLOW
                canvas.drawText("Puissance: ${whipPower.toInt()}%", w/2f, h * 0.25f, paint)
            }
            
            drawTakeoffPowerMeter(canvas, w, h)
        }
        
        private fun drawFlight(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#87CEEB")
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            paint.color = Color.parseColor("#DDDDDD")
            val mountainPath = Path()
            val scrollOffset = (phaseTimer * 25f) % 200f
            mountainPath.moveTo(-scrollOffset, h * 0.7f)
            mountainPath.lineTo(w * 0.2f - scrollOffset, h * 0.4f)
            mountainPath.lineTo(w * 0.5f - scrollOffset, h * 0.6f)
            mountainPath.lineTo(w * 0.8f - scrollOffset, h * 0.3f)
            mountainPath.lineTo(w + 100f - scrollOffset, h * 0.5f)
            mountainPath.lineTo(w + 100f, h.toFloat())
            mountainPath.lineTo(-100f, h.toFloat())
            mountainPath.close()
            canvas.drawPath(mountainPath, paint)
            
            val flightProgress = phaseTimer / flightDuration
            val skierX = w * (-0.1f + flightProgress * 1.2f)
            val baseY = h * 0.4f
            
            val verticalOffset = tiltY * 100f
            val skierY = baseY + verticalOffset
            
            canvas.save()
            canvas.translate(skierX, skierY)
            
            val skierRotation = tiltY * 20f
            canvas.rotate(skierRotation)
            
            val windRotation = windDirection * windStrength * 10f
            canvas.rotate(windRotation)
            
            val scale = 0.4f
            
            skierFlightBitmap?.let { bmp ->
                val dstRect = RectF(
                    -bmp.width * scale / 2f,
                    -bmp.height * scale / 2f,
                    bmp.width * scale / 2f,
                    bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            canvas.restore()
            
            paint.color = Color.WHITE
            paint.alpha = 100
            for (i in 1..3) {
                val trailX = skierX - i * 30f
                val trailY = skierY + kotlin.random.Random.nextFloat() * 20f - 10f
                canvas.drawCircle(trailX, trailY, 6f, paint)
            }
            paint.alpha = 255
            
            drawWindIndicator(canvas, w, h)
            drawStabilityIndicators(canvas, w, h)
            
            paint.color = Color.WHITE
            paint.textSize = 55f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("⚖️ COMPENSEZ LE VENT! ⚖️", w/2f, 70f, paint)
            
            paint.textSize = 40f
            paint.color = Color.CYAN
            canvas.drawText("📱 Avant/Arrière = Angle de vol", w/2f, h - 60f, paint)
        }
        
        private fun drawLanding(canvas: Canvas, w: Int, h: Int) {
            // Vue de côté - skieur va vers la droite
            paint.color = Color.parseColor("#87CEEB")
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste d'atterrissage horizontale (vue de côté)
            paint.color = Color.WHITE
            canvas.drawRect(0f, h * 0.8f, w.toFloat(), h.toFloat(), paint)
            
            // Marques de distance sur la piste
            paint.color = Color.parseColor("#666666")
            paint.textSize = 24f
            paint.textAlign = Paint.Align.CENTER
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            for (i in 1..5) {
                val markX = w * 0.1f + i * (w * 0.8f / 5f)
                val markY = h * 0.8f
                canvas.drawLine(markX, markY, markX, markY + 20f, paint)
                canvas.drawText("${i * 25}m", markX, markY + 40f, paint)
            }
            paint.style = Paint.Style.FILL
            
            val landingProgress = phaseTimer / landingDuration
            
            // Position du skieur selon la phase d'atterrissage
            val skierX: Float
            val skierY: Float
            val currentBitmap: Bitmap?
            val scale: Float
            
            when {
                landingProgress < 0.3f -> {
                    // Phase 1: Descente vers la piste (0-1.5s)
                    val descentProgress = landingProgress / 0.3f
                    skierX = w * (0.2f + descentProgress * 0.3f) // Arrive en diagonale
                    skierY = h * (0.3f + descentProgress * 0.45f) // Descend vers la piste
                    currentBitmap = skierLand1Bitmap
                    scale = 0.8f
                    
                    // Trail de particules pendant la descente
                    paint.color = Color.WHITE
                    paint.alpha = 120
                    for (i in 1..4) {
                        val trailX = skierX - i * 20f
                        val trailY = skierY - i * 10f
                        canvas.drawCircle(trailX, trailY, 8f, paint)
                    }
                    paint.alpha = 255
                }
                landingProgress < 0.6f -> {
                    // Phase 2: Impact et explosion de neige (1.5-3s)
                    val impactProgress = (landingProgress - 0.3f) / 0.3f
                    skierX = w * (0.5f + impactProgress * 0.1f) // Glisse un peu
                    skierY = h * 0.75f // Sur la piste
                    currentBitmap = skierLand2Bitmap
                    scale = 1.0f
                    
                    // Explosion de neige à l'impact
                    paint.color = Color.WHITE
                    paint.alpha = 180
                    for (i in 1..12) {
                        val angle = i * 30f
                        val particleX = skierX + cos(Math.toRadians(angle.toDouble())).toFloat() * 60f
                        val particleY = skierY + sin(Math.toRadians(angle.toDouble())).toFloat() * 30f
                        canvas.drawCircle(particleX, particleY, 12f, paint)
                    }
                    paint.alpha = 255
                }
                else -> {
                    // Phase 3: Se relève et salue (3-5s)
                    val standingProgress = (landingProgress - 0.6f) / 0.4f
                    skierX = w * (0.6f + standingProgress * 0.1f) // Continue à glisser doucement
                    skierY = h * 0.75f // Stable sur la piste
                    currentBitmap = skierLand3Bitmap
                    scale = 1.1f // Légèrement plus grand pour montrer la fierté
                }
            }
            
            // Dessiner le skieur
            currentBitmap?.let { bmp ->
                val dstRect = RectF(
                    skierX - bmp.width * scale / 2f,
                    skierY - bmp.height * scale / 2f,
                    skierX + bmp.width * scale / 2f,
                    skierY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            // Affichage de la distance finale
            paint.color = Color.YELLOW
            paint.textSize = 80f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${jumpDistance.toInt()}m", w/2f, h * 0.15f, paint)
            
            // Instructions selon la phase
            paint.color = Color.WHITE
            paint.textSize = 40f
            
            val instruction = when {
                landingProgress < 0.3f -> "✈️ DESCEND VERS LA PISTE"
                landingProgress < 0.6f -> "💥 ATTERRISSAGE!"
                else -> "🎉 SE RELÈVE ET SALUE!"
            }
            canvas.drawText(instruction, w/2f, h * 0.25f, paint)
            
            // Bonus atterrissage
            paint.textSize = 30f
            paint.color = if (landingBonus > 15f) Color.GREEN else if (landingBonus > 5f) Color.YELLOW else Color.RED
            canvas.drawText("Bonus atterrissage: +${landingBonus.toInt()}", w/2f, h * 0.32f, paint)
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#87CEEB")
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
            
            paint.color = Color.BLACK
            paint.textSize = 80f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore}", w/2f, h * 0.2f, paint)
            
            paint.textSize = 40f
            canvas.drawText("POINTS", w/2f, h * 0.3f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 32f
            canvas.drawText("🎿 Distance: ${jumpDistance.toInt()}m", w/2f, h * 0.5f, paint)
            canvas.drawText("⚡ Vitesse: ${speed.toInt()} km/h", w/2f, h * 0.55f, paint)
            canvas.drawText("🚀 Coup de fouet: ${whipPower.toInt()}%", w/2f, h * 0.6f, paint)
            canvas.drawText("⚖️ Stabilité: ${(stability * 100).toInt()}%", w/2f, h * 0.65f, paint)
            canvas.drawText("🎯 Atterrissage: ${landingBonus.toInt()} bonus", w/2f, h * 0.7f, paint)
            
            paint.color = Color.YELLOW
            for (i in 1..10) {
                val starX = kotlin.random.Random.nextFloat() * w
                val starY = kotlin.random.Random.nextFloat() * h * 0.4f
                drawStar(canvas, starX, starY, 12f)
            }
        }
        
        private fun drawSpeedMeter(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#333333")
            paint.style = Paint.Style.FILL
            canvas.drawRect(w - 110f, 140f, w - 30f, h - 140f, paint)
            
            paint.color = Color.GREEN
            val speedHeight = (speed / maxSpeed) * (h - 280f)
            canvas.drawRect(w - 105f, h - 140f - speedHeight, w - 35f, h - 140f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 28f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("VITESSE", w - 70f, 120f, paint)
            canvas.drawText("${speed.toInt()}", w - 70f, h - 90f, paint)
            canvas.drawText("km/h", w - 70f, h - 60f, paint)
        }
        
        private fun drawTakeoffPowerMeter(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#333333")
            paint.style = Paint.Style.FILL
            canvas.drawRect(140f, h - 120f, w - 140f, h - 30f, paint)
            
            val displayPower = if (takeoffTriggered) whipPower else takeoffPower
            paint.color = if (displayPower > 70f) Color.GREEN else if (displayPower > 40f) Color.YELLOW else Color.RED
            val powerWidth = (displayPower / 100f) * (w - 280f)
            canvas.drawRect(140f, h - 115f, 140f + powerWidth, h - 35f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 28f
            paint.textAlign = Paint.Align.CENTER
            val powerText = if (takeoffTriggered) "COUP DE FOUET: ${whipPower.toInt()}%" else "PUISSANCE: ${takeoffPower.toInt()}%"
            canvas.drawText(powerText, w/2f, h - 130f, paint)
        }
        
        private fun drawWindIndicator(canvas: Canvas, w: Int, h: Int) {
            val windX = w - 150f
            val windY = 150f
            
            paint.color = Color.parseColor("#333333")
            paint.style = Paint.Style.FILL
            canvas.drawRect(windX - 80f, windY - 60f, windX + 80f, windY + 60f, paint)
            
            paint.color = Color.YELLOW
            paint.textSize = 48f
            paint.textAlign = Paint.Align.CENTER
            
            val windText = if (windDirection < -0.1f) "⬅️" else if (windDirection > 0.1f) "➡️" else "⏸️"
            canvas.drawText(windText, windX, windY - 10f, paint)
            
            paint.textSize = 24f
            paint.color = Color.WHITE
            canvas.drawText("VENT", windX, windY - 35f, paint)
            canvas.drawText("${(windStrength * 100).toInt()}%", windX, windY + 25f, paint)
            
            paint.textSize = 20f
            paint.color = Color.CYAN
            val instruction = when {
                windDirection < -0.1f -> "Penchez à DROITE"
                windDirection > 0.1f -> "Penchez à GAUCHE"
                else -> "Restez stable"
            }
            canvas.drawText(instruction, windX, windY + 50f, paint)
        }
        
        private fun drawStabilityIndicators(canvas: Canvas, w: Int, h: Int) {
            val baseY = h - 220f
            
            val idealTiltX = -windDirection * windStrength * 0.5f
            val tiltXError = abs(tiltX - idealTiltX)
            
            paint.color = Color.parseColor("#333333")
            paint.style = Paint.Style.FILL
            canvas.drawRect(80f, baseY, 340f, baseY + 60f, paint)
            
            paint.color = if (stability > 0.8f) Color.GREEN else if (stability > 0.5f) Color.YELLOW else Color.RED
            canvas.drawRect(80f, baseY, 80f + stability * 260f, baseY + 60f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 30f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("STABILITÉ: ${(stability * 100).toInt()}%", 80f, baseY - 20f, paint)
            
            paint.textSize = 24f
            canvas.drawText("Compensation vent: ${if (tiltXError < 0.3f) "✅" else "❌"}", 80f, baseY + 90f, paint)
            canvas.drawText("Avant/Arrière: ${if (abs(tiltY) < 0.3f) "✅" else "❌"}", 80f, baseY + 120f, paint)
            canvas.drawText("Rotation: ${if (abs(tiltZ) < 0.3f) "✅" else "❌"}", 80f, baseY + 150f, paint)
        }
        
        private fun drawSnowParticles(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.WHITE
            paint.alpha = 180
            paint.style = Paint.Style.FILL
            for (particle in particles) {
                canvas.drawCircle(particle.x, particle.y, particle.size, paint)
            }
            paint.alpha = 255
        }
        
        private fun drawStar(canvas: Canvas, x: Float, y: Float, size: Float) {
            val path = Path()
            for (i in 0..4) {
                val angle = i * 72f - 90f
                val outerX = x + cos(Math.toRadians(angle.toDouble())).toFloat() * size
                val outerY = y + sin(Math.toRadians(angle.toDouble())).toFloat() * size
                
                if (i == 0) path.moveTo(outerX, outerY) else path.lineTo(outerX, outerY)
                
                val innerAngle = angle + 36f
                val innerX = x + cos(Math.toRadians(innerAngle.toDouble())).toFloat() * size * 0.4f
                val innerY = y + sin(Math.toRadians(innerAngle.toDouble())).toFloat() * size * 0.4f
                path.lineTo(innerX, innerY)
            }
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    data class SnowParticle(
        var x: Float,
        var y: Float,
        val speed: Float,
        val size: Float
    )

    enum class GameState {
        PREPARATION, APPROACH, TAKEOFF, FLIGHT, LANDING, RESULTS, FINISHED
    }
}
