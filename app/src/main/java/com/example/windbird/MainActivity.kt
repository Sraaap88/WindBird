package com.example.windbird

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.LinearLayout
import android.graphics.Color
import android.view.ViewGroup
import android.view.View

class MainActivity : Activity(), SensorEventListener {
    
    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null
    private lateinit var statusText: TextView
    private lateinit var mainLayout: LinearLayout
    private lateinit var eventsLayout: LinearLayout
    
    // Données des épreuves
    private val eventNames = arrayOf(
        "Biathlon", "Saut à Ski", "Bobsleigh", "Patinage Vitesse", 
        "Slalom", "Snowboard Halfpipe", "Ski Freestyle", "Luge",
        "Curling", "Hockey sur Glace"
    )
    
    private val eventDescriptions = arrayOf(
        "Visez avec précision et stabilité",
        "Équilibrez-vous en vol",
        "Négociez les virages avec finesse",
        "Maintenez le rythme parfait",
        "Slalomez entre les portes",
        "Réalisez des figures spectaculaires",
        "Enchaînez les acrobaties",
        "Contrôlez votre trajectoire",
        "Précision et puissance",
        "Marquez le but décisif"
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Configuration plein écran
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        setupUI()
        setupSensors()
        
        // Vérification du gyroscope
        if (gyroscope == null) {
            statusText.text = "Gyroscope non disponible sur cet appareil"
            statusText.setTextColor(Color.RED)
        } else {
            statusText.text = "Gyroscope activé - Prêt à jouer !"
            statusText.setTextColor(Color.GREEN)
        }
    }
    
    private fun setupUI() {
        // Layout principal
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(20, 20, 20, 20)
        }
        
        // Titre
        val titleText = TextView(this).apply {
            text = "WINTER GAMES"
            textSize = 32f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }
        mainLayout.addView(titleText)
        
        // Sous-titre
        val subtitleText = TextView(this).apply {
            text = "Édition Gyroscopique"
            textSize = 18f
            setTextColor(Color.CYAN)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        mainLayout.addView(subtitleText)
        
        // Statut gyroscope
        statusText = TextView(this).apply {
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }
        mainLayout.addView(statusText)
        
        // Layout des épreuves
        eventsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        mainLayout.addView(eventsLayout)
        
        // Création des boutons d'épreuves
        createEventButtons()
        
        setContentView(mainLayout)
    }
    
    private fun createEventButtons() {
        for (i in eventNames.indices) {
            val eventLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(10, 10, 10, 10)
                setBackgroundColor(Color.parseColor("#003366"))
            }
            
            // Marge entre les épreuves
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 10)
            }
            eventLayout.layoutParams = params
            
            // Icône colorée
            val iconView = View(this).apply {
                setBackgroundColor(getEventColor(i))
            }
            val iconParams = LinearLayout.LayoutParams(60, 60).apply {
                setMargins(0, 0, 20, 0)
            }
            iconView.layoutParams = iconParams
            eventLayout.addView(iconView)
            
            // Texte de l'épreuve
            val textLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, 
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val nameText = TextView(this).apply {
                text = eventNames[i]
                textSize = 18f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            textLayout.addView(nameText)
            
            val descText = TextView(this).apply {
                text = eventDescriptions[i]
                textSize = 12f
                setTextColor(Color.LTGRAY)
            }
            textLayout.addView(descText)
            
            eventLayout.addView(textLayout)
            
            // Bouton jouer
            val playButton = Button(this).apply {
                text = "JOUER"
                setBackgroundColor(Color.parseColor("#ff6600"))
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                
                setOnClickListener { startEvent(i) }
            }
            
            eventLayout.addView(playButton)
            eventsLayout.addView(eventLayout)
        }
    }
    
    private fun getEventColor(index: Int): Int {
        val colors = arrayOf(
            Color.parseColor("#ff4444"), // Biathlon - Rouge
            Color.parseColor("#44ff44"), // Saut - Vert
            Color.parseColor("#4444ff"), // Bobsleigh - Bleu
            Color.parseColor("#ffff44"), // Patinage - Jaune
            Color.parseColor("#ff44ff"), // Slalom - Magenta
            Color.parseColor("#44ffff"), // Snowboard - Cyan
            Color.parseColor("#ff8844"), // Freestyle - Orange
            Color.parseColor("#8844ff"), // Luge - Violet
            Color.parseColor("#44ff88"), // Curling - Vert clair
            Color.parseColor("#ff4488")  // Hockey - Rose
        )
        return colors[index % colors.size]
    }
    
    private fun setupSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }
    
    private fun startEvent(eventIndex: Int) {
        val intent = when (eventIndex) {
            0 -> Intent(this, BiathlonActivity::class.java) // Biathlon
            else -> {
                // Pour l'instant, seul le Biathlon est implémenté
                return
            }
        }
        
        startActivity(intent)
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
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            // Pas utilisé dans le menu, juste pour détecter le gyroscope
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}
