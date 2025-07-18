package com.example.windbird

import android.app.Activity
import android.content.Context
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
    private lateinit var scoreText: TextView
    private lateinit var fireButton: Button
    private lateinit var uiLayout: LinearLayout
    
    private lateinit var game: BiathlonGame
    private lateinit var gameHandler: Handler
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
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
        
        // Vue du jeu
        gameView = BiathlonView(this)
        gameView.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        mainLayout.addView(gameView)
        
        // Interface utilisateur
        uiLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#003366"))
            setPadding(20, 10, 20, 10)
        }
        
        // Instructions
        instructionText = TextView(this).apply {
            text = "Inclinez pour viser - Stabilisez pour tirer"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = android.view.Gravity.CENTER
        }
        uiLayout.addView(instructionText)
        
        // Score
        scoreText = TextView(this).apply {
            text = "Score: 0 | Cibles: 5"
            setTextColor(Color.YELLOW)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        }
        uiLayout.addView(scoreText)
        
        // Bouton de tir
        fireButton = Button(this).apply {
            text = "TIRER"
            setBackgroundColor(Color.parseColor("#ff4444"))
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setOnClickListener { game.fire() }
        }
        uiLayout.addView(fireButton)
        
        // Bouton retour
        val backButton = Button(this).apply {
            text = "RETOUR"
            setBackgroundColor(Color.parseColor("#666666"))
            setTextColor(Color.WHITE)
            setOnClickListener { finish() }
        }
        uiLayout.addView(backButton)
        
        mainLayout.addView(uiLayout)
        setContentView(mainLayout)
        
        // Démarrer le jeu
        startGame()
    }
    
    private fun startGame() {
        game.start()
        gameHandler.post(gameLoop)
    }
    
    private val gameLoop = object : Runnable {
        override fun run() {
            game.update()
            gameView.invalidate()
            updateUI()
            
            if (!game.isGameOver()) {
                gameHandler.postDelayed(this, 16) // ~60 FPS
            } else {
                endGame()
            }
        }
    }
    
    private fun updateUI() {
        scoreText.text = "Score: ${game.getScore()} | Cibles: ${game.getTargetsRemaining()}"
        
        if (game.isAiming()) {
            instructionText.text = "Visez avec le gyroscope - Stabilisez pour tirer"
            fireButton.isEnabled = game.isStable()
            fireButton.setBackgroundColor(
                if (game.isStable()) Color.parseColor("#44ff44") 
                else Color.parseColor("#ff4444")
            )
        } else {
            instructionText.text = "Préparez-vous pour la prochaine cible..."
            fireButton.isEnabled = false
            fireButton.setBackgroundColor(Color.parseColor("#666666"))
        }
    }
    
    private fun endGame() {
        instructionText.text = "Terminé! Score final: ${game.getScore()}"
        fireButton.text = "REJOUER"
        fireButton.isEnabled = true
        fireButton.setBackgroundColor(Color.parseColor("#4444ff"))
        fireButton.setOnClickListener { restartGame() }
    }
    
    private fun restartGame() {
        game.reset()
        fireButton.text = "TIRER"
        fireButton.setOnClickListener { game.fire() }
        startGame()
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
    
    // Vue personnalisée pour le rendu du jeu
    private inner class BiathlonView(context: Context) : View(context) {
        private val paint = Paint().apply { isAntiAlias = true }
        private val targetPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        private val crosshairPaint = Paint().apply {
            isAntiAlias = true
            color = Color.RED
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        private val backgroundPaint = Paint().apply {
            color = Color.parseColor("#87CEEB") // Bleu ciel
        }
        
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            
            val width = width
            val height = height
            
            // Arrière-plan (ciel d'hiver)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
            
            // Montagnes en arrière-plan
            drawMountains(canvas, width, height)
            
            // Cible
            game.getCurrentTarget()?.let { target ->
                drawTarget(canvas, target, width, height)
            }
            
            // Réticule (crosshair)
            if (game.isAiming()) {
                drawCrosshair(canvas, game.getCrosshairPosition(), width, height)
            }
            
            // Indicateur de stabilité
            drawStabilityIndicator(canvas, width, height)
        }
        
        private fun drawMountains(canvas: Canvas, width: Int, height: Int) {
            paint.color = Color.parseColor("#4A4A4A")
            
            val mountainPath = Path().apply {
                moveTo(0f, height * 0.7f)
                lineTo(width * 0.3f, height * 0.3f)
                lineTo(width * 0.6f, height * 0.5f)
                lineTo(width * 0.9f, height * 0.2f)
                lineTo(width.toFloat(), height * 0.4f)
                lineTo(width.toFloat(), height.toFloat())
                lineTo(0f, height.toFloat())
                close()
            }
            
            canvas.drawPath(mountainPath, paint)
        }
        
        private fun drawTarget(canvas: Canvas, target: BiathlonGame.Target, width: Int, height: Int) {
            val x = target.x * width
            val y = target.y * height
            val radius = target.radius * minOf(width, height)
            
            // Cible avec anneaux
            paint.color = Color.WHITE
            canvas.drawCircle(x, y, radius, paint)
            
            paint.color = Color.BLACK
            canvas.drawCircle(x, y, radius * 0.8f, paint)
            
            paint.color = Color.BLUE
            canvas.drawCircle(x, y, radius * 0.6f, paint)
            
            paint.color = Color.RED
            canvas.drawCircle(x, y, radius * 0.4f, paint)
            
            paint.color = Color.YELLOW
            canvas.drawCircle(x, y, radius * 0.2f, paint)
            
            // Contour
            paint.apply {
                color = Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawCircle(x, y, radius, paint)
            paint.style = Paint.Style.FILL
        }
        
        private fun drawCrosshair(canvas: Canvas, pos: BiathlonGame.Position, width: Int, height: Int) {
            val x = pos.x * width
            val y = pos.y * height
            val size = 20f
            
            // Réticule en croix
            canvas.drawLine(x - size, y, x + size, y, crosshairPaint)
            canvas.drawLine(x, y - size, x, y + size, crosshairPaint)
            
            // Cercle central
            crosshairPaint.style = Paint.Style.STROKE
            canvas.drawCircle(x, y, size / 2, crosshairPaint)
            crosshairPaint.style = Paint.Style.FILL
        }
        
        private fun drawStabilityIndicator(canvas: Canvas, width: Int, height: Int) {
            val stability = game.getStability()
            
            // Barre de stabilité
            val barWidth = width * 0.2f
            val barHeight = 20f
            val barX = width - barWidth - 20
            val barY = 20f
            
            // Arrière-plan de la barre
            paint.color = Color.GRAY
            canvas.drawRect(barX, barY, barX + barWidth, barY + barHeight, paint)
            
            // Niveau de stabilité
            paint.color = when {
                stability > 0.8f -> Color.GREEN
                stability > 0.5f -> Color.YELLOW
                else -> Color.RED
            }
            canvas.drawRect(barX, barY, barX + barWidth * stability, barY + barHeight, paint)
            
            // Texte
            paint.apply {
                color = Color.WHITE
                textSize = 16f
            }
            canvas.drawText("Stabilité", barX, barY - 5, paint)
        }
    }
    
    // Logique du jeu de biathlon
    private inner class BiathlonGame {
        private val targets = mutableListOf<Target>()
        private var currentTarget: Target? = null
        private val crosshairPosition = Position(0.5f, 0.5f)
        private var score = 0
        private var targetIndex = 0
        private var isAiming = false
        private var gameOver = false
        private var stability = 0f
        private val gyroBuffer = FloatArray(10)
        private var gyroBufferIndex = 0
        
        fun start() {
            generateTargets()
            nextTarget()
        }
        
        fun reset() {
            targets.clear()
            score = 0
            targetIndex = 0
            isAiming = false
            gameOver = false
            stability = 0f
            gyroBufferIndex = 0
        }
        
        private fun generateTargets() {
            repeat(5) {
                val x = 0.2f + Random.nextFloat() * 0.6f
                val y = 0.2f + Random.nextFloat() * 0.6f
                val radius = 0.05f + Random.nextFloat() * 0.03f
                targets.add(Target(x, y, radius))
            }
        }
        
        private fun nextTarget() {
            if (targetIndex < targets.size) {
                currentTarget = targets[targetIndex]
                isAiming = true
                
                // Repositionner le réticule aléatoirement
                crosshairPosition.x = 0.3f + Random.nextFloat() * 0.4f
                crosshairPosition.y = 0.3f + Random.nextFloat() * 0.4f
            } else {
                gameOver = true
            }
        }
        
        fun updateGyroscope(x: Float, y: Float, z: Float) {
            if (!isAiming) return
            
            // Mettre à jour la position du réticule basée sur le gyroscope
            crosshairPosition.x += x * 0.01f
            crosshairPosition.y += y * 0.01f
            
            // Limiter dans les bornes
            crosshairPosition.x = crosshairPosition.x.coerceIn(0.1f, 0.9f)
            crosshairPosition.y = crosshairPosition.y.coerceIn(0.1f, 0.9f)
            
            // Calculer la stabilité
            val gyroMagnitude = sqrt(x*x + y*y + z*z)
            gyroBuffer[gyroBufferIndex] = gyroMagnitude
            gyroBufferIndex = (gyroBufferIndex + 1) % gyroBuffer.size
            
            calculateStability()
        }
        
        private fun calculateStability() {
            val average = gyroBuffer.average().toFloat()
            // La stabilité est inversement proportionnelle au mouvement
            stability = (1 - average * 2).coerceAtLeast(0f)
        }
        
        fun fire() {
            if (!isAiming || !isStable()) return
            
            currentTarget?.let { target ->
                // Calculer la distance au centre de la cible
                val dx = crosshairPosition.x - target.x
                val dy = crosshairPosition.y - target.y
                val distance = sqrt(dx*dx + dy*dy)
                
                // Calculer le score basé sur la précision
                var hitScore = when {
                    distance <= target.radius * 0.2f -> 100 // Centre
                    distance <= target.radius * 0.4f -> 80  // Jaune
                    distance <= target.radius * 0.6f -> 60  // Rouge
                    distance <= target.radius * 0.8f -> 40  // Bleu
                    distance <= target.radius -> 20         // Blanc
                    else -> 0
                }
                
                // Bonus de stabilité
                hitScore = (hitScore * stability).roundToInt()
                score += hitScore
                
                // Cible suivante
                targetIndex++
                isAiming = false
                
                // Petit délai avant la prochaine cible
                gameHandler.postDelayed({ nextTarget() }, 1000)
            }
        }
        
        fun update() {
            // Mise à jour du jeu (animations, etc.)
        }
        
        // Getters
        fun getCurrentTarget() = currentTarget
        fun getCrosshairPosition() = crosshairPosition
        fun getScore() = score
        fun getTargetsRemaining() = targets.size - targetIndex
        fun isAiming() = isAiming
        fun isGameOver() = gameOver
        fun getStability() = stability
        fun isStable() = stability > 0.7f
        
        // Classes internes
        inner class Target(val x: Float, val y: Float, val radius: Float)
        inner class Position(var x: Float, var y: Float)
    }
}
