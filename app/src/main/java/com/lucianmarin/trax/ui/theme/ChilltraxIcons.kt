package com.lucianmarin.trax.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp

/**
 * Google Fonts Material Symbols (https://fonts.google.com/icons) & `icon.svg` vector definitions:
 * - `MusicNote2` (`music_note_2`): Replaces the Apple logo for default station artwork & Apple Music overlay.
 * - `MusicNote` (`music_note`): Used for song history results placeholder artwork.
 * - `BarChart4Bars` (`bar_chart_4_bars`): Used for the Spectrum visualizer segment.
 * - `VitalSigns` (`vital_signs`): Used for the Waveform visualizer segment.
 * - `SettingsSlowMotion` (`settings_slow_motion`): Used for the Settings button in the bottom app controls bar.
 */
object ChilltraxIcons {
    // Exact SVG path data from `../chilltrax/icon.svg` (`viewBox="0 0 240 240"`)
    const val ICON_SVG_CENTER_DOT_PATH_D =
        "M90 108.75C92.9837 108.75 95.8452 109.935 97.9549 112.045C100.065 114.155 101.25 117.016 101.25 120C101.25 122.984 100.065 125.845 97.9549 127.955C95.8452 130.065 92.9837 131.25 90 131.25C87.0163 131.25 84.1548 130.065 82.0451 127.955C79.9353 125.845 78.75 122.984 78.75 120C78.75 117.016 79.9353 114.155 82.0451 112.045C84.1548 109.935 87.0163 108.75 90 108.75Z"

    const val ICON_SVG_INNER_WAVE_PATH_D =
        "M59.175 101.1C57.5949 99.5179 56.7074 97.3734 56.7074 95.1375C56.7074 92.9015 57.5949 90.757 59.175 89.175C67.3504 80.9998 78.4384 76.4071 90 76.4071C101.562 76.4071 112.65 80.9998 120.825 89.175C121.654 89.9474 122.319 90.8789 122.78 91.9139C123.241 92.9489 123.489 94.0662 123.509 95.1991C123.529 96.332 123.321 97.4573 122.896 98.508C122.472 99.5586 121.84 100.513 121.039 101.314C120.238 102.115 119.284 102.747 118.233 103.171C117.182 103.596 116.057 103.804 114.924 103.784C113.791 103.764 112.674 103.516 111.639 103.055C110.604 102.594 109.672 101.929 108.9 101.1C106.419 98.6168 103.472 96.647 100.229 95.303C96.9864 93.959 93.5104 93.2673 90 93.2673C86.4896 93.2673 83.0135 93.959 79.7706 95.303C76.5276 96.647 73.5813 98.6168 71.1 101.1C69.5179 102.68 67.3734 103.568 65.1375 103.568C62.9015 103.568 60.757 102.68 59.175 101.1ZM59.175 138.9C60.757 137.32 62.9015 136.432 65.1375 136.432C67.3734 136.432 69.5179 137.32 71.1 138.9C73.5813 141.383 76.5276 143.353 79.7706 144.697C83.0135 146.041 86.4896 146.733 90 146.733C93.5104 146.733 96.9864 146.041 100.229 144.697C103.472 143.353 106.419 141.383 108.9 138.9C109.672 138.071 110.604 137.406 111.639 136.945C112.674 136.484 113.791 136.236 114.924 136.216C116.057 136.196 117.182 136.404 118.233 136.829C119.284 137.253 120.238 137.885 121.039 138.686C121.84 139.487 122.472 140.441 122.896 141.492C123.321 142.543 123.529 143.668 123.509 144.801C123.489 145.934 123.241 147.051 122.78 148.086C122.319 149.121 121.654 150.053 120.825 150.825C112.65 159 101.562 163.593 90 163.593C78.4384 163.593 67.3504 159 59.175 150.825C57.5949 149.243 56.7074 147.098 56.7074 144.862C56.7074 142.627 57.5949 140.482 59.175 138.9Z"

    const val ICON_SVG_OUTER_WAVE_PATH_D =
        "M34.3125 76.2375C32.7324 74.6554 31.8449 72.5109 31.8449 70.275C31.8449 68.039 32.7324 65.8945 34.3125 64.3125C41.6252 56.999 50.3069 51.1976 59.8618 47.2396C69.4167 43.2815 79.6577 41.2443 90 41.2443C100.342 41.2443 110.583 43.2815 120.138 47.2396C129.693 51.1976 138.375 56.999 145.687 64.3125C146.516 65.0849 147.181 66.0164 147.643 67.0514C148.104 68.0864 148.352 69.2037 148.372 70.3366C148.392 71.4695 148.183 72.5948 147.759 73.6455C147.335 74.6961 146.703 75.6505 145.902 76.4517C145.1 77.2529 144.146 77.8845 143.095 78.3089C142.045 78.7332 140.92 78.9416 139.787 78.9216C138.654 78.9017 137.536 78.6537 136.501 78.1925C135.466 77.7314 134.535 77.0665 133.762 76.2375C128.016 70.4888 121.194 65.9285 113.685 62.8172C106.176 59.7059 98.128 58.1045 90 58.1045C81.872 58.1045 73.8237 59.7059 66.3147 62.8172C58.8058 65.9285 51.9835 70.4888 46.2375 76.2375C44.6555 77.8176 42.5109 78.7051 40.275 78.7051C38.039 78.7051 35.8945 77.8176 34.3125 76.2375ZM34.3125 163.762C35.8945 162.182 38.039 161.295 40.275 161.295C42.5109 161.295 44.6555 162.182 46.2375 163.762C51.9835 169.511 58.8058 174.071 66.3147 177.183C73.8237 180.294 81.872 181.896 90 181.896C98.128 181.896 106.176 180.294 113.685 177.183C121.194 174.071 128.016 169.511 133.762 163.762C134.535 162.934 135.466 162.269 136.501 161.807C137.536 161.346 138.654 161.098 139.787 161.078C140.92 161.058 142.045 161.267 143.095 161.691C144.146 162.115 145.1 162.747 145.902 163.548C146.703 164.35 147.335 165.304 147.759 166.355C148.183 167.405 148.392 168.53 148.372 169.663C148.352 170.796 148.104 171.914 147.643 172.949C147.181 173.984 146.516 174.915 145.687 175.687C138.375 183.001 129.693 188.802 120.138 192.76C110.583 196.718 100.342 198.756 90 198.756C79.6577 198.756 69.4167 196.718 59.8618 192.76C50.3069 188.802 41.6252 183.001 34.3125 175.687C32.7324 174.105 31.8449 171.961 31.8449 169.725C31.8449 167.489 32.7324 165.345 34.3125 163.762Z"

    // Google Fonts Material Symbols SVG paths (viewBox="0 -960 960 960")
    const val MUSIC_NOTE_2_PATH_D =
        "M127,-167q-47,-47 -47,-113t47,-113q47,-47 113,-47 23,0 42.5,5.5T320,-418v-308q0,-15 9.5,-26.5T353,-766l400,-66q18,-3 32.5,8.5T800,-793v433q0,66 -47,113t-113,47q-66,0 -113,-47t-47,-113q0,-66 47,-113t113,-47q23,0 42.5,5.5T720,-498v-165l-320,63v320q0,66 -47,113t-113,47q-66,0 -113,-47Z"

    const val MUSIC_NOTE_PATH_D =
        "M287,-167q-47,-47 -47,-113t47,-113q47,-47 113,-47 23,0 42.5,5.5T480,-418v-382q0,-17 11.5,-28.5T520,-840h160q17,0 28.5,11.5T720,-800v80q0,17 -11.5,28.5T680,-680H560v400q0,66 -47,113t-113,47q-66,0 -113,-47Z"

    const val BAR_CHART_4_BARS_PATH_D =
        "M120,-120q-17,0 -28.5,-11.5T80,-160q0,-17 11.5,-28.5T120,-200h720q17,0 28.5,11.5T880,-160q0,17 -11.5,28.5T840,-120H120Zm17.5,-137.5Q120,-275 120,-300v-160q0,-25 17.5,-42.5T180,-520q25,0 42.5,17.5T240,-460v160q0,25 -17.5,42.5T180,-240q-25,0 -42.5,-17.5Zm200,0Q320,-275 320,-300v-360q0,-25 17.5,-42.5T380,-720q25,0 42.5,17.5T440,-660v360q0,25 -17.5,42.5T380,-240q-25,0 -42.5,-17.5Zm200,0Q520,-275 520,-300v-240q0,-25 17.5,-42.5T580,-600q25,0 42.5,17.5T640,-540v240q0,25 -17.5,42.5T580,-240q-25,0 -42.5,-17.5Zm200,0Q720,-275 720,-300v-480q0,-25 17.5,-42.5T780,-840q25,0 42.5,17.5T840,-780v480q0,25 -17.5,42.5T780,-240q-25,0 -42.5,-17.5Z"

    const val VITAL_SIGNS_PATH_D =
        "M326,-171q-15,-11 -22,-28l-92,-241H80q-17,0 -28.5,-11.5T40,-480q0,-17 11.5,-28.5T80,-520h160q13,0 22.5,7t14.5,19l83,218 184,-485q7,-17 22,-28t34,-11q19,0 34,11t22,28l92,241h132q17,0 28.5,11.5T920,-480q0,17 -11.5,28.5T880,-440H720q-13,0 -22.5,-7T683,-466l-83,-218 -184,485q-7,17 -22,28t-34,11q-19,0 -34,-11Z"

    const val SETTINGS_SLOW_MOTION_PATH_D =
        "M749,-93q-11,4 -21,-2.5T715,-115q-2,-11 3.5,-22t16.5,-16q38,-17 60.5,-51t22.5,-76q0,-42 -22.5,-76T735,-407q-12,-5 -17,-16t-3,-22q3,-13 13,-19.5t21,-2.5q59,22 94,73t35,114q0,63 -35,114t-94,73Zm-141,-2q-20,-8 -37.5,-19T538,-140q-15,-15 -26,-32.5T493,-210q-4,-11 2.5,-21t18.5,-13q11,-2 21.5,4t15.5,17q5,12 12.5,22t16.5,19q9,9 19.5,16.5T621,-153q11,5 16.5,16t3.5,22q-3,12 -12.5,18T608,-95Zm46,-115q-5,3 -10.5,0.5T638,-218v-124q0,-6 5.5,-8.5t10.5,0.5l95,62q5,3 5,8t-5,8l-95,62ZM514,-316q-12,-3 -18.5,-13t-2.5,-21q8,-20 19,-37.5t26,-32.5q15,-15 32.5,-26t37.5,-19q11,-4 20.5,2t12.5,18q2,11 -3.5,22T621,-407q-11,5 -21.5,12.5T580,-378q-9,9 -16.5,19T551,-337q-5,11 -15.5,17t-21.5,4ZM423,-80q-27,0 -41.5,-18T363,-142l-9,-66q-13,-5 -24.5,-12T307,-235l-62,26q-25,11 -50,2t-39,-32l-47,-82q-14,-23 -8,-49t27,-43l53,-40q-1,-7 -1,-13.5v-27q0,-6.5 1,-13.5l-53,-40q-21,-17 -27,-43t8,-49l47,-82q14,-23 39,-32t50,2l62,26q11,-8 23,-15t24,-12l9,-66q4,-26 23.5,-44t46.5,-18h94q27,0 46.5,18t23.5,44l9,66q13,5 24.5,12t22.5,15l62,-26q25,-11 50,-2t39,32l47,82q14,23 8,49t-27,43l-24,18q-13,11 -29,8.5T752,-536q-11,-13 -9,-29t15,-27l19,-14 -39,-68 -99,42q-22,-23 -48.5,-38.5T533,-694l-13,-106h-79l-14,106q-31,8 -57.5,23.5T321,-633l-99,-41 -39,68 86,64q-5,15 -7,30t-2,32q0,16 2,31t7,30l-86,65 39,68 99,-42q17,17 36.5,30.5T400,-275q1,44 14.5,83.5T451,-119q8,12 -1,25.5T423,-80Zm59,-540q-58,0 -99,40.5T342,-480q0,21 6,41t18,38q11,15 28.5,19t31.5,-7q14,-10 16.5,-26.5T434,-444q-6,-8 -9,-16.5t-3,-19.5q0,-25 17.5,-42.5T482,-540q10,0 19.5,3.5T519,-527q12,9 27,6.5t25,-16.5q10,-14 6.5,-31T560,-596q-14,-12 -34,-18t-44,-6Z"

    const val NOTES_PATH_D =
        "M160,-240q-17,0 -28.5,-11.5T120,-280q0,-17 11.5,-28.5T160,-320h400q17,0 28.5,11.5T600,-280q0,17 -11.5,28.5T560,-240H160Zm0,-200q-17,0 -28.5,-11.5T120,-480q0,-17 11.5,-28.5T160,-520h640q17,0 28.5,11.5T840,-480q0,17 -11.5,28.5T800,-440H160Zm0,-200q-17,0 -28.5,-11.5T120,-680q0,-17 11.5,-28.5T160,-720h640q17,0 28.5,11.5T840,-680q0,17 -11.5,28.5T800,-640H160Z"

    const val PLAY_ARROW_FILLED_PATH_D =
        "M320,-273v-414q0,-17 12,-28.5t28,-11.5q5,0 10.5,1.5T381,-721l326,207q9,6 13.5,15t4.5,19q0,10 -4.5,19T707,-446L381,-239q-5,3 -10.5,4.5T360,-233q-16,0 -28,-11.5T320,-273Z"

    const val PAUSE_FILLED_PATH_D =
        "M640,-200q-33,0 -56.5,-23.5T560,-280v-400q0,-33 23.5,-56.5T640,-760q33,0 56.5,23.5T720,-680v400q0,33 -23.5,56.5T640,-200Zm-320,0q-33,0 -56.5,-23.5T240,-280v-400q0,-33 23.5,-56.5T320,-760q33,0 56.5,23.5T400,-680v400q0,33 -23.5,56.5T320,-200Z"

    const val STOP_FILLED_PATH_D =
        "M240,-280v-400q0,-17 11.5,-28.5T280,-720h400q17,0 28.5,11.5T720,-680v400q0,17 -11.5,28.5T680,-240H280q-17,0 -28.5,-11.5T240,-280Z"

    const val SEARCH_PATH_D =
        "M380,-320q-109,0 -184.5,-75.5T120,-580q0,-109 75.5,-184.5T380,-840q109,0 184.5,75.5T640,-580q0,44 -14,83t-38,69l224,224q11,11 11,28t-11,28q-11,11 -28,11t-28,-11L532,-372q-30,24 -69,38t-83,14Zm0,-80q75,0 127.5,-52.5T560,-580q0,-75 -52.5,-127.5T380,-760q-75,0 -127.5,52.5T200,-580q0,75 52.5,127.5T380,-400Z"

    const val CANCEL_PATH_D =
        "m480,-424 116,116q11,11 28,11t28,-11q11,-11 11,-28t-11,-28L536,-480l116,-116q11,-11 11,-28t-11,-28q-11,-11 -28,-11t-28,11L480,-536 364,-652q-11,-11 -28,-11t-28,11q-11,11 -11,28t11,28l116,116 -116,116q-11,11 -11,28t11,28q11,11 28,11t28,-11l116,-116Zm0,344q-83,0 -156,-31.5T197,-197q-54,-54 -85.5,-127T80,-480q0,-83 31.5,-156T197,-763q54,-54 127,-85.5T480,-880q83,0 156,31.5T763,-763q54,54 85.5,127T880,-480q0,83 -31.5,156T763,-197q-54,54 -127,85.5T480,-80Zm0,-80q134,0 227,-93t93,-227q0,-134 -93,-227t-227,-93q-134,0 -227,93t-93,227q0,134 93,227t227,93Z"

    const val CLOSE_PATH_D =
        "M480,-424 284,-228q-11,11 -28,11t-28,-11q-11,-11 -11,-28t11,-28l196,-196 -196,-196q-11,-11 -11,-28t11,-28q11,-11 28,-11t28,11l196,196 196,-196q11,-11 28,-11t28,11q11,11 11,28t-11,28L536,-480l196,196q11,11 11,28t-11,28q-11,11 -28,11t-28,-11L480,-424Z"

    const val SENSORS_PATH_D =
        "M160,-480q0,56 17,105.5t49,89.5q11,14 11,30.5T225,-226q-12,12 -29,11.5T168,-229q-42,-52 -65,-115.5T80,-480q0,-72 23,-136t65,-115q11,-14 28,-14.5t29,11.5q12,12 12,28.5T226,-675q-32,41 -49,90.5T160,-480Zm160,0q0,23 6,43.5t17,38.5q9,14 8.5,31T339,-338q-12,12 -29,11.5T284,-341q-21,-29 -32.5,-65T240,-480q0,-39 11.5,-74.5T284,-619q10,-14 26.5,-14t28.5,12q12,12 12.5,28.5T343,-562q-11,18 -17,38.5t-6,43.5Zm103.5,56.5Q400,-447 400,-480t23.5,-56.5Q447,-560 480,-560t56.5,23.5Q560,-513 560,-480t-23.5,56.5Q513,-400 480,-400t-56.5,-23.5ZM640,-480q0,-23 -6,-43.5T617,-562q-9,-14 -8,-31t13,-29q12,-12 28,-11.5t26,14.5q21,29 32.5,64.5T720,-480q0,38 -11.5,74T676,-341q-9,14 -26,14t-29,-12q-12,-12 -12.5,-28.5T617,-398q11,-18 17,-38.5t6,-43.5Zm160,0q0,-56 -17,-105.5T734,-675q-11,-14 -11.5,-30.5T734,-734q12,-12 29.5,-11.5T792,-731q42,51 65,115t23,136q0,72 -23,135.5T792,-229q-11,14 -28,14.5T735,-226q-12,-12 -12,-28.5t11,-30.5q32,-41 49,-90.5T800,-480Z"

    val MusicNote2: ImageVector by lazy {
        materialSymbolVector(name = "music_note_2", pathData = MUSIC_NOTE_2_PATH_D)
    }

    val MusicNote: ImageVector by lazy {
        materialSymbolVector(name = "music_note", pathData = MUSIC_NOTE_PATH_D)
    }

    val BarChart4Bars: ImageVector by lazy {
        materialSymbolVector(name = "bar_chart_4_bars", pathData = BAR_CHART_4_BARS_PATH_D)
    }

    val VitalSigns: ImageVector by lazy {
        materialSymbolVector(name = "vital_signs", pathData = VITAL_SIGNS_PATH_D)
    }

    val SettingsSlowMotion: ImageVector by lazy {
        materialSymbolVector(name = "settings_slow_motion", pathData = SETTINGS_SLOW_MOTION_PATH_D)
    }

    val Notes: ImageVector by lazy {
        materialSymbolVector(name = "notes", pathData = NOTES_PATH_D)
    }

    val PlayArrowFilled: ImageVector by lazy {
        materialSymbolVector(name = "play_arrow", pathData = PLAY_ARROW_FILLED_PATH_D)
    }

    val PauseFilled: ImageVector by lazy {
        materialSymbolVector(name = "pause", pathData = PAUSE_FILLED_PATH_D)
    }

    val StopFilled: ImageVector by lazy {
        materialSymbolVector(name = "stop", pathData = STOP_FILLED_PATH_D)
    }

    val Search: ImageVector by lazy {
        materialSymbolVector(name = "search", pathData = SEARCH_PATH_D)
    }

    val Cancel: ImageVector by lazy {
        materialSymbolVector(name = "cancel", pathData = CANCEL_PATH_D)
    }

    val Close: ImageVector by lazy {
        materialSymbolVector(name = "close", pathData = CLOSE_PATH_D)
    }

    val Sensors: ImageVector by lazy {
        materialSymbolVector(name = "sensors", pathData = SENSORS_PATH_D)
    }

    private fun materialSymbolVector(name: String, pathData: String): ImageVector {
        val nodes = PathParser().parsePathString(pathData).toNodes()
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            group(translationY = 960f) {
                addPath(
                    pathData = nodes,
                    pathFillType = PathFillType.NonZero,
                    fill = SolidColor(Color.White)
                )
            }
        }.build()
    }
}

/**
 * Full-bleed Compose rendering of `../chilltrax/icon.svg`:
 * - Left 3/4 (`0..180`): Black `#000000`
 * - Right 1/4 (`180..240`): Four vertical 60×60 color blocks (`#FA0000`, `#FF5252`, `#FFA8A8`, `#FFFFFF`)
 * - White radio wave paths (`ICON_SVG_CENTER_DOT_PATH_D`, `ICON_SVG_INNER_WAVE_PATH_D`, `ICON_SVG_OUTER_WAVE_PATH_D`)
 */
@Composable
fun ChilltraxIconSvgCanvas(
    modifier: Modifier = Modifier,
    isDark: Boolean = true
) {
    val leftBackgroundColor = ChilltraxTheme.visualizerBackground(isDark)
    val stripe1Color = if (isDark) ChilltraxTheme.AccentRed else Color(0xFFFF0505)
    val stripe2Color = if (isDark) ChilltraxTheme.CoralRed else Color(0xFFAD0000)
    val stripe3Color = if (isDark) ChilltraxTheme.SoftPink else Color(0xFF570000)
    val stripe4Color = if (isDark) ChilltraxTheme.VisualizerWhite else Color(0xFF000000)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val leftWidth = w * 0.75f
        val rightWidth = w - leftWidth
        val stripeHeight = h * 0.25f

        // Right 1/4 4-tier vertical blocks
        drawRect(
            color = stripe1Color,
            topLeft = Offset(leftWidth, 0f),
            size = Size(rightWidth, stripeHeight + 0.5f)
        )
        drawRect(
            color = stripe2Color,
            topLeft = Offset(leftWidth, stripeHeight),
            size = Size(rightWidth, stripeHeight + 0.5f)
        )
        drawRect(
            color = stripe3Color,
            topLeft = Offset(leftWidth, stripeHeight * 2f),
            size = Size(rightWidth, stripeHeight + 0.5f)
        )
        drawRect(
            color = stripe4Color,
            topLeft = Offset(leftWidth, stripeHeight * 3f),
            size = Size(rightWidth, stripeHeight)
        )

        // Left 3/4 solid panel
        drawRect(
            color = leftBackgroundColor,
            topLeft = Offset.Zero,
            size = Size(leftWidth + 0.5f, h)
        )
    }
}
