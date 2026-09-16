package com.minimalarchiver.app

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku

private const val SHIZUKU_REQUEST_CODE = 9001

/**
 * Bridges the app to Shizuku via a bound UserService (not the deprecated
 * reflection-based Shizuku.newProcess()), matching the approach used in
 * the AI-Helper project. The service runs with shell-UID privileges, so
 * commands executed through it can reach paths outside this app's sandbox
 * (e.g. /data) subject to shell's own permissions.
 *
 * Known limitation: created shell processes are not force-killed on
 * unbind (no destroy() transaction wired up in this minimal build) —
 * they exit on their own once their command finishes.
 */
class ShizukuBridge(
    private val packageName: String,
    private val onShellReady: (IShellService?) -> Unit
) {
    var shellService: IShellService? = null
        private set

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(packageName, ShellUserService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(false)
            .version(1)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            shellService = IShellService.Stub.asInterface(service)
            onShellReady(shellService)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            shellService = null
            onShellReady(null)
        }
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) bindService()
    }

    fun isAvailable(): Boolean = try { Shizuku.pingBinder() } catch (t: Throwable) { false }

    fun hasPermission(): Boolean =
        isAvailable() && try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (t: Throwable) { false }

    fun requestPermission() {
        if (!isAvailable()) return
        if (hasPermission()) { bindService(); return }
        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
    }

    fun bindService() {
        Shizuku.bindUserService(userServiceArgs, connection)
    }

    fun unbindService() {
        try { Shizuku.unbindUserService(userServiceArgs, connection, true) } catch (t: Throwable) { }
        try { Shizuku.removeRequestPermissionResultListener(permissionListener) } catch (t: Throwable) { }
    }

    fun exec(command: String): String =
        try {
            shellService?.exec(command) ?: "ERROR: shell service مش متصل"
        } catch (t: Throwable) {
            "ERROR: ${t.message}"
        }
}
