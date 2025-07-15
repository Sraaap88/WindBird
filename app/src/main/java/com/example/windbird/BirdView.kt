package com.example.windbird

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class BirdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private var windForce = 0f
    
    private val bodyPaint = Paint().apply {
        color = Color.rgb(139, 69, 19)
        isAntiAlias = true
    }
    
    private val eyePaint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = true
    }
    
    private val beakPaint = Paint().apply {
        color = Color.rgb(255, 140, 0)
        isAntiAlias = true
    }
    
    fun updateWindForce(force: Float) {
        windForce = force
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f
        val centerY = height / 2f
        val size = width * 0.3f
        
        // Corps de l'oiseau
        canvas.drawCircle(centerX, centerY, size * 0.4f, bodyPaint)
        
        // Tête
        canvas.drawCircle(centerX, centerY - size * 0.3f, size * 0.25f, bodyPaint)
        
        // Yeux (qui se plissent avec le vent)
        val eyeSize = size * 0.05f * (1f - windForce * 0.7f)
        canvas.drawCircle(centerX - size * 0.1f, centerY - size * 0.35f, eyeSize, eyePaint)
        canvas.drawCircle(centerX + size * 0.1f, centerY - size * 0.35f, eyeSize, eyePaint)
        
        // Bec
        val beakPath = Path()
        val beakY = centerY - size * 0.2f
        beakPath.moveTo(centerX, beakY)
        beakPath.lineTo(centerX - size * 0.08f, beakY + size * 0.06f)
        beakPath.lineTo(centerX + size * 0.08f, beakY + size * 0.06f)
        beakPath.close()
        canvas.drawPath(beakPath, beakPaint)
        
        // Corps qui penche avec le vent
        canvas.save()
        canvas.rotate(windForce * 15f, centerX, centerY)
        
        // Branche
        val branchPaint = Paint().apply {
            color = Color.rgb(101, 67, 33)
            strokeWidth = 10f
        }
        canvas.drawLine(0f, centerY + size * 0.5f, width.toFloat(), centerY + size * 0.5f, branchPaint)
        
        canvas.restore()
    }
}
