package com.example.windbird

import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

class LugeRenderer(private val engine: LugeGameEngine) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gradientCache = mutableMapOf<String, LinearGradient>()
    private val effectsRenderer = LugeEffectsRenderer(engine)

    fun drawPreparation(canvas: Canvas, w: Int, h: Int) {
        canvas.save()
        applyBasicVisualEffects(canvas, w, h)
        
        // Fond glacé avec dégradé
        val skyGradient = getOrCreateGradient("sky", 
            intArrayOf(Color.parseColor("#87CEEB"), Color.parseColor("#E0F6FF")),
            0f, 0f, 0f, h.toFloat())
        paint.shader = skyGradient
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        
        // Piste de base
        drawBasicTrack(canvas, w, h, true)
        
        // Environnement de départ
        drawStartEnvironment(canvas, w, h)
        
        // Instructions
        drawPreparationText(canvas, w, h)
        
        canvas.restore()
    }
    
    fun drawRiding(canvas: Canvas, w: Int, h: Int) {
        canvas.save()
        
        // Effets de caméra
        applyCameraEffects(canvas, w, h)
        
        // Fond dynamique
        drawDynamicBackground(canvas, w, h)
        
        // Piste 3D
        drawBasicTrack(canvas, w, h, false)
        
        // Lugeur
        drawBasicLuger(canvas, w, h)
        
        // Interface principale
        drawBasicInterface(canvas, w, h)
        
        // Déléguer les effets complexes
        effectsRenderer.drawAllEffects(canvas, w, h)
        
        canvas.restore()
    }
    
    fun drawResults(canvas: Canvas, w: Int, h: Int) {
        // Fond métallique
        paint.color = Color.parseColor("#F8F8FF")
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        
        // Bandeau argenté
        paint.color = Color.parseColor("#C0C0C0")
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
        
        // Score final
        paint.color = Color.parseColor("#001133")
        paint.textSize = 96f
        paint.textAlign = Paint.Align.CENTER
        paint.setShadowLayer(5f, 3f, 3f, Color.parseColor("#80000000"))
        canvas.drawText("${engine.finalScore}", w/2f, h * 0.2f, paint)
        
        paint.textSize = 36f
        canvas.drawText("POINTS", w/2f, h * 0.3f, paint)
        
        // Métriques de base
        drawResultsMetrics(canvas, w, h)
        
        paint.clearShadowLayer()
    }
    
    private fun applyCameraEffects(canvas: Canvas, w: Int, h: Int) {
        // Shake de caméra
        if (engine.cameraShake > 0f) {
            canvas.translate(
                (Random.nextFloat() - 0.5f) * engine.cameraShake * 20f,
                (Random.nextFloat() - 0.5f) * engine.cameraShake * 20f
            )
        }
        
        // Rotation pour les virages
        canvas.translate(w/2f, h/2f)
        canvas.rotate(engine.cameraRotation)
        canvas.translate(-w/2f, -h/2f)
        
        applyBasicVisualEffects(canvas, w, h)
    }
    
    private fun applyBasicVisualEffects(canvas: Canvas, w: Int, h: Int) {
        // Flou de vitesse
        if (engine.speedBlur > 0.3f) {
            paint.alpha = (engine.speedBlur * 60).toInt()
            paint.color = Color.parseColor("#EEFFFFFF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.alpha = 255
        }
        
        // Vignetting
        if (engine.vignetting > 0f) {
            val vignette = RadialGradient(
                w/2f, h/2f, w*0.8f,
                intArrayOf(Color.TRANSPARENT, Color.parseColor("#40000000")),
                floatArrayOf(0.7f, 1f),
                Shader.TileMode.CLAMP
            )
            paint.shader = vignette
            paint.alpha = (engine.vignetting * 150).toInt()
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
            paint.alpha = 255
        }
    }
    
    private fun drawDynamicBackground(canvas: Canvas, w: Int, h: Int) {
        val skyColor = when {
            engine.speed > 110f -> Color.parseColor("#F0F0FF")
            engine.speed > 80f -> Color.parseColor("#F5F5FF")
            engine.speed > 50f -> Color.parseColor("#FAFAFF")
            else -> Color.parseColor("#E6F8FF")
        }
        
        val groundColor = Color.parseColor("#FFFFFF")
        val skyGradient = getOrCreateGradient("riding_sky",
            intArrayOf(skyColor, groundColor),
            0f, 0f, 0f, h.toFloat())
        paint.shader = skyGradient
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
    }
    
    private fun drawBasicTrack(canvas: Canvas, w: Int, h: Int, isPreparation: Boolean) {
        val trackWidth = w * 0.7f
        val curveOffset = if (isPreparation) 0f else engine.currentCurveStrength * engine.curveDirection * 120f
        
        // Piste principale
        paint.color = Color.WHITE
        val trackPath = Path()
        trackPath.moveTo((w - trackWidth) / 2f + curveOffset * 0.2f, 0f)
        trackPath.lineTo((w + trackWidth) / 2f + curveOffset * 0.2f, 0f)
        trackPath.lineTo(w * 0.9f + curveOffset, h.toFloat())
        trackPath.lineTo(w * 0.1f + curveOffset, h.toFloat())
        trackPath.close()
        canvas.drawPath(trackPath, paint)
        
        // Murs
        paint.color = Color.parseColor("#CCCCCC")
        paint.strokeWidth = 12f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(w * 0.1f + curveOffset, 0f, w * 0.1f + curveOffset, h.toFloat(), paint)
        canvas.drawLine(w * 0.9f + curveOffset, 0f, w * 0.9f + curveOffset, h.toFloat(), paint)
        paint.style = Paint.Style.FILL
        
        // Lignes de glace
        if (!isPreparation) {
            drawIceLines(canvas, w, h, curveOffset)
        }
    }
    
    private fun drawIceLines(canvas: Canvas, w: Int, h: Int, curveOffset: Float) {
        paint.color = Color.parseColor("#EEEEFF")
        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE
        
        val lineSpeed = engine.speed / 20f
        for (i in 1..12) {
            val lineY = (i * h / 13f + (engine.distance * lineSpeed) % (h / 13f))
            val lineLeft = w * 0.15f + curveOffset * 0.8f
            val lineRight = w * 0.85f + curveOffset * 0.8f
            canvas.drawLine(lineLeft, lineY, lineRight, lineY, paint)
        }
        
        paint.style = Paint.Style.FILL
    }
    
    private fun drawStartEnvironment(canvas: Canvas, w: Int, h: Int) {
        // Arbres simples
        paint.color = Color.parseColor("#228B22")
        for (i in 0..8) {
            val treeX = i * w / 9f + Random.nextFloat() * 50f
            val treeY = h * 0.3f + Random.nextFloat() * 200f
            drawSimpleTree(canvas, treeX, treeY)
        }
        
        // Portique de départ
        drawStartGate(canvas, w, h)
    }
    
    private fun drawSimpleTree(canvas: Canvas, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        
        // Tronc
        paint.color = Color.parseColor("#8B4513")
        canvas.drawRect(-8f, 20f, 8f, 40f, paint)
        
        // Feuillage
        paint.color = Color.parseColor("#228B22")
        val path = Path()
        path.moveTo(0f, -20f)
        path.lineTo(-15f, 20f)
        path.lineTo(15f, 20f)
        path.close()
        canvas.drawPath(path, paint)
        
        canvas.restore()
    }
    
    private fun drawStartGate(canvas: Canvas, w: Int, h: Int) {
        paint.color = Color.parseColor("#FF0000")
        paint.strokeWidth = 8f
        paint.style = Paint.Style.STROKE
        
        // Poteaux
        canvas.drawLine(w * 0.1f, h * 0.6f, w * 0.1f, h * 0.4f, paint)
        canvas.drawLine(w * 0.9f, h * 0.6f, w * 0.9f, h * 0.4f, paint)
        
        // Barre
        canvas.drawLine(w * 0.1f, h * 0.4f, w * 0.9f, h * 0.4f, paint)
        
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#FF4444")
        canvas.drawRect(w * 0.4f, h * 0.35f, w * 0.6f, h * 0.45f, paint)
        
        paint.color = Color.WHITE
        paint.textSize = 24f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("START", w/2f, h * 0.42f, paint)
    }
    
    private fun drawPreparationText(canvas: Canvas, w: Int, h: Int) {
        paint.color = Color.parseColor("#001133")
        paint.textSize = 56f
        paint.textAlign = Paint.Align.CENTER
        paint.setShadowLayer(3f, 2f, 2f, Color.parseColor("#80000000"))
        canvas.drawText("🛷 LUGE EXTRÊME 🛷", w/2f, h * 0.12f, paint)
        
        paint.textSize = 38f
        paint.color = Color.parseColor("#0066CC")
        canvas.drawText("Préparation...", w/2f, h * 0.82f, paint)
        
        paint.textSize = 28f
        paint.color = Color.parseColor("#222222")
        canvas.drawText("📱 Inclinez DOUCEMENT pour diriger", w/2f, h * 0.88f, paint)
        canvas.drawText("📱 Secouez FORT pour freiner", w/2f, h * 0.92f, paint)
        canvas.drawText("⚡ Position aéro = Vitesse MAX", w/2f, h * 0.96f, paint)
        
        paint.clearShadowLayer()
    }
    
    private fun drawBasicLuger(canvas: Canvas, w: Int, h: Int) {
        val lugerScreenX = engine.lugerX * w
        val lugerScreenY = h * 0.75f
        
        canvas.save()
        canvas.translate(lugerScreenX, lugerScreenY)
        canvas.rotate(engine.gForce * 12f + engine.lugerRotation)
        
        // Ombre
        paint.alpha = 150
        paint.color = Color.parseColor("#000000")
        canvas.drawOval(-30f, 5f, 30f, 15f, paint)
        paint.alpha = 255
        
        // Luge
        paint.color = Color.parseColor("#444444")
        canvas.drawRoundRect(-50f, 0f, 50f, 60f, 15f, 15f, paint)
        
        // Lugeur
        paint.color = Color.parseColor("#FF6600")
        canvas.drawOval(-45f, -30f, 45f, 30f, paint)
        
        // Casque
        paint.color = Color.parseColor("#0066CC")
        canvas.drawCircle(0f, -35f, 18f, paint)
        
        // Patins
        paint.color = Color.parseColor("#AAAAAA")
        paint.strokeWidth = 8f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(-30f, 55f, -30f, 70f, paint)
        canvas.drawLine(30f, 55f, 30f, 70f, paint)
        paint.style = Paint.Style.FILL
        
        canvas.restore()
        
        // Traînée de vitesse
        if (engine.speed > 40f) {
            drawSpeedTrail(canvas, lugerScreenX, lugerScreenY)
        }
    }
    
    private fun drawSpeedTrail(canvas: Canvas, lugerX: Float, lugerY: Float) {
        val trailIntensity = (engine.speed - 40f) / (engine.maxSpeed - 40f)
        val trailLength = 3 + (trailIntensity * 3).toInt()
        
        for (i in 1..trailLength) {
            val trailAlpha = ((trailLength - i) * 255 / trailLength * trailIntensity).toInt()
            paint.alpha = trailAlpha
            
            val trailScale = 1f - i * 0.15f
            val trailY = lugerY + i * 30f
            
            paint.color = when {
                engine.speed > 120f -> Color.parseColor("#FF0000")
                engine.speed > 100f -> Color.parseColor("#FF6600")
                else -> Color.parseColor("#FFFFFF")
            }
            
            canvas.drawOval(
                lugerX - 40f * trailScale, trailY,
                lugerX + 40f * trailScale, trailY + 20f * trailScale,
                paint
            )
        }
        paint.alpha = 255
    }
    
    private fun drawBasicInterface(canvas: Canvas, w: Int, h: Int) {
        val baseY = h - 200f
        
        // Compteur de vitesse
        drawBasicSpeedometer(canvas, w - 150f, 120f)
        
        // Métriques de base
        paint.color = Color.parseColor("#001133")
        paint.textSize = 20f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Parfaits: ${engine.perfectCurves}", 20f, baseY, paint)
        canvas.drawText("Murs: ${engine.wallHits}", 20f, baseY + 30f, paint)
        canvas.drawText("Temps: ${engine.raceTime.toInt()}s", 20f, baseY + 60f, paint)
        canvas.drawText("Vitesse: ${engine.speed.toInt()} km/h", 20f, baseY + 90f, paint)
        
        // Barres de performance
        drawBasicMeter(canvas, 200f, baseY, 180f, engine.precision / 140f, "PRÉCISION", Color.GREEN)
        drawBasicMeter(canvas, 200f, baseY + 30f, 180f, engine.aerodynamics / 140f, "AÉRO", Color.BLUE)
        drawBasicMeter(canvas, 200f, baseY + 60f, 180f, engine.stamina / 100f, "STAMINA", Color.MAGENTA)
        
        // Avertissements
        if (engine.speed > 120f) {
            paint.color = Color.RED
            paint.textSize = 28f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("⚠️ VITESSE EXTRÊME ⚠️", w/2f, h - 40f, paint)
        }
    }
    
    private fun drawBasicSpeedometer(canvas: Canvas, centerX: Float, centerY: Float) {
        // Cadran
        paint.color = Color.parseColor("#333333")
        canvas.drawCircle(centerX, centerY, 60f, paint)
        
        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, 55f, paint)
        
        // Aiguille
        val speedAngle = (engine.speed / engine.maxSpeed) * 270f - 135f
        paint.color = Color.RED
        paint.strokeWidth = 6f
        paint.style = Paint.Style.STROKE
        
        val needleX = centerX + cos(Math.toRadians(speedAngle.toDouble())).toFloat() * 45f
        val needleY = centerY + sin(Math.toRadians(speedAngle.toDouble())).toFloat() * 45f
        canvas.drawLine(centerX, centerY, needleX, needleY, paint)
        
        paint.style = Paint.Style.FILL
        
        // Valeur numérique
        paint.color = Color.BLACK
        paint.textSize = 16f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("${engine.speed.toInt()}", centerX, centerY + 70f, paint)
        canvas.drawText("km/h", centerX, centerY + 90f, paint)
    }
    
    private fun drawBasicMeter(canvas: Canvas, x: Float, y: Float, width: Float, 
                              value: Float, label: String, color: Int) {
        val barHeight = 18f
        val clampedValue = value.coerceIn(0f, 1f)
        
        // Fond
        paint.color = Color.parseColor("#333333")
        canvas.drawRect(x, y, x + width, y + barHeight, paint)
        
        // Barre
        paint.color = color
        val filledWidth = clampedValue * width
        canvas.drawRect(x, y, x + filledWidth, y + barHeight, paint)
        
        // Label
        paint.color = Color.WHITE
        paint.textSize = 14f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("$label: ${(clampedValue * 100).toInt()}%", x, y - 5f, paint)
    }
    
    private fun drawResultsMetrics(canvas: Canvas, w: Int, h: Int) {
        paint.color = Color.parseColor("#333333")
        paint.textSize = 22f
        paint.textAlign = Paint.Align.CENTER
        
        val metrics = arrayOf(
            "⏱️ Temps: ${engine.raceTime.toInt()}s",
            "⚡ Vitesse max: ${engine.topSpeed.toInt()} km/h",
            "🎯 Virages parfaits: ${engine.perfectCurves}",
            "💨 Aérodynamisme: ${engine.aerodynamics.toInt()}%",
            "🎪 Précision: ${engine.precision.toInt()}%",
            "💪 Stamina: ${engine.stamina.toInt()}%"
        )
        
        for ((index, metric) in metrics.withIndex()) {
            canvas.drawText(metric, w/2f, h * (0.5f + index * 0.06f), paint)
        }
        
        if (engine.wallHits > 0) {
            paint.color = Color.RED
            canvas.drawText("💥 Contacts murs: ${engine.wallHits}", w/2f, h * 0.86f, paint)
        }
        
        if (engine.brakingUsed) {
            paint.color = Color.parseColor("#666666")
            canvas.drawText("🦶 Freinage utilisé", w/2f, h * 0.92f, paint)
        }
    }
    
    private fun getOrCreateGradient(key: String, colors: IntArray, x0: Float, y0: Float, x1: Float, y1: Float): LinearGradient {
        return gradientCache.getOrPut(key) {
            LinearGradient(x0, y0, x1, y1, colors, null, Shader.TileMode.CLAMP)
        }
    }
}
