// IntroActivity.kt – Écran d’introduction rétro stylé
package com.example.windbird

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class IntroActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground) // Remplace par ton logo pixelisé rétro
            val fadeIn = AlphaAnimation(0f, 1f).apply {
                duration = 2000
                fillAfter = true
            }
            startAnimation(fadeIn)
        }
        layout.addView(logo)

        val title = TextView(this).apply {
            text = "❄️ WINTER GAMES 2025 ❄️"
            textSize = 28f
            setTextColor(Color.CYAN)
            setPadding(0, 40, 0, 10)
        }
        layout.addView(title)

        val subtitle = TextView(this).apply {
            text = "Présenté par Studio Maître du Code"
            textSize = 16f
            setTextColor(Color.LTGRAY)
        }
        layout.addView(subtitle)

        val loading = TextView(this).apply {
            text = "Chargement..."
            textSize = 14f
            setTextColor(Color.GRAY)
            setPadding(0, 60, 0, 0)
        }
        layout.addView(loading)

        setContentView(layout)

        // Lance le menu principal après 3 secondes
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this@IntroActivity, MainMenuActivity::class.java)
            startActivity(intent)
            finish()
        }, 3000)
    }
}
