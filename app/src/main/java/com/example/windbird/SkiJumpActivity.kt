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
    
    // Phases avec durées TRÈS longues
    private val preparationDuration = 8f // AUGMENTÉ de 5f
    private val approachDuration = 15f // AUGMENTÉ de 10f
    private val takeoffDuration = 8f // AUGMENTÉ de 5f
    private val flightDuration = 12f // AUGMENTÉ de 8f
    private val landingDuration = 5f // AUGMENTÉ de 3f
    private val resultsDuration = 8f // AUGMENTÉ de 5f
    
    // Variables de jeu
    private var speed = 0f
    private var maxSpeed = 80f // RÉDUIT de 100f
    private var takeoffPower = 0f
    private var jumpDistance = 0f
    private var stability = 1f
    private var landingBonus = 0f
    
    // Contrôles gyroscope - MOINS SENSIBLE
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
            textSize = 22f // AUGMENTÉ de 18f
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(25, 20, 25, 20) // AUGMENTÉ
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
        finalScore = 0
        scoreCalculated = false
        cameraShake = 0f
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

        tiltX = event.values[0]
        tiltY = event.values[1]
        tiltZ = event.values[2]

        // Progression TRÈS lente du jeu
        phaseTimer += 0.025f // RÉDUIT de 0.05f

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
    
    private fun handleApproach() {
        // Incliner vers l'avant pour accélérer - MOINS SENSIBLE
        if (tiltY < -0.4f) { // AUGMENTÉ de -0.2f
            speed += 0.8f // RÉDUIT de 1.2f
        } else if (tiltY > 0.4f) { // AUGMENTÉ de 0.2f
            speed -= 0.5f // RÉDUIT de 0.8f
        }
        
        // Pénalité pour mouvement latéral - MOINS SENSIBLE
        if (abs(tiltX) > 0.6f) { // AUGMENTÉ de 0.4f
            speed -= 0.2f // RÉDUIT de 0.3f
        }
        
        speed = speed.coerceIn(0f, maxSpeed)
        
        if (phaseTimer >= approachDuration) {
            gameState = GameState.TAKEOFF
            phaseTimer = 0f
            cameraShake = 0.5f
        }
    }
    
    private fun handleTakeoff() {
        // Redresser le téléphone pour puissance de saut - MOINS SENSIBLE
        if (tiltY > 0.7f) { // AUGMENTÉ de 0.5f
            takeoffPower += 2f // RÉDUIT de 3f
        }
        
        takeoffPower = takeoffPower.coerceIn(0f, 100f)
        
        if (phaseTimer >= takeoffDuration) {
            // Calculer distance de base
            jumpDistance = (speed * 1.2f) + (takeoffPower * 0.8f)
            gameState = GameState.FLIGHT
            phaseTimer = 0f
            generateMoreSnowParticles()
        }
    }
    
    private fun handleFlight() {
        // Stabilité critique - rester immobile - MOINS SENSIBLE
        val currentStability = 1f - (abs(tiltX) + abs(tiltY) + abs(tiltZ)) / 3f
        stability = (stability * 0.95f + currentStability.coerceIn(0f, 1f) * 0.05f) // PLUS STABLE
        
        // Bonus distance pour stabilité
        jumpDistance += stability * 0.2f // RÉDUIT de 0.4f
        
        if (phaseTimer >= flightDuration) {
            gameState = GameState.LANDING
            phaseTimer = 0f
            cameraShake = 1f
        }
    }
    
    private fun handleLanding() {
        // Bonus atterrissage - MOINS SENSIBLE
        if (tiltY < -0.05f && tiltY > -0.6f && abs(tiltX) < 0.3f) { // AJUSTÉ
            landingBonus += 0.5f // RÉDUIT de 0.8f
        }
        
        jumpDistance += landingBonus
        
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
    
    private fun calculateFinalScore() {
        if (!scoreCalculated) {
            val speedBonus = (speed / maxSpeed * 60).toInt()
            val distanceBonus = (jumpDistance * 1.5f).toInt()
            val stabilityBonus = (stability * 40).toInt()
            val landingBonus = (landingBonus * 10).toInt()
            
            finalScore = maxOf(50, speedBonus + distanceBonus + stabilityBonus + landingBonus)
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
        
        cameraShake = maxOf(0f, cameraShake - 0.015f) // RÉDUIT de 0.02f
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
            GameState.APPROACH -> "⛷️ ${tournamentData.playerNames[currentPlayerIndex]} | Élan: ${speed.toInt()} km/h | ${(approachDuration - phaseTimer).toInt() + 1}s"
            GameState.TAKEOFF -> "🚀 ${tournamentData.playerNames[currentPlayerIndex]} | REDRESSEZ! Puissance: ${takeoffPower.toInt()}% | ${(takeoffDuration - phaseTimer).toInt() + 1}s"
            GameState.FLIGHT -> "✈️ ${tournamentData.playerNames[currentPlayerIndex]} | Vol: ${jumpDistance.toInt()}m | Stabilité: ${(stability * 100).toInt()}% | ${(flightDuration - phaseTimer).toInt() + 1}s"
            GameState.LANDING -> "🎯 ${tournamentData.playerNames[currentPlayerIndex]} | Atterrissage! | ${(landingDuration - phaseTimer).toInt() + 1}s"
            GameState.RESULTS -> "🏆 ${tournamentData.playerNames[currentPlayerIndex]} | Distance finale: ${jumpDistance.toInt()}m | Score: ${finalScore}"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Saut terminé!"
        }
    }

    inner class SkiJumpView(context: Context) : View(context) {
        private val paint = Paint()
        
        // Variables pour le sprite sheet
        private var skierBitmap: Bitmap? = null
        private val spriteCols = 4  // 4 colonnes dans votre sprite sheet
        private val spriteRows = 4  // 4 rangées dans votre sprite sheet
        private val totalFrames = 16  // 4x4 = 16 frames
        private var frameWidth = 0
        private var frameHeight = 0
        
        init {
            // Créer un bitmap générique avec du code puisqu'on n'a pas accès au sprite sheet
            // En production, remplacez cette ligne par :
            // skierBitmap = BitmapFactory.decodeResource(resources, R.drawable.skier_sprite_sheet)
            skierBitmap = BitmapFactory.decodeResource(resources, R.drawable.skier_sprite_sheet)
            skierBitmap?.let {
            frameWidth = it.width / spriteCols
            frameHeight = it.height / spriteRows
}
        }
        
        private fun createFallbackSkierBitmap() {
            // Créer un bitmap de substitution 200x200 pixels (4x4 frames de 50x50)
            skierBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(skierBitmap!!)
            val tempPaint = Paint().apply {
                color = Color.parseColor("#FF4444")
                style = Paint.Style.FILL
            }
            
            // Dessiner 16 frames simples (cercles avec légères variations)
            for (row in 0 until spriteRows) {
                for (col in 0 until spriteCols) {
                    val x = col * 50 + 25  // Centre de chaque frame 50x50
                    val y = row * 50 + 25
                    val radius = 15f + ((row * 4 + col) % 3) * 2f  // Variation de taille
                    canvas.drawCircle(x.toFloat(), y.toFloat(), radius, tempPaint)
                }
            }
            
            frameWidth = 50
            frameHeight = 50
        }

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            
            // Appliquer camera shake si présent
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
            // Fond montagne
            paint.color = Color.parseColor("#87CEEB")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Montagnes en arrière-plan
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
            
            // Instructions centrales - TEXTE PLUS GROS
            paint.color = Color.WHITE
            paint.textSize = 44f // AUGMENTÉ de 32f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🎿 SAUT À SKI 🎿", w/2f, h * 0.3f, paint)
            
            paint.textSize = 32f // AUGMENTÉ de 24f
            canvas.drawText("Préparez-vous...", w/2f, h * 0.4f, paint)
            
            paint.textSize = 28f // AUGMENTÉ de 20f
            paint.color = Color.YELLOW
            canvas.drawText("Dans ${(preparationDuration - phaseTimer).toInt() + 1} secondes", w/2f, h * 0.5f, paint)
            
            paint.textSize = 22f // AUGMENTÉ de 16f
            paint.color = Color.CYAN
            canvas.drawText("📱 Inclinez vers l'avant pour accélérer", w/2f, h * 0.7f, paint)
            canvas.drawText("📱 Redressez au signal pour sauter", w/2f, h * 0.75f, paint)
            canvas.drawText("📱 Restez stable en vol", w/2f, h * 0.8f, paint)
        }
        
        private fun drawApproach(canvas: Canvas, w: Int, h: Int) {
            // VUE DE HAUT - Tremplin en perspective
            paint.color = Color.parseColor("#87CEEB")
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Tremplin en perspective (vue de haut)
            paint.color = Color.WHITE
            val jumpPath = Path()
            jumpPath.moveTo(w * 0.4f, h.toFloat())
            jumpPath.lineTo(w * 0.6f, h.toFloat())
            jumpPath.lineTo(w * 0.55f, h * 0.2f)
            jumpPath.lineTo(w * 0.45f, h * 0.2f)
            jumpPath.close()
            canvas.drawPath(jumpPath, paint)
            
            // Lignes de vitesse sur les côtés - PLUS LENTES
            paint.color = Color.parseColor("#CCCCCC")
            paint.strokeWidth = 2f
            paint.style = Paint.Style.STROKE
            for (i in 1..10) {
                val lineY = h - (i * h * 0.08f) + (phaseTimer * 10f) % (h * 0.08f) // RÉDUIT de 20f
                canvas.drawLine(w * 0.4f, lineY, w * 0.6f, lineY, paint)
            }
            
            // Réinitialiser le style pour le sprite
            paint.style = Paint.Style.FILL
            
            // NOUVEAU: Skieur avec sprite sheet au lieu d'un simple cercle
            val skierProgress = phaseTimer / approachDuration
            val skierY = h * (1f - skierProgress * 0.8f)
            val skierX = w / 2f
            
            // Animation frame basée sur le temps
            val frameIndex = ((phaseTimer * 8f) % totalFrames).toInt()  // 8 FPS d'animation
            val srcX = (frameIndex % spriteCols) * frameWidth
            val srcY = (frameIndex / spriteCols) * frameHeight
            val srcRect = Rect(srcX, srcY, srcX + frameWidth, srcY + frameHeight)
            
            // Taille à l'écran (augmente en descendant pour effet de perspective)
            val scale = 1.5f + skierProgress * 1.0f  // Commence petit, grandit
            val dstRect = RectF(
                skierX - frameWidth * scale / 2f,
                skierY - frameHeight * scale / 2f,
                skierX + frameWidth * scale / 2f,
                skierY + frameHeight * scale / 2f
            )
            
            skierBitmap?.let { bmp ->
                canvas.drawBitmap(bmp, srcRect, dstRect, paint)
            }
            
            // Barre de vitesse ÉNORME
            drawSpeedMeter(canvas, w, h)
            
            // Instructions - TEXTE PLUS GROS
            paint.color = Color.WHITE
            paint.textSize = 28f // AUGMENTÉ de 20f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("📱 INCLINEZ VERS L'AVANT POUR ACCÉLÉRER", w/2f, 60f, paint)
        }
        
        private fun drawTakeoff(canvas: Canvas, w: Int, h: Int) {
            // VUE DE PROFIL - Moment dramatique
            paint.color = Color.parseColor("#87CEEB")
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Tremplin courbé (vue de profil)
            paint.color = Color.WHITE
            val rampPath = Path()
            rampPath.moveTo(0f, h * 0.8f)
            rampPath.quadTo(w * 0.7f, h * 0.6f, w * 0.9f, h * 0.4f)
            rampPath.lineTo(w.toFloat(), h * 0.45f)
            rampPath.lineTo(w.toFloat(), h.toFloat())
            rampPath.lineTo(0f, h.toFloat())
            rampPath.close()
            canvas.drawPath(rampPath, paint)
            
            // NOUVEAU: Skieur avec sprite au moment du décollage
            val skierX = w * 0.85f
            val skierY = h * 0.4f
            
            // Frame spécifique pour le décollage (peut être une frame différente)
            val frameIndex = (totalFrames * 0.75f).toInt()  // Utilise une frame vers la fin
            val srcX = (frameIndex % spriteCols) * frameWidth
            val srcY = (frameIndex / spriteCols) * frameHeight
            val srcRect = Rect(srcX, srcY, srcX + frameWidth, srcY + frameHeight)
            
            val scale = 2.0f
            val dstRect = RectF(
                skierX - frameWidth * scale / 2f,
                skierY - frameHeight * scale / 2f,
                skierX + frameWidth * scale / 2f,
                skierY + frameHeight * scale / 2f
            )
            
            skierBitmap?.let { bmp ->
                canvas.drawBitmap(bmp, srcRect, dstRect, paint)
            }
            
            // Effet de ralenti avec trails du sprite
            paint.alpha = 100
            for (i in 1..5) {
                val trailRect = RectF(
                    skierX - frameWidth * scale / 2f - i * 12f,
                    skierY - frameHeight * scale / 2f,
                    skierX + frameWidth * scale / 2f - i * 12f,
                    skierY + frameHeight * scale / 2f
                )
                skierBitmap?.let { bmp ->
                    canvas.drawBitmap(bmp, srcRect, trailRect, paint)
                }
            }
            paint.alpha = 255
            
            // Barre de puissance de décollage ÉNORME
            drawTakeoffPowerMeter(canvas, w, h)
            
            // Instructions dramatiques - TEXTE PLUS GROS
            paint.color = Color.YELLOW
            paint.textSize = 36f // AUGMENTÉ de 28f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🚀 REDRESSEZ MAINTENANT! 🚀", w/2f, h * 0.2f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 28f // AUGMENTÉ de 20f
            canvas.drawText("📱 INCLINEZ VERS L'ARRIÈRE", w/2f, h * 0.25f, paint)
        }
        
        private fun drawFlight(canvas: Canvas, w: Int, h: Int) {
            // VUE DE BIAIS - Parfait pour voir déséquilibres
            paint.color = Color.parseColor("#87CEEB")
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Montagnes qui défilent - PLUS LENT
            paint.color = Color.parseColor("#DDDDDD")
            val mountainPath = Path()
            val scrollOffset = (phaseTimer * 25f) % 200f // RÉDUIT de 50f
            mountainPath.moveTo(-scrollOffset, h * 0.7f)
            mountainPath.lineTo(w * 0.2f - scrollOffset, h * 0.4f)
            mountainPath.lineTo(w * 0.5f - scrollOffset, h * 0.6f)
            mountainPath.lineTo(w * 0.8f - scrollOffset, h * 0.3f)
            mountainPath.lineTo(w + 100f - scrollOffset, h * 0.5f)
            mountainPath.lineTo(w + 100f, h.toFloat())
            mountainPath.lineTo(-100f, h.toFloat())
            mountainPath.close()
            canvas.drawPath(mountainPath, paint)
            
            // NOUVEAU: Skieur en vol avec sprite qui montre les déséquilibres
            val centerX = w / 2f
            val centerY = h / 2f
            
            canvas.save()
            canvas.translate(centerX, centerY)
            
            // Rotation selon gyroscope pour montrer déséquilibre - MOINS INTENSE
            canvas.rotate(tiltX * 10f + tiltZ * 5f) // RÉDUIT de 20f et 10f
            
            // Frame d'animation pour le vol
            val frameIndex = ((phaseTimer * 6f) % totalFrames).toInt()  // Animation plus lente en vol
            val srcX = (frameIndex % spriteCols) * frameWidth
            val srcY = (frameIndex / spriteCols) * frameHeight
            val srcRect = Rect(srcX, srcY, srcX + frameWidth, srcY + frameHeight)
            
            val scale = 3.0f  // Plus gros pour mieux voir en vol
            val dstRect = RectF(
                -frameWidth * scale / 2f,
                -frameHeight * scale / 2f,
                frameWidth * scale / 2f,
                frameHeight * scale / 2f
            )
            
            skierBitmap?.let { bmp ->
                canvas.drawBitmap(bmp, srcRect, dstRect, paint)
            }
            
            canvas.restore()
            
            // Indicateurs de stabilité ÉNORMES
            drawStabilityIndicators(canvas, w, h)
            
            // Instructions - TEXTE PLUS GROS
            paint.color = Color.WHITE
            paint.textSize = 32f // AUGMENTÉ de 24f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("⚖️ GARDEZ LE TÉLÉPHONE STABLE ⚖️", w/2f, 60f, paint)
        }
        
        private fun drawLanding(canvas: Canvas, w: Int, h: Int) {
            // VUE DE FACE/LÉGÈREMENT EN BIAIS - Impact dramatique
            paint.color = Color.parseColor("#87CEEB")
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste d'atterrissage en perspective
            paint.color = Color.WHITE
            val landingPath = Path()
            landingPath.moveTo(w * 0.2f, h * 0.8f)
            landingPath.lineTo(w * 0.8f, h * 0.8f)
            landingPath.lineTo(w * 0.9f, h.toFloat())
            landingPath.lineTo(w * 0.1f, h.toFloat())
            landingPath.close()
            canvas.drawPath(landingPath, paint)
            
            // Marques de distance sur la piste - TEXTE PLUS GROS
            paint.color = Color.parseColor("#666666")
            paint.textSize = 20f // AUGMENTÉ de 16f
            paint.textAlign = Paint.Align.CENTER
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f
            for (i in 1..5) {
                val markX = w * 0.2f + i * (w * 0.6f / 5f)
                canvas.drawLine(markX, h * 0.8f, markX, h * 0.85f, paint)
                canvas.drawText("${i * 20}m", markX, h * 0.87f, paint)
            }
            
            // NOUVEAU: Skieur qui atterrit avec sprite
            val skierX = w * (0.2f + (jumpDistance / 120f) * 0.6f)
            val skierY = h * 0.75f
            
            // Frame d'atterrissage
            val frameIndex = (totalFrames * 0.9f).toInt()  // Frame vers la fin pour l'atterrissage
            val srcX = (frameIndex % spriteCols) * frameWidth
            val srcY = (frameIndex / spriteCols) * frameHeight
            val srcRect = Rect(srcX, srcY, srcX + frameWidth, srcY + frameHeight)
            
            val scale = 2.5f
            val dstRect = RectF(
                skierX - frameWidth * scale / 2f,
                skierY - frameHeight * scale / 2f,
                skierX + frameWidth * scale / 2f,
                skierY + frameHeight * scale / 2f
            )
            
            paint.style = Paint.Style.FILL
            skierBitmap?.let { bmp ->
                canvas.drawBitmap(bmp, srcRect, dstRect, paint)
            }
            
            // Explosion de neige à l'impact - PLUS GROS
            paint.color = Color.WHITE
            paint.alpha = 150
            for (i in 1..8) {
                val angle = i * 45f
                val particleX = skierX + cos(Math.toRadians(angle.toDouble())).toFloat() * 40f // PLUS GROS
                val particleY = skierY + sin(Math.toRadians(angle.toDouble())).toFloat() * 20f
                canvas.drawCircle(particleX, particleY, 12f, paint) // PLUS GROS
            }
            paint.alpha = 255
            
            // Distance atteinte ÉNORME - TEXTE PLUS GROS
            paint.color = Color.YELLOW
            paint.textSize = 64f // AUGMENTÉ de 48f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${jumpDistance.toInt()}m", w/2f, h * 0.3f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 28f // AUGMENTÉ de 20f
            canvas.drawText("📱 INCLINEZ LÉGÈREMENT VERS L'AVANT", w/2f, h * 0.4f, paint)
        }
        
        private fun drawResults(canvas: Canvas, w: Int, h: Int) {
            // VUE PANORAMIQUE - Belle vue d'ensemble
            paint.color = Color.parseColor("#87CEEB")
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Fond doré pour les résultats
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
            
            // SCORE ÉNORME ET LISIBLE - TEXTE PLUS GROS
            paint.color = Color.BLACK
            paint.textSize = 80f // AUGMENTÉ de 64f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${finalScore}", w/2f, h * 0.2f, paint)
            
            paint.textSize = 40f // AUGMENTÉ de 32f
            canvas.drawText("POINTS", w/2f, h * 0.3f, paint)
            
            // Détails du score - TEXTE PLUS GROS
            paint.color = Color.WHITE
            paint.textSize = 28f // AUGMENTÉ de 24f
            canvas.drawText("🎿 Distance: ${jumpDistance.toInt()}m", w/2f, h * 0.5f, paint)
            canvas.drawText("⚡ Vitesse max: ${speed.toInt()} km/h", w/2f, h * 0.55f, paint)
            canvas.drawText("⚖️ Stabilité: ${(stability * 100).toInt()}%", w/2f, h * 0.6f, paint)
            canvas.drawText("🎯 Atterrissage: ${landingBonus.toInt()} bonus", w/2f, h * 0.65f, paint)
            
            // Effet d'étoiles
            paint.color = Color.YELLOW
            for (i in 1..10) {
                val starX = kotlin.random.Random.nextFloat() * w
                val starY = kotlin.random.Random.nextFloat() * h * 0.4f
                drawStar(canvas, starX, starY, 10f) // PLUS GROS
            }
        }
        
        private fun drawSpeedMeter(canvas: Canvas, w: Int, h: Int) {
            // Barre de vitesse énorme sur le côté droit - PLUS GROSSE
            paint.color = Color.parseColor("#333333")
            paint.style = Paint.Style.FILL
            canvas.drawRect(w - 100f, 120f, w - 30f, h - 120f, paint) // PLUS LARGE
            
            paint.color = Color.GREEN
            val speedHeight = (speed / maxSpeed) * (h - 240f) // ADAPTÉ
            canvas.drawRect(w - 95f, h - 120f - speedHeight, w - 35f, h - 120f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 24f // AUGMENTÉ de 20f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("VITESSE", w - 65f, 100f, paint)
            canvas.drawText("${speed.toInt()}", w - 65f, h - 70f, paint)
            canvas.drawText("km/h", w - 65f, h - 45f, paint)
        }
        
        private fun drawTakeoffPowerMeter(canvas: Canvas, w: Int, h: Int) {
            // Barre de puissance de décollage énorme - PLUS GROSSE
            paint.color = Color.parseColor("#333333")
            paint.style = Paint.Style.FILL
            canvas.drawRect(120f, h - 100f, w - 120f, h - 30f, paint) // PLUS HAUTE
            
            paint.color = if (takeoffPower > 70f) Color.GREEN else if (takeoffPower > 40f) Color.YELLOW else Color.RED
            val powerWidth = (takeoffPower / 100f) * (w - 240f) // ADAPTÉ
            canvas.drawRect(120f, h - 95f, 120f + powerWidth, h - 35f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 24f // AUGMENTÉ de 18f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("PUISSANCE DE DÉCOLLAGE: ${takeoffPower.toInt()}%", w/2f, h - 110f, paint)
        }
        
        private fun drawStabilityIndicators(canvas: Canvas, w: Int, h: Int) {
            val baseY = h - 200f // PLUS BAS
            
            // Indicateur global de stabilité - ÉNORME
            paint.color = Color.parseColor("#333333")
            paint.style = Paint.Style.FILL
            canvas.drawRect(60f, baseY, 300f, baseY + 50f, paint) // PLUS GROS
            
            paint.color = if (stability > 0.8f) Color.GREEN else if (stability > 0.5f) Color.YELLOW else Color.RED
            canvas.drawRect(60f, baseY, 60f + stability * 240f, baseY + 50f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 26f // AUGMENTÉ de 20f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("STABILITÉ: ${(stability * 100).toInt()}%", 60f, baseY - 15f, paint)
            
            // Indicateurs détaillés - TEXTE PLUS GROS
            paint.textSize = 20f // AUGMENTÉ de 16f
            canvas.drawText("Gauche/Droite: ${if (abs(tiltX) < 0.4f) "✅" else "❌"}", 60f, baseY + 80f, paint) // SEUIL AJUSTÉ
            canvas.drawText("Avant/Arrière: ${if (abs(tiltY) < 0.4f) "✅" else "❌"}", 60f, baseY + 110f, paint)
            canvas.drawText("Rotation: ${if (abs(tiltZ) < 0.4f) "✅" else "❌"}", 60f, baseY + 140f, paint)
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
