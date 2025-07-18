package com.example.windbird

import java.io.Serializable

class TournamentData(
    val playerNames: ArrayList<String>,
    val playerCountries: ArrayList<String>
) : Serializable {
    
    private val eventScores = Array(4) { Array(10) { -1 } }
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
            eventIndex == 0 -> EventsMenuActivity.EventStatus.AVAILABLE
            else -> EventsMenuActivity.EventStatus.LOCKED
        }
    }
    
    fun getNextPlayer(eventIndex: Int): Int {
        for (player in 0..3) {
            if (attempts[player][eventIndex] < 2) {
                return player
            }
        }
        return -1
    }
    
    // Méthodes ajoutées pour la compatibilité avec ScoreTransitionActivity
    fun getNumberOfPlayers(): Int = 4
    
    fun getNumberOfEvents(): Int = 10
    
    fun getPlayerName(index: Int): String = playerNames.getOrElse(index) { "Joueur ${index + 1}" }
    
    fun getPlayerCountry(index: Int): String = playerCountries.getOrElse(index) { "?" }
}
