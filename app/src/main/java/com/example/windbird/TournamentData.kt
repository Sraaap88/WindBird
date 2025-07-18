// TournamentData.kt – Stocke toutes les infos du tournoi
package com.example.windbird

import java.io.Serializable

class TournamentData(
    private val numberOfPlayers: Int,
    private val numberOfEvents: Int,
    private val playerNames: List<String>,
    private val playerCountries: List<String>
) : Serializable {

    private val scores = Array(numberOfPlayers) { IntArray(numberOfEvents) { -1 } }

    fun addScore(playerIndex: Int, eventIndex: Int, score: Int) {
        scores[playerIndex][eventIndex] = score
    }

    fun getScore(playerIndex: Int, eventIndex: Int): Int {
        return scores[playerIndex][eventIndex]
    }

    fun getTotalScore(playerIndex: Int): Int {
        return scores[playerIndex].sumOf { it.coerceAtLeast(0) }
    }

    fun getScores(): Array<IntArray> = scores

    fun getNumberOfPlayers(): Int = numberOfPlayers

    fun getNumberOfEvents(): Int = numberOfEvents

    fun getPlayerName(index: Int): String = playerNames.getOrElse(index) { "Joueur ${index + 1}" }

    fun getPlayerCountry(index: Int): String = playerCountries.getOrElse(index) { "?" }

    fun getNextPlayer(eventIndex: Int): Int {
        for (i in 0 until numberOfPlayers) {
            if (scores[i][eventIndex] == -1) {
                return i
            }
        }
        return -1 // Tous les joueurs ont terminé l'épreuve
    }
}
