package com.glavakartela.iconrecolor

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import kotlin.system.exitProcess

/** Runs in a separate process with shell privileges (like `adb shell`); started by Shizuku. */
class UserService : IUserService.Stub() {

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String = try {
        val p = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out.trim()
    } catch (e: Exception) {
        "error: ${e.message}"
    }
}

object ShizukuHelper {
    private const val REQUEST_CODE = 1001

    @Volatile
    private var service: IUserService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = if (binder != null && binder.pingBinder()) {
                IUserService.Stub.asInterface(binder)
            } else {
                null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    private fun args(ctx: Context) =
        Shizuku.UserServiceArgs(ComponentName(ctx.packageName, UserService::class.java.name))
            .processNameSuffix("shell")
            .daemon(false)
            .version(1)

    fun isRunning(): Boolean = Shizuku.pingBinder()

    fun hasPermission(): Boolean =
        isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    fun isConnected(): Boolean = service != null

    /** Requests Shizuku permission and binds the service. Returns a status message for the UI. */
    fun connect(ctx: Context): String {
        if (!isRunning()) {
            return "Shizuku isn't running: open the Shizuku app and start it (wireless debugging or adb)"
        }
        if (!hasPermission()) {
            Shizuku.requestPermission(REQUEST_CODE)
            return "Grant access in the Shizuku dialog and tap “Connect” again"
        }
        if (service == null) Shizuku.bindUserService(args(ctx), connection)
        return "Connecting…"
    }

    /** Runs a command with shell privileges. Must not be called on the main thread. */
    fun exec(command: String): String =
        service?.exec(command) ?: "Shizuku service not connected"

    /** Xiaomi: the "Home screen shortcuts" permission is appop 10017. */
    fun grantShortcutPermission(ctx: Context): String {
        val pkg = ctx.packageName
        val out = exec("appops set $pkg 10017 allow; appops get $pkg 10017")
        return out.ifBlank { "Done" }
    }
}
