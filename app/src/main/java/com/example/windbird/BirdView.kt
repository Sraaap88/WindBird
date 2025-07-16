package com.example.windbird

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

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
    
    // Peinture pour le fond
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
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
        
        // Dessiner le fond coucher de soleil en feu
        drawFierySunsetBackground(canvas)
        
        birdAnimationManager?.updateWind(rawWindForce, deltaTime)
        
        birdAnimationManager?.draw(canvas)
        
        windGauge?.draw(canvas, rawWindForce)
        
        invalidate()
    }
    
    private fun drawFierySunsetBackground(canvas: Canvas) {
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        
        // Animation du temps qui passe
        val time = System.currentTimeMillis() * 0.0001f
        val windIntensity = rawWindForce * 0.5f
        
        // Gradient principal du coucher de soleil
        val skyGradient = LinearGradient(
            0f, 0f, 0f, height,
            intArrayOf(
                Color.rgb(255, 94, 77),    // Rouge-orange intense en haut
                Color.rgb(255, 154, 0),    // Orange vif
                Color.rgb(255, 206, 84),   // Jaune-orange
                Color.rgb(255, 138, 101),  // Orange-rose
                Color.rgb(139, 69, 19),    // Brun sombre en bas
                Color.rgb(25, 25, 30)      // Quasi-noir au sol
            ),
            floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f),
            Shader.TileMode.CLAMP
        )
        backgroundPaint.shader = skyGradient
        canvas.drawRect(0f, 0f, width, height, backgroundPaint)
        backgroundPaint.shader = null
        
        // Soleil couchant avec halo
        val sunX = width * 0.2f + sin(time * 0.1f) * 20f
        val sunY = height * 0.3f
        val sunRadius = 80f + windIntensity * 20f
        
        // Halo du soleil
        val sunHaloGradient = RadialGradient(
            sunX, sunY, sunRadius * 2.5f,
            intArrayOf(
                Color.argb(120, 255, 255, 255),
                Color.argb(80, 255, 154, 0),
                Color.argb(40, 255, 94, 77),
                Color.argb(0, 255, 94, 77)
            ),
            floatArrayOf(0f, 0.3f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        backgroundPaint.shader = sunHaloGradient
        canvas.drawCircle(sunX, sunY, sunRadius * 2.5f, backgroundPaint)
        backgroundPaint.shader = null
        
        // Soleil principal
        val sunGradient = RadialGradient(
            sunX, sunY, sunRadius,
            intArrayOf(
                Color.rgb(255, 255, 200),  // Centre blanc-jaune
                Color.rgb(255, 200, 0),    // Jaune vif
                Color.rgb(255, 94, 77)     // Rouge-orange bord
            ),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        backgroundPaint.shader = sunGradient
        canvas.drawCircle(sunX, sunY, sunRadius, backgroundPaint)
        backgroundPaint.shader = null
        
        // Nuages dramatiques en mouvement
        drawDramaticClouds(canvas, width, height, time, windIntensity)
        
        // Rayons de soleil perçants
        drawSunRays(canvas, sunX, sunY, width, height, time, windIntensity)
        
        // Particules de cendres volantes
        drawFloatingEmbers(canvas, width, height, time, windIntensity)
    }
    
    private fun drawDramaticClouds(canvas: Canvas, width: Float, height: Float, time: Float, windIntensity: Float) {
        backgroundPaint.style = Paint.Style.FILL
        
        // Nuages sombres menaçants
        for (i in 0..6) {
            val cloudX = (width * 0.1f + i * width * 0.15f + sin(time + i) * 30f + windIntensity * 40f) % (width + 200f) - 100f
            val cloudY = height * (0.1f + i * 0.08f)
            val cloudSize = 80f + i * 20f + windIntensity * 30f
            
            // Couleur des nuages selon la position
            val darkness = (i * 30 + windIntensity * 50).toInt().coerceIn(0, 100)
            backgroundPaint.color = Color.argb(
                150 + darkness,
                50 - darkness/3,
                25 - darkness/4,
                20 - darkness/5
            )
            
            // Forme de nuage organique
            val cloudPath = Path()
            cloudPath.addCircle(cloudX - cloudSize*0.3f, cloudY, cloudSize*0.6f, Path.Direction.CW)
            cloudPath.addCircle(cloudX, cloudY - cloudSize*0.2f, cloudSize*0.8f, Path.Direction.CW)
            cloudPath.addCircle(cloudX + cloudSize*0.4f, cloudY, cloudSize*0.5f, Path.Direction.CW)
            cloudPath.addCircle(cloudX + cloudSize*0.2f, cloudY + cloudSize*0.3f, cloudSize*0.4f, Path.Direction.CW)
            
            canvas.drawPath(cloudPath, backgroundPaint)
        }
    }
    
    private fun drawSunRays(canvas: Canvas, sunX: Float, sunY: Float, width: Float, height: Float, time: Float, windIntensity: Float) {
        backgroundPaint.style = Paint.Style.STROKE
        backgroundPaint.strokeWidth = 3f + windIntensity * 2f
        backgroundPaint.color = Color.argb((100 + windIntensity * 50).toInt(), 255, 255, 200)
        
        // Rayons qui bougent
        for (i in 0..8) {
            val angle = i * 45f + time * 10f + windIntensity * 20f
            val rayLength = 200f + windIntensity * 100f
            val angleRad = Math.toRadians(angle.toDouble())
            
            val endX = sunX + cos(angleRad) * rayLength
            val endY = sunY + sin(angleRad) * rayLength
            
            // Rayons avec effet de scintillement
            val alpha = (sin(time * 2f + i) * 50 + 100).toInt().coerceIn(50, 150)
            backgroundPaint.color = Color.argb(alpha, 255, 255, 200)
            
            canvas.drawLine(sunX, sunY, endX.toFloat(), endY.toFloat(), backgroundPaint)
        }
        
        backgroundPaint.style = Paint.Style.FILL
    }
    
    private fun drawFloatingEmbers(canvas: Canvas, width: Float, height: Float, time: Float, windIntensity: Float) {
        // Particules de braises volantes
        for (i in 0..12) {
            val emberX = (width * (i * 0.08f) + sin(time * 0.5f + i) * 100f + windIntensity * 60f) % width
            val emberY = (height * 0.3f + i * height * 0.05f + cos(time * 0.3f + i) * 50f) % height
            val emberSize = 2f + sin(time + i) * 2f + windIntensity * 3f
            
            // Couleur des braises
            val intensity = (sin(time * 2f + i) * 100 + 155).toInt().coerceIn(100, 255)
            backgroundPaint.color = Color.argb(intensity, 255, intensity/2, 0)
            
            // Effet de lueur
            val glowGradient = RadialGradient(
                emberX, emberY, emberSize * 3f,
                intArrayOf(
                    Color.argb(intensity, 255, 200, 0),
                    Color.argb(intensity/3, 255, 100, 0),
                    Color.argb(0, 255, 100, 0)
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            backgroundPaint.shader = glowGradient
            canvas.drawCircle(emberX, emberY, emberSize * 3f, backgroundPaint)
            backgroundPaint.shader = null
            
            // Braise centrale
            backgroundPaint.color = Color.argb(255, 255, 255, 200)
            canvas.drawCircle(emberX, emberY, emberSize, backgroundPaint)
        }
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
