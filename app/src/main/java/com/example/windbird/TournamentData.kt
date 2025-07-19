package com.example.windbird

import java.io.Serializable

class TournamentData(
    val playerNames: ArrayList<String>,
    val playerCountries: ArrayList<String>
) : Serializable {
    
    // MODIFIÉ : 9 épreuves (indices 0 à 8) au lieu de 10
    private val eventScores = Array(4) { Array(9) { -1 } }
    private val attempts = Array(4) { Array(9) { 0 } }
    
    fun addScore(playerIndex: Int, eventIndex: Int, score: Int) {
        if (playerIndex in 0..3 && eventIndex in 0..8) { // MODIFIÉ : 0..8 au lieu de 0..9
            eventScores[playerIndex][eventIndex] = score
            attempts[playerIndex][eventIndex]++
        }
    }
    
    fun getScore(playerIndex: Int, eventIndex: Int): Int {
        return if (playerIndex in 0..3 && eventIndex in 0..8) { // MODIFIÉ : 0..8 au lieu de 0..9
            eventScores[playerIndex][eventIndex]
        } else -1
    }
    
    fun getTotalScore(playerIndex: Int): Int {
        return if (playerIndex in 0..3) {
            eventScores[playerIndex].filter { it > 0 }.sum()
        } else 0
    }
    
    fun getAttempts(playerIndex: Int, eventIndex: Int): Int {
        return if (playerIndex in 0..3 && eventIndex in 0..8) { // MODIFIÉ : 0..8 au lieu de 0..9
            attempts[playerIndex][eventIndex]
        } else 0
    }
    
    // MODIFIÉ : Logique de déverrouillage séquentiel pour le mode tournoi - TOUTES LES ÉPREUVES IMPLÉMENTÉES
    fun getEventStatus(eventIndex: Int, practiceMode: Boolean = false): EventsMenuActivity.EventStatus {
        // En mode pratique, toutes les épreuves sont disponibles
        if (practiceMode) {
            return EventsMenuActivity.EventStatus.AVAILABLE // TOUTES disponibles maintenant
        }
        
        // En mode tournoi : déverrouillage séquentiel
        val totalAttempts = (0..3).sumOf { attempts[it][eventIndex] }
        val allCompleted = (0..3).all { attempts[it][eventIndex] >= 1 }
        
        return when {
            // Épreuve terminée
            allCompleted -> EventsMenuActivity.EventStatus.COMPLETED
            
            // Épreuve en cours
            totalAttempts > 0 -> EventsMenuActivity.EventStatus.IN_PROGRESS
            
            // Première épreuve (Biathlon) : toujours disponible
            eventIndex == 0 -> EventsMenuActivity.EventStatus.AVAILABLE
            
            // Épreuves suivantes : disponibles seulement si la précédente est terminée
            eventIndex > 0 -> {
                val previousEventCompleted = (0..3).all { attempts[it][eventIndex - 1] >= 1 }
                if (previousEventCompleted) {
                    // TOUTES LES ÉPREUVES SONT MAINTENANT IMPLÉMENTÉES
                    EventsMenuActivity.EventStatus.AVAILABLE
                } else {
                    EventsMenuActivity.EventStatus.LOCKED
                }
            }
            
            else -> EventsMenuActivity.EventStatus.LOCKED
        }
    }
    
    fun getNextPlayer(eventIndex: Int): Int {
        for (player in 0..3) {
            if (attempts[player][eventIndex] < 1) {
                return player
            }
        }
        return -1
    }
    
    // Méthodes de compatibilité
    fun getNumberOfPlayers(): Int = 4
    
    fun getNumberOfEvents(): Int = 9 // MODIFIÉ : 9 au lieu de 10
    
    fun getPlayerName(index: Int): String = playerNames.getOrElse(index) { "Joueur ${index + 1}" }
    
    fun getPlayerCountry(index: Int): String = playerCountries.getOrElse(index) { "?" }
    
    // MODIFIÉ : Vérifier si toutes les épreuves implémentées sont terminées
    fun isTournamentComplete(): Boolean {
        // Maintenant : TOUTES les 9 épreuves sont implémentées (0 à 8)
        val implementedEvents = (0..8).toList()
        
        return implementedEvents.all { eventIndex ->
            (0..3).all { playerIndex ->
                attempts[playerIndex][eventIndex] >= 1
            }
        }
    }
    
    // Obtenir le classement final du tournoi
    fun getFinalRanking(): List<PlayerFinalRanking> {
        val rankings = mutableListOf<PlayerFinalRanking>()
        
        for (i in 0..3) {
            val totalScore = getTotalScore(i)
            val medals = calculateTournamentMedals(i)
            val eventsCompleted = countCompletedEvents(i)
            
            rankings.add(PlayerFinalRanking(
                playerIndex = i,
                name = playerNames[i],
                country = playerCountries[i],
                totalScore = totalScore,
                goldMedals = medals[0],
                silverMedals = medals[1],
                bronzeMedals = medals[2],
                eventsCompleted = eventsCompleted
            ))
        }
        
        return rankings.sortedByDescending { it.totalScore }
    }
    
    private fun calculateTournamentMedals(playerIndex: Int): IntArray {
        val medals = intArrayOf(0, 0, 0)
        
        // MODIFIÉ : Boucle sur 9 épreuves (0 à 8)
        for (eventIndex in 0..8) {
            val scores = mutableListOf<Pair<Int, Int>>()
            
            for (i in 0..3) {
                val score = getScore(i, eventIndex)
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
        // MODIFIÉ : Boucle sur 9 épreuves (0 à 8)
        for (eventIndex in 0..8) {
            if (getScore(playerIndex, eventIndex) > 0) {
                count++
            }
        }
        return count
    }
    
    data class PlayerFinalRanking(
        val playerIndex: Int,
        val name: String,
        val country: String,
        val totalScore: Int,
        val goldMedals: Int,
        val silverMedals: Int,
        val bronzeMedals: Int,
        val eventsCompleted: Int
    )
}
