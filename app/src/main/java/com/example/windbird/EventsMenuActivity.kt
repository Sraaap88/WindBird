package com.example.windbird

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import android.graphics.Color
import android.view.ViewGroup
import android.view.View

class EventsMenuActivity : Activity() {
    
    private lateinit var playerNames: ArrayList<String>
    private lateinit var playerCountries: ArrayList<String>
    private var numberOfPlayers = 1
    
    // Données du tournoi
    private lateinit var tournamentData: TournamentData
    private lateinit var eventsLayout: LinearLayout
    private lateinit var statusText: TextView
    
    // Liste des épreuves
    private val events = arrayOf(
        Event("Biathlon", "🎯", "Ski de fond + tir de précision", true),
        Event("Saut à Ski", "🎿", "Envol et atterrissage parfait", false),
        Event("Bobsleigh", "🛷", "Descente à haute vitesse", false),
        Event("Patinage Vitesse", "⛸️", "Course sur glace", false),
        Event("Slalom", "⛷️", "Zigzag entre les portes", false),
        Event("Snowboard Halfpipe", "🏂", "Figures aériennes", false),
        Event("Ski Freestyle", "🎿", "Acrobaties en vol", false),
        Event("Luge", "🛷", "Contrôle de trajectoire", false),
        Event("Curling", "🥌", "Précision et stratégie", false),
        Event("Hockey sur Glace", "🏒", "Tirs au but", false)
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        // Récupérer les données des joueurs
        playerNames = intent.getStringArrayListExtra("player_names") ?: arrayListOf()
        playerCountries = intent.getStringArrayListExtra("player_countries") ?: arrayListOf()
        numberOfPlayers = intent.getIntExtra("number_of_players", 1)
        
        // Compléter avec l'IA si nécessaire
        while (playerNames.size < 4) {
            playerNames.add("IA ${playerNames.size + 1}")
            playerCountries.add("🤖 Intelligence Artificielle")
        }
        
        // Initialiser les données du tournoi
        tournamentData = TournamentData(playerNames, playerCountries)
        
        setupUI()
    }
    
    private fun setupUI() {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(20, 20, 20, 20)
        }
        
        // En-tête avec titre et statut
        createHeader(mainLayout)
        
        // Liste des joueurs
        createPlayersList(mainLayout)
        
        // Liste des épreuves
        eventsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        val scrollView = ScrollView(this).apply {
            addView(eventsLayout)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        mainLayout.addView(scrollView)
        
        createEventsList()
        
        // Boutons de navigation
        createNavigationButtons(mainLayout)
        
        setContentView(mainLayout)
    }
    
    private fun createHeader(parent: LinearLayout) {
        val titleText = TextView(this).apply {
            text = "🏆 WINTER GAMES TOURNAMENT 🏆"
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        parent.addView(titleText)
        
        statusText = TextView(this).apply {
            text = "Sélectionnez une épreuve pour commencer"
            textSize = 14f
            setTextColor(Color.CYAN)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        parent.addView(statusText)
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
        for (i in events.indices) {
            val event = events[i]
            val eventStatus = tournamentData.getEventStatus(i)
            
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
            
            // Icône et nom de l'épreuve
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
            
            // Statut de l'épreuve
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
            
            // Bouton jouer
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
        if (eventIndex == 0 && events[0].implemented) {
            // Biathlon - seule épreuve implémentée
            val intent = Intent(this, BiathlonActivity::class.java).apply {
                putExtra("tournament_data", tournamentData)
                putExtra("event_index", eventIndex)
                putExtra("number_of_players", numberOfPlayers)
            }
            startActivityForResult(intent, 100)
        } else {
            Toast.makeText(this, "Cette épreuve n'est pas encore implémentée", Toast.LENGTH_SHORT).show()
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
            // Récupérer les résultats de l'épreuve
            data?.getSerializableExtra("tournament_data")?.let {
                tournamentData = it as TournamentData
                // Rafraîchir l'affichage
                eventsLayout.removeAllViews()
                createEventsList()
                
                // Mettre à jour les scores des joueurs
                setupUI()
            }
        }
    }
    
    // Classes de données
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

// Classe pour gérer les données du tournoi
import java.io.Serializable

class TournamentData(
    val playerNames: ArrayList<String>,
    val playerCountries: ArrayList<String>
) : Serializable {
    
    // Scores par épreuve [joueur][épreuve] = score
    private val eventScores = Array(4) { Array(10) { -1 } }
    
    // Nombre d'essais par joueur par épreuve [joueur][épreuve] = essais
    private val attempts = Array(4) { Array(10) { 0 } }
    
    fun addScore(playerIndex: Int, eventIndex: Int, score: Int) {
        if (playerIndex in 0..3 && eventIndex in 0..9) {
            eventScores[playerIndex][eventIndex] = score
            attempts[playerIndex][eventIndex]++
        }
    }
    
    fun getScore(playerIndex: Int, eventIndex: Int): Int {
        return if (playerIndex in 0..3 && eventIndex in 0..9) {
            eventScores[playerIndex][eventIndex]
        } else -1
    }
    
    fun getTotalScore(playerIndex: Int): Int {
        return if (playerIndex in 0..3) {
            eventScores[playerIndex].filter { it > 0 }.sum()
        } else 0
    }
    
    fun getAttempts(playerIndex: Int, eventIndex: Int): Int {
        return if (playerIndex in 0..3 && eventIndex in 0..9) {
            attempts[playerIndex][eventIndex]
        } else 0
    }
    
    fun getEventStatus(eventIndex: Int): EventsMenuActivity.EventStatus {
        val totalAttempts = (0..3).sumOf { attempts[it][eventIndex] }
        val maxAttempts = (0..3).count { attempts[it][eventIndex] == 2 }
        
        return when {
            maxAttempts == 4 -> EventsMenuActivity.EventStatus.COMPLETED
            totalAttempts > 0 -> EventsMenuActivity.EventStatus.IN_PROGRESS
            eventIndex == 0 -> EventsMenuActivity.EventStatus.AVAILABLE // Biathlon toujours disponible
            else -> EventsMenuActivity.EventStatus.LOCKED // Autres épreuves verrouillées pour l'instant
        }
    }
    
    fun getNextPlayer(eventIndex: Int): Int {
        // Trouve le prochain joueur qui doit jouer cette épreuve
        for (player in 0..3) {
            if (attempts[player][eventIndex] < 2) {
                return player
            }
        }
        return -1 // Tous les joueurs ont terminé
    }
}
