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
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.ViewGroup
import kotlin.math.*
import kotlin.random.Random

class BiathlonActivity : Activity(), SensorEventListener {
    
    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null
    private lateinit var gameView: BiathlonView
    private lateinit var instructionText: TextView
    private lateinit var statusText: TextView
    private lateinit var fireButton: Button
    private lateinit var uiLayout: LinearLayout
    
    private lateinit var game: BiathlonGame
    private lateinit var gameHandler: Handler
    private lateinit var tournamentData: TournamentData
    private var eventIndex = 0
    private var numberOfPlayers = 1
    private var currentPlayerIndex = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        tournamentData = intent.getSerializableExtra("tournament_data") as TournamentData
        eventIndex = intent.getIntExtra("event_index", 0)
        numberOfPlayers = intent.getIntExtra("number_of_players", 1)
        currentPlayerIndex = tournamentData.getNextPlayer(eventIndex)
        
        setupSensors()
        setupGame()
        setupUI()
    }
    
    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }
    
    private fun setupGame() {
        game = BiathlonGame()
        gameHandler = Handler(Looper.getMainLooper())
    }
    
    private fun setupUI() {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#001122"))
        }
        
        gameView = BiathlonView(this)
        gameView.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        mainLayout.addView(gameView)
        
        uiLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#003366"))
            setPadding(15, 10, 15, 10)
        }
        
        val playerText = TextView(this).apply {
            text = "🎿 ${tournamentData.playerNames[currentPlayerIndex]} (${tournamentData.playerCountries[currentPlayerIndex]})"
            setTextColor(Color.YELLOW)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        }
        uiLayout.addView(playerText)
        
        instructionText = TextView(this).apply {
            text = "Inclinez pour skier - Préparez-vous au départ !"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = android.view.Gravity.CENTER
        }
        uiLayout.addView(instructionText)
        
        statusText = TextView(this).apply {
            text = "Distance: 0m | Temps: 0:00 | Cibles: 0/10"
            setTextColor(Color.CYAN)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        }
        uiLayout.addView(statusText)
        
        fireButton = Button(this).apply {
            text = "🎯 TIRER"
            setBackgroundColor(Color.parseColor("#ff4444"))
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            visibility = View.GONE
            setOnClickListener { game.fire() }
        }
        uiLayout.addView(fireButton)
        
        val controlLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        
        val startButton = Button(this).apply {
            text = "🏁 DÉPART"
            setBackgroundColor(Color.parseColor("#44ff44"))
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 5, 0)
            }
            setOnClickListener { startRace() }
        }
        controlLayout.addView(startButton)
        
        val backButton = Button(this).apply {
            text = "↩️ RETOUR"
            setBackgroundColor(Color.parseColor("#666666"))
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(5, 0, 0, 0)
            }
            setOnClickListener { finish() }
        }
        controlLayout.addView(backButton)
        
        uiLayout.addView(controlLayout)
        mainLayout.addView(uiLayout)
        setContentView(mainLayout)
        
        prepareRace()
    }
    
    private fun prepareRace() {
        if (currentPlayerIndex >= numberOfPlayers) {
            instructionText.text = "L'IA ${tournamentData.playerNames[currentPlayerIndex]} est en cours..."
            runAIRace()
        } else {
            instructionText.text = "Prêt à commencer ? Utilisez le gyroscope pour diriger !"
        }
    }
    
    private fun startRace() {
        game.start()
        gameHandler.post(gameLoop)
        instructionText.text = "Skiez vers la première zone de tir !"
    }
    
    private fun runAIRace() {
        gameHandler.postDelayed({
            val aiScore = Random.nextInt(600, 900)
            finishRace(aiScore)
        }, 3000)
    }
    
    private val gameLoop = object : Runnable {
        override fun run() {
            game.update()
            gameView.invalidate()
            updateUI()
            
            if (!game.isRaceFinished()) {
                gameHandler.postDelayed(this, 16)
            } else {
                finishRace(game.getFinalScore())
            }
        }
    }
    
    private fun updateUI() {
        val distance = game.getDistance()
        val time = game.getTime()
        val minutes = (time / 60000).toInt()
        val seconds = ((time % 60000) / 1000).toInt()
        val targets = game.getTargetsHit()
        
        statusText.text = "Distance: ${distance}m | Temps: $minutes:${seconds.toString().padStart(2, '0')} | Cibles: $targets/10"
        
        when (game.getGameState()) {
            GameState.SKIING -> {
                instructionText.text = "Inclinez pour diriger - Vitesse: ${game.getSpeed().toInt()} km/h"
                fireButton.visibility = View.GONE
            }
            GameState.SHOOTING -> {
                instructionText.text = "ZONE DE TIR - Stabilisez et tirez sur les cibles !"
                fireButton.visibility = View.VISIBLE
                fireButton.isEnabled = game.isStable()
                fireButton.setBackgroundColor(
                    if (game.isStable()) Color.parseColor("#44ff44") 
                    else Color.parseColor("#ff4444")
                )
            }
            GameState.PENALTY -> {
                instructionText.text = "TOUR DE PÉNALITÉ - Vous avez raté des cibles !"
                fireButton.visibility = View.GONE
            }
        }
    }
    
    private fun finishRace(finalScore: Int) {
        tournamentData.addScore(currentPlayerIndex, eventIndex, finalScore)
        
        val nextPlayer = tournamentData.getNextPlayer(eventIndex)
        
        if (nextPlayer != -1) {
            currentPlayerIndex = nextPlayer
            game.reset()
            prepareRace()
            setupUI()
        } else {
            val resultIntent = Intent().apply {
                putExtra("tournament_data", tournamentData)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }
    
    override fun onResume() {
        super.onResume()
        gyroscope?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }
    
    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        gameHandler.removeCallbacksAndMessages(null)
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            game.updateGyroscope(event.values[0], event.values[1], event.values[2])
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    
    private inner class BiathlonView(context: Context) : View(context) {
        private val paint = Paint().apply { isAntiAlias = true }
        private val backgroundPaint = Paint().apply { color = Color.parseColor("#87CEEB") }
        
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            
            val width = width.toFloat()
            val height = height.toFloat()
            
            canvas.drawRect(0f, 0f, width, height, backgroundPaint)
            
            drawWinterLandscape(canvas, width, height)
            drawSkiTrack(canvas, width, height)
            drawShootingZones(canvas, width, height)
            drawPlayer(canvas, width, height)
            
            if (game.getGameState() == GameState.SHOOTING) {
                drawTargets(canvas, width, height)
                drawCrosshair(canvas, width, height)
            }
            
            drawMinimap(canvas, width, height)
        }
        
        private fun drawWinterLandscape(canvas: Canvas, width: Float, height: Float) {
            paint.color = Color.parseColor("#4A4A4A")
            val mountainPath = Path().apply {
                moveTo(0f, height * 0.6f)
                lineTo(width * 0.2f, height * 0.3f)
                lineTo(width * 0.4f, height * 0.4f)
                lineTo(width * 0.6f, height * 0.2f)
                lineTo(width * 0.8f, height * 0.35f)
                lineTo(width, height * 0.25f)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            canvas.drawPath(mountainPath, paint)
            
            paint.color = Color.parseColor("#2d5016")
            for (i in 0..20) {
                val x = Random.nextFloat() * width
                val y = height * 0.6f + Random.nextFloat() * height * 0.3f
                canvas.drawCircle(x, y, 5f + Random.nextFloat() * 10f, paint)
            }
        }
        
        private fun drawSkiTrack(canvas: Canvas, width: Float, height: Float) {
            val trackY = height * 0.7f
            
            paint.color = Color.parseColor("#E6E6FA")
            paint.strokeWidth = 20f
            canvas.drawLine(0f, trackY, width, trackY, paint)
            
            paint.color = Color.parseColor("#4169E1")
            paint.strokeWidth = 2f
            paint.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
            canvas.drawLine(0f, trackY, width, trackY, paint)
            paint.pathEffect = null
        }
        
        private fun drawShootingZones(canvas: Canvas, width: Float, height: Float) {
            val zone1X = width * 0.33f
            paint.color = Color.parseColor("#FFD700")
            canvas.drawRect(zone1X - 30f, height * 0.65f, zone1X + 30f, height * 0.75f, paint)
            
            val zone2X = width * 0.66f
            canvas.drawRect(zone2X - 30f, height * 0.65f, zone2X + 30f, height * 0.75f, paint)
            
            paint.color = Color.BLACK
            paint.textSize = 12f
            canvas.drawText("TIR 1", zone1X - 15f, height * 0.63f, paint)
            canvas.drawText("TIR 2", zone2X - 15f, height * 0.63f, paint)
        }
        
        private fun drawPlayer(canvas: Canvas, width: Float, height: Float) {
            val playerX = width * game.getProgress()
            val playerY = height * 0.7f + game.getOffset() * 50f
            
            paint.color = Color.RED
            canvas.drawCircle(playerX, playerY - 15f, 8f, paint)
            canvas.drawRect(playerX - 4f, playerY - 15f, playerX + 4f, playerY + 5f, paint)
            
            paint.color = Color.parseColor("#8B4513")
            paint.strokeWidth = 3f
            canvas.drawLine(playerX - 15f, playerY + 5f, playerX + 15f, playerY + 5f, paint)
            
            paint.color = Color.BLACK
            paint.strokeWidth = 2f
            canvas.drawLine(playerX - 10f, playerY - 10f, playerX - 15f, playerY + 5f, paint)
            canvas.drawLine(playerX + 10f, playerY - 10f, playerX + 15f, playerY + 5f, paint)
        }
        
        private fun drawTargets(canvas: Canvas, width: Float, height: Float) {
            val targets = game.getCurrentTargets()
            for (i in targets.indices) {
                val target = targets[i]
                val x = width * 0.2f + i * (width * 0.15f)
                val y = height * 0.4f
                
                paint.color = if (target.hit) Color.GREEN else Color.WHITE
                canvas.drawCircle(x, y, 20f, paint)
                
                paint.color = Color.BLACK
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                canvas.drawCircle(x, y, 20f, paint)
                canvas.drawCircle(x, y, 10f, paint)
                paint.style = Paint.Style.FILL
                
                paint.color = Color.BLACK
                paint.textSize = 12f
                canvas.drawText("${i + 1}", x - 5f, y + 5f, paint)
            }
        }
        
        private fun drawCrosshair(canvas: Canvas, width: Float, height: Float) {
            val pos = game.getCrosshairPosition()
            val x = pos.x * width
            val y = pos.y * height
            
            paint.color = Color.RED
            paint.strokeWidth = 3f
            canvas.drawLine(x - 15f, y, x + 15f, y, paint)
            canvas.drawLine(x, y - 15f, x, y + 15f, paint)
            
            paint.style = Paint.Style.STROKE
            canvas.drawCircle(x, y, 10f, paint)
            paint.style = Paint.Style.FILL
        }
        
        private fun drawMinimap(canvas: Canvas, width: Float, height: Float) {
            val mapWidth = width * 0.3f
            val mapHeight = 30f
            val mapX = width - mapWidth - 10f
            val mapY = 10f
            
            paint.color = Color.parseColor("#000080")
            canvas.drawRect(mapX, mapY, mapX + mapWidth, mapY + mapHeight, paint)
            
            paint.color = Color.YELLOW
            canvas.drawRect(mapX, mapY, mapX + mapWidth * game.getProgress(), mapY + mapHeight, paint)
            
            paint.color = Color.RED
            canvas.drawRect(mapX + mapWidth * 0.33f - 2f, mapY, mapX + mapWidth * 0.33f + 2f, mapY + mapHeight, paint)
            canvas.drawRect(mapX + mapWidth * 0.66f - 2f, mapY, mapX + mapWidth * 0.66f + 2f, mapY + mapHeight, paint)
            
            paint.color = Color.WHITE
            val playerMapX = mapX + mapWidth * game.getProgress()
            canvas.drawCircle(playerMapX, mapY + mapHeight / 2, 3f, paint)
        }
    }
    
    private inner class BiathlonGame {
        private var gameState = GameState.SKIING
        private var distance = 0f
        private var totalDistance = 5000f
        private var speed = 0f
        private var startTime = 0L
        private var currentTime = 0L
        private var playerOffset = 0f
        private var stability = 0f
        private val gyroBuffer = FloatArray(10)
        private var gyroBufferIndex = 0
        
        private var currentShootingZone = 0
        private var shotsInZone = 0
        private var targetsHit = 0
        private var penaltyLaps = 0
        private var inPenalty = false
        
        private val currentTargets = Array(5) { Target() }
        private val crosshairPosition = Position(0.5f, 0.4f)
        
        private var raceFinished = false
        
        fun start() {
            gameState = GameState.SKIING
            distance = 0f
            speed = 20f
            startTime = System.currentTimeMillis()
            currentTime = startTime
            targetsHit = 0
            penaltyLaps = 0
            currentShootingZone = 0
            raceFinished = false
            
            resetTargets()
        }
        
        fun reset() {
            raceFinished = false
            gameState = GameState.SKIING
            distance = 0f
            targetsHit = 0
            penaltyLaps = 0
        }
        
        fun update() {
            if (raceFinished) return
            
            currentTime = System.currentTimeMillis()
            val deltaTime = 16f / 1000f
            
            when (gameState) {
                GameState.SKIING -> updateSkiing(deltaTime)
                GameState.SHOOTING -> updateShooting(deltaTime)
                GameState.PENALTY -> updatePenalty(deltaTime)
            }
            
            if (distance >= totalDistance) {
                raceFinished = true
            }
        }
        
        private fun updateSkiing(deltaTime: Float) {
            distance += speed * deltaTime * (1000f / 3600f)
            speed = (15f + abs(playerOffset) * 10f).coerceIn(10f, 35f)
            
            val progress = distance / totalDistance
            if ((progress >= 0.33f && currentShootingZone == 0) || 
                (progress >= 0.66f && currentShootingZone == 1)) {
                enterShootingZone()
            }
        }
        
        private fun updateShooting(deltaTime: Float) {
            // Attendre le tir du joueur
        }
        
        private fun updatePenalty(deltaTime: Float) {
            if (penaltyLaps > 0) {
                penaltyLaps--
                if (penaltyLaps == 0) {
                    gameState = GameState.SKIING
                    inPenalty = false
                }
            }
        }
        
        private fun enterShootingZone() {
            gameState = GameState.SHOOTING
            shotsInZone = 0
            resetTargets()
            crosshairPosition.x = 0.5f
            crosshairPosition.y = 0.4f
        }
        
        private fun resetTargets() {
            for (i in currentTargets.indices) {
                currentTargets[i].hit = false
                currentTargets[i].x = 0.2f + i * 0.15f
                currentTargets[i].y = 0.4f
            }
        }
        
        fun updateGyroscope(x: Float, y: Float, z: Float) {
            when (gameState) {
                GameState.SKIING -> {
                    playerOffset += y * 0.02f
                    playerOffset = playerOffset.coerceIn(-1f, 1f)
                }
                GameState.SHOOTING -> {
                    crosshairPosition.x += y * 0.01f
                    crosshairPosition.y += x * 0.01f
                    
                    crosshairPosition.x = crosshairPosition.x.coerceIn(0.1f, 0.9f)
                    crosshairPosition.y = crosshairPosition.y.coerceIn(0.2f, 0.6f)
                }
                else -> {}
            }
            
            val gyroMagnitude = sqrt(x*x + y*y + z*z)
            gyroBuffer[gyroBufferIndex] = gyroMagnitude
            gyroBufferIndex = (gyroBufferIndex + 1) % gyroBuffer.size
            calculateStability()
        }
        
        private fun calculateStability() {
            val average = gyroBuffer.average().toFloat()
            stability = (1 - average * 2).coerceAtLeast(0f)
        }
        
        fun fire() {
            if (gameState != GameState.SHOOTING || shotsInZone >= 5) return
            
            var closestTarget = -1
            var closestDistance = Float.MAX_VALUE
            
            for (i in currentTargets.indices) {
                if (!currentTargets[i].hit) {
                    val dx = crosshairPosition.x - currentTargets[i].x
                    val dy = crosshairPosition.y - currentTargets[i].y
                    val distance = sqrt(dx*dx + dy*dy)
                    
                    if (distance < closestDistance) {
                        closestDistance = distance
                        closestTarget = i
                    }
                }
            }
            
            shotsInZone++
            
            if (closestTarget != -1 && closestDistance < 0.05f && stability > 0.6f) {
                currentTargets[closestTarget].hit = true
                targetsHit++
            }
            
            if (shotsInZone >= 5) {
                exitShootingZone()
            }
        }
        
        private fun exitShootingZone() {
            currentShootingZone++
            
            val missedTargets = 5 - currentTargets.count { it.hit }
            penaltyLaps += missedTargets * 30
            
            if (penaltyLaps > 0) {
                gameState = GameState.PENALTY
                inPenalty = true
            } else {
                gameState = GameState.SKIING
            }
            
            resetTargets()
        }
        
        fun getFinalScore(): Int {
            val timeBonus = maxOf(0, 600 - (getTime() / 1000).toInt())
            val accuracyBonus = targetsHit * 50
            val penaltyMalus = penaltyLaps * 10
            
            return maxOf(100, timeBonus + accuracyBonus - penaltyMalus)
        }
        
        fun getGameState() = gameState
        fun getDistance() = distance.toInt()
        fun getProgress() = (distance / totalDistance).coerceIn(0f, 1f)
        fun getTime() = currentTime - startTime
        fun getSpeed() = speed
        fun getOffset() = playerOffset
        fun getTargetsHit() = targetsHit
        fun getCurrentTargets() = currentTargets
        fun getCrosshairPosition() = crosshairPosition
        fun getStability() = stability
        fun isStable() = stability > 0.7f
        fun isRaceFinished() = raceFinished
        
        inner class Target {
            var hit = false
            var x = 0f
            var y = 0f
        }
        
        inner class Position(var x: Float, var y: Float)
    }
    
    enum class GameState {
        SKIING, SHOOTING, PENALTY
    }
}
