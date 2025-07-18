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
    private lateinit var player1Layout: LinearLayout
    private lateinit var player2Layout: LinearLayout
    private lateinit var player3Layout: LinearLayout
    private lateinit var player4Layout: LinearLayout
    private lateinit var player1Name: EditText
    private lateinit var player1Country: Spinner
    private lateinit var player2Name: EditText
    private lateinit var player2Country: Spinner
    private lateinit var player3Name: EditText
    private lateinit var player3Country: Spinner
    private lateinit var player4Name: EditText
    private lateinit var player4Country: Spinner
    
    private var numberOfPlayers = 1
    
    private val countries = arrayOf(
        "🇨🇦 Canada", "🇺🇸 États-Unis", "🇳🇴 Norvège", "🇸🇪 Suède", 
        "🇫🇮 Finlande", "🇩🇪 Allemagne", "🇫🇷 France", "🇮🇹 Italie",
        "🇦🇹 Autriche", "🇨🇭 Suisse", "🇷🇺 Russie", "🇯🇵 Japon"
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
        
        val titleText = TextView(this).apply {
            text = "🏔️ WINTER GAMES 🎿"
            textSize = 28f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }
        mainLayout.addView(titleText)
        
        val playerCountLabel = TextView(this).apply {
            text = "Nombre de joueurs humains :"
            textSize = 16f
            setTextColor(Color.YELLOW)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 10)
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
                    updatePlayerVisibility()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        
        val spinnerParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 80
        ).apply {
            setMargins(0, 0, 0, 20)
        }
        playerCountSpinner.layoutParams = spinnerParams
        mainLayout.addView(playerCountSpinner)
        
        createPlayerSetup(mainLayout)
        
        val startButton = Button(this).apply {
            text = "🎯 MODE PRATIQUE"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0066cc"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(20, 15, 20, 15)
            
            setOnClickListener { startPracticeMode() }
        }
        
        val buttonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 80
        ).apply {
            setMargins(0, 20, 0, 10)
        }
        startButton.layoutParams = buttonParams
        mainLayout.addView(startButton)
        
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
        ).apply {
            setMargins(0, 0, 0, 0)
        }
        tournamentButton.layoutParams = tournamentParams
        mainLayout.addView(tournamentButton)
        
        scrollView.addView(mainLayout)
        setContentView(scrollView)
        
        updatePlayerVisibility()
    }
    
    private fun createPlayerSetup(parent: LinearLayout) {
        player1Layout = createPlayerRow("Joueur 1 :", 1)
        parent.addView(player1Layout)
        
        player2Layout = createPlayerRow("Joueur 2 :", 2)
        parent.addView(player2Layout)
        
        player3Layout = createPlayerRow("Joueur 3 :", 3)
        parent.addView(player3Layout)
        
        player4Layout = createPlayerRow("Joueur 4 :", 4)
        parent.addView(player4Layout)
    }
    
    private fun createPlayerRow(label: String, playerNum: Int): LinearLayout {
        val playerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(15, 15, 15, 15)
            setBackgroundColor(Color.parseColor("#003366"))
        }
        
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 5, 0, 5)
        }
        playerLayout.layoutParams = params
        
        val playerLabel = TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 10)
        }
        playerLayout.addView(playerLabel)
        
        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        
        val nameInput = EditText(this).apply {
            hint = "Entrez votre nom"
            setHintTextColor(Color.LTGRAY)
            setTextColor(Color.BLACK) // TEXTE NOIR
            setBackgroundColor(Color.WHITE) // FOND BLANC
            setPadding(15, 10, 15, 10)
        }
        
        val nameParams = LinearLayout.LayoutParams(0, 70, 1f).apply {
            setMargins(0, 0, 15, 0)
        }
        nameInput.layoutParams = nameParams
        inputLayout.addView(nameInput)
        
        val countrySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                countries
            ).also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        
        val countryParams = LinearLayout.LayoutParams(250, 70)
        countrySpinner.layoutParams = countryParams
        inputLayout.addView(countrySpinner)
        
        playerLayout.addView(inputLayout)
        
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
        
        return playerLayout
    }
    
    private fun updatePlayerVisibility() {
        player1Layout.visibility = View.VISIBLE
        player2Layout.visibility = if (numberOfPlayers >= 2) View.VISIBLE else View.GONE
        player3Layout.visibility = if (numberOfPlayers >= 3) View.VISIBLE else View.GONE
        player4Layout.visibility = if (numberOfPlayers >= 4) View.VISIBLE else View.GONE
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
    }
    
    private fun startTournament() {
        val playerNames = arrayListOf<String>()
        val playerCountries = arrayListOf<String>()
        
        if (numberOfPlayers >= 1) {
            playerNames.add(player1Name.text.toString().ifEmpty { "Joueur 1" })
            playerCountries.add(countries[player1Country.selectedItemPosition])
        }
        if (numberOfPlayers >= 2) {
            playerNames.add(player2Name.text.toString().ifEmpty { "Joueur 2" })
            playerCountries.add(countries[player2Country.selectedItemPosition])
        }
        if (numberOfPlayers >= 3) {
            playerNames.add(player3Name.text.toString().ifEmpty { "Joueur 3" })
            playerCountries.add(countries[player3Country.selectedItemPosition])
        }
        if (numberOfPlayers >= 4) {
            playerNames.add(player4Name.text.toString().ifEmpty { "Joueur 4" })
            playerCountries.add(countries[player4Country.selectedItemPosition])
        }
        
        while (playerNames.size < 4) {
            playerNames.add("IA ${playerNames.size + 1}")
            playerCountries.add("🤖 Intelligence Artificielle")
        }
        
        val intent = Intent(this, EventsMenuActivity::class.java).apply {
            putStringArrayListExtra("player_names", playerNames)
            putStringArrayListExtra("player_countries", playerCountries)
            putExtra("number_of_players", numberOfPlayers)
        }
        
        startActivity(intent)
    }
}
