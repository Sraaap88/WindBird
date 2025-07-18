package com.example.windbird

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import android.graphics.Color
import android.view.ViewGroup
import android.view.Gravity

class ScoreboardActivity : Activity() {
    
    private lateinit var tournamentData: TournamentData
    private lateinit var mainLayout: LinearLayout
    
    // Noms des épreuves pour l'affichage
    private val eventNames = arrayOf(
        "Biathlon", "Saut à Ski", "Bobsleigh", "Patinage Vitesse", 
        "Slalom", "Snowboard Halfpipe", "Ski Freestyle", "Luge",
        "Curling", "Hockey sur Glace"
    )
    
    // Émojis des médailles
    private val medals = arrayOf("🥇", "🥈", "🥉", "🏅")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        // Récupérer les données du tournoi
        tournamentData = intent.getSerializableExtra("tournament_data") as TournamentData
        
        setupUI()
    }
    
    private fun setupUI() {
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(20, 20, 20, 20)
        }
        
        // Titre
        val titleText = TextView(this).apply {
            text = "🏆 TABLEAU DES SCORES 🏆"
            textSize = 28f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }
        mainLayout.addView(titleText)
        
        // Onglets
        createTabs()
        
        // Contenu principal dans un ScrollView
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        
        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(contentLayout)
        mainLayout.addView(scrollView)
        
        // Affichage par défaut : classement général
        showGeneralRanking(contentLayout)
        
        // Bouton retour
        val backButton = Button(this).apply {
            text = "↩️ RETOUR AU MENU"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#666666"))
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(20, 15, 20, 15)
            setOnClickListener { finish() }
        }
        
        val buttonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 20, 0, 0)
        }
        backButton.layoutParams = buttonParams
        mainLayout.addView(backButton)
        
        setContentView(mainLayout)
    }
    
    private fun createTabs() {
        val tabLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 20)
        }
        
        // Onglet Classement Général
        val generalTab = Button(this).apply {
            text = "🏅 GÉNÉRAL"
            textSize = 14f
            setBackgroundColor(Color.parseColor("#004488"))
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, 5, 0)
            }
            setOnClickListener { showGeneralTab() }
        }
        tabLayout.addView(generalTab)
        
        // Onglet Par Épreuve
        val eventsTab = Button(this).apply {
            text = "🎯 PAR ÉPREUVE"
            textSize = 14f
            setBackgroundColor(Color.parseColor("#006600"))
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(5, 0, 0, 0)
            }
            setOnClickListener { showEventsTab() }
        }
        tabLayout.addView(eventsTab)
        
        mainLayout.addView(tabLayout)
    }
    
    private fun showGeneralTab() {
        // Rafraîchir l'affichage avec le classement général
        val scrollView = mainLayout.getChildAt(2) as ScrollView
        val contentLayout = scrollView.getChildAt(0) as LinearLayout
        contentLayout.removeAllViews()
        showGeneralRanking(contentLayout)
    }
    
    private fun showEventsTab() {
        // Rafraîchir l'affichage avec les résultats par épreuve
        val scrollView = mainLayout.getChildAt(2) as ScrollView
        val contentLayout = scrollView.getChildAt(0) as LinearLayout
        contentLayout.removeAllViews()
        showEventDetails(contentLayout)
    }
    
    private fun showGeneralRanking(parent: LinearLayout) {
        // Calculer le classement général
        val playerRankings = mutableListOf<PlayerRanking>()
        
        for (i in 0..3) {
            val totalScore = tournamentData.getTotalScore(i)
            val medals = calculateMedals(i)
            val eventsCompleted = countCompletedEvents(i)
            
            playerRankings.add(PlayerRanking(
                playerIndex = i,
                name = tournamentData.playerNames[i],
                country = tournamentData.playerCountries[i],
                totalScore = totalScore,
                goldMedals = medals[0],
                silverMedals = medals[1],
                bronzeMedals = medals[2],
                eventsCompleted = eventsCompleted
            ))
        }
        
        // Trier par score total décroissant
        playerRankings.sortByDescending { it.totalScore }
        
        // Titre du classement
        val rankingTitle = TextView(this).apply {
            text = "🏆 CLASSEMENT GÉNÉRAL 🏆"
            textSize = 20f
            setTextColor(Color.YELLOW)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        parent.addView(rankingTitle)
        
        // Podium (top 3)
        createPodium(parent, playerRankings)
        
        // Classement détaillé
        createDetailedRanking(parent, playerRankings)
    }
    
    private fun createPodium(parent: LinearLayout, rankings: List<PlayerRanking>) {
        val podiumLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 30)
        }
        
        // Afficher le podium : 2ème, 1er, 3ème
        val podiumOrder = if (rankings.size >= 3) listOf(1, 0, 2) else listOf(0)
        val podiumHeights = arrayOf(120, 150, 100) // Hauteurs différentes
        val podiumColors = arrayOf(Color.parseColor("#C0C0C0"), Color.parseColor("#FFD700"), Color.parseColor("#CD7F32"))
        
        for (i in podiumOrder.indices) {
            if (i < rankings.size) {
                val rankIndex = podiumOrder[i]
                val ranking = rankings[rankIndex]
                
                val podiumItem = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(10, 0, 10, 0)
                    }
                }
                
                // Nom et pays
                val nameText = TextView(this).apply {
                    text = ranking.name
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                }
                podiumItem.addView(nameText)
                
                val countryText = TextView(this).apply {
                    text = ranking.country
                    textSize = 10f
                    setTextColor(Color.LTGRAY)
                    gravity = Gravity.CENTER
                }
                podiumItem.addView(countryText)
                
                // Position et médaille
                val positionText = TextView(this).apply {
                    text = "${medals[rankIndex]} ${rankIndex + 1}${when(rankIndex) { 0 -> "er"; else -> "ème" }}"
                    textSize = 24f
                    setTextColor(podiumColors[rankIndex])
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(0, 10, 0, 0)
                }
                podiumItem.addView(positionText)
                
                // Score
                val scoreText = TextView(this).apply {
                    text = "${ranking.totalScore} pts"
                    textSize = 16f
                    setTextColor(Color.CYAN)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                }
                podiumItem.addView(scoreText)
                
                // Médailles
                val medalsText = TextView(this).apply {
                    text = "🥇${ranking.goldMedals} 🥈${ranking.silverMedals} 🥉${ranking.bronzeMedals}"
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(0, 5, 0, 0)
                }
                podiumItem.addView(medalsText)
                
                // Socle du podium
                val podium = TextView(this).apply {
                    text = "${rankIndex + 1}"
                    textSize = 20f
                    setTextColor(Color.BLACK)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setBackgroundColor(podiumColors[rankIndex])
                    layoutParams = LinearLayout.LayoutParams(80, podiumHeights[rankIndex]).apply {
                        setMargins(0, 10, 0, 0)
                    }
                }
                podiumItem.addView(podium)
                
                podiumLayout.addView(podiumItem)
            }
        }
        
        parent.addView(podiumLayout)
    }
    
    private fun createDetailedRanking(parent: LinearLayout, rankings: List<PlayerRanking>) {
        val detailTitle = TextView(this).apply {
            text = "📊 CLASSEMENT DÉTAILLÉ"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 15)
        }
        parent.addView(detailTitle)
        
        for (i in rankings.indices) {
            val ranking = rankings[i]
            
            val playerLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(15, 15, 15, 15)
                setBackgroundColor(
                    when (i) {
                        0 -> Color.parseColor("#FFD700") // Or
                        1 -> Color.parseColor("#C0C0C0") // Argent
                        2 -> Color.parseColor("#CD7F32") // Bronze
                        else -> Color.parseColor("#003366")
                    }
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 10)
                }
            }
            
            // Position
            val positionText = TextView(this).apply {
                text = "${i + 1}"
                textSize = 24f
                setTextColor(if (i < 3) Color.BLACK else Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(60, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            playerLayout.addView(positionText)
            
            // Info joueur
            val playerInfo = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val nameText = TextView(this).apply {
                text = ranking.name
                textSize = 16f
                setTextColor(if (i < 3) Color.BLACK else Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            playerInfo.addView(nameText)
            
            val countryText = TextView(this).apply {
                text = ranking.country
                textSize = 12f
                setTextColor(if (i < 3) Color.parseColor("#444444") else Color.LTGRAY)
            }
            playerInfo.addView(countryText)
            
            val statsText = TextView(this).apply {
                text = "Épreuves: ${ranking.eventsCompleted}/10"
                textSize = 11f
                setTextColor(if (i < 3) Color.parseColor("#444444") else Color.LTGRAY)
            }
            playerInfo.addView(statsText)
            
            playerLayout.addView(playerInfo)
            
            // Score et médailles
            val scoresLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
            }
            
            val scoreText = TextView(this).apply {
                text = "${ranking.totalScore} pts"
                textSize = 18f
                setTextColor(if (i < 3) Color.BLACK else Color.CYAN)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.END
            }
            scoresLayout.addView(scoreText)
            
            val medalsText = TextView(this).apply {
                text = "🥇${ranking.goldMedals} 🥈${ranking.silverMedals} 🥉${ranking.bronzeMedals}"
                textSize = 12f
                setTextColor(if (i < 3) Color.BLACK else Color.WHITE)
                gravity = Gravity.END
            }
            scoresLayout.addView(medalsText)
            
            playerLayout.addView(scoresLayout)
            parent.addView(playerLayout)
        }
    }
    
    private fun showEventDetails(parent: LinearLayout) {
        val eventTitle = TextView(this).apply {
            text = "🎯 RÉSULTATS PAR ÉPREUVE"
            textSize = 20f
            setTextColor(Color.YELLOW)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        parent.addView(eventTitle)
        
        for (eventIndex in 0..9) {
            val eventLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(15, 15, 15, 15)
                setBackgroundColor(Color.parseColor("#003366"))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 15)
                }
            }
            
            // Nom de l'épreuve
            val eventNameText = TextView(this).apply {
                text = "🏅 ${eventNames[eventIndex]}"
                textSize = 18f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 10)
            }
            eventLayout.addView(eventNameText)
            
            // Résultats pour cette épreuve
            val eventResults = mutableListOf<EventResult>()
            var hasResults = false
            
            for (playerIndex in 0..3) {
                val score = tournamentData.getScore(playerIndex, eventIndex)
                val attempts = tournamentData.getAttempts(playerIndex, eventIndex)
                
                if (score > 0) {
                    hasResults = true
                    eventResults.add(EventResult(
                        playerIndex = playerIndex,
                        name = tournamentData.playerNames[playerIndex],
                        country = tournamentData.playerCountries[playerIndex],
                        score = score,
                        attempts = attempts
                    ))
                }
            }
            
            if (hasResults) {
                // Trier par score décroissant
                eventResults.sortByDescending { it.score }
                
                // Afficher les résultats
                for (i in eventResults.indices) {
                    val result = eventResults[i]
                    
                    val resultLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(10, 8, 10, 8)
                        setBackgroundColor(
                            when (i) {
                                0 -> Color.parseColor("#FFD700") // Or
                                1 -> Color.parseColor("#C0C0C0") // Argent
                                2 -> Color.parseColor("#CD7F32") // Bronze
                                else -> Color.parseColor("#004488")
                            }
                        )
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 2, 0, 2)
                        }
                    }
                    
                    // Position et médaille
                    val positionText = TextView(this).apply {
                        text = "${medals[i]} ${i + 1}"
                        textSize = 14f
                        setTextColor(if (i < 3) Color.BLACK else Color.WHITE)
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        layoutParams = LinearLayout.LayoutParams(60, ViewGroup.LayoutParams.WRAP_CONTENT)
                    }
                    resultLayout.addView(positionText)
                    
                    // Nom du joueur
                    val nameText = TextView(this).apply {
                        text = "${result.name} (${result.country})"
                        textSize = 12f
                        setTextColor(if (i < 3) Color.BLACK else Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    resultLayout.addView(nameText)
                    
                    // Score
                    val scoreText = TextView(this).apply {
                        text = "${result.score} pts"
                        textSize = 14f
                        setTextColor(if (i < 3) Color.BLACK else Color.CYAN)
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        gravity = Gravity.END
                    }
                    resultLayout.addView(scoreText)
                    
                    eventLayout.addView(resultLayout)
                }
            } else {
                // Aucun résultat
                val noResultText = TextView(this).apply {
                    text = "Aucun résultat encore disponible"
                    textSize = 14f
                    setTextColor(Color.LTGRAY)
                    gravity = Gravity.CENTER
                    setPadding(0, 10, 0, 10)
                }
                eventLayout.addView(noResultText)
            }
            
            parent.addView(eventLayout)
        }
    }
    
    private fun calculateMedals(playerIndex: Int): IntArray {
        val medals = intArrayOf(0, 0, 0) // Or, Argent, Bronze
        
        for (eventIndex in 0..9) {
            val scores = mutableListOf<Pair<Int, Int>>() // (playerIndex, score)
            
            for (i in 0..3) {
                val score = tournamentData.getScore(i, eventIndex)
                if (score > 0) {
                    scores.add(Pair(i, score))
                }
            }
            
            if (scores.size >= 2) {
                scores.sortByDescending { it.second }
                
                for (i in 0 until minOf(3, scores.size)) {
                    if (scores[i].first == playerIndex) {
                        medals[i]++
                        break
                    }
                }
            }
        }
        
        return medals
    }
    
    private fun countCompletedEvents(playerIndex: Int): Int {
        var count = 0
        for (eventIndex in 0..9) {
            if (tournamentData.getScore(playerIndex, eventIndex) > 0) {
                count++
            }
        }
        return count
    }
    
    // Classes de données
    data class PlayerRanking(
        val playerIndex: Int,
        val name: String,
        val country: String,
        val totalScore: Int,
        val goldMedals: Int,
        val silverMedals: Int,
        val bronzeMedals: Int,
        val eventsCompleted: Int
    )
    
    data class EventResult(
        val playerIndex: Int,
        val name: String,
        val country: String,
        val score: Int,
        val attempts: Int
    )
}
