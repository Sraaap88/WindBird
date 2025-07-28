package com.example.windbird

import android.graphics.*
import kotlin.math.*
import kotlin.random.Random

class LugeEffectsRenderer(private val engine: LugeGameEngine) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun drawAllEffects(canvas: Canvas, w: Int, h: Int) {
        // Éléments de piste 3D
        drawTrackElements3D(canvas, w, h)
        
        // Indicateurs de virages avancés
        drawAdvancedCurveIndicators(canvas, w, h)
        
        // Tous les systèmes de particules
        drawParticleEffects(canvas, w, h)
        
        // Interface avancée
        drawAdvancedInterface(canvas, w, h)
        
        // Effets spéciaux selon la vitesse
        drawSpeedSpecialEffects(canvas, w, h)
    }
    
    private fun drawTrackElements3D(canvas: Canvas, w: Int, h: Int) {
        for (element in engine.trackElements) {
            val elementDistance = element.distance - engine.distance
            if (elementDistance > -100f && elementDistance < 800f) {
                val depth = (elementDistance + 100f) / 900f
                val scale = (1f - depth * 0.8f).coerceIn(0.1f, 1f)
                
                val baseX = w/2f + element.sideOffset * element.offsetDistance * scale
                val baseY = h * (0.3f + depth * 0.6f)
                
                if (scale > 0.2f) {
                    drawTrackElement3D(canvas, element, baseX, baseY, scale)
                }
            }
        }
    }
    
    private fun drawTrackElement3D(canvas: Canvas, element: TrackElement, x: Float, y: Float, scale: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(scale, scale)
        
        paint.alpha = (scale * 255).toInt()
        
        when (element.type) {
            TrackElement.ElementType.TREE -> drawElement3DTree(canvas, element)
            TrackElement.ElementType.SAFETY_BARRIER -> drawElement3DBarrier(canvas, element)
            TrackElement.ElementType.SPECTATOR -> drawElement3DSpectator(canvas, element)
            TrackElement.ElementType.FLAG -> drawElement3DFlag(canvas, element)
            TrackElement.ElementType.TUNNEL_WALL -> drawElement3DTunnel(canvas, element)
            TrackElement.ElementType.SPEED_MARKER -> drawElement3DSpeedMarker(canvas, element)
            TrackElement.ElementType.ROCK -> drawElement3DRock(canvas, element)
        }
        
        paint.alpha = 255
        canvas.restore()
    }
    
    private fun drawElement3DTree(canvas: Canvas, element: TrackElement) {
        // Ombre
        paint.color = Color.parseColor("#40000000")
        canvas.drawOval(-25f, 15f, 25f, 25f, paint)
        
        // Tronc avec volume
        paint.color = Color.parseColor("#8B4513")
        canvas.drawRect(-6f, 20f, 6f, 40f, paint)
        paint.color = Color.parseColor("#654321")
        canvas.drawRect(6f, 20f, 10f, 40f, paint)
        
        // Feuillage
        paint.color = Color.parseColor("#228B22")
        drawTriangle(canvas, 0f, -element.height, -20f, 20f, 20f, 20f)
        paint.color = Color.parseColor("#32CD32")
        drawTriangle(canvas, 5f, -element.height + 5f, -15f, 25f, 25f, 25f)
    }
    
    private fun drawElement3DBarrier(canvas: Canvas, element: TrackElement) {
        // Barrière avec volume
        paint.color = Color.parseColor("#FF0000")
        canvas.drawRect(-30f, 0f, 30f, element.height, paint)
        
        paint.color = Color.parseColor("#CC0000")
        canvas.drawRect(30f, 0f, 35f, element.height, paint)
        
        // Bandes réfléchissantes
        paint.color = Color.WHITE
        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE
        for (i in 1..3) {
            val stripeY = i * element.height / 4f
            canvas.drawLine(-30f, stripeY, 30f, stripeY, paint)
        }
        paint.style = Paint.Style.FILL
    }
    
    private fun drawElement3DSpectator(canvas: Canvas, element: TrackElement) {
        // Spectateur animé
        val wave = sin(engine.raceTime * 2f) * 5f
        
        // Corps
        paint.color = Color.parseColor("#FF6600")
        canvas.drawRect(-8f, 0f, 8f, 60f, paint)
        
        // Bras qui bougent
        paint.color = Color.parseColor("#FFAA66")
        paint.strokeWidth = 6f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(-8f, 20f, -15f + wave, 10f + wave, paint)
        canvas.drawLine(8f, 20f, 15f + wave, 10f + wave, paint)
        paint.style = Paint.Style.FILL
        
        // Tête
        paint.color = Color.parseColor("#FFDDAA")
        canvas.drawCircle(0f, -10f, 12f, paint)
        
        // Casquette
        paint.color = Color.parseColor("#0066CC")
        canvas.drawOval(-15f, -25f, 15f, -5f, paint)
    }
    
    private fun drawElement3DFlag(canvas: Canvas, element: TrackElement) {
        // Mât
        paint.color = Color.parseColor("#8B4513")
        paint.strokeWidth = 6f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(0f, -element.height, 0f, 20f, paint)
        paint.style = Paint.Style.FILL
        
        // Drapeau qui flotte
        val wave1 = sin(engine.raceTime * 3f) * 10f
        val wave2 = sin(engine.raceTime * 3f + 1f) * 8f
        
        paint.color = Color.parseColor("#FF0000")
        val flagPath = Path()
        flagPath.moveTo(0f, -element.height)
        flagPath.lineTo(60f + wave1, -element.height + 10f)
        flagPath.lineTo(60f + wave2, -element.height + 40f)
        flagPath.lineTo(0f, -element.height + 30f)
        flagPath.close()
        canvas.drawPath(flagPath, paint)
    }
    
    private fun drawElement3DTunnel(canvas: Canvas, element: TrackElement) {
        // Mur de tunnel
        paint.color = Color.parseColor("#666666")
        canvas.drawRect(-20f, -element.height, 20f, 20f, paint)
        
        // Texture de pierre
        paint.color = Color.parseColor("#555555")
        for (i in 0..3) {
            for (j in 0..2) {
                val blockX = -20f + i * 10f
                val blockY = -element.height + j * 20f
                canvas.drawRect(blockX, blockY, blockX + 9f, blockY + 19f, paint)
            }
        }
    }
    
    private fun drawElement3DSpeedMarker(canvas: Canvas, element: TrackElement) {
        // Panneau de vitesse
        paint.color = Color.parseColor("#00FF00")
        canvas.drawRect(-15f, 0f, 15f, element.height, paint)
        
        // Bordure
        paint.color = Color.parseColor("#00AA00")
        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE
        canvas.drawRect(-15f, 0f, 15f, element.height, paint)
        paint.style = Paint.Style.FILL
        
        // Vitesse
        paint.color = Color.BLACK
        paint.textSize = 18f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("${engine.speed.toInt()}", 0f, element.height/2f + 6f, paint)
        canvas.drawText("km/h", 0f, element.height/2f + 24f, paint)
    }
    
    private fun drawElement3DRock(canvas: Canvas, element: TrackElement) {
        // Rocher avec volume
        paint.color = Color.parseColor("#888888")
        canvas.drawCircle(0f, 0f, element.height/2f, paint)
        
        // Côté éclairé
        paint.color = Color.parseColor("#AAAAAA")
        canvas.drawCircle(-element.height/6f, -element.height/6f, element.height/2.5f, paint)
    }
    
    private fun drawAdvancedCurveIndicators(canvas: Canvas, w: Int, h: Int) {
        for (indicator in engine.curveIndicators) {
            val alpha = (indicator.life * 255 / 4f).toInt()
            paint.alpha = alpha
            
            val arrowColor = when (indicator.type) {
                TrackCurve.Type.GENTLE -> Color.GREEN
                TrackCurve.Type.MEDIUM -> Color.YELLOW
                TrackCurve.Type.SHARP -> Color.RED
                TrackCurve.Type.HAIRPIN -> Color.MAGENTA
                TrackCurve.Type.CHICANE -> Color.CYAN
            }
            paint.color = arrowColor
            
            val centerX = w / 2f
            val arrowY = h * 0.15f
            val arrowSize = 50f * indicator.intensity * (1f + indicator.urgency)
            
            // Pulsation selon l'urgence
            val pulse = 1f + sin(engine.raceTime * 10f * indicator.urgency) * 0.2f * indicator.urgency
            val finalArrowSize = arrowSize * pulse
            
            paint.strokeWidth = 8f + indicator.urgency * 4f
            paint.style = Paint.Style.STROKE
            
            // Flèche directionnelle
            drawDirectionalArrow(canvas, centerX, arrowY, finalArrowSize, indicator.direction)
            
            paint.style = Paint.Style.FILL
            
            // Texte du type
            paint.textSize = 20f + indicator.urgency * 6f
            paint.textAlign = Paint.Align.CENTER
            val typeText = when (indicator.type) {
                TrackCurve.Type.GENTLE -> "DOUX"
                TrackCurve.Type.MEDIUM -> "MOYEN"
                TrackCurve.Type.SHARP -> "SERRÉ"
                TrackCurve.Type.HAIRPIN -> "ÉPINGLE"
                TrackCurve.Type.CHICANE -> "CHICANE"
            }
            canvas.drawText(typeText, centerX, arrowY + 50f, paint)
            
            // Distance
            paint.textSize = 14f
            paint.color = Color.WHITE
            canvas.drawText("${indicator.distance.toInt()}m", centerX, arrowY + 70f, paint)
            
            // Avertissement
            if (indicator.urgency > 0.7f) {
                paint.color = Color.RED
                paint.textSize = 16f
                canvas.drawText("ATTENTION!", centerX, arrowY + 90f, paint)
            }
        }
        paint.alpha = 255
    }
    
    private fun drawDirectionalArrow(canvas: Canvas, centerX: Float, arrowY: Float, arrowSize: Float, direction: Float) {
        if (direction < 0) {
            // Flèche gauche
            canvas.drawLine(centerX - arrowSize, arrowY, centerX - 15f, arrowY - arrowSize/2, paint)
            canvas.drawLine(centerX - arrowSize, arrowY, centerX - 15f, arrowY + arrowSize/2, paint)
            canvas.drawLine(centerX - arrowSize, arrowY, centerX - 5f, arrowY, paint)
        } else {
            // Flèche droite
            canvas.drawLine(centerX + arrowSize, arrowY, centerX + 15f, arrowY - arrowSize/2, paint)
            canvas.drawLine(centerX + arrowSize, arrowY, centerX + 15f, arrowY + arrowSize/2, paint)
            canvas.drawLine(centerX + arrowSize, arrowY, centerX + 5f, arrowY, paint)
        }
    }
    
    private fun drawParticleEffects(canvas: Canvas, w: Int, h: Int) {
        // Particules de neige 3D
        drawSnowParticles3D(canvas, w, h)
        
        // Copeaux de glace
        drawIceChips(canvas, w, h)
        
        // Étincelles de mur
        drawWallSparks(canvas, w, h)
        
        // Traînées de vent
        drawWindTrails(canvas, w, h)
        
        // Impacts au sol
        drawGroundImpacts(canvas, w, h)
        
        // Étincelles magiques
        drawSparkles(canvas, w, h)
        
        // Traînées aéro
        drawAeroTrails(canvas, w, h)
    }
    
    private fun drawSnowParticles3D(canvas: Canvas, w: Int, h: Int) {
        for (particle in engine.snowParticles3D) {
            if (particle.z > 0f) {
                val scale = 1000f / (particle.z + 1000f)
                val screenX = (particle.x - w/2f) * scale + w/2f
                val screenY = (particle.y - h/2f) * scale + h/2f
                val size = particle.size * scale
                
                if (screenX > -50f && screenX < w + 50f && screenY > -50f && screenY < h + 50f) {
                    paint.alpha = (scale * particle.life * 255 / 8f).toInt().coerceIn(0, 255)
                    paint.color = Color.WHITE
                    canvas.drawCircle(screenX, screenY, size, paint)
                }
            }
        }
        paint.alpha = 255
    }
    
    private fun drawIceChips(canvas: Canvas, w: Int, h: Int) {
        for (chip in engine.iceChips) {
            paint.alpha = (chip.life * 255 / 1.5f).toInt().coerceIn(0, 255)
            paint.color = if (chip.sparkle) Color.parseColor("#AAEEFF") else Color.parseColor("#CCDDFF")
            
            val size = chip.life * 4f + 2f
            canvas.drawCircle(chip.x, chip.y, size, paint)
            
            if (chip.sparkle && Random.nextFloat() < 0.3f) {
                paint.color = Color.WHITE
                canvas.drawCircle(chip.x, chip.y, size * 0.5f, paint)
            }
        }
        paint.alpha = 255
    }
    
    private fun drawWallSparks(canvas: Canvas, w: Int, h: Int) {
        for (spark in engine.wallSparks) {
            paint.alpha = (spark.life * spark.intensity * 255 / 1.5f).toInt().coerceIn(0, 255)
            paint.color = spark.color
            
            val size = spark.life * 8f * spark.intensity
            canvas.drawCircle(spark.x, spark.y, size, paint)
            
            // Traînée
            paint.strokeWidth = size * 0.5f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(spark.x, spark.y, spark.x - spark.vx * 0.1f, spark.y - spark.vy * 0.1f, paint)
            paint.style = Paint.Style.FILL
        }
        paint.alpha = 255
    }
    
    private fun drawWindTrails(canvas: Canvas, w: Int, h: Int) {
        for (trail in engine.windTrails) {
            if (trail.z > 0f) {
                val scale = 500f / (trail.z + 500f)
                val screenX = (trail.x - w/2f) * scale + w/2f
                val screenY = (trail.y - h/2f) * scale + h/2f
                
                if (scale > 0.1f) {
                    paint.alpha = (trail.life * trail.intensity * scale * 150).toInt().coerceIn(0, 255)
                    paint.color = Color.parseColor("#CCFFFFFF")
                    paint.strokeWidth = scale * 6f
                    paint.style = Paint.Style.STROKE
                    
                    val length = 30f * scale * trail.intensity
                    canvas.drawLine(screenX, screenY, screenX, screenY + length, paint)
                    paint.style = Paint.Style.FILL
                }
            }
        }
        paint.alpha = 255
    }
    
    private fun drawGroundImpacts(canvas: Canvas, w: Int, h: Int) {
        for (impact in engine.groundImpacts) {
            paint.alpha = (impact.life * 255 / 1.3f).toInt().coerceIn(0, 255)
            paint.color = Color.parseColor("#DDDDDD")
            
            canvas.drawCircle(impact.x, impact.y, impact.size * impact.life, paint)
            
            // Particules autour
            if (Random.nextFloat() < 0.4f) {
                val angle = Random.nextFloat() * 2f * PI.toFloat()
                val radius = impact.size * 1.5f
                val px = impact.x + cos(angle) * radius
                val py = impact.y + sin(angle) * radius
                canvas.drawCircle(px, py, 2f, paint)
            }
        }
        paint.alpha = 255
    }
    
    private fun drawSparkles(canvas: Canvas, w: Int, h: Int) {
        for (sparkle in engine.sparkles) {
            paint.alpha = (sparkle.life * 255 / 3f).toInt().coerceIn(0, 255)
            paint.color = sparkle.color
            
            val size = 3f + sin(engine.raceTime / sparkle.sparkleRate) * 2f
            canvas.drawCircle(sparkle.x, sparkle.y, size, paint)
            
            // Éclat en croix
            if (Random.nextFloat() < sparkle.sparkleRate) {
                paint.strokeWidth = 2f
                paint.style = Paint.Style.STROKE
                canvas.drawLine(sparkle.x - 8f, sparkle.y, sparkle.x + 8f, sparkle.y, paint)
                canvas.drawLine(sparkle.x, sparkle.y - 8f, sparkle.x, sparkle.y + 8f, paint)
                paint.style = Paint.Style.FILL
            }
        }
        paint.alpha = 255
    }
    
    private fun drawAeroTrails(canvas: Canvas, w: Int, h: Int) {
        for (trail in engine.aeroTrails) {
            paint.alpha = (trail.life * trail.intensity * 180).toInt().coerceIn(0, 255)
            paint.color = Color.parseColor("#88FFFFFF")
            paint.strokeWidth = 4f + trail.intensity * 3f
            paint.style = Paint.Style.STROKE
            
            canvas.drawLine(trail.x, trail.y, trail.x + trail.length * 0.8f, trail.y, paint)
            paint.style = Paint.Style.FILL
        }
        paint.alpha = 255
    }
    
    private fun drawAdvancedInterface(canvas: Canvas, w: Int, h: Int) {
        val baseY = h - 240f
        
        // Compteur de vitesse avancé
        drawAdvancedSpeedometer(canvas, w - 160f, 130f)
        
        // Métriques avancées
        drawAdvancedMetrics(canvas, baseY)
        
        // Barres de performance améliorées
        drawAdvancedPerformanceBars(canvas, 250f, baseY)
        
        // Indicateurs spéciaux
        drawSpecialIndicators(canvas, w, h, baseY)
    }
    
    private fun drawAdvancedSpeedometer(canvas: Canvas, centerX: Float, centerY: Float) {
        // Cadran avec dégradé
        val speedGradient = RadialGradient(
            centerX, centerY, 65f,
            intArrayOf(Color.parseColor("#333333"), Color.parseColor("#111111")),
            floatArrayOf(0.8f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = speedGradient
        canvas.drawCircle(centerX, centerY, 65f, paint)
        paint.shader = null
        
        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, 60f, paint)
        
        // Zones de couleur
        drawSpeedZones(canvas, centerX, centerY)
        
        // Aiguille
        val speedAngle = (engine.speed / engine.maxSpeed) * 270f - 135f
        drawAdvancedNeedle(canvas, centerX, centerY, speedAngle)
        
        // Affichage numérique
        paint.color = Color.BLACK
        canvas.drawRoundRect(centerX - 20f, centerY + 15f, centerX + 20f, centerY + 35f, 3f, 3f, paint)
        paint.color = Color.parseColor("#00FF00")
        paint.textSize = 14f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("${engine.speed.toInt()}", centerX, centerY + 30f, paint)
    }
    
    private fun drawSpeedZones(canvas: Canvas, centerX: Float, centerY: Float) {
        // Zone verte (0-70%)
        paint.color = Color.parseColor("#4000FF00")
        drawSpeedArc(canvas, centerX, centerY, -135f, 189f, 50f)
        
        // Zone jaune (70-85%)
        paint.color = Color.parseColor("#40FFFF00")
        drawSpeedArc(canvas, centerX, centerY, 54f, 40.5f, 50f)
        
        // Zone rouge (85-100%)
        paint.color = Color.parseColor("#40FF0000")
        drawSpeedArc(canvas, centerX, centerY, 94.5f, 40.5f, 50f)
    }
    
    private fun drawSpeedArc(canvas: Canvas, centerX: Float, centerY: Float, startAngle: Float, sweepAngle: Float, radius: Float) {
        val rect = RectF(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        canvas.drawArc(rect, startAngle, sweepAngle, true, paint)
    }
    
    private fun drawAdvancedNeedle(canvas: Canvas, centerX: Float, centerY: Float, angle: Float) {
        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.rotate(angle)
        
        val needleColor = when {
            engine.speed > 120f -> Color.RED
            engine.speed > 100f -> Color.parseColor("#FF6600")
            else -> Color.parseColor("#333333")
        }
        
        paint.color = needleColor
        paint.strokeWidth = 5f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(0f, 0f, 0f, -40f, paint)
        
        paint.style = Paint.Style.FILL
        canvas.restore()
        
        // Centre
        paint.color = Color.parseColor("#444444")
        canvas.drawCircle(centerX, centerY, 6f, paint)
    }
    
    private fun drawAdvancedMetrics(canvas: Canvas, baseY: Float) {
        paint.color = Color.parseColor("#001133")
        paint.textSize = 18f
        paint.textAlign = Paint.Align.LEFT
        
        val metrics = arrayOf(
            "Perfect: ${engine.perfectCurves}",
            "Murs: ${engine.wallHits}",
            "Temps: ${engine.raceTime.toInt()}s",
            "Alt: ${engine.altitude.toInt()}m",
            "Secteur: ${engine.currentSector + 1}/7"
        )
        
        for ((index, metric) in metrics.withIndex()) {
            canvas.drawText(metric, 20f, baseY + index * 30f, paint)
        }
    }
    
    private fun drawAdvancedPerformanceBars(canvas: Canvas, x: Float, baseY: Float) {
        val barWidth = 200f
        
        drawAdvancedMeter(canvas, x, baseY, barWidth, engine.precision / 140f, "PRÉCISION", Color.parseColor("#00AA00"))
        drawAdvancedMeter(canvas, x, baseY + 30f, barWidth, engine.aerodynamics / 140f, "AÉRO", Color.parseColor("#0066CC"))
        drawAdvancedMeter(canvas, x, baseY + 60f, barWidth, engine.momentum, "ÉLAN", Color.parseColor("#FF6600"))
        drawAdvancedMeter(canvas, x, baseY + 90f, barWidth, engine.stamina / 100f, "STAMINA", Color.parseColor("#8B008B"))
    }
    
    private fun drawAdvancedMeter(canvas: Canvas, x: Float, y: Float, width: Float, 
                                 value: Float, label: String, color: Int) {
        val barHeight = 20f
        val clampedValue = value.coerceIn(0f, 1f)
        
        // Fond avec dégradé
        val bgGradient = LinearGradient(
            x, y, x, y + barHeight,
            intArrayOf(Color.parseColor("#444444"), Color.parseColor("#222222")),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = bgGradient
        canvas.drawRoundRect(x, y, x + width, y + barHeight, 4f, 4f, paint)
        paint.shader = null
        
        // Barre de progression
        if (clampedValue > 0f) {
            val filledWidth = clampedValue * width
            paint.color = color
            canvas.drawRoundRect(x, y, x + filledWidth, y + barHeight, 4f, 4f, paint)
        }
        
        // Bordure
        paint.color = Color.parseColor("#666666")
        paint.strokeWidth = 1f
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(x, y, x + width, y + barHeight, 4f, 4f, paint)
        paint.style = Paint.Style.FILL
        
        // Label
        paint.color = Color.WHITE
        paint.textSize = 12f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("$label: ${(clampedValue * 100).toInt()}%", x, y - 5f, paint)
    }
    
    private fun drawSpecialIndicators(canvas: Canvas, w: Int, h: Int, baseY: Float) {
        // G-Force
        if (engine.gForce > 0.15f) {
            val gIntensity = engine.gForce.coerceIn(0f, 1f)
            paint.color = Color.argb((gIntensity * 255).toInt(), 255, (255 * (1f - gIntensity)).toInt(), 0)
            paint.textSize = 20f + gIntensity * 8f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("G-FORCE: ${(engine.gForce * 100).toInt()}%", w/2f, baseY + 140f, paint)
        }
        
        // Rythme cardiaque avec pulsation
        val heartPulse = sin(engine.raceTime * (engine.heartRate / 60f) * 6f) * 0.3f + 1f
        paint.color = Color.RED
        paint.textSize = 16f * heartPulse
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("❤️ ${engine.heartRate.toInt()}", w - 20f, baseY + 30f, paint)
        
        // Adrénaline
        if (engine.adrenaline > 0.3f) {
            paint.color = Color.parseColor("#FF4444")
            paint.textSize = 14f
            canvas.drawText("⚡ ${(engine.adrenaline * 100).toInt()}%", w - 180f, baseY + 60f, paint)
        }
        
        // Freinage
        if (engine.brakingPower > 0f) {
            paint.color = Color.parseColor("#FF6644")
            paint.textSize = 16f
            canvas.drawText("🦶 ${engine.brakingPower.toInt()}%", w - 180f, baseY + 90f, paint)
        }
    }
    
    private fun drawSpeedSpecialEffects(canvas: Canvas, w: Int, h: Int) {
        // Effets selon la vitesse
        when {
            engine.speed > 130f -> drawExtremeSpeedEffects(canvas, w, h)
            engine.speed > 100f -> drawHighSpeedEffects(canvas, w, h)
            engine.speed > 70f -> drawMediumSpeedEffects(canvas, w, h)
        }
        
        // Aura d'adrénaline
        if (engine.adrenaline > 0.5f) {
            drawAdrenalineAura(canvas, w, h)
        }
    }
    
    private fun drawExtremeSpeedEffects(canvas: Canvas, w: Int, h: Int) {
        val pulse = sin(engine.raceTime * 15f) * 0.5f + 0.5f
        paint.alpha = (pulse * 200).toInt()
        paint.color = Color.RED
        paint.textSize = 28f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("⚠️ VITESSE EXTRÊME ⚠️", w/2f, h - 30f, paint)
        paint.alpha = 255
        
        // Distorsion des bords
        paint.color = Color.parseColor("#44FF0000")
        canvas.drawRect(0f, 0f, 20f, h.toFloat(), paint)
        canvas.drawRect(w - 20f, 0f, w.toFloat(), h.toFloat(), paint)
    }
    
    private fun drawHighSpeedEffects(canvas: Canvas, w: Int, h: Int) {
        paint.color = Color.parseColor("#FFAA00")
        paint.textSize = 24f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("🔥 HAUTE VITESSE 🔥", w/2f, h - 30f, paint)
    }
    
    private fun drawMediumSpeedEffects(canvas: Canvas, w: Int, h: Int) {
        paint.color = Color.parseColor("#00AAFF")
        paint.textSize = 20f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("💨 VITESSE ÉLEVÉE", w/2f, h - 30f, paint)
    }
    
    private fun drawAdrenalineAura(canvas: Canvas, w: Int, h: Int) {
        val lugerX = engine.lugerX * w
        val lugerY = h * 0.75f
        val pulse = sin(engine.raceTime * 8f) * 0.3f + 0.7f
        val auraSize = 60f * engine.adrenaline * pulse
        
        paint.alpha = (engine.adrenaline * 80).toInt()
        
        val auraGradient = RadialGradient(
            lugerX, lugerY, auraSize,
            intArrayOf(Color.parseColor("#FFFF0000"), Color.TRANSPARENT),
            floatArrayOf(0.3f, 1f),
            Shader.TileMode.CLAMP
        )
        
        paint.shader = auraGradient
        canvas.drawCircle(lugerX, lugerY, auraSize, paint)
        paint.shader = null
        paint.alpha = 255
    }
    
    private fun drawTriangle(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        val path = Path()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        path.close()
        canvas.drawPath(path, paint)
    }
}
