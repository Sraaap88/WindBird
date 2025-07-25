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
        reusableRectF.set(50f, 50f, 350f, 220f) // Plus gros
        canvas.drawRoundRect(reusableRectF, 15f, 15f, paint)
        
        paint.color = Color.parseColor("#001122")
        paint.textSize = 80f // 6x plus gros (était ~13f)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(flagText, 200f, 150f, paint)
        
        paint.textSize = 32f // 6x plus gros
        canvas.drawText(playerCountry.uppercase(), 200f, 200f, paint)
        
        // Titre
        paint.textSize = 72f // 6x plus gros
        canvas.drawText("🎿 SKI FREESTYLE 🎿", w/2f, h * 0.15f, paint)
        
        // Countdown
        paint.textSize = 150f // 6x plus gros
        paint.color = Color.RED
        canvas.drawText("3", w/2f, h * 0.5f, paint)
        
        // Instructions BEAUCOUP PLUS GROSSES
        paint.textSize = 48f // 6x plus gros (était 8f)
        paint.color = Color.parseColor("#001122")
        canvas.drawText("📱 PASSE SUR LES KICKERS ROUGES", w/2f, h * 0.7f, paint)
        canvas.drawText("🎿 POUR SAUTER AUTOMATIQUEMENT!", w/2f, h * 0.75f, paint)
        canvas.drawText("📱 INCLINE EN L'AIR = TRICKS", w/2f, h * 0.82f, paint)
        canvas.drawText("📱 INCLINE AVANT = PUMP VITESSE", w/2f, h * 0.87f, paint)
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
        // Fond ciel plus haut pour voir plus de piste
        val skyGradient = LinearGradient(0f, 0f, 0f, h * 0.3f,
            Color.parseColor("#87CEEB"), Color.parseColor("#E0F6FF"), Shader.TileMode.CLAMP)
        paint.shader = skyGradient
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.3f, paint)
        paint.shader = null
        
        // EFFET CYLINDRE : la piste se courbe vers l'horizon
        val scrollOffset = activity.pisteScroll % 150f
        
        paint.color = Color.WHITE
        
        // Piste courbée comme sur un cylindre géant
        reusablePath.reset()
        reusablePath.moveTo(w * 0.48f, 0f)           // Sommet étroit (horizon)
        reusablePath.lineTo(w * 0.52f, 0f)           
        reusablePath.lineTo(w * 0.75f, h.toFloat())   // Bas élargi (proche)
        reusablePath.lineTo(w * 0.25f, h.toFloat())   
        reusablePath.close()
        canvas.drawPath(reusablePath, paint)
        
        // LIGNES COURBES QUI SUIVENT LA COURBURE DU CYLINDRE
        paint.color = Color.parseColor("#DDDDDD")
        paint.strokeWidth = 4f
        paint.style = Paint.Style.STROKE
        
        for (i in 0..20) {
            val progress = i / 20f // De 0 (loin) à 1 (proche)
            val baseY = i * 40f + scrollOffset
            
            if (baseY >= 0f && baseY <= h.toFloat()) {
                // Perspective en Y (plus proche = plus bas)
                val perspectiveY = h * 0.3f + (h * 0.7f) * progress
                
                // Largeur qui s'élargit avec la perspective
                val leftX = w * (0.48f - progress * 0.23f)   // De 0.48 à 0.25
                val rightX = w * (0.52f + progress * 0.23f)  // De 0.52 à 0.75
                
                // COURBURE CYLINDRIQUE : effet "sommet de colline"
                val cylinderCurve = sin(progress * PI / 2).toFloat() // Courbe douce
                val curveIntensity = 50f * (1f - progress) // Plus courbé au loin
                
                reusablePath.reset()
                reusablePath.moveTo(leftX, perspectiveY)
                
                // Créer une courbe qui simule la courbure du cylindre
                val segments = 15
                for (j in 0..segments) {
                    val segmentProgress = j.toFloat() / segments
                    val segmentX = leftX + (rightX - leftX) * segmentProgress
                    
                    // Courbe vers le haut au centre (effet cylindre)
                    val centerCurve = sin(segmentProgress * PI).toFloat() * curveIntensity * cylinderCurve
                    val segmentY = perspectiveY - centerCurve
                    
                    reusablePath.lineTo(segmentX, segmentY)
                }
                
                canvas.drawPath(reusablePath, paint)
            }
        }
        
        // LIGNES DE BORD pour accentuer l'effet cylindre
        paint.color = Color.parseColor("#AAAAAA")
        paint.strokeWidth = 6f
        
        // Bord gauche courbé
        reusablePath.reset()
        reusablePath.moveTo(w * 0.48f, 0f)
        for (i in 0..20) {
            val progress = i / 20f
            val y = h * 0.3f + (h * 0.7f) * progress
            val x = w * (0.48f - progress * 0.23f)
            val curve = sin(progress * PI / 2).toFloat() * 30f
            reusablePath.lineTo(x - curve * 0.3f, y)
        }
        canvas.drawPath(reusablePath, paint)
        
        // Bord droit courbé
        reusablePath.reset()
        reusablePath.moveTo(w * 0.52f, 0f)
        for (i in 0..20) {
            val progress = i / 20f
            val y = h * 0.3f + (h * 0.7f) * progress
            val x = w * (0.52f + progress * 0.23f)
            val curve = sin(progress * PI / 2).toFloat() * 30f
            reusablePath.lineTo(x + curve * 0.3f, y)
        }
        canvas.drawPath(reusablePath, paint)
        
        paint.style = Paint.Style.FILL
    }
    
    private fun drawKickers(canvas: Canvas, w: Int, h: Int) {
        for (kicker in activity.kickers) {
            val kickerScreenDistance = kicker.distance - activity.distanceTraveled
            
            if (kickerScreenDistance > -50f && kickerScreenDistance < 800f) {
                // NOUVELLE PERSPECTIVE : vue 3/4 arrière en hauteur
                val progress = 1f - (kickerScreenDistance / 800f).coerceIn(0f, 1f)
                
                // Position Y avec effet cylindre
                val perspectiveY = h * 0.3f + (h * 0.7f) * progress
                val cylinderCurve = sin(progress * PI / 2).toFloat()
                val curveOffset = cylinderCurve * 40f
                val screenY = perspectiveY - curveOffset
                
                if (screenY >= 0f && screenY < h.toFloat()) {
                    // KICKERS VRAIMENT VISIBLES sur la piste courbée
                    val kickerSize = when (kicker.size) {
                        SkiFreestyleActivity.KickerSize.SMALL -> 30f
                        SkiFreestyleActivity.KickerSize.MEDIUM -> 50f
                        SkiFreestyleActivity.KickerSize.LARGE -> 80f
                    } * progress.coerceIn(0.2f, 1.5f)
                    
                    // Position X sur la piste courbée
                    val pisteLeft = w * (0.48f - progress * 0.23f)
                    val pisteRight = w * (0.52f + progress * 0.23f)
                    val pisteWidth = pisteRight - pisteLeft
                    val screenX = pisteLeft + kicker.x * pisteWidth
                    
                    // COULEURS SUPER VISIBLES
                    paint.color = if (kicker.hit) {
                        Color.parseColor("#00FF00") // VERT une fois hit
                    } else if (kickerScreenDistance < 40f && kickerScreenDistance > 0f) {
                        Color.parseColor("#FF0000") // ROUGE = SAUTE MAINTENANT!
                    } else if (kickerScreenDistance < 100f && kickerScreenDistance > 0f) {
                        Color.parseColor("#FFAA00") // ORANGE proche
                    } else if (kickerScreenDistance < 200f && kickerScreenDistance > 0f) {
                        Color.parseColor("#FFFF00") // JAUNE
                    } else {
                        Color.parseColor("#FFFFFF") // Blanc au loin
                    }
                    
                    // FORME DE RAMPE 3D sur la piste courbée
                    reusablePath.reset()
                    
                    // Base du kicker qui suit la courbure
                    val baseLeft = screenX - kickerSize
                    val baseRight = screenX + kickerSize
                    val baseY = screenY + kickerSize * 0.3f
                    
                    // Sommet du kicker (lip)
                    val lipY = screenY - kickerSize * 0.4f
                    val lipCurve = cylinderCurve * 15f
                    
                    // Dessiner le kicker en 3D
                    reusablePath.moveTo(baseLeft, baseY)
                    reusablePath.lineTo(screenX - kickerSize * 0.3f, lipY - lipCurve)
                    reusablePath.lineTo(screenX, lipY - lipCurve * 1.2f) // Pointe du lip
                    reusablePath.lineTo(screenX + kickerSize * 0.3f, lipY - lipCurve)
                    reusablePath.lineTo(baseRight, baseY)
                    reusablePath.close()
                    canvas.drawPath(reusablePath, paint)
                    
                    // CONTOUR NOIR ÉPAIS
                    paint.color = Color.BLACK
                    paint.strokeWidth = 4f
                    paint.style = Paint.Style.STROKE
                    canvas.drawPath(reusablePath, paint)
                    paint.style = Paint.Style.FILL
                    
                    // OMBRE qui suit la courbure
                    paint.color = Color.parseColor("#44000000")
                    reusableRectF.set(baseLeft, baseY, baseRight, baseY + 12f)
                    canvas.drawOval(reusableRectF, paint)
                    
                    // INDICATEURS GROS ET VISIBLES
                    if (progress > 0.3f) {
                        // Taille du kicker
                        paint.color = Color.BLACK
                        paint.textSize = 24f * progress
                        paint.textAlign = Paint.Align.CENTER
                        val sizeText = when (kicker.size) {
                            SkiFreestyleActivity.KickerSize.SMALL -> "SMALL"
                            SkiFreestyleActivity.KickerSize.MEDIUM -> "MEDIUM" 
                            SkiFreestyleActivity.KickerSize.LARGE -> "BIG"
                        }
                        canvas.drawText(sizeText, screenX, screenY + kickerSize * 0.1f, paint)
                        
                        // Distance avec couleur progressive
                        if (kickerScreenDistance > 0f && kickerScreenDistance < 200f) {
                            paint.textSize = 20f * progress
                            paint.color = when {
                                kickerScreenDistance < 30f -> Color.RED
                                kickerScreenDistance < 80f -> Color.parseColor("#FF6600") 
                                else -> Color.BLACK
                            }
                            canvas.drawText("${kickerScreenDistance.toInt()}m", screenX, lipY - kickerSize * 0.2f, paint)
                        }
                        
                        // FLÈCHE ÉNORME quand très proche
                        if (kickerScreenDistance < 60f && kickerScreenDistance > 0f) {
                            paint.color = Color.RED
                            paint.strokeWidth = 8f
                            paint.style = Paint.Style.STROKE
                            val arrowY = lipY - kickerSize * 0.8f
                            canvas.drawLine(screenX, arrowY, screenX - 20f, arrowY + 20f, paint)
                            canvas.drawLine(screenX, arrowY, screenX + 20f, arrowY + 20f, paint)
                            canvas.drawLine(screenX, arrowY, screenX, arrowY + 30f, paint)
                            paint.style = Paint.Style.FILL
                        }
                    }
                }
            }
        }
    }
    
    private fun drawSkierFromBehind(canvas: Canvas, w: Int, h: Int) {
        val skierScreenX = w * (0.35f + activity.skierX * 0.3f) // Centré sur la piste courbée
        var skierScreenY = h * 0.7f // Position plus visible sur la piste
        
        // SKIEUR QUI SUIT LA COURBURE DE LA PISTE
        val skierProgress = 0.8f // Position du skieur sur la courbe cylindrique
        val cylinderCurve = sin(skierProgress * PI / 2).toFloat()
        val curveOffset = cylinderCurve * 30f
        skierScreenY -= curveOffset
        
        // Si en l'air, mouvement vertical amplifié
        if (activity.isInAir) {
            activity.updateAirPhysics()
            // Calculer position relative avec la courbure
            val airOffset = (activity.skierY - 0.7f) * h * -3f // Amplification x3!
            skierScreenY += airOffset
            
            // EFFET VISUEL ÉNORME pour montrer qu'on est en l'air
            val airTimeIndicator = (activity.airTime * 150f).coerceAtMost(60f)
            
            // Cercle jaune qui pulse
            paint.color = Color.parseColor("#66FFFF00")
            canvas.drawCircle(skierScreenX, skierScreenY, 60f + airTimeIndicator, paint)
            
            // Traînée d'air derrière le skieur
            paint.color = Color.parseColor("#33FFFFFF")
            for (i in 1..5) {
                canvas.drawCircle(skierScreenX, skierScreenY + i * 15f, 40f - i * 5f, paint)
            }
            
            // Texte "AIR TIME" visible
            paint.color = Color.RED
            paint.textSize = 42f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("✈️ EN L'AIR!", skierScreenX, skierScreenY - 180f, paint)
            canvas.drawText("${(activity.airTime * 100).toInt()}%", skierScreenX, skierScreenY - 140f, paint)
        }
        
        canvas.save()
        canvas.translate(skierScreenX, skierScreenY)
        
        // ROTATIONS VRAIMENT VISIBLES pour les tricks!
        var totalRotation = 0f
        when (activity.currentTrick) {
            SkiFreestyleActivity.FreestyleTrick.SPIN_360 -> {
                totalRotation = activity.trickRotation * 1.5f // ENCORE PLUS VISIBLE!
                paint.color = Color.parseColor("#66FF0000")
                canvas.drawCircle(0f, 0f, 90f, paint)
            }
            SkiFreestyleActivity.FreestyleTrick.BACKFLIP -> {
                totalRotation = activity.trickRotation * 1f // FLIP BIEN VISIBLE!
                paint.color = Color.parseColor("#660000FF")
                canvas.drawCircle(0f, 0f, 90f, paint)
            }
            SkiFreestyleActivity.FreestyleTrick.SPIN_GRAB -> {
                totalRotation = activity.trickRotation * 1.2f
                canvas.scale(1f + activity.trickProgress * 0.3f, 1f + activity.trickProgress * 0.3f)
                paint.color = Color.parseColor("#66FFFF00")
                canvas.drawCircle(0f, 0f, 90f, paint)
            }
            SkiFreestyleActivity.FreestyleTrick.INDY_GRAB -> {
                canvas.scale(1f + activity.trickProgress * 0.2f, 1f + activity.trickProgress * 0.2f)
                paint.color = Color.parseColor("#6600FFFF")
                canvas.drawCircle(0f, 0f, 85f, paint)
            }
            else -> {}
        }
        
        // APPLIQUER LA ROTATION À L'IMAGE!
        if (totalRotation != 0f) {
            canvas.rotate(totalRotation)
        }
        
        // IMAGE DU SKIEUR ÉNORME et bien visible dans cette vue
        if (activity.skierBitmap != null) {
            val bitmapSize = if (activity.isInAir) 600f else 500f // ENCORE PLUS GROS!
            val srcRect = Rect(0, 0, activity.skierBitmap!!.width, activity.skierBitmap!!.height)
            val dstRect = RectF(-bitmapSize/2f, -bitmapSize/2f, bitmapSize/2f, bitmapSize/2f)
            canvas.drawBitmap(activity.skierBitmap!!, srcRect, dstRect, paint)
        } else {
            // Fallback énorme aussi
            paint.color = Color.parseColor("#FF6600")
            canvas.drawRect(-75f, -150f, 75f, 100f, paint)
            paint.color = Color.parseColor("#FFFFFF")
            canvas.drawCircle(0f, -180f, 60f, paint)
        }
        
        canvas.restore()
        
        // OMBRE qui suit la courbure de la piste
        if (!activity.isInAir) {
            paint.color = Color.parseColor("#55000000")
            val shadowY = h * 0.75f + curveOffset * 0.5f
            canvas.drawOval(skierScreenX - 80f, shadowY, skierScreenX + 80f, shadowY + 25f, paint)
        } else {
            // Ombre projetée en l'air (où on va atterrir)
            val projectedLanding = skierScreenX + activity.horizontalVelocity * 150f
            val shadowY = h * 0.75f + curveOffset * 0.5f
            paint.color = Color.parseColor("#33FF0000")
            canvas.drawOval(projectedLanding - 60f, shadowY, projectedLanding + 60f, shadowY + 20f, paint)
            
            paint.color = Color.parseColor("#44FFFFFF")
            paint.strokeWidth = 4f
            paint.style = Paint.Style.STROKE
            canvas.drawLine(skierScreenX, skierScreenY, projectedLanding, shadowY + 10f, paint)
            paint.style = Paint.Style.FILL
        }
        
        // INSTRUCTIONS TOUJOURS VISIBLES en bas
        paint.color = Color.parseColor("#FFFF00")
        paint.textSize = 32f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("🎿 PASSE SUR LES KICKERS ROUGES POUR SAUTER!", w/2f, h - 60f, paint)
        canvas.drawText("📱 Incline en l'air pour faire des tricks!", w/2f, h - 25f, paint)
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
