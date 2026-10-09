package com.example.cacheclean.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Загрузка иконок приложений с небольшим кэшем в памяти. */
object IconLoader {
    private const val SIZE_PX = 96
    private val cache = LruCache<String, ImageBitmap>(300)

    fun peek(packageName: String): ImageBitmap? = cache.get(packageName)

    fun load(context: Context, packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, SIZE_PX, SIZE_PX)
            drawable.draw(Canvas(bitmap))
            bitmap.asImageBitmap().also { cache.put(packageName, it) }
        } catch (e: Exception) {
            null
        }
    }
}
