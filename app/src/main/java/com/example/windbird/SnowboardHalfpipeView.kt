package com.example.windbird

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.*

class SnowboardHalfpipeView(
    context: Context,
    private val activity: SnowboardHalfpipeActivity
) : View(context) {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Cache des objets réutilisables
    private val reusableRectF = RectF()
    private val reusablePath = Path()
    
    // Images du snowboarder
    private var snowFrontBitmap: Bitmap? = null
    private var snowTrickBitmap: Bitmap? = null
    
    init {
        loadBitmaps()
    }
    
    private fun loadBitmaps() {
        try {
            snowFrontBitmap = BitmapFactory.decodeResource(resources, R.drawable.snow_front)
            snowTrickBitmap = BitmapFactory.decodeResource(resources, R.drawable.snow_trick)
        } catch (e: Exception) {
            // Les bitmaps resteront null, le fallback sera utilisé
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        snowFrontBitmap?.recycle()
        snowTrickBitmap?.recycle()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width
        val h = height
        
        when (activity.getGameState()) {
            SnowboardHalfpipeActivity.GameState.PREPARATION -> drawPreparation(canvas, w, h)
            SnowboardHalfpipeActivity.GameState.RIDING -> drawRiding(canvas, w, h)
            SnowboardHalfpipeActivity.GameState.RESULTS -> drawResults(canvas, w, h)
            SnowboardHalfpipeActivity.GameState.FINISHED -> drawResults(canvas, w, h)
        }
    }
    
    private fun drawPreparation(canvas: Canvas, w: Int, h: Int) {
        // Dessiner l'image de préparation du halfpipe
        activity.getHalfpipePreparationBitmap()?.let { bitmap ->
            val scaleX = w.toFloat() / bitmap.width
            val scaleY = h.toFloat() / bitmap.height
            val scale = maxOf(scaleX, scaleY)
            
            val scaledWidth = bitmap.width * scale
            val scaledHeight = bitmap.height * scale
            val left = (w - scaledWidth) / 2f
            val top = (h - scaledHeight) / 2f
            
            val destRect = RectF(left, top, left + scaledWidth, top + scaledHeight)
            canvas.drawBitmap(bitmap, null, destRect, null)
        } ?: run {
            // Fallback si pas d'image de préparation
            // Fond dégradé ciel
            val skyGradient = LinearGradient(0f, 0f, 0f, h.toFloat(),
                Color.parseColor("#87CEEB"), Color.parseColor("#E0F6FF"), Shader.TileMode.CLAMP)
            paint.shader = skyGradient
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            paint.shader = null
            
            // Vue de dessus du halfpipe (perspective)
            drawHalfpipePerspective(canvas, w, h)
        }
        
        // Dessiner le drapeau du pays en haut à gauche
        activity.getCountryFlagBitmap()?.let { flagBitmap ->
            val flagSize = w * 0.15f // 15% de la largeur de l'écran
            val margin = 20f
            val destRect = RectF(margin, margin, margin + flagSize, margin + flagSize * 0.6f)
            canvas.drawBitmap(flagBitmap, null, destRect, null)
        } ?: run {
            // Fallback avec emoji si pas d'image de drapeau
            val playerCountry = if (activity.getPracticeMode()) "CANADA" else activity.getTournamentData().playerCountries[activity.getCurrentPlayerIndex()]
            val flagText = activity.getCountryFlag(playerCountry)
            
            paint.color = Color.parseColor("#FFFFFF")
            paint.style = Paint.Style.FILL
            reusableRectF.set(50f, 50f, 250f, 170f)
            canvas.drawRoundRect(reusableRectF, 15f, 15f, paint)
            
            paint.color = Color.parseColor("#001144")
            paint.textSize = 60f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(flagText, 150f, 130f, paint)
            
            paint.textSize = 20f
            canvas.drawText(playerCountry.uppercase(), 150f, 160f, paint)
        }
        
        // Titre de l'épreuve
        paint.color = Color.parseColor("#001144")
        paint.textSize = 48f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("🏂 SNOWBOARD HALFPIPE 🏂", w/2f, h * 0.15f, paint)
        
        // Texte de préparation (3 fois plus gros et gras)
        val preparationPaint = Paint().apply {
            color = Color.WHITE
            textSize = 180f // 3 fois plus gros que 60f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            setShadowLayer(5f, 2f, 2f, Color.BLACK) // Ombre pour meilleure lisibilité
        }
        
        canvas.drawText(
            "Préparez-vous !",
            w / 2f,
            h / 2f - 150,
            preparationPaint
        )
        
        val countdownText = "Départ dans ${((activity.getPreparationDuration() - activity.getPhaseTimer())).toInt() + 1}"
        canvas.drawText(
            countdownText,
            w / 2f,
            h / 2f + 50,
            preparationPaint
        )
        
        // Instructions
        paint.textSize = 24f
        paint.color = Color.parseColor("#333333")
        canvas.drawText("📱 Inclinez vers l'avant pour pomper", w/2f, h * 0.85f, paint)
        canvas.drawText("📱 Mouvements en l'air = tricks", w/2f, h * 0.9f, paint)
    }
    
    private fun drawHalfpipePerspective(canvas: Canvas, w: Int, h: Int) {
        // Vue en perspective du halfpipe depuis le haut
        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        
        // Halfpipe en perspective (forme d'ellipse allongée)
        reusableRectF.set(w * 0.2f, h * 0.3f, w * 0.8f, h * 0.6f)
        canvas.drawOval(reusableRectF, paint)
        
        // Bords du halfpipe
        paint.color = Color.parseColor("#CCCCCC")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        canvas.drawOval(reusableRectF, paint)
        
        // Lignes de perspective
        paint.strokeWidth = 3f
        for (i in 1..4) {
            val y = h * (0.3f + i * 0.075f)
            canvas.drawLine(w * 0.25f, y, w * 0.75f, y, paint)
        }
        
        paint.style = Paint.Style.FILL
    }
    
    private fun drawRiding(canvas: Canvas, w: Int, h: Int) {
        // Vue depuis l'intérieur du halfpipe
        drawHalfpipeInterior(canvas, w, h)
        
        // Snowboarder vu de derrière
        drawSnowboarderFromBehind(canvas, w, h)
        
        // Interface de jeu
        drawGameInterface(canvas, w, h)
        
        // Barre de rythme de pumping
        drawPumpRhythmBar(canvas, w, h)
        
        // Altimètre si en l'air
        if (activity.getIsInAir()) {
            drawAltimeter(canvas, w, h)
        }
    }
    
    private fun drawHalfpipeInterior(canvas: Canvas, w: Int, h: Int) {
        // Décor hivernal en arrière-plan
        drawWinterBackground(canvas, w, h)
        
        // Piste animée avec le sprite-sheet
        drawAnimatedTrack(canvas, w, h)
    }
    
    private fun drawWinterBackground(canvas: Canvas, w: Int, h: Int) {
        // Ciel hivernal dégradé
        val skyGradient = LinearGradient(0f, 0f, 0f, h * 0.6f,
            Color.parseColor("#E6F3FF"), Color.parseColor("#B8E0FF"), Shader.TileMode.CLAMP)
        paint.shader = skyGradient
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.6f, paint)
        paint.shader = null
        
        // Montagnes enneigées en arrière-plan
        paint.color = Color.parseColor("#F0F8FF")
        val mountainPath = Path()
        mountainPath.moveTo(0f, h * 0.3f)
        mountainPath.lineTo(w * 0.15f, h * 0.1f)
        mountainPath.lineTo(w * 0.3f, h * 0.25f)
        mountainPath.lineTo(w * 0.5f, h * 0.05f)
        mountainPath.lineTo(w * 0.7f, h * 0.2f)
        mountainPath.lineTo(w * 0.85f, h * 0.08f)
        mountainPath.lineTo(w.toFloat(), h * 0.25f)
        mountainPath.lineTo(w.toFloat(), h * 0.6f)
        mountainPath.lineTo(0f, h * 0.6f)
        mountainPath.close()
        canvas.drawPath(mountainPath, paint)
        
        // Sapins sur les côtés
        drawWinterTrees(canvas, w, h)
        
        // Flocons de neige qui tombent
        drawSnowflakes(canvas, w, h)
    }
    
    private fun drawWinterTrees(canvas: Canvas, w: Int, h: Int) {
        paint.color = Color.parseColor("#2D5016") // Vert sapin foncé
        
        // Sapins à gauche
        for (i in 0..3) {
            val treeX = w * (0.05f + i * 0.08f)
            val treeY = h * (0.35f + i * 0.1f)
            val treeSize = 30f + i * 10f
            
            // Tronc
            paint.color = Color.parseColor("#8B4513")
            canvas.drawRect(treeX - 5f, treeY, treeX + 5f, treeY + treeSize * 0.3f, paint)
            
            // Branches (3 étages)
            paint.color = Color.parseColor("#2D5016")
            for (j in 0..2) {
                val branchY = treeY - j * treeSize * 0.2f
                val branchSize = treeSize * (1f - j * 0.2f)
                val trianglePath = Path()
                trianglePath.moveTo(treeX, branchY - branchSize * 0.4f)
                trianglePath.lineTo(treeX - branchSize * 0.5f, branchY)
                trianglePath.lineTo(treeX + branchSize * 0.5f, branchY)
                trianglePath.close()
                canvas.drawPath(trianglePath, paint)
            }
        }
        
        // Sapins à droite (miroir)
        for (i in 0..3) {
            val treeX = w * (0.95f - i * 0.08f)
            val treeY = h * (0.35f + i * 0.1f)
            val treeSize = 30f + i * 10f
            
            // Tronc
            paint.color = Color.parseColor("#8B4513")
            canvas.drawRect(treeX - 5f, treeY, treeX + 5f, treeY + treeSize * 0.3f, paint)
            
            // Branches
            paint.color = Color.parseColor("#2D5016")
            for (j in 0..2) {
                val branchY = treeY - j * treeSize * 0.2f
                val branchSize = treeSize * (1f - j * 0.2f)
                val trianglePath = Path()
                trianglePath.moveTo(treeX, branchY - branchSize * 0.4f)
                trianglePath.lineTo(treeX - branchSize * 0.5f, branchY)
                trianglePath.lineTo(treeX + branchSize * 0.5f, branchY)
                trianglePath.close()
                canvas.drawPath(trianglePath, paint)
            }
        }
    }
    
    private fun drawSnowflakes(canvas: Canvas, w: Int, h: Int) {
        paint.color = Color.WHITE
        val currentTime = System.currentTimeMillis()
        
        // Flocons animés qui tombent
        for (i in 0..15) {
            val flakeX = (w * 0.1f + (i * 67f) % (w * 0.8f)) + 
                        sin((currentTime + i * 1000L) / 2000.0) * 30f
            val flakeY = ((currentTime / 50L + i * 100L) % (h * 1.2f)).toFloat() - h * 0.2f
            
            if (flakeY > 0 && flakeY < h) {
                val flakeSize = 2f + (i % 3) * 1f
                canvas.drawCircle(flakeX.toFloat(), flakeY, flakeSize, paint)
            }
        }
    }
    
    private fun drawAnimatedTrack(canvas: Canvas, w: Int, h: Int) {
        activity.getSnowTrackSpriteBitmap()?.let { trackBitmap ->
            val trackFrames = activity.getTrackFrames()
            if (trackFrames.isEmpty()) return
            
            // Animation simple : changer d'image selon la vitesse et le temps
            val currentTime = System.currentTimeMillis()
            val animationSpeed = activity.getSpeed() / 5f
            val frameIndex = ((currentTime / (200f / animationSpeed)).toInt() % trackFrames.size + trackFrames.size) % trackFrames.size
            
            // Prendre UNE SEULE image du sprite-sheet et l'afficher en plein écran
            val frame = trackFrames[frameIndex]
            
            // Afficher l'image en plein écran
            reusableRectF.set(0f, 0f, w.toFloat(), h.toFloat())
            canvas.drawBitmap(trackBitmap, frame, reusableRectF, paint)
        }
    }
    
    private fun drawMovementParticles(canvas: Canvas, w: Int, h: Int, speed: Float) {
        if (speed > 8f) {
            paint.color = Color.parseColor("#60FFFFFF") // Blanc semi-transparent
            val currentTime = System.currentTimeMillis()
            
            // Particules qui se déplacent depuis le fond vers l'avant
            for (i in 0..12) {
                val particleSpeed = speed * (1f + i * 0.1f)
                val particleX = ((currentTime / 60f + i * 80f) * particleSpeed / 15f) % (w * 1.3f) - w * 0.15f
                val particleY = h * (0.2f + (i % 4) * 0.2f)
                val particleSize = 3f + (speed / 20f) * 4f
                
                if (particleX > -20f && particleX < w + 20f) {
                    canvas.drawCircle(particleX, particleY, particleSize, paint)
                }
            }
        }
    }
    
    private fun drawSpeedLines(canvas: Canvas, w: Int, h: Int, speed: Float) {
        if (speed > 10f) {
            paint.color = Color.parseColor("#40FFFFFF") // Blanc semi-transparent
            paint.strokeWidth = 2f
            
            val currentTime = System.currentTimeMillis()
            val lineSpeed = speed * 4f
            
            // Dessiner des lignes de vitesse qui défilent
            for (i in 0..8) {
                val lineOffset = ((currentTime / 30f + i * 50f) * lineSpeed / 10f) % (h * 1.5f)
                val lineY = lineOffset - h * 0.25f
                
                if (lineY > 0 && lineY < h) {
                    val lineLength = 30f + (speed / 30f) * 20f
                    canvas.drawLine(
                        w * (0.1f + i * 0.1f),
                        lineY,
                        w * (0.1f + i * 0.1f),
                        lineY + lineLength,
                        paint
                    )
                }
            }
        }
    }
    
    private fun drawSnowboarderFromBehind(canvas: Canvas, w: Int, h: Int) {
        // Position du snowboarder qui suit la forme du halfpipe
        // Positionner le skieur 10% plus haut dans l'écran
        val riderScreenX = w * activity.getRiderPosition()
        val riderScreenY = h * (activity.getRiderHeight() - 0.1f) // 10% plus haut
        
        canvas.save()
        canvas.translate(riderScreenX, riderScreenY)
        
        // Rotation selon la position sur la rampe (inclinaison naturelle)
        val slopeAngle = (activity.getRiderPosition() - 0.5f) * 30f
        canvas.rotate(slopeAngle)
        
        // Sélection de l'image et rotations selon l'état
        val (snowboarderImage, additionalRotation) = getSnowboarderImageAndRotation()
        
        // Appliquer la rotation additionnelle pour les tricks
        canvas.rotate(additionalRotation)
        
        // Échelle selon si en l'air ou pas
        val scale = if (activity.getIsInAir()) 1.1f else 1f
        canvas.scale(scale, scale)
        
        // Dessiner l'image du snowboarder avec découpage intelligent
        snowboarderImage?.let { image ->
            drawSnowboarderFromSpriteSheet(canvas, image, activity.getCacheKey(image))
        } ?: run {
            // Fallback si pas d'image
            paint.color = Color.parseColor("#FF6600")
            canvas.drawCircle(0f, 0f, 16f, paint) // Aussi réduit de 20%
            
            // Snowboard
            paint.color = Color.parseColor("#4400FF")
            canvas.drawRoundRect(-20f, 12f, 20f, 20f, 4f, 4f, paint) // Réduit de 20%
        }
        
        canvas.restore()
        
        // Trail de mouvement selon la direction
        if (abs(activity.getMomentum()) > 0.1f) {
            paint.color = Color.parseColor("#60FFFFFF")
            for (i in 1..3) {
                val trailX = riderScreenX - activity.getMomentum() * i * 30f
                val trailY = riderScreenY // Utiliser la même position Y ajustée
                val trailAlpha = (255 * (1f - i * 0.3f)).toInt()
                paint.alpha = trailAlpha
                canvas.drawCircle(trailX, trailY, (4f - i) * 6f, paint)
            }
            paint.alpha = 255
        }
    }
    
    private fun getSnowboarderImageAndRotation(): Pair<Bitmap?, Float> {
        return when {
            // Phase de landing
            activity.getIsLanding() -> {
                val sideLanding = if (activity.getLastSide() == SnowboardHalfpipeActivity.RiderSide.LEFT) SnowboardHalfpipeActivity.RiderSide.LEFT else SnowboardHalfpipeActivity.RiderSide.RIGHT
                val image = if (sideLanding == SnowboardHalfpipeActivity.RiderSide.LEFT) activity.getSnowLeftLandingBitmap() else activity.getSnowRightLandingBitmap()
                Pair(image, 0f)
            }
            
            // Tricks en l'air
            activity.getIsInAir() && activity.getCurrentTrick() != SnowboardHalfpipeActivity.TrickType.NONE -> {
                when (activity.getCurrentTrick()) {
                    SnowboardHalfpipeActivity.TrickType.SPIN -> {
                        val side = if (activity.getRiderPosition() < 0.5f) SnowboardHalfpipeActivity.RiderSide.LEFT else SnowboardHalfpipeActivity.RiderSide.RIGHT
                        val image = if (side == SnowboardHalfpipeActivity.RiderSide.LEFT) activity.getSnowLeftRotationBitmap() else activity.getSnowRightRotationBitmap()
                        Pair(image, activity.getTrickRotation() * 0.8f) // Rotation selon progression
                    }
                    SnowboardHalfpipeActivity.TrickType.FLIP -> {
                        val side = if (activity.getRiderPosition() < 0.5f) SnowboardHalfpipeActivity.RiderSide.LEFT else SnowboardHalfpipeActivity.RiderSide.RIGHT
                        val image = if (side == SnowboardHalfpipeActivity.RiderSide.LEFT) activity.getSnowLeftSpinBitmap() else activity.getSnowRightSpinBitmap()
                        Pair(image, activity.getTrickFlip() * 0.6f)
                    }
                    SnowboardHalfpipeActivity.TrickType.GRAB -> {
                        val side = if (activity.getRiderPosition() < 0.5f) SnowboardHalfpipeActivity.RiderSide.LEFT else SnowboardHalfpipeActivity.RiderSide.RIGHT
                        val image = if (side == SnowboardHalfpipeActivity.RiderSide.LEFT) activity.getSnowLeftGrabBitmap() else activity.getSnowRightGrabBitmap()
                        Pair(image, 0f)
                    }
                    SnowboardHalfpipeActivity.TrickType.COMBO -> {
                        val side = if (activity.getRiderPosition() < 0.5f) SnowboardHalfpipeActivity.RiderSide.LEFT else SnowboardHalfpipeActivity.RiderSide.RIGHT
                        val image = if (side == SnowboardHalfpipeActivity.RiderSide.LEFT) activity.getSnowLeftRotationBitmap() else activity.getSnowRightRotationBitmap()
                        Pair(image, (activity.getTrickRotation() + activity.getTrickFlip()) * 0.5f)
                    }
                    else -> {
                        val side = if (activity.getRiderPosition() < 0.5f) SnowboardHalfpipeActivity.RiderSide.LEFT else SnowboardHalfpipeActivity.RiderSide.RIGHT
                        val image = if (side == SnowboardHalfpipeActivity.RiderSide.LEFT) activity.getSnowLeftSpriteBitmap() else activity.getSnowRightSpriteBitmap()
                        Pair(image, 0f)
                    }
                }
            }
            
            // Montée/descente normale
            else -> {
                when (activity.getCurrentSide()) {
                    SnowboardHalfpipeActivity.RiderSide.LEFT -> Pair(activity.getSnowLeftSpriteBitmap(), 0f)
                    SnowboardHalfpipeActivity.RiderSide.RIGHT -> Pair(activity.getSnowRightSpriteBitmap(), 0f)
                    SnowboardHalfpipeActivity.RiderSide.CENTER -> {
                        // Au centre, utiliser l'image selon d'où on vient
                        when (activity.getLastSide()) {
                            SnowboardHalfpipeActivity.RiderSide.LEFT -> Pair(activity.getSnowLeftSpriteBitmap(), 0f)
                            SnowboardHalfpipeActivity.RiderSide.RIGHT -> Pair(activity.getSnowRightSpriteBitmap(), 0f)
                            else -> Pair(activity.getSnowLeftSpriteBitmap(), 0f) // Par défaut
                        }
                    }
                }
            }
        }
    }
    
    private fun drawSnowboarderFromSpriteSheet(canvas: Canvas, spriteSheet: Bitmap, cacheKey: String) {
        val frames = activity.getSnowboarderFrameCache()[cacheKey]
        if (frames == null || frames.isEmpty()) {
            // Fallback : découpage simple si pas de cache
            val frameWidth = spriteSheet.width / 5
            val frameHeight = spriteSheet.height
            val frameIndex = 2 // Frame du milieu par défaut
            
            val srcLeft = frameIndex * frameWidth
            val srcRect = Rect(srcLeft, 0, srcLeft + frameWidth, frameHeight)
            
            val imageScale = 1.2f // DOUBLE la taille (0.6f * 2 = 1.2f)
            val imageWidth = frameWidth * imageScale
            val imageHeight = frameHeight * imageScale
            
            reusableRectF.set(
                -imageWidth/2f,
                -imageHeight/2f,
                imageWidth/2f,
                imageHeight/2f
            )
            
            canvas.drawBitmap(spriteSheet, srcRect, reusableRectF, paint)
            return
        }
        
        // Sélectionner la frame selon l'état avec une meilleure logique d'animation
        val frameIndex = when {
            activity.getIsLanding() -> {
                // Animation de landing fluide
                val progress = (activity.getLandingTimer() * 4f).coerceIn(0f, frames.size - 1f)
                progress.toInt()
            }
            activity.getIsInAir() -> {
                when (activity.getCurrentTrick()) {
                    SnowboardHalfpipeActivity.TrickType.SPIN -> {
                        // Animation de rotation basée sur l'angle
                        val rotationFrame = ((activity.getTrickRotation() / 72f).toInt() % frames.size + frames.size) % frames.size
                        rotationFrame
                    }
                    SnowboardHalfpipeActivity.TrickType.GRAB -> {
                        // Frame de grab - utiliser les frames du milieu
                        val grabFrame = (frames.size / 2) + ((activity.getTrickProgress() * 2f).toInt() % 2)
                        grabFrame.coerceIn(0, frames.size - 1)
                    }
                    SnowboardHalfpipeActivity.TrickType.FLIP -> {
                        // Animation de flip
                        val flipFrame = ((activity.getTrickFlip() / 45f).toInt() % frames.size + frames.size) % frames.size
                        flipFrame
                    }
                    else -> {
                        // Frame d'air stable
                        (frames.size / 2).coerceAtMost(frames.size - 1)
                    }
                }
            }
            activity.getCurrentSide() == SnowboardHalfpipeActivity.RiderSide.CENTER -> {
                // Frame centrale pour aller tout droit
                (frames.size / 2).coerceAtMost(frames.size - 1)
            }
            else -> {
                // Animation cyclique basée sur le mouvement et le temps
                val animSpeed = maxOf(1f, abs(activity.getMomentum()) * 15f)
                val timeComponent = (System.currentTimeMillis() / 250L).toInt()
                val movementFrame = (timeComponent * animSpeed.toInt()) % frames.size
                movementFrame
            }
        }
        
        val safeFrameIndex = frameIndex.coerceIn(0, frames.size - 1)
        val frame = frames[safeFrameIndex]
        
        // Utiliser les vraies dimensions du sprite découpé
        val frameWidth = frame.width().toFloat()
        val frameHeight = frame.height().toFloat()
        
        // DOUBLE la taille du snowboarder
        val baseScale = 1.6f // Double de 0.8f
        val adaptiveScale = baseScale * minOf(1f, 80f / maxOf(frameWidth, frameHeight))
        
        val imageWidth = frameWidth * adaptiveScale
        val imageHeight = frameHeight * adaptiveScale
        
        reusableRectF.set(
            -imageWidth/2f,
            -imageHeight/2f,
            imageWidth/2f,
            imageHeight/2f
        )
        
        canvas.drawBitmap(spriteSheet, frame, reusableRectF, paint)
    }
    
    private fun drawGameInterface(canvas: Canvas, w: Int, h: Int) {
        val baseY = h - 140f
        
        // Score et métriques
        paint.color = Color.parseColor("#001144")
        paint.textSize = 22f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("Score: ${activity.getTotalScore().toInt()}", 20f, baseY, paint)
        canvas.drawText("Tricks: ${activity.getTricksCompleted()}", 20f, baseY + 25f, paint)
        canvas.drawText("Speed: ${activity.getSpeed().toInt()} km/h", 20f, baseY + 50f, paint)
        
        // Trick en cours
        if (activity.getCurrentTrick() != SnowboardHalfpipeActivity.TrickType.NONE) {
            paint.color = Color.parseColor("#FF6600")
            paint.textSize = 28f
            paint.textAlign = Paint.Align.CENTER
            
            val trickText = when (activity.getCurrentTrick()) {
                SnowboardHalfpipeActivity.TrickType.SPIN -> "${(activity.getTrickRotation()).toInt()}° SPIN"
                SnowboardHalfpipeActivity.TrickType.FLIP -> "FLIP ${(activity.getTrickFlip()).toInt()}°"
                SnowboardHalfpipeActivity.TrickType.GRAB -> "GRAB ${(activity.getTrickProgress() * 100).toInt()}%"
                SnowboardHalfpipeActivity.TrickType.COMBO -> "COMBO ${(activity.getTrickProgress() * 100).toInt()}%"
                else -> ""
            }
            
            canvas.drawText(trickText, w/2f, baseY, paint)
            
            // Phase du trick
            paint.textSize = 18f
            canvas.drawText("Phase: ${activity.getTrickPhase().name}", w/2f, baseY + 25f, paint)
        }
        
        // Métriques de performance
        drawPerformanceMeter(canvas, w - 180f, baseY - 20f, 160f, activity.getAmplitude() / 10f, "AMPLITUDE", Color.parseColor("#FF4444"))
        drawPerformanceMeter(canvas, w - 180f, baseY + 5f, 160f, activity.getFlow() / 120f, "FLOW", Color.parseColor("#44AAFF"))
        drawPerformanceMeter(canvas, w - 180f, baseY + 30f, 160f, activity.getStyle() / 120f, "STYLE", Color.parseColor("#44FF44"))
        
        // Combo actuel
        if (activity.getTrickCombo() > 1) {
            paint.color = Color.parseColor("#FFD700")
            paint.textSize = 20f
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("🔥 COMBO x${activity.getTrickCombo()}", w - 20f, baseY + 55f, paint)
        }
    }
    
    private fun drawPumpRhythmBar(canvas: Canvas, w: Int, h: Int) {
        val barX = 50f
        val barY = 120f
        val barWidth = w * 0.4f
        val barHeight = 30f
        
        // Fond de la barre
        paint.color = Color.parseColor("#333333")
        reusableRectF.set(barX, barY, barX + barWidth, barY + barHeight)
        canvas.drawRect(reusableRectF, paint)
        
        // Zone de pumping optimal
        if (activity.getPumpWindow()) {
            paint.color = Color.parseColor("#00FF00")
            val optimalWidth = barWidth * 0.3f
            val optimalX = barX + barWidth * 0.35f
            reusableRectF.set(optimalX, barY, optimalX + optimalWidth, barY + barHeight)
            canvas.drawRect(reusableRectF, paint)
        }
        
        // Indicateur de timing actuel
        val currentX = barX + (activity.getRiderHeight() - 0.6f) * barWidth / 0.4f
        paint.color = Color.parseColor("#FFFF00")
        canvas.drawLine(currentX, barY, currentX, barY + barHeight, paint)
        
        // Efficacité du pump
        if (activity.getPumpEnergy() > 0f) {
            paint.color = Color.parseColor("#FF6600")
            paint.alpha = (activity.getPumpEnergy() * 255).toInt()
            val pumpWidth = barWidth * activity.getPumpEfficiency()
            reusableRectF.set(barX, barY, barX + pumpWidth, barY + barHeight)
            canvas.drawRect(reusableRectF, paint)
            paint.alpha = 255
        }
        
        // Label
        paint.color = Color.WHITE
        paint.textSize = 16f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("PUMP RHYTHM", barX, barY - 5f, paint)
        
        if (activity.getPumpCombo() > 0) {
            paint.color = Color.parseColor("#00FF00")
            canvas.drawText("Perfect x${activity.getPumpCombo()}", barX, barY + barHeight + 20f, paint)
        }
    }
    
    private fun drawPerformanceMeter(canvas: Canvas, x: Float, y: Float, width: Float, 
                                   value: Float, label: String, color: Int) {
        // Fond
        paint.color = Color.parseColor("#333333")
        reusableRectF.set(x, y, x + width, y + 15f)
        canvas.drawRect(reusableRectF, paint)
        
        // Barre
        paint.color = color
        val filledWidth = value.coerceIn(0f, 1f) * width
        reusableRectF.set(x, y, x + filledWidth, y + 15f)
        canvas.drawRect(reusableRectF, paint)
        
        // Label
        paint.color = Color.WHITE
        paint.textSize = 12f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("$label: ${(value * 100).toInt()}%", x, y - 3f, paint)
    }
    
    private fun drawAltimeter(canvas: Canvas, w: Int, h: Int) {
        val altX = w - 120f
        val altY = 200f
        
        // Fond de l'altimètre
        paint.color = Color.parseColor("#000000")
        paint.alpha = 180
        reusableRectF.set(altX, altY, altX + 100f, altY + 120f)
        canvas.drawRoundRect(reusableRectF, 10f, 10f, paint)
        paint.alpha = 255
        
        // Hauteur actuelle
        paint.color = Color.parseColor("#00FF00")
        paint.textSize = 24f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("${activity.getAltimeter().toInt()}m", altX + 50f, altY + 40f, paint)
        
        // Air time
        paint.textSize = 16f
        canvas.drawText("Air: ${activity.getAirTime().toString().take(4)}s", altX + 50f, altY + 60f, paint)
        
        // Barre de hauteur
        val maxBarHeight = 80f
        val currentHeight = (activity.getAltimeter() / 15f).coerceIn(0f, 1f) * maxBarHeight
        
        paint.color = Color.parseColor("#333333")
        reusableRectF.set(altX + 10f, altY + 80f, altX + 30f, altY + 80f + maxBarHeight)
        canvas.drawRect(reusableRectF, paint)
        
        paint.color = Color.parseColor("#00FFFF")
        reusableRectF.set(altX + 10f, altY + 80f + maxBarHeight - currentHeight, altX + 30f, altY + 80f + maxBarHeight)
        canvas.drawRect(reusableRectF, paint)
        
        // Label
        paint.color = Color.WHITE
        paint.textSize = 14f
        canvas.drawText("ALTITUDE", altX + 50f, altY + 100f, paint)
    }
    
    private fun drawResults(canvas: Canvas, w: Int, h: Int) {
        // Fond festif avec dégradé
        val resultGradient = LinearGradient(0f, 0f, 0f, h.toFloat(),
            Color.parseColor("#FFD700"), Color.parseColor("#FFF8DC"), Shader.TileMode.CLAMP)
        paint.shader = resultGradient
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
        
        // Score final
        paint.color = Color.parseColor("#001144")
        paint.textSize = 72f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("${activity.getFinalScore()}", w/2f, h * 0.2f, paint)
        
        paint.textSize = 32f
        canvas.drawText("POINTS", w/2f, h * 0.28f, paint)
        
        // Détails de performance
        paint.color = Color.parseColor("#333333")
        paint.textSize = 24f
        
        val startY = h * 0.4f
        val lineHeight = 35f
        
        canvas.drawText("🏂 Tricks réussis: ${activity.getTricksCompleted()}", w/2f, startY, paint)
        canvas.drawText("⭐ Variété: ${activity.getTrickVariety().size} types", w/2f, startY + lineHeight, paint)
        canvas.drawText("📏 Amplitude max: ${activity.getMaxHeight().toInt()}m", w/2f, startY + lineHeight * 2, paint)
        canvas.drawText("⏱️ Air time max: ${activity.getMaxAirTime().toString().take(4)}s", w/2f, startY + lineHeight * 3, paint)
        canvas.drawText("🎯 Landings parfaits: ${activity.getPerfectLandings()}", w/2f, startY + lineHeight * 4, paint)
        canvas.drawText("🌊 Flow: ${activity.getFlow().toInt()}%", w/2f, startY + lineHeight * 5, paint)
        canvas.drawText("💎 Style: ${activity.getStyle().toInt()}%", w/2f, startY + lineHeight * 6, paint)
        
        // Message d'encouragement
        val encouragement = when {
            activity.getFinalScore() >= 300 -> "🏆 PERFORMANCE LÉGENDAIRE!"
            activity.getFinalScore() >= 250 -> "🥇 EXCELLENT RUN!"
            activity.getFinalScore() >= 200 -> "🥈 TRÈS BON STYLE!"
            activity.getFinalScore() >= 150 -> "🥉 BIEN JOUÉ!"
            else -> "💪 CONTINUE À T'ENTRAÎNER!"
        }
        
        paint.color = Color.parseColor("#FF6600")
        paint.textSize = 28f
        canvas.drawText(encouragement, w/2f, h * 0.9f, paint)
    }
}
