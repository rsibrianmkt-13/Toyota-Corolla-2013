package com.bryan.corolladash.ui.components

import android.content.res.Resources
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

/**
 * Fotos grandes del Inicio decodificadas una sola vez por sesión. painterResource vuelve a
 * decodificar la imagen cada vez que la pantalla entra en composición (al cambiar de vista o sección).
 */
object ImageCache {
    private val cache = HashMap<Int, ImageBitmap>()

    @Synchronized
    fun get(res: Resources, @DrawableRes id: Int): ImageBitmap = cache.getOrPut(id) {
        BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inScaled = false }).asImageBitmap()
    }
}

@Composable
fun cachedImage(@DrawableRes id: Int): ImageBitmap {
    val res = LocalContext.current.resources
    return remember(id) { ImageCache.get(res, id) }
}
