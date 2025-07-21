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

class BobsledActivity : Activity(), SensorEventListener {

    private lateinit var gameView: BobsledView
    private lateinit var statusText: TextView

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null

    // Nouvelle structure de jeu SIMPLIFIÉE
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Durées ajustées - ON SUPPRIME LES 2 DESCENTES MOCHES
    private val preparationDuration = 6f
    private val pushStartDuration = 8f
    private val controlDescentDuration = 25f   // Plus long, on accélère progressivement
    private val finishLineDuration = 4f        // Belle traversée de ligne
    private val celebrationDuration = 8f       // Belle célébration
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
    private val speedLines = mutableListOf<SpeedLine>()
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
        speedLines.clear()
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
            GameState.CONTROL_DESCENT -> handleControlDescent()  // DIRECT après poussée !
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
        // Plus de secouage ! Maintenant on pousse avec le tactile
        // Le bobsleigh démarre à 1 pouce du bord gauche
        
        // Simulation de poussée tactile basée sur le mouvement du gyroscope
        val pushForce = abs(tiltX) + abs(tiltY)
        if (pushForce > 0.5f) {
            pushPower += pushForce * 2f
            pushCount++
            
            // Calcul d'efficacité selon la régularité
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastPushTime > 200 && currentTime - lastPushTime < 800) {
                pushRhythm += 3f  // Bon rythme
            } else {
                pushRhythm += 1f  // Rythme moyen
            }
            lastPushTime = currentTime
        }
        
        pushPower = pushPower.coerceAtMost(100f)
        pushRhythm = pushRhythm.coerceAtMost(100f)
        
        if (phaseTimer >= pushStartDuration) {
            pushQuality = (pushPower * 0.6f + pushRhythm * 0.4f) / 100f
            speed = 45f + (pushQuality * 35f)  // Commence plus vite (45-80 km/h)
            
            // ON PASSE DIRECT AU CONTRÔLE !
            gameState = GameState.CONTROL_DESCENT
            phaseTimer = 0f
            cameraShake = 0.5f
            generateNewTurn()
        }
    }
    
    private fun handleFirstDescent() {
        // Première descente - vitesse modérée
        if (phaseTimer >= firstDescentDuration) {
            speed += 15f  // Accélération pour la deuxième descente
            gameState = GameState.SECOND_DESCENT
            phaseTimer = 0f
        }
    }
    
    private fun handleSecondDescent() {
        // Deuxième descente - plus rapide
        if (phaseTimer >= secondDescentDuration) {
            speed += 20f  // Encore plus de vitesse pour le contrôle
            speed = speed.coerceAtMost(maxSpeed)
            gameState = GameState.CONTROL_DESCENT
            phaseTimer = 0f
            generateNewTurn()
        }
    }
    
    private fun handleControlDescent() {
        // Phase de contrôle avec gyroscope - ACCÉLÉRATION PROGRESSIVE
        updateControlPhase()
        
        // ACCÉLÉRATION continue pendant la descente (45-80 -> 150+ km/h)
        val accelerationBonus = steeringAccuracy * 0.8f  // Bonne conduite = plus de vitesse
        speed += (0.3f + accelerationBonus)  // Accélération de base + bonus
        maxSpeed = 180f  // On peut aller jusqu'à 180 km/h !
        speed = speed.coerceAtMost(maxSpeed)
        
        // Progression sur la piste (pour savoir quand finir)
        trackProgress += 0.025f / controlDescentDuration
        trackProgress = trackProgress.coerceAtMost(1f)
        
        if (phaseTimer >= controlDescentDuration) {
            gameState = GameState.FINISH_LINE
            phaseTimer = 0f
            cameraShake = 0.8f
        }
    }
    
    private fun handleFinishLine() {
        // Passage de la ligne d'arrivée
        if (phaseTimer >= finishLineDuration) {
            gameState = GameState.CELEBRATION
            phaseTimer = 0f
            speed *= 0.8f  // Ralentissement pour la célébration
        }
    }
    
    private fun handleCelebration() {
        // Ralentissement progressif
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
        // Génération de direction idéale qui change
        turnTimer += 0.025f
        if (turnTimer > 2.5f) {
            idealDirection = -1f + kotlin.random.Random.nextFloat() * 2f
            turnTimer = 0f
        }
        
        // Calcul de précision du steering
        val directionError = abs(tiltZ - idealDirection)
        steeringAccuracy = 1f - (directionError / 2f).coerceIn(0f, 1f)
        
        // Accumulation de performance
        controlPerformance = (controlPerformance * 0.98f + steeringAccuracy * 0.02f)
        
        // Ajustement de vitesse selon performance
        if (steeringAccuracy < 0.5f) {
            speed = maxOf(speed * 0.995f, 40f)  // Ralentissement si mauvais contrôle
        }
    }
    
    private fun generateNewTurn() {
        currentTurn = -1f + kotlin.random.Random.nextFloat() * 2f
        turnIntensity = 0.4f + kotlin.random.Random.nextFloat() * 0.6f
    }
    
    private fun generateSpeedLines() {
        speedLines.add(SpeedLine(
            x = kotlin.random.Random.nextFloat() * 1000f,
            y = kotlin.random.Random.nextFloat() * 800f,
            speed = 8f + kotlin.random.Random.nextFloat() * 4f
        ))
        
        if (speedLines.size > 25) {
            speedLines.removeFirst()
        }
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
        speedLines.removeAll { line ->
            line.x -= line.speed
            line.x < -50f
        }
        
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
            GameState.PUSH_START -> "🚀 ${tournamentData.playerNames[currentPlayerIndex]} | Poussée: ${pushCount} (${pushPower.toInt()}%) | ${(pushStartDuration - phaseTimer).toInt() + 1}s"
            GameState.FIRST_DESCENT -> "🛷 ${tournamentData.playerNames[currentPlayerIndex]} | Première descente: ${speed.toInt()} km/h"
            GameState.SECOND_DESCENT -> "⚡ ${tournamentData.playerNames[currentPlayerIndex]} | Accélération: ${speed.toInt()} km/h"
            GameState.CONTROL_DESCENT -> "🎮 ${tournamentData.playerNames[currentPlayerIndex]} | Contrôle: ${(steeringAccuracy * 100).toInt()}% | ${speed.toInt()} km/h"
            GameState.FINISH_LINE -> "🏁 ${tournamentData.playerNames[currentPlayerIndex]} | Ligne d'arrivée!"
            GameState.CELEBRATION -> "🎉 ${tournamentData.playerNames[currentPlayerIndex]} | Célébration!"
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
            // Créer des images de substitution si les vraies images ne sont pas trouvées
            bobsledPreparationBitmap = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888)
            val canvas1 = Canvas(bobsledPreparationBitmap!!)
            val tempPaint = Paint().apply {
                color = Color.parseColor("#FF4444")
                style = Paint.Style.FILL
            }
            canvas1.drawRoundRect(20f, 40f, 180f, 80f, 15f, 15f, tempPaint)
            
            // Autres images de substitution
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
                GameState.CONTROL_DESCENT -> drawFullScreenTunnel(canvas, w, h)  // PLEIN ÉCRAN !
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
                // Fond de substitution
                paint.color = Color.parseColor("#E0F6FF")
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
            
            // Rectangle blanc pour le drapeau (comme dans l'image de référence)
            paint.color = Color.WHITE
            val flagRect = RectF(50f, 50f, 300f, 200f)
            canvas.drawRect(flagRect, paint)
            
            // Bordure noire du rectangle
            paint.color = Color.BLACK
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawRect(flagRect, paint)
            paint.style = Paint.Style.FILL
            
            // Gros drapeau emoji dans le rectangle blanc
            val playerCountry = tournamentData.playerCountries[currentPlayerIndex]
            val flag = getCountryFlag(playerCountry)
            
            paint.color = Color.BLACK
            paint.textSize = 120f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(flag, flagRect.centerX(), flagRect.centerY() + 40f, paint)
            
            // Nom du pays sous le rectangle
            paint.textSize = 28f
            canvas.drawText(playerCountry.uppercase(), flagRect.centerX(), flagRect.bottom + 40f, paint)
            
            // Instructions au centre
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
            // Fond bleu ciel
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste inclinée pour la poussée
            paint.color = Color.parseColor("#FFFFFF")
            val trackWidth = 200f
            val trackY = h * 0.75f
            canvas.drawRect(0f, trackY - trackWidth/2f, w.toFloat(), trackY + trackWidth/2f, paint)
            
            // Bordures de piste
            paint.color = Color.parseColor("#CCCCCC")
            paint.strokeWidth = 8f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(0f, trackY - trackWidth/2f, w.toFloat(), trackY - trackWidth/2f, paint)
            canvas.drawLine(0f, trackY + trackWidth/2f, w.toFloat(), trackY + trackWidth/2f, paint)
            paint.style = Paint.Style.FILL
            
            // Bobsleigh qui avance selon la poussée - COMMENCE À 1 POUCE DU BORD
            val pushProgress = pushPower / 100f
            val bobX = 50f + pushProgress * (w - 100f)  // De 50px à w-50px
            val bobY = trackY
            val scale = 0.18f
            
            bobPushBitmap?.let { bmp ->
                val dstRect = RectF(
                    bobX - bmp.width * scale / 2f,
                    bobY - bmp.height * scale / 2f,
                    bobX + bmp.width * scale / 2f,
                    bobY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            // Instructions améliorées
            paint.color = Color.RED
            paint.textSize = 50f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🏃‍♂️ BOUGEZ POUR POUSSER! 🏃‍♂️", w/2f, h * 0.15f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 35f
            canvas.drawText("Poussées: ${pushCount}", w/2f, h * 0.25f, paint)
            
            // Efficacité de poussée
            val efficiency = (pushQuality * 100).toInt()
            paint.color = when {
                efficiency > 80 -> Color.GREEN
                efficiency > 60 -> Color.YELLOW
                else -> Color.RED
            }
            paint.textSize = 40f
            canvas.drawText("Efficacité: ${efficiency}%", w/2f, h * 0.32f, paint)
            
            // Barre de puissance améliorée
            drawImprovedPushPowerMeter(canvas, w, h)
        }
        
        private fun drawFirstDescent(canvas: Canvas, w: Int, h: Int) {
            // Fond montagne/ciel
            paint.color = Color.parseColor("#87CEEB")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // PISTE INCLINÉE haut-gauche vers bas-droite
            val progress = phaseTimer / firstDescentDuration
            
            // Points de la piste inclinée
            val trackStartX = -100f
            val trackStartY = h * 0.1f
            val trackEndX = w + 100f
            val trackEndY = h * 0.9f
            val trackWidth = 150f
            
            // Dessiner la piste inclinée
            paint.color = Color.parseColor("#FFFFFF")
            val trackPath = Path().apply {
                // Côté haut de la piste
                moveTo(trackStartX - trackWidth/2f, trackStartY - trackWidth/4f)
                lineTo(trackEndX - trackWidth/2f, trackEndY - trackWidth/4f)
                // Côté bas de la piste
                lineTo(trackEndX + trackWidth/2f, trackEndY + trackWidth/4f)
                lineTo(trackStartX + trackWidth/2f, trackStartY + trackWidth/4f)
                close()
            }
            canvas.drawPath(trackPath, paint)
            
            // Bordures de la piste
            paint.color = Color.parseColor("#CCCCCC")
            paint.strokeWidth = 6f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(trackStartX - trackWidth/2f, trackStartY - trackWidth/4f, trackEndX - trackWidth/2f, trackEndY - trackWidth/4f, paint)
            canvas.drawLine(trackStartX + trackWidth/2f, trackStartY + trackWidth/4f, trackEndX + trackWidth/2f, trackEndY + trackWidth/4f, paint)
            paint.style = Paint.Style.FILL
            
            // Bobsleigh SUR la piste inclinée
            val bobX = trackStartX + (trackEndX - trackStartX) * progress
            val bobY = trackStartY + (trackEndY - trackStartY) * progress
            
            // Angle du bobsleigh selon la pente
            val angle = atan2(trackEndY - trackStartY, trackEndX - trackStartX) * 180f / PI.toFloat()
            val scale = 0.2f
            
            canvas.save()
            canvas.rotate(angle, bobX, bobY)
            
            bobFinishLineBitmap?.let { bmp ->
                val dstRect = RectF(
                    bobX - bmp.width * scale / 2f,
                    bobY - bmp.height * scale / 2f,
                    bobX + bmp.width * scale / 2f,
                    bobY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            canvas.restore()
            
            // Effet de traînée SUR LA PISTE
            for (i in 1..5) {
                val trailProgress = progress - (i * 0.08f)
                if (trailProgress > 0f) {
                    val trailX = trackStartX + (trackEndX - trackStartX) * trailProgress
                    val trailY = trackStartY + (trackEndY - trackStartY) * trailProgress
                    
                    paint.color = Color.WHITE
                    paint.alpha = (255 * (1f - i * 0.2f)).toInt()
                    canvas.drawCircle(trailX, trailY, 8f - i * 1f, paint)
                    paint.alpha = 255
                }
            }
            
            // Vitesse et titre
            paint.color = Color.BLACK
            paint.textSize = 50f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("⛷️ PREMIÈRE DESCENTE", w/2f, 80f, paint)
            
            paint.textSize = 60f
            paint.color = Color.YELLOW
            canvas.drawText("${speed.toInt()} KM/H", w/2f, h - 60f, paint)
        }
        
        private fun drawSecondDescent(canvas: Canvas, w: Int, h: Int) {
            // Fond plus sombre (plus rapide)
            paint.color = Color.parseColor("#6495ED")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // MÊME PISTE INCLINÉE mais plus rapide
            val progress = phaseTimer / secondDescentDuration
            
            val trackStartX = -100f
            val trackStartY = h * 0.1f
            val trackEndX = w + 100f
            val trackEndY = h * 0.9f
            val trackWidth = 160f  // Légèrement plus large
            
            // Piste inclinée
            paint.color = Color.parseColor("#FFFFFF")
            val trackPath = Path().apply {
                moveTo(trackStartX - trackWidth/2f, trackStartY - trackWidth/4f)
                lineTo(trackEndX - trackWidth/2f, trackEndY - trackWidth/4f)
                lineTo(trackEndX + trackWidth/2f, trackEndY + trackWidth/4f)
                lineTo(trackStartX + trackWidth/2f, trackStartY + trackWidth/4f)
                close()
            }
            canvas.drawPath(trackPath, paint)
            
            // Bordures renforcées
            paint.color = Color.parseColor("#AAAAAA")
            paint.strokeWidth = 8f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(trackStartX - trackWidth/2f, trackStartY - trackWidth/4f, trackEndX - trackWidth/2f, trackEndY - trackWidth/4f, paint)
            canvas.drawLine(trackStartX + trackWidth/2f, trackStartY + trackWidth/4f, trackEndX + trackWidth/2f, trackEndY + trackWidth/4f, paint)
            paint.style = Paint.Style.FILL
            
            // Bobsleigh SUR la piste avec angle
            val bobX = trackStartX + (trackEndX - trackStartX) * progress
            val bobY = trackStartY + (trackEndY - trackStartY) * progress
            val angle = atan2(trackEndY - trackStartY, trackEndX - trackStartX) * 180f / PI.toFloat()
            val scale = 0.22f
            
            canvas.save()
            canvas.rotate(angle, bobX, bobY)
            
            bobFinishLineBitmap?.let { bmp ->
                val dstRect = RectF(
                    bobX - bmp.width * scale / 2f,
                    bobY - bmp.height * scale / 2f,
                    bobX + bmp.width * scale / 2f,
                    bobY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            canvas.restore()
            
            // Effet de vitesse plus intense SUR LA PISTE
            for (i in 1..8) {
                val trailProgress = progress - (i * 0.04f)
                if (trailProgress > 0f) {
                    val trailX = trackStartX + (trackEndX - trackStartX) * trailProgress
                    val trailY = trackStartY + (trackEndY - trackStartY) * trailProgress
                    
                    paint.color = Color.CYAN
                    paint.alpha = (255 * (1f - i * 0.125f)).toInt()
                    canvas.drawCircle(trailX, trailY, 12f - i * 1f, paint)
                    paint.alpha = 255
                }
            }
            
            // Lignes de vitesse parallèles à la piste
            for (i in 0..8) {
                paint.color = Color.WHITE
                paint.alpha = 120
                paint.strokeWidth = 2f
                
                val lineProgress = (i * 0.15f + phaseTimer * 3f) % 1.2f
                if (lineProgress <= 1f) {
                    val lineX = trackStartX + (trackEndX - trackStartX) * lineProgress
                    val lineY = trackStartY + (trackEndY - trackStartY) * lineProgress
                    
                    // Lignes perpendiculaires à la direction de la piste
                    val perpX = -(trackEndY - trackStartY) / 10f
                    val perpY = (trackEndX - trackStartX) / 10f
                    
                    canvas.drawLine(lineX - perpX, lineY - perpY, lineX + perpX, lineY + perpY, paint)
                }
                paint.alpha = 255
            }
            
            paint.color = Color.BLACK
            paint.textSize = 50f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("⚡ ACCÉLÉRATION MAXIMALE", w/2f, 80f, paint)
            
            paint.textSize = 70f
            paint.color = Color.RED
            canvas.drawText("${speed.toInt()} KM/H", w/2f, h - 60f, paint)
        }
        
        private fun drawControlDescent(canvas: Canvas, w: Int, h: Int) {
            // CARTE À GAUCHE (65% de l'écran)
            val mapRect = RectF(0f, 0f, w * 0.65f, h.toFloat())
            
            // Fond de carte
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(mapRect, paint)
            
            // Dessiner la piste de bobsleigh (serpentine)
            paint.color = Color.parseColor("#CCCCCC")
            paint.strokeWidth = 8f
            paint.style = Paint.Style.STROKE
            
            val trackPath = Path()
            val startX = mapRect.width() * 0.5f
            val startY = 50f
            trackPath.moveTo(startX, startY)
            
            // Piste serpentine avec courbes
            for (i in 1..20) {
                val y = startY + i * (mapRect.height() - 100f) / 20f
                val wiggle = sin(i * 0.8f) * mapRect.width() * 0.2f
                val x = startX + wiggle
                trackPath.lineTo(x, y)
            }
            canvas.drawPath(trackPath, paint)
            paint.style = Paint.Style.FILL
            
            // Point rouge montrant la position actuelle
            val currentY = 50f + trackProgress * (mapRect.height() - 100f)
            val currentWiggle = sin(trackProgress * 16f) * mapRect.width() * 0.2f
            val currentX = startX + currentWiggle
            
            paint.color = Color.RED
            canvas.drawCircle(currentX, currentY, 12f, paint)
            
            // Ligne d'arrivée
            paint.color = Color.parseColor("#FFD700")
            paint.strokeWidth = 6f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(mapRect.left + 50f, mapRect.bottom - 50f, mapRect.right - 50f, mapRect.bottom - 50f, paint)
            paint.style = Paint.Style.FILL
            
            // Titre de la carte
            paint.color = Color.BLACK
            paint.textSize = 25f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("CARTE DU PARCOURS", mapRect.centerX(), 30f, paint)
            
            // TUNNEL IMMERSIF À DROITE (35% de l'écran)
            val tunnelRect = RectF(w * 0.65f, 0f, w.toFloat(), h.toFloat())
            drawImmersiveTunnel(canvas, tunnelRect)
        }
        
        private fun drawFullScreenTunnel(canvas: Canvas, w: Int, h: Int) {
            // TUNNEL PLEIN ÉCRAN avec l'effet starfield !
            val centerX = w/2f + (tiltZ * w * 0.15f)
            val centerY = h/2f
            
            // Fond profond bleuté
            paint.color = Color.rgb(10, 20, 40)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // EFFET TUNNEL AVEC 60 LIGNES RADIALES
            val lineCount = 60
            val maxLength = h * 1.2f
            
            for (i in 0 until lineCount) {
                val angle = (2 * PI / lineCount * i).toFloat()
                val speedFactor = (speed / maxSpeed).coerceIn(0.3f, 1f)
                val length = maxLength * speedFactor
                
                // Position finale de chaque ligne
                val endX = centerX + cos(angle) * length
                val endY = centerY + sin(angle) * length
                
                // Couleur et épaisseur selon la vitesse
                paint.color = Color.argb((120 + 100 * speedFactor).toInt(), 200, 200, 255)
                paint.strokeWidth = 3f + 7f * speedFactor
                
                canvas.drawLine(centerX, centerY, endX, endY, paint)
            }
            
            // Effet de vibration quand ça va vite
            if (cameraShake > 0f) {
                canvas.save()
                canvas.translate(
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 15f,
                    (kotlin.random.Random.nextFloat() - 0.5f) * cameraShake * 15f
                )
            }
            
            // Flèches directionnelles ÉNORMES (si le tunnel est pas assez clair)
            if (abs(idealDirection) > 0.3f) {
                paint.color = Color.YELLOW
                paint.textSize = 120f  // ENCORE PLUS GROS !
                paint.textAlign = Paint.Align.CENTER
                
                val directionArrow = if (idealDirection < 0f) "⬅️" else "➡️"
                canvas.drawText(directionArrow, w/2f, 150f, paint)
            }
            
            // Cockpit centré en bas
            bobCockpitBitmap?.let { bmp ->
                val scale = 0.3f
                val cockpitRect = RectF(
                    w/2f - bmp.width * scale / 2f,
                    h - bmp.height * scale - 30f,
                    w/2f + bmp.width * scale / 2f,
                    h - 30f
                )
                canvas.drawBitmap(bmp, null, cockpitRect, paint)
            }
            
            // Vitesse en haut à droite
            paint.color = Color.WHITE
            paint.textSize = 50f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${speed.toInt()}", w - 30f, 80f, paint)
            canvas.drawText("KM/H", w - 30f, 130f, paint)
            
            // Performance en temps réel
            paint.textSize = 35f
            paint.color = if (steeringAccuracy > 0.8f) Color.GREEN else if (steeringAccuracy > 0.5f) Color.YELLOW else Color.RED
            canvas.drawText("${(steeringAccuracy * 100).toInt()}%", w - 30f, 180f, paint)
            
            if (cameraShake > 0f) canvas.restore()
        }
        
        private fun drawBeautifulFinishLine(canvas: Canvas, w: Int, h: Int) {
            // BELLE TRAVERSÉE DE LIGNE D'ARRIVÉE
            
            // Même fond tunnel mais plus clair
            paint.color = Color.rgb(20, 30, 60)
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            val centerX = w/2f
            val centerY = h/2f
            
            // Effet tunnel atténué en arrière-plan
            val lineCount = 30
            val maxLength = h * 0.8f
            
            for (i in 0 until lineCount) {
                val angle = (2 * PI / lineCount * i).toFloat()
                val length = maxLength * 0.6f  // Plus court pour l'arrière-plan
                
                val endX = centerX + cos(angle) * length
                val endY = centerY + sin(angle) * length
                
                paint.color = Color.argb(60, 200, 200, 255)  // Très transparent
                paint.strokeWidth = 2f
                canvas.drawLine(centerX, centerY, endX, endY, paint)
            }
            
            // LIGNE D'ARRIVÉE qui grandit depuis le centre
            val lineProgress = phaseTimer / finishLineDuration
            val lineSize = lineProgress * w * 1.5f
            
            // Cercles concentriques damier (effet de ligne qui arrive vers nous)
            for (radius in 50..lineSize.toInt() step 60) {
                val ringIndex = (radius / 60) % 2
                val color = if (ringIndex == 0) Color.WHITE else Color.BLACK
                
                paint.color = color
                paint.alpha = (255 * (1f - radius / lineSize)).toInt().coerceIn(0, 255)
                paint.strokeWidth = 30f
                paint.style = Paint.Style.STROKE
                canvas.drawCircle(centerX, centerY, radius.toFloat(), paint)
            }
            paint.alpha = 255
            paint.style = Paint.Style.FILL
            
            // GROS TEXTE FINISH
            paint.color = Color.YELLOW
            paint.textSize = 100f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🏁", centerX, centerY - 50f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 60f
            canvas.drawText("FINISH!", centerX, centerY + 80f, paint)
            
            // Vitesse finale
            paint.textSize = 80f
            paint.color = Color.GREEN
            canvas.drawText("${speed.toInt()} KM/H", centerX, h - 100f, paint)
        }
        
        private fun drawBeautifulCelebration(canvas: Canvas, w: Int, h: Int) {
            // BELLE CÉLÉBRATION COHÉRENTE
            val progress = phaseTimer / celebrationDuration
            
            // Fond qui s'éclaircit progressivement (tunnel -> doré)
            val bgColor = if (progress < 0.3f) {
                // Reste dans le tunnel au début
                Color.rgb(10, 20, 40)
            } else {
                // Transition vers le doré
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
            
            if (progress < 0.4f) {
                // PHASE 1 : Sortie du tunnel avec ralentissement
                val tunnelProgress = progress / 0.4f
                val lineCount = 40
                val maxLength = h * (1.2f - tunnelProgress * 0.8f)  // Tunnel qui rétrécit
                
                for (i in 0 until lineCount) {
                    val angle = (2 * PI / lineCount * i).toFloat()
                    val speedFactor = 1f - tunnelProgress * 0.7f  // Ralentissement
                    val length = maxLength * speedFactor
                    
                    val endX = centerX + cos(angle) * length
                    val endY = centerY + sin(angle) * length
                    
                    paint.color = Color.argb((200 - tunnelProgress * 100).toInt(), 200, 200, 255)
                    paint.strokeWidth = (8f - tunnelProgress * 5f).coerceAtLeast(1f)
                    
                    canvas.drawLine(centerX, centerY, endX, endY, paint)
                }
            } else {
                // PHASE 2 : Célébration avec particules et feux d'artifice
                val celebProgress = (progress - 0.4f) / 0.6f
                
                // Particules d'or qui explosent
                for (i in 0..20) {
                    val angle = (2 * PI / 20 * i + celebProgress * 4).toFloat()
                    val radius = celebProgress * 300f
                    val particleX = centerX + cos(angle) * radius
                    val particleY = centerY + sin(angle) * radius
                    
                    paint.color = Color.YELLOW
                    paint.alpha = ((1f - celebProgress) * 255).toInt()
                    canvas.drawCircle(particleX, particleY, 8f, paint)
                }
                paint.alpha = 255
                
                // Étoiles scintillantes
                for (i in 0..15) {
                    val starX = (i * 67) % w.toFloat()
                    val starY = (i * 43 + celebProgress * 200) % h.toFloat()
                    val twinkle = sin(celebProgress * 12 + i).coerceAtLeast(0f)
                    
                    if (twinkle > 0.5f) {
                        paint.color = Color.WHITE
                        paint.textSize = 20f + twinkle * 20f
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText("✨", starX, starY, paint)
                    }
                }
            }
            
            // Bobsleigh qui ralentit et s'arrête au centre
            val bobProgress = progress.coerceAtMost(0.8f) / 0.8f
            val bobX = centerX - 200f + bobProgress * 200f  // Arrive au centre
            val bobY = centerY + 100f
            
            val scale = 0.3f
            bobCelebrationBitmap?.let { bmp ->
                val dstRect = RectF(
                    bobX - bmp.width * scale / 2f,
                    bobY - bmp.height * scale / 2f,
                    bobX + bmp.width * scale / 2f,
                    bobY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            // Textes de félicitations
            paint.color = Color.WHITE
            paint.textSize = 80f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🎉", centerX, 150f, paint)
            
            paint.textSize = 50f
            canvas.drawText("FÉLICITATIONS!", centerX, 220f, paint)
            
            // Vitesse finale spectaculaire
            paint.textSize = 60f
            paint.color = if (speed >= 150f) Color.GREEN else Color.YELLOW
            canvas.drawText("${speed.toInt()} KM/H", centerX, h - 150f, paint)
            
            if (speed >= 150f) {
                paint.color = Color.RED
                paint.textSize = 40f
                canvas.drawText("VITESSE MAXIMALE!", centerX, h - 100f, paint)
            }
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            // Fond doré
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
            
            // Score final
            paint.color = Color.BLACK
            paint.textSize = 80f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore}", w/2f, h * 0.2f, paint)
            
            paint.textSize = 40f
            canvas.drawText("POINTS", w/2f, h * 0.3f, paint)
            
            // Détails de performance
            paint.color = Color.parseColor("#001122")
            paint.textSize = 28f
            canvas.drawText("🚀 Poussée: ${(pushQuality * 100).toInt()}%", w/2f, h * 0.45f, paint)
            canvas.drawText("🎮 Contrôle: ${(controlPerformance * 100).toInt()}%", w/2f, h * 0.5f, paint)
            canvas.drawText("⚡ Vitesse max: ${speed.toInt()} km/h", w/2f, h * 0.55f, paint)
            canvas.drawText("🕒 Temps: ${raceTime.toInt()}s", w/2f, h * 0.6f, paint)
        }
        
        private fun drawImprovedPushPowerMeter(canvas: Canvas, w: Int, h: Int) {
            // Barre de puissance avec efficacité
            paint.color = Color.parseColor("#333333")
            canvas.drawRect(100f, h - 120f, w - 100f, h - 50f, paint)
            
            // Barre de puissance colorée
            paint.color = if (pushPower > 70f) Color.GREEN else if (pushPower > 40f) Color.YELLOW else Color.RED
            val powerWidth = (pushPower / 100f) * (w - 200f)
            canvas.drawRect(100f, h - 115f, 100f + powerWidth, h - 55f, paint)
            
            // Texte de puissance
            paint.color = Color.WHITE
            paint.textSize = 25f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("PUISSANCE: ${pushPower.toInt()}%", w/2f, h - 130f, paint)
            
            // Indicateur d'efficacité séparé
            val efficiency = (pushQuality * 100).toInt()
            paint.color = when {
                efficiency > 80 -> Color.GREEN
                efficiency > 60 -> Color.YELLOW  
                else -> Color.RED
            }
            paint.textSize = 20f
            canvas.drawText("Efficacité: ${efficiency}%", w/2f, h - 25f, paint)
        }
        
        private fun drawIceParticles(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#CCEEEE")
            paint.alpha = 200
            for (particle in iceParticles) {
                canvas.drawCircle(particle.x, particle.y, particle.size, paint)
            }
            paint.alpha = 255
        }
    }

    data class SpeedLine(
        var x: Float,
        var y: Float,
        val speed: Float
    )
    
    data class IceParticle(
        var x: Float,
        var y: Float,
        val speed: Float,
        val size: Float
    )

    enum class GameState {
        PREPARATION, PUSH_START, FIRST_DESCENT, SECOND_DESCENT, CONTROL_DESCENT, FINISH_LINE, CELEBRATION, RESULTS, FINISHED
    }
}
