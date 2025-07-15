package com.example.windbird

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class BirdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var birdAnimationManager: BirdAnimationManager? = null
    private var birdRenderer: BirdRenderer? = null
    private var windGauge: WindGauge? = null
    
    private var lastFrameTime = System.currentTimeMillis()
    private var rawWindForce = 0f
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // Initialisation des composants
        birdAnimationManager = BirdAnimationManager(w.toFloat(), h.toFloat())
        birdRenderer = BirdRenderer(w.toFloat(), h.toFloat())
        windGauge = WindGauge(w.toFloat(), h.toFloat())
        
        // Liaison des composants
        birdAnimationManager?.setBirdRenderer(birdRenderer!!)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val currentTime = System.currentTimeMillis()
        val deltaTime = (currentTime - lastFrameTime).toFloat()
        lastFrameTime = currentTime
        
        // Mise à jour de l'animation
        birdAnimationManager?.updateWind(rawWindForce, deltaTime)
        
        // Dessin de l'oiseau et effets
        birdAnimationManager?.draw(canvas)
        
        // Dessin de la jauge de vent
        windGauge?.draw(canvas, rawWindForce)
        
        // Redessiner en continu
        invalidate()
    }
    
    fun updateWindForce(force: Float) {
        // Force brute reçue du micro (0.0 à 1.0)
        // La réduction de sensibilité se fait dans BirdAnimationManager
        rawWindForce = force.coerceIn(0f, 1f)
    }
    
    fun resetBird() {
        birdAnimationManager?.resetBird()
    }
    
    fun getBirdState(): String {
        return birdAnimationManager?.getCurrentState() ?: "Non initialisé"
    }
}
