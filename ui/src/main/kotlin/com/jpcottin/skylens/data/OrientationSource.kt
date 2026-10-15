package com.jpcottin.skylens.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Device orientation from the rotation-vector sensor as a stream of [DevicePose]s. */
class OrientationSource(context: Context) {
    private val sensorManager = context.getSystemService(SensorManager::class.java)

    val isAvailable: Boolean
        get() = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null ||
            sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null

    /**
     * @param displayRotation one of `Surface.ROTATION_*`, read per event so a fold or a rotation
     * mid-stream is honoured.
     * @param declinationDeg magnetic declination at the observer, east positive.
     */
    fun poses(displayRotation: () -> Int, declinationDeg: () -> Double, smoothingAlpha: Double = 0.3): Flow<DevicePose> = callbackFlow {
        val manager = sensorManager ?: run { close(); return@callbackFlow }
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: run { close(); return@callbackFlow }
        val rotation = FloatArray(9)
        val remapped = FloatArray(9)
        var previous: DevicePose? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                val (axisX, axisY) = when (displayRotation()) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rotation, axisX, axisY, remapped)
                val pose = DevicePose.smooth(previous, DevicePose.fromRotationMatrix(remapped, declinationDeg()), smoothingAlpha)
                previous = pose
                trySend(pose)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { manager.unregisterListener(listener) }
    }
}
