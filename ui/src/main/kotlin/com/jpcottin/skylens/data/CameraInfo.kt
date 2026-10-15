package com.jpcottin.skylens.data

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.jpcottin.skylens.astro.SkyCamera

/** Field of view of the back camera from its Camera2 characteristics (sensor size and widest lens). */
object CameraInfo {
    data class SensorFov(val longSideDeg: Double, val shortSideDeg: Double)

    fun backCameraFov(context: Context): SensorFov? {
        val manager = context.getSystemService(CameraManager::class.java) ?: return null
        return try {
            for (id in manager.cameraIdList) {
                val c = manager.getCameraCharacteristics(id)
                if (c.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) continue
                val size = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: continue
                val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull() ?: continue
                val long = maxOf(size.width, size.height).toDouble()
                val short = minOf(size.width, size.height).toDouble()
                return SensorFov(SkyCamera.fovDegrees(long, focal.toDouble()), SkyCamera.fovDegrees(short, focal.toDouble()))
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
