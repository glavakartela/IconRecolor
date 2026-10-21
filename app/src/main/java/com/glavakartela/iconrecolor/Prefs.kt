package com.glavakartela.iconrecolor

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("icrec", Context.MODE_PRIVATE)

    fun customShapes(ctx: Context): List<IconShape> =
        (sp(ctx).getString("shapes", "") ?: "").lines().mapNotNull { line ->
            val i = line.indexOf('|')
            if (i <= 0) return@mapNotNull null
            val q = ShapeCodec.decode(line.substring(i + 1)) ?: return@mapNotNull null
            IconShape(line.substring(0, i), q)
        }

    fun saveCustomShapes(ctx: Context, list: List<IconShape>) {
        val text = list.filter { !it.isLauncher }
            .joinToString("\n") { it.name.replace("|", " ").replace("\n", " ") + "|" + it.code }
        sp(ctx).edit().putString("shapes", text).apply()
    }

    fun selectedShape(ctx: Context): IconShape {
        val code = sp(ctx).getString("shape", null) ?: return IconShapes.LAUNCHER
        val q = ShapeCodec.decode(code) ?: return IconShapes.LAUNCHER
        return (IconShapes.presets + customShapes(ctx)).firstOrNull { it.q == q } ?: IconShape("Custom", q)
    }

    fun setSelectedShape(ctx: Context, shape: IconShape) {
        val e = sp(ctx).edit()
        val c = shape.code
        if (c == null) e.remove("shape") else e.putString("shape", c)
        e.apply()
    }

    fun method(ctx: Context, comp: ComponentName): Method =
        Method.entries.getOrElse(sp(ctx).getInt("m:" + comp.flattenToString(), 0)) { Method.AUTO }

    fun setMethod(ctx: Context, comp: ComponentName, m: Method) {
        val e = sp(ctx).edit()
        val k = "m:" + comp.flattenToString()
        if (m == Method.AUTO) e.remove(k) else e.putInt(k, m.ordinal)
        e.apply()
    }

    fun clearMethods(ctx: Context) {
        val e = sp(ctx).edit()
        for (k in sp(ctx).all.keys) if (k.startsWith("m:")) e.remove(k)
        e.apply()
    }

    /** App theme: 0 = system, 1 = light, 2 = dark. */
    fun themeMode(ctx: Context): Int = sp(ctx).getInt("theme", 0)

    fun setThemeMode(ctx: Context, mode: Int) {
        sp(ctx).edit().putInt("theme", mode).apply()
    }

    /** Tint the UI with the wallpaper color; when off, the UI is gray. */
    fun colorUi(ctx: Context): Boolean = sp(ctx).getBoolean("color_ui", true)

    fun setColorUi(ctx: Context, on: Boolean) {
        sp(ctx).edit().putBoolean("color_ui", on).apply()
    }
}

/** Wallpaper image picked by the user; used for the preview and for color extraction. */
object Backgrounds {
    private fun file(ctx: Context) = File(ctx.filesDir, "bg.jpg")

    fun loadSaved(ctx: Context): Bitmap? =
        file(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    suspend fun import(ctx: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val src = ImageDecoder.createSource(ctx.contentResolver, uri)
            val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val big = max(info.size.width, info.size.height)
                decoder.setTargetSampleSize(max(1, big / 1280))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            file(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bmp
        }.getOrNull()
    }

    fun clear(ctx: Context) {
        file(ctx).delete()
    }
}
