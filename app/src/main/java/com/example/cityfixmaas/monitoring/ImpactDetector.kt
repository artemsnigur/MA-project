package com.example.cityfixmaas.monitoring

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Impact Detection: emits when the accelerometer feels a hard jolt, like a wheel dropping into a
 * pothole. We do not know how the phone is held, so we use the total acceleration and look at how
 * far it jumps away from gravity. At most one event every [COOLDOWN_MS].
 */
object ImpactDetector {
    /** Jolts found by MonitoringService while the app is on screen; AppShell shows a dialog for them. */
    val detectedInApp = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** About 1.5 g on top of gravity: walking or a normal car ride stays well below this. */
    private const val THRESHOLD = 15f
    private const val COOLDOWN_MS = 30_000L

    fun impacts(context: Context): Flow<Unit> = callbackFlow {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var lastImpact = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                val total = sqrt(x * x + y * y + z * z)
                val now = System.currentTimeMillis()
                if (abs(total - SensorManager.GRAVITY_EARTH) > THRESHOLD && now - lastImpact > COOLDOWN_MS) {
                    lastImpact = now
                    trySend(Unit)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        if (accelerometer != null) sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
