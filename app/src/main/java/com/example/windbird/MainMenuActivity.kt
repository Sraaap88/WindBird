// MainMenuActivity.kt – Menu principal pour démarrer le tournoi
package com.example.windbird

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class MainMenuActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setPadding(50, 50, 50, 50)
        }

        val title = TextView(this).apply {
            text = "JEUX D'HIVER 2025"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Combien de joueurs ?"
            textSize = 20f
            setTextColor(Color.LTGRAY)
            setPadding(0, 40, 0, 10)
        }

        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainMenuActivity,
                android.R.layout.simple_spinner_dropdown_item,
                (1..4).map { "$it joueurs" }
            )
        }

        val startButton = Button(this).apply {
            text = "Commencer le tournoi >>"
            textSize = 18f
            setOnClickListener {
                val selectedPlayers = spinner.selectedItemPosition + 1
                val intent = Intent(this@MainMenuActivity, PlayerRegistrationActivity::class.java).apply {
                    putExtra("number_of_players", selectedPlayers)
                }
                startActivity(intent)
                finish()
            }
        }

        layout.apply {
            addView(title)
            addView(subtitle)
            addView(spinner)
            addView(startButton)
        }

        setContentView(layout, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }
}
