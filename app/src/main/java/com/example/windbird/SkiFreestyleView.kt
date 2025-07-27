package com.example.windbird

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

class SkiFreestyleView(context: Context, private val activity: SkiFreestyleActivity) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val reusableRectF = RectF()
    private val reusablePath = Path()

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height
        
        // Appliquer shake de caméra
        canvas.save()
        if (activity.cameraShake > 0f) {
            canvas.translate(
                (kotlin.random.Random.nextFloat() - 0.5f) * activity.cameraShake * 15f,
                (kotlin.random.Random.nextFloat() - 0.5f) * activity.cameraShake * 15f
            )
        }
        
        when (activity.gameState) {
            SkiFreestyleActivity.GameState.PREPARATION -> drawPreparation(canvas, w, h)
            SkiFreestyleActivity.GameState.SKIING -> drawSkiing(canvas, w, h)
            SkiFreestyleActivity.GameState.RESULTS -> drawResults(canvas, w, h)
            SkiFreestyleActivity.GameState.FINISHED -> drawResults(canvas, w, h)
        }
        
        drawEffects(canvas, w, h)
        canvas.restore()
    }
    
    private fun drawPreparation(canvas: Canvas, w: Int, h: Int) {
        // Fond de montagne
        val skyGradient = LinearGradient(0f, 0f, 0f, h.toFloat(),
            Color.parseColor("#87CEEB"), Color.parseColor("#E0F6FF"), Shader.TileMode.CLAMP)
        paint.shader = skyGradient
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        
        // IMAGE DE PRÉPARATION CENTRÉE (pas étirée)
        if (activity.preparationBitmap != null) {
            val bitmap = activity.preparationBitmap!!
            val bitmapWidth = bitmap.width.toFloat()
            val bitmapHeight = bitmap.height.toFloat()
            
            // Calculer la taille pour garder les proportions
            val maxSize = min(w * 0.6f, h * 0.6f)
            val scale = min(maxSize / bitmapWidth, maxSize / bitmapHeight)
            val scaledWidth = bitmapWidth * scale
            val scaledHeight = bitmapHeight * scale
            
            // Centrer l'image
            val centerX = w / 2f
            val centerY = h / 2f
            val left = centerX - scaledWidth / 2f
            val top = centerY - scaledHeight / 2f
            
            val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
            val dstRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
            canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
        }
        
        // INSTRUCTIONS SUR LES CÔTÉS - CLAIRES ET VISIBLES
        paint.color = Color.parseColor("#001122")
        paint.textSize = 36f // Gros et lisible
        paint.textAlign = Paint.Align.LEFT
        
        // CÔTÉ GAUCHE
        canvas.drawText("🎿 COMMENT JOUER:", 30f, h * 0.15f, paint)
        paint.textSize = 28f
        canvas.drawText("• Passe sur les kickers", 30f, h * 0.22f, paint)
        canvas.drawText("  ROUGES pour sauter", 30f, h * 0.27f, paint)
        canvas.drawText("• Incline AVANT pour", 30f, h * 0.35f, paint)
        canvas.drawText("  pumper et aller vite", 30f, h * 0.4f, paint)
        canvas.drawText("• Bouge en l'air pour", 30f, h * 0.48f, paint)
        canvas.drawText("  faire des tricks!", 30f, h * 0.53f, paint)
        
        // CÔTÉ DROIT
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 36f
        canvas.drawText("🏆 TRICKS:", w - 30f, h * 0.15f, paint)
        paint.textSize = 28f
        canvas.drawText("Rotation Z = SPIN 360°", w - 30f, h * 0.22f, paint)
        canvas.drawText("Rotation Y = BACKFLIP", w - 30f, h * 0.27f, paint)
        canvas.drawText("Secouer = INDY GRAB", w - 30f, h * 0.32f, paint)
        canvas.drawText("Combo = SPIN GRAB", w - 30f, h * 0.37f, paint)
        
        paint.textSize = 32f
        paint.color = Color.parseColor("#FF6600")
        canvas.drawText("Plus tu tiens le grab", w - 30f, h * 0.45f, paint)
        canvas.drawText("longtemps = plus de pts!", w - 30f, h * 0.5f, paint)
        
        // COUNTDOWN AU CENTRE EN BAS
        paint.textSize = 120f
        paint.color = Color.RED
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("3", w/2f, h * 0.9f, paint)
        
        paint.textSize = 40f
        paint.color = Color.parseColor("#001122")
        canvas.drawText("PRÊT?", w/2f, h * 0.95f, paint)
    }
    
    private fun drawPisteOverview(canvas: Canvas, w: Int, h: Int) {
        // Piste vue de dessus avec perspective
        paint.color = Color.WHITE
        
        reusablePath.reset()
        reusablePath.moveTo(w * 0.3f, h * 0.3f)
        reusablePath.lineTo(w * 0.7f, h * 0.3f)
        reusablePath.lineTo(w * 0.6f, h * 0.7f)
        reusablePath.lineTo(w * 0.4f, h * 0.7f)
        reusablePath.close()
        canvas.drawPath(reusablePath, paint)
        
        // Kickers prévisualisés
        for (i in 0..2) {
            val y = h * (0.35f + i * 0.1f)
            val kickerWidth = 40f - i * 8f
            paint.color = Color.parseColor("#DDDDDD")
            reusableRectF.set(w/2f - kickerWidth/2f, y, w/2f + kickerWidth/2f, y + kickerWidth/3f)
            canvas.drawRoundRect(reusableRectF, 5f, 5f, paint)
        }
    }
    
    private fun drawSkiing(canvas: Canvas, w: Int, h: Int) {
        // Vue depuis derrière le skieur en perspective
        drawPisteFromBehind(canvas, w, h)
        
        // Kickers sur la piste
        drawKickers(canvas, w, h)
        
        // Skieur vu de dos
        drawSkierFromBehind(canvas, w, h)
        
        // Interface de jeu
        drawGameInterface(canvas, w, h)
        
        // Barre de pump rhythm
        drawPumpBar(canvas, w, h)
        
        // Trajectoire si en l'air
        if (activity.isInAir) {
            drawTrajectory(canvas, w, h)
        }
    }
    
    private fun drawPisteFromBehind(canvas: Canvas, w: Int, h: Int) {
        // Fond ciel
        val skyGradient = LinearGradient(0f, 0f, 0f, h * 0.4f,
            Color.parseColor("#87CEEB"), Color.parseColor("#E0F6FF"), Shader.TileMode.CLAMP)
        paint.shader = skyGradient
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
        paint.shader = null
        
        // PISTE AVEC COURBURE NATURELLE SUBTILE
        val scrollOffset = activity.pisteScroll % 100f
        
        paint.color = Color.WHITE
        
        // Piste qui se courbe DOUCEMENT vers l'horizon (comme vraie colline)
        reusablePath.reset()
        reusablePath.moveTo(w * 0.45f, 0f)           // Haut étroit (horizon)
        reusablePath.lineTo(w * 0.55f, 0f)           
        reusablePath.lineTo(w * 0.85f, h.toFloat())   // Bas large (proche)
        reusablePath.lineTo(w * 0.15f, h.toFloat())   
        reusablePath.close()
        canvas.drawPath(reusablePath, paint)
        
        // Lignes qui suivent une LÉGÈRE courbure naturelle
        paint.color = Color.parseColor("#EEEEEE")
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        
        for (i in 0..15) {
            val lineY = i * 60f + scrollOffset
            if (lineY >= 0f && lineY <= h.toFloat()) {
                val perspective = (h.toFloat() - lineY) / h.toFloat()
                val leftX = w * (0.15f + perspective * 0.3f)
                val rightX = w * (0.85f - perspective * 0.3f)
                
                // COURBURE TRÈS SUBTILE (comme vraie pente de colline)
                val curveIntensity = 8f * (1f - perspective) // Très léger
                val centerX = (leftX + rightX) / 2f
                val curvedY = lineY - curveIntensity * sin(perspective * PI / 2).toFloat()
                
                // Dessiner ligne légèrement courbée
                reusablePath.reset()
                reusablePath.moveTo(leftX, lineY)
                reusablePath.quadTo(centerX, curvedY, rightX, lineY)
                canvas.drawPath(reusablePath, paint)
            }
        }
        
        paint.style = Paint.Style.FILL
    }
    
    private fun drawKickers(canvas: Canvas, w: Int, h: Int) {
        for (kicker in activity.kickers) {
            val kickerScreenDistance = kicker.distance - activity.distanceTraveled
            
            if (kickerScreenDistance > -30f && kickerScreenDistance < 500f) {
                // Position avec légère courbure naturelle
                val progress = 1f - (kickerScreenDistance / 500f).coerceIn(0f, 1f)
                val baseY = h.toFloat() - progress * h
                val perspective = progress
                
                // COURBURE TRÈS SUBTILE pour les kickers aussi
                val curveOffset = 6f * (1f - progress) * sin(progress * PI / 2).toFloat()
                val screenY = baseY - curveOffset
                
                if (screenY >= 0f && screenY < h.toFloat()) {
                    val kickerSize = when (kicker.size) {
                        SkiFreestyleActivity.KickerSize.SMALL -> 40f
                        SkiFreestyleActivity.KickerSize.MEDIUM -> 70f
                        SkiFreestyleActivity.KickerSize.LARGE -> 100f
                    } * perspective.coerceIn(0.3f, 1.5f)
                    
                    // Position sur la piste avec perspective normale
                    val pisteLeft = w * (0.15f + perspective * 0.3f)
                    val pisteRight = w * (0.85f - perspective * 0.3f)
                    val pisteWidth = pisteRight - pisteLeft
                    val screenX = pisteLeft + kicker.x * pisteWidth
                    
                    // Couleurs progressives
                    paint.color = if (kicker.hit) {
                        Color.parseColor("#00FF00")
                    } else if (kickerScreenDistance < 50f && kickerScreenDistance > 0f) {
                        Color.parseColor("#FF0000")
                    } else if (kickerScreenDistance < 120f && kickerScreenDistance > 0f) {
                        Color.parseColor("#FFFF00")
                    } else {
                        Color.parseColor("#FFFFFF")
                    }
                    
                    // Forme normale du kicker (pas compliqué)
                    reusablePath.reset()
                    reusablePath.moveTo(screenX - kickerSize, screenY + kickerSize/2f)
                    reusablePath.lineTo(screenX - kickerSize/2f, screenY)
                    reusablePath.lineTo(screenX, screenY - kickerSize/3f)
                    reusablePath.lineTo(screenX + kickerSize/2f, screenY)
                    reusablePath.lineTo(screenX + kickerSize, screenY + kickerSize/2f)
                    reusablePath.close()
                    canvas.drawPath(reusablePath, paint)
                    
                    // Contour simple
                    paint.color = Color.BLACK
                    paint.strokeWidth = 3f
                    paint.style = Paint.Style.STROKE
                    canvas.drawPath(reusablePath, paint)
                    paint.style = Paint.Style.FILL
                    
                    // Indicateurs simples
                    if (perspective > 0.2f) {
                        paint.color = Color.BLACK
                        paint.textSize = 20f * perspective
                        paint.textAlign = Paint.Align.CENTER
                        val sizeText = when (kicker.size) {
                            SkiFreestyleActivity.KickerSize.SMALL -> "SMALL"
                            SkiFreestyleActivity.KickerSize.MEDIUM -> "MEDIUM" 
                            SkiFreestyleActivity.KickerSize.LARGE -> "BIG"
                        }
                        canvas.drawText(sizeText, screenX, screenY + kickerSize/4f, paint)
                        
                        if (kickerScreenDistance > 0f && kickerScreenDistance < 150f) {
                            paint.textSize = 18f * perspective
                            paint.color = when {
                                kickerScreenDistance < 30f -> Color.RED
                                kickerScreenDistance < 80f -> Color.parseColor("#FF6600") 
                                else -> Color.BLACK
                            }
                            canvas.drawText("${kickerScreenDistance.toInt()}m", screenX, screenY - kickerSize/2f, paint)
                        }
                    }
                }
            }
        }
    }
    
    private fun drawSkierFromBehind(canvas: Canvas, w: Int, h: Int) {
        val skierScreenX = w * (0.15f + activity.skierX * 0.7f) // Position normale
        var skierScreenY = h * 0.7f // Position normale
        
        // Si en l'air, mouvement vertical
        if (activity.isInAir) {
            activity.updateAirPhysics()
            val airOffset = (activity.skierY - 0.7f) * h * -2f
            skierScreenY += airOffset
            
            // Effets visuels en l'air
            val airTimeIndicator = (activity.airTime * 100f).coerceAtMost(40f)
            
            paint.color = Color.parseColor("#66FFFF00")
            canvas.drawCircle(skierScreenX, skierScreenY, 50f + airTimeIndicator, paint)
            
            paint.color = Color.RED
            paint.textSize = 36f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("✈️ EN L'AIR!", skierScreenX, skierScreenY - 120f, paint)
            canvas.drawText("${(activity.airTime * 100).toInt()}%", skierScreenX, skierScreenY - 85f, paint)
        }
        
        canvas.save()
        canvas.translate(skierScreenX, skierScreenY)
        
        // Rotations pour les tricks
        var totalRotation = 0f
        when (activity.currentTrick) {
            SkiFreestyleActivity.FreestyleTrick.SPIN_360 -> {
                totalRotation = activity.trickRotation * 1.2f
                paint.color = Color.parseColor("#66FF0000")
                canvas.drawCircle(0f, 0f, 60f, paint)
            }
            SkiFreestyleActivity.FreestyleTrick.BACKFLIP -> {
                totalRotation = activity.trickRotation * 0.8f
                paint.color = Color.parseColor("#660000FF")
                canvas.drawCircle(0f, 0f, 60f, paint)
            }
            SkiFreestyleActivity.FreestyleTrick.SPIN_GRAB -> {
                totalRotation = activity.trickRotation * 1f
                canvas.scale(1f + activity.trickProgress * 0.2f, 1f + activity.trickProgress * 0.2f)
                paint.color = Color.parseColor("#66FFFF00")
                canvas.drawCircle(0f, 0f, 60f, paint)
            }
            SkiFreestyleActivity.FreestyleTrick.INDY_GRAB -> {
                canvas.scale(1f + activity.trickProgress * 0.15f, 1f + activity.trickProgress * 0.15f)
                paint.color = Color.parseColor("#6600FFFF")
                canvas.drawCircle(0f, 0f, 55f, paint)
            }
            else -> {}
        }
        
        if (totalRotation != 0f) {
            canvas.rotate(totalRotation)
        }
        
        // IMAGE DU SKIEUR - MOITIÉ PLUS PETITE (250px au lieu de 500px)
        if (activity.skierBitmap != null) {
            val bitmapSize = if (activity.isInAir) 300f else 250f // MOITIÉ plus petit
            val srcRect = Rect(0, 0, activity.skierBitmap!!.width, activity.skierBitmap!!.height)
            val dstRect = RectF(-bitmapSize/2f, -bitmapSize/2f, bitmapSize/2f, bitmapSize/2f)
            
            // PAS DE TRANSPARENCE - image normale
            paint.alpha = 255
            canvas.drawBitmap(activity.skierBitmap!!, srcRect, dstRect, paint)
        } else {
            // Fallback plus petit aussi
            paint.color = Color.parseColor("#FF6600")
            paint.alpha = 255 // PAS TRANSPARENT
            canvas.drawRect(-40f, -75f, 40f, 50f, paint)
            paint.color = Color.parseColor("#FFFFFF")
            canvas.drawCircle(0f, -90f, 30f, paint)
        }
        
        canvas.restore()
        
        // Ombre normale
        if (!activity.isInAir) {
            paint.color = Color.parseColor("#55000000")
            canvas.drawOval(skierScreenX - 40f, h * 0.85f, skierScreenX + 40f, h * 0.88f, paint)
        } else {
            val projectedLanding = skierScreenX + activity.horizontalVelocity * 100f
            paint.color = Color.parseColor("#33FF0000")
            canvas.drawOval(projectedLanding - 30f, h * 0.85f, projectedLanding + 30f, h * 0.88f, paint)
            
            paint.color = Color.parseColor("#44FFFFFF")
            paint.strokeWidth = 3f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(skierScreenX, skierScreenY, projectedLanding, h * 0.86f, paint)
            paint.style = Paint.Style.FILL
        }
        
        // Instructions
        paint.color = Color.parseColor("#FFFF00")
        paint.textSize = 28f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("🎿 PASSE SUR LES KICKERS ROUGES POUR SAUTER!", w/2f, h - 50f, paint)
        canvas.drawText("📱 Incline en l'air pour faire des tricks!", w/2f, h - 20f, paint)
    }
    
    private fun drawGameInterface(canvas: Canvas, w: Int, h: Int) {
        val baseY = h - 160f
        
        // Scores et stats
        paint.color = Color.parseColor("#001122")
        paint.textSize = 22f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Score: ${activity.totalScore.toInt()}", 20f, baseY, paint)
        canvas.drawText("Speed: ${activity.speed.toInt()} km/h", 20f, baseY + 25f, paint)
        canvas.drawText("Kickers: ${activity.kickersHit}/6", 20f, baseY + 50f, paint)
        
        // Trick en cours avec progression plus claire
        if (activity.currentTrick != SkiFreestyleActivity.FreestyleTrick.NONE) {
            paint.color = Color.parseColor("#FF6600")
            paint.textSize = 28f
            paint.textAlign = Paint.Align.CENTER
            
            val trickText = when (activity.currentTrick) {
                SkiFreestyleActivity.FreestyleTrick.SPIN_360 -> "${abs(activity.trickRotation).toInt()}° SPIN"
                SkiFreestyleActivity.FreestyleTrick.BACKFLIP -> "BACKFLIP ${(activity.trickProgress * 100).toInt()}%"
                SkiFreestyleActivity.FreestyleTrick.INDY_GRAB -> "INDY ${(activity.trickProgress * 100).toInt()}%"
                SkiFreestyleActivity.FreestyleTrick.SPIN_GRAB -> "${abs(activity.trickRotation).toInt()}° GRAB"
                else -> activity.currentTrick.displayName
            }
            
            canvas.drawText(trickText, w/2f, baseY, paint)
            
            // Barre de progression du trick
            val progressBarY = baseY + 30f
            paint.color = Color.parseColor("#333333")
            reusableRectF.set(w/2f - 100f, progressBarY, w/2f + 100f, progressBarY + 10f)
            canvas.drawRect(reusableRectF, paint)
            
            paint.color = Color.parseColor("#00FF00")
            val progressWidth = (activity.trickProgress.coerceIn(0f, 1f)) * 200f
            reusableRectF.set(w/2f - 100f, progressBarY, w/2f - 100f + progressWidth, progressBarY + 10f)
            canvas.drawRect(reusableRectF, paint)
        }
        
        // Métriques de performance
        drawPerformanceMeter(canvas, w - 200f, baseY - 30f, 180f, activity.amplitude / 0.4f, "AMPLITUDE", Color.parseColor("#FF4444"))
        drawPerformanceMeter(canvas, w - 200f, baseY - 5f, 180f, activity.execution / 120f, "EXECUTION", Color.parseColor("#44FF44"))
        drawPerformanceMeter(canvas, w - 200f, baseY + 20f, 180f, (activity.variety / 90f).coerceAtMost(1f), "VARIETY", Color.parseColor("#4444FF"))
    }
    
    private fun drawPumpBar(canvas: Canvas, w: Int, h: Int) {
        val barX = 50f
        val barY = 100f
        val barWidth = w * 0.4f
        val barHeight = 25f
        
        // Fond
        paint.color = Color.parseColor("#333333")
        reusableRectF.set(barX, barY, barX + barWidth, barY + barHeight)
        canvas.drawRect(reusableRectF, paint)
        
        // Zone de pump optimal
        if (activity.pumpWindow) {
            paint.color = Color.parseColor("#00FF00")
            val optimalStart = barWidth * 0.3f
            val optimalWidth = barWidth * 0.4f
            reusableRectF.set(barX + optimalStart, barY, barX + optimalStart + optimalWidth, barY + barHeight)
            canvas.drawRect(reusableRectF, paint)
        }
        
        // Indicateur de vitesse actuelle
        val speedRatio = (activity.speed / 35f).coerceIn(0f, 1f)
        val indicatorX = barX + speedRatio * barWidth
        paint.color = Color.parseColor("#FFFF00")
        paint.strokeWidth = 4f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(indicatorX, barY, indicatorX, barY + barHeight, paint)
        paint.style = Paint.Style.FILL
        
        // Effet de pump
        if (activity.pumpEnergy > 0f) {
            paint.color = Color.parseColor("#FF6600")
            paint.alpha = (activity.pumpEnergy * 180).toInt()
            val pumpWidth = barWidth * activity.pumpTiming
            reusableRectF.set(barX, barY, barX + pumpWidth, barY + barHeight)
            canvas.drawRect(reusableRectF, paint)
            paint.alpha = 255
        }
        
        // Label
        paint.color = Color.WHITE
        paint.textSize = 14f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("PUMP RHYTHM - VITESSE", barX, barY - 5f, paint)
        
        if (activity.pumpCombo > 0) {
            paint.color = Color.parseColor("#00FF00")
            canvas.drawText("Perfect Pumps x${activity.pumpCombo}", barX, barY + barHeight + 18f, paint)
        }
    }
    
    private fun drawTrajectory(canvas: Canvas, w: Int, h: Int) {
        // Arc de trajectoire prévisionnelle
        val startX = w * (0.15f + activity.skierX * 0.7f)
        val startY = h * activity.skierY
        
        // Calcul de la trajectoire restante
        val remainingTime = (-activity.verticalVelocity / 0.4f).coerceAtLeast(0f)
        val landingX = startX + activity.horizontalVelocity * remainingTime * 60f
        val peakY = startY + activity.verticalVelocity * remainingTime * 30f - 0.5f * 0.4f * remainingTime * remainingTime * 900f
        
        // Dessiner l'arc
        paint.color = Color.parseColor("#AAFFFFFF")
        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE
        
        reusablePath.reset()
        reusablePath.moveTo(startX, startY)
        reusablePath.quadTo(
            (startX + landingX) / 2f, 
            peakY,
            landingX, 
            h * 0.9f
        )
        canvas.drawPath(reusablePath, paint)
        
        paint.style = Paint.Style.FILL
        
        // Point d'atterrissage prévu
        paint.color = Color.parseColor("#FFFF00")
        canvas.drawCircle(landingX, h * 0.9f, 8f, paint)
    }
    
    private fun drawPerformanceMeter(canvas: Canvas, x: Float, y: Float, width: Float, 
                                   value: Float, label: String, color: Int) {
        // Fond
        paint.color = Color.parseColor("#333333")
        reusableRectF.set(x, y, x + width, y + 18f)
        canvas.drawRect(reusableRectF, paint)
        
        // Barre
        paint.color = color
        val filledWidth = value.coerceIn(0f, 1f) * width
        reusableRectF.set(x, y, x + filledWidth, y + 18f)
        canvas.drawRect(reusableRectF, paint)
        
        // Label
        paint.color = Color.WHITE
        paint.textSize = 12f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("$label: ${(value * 100).toInt()}%", x, y - 3f, paint)
    }
    
    private fun drawResults(canvas: Canvas, w: Int, h: Int) {
        // Fond avec dégradé
        val resultGradient = LinearGradient(0f, 0f, 0f, h.toFloat(),
            Color.parseColor("#FFD700"), Color.parseColor("#FFF8DC"), Shader.TileMode.CLAMP)
        paint.shader = resultGradient
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        
        // Score final ÉNORME
        paint.color = Color.parseColor("#001122")
        paint.textSize = 150f // 4x plus gros (était ~36f)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("${activity.finalScore}", w/2f, h * 0.2f, paint)
        
        paint.textSize = 64f // 4x plus gros
        canvas.drawText("POINTS", w/2f, h * 0.28f, paint)
        
        // Breakdown détaillé BEAUCOUP PLUS GROS
        paint.color = Color.parseColor("#001122")
        paint.textSize = 44f // 4x plus gros (était ~11f)
        
        val startY = h * 0.4f
        val lineHeight = 50f // Plus d'espace entre les lignes
        
        canvas.drawText("🎿 Kickers touchés: ${activity.kickersHit}/6", w/2f, startY, paint)
        canvas.drawText("🎪 Tricks réussis: ${activity.tricksCompleted}", w/2f, startY + lineHeight, paint)
        canvas.drawText("📏 Amplitude max: ${(activity.amplitude * 250).toInt()}cm", w/2f, startY + lineHeight * 2, paint)
        canvas.drawText("🎯 Atterrissages parfaits: ${activity.perfectLandings}", w/2f, startY + lineHeight * 3, paint)
        canvas.drawText("🌈 Variété: ${activity.tricksUsed.size} tricks différents", w/2f, startY + lineHeight * 4, paint)
        canvas.drawText("⚡ Vitesse max: ${activity.speed.toInt()} km/h", w/2f, startY + lineHeight * 5, paint)
        
        // Message selon performance PLUS GROS
        val message = when {
            activity.finalScore >= 300 -> "🏆 RUN LÉGENDAIRE!"
            activity.finalScore >= 250 -> "🥇 EXCELLENT STYLE!"
            activity.finalScore >= 200 -> "🥈 TRÈS BON RUN!"
            activity.finalScore >= 150 -> "🥉 BIEN JOUÉ!"
            else -> "💪 CONTINUE À PROGRESSER!"
        }
        
        paint.color = Color.parseColor("#FF6600")
        paint.textSize = 56f // 4x plus gros
        canvas.drawText(message, w/2f, h * 0.9f, paint)
    }
    
    private fun drawEffects(canvas: Canvas, w: Int, h: Int) {
        // Particules de neige
        for (particle in activity.snowSpray) {
            paint.alpha = (particle.life * 255).toInt()
            paint.color = particle.color
            canvas.drawCircle(particle.x, particle.y, particle.life * 6f, paint)
        }
        paint.alpha = 255
        
        // Lignes de vitesse verticales
        paint.color = Color.parseColor("#66FFFFFF")
        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE
        for (line in activity.speedLines) {
            canvas.drawLine(line.x, line.y, line.x, line.y + 25f, paint) // Lignes verticales
        }
        paint.style = Paint.Style.FILL
    }
}
