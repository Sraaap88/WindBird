// ScoreTransitionActivity.kt – Affiche les scores après une épreuve et lance la suivante
package com.example.windbird

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class ScoreTransitionActivity : Activity() {

    private lateinit var tournamentData: TournamentData
    private var eventIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tournamentData = intent.getSerializableExtra("tournament_data") as TournamentData
        eventIndex = intent.getIntExtra("event_index", 0)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(30, 30, 30, 30)
        }

        val title = TextView(this).apply {
            text = "Résultats - Épreuve ${eventIndex + 1}"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        layout.addView(title)

        for (i in 0 until tournamentData.getNumberOfPlayers()) {
            val score = tournamentData.getScore(i, eventIndex)
            val line = TextView(this).apply {
                text = "${i + 1}. ${tournamentData.getPlayerName(i)} (${tournamentData.getPlayerCountry(i)}) : $score pts"
                textSize = 20f
                setTextColor(Color.LTGRAY)
                setPadding(10, 20, 10, 20)
            }
            layout.addView(line)
        }

        val nextButton = Button(this).apply {
            text = "Prochaine épreuve >>"
            textSize = 18f
            setOnClickListener {
                val nextEventIndex = eventIndex + 1
                if (nextEventIndex < tournamentData.getNumberOfEvents()) {
                    val intent = Intent(this@ScoreTransitionActivity, BiathlonActivity::class.java) // Remplacer par la bonne activité
                    intent.putExtra("tournament_data", tournamentData)
                    intent.putExtra("event_index", nextEventIndex)
                    intent.putExtra("number_of_players", tournamentData.getNumberOfPlayers())
                    startActivity(intent)
                    finish()
                } else {
                    // Dernière épreuve terminée – on pourrait afficher un classement final ici
                    finish()
                }
            }
        }

        layout.addView(nextButton)
        setContentView(layout, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }
}
