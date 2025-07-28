package com.example.windbird

import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

class LugeRenderer(private val engine: LugeGameEngine, private val context: android.content.Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gradientCache = mutableMapOf<String, LinearGradient>()
    private val effectsRenderer = LugeEffectsRenderer(engine)
    private var playerCountry: String = "CA"

    fun drawPreparation(canvas: Canvas, w: Int, h: Int) {
        canvas.save()
        
        // Image de fond depuis drawable
        drawLugeBackgroundImage(canvas, w, h)
        
        // Drapeau du pays du joueur
        drawCountryFlag(canvas, w, h)
        
        // Instructions améliorées
        drawEnhancedPreparationText(canvas, w, h)
        
        canvas.restore()
    }
    
    fun drawRiding(canvas: Canvas, w: Int, h: Int) {
        canvas.save()
        
        // Effets de caméra
        applyCameraEffects(canvas, w, h)
        
        // Fond dynamique
        drawDynamicBackground(canvas, w, h)
        
        // Piste étroite
        drawNarrowTrack(canvas, w, h)
        
        // Éléments de décor
        drawTrackElements(canvas, w, h)
        
        // Lugeur
        drawBasicLuger(canvas, w, h)
        
        // Interface principale
        drawBasicInterface(canvas, w, h)
        
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
    
    private fun drawNarrowTrack(canvas: Canvas, w: Int, h: Int) {
        // Piste 3x plus étroite
        val trackWidth = w * 0.25f
        val curveOffset = engine.currentCurveStrength * engine.curveDirection * 80f
        
        // Piste principale
        paint.color = Color.WHITE
        val trackPath = Path()
        trackPath.moveTo((w - trackWidth) / 2f + curveOffset * 0.2f, 0f)
        trackPath.lineTo((w + trackWidth) / 2f + curveOffset * 0.2f, 0f)
        trackPath.lineTo(w * 0.7f + curveOffset, h.toFloat())
        trackPath.lineTo(w * 0.3f + curveOffset, h.toFloat())
        trackPath.close()
        canvas.drawPath(trackPath, paint)
        
        // Murs
        paint.color = Color.parseColor("#CCCCCC")
        paint.strokeWidth = 8f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(w * 0.3f + curveOffset, 0f, w * 0.3f + curveOffset, h.toFloat(), paint)
        canvas.drawLine(w * 0.7f + curveOffset, 0f, w * 0.7f + curveOffset, h.toFloat(), paint)
        paint.style = Paint.Style.FILL
        
        // Lignes de glace
        drawIceLines(canvas, w, h, curveOffset)
    }
    
    private fun drawIceLines(canvas: Canvas, w: Int, h: Int, curveOffset: Float) {
        paint.color = Color.parseColor("#EEEEFF")
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        
        val lineSpeed = engine.speed / 15f
        for (i in 1..8) {
            val lineY = (i * h / 9f + (engine.distance * lineSpeed) % (h / 9f))
            val lineLeft = w * 0.35f + curveOffset * 0.8f
            val lineRight = w * 0.65f + curveOffset * 0.8f
            canvas.drawLine(lineLeft, lineY, lineRight, lineY, paint)
        }
        
        paint.style = Paint.Style.FILL
    }
    
    private fun drawTrackElements(canvas: Canvas, w: Int, h: Int) {
        for (element in engine.trackElements) {
            if (element.distance > -50f && element.distance < 800f) {
                val depth = (element.distance + 50f) / 850f
                val scale = (1f - depth * 0.9f).coerceIn(0.05f, 1f)
                
                val baseX = w/2f + element.sideOffset * element.offsetDistance * scale
                val baseY = h * (0.2f + depth * 0.7f)
                
                if (scale > 0.1f) {
                    drawSimpleElement(canvas, baseX, baseY, scale, element.height)
                }
            }
        }
    }
    
    private fun drawSimpleElement(canvas: Canvas, x: Float, y: Float, scale: Float, height: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(scale, scale)
        
        paint.alpha = (scale * 255).toInt()
        
        // Arbre simple
        paint.color = Color.parseColor("#8B4513")
        canvas.drawRect(-4f, 10f, 4f, 20f, paint)
        
        paint.color = Color.parseColor("#228B22")
        val path = Path()
        path.moveTo(0f, -height * 0.3f)
        path.lineTo(-height * 0.2f, 10f)
        path.lineTo(height * 0.2f, 10f)
        path.close()
        canvas.drawPath(path, paint)
        
        paint.alpha = 255
        canvas.restore()
    }
    
    private fun drawLugeBackgroundImage(canvas: Canvas, w: Int, h: Int) {
        try {
            val resourceId = context.resources.getIdentifier("luge_preparation", "drawable", context.packageName)
            if (resourceId != 0) {
                val backgroundBitmap = BitmapFactory.decodeResource(context.resources, resourceId)
                val scaledBitmap = Bitmap.createScaledBitmap(backgroundBitmap, w, h, true)
                canvas.drawBitmap(scaledBitmap, 0f, 0f, paint)
                
                // Overlay léger pour lisibilité
                paint.color = Color.parseColor("#30000000")
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            } else {
                // Fond par défaut si image pas trouvée
                drawDefaultBackground(canvas, w, h)
            }
        } catch (e: Exception) {
            drawDefaultBackground(canvas, w, h)
        }
    }
    
    private fun drawDefaultBackground(canvas: Canvas, w: Int, h: Int) {
        val skyGradient = getOrCreateGradient("sky", 
            intArrayOf(Color.parseColor("#87CEEB"), Color.parseColor("#E0F6FF")),
            0f, 0f, 0f, h.toFloat())
        paint.shader = skyGradient
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        
        drawNarrowTrack(canvas, w, h)
        drawStartEnvironment(canvas, w, h)
    }
    
    private fun drawStartEnvironment(canvas: Canvas, w: Int, h: Int) {
        // Arbres simples
        paint.color = Color.parseColor("#228B22")
        for (i in 0..6) {
            val treeX = i * w / 7f + Random.nextFloat() * 30f
            val treeY = h * 0.4f + Random.nextFloat() * 150f
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
        canvas.drawRect(-6f, 15f, 6f, 30f, paint)
        
        // Feuillage
        paint.color = Color.parseColor("#228B22")
        val path = Path()
        path.moveTo(0f, -15f)
        path.lineTo(-12f, 15f)
        path.lineTo(12f, 15f)
        path.close()
        canvas.drawPath(path, paint)
        
        canvas.restore()
    }
    
    private fun drawStartGate(canvas: Canvas, w: Int, h: Int) {
        paint.color = Color.parseColor("#FF0000")
        paint.strokeWidth = 6f
        paint.style = Paint.Style.STROKE
        
        // Poteaux
        canvas.drawLine(w * 0.3f, h * 0.6f, w * 0.3f, h * 0.4f, paint)
        canvas.drawLine(w * 0.7f, h * 0.6f, w * 0.7f, h * 0.4f, paint)
        
        // Barre
        canvas.drawLine(w * 0.3f, h * 0.4f, w * 0.7f, h * 0.4f, paint)
        
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#FF4444")
        canvas.drawRect(w * 0.45f, h * 0.35f, w * 0.55f, h * 0.45f, paint)
        
        paint.color = Color.WHITE
        paint.textSize = 20f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("START", w/2f, h * 0.42f, paint)
    }
    
    private fun drawCountryFlag(canvas: Canvas, w: Int, h: Int) {
        val flagSize = 60f
        val flagX = 20f
        val flagY = 20f
        
        // Fond du drapeau
        paint.color = Color.WHITE
        canvas.drawRoundRect(flagX - 3f, flagY - 3f, flagX + flagSize + 3f, flagY + flagSize * 0.6f + 3f, 5f, 5f, paint)
        
        // Drapeau canadien simple
        when (playerCountry) {
            "CA" -> drawCanadianFlag(canvas, flagX, flagY, flagSize)
            else -> drawGenericFlag(canvas, flagX, flagY, flagSize)
        }
        
        // Bordure
        paint.color = Color.parseColor("#333333")
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(flagX, flagY, flagX + flagSize, flagY + flagSize * 0.6f, 3f, 3f, paint)
        paint.style = Paint.Style.FILL
    }
    
    private fun drawCanadianFlag(canvas: Canvas, x: Float, y: Float, size: Float) {
        val flagHeight = size * 0.6f
        
        // Bandes rouges
        paint.color = Color.parseColor("#FF0000")
        canvas.drawRect(x, y, x + size * 0.25f, y + flagHeight, paint)
        canvas.drawRect(x + size * 0.75f, y, x + size, y + flagHeight, paint)
        
        // Bande blanche
        paint.color = Color.WHITE
        canvas.drawRect(x + size * 0.25f, y, x + size * 0.75f, y + flagHeight, paint)
        
        // Feuille d'érable
        paint.color = Color.parseColor("#FF0000")
        val centerX = x + size * 0.5f
        val centerY = y + flagHeight * 0.5f
        val leafSize = size * 0.1f
        
        canvas.drawCircle(centerX, centerY, leafSize, paint)
    }
    
    private fun drawGenericFlag(canvas: Canvas, x: Float, y: Float, size: Float) {
        val flagHeight = size * 0.6f
        paint.color = Color.BLUE
        canvas.drawRect(x, y, x + size, y + flagHeight, paint)
    }
    
    fun setPlayerCountry(country: String) {
        playerCountry = country
    }
    
    private fun drawEnhancedPreparationText(canvas: Canvas, w: Int, h: Int) {
        // Titre principal
        paint.color = Color.parseColor("#FFFFFF")
        paint.textSize = 48f
        paint.textAlign = Paint.Align.CENTER
        paint.setShadowLayer(4f, 2f, 2f, Color.parseColor("#80000000"))
        canvas.drawText("🛷 LUGE EXTRÊME 🛷", w/2f, h * 0.15f, paint)
        
        // Sous-titre
        paint.textSize = 32f
        paint.color = Color.parseColor("#FFD700")
        canvas.drawText("Préparation...", w/2f, h * 0.22f, paint)
        
        // Instructions
        paint.clearShadowLayer()
        paint.setShadowLayer(2f, 1f, 1f, Color.parseColor("#80000000"))
        
        paint.textSize = 24f
        paint.color = Color.parseColor("#FFFFFF")
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("📱 INCLINEZ", 30f, h * 0.75f, paint)
        canvas.drawText("   pour diriger", 30f, h * 0.80f, paint)
        
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("SECOUEZ 📱", w - 30f, h * 0.75f, paint)
        canvas.drawText("pour freiner", w - 30f, h * 0.80f, paint)
        
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 20f
        paint.color = Color.parseColor("#FFD700")
        canvas.drawText("⚡ Position aérodynamique = VITESSE MAX ⚡", w/2f, h * 0.9f, paint)
        
        paint.clearShadowLayer()
    }
    
    private fun drawBasicLuger(canvas: Canvas, w: Int, h: Int) {
        val lugerScreenX = engine.lugerX * w
        val lugerScreenY = h * 0.75f
        
        canvas.save()
        canvas.translate(lugerScreenX, lugerScreenY)
        canvas.rotate(engine.gForce * 8f + engine.lugerRotation)
        
        // Ombre
        paint.alpha = 120
        paint.color = Color.parseColor("#000000")
        canvas.drawOval(-25f, 3f, 25f, 10f, paint)
        paint.alpha = 255
        
        // Utiliser l'image du lugeur
        try {
            val resourceId = context.resources.getIdentifier("luge", "drawable", context.packageName)
            if (resourceId != 0) {
                val lugerBitmap = BitmapFactory.decodeResource(context.resources, resourceId)
                val scaledBitmap = Bitmap.createScaledBitmap(lugerBitmap, 80, 80, true)
                canvas.drawBitmap(scaledBitmap, -40f, -40f, paint)
            } else {
                // Fallback si image pas trouvée
                drawFallbackLuger(canvas)
            }
        } catch (e: Exception) {
            drawFallbackLuger(canvas)
        }
        
        canvas.restore()
        
        // Traînée de vitesse
        if (engine.speed > 30f) {
            drawSpeedTrail(canvas, lugerScreenX, lugerScreenY)
        }
    }
    
    private fun drawFallbackLuger(canvas: Canvas) {
        // Luge
        paint.color = Color.parseColor("#444444")
        canvas.drawRoundRect(-40f, 0f, 40f, 50f, 12f, 12f, paint)
        
        // Lugeur
        paint.color = Color.parseColor("#FF6600")
        canvas.drawOval(-35f, -25f, 35f, 25f, paint)
        
        // Casque
        paint.color = Color.parseColor("#0066CC")
        canvas.drawCircle(0f, -30f, 15f, paint)
        
        // Patins
        paint.color = Color.parseColor("#AAAAAA")
        paint.strokeWidth = 6f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(-25f, 45f, -25f, 55f, paint)
        canvas.drawLine(25f, 45f, 25f, 55f, paint)
        paint.style = Paint.Style.FILL
    }
    
    private fun drawSpeedTrail(canvas: Canvas, lugerX: Float, lugerY: Float) {
        val trailIntensity = (engine.speed - 30f) / (engine.maxSpeed - 30f)
        val trailLength = 2 + (trailIntensity * 3).toInt()
        
        for (i in 1..trailLength) {
            val trailAlpha = ((trailLength - i) * 200 / trailLength * trailIntensity).toInt()
            paint.alpha = trailAlpha
            
            val trailScale = 1f - i * 0.2f
            val trailY = lugerY + i * 25f
            
            paint.color = when {
                engine.speed > 100f -> Color.parseColor("#FF0000")
                engine.speed > 70f -> Color.parseColor("#FF6600")
                else -> Color.parseColor("#FFFFFF")
            }
            
            canvas.drawOval(
                lugerX - 30f * trailScale, trailY,
                lugerX + 30f * trailScale, trailY + 15f * trailScale,
                paint
            )
        }
        paint.alpha = 255
    }
    
    private fun drawBasicInterface(canvas: Canvas, w: Int, h: Int) {
        val baseY = h - 150f
        
        // Compteur de vitesse
        drawBasicSpeedometer(canvas, w - 120f, 100f)
        
        // Métriques de base
        paint.color = Color.parseColor("#001133")
        paint.textSize = 16f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Parfaits: ${engine.perfectCurves}", 15f, baseY, paint)
        canvas.drawText("Murs: ${engine.wallHits}", 15f, baseY + 25f, paint)
        canvas.drawText("Temps: ${engine.raceTime.toInt()}s", 15f, baseY + 50f, paint)
        canvas.drawText("Vitesse: ${engine.speed.toInt()} km/h", 15f, baseY + 75f, paint)
        
        // Barres de performance
        drawBasicMeter(canvas, 150f, baseY, 140f, engine.precision / 130f, "PRÉCISION", Color.GREEN)
        drawBasicMeter(canvas, 150f, baseY + 25f, 140f, engine.aerodynamics / 130f, "AÉRO", Color.BLUE)
        drawBasicMeter(canvas, 150f, baseY + 50f, 140f, engine.stamina / 100f, "STAMINA", Color.MAGENTA)
        
        // Avertissement vitesse
        if (engine.speed > 100f) {
            paint.color = Color.RED
            paint.textSize = 24f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("⚠️ VITESSE EXTRÊME ⚠️", w/2f, h - 20f, paint)
        }
    }
    
    private fun drawBasicSpeedometer(canvas: Canvas, centerX: Float, centerY: Float) {
        // Cadran
        paint.color = Color.parseColor("#333333")
        canvas.drawCircle(centerX, centerY, 50f, paint)
        
        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, 45f, paint)
        
        // Aiguille
        val speedAngle = (engine.speed / engine.maxSpeed) * 270f - 135f
        paint.color = Color.RED
        paint.strokeWidth = 4f
        paint.style = Paint.Style.STROKE
        
        val needleX = centerX + cos(Math.toRadians(speedAngle.toDouble())).toFloat() * 35f
        val needleY = centerY + sin(Math.toRadians(speedAngle.toDouble())).toFloat() * 35f
        canvas.drawLine(centerX, centerY, needleX, needleY, paint)
        
        paint.style = Paint.Style.FILL
        
        // Valeur numérique
        paint.color = Color.BLACK
        paint.textSize = 14f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("${engine.speed.toInt()}", centerX, centerY + 55f, paint)
        canvas.drawText("km/h", centerX, centerY + 70f, paint)
    }
    
    private fun drawBasicMeter(canvas: Canvas, x: Float, y: Float, width: Float, 
                              value: Float, label: String, color: Int) {
        val barHeight = 15f
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
        paint.textSize = 12f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("$label: ${(clampedValue * 100).toInt()}%", x, y - 3f, paint)
    }
    
    private fun drawResultsMetrics(canvas: Canvas, w: Int, h: Int) {
        paint.color = Color.parseColor("#333333")
        paint.textSize = 20f
        paint.textAlign = Paint.Align.CENTER
        
        val metrics = arrayOf(
            "⏱️ Temps: ${engine.raceTime.toInt()}s",
            "⚡ Vitesse max: ${engine.topSpeed.toInt()} km/h",
            "🎯 Virages parfaits: ${engine.perfectCurves}",
            "💨 Aérodynamisme: ${engine.aerodynamics.toInt()}%",
            "🎪 Précision: ${engine.precision.toInt()}%"
        )
        
        for ((index, metric) in metrics.withIndex()) {
            canvas.drawText(metric, w/2f, h * (0.5f + index * 0.08f), paint)
        }
        
        if (engine.wallHits > 0) {
            paint.color = Color.RED
            canvas.drawText("💥 Contacts murs: ${engine.wallHits}", w/2f, h * 0.9f, paint)
        }
    }
    
    private fun getOrCreateGradient(key: String, colors: IntArray, x0: Float, y0: Float, x1: Float, y1: Float): LinearGradient {
        return gradientCache.getOrPut(key) {
            LinearGradient(x0, y0, x1, y1, colors, null, Shader.TileMode.CLAMP)
        }
    }
}
