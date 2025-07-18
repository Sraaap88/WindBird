// PlayerRegistrationActivity.kt – Écran d'inscription des joueurs avec nom et pays
package com.example.windbird

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class PlayerRegistrationActivity : Activity() {

    private val countryOptions = arrayOf("Canada", "France", "USA", "Norvège", "Japon")
    private var numberOfPlayers = 2
    private val playerNames = mutableListOf<String>()
    private val playerCountries = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        numberOfPlayers = intent.getIntExtra("number_of_players", 2)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(30, 30, 30, 30)
        }

        val title = TextView(this).apply {
            text = "Inscription des joueurs"
            textSize = 26f
            setTextColor(Color.WHITE)
        }
        layout.addView(title)

        val playerForms = mutableListOf<Pair<EditText, Spinner>>()

        for (i in 1..numberOfPlayers) {
            val nameInput = EditText(this).apply {
                hint = "Nom du joueur $i"
                setTextColor(Color.BLACK) // TEXTE NOIR
                setHintTextColor(Color.LTGRAY)
                setBackgroundColor(Color.WHITE) // FOND BLANC
                setPadding(15, 10, 15, 10)
            }

            val countrySpinner = Spinner(this).apply {
                adapter = ArrayAdapter(this@PlayerRegistrationActivity, android.R.layout.simple_spinner_dropdown_item, countryOptions)
            }

            layout.addView(nameInput)
            layout.addView(countrySpinner)
            playerForms.add(Pair(nameInput, countrySpinner))
        }

        val startButton = Button(this).apply {
            text = "Démarrer le tournoi"
            setOnClickListener {
                val names = arrayListOf<String>()
                val countries = arrayListOf<String>()
                for ((nameField, countrySpinner) in playerForms) {
                    names.add(nameField.text.toString().ifBlank { "Anonyme" })
                    countries.add(countrySpinner.selectedItem.toString())
                }
                
                // Compléter avec l'IA si nécessaire
                while (names.size < 4) {
                    names.add("IA ${names.size + 1}")
                    countries.add("🤖 Intelligence Artificielle")
                }
                
                val tournamentData = TournamentData(names, countries)
                val intent = Intent(this@PlayerRegistrationActivity, BiathlonActivity::class.java)
                intent.putExtra("tournament_data", tournamentData)
                intent.putExtra("event_index", 0)
                intent.putExtra("number_of_players", numberOfPlayers)
                startActivity(intent)
                finish()
            }
        }

        layout.addView(startButton)
        setContentView(layout, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }
}
