package com.example.windbird

import android.graphics.*
import kotlin.math.*

class WindGauge(private val screenWidth: Float, private val screenHeight: Float) {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Position et taille de la jauge
    private val gaugeRadius = min(screenWidth, screenHeight) * 0.15f
    private val gaugeCenterX = screenWidth - gaugeRadius - 50f
    private val gaugeCenterY = gaugeRadius + 50f
    
    // Couleurs des zones
    private val greenZoneColor = Color.rgb(76, 175, 80)
    private val yellowZoneColor = Color.rgb(255, 193, 7)
    private val redZoneColor = Color.rgb(244, 67, 54)
    
    // Variables pour l'aiguille lissée
    private var smoothedForce = 0f
    private val smoothingFactor = 0.85f
    
    init {
        textPaint.textSize = 24f
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.style = Paint.Style.FILL
        textPaint.setShadowLayer(2f, 1f, 1f, Color.BLACK)
    }
    
    fun draw(canvas: Canvas, rawForce: Float) {
        // Mise à jour de l'aiguille lissée
        smoothedForce = smoothedForce * smoothingFactor + rawForce * (1f - smoothingFactor)
        
        drawGaugeBackground(canvas)
        drawGaugeZones(canvas)
        drawGaugeNumbers(canvas)
        drawNeedles(canvas, rawForce, smoothedForce)
        drawForceText(canvas)
        drawFallThreshold(canvas)
    }
    
    private fun drawGaugeBackground(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        paint.color = Color.rgb(64, 64, 64)
        
        canvas.drawCircle(gaugeCenterX, gaugeCenterY, gaugeRadius, paint)
        
        // Fond semi-transparent
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(100, 0, 0, 0)
        canvas.drawCircle(gaugeCenterX, gaugeCenterY, gaugeRadius - 4f, paint)
    }
    
    private fun drawGaugeZones(canvas: Canvas) {
        val rect = RectF(
            gaugeCenterX - gaugeRadius + 20f,
            gaugeCenterY - gaugeRadius + 20f,
            gaugeCenterX + gaugeRadius - 20f,
            gaugeCenterY + gaugeRadius - 20f
        )
        
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 15f
        
        // Zone verte (0-70%) - Plus large car sensibilité réduite
        paint.color = greenZoneColor
        canvas.drawArc(rect, 135f, 126f, false, paint) // 0% à 70%
        
        // Zone jaune (70-90%) 
        paint.color = yellowZoneColor
        canvas.drawArc(rect, 261f, 36f, false, paint) // 70% à 90%
        
        // Zone rouge (90-100%) - Zone critique avant chute
        paint.color = redZoneColor
        canvas.drawArc(rect, 297f, 18f, false, paint) // 90% à 100%
    }
    
    private fun drawGaugeNumbers(canvas: Canvas) {
        textPaint.textSize = 18f
        textPaint.color = Color.WHITE
        
        for (i in 0..10) {
            val percentage = i * 10
            val angle = 135f + (i * 27f) // 270° répartis sur 10 segments
            val angleRad = Math.toRadians(angle.toDouble())
            
            val textX = gaugeCenterX + cos(angleRad) * (gaugeRadius - 35f)
            val textY = gaugeCenterY + sin(angleRad) * (gaugeRadius - 35f) + 6f
            
            canvas.drawText("$percentage", textX.toFloat(), textY.toFloat(), textPaint)
        }
    }
    
    private fun drawNeedles(canvas: Canvas, rawForce: Float, smoothedForce: Float) {
        // Aiguille brute (jaune, fine)
        drawNeedle(canvas, rawForce, Color.YELLOW, 4f, gaugeRadius - 30f)
        
        // Aiguille lissée (blanche, épaisse)
        drawNeedle(canvas, smoothedForce, Color.WHITE, 6f, gaugeRadius - 25f)
    }
    
    private fun drawNeedle(canvas: Canvas, force: Float, color: Int, width: Float, length: Float) {
        val angle = 135f + (force * 270f) // 0% = 135°, 100% = 405° (45°)
        val angleRad = Math.toRadians(angle.toDouble())
        
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.color = color
        paint.strokeCap = Paint.Cap.ROUND
        
        val endX = gaugeCenterX + cos(angleRad) * length
        val endY = gaugeCenterY + sin(angleRad) * length
        
        canvas.drawLine(gaugeCenterX, gaugeCenterY, endX.toFloat(), endY.toFloat(), paint)
        
        // Point central
        paint.style = Paint.Style.FILL
        canvas.drawCircle(gaugeCenterX, gaugeCenterY, 5f, paint)
    }
    
    private fun drawFallThreshold(canvas: Canvas) {
        // Ligne rouge pour le seuil de chute à 100%
        val angle = 405f // 100% = 405° (45°)
        val angleRad = Math.toRadians(angle.toDouble())
        
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.RED
        
        val innerRadius = gaugeRadius - 40f
        val outerRadius = gaugeRadius - 10f
        
        val startX = gaugeCenterX + cos(angleRad) * innerRadius
        val startY = gaugeCenterY + sin(angleRad) * innerRadius
        val endX = gaugeCenterX + cos(angleRad) * outerRadius
        val endY = gaugeCenterY + sin(angleRad) * outerRadius
        
        canvas.drawLine(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), paint)
    }
    
    private fun drawForceText(canvas: Canvas) {
        textPaint.textSize = 20f
        textPaint.color = Color.WHITE
        
        val displayForce = (smoothedForce * 100).toInt()
        val text = "${displayForce}%"
        
        canvas.drawText(text, gaugeCenterX, gaugeCenterY + 30f, textPaint)
        
        // Statut
        textPaint.textSize = 16f
        val status = when {
            smoothedForce < 0.7f -> "CALME"
            smoothedForce < 0.9f -> "VENT FORT"
            else -> "DANGER!"
        }
        
        textPaint.color = when {
            smoothedForce < 0.7f -> greenZoneColor
            smoothedForce < 0.9f -> yellowZoneColor
            else -> redZoneColor
        }
        
        canvas.drawText(status, gaugeCenterX, gaugeCenterY + 50f, textPaint)
        
        // Info sensibilité
        textPaint.textSize = 12f
        textPaint.color = Color.LTGRAY
        canvas.drawText("Sensibilité: 80%", gaugeCenterX, gaugeCenterY + 70f, textPaint)
    }
}
