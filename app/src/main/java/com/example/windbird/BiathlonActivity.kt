// BiathlonActivity.kt — version avec animation sprite basée sur skidefond_sprite.png
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

    private var playerOffset = 0f
    private var distance = 0f
    private val totalDistance = 3000f
    private var previousGyroDirection = 0
    private var backgroundOffset = 0f

    // MODIFIÉ : Sprite sheet avec 2 frames d'animation
    private lateinit var spriteSheet: Bitmap
    private lateinit var leftFrame: Bitmap
    private lateinit var rightFrame: Bitmap
    private var currentFrame: Bitmap? = null
    private var animationTimer = 0L
    private var useLeftFrame = true

    private var gameState = GameState.SKIING
    private var targetsHit = 0
    private var shotsFired = 0
    private var crosshair = PointF(0.5f, 0.4f)
    private val targetPositions = List(5) { PointF(0.2f + it * 0.15f, 0.4f) }
    private val targetHitStatus = BooleanArray(5) { false }

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
        
        // MODIFIÉ : Charger et découper le sprite sheet
        loadSpriteSheet()

        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        statusText = TextView(this).apply {
            text = "Joueur: ${tournamentData.playerNames[currentPlayerIndex]} | Distance: 0m"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(20, 10, 20, 10)
        }

        gameView = BiathlonView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
    }

    // AJOUTÉ : Fonction pour charger et découper le sprite sheet
    private fun loadSpriteSheet() {
        try {
            spriteSheet = BitmapFactory.decodeResource(resources, R.drawable.skidefond_sprite)
            
            // CORRIGÉ : Calculer les dimensions en tenant compte des bordures
            // Cadre de 5px + séparation de 5px entre les images
            val totalWidth = spriteSheet.width
            val totalHeight = spriteSheet.height
            
            // Largeur de chaque frame = (largeur totale - cadre gauche - cadre droit - séparation) / 2
            val frameWidth = (totalWidth - 5 - 5 - 5) / 2  // -15px au total
            val frameHeight = totalHeight - 5 - 5  // -10px (cadre haut + bas)
            
            // Découper en enlevant les bordures
            leftFrame = Bitmap.createBitmap(spriteSheet, 5, 5, frameWidth, frameHeight)
            rightFrame = Bitmap.createBitmap(spriteSheet, 5 + frameWidth + 5, 5, frameWidth, frameHeight)
            
            // AJOUTÉ : Redimensionner à 1/3 de la taille
            val newWidth = frameWidth / 3
            val newHeight = frameHeight / 3
            
            leftFrame = Bitmap.createScaledBitmap(leftFrame, newWidth, newHeight, true)
            rightFrame = Bitmap.createScaledBitmap(rightFrame, newWidth, newHeight, true)
            
            // Frame par défaut
            currentFrame = leftFrame
        } catch (e: Exception) {
            // Fallback au skieur_pixel si le sprite sheet n'existe pas
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
                val y = event.values[1]
                val x = event.values[0]
                val z = event.values[2]

                if (gameState == GameState.SKIING) {
                    // MODIFIÉ : Animation basée sur l'inclinaison
                    playerOffset += x * 0.1f
                    playerOffset = playerOffset.coerceIn(-1f, 1f)
                    
                    // Changer l'animation selon l'inclinaison
                    updateAnimation(x)

                    val rotationDirection = when {
                        z > 1.0f -> 1
                        z < -1.0f -> -1
                        else -> 0
                    }
                    if (rotationDirection != 0 && rotationDirection != previousGyroDirection) {
                        distance += 25f
                        backgroundOffset -= 10f
                        previousGyroDirection = rotationDirection
                    }
                    
                    if (distance >= totalDistance * 0.5f) {
                        gameState = GameState.SHOOTING
                    }
                }

                if (gameState == GameState.FINAL_SKIING) {
                    // MODIFIÉ : Animation aussi dans le ski final
                    playerOffset += x * 0.1f
                    playerOffset = playerOffset.coerceIn(-1f, 1f)
                    
                    // Changer l'animation selon l'inclinaison
                    updateAnimation(x)

                    val rotationDirection = when {
                        z > 1.0f -> 1
                        z < -1.0f -> -1
                        else -> 0
                    }
                    if (rotationDirection != 0 && rotationDirection != previousGyroDirection) {
                        distance += 25f
                        backgroundOffset -= 10f
                        previousGyroDirection = rotationDirection
                    }
                    
                    if (distance >= totalDistance) {
                        gameState = GameState.FINISHED
                        
                        if (!practiceMode) {
                            tournamentData.addScore(currentPlayerIndex, eventIndex, calculateScore())
                        }
                        
                        statusText.postDelayed({
                            proceedToNextPlayerOrEvent()
                        }, 2000)
                    }
                }
            }
            
            Sensor.TYPE_ACCELEROMETER -> {
                if (gameState == GameState.SHOOTING) {
                    val x = event.values[0]  // Inclinaison gauche/droite
                    val y = event.values[1]  // Inclinaison avant/arrière
                    
                    // CORRIGÉ : Axes inversés pour le mode paysage
                    // En paysage : Y devient horizontal, X devient vertical
                    crosshair.x += y * 0.012f  // Utiliser Y pour mouvement horizontal
                    crosshair.y += x * 0.012f  // Utiliser X pour mouvement vertical
                    
                    // Limiter la visée dans la zone de tir
                    crosshair.x = crosshair.x.coerceIn(0.1f, 0.9f)
                    crosshair.y = crosshair.y.coerceIn(0.2f, 0.6f)
                }
            }
        }
        
        if (gameState == GameState.SHOOTING && shotsFired >= 5) {
            statusText.postDelayed({
                distance = totalDistance * 0.5f
                gameState = GameState.FINAL_SKIING
            }, 1000)
        }

        updateStatus()
        gameView.invalidate()
    }

    // AJOUTÉ : Fonction pour mettre à jour l'animation selon l'inclinaison
    private fun updateAnimation(tiltX: Float) {
        val currentTime = System.currentTimeMillis()
        
        // Changer de frame toutes les 200ms quand il y a du mouvement
        if (abs(tiltX) > 0.1f && currentTime - animationTimer > 200) {
            animationTimer = currentTime
            
            // Choisir la frame selon l'inclinaison
            currentFrame = when {
                tiltX < -0.1f -> leftFrame  // Inclinaison vers la gauche
                tiltX > 0.1f -> rightFrame  // Inclinaison vers la droite
                else -> if (useLeftFrame) leftFrame else rightFrame // Alternance par défaut
            }
            
            useLeftFrame = !useLeftFrame
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
        val aiAccuracy = (1..4).random()
        val aiDistance = (4000..5000).random()
        val accuracyBonus = aiAccuracy * 50
        val distanceBonus = (aiDistance / totalDistance * 100).toInt()
        val penalty = (5 - aiAccuracy) * 20
        return maxOf(50, accuracyBonus + distanceBonus - penalty)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && gameState == GameState.SHOOTING && shotsFired < 5) {
            shotsFired++
            
            for (i in targetPositions.indices) {
                val dx = crosshair.x - targetPositions[i].x
                val dy = crosshair.y - targetPositions[i].y
                if (!targetHitStatus[i] && sqrt(dx * dx + dy * dy) < 0.08f) {
                    targetHitStatus[i] = true
                    targetsHit++
                    break
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
            GameState.SKIING -> "🎿 ${tournamentData.playerNames[currentPlayerIndex]} | Distance: ${distance.toInt()}m / ${totalDistance.toInt()}m"
            GameState.SHOOTING -> "🎯 ${tournamentData.playerNames[currentPlayerIndex]} | Tir ${shotsFired}/5 — Touchés: $targetsHit"
            GameState.FINAL_SKIING -> "🎿 ${tournamentData.playerNames[currentPlayerIndex]} | Sprint final: ${distance.toInt()}m / ${totalDistance.toInt()}m"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Score final: ${calculateScore()} points"
        }
    }

    private fun calculateScore(): Int {
        val accuracyBonus = targetsHit * 50
        val distanceBonus = (distance / totalDistance * 100).toInt()
        val penaltyForMissedShots = (5 - targetsHit) * 20
        return maxOf(0, accuracyBonus + distanceBonus - penaltyForMissedShots)
    }

    inner class BiathlonView(context: Context) : View(context) {
        private val paint = Paint()
        private val bgPaint = Paint().apply { color = Color.parseColor("#87CEEB") }
        private val snowPaint = Paint().apply { color = Color.WHITE }
        private val trackPaint = Paint().apply { color = Color.LTGRAY }
        private val treePaint = Paint().apply { color = Color.parseColor("#0F5132") }
        private val mountainPaint = Paint().apply { color = Color.parseColor("#6C757D") }
        private val cloudPaint = Paint().apply { color = Color.parseColor("#F8F9FA") }

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            
            // AMÉLIORÉ : Ciel dégradé
            val skyGradient = LinearGradient(
                0f, 0f, 0f, h * 0.4f,
                Color.parseColor("#87CEEB"), Color.parseColor("#B0E0E6"),
                Shader.TileMode.CLAMP
            )
            bgPaint.shader = skyGradient
            canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, bgPaint)
            bgPaint.shader = null
            
            // AJOUTÉ : Montagnes en arrière-plan
            mountainPaint.color = Color.parseColor("#A0A0A0")
            for (i in 0..4) {
                val mountainX = (backgroundOffset * 0.3f + i * w * 0.5f) % (w + w * 0.5f) - w * 0.25f
                canvas.drawPath(createMountainPath(mountainX, h * 0.15f, w * 0.4f, h * 0.25f), mountainPaint)
            }
            
            mountainPaint.color = Color.parseColor("#707070")
            for (i in 0..3) {
                val mountainX = (backgroundOffset * 0.5f + i * w * 0.7f) % (w + w * 0.7f) - w * 0.35f
                canvas.drawPath(createMountainPath(mountainX, h * 0.2f, w * 0.5f, h * 0.2f), mountainPaint)
            }
            
            // AJOUTÉ : Nuages
            cloudPaint.alpha = 180
            for (i in 0..3) {
                val cloudX = (backgroundOffset * 0.2f + i * w * 0.8f) % (w + w * 0.8f) - w * 0.4f
                drawCloud(canvas, cloudX, h * 0.1f + i * 30f, 80f)
            }
            cloudPaint.alpha = 255
            
            // AJOUTÉ : Forêt d'arrière-plan
            for (i in 0..15) {
                val treeX = (backgroundOffset * 0.7f + i * 80) % (w + 160) - 80
                drawTree(canvas, treeX, h * 0.4f, 60f, h * 0.2f)
            }
            
            // Sol de neige avec dégradé
            val snowGradient = LinearGradient(
                0f, h * 0.6f, 0f, h.toFloat(),
                Color.parseColor("#FFFFFF"), Color.parseColor("#F0F8FF"),
                Shader.TileMode.CLAMP
            )
            snowPaint.shader = snowGradient
            canvas.drawRect(0f, h * 0.6f, w.toFloat(), h.toFloat(), snowPaint)
            snowPaint.shader = null
            
            // AJOUTÉ : Traces de ski et empreintes
            paint.color = Color.parseColor("#E0E0E0")
            paint.strokeWidth = 3f
            for (i in 0..20) {
                val traceX = (backgroundOffset + i * 50) % (w + 100) - 50
                canvas.drawLine(traceX, h * 0.6f, traceX + 40, h.toFloat(), paint)
                canvas.drawLine(traceX + 10, h * 0.6f, traceX + 50, h.toFloat(), paint)
            }
            
            // Piste principale
            trackPaint.color = Color.parseColor("#DCDCDC")
            canvas.drawRect(0f, h * 0.75f, w.toFloat(), h.toFloat(), trackPaint)
            
            // AJOUTÉ : Bordures de piste
            paint.color = Color.parseColor("#FF6B6B")
            paint.strokeWidth = 8f
            for (i in 0..10) {
                val flagX = (backgroundOffset + i * 150) % (w + 300) - 150
                canvas.drawLine(flagX, h * 0.73f, flagX, h * 0.77f, paint)
                canvas.drawLine(flagX, h * 0.73f, flagX + 20, h * 0.74f, paint)
            }
            
            // CORRIGÉ : Le skieur avance et ne recule jamais
            val progressRatio = distance / totalDistance
            val skierX = (w * 0.1f) + (progressRatio * w * 0.6f) + playerOffset * 100f
            val skierY = h * 0.75f - (currentFrame?.height ?: 50)
            
            currentFrame?.let { frame ->
                val destX = skierX - frame.width / 2f
                canvas.drawBitmap(frame, destX, skierY, null)
            }

            if (gameState == GameState.SHOOTING || gameState == GameState.FINISHED) {
                paint.color = Color.parseColor("#1a1a2e")
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                
                paint.color = Color.WHITE
                paint.textSize = 40f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("🎯 ZONE DE TIR 🎯", w/2f, 60f, paint)
                
                paint.textSize = 24f
                canvas.drawText("Téléphone à PLAT • Inclinez : droite→droite, haut→haut, comme une bille !", w/2f, 100f, paint)
                
                if (shotsFired >= 5) {
                    paint.color = Color.GREEN
                    paint.textSize = 30f
                    canvas.drawText("TIR TERMINÉ - Passage au ski final...", w/2f, 140f, paint)
                }
                
                for (i in targetPositions.indices) {
                    val px = targetPositions[i].x * w
                    val py = targetPositions[i].y * h + 50
                    
                    if (targetHitStatus[i]) {
                        paint.color = Color.parseColor("#00ff00")
                        canvas.drawCircle(px, py, 35f, paint)
                        paint.color = Color.parseColor("#004400")
                        canvas.drawCircle(px, py, 30f, paint)
                        paint.color = Color.GREEN
                        paint.strokeWidth = 8f
                        paint.style = Paint.Style.STROKE
                        canvas.drawLine(px-15, py-15, px+15, py+15, paint)
                        canvas.drawLine(px+15, py-15, px-15, py+15, paint)
                        paint.style = Paint.Style.FILL
                        
                        paint.color = Color.WHITE
                        paint.textSize = 16f
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText("+50", px, py + 60f, paint)
                    } else {
                        paint.color = Color.WHITE
                        canvas.drawCircle(px, py, 35f, paint)
                        paint.color = Color.BLACK
                        canvas.drawCircle(px, py, 28f, paint)
                        paint.color = Color.WHITE
                        canvas.drawCircle(px, py, 21f, paint)
                        paint.color = Color.BLACK
                        canvas.drawCircle(px, py, 14f, paint)
                        paint.color = Color.RED
                        canvas.drawCircle(px, py, 7f, paint)
                        
                        paint.color = Color.WHITE
                        paint.textSize = 20f
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText("${i+1}", px, py + 60f, paint)
                    }
                }
                
                val crossX = crosshair.x * w
                val crossY = crosshair.y * h + 50
                
                paint.color = Color.BLACK
                paint.strokeWidth = 8f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(crossX - 30, crossY, crossX + 30, crossY, paint)
                canvas.drawLine(crossX, crossY - 30, crossX, crossY + 30, paint)
                canvas.drawCircle(crossX, crossY, 20f, paint)
                
                paint.color = Color.RED
                paint.strokeWidth = 4f
                canvas.drawLine(crossX - 25, crossY, crossX + 25, crossY, paint)
                canvas.drawLine(crossX, crossY - 25, crossX, crossY + 25, paint)
                
                paint.color = Color.YELLOW
                paint.style = Paint.Style.FILL
                canvas.drawCircle(crossX, crossY, 6f, paint)
                paint.color = Color.RED
                canvas.drawCircle(crossX, crossY, 12f, paint)
                paint.style = Paint.Style.FILL
                
                paint.color = Color.WHITE
                paint.textSize = 24f
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText("🔫 Munitions: ${5-shotsFired}/5", 30f, h - 80f, paint)
                canvas.drawText("🎯 Touchés: $targetsHit/5", 30f, h - 50f, paint)
                
                for (i in 0 until 5) {
                    paint.color = if (i < shotsFired) Color.GRAY else Color.YELLOW
                    canvas.drawRect(w - 200f + i * 35f, h - 60f, w - 175f + i * 35f, h - 40f, paint)
                }
            }
            
            // Barre de progression
            paint.color = Color.BLACK
            canvas.drawRect(w * 0.1f, 20f, w * 0.9f, 40f, paint)
            paint.color = Color.GREEN
            canvas.drawRect(w * 0.1f, 20f, w * 0.1f + (progressRatio * w * 0.8f), 40f, paint)
        }
        
        // AJOUTÉ : Fonction pour dessiner une montagne
        private fun createMountainPath(startX: Float, startY: Float, width: Float, height: Float): Path {
            val path = Path()
            path.moveTo(startX, startY + height)
            path.lineTo(startX + width * 0.3f, startY + height * 0.7f)
            path.lineTo(startX + width * 0.5f, startY)
            path.lineTo(startX + width * 0.7f, startY + height * 0.6f)
            path.lineTo(startX + width, startY + height)
            path.close()
            return path
        }
        
        // AJOUTÉ : Fonction pour dessiner un nuage
        private fun drawCloud(canvas: Canvas, x: Float, y: Float, size: Float) {
            canvas.drawCircle(x, y, size * 0.6f, cloudPaint)
            canvas.drawCircle(x + size * 0.5f, y, size * 0.5f, cloudPaint)
            canvas.drawCircle(x - size * 0.3f, y, size * 0.4f, cloudPaint)
            canvas.drawCircle(x + size * 0.2f, y - size * 0.3f, size * 0.4f, cloudPaint)
        }
        
        // AJOUTÉ : Fonction pour dessiner un arbre
        private fun drawTree(canvas: Canvas, x: Float, baseY: Float, width: Float, height: Float) {
            // Tronc
            treePaint.color = Color.parseColor("#8B4513")
            canvas.drawRect(x - width * 0.1f, baseY, x + width * 0.1f, baseY + height * 0.3f, treePaint)
            
            // Feuillage
            treePaint.color = Color.parseColor("#0F5132")
            canvas.drawCircle(x, baseY + height * 0.2f, width * 0.4f, treePaint)
            treePaint.color = Color.parseColor("#228B22")
            canvas.drawCircle(x, baseY, width * 0.3f, treePaint)
        }
    }

    enum class GameState {
        SKIING, SHOOTING, FINAL_SKIING, FINISHED
    }
}
