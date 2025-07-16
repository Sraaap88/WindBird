package com.example.windbird

import android.graphics.*
import kotlin.math.*

class WindGauge(private val screenWidth: Int, private val screenHeight: Int) {
    
    // ==================== PROPRIÉTÉS DE LA JAUGE ====================
    
    private val gaugeX = screenWidth * 0.05f
    private val gaugeY = screenHeight * 0.05f
    private val gaugeWidth = screenWidth * 0.3f
    private val gaugeHeight = 40f
    
    // ==================== PINCEAUX POUR L'INTERFACE ====================
    
    private val backgroundPaint = Paint().apply {
        color = Color.argb(150, 50, 50, 50)
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val greenZonePaint = Paint().apply {
        color = Color.argb(120, 0, 200, 0)
        isAntiAlias = true
    }
    
    private val yellowZonePaint = Paint().apply {
        color = Color.argb(120, 255, 255, 0)
        isAntiAlias = true
    }
    
    private val redZonePaint = Paint().apply {
        color = Color.argb(120, 255, 0, 0)
        isAntiAlias = true
    }
    
    private val needlePaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    
    private val rawNeedlePaint = Paint().apply {
        color = Color.YELLOW
        isAntiAlias = true
        strokeWidth = 2f
        style = Paint.Style.STROKE
        alpha = 150
    }
    
    private val thresholdPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 3f
        isAntiAlias = true
    }
    
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    
    private val progressPaint = Paint().apply {
        color = Color.argb(200, 255, 100, 100)
        isAntiAlias = true
    }
    
    private val legendPaint = Paint().apply {
        color = Color.WHITE
        textSize = 18f
        isAntiAlias = true
    }
    
    // ==================== FONCTION PRINCIPALE DE DESSIN ====================
    
    fun draw(
        canvas: Canvas,
        currentWindForce: Float,
        windForceSmoothed: Float,
        sustainedWindTime: Float,
        extremeWindThreshold: Float,
        extremeWindDuration: Float
    ) {
        // Fond de la jauge avec coins arrondis
        val backgroundRect = RectF(gaugeX, gaugeY, gaugeX + gaugeWidth, gaugeY + gaugeHeight)
        canvas.drawRoundRect(backgroundRect, 20f, 20f, backgroundPaint)
        
        // Zones colorées de progression
        drawGaugeZones(canvas, extremeWindThreshold)
        
        // Aiguilles de mesure
        drawNeedles(canvas, currentWindForce, windForceSmoothed)
        
        // Ligne de seuil critique
        drawThresholdLine(canvas, extremeWindThreshold)
        
        // Informations textuelles
        drawWindInfo(canvas, windForceSmoothed, sustainedWindTime, extremeWindThreshold)
        
        // Barre de progression temporelle
        if (sustainedWindTime > 0f) {
            drawTimeProgress(canvas, sustainedWindTime, extremeWindDuration)
        }
        
        // Légendes explicatives
        drawLegends(canvas)
    }
    
    // ==================== ZONES COLORÉES ====================
    
    private fun drawGaugeZones(canvas: Canvas, threshold: Float) {
        // Zone verte (0-20%) - Calme, oiseau normal
        val greenZone = RectF(gaugeX, gaugeY, gaugeX + gaugeWidth * 0.2f, gaugeY + gaugeHeight)
        canvas.drawRect(greenZone, greenZonePaint)
        
        // Zone jaune (20-seuil%) - Léger vent, animations commencent
        val yellowStart = gaugeX + gaugeWidth * 0.2f
        val yellowEnd = gaugeX + gaugeWidth * threshold
        val yellowZone = RectF(yellowStart, gaugeY, yellowEnd, gaugeY + gaugeHeight)
        canvas.drawRect(yellowZone, yellowZonePaint)
        
        // Zone rouge (seuil%+) - ZONE DE CHUTE !
        val redZone = RectF(gaugeX + gaugeWidth * threshold, gaugeY, gaugeX + gaugeWidth, gaugeY + gaugeHeight)
        canvas.drawRect(redZone, redZonePaint)
    }
    
    // ==================== AIGUILLES DE MESURE ====================
    
    private fun drawNeedles(canvas: Canvas, currentWind: Float, smoothedWind: Float) {
        // Aiguille principale (vent lissé) - celle qui compte vraiment
        val needlePosition = (smoothedWind * gaugeWidth).coerceIn(0f, gaugeWidth)
        canvas.drawLine(
            gaugeX + needlePosition, gaugeY - 5f,
            gaugeX + needlePosition, gaugeY + gaugeHeight + 5f,
            needlePaint
        )
        
        // Aiguille secondaire (vent brut) - plus fine, pour voir les variations
        val rawNeedlePosition = (currentWind * gaugeWidth).coerceIn(0f, gaugeWidth)
        canvas.drawLine(
            gaugeX + rawNeedlePosition, gaugeY,
            gaugeX + rawNeedlePosition, gaugeY + gaugeHeight,
            rawNeedlePaint
        )
    }
    
    // ==================== LIGNE DE SEUIL ====================
    
    private fun drawThresholdLine(canvas: Canvas, threshold: Float) {
        val thresholdX = gaugeX + gaugeWidth * threshold
        canvas.drawLine(thresholdX, gaugeY - 10f, thresholdX, gaugeY + gaugeHeight + 10f, thresholdPaint)
        
        // Étiquette du seuil
        val thresholdText = "${(threshold * 100).toInt()}%"
        val thresholdTextWidth = textPaint.measureText(thresholdText)
        canvas.drawText(
            thresholdText, 
            thresholdX - thresholdTextWidth / 2f, 
            gaugeY - 15f, 
            textPaint
        )
    }
    
    // ==================== INFORMATIONS TEXTUELLES ====================
    
    private fun drawWindInfo(canvas: Canvas, windForce: Float, sustainedTime: Float, threshold: Float) {
        // Pourcentage actuel en haut à gauche
        val percentText = "${(windForce * 100).toInt()}%"
        canvas.drawText(percentText, gaugeX, gaugeY - 10f, textPaint)
        
        // Temps soutenu en haut à droite (si applicable)
        if (sustainedTime > 0f) {
            val timeText = "${(sustainedTime / 1000f).toInt()}s"
            val timeTextWidth = textPaint.measureText(timeText)
            canvas.drawText(timeText, gaugeX + gaugeWidth - timeTextWidth, gaugeY - 10f, textPaint)
        }
        
        // Indicateur d'état
        val statusText = when {
            windForce < 0.2f -> "😴 Calme"
            windForce < threshold -> "💨 Vent léger"
            sustainedTime > 0f -> "⚠️ DANGER!"
            else -> "🔥 Zone critique!"
        }
        
        val statusY = gaugeY + gaugeHeight + 60f
        canvas.drawText(statusText, gaugeX, statusY, textPaint)
    }
    
    // ==================== BARRE DE PROGRESSION TEMPORELLE ====================
    
    private fun drawTimeProgress(canvas: Canvas, sustainedTime: Float, duration: Float) {
        val timeProgress = (sustainedTime / duration).coerceIn(0f, 1f)
        val progressWidth = gaugeWidth * timeProgress
        
        // Barre de fond
        val progressBackgroundPaint = Paint().apply {
            color = Color.argb(100, 100, 100, 100)
            isAntiAlias = true
        }
        canvas.drawRect(
            gaugeX, gaugeY + gaugeHeight + 10f, 
            gaugeX + gaugeWidth, gaugeY + gaugeHeight + 20f, 
            progressBackgroundPaint
        )
        
        // Barre de progression remplie
        canvas.drawRect(
            gaugeX, gaugeY + gaugeHeight + 10f, 
            gaugeX + progressWidth, gaugeY + gaugeHeight + 20f, 
            progressPaint
        )
        
        // Texte de progression
        val progressText = "Temps de chute: ${((sustainedTime / 1000f) * 100 / (duration / 1000f)).toInt()}%"
        canvas.drawText(progressText, gaugeX, gaugeY + gaugeHeight + 35f, legendPaint)
        
        // Animation de pulsation quand près de la chute
        if (timeProgress > 0.7f) {
            val pulse = sin(System.currentTimeMillis() * 0.01f) * 0.5f + 0.5f
            val pulsePaint = Paint().apply {
                color = Color.argb((100 * pulse).toInt(), 255, 0, 0)
                isAntiAlias = true
            }
            canvas.drawRect(
                gaugeX - 5f, gaugeY - 5f,
                gaugeX + gaugeWidth + 5f, gaugeY + gaugeHeight + 25f,
                pulsePaint
            )
        }
    }
    
    // ==================== LÉGENDES EXPLICATIVES ====================
    
    private fun drawLegends(canvas: Canvas) {
        val legendY = gaugeY + gaugeHeight + 90f
        
        // Ligne 1: Zones colorées
        canvas.drawText("🟢 Calme", gaugeX, legendY, legendPaint)
        canvas.drawText("🟡 Léger", gaugeX + 80f, legendY, legendPaint)
        canvas.drawText("🔴 CHUTE!", gaugeX + 160f, legendY, legendPaint)
        
        // Ligne 2: Types d'aiguilles
        canvas.drawText("⚪ Vent lissé (compte)", gaugeX, legendY + 25f, legendPaint)
        canvas.drawText("🟡 Vent brut (instantané)", gaugeX, legendY + 50f, legendPaint)
        
        // Instructions
        val instructionPaint = Paint().apply {
            color = Color.argb(200, 255, 255, 255)
            textSize = 16f
            isAntiAlias = true
        }
        canvas.drawText("💡 Gardez l'aiguille blanche dans la zone rouge!", gaugeX, legendY + 80f, instructionPaint)
    }
    
    // ==================== JAUGE AVEC STYLE AMÉLIORÉ ====================
    
    fun drawEnhanced(
        canvas: Canvas,
        currentWindForce: Float,
        windForceSmoothed: Float,
        sustainedWindTime: Float,
        extremeWindThreshold: Float,
        extremeWindDuration: Float
    ) {
        // Version améliorée avec effets visuels supplémentaires
        drawGlowEffect(canvas, windForceSmoothed)
        draw(canvas, currentWindForce, windForceSmoothed, sustainedWindTime, extremeWindThreshold, extremeWindDuration)
        drawSparkles(canvas, windForceSmoothed)
    }
    
    private fun drawGlowEffect(canvas: Canvas, windForce: Float) {
        if (windForce > 0.5f) {
            val glowIntensity = (windForce - 0.5f) * 2f
            val glowPaint = Paint().apply {
                color = Color.argb((50 * glowIntensity).toInt(), 255, 255, 255)
                isAntiAlias = true
                maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
            }
            
            val glowRect = RectF(
                gaugeX - 10f, gaugeY - 10f,
                gaugeX + gaugeWidth + 10f, gaugeY + gaugeHeight + 10f
            )
            canvas.drawRoundRect(glowRect, 30f, 30f, glowPaint)
        }
    }
    
    private fun drawSparkles(canvas: Canvas, windForce: Float) {
        if (windForce > 0.8f) {
            val sparklePaint = Paint().apply {
                color = Color.WHITE
                isAntiAlias = true
            }
            
            for (i in 0..5) {
                val sparkleX = gaugeX + kotlin.random.Random.nextFloat() * gaugeWidth
                val sparkleY = gaugeY + kotlin.random.Random.nextFloat() * gaugeHeight
                val sparkleSize = kotlin.random.Random.nextFloat() * 3f + 1f
                
                canvas.drawCircle(sparkleX, sparkleY, sparkleSize, sparklePaint)
            }
        }
    }
    
    // ==================== JAUGE COMPACTE ====================
    
    fun drawCompact(
        canvas: Canvas,
        windForceSmoothed: Float,
        sustainedWindTime: Float,
        extremeWindThreshold: Float,
        extremeWindDuration: Float
    ) {
        // Version compacte pour économiser l'espace
        val compactHeight = 20f
        val compactY = screenHeight * 0.02f
        
        // Fond compact
        val compactRect = RectF(gaugeX, compactY, gaugeX + gaugeWidth, compactY + compactHeight)
        canvas.drawRoundRect(compactRect, 10f, 10f, backgroundPaint)
        
        // Zone de danger uniquement
        val dangerZone = RectF(
            gaugeX + gaugeWidth * extremeWindThreshold, compactY,
            gaugeX + gaugeWidth, compactY + compactHeight
        )
        canvas.drawRect(dangerZone, redZonePaint)
        
        // Aiguille simple
        val needlePos = gaugeX + windForceSmoothed * gaugeWidth
        canvas.drawLine(needlePos, compactY, needlePos, compactY + compactHeight, needlePaint)
        
        // Pourcentage seulement
        val compactText = "${(windForceSmoothed * 100).toInt()}%"
        canvas.drawText(compactText, gaugeX, compactY - 5f, legendPaint)
    }
    
    // ==================== FONCTIONS UTILITAIRES ====================
    
    fun getGaugeRect(): RectF {
        return RectF(gaugeX, gaugeY, gaugeX + gaugeWidth, gaugeY + gaugeHeight + 120f)
    }
    
    fun isPointInGauge(x: Float, y: Float): Boolean {
        return x >= gaugeX && x <= gaugeX + gaugeWidth && 
               y >= gaugeY && y <= gaugeY + gaugeHeight + 120f
    }
    
    fun getWindValueAtPosition(x: Float): Float {
        if (x < gaugeX) return 0f
        if (x > gaugeX + gaugeWidth) return 1f
        return (x - gaugeX) / gaugeWidth
    }
    
    fun cleanup() {
        // Nettoyer les ressources si nécessaire
    }
    
    // ==================== THÈMES VISUELS ====================
    
    fun setDarkTheme() {
        backgroundPaint.color = Color.argb(180, 30, 30, 30)
        textPaint.color = Color.WHITE
        legendPaint.color = Color.rgb(192, 192, 192) // LIGHT_GRAY equivalent
    }
    
    fun setLightTheme() {
        backgroundPaint.color = Color.argb(180, 240, 240, 240)
        textPaint.color = Color.BLACK
        legendPaint.color = Color.rgb(64, 64, 64) // DARK_GRAY equivalent
    }
    
    fun setRainbowTheme() {
        // Thème arc-en-ciel pour s'amuser
        greenZonePaint.color = Color.argb(120, 0, 255, 127)
        yellowZonePaint.color = Color.argb(120, 255, 215, 0)
        redZonePaint.color = Color.argb(120, 255, 20, 147)
    }
}
