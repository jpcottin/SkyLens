package com.jpcottin.skylens.ui.common

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview

/** The four window classes every screen is previewed in. */
@Preview(name = "Phone", device = Devices.PHONE, showBackground = true, showSystemUi = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true, showSystemUi = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true, showSystemUi = true)
@Preview(name = "Desktop", device = Devices.DESKTOP, showBackground = true, showSystemUi = true)
annotation class FormFactorPreviews

/** Light and dark on a phone. */
@Preview(name = "Light", device = Devices.PHONE, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", device = Devices.PHONE, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ThemePreviews

/** Landscape phone and the folded cover screen: the narrow and the short extremes. */
@Preview(name = "Landscape", device = "spec:width=891dp,height=411dp", showBackground = true)
@Preview(name = "Cover screen", device = "spec:width=360dp,height=720dp", showBackground = true)
annotation class EdgeCasePreviews
