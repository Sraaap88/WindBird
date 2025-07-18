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
    private lateinit var player1Name: EditText
    private lateinit var player1Country: Spinner
    private lateinit var player2Name: EditText
    private lateinit var player2Country: Spinner
    private lateinit var player3Name: EditText
    private lateinit var player3Country: Spinner
    private lateinit var player4Name: EditText
    private lateinit var player4Country: Spinner
    private lateinit var startButton: Button
    
    private var numberOfPlayers = 1
    
    // Pays disponibles (seulement les noms, pas d'émojis)
    private val countries = arrayOf(
        "Canada", "États-Unis", "Norvège", "Suède", "Finlande", 
        "Allemagne", "France", "Italie", "Autriche", "Suisse"
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
        val scrollView = ScrollView(this)
        
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#001122"))
            setPadding(30, 30, 30, 30)
        }
        
        // Titre
        val titleText = TextView(this).apply {
            text = "WINTER GAMES"
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        mainLayout.addView(titleText)
        
        // Nombre de joueurs
        val playerCountLabel = TextView(this).apply {
            text = "Nombre de joueurs humains:"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 10)
        }
        mainLayout.addView(playerCountLabel)
        
        playerCountSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                arrayOf("1", "2", "3", "4")
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    numberOfPlayers = position + 1
                    updatePlayerVisibility()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            setPadding(0, 0, 0, 20)
        }
        mainLayout.addView(playerCountSpinner)
        
        // Configuration des joueurs
        createPlayerSetup(mainLayout)
        
        // Bouton commencer
        startButton = Button(this).apply {
            text = "COMMENCER LES JEUX"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#ff6600"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(20, 15, 20, 15)
            
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 30, 0, 0)
            }
            
            setOnClickListener { startTournament() }
        }
        mainLayout.addView(startButton)
        
        scrollView.addView(mainLayout)
        setContentView(scrollView)
        
        updatePlayerVisibility()
    }
    
    private fun createPlayerSetup(parent: LinearLayout) {
        // Joueur 1
        createPlayerRow(parent, "Joueur 1:", 1)
        
        // Joueur 2
        createPlayerRow(parent, "Joueur 2:", 2)
        
        // Joueur 3
        createPlayerRow(parent, "Joueur 3:", 3)
        
        // Joueur 4
        createPlayerRow(parent, "Joueur 4:", 4)
    }
    
    private fun createPlayerRow(parent: LinearLayout, label: String, playerNum: Int) {
        val playerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)
            setBackgroundColor(Color.parseColor("#003366"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 5, 0, 5)
            }
        }
        
        val playerLabel = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(10, 5, 10, 5)
        }
        playerLayout.addView(playerLabel)
        
        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(10, 0, 10, 10)
        }
        
        val nameInput = EditText(this).apply {
            hint = "Nom"
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#004488"))
            setPadding(10, 5, 10, 5)
            layoutParams = LinearLayout.LayoutParams(0, 60, 1f).apply {
                setMargins(0, 0, 10, 0)
            }
        }
        inputLayout.addView(nameInput)
        
        val countrySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                countries
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            layoutParams = LinearLayout.LayoutParams(200, 60)
        }
        inputLayout.addView(countrySpinner)
        
        playerLayout.addView(inputLayout)
        parent.addView(playerLayout)
        
        // Assigner les références
        when (playerNum) {
            1 -> {
                player1Name = nameInput
                player1Country = countrySpinner
            }
            2 -> {
                player2Name = nameInput
                player2Country = countrySpinner
            }
            3 -> {
                player3Name = nameInput
                player3Country = countrySpinner
            }
            4 -> {
                player4Name = nameInput
                player4Country = countrySpinner
            }
        }
    }
    
    private fun updatePlayerVisibility() {
        val mainLayout = (findViewById<ScrollView>(android.R.id.content).getChildAt(0) as ViewGroup).getChildAt(0) as LinearLayout
        
        // Joueur 1 toujours visible (index 3)
        mainLayout.getChildAt(3).visibility = View.VISIBLE
        
        // Joueur 2 (index 4)
        mainLayout.getChildAt(4).visibility = if (numberOfPlayers >= 2) View.VISIBLE else View.GONE
        
        // Joueur 3 (index 5)
        mainLayout.getChildAt(5).visibility = if (numberOfPlayers >= 3) View.VISIBLE else View.GONE
        
        // Joueur 4 (index 6)
        mainLayout.getChildAt(6).visibility = if (numberOfPlayers >= 4) View.VISIBLE else View.GONE
    }
    
    private fun startTournament() {
        val playerNames = arrayListOf<String>()
        val playerCountries = arrayListOf<String>()
        
        // Récupérer les noms et pays selon le nombre de joueurs
        when (numberOfPlayers) {
            1 -> {
                playerNames.add(player1Name.text.toString().ifEmpty { "Joueur 1" })
                playerCountries.add(countries[player1Country.selectedItemPosition])
            }
            2 -> {
                playerNames.add(player1Name.text.toString().ifEmpty { "Joueur 1" })
                playerNames.add(player2Name.text.toString().ifEmpty { "Joueur 2" })
                playerCountries.add(countries[player1Country.selectedItemPosition])
                playerCountries.add(countries[player2Country.selectedItemPosition])
            }
            3 -> {
                playerNames.add(player1Name.text.toString().ifEmpty { "Joueur 1" })
                playerNames.add(player2Name.text.toString().ifEmpty { "Joueur 2" })
                playerNames.add(player3Name.text.toString().ifEmpty { "Joueur 3" })
                playerCountries.add(countries[player1Country.selectedItemPosition])
                playerCountries.add(countries[player2Country.selectedItemPosition])
                playerCountries.add(countries[player3Country.selectedItemPosition])
            }
            4 -> {
                playerNames.add(player1Name.text.toString().ifEmpty { "Joueur 1" })
                playerNames.add(player2Name.text.toString().ifEmpty { "Joueur 2" })
                playerNames.add(player3Name.text.toString().ifEmpty { "Joueur 3" })
                playerNames.add(player4Name.text.toString().ifEmpty { "Joueur 4" })
                playerCountries.add(countries[player1Country.selectedItemPosition])
                playerCountries.add(countries[player2Country.selectedItemPosition])
                playerCountries.add(countries[player3Country.selectedItemPosition])
                playerCountries.add(countries[player4Country.selectedItemPosition])
            }
        }
        
        // Compléter avec l'IA si nécessaire
        while (playerNames.size < 4) {
            playerNames.add("IA ${playerNames.size + 1}")
            playerCountries.add("Intelligence Artificielle")
        }
        
        val intent = Intent(this, EventsMenuActivity::class.java).apply {
            putStringArrayListExtra("player_names", playerNames)
            putStringArrayListExtra("player_countries", playerCountries)
            putExtra("number_of_players", numberOfPlayers)
        }
        
        startActivity(intent)
    }
}
