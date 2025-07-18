package com.example.windbird

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import android.graphics.Color
import android.view.ViewGroup
import android.view.View

class MainActivity : Activity() {
    
    private lateinit var playerCountSpinner: Spinner
    private lateinit var playerSetupLayout: LinearLayout
    private lateinit var startButton: Button
    
    private val playerNames = mutableListOf<String>()
    private val playerCountries = mutableListOf<String>()
    private var numberOfPlayers = 1
    
    // Pays disponibles avec leurs drapeaux (émojis)
    private val countries = arrayOf(
        "🇨🇦 Canada", "🇺🇸 États-Unis", "🇳🇴 Norvège", "🇸🇪 Suède", 
        "🇫🇮 Finlande", "🇩🇪 Allemagne", "🇫🇷 France", "🇮🇹 Italie",
        "🇦🇹 Autriche", "🇨🇭 Suisse", "🇷🇺 Russie", "🇯🇵 Japon",
        "🇰🇷 Corée du Sud", "🇬🇧 Grande-Bretagne", "🇳🇱 Pays-Bas", "🇨🇿 République Tchèque"
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        setupUI()
    }
    
    private fun setupUI() {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(40, 40, 40, 40)
        }
        
        // Titre
        val titleText = TextView(this).apply {
            text = "WINTER GAMES"
            textSize = 36f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        mainLayout.addView(titleText)
        
        val subtitleText = TextView(this).apply {
            text = "🏔️ ÉDITION GYROSCOPIQUE 🎿"
            textSize = 18f
            setTextColor(Color.CYAN)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }
        mainLayout.addView(subtitleText)
        
        // Sélection du nombre de joueurs
        val playerCountLabel = TextView(this).apply {
            text = "Nombre de joueurs humains :"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        mainLayout.addView(playerCountLabel)
        
        playerCountSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                arrayOf("1 joueur", "2 joueurs", "3 joueurs", "4 joueurs")
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    numberOfPlayers = position + 1
                    updatePlayerSetup()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        
        val spinnerParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 10, 0, 30)
        }
        playerCountSpinner.layoutParams = spinnerParams
        mainLayout.addView(playerCountSpinner)
        
        // Info IA
        val infoText = TextView(this).apply {
            text = "Les autres joueurs seront contrôlés par l'IA"
            textSize = 14f
            setTextColor(Color.LTGRAY)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        mainLayout.addView(infoText)
        
        // Layout pour la configuration des joueurs
        playerSetupLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        mainLayout.addView(playerSetupLayout)
        
        // Bouton commencer
        startButton = Button(this).apply {
            text = "🏆 COMMENCER LES JEUX 🏆"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#ff6600"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(20, 15, 20, 15)
            
            setOnClickListener { startTournament() }
        }
        
        val buttonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 30, 0, 0)
        }
        startButton.layoutParams = buttonParams
        mainLayout.addView(startButton)
        
        setContentView(mainLayout)
        
        // Configuration initiale
        updatePlayerSetup()
    }
    
    private fun updatePlayerSetup() {
        playerSetupLayout.removeAllViews()
        playerNames.clear()
        playerCountries.clear()
        
        // Créer les champs pour chaque joueur humain
        for (i in 0 until numberOfPlayers) {
            val playerLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 10, 0, 10)
                setBackgroundColor(Color.parseColor("#003366"))
            }
            
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 5, 0, 5)
            }
            playerLayout.layoutParams = params
            
            // Label joueur
            val playerLabel = TextView(this).apply {
                text = "Joueur ${i + 1}:"
                textSize = 16f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(120, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            playerLayout.addView(playerLabel)
            
            // Nom du joueur
            val nameInput = EditText(this).apply {
                hint = "Nom"
                setHintTextColor(Color.LTGRAY)
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#004488"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(10, 5, 10, 5)
            }
            playerLayout.addView(nameInput)
            
            // Sélection du pays
            val countrySpinner = Spinner(this).apply {
                adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_item,
                    countries
                ).also { adapter ->
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                layoutParams = LinearLayout.LayoutParams(200, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            playerLayout.addView(countrySpinner)
            
            playerSetupLayout.addView(playerLayout)
            
            // Initialiser avec des valeurs par défaut
            playerNames.add("Joueur ${i + 1}")
            playerCountries.add(countries[i % countries.size])
            
            // Écouter les changements
            nameInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val name = nameInput.text.toString().ifEmpty { "Joueur ${i + 1}" }
                    if (i < playerNames.size) playerNames[i] = name
                }
            }
            
            countrySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (i < playerCountries.size) playerCountries[i] = countries[position]
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        
        // Ajouter les joueurs IA
        if (numberOfPlayers < 4) {
            val aiLabel = TextView(this).apply {
                text = "\n🤖 JOUEURS IA :"
                textSize = 16f
                setTextColor(Color.YELLOW)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            playerSetupLayout.addView(aiLabel)
            
            for (i in numberOfPlayers until 4) {
                val aiLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 5, 0, 5)
                    setBackgroundColor(Color.parseColor("#332200"))
                }
                
                val aiText = TextView(this).apply {
                    text = "IA ${i + 1}: ${countries[(i + 4) % countries.size]}"
                    textSize = 14f
                    setTextColor(Color.LTGRAY)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(10, 5, 10, 5)
                }
                aiLayout.addView(aiText)
                
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 2, 0, 2)
                }
                aiLayout.layoutParams = params
                playerSetupLayout.addView(aiLayout)
            }
        }
    }
    
    private fun startTournament() {
        // Finaliser les noms des joueurs
        for (i in 0 until numberOfPlayers) {
            val nameInput = playerSetupLayout.getChildAt(i) as LinearLayout
            val editText = nameInput.getChildAt(1) as EditText
            val name = editText.text.toString().ifEmpty { "Joueur ${i + 1}" }
            playerNames[i] = name
        }
        
        // Créer les données du tournoi
        val intent = Intent(this, EventsMenuActivity::class.java).apply {
            putStringArrayListExtra("player_names", ArrayList(playerNames))
            putStringArrayListExtra("player_countries", ArrayList(playerCountries))
            putExtra("number_of_players", numberOfPlayers)
        }
        
        startActivity(intent)
    }
}
