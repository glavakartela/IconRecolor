package com.glavakartela.iconrecolor

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Bundle

object Shortcuts {
    private const val PREFIX = "rc:"
    const val ACTION_LAUNCH = "com.glavakartela.iconrecolor.LAUNCH"
    const val EXTRA_TARGET = "target"

    private fun manager(ctx: Context): ShortcutManager =
        ctx.getSystemService(ShortcutManager::class.java)

    /** Canvas size: an adaptive bitmap is 1.5x the maximum icon size. */
    fun iconSize(ctx: Context): Int =
        (manager(ctx).iconMaxWidth * 1.5f).toInt().coerceIn(216, 576)

    fun pinnedKeys(ctx: Context): Set<String> = runCatching {
        manager(ctx).pinnedShortcuts
            .filter { it.id.startsWith(PREFIX) }
            .map { it.id.removePrefix(PREFIX) }
            .toSet()
    }.getOrDefault(emptySet())

    fun pin(ctx: Context, app: AppEntry, style: IconStyle): Boolean {
        val sm = manager(ctx)
        if (!sm.isRequestPinShortcutSupported) return false
        // The shortcut targets our invisible trampoline activity, which launches the real app
        val intent = Intent(ctx, LaunchActivity::class.java)
            .setAction(ACTION_LAUNCH)
            .putExtra(EXTRA_TARGET, app.component.flattenToString())
        val info = ShortcutInfo.Builder(ctx, PREFIX + app.component.flattenToString())
            .setShortLabel(app.label)
            .setIcon(Recolor.shortcutIcon(ctx, app.component, style, iconSize(ctx)))
            .setIntent(intent)
            .build()
        return sm.requestPinShortcut(info, null)
    }

    /**
     * Recolors already pinned shortcuts without asking for confirmation again.
     * Returns the number updated; 0 if none are pinned; -1 if the system rejected the update.
     */
    fun updatePinned(ctx: Context, style: IconStyle): Int {
        val sm = manager(ctx)
        val size = iconSize(ctx)
        val updates = sm.pinnedShortcuts
            .filter { it.id.startsWith(PREFIX) }
            .mapNotNull { s ->
                val comp = ComponentName.unflattenFromString(s.id.removePrefix(PREFIX))
                    ?: return@mapNotNull null
                runCatching {
                    ShortcutInfo.Builder(ctx, s.id)
                        .setIcon(Recolor.shortcutIcon(ctx, comp, style, size))
                        .build()
                }.getOrNull()
            }
        if (updates.isEmpty()) return 0
        return if (sm.updateShortcuts(updates)) updates.size else -1
    }
}

/**
 * Invisible trampoline: launches the target app the way a regular launcher does
 * (without resetting its task) and finishes immediately.
 */
class LaunchActivity : Activity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = intent.getStringExtra(Shortcuts.EXTRA_TARGET)
            ?.let { ComponentName.unflattenFromString(it) }
        if (target != null) {
            val launch = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            try {
                startActivity(launch)
            } catch (e: Exception) {
                // app was uninstalled or is unavailable
            }
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
