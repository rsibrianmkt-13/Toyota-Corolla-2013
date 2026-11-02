package com.bryan.corolladash.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Iconos monolineales estilo SF Symbols, generados desde los mismos trazos del prototipo web
 * (viewBox 24×24). Se tiñen con `Icon(tint = …)`.
 */
object DashIcons {
    private class Builder(private val b: ImageVector.Builder) {
        /** Forma rellena con agujeros (regla evenOdd), para iconos "fill". */
        fun solid(d: String) {
            b.addPath(pathData = addPathNodes(d), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black))
        }

        fun p(d: String, fill: Boolean, stroke: Boolean, width: Float, alpha: Float) {
            b.addPath(
                pathData = addPathNodes(d),
                fill = if (fill) SolidColor(Color.Black) else null,
                fillAlpha = alpha,
                stroke = if (stroke) SolidColor(Color.Black) else null,
                strokeAlpha = alpha,
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }

    private fun icon(name: String, block: Builder.() -> Unit): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        Builder(b).block()
        return b.build()
    }

    val Home: ImageVector by lazy {
      icon("home") {
        p("M3.2 11.4 12 3.8l8.8 7.6V20a1.4 1.4 0 0 1-1.4 1.4h-4.7v-6.2H9.3v6.2H4.6A1.4 1.4 0 0 1 3.2 20z", fill = true, stroke = false, width = 1.6f, alpha = 1.0f)
      }
    }


    val Music: ImageVector by lazy {
      icon("music") {
        p("M9.2 17.6V5.9l10.6-2.3v11.6", fill = false, stroke = true, width = 1.6f, alpha = 1.0f)
        p("M3.6000000000000005 17.7A2.8 2.4 0 1 0 9.2 17.7A2.8 2.4 0 1 0 3.6000000000000005 17.7Z", fill = true, stroke = true, width = 1.6f, alpha = 1.0f)
        p("M14.2 15.3A2.8 2.4 0 1 0 19.8 15.3A2.8 2.4 0 1 0 14.2 15.3Z", fill = true, stroke = true, width = 1.6f, alpha = 1.0f)
      }
    }

    val Aa: ImageVector by lazy {
      icon("aa") {
        p("M12 3.2 20 20.6l-8-4.2-8 4.2z", fill = false, stroke = true, width = 1.6f, alpha = 1.0f)
        p("M12 9.6l3.3 7.4L12 15.3 8.7 17z", fill = true, stroke = false, width = 1.6f, alpha = 1.0f)
      }
    }

    /** Engrane relleno (estilo "gearshape.fill"): 8 dientes y agujero central. */
    val Gear: ImageVector by lazy {
      icon("gear") {
        solid("M10.31 4.18 L10.45 1.51 L13.55 1.51 L13.69 4.18 A8.0 8.0 0 0 1 16.33 5.28 L18.32 3.49 L20.51 5.68 L18.72 7.67 A8.0 8.0 0 0 1 19.82 10.31 L22.49 10.45 L22.49 13.55 L19.82 13.69 A8.0 8.0 0 0 1 18.72 16.33 L20.51 18.32 L18.32 20.51 L16.33 18.72 A8.0 8.0 0 0 1 13.69 19.82 L13.55 22.49 L10.45 22.49 L10.31 19.82 A8.0 8.0 0 0 1 7.67 18.72 L5.68 20.51 L3.49 18.32 L5.28 16.33 A8.0 8.0 0 0 1 4.18 13.69 L1.51 13.55 L1.51 10.45 L4.18 10.31 A8.0 8.0 0 0 1 5.28 7.67 L3.49 5.68 L5.68 3.49 L7.67 5.28 A8.0 8.0 0 0 1 10.31 4.18Z M8.6 12a3.4 3.4 0 1 0 6.8 0a3.4 3.4 0 1 0 -6.8 0Z")
      }
    }

    val Bt: ImageVector by lazy {
      icon("bt") {
        p("M6.8 7.4 17 16.4 12 21V3l5 4.6L6.8 16.6", fill = false, stroke = true, width = 1.6f, alpha = 1.0f)
      }
    }

    val Wifi: ImageVector by lazy {
      icon("wifi") {
        p("M2.4 9a14.8 14.8 0 0 1 19.2 0M5.5 12.6a10.1 10.1 0 0 1 13 0M8.7 16.1a5.2 5.2 0 0 1 6.6 0", fill = false, stroke = true, width = 2.1f, alpha = 1.0f)
        p("M10.5 19.4A1.5 1.5 0 1 0 13.5 19.4A1.5 1.5 0 1 0 10.5 19.4Z", fill = true, stroke = false, width = 1.6f, alpha = 1.0f)
      }
    }

    val Gauge: ImageVector by lazy {
      icon("gauge") {
        p("M3.8 17.4a8.8 8.8 0 1 1 16.4 0", fill = false, stroke = true, width = 1.6f, alpha = 1.0f)
        p("M12 6.4v1.5M6.6 9.1l1 1M17.4 9.1l-1 1M5.2 13.8h1.5M17.3 13.8h1.5", fill = false, stroke = true, width = 1.4f, alpha = 1.0f)
        p("M12 14.3l3.6-4.2", fill = false, stroke = true, width = 2.0f, alpha = 1.0f)
        p("M10.5 14.4A1.5 1.5 0 1 0 13.5 14.4A1.5 1.5 0 1 0 10.5 14.4Z", fill = true, stroke = false, width = 1.6f, alpha = 1.0f)
      }
    }

    val Radio: ImageVector by lazy {
      icon("radio") {
        p("M5.6 8.6H18.4A2.6 2.6 0 0 1 21.0 11.2V17.599999999999998A2.6 2.6 0 0 1 18.4 20.2H5.6A2.6 2.6 0 0 1 3.0 17.599999999999998V11.2A2.6 2.6 0 0 1 5.6 8.6Z", fill = false, stroke = true, width = 1.6f, alpha = 1.0f)
        p("M7.2 8.6l9.2-4.3", fill = false, stroke = true, width = 1.6f, alpha = 1.0f)
        p("M6.2 14.4A2.2 2.2 0 1 0 10.600000000000001 14.4A2.2 2.2 0 1 0 6.2 14.4Z", fill = false, stroke = true, width = 1.6f, alpha = 1.0f)
        p("M13.6 12.9h4M13.6 15.9h4", fill = false, stroke = true, width = 1.6f, alpha = 1.0f)
      }
    }

    val Grid: ImageVector by lazy {
      icon("grid") {
        p("M5.6 3.6H8.9A2.0 2.0 0 0 1 10.9 5.6V8.9A2.0 2.0 0 0 1 8.9 10.9H5.6A2.0 2.0 0 0 1 3.6 8.9V5.6A2.0 2.0 0 0 1 5.6 3.6Z", fill = true, stroke = false, width = 1.6f, alpha = 1.0f)
        p("M15.1 3.6H18.4A2.0 2.0 0 0 1 20.4 5.6V8.9A2.0 2.0 0 0 1 18.4 10.9H15.1A2.0 2.0 0 0 1 13.1 8.9V5.6A2.0 2.0 0 0 1 15.1 3.6Z", fill = true, stroke = false, width = 1.6f, alpha = 1.0f)
        p("M5.6 13.1H8.9A2.0 2.0 0 0 1 10.9 15.1V18.4A2.0 2.0 0 0 1 8.9 20.4H5.6A2.0 2.0 0 0 1 3.6 18.4V15.1A2.0 2.0 0 0 1 5.6 13.1Z", fill = true, stroke = false, width = 1.6f, alpha = 1.0f)
        p("M15.1 13.1H18.4A2.0 2.0 0 0 1 20.4 15.1V18.4A2.0 2.0 0 0 1 18.4 20.4H15.1A2.0 2.0 0 0 1 13.1 18.4V15.1A2.0 2.0 0 0 1 15.1 13.1Z", fill = true, stroke = false, width = 1.6f, alpha = 1.0f)
      }
    }

    val Chev: ImageVector by lazy {
      icon("chev") {
        p("M9 5.5 15.5 12 9 18.5", fill = false, stroke = true, width = 2.0f, alpha = 1.0f)
      }
    }

    val Prev: ImageVector by lazy {
      icon("prev") {
        p("M19.6 5.4v13.2L10.2 12z", fill = true, stroke = true, width = 1.4f, alpha = 1.0f)
        p("M5.6 5.4v13.2", fill = false, stroke = true, width = 2.6f, alpha = 1.0f)
      }
    }

    val Next: ImageVector by lazy {
      icon("next") {
        p("M4.4 5.4v13.2l9.4-6.6z", fill = true, stroke = true, width = 1.4f, alpha = 1.0f)
        p("M18.4 5.4v13.2", fill = false, stroke = true, width = 2.6f, alpha = 1.0f)
      }
    }

    val Play: ImageVector by lazy {
      icon("play") {
        p("M8 4.8v14.4L19.4 12z", fill = true, stroke = true, width = 1.4f, alpha = 1.0f)
      }
    }

    val Pause: ImageVector by lazy {
      icon("pause") {
        p("M7.8999999999999995 4.6H9.099999999999998A1.3 1.3 0 0 1 10.399999999999999 5.8999999999999995V18.099999999999998A1.3 1.3 0 0 1 9.099999999999998 19.4H7.8999999999999995A1.3 1.3 0 0 1 6.6 18.099999999999998V5.8999999999999995A1.3 1.3 0 0 1 7.8999999999999995 4.6Z", fill = true, stroke = false, width = 1.6f, alpha = 1.0f)
        p("M14.9 4.6H16.099999999999998A1.3 1.3 0 0 1 17.4 5.8999999999999995V18.099999999999998A1.3 1.3 0 0 1 16.099999999999998 19.4H14.9A1.3 1.3 0 0 1 13.6 18.099999999999998V5.8999999999999995A1.3 1.3 0 0 1 14.9 4.6Z", fill = true, stroke = false, width = 1.6f, alpha = 1.0f)
      }
    }

    val Moon: ImageVector by lazy {
      icon("moon") {
        p("M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z", fill = false, stroke = true, width = 1.6f, alpha = 1.0f)
      }
    }

    val Eq: ImageVector by lazy {
      icon("eq") {
        p("M5 20v-6M5 10V4M12 20v-9M12 7V4M19 20v-4M19 12V4M2.6 14h4.8M9.6 7h4.8M16.6 16h4.8", fill = false, stroke = true, width = 1.6f, alpha = 1.0f)
      }
    }

    val Sun: ImageVector by lazy {
      icon("sun") {
        p("M8.0 12.0A4.0 4.0 0 1 0 16.0 12.0A4.0 4.0 0 1 0 8.0 12.0Z", fill = false, stroke = true, width = 1.6f, alpha = 1.0f)
        p("M12 2.6v2M12 19.4v2M2.6 12h2M19.4 12h2M5.4 5.4l1.4 1.4M17.2 17.2l1.4 1.4M5.4 18.6l1.4-1.4M17.2 6.8l1.4-1.4", fill = false, stroke = true, width = 1.6f, alpha = 1.0f)
      }
    }

    val Vol: ImageVector by lazy {
      icon("vol") {
        p("M4 9.2v5.6h3.8L13 19V5L7.8 9.2z", fill = true, stroke = true, width = 1.4f, alpha = 1.0f)
        p("M16.3 8.7a4.8 4.8 0 0 1 0 6.6M18.8 6.2a8.4 8.4 0 0 1 0 11.6", fill = false, stroke = true, width = 1.6f, alpha = 1.0f)
      }
    }

    val X: ImageVector by lazy {
      icon("x") {
        p("M6 6l12 12M18 6 6 18", fill = false, stroke = true, width = 2.0f, alpha = 1.0f)
      }
    }

    val Check: ImageVector by lazy {
      icon("check") {
        p("m5 12.5 4.5 4.5L19 7.5", fill = false, stroke = true, width = 2.4f, alpha = 1.0f)
      }
    }

    val Phone: ImageVector by lazy {
      icon("phone") {
        p("M9.0 2.6H14.999999999999998A2.4 2.4 0 0 1 17.4 5.0V19.000000000000004A2.4 2.4 0 0 1 14.999999999999998 21.400000000000002H9.0A2.4 2.4 0 0 1 6.6 19.000000000000004V5.0A2.4 2.4 0 0 1 9.0 2.6Z", fill = false, stroke = true, width = 1.6f, alpha = 1.0f)
        p("M10.6 18.2h2.8", fill = false, stroke = true, width = 1.6f, alpha = 1.0f)
      }
    }

    // ---- Iconos añadidos en v0.2 (mismo estilo monolineal 24×24)

    val ChevL: ImageVector by lazy {
      icon("chevl") {
        p("M15 5.5 8.5 12 15 18.5", fill = false, stroke = true, width = 2.0f, alpha = 1.0f)
      }
    }

    val GridO: ImageVector by lazy {
      icon("grido") {
        p("M5.6 3.8h3.5a1.8 1.8 0 0 1 1.8 1.8v3.5a1.8 1.8 0 0 1-1.8 1.8H5.6a1.8 1.8 0 0 1-1.8-1.8V5.6a1.8 1.8 0 0 1 1.8-1.8z", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
        p("M14.9 3.8h3.5a1.8 1.8 0 0 1 1.8 1.8v3.5a1.8 1.8 0 0 1-1.8 1.8h-3.5a1.8 1.8 0 0 1-1.8-1.8V5.6a1.8 1.8 0 0 1 1.8-1.8z", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
        p("M5.6 13.1h3.5a1.8 1.8 0 0 1 1.8 1.8v3.5a1.8 1.8 0 0 1-1.8 1.8H5.6a1.8 1.8 0 0 1-1.8-1.8v-3.5a1.8 1.8 0 0 1 1.8-1.8z", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
        p("M14.9 13.1h3.5a1.8 1.8 0 0 1 1.8 1.8v3.5a1.8 1.8 0 0 1-1.8 1.8h-3.5a1.8 1.8 0 0 1-1.8-1.8v-3.5a1.8 1.8 0 0 1 1.8-1.8z", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
      }
    }

    val Download: ImageVector by lazy {
      icon("download") {
        p("M12 3.5v11.5M7 10.5l5 5 5-5M4.5 19.5h15", fill = false, stroke = true, width = 1.8f, alpha = 1.0f)
      }
    }

    val Info: ImageVector by lazy {
      icon("info") {
        p("M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0z", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
        p("M12 11v6", fill = false, stroke = true, width = 2.0f, alpha = 1.0f)
        p("M10.9 7.8a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0-2.2 0z", fill = true, stroke = false, width = 1.0f, alpha = 1.0f)
      }
    }

    val Lock: ImageVector by lazy {
      icon("lock") {
        p("M6 10.5h12a1.5 1.5 0 0 1 1.5 1.5v7a1.5 1.5 0 0 1-1.5 1.5H6A1.5 1.5 0 0 1 4.5 19v-7A1.5 1.5 0 0 1 6 10.5z", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
        p("M8 10.5V7.5a4 4 0 0 1 8 0v3", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
      }
    }

    val Star: ImageVector by lazy {
      icon("star") {
        p("M12 3.5l2.6 5.3 5.8.8-4.2 4.1 1 5.8L12 16.8l-5.2 2.7 1-5.8-4.2-4.1 5.8-.8z", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
      }
    }

    val More: ImageVector by lazy {
      icon("more") {
        p("M4.6 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0-3.2 0z", fill = true, stroke = false, width = 1.0f, alpha = 1.0f)
        p("M10.4 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0-3.2 0z", fill = true, stroke = false, width = 1.0f, alpha = 1.0f)
        p("M16.2 12a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0-3.2 0z", fill = true, stroke = false, width = 1.0f, alpha = 1.0f)
      }
    }

    val Display: ImageVector by lazy {
      icon("display") {
        p("M5 4.5h14a1.5 1.5 0 0 1 1.5 1.5v9a1.5 1.5 0 0 1-1.5 1.5H5A1.5 1.5 0 0 1 3.5 15V6A1.5 1.5 0 0 1 5 4.5z", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
        p("M9 20.5h6M12 16.5v4", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
      }
    }

    val Wrench: ImageVector by lazy {
      icon("wrench") {
        p("M14.7 6.3a4 4 0 0 0 5 5L13 18a2.1 2.1 0 0 1-3-3l6.7-6.7", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
        p("M14.7 6.3 9 12 5 8l1.5-1.5L9 9l4.2-4.2", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
      }
    }

    /** Flecha de navegación rellena (misma familia "fill" del sidebar). */
    val AaFill: ImageVector by lazy {
      icon("aafill") {
        solid("M12 2.8 20.3 21 12 16.6 3.7 21z")
      }
    }

    // ---- v0.3

    /** Auricular relleno (sidebar Teléfono y botones de llamada; girado 135° = colgar). */
    val PhoneFill: ImageVector by lazy {
      icon("phonefill") {
        solid("M20.01 15.38c-1.23 0-2.42-.2-3.53-.56-.35-.12-.74-.03-1.01.24l-1.57 1.97c-2.83-1.35-5.48-3.9-6.89-6.83l1.95-1.66c.27-.28.35-.67.24-1.02-.37-1.11-.56-2.3-.56-3.53 0-.54-.45-.99-.99-.99H4.19C3.65 3 3 3.24 3 3.99 3 13.28 10.73 21 20.01 21c.71 0 .99-.63.99-1.18v-3.45c0-.54-.45-.99-.99-.99z")
      }
    }

    val Clock: ImageVector by lazy {
      icon("clock") {
        p("M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0z", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
        p("M12 7v5l3.3 2.2", fill = false, stroke = true, width = 1.8f, alpha = 1.0f)
      }
    }

    val Power: ImageVector by lazy {
      icon("power") {
        p("M12 3.2v8.3", fill = false, stroke = true, width = 1.9f, alpha = 1.0f)
        p("M7.1 6.4a7.6 7.6 0 1 0 9.8 0", fill = false, stroke = true, width = 1.9f, alpha = 1.0f)
      }
    }

    // ---- v0.4

    val Shuffle: ImageVector by lazy {
      icon("shuffle") {
        p("M3 7h3.2c2 0 3.2 1 4.3 2.7l2.6 4.6c1.1 1.7 2.3 2.7 4.3 2.7H20.5M17.5 14l3 3-3 3M3 17h3.2c1.4 0 2.4-.5 3.2-1.4M14.4 8.4c.8-.9 1.8-1.4 3-1.4h3.1M17.5 4l3 3-3 3", fill = false, stroke = true, width = 1.8f, alpha = 1.0f)
      }
    }

    val Repeat: ImageVector by lazy {
      icon("repeat") {
        p("M4 11.5V9.8A2.8 2.8 0 0 1 6.8 7H20M17 4l3 3-3 3M20 12.5v1.7A2.8 2.8 0 0 1 17.2 17H4M7 20l-3-3 3-3", fill = false, stroke = true, width = 1.8f, alpha = 1.0f)
      }
    }

    /** Repetir una canción: el mismo trazo con un "1" pequeño al centro. */
    val RepeatOne: ImageVector by lazy {
      icon("repeatone") {
        p("M4 11.5V9.8A2.8 2.8 0 0 1 6.8 7H20M17 4l3 3-3 3M20 12.5v1.7A2.8 2.8 0 0 1 17.2 17H4M7 20l-3-3 3-3", fill = false, stroke = true, width = 1.8f, alpha = 1.0f)
        p("M11.2 10.6 12.6 9.8v4.6", fill = false, stroke = true, width = 1.6f, alpha = 1.0f)
      }
    }

    /** Letra: líneas de texto con una nota. */
    val Lyrics: ImageVector by lazy {
      icon("lyrics") {
        p("M3.5 6.5h12M3.5 11h12M3.5 15.5h7", fill = false, stroke = true, width = 1.8f, alpha = 1.0f)
        p("M18.6 17.6V9.8l2.4-.8", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
        p("M14.2 17.6a2.2 1.9 0 1 0 4.4 0a2.2 1.9 0 1 0-4.4 0z", fill = true, stroke = false, width = 1.0f, alpha = 1.0f)
      }
    }

    val Expand: ImageVector by lazy {
      icon("expand") {
        p("M14 4h6v6M20 4l-6.5 6.5M10 20H4v-6M4 20l6.5-6.5", fill = false, stroke = true, width = 1.9f, alpha = 1.0f)
      }
    }

    val ChevD: ImageVector by lazy {
      icon("chevd") {
        p("M5.5 9 12 15.5 18.5 9", fill = false, stroke = true, width = 2.0f, alpha = 1.0f)
      }
    }

    val Search: ImageVector by lazy {
      icon("search") {
        p("M4 10.5a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0-13 0zM15.3 15.3 20 20", fill = false, stroke = true, width = 1.9f, alpha = 1.0f)
      }
    }

    // ---- v0.5

    /** Posición GPS (mira). */
    val Gps: ImageVector by lazy {
      icon("gps") {
        p("M12 3v2.5M12 18.5V21M3 12h2.5M18.5 12H21", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
        p("M5.5 12a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0-13 0z", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
        p("M9.8 12a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0-4.4 0z", fill = true, stroke = false, width = 1.0f, alpha = 1.0f)
      }
    }

    // ---- v0.8: clima y combustible

    val Cloud: ImageVector by lazy {
      icon("cloud") {
        p("M7 18.5h10.2a3.9 3.9 0 0 0 .5-7.8A5.6 5.6 0 0 0 6.9 9.4a4.6 4.6 0 0 0 .1 9.1z", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
      }
    }

    val PartlyCloudy: ImageVector by lazy {
      icon("partly") {
        p("M8.2 5.2V3.6M3.6 9.8H2M4.9 6.5 3.8 5.4M12.6 6.5l1.1-1.1", fill = false, stroke = true, width = 1.6f, alpha = 1.0f)
        p("M5.4 11.6a3.3 3.3 0 0 1 5.9-3", fill = false, stroke = true, width = 1.6f, alpha = 1.0f)
        p("M9 20h9.2a3.4 3.4 0 0 0 .4-6.8 4.9 4.9 0 0 0-9.3-1.1A4 4 0 0 0 9 20z", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
      }
    }

    val Rain: ImageVector by lazy {
      icon("rain") {
        p("M7 14.5h10.2a3.6 3.6 0 0 0 .5-7.2A5.3 5.3 0 0 0 7.4 6.1 4.2 4.2 0 0 0 7 14.5z", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
        p("M8.5 17.2 7.6 19.6M12.4 17.2l-.9 2.4M16.3 17.2l-.9 2.4", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
      }
    }

    val Storm: ImageVector by lazy {
      icon("storm") {
        p("M7 14.5h10.2a3.6 3.6 0 0 0 .5-7.2A5.3 5.3 0 0 0 7.4 6.1 4.2 4.2 0 0 0 7 14.5z", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
        p("M12.8 15.6 10.6 19h3l-2 3.2", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
      }
    }

    val Fog: ImageVector by lazy {
      icon("fog") {
        p("M4 9h16M3 13h13M6 17h14", fill = false, stroke = true, width = 1.8f, alpha = 1.0f)
      }
    }

    /** Surtidor de combustible. */
    val Fuel: ImageVector by lazy {
      icon("fuel") {
        p("M5 20.5V5.6A1.6 1.6 0 0 1 6.6 4h6.3a1.6 1.6 0 0 1 1.6 1.6v14.9M3.8 20.5h11.9M5 10.2h9.5", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
        p("M14.5 9.2h1.6a1.6 1.6 0 0 1 1.6 1.6v5.4a1.3 1.3 0 0 0 2.6 0V8.6l-2.6-2.8", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
      }
    }

    // ---- v0.9

    val Briefcase: ImageVector by lazy {
      icon("briefcase") {
        p("M5 7.5h14a1.6 1.6 0 0 1 1.6 1.6v8.8a1.6 1.6 0 0 1-1.6 1.6H5a1.6 1.6 0 0 1-1.6-1.6V9.1A1.6 1.6 0 0 1 5 7.5z", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
        p("M9 7.5V5.8A1.3 1.3 0 0 1 10.3 4.5h3.4A1.3 1.3 0 0 1 15 5.8v1.7M3.4 12.6h17.2", fill = false, stroke = true, width = 1.7f, alpha = 1.0f)
      }
    }
}
