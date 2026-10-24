package com.jpcottin.skylens.ui.sky

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.jpcottin.skylens.astro.BodyKind
import com.jpcottin.skylens.astro.Horizontal
import com.jpcottin.skylens.astro.MoonObject
import com.jpcottin.skylens.astro.PlanetObject
import com.jpcottin.skylens.astro.SatelliteObject
import com.jpcottin.skylens.astro.ScreenPoint
import com.jpcottin.skylens.astro.SkyCamera
import com.jpcottin.skylens.astro.SkyPosition
import com.jpcottin.skylens.astro.SkySnapshot
import com.jpcottin.skylens.astro.StarObject
import com.jpcottin.skylens.astro.SunObject
import com.jpcottin.skylens.astro.Vector3
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** What the overlay draws besides the objects. */
data class SkyOverlayOptions(
    val showLabels: Boolean = true,
    val showTrajectories: Boolean = true,
    val drawGround: Boolean = false,
    val drawGrid: Boolean = false,
    /** Stars brighter than this get a name label. */
    val starLabelMagnitude: Double = 1.6,
    /** Label text size; the glasses need at least 18 sp to be legible. */
    val labelSizeSp: Float = 12f,
    /** Scales markers (dots, diamonds) — larger on the lens. */
    val markerScale: Float = 1f,
)

/**
 * Draws the sky through a [SkyCamera] onto a Canvas. It is a pure function of its inputs, so the
 * same snapshot, camera and options always give the same pixels (screenshot-testable).
 */
@Composable
fun SkyOverlay(
    snapshot: SkySnapshot?,
    camera: SkyCamera,
    palette: SkyPalette,
    options: SkyOverlayOptions,
    selectedId: String?,
    targetId: String?,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val description = snapshot?.let { s ->
        val visible = s.positions.count { p -> camera.project(p.horizontal)?.isOnScreen == true }
        "Sky view, $visible objects on screen"
    } ?: "Sky view, waiting for position"
    Canvas(modifier.semantics { contentDescription = description }) {
        val renderer = SkyRenderer(this, camera, palette, textMeasurer, options)
        renderer.drawHorizonAndGround()
        if (options.drawGrid) renderer.drawGrid()
        if (snapshot != null) {
            renderer.drawObjects(snapshot, selectedId, targetId)
        }
    }
}

/** Projection helpers shared by the overlay and the hit testing in the screen. */
object SkyProjection {
    fun toOffset(point: ScreenPoint, size: Size): Offset =
        Offset(((point.x + 1.0) / 2.0 * size.width).toFloat(), ((1.0 - point.y) / 2.0 * size.height).toFloat())

    fun toScreenPoint(offset: Offset, size: Size): ScreenPoint =
        ScreenPoint(offset.x / size.width * 2.0 - 1.0, 1.0 - offset.y / size.height * 2.0)

    /** Angular radius (degrees) that a pixel radius subtends at the centre of the view. */
    fun degreesPerPixel(camera: SkyCamera, size: Size): Double = camera.horizontalFovDeg / max(1f, size.width)
}

private class SkyRenderer(
    private val scope: DrawScope,
    private val camera: SkyCamera,
    private val palette: SkyPalette,
    private val textMeasurer: TextMeasurer,
    private val options: SkyOverlayOptions,
) {
    private val size = scope.size
    private val density = scope.density
    private val labelStyle = TextStyle(color = palette.label, fontSize = options.labelSizeSp.sp, fontWeight = FontWeight.Medium)
    private val cardinalStyle = TextStyle(color = palette.cardinal, fontSize = (options.labelSizeSp + 2f).sp, fontWeight = FontWeight.Bold)

    private fun dp(v: Float) = v * density * options.markerScale

    private fun project(h: Horizontal): Offset? = camera.project(h)?.let { SkyProjection.toOffset(it, size) }

    private fun isInFront(h: Horizontal) = camera.project(h) != null

    fun drawHorizonAndGround() {
        // Horizon polyline, sampled every 2° of azimuth; broken where it passes behind the camera.
        val points = ArrayList<Offset?>(181)
        for (az in 0..360 step 2) points += project(Horizontal(az.toDouble(), 0.0))
        if (options.drawGround) drawGround(points)
        var path = Path()
        var open = false
        for (p in points) {
            if (p == null) {
                open = false
                continue
            }
            if (!open) {
                path.moveTo(p.x, p.y)
                open = true
            } else {
                path.lineTo(p.x, p.y)
            }
        }
        scope.drawPath(path, palette.horizon, style = Stroke(width = dp(1.5f)))
        // Ticks every 10°.
        for (az in 0 until 360 step 10) {
            val base = project(Horizontal(az.toDouble(), 0.0)) ?: continue
            val tickLen = if (az % 90 == 0) 2.0 else if (az % 30 == 0) 1.2 else 0.6
            val top = project(Horizontal(az.toDouble(), tickLen)) ?: continue
            if (!onCanvas(base) && !onCanvas(top)) continue
            scope.drawLine(palette.horizon, base, top, strokeWidth = dp(1.5f))
        }
        // Labels at the cardinal and intercardinal points (their own loop: 45° is not a
        // multiple of 10°, so the tick loop never reached NE, SE, SW and NW).
        for (az in 0 until 360 step 45) {
            val label = when (az) { 0 -> "N"; 45 -> "NE"; 90 -> "E"; 135 -> "SE"; 180 -> "S"; 225 -> "SW"; 270 -> "W"; else -> "NW" }
            val labelPos = project(Horizontal(az.toDouble(), 3.5)) ?: continue
            if (!onCanvas(labelPos)) continue
            drawCentredText(label, labelPos, cardinalStyle)
        }
    }

    private fun drawGround(horizon: List<Offset?>) {
        // Fill below the horizon: polyline plus the two bottom corners on the "down" side.
        val visible = horizon.filterNotNull()
        if (visible.size < 2) {
            // Horizon not in view: if we look down the whole view is ground, if up none of it.
            if (camera.centre.elevationDeg < 0) scope.drawRect(palette.ground)
            return
        }
        val path = Path()
        path.moveTo(visible.first().x, visible.first().y)
        for (p in visible.drop(1)) path.lineTo(p.x, p.y)
        // Screen-space "down" is where the nadir projects, else the bottom edge.
        val downY = size.height + dp(10f)
        path.lineTo(visible.last().x, downY)
        path.lineTo(visible.first().x, downY)
        path.close()
        scope.drawPath(path, palette.ground)
    }

    fun drawGrid() {
        val effect = PathEffect.dashPathEffect(floatArrayOf(dp(2f), dp(6f)))
        for (el in listOf(30.0, 60.0)) {
            var prev: Offset? = null
            for (az in 0..360 step 3) {
                val p = project(Horizontal(az.toDouble(), el))
                if (p != null && prev != null) scope.drawLine(palette.grid, prev, p, strokeWidth = dp(1f), pathEffect = effect)
                prev = p
            }
        }
        for (az in 0 until 360 step 30) {
            var prev: Offset? = null
            for (el in 0..90 step 3) {
                val p = project(Horizontal(az.toDouble(), el.toDouble()))
                if (p != null && prev != null) scope.drawLine(palette.grid, prev, p, strokeWidth = dp(1f), pathEffect = effect)
                prev = p
            }
        }
    }

    fun drawObjects(snapshot: SkySnapshot, selectedId: String?, targetId: String?) {
        // Stars first (behind everything), then solar system, then satellites, then markers.
        val stars = snapshot.ofKind(BodyKind.STAR)
        for (p in stars) drawStar(p)
        for (p in snapshot.positions) {
            when (p.obj) {
                is SunObject -> drawSun(p)
                is MoonObject -> drawMoon(p)
                is PlanetObject -> drawPlanet(p)
                else -> Unit
            }
        }
        for (p in snapshot.ofKind(BodyKind.SATELLITE)) drawSatellite(p)
        selectedId?.let { id -> snapshot.find(id)?.let { drawSelection(it, palette.selection) } }
        targetId?.let { id -> snapshot.find(id)?.let { drawTargetGuide(it) } }
    }

    private fun drawStar(p: SkyPosition) {
        if (p.horizontal.elevationDeg < -1.0) return
        val pos = project(p.horizontal) ?: return
        if (!onCanvas(pos)) return
        val mag = p.magnitude ?: 5.0
        val radius = dp((5.5 - mag).coerceIn(1.0, 5.0).toFloat() * 0.8f)
        val star = (p.obj as StarObject).star
        scope.drawCircle(palette.star(star.colorIndex), radius, pos)
        if (options.showLabels && mag <= options.starLabelMagnitude && star.label.isNotBlank()) {
            drawLabel(star.label, pos, radius)
        }
    }

    private fun drawSun(p: SkyPosition) {
        val pos = project(p.horizontal) ?: return
        if (!onCanvas(pos, margin = dp(40f))) return
        scope.drawCircle(palette.sunGlow, dp(22f), pos)
        scope.drawCircle(palette.sun, dp(12f), pos)
        if (options.showLabels) drawLabel("Sun", pos, dp(14f))
    }

    private fun drawMoon(p: SkyPosition) {
        val pos = project(p.horizontal) ?: return
        if (!onCanvas(pos, margin = dp(40f))) return
        val r = dp(11f)
        val fraction = (p.illuminatedFraction ?: 1.0).coerceIn(0.0, 1.0)
        // Lit disc, then the shadow: a half disc on the dark side plus an ellipse of the terminator.
        scope.drawCircle(palette.moon, r, pos)
        val waxing = p.waxing ?: true
        val darkSide = if (waxing) -1f else 1f // waxing: dark limb on the left (northern hemisphere view)
        val shadow = Path()
        shadow.addArc(androidx.compose.ui.geometry.Rect(pos.x - r, pos.y - r, pos.x + r, pos.y + r), if (darkSide < 0) 90f else -90f, 180f)
        val terminatorHalfWidth = (r * (2 * fraction - 1)).toFloat()
        // Ellipse of the terminator: width depends on the phase; it bulges toward the dark side
        // before half moon and toward the lit side after.
        val ellipse = androidx.compose.ui.geometry.Rect(pos.x - kotlin.math.abs(terminatorHalfWidth), pos.y - r, pos.x + kotlin.math.abs(terminatorHalfWidth), pos.y + r)
        scope.drawPath(shadow, palette.moonShadow)
        if (fraction < 0.5) {
            // Crescent: the lit side also loses an ellipse.
            scope.drawOval(palette.moonShadow, topLeft = ellipse.topLeft, size = ellipse.size)
        } else if (fraction < 1.0) {
            // Gibbous: part of the dark half is lit back.
            scope.drawOval(palette.moon, topLeft = ellipse.topLeft, size = ellipse.size)
        }
        scope.drawCircle(palette.moonShadow.copy(alpha = 0.6f), r, pos, style = Stroke(dp(1f)))
        if (options.showLabels) drawLabel("Moon", pos, r + dp(2f))
    }

    private fun drawPlanet(p: SkyPosition) {
        if (p.horizontal.elevationDeg < -2.0) return
        val pos = project(p.horizontal) ?: return
        if (!onCanvas(pos)) return
        val mag = p.magnitude ?: 0.0
        val radius = dp((5.0 - mag * 0.6).coerceIn(3.0, 7.0).toFloat())
        val color = palette.planet(p.obj.name)
        scope.drawCircle(color.copy(alpha = 0.35f), radius * 1.8f, pos)
        scope.drawCircle(color, radius, pos)
        if (options.showLabels) drawLabel(p.obj.name, pos, radius)
    }

    private fun drawSatellite(p: SkyPosition) {
        val color = if (p.sunlit == false) palette.satelliteShadowed else palette.satellite
        if (options.showTrajectories && p.track.size >= 2) drawTrack(p, color)
        if (p.horizontal.elevationDeg < -5.0) return
        val pos = project(p.horizontal) ?: return
        if (!onCanvas(pos)) return
        val s = dp(6f)
        val diamond = Path().apply {
            moveTo(pos.x, pos.y - s)
            lineTo(pos.x + s, pos.y)
            lineTo(pos.x, pos.y + s)
            lineTo(pos.x - s, pos.y)
            close()
        }
        scope.drawPath(diamond, color)
        scope.drawPath(diamond, palette.labelShadow, style = Stroke(dp(1f)))
        if (options.showLabels) drawLabel(p.obj.name, pos, s)
    }

    private fun drawTrack(p: SkyPosition, color: Color) {
        val effect = PathEffect.dashPathEffect(floatArrayOf(dp(4f), dp(4f)))
        var prev: Offset? = null
        var last: Offset? = null
        var beforeLast: Offset? = null
        for (h in p.track) {
            val pt = if (h.elevationDeg > -3.0) project(h) else null
            if (pt != null && prev != null) {
                scope.drawLine(palette.track.copy(alpha = color.alpha * 0.7f), prev, pt, strokeWidth = dp(1.5f), pathEffect = effect, cap = StrokeCap.Round)
            }
            if (pt != null) {
                beforeLast = last
                last = pt
            }
            prev = pt
        }
        // Arrow head at the end of the track shows the direction of travel.
        if (last != null && beforeLast != null && onCanvas(last)) {
            val angle = atan2(last.y - beforeLast.y, last.x - beforeLast.x)
            val len = dp(7f)
            val a1 = angle + 2.6f
            val a2 = angle - 2.6f
            scope.drawLine(palette.track, last, Offset(last.x + len * cos(a1), last.y + len * sin(a1)), strokeWidth = dp(1.5f))
            scope.drawLine(palette.track, last, Offset(last.x + len * cos(a2), last.y + len * sin(a2)), strokeWidth = dp(1.5f))
        }
    }

    private fun drawSelection(p: SkyPosition, color: Color) {
        val pos = project(p.horizontal) ?: return
        if (!onCanvas(pos, margin = dp(30f))) return
        scope.drawCircle(color, dp(18f), pos, style = Stroke(dp(2f)))
        scope.drawCircle(color.copy(alpha = 0.4f), dp(24f), pos, style = Stroke(dp(1f)))
    }

    /** When the target is off screen, an arrow at the edge points the way to it. */
    private fun drawTargetGuide(p: SkyPosition) {
        val projected = camera.project(p.horizontal)
        if (projected != null && projected.isOnScreen) {
            drawSelection(p, palette.target)
            return
        }
        val d = p.horizontal.toUnitVector()
        val dx = d dot camera.right
        val dy = d dot camera.up
        val norm = sqrt(dx * dx + dy * dy)
        if (norm < 1e-9) return
        val ux = (dx / norm).toFloat()
        val uy = (-dy / norm).toFloat() // screen y grows downward
        val cx = size.width / 2
        val cy = size.height / 2
        val radius = min(size.width, size.height) / 2 - dp(36f)
        val tip = Offset(cx + ux * radius, cy + uy * radius)
        val angle = atan2(uy, ux)
        val len = dp(22f)
        val wing = 2.5f
        val arrow = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x + len * cos(angle + wing), tip.y + len * sin(angle + wing))
            lineTo(tip.x + len * 0.55f * cos(angle + Math.PI.toFloat()), tip.y + len * 0.55f * sin(angle + Math.PI.toFloat()))
            lineTo(tip.x + len * cos(angle - wing), tip.y + len * sin(angle - wing))
            close()
        }
        scope.drawPath(arrow, palette.target)
        scope.drawPath(arrow, palette.labelShadow, style = Stroke(dp(1f)))
        val labelPos = Offset(tip.x - ux * dp(40f), tip.y - uy * dp(40f))
        drawCentredText(p.obj.name, labelPos, labelStyle)
    }

    // Labels are measured unconstrained so a name near the right edge runs off screen instead
    // of wrapping onto a second line.
    private fun measure(text: String, style: TextStyle) =
        textMeasurer.measure(text, style, softWrap = false, maxLines = 1, constraints = androidx.compose.ui.unit.Constraints())

    private fun drawLabel(text: String, anchor: Offset, radius: Float) {
        val topLeft = Offset(anchor.x + radius + dp(4f), anchor.y)
        drawShadowedText(text, labelStyle, topLeft, centred = false)
    }

    private fun drawCentredText(text: String, centre: Offset, style: TextStyle) = drawShadowedText(text, style, centre, centred = true)

    private fun drawShadowedText(text: String, style: TextStyle, at: Offset, centred: Boolean) {
        val shadow = measure(text, style.copy(color = palette.labelShadow))
        val main = measure(text, style)
        val topLeft = if (centred) Offset(at.x - main.size.width / 2f, at.y - main.size.height / 2f) else Offset(at.x, at.y - main.size.height / 2f)
        scope.drawText(shadow, topLeft = Offset(topLeft.x + dp(1f), topLeft.y + dp(1f)))
        scope.drawText(main, topLeft = topLeft)
    }

    private fun onCanvas(p: Offset, margin: Float = dp(16f)): Boolean =
        p.x >= -margin && p.x <= size.width + margin && p.y >= -margin && p.y <= size.height + margin
}

/** Small helpers for the HUD. */
object SkyFormat {
    fun degrees(v: Double): Int = Math.round(v).toInt()

    fun km(v: Double): String = when {
        v >= 1_000_000 -> "%.2f M".format(v / 1_000_000)
        v >= 10_000 -> "%,d".format(Math.round(v))
        else -> "%.0f".format(v)
    }

    fun au(km: Double): String = "%.3f".format(km / 149_597_870.7)

    fun magnitude(m: Double): String = "%.1f".format(m)
}

/** Unit vector helper used by the guide arrow. */
private infix fun Vector3.dot(o: Vector3): Double = x * o.x + y * o.y + z * o.z
