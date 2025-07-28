package com.example.windbird

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import android.graphics.Color
import android.view.ViewGroup
import android.view.Gravity

class ScoreboardActivity : Activity() {
    
    private lateinit var tournamentData: TournamentData
    private lateinit var mainLayout: LinearLayout
    
    private val eventNames = arrayOf(
        "Biathlon", "Saut à Ski", "Bobsleigh", "Patinage Vitesse", 
        "Slalom", "Snowboard Halfpipe", "Ski Freestyle", "Luge"
        // MODIFIÉ : 8 épreuves au lieu de 9 (retiré Curling)
    )
    
    private val medals = arrayOf("🥇", "🥈", "🥉", "🏅")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        tournamentData = intent.getSerializableExtra("tournament_data") as TournamentData
        val eventCompleted = intent.getIntExtra("event_completed", -1)
        val tournamentFinal = intent.getBooleanExtra("tournament_final", false)
        
        setupUI(eventCompleted, tournamentFinal)
    }
    
    private fun setupUI(eventCompleted: Int, tournamentFinal: Boolean) {
        val scrollView = ScrollView(this)
        
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(20, 20, 20, 20)
        }
        
        val titleText = TextView(this).apply {
            text = when {
                tournamentFinal -> "🏆 CLASSEMENT FINAL DU TOURNOI 🏆"
                eventCompleted >= 0 -> "🏆 RÉSULTATS ${eventNames[eventCompleted].toUpperCase()} 🏆"
                else -> "🏆 TABLEAU DES SCORES 🏆"
            }
            textSize = 28f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }
        mainLayout.addView(titleText)
        
        if (tournamentFinal) {
            // Afficher le grand podium final du tournoi
            showTournamentFinalPodium(mainLayout)
        } else if (eventCompleted >= 0) {
            // Afficher le podium pour l'épreuve terminée
            showEventPodium(mainLayout, eventCompleted)
        } else {
            // Afficher les onglets normaux
            createTabs()
            
            val contentLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            
            showGeneralRanking(contentLayout)
            mainLayout.addView(contentLayout)
        }
        
        val backButton = Button(this).apply {
            text = when {
                tournamentFinal -> "🎉 RETOUR AU MENU PRINCIPAL"
                eventCompleted >= 0 -> "➡️ PROCHAINE ÉPREUVE"
                else -> "↩️ RETOUR AU MENU"
            }
            textSize = 16f
            setBackgroundColor(when {
                tournamentFinal -> Color.parseColor("#aa0066")
                eventCompleted >= 0 -> Color.parseColor("#00aa00")
                else -> Color.parseColor("#666666")
            })
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(20, 15, 20, 15)
            setOnClickListener { 
                if (tournamentFinal) {
                    // Retourner au menu principal après le tournoi final
                    val intent = Intent(this@ScoreboardActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                } else if (eventCompleted >= 0) {
                    // Retourner au menu des épreuves
                    val intent = Intent(this@ScoreboardActivity, EventsMenuActivity::class.java).apply {
                        putExtra("tournament_data", tournamentData)
                        putExtra("player_names", tournamentData.playerNames)
                        putExtra("player_countries", tournamentData.playerCountries)
                        putExtra("number_of_players", tournamentData.playerNames.size)
                    }
                    startActivity(intent)
                }
                finish() 
            }
        }
        
        val buttonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 20, 0, 0)
        }
        backButton.layoutParams = buttonParams
        mainLayout.addView(backButton)
        
        scrollView.addView(mainLayout)
        setContentView(scrollView)
    }
    
    private fun showTournamentFinalPodium(parent: LinearLayout) {
        // Récupérer le classement final du tournoi
        val finalRankings = tournamentData.getFinalRanking()
        
        if (finalRankings.isEmpty()) {
            val noResultText = TextView(this).apply {
                text = "Aucun résultat disponible"
                textSize = 18f
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                setPadding(0, 50, 0, 50)
            }
            parent.addView(noResultText)
            return
        }
        
        val congratsTitle = TextView(this).apply {
            text = "🎊 FÉLICITATIONS ! TOURNOI TERMINÉ ! 🎊"
            textSize = 26f
            setTextColor(Color.YELLOW)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 30)
        }
        parent.addView(congratsTitle)
        
        val winnerText = TextView(this).apply {
            text = "🏆 GRAND CHAMPION : ${finalRankings[0].name} 🏆"
            textSize = 22f
            setTextColor(Color.parseColor("#FFD700"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        parent.addView(winnerText)
        
        // Décompte de médailles
        val medalCountTitle = TextView(this).apply {
            text = "📊 DÉCOMPTE FINAL DES MÉDAILLES"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 20)
        }
        parent.addView(medalCountTitle)
        
        // Affichage du décompte pour chaque joueur
        for (i in finalRankings.indices) {
            val ranking = finalRankings[i]
            
            val playerMedalLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(20, 15, 20, 15)
                setBackgroundColor(
                    when (i) {
                        0 -> Color.parseColor("#FFD700")
                        1 -> Color.parseColor("#C0C0C0")
                        2 -> Color.parseColor("#CD7F32")
                        else -> Color.parseColor("#444444")
                    }
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 5, 0, 5)
                }
            }
            
            val positionText = TextView(this).apply {
                text = "${i + 1}."
                textSize = 20f
                setTextColor(if (i < 3) Color.BLACK else Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(50, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            playerMedalLayout.addView(positionText)
            
            val playerInfo = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val nameText = TextView(this).apply {
                text = ranking.name
                textSize = 18f
                setTextColor(if (i < 3) Color.BLACK else Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            playerInfo.addView(nameText)
            
            val countryText = TextView(this).apply {
                text = ranking.country
                textSize = 14f
                setTextColor(if (i < 3) Color.parseColor("#333333") else Color.LTGRAY)
            }
            playerInfo.addView(countryText)
            
            playerMedalLayout.addView(playerInfo)
            
            val medalInfo = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
            }
            
            val medalText = TextView(this).apply {
                text = "🥇${ranking.goldMedals} 🥈${ranking.silverMedals} 🥉${ranking.bronzeMedals}"
                textSize = 16f
                setTextColor(if (i < 3) Color.BLACK else Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.END
            }
            medalInfo.addView(medalText)
            
            val scoreText = TextView(this).apply {
                text = "${ranking.totalScore} pts"
                textSize = 14f
                setTextColor(if (i < 3) Color.parseColor("#333333") else Color.CYAN)
                gravity = Gravity.END
            }
            medalInfo.addView(scoreText)
            
            playerMedalLayout.addView(medalInfo)
            parent.addView(playerMedalLayout)
        }
        
        // Message final
        val finalMessage = TextView(this).apply {
            text = "🎉 Merci d'avoir participé aux Winter Games 2025 ! 🎉"
            textSize = 18f
            setTextColor(Color.CYAN)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(20, 30, 20, 20)
        }
        parent.addView(finalMessage)
    }
    
    private fun showEventPodium(parent: LinearLayout, eventIndex: Int) {
        // Récupérer les résultats de l'épreuve
        val eventResults = mutableListOf<EventResult>()
        
        for (playerIndex in 0..3) {
            val score = tournamentData.getScore(playerIndex, eventIndex)
            if (score > 0) {
                eventResults.add(EventResult(
                    playerIndex = playerIndex,
                    name = tournamentData.playerNames[playerIndex],
                    country = tournamentData.playerCountries[playerIndex],
                    score = score,
                    attempts = 1
                ))
            }
        }
        
        if (eventResults.isEmpty()) {
            val noResultText = TextView(this).apply {
                text = "Aucun résultat disponible"
                textSize = 18f
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                setPadding(0, 50, 0, 50)
            }
            parent.addView(noResultText)
            return
        }
        
        // Trier par score décroissant
        eventResults.sortByDescending { it.score }
        
        val podiumTitle = TextView(this).apply {
            text = "🥇 PODIUM DE L'ÉPREUVE 🥇"
            textSize = 24f
            setTextColor(Color.YELLOW)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 30)
        }
        parent.addView(podiumTitle)
        
        // Créer le podium visuel
        val podiumLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 40)
        }
        
        val podiumPositions = if (eventResults.size >= 3) listOf(1, 0, 2) else listOf(0)
        val podiumHeights = arrayOf(100, 140, 80)
        val podiumColors = arrayOf(Color.parseColor("#C0C0C0"), Color.parseColor("#FFD700"), Color.parseColor("#CD7F32"))
        val medals = arrayOf("🥈", "🥇", "🥉")
        
        for (i in podiumPositions.indices) {
            if (i < eventResults.size) {
                val rankIndex = podiumPositions[i]
                val result = eventResults[rankIndex]
                
                val podiumColumn = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(10, 0, 10, 0)
                    }
                }
                
                // Médaille et position
                val medalText = TextView(this).apply {
                    text = "${medals[rankIndex]} ${rankIndex + 1}${when(rankIndex) { 0 -> "er"; else -> "ème" }}"
                    textSize = 32f
                    setTextColor(podiumColors[rankIndex])
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 10)
                }
                podiumColumn.addView(medalText)
                
                // Nom du joueur
                val nameText = TextView(this).apply {
                    text = result.name
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                }
                podiumColumn.addView(nameText)
                
                // Pays
                val countryText = TextView(this).apply {
                    text = result.country
                    textSize = 12f
                    setTextColor(Color.LTGRAY)
                    gravity = Gravity.CENTER
                    setPadding(0, 5, 0, 10)
                }
                podiumColumn.addView(countryText)
                
                // Score
                val scoreText = TextView(this).apply {
                    text = "${result.score} pts"
                    textSize = 18f
                    setTextColor(Color.CYAN)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, 15)
                }
                podiumColumn.addView(scoreText)
                
                // Socle du podium
                val podium = TextView(this).apply {
                    text = "${rankIndex + 1}"
                    textSize = 24f
                    setTextColor(Color.BLACK)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setBackgroundColor(podiumColors[rankIndex])
                    layoutParams = LinearLayout.LayoutParams(100, podiumHeights[rankIndex])
                }
                podiumColumn.addView(podium)
                
                podiumLayout.addView(podiumColumn)
            }
        }
        
        parent.addView(podiumLayout)
        
        // Message de félicitations
        if (eventResults.isNotEmpty()) {
            val congratsText = TextView(this).apply {
                text = "🎉 Félicitations à ${eventResults[0].name} pour cette victoire ! 🎉"
                textSize = 16f
                setTextColor(Color.YELLOW)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(20, 30, 20, 20)
            }
            parent.addView(congratsText)
        }
    }
    
    private fun createTabs() {
        val tabLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 20)
        }
        
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
        val contentLayout = mainLayout.getChildAt(2) as LinearLayout
        contentLayout.removeAllViews()
        showGeneralRanking(contentLayout)
    }
    
    private fun showEventsTab() {
        val contentLayout = mainLayout.getChildAt(2) as LinearLayout
        contentLayout.removeAllViews()
        showEventDetails(contentLayout)
    }
    
    private fun showGeneralRanking(parent: LinearLayout) {
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
        
        playerRankings.sortByDescending { it.totalScore }
        
        val rankingTitle = TextView(this).apply {
            text = "🏆 CLASSEMENT GÉNÉRAL 🏆"
            textSize = 20f
            setTextColor(Color.YELLOW)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        parent.addView(rankingTitle)
        
        createPodium(parent, playerRankings)
        createDetailedRanking(parent, playerRankings)
    }
    
    private fun createPodium(parent: LinearLayout, rankings: List<PlayerRanking>) {
        val podiumLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 30)
        }
        
        val podiumOrder = if (rankings.size >= 3) listOf(1, 0, 2) else listOf(0)
        val podiumHeights = arrayOf(120, 150, 100)
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
                
                val positionText = TextView(this).apply {
                    text = "${medals[rankIndex]} ${rankIndex + 1}${when(rankIndex) { 0 -> "er"; else -> "ème" }}"
                    textSize = 24f
                    setTextColor(podiumColors[rankIndex])
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(0, 10, 0, 0)
                }
                podiumItem.addView(positionText)
                
                val scoreText = TextView(this).apply {
                    text = "${ranking.totalScore} pts"
                    textSize = 16f
                    setTextColor(Color.CYAN)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                }
                podiumItem.addView(scoreText)
                
                val medalsText = TextView(this).apply {
                    text = "🥇${ranking.goldMedals} 🥈${ranking.silverMedals} 🥉${ranking.bronzeMedals}"
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    setPadding(0, 5, 0, 0)
                }
                podiumItem.addView(medalsText)
                
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
                        0 -> Color.parseColor("#FFD700")
                        1 -> Color.parseColor("#C0C0C0")
                        2 -> Color.parseColor("#CD7F32")
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
            
            val positionText = TextView(this).apply {
                text = "${i + 1}"
                textSize = 24f
                setTextColor(if (i < 3) Color.BLACK else Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(60, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            playerLayout.addView(positionText)
            
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
                text = "Épreuves: ${ranking.eventsCompleted}/8" // MODIFIÉ : 8 au lieu de 9
                textSize = 11f
                setTextColor(if (i < 3) Color.parseColor("#444444") else Color.LTGRAY)
            }
            playerInfo.addView(statsText)
            
            playerLayout.addView(playerInfo)
            
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
        
        // MODIFIÉ : Boucle sur 8 épreuves (0 à 7) au lieu de 9 (0 à 8)
        for (eventIndex in 0..7) {
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
            
            val eventNameText = TextView(this).apply {
                text = "🏅 ${eventNames[eventIndex]}"
                textSize = 18f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 0, 10)
            }
            eventLayout.addView(eventNameText)
            
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
                eventResults.sortByDescending { it.score }
                
                for (i in eventResults.indices) {
                    val result = eventResults[i]
                    
                    val resultLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(10, 8, 10, 8)
                        setBackgroundColor(
                            when (i) {
                                0 -> Color.parseColor("#FFD700")
                                1 -> Color.parseColor("#C0C0C0")
                                2 -> Color.parseColor("#CD7F32")
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
                    
                    val positionText = TextView(this).apply {
                        text = "${medals[i]} ${i + 1}"
                        textSize = 14f
                        setTextColor(if (i < 3) Color.BLACK else Color.WHITE)
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        layoutParams = LinearLayout.LayoutParams(60, ViewGroup.LayoutParams.WRAP_CONTENT)
                    }
                    resultLayout.addView(positionText)
                    
                    val nameText = TextView(this).apply {
                        text = "${result.name} (${result.country})"
                        textSize = 12f
                        setTextColor(if (i < 3) Color.BLACK else Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    resultLayout.addView(nameText)
                    
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
        val medals = intArrayOf(0, 0, 0)
        
        // MODIFIÉ : Boucle sur 8 épreuves (0 à 7)
        for (eventIndex in 0..7) {
            val scores = mutableListOf<Pair<Int, Int>>()
            
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
        // MODIFIÉ : Boucle sur 8 épreuves (0 à 7)
        for (eventIndex in 0..7) {
            if (tournamentData.getScore(playerIndex, eventIndex) > 0) {
                count++
            }
        }
        return count
    }
    
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
