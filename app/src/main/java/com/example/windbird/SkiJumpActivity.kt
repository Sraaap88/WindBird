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

    // Variables de jeu
    private var gameState = GameState.APPROACH
    private var speed = 0f
    private var maxSpeed = 100f
    private var jumpDistance = 0f
    private var jumpHeight = 0f
    private var takeoffTiming = 0f
    private var takeoffAngle = 0f
    
    // Variables de vol (3 axes à stabiliser)
    private var pitch = 0f    // Tangage (avant/arrière)
    private var roll = 0f     // Roulis (gauche/droite)
    private var yaw = 0f      // Lacet (rotation)
    
    // Perturbations aléatoires (vent)
    private var windPitch = 0f
    private var windRoll = 0f
    private var windYaw = 0f
    
    // Variables de temps
    private var approachTime = 0f
    private var flightTime = 0f
    private var totalFlightTime = 3.0f
    
    // Variables d'animation
    private var skierX = 0f
    private var skierY = 0f
    private var backgroundOffset = 0f
    
    // Données du tournoi
    private lateinit var tournamentData: TournamentData
    private var eventIndex: Int = 0
    private var numberOfPlayers: Int = 1
    private var currentPlayerIndex: Int = 0
    private var practiceMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // Récupérer les données du tournoi
        tournamentData = intent.getSerializableExtra("tournament_data") as TournamentData
        eventIndex = intent.getIntExtra("event_index", 0)
        numberOfPlayers = intent.getIntExtra("number_of_players", 1)
        practiceMode = intent.getBooleanExtra("practice_mode", false)
        currentPlayerIndex = intent.getIntExtra("current_player_index", tournamentData.getNextPlayer(eventIndex))

        // Initialiser les capteurs
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // Créer l'interface
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        statusText = TextView(this).apply {
            text = "Joueur: ${tournamentData.playerNames[currentPlayerIndex]} | Phase: Élan"
            setTextColor(Color.WHITE)
            textSize = 16f
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(20, 10, 20, 10)
        }

        gameView = SkiJumpView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        
        // Initialiser la position du skieur
        initializeGame()
    }
    
    private fun initializeGame() {
        speed = 0f
        jumpDistance = 0f
        jumpHeight = 0f
        pitch = 0f
        roll = 0f
        yaw = 0f
        approachTime = 0f
        flightTime = 0f
        backgroundOffset = 0f
        takeoffTiming = 0f
        gameState = GameState.APPROACH
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

        val x = event.values[0] // Roulis (gauche/droite)
        val y = event.values[1] // Tangage (avant/arrière)
        val z = event.values[2] // Lacet (rotation)

        when (gameState) {
            GameState.APPROACH -> handleApproach(x, y, z)
            GameState.TAKEOFF -> handleTakeoff(x, y, z)
            GameState.FLIGHT -> handleFlight(x, y, z)
            GameState.LANDING -> handleLanding(x, y, z)
            GameState.FINISHED -> {} // Rien à faire
        }

        updateStatus()
        gameView.invalidate()
    }
    
    private fun handleApproach(x: Float, y: Float, z: Float) {
        approachTime += 0.05f
        
        // Incliner vers l'avant pour accélérer
        if (y < -0.5f) {
            speed += 2f
            backgroundOffset -= 5f
        }
        
        // Équilibre latéral
        if (abs(x) > 0.3f) {
            speed -= 0.5f // Perte de vitesse si on zigzague
        }
        
        // Limiter la vitesse maximale
        speed = speed.coerceIn(0f, maxSpeed)
        
        // Passer au décollage après 4 secondes ou si vitesse élevée
        if (approachTime >= 4f || speed >= 80f) {
            gameState = GameState.TAKEOFF
            takeoffTiming = 0f
        }
    }
    
    private fun handleTakeoff(x: Float, y: Float, z: Float) {
        takeoffTiming += 0.05f
        
        // Détecter le mouvement de "redressement" pour le saut
        if (y > 1.0f && takeoffTiming < 1.5f) {
            // Bon timing de décollage
            takeoffAngle = speed / maxSpeed * 45f // Angle basé sur la vitesse
            jumpHeight = speed * 0.3f
            jumpDistance = speed * 1.2f
            gameState = GameState.FLIGHT
            flightTime = 0f
            generateWindDisturbances()
        } else if (takeoffTiming >= 1.5f) {
            // Décollage automatique si pas de mouvement
            takeoffAngle = speed / maxSpeed * 30f // Angle moins bon
            jumpHeight = speed * 0.2f
            jumpDistance = speed * 1.0f
            gameState = GameState.FLIGHT
            flightTime = 0f
            generateWindDisturbances()
        }
    }
    
    private fun handleFlight(x: Float, y: Float, z: Float) {
        flightTime += 0.05f
        
        // Ajouter les perturbations du vent
        pitch += windPitch * 0.02f
        roll += windRoll * 0.02f
        yaw += windYaw * 0.02f
        
        // Contrôle du joueur pour corriger
        pitch += y * 0.03f
        roll += x * 0.03f
        yaw += z * 0.03f
        
        // Limiter les mouvements
        pitch = pitch.coerceIn(-30f, 30f)
        roll = roll.coerceIn(-20f, 20f)
        yaw = yaw.coerceIn(-15f, 15f)
        
        // Calculer l'impact de la stabilité sur la distance
        val stability = calculateStability()
        jumpDistance += stability * 0.5f
        
        // Transition vers l'atterrissage
        if (flightTime >= totalFlightTime) {
            gameState = GameState.LANDING
        }
    }
    
    private fun handleLanding(x: Float, y: Float, z: Float) {
        // Détecter l'inclinaison vers l'avant pour préparer l'atterrissage
        if (y < -0.8f) {
            // Bon atterrissage
            jumpDistance += 10f
        }
        
        // Vérifier la stabilité lors de l'atterrissage
        if (abs(x) < 0.3f && abs(z) < 0.3f) {
            jumpDistance += 5f // Bonus stabilité
        }
        
        gameState = GameState.FINISHED
        
        if (!practiceMode) {
            tournamentData.addScore(currentPlayerIndex, eventIndex, calculateScore())
        }
        
        // Attendre 3 secondes avant de continuer
        statusText.postDelayed({
            proceedToNextPlayerOrEvent()
        }, 3000)
    }
    
    private fun generateWindDisturbances() {
        // Générer des perturbations aléatoires pour simuler le vent
        windPitch = -2f + kotlin.random.Random.nextFloat() * 4f // -2f à +2f
        windRoll = -1.5f + kotlin.random.Random.nextFloat() * 3f // -1.5f à +1.5f
        windYaw = -1f + kotlin.random.Random.nextFloat() * 2f // -1f à +1f
    }
    
    private fun calculateStability(): Float {
        // Plus les valeurs sont proches de 0, meilleure est la stabilité
        val pitchStability = 1f - (abs(pitch) / 30f)
        val rollStability = 1f - (abs(roll) / 20f)
        val yawStability = 1f - (abs(yaw) / 15f)
        
        return (pitchStability + rollStability + yawStability) / 3f
    }
    
    private fun calculateScore(): Int {
        val speedBonus = (speed / maxSpeed * 100).toInt()
        val distanceBonus = (jumpDistance * 2).toInt()
        val stabilityBonus = (calculateStability() * 50).toInt()
        val takeoffBonus = if (takeoffAngle > 35f) 30 else 0
        
        return maxOf(50, speedBonus + distanceBonus + stabilityBonus + takeoffBonus)
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
                val aiScore = generateAIScore()
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
    
    private fun generateAIScore(): Int {
        return 100 + kotlin.random.Random.nextInt(151) // 100 à 250
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    
    private fun updateStatus() {
        statusText.text = when (gameState) {
            GameState.APPROACH -> "🎿 ${tournamentData.playerNames[currentPlayerIndex]} | Élan: ${speed.toInt()} km/h (inclinez vers l'avant)"
            GameState.TAKEOFF -> "🚀 ${tournamentData.playerNames[currentPlayerIndex]} | Décollage! (redressez le téléphone)"
            GameState.FLIGHT -> "✈️ ${tournamentData.playerNames[currentPlayerIndex]} | Vol: ${jumpDistance.toInt()}m (stabilisez les 3 axes!)"
            GameState.LANDING -> "🎯 ${tournamentData.playerNames[currentPlayerIndex]} | Atterrissage (inclinez vers l'avant)"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Distance: ${jumpDistance.toInt()}m | Score: ${calculateScore()}"
        }
    }

    inner class SkiJumpView(context: Context) : View(context) {
        private val paint = Paint()
        private val skyPaint = Paint().apply { color = Color.parseColor("#87CEEB") }
        private val snowPaint = Paint().apply { color = Color.WHITE }
        private val jumpPaint = Paint().apply { color = Color.LTGRAY }

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            
            // Fond ciel
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), skyPaint)
            
            when (gameState) {
                GameState.APPROACH -> drawApproach(canvas, w, h)
                GameState.TAKEOFF -> drawTakeoff(canvas, w, h)
                GameState.FLIGHT -> drawFlight(canvas, w, h)
                GameState.LANDING -> drawLanding(canvas, w, h)
                GameState.FINISHED -> drawFinished(canvas, w, h)
            }
            
            // Affichage des informations
            drawUI(canvas, w, h)
        }
        
        private fun drawApproach(canvas: Canvas, w: Int, h: Int) {
            // Tremplin
            jumpPaint.color = Color.LTGRAY
            val startY = h * 0.8f
            val endY = h * 0.6f
            canvas.drawRect(0f, startY, w.toFloat(), h.toFloat(), jumpPaint)
            
            // Piste d'élan inclinée
            paint.color = Color.WHITE
            val path = Path()
            path.moveTo(0f, startY)
            path.lineTo(w.toFloat(), endY)
            path.lineTo(w.toFloat(), h.toFloat())
            path.lineTo(0f, h.toFloat())
            path.close()
            canvas.drawPath(path, paint)
            
            // Skieur en mouvement (simple cercle bleu pour éviter les erreurs)
            skierX = w * 0.3f + (speed / maxSpeed) * w * 0.4f
            skierY = startY - ((speed / maxSpeed) * (startY - endY))
            
            paint.color = Color.BLUE
            canvas.drawCircle(skierX, skierY - 20f, 15f, paint)
            
            // Barre de vitesse
            drawSpeedBar(canvas, w, h)
        }
        
        private fun drawTakeoff(canvas: Canvas, w: Int, h: Int) {
            // Tremplin
            jumpPaint.color = Color.LTGRAY
            canvas.drawRect(w * 0.6f, h * 0.6f, w.toFloat(), h.toFloat(), jumpPaint)
            
            // Skieur au décollage
            skierX = w * 0.7f
            skierY = h * 0.6f - 30f
            
            paint.color = Color.BLUE
            canvas.drawCircle(skierX, skierY, 15f, paint)
            
            // Indicateur de timing
            paint.color = Color.YELLOW
            paint.textSize = 24f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("REDRESSEZ MAINTENANT!", w/2f, h * 0.3f, paint)
        }
        
        private fun drawFlight(canvas: Canvas, w: Int, h: Int) {
            // Fond dégradé style 16-bit
            val gradient = LinearGradient(0f, 0f, 0f, h.toFloat(), 
                Color.parseColor("#87CEEB"), Color.parseColor("#4682B4"), Shader.TileMode.CLAMP)
            paint.shader = gradient
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
            
            // Nuages pixel art en arrière-plan
            drawPixelClouds(canvas, w, h)
            
            // Montagnes pixel style 16-bit
            drawPixelMountains(canvas, w, h)
            
            // Particules de neige animées
            drawSnowParticles(canvas, w, h)
            
            // Skieur au centre de l'écran (VUE DE HAUT)
            val centerX = w / 2f
            val centerY = h / 2f
            
            // Sauvegarder l'état du canvas pour les rotations
            canvas.save()
            canvas.translate(centerX, centerY)
            
            // Appliquer les rotations du gyroscope
            canvas.rotate(yaw * 2f) // Rotation générale
            
            // Dessiner le skieur pixel art vue de haut
            drawPixelSkierTopView(canvas, centerX, centerY)
            
            canvas.restore()
            
            // Interface utilisateur pixel style
            drawPixelUI(canvas, w, h)
        }
        
        private fun drawPixelSkierTopView(canvas: Canvas, centerX: Float, centerY: Float) {
            val skierSize = 120f // Gros skieur bien visible
            
            // Ombre du skieur (effet de profondeur)
            paint.color = Color.parseColor("#66000000")
            canvas.drawOval(-skierSize/2 + 5f, -skierSize/3 + 5f, 
                           skierSize/2 + 5f, skierSize/3 + 5f, paint)
            
            // SKIS (2 rectangles blancs avec détails)
            val skiLength = skierSize * 0.8f
            val skiWidth = 12f
            val skiSeparation = 20f + abs(roll) * 0.5f // Skis s'écartent si déséquilibre
            
            // Ski gauche
            paint.color = Color.WHITE
            canvas.save()
            canvas.rotate(pitch * 0.5f - roll * 0.3f) // Inclinaison basée sur gyroscope
            canvas.drawRect(-skiSeparation/2 - skiWidth, -skiLength/2, 
                           -skiSeparation/2, skiLength/2, paint)
            canvas.restore()
            
            // Ski droit
            paint.color = Color.WHITE
            canvas.save()
            canvas.rotate(pitch * 0.5f + roll * 0.3f)
            canvas.drawRect(skiSeparation/2, -skiLength/2, 
                           skiSeparation/2 + skiWidth, skiLength/2, paint)
            canvas.restore()
            
            // CORPS DU SKIEUR (vue de haut)
            // Tête/Casque
            paint.color = Color.parseColor("#0066CC") // Bleu électrique
            canvas.drawCircle(0f, -15f, 18f, paint)
            
            // Corps/Torse 
            paint.color = Color.parseColor("#003399") // Bleu foncé
            canvas.drawRect(-20f, -10f, 20f, 25f, paint)
            
            // Dossard jaune
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(-12f, -5f, 12f, 15f, paint)
            
            // Numéro sur le dossard
            paint.color = Color.BLACK
            paint.textSize = 16f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${currentPlayerIndex + 1}", 0f, 8f, paint)
            
            // Bras étendus
            paint.color = Color.parseColor("#0066CC")
            canvas.drawRect(-35f, -5f, -20f, 5f, paint) // Bras gauche
            canvas.drawRect(20f, -5f, 35f, 5f, paint)   // Bras droit
            
            // Effet de mouvement si déséquilibre
            if (abs(roll) > 10f || abs(pitch) > 15f) {
                paint.color = Color.parseColor("#44FF0000")
                canvas.drawCircle(0f, 0f, skierSize/2 + 10f, paint)
            } else if (abs(roll) < 5f && abs(pitch) < 8f) {
                // Halo vert si bon équilibre
                paint.color = Color.parseColor("#4400FF00")
                canvas.drawCircle(0f, 0f, skierSize/2 + 5f, paint)
            }
        }
        
        private fun drawPixelClouds(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#EEEEEE")
            
            // Nuages qui défilent selon le temps de vol
            val cloudOffset = (flightTime * 50f) % w
            
            for (i in 0..4) {
                val x = (i * w/3f - cloudOffset) % (w + 100f)
                val y = h * 0.2f + i * 30f
                
                // Nuage pixel style (plusieurs cercles)
                canvas.drawCircle(x, y, 25f, paint)
                canvas.drawCircle(x + 20f, y, 20f, paint)
                canvas.drawCircle(x + 35f, y, 15f, paint)
                canvas.drawCircle(x - 15f, y, 18f, paint)
            }
        }
        
        private fun drawPixelMountains(canvas: Canvas, w: Int, h: Int) {
            // Montagnes en arrière-plan style 16-bit
            paint.color = Color.parseColor("#8B4513")
            
            val mountainPath = Path()
            mountainPath.moveTo(0f, h * 0.8f)
            mountainPath.lineTo(w * 0.2f, h * 0.6f)
            mountainPath.lineTo(w * 0.4f, h * 0.7f)
            mountainPath.lineTo(w * 0.6f, h * 0.5f)
            mountainPath.lineTo(w * 0.8f, h * 0.65f)
            mountainPath.lineTo(w.toFloat(), h * 0.55f)
            mountainPath.lineTo(w.toFloat(), h.toFloat())
            mountainPath.lineTo(0f, h.toFloat())
            mountainPath.close()
            
            canvas.drawPath(mountainPath, paint)
            
            // Neige sur les sommets
            paint.color = Color.WHITE
            canvas.drawCircle(w * 0.2f, h * 0.6f, 15f, paint)
            canvas.drawCircle(w * 0.6f, h * 0.5f, 20f, paint)
        }
        
        private fun drawSnowParticles(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.WHITE
            
            // Particules de neige animées
            for (i in 0..20) {
                val x = (i * 50f + flightTime * 100f) % w
                val y = (i * 30f + flightTime * 80f) % h
                val size = 2f + (i % 3)
                
                canvas.drawCircle(x, y, size, paint)
            }
            
            // Effet de trainée de vent
            paint.alpha = 100
            for (i in 0..10) {
                val x = w * 0.1f + i * w * 0.08f
                val y = h/2f + sin(flightTime + i * 0.5f) * 20f
                canvas.drawCircle(x, y, 3f, paint)
            }
            paint.alpha = 255
        }
        
        private fun drawPixelUI(canvas: Canvas, w: Int, h: Int) {
            // Interface utilisateur style pixel/16-bit
            
            // Cadre de stabilité (coin bas-gauche)
            paint.color = Color.parseColor("#333333")
            canvas.drawRoundRect(20f, h - 200f, 300f, h - 20f, 10f, 10f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 18f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("STABILITÉ", 30f, h - 170f, paint)
            
            // Barres de stabilité pixel style
            val barY = h - 140f
            val barHeight = 15f
            
            // Tangage
            paint.color = if (abs(pitch) < 10f) Color.GREEN else Color.RED
            canvas.drawRect(30f, barY, 30f + abs(pitch) * 4f, barY + barHeight, paint)
            paint.color = Color.WHITE
            paint.textSize = 12f
            canvas.drawText("Tangage: ${pitch.toInt()}°", 30f, barY + 30f, paint)
            
            // Roulis  
            paint.color = if (abs(roll) < 7f) Color.GREEN else Color.RED
            canvas.drawRect(30f, barY + 40f, 30f + abs(roll) * 5f, barY + 40f + barHeight, paint)
            paint.color = Color.WHITE
            canvas.drawText("Roulis: ${roll.toInt()}°", 30f, barY + 70f, paint)
            
            // Lacet
            paint.color = if (abs(yaw) < 5f) Color.GREEN else Color.RED
            canvas.drawRect(30f, barY + 80f, 30f + abs(yaw) * 6f, barY + 80f + barHeight, paint)
            paint.color = Color.WHITE
            canvas.drawText("Lacet: ${yaw.toInt()}°", 30f, barY + 110f, paint)
            
            // Distance actuelle (coin haut-droit)
            paint.color = Color.parseColor("#FFD700")
            paint.textSize = 32f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${jumpDistance.toInt()}m", w - 30f, 60f, paint)
            
            // Indicateur de stabilité globale (centre-haut)
            val stability = calculateStability()
            paint.color = when {
                stability > 0.8f -> Color.GREEN
                stability > 0.5f -> Color.YELLOW
                else -> Color.RED
            }
            paint.textSize = 24f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("Stabilité: ${(stability * 100).toInt()}%", w/2f, 40f, paint)
        }
        
        private fun drawLanding(canvas: Canvas, w: Int, h: Int) {
            // Zone d'atterrissage
            paint.color = Color.WHITE
            canvas.drawRect(0f, h * 0.7f, w.toFloat(), h.toFloat(), paint)
            
            // Skieur à l'atterrissage
            skierX = w * 0.8f
            skierY = h * 0.7f - 20f
            
            paint.color = Color.BLUE
            canvas.drawCircle(skierX, skierY, 15f, paint)
            
            // Distance finale
            paint.color = Color.GREEN
            paint.textSize = 32f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${jumpDistance.toInt()}m", w/2f, h * 0.5f, paint)
        }
        
        private fun drawFinished(canvas: Canvas, w: Int, h: Int) {
            // Fond de victoire
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.2f, paint)
            
            // Résultats
            paint.color = Color.BLACK
            paint.textSize = 24f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🏆 RÉSULTAT FINAL 🏆", w/2f, h * 0.1f, paint)
            
            paint.textSize = 20f
            canvas.drawText("Distance: ${jumpDistance.toInt()}m", w/2f, h * 0.3f, paint)
            canvas.drawText("Stabilité: ${(calculateStability() * 100).toInt()}%", w/2f, h * 0.4f, paint)
            canvas.drawText("Score: ${calculateScore()} points", w/2f, h * 0.5f, paint)
        }
        
        private fun drawSpeedBar(canvas: Canvas, w: Int, h: Int) {
            // Barre de vitesse
            paint.color = Color.BLACK
            canvas.drawRect(50f, 50f, 250f, 80f, paint)
            
            paint.color = if (speed >= 80f) Color.GREEN else Color.YELLOW
            val speedWidth = (speed / maxSpeed) * 200f
            canvas.drawRect(50f, 50f, 50f + speedWidth, 80f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Vitesse: ${speed.toInt()} km/h", 50f, 100f, paint)
        }
        
        private fun drawStabilityIndicators(canvas: Canvas, w: Int, h: Int) {
            val indicatorY = h - 150f
            
            // Tangage (Pitch)
            paint.color = if (abs(pitch) < 10f) Color.GREEN else Color.RED
            canvas.drawRect(50f, indicatorY, 50f + abs(pitch) * 3f, indicatorY + 20f, paint)
            paint.color = Color.WHITE
            paint.textSize = 14f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("Tangage: ${pitch.toInt()}°", 50f, indicatorY + 35f, paint)
            
            // Roulis (Roll)
            paint.color = if (abs(roll) < 7f) Color.GREEN else Color.RED
            canvas.drawRect(50f, indicatorY + 50f, 50f + abs(roll) * 4f, indicatorY + 70f, paint)
            paint.color = Color.WHITE
            canvas.drawText("Roulis: ${roll.toInt()}°", 50f, indicatorY + 85f, paint)
            
            // Lacet (Yaw)
            paint.color = if (abs(yaw) < 5f) Color.GREEN else Color.RED
            canvas.drawRect(50f, indicatorY + 100f, 50f + abs(yaw) * 5f, indicatorY + 120f, paint)
            paint.color = Color.WHITE
            canvas.drawText("Lacet: ${yaw.toInt()}°", 50f, indicatorY + 135f, paint)
            
            // Stabilité globale
            val stability = calculateStability()
            paint.color = Color.CYAN
            paint.textSize = 18f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Stabilité: ${(stability * 100).toInt()}%", w - 50f, indicatorY + 60f, paint)
        }
        
        private fun drawUI(canvas: Canvas, w: Int, h: Int) {
            // Instructions selon la phase
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.textAlign = Paint.Align.CENTER
            
            val instruction = when (gameState) {
                GameState.APPROACH -> "📱 Inclinez vers l'avant pour accélérer"
                GameState.TAKEOFF -> "⬆️ Redressez le téléphone pour sauter"
                GameState.FLIGHT -> "⚖️ Stabilisez les 3 axes pour maintenir l'équilibre"
                GameState.LANDING -> "📱 Inclinez vers l'avant pour atterrir"
                GameState.FINISHED -> "🎉 Saut terminé!"
            }
            
            canvas.drawText(instruction, w/2f, 30f, paint)
        }
    }

    enum class GameState {
        APPROACH, TAKEOFF, FLIGHT, LANDING, FINISHED
    }
}
