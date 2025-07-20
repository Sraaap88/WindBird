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
    private val preparationDuration = 6f // RÉDUIT de 8f
    private val approachDuration = 13f // RÉDUIT de 15f
    private val takeoffDuration = 8f
    private val jumpDuration = 3f // NOUVELLE PHASE
    private val flightDuration = 12f
    private val landingDuration = 5f
    private val resultsDuration = 8f
    
    // Variables de jeu
    private var speed = 0f
    private var maxSpeed = 80f // RÉDUIT de 100f
    private var takeoffPower = 0f
    private var jumpDistance = 0f
    private var stability = 1f
    private var landingBonus = 0f
    
    // NOUVEAU: Variables pour le vent
    private var windDirection = 0f // -1 = gauche, +1 = droite, 0 = pas de vent
    private var windStrength = 0f // Force du vent 0-1
    private var windTimer = 0f
    
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
            textSize = 30f // AUGMENTÉ de 22f
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(35, 30, 35, 30) // AUGMENTÉ
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
        windDirection = 0f
        windStrength = 0f
        windTimer = 0f
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
            GameState.JUMP -> handleJump()
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
        // Incliner vers l'avant (téléphone penché vers soi) pour accélérer - CORRIGÉ
        if (tiltY > 0.2f) { // INVERSÉ - pencher vers soi = positif
            speed += 1.0f // AUGMENTÉ pour progression plus rapide
        } else if (tiltY < -0.2f) { // Pencher vers l'arrière = freinage
            speed -= 0.6f
        }
        
        // Pénalité pour mouvement latéral - MOINS SENSIBLE
        if (abs(tiltX) > 0.6f) {
            speed -= 0.2f
        }
        
        speed = speed.coerceIn(0f, maxSpeed)
        
        if (phaseTimer >= approachDuration) {
            gameState = GameState.TAKEOFF
            phaseTimer = 0f
            cameraShake = 0.5f
        }
    }
    
    private fun handleTakeoff() {
        // Redresser le téléphone pour puissance de saut - LOGIQUE INVERSÉE
        if (tiltY < -0.3f) { // Pencher téléphone vers l'avant (loin de soi) = saut
            takeoffPower += 2.5f // AUGMENTÉ pour meilleure réactivité
        }
        
        takeoffPower = takeoffPower.coerceIn(0f, 100f)
        
        if (phaseTimer >= takeoffDuration) {
            // Calculer distance de base
            jumpDistance = (speed * 1.2f) + (takeoffPower * 0.8f)
            gameState = GameState.JUMP // NOUVELLE PHASE
            phaseTimer = 0f
            cameraShake = 1f
        }
    }
    
    private fun handleJump() {
        // Phase de transition - le skieur quitte le tremplin
        if (phaseTimer >= jumpDuration) {
            gameState = GameState.FLIGHT
            phaseTimer = 0f
            generateMoreSnowParticles()
            generateWind() // Générer le vent pour la phase de vol
        }
    }
    
    private fun handleFlight() {
        // Gestion du vent - change toutes les 2 secondes
        windTimer += 0.025f
        if (windTimer > 2f) {
            generateWind()
            windTimer = 0f
        }
        
        // Calculer la position idéale pour compenser le vent
        val idealTiltX = -windDirection * windStrength * 0.5f // Compenser dans le sens opposé
        
        // Stabilité critique - compenser le vent
        val tiltXError = abs(tiltX - idealTiltX)
        val tiltYError = abs(tiltY)
        val tiltZError = abs(tiltZ)
        
        val currentStability = 1f - (tiltXError + tiltYError + tiltZError) / 3f
        stability = (stability * 0.9f + currentStability.coerceIn(0f, 1f) * 0.1f)
        
        // Bonus distance pour stabilité
        jumpDistance += stability * 0.3f
        
        if (phaseTimer >= flightDuration) {
            gameState = GameState.LANDING
            phaseTimer = 0f
            cameraShake = 1f
        }
    }
    
    private fun handleLanding() {
        // Atterrissage plus clair - pencher légèrement vers l'avant
        if (tiltY > 0.1f && tiltY < 0.5f && abs(tiltX) < 0.3f) {
            landingBonus += 1.0f // AUGMENTÉ pour récompenser
        } else {
            landingBonus -= 0.5f // Pénalité pour mauvais atterrissage
        }
        
        landingBonus = landingBonus.coerceIn(0f, 30f) // Limiter le bonus
        
        if (phaseTimer >= landingDuration) {
            jumpDistance += landingBonus
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
    
    private fun generateWind() {
        // Générer vent aléatoire
        windDirection = (kotlin.random.Random.nextFloat() - 0.5f) * 2f // -1 à +1
        windStrength = 0.3f + kotlin.random.Random.nextFloat() * 0.7f // 0.3 à 1.0
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
            GameState.TAKEOFF -> "🚀 ${tournamentData.playerNames[currentPlayerIndex]} | PENCHEZ VERS L'AVANT! Puissance: ${takeoffPower.toInt()}% | ${(takeoffDuration - phaseTimer).toInt() + 1}s"
            GameState.JUMP -> "🛫 ${tournamentData.playerNames[currentPlayerIndex]} | ENVOL! Puissance: ${takeoffPower.toInt()}%"
            GameState.FLIGHT -> "✈️ ${tournamentData.playerNames[currentPlayerIndex]} | Vol: ${jumpDistance.toInt()}m | Stabilité: ${(stability * 100).toInt()}% | ${(flightDuration - phaseTimer).toInt() + 1}s"
            GameState.LANDING -> "🎯 ${tournamentData.playerNames[currentPlayerIndex]} | Atterrissage! | ${(landingDuration - phaseTimer).toInt() + 1}s"
            GameState.RESULTS -> "🏆 ${tournamentData.playerNames[currentPlayerIndex]} | Distance finale: ${jumpDistance.toInt()}m | Score: ${finalScore}"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Saut terminé!"
        }
    }

    // Fonction pour obtenir l'emoji du drapeau selon le pays
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
            else -> "🏴" // Drapeau générique pour pays non reconnus
        }
    }

    inner class SkiJumpView(context: Context) : View(context) {
        private val paint = Paint()
        
        // Variables pour les images du skieur
        private var skierBitmap: Bitmap? = null
        private var skierJumpBitmap: Bitmap? = null
        private var skierFlightBitmap: Bitmap? = null
        private var skierLand1Bitmap: Bitmap? = null // NOUVEAU: Approche atterrissage
        private var skierLand2Bitmap: Bitmap? = null // NOUVEAU: Impact atterrissage
        private var skierLand3Bitmap: Bitmap? = null // NOUVEAU: Satisfaction
        
        init {
            // Charger les images du skieur
            try {
                skierBitmap = BitmapFactory.decodeResource(resources, R.drawable.skier_approach)
                skierJumpBitmap = BitmapFactory.decodeResource(resources, R.drawable.skier_jump)
                skierFlightBitmap = BitmapFactory.decodeResource(resources, R.drawable.skier_flight)
                skierLand1Bitmap = BitmapFactory.decodeResource(resources, R.drawable.skier_land1)
                skierLand2Bitmap = BitmapFactory.decodeResource(resources, R.drawable.skier_land2)
                skierLand3Bitmap = BitmapFactory.decodeResource(resources, R.drawable.skier_land3)
            } catch (e: Exception) {
                // Si le chargement échoue, créer un bitmap de substitution
                createFallbackSkierBitmaps()
            }
        }
        
        private fun createFallbackSkierBitmaps() {
            // Créer un bitmap de substitution 60x80 pixels pour l'approche
            skierBitmap = Bitmap.createBitmap(60, 80, Bitmap.Config.ARGB_8888)
            val canvas1 = Canvas(skierBitmap!!)
            val tempPaint = Paint().apply {
                color = Color.parseColor("#FF4444")
                style = Paint.Style.FILL
            }
            
            // Dessiner un skieur simple (rectangle + cercle pour la tête)
            canvas1.drawRect(20f, 20f, 40f, 60f, tempPaint) // Corps
            canvas1.drawCircle(30f, 15f, 10f, tempPaint) // Tête
            
            // Skis
            tempPaint.color = Color.YELLOW
            canvas1.drawRect(15f, 55f, 18f, 75f, tempPaint) // Ski gauche
            canvas1.drawRect(42f, 55f, 45f, 75f, tempPaint) // Ski droit
            
            // Créer bitmap pour le saut (horizontal)
            skierJumpBitmap = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888)
            val canvas2 = Canvas(skierJumpBitmap!!)
            tempPaint.color = Color.parseColor("#FF4444")
            canvas2.drawRect(20f, 20f, 80f, 40f, tempPaint) // Corps horizontal
            canvas2.drawCircle(15f, 30f, 10f, tempPaint) // Tête
            
            // Créer bitmap pour le vol (aérodynamique)
            skierFlightBitmap = Bitmap.createBitmap(120, 50, Bitmap.Config.ARGB_8888)
            val canvas3 = Canvas(skierFlightBitmap!!)
            tempPaint.color = Color.parseColor("#FF4444")
            canvas3.drawRect(30f, 15f, 90f, 35f, tempPaint) // Corps allongé
            canvas3.drawCircle(25f, 25f, 10f, tempPaint) // Tête
            
            // Créer bitmaps pour l'atterrissage
            skierLand1Bitmap = Bitmap.createBitmap(80, 70, Bitmap.Config.ARGB_8888)
            val canvas4 = Canvas(skierLand1Bitmap!!)
            tempPaint.color = Color.parseColor("#FF4444")
            canvas4.drawRect(20f, 30f, 60f, 50f, tempPaint) // Corps penché
            canvas4.drawCircle(15f, 35f, 10f, tempPaint) // Tête
            
            skierLand2Bitmap = Bitmap.createBitmap(90, 80, Bitmap.Config.ARGB_8888)
            val canvas5 = Canvas(skierLand2Bitmap!!)
            canvas5.drawRect(25f, 40f, 65f, 70f, tempPaint) // Corps accroupi
            canvas5.drawCircle(45f, 30f, 10f, tempPaint) // Tête
            
            skierLand3Bitmap = Bitmap.createBitmap(70, 90, Bitmap.Config.ARGB_8888)
            val canvas6 = Canvas(skierLand3Bitmap!!)
            canvas6.drawRect(25f, 30f, 45f, 70f, tempPaint) // Corps droit
            canvas6.drawCircle(35f, 20f, 10f, tempPaint) // Tête
            // Bras levés
            canvas6.drawCircle(15f, 25f, 5f, tempPaint) // Bras gauche
            canvas6.drawCircle(55f, 25f, 5f, tempPaint) // Bras droit
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
                GameState.JUMP -> drawJump(canvas, w, h)
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
            
            // Arbres sur les côtés
            drawTrees(canvas, w, h)
            
            // Foule qui applaudit
            drawCrowd(canvas, w, h)
            
            // Drapeau du pays du joueur actuel - ÉNORME
            val playerCountry = tournamentData.playerCountries[currentPlayerIndex]
            val flag = getCountryFlag(playerCountry)
            
            paint.color = Color.WHITE
            paint.textSize = 180f // ÉNORME - était 100f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(flag, w/2f, h * 0.18f, paint)
            
            // Nom du pays sous le drapeau - PLUS GROS
            paint.textSize = 48f // AUGMENTÉ de 36f
            canvas.drawText(playerCountry.uppercase(), w/2f, h * 0.25f, paint)
            
            // Instructions centrales - TEXTE PLUS GROS
            paint.textSize = 56f // AUGMENTÉ de 44f
            canvas.drawText("🎿 SAUT À SKI 🎿", w/2f, h * 0.35f, paint)
            
            paint.textSize = 40f // AUGMENTÉ de 32f
            canvas.drawText("Préparez-vous...", w/2f, h * 0.42f, paint)
            
            paint.textSize = 36f // AUGMENTÉ de 28f
            paint.color = Color.YELLOW
            canvas.drawText("Dans ${(preparationDuration - phaseTimer).toInt() + 1} secondes", w/2f, h * 0.5f, paint)
            
            paint.textSize = 32f // AUGMENTÉ de 28f
            paint.color = Color.CYAN
            canvas.drawText("📱 Penchez vers VOUS pour accélérer", w/2f, h * 0.7f, paint)
            canvas.drawText("📱 Penchez vers l'AVANT au signal", w/2f, h * 0.75f, paint)
            canvas.drawText("📱 Compensez le vent en vol", w/2f, h * 0.8f, paint)
        }
        
        private fun drawTrees(canvas: Canvas, w: Int, h: Int) {
            paint.color = Color.parseColor("#228B22") // Vert sapin
            
            // Arbres à gauche - PLUS GROS
            for (i in 1..3) {
                val treeX = w * 0.1f
                val treeY = h * (0.4f + i * 0.15f)
                drawTree(canvas, treeX, treeY, 60f) // DOUBLÉ de 30f
            }
            
            // Arbres à droite - PLUS GROS
            for (i in 1..3) {
                val treeX = w * 0.9f
                val treeY = h * (0.4f + i * 0.15f)
                drawTree(canvas, treeX, treeY, 60f) // DOUBLÉ de 30f
            }
        }
        
        private fun drawTree(canvas: Canvas, x: Float, y: Float, size: Float) {
            // Tronc - PLUS GROS
            paint.color = Color.parseColor("#8B4513")
            canvas.drawRect(x - size/4, y, x + size/4, y + size/2, paint) // PLUS LARGE
            
            // Feuillage (triangle) - PLUS GROS
            paint.color = Color.parseColor("#228B22")
            val path = Path()
            path.moveTo(x, y - size/2)
            path.lineTo(x - size/1.5f, y) // PLUS LARGE
            path.lineTo(x + size/1.5f, y)
            path.close()
            canvas.drawPath(path, paint)
        }
        
        private fun drawCrowd(canvas: Canvas, w: Int, h: Int) {
            // Foule plus nombreuse - AUGMENTÉE
            paint.color = Color.parseColor("#444444")
            
            for (i in 1..15) { // AUGMENTÉ de 8 à 15
                val crowdX = w * 0.15f + i * (w * 0.7f / 15f) // ÉTALÉ sur plus de largeur
                val crowdY = h * 0.9f
                
                // Tête - PLUS GROSSE
                canvas.drawCircle(crowdX, crowdY - 30f, 15f, paint)
                // Corps - PLUS GROS
                canvas.drawRect(crowdX - 12f, crowdY - 15f, crowdX + 12f, crowdY, paint)
                
                // Bras levés (applaudissements) - PLUS GROS
                if (i % 2 == 0) {
                    canvas.drawCircle(crowdX - 20f, crowdY - 40f, 8f, paint) // Bras gauche
                    canvas.drawCircle(crowdX + 20f, crowdY - 40f, 8f, paint) // Bras droit
                }
            }
        }
        
        private fun drawApproach(canvas: Canvas, w: Int, h: Int) {
            // VUE DE HAUT - Tremplin en perspective
            paint.color = Color.parseColor("#87CEEB")
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Tremplin en perspective (vue de haut) - INVERSÉ POUR LOGIQUE
            paint.color = Color.WHITE
            val jumpPath = Path()
            jumpPath.moveTo(w * 0.45f, h * 0.9f)  // Bas (départ) - plus large
            jumpPath.lineTo(w * 0.55f, h * 0.9f)
            jumpPath.lineTo(w * 0.48f, h * 0.1f)  // Haut (arrivée) - plus étroit
            jumpPath.lineTo(w * 0.52f, h * 0.1f)
            jumpPath.close()
            canvas.drawPath(jumpPath, paint)
            
            // Lignes de vitesse sur les côtés - PLUS LENTES
            paint.color = Color.parseColor("#CCCCCC")
            paint.strokeWidth = 3f // PLUS ÉPAIS
            paint.style = Paint.Style.STROKE
            for (i in 1..10) {
                val lineY = h * 0.9f - (i * h * 0.07f) + (phaseTimer * 15f) % (h * 0.07f)
                canvas.drawLine(w * 0.45f, lineY, w * 0.55f, lineY, paint)
            }
            
            // Réinitialiser le style
            paint.style = Paint.Style.FILL
            
            // NOUVEAU: Skieur remonte le tremplin selon la vitesse
            val speedProgress = if (maxSpeed > 0) speed / maxSpeed else 0f
            val timeProgress = phaseTimer / approachDuration
            
            // Position basée sur la vitesse accumulée et le temps
            val combinedProgress = (speedProgress * 0.7f + timeProgress * 0.3f).coerceIn(0f, 1f)
            
            // Le skieur commence en bas (0.85) et remonte vers le haut (0.15)
            val skierY = h * (0.85f - combinedProgress * 0.7f)
            val skierX = w / 2f
            
            // Taille qui diminue BEAUCOUP en remontant (effet de perspective fort)
            val scale = 0.8f - combinedProgress * 0.6f  // Commence à 0.8, finit à 0.2 (très petit)
            
            skierBitmap?.let { bmp ->
                val dstRect = RectF(
                    skierX - bmp.width * scale / 2f,
                    skierY - bmp.height * scale / 2f,
                    skierX + bmp.width * scale / 2f,
                    skierY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            // Barre de vitesse ÉNORME
            drawSpeedMeter(canvas, w, h)
            
            // Instructions - TEXTE PLUS GROS
            paint.color = Color.WHITE
            paint.textSize = 36f // AUGMENTÉ de 28f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("📱 PENCHEZ LE TÉLÉPHONE VERS VOUS", w/2f, 70f, paint)
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
            
            // Skieur au moment du décollage
            val skierX = w * 0.85f
            val skierY = h * 0.4f
            val scale = 2.0f
            
            skierBitmap?.let { bmp ->
                val dstRect = RectF(
                    skierX - bmp.width * scale / 2f,
                    skierY - bmp.height * scale / 2f,
                    skierX + bmp.width * scale / 2f,
                    skierY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            // Effet de ralenti avec trails
            paint.alpha = 100
            for (i in 1..5) {
                skierBitmap?.let { bmp ->
                    val trailRect = RectF(
                        skierX - bmp.width * scale / 2f - i * 15f,
                        skierY - bmp.height * scale / 2f,
                        skierX + bmp.width * scale / 2f - i * 15f,
                        skierY + bmp.height * scale / 2f
                    )
                    canvas.drawBitmap(bmp, null, trailRect, paint)
                }
            }
            paint.alpha = 255
            
            // Barre de puissance de décollage ÉNORME
            drawTakeoffPowerMeter(canvas, w, h)
            
            // Instructions dramatiques - TEXTE PLUS GROS
            paint.color = Color.YELLOW
            paint.textSize = 48f // AUGMENTÉ de 36f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🚀 PENCHEZ VERS L'AVANT! 🚀", w/2f, h * 0.2f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 36f // AUGMENTÉ de 28f
            canvas.drawText("📱 TÉLÉPHONE LOIN DE VOUS", w/2f, h * 0.25f, paint)
        }
        
        private fun drawJump(canvas: Canvas, w: Int, h: Int) {
            // VUE DE PROFIL DROIT - Moment dramatique du saut
            paint.color = Color.parseColor("#87CEEB")
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Tremplin en vue de profil avec fin de rampe
            paint.color = Color.WHITE
            val rampPath = Path()
            rampPath.moveTo(0f, h * 0.8f)
            rampPath.quadTo(w * 0.4f, h * 0.6f, w * 0.6f, h * 0.4f) // Rampe courbée
            rampPath.lineTo(w * 0.65f, h * 0.45f)
            rampPath.lineTo(w * 0.65f, h.toFloat())
            rampPath.lineTo(0f, h.toFloat())
            rampPath.close()
            canvas.drawPath(rampPath, paint)
            
            // Animation du skieur qui s'élève
            val jumpProgress = phaseTimer / jumpDuration
            
            // Position: part du bout du tremplin et s'élève en arc
            val startX = w * 0.6f
            val startY = h * 0.4f
            
            val skierX = startX + jumpProgress * w * 0.3f // Se déplace vers la droite
            val skierY = startY - jumpProgress * h * 0.2f + (jumpProgress * jumpProgress) * h * 0.1f // Arc parabolique
            
            // Rotation légère du skieur en fonction de takeoffPower
            val rotation = (takeoffPower / 100f) * 15f - 7.5f // -7.5° à +7.5°
            
            canvas.save()
            canvas.translate(skierX, skierY)
            canvas.rotate(rotation)
            
            val scale = 2.0f
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
            
            // Particules de neige projetées du tremplin
            paint.color = Color.WHITE
            paint.alpha = 180
            for (i in 1..5) {
                val particleX = startX + kotlin.random.Random.nextFloat() * 50f
                val particleY = startY + kotlin.random.Random.nextFloat() * 30f
                canvas.drawCircle(particleX, particleY, 8f, paint)
            }
            paint.alpha = 255
            
            // Instructions - TEXTE PLUS GROS
            paint.color = Color.YELLOW
            paint.textSize = 48f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🚀 ENVOL! 🚀", w/2f, h * 0.15f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 32f
            canvas.drawText("Puissance: ${takeoffPower.toInt()}%", w/2f, h * 0.2f, paint)
        }
            
        private fun drawFlight(canvas: Canvas, w: Int, h: Int) {
            // VUE LATÉRALE - Skieur qui traverse l'écran de gauche à droite
            paint.color = Color.parseColor("#87CEEB")
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Montagnes qui défilent - PLUS LENT
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
            
            // Progression du skieur de gauche à droite
            val flightProgress = phaseTimer / flightDuration
            val skierX = w * (-0.1f + flightProgress * 1.2f) // De -10% à 110% de l'écran
            val baseY = h * 0.4f
            
            // Position verticale selon l'angle du téléphone (contrôle de l'attitude)
            val verticalOffset = tiltY * 100f // Pencher avant/arrière = monter/descendre
            val skierY = baseY + verticalOffset
            
            canvas.save()
            canvas.translate(skierX, skierY)
            
            // Rotation du skieur selon l'angle du téléphone pour feedback visuel
            val skierRotation = tiltY * 20f // L'angle suit l'inclinaison
            canvas.rotate(skierRotation)
            
            // Compensation du vent affecte aussi la rotation
            val windRotation = windDirection * windStrength * 10f
            canvas.rotate(windRotation)
            
            val scale = 2.5f // Taille cohérente avec le reste
            
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
            
            // Trail de particules derrière le skieur
            paint.color = Color.WHITE
            paint.alpha = 100
            for (i in 1..3) {
                val trailX = skierX - i * 30f
                val trailY = skierY + kotlin.random.Random.nextFloat() * 20f - 10f
                canvas.drawCircle(trailX, trailY, 6f, paint)
            }
            paint.alpha = 255
            
            // Indicateurs de vent ÉNORMES et visibles
            drawWindIndicator(canvas, w, h)
            
            // Indicateurs de stabilité ÉNORMES
            drawStabilityIndicators(canvas, w, h)
            
            // Instructions - TEXTE PLUS GROS
            paint.color = Color.WHITE
            paint.textSize = 40f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("⚖️ COMPENSEZ LE VENT! ⚖️", w/2f, 70f, paint)
            
            // Indication de contrôle d'attitude
            paint.textSize = 28f
            paint.color = Color.CYAN
            canvas.drawText("📱 Avant/Arrière = Angle de vol", w/2f, h - 60f, paint)
        }
        }
        
        private fun drawLanding(canvas: Canvas, w: Int, h: Int) {
            // VUE DE PROFIL - Atterrissage cinématique
            paint.color = Color.parseColor("#87CEEB")
            paint.style = Paint.Style.FILL
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Piste d'atterrissage en vue de profil (pente descendante)
            paint.color = Color.WHITE
            val landingPath = Path()
            landingPath.moveTo(0f, h * 0.7f)
            landingPath.lineTo(w.toFloat(), h * 0.8f) // Pente légère
            landingPath.lineTo(w.toFloat(), h.toFloat())
            landingPath.lineTo(0f, h.toFloat())
            landingPath.close()
            canvas.drawPath(landingPath, paint)
            
            // Marques de distance en vue de profil - TEXTE PLUS GROS
            paint.color = Color.parseColor("#666666")
            paint.textSize = 28f // AUGMENTÉ
            paint.textAlign = Paint.Align.CENTER
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            for (i in 1..5) {
                val markX = w * 0.1f + i * (w * 0.8f / 5f)
                val markY = h * 0.7f + (markX / w) * (h * 0.1f) // Suit la pente
                canvas.drawLine(markX, markY, markX, markY + 15f, paint)
                canvas.drawText("${i * 25}m", markX, markY + 35f, paint)
            }
            paint.style = Paint.Style.FILL
            
            // Position du skieur selon la distance de saut
            val landingProgress = phaseTimer / landingDuration
            val baseSkierX = w * 0.1f + (jumpDistance / 150f) * (w * 0.8f) // Position selon distance
            val skierY = h * 0.7f + (baseSkierX / w) * (h * 0.1f) - 60f // Sur la pente, légèrement au-dessus
            
            // Glissement après l'atterrissage
            val slideDistance = if (landingProgress > 0.4f) (landingProgress - 0.4f) * 100f else 0f
            val skierX = baseSkierX + slideDistance
            
            val scale = 2.2f
            
            // Séquence d'atterrissage en 3 phases
            val currentBitmap = when {
                landingProgress < 0.4f -> skierLand1Bitmap // 0-2s: Approche
                landingProgress < 0.6f -> skierLand2Bitmap // 2-3s: Impact
                else -> skierLand3Bitmap // 3-5s: Satisfaction
            }
            
            currentBitmap?.let { bmp ->
                val dstRect = RectF(
                    skierX - bmp.width * scale / 2f,
                    skierY - bmp.height * scale / 2f,
                    skierX + bmp.width * scale / 2f,
                    skierY + bmp.height * scale / 2f
                )
                canvas.drawBitmap(bmp, null, dstRect, paint)
            }
            
            // Explosion de neige à l'impact (seulement pendant la phase d'impact)
            if (landingProgress >= 0.4f && landingProgress < 0.7f) {
                paint.color = Color.WHITE
                paint.alpha = 150
                for (i in 1..12) { // Plus de particules
                    val angle = i * 30f
                    val particleX = skierX + cos(Math.toRadians(angle.toDouble())).toFloat() * 60f
                    val particleY = skierY + sin(Math.toRadians(angle.toDouble())).toFloat() * 30f
                    canvas.drawCircle(particleX, particleY, 18f, paint) // Plus grosses
                }
                paint.alpha = 255
            }
            
            // Distance atteinte ÉNORME - TEXTE PLUS GROS
            paint.color = Color.YELLOW
            paint.textSize = 84f // ENCORE PLUS GROS
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${jumpDistance.toInt()}m", w/2f, h * 0.2f, paint)
            
            // Instructions d'atterrissage ÉNORMES et visibles
            paint.color = Color.WHITE
            paint.textSize = 44f // ÉNORME
            
            val instruction = when {
                landingProgress < 0.4f -> "📱 PRÉPAREZ L'ATTERRISSAGE"
                landingProgress < 0.6f -> "📱 PENCHEZ LÉGÈREMENT VERS VOUS"
                else -> "🎉 EXCELLENT SAUT!"
            }
            canvas.drawText(instruction, w/2f, h * 0.3f, paint)
            
            // Bonus atterrissage en temps réel
            paint.textSize = 36f
            paint.color = if (landingBonus > 10f) Color.GREEN else if (landingBonus > 5f) Color.YELLOW else Color.RED
            canvas.drawText("Bonus atterrissage: +${landingBonus.toInt()}", w/2f, h * 0.35f, paint)
            
            // Indication de phase
            paint.textSize = 28f
            paint.color = Color.CYAN
            val phaseText = when {
                landingProgress < 0.4f -> "Phase: Approche"
                landingProgress < 0.6f -> "Phase: Impact"
                else -> "Phase: Célébration"
            }
            canvas.drawText(phaseText, w/2f, h * 0.4f, paint)
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
            paint.textSize = 32f // AUGMENTÉ de 28f
            canvas.drawText("🎿 Distance: ${jumpDistance.toInt()}m", w/2f, h * 0.5f, paint)
            canvas.drawText("⚡ Vitesse max: ${speed.toInt()} km/h", w/2f, h * 0.55f, paint)
            canvas.drawText("⚖️ Stabilité: ${(stability * 100).toInt()}%", w/2f, h * 0.6f, paint)
            canvas.drawText("🎯 Atterrissage: ${landingBonus.toInt()} bonus", w/2f, h * 0.65f, paint)
            
            // Effet d'étoiles
            paint.color = Color.YELLOW
            for (i in 1..10) {
                val starX = kotlin.random.Random.nextFloat() * w
                val starY = kotlin.random.Random.nextFloat() * h * 0.4f
                drawStar(canvas, starX, starY, 12f) // PLUS GROS
            }
        }
        
        private fun drawSpeedMeter(canvas: Canvas, w: Int, h: Int) {
            // Barre de vitesse énorme sur le côté droit - PLUS GROSSE
            paint.color = Color.parseColor("#333333")
            paint.style = Paint.Style.FILL
            canvas.drawRect(w - 110f, 140f, w - 30f, h - 140f, paint) // PLUS LARGE
            
            paint.color = Color.GREEN
            val speedHeight = (speed / maxSpeed) * (h - 280f) // ADAPTÉ
            canvas.drawRect(w - 105f, h - 140f - speedHeight, w - 35f, h - 140f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 28f // AUGMENTÉ de 24f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("VITESSE", w - 70f, 120f, paint)
            canvas.drawText("${speed.toInt()}", w - 70f, h - 90f, paint)
            canvas.drawText("km/h", w - 70f, h - 60f, paint)
        }
        
        private fun drawTakeoffPowerMeter(canvas: Canvas, w: Int, h: Int) {
            // Barre de puissance de décollage énorme - PLUS GROSSE
            paint.color = Color.parseColor("#333333")
            paint.style = Paint.Style.FILL
            canvas.drawRect(140f, h - 120f, w - 140f, h - 30f, paint) // PLUS HAUTE
            
            paint.color = if (takeoffPower > 70f) Color.GREEN else if (takeoffPower > 40f) Color.YELLOW else Color.RED
            val powerWidth = (takeoffPower / 100f) * (w - 280f) // ADAPTÉ
            canvas.drawRect(140f, h - 115f, 140f + powerWidth, h - 35f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 28f // AUGMENTÉ de 24f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("PUISSANCE DE DÉCOLLAGE: ${takeoffPower.toInt()}%", w/2f, h - 130f, paint)
        }
        
        private fun drawWindIndicator(canvas: Canvas, w: Int, h: Int) {
            // Indicateur de vent ÉNORME en haut à droite
            val windX = w - 150f
            val windY = 150f
            
            // Fond de l'indicateur
            paint.color = Color.parseColor("#333333")
            paint.style = Paint.Style.FILL
            canvas.drawRect(windX - 80f, windY - 60f, windX + 80f, windY + 60f, paint)
            
            // Direction du vent avec flèche
            paint.color = Color.YELLOW
            paint.textSize = 48f
            paint.textAlign = Paint.Align.CENTER
            
            val windText = if (windDirection < -0.1f) "⬅️" else if (windDirection > 0.1f) "➡️" else "⏸️"
            canvas.drawText(windText, windX, windY - 10f, paint)
            
            // Force du vent
            paint.textSize = 24f
            paint.color = Color.WHITE
            canvas.drawText("VENT", windX, windY - 35f, paint)
            canvas.drawText("${(windStrength * 100).toInt()}%", windX, windY + 25f, paint)
            
            // Instructions de compensation
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
            val baseY = h - 220f // PLUS BAS
            
            // Calculer la position idéale selon le vent
            val idealTiltX = -windDirection * windStrength * 0.5f
            val tiltXError = abs(tiltX - idealTiltX)
            
            // Indicateur global de stabilité - ÉNORME
            paint.color = Color.parseColor("#333333")
            paint.style = Paint.Style.FILL
            canvas.drawRect(80f, baseY, 340f, baseY + 60f, paint) // PLUS GROS
            
            paint.color = if (stability > 0.8f) Color.GREEN else if (stability > 0.5f) Color.YELLOW else Color.RED
            canvas.drawRect(80f, baseY, 80f + stability * 260f, baseY + 60f, paint)
            
            paint.color = Color.WHITE
            paint.textSize = 30f // AUGMENTÉ de 26f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("STABILITÉ: ${(stability * 100).toInt()}%", 80f, baseY - 20f, paint)
            
            // Indicateurs détaillés - TEXTE PLUS GROS avec compensation vent
            paint.textSize = 24f // AUGMENTÉ de 20f
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
        PREPARATION, APPROACH, TAKEOFF, JUMP, FLIGHT, LANDING, RESULTS, FINISHED
    }
}
