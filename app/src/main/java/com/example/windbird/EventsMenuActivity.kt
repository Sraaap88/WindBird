package com.example.windbird

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import android.graphics.Color
import android.view.ViewGroup
import android.view.View
import java.io.Serializable

class EventsMenuActivity : Activity() {
    
    private lateinit var playerNames: ArrayList<String>
    private lateinit var playerCountries: ArrayList<String>
    private var numberOfPlayers = 1
    private var practiceMode = false
    
    private lateinit var tournamentData: TournamentData
    private lateinit var eventsLayout: LinearLayout
    private lateinit var statusText: TextView
    
    // MODIFIÉ : 8 épreuves au lieu de 9 (retiré Curling) - TOUTES IMPLÉMENTÉES
    private val events = arrayOf(
        Event("Biathlon", "🎯", "Ski de fond + tir de précision", true),
        Event("Saut à Ski", "🎿", "Envol et atterrissage parfait", true),
        Event("Bobsleigh", "🛷", "Descente à haute vitesse", true),
        Event("Patinage Vitesse", "⛸️", "Course sur glace", true),
        Event("Slalom", "⛷️", "Zigzag entre les portes", true),
        Event("Snowboard Halfpipe", "🏂", "Figures aériennes", true),
        Event("Ski Freestyle", "🎿", "Acrobaties en vol", true),
        Event("Luge", "🛷", "Contrôle de trajectoire", true)
        // RETIRÉ : Curling
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        playerNames = intent.getStringArrayListExtra("player_names") ?: arrayListOf()
        playerCountries = intent.getStringArrayListExtra("player_countries") ?: arrayListOf()
        numberOfPlayers = intent.getIntExtra("number_of_players", 1)
        practiceMode = intent.getBooleanExtra("practice_mode", false)
        
        while (playerNames.size < 4) {
            playerNames.add("IA ${playerNames.size + 1}")
            playerCountries.add("🤖 Intelligence Artificielle")
        }
        
        tournamentData = TournamentData(playerNames, playerCountries)
        
        setupUI()
    }
    
    private fun setupUI() {
        val scrollView = ScrollView(this)
        
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(20, 20, 20, 20)
        }
        
        val titleText = TextView(this).apply {
            text = if (practiceMode) "🎯 MODE PRATIQUE 🎯" else "🏆 WINTER GAMES TOURNAMENT 🏆"
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        mainLayout.addView(titleText)
        
        statusText = TextView(this).apply {
            text = if (practiceMode) "Choisissez une épreuve à pratiquer" else "Sélectionnez une épreuve pour commencer"
            textSize = 14f
            setTextColor(Color.CYAN)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        mainLayout.addView(statusText)
        
        if (!practiceMode) {
            createPlayersList(mainLayout)
        }
        
        eventsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        mainLayout.addView(eventsLayout)
        
        createEventsList()
        
        createNavigationButtons(mainLayout)
        
        scrollView.addView(mainLayout)
        setContentView(scrollView)
    }
    
    private fun createPlayersList(parent: LinearLayout) {
        val playersLabel = TextView(this).apply {
            text = "PARTICIPANTS :"
            textSize = 16f
            setTextColor(Color.YELLOW)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        parent.addView(playersLabel)
        
        val playersLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 20)
        }
        
        for (i in 0 until 4) {
            val playerCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(if (i < numberOfPlayers) Color.parseColor("#003366") else Color.parseColor("#332200"))
                setPadding(10, 10, 10, 10)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(2, 0, 2, 0)
                }
            }
            
            val nameText = TextView(this).apply {
                text = playerNames[i]
                textSize = 12f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
            }
            playerCard.addView(nameText)
            
            val countryText = TextView(this).apply {
                text = playerCountries[i]
                textSize = 10f
                setTextColor(Color.LTGRAY)
                gravity = android.view.Gravity.CENTER
            }
            playerCard.addView(countryText)
            
            val scoreText = TextView(this).apply {
                text = "Score: ${tournamentData.getTotalScore(i)}"
                textSize = 10f
                setTextColor(Color.CYAN)
                gravity = android.view.Gravity.CENTER
            }
            playerCard.addView(scoreText)
            
            playersLayout.addView(playerCard)
        }
        
        parent.addView(playersLayout)
    }
    
    private fun createEventsList() {
        // MODIFIÉ : Boucle jusqu'à 7 (8 épreuves : 0 à 7)
        for (i in events.indices) {
            val event = events[i]
            val eventStatus = tournamentData.getEventStatus(i, practiceMode)
            
            val eventLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(15, 15, 15, 15)
                setBackgroundColor(
                    when (eventStatus) {
                        EventStatus.COMPLETED -> Color.parseColor("#004400")
                        EventStatus.IN_PROGRESS -> Color.parseColor("#444400")
                        EventStatus.AVAILABLE -> Color.parseColor("#003366")
                        EventStatus.LOCKED -> Color.parseColor("#333333")
                    }
                )
            }
            
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 10)
            }
            eventLayout.layoutParams = params
            
            val eventInfo = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val nameText = TextView(this).apply {
                text = "${event.icon} ${event.name}"
                textSize = 18f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            eventInfo.addView(nameText)
            
            val descText = TextView(this).apply {
                text = event.description
                textSize = 12f
                setTextColor(Color.LTGRAY)
            }
            eventInfo.addView(descText)
            
            val statusText = TextView(this).apply {
                text = when (eventStatus) {
                    EventStatus.COMPLETED -> "✅ TERMINÉ"
                    EventStatus.IN_PROGRESS -> "🔄 EN COURS"
                    EventStatus.AVAILABLE -> "▶️ DISPONIBLE"
                    EventStatus.LOCKED -> "🔒 VERROUILLÉ"
                }
                textSize = 12f
                setTextColor(
                    when (eventStatus) {
                        EventStatus.COMPLETED -> Color.GREEN
                        EventStatus.IN_PROGRESS -> Color.YELLOW
                        EventStatus.AVAILABLE -> Color.CYAN
                        EventStatus.LOCKED -> Color.GRAY
                    }
                )
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            eventInfo.addView(statusText)
            
            eventLayout.addView(eventInfo)
            
            val playButton = Button(this).apply {
                text = if (eventStatus == EventStatus.COMPLETED) "REJOUER" else "JOUER"
                setBackgroundColor(
                    if (eventStatus == EventStatus.AVAILABLE || eventStatus == EventStatus.IN_PROGRESS) 
                        Color.parseColor("#ff6600") else Color.parseColor("#666666")
                )
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                isEnabled = eventStatus == EventStatus.AVAILABLE || eventStatus == EventStatus.IN_PROGRESS
                
                setOnClickListener { startEvent(i) }
            }
            
            eventLayout.addView(playButton)
            eventsLayout.addView(eventLayout)
        }
    }
    
    private fun createNavigationButtons(parent: LinearLayout) {
        val buttonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 20, 0, 0)
        }
        
        val scoresButton = Button(this).apply {
            text = "📊 CLASSEMENT"
            setBackgroundColor(Color.parseColor("#004488"))
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 10, 0)
            }
            
            setOnClickListener { showScoreboard() }
        }
        buttonsLayout.addView(scoresButton)
        
        val resetButton = Button(this).apply {
            text = "🔄 NOUVEAU TOURNOI"
            setBackgroundColor(Color.parseColor("#666666"))
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            
            setOnClickListener { resetTournament() }
        }
        buttonsLayout.addView(resetButton)
        
        parent.addView(buttonsLayout)
    }
    
    private fun startEvent(eventIndex: Int) {
        when (eventIndex) {
            0 -> {
                // Biathlon - Code qui FONCTIONNE
                if (events[0].implemented) {
                    val intent = Intent(this, BiathlonActivity::class.java).apply {
                        putExtra("tournament_data", tournamentData)
                        putExtra("event_index", eventIndex)
                        putExtra("number_of_players", numberOfPlayers)
                        putExtra("practice_mode", practiceMode)
                        // CORRECTION : Toujours passer current_player_index explicitement
                        putExtra("current_player_index", if (practiceMode) 0 else tournamentData.getNextPlayer(eventIndex))
                    }
                    startActivityForResult(intent, 100)
                }
            }
            1 -> {
                // Saut à Ski - COPIE EXACTE du code Biathlon
                if (events[1].implemented) {
                    val intent = Intent(this, SkiJumpActivity::class.java).apply {
                        putExtra("tournament_data", tournamentData)
                        putExtra("event_index", eventIndex)
                        putExtra("number_of_players", numberOfPlayers)
                        putExtra("practice_mode", practiceMode)
                        // CORRECTION : Toujours passer current_player_index explicitement
                        putExtra("current_player_index", if (practiceMode) 0 else tournamentData.getNextPlayer(eventIndex))
                    }
                    startActivityForResult(intent, 100)
                }
            }
            2 -> {
                // Bobsleigh - COPIE EXACTE du code Biathlon
                if (events[2].implemented) {
                    val intent = Intent(this, BobsledActivity::class.java).apply {
                        putExtra("tournament_data", tournamentData)
                        putExtra("event_index", eventIndex)
                        putExtra("number_of_players", numberOfPlayers)
                        putExtra("practice_mode", practiceMode)
                        // CORRECTION : Toujours passer current_player_index explicitement
                        putExtra("current_player_index", if (practiceMode) 0 else tournamentData.getNextPlayer(eventIndex))
                    }
                    startActivityForResult(intent, 100)
                }
            }
            3 -> {
                // Patinage Vitesse - COPIE EXACTE du code Biathlon
                if (events[3].implemented) {
                    val intent = Intent(this, PatinageVitesseActivity::class.java).apply {
                        putExtra("tournament_data", tournamentData)
                        putExtra("event_index", eventIndex)
                        putExtra("number_of_players", numberOfPlayers)
                        putExtra("practice_mode", practiceMode)
                        putExtra("current_player_index", if (practiceMode) 0 else tournamentData.getNextPlayer(eventIndex))
                    }
                    startActivityForResult(intent, 100)
                }
            }
            4 -> {
                // Slalom - COPIE EXACTE du code Biathlon
                if (events[4].implemented) {
                    val intent = Intent(this, SlalomActivity::class.java).apply {
                        putExtra("tournament_data", tournamentData)
                        putExtra("event_index", eventIndex)
                        putExtra("number_of_players", numberOfPlayers)
                        putExtra("practice_mode", practiceMode)
                        putExtra("current_player_index", if (practiceMode) 0 else tournamentData.getNextPlayer(eventIndex))
                    }
                    startActivityForResult(intent, 100)
                }
            }
            5 -> {
                // Snowboard Halfpipe - COPIE EXACTE du code Biathlon
                if (events[5].implemented) {
                    val intent = Intent(this, SnowboardHalfpipeActivity::class.java).apply {
                        putExtra("tournament_data", tournamentData)
                        putExtra("event_index", eventIndex)
                        putExtra("number_of_players", numberOfPlayers)
                        putExtra("practice_mode", practiceMode)
                        putExtra("current_player_index", if (practiceMode) 0 else tournamentData.getNextPlayer(eventIndex))
                    }
                    startActivityForResult(intent, 100)
                }
            }
            6 -> {
                // Ski Freestyle - COPIE EXACTE du code Biathlon
                if (events[6].implemented) {
                    val intent = Intent(this, SkiFreestyleActivity::class.java).apply {
                        putExtra("tournament_data", tournamentData)
                        putExtra("event_index", eventIndex)
                        putExtra("number_of_players", numberOfPlayers)
                        putExtra("practice_mode", practiceMode)
                        putExtra("current_player_index", if (practiceMode) 0 else tournamentData.getNextPlayer(eventIndex))
                    }
                    startActivityForResult(intent, 100)
                }
            }
            7 -> {
                // Luge - COPIE EXACTE du code Biathlon
                if (events[7].implemented) {
                    val intent = Intent(this, LugeActivity::class.java).apply {
                        putExtra("tournament_data", tournamentData)
                        putExtra("event_index", eventIndex)
                        putExtra("number_of_players", numberOfPlayers)
                        putExtra("practice_mode", practiceMode)
                        putExtra("current_player_index", if (practiceMode) 0 else tournamentData.getNextPlayer(eventIndex))
                    }
                    startActivityForResult(intent, 100)
                }
            }
            else -> {
                Toast.makeText(this, "Cette épreuve n'est pas encore implémentée", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showScoreboard() {
        val intent = Intent(this, ScoreboardActivity::class.java).apply {
            putExtra("tournament_data", tournamentData)
        }
        startActivity(intent)
    }
    
    private fun resetTournament() {
        finish()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == 100 && resultCode == RESULT_OK) {
            data?.getSerializableExtra("tournament_data")?.let {
                tournamentData = it as TournamentData
                eventsLayout.removeAllViews()
                createEventsList()
                setupUI()
            }
        }
    }
    
    data class Event(
        val name: String,
        val icon: String,
        val description: String,
        val implemented: Boolean
    )
    
    enum class EventStatus {
        LOCKED, AVAILABLE, IN_PROGRESS, COMPLETED
    }
}
