package com.example.windbird

import android.graphics.*
import kotlin.math.*

class WindGauge(private val screenWidth: Float, private val screenHeight: Float) {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private val gaugeRadius = min(screenWidth, screenHeight) * 0.15f
    private val gaugeCenterX = screenWidth - gaugeRadius - 50f
    private val gaugeCenterY = gaugeRadius + 50f
    
    // Couleurs sombres assorties au corbeau
    private val darkGreenColor = Color.rgb(34, 80, 34)      // Vert sombre
    private val ominousYellow = Color.rgb(180, 140, 20)     // Jaune sinistre
    private val bloodRedColor = Color.rgb(139, 0, 0)        // Rouge sang
    private val darkMetal = Color.rgb(60, 60, 70)           // Métal sombre
    private val ghostWhite = Color.rgb(220, 220, 230)       // Blanc fantomatique
    
    private var smoothedForce = 0f
    private val smoothingFactor = 0.85f
    
    // Effet de pulsation sinistre
    private var pulseIntensity = 0f
    
    init {
        textPaint.textSize = 24f
        textPaint.color = ghostWhite
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.style = Paint.Style.FILL
        textPaint.setShadowLayer(3f, 2f, 2f, Color.BLACK)
    }
    
    fun draw(canvas: Canvas, rawForce: Float) {
        smoothedForce = smoothedForce * smoothingFactor + rawForce * (1f - smoothingFactor)
        
        // Mise à jour de la pulsation sinistre
        pulseIntensity = sin(System.currentTimeMillis() * 0.008f) * 0.3f + 0.7f
        
        drawDarkGaugeBackground(canvas)
        drawOminousZones(canvas)
        drawGothicNumbers(canvas)
        drawSinisterNeedles(canvas, rawForce, smoothedForce)
        drawDarkForceText(canvas)
        drawDeathThreshold(canvas)
        drawAuraEffect(canvas)
    }
    
    private fun drawDarkGaugeBackground(canvas: Canvas) {
        // Contour métallique sombre
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10f
        paint.color = darkMetal
        canvas.drawCircle(gaugeCenterX, gaugeCenterY, gaugeRadius, paint)
        
        // Fond sombre avec gradient
        val backgroundGradient = RadialGradient(
            gaugeCenterX, gaugeCenterY, gaugeRadius - 5f,
            intArrayOf(Color.rgb(20, 20, 25), Color.rgb(10, 10, 15), Color.rgb(5, 5, 10)),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = backgroundGradient
        paint.style = Paint.Style.FILL
        canvas.drawCircle(gaugeCenterX, gaugeCenterY, gaugeRadius - 5f, paint)
        paint.shader = null
        
        // Gravures sinistres autour du cadran
        paint.color = Color.argb(80, 60, 60, 70)
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        for (i in 0..23) {
            val angle = i * 15f
            val angleRad = Math.toRadians(angle.toDouble())
            val innerRadius = gaugeRadius - 15f
            val outerRadius = gaugeRadius - 8f
            
            val startX = gaugeCenterX + cos(angleRad) * innerRadius
            val startY = gaugeCenterY + sin(angleRad) * innerRadius
            val endX = gaugeCenterX + cos(angleRad) * outerRadius
            val endY = gaugeCenterY + sin(angleRad) * outerRadius
            
            canvas.drawLine(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), paint)
        }
        paint.style = Paint.Style.FILL
    }
    
    private fun drawOminousZones(canvas: Canvas) {
        val rect = RectF(
            gaugeCenterX - gaugeRadius + 25f,
            gaugeCenterY - gaugeRadius + 25f,
            gaugeCenterX + gaugeRadius - 25f,
            gaugeCenterY + gaugeRadius - 25f
        )
        
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 18f
        
        // Zone de calme (vert sombre) - 0-70%
        paint.color = darkGreenColor
        canvas.drawArc(rect, 135f, 126f, false, paint)
        
        // Zone d'avertissement (jaune sinistre) - 70-90%
        paint.color = ominousYellow
        canvas.drawArc(rect, 261f, 36f, false, paint)
        
        // Zone de mort (rouge sang) - 90-100%
        val dangerIntensity = if (smoothedForce > 0.9f) (pulseIntensity * 100).toInt() else 0
        paint.color = Color.rgb(139 + dangerIntensity/2, dangerIntensity/3, dangerIntensity/3)
        canvas.drawArc(rect, 297f, 18f, false, paint)
    }
    
    private fun drawGothicNumbers(canvas: Canvas) {
        textPaint.textSize = 16f
        textPaint.color = ghostWhite
        
        for (i in 0..10) {
            val percentage = i * 10
            val angle = 135f + (i * 27f)
            val angleRad = Math.toRadians(angle.toDouble())
            
            val textX = gaugeCenterX + cos(angleRad) * (gaugeRadius - 40f)
            val textY = gaugeCenterY + sin(angleRad) * (gaugeRadius - 40f) + 6f
            
            // Effet de lueur pour les nombres critiques
            if (percentage >= 90) {
                textPaint.setShadowLayer(8f, 0f, 0f, bloodRedColor)
            } else {
                textPaint.setShadowLayer(3f, 2f, 2f, Color.BLACK)
            }
            
            canvas.drawText("$percentage", textX.toFloat(), textY.toFloat(), textPaint)
        }
    }
    
    private fun drawSinisterNeedles(canvas: Canvas, rawForce: Float, smoothedForce: Float) {
        // Aiguille brute (jaune sinistre, tremblante)
        drawSinisterNeedle(canvas, rawForce, ominousYellow, 5f, gaugeRadius - 30f, true)
        
        // Aiguille lissée (blanc fantomatique, stable)
        drawSinisterNeedle(canvas, smoothedForce, ghostWhite, 7f, gaugeRadius - 25f, false)
    }
    
    private fun drawSinisterNeedle(canvas: Canvas, force: Float, color: Int, width: Float, length: Float, trembling: Boolean) {
        val baseAngle = 135f + (force * 270f)
        val tremble = if (trembling && force > 0.8f) sin(System.currentTimeMillis() * 0.05f) * 2f else 0f
        val angle = baseAngle + tremble
        val angleRad = Math.toRadians(angle.toDouble())
        
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.color = color
        paint.strokeCap = Paint.Cap.ROUND
        
        // Effet de lueur pour l'aiguille
        if (force > 0.9f) {
            paint.setShadowLayer(12f, 0f, 0f, color)
        } else {
            paint.setShadowLayer(4f, 1f, 1f, Color.BLACK)
        }
        
        val endX = gaugeCenterX + cos(angleRad) * length
        val endY = gaugeCenterY + sin(angleRad) * length
        
        canvas.drawLine(gaugeCenterX, gaugeCenterY, endX.toFloat(), endY.toFloat(), paint)
        
        // Centre métallique sombre
        paint.style = Paint.Style.FILL
        paint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        paint.color = darkMetal
        canvas.drawCircle(gaugeCenterX, gaugeCenterY, 6f, paint)
        
        // Point central lumineux
        paint.color = color
        canvas.drawCircle(gaugeCenterX, gaugeCenterY, 3f, paint)
    }
    
    private fun drawDeathThreshold(canvas: Canvas) {
        // Ligne de mort à 100% - pulsante et menaçante
        val angle = 405f
        val angleRad = Math.toRadians(angle.toDouble())
        
        val pulseWidth = 4f + pulseIntensity * 2f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = pulseWidth
        paint.color = bloodRedColor
        paint.setShadowLayer(8f, 0f, 0f, bloodRedColor)
        
        val innerRadius = gaugeRadius - 45f
        val outerRadius = gaugeRadius - 12f
        
        val startX = gaugeCenterX + cos(angleRad) * innerRadius
        val startY = gaugeCenterY + sin(angleRad) * innerRadius
        val endX = gaugeCenterX + cos(angleRad) * outerRadius
        val endY = gaugeCenterY + sin(angleRad) * outerRadius
        
        canvas.drawLine(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), paint)
        
        // Symbole de mort au niveau du seuil
        paint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        textPaint.textSize = 12f
        textPaint.color = bloodRedColor
        textPaint.setShadowLayer(6f, 0f, 0f, bloodRedColor)
        canvas.drawText("☠", endX.toFloat() + 15f, endY.toFloat() + 4f, textPaint)
    }
    
    private fun drawDarkForceText(canvas: Canvas) {
        textPaint.textSize = 22f
        textPaint.color = ghostWhite
        textPaint.setShadowLayer(4f, 2f, 2f, Color.BLACK)
        
        val displayForce = (smoothedForce * 100).toInt()
        val text = "${displayForce}%"
        
        // Effet de pulsation pour les valeurs dangereuses
        if (smoothedForce > 0.9f) {
            textPaint.setShadowLayer(10f, 0f, 0f, bloodRedColor)
            textPaint.textSize = 22f + pulseIntensity * 4f
        }
        
        canvas.drawText(text, gaugeCenterX, gaugeCenterY + 35f, textPaint)
        
        // Statut sinistre
        textPaint.textSize = 14f
        val (status, statusColor) = when {
            smoothedForce < 0.7f -> "CALME" to darkGreenColor
            smoothedForce < 0.9f -> "INQUIÉTANT" to ominousYellow
            else -> "MORTEL" to bloodRedColor
        }
        
        textPaint.color = statusColor
        if (smoothedForce > 0.9f) {
            textPaint.setShadowLayer(8f, 0f, 0f, statusColor)
        } else {
            textPaint.setShadowLayer(3f, 1f, 1f, Color.BLACK)
        }
        
        canvas.drawText(status, gaugeCenterX, gaugeCenterY + 55f, textPaint)
        
        // Info technique sombre
        textPaint.textSize = 10f
        textPaint.color = Color.rgb(120, 120, 130)
        textPaint.setShadowLayer(2f, 1f, 1f, Color.BLACK)
        canvas.drawText("Sensibilité: 80%", gaugeCenterX, gaugeCenterY + 75f, textPaint)
        canvas.drawText("Corbeau Digital", gaugeCenterX, gaugeCenterY + 90f, textPaint)
    }
    
    private fun drawAuraEffect(canvas: Canvas) {
        // Aura sombre qui s'intensifie avec la force
        if (smoothedForce > 0.3f) {
            val auraIntensity = (smoothedForce - 0.3f) * 100f
            val auraRadius = gaugeRadius * (1.3f + smoothedForce * 0.5f)
            
            val auraGradient = RadialGradient(
                gaugeCenterX, gaugeCenterY, auraRadius,
                intArrayOf(
                    Color.argb((auraIntensity * 0.6f).toInt(), 20, 0, 0),
                    Color.argb((auraIntensity * 0.3f).toInt(), 10, 0, 0),
                    Color.argb(0, 0, 0, 0)
                ),
                floatArrayOf(0f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
            
            glowPaint.shader = auraGradient
            canvas.drawCircle(gaugeCenterX, gaugeCenterY, auraRadius, glowPaint)
            glowPaint.shader = null
        }
    }
}
