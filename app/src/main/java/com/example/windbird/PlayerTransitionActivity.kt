package com.example.windbird

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*

class PlayerTransitionActivity : Activity() {

    private lateinit var tournamentData: TournamentData
    private var eventIndex: Int = 0
    private var numberOfPlayers: Int = 1
    private var nextPlayerIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        tournamentData = intent.getSerializableExtra("tournament_data") as TournamentData
        eventIndex = intent.getIntExtra("event_index", 0)
        numberOfPlayers = intent.getIntExtra("number_of_players", 1)
        nextPlayerIndex = intent.getIntExtra("next_player_index", 0)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(50, 50, 50, 50)
        }

        val titleText = TextView(this).apply {
            text = "🏆 CHANGEMENT DE JOUEUR 🏆"
            textSize = 24f
            setTextColor(Color.YELLOW)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }
        layout.addView(titleText)

        val playerText = TextView(this).apply {
            text = "C'est maintenant au tour de :"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        layout.addView(playerText)

        val nextPlayerText = TextView(this).apply {
            text = "${tournamentData.playerNames[nextPlayerIndex]}"
            textSize = 32f
            setTextColor(Color.CYAN)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }
        layout.addView(nextPlayerText)

        val countryText = TextView(this).apply {
            text = "${tournamentData.playerCountries[nextPlayerIndex]}"
            textSize = 16f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }
        layout.addView(countryText)

        val instructionText = TextView(this).apply {
            text = "🎿 Préparez-vous pour l'épreuve de Biathlon!\n\n• Inclinez le téléphone pour skier\n• Secouez pour tirer sur les cibles"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(20, 20, 20, 40)
        }
        layout.addView(instructionText)

        val readyButton = Button(this).apply {
            text = "✅ JE SUIS PRÊT(E) !"
            textSize = 20f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#00aa00"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(30, 20, 30, 20)
            
            setOnClickListener {
                val intent = Intent(this@PlayerTransitionActivity, BiathlonActivity::class.java).apply {
                    putExtra("tournament_data", tournamentData)
                    putExtra("event_index", eventIndex)
                    putExtra("number_of_players", numberOfPlayers)
                    putExtra("current_player_index", nextPlayerIndex)
                }
                startActivity(intent)
                finish()
            }
        }
        
        val buttonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        readyButton.layoutParams = buttonParams
        layout.addView(readyButton)

        setContentView(layout)
    }
}
