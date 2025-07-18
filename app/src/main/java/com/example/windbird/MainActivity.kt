package com.example.windbird

import android.app.Activity
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.Intent
import android.widget.Button
import android.widget.LinearLayout
import android.graphics.Color
import android.view.ViewGroup
import android.widget.TextView

class MainActivity : Activity() {

    private var mediaRecorder: MediaRecorder? = null
    private var handler: Handler? = null
    private var animationHandler: Handler? = null
    private var birdView: BirdView? = null
    private var isRecording = false
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
        private const val UPDATE_INTERVAL = 33L // 30 FPS
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        birdView = findViewById(R.id.birdView)
        handler = Handler(Looper.getMainLooper())
        animationHandler = Handler(Looper.getMainLooper())
        
        // NOUVEAU : Ajouter le bouton Winter Games
        addWinterGamesButton()
        
        // Démarrer l'animation continue
        startContinuousAnimation()
        
        // Vérifier et demander les permissions
        if (checkPermissions()) {
            startRecording()
        } else {
            requestPermissions()
        }
    }
    
    // NOUVELLE FONCTION : Ajouter le bouton Winter Games
    private fun addWinterGamesButton() {
        try {
            // Trouver le layout principal (supposé être un LinearLayout ou FrameLayout)
            val rootView = findViewById<ViewGroup>(android.R.id.content)
            val mainLayout = rootView.getChildAt(0) as? ViewGroup
            
            if (mainLayout != null) {
                // Créer un conteneur pour le bouton Winter Games
                val buttonContainer = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(20, 20, 20, 20)
                }
                
                // Titre Winter Games
                val titleText = TextView(this).apply {
                    text = "🎿 WINTER GAMES 🏔️"
                    textSize = 20f
                    setTextColor(Color.WHITE)
                    gravity = android.view.Gravity.CENTER
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                
                // Bouton Winter Games
                val winterGamesButton = Button(this).apply {
                    text = "JOUER AUX JEUX D'HIVER"
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#0066cc"))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(30, 20, 30, 20)
                    
                    setOnClickListener {
                        val intent = Intent(this@MainActivity, WinterGamesMenuActivity::class.java)
                        startActivity(intent)
                    }
                }
                
                // Paramètres de layout
                val buttonParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 10, 0, 0)
                }
                
                // Ajouter les éléments au conteneur
                buttonContainer.addView(titleText)
                buttonContainer.addView(winterGamesButton, buttonParams)
                
                // Ajouter le conteneur au layout principal
                val containerParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                
                when (mainLayout) {
                    is LinearLayout -> {
                        mainLayout.addView(buttonContainer, containerParams)
                    }
                    else -> {
                        // Si ce n'est pas un LinearLayout, utiliser addView basique
                        mainLayout.addView(buttonContainer)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Si l'ajout automatique échoue, ne rien faire pour ne pas casser l'app
        }
    }
    
    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, 
            arrayOf(Manifest.permission.RECORD_AUDIO), 
            PERMISSION_REQUEST_CODE)
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording()
            }
        }
    }
    
    private fun startRecording() {
        try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile("/dev/null")
                prepare()
                start()
            }
            isRecording = true
            
            // Démarrer la mise à jour périodique
            updateAmplitude()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun updateAmplitude() {
        if (isRecording && mediaRecorder != null) {
            try {
                val amplitude = mediaRecorder?.maxAmplitude ?: 0
                
                // Normalisation et amplification pour signaux faibles
                val normalizedAmplitude = minOf(amplitude / 32767.0f, 1.0f)
                val amplifiedForce = minOf(normalizedAmplitude * 4.0f, 1.0f)
                
                // Envoyer la force à la vue oiseau
                birdView?.updateWindForce(amplifiedForce)
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Programmer la prochaine mise à jour
        handler?.postDelayed({ updateAmplitude() }, UPDATE_INTERVAL)
    }
    
    private fun startContinuousAnimation() {
        animationHandler?.post(object : Runnable {
            override fun run() {
                birdView?.invalidate()
                animationHandler?.postDelayed(this, 33) // 30 FPS
            }
        })
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        handler?.removeCallbacksAndMessages(null)
        animationHandler?.removeCallbacksAndMessages(null)
    }
}
