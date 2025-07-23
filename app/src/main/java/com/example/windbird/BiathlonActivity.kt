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

class BiathlonActivity : Activity(), SensorEventListener {

    private lateinit var gameView: BiathlonView
    private lateinit var statusText: TextView

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null
    private var accelerometer: Sensor? = null

    // Variables de gameplay AMÉLIORÉES
    private var distance = 0f
    private val totalDistance = 3000f
    private var backgroundOffset = 0f
    private var currentSection = 0 // 0=Forêt, 1=Montagne, 2=Vallée, 3=Arrivée
    
    // NOUVEAU - Système de poussées rythmées
    private var pushDirection = 0 // -1=gauche, 0=neutre, 1=droite
    private var lastPushTime = 0L
    private var rhythmBonus = 1f
    private var pushCount = 0
    
    // Sprite animation
    private lateinit var spriteSheet: Bitmap
    private lateinit var leftFrame: Bitmap
    private lateinit var rightFrame: Bitmap
    private var currentFrame: Bitmap? = null
    private var animationTimer = 0L
    private var useLeftFrame = true

    // Variables de tir AMÉLIORÉES
    private var gameState = GameState.SKIING
    private var targetsHit = 0
    private var shotsFired = 0
    private var totalScore = 0
    private var crosshair = PointF(0.5f, 0.4f)
    private val targetPositions = List(5) { PointF(0.15f + it * 0.175f, 0.4f) }
    private val targetHitStatus = BooleanArray(5) { false }
    private val targetScores = IntArray(5) { 0 } // Score pour chaque cible

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
        
        loadSpriteSheet()

        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        statusText = TextView(this).apply {
            text = "🎿 ${tournamentData.playerNames[currentPlayerIndex]} | ${getSectionName()} | Distance: 0m"
            setTextColor(Color.WHITE)
            textSize = 18f
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(20, 15, 20, 15)
        }

        gameView = BiathlonView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
    }

    private fun getSectionName(): String {
        return when (currentSection) {
            0 -> "🌲 Forêt"
            1 -> "🏔️ Montagne" 
            2 -> "🏞️ Vallée"
            3 -> "🏁 Arrivée"
            else -> "Ski"
        }
    }

    private fun loadSpriteSheet() {
        try {
            spriteSheet = BitmapFactory.decodeResource(resources, R.drawable.skidefond_sprite)
            
            val totalWidth = spriteSheet.width
            val totalHeight = spriteSheet.height
            val frameWidth = (totalWidth - 5 - 5 - 5) / 2
            val frameHeight = totalHeight - 5 - 5
            
            leftFrame = Bitmap.createBitmap(spriteSheet, 5, 5, frameWidth, frameHeight)
            rightFrame = Bitmap.createBitmap(spriteSheet, 5 + frameWidth + 5, 5, frameWidth, frameHeight)
            
            val newWidth = frameWidth / 3
            val newHeight = frameHeight / 3
            
            leftFrame = Bitmap.createScaledBitmap(leftFrame, newWidth, newHeight, true)
            rightFrame = Bitmap.createScaledBitmap(rightFrame, newWidth, newHeight, true)
            
            currentFrame = leftFrame
        } catch (e: Exception) {
            val fallback = BitmapFactory.decodeResource(resources, R.drawable.skieur_pixel)
            val scaledWidth = fallback.width / 3
            val scaledHeight = fallback.height / 3
            currentFrame = Bitmap.createScaledBitmap(fallback, scaledWidth, scaledHeight, true)
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
                val x = event.values[0] // Gauche/Droite
                val z = event.values[2] // Rotation

                if (gameState == GameState.SKIING || gameState == GameState.FINAL_SKIING) {
                    handleSkiingInput(x, z)
                }
            }
            
            Sensor.TYPE_ACCELEROMETER -> {
                if (gameState == GameState.SHOOTING) {
                    val x = event.values[0]
                    val y = event.values[1]
                    
                    // Mouvement de la mire - plus sensible
                    crosshair.x += y * 0.015f
                    crosshair.y += x * 0.015f
                    
                    crosshair.x = crosshair.x.coerceIn(0.05f, 0.95f)
                    crosshair.y = crosshair.y.coerceIn(0.25f, 0.65f)
                }
            }
        }
        
        // Transitions d'état
        if (gameState == GameState.SKIING && distance >= totalDistance * 0.5f) {
            gameState = GameState.SHOOTING
        }
        
        if (gameState == GameState.SHOOTING && shotsFired >= 5) {
            statusText.postDelayed({
                gameState = GameState.FINAL_SKIING
            }, 1500)
        }
        
        if (gameState == GameState.FINAL_SKIING && distance >= totalDistance) {
            gameState = GameState.FINISHED
            
            if (!practiceMode) {
                tournamentData.addScore(currentPlayerIndex, eventIndex, calculateScore())
            }
            
            statusText.postDelayed({
                proceedToNextPlayerOrEvent()
            }, 2000)
        }

        updateStatus()
        gameView.invalidate()
    }

    // NOUVEAU - Système de ski amélioré
    private fun handleSkiingInput(tiltX: Float, rotationZ: Float) {
        val currentTime = System.currentTimeMillis()
        
        // Détection des poussées gauche/droite
        val newDirection = when {
            rotationZ > 1.5f -> 1  // Droite
            rotationZ < -1.5f -> -1 // Gauche
            else -> 0
        }
        
        // Si changement de direction (poussée)
        if (newDirection != 0 && newDirection != pushDirection) {
            val timeSinceLastPush = currentTime - lastPushTime
            
            // Calculer le bonus de rythme (optimal: 400-800ms entre poussées)
            rhythmBonus = when {
                timeSinceLastPush in 400..800 -> 1.5f // Excellent rythme
                timeSinceLastPush in 300..1000 -> 1.2f // Bon rythme
                else -> 0.8f // Rythme moins bon
            }
            
            // Avancer selon le rythme
            val advancement = 35f * rhythmBonus
            distance += advancement
            backgroundOffset -= advancement * 0.3f
            
            pushCount++
            lastPushTime = currentTime
            
            // Animation selon la direction
            currentFrame = if (newDirection == -1) leftFrame else rightFrame
            
            // Mettre à jour la section selon la distance
            val progressRatio = distance / totalDistance
            currentSection = when {
                progressRatio < 0.25f -> 0 // Forêt
                progressRatio < 0.5f -> 1  // Montagne
                progressRatio < 0.75f -> 2 // Vallée
                else -> 3 // Arrivée
            }
        }
        
        pushDirection = newDirection
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && gameState == GameState.SHOOTING && shotsFired < 5) {
            shotsFired++
            
            // Calculer le score selon la précision
            for (i in targetPositions.indices) {
                if (!targetHitStatus[i]) {
                    val dx = crosshair.x - targetPositions[i].x
                    val dy = crosshair.y - targetPositions[i].y
                    val distance = sqrt(dx * dx + dy * dy)
                    
                    val score = when {
                        distance < 0.03f -> 10 // Centre (rouge)
                        distance < 0.05f -> 8  // Cercle 2
                        distance < 0.07f -> 6  // Cercle 3
                        distance < 0.09f -> 4  // Cercle 4
                        distance < 0.11f -> 2  // Cercle 5
                        else -> 0 // Raté
                    }
                    
                    if (score > 0) {
                        targetHitStatus[i] = true
                        targetScores[i] = score
                        targetsHit++
                        totalScore += score
                        break
                    }
                }
            }
            
            updateStatus()
            gameView.invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun updateStatus() {
        statusText.text = when (gameState) {
            GameState.SKIING -> "🎿 ${tournamentData.playerNames[currentPlayerIndex]} | ${getSectionName()} | ${distance.toInt()}m/${totalDistance.toInt()}m | Rythme: ${(rhythmBonus * 100).toInt()}%"
            GameState.SHOOTING -> "🎯 ${tournamentData.playerNames[currentPlayerIndex]} | Tir ${shotsFired}/5 | Score: ${totalScore} pts"
            GameState.FINAL_SKIING -> "🏁 ${tournamentData.playerNames[currentPlayerIndex]} | ${getSectionName()} | Sprint final: ${distance.toInt()}m/${totalDistance.toInt()}m"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Score final: ${calculateScore()} points"
        }
    }

    private fun calculateScore(): Int {
        val shootingScore = totalScore
        val distanceBonus = (distance / totalDistance * 100).toInt()
        val rhythmBonus = (pushCount * 2).coerceAtMost(50) // Bonus pour les poussées
        val penaltyForMissedShots = (5 - targetsHit) * 15
        return maxOf(50, shootingScore + distanceBonus + rhythmBonus - penaltyForMissedShots)
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
        val aiAccuracy = (2..8).random()
        val aiTotalScore = (15..45).random()
        return maxOf(80, aiTotalScore + (150..200).random())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    inner class BiathlonView(context: Context) : View(context) {
        private val paint = Paint()

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            
            if (gameState == GameState.SHOOTING) {
                drawShootingRange(canvas, w, h)
            } else {
                drawSkiingSection(canvas, w, h)
            }
        }
        
        private fun drawSkiingSection(canvas: Canvas, w: Int, h: Int) {
            // Couleurs selon la section
            val (skyColor, groundColor, accentColor) = when (currentSection) {
                0 -> Triple("#87CEEB", "#F0F8FF", "#228B22") // Forêt: bleu ciel, neige, vert
                1 -> Triple("#B0C4DE", "#FFFAFA", "#696969") // Montagne: gris-bleu, neige pure, gris foncé
                2 -> Triple("#87CEFA", "#F5FFFA", "#4682B4") // Vallée: bleu clair, neige menthe, bleu acier
                3 -> Triple("#FFD700", "#FFFFFF", "#FF6347") // Arrivée: or, blanc pur, rouge tomate
                else -> Triple("#87CEEB", "#F0F8FF", "#228B22")
            }
            
            // Ciel
            paint.color = Color.parseColor(skyColor)
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.5f, paint)
            
            // Sol
            paint.color = Color.parseColor(groundColor)
            canvas.drawRect(0f, h * 0.5f, w.toFloat(), h.toFloat(), paint)
            
            // Éléments de décor selon la section
            when (currentSection) {
                0 -> drawForestDecor(canvas, w, h, accentColor)
                1 -> drawMountainDecor(canvas, w, h, accentColor)
                2 -> drawValleyDecor(canvas, w, h, accentColor)
                3 -> drawFinishDecor(canvas, w, h, accentColor)
            }
            
            // Piste principale
            paint.color = Color.parseColor("#DCDCDC")
            canvas.drawRect(0f, h * 0.75f, w.toFloat(), h.toFloat(), paint)
            
            // Skieur
            val progressRatio = distance / totalDistance
            val skierX = w * 0.1f + (progressRatio * w * 0.8f)
            val skierY = h * 0.7f
            
            currentFrame?.let { frame ->
                val destX = skierX - frame.width / 2f
                canvas.drawBitmap(frame, destX, skierY, null)
            }
            
            // Instructions GÉANTES
            if (pushCount < 3) { // Afficher au début
                paint.color = Color.BLACK
                paint.textSize = 60f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("🎿 TOURNEZ COMME UN VOLANT", w/2f, h * 0.15f, paint)
                
                paint.color = Color.RED
                paint.textSize = 50f
                canvas.drawText("+ ALTERNEZ GAUCHE-DROITE!", w/2f, h * 0.22f, paint)
            }
            
            // Barre de progression avec sections
            drawProgressBar(canvas, w, h, progressRatio)
        }
        
        private fun drawForestDecor(canvas: Canvas, w: Int, h: Int, accentColor: String) {
            paint.color = Color.parseColor(accentColor)
            // Arbres simples
            for (i in 0..8) {
                val treeX = (backgroundOffset * 0.5f + i * 100) % (w + 200) - 100
                canvas.drawRect(treeX, h * 0.35f, treeX + 15f, h * 0.5f, paint)
                canvas.drawCircle(treeX + 7.5f, h * 0.35f, 20f, paint)
            }
        }
        
        private fun drawMountainDecor(canvas: Canvas, w: Int, h: Int, accentColor: String) {
            paint.color = Color.parseColor(accentColor)
            // Montagnes simples
            for (i in 0..4) {
                val mountainX = (backgroundOffset * 0.3f + i * 200) % (w + 400) - 200
                val path = Path()
                path.moveTo(mountainX, h * 0.5f)
                path.lineTo(mountainX + 100f, h * 0.1f)
                path.lineTo(mountainX + 200f, h * 0.5f)
                path.close()
                canvas.drawPath(path, paint)
            }
        }
        
        private fun drawValleyDecor(canvas: Canvas, w: Int, h: Int, accentColor: String) {
            paint.color = Color.parseColor(accentColor)
            // Rivière gelée
            canvas.drawRect(0f, h * 0.45f, w.toFloat(), h * 0.5f, paint)
            // Quelques buissons
            for (i in 0..6) {
                val bushX = (backgroundOffset * 0.4f + i * 150) % (w + 300) - 150
                canvas.drawCircle(bushX, h * 0.45f, 15f, paint)
            }
        }
        
        private fun drawFinishDecor(canvas: Canvas, w: Int, h: Int, accentColor: String) {
            paint.color = Color.parseColor(accentColor)
            // Drapeaux d'arrivée
            for (i in 0..10) {
                val flagX = (backgroundOffset * 0.6f + i * 80) % (w + 160) - 80
                canvas.drawRect(flagX, h * 0.3f, flagX + 5f, h * 0.5f, paint)
                canvas.drawRect(flagX, h * 0.3f, flagX + 30f, h * 0.35f, paint)
            }
            
            // Ligne d'arrivée si proche de la fin
            if (distance > totalDistance * 0.9f) {
                paint.color = Color.BLACK
                paint.strokeWidth = 8f
                for (i in 0..20) {
                    val y = h * 0.75f + i * 10f
                    val color = if (i % 2 == 0) Color.BLACK else Color.WHITE
                    paint.color = color
                    canvas.drawRect(w * 0.9f, y, w.toFloat(), y + 10f, paint)
                }
            }
        }
        
        private fun drawProgressBar(canvas: Canvas, w: Int, h: Int, progress: Float) {
            // Fond de la barre
            paint.color = Color.BLACK
            canvas.drawRect(w * 0.1f, 20f, w * 0.9f, 45f, paint)
            
            // Sections colorées
            val sectionWidth = w * 0.8f / 4f
            val colors = arrayOf("#228B22", "#696969", "#4682B4", "#FF6347")
            
            for (i in 0..3) {
                paint.color = Color.parseColor(colors[i])
                val startX = w * 0.1f + i * sectionWidth
                val endX = startX + sectionWidth
                canvas.drawRect(startX, 22f, endX, 43f, paint)
            }
            
            // Progression actuelle
            paint.color = Color.YELLOW
            canvas.drawRect(w * 0.1f, 20f, w * 0.1f + (progress * w * 0.8f), 45f, paint)
            
            // Marqueurs de section
            paint.color = Color.WHITE
            paint.strokeWidth = 3f
            for (i in 1..3) {
                val x = w * 0.1f + i * sectionWidth
                canvas.drawLine(x, 20f, x, 45f, paint)
            }
        }
        
        private fun drawShootingRange(canvas: Canvas, w: Int, h: Int) {
            // Fond sombre
            paint.color = Color.parseColor("#1a1a2e")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            
            // Instructions GÉANTES
            paint.color = Color.WHITE
            paint.textSize = 70f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🎯 ZONE DE TIR 🎯", w/2f, 80f, paint)
            
            paint.textSize = 40f
            paint.color = Color.YELLOW
            canvas.drawText("TÉLÉPHONE À PLAT", w/2f, 130f, paint)
            canvas.drawText("VISEZ LE CENTRE ROUGE!", w/2f, 170f, paint)
            
            if (shotsFired >= 5) {
                paint.color = Color.GREEN
                paint.textSize = 50f
                canvas.drawText("TIR TERMINÉ - Sprint final...", w/2f, 220f, paint)
            }
            
            // Cibles avec cercles concentriques
            for (i in targetPositions.indices) {
                val px = targetPositions[i].x * w
                val py = targetPositions[i].y * h + 100
                
                drawTarget(canvas, px, py, i)
            }
            
            // Mire
            drawCrosshair(canvas, w, h)
            
            // Interface
            drawShootingUI(canvas, w, h)
        }
        
        private fun drawTarget(canvas: Canvas, px: Float, py: Float, index: Int) {
            if (targetHitStatus[index]) {
                // Cible touchée
                paint.color = Color.parseColor("#00aa00")
                canvas.drawCircle(px, py, 50f, paint)
                
                // Afficher le score
                paint.color = Color.WHITE
                paint.textSize = 30f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("+${targetScores[index]}", px, py + 10f, paint)
                
                paint.textSize = 20f
                canvas.drawText("TOUCHÉ", px, py + 70f, paint)
            } else {
                // Cible intacte avec cercles concentriques
                // Cercle 5 (extérieur) - 2 points
                paint.color = Color.parseColor("#FFFFFF")
                canvas.drawCircle(px, py, 50f, paint)
                
                // Cercle 4 - 4 points  
                paint.color = Color.parseColor("#000000")
                canvas.drawCircle(px, py, 40f, paint)
                
                // Cercle 3 - 6 points
                paint.color = Color.parseColor("#FFFFFF")
                canvas.drawCircle(px, py, 30f, paint)
                
                // Cercle 2 - 8 points
                paint.color = Color.parseColor("#000000")
                canvas.drawCircle(px, py, 20f, paint)
                
                // Centre - 10 points
                paint.color = Color.parseColor("#FF0000")
                canvas.drawCircle(px, py, 10f, paint)
                
                paint.color = Color.WHITE
                paint.textSize = 16f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("${index + 1}", px, py + 70f, paint)
            }
        }
        
        private fun drawCrosshair(canvas: Canvas, w: Int, h: Int) {
            val crossX = crosshair.x * w
            val crossY = crosshair.y * h + 100
            
            // Croix noire épaisse
            paint.color = Color.BLACK
            paint.strokeWidth = 6f
            canvas.drawLine(crossX - 25, crossY, crossX + 25, crossY, paint)
            canvas.drawLine(crossX, crossY - 25, crossX, crossY + 25, paint)
            
            // Croix rouge fine
            paint.color = Color.RED
            paint.strokeWidth = 2f
            canvas.drawLine(crossX - 20, crossY, crossX + 20, crossY, paint)
            canvas.drawLine(crossX, crossY - 20, crossX, crossY + 20, paint)
            
            // Point central
            paint.style = Paint.Style.FILL
            paint.color = Color.YELLOW
            canvas.drawCircle(crossX, crossY, 3f, paint)
        }
        
        private fun drawShootingUI(canvas: Canvas, w: Int, h: Int) {
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            paint.textSize = 30f
            paint.textAlign = Paint.Align.LEFT
            canvas.drawText("🔫 Munitions: ${5-shotsFired}/5", 30f, h - 100f, paint)
            canvas.drawText("🎯 Score: $totalScore pts", 30f, h - 60f, paint)
            
            // Munitions restantes
            for (i in 0 until 5) {
                paint.color = if (i < shotsFired) Color.GRAY else Color.YELLOW
                canvas.drawRect(w - 250f + i * 40f, h - 70f, w - 220f + i * 40f, h - 40f, paint)
            }
        }
    }

    enum class GameState {
        SKIING, SHOOTING, FINAL_SKIING, FINISHED
    }
}
