// ScoreTransitionActivity.kt – Affiche les scores après chaque épreuve
package com.example.windbird

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class ScoreTransitionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tournamentData = intent.getSerializableExtra("tournament_data") as? TournamentData
        val eventIndex = intent.getIntExtra("event_index", 0)

        if (tournamentData == null) {
            finish()
            return
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(50, 50, 50, 50)
        }

        val title = TextView(this).apply {
            text = "Scores après l'épreuve ${eventIndex + 1}"
            textSize = 24f
            setTextColor(Color.WHITE)
        }
        layout.addView(title)

        for (i in 0 until tournamentData.getNumberOfPlayers()) {
            val name = tournamentData.getPlayerName(i)
            val country = tournamentData.getPlayerCountry(i)
            val score = tournamentData.getScore(i, eventIndex)

            val row = TextView(this).apply {
                text = "$name ($country) : $score points"
                setTextColor(Color.LTGRAY)
                textSize = 18f
                setPadding(0, 10, 0, 10)
            }
            layout.addView(row)
        }

        val nextButton = Button(this).apply {
            text = "Prochaine épreuve >>"
            setOnClickListener {
                val nextEventIndex = eventIndex + 1
                if (nextEventIndex < tournamentData.getNumberOfEvents()) {
                    val intent = Intent(this@ScoreTransitionActivity, BiathlonActivity::class.java).apply {
                        putExtra("tournament_data", tournamentData)
                        putExtra("event_index", nextEventIndex)
                        putExtra("number_of_players", tournamentData.getNumberOfPlayers())
                    }
                    startActivity(intent)
                } else {
                    val intent = Intent(this@ScoreTransitionActivity, ScoreboardActivity::class.java).apply {
                        putExtra("tournament_data", tournamentData)
                    }
                    startActivity(intent)
                }
                finish()
            }
        }

        layout.addView(nextButton)
        setContentView(layout, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }
}
