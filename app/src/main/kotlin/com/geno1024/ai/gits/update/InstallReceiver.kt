package com.geno1024.ai.gits.update

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.geno1024.ai.gits.R
import com.geno1024.ai.gits.data.AppSettings

/**
 * Reports how an install ended.
 *
 * A session install finishes after this app has stopped being foreground, so the result
 * has nowhere else to go. The platform says why an install was refused, and "a
 * different signing key" is the one a person can actually do something about.
 */
class InstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val text = when (val result = resultCode) {
            PackageInstaller.STATUS_SUCCESS ->
                context.getString(R.string.update_installed)
            PackageInstaller.STATUS_FAILURE_ABORTED ->
                context.getString(R.string.update_install_canceled)
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                context.getString(R.string.update_install_conflict)
            PackageInstaller.STATUS_FAILURE_INVALID ->
                context.getString(R.string.update_install_invalid)
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                context.getString(R.string.update_install_incompatible)
            PackageInstaller.STATUS_FAILURE_STORAGE ->
                context.getString(R.string.update_install_no_space)
            PackageInstaller.STATUS_FAILURE_TIMEOUT ->
                context.getString(R.string.update_install_timeout)
            PackageInstaller.STATUS_FAILURE_BLOCKED ->
                context.getString(R.string.update_install_blocked)
            else -> context.getString(R.string.update_install_failed, result)
        }
        AppSettings.of(context.applicationContext as Application).lastInstallOutcome = text
        notify(context, text)
    }

    private fun notify(context: Context, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.update_channel), IMPORTANCE)
            .also(manager::createNotificationChannel)
        manager.notify(NOTIFICATION_ID, Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setAutoCancel(true)
            .build())
    }

    companion object {
        const val ACTION = "com.geno1024.ai.gits.INSTALL_RESULT"

        private const val CHANNEL = "install"
        private const val NOTIFICATION_ID = 1
        private const val IMPORTANCE = NotificationManager.IMPORTANCE_DEFAULT
    }
}
