package com.example.windbird

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class BirdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    // ==================== GESTIONNAIRE PRINCIPAL ====================
    
    private var birdAnimationManager: BirdAnimationManager? = null
    private var isInitialized = false
    
    // ==================== INITIALISATION ====================
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        if (w <= 0 || h <= 0) return
        
        try {
            // Initialiser le gestionnaire d'animation d'oiseau
            birdAnimationManager = BirdAnimationManager(w, h)
            isInitialized = true
            
        } catch (e: Exception) {
            e.printStackTrace()
            isInitialized = false
        }
    }
    
    // ==================== MISE À JOUR DU VENT ====================
    
    fun updateWindForce(force: Float) {
        if (!isInitialized || birdAnimationManager == null) return
        
        try {
            // Transmettre la force du vent au gestionnaire d'animation
            birdAnimationManager?.updateWind(force)
            
            // Redessiner la vue
            invalidate()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // ==================== AFFICHAGE ====================
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (!isInitialized || birdAnimationManager == null) {
            return
        }
        
        try {
            // Dessiner l'oiseau avec toutes ses animations
            birdAnimationManager?.draw(canvas)
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // ==================== CYCLE DE VIE ====================
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            birdAnimationManager?.cleanup()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // ==================== FONCTIONS UTILITAIRES ====================
    
    fun resetBird() {
        try {
            birdAnimationManager?.reset()
            invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun getBirdState(): String {
        return birdAnimationManager?.getCurrentState() ?: "Non initialisé"
    }
}
