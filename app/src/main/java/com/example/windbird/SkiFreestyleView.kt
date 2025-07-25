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
        
        // Vue de dessus de la piste en perspective
        drawPisteOverview(canvas, w, h)
        
        // Drapeau du pays
        val playerCountry = if (activity.practiceMode) "CANADA" else activity.getTournamentData().playerCountries[activity.getCurrentPlayerIndex()]
        val flagText = activity.getCountryFlag(playerCountry)
        
        paint.color = Color.WHITE
        reusableRectF.set(50f, 50f, 250f, 170f)
        canvas.drawRoundRect(reusableRectF, 15f, 15f, paint)
        
        paint.color = Color.parseColor("#001122")
        paint.textSize = 60f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(flagText, 150f, 130f, paint)
        
        paint.textSize = 20f
        canvas.drawText(playerCountry.uppercase(), 150f, 160f, paint)
        
        // Titre
        paint.textSize = 48f
        canvas.drawText("🎿 SKI FREESTYLE 🎿", w/2f, h * 0.15f, paint)
        
        // Countdown - on prend une valeur fixe pour l'affichage
        paint.textSize = 80f
        paint.color = Color.RED
        canvas.drawText("3", w/2f, h * 0.7f, paint)
        
        // Instructions
        paint.textSize = 24f
        paint.color = Color.parseColor("#333333")
        canvas.drawText("📱 Inclinez vers l'avant pour pumper", w/2f, h * 0.85f, paint)
        canvas.drawText("📱 Mouvements en l'air = tricks", w/2f, h * 0.9f, paint)
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
        
        // Piste qui défile de BAS vers HAUT avec perspective (CORRIGÉ)
        val scrollOffset = activity.pisteScroll % 100f
        
        paint.color = Color.WHITE
        
        // Piste en perspective qui se rétrécit vers le haut
        reusablePath.reset()
        reusablePath.moveTo(w * 0.45f, 0f)           // Haut étroit (loin)
        reusablePath.lineTo(w * 0.55f, 0f)           
        reusablePath.lineTo(w * 0.85f, h.toFloat())   // Bas large (proche)
        reusablePath.lineTo(w * 0.15f, h.toFloat())   
        reusablePath.close()
        canvas.drawPath(reusablePath, paint)
        
        // Lignes de défilement qui MONTENT pour effet de mouvement vers l'avant
        paint.color = Color.parseColor("#EEEEEE")
        paint.strokeWidth = 2f
        paint.style = Paint.Style.STROKE
        
        for (i in 0..15) {
            val lineY = i * 60f + scrollOffset // Les lignes montent maintenant
            if (lineY >= 0f && lineY <= h.toFloat()) {
                val perspective = (h.toFloat() - lineY) / h.toFloat()
                val leftX = w * (0.15f + perspective * 0.3f)
                val rightX = w * (0.85f - perspective * 0.3f)
                canvas.drawLine(leftX, lineY, rightX, lineY, paint)
            }
        }
        
        paint.style = Paint.Style.FILL
    }
    
    private fun drawKickers(canvas: Canvas, w: Int, h: Int) {
        for (kicker in activity.kickers) {
            val kickerScreenDistance = kicker.distance - activity.distanceTraveled
            
            if (kickerScreenDistance > -30f && kickerScreenDistance < 500f) {
                // Position sur l'écran - les kickers apparaissent du haut et descendent
                val screenY = h.toFloat() - (kickerScreenDistance / 500f) * h // Plus proche = plus bas
                val perspective = (h.toFloat() - screenY) / h.toFloat()
                
                if (screenY >= 0f && screenY < h.toFloat()) {
                    val kickerSize = when (kicker.size) {
                        SkiFreestyleActivity.KickerSize.SMALL -> 20f
                        SkiFreestyleActivity.KickerSize.MEDIUM -> 35f
                        SkiFreestyleActivity.KickerSize.LARGE -> 50f
                    } * perspective.coerceIn(0.2f, 1f)
                    
                    // Position sur la piste en perspective
                    val pisteLeft = w * (0.15f + perspective * 0.3f)
                    val pisteRight = w * (0.85f - perspective * 0.3f)
                    val pisteWidth = pisteRight - pisteLeft
                    val screenX = pisteLeft + kicker.x * pisteWidth
                    
                    // Couleur selon statut et proximité
                    paint.color = if (kicker.hit) {
                        Color.parseColor("#44AA44")
                    } else if (kickerScreenDistance < 80f && kickerScreenDistance > 0f) {
                        Color.parseColor("#FFFF00") // Jaune quand proche
                    } else {
                        Color.parseColor("#FFFFFF")
                    }
                    
                    // Forme du kicker plus visible
                    reusablePath.reset()
                    reusablePath.moveTo(screenX - kickerSize, screenY + kickerSize/2f)
                    reusablePath.lineTo(screenX - kickerSize/3f, screenY - kickerSize/4f)
                    reusablePath.lineTo(screenX + kickerSize/3f, screenY - kickerSize/4f)
                    reusablePath.lineTo(screenX + kickerSize, screenY + kickerSize/2f)
                    reusablePath.close()
                    canvas.drawPath(reusablePath, paint)
                    
                    // Ombre du kicker
                    paint.color = Color.parseColor("#33000000")
                    reusableRectF.set(screenX - kickerSize, screenY + kickerSize/2f, 
                                     screenX + kickerSize, screenY + kickerSize/2f + 8f)
                    canvas.drawOval(reusableRectF, paint)
                    
                    // Indicateur de taille et distance
                    if (perspective > 0.3f) {
                        paint.color = Color.BLACK
                        paint.textSize = 14f * perspective
                        paint.textAlign = Paint.Align.CENTER
                        val sizeText = when (kicker.size) {
                            SkiFreestyleActivity.KickerSize.SMALL -> "S"
                            SkiFreestyleActivity.KickerSize.MEDIUM -> "M"
                            SkiFreestyleActivity.KickerSize.LARGE -> "L"
                        }
                        canvas.drawText(sizeText, screenX, screenY + kickerSize/6f, paint)
                        
                        // Distance si proche
                        if (kickerScreenDistance > 0f && kickerScreenDistance < 100f) {
                            paint.textSize = 12f * perspective
                            paint.color = Color.RED
                            canvas.drawText("${kickerScreenDistance.toInt()}m", screenX, screenY - kickerSize/2f, paint)
                        }
                    }
                }
            }
        }
    }
    
    private fun drawSkierFromBehind(canvas: Canvas, w: Int, h: Int) {
        val skierScreenX = w * (0.15f + activity.skierX * 0.7f)
        val skierScreenY = h * activity.skierY
        
        canvas.save()
        canvas.translate(skierScreenX, skierScreenY)
        
        // Rotation selon tricks (RÉDUITE)
        when (activity.currentTrick) {
            SkiFreestyleActivity.FreestyleTrick.SPIN_360 -> canvas.rotate(activity.trickRotation * 0.2f) // Réduit de 0.5f
            SkiFreestyleActivity.FreestyleTrick.BACKFLIP -> canvas.rotate(activity.trickRotation * 0.1f) // Réduit de 0.3f
            SkiFreestyleActivity.FreestyleTrick.SPIN_GRAB -> {
                canvas.rotate(activity.trickRotation * 0.15f) // Réduit de 0.4f
                canvas.scale(1f + activity.trickProgress * 0.05f, 1f + activity.trickProgress * 0.05f) // Réduit
            }
            else -> {}
        }
        
        // Utiliser l'image ou dessiner le skieur
        if (activity.skierBitmap != null) {
            // Dessiner l'image redimensionnée
            val bitmapSize = 60f
            val srcRect = Rect(0, 0, activity.skierBitmap!!.width, activity.skierBitmap!!.height)
            val dstRect = RectF(-bitmapSize/2f, -bitmapSize/2f, bitmapSize/2f, bitmapSize/2f)
            canvas.drawBitmap(activity.skierBitmap!!, srcRect, dstRect, paint)
        } else {
            // Dessiner le skieur original si l'image n'est pas disponible
            // Corps du skieur (vu de dos)
            paint.color = Color.parseColor("#FF6600") // Combinaison
            canvas.drawRect(-12f, -25f, 12f, 15f, paint)
            
            // Casque
            paint.color = Color.parseColor("#FFFFFF")
            canvas.drawCircle(0f, -30f, 10f, paint)
            
            // Bras
            paint.color = Color.parseColor("#FF6600")
            paint.strokeWidth = 6f
            paint.style = Paint.Style.STROKE
            
            if (activity.currentTrick == SkiFreestyleActivity.FreestyleTrick.INDY_GRAB || 
                activity.currentTrick == SkiFreestyleActivity.FreestyleTrick.SPIN_GRAB) {
                // Position grab
                canvas.drawLine(-8f, -8f, -15f, 20f, paint)
                canvas.drawLine(8f, -8f, 15f, 20f, paint)
            } else {
                // Position normale
                canvas.drawLine(-10f, -12f, -18f, -5f, paint)
                canvas.drawLine(10f, -12f, 18f, -5f, paint)
            }
            
            // Jambes
            canvas.drawLine(-6f, 10f, -10f, 30f, paint)
            canvas.drawLine(6f, 10f, 10f, 30f, paint)
            
            // Skis
            paint.color = Color.YELLOW
            paint.strokeWidth = 8f
            canvas.drawLine(-15f, 25f, -15f, 45f, paint)
            canvas.drawLine(15f, 25f, 15f, 45f, paint)
            
            // Bâtons
            paint.color = Color.parseColor("#8B4513")
            paint.strokeWidth = 4f
            canvas.drawLine(-20f, -8f, -25f, -20f, paint)
            canvas.drawLine(20f, -8f, 25f, -20f, paint)
            
            paint.style = Paint.Style.FILL
        }
        
        canvas.restore()
        
        // Ombre si au sol
        if (!activity.isInAir) {
            paint.color = Color.parseColor("#33000000")
            canvas.drawOval(skierScreenX - 25f, h * 0.92f, skierScreenX + 25f, h * 0.95f, paint)
        }
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
        
        // Score final
        paint.color = Color.parseColor("#001122")
        paint.textSize = 72f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("${activity.finalScore}", w/2f, h * 0.2f, paint)
        
        paint.textSize = 32f
        canvas.drawText("POINTS", w/2f, h * 0.28f, paint)
        
        // Breakdown détaillé
        paint.color = Color.parseColor("#333333")
        paint.textSize = 22f
        
        val startY = h * 0.4f
        val lineHeight = 30f
        
        canvas.drawText("🎿 Kickers touchés: ${activity.kickersHit}/6", w/2f, startY, paint)
        canvas.drawText("🎪 Tricks réussis: ${activity.tricksCompleted}", w/2f, startY + lineHeight, paint)
        canvas.drawText("📏 Amplitude max: ${(activity.amplitude * 250).toInt()}cm", w/2f, startY + lineHeight * 2, paint)
        canvas.drawText("🎯 Atterrissages parfaits: ${activity.perfectLandings}", w/2f, startY + lineHeight * 3, paint)
        canvas.drawText("🌈 Variété: ${activity.tricksUsed.size} tricks différents", w/2f, startY + lineHeight * 4, paint)
        canvas.drawText("⚡ Vitesse max: ${activity.speed.toInt()} km/h", w/2f, startY + lineHeight * 5, paint)
        
        // Message selon performance
        val message = when {
            activity.finalScore >= 300 -> "🏆 RUN LÉGENDAIRE!"
            activity.finalScore >= 250 -> "🥇 EXCELLENT STYLE!"
            activity.finalScore >= 200 -> "🥈 TRÈS BON RUN!"
            activity.finalScore >= 150 -> "🥉 BIEN JOUÉ!"
            else -> "💪 CONTINUE À PROGRESSER!"
        }
        
        paint.color = Color.parseColor("#FF6600")
        paint.textSize = 28f
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
