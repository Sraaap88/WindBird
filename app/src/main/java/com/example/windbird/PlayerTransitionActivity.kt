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

    // AJOUTÉ : Informations sur les épreuves - TOUTES LES 9 ÉPREUVES
    private val eventNames = arrayOf(
        "Biathlon", "Saut à Ski", "Bobsleigh", "Patinage Vitesse", 
        "Slalom", "Snowboard Halfpipe", "Ski Freestyle", "Luge", "Curling"
    )
    
    private val eventIcons = arrayOf(
        "🎯", "🎿", "🛷", "⛸️", "⛷️", "🏂", "🎿", "🛷", "🥌"
    )
    
    private val eventInstructions = arrayOf(
        "• Inclinez le téléphone pour skier\n• Secouez pour tirer sur les cibles",
        "• Inclinez vers l'avant pour l'élan\n• Redressez pour le décollage\n• Stabilisez les 3 axes en vol",
        "• Secouez pour la poussée de départ\n• Inclinez gauche/droite pour diriger\n• Avant/arrière pour vitesse/freinage",
        "• Inclinez le téléphone pour patiner\n• Secouez pour accélérer sur la glace",
        "• Inclinez gauche/droite pour zigzaguer\n• Secouez pour maintenir la vitesse",
        "• Inclinez pour les figures aériennes\n• Secouez pour les rotations",
        "• Inclinez pour les acrobaties\n• Secouez pour les figures en vol",
        "• Inclinez pour contrôler la trajectoire\n• Secouez pour la vitesse de descente",
        "• Inclinez pour viser la pierre\n• Secouez pour ajuster la force"
    )

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

        // MODIFIÉ : Affichage de l'épreuve actuelle
        val eventText = TextView(this).apply {
            text = "ÉPREUVE : ${eventIcons[eventIndex]} ${eventNames[eventIndex]}"
            textSize = 20f
            setTextColor(Color.CYAN)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        layout.addView(eventText)

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

        // MODIFIÉ : Instructions spécifiques à l'épreuve
        val instructionText = TextView(this).apply {
            text = "${eventIcons[eventIndex]} Préparez-vous pour l'épreuve de ${eventNames[eventIndex]}!\n\n${eventInstructions[eventIndex]}"
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
                startEventActivity()
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
    
    // CORRIGÉ : Méthode pour démarrer la bonne activité selon l'épreuve - TOUTES LES 9 ÉPREUVES
    private fun startEventActivity() {
        val intent = when (eventIndex) {
            0 -> {
                // Biathlon
                Intent(this, BiathlonActivity::class.java)
            }
            1 -> {
                // Saut à Ski
                Intent(this, SkiJumpActivity::class.java)
            }
            2 -> {
                // Bobsleigh
                Intent(this, BobsledActivity::class.java)
            }
            3 -> {
                // Patinage Vitesse
                Intent(this, PatinageVitesseActivity::class.java)
            }
            4 -> {
                // Slalom
                Intent(this, SlalomActivity::class.java)
            }
            5 -> {
                // Snowboard Halfpipe
                Intent(this, SnowboardHalfpipeActivity::class.java)
            }
            6 -> {
                // Ski Freestyle
                Intent(this, SkiFreestyleActivity::class.java)
            }
            7 -> {
                // Luge
                Intent(this, LugeActivity::class.java)
            }
            8 -> {
                // Curling
                Intent(this, CurlingActivity::class.java)
            }
            else -> {
                // Fallback - retourner au Biathlon par défaut
                Toast.makeText(this, "Épreuve pas encore implémentée, retour au Biathlon", Toast.LENGTH_SHORT).show()
                Intent(this, BiathlonActivity::class.java)
            }
        }
        
        intent.apply {
            putExtra("tournament_data", tournamentData)
            putExtra("event_index", eventIndex)
            putExtra("number_of_players", numberOfPlayers)
            putExtra("current_player_index", nextPlayerIndex)
        }
        
        startActivity(intent)
        finish()
    }
}
