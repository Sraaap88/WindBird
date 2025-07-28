package com.example.windbird

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import kotlin.math.*

class BobsledRenderer(private val context: Context, private val activity: BobsledActivity) {
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Images du bobsleigh
    private var bobsledPreparationBitmap: Bitmap? = null
    private var bobPushBitmap: Bitmap? = null
    private var bobStraightBitmap: Bitmap? = null
    private var bobLeftBitmap: Bitmap? = null
    private var bobRightBitmap: Bitmap? = null
    private var bobFinishLineBitmap: Bitmap? = null
    private var bobCelebrationBitmap: Bitmap? = null
    
    // Images de la piste sprite
    private var bobtrackLeftSpriteBitmap: Bitmap? = null
    
    // Images des drapeaux
    private var flagCanadaBitmap: Bitmap? = null
    private var flagUsaBitmap: Bitmap? = null
    private var flagFranceBitmap: Bitmap? = null
    private var flagNorvegeBitmap: Bitmap? = null
    private var flagJapanBitmap: Bitmap? = null
    
    // Variables pour découper le sprite-sheet
    private var spriteFrameWidth = 0
    private var spriteFrameHeight = 0
    private val totalFrames = 9 // Nombre d'images dans le sprite-sheet
    
    // Variables pour sprite-sheet Winter Games
    private var currentFrameIndex = 0
    private var frameTimer = 0f
    private var isReversing = false
    private var trackSection = TrackSection.STRAIGHT
    private var landscapeOffset = 0f
    
    // NOUVEAU: Variables pour effets de vitesse
    private var speedLineOffset = 0f
    private var tunnelEffect = 0f
    
    enum class TrackSection {
        STRAIGHT, LEFT_TURN, RIGHT_TURN, LEFT_RETURN, RIGHT_RETURN
    }
    
    init {
        loadBitmaps()
    }
    
    private fun loadBitmaps() {
        try {
            bobsledPreparationBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bobsled_preparation)
            bobPushBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bob_push)
            bobStraightBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bobnv_straight)
            bobLeftBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bobnv_left)
            bobRightBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bobnv_right)
            bobFinishLineBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bob_finish_line)
            bobCelebrationBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bob_celebration)
            
            // Charger le sprite-sheet de la piste
            bobtrackLeftSpriteBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.bobtrack_left_sprite)
            bobtrackLeftSpriteBitmap?.let { sprite ->
                spriteFrameWidth = sprite.width / totalFrames
                spriteFrameHeight = sprite.height
            }
            
            // Charger les drapeaux
            flagCanadaBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.flag_canada)
            flagUsaBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.flag_usa)
            flagFranceBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.flag_france)
            flagNorvegeBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.flag_norvege)
            flagJapanBitmap = BitmapFactory.decodeResource(context.resources, R.drawable.flag_japan)
        } catch (e: Exception) {
            createFallbackBobsledBitmaps()
        }
    }
    
    private fun createFallbackBobsledBitmaps() {
        bobsledPreparationBitmap = createSubstituteBitmap(Color.parseColor("#FF4444"))
        bobPushBitmap = createSubstituteBitmap(Color.parseColor("#FF6644"))
        bobStraightBitmap = createSubstituteBitmap(Color.parseColor("#FFB444"))
        bobLeftBitmap = createSubstituteBitmap(Color.parseColor("#44FF44"))
        bobRightBitmap = createSubstituteBitmap(Color.parseColor("#4444FF"))
        bobFinishLineBitmap = createSubstituteBitmap(Color.parseColor("#44FFFF"))
        bobCelebrationBitmap = createSubstituteBitmap(Color.parseColor("#FFB444"))
    }
    
    private fun createSubstituteBitmap(color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val tempPaint = Paint().apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(10f, 20f, 110f, 60f, 10f, 10f, tempPaint)
        return bitmap
    }

    // Fonction pour extraire une frame du sprite-sheet
    private fun getTrackSpriteFrame(frameIndex: Int, mirrorHorizontal: Boolean = false, reverse: Boolean = false): Bitmap? {
        return bobtrackLeftSpriteBitmap?.let { sprite ->
            val actualFrameIndex = if (reverse) {
                (totalFrames - 1 - frameIndex).coerceIn(0, totalFrames - 1)
            } else {
                frameIndex.coerceIn(0, totalFrames - 1)
            }
            
            val sourceRect = Rect(
                actualFrameIndex * spriteFrameWidth,
                0,
                (actualFrameIndex + 1) * spriteFrameWidth,
                spriteFrameHeight
            )
            
            val frameBitmap = Bitmap.createBitmap(sprite, sourceRect.left, sourceRect.top, sourceRect.width(), sourceRect.height())
            
            if (mirrorHorizontal) {
                val matrix = Matrix().apply { postScale(-1f, 1f) }
                Bitmap.createBitmap(frameBitmap, 0, 0, frameBitmap.width, frameBitmap.height, matrix, false)
            } else {
                frameBitmap
            }
        }
    }

    fun handleTouch(event: MotionEvent, width: Int, height: Int): Boolean {
        val gameData = activity.getGameData()
        
        if (gameData.gameState == BobsledActivity.GameState.PUSH_START) {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    // NOUVEAU SYSTÈME: Zone de toucher fixe au centre
                    val bobX = width / 2f
                    val bobY = height * 0.65f
                    val touchRadius = 120f // Zone plus large
                    
                    val touchX = event.x
                    val touchY = event.y
                    val distance = sqrt((touchX - bobX).pow(2) + (touchY - bobY).pow(2))
                    
                    if (distance <= touchRadius) {
                        // TENIR = CHARGER (plus rapide)
                        activity.updatePushPower(8f) // 2x plus rapide
                        return true
                    }
                }
            }
        }
        return false
    }

    fun render(canvas: Canvas, w: Int, h: Int) {
        val gameData = activity.getGameData()
        
        when (gameData.gameState) {
            BobsledActivity.GameState.PREPARATION -> drawPreparation(canvas, w, h, gameData)
            BobsledActivity.GameState.PUSH_START -> drawPushStart(canvas, w, h, gameData)
            BobsledActivity.GameState.CONTROL_DESCENT -> drawWinterGamesSystem(canvas, w, h, gameData)
            BobsledActivity.GameState.FINISH_LINE -> drawFinishLine(canvas, w, h, gameData)
            BobsledActivity.GameState.CELEBRATION -> drawCelebration(canvas, w, h, gameData)
            BobsledActivity.GameState.RESULTS -> drawResults(canvas, w, h, gameData)
            BobsledActivity.GameState.FINISHED -> drawResults(canvas, w, h, gameData)
        }
    }
    
    // SYSTÈME WINTER GAMES AMÉLIORÉ
    private fun drawWinterGamesSystem(canvas: Canvas, w: Int, h: Int, gameData: GameData) {
        val trackStartY = h * 0.2f // 20% pour le décor
        
        // Mettre à jour tous les effets
        updateSpeedEffects(gameData.speed)
        updateLandscapeScrolling(gameData.speed)
        
        // 1. FOND SIMPLE AVEC EFFET DE TUNNEL
        drawSpeedBackground(canvas, w, h, trackStartY, gameData)
        
        // 2. PISTE AVEC FRAMES MULTIPLIÉES ET DÉCALÉES
        drawEnhancedTrackBackground(canvas, w, h, trackStartY, gameData)
        
        // 3. LIGNES DE VITESSE MASSIVES
        drawMassiveSpeedLines(canvas, w, h, trackStartY, gameData)
        
        // 4. BOBSLEIGH TRÈS VISIBLE AU PREMIER PLAN
        drawCenteredBobsled(canvas, w, h, trackStartY, gameData)
        
        // 5. SYMBOLES DE DIRECTION
        drawTurnIndicators(canvas, w, h, gameData)
        
        // 6. Interface vitesse
        drawInterface(canvas, w, h, gameData)
    }
    
    // NOUVEAU: Fond avec effet de tunnel de vitesse
    private fun drawSpeedBackground(canvas: Canvas, w: Int, h: Int, startY: Float, gameData: GameData) {
        val speedFactor = (gameData.speed / 150f).coerceIn(0f, 1f)
        
        // Ciel qui devient plus sombre avec la vitesse
        val skyColor = Color.rgb(
            (220 - speedFactor * 50).toInt(),
            (235 - speedFactor * 60).toInt(),
            (250 - speedFactor * 70).toInt()
        )
        paint.color = skyColor
        canvas.drawRect(0f, 0f, w.toFloat(), startY, paint)
        
        // Effet de tunnel concentrique pour la vitesse
        if (speedFactor > 0.3f) {
            val centerX = w / 2f
            val centerY = startY / 2f
            
            paint.style = Paint.Style.STROKE
            paint.color = Color.argb((speedFactor * 100).toInt(), 255, 255, 255)
            
            for (i in 1..8) {
                val radius = (tunnelEffect + i * 30f) % 200f + 20f
                paint.strokeWidth = (speedFactor * 8f).coerceAtMost(12f)
                canvas.drawCircle(centerX, centerY, radius, paint)
            }
            paint.style = Paint.Style.FILL
        }
        
        // Montagnes simples qui bougent
        val currentCurve = getCurrentTrackCurve(gameData)
        val mountainShift = currentCurve * w * 0.4f
        
        paint.color = Color.rgb(180, 190, 210)
        val mountains = Path().apply {
            moveTo(-mountainShift, startY)
            lineTo(w * 0.25f - mountainShift, startY * 0.3f)
            lineTo(w * 0.5f - mountainShift, startY * 0.1f)
            lineTo(w * 0.75f - mountainShift, startY * 0.4f)
            lineTo(w.toFloat() - mountainShift, startY * 0.2f)
            lineTo(w.toFloat(), startY)
            close()
        }
        canvas.drawPath(mountains, paint)
    }
    
    // NOUVEAU: Système de frames vraiment multipliées
    private fun drawEnhancedTrackBackground(canvas: Canvas, w: Int, h: Int, startY: Float, gameData: GameData) {
        updateTrackFrame(gameData)
        
        val currentFrame = getCurrentTrackFrame()
        val speedFactor = (gameData.speed / 150f).coerceIn(0f, 1f)
        val currentCurve = getCurrentTrackCurve(gameData)
        
        currentFrame?.let { frame ->
            
            // === VRAIE MULTIPLICATION AVEC DÉCALAGES VISIBLES ===
            val baseSpeed = speedFactor * 15f // Plus rapide
            
            // FRAME PRINCIPALE (toujours visible)
            val mainOffsetY = (frameTimer * baseSpeed) % 50f
            val dstRect1 = RectF(0f, startY + mainOffsetY, w.toFloat(), h.toFloat() + mainOffsetY)
            canvas.drawBitmap(frame, null, dstRect1, paint)
            
            // FRAME 2: Décalage Y différent
            paint.alpha = 180
            val offsetY2 = (frameTimer * baseSpeed * 1.3f) % 50f
            val offsetX2 = sin(frameTimer * 5f) * currentCurve * 8f
            val dstRect2 = RectF(offsetX2, startY + offsetY2, w.toFloat() + offsetX2, h.toFloat() + offsetY2)
            canvas.drawBitmap(frame, null, dstRect2, paint)
            
            if (speedFactor > 0.4f) {
                // FRAME 3: Décalage encore différent
                paint.alpha = 150
                val offsetY3 = (frameTimer * baseSpeed * 0.7f) % 50f
                val offsetX3 = cos(frameTimer * 3f) * currentCurve * -6f
                val dstRect3 = RectF(offsetX3, startY + offsetY3, w.toFloat() + offsetX3, h.toFloat() + offsetY3)
                canvas.drawBitmap(frame, null, dstRect3, paint)
                
                // FRAME 4: Sens inverse
                paint.alpha = 120
                val offsetY4 = (frameTimer * baseSpeed * -0.5f) % 50f + 50f
                val offsetX4 = sin(frameTimer * 7f) * currentCurve * 4f
                val dstRect4 = RectF(offsetX4, startY + offsetY4, w.toFloat() + offsetX4, h.toFloat() + offsetY4)
                canvas.drawBitmap(frame, null, dstRect4, paint)
            }
            
            if (speedFactor > 0.7f) {
                // FRAME 5: Ultra-rapide
                paint.alpha = 100
                val offsetY5 = (frameTimer * baseSpeed * 2f) % 50f
                val offsetX5 = cos(frameTimer * 12f) * currentCurve * 10f
                val dstRect5 = RectF(offsetX5, startY + offsetY5, w.toFloat() + offsetX5, h.toFloat() + offsetY5)
                canvas.drawBitmap(frame, null, dstRect5, paint)
                
                // FRAME 6: Micro-décalages
                paint.alpha = 80
                val offsetY6 = (frameTimer * baseSpeed * 0.3f) % 50f
                val offsetX6 = sin(frameTimer * 20f) * 3f
                val dstRect6 = RectF(offsetX6, startY + offsetY6, w.toFloat() + offsetX6, h.toFloat() + offsetY6)
                canvas.drawBitmap(frame, null, dstRect6, paint)
            }
            
            paint.alpha = 255 // Remettre opaque
            
        } ?: run {
            // Fallback: piste procédurale animée
            drawProceduralTrack(canvas, w, h, startY, gameData)
        }
    }
    
    // NOUVEAU: Piste procédurale si pas d'images
    private fun drawProceduralTrack(canvas: Canvas, w: Int, h: Int, startY: Float, gameData: GameData) {
        val speedFactor = (gameData.speed / 150f).coerceIn(0f, 1f)
        val currentCurve = getCurrentTrackCurve(gameData)
        
        // Fond de piste qui défile
        val scrollOffset = (speedLineOffset * speedFactor * 3f) % 40f
        
        for (i in 0..20) {
            val y = startY + (h - startY) * i / 20f - scrollOffset
            if (y > startY && y < h + 40f) {
                val perspective = (y - startY) / (h - startY)
                val trackWidth = w * (0.3f + perspective * 0.5f)
                val centerX = w / 2f + currentCurve * w * 0.2f * perspective
                
                // Couleur de la piste
                val gray = (160 + i % 3 * 20).coerceAtMost(255)
                paint.color = Color.rgb(gray, gray, gray)
                
                canvas.drawRect(
                    centerX - trackWidth / 2f,
                    y,
                    centerX + trackWidth / 2f,
                    y + 20f,
                    paint
                )
                
                // Lignes blanches
                if (i % 4 == 0) {
                    paint.color = Color.WHITE
                    canvas.drawRect(
                        centerX - 4f,
                        y,
                        centerX + 4f,
                        y + 20f,
                        paint
                    )
                }
            }
        }
    }
    
    // NOUVEAU: Lignes de vitesse massives et visibles
    private fun drawMassiveSpeedLines(canvas: Canvas, w: Int, h: Int, startY: Float, gameData: GameData) {
        val speedFactor = (gameData.speed / 150f).coerceIn(0f, 1f)
        
        if (speedFactor < 0.3f) return // Pas de lignes si trop lent
        
        val currentCurve = getCurrentTrackCurve(gameData)
        
        // LIGNES HORIZONTALES MASSIVES
        paint.strokeWidth = (4f + speedFactor * 8f).coerceAtMost(15f)
        paint.style = Paint.Style.STROKE
        paint.color = Color.argb((speedFactor * 150).toInt(), 255, 255, 255)
        
        val lineSpeed = speedFactor * 20f
        val scrollOffset = (speedLineOffset * lineSpeed) % 80f
        
        for (i in 0..15) {
            val lineY = startY + (h - startY) * i / 15f - scrollOffset + (i * 8f)
            val perspective = (lineY - startY) / (h - startY)
            
            if (lineY > startY && lineY < h) {
                val lineWidth = w * (0.2f + perspective * 0.6f)
                val centerX = w / 2f + currentCurve * w * 0.3f * perspective
                
                // Ligne principale
                canvas.drawLine(
                    centerX - lineWidth / 2f,
                    lineY,
                    centerX + lineWidth / 2f,
                    lineY,
                    paint
                )
                
                // Lignes latérales pour effet tunnel
                if (speedFactor > 0.6f) {
                    paint.alpha = (speedFactor * 80).toInt()
                    canvas.drawLine(
                        centerX - lineWidth / 2f - 20f,
                        lineY,
                        centerX - lineWidth / 2f,
                        lineY,
                        paint
                    )
                    canvas.drawLine(
                        centerX + lineWidth / 2f,
                        lineY,
                        centerX + lineWidth / 2f + 20f,
                        lineY,
                        paint
                    )
                    paint.alpha = (speedFactor * 150).toInt()
                }
            }
        }
        
        // LIGNES DIAGONALES POUR ULTRA-VITESSE
        if (speedFactor > 0.8f) {
            paint.strokeWidth = 3f
            paint.color = Color.argb((speedFactor * 100).toInt(), 255, 255, 0)
            
            for (i in 0..10) {
                val angle = (speedLineOffset * 8f + i * 36f) % 360f
                val startX = w / 2f + cos(Math.toRadians(angle.toDouble())).toFloat() * w * 0.3f
                val startY2 = startY + sin(Math.toRadians(angle.toDouble())).toFloat() * (h - startY) * 0.3f
                val endX = w / 2f + cos(Math.toRadians(angle.toDouble())).toFloat() * w * 0.5f
                val endY = startY + sin(Math.toRadians(angle.toDouble())).toFloat() * (h - startY) * 0.5f
                
                canvas.drawLine(startX, startY2, endX, endY, paint)
            }
        }
        
        paint.style = Paint.Style.FILL
        paint.alpha = 255
    }
    
    // Bobsleigh visible (taille originale)
    private fun drawCenteredBobsled(canvas: Canvas, w: Int, h: Int, trackStartY: Float, gameData: GameData) {
        val baseBobX = w / 2f
        val baseBobY = trackStartY + (h - trackStartY) * 0.75f
        val bobScale = 0.3f // Taille originale
        
        val currentCurve = getCurrentTrackCurve(gameData)
        
        var bobOffsetX = 0f
        var bobRotation = 0f
        
        when {
            currentCurve < -0.6f -> {
                bobOffsetX = w * 0.08f
                bobRotation = -20f
            }
            currentCurve < -0.3f -> {
                bobOffsetX = w * 0.05f
                bobRotation = -10f
            }
            currentCurve > 0.6f -> {
                bobOffsetX = -w * 0.08f
                bobRotation = 20f
            }
            currentCurve > 0.3f -> {
                bobOffsetX = -w * 0.05f
                bobRotation = 10f
            }
        }
        
        bobRotation += (gameData.tiltZ * 10f).coerceIn(-30f, 30f)
        
        val bobX = baseBobX + bobOffsetX
        val bobY = baseBobY
        
        val bobSprite = when {
            currentCurve < -0.3f -> bobLeftBitmap
            currentCurve > 0.3f -> bobRightBitmap
            else -> bobStraightBitmap
        }
        
        // OMBRE PLUS VISIBLE
        paint.color = Color.argb(180, 0, 0, 0)
        canvas.drawOval(bobX - 30f, bobY + 20f, bobX + 30f, bobY + 32f, paint)
        
        bobSprite?.let { bmp ->
            val dstRect = RectF(
                bobX - bmp.width * bobScale / 2f,
                bobY - bmp.height * bobScale / 2f,
                bobX + bmp.width * bobScale / 2f,
                bobY + bmp.height * bobScale / 2f
            )
            
            if (abs(bobRotation) > 3f) {
                canvas.save()
                canvas.rotate(bobRotation, bobX, bobY)
            }
            
            // CONTOUR NOIR ÉPAIS pour visibilité
            paint.color = Color.BLACK
            paint.strokeWidth = 6f
            paint.style = Paint.Style.STROKE
            canvas.drawRoundRect(
                dstRect.left - 3f, dstRect.top - 3f, 
                dstRect.right + 3f, dstRect.bottom + 3f, 
                8f, 8f, paint
            )
            paint.style = Paint.Style.FILL
            
            // Image du bobsleigh
            canvas.drawBitmap(bmp, null, dstRect, paint)
            
            if (abs(bobRotation) > 3f) {
                canvas.restore()
            }
            
        } ?: run {
            // Fallback avec contour très visible
            if (abs(bobRotation) > 3f) {
                canvas.save()
                canvas.rotate(bobRotation, bobX, bobY)
            }
            
            // Contour noir épais
            paint.color = Color.BLACK
            canvas.drawRoundRect(bobX - 33f, bobY - 21f, bobX + 33f, bobY + 21f, 8f, 8f, paint)
            
            // Bobsleigh coloré
            paint.color = when {
                currentCurve < -0.3f -> Color.GREEN
                currentCurve > 0.3f -> Color.BLUE
                else -> Color.YELLOW
            }
            canvas.drawRoundRect(bobX - 30f, bobY - 18f, bobX + 30f, bobY + 18f, 8f, 8f, paint)
            
            if (abs(bobRotation) > 3f) {
                canvas.restore()
            }
        }
    }
    
    // Mettre à jour les effets de vitesse
    private fun updateSpeedEffects(speed: Float) {
        val speedMultiplier = (speed / 150f).coerceIn(0f, 1f)
        speedLineOffset += speedMultiplier * 0.5f
        tunnelEffect += speedMultiplier * 3f
        
        if (speedLineOffset > 1000f) speedLineOffset -= 1000f
        if (tunnelEffect > 1000f) tunnelEffect -= 1000f
    }
    
    private fun updateLandscapeScrolling(speed: Float) {
        val scrollSpeed = speed * 0.02f
        landscapeOffset += scrollSpeed
        if (landscapeOffset > 1000f) landscapeOffset -= 1000f
    }
    
    private fun updateTrackFrame(gameData: GameData) {
        // FLUIDITÉ EXTRÊME avec changement de frames plus rapide
        val frameSpeed = when {
            gameData.speed > 120f -> 0.012f  // Plus rapide pour fluidité
            gameData.speed > 80f -> 0.016f   
            gameData.speed > 40f -> 0.025f   
            else -> 0.04f          
        }
        
        frameTimer += frameSpeed
        
        if (frameTimer >= 1f) {
            frameTimer = 0f
            
            val currentCurve = getCurrentTrackCurve(gameData)
            
            when {
                abs(currentCurve) < 0.3f -> {
                    trackSection = TrackSection.STRAIGHT
                    // Pattern pour varier les 2 images disponibles
                    val speedVariation = (gameData.speed * 0.1f).toInt() % 8
                    currentFrameIndex = if (speedVariation < 4) 0 else 1
                    isReversing = false
                }
                
                currentCurve < -0.3f -> {
                    if (trackSection != TrackSection.LEFT_TURN && trackSection != TrackSection.LEFT_RETURN) {
                        trackSection = TrackSection.LEFT_TURN
                        currentFrameIndex = 2
                        isReversing = false
                    }
                    
                    if (trackSection == TrackSection.LEFT_TURN && !isReversing) {
                        currentFrameIndex++
                        if (currentFrameIndex >= totalFrames - 1) {
                            trackSection = TrackSection.LEFT_RETURN
                            isReversing = true
                        }
                    } else if (trackSection == TrackSection.LEFT_RETURN && isReversing) {
                        currentFrameIndex--
                        if (currentFrameIndex <= 1) {
                            trackSection = TrackSection.STRAIGHT
                            currentFrameIndex = 0
                            isReversing = false
                        }
                    }
                }
                
                currentCurve > 0.3f -> {
                    if (trackSection != TrackSection.RIGHT_TURN && trackSection != TrackSection.RIGHT_RETURN) {
                        trackSection = TrackSection.RIGHT_TURN
                        currentFrameIndex = 2
                        isReversing = false
                    }
                    
                    if (trackSection == TrackSection.RIGHT_TURN && !isReversing) {
                        currentFrameIndex++
                        if (currentFrameIndex >= totalFrames - 1) {
                            trackSection = TrackSection.RIGHT_RETURN
                            isReversing = true
                        }
                    } else if (trackSection == TrackSection.RIGHT_RETURN && isReversing) {
                        currentFrameIndex--
                        if (currentFrameIndex <= 1) {
                            trackSection = TrackSection.STRAIGHT
                            currentFrameIndex = 0
                            isReversing = false
                        }
                    }
                }
            }
        }
    }
    
    // SYMBOLES DE DIRECTION ÉNORMES ET CLAIRS
    private fun drawTurnIndicators(canvas: Canvas, w: Int, h: Int, gameData: GameData) {
        val currentCurve = getCurrentTrackCurve(gameData)
        
        // Seulement si il y a un virage
        if (abs(currentCurve) > 0.2f) {
            // Fond semi-transparent
            paint.color = Color.argb(180, 0, 0, 0)
            canvas.drawRoundRect(w/8f, h * 0.2f, w*7f/8f, h * 0.45f, 25f, 25f, paint)
            
            // Symbole ÉNORME selon l'intensité
            paint.textSize = 200f
            paint.textAlign = Paint.Align.CENTER
            paint.color = Color.WHITE
            
            val directionSymbol = when {
                currentCurve < -0.6f -> "⬅️🔴" // FORT gauche
                currentCurve < -0.3f -> "⬅️🟡" // MOYEN gauche
                currentCurve > 0.6f -> "🔴➡️"  // FORT droite
                currentCurve > 0.3f -> "🟡➡️"  // MOYEN droite
                else -> ""
            }
            
            canvas.drawText(directionSymbol, w/2f, h * 0.35f, paint)
            
            // Performance avec couleur selon la qualité
            paint.textSize = 120f
            paint.color = when {
                gameData.playerReactionAccuracy > 0.8f -> Color.GREEN
                gameData.playerReactionAccuracy > 0.6f -> Color.YELLOW
                else -> Color.RED
            }
            canvas.drawText("${(gameData.playerReactionAccuracy * 100).toInt()}%", w/2f, h * 0.42f, paint)
        }
    }
    
    private fun getCurrentTrackFrame(): Bitmap? {
        return when (trackSection) {
            TrackSection.STRAIGHT -> {
                getTrackSpriteFrame(currentFrameIndex, false, false)
            }
            TrackSection.LEFT_TURN, TrackSection.LEFT_RETURN -> {
                getTrackSpriteFrame(currentFrameIndex, false, false)
            }
            TrackSection.RIGHT_TURN, TrackSection.RIGHT_RETURN -> {
                getTrackSpriteFrame(currentFrameIndex, true, false)
            }
        }
    }
    
    // HELPER: Obtenir la courbe actuelle (évite les recalculs)
    private fun getCurrentTrackCurve(gameData: GameData): Float {
        val trackIndex = (gameData.trackPosition * (gameData.trackCurves.size - 1)).toInt()
        return if (trackIndex < gameData.trackCurves.size) gameData.trackCurves[trackIndex] else 0f
    }
    
    private fun drawInterface(canvas: Canvas, w: Int, h: Int, gameData: GameData) {
        paint.color = Color.BLACK
        paint.textSize = 60f
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText("${gameData.speed.toInt()} KM/H", 30f, 80f, paint)
    }
    
    private fun drawFinishLine(canvas: Canvas, w: Int, h: Int, gameData: GameData) {
        drawWinterGamesSystem(canvas, w, h, gameData)
        
        paint.color = Color.YELLOW
        paint.textSize = 80f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("🏁 FINISH! 🏁", w/2f, h * 0.3f, paint)
    }
    
    private fun drawPreparation(canvas: Canvas, w: Int, h: Int, gameData: GameData) {
        bobsledPreparationBitmap?.let { bmp ->
            val dstRect = RectF(0f, 0f, w.toFloat(), h.toFloat())
            canvas.drawBitmap(bmp, null, dstRect, paint)
        } ?: run {
            paint.color = Color.parseColor("#E0F6FF")
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }
        
        paint.color = Color.WHITE
        val flagRect = RectF(50f, 50f, 300f, 200f)
        canvas.drawRect(flagRect, paint)
        
        paint.color = Color.BLACK
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawRect(flagRect, paint)
        paint.style = Paint.Style.FILL
        
        val playerCountry = if (gameData.practiceMode) {
            "CANADA"
        } else {
            gameData.tournamentData.playerCountries[gameData.currentPlayerIndex]
        }
        
        val flagBitmap = when (playerCountry.uppercase()) {
            "CANADA" -> flagCanadaBitmap
            "USA" -> flagUsaBitmap
            "FRANCE" -> flagFranceBitmap
            "NORVÈGE" -> flagNorvegeBitmap
            "JAPON" -> flagJapanBitmap
            else -> flagCanadaBitmap
        }
        
        flagBitmap?.let { flag ->
            val targetWidth = flagRect.width() * 0.8f
            val targetHeight = flagRect.height() * 0.8f
            
            val centerX = flagRect.centerX()
            val centerY = flagRect.centerY()
            
            val flagImageRect = RectF(
                centerX - targetWidth / 2f,
                centerY - targetHeight / 2f,
                centerX + targetWidth / 2f,
                centerY + targetHeight / 2f
            )
            
            canvas.drawBitmap(flag, null, flagImageRect, paint)
        } ?: run {
            val flag = getCountryFlag(playerCountry)
            paint.color = Color.BLACK
            paint.textSize = 100f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(flag, flagRect.centerX(), flagRect.centerY() + 30f, paint)
        }
        
        paint.color = Color.BLACK
        paint.textSize = 28f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(playerCountry.uppercase(), flagRect.centerX(), flagRect.bottom + 40f, paint)
        
        paint.textSize = 56f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("🛷 BOBSLEIGH 🛷", w/2f, h * 0.4f, paint)
        
        paint.textSize = 40f
        canvas.drawText("L'équipe se prépare...", w/2f, h * 0.47f, paint)
        
        paint.textSize = 36f
        paint.color = Color.YELLOW
        canvas.drawText("Dans ${(gameData.preparationDuration - gameData.phaseTimer).toInt() + 1} secondes", w/2f, h * 0.55f, paint)
    }
    
    private fun drawPushStart(canvas: Canvas, w: Int, h: Int, gameData: GameData) {
        paint.color = Color.rgb(150, 200, 255)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        
        paint.color = Color.rgb(100, 100, 100)
        val mountainPath = Path().apply {
            moveTo(0f, h * 0.4f)
            lineTo(w * 0.3f, h * 0.2f)
            lineTo(w * 0.7f, h * 0.25f)
            lineTo(w.toFloat(), h * 0.35f)
            lineTo(w.toFloat(), h * 0.4f)
            close()
        }
        canvas.drawPath(mountainPath, paint)
        
        paint.color = Color.WHITE
        val trackY = h * 0.65f
        canvas.drawRect(50f, trackY - 40f, w - 50f, trackY + 40f, paint)
        
        paint.color = Color.GRAY
        paint.strokeWidth = 4f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(50f, trackY - 40f, w - 50f, trackY - 40f, paint)
        canvas.drawLine(50f, trackY + 40f, w - 50f, trackY + 40f, paint)
        paint.style = Paint.Style.FILL
        
        paint.color = Color.RED
        paint.strokeWidth = 6f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(100f, trackY - 50f, 100f, trackY + 50f, paint)
        paint.style = Paint.Style.FILL
        
        val bobX = w / 2f
        val bobY = trackY
        
        val powerProgress = (gameData.pushPower / 150f).coerceIn(0f, 1f)
        val circleRadius = 100f + powerProgress * 20f
        
        val circleColor = when {
            powerProgress > 0.8f -> Color.argb(100, 255, 0, 0)
            powerProgress > 0.5f -> Color.argb(100, 255, 165, 0)
            powerProgress > 0.2f -> Color.argb(100, 255, 255, 0)
            else -> Color.argb(100, 0, 255, 0)
        }
        
        paint.color = circleColor
        canvas.drawCircle(bobX, bobY, circleRadius, paint)
        
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawCircle(bobX, bobY, circleRadius, paint)
        paint.style = Paint.Style.FILL
        
        bobPushBitmap?.let { bmp ->
            val scale = 0.3f
            val dstRect = RectF(
                bobX - bmp.width * scale / 2f,
                bobY - bmp.height * scale / 2f,
                bobX + bmp.width * scale / 2f,
                bobY + bmp.height * scale / 2f
            )
            canvas.drawBitmap(bmp, null, dstRect, paint)
        } ?: run {
            paint.color = Color.RED
            canvas.drawRoundRect(bobX - 40f, bobY - 20f, bobX + 40f, bobY + 20f, 8f, 8f, paint)
        }
        
        paint.color = Color.argb(200, 0, 0, 0)
        canvas.drawRoundRect(w/2f - 300f, 80f, w/2f + 300f, 160f, 10f, 10f, paint)
        
        paint.color = Color.WHITE
        paint.textSize = 60f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("MAINTENEZ LE CERCLE POUR CHARGER", w/2f, 130f, paint)
        
        paint.color = Color.argb(200, 0, 0, 0)
        canvas.drawRoundRect(w/2f - 250f, h - 150f, w/2f + 250f, h - 40f, 10f, 10f, paint)
        
        paint.color = Color.GRAY
        canvas.drawRect(w/2f - 230f, h - 120f, w/2f + 230f, h - 80f, paint)
        
        val barColor = when {
            powerProgress > 0.8f -> Color.RED
            powerProgress > 0.5f -> Color.rgb(255, 165, 0)
            else -> Color.GREEN
        }
        paint.color = barColor
        val powerWidth = powerProgress * 460f
        canvas.drawRect(w/2f - 230f, h - 120f, w/2f - 230f + powerWidth, h - 80f, paint)
        
        paint.color = Color.WHITE
        paint.textSize = 50f
        canvas.drawText("PUISSANCE: ${gameData.pushPower.toInt()}%", w/2f, h - 50f, paint)
        
        paint.color = Color.argb(200, 255, 0, 0)
        canvas.drawRoundRect(w - 100f, 40f, w - 20f, 120f, 10f, 10f, paint)
        
        paint.textSize = 40f
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("${(gameData.pushStartDuration - gameData.phaseTimer).toInt() + 1}s", w - 60f, 90f, paint)
    }
    
    private fun drawCelebration(canvas: Canvas, w: Int, h: Int, gameData: GameData) {
        paint.color = Color.parseColor("#FFD700")
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        
        val progress = gameData.phaseTimer / gameData.celebrationDuration
        
        val bobCelebrationX = if (progress < 0.7f) {
            val moveProgress = (progress / 0.7f)
            val easedProgress = 1f - (1f - moveProgress) * (1f - moveProgress)
            -200f + easedProgress * (w/2f + 200f)
        } else {
            w/2f
        }
        
        val bobCelebrationY = h/2f
        
        for (i in 0..15) {
            val angle = (2.0 * PI / 15 * i + progress * 6).toFloat()
            val radius = progress * 200f
            val particleX = bobCelebrationX + cos(angle) * radius
            val particleY = bobCelebrationY + sin(angle) * radius
            
            paint.color = Color.WHITE
            canvas.drawCircle(particleX, particleY, 6f, paint)
        }
        
        bobCelebrationBitmap?.let { bmp ->
            val scale = 0.4f
            val dstRect = RectF(
                bobCelebrationX - bmp.width * scale / 2f,
                bobCelebrationY - bmp.height * scale / 2f,
                bobCelebrationX + bmp.width * scale / 2f,
                bobCelebrationY + bmp.height * scale / 2f
            )
            canvas.drawBitmap(bmp, null, dstRect, paint)
        }
        
        paint.color = Color.BLACK
        paint.textSize = 60f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("🎉 BRAVO! 🎉", w/2f, 150f, paint)
        
        paint.textSize = 40f
        canvas.drawText("Temps: ${gameData.raceTime.toInt()}s", w/2f, h - 100f, paint)
        canvas.drawText("Vitesse moy: ${gameData.speed.toInt()} km/h", w/2f, h - 50f, paint)
    }
    
    private fun drawResults(canvas: Canvas, w: Int, h: Int, gameData: GameData) {
        paint.color = Color.parseColor("#E0F6FF")
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        
        paint.color = Color.parseColor("#FFD700")
        canvas.drawRect(0f, 0f, w.toFloat(), h * 0.4f, paint)
        
        paint.color = Color.BLACK
        paint.textSize = 120f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("${gameData.finalScore}", w/2f, h * 0.25f, paint)
        
        paint.textSize = 50f
        canvas.drawText("POINTS", w/2f, h * 0.35f, paint)
        
        paint.color = Color.parseColor("#001122")
        paint.textSize = 48f
        canvas.drawText("🚀 Poussée: ${(gameData.pushQuality * 100).toInt()}%", w/2f, h * 0.5f, paint)
        canvas.drawText("🎮 Réflexes: ${(gameData.playerReactionAccuracy * 100).toInt()}%", w/2f, h * 0.56f, paint)
        canvas.drawText("🏆 Virages parfaits: ${gameData.perfectTurns}", w/2f, h * 0.62f, paint)
        canvas.drawText("🕒 Temps: ${gameData.raceTime.toInt()}s", w/2f, h * 0.68f, paint)
        canvas.drawText("⚡ Vitesse moy: ${gameData.speed.toInt()} km/h", w/2f, h * 0.74f, paint)
        canvas.drawText("💥 Impacts murs: ${gameData.wallHits}", w/2f, h * 0.8f, paint)
    }
    
    private fun getCountryFlag(country: String): String {
        return when (country.uppercase()) {
            "CANADA" -> "🇨🇦"
            "FRANCE" -> "🇫🇷"
            "USA" -> "🇺🇸"
            "NORVÈGE" -> "🇳🇴"
            "JAPON" -> "🇯🇵"
            else -> "🏴"
        }
    }
}
