// MainMenuActivity.kt – Menu principal pour démarrer le tournoi
package com.example.windbird

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*

class MainMenuActivity : Activity() {

    private lateinit var spinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(50, 50, 50, 50)
        }

        val title = TextView(this).apply {
            text = "❄️ JEUX D'HIVER 2025 ❄️"
            textSize = 32f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }

        val subtitle = TextView(this).apply {
            text = "Combien de joueurs ?"
            textSize = 20f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, 20)
            gravity = Gravity.CENTER
        }

        spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainMenuActivity,
                android.R.layout.simple_spinner_dropdown_item,
                (1..4).map { "$it joueur${if (it > 1) "s" else ""}" }
            )
        }
        
        val spinnerParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 80
        ).apply {
            setMargins(0, 0, 0, 40)
        }
        spinner.layoutParams = spinnerParams

        val practiceButton = Button(this).apply {
            text = "🎯 MODE PRATIQUE"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0066cc"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(20, 15, 20, 15)
            
            setOnClickListener { startPracticeMode() }
        }
        
        val practiceParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 80
        ).apply {
            setMargins(0, 0, 0, 15)
        }
        practiceButton.layoutParams = practiceParams

        val tournamentButton = Button(this).apply {
            text = "🏆 MODE TOURNOI"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#ff6600"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(20, 15, 20, 15)
            
            setOnClickListener { startTournament() }
        }
        
        val tournamentParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 80
        )
        tournamentButton.layoutParams = tournamentParams

        layout.apply {
            addView(title)
            addView(subtitle)
            addView(spinner)
            addView(practiceButton)
            addView(tournamentButton)
        }

        setContentView(layout, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }
    
    private fun startPracticeMode() {
        // Mode pratique - va direct au menu des épreuves
        val intent = Intent(this, EventsMenuActivity::class.java).apply {
            putExtra("practice_mode", true)
            putStringArrayListExtra("player_names", arrayListOf("Joueur"))
            putStringArrayListExtra("player_countries", arrayListOf("🇨🇦 Canada"))
            putExtra("number_of_players", 1)
        }
        startActivity(intent)
        finish()
    }
    
    private fun startTournament() {
        val selectedPlayers = spinner.selectedItemPosition + 1
        val intent = Intent(this, PlayerRegistrationActivity::class.java).apply {
            putExtra("number_of_players", selectedPlayers)
        }
        startActivity(intent)
        finish()
    }
}
