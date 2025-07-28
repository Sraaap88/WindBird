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

class LugeActivity : Activity(), SensorEventListener {

    private lateinit var gameView: LugeView
    private lateinit var statusText: TextView
    private lateinit var lugeEngine: LugeGameEngine

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null
    private var accelerometer: Sensor? = null

    // Variables de gameplay principales
    private var gameState = GameState.PREPARATION
    private var phaseTimer = 0f
    
    // Phases avec durées pour expérience immersive
    private val preparationDuration = 12f
    private val rideDuration = 120f  // 2 MINUTES de descente !
    private val resultsDuration = 15f
    
    // Contrôles capteurs
    private var tiltX = 0f
    private var tiltY = 0f
    private var tiltZ = 0f
    private var accelX = 0f
    private var accelY = 0f
    private var accelZ = 0f

    // Variables du tournoi
    private lateinit var tournamentData: TournamentData
    private var eventIndex: Int = 0
    private var numberOfPlayers: Int = 1
    private var currentPlayerIndex: Int = 0
    private var practiceMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // Récupération des données du tournoi
        tournamentData = intent.getSerializableExtra("tournament_data") as TournamentData
        eventIndex = intent.getIntExtra("event_index", 0)
        numberOfPlayers = intent.getIntExtra("number_of_players", 1)
        practiceMode = intent.getBooleanExtra("practice_mode", false)
        currentPlayerIndex = intent.getIntExtra("current_player_index", tournamentData.getNextPlayer(eventIndex))

        // Configuration des capteurs
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Configuration de l'interface
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        statusText = TextView(this).apply {
            text = "🛷 LUGE EXTRÊME - ${tournamentData.playerNames[currentPlayerIndex]}"
            setTextColor(Color.WHITE)
            textSize = 24f
            setBackgroundColor(Color.parseColor("#001133"))
            setPadding(30, 25, 30, 25)
        }

        // Initialisation du moteur de jeu
        lugeEngine = LugeGameEngine()
        gameView = LugeView(this)

        layout.addView(statusText)
        layout.addView(gameView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        
        initializeGame()
    }
    
    private fun initializeGame() {
        gameState = GameState.PREPARATION
        phaseTimer = 0f
        
        // Reset des contrôles
        tiltX = 0f
        tiltY = 0f
        tiltZ = 0f
        accelX = 0f
        accelY = 0f
        accelZ = 0f
        
        // Initialisation du moteur de jeu
        lugeEngine.initialize()
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
                tiltX = event.values[0] * lugeEngine.gyroSensitivity
                tiltY = event.values[1] * lugeEngine.gyroSensitivity
                tiltZ = event.values[2] * lugeEngine.gyroSensitivity
            }
            Sensor.TYPE_ACCELEROMETER -> {
                accelX = event.values[0]
                accelY = event.values[1]
                accelZ = event.values[2]
            }
        }

        // Progression du jeu
        val deltaTime = 0.01f
        phaseTimer += deltaTime
        
        // Mise à jour des entrées dans le moteur
        lugeEngine.updateInputs(tiltX, tiltY, tiltZ, accelX, accelY, accelZ)

        when (gameState) {
            GameState.PREPARATION -> handlePreparation()
            GameState.RIDING -> {
                if (gameState == GameState.RIDING) {
                    lugeEngine.raceTime += deltaTime
                }
                handleRiding(deltaTime)
            }
            GameState.RESULTS -> handleResults()
            GameState.FINISHED -> {}
        }

        updateStatus()
        gameView.invalidate()
    }
    
    private fun handlePreparation() {
        if (phaseTimer >= preparationDuration) {
            gameState = GameState.RIDING
            phaseTimer = 0f
            lugeEngine.startRacing()
        }
    }
    
    private fun handleRiding(deltaTime: Float) {
        // Déléguer la logique de course au moteur
        lugeEngine.updateRiding(deltaTime)
        
        // Vérifier la fin de course
        if (lugeEngine.distance >= lugeEngine.totalDistance) {
            lugeEngine.calculateFinalScore()
            gameState = GameState.RESULTS
            phaseTimer = 0f
        }
    }
    
    private fun handleResults() {
        if (phaseTimer >= resultsDuration) {
            gameState = GameState.FINISHED
            
            if (!practiceMode) {
                tournamentData.addScore(currentPlayerIndex, eventIndex, lugeEngine.finalScore)
            }
            
            statusText.postDelayed({
                proceedToNextPlayerOrEvent()
            }, 5000)
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
                val aiScore = (150..280).random()
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
            GameState.PREPARATION -> "🛷 ${tournamentData.playerNames[currentPlayerIndex]} | Préparation... ${(preparationDuration - phaseTimer).toInt() + 1}s | Inclinez pour diriger, secouez pour freiner"
            GameState.RIDING -> {
                val currentSectorName = if (lugeEngine.currentSector < lugeEngine.trackSectors.size) 
                    lugeEngine.trackSectors[lugeEngine.currentSector].name else "Final"
                "🛷 ${tournamentData.playerNames[currentPlayerIndex]} | ${lugeEngine.speed.toInt()} km/h | $currentSectorName | ❤️${lugeEngine.heartRate.toInt()} | Parfaits: ${lugeEngine.perfectCurves} | Murs: ${lugeEngine.wallHits}"
            }
            GameState.RESULTS -> "🏆 ${tournamentData.playerNames[currentPlayerIndex]} | Temps: ${lugeEngine.raceTime.toInt()}s | Vitesse Max: ${lugeEngine.topSpeed.toInt()} km/h | Score: ${lugeEngine.finalScore}"
            GameState.FINISHED -> "✅ ${tournamentData.playerNames[currentPlayerIndex]} | Descente EXTRÊME terminée!"
        }
    }

    inner class LugeView(context: Context) : View(context) {
        private val renderer = LugeRenderer(lugeEngine)

        override fun onDraw(canvas: Canvas) {
            val w = canvas.width
            val h = canvas.height
            
            when (gameState) {
                GameState.PREPARATION -> renderer.drawPreparation(canvas, w, h)
                GameState.RIDING -> renderer.drawRiding(canvas, w, h)
                GameState.RESULTS -> renderer.drawResults(canvas, w, h)
                GameState.FINISHED -> renderer.drawResults(canvas, w, h)
            }
        }
    }

    enum class GameState {
        PREPARATION, RIDING, RESULTS, FINISHED
    }
}

// Classes de données pour la structure du jeu
data class Vector3(var x: Float, var y: Float, var z: Float)

data class TrackCurve(
    val distance: Float,
    val type: Type,
    val direction: Float, // -1 = gauche, +1 = droite
    val length: Float,
    val banking: Float = 0f
) {
    val intensity: Float = when (type) {
        Type.GENTLE -> 0.25f
        Type.MEDIUM -> 0.45f
        Type.SHARP -> 0.7f
        Type.HAIRPIN -> 0.9f
        Type.CHICANE -> 0.4f
    }
    
    enum class Type { GENTLE, MEDIUM, SHARP, HAIRPIN, CHICANE }
}

data class TrackElement(
    val distance: Float,
    val sideOffset: Float, // -1 = gauche, +1 = droite
    val offsetDistance: Float,
    val type: ElementType,
    val scale: Float = 1f,
    val height: Float = 100f
) {
    enum class ElementType { 
        TREE, SAFETY_BARRIER, SPECTATOR, FLAG, TUNNEL_WALL, SPEED_MARKER, ROCK 
    }
}

data class TrackSector(
    val name: String,
    val startDistance: Float,
    val length: Float,
    val type: SectorType,
    val difficulty: Float
) {
    enum class SectorType(val displayName: String) {
        GENTLE_START("Départ en Douceur"),
        SPEED_ZONE("Zone de Vitesse"),
        TECHNICAL_CURVES("Virages Techniques"),
        EXTREME_DESCENT("Descente Extrême"),
        TUNNEL_SECTION("Section Tunnel"),
        CHICANE_COMPLEX("Complexe de Chicanes"),
        FINAL_SPRINT("Sprint Final")
    }
}

data class Checkpoint(
    val distance: Float,
    val name: String,
    val targetTime: Float,
    var passed: Boolean = false,
    var actualTime: Float = 0f
)

data class PerformanceMetrics(
    var finalTime: Float = 0f,
    var topSpeed: Float = 0f,
    var averageSpeed: Float = 0f,
    var perfectCurves: Int = 0,
    var wallHits: Int = 0,
    var finalAerodynamics: Float = 100f,
    var finalPrecision: Float = 100f,
    var brakingUsed: Boolean = false,
    var stamina: Float = 100f
)

// Classes pour les particules et effets
data class SnowParticle3D(
    var x: Float, var y: Float, var z: Float,
    var vx: Float, var vy: Float, var vz: Float,
    var size: Float, var life: Float
)

data class IceChip(
    var x: Float, var y: Float, var z: Float,
    var vx: Float, var vy: Float, var vz: Float,
    var life: Float, val sparkle: Boolean = false
)

data class SpeedStreak(
    var x: Float, val y: Float, var vx: Float, var vy: Float, var life: Float
)

data class WallSpark(
    var x: Float, var y: Float, var z: Float,
    var vx: Float, var vy: Float, var vz: Float,
    var life: Float, val color: Int, val intensity: Float
)

data class WindTrail(
    var x: Float, var y: Float, var z: Float,
    var vx: Float, var vy: Float, var vz: Float,
    var life: Float, val intensity: Float
)

data class GroundImpact(
    var x: Float, var y: Float, var z: Float,
    var vx: Float, var vy: Float, var vz: Float,
    var life: Float, val size: Float
)

data class IceSparkle(
    var x: Float, var y: Float, var z: Float,
    var vx: Float, var vy: Float, var vz: Float,
    var life: Float, val color: Int, val sparkleRate: Float
)

data class AeroTrail(
    val x: Float, val y: Float, val z: Float,
    val length: Float, val intensity: Float, var life: Float
)

data class CurveIndicator(
    val direction: Float,
    val intensity: Float,
    val type: TrackCurve.Type,
    val distance: Float,
    var urgency: Float,
    var life: Float,
    val banking: Float
)

data class SpeedZone(
    val distance: Float,
    val length: Float,
    val speedBonus: Float
)
