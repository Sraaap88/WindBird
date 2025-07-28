package com.example.windbird

import android.graphics.Color
import kotlin.math.*
import kotlin.random.Random

class LugeGameEngine {
    
    // Variables de luge avec physique avancée
    var lugerX = 0.5f
    var lugerY = 0.75f
    var speed = 0f
    var maxSpeed = 140f
    var distance = 0f
    var totalDistance = 4000f
    var raceTime = 0f
    var altitude = 1000f
    
    // Physique avancée
    var velocity = Vector3(0f, 0f, 0f)
    var acceleration = Vector3(0f, 0f, 0f)
    var lugerRotation = 0f
    var lugerTilt = 0f
    var airResistance = 0f
    var groundFriction = 0f
    
    // Système de piste
    val trackCurves = mutableListOf<TrackCurve>()
    val trackElements = mutableListOf<TrackElement>()
    val trackSectors = mutableListOf<TrackSector>()
    var nextCurveIndex = 0
    var currentCurveStrength = 0f
    var curveDirection = 0f
    var currentSector = 0
    
    // Caméra 3D et effets visuels
    var cameraDistance = 150f
    var cameraHeight = 100f
    var cameraRotation = 0f
    var cameraTilt = 0f
    var fovEffect = 1f
    var perspectiveShift = 0f
    
    // Contrôles
    var gyroSensitivity = 1f
    
    // Performance
    var wallHits = 0
    var perfectCurves = 0
    var aerodynamics = 100f
    var precision = 100f
    var momentum = 0f
    var gForce = 0f
    var topSpeed = 0f
    var heartRate = 60f
    var adrenaline = 0f
    var stamina = 100f
    
    // Freinage
    var brakingPower = 0f
    var lastBrakeTime = 0L
    var brakingUsed = false
    var steeringPrecision = 100f
    var lugerMomentum = Vector3(0f, 0f, 0f)
    
    // Effets visuels
    var cameraShake = 0f
    var speedBlur = 0f
    var motionBlur = 0f
    var chromaticAberration = 0f
    var vignetting = 0f
    var bloom = 0f
    
    // Systèmes de particules SIMPLES
    val snowParticles3D = mutableListOf<SnowParticle3D>()
    val iceChips = mutableListOf<IceChip>()
    val speedStreaks = mutableListOf<SpeedStreak>()
    val wallSparks = mutableListOf<WallSpark>()
    val windTrails = mutableListOf<WindTrail>()
    val groundImpacts = mutableListOf<GroundImpact>()
    val sparkles = mutableListOf<IceSparkle>()
    val aeroTrails = mutableListOf<AeroTrail>()
    val curveIndicators = mutableListOf<CurveIndicator>()
    val speedZones = mutableListOf<SpeedZone>()
    val checkpoints = mutableListOf<Checkpoint>()
    
    // Score
    var finalScore = 0
    var scoreCalculated = false
    var sectorTimes = mutableListOf<Float>()
    var performanceMetrics = PerformanceMetrics()
    
    // Entrées capteurs
    private var tiltX = 0f
    private var tiltY = 0f
    private var tiltZ = 0f
    private var accelX = 0f
    private var accelY = 0f
    private var accelZ = 0f
    
    fun initialize() {
        resetAllParameters()
        generateBasicLugeTrack()
        initializeBasicParticleSystems()
    }
    
    private fun resetAllParameters() {
        lugerX = 0.5f
        lugerY = 0.75f
        speed = 0f
        distance = 0f
        raceTime = 0f
        altitude = 1000f
        nextCurveIndex = 0
        currentCurveStrength = 0f
        curveDirection = 0f
        currentSector = 0
        
        velocity = Vector3(0f, 0f, 0f)
        acceleration = Vector3(0f, 0f, 0f)
        lugerRotation = 0f
        lugerTilt = 0f
        airResistance = 0f
        groundFriction = 0f
        
        cameraDistance = 150f
        cameraHeight = 100f
        cameraRotation = 0f
        cameraTilt = 0f
        fovEffect = 1f
        perspectiveShift = 0f
        
        gyroSensitivity = 1f
        wallHits = 0
        perfectCurves = 0
        aerodynamics = 100f
        precision = 100f
        momentum = 0f
        gForce = 0f
        topSpeed = 0f
        heartRate = 60f
        adrenaline = 0f
        stamina = 100f
        
        brakingPower = 0f
        lastBrakeTime = 0L
        brakingUsed = false
        steeringPrecision = 100f
        lugerMomentum = Vector3(0f, 0f, 0f)
        
        cameraShake = 0f
        speedBlur = 0f
        motionBlur = 0f
        chromaticAberration = 0f
        vignetting = 0f
        bloom = 0f
        
        finalScore = 0
        scoreCalculated = false
        
        clearAllLists()
    }
    
    private fun clearAllLists() {
        trackCurves.clear()
        trackElements.clear()
        trackSectors.clear()
        snowParticles3D.clear()
        iceChips.clear()
        speedStreaks.clear()
        wallSparks.clear()
        windTrails.clear()
        groundImpacts.clear()
        sparkles.clear()
        aeroTrails.clear()
        curveIndicators.clear()
        speedZones.clear()
        checkpoints.clear()
        sectorTimes.clear()
    }
    
    fun startRacing() {
        speed = 45f
    }
    
    fun updateInputs(tX: Float, tY: Float, tZ: Float, aX: Float, aY: Float, aZ: Float) {
        tiltX = tX
        tiltY = tY
        tiltZ = tZ
        accelX = aX
        accelY = aY
        accelZ = aZ
    }
    
    fun updateRiding(deltaTime: Float) {
        handleBasicLugeMovement(deltaTime)
        handleBasicTrackCurves()
        handleBasicFootBraking()
        updateBasicPhysics(deltaTime)
        updateBasicRaceProgress(deltaTime)
        updateBasicEffects(deltaTime)
        updateTrackElements(deltaTime)
    }
    
    private fun generateBasicLugeTrack() {
        var currentDistance = 0f
        
        // Génération simple de virages
        for (i in 0..19) {
            trackCurves.add(TrackCurve(
                distance = currentDistance + (i + 1) * 200f,
                type = TrackCurve.Type.MEDIUM,
                direction = if (Random.nextBoolean()) -1f else 1f,
                length = 150f,
                banking = 0f
            ))
        }
        
        // Génération d'éléments de décor pour effet de vitesse
        for (i in 0..199) {
            trackElements.add(TrackElement(
                distance = i * 20f,
                sideOffset = if (Random.nextBoolean()) -1f else 1f,
                offsetDistance = Random.nextFloat() * 50f + 30f,
                type = TrackElement.ElementType.TREE,
                scale = Random.nextFloat() * 0.3f + 0.5f,
                height = Random.nextFloat() * 40f + 60f
            ))
        }
        
        // Génération simple de checkpoints
        for (i in 1..6) {
            checkpoints.add(Checkpoint(
                distance = i * (totalDistance / 7f),
                name = "CP$i",
                targetTime = i * 18f
            ))
        }
    }
    
    private fun initializeBasicParticleSystems() {
        for (i in 0..99) {
            snowParticles3D.add(SnowParticle3D(
                x = Random.nextFloat() * 1000f - 500f,
                y = Random.nextFloat() * 500f,
                z = Random.nextFloat() * 1000f + 200f,
                vx = (Random.nextFloat() - 0.5f) * 10f,
                vy = Random.nextFloat() * 20f + 5f,
                vz = Random.nextFloat() * 30f + 10f,
                size = Random.nextFloat() * 2f + 1f,
                life = Random.nextFloat() * 3f + 2f
            ))
        }
    }
    
    private fun handleBasicLugeMovement(deltaTime: Float) {
        // Contrôle de direction simple
        val steeringInput = tiltX * 0.5f
        lugerX += steeringInput * deltaTime
        lugerX = lugerX.coerceIn(0.1f, 0.9f)
        
        // Physique de base - accélération plus rapide
        acceleration.z = 4f
        if (tiltY < -0.5f) acceleration.z += 3f
        
        velocity.z += acceleration.z * deltaTime
        velocity.z = velocity.z.coerceIn(0f, maxSpeed)
        speed = velocity.z
        
        // Collision avec murs
        if (lugerX <= 0.15f || lugerX >= 0.85f) {
            wallHits++
            speed *= 0.9f
            precision -= 5f
        }
        
        // Mise à jour des forces
        momentum = speed / maxSpeed
        gForce = abs(steeringInput) * speed * 0.02f
        topSpeed = maxOf(topSpeed, speed)
        heartRate = 60f + speed * 0.5f
        adrenaline = (speed / maxSpeed * 0.5f).coerceIn(0f, 1f)
        
        // Effets visuels
        speedBlur = (speed / maxSpeed * 0.7f).coerceIn(0f, 1f)
        vignetting = speedBlur * 0.4f
        
        if (speed > 80f) {
            cameraShake = (speed - 80f) / 60f * 0.3f
        }
    }
    
    private fun handleBasicTrackCurves() {
        if (nextCurveIndex < trackCurves.size) {
            val curve = trackCurves[nextCurveIndex]
            val curveDistance = curve.distance - distance
            
            if (curveDistance < -curve.length) {
                nextCurveIndex++
                currentCurveStrength = 0f
                curveDirection = 0f
                return
            }
            
            if (curveDistance <= 100f && curveDistance >= -curve.length) {
                val curveProgress = when {
                    curveDistance > 0 -> 0f
                    curveDistance >= -curve.length -> (-curveDistance) / curve.length
                    else -> 1f
                }
                
                if (curveDistance <= 0f && curveDistance >= -curve.length) {
                    currentCurveStrength = sin(curveProgress * PI).toFloat() * 0.5f
                    curveDirection = curve.direction
                }
                
                // Évaluation simple de performance
                if (curveProgress > 0.3f && curveProgress < 0.7f) {
                    val idealPosition = 0.5f + curve.direction * 0.2f
                    val positionError = abs(lugerX - idealPosition)
                    
                    if (positionError < 0.2f) {
                        perfectCurves++
                        precision += 2f
                    }
                }
            }
        }
    }
    
    private fun handleBasicFootBraking() {
        val currentTime = System.currentTimeMillis()
        val brakeInput = abs(tiltZ) > 2f || sqrt(accelX*accelX + accelY*accelY + accelZ*accelZ) > 15f
        
        if (brakeInput && currentTime - lastBrakeTime > 500) {
            brakingPower += 10f
            speed *= 0.95f
            brakingUsed = true
            lastBrakeTime = currentTime
            stamina -= 1f
        }
        
        brakingPower *= 0.9f
        stamina = stamina.coerceIn(0f, 100f)
    }
    
    private fun updateBasicPhysics(deltaTime: Float) {
        // Physique simplifiée
        aerodynamics -= 0.01f
        precision -= 0.005f
        
        if (speed > 50f && abs(tiltX) < 0.2f) {
            aerodynamics += 0.02f
            precision += 0.01f
        }
        
        aerodynamics = aerodynamics.coerceIn(70f, 130f)
        precision = precision.coerceIn(50f, 130f)
    }
    
    private fun updateBasicRaceProgress(deltaTime: Float) {
        distance += speed * deltaTime * 1.5f
        altitude = maxOf(0f, 1000f - (distance / totalDistance) * 1000f)
        
        // Checkpoints
        for (checkpoint in checkpoints) {
            if (!checkpoint.passed && distance >= checkpoint.distance) {
                checkpoint.passed = true
                checkpoint.actualTime = raceTime
                
                if (checkpoint.actualTime < checkpoint.targetTime) {
                    precision += 5f
                }
            }
        }
    }
    
    private fun updateBasicEffects(deltaTime: Float) {
        // Particules de neige
        for (particle in snowParticles3D) {
            particle.x += particle.vx * deltaTime
            particle.y += particle.vy * deltaTime
            particle.z += particle.vz * deltaTime
            particle.life -= deltaTime
            
            if (particle.life <= 0f || particle.z < -50f) {
                particle.x = Random.nextFloat() * 500f - 250f
                particle.y = Random.nextFloat() * 300f + 100f
                particle.z = Random.nextFloat() * 100f + 500f
                particle.life = Random.nextFloat() * 3f + 2f
            }
        }
        
        // Nettoyage simple
        iceChips.removeIf { it.life <= 0f }
        wallSparks.removeIf { it.life <= 0f }
        sparkles.removeIf { it.life <= 0f }
        
        // Effets caméra
        cameraShake = maxOf(0f, cameraShake - deltaTime)
    }
    
    private fun updateTrackElements(deltaTime: Float) {
        // Mise à jour des éléments de décor pour effet de vitesse
        val elementsToUpdate = mutableListOf<TrackElement>()
        
        for (i in trackElements.indices) {
            val element = trackElements[i]
            val newDistance = element.distance - speed * deltaTime * 2f
            
            if (newDistance < -100f) {
                // Recycler l'élément
                val maxDistance = trackElements.maxByOrNull { it.distance }?.distance ?: 0f
                elementsToUpdate.add(TrackElement(
                    distance = maxDistance + Random.nextFloat() * 30f + 20f,
                    sideOffset = if (Random.nextBoolean()) -1f else 1f,
                    offsetDistance = Random.nextFloat() * 50f + 30f,
                    type = TrackElement.ElementType.TREE,
                    scale = Random.nextFloat() * 0.3f + 0.5f,
                    height = Random.nextFloat() * 40f + 60f
                ))
            } else {
                // Mettre à jour la distance
                elementsToUpdate.add(TrackElement(
                    distance = newDistance,
                    sideOffset = element.sideOffset,
                    offsetDistance = element.offsetDistance,
                    type = element.type,
                    scale = element.scale,
                    height = element.height
                ))
            }
        }
        
        trackElements.clear()
        trackElements.addAll(elementsToUpdate)
    }
    
    fun calculateFinalScore() {
        if (!scoreCalculated) {
            performanceMetrics.finalTime = raceTime
            performanceMetrics.topSpeed = topSpeed
            performanceMetrics.averageSpeed = distance / raceTime
            performanceMetrics.perfectCurves = perfectCurves
            performanceMetrics.wallHits = wallHits
            performanceMetrics.finalAerodynamics = aerodynamics
            performanceMetrics.finalPrecision = precision
            performanceMetrics.brakingUsed = brakingUsed
            performanceMetrics.stamina = stamina
            
            val timeBonus = maxOf(0, (150 - raceTime).toInt()) / 5
            val speedBonus = (performanceMetrics.averageSpeed / maxSpeed * 10).toInt()
            val precisionBonus = ((precision - 100f) * 0.2f).toInt()
            val perfectCurveBonus = perfectCurves * 3
            val checkpointBonus = checkpoints.count { it.passed && it.actualTime < it.targetTime } * 2
            
            val wallPenalty = wallHits * 2
            val brakingPenalty = if (brakingUsed) 2 else 0
            
            finalScore = maxOf(10, timeBonus + speedBonus + precisionBonus + perfectCurveBonus + checkpointBonus - wallPenalty - brakingPenalty)
            
            scoreCalculated = true
        }
    }
}
