package com.example.windbird

import java.io.Serializable

class TournamentData(private val numberOfPlayers: Int, private val numberOfEvents: Int) : Serializable {

    private val scores = Array(numberOfPlayers) { IntArray(numberOfEvents) { -1 } }

    fun addScore(playerIndex: Int, eventIndex: Int, score: Int) {
        scores[playerIndex][eventIndex] = score
    }

    fun getNextPlayer(eventIndex: Int): Int {
        for (i in scores.indices) {
            if (scores[i][eventIndex] == -1) {
                return i
            }
        }
        return -1 // Tous les joueurs ont complété l'épreuve
    }

    fun getScore(playerIndex: Int, eventIndex: Int): Int {
        return scores[playerIndex][eventIndex]
    }

    fun getTotalScore(playerIndex: Int): Int {
        return scores[playerIndex].sumOf { it.coerceAtLeast(0) }
    }

    fun getScores(): Array<IntArray> {
        return scores
    }

    fun getNumberOfPlayers(): Int = numberOfPlayers

    fun getNumberOfEvents(): Int = numberOfEvents
}
