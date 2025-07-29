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
    
    // Variables pour effets de vitesse SIMPLES
    private var scrollOffset = 0f
    
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
                    val bobX = width / 2f
                    val bobY = height * 0.65f
                    val touchRadius = 120f
                    
                    val touchX = event.x
                    val touchY = event.y
                    val distance = sqrt((touchX - bobX).pow(2) + (touchY - bobY).pow(2))
                    
                    if (distance <= touchRadius) {
                        activity.updatePushPower(8f)
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
            BobsledActivity.GameState.CONTROL_DESCENT -> drawDescentSystem(canvas, w, h, gameData)
            BobsledActivity.GameState.FINISH_LINE -> drawFinishLine(canvas, w, h, gameData)
            BobsledActivity.GameState.CELEBRATION -> drawCelebration(canvas, w, h, gameData)
            BobsledActivity.GameState.RESULTS -> drawResults(canvas, w, h, gameData)
            BobsledActivity.GameState.FINISHED -> drawResults(canvas, w, h, gameData)
        }
    }
    
    // SYSTÈME DE DESCENTE PROPRE ET EFFICACE
    private fun drawDescentSystem(canvas: Canvas, w: Int, h: Int, gameData: GameData) {
        val trackStartY = h * 0.15f
        
        // Mettre à jour le défilement
        updateScrolling(gameData.speed)
        
        // 1. CIEL ET MONTAGNES SIMPLES
        drawBackground(canvas, w, trackStartY.toInt(), gameData)
        
        // 2. PISTE AVEC VRAIE MULTIPLICATION DES FRAMES
        drawTrackWithMultipleFrames(canvas, w, h, trackStartY, gameData)
        
        // 3. BOBSLEIGH VISIBLE PAR-DESSUS TOUT
        drawBobsled(canvas, w, h, trackStartY, gameData)
        
        // 4. INTERFACE
        drawInterface(canvas, w, h, gameData)
    }
    
    // Fond simple et efficace
    private fun drawBackground(canvas: Canvas, w: Int, horizonHeight: Int, gameData: GameData) {
        // Ciel
        paint.color = Color.rgb(220, 235, 250)
        canvas.drawRect(0f, 0f, w.toFloat(), horizonHeight.toFloat(), paint)
        
        // Montagnes qui bougent selon les virages
        val currentCurve = getCurrentTrackCurve(gameData)
        val mountainShift = currentCurve * w * 0.3f
        
        paint.color = Color.rgb(180, 190, 210)
        val mountains = Path().apply {
            moveTo(-mountainShift, horizonHeight.toFloat())
            lineTo(w * 0.3f - mountainShift, horizonHeight * 0.2f)
            lineTo(w * 0.7f - mountainShift, horizonHeight * 0.4f)
            lineTo(w.toFloat() - mountainShift, horizonHeight * 0.3f)
            lineTo(w.toFloat(), horizonHeight.toFloat())
            close()
        }
        canvas.drawPath(mountains, paint)
    }
    
    // MEILLEURE UTILISATION DES 9 FRAMES + EFFET DE VITESSE
    private fun drawTrackWithMultipleFrames(canvas: Canvas, w: Int, h: Int, startY: Float, gameData: GameData) {
        updateTrackFrame(gameData)
        
        val speedFactor = (gameData.speed / 150f).coerceIn(0f, 1f)
        val currentCurve = getCurrentTrackCurve(gameData)
        
        // === SYSTÈME AMÉLIORÉ: UTILISER MIEUX LES FRAMES ===
        
        when (trackSection) {
            TrackSection.STRAIGHT -> {
                // LIGNES DROITES: Alterner entre frames 0 et 1 + leurs copies
                val frame1 = getTrackSpriteFrame(0, false, false)
                val frame2 = getTrackSpriteFrame(1, false, false)
                
                frame1?.let { f1 ->
                    // Frame 1 à position normale
                    val offset1 = scrollOffset % 80f
                    val dstRect1 = RectF(0f, startY + offset1, w.toFloat(), h.toFloat() + offset1)
                    canvas.drawBitmap(f1, null, dstRect1, paint)
                    
                    // Frame 1 décalée
                    val offset1b = (scrollOffset + 40f) % 80f
                    val dstRect1b = RectF(0f, startY + offset1b, w.toFloat(), h.toFloat() + offset1b)
                    paint.alpha = 180
                    canvas.drawBitmap(f1, null, dstRect1b, paint)
                    paint.alpha = 255
                }
                
                frame2?.let { f2 ->
                    // Frame 2 à position intermédiaire
                    val offset2 = (scrollOffset + 20f) % 80f
                    val dstRect2 = RectF(0f, startY + offset2, w.toFloat(), h.toFloat() + offset2)
                    paint.alpha = 200
                    canvas.drawBitmap(f2, null, dstRect2, paint)
                    
                    // Frame 2 décalée
                    val offset2b = (scrollOffset + 60f) % 80f
                    val dstRect2b = RectF(0f, startY + offset2b, w.toFloat(), h.toFloat() + offset2b)
                    paint.alpha = 150
                    canvas.drawBitmap(f2, null, dstRect2b, paint)
                    paint.alpha = 255
                }
            }
            
            else -> {
                // VIRAGES: Copier et décaler les frames de virage
                val currentFrame = getCurrentTrackFrame()
                
                currentFrame?.let { frame ->
                    // Frame principale
                    val offset1 = scrollOffset % 60f
                    val offsetX1 = currentCurve * 8f
                    val dstRect1 = RectF(offsetX1, startY + offset1, w.toFloat() + offsetX1, h.toFloat() + offset1)
                    canvas.drawBitmap(frame, null, dstRect1, paint)
                    
                    // Copie décalée Y
                    val offset2 = (scrollOffset + 30f) % 60f
                    val offsetX2 = currentCurve * -5f
                    val dstRect2 = RectF(offsetX2, startY + offset2, w.toFloat() + offsetX2, h.toFloat() + offset2)
                    paint.alpha = 170
                    canvas.drawBitmap(frame, null, dstRect2, paint)
                    
                    // Copie avec vitesse différente
                    if (speedFactor > 0.4f) {
                        val offset3 = (scrollOffset * 1.4f) % 60f
                        val offsetX3 = currentCurve * 12f
                        val dstRect3 = RectF(offsetX3, startY + offset3, w.toFloat() + offsetX3, h.toFloat() + offset3)
                        paint.alpha = 130
                        canvas.drawBitmap(frame, null, dstRect3, paint)
                    }
                    
                    paint.alpha = 255
                }
            }
        }
        
        // LIGNES DE VITESSE AU SOL qui suivent la piste
        drawSpeedLines(canvas, w, h, startY, gameData)
    }
    
    // Piste simple si pas d'images sprite
    private fun drawSimpleTrack(canvas: Canvas, w: Int, h: Int, startY: Float, gameData: GameData) {
        val currentCurve = getCurrentTrackCurve(gameData)
        val trackOffset = scrollOffset % 30f
        
        for (i in 0..25) {
            val y = startY + (h - startY) * i / 25f - trackOffset
            if (y > startY && y < h + 30f) {
                val perspective = (y - startY) / (h - startY)
                val trackWidth = w * (0.4f + perspective * 0.4f)
                val centerX = w / 2f + currentCurve * w * 0.15f * perspective
                
                // Piste grise
                paint.color = Color.rgb(160, 160, 160)
                canvas.drawRect(
                    centerX - trackWidth / 2f,
                    y,
                    centerX + trackWidth / 2f,
                    y + 15f,
                    paint
                )
                
                // Ligne centrale blanche
                if (i % 3 == 0) {
                    paint.color = Color.WHITE
                    canvas.drawRect(
                        centerX - 3f,
                        y,
                        centerX + 3f,
                        y + 15f,
                        paint
                    )
                }
            }
        }
    }
    
    // Bobsleigh simple et visible
    private fun drawBobsled(canvas: Canvas, w: Int, h: Int, trackStartY: Float, gameData: GameData) {
        val bobX = w / 2f
        val bobY = trackStartY + (h - trackStartY) * 0.7f
        val bobScale = 0.3f
        
        val currentCurve = getCurrentTrackCurve(gameData)
        
        val bobSprite = when {
            currentCurve < -0.3f -> bobLeftBitmap
            currentCurve > 0.3f -> bobRightBitmap
            else -> bobStraightBitmap
        }
        
        // Ombre
        paint.color = Color.argb(150, 0, 0, 0)
        canvas.drawOval(bobX - 25f, bobY + 15f, bobX + 25f, bobY + 25f, paint)
        
        bobSprite?.let { bmp ->
            val dstRect = RectF(
                bobX - bmp.width * bobScale / 2f,
                bobY - bmp.height * bobScale / 2f,
                bobX + bmp.width * bobScale / 2f,
                bobY + bmp.height * bobScale / 2f
            )
            
            // Contour noir pour visibilité
            paint.color = Color.BLACK
            paint.strokeWidth = 4f
            paint.style = Paint.Style.STROKE
            canvas.drawRoundRect(
                dstRect.left - 2f, dstRect.top - 2f,
                dstRect.right + 2f, dstRect.bottom + 2f,
                6f, 6f, paint
            )
            paint.style = Paint.Style.FILL
            
            // Bobsleigh
            canvas.drawBitmap(bmp, null, dstRect, paint)
            
        } ?: run {
            // Fallback coloré
            paint.color = Color.BLACK
            canvas.drawRoundRect(bobX - 32f, bobY - 17f, bobX + 32f, bobY + 17f, 6f, 6f, paint)
            
            paint.color = when {
                currentCurve < -0.3f -> Color.GREEN
                currentCurve > 0.3f -> Color.BLUE
                else -> Color.YELLOW
            }
            canvas.drawRoundRect(bobX - 30f, bobY - 15f, bobX + 30f, bobY + 15f, 6f, 6f, paint)
        }
    }
    
    // Mettre à jour le défilement simple
    private fun updateScrolling(speed: Float) {
        val scrollSpeed = (speed / 150f).coerceIn(0f, 1f) * 8f
        scrollOffset += scrollSpeed
        if (scrollOffset > 200f) scrollOffset -= 200f
    }
    
    private fun updateTrackFrame(gameData: GameData) {
        val frameSpeed = when {
            gameData.speed > 100f -> 0.015f
            gameData.speed > 60f -> 0.025f
            else -> 0.04f
        }
        
        frameTimer += frameSpeed
        
        if (frameTimer >= 1f) {
            frameTimer = 0f
            
            val currentCurve = getCurrentTrackCurve(gameData)
            
            when {
                abs(currentCurve) < 0.3f -> {
                    trackSection = TrackSection.STRAIGHT
                    currentFrameIndex = (currentFrameIndex + 1) % 2 // Alterne entre 0 et 1
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
        drawDescentSystem(canvas, w, h, gameData)
        
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
