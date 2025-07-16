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
        
        birdAnimationManager = BirdAnimationManager(w.toFloat(), h.toFloat())
        birdRenderer = BirdRenderer(w.toFloat(), h.toFloat())
        windGauge = WindGauge(w.toFloat(), h.toFloat())
        
        birdAnimationManager?.setBirdRenderer(birdRenderer!!)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val currentTime = System.currentTimeMillis()
        val deltaTime = (currentTime - lastFrameTime).toFloat()
        lastFrameTime = currentTime
        
        birdAnimationManager?.updateWind(rawWindForce, deltaTime)
        
        birdAnimationManager?.draw(canvas)
        
        windGauge?.draw(canvas, rawWindForce)
        
        invalidate()
    }
    
    fun updateWindForce(force: Float) {
        rawWindForce = force.coerceIn(0f, 1f)
    }
    
    fun resetBird() {
        birdAnimationManager?.resetBird()
    }
    
    fun getBirdState(): String {
        return birdAnimationManager?.getCurrentState() ?: "Non initialisé"
    }
}
