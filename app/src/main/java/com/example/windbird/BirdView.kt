package com.example.windbird

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class BirdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    fun updateWindForce(force: Float) {
        // Ne fait absolument rien
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Affiche juste du texte blanc
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 50f
        }
        canvas.drawText("WINDBIRD WORKS", 100f, 300f, paint)
    }
}
