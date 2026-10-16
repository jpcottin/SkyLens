package com.jpcottin.skylens.ui.glasses

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.xr.projected.ProjectedContext
import androidx.xr.projected.experimental.ExperimentalProjectedApi

/** Starts [GlassesActivity] on the connected Display AI Glasses (never on the phone). */
@OptIn(ExperimentalProjectedApi::class)
object GlassesLauncher {
    fun launch(context: Context, extras: Intent? = null): Result<Unit> = runCatching {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) { "Display AI Glasses need Android 16" }
        val options = ProjectedContext.createProjectedActivityOptions(context)
        val intent = Intent(context, GlassesActivity::class.java)
        extras?.extras?.let(intent::putExtras)
        context.startActivity(intent, options.toBundle())
    }
}
