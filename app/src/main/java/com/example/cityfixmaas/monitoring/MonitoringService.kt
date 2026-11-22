package com.example.cityfixmaas.monitoring

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.cityfixmaas.CityFixApp
import com.example.cityfixmaas.MainActivity
import com.example.cityfixmaas.R
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/**
 * Keeps Impact Detection (citizens) or the 20 m geofence (operators) running when the app is in
 * the background. Android only allows that in a foreground service, which must show an ongoing
 * notification. Started and stopped by AppShell after sign-in / sign-out.
 */
class MonitoringService : Service() {

    private val scope = MainScope()

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val operator = intent?.getBooleanExtra(EXTRA_OPERATOR, false) == true
        createChannels(this)
        val ongoing = NotificationCompat.Builder(this, CHANNEL_MONITORING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(if (operator) R.string.monitoring_operator else R.string.monitoring_citizen))
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(this, ONGOING_ID, ongoing, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)

        val container = (application as CityFixApp).container
        scope.coroutineContext.cancelChildren() // started again for another user: replace the old work
        scope.launch {
            if (operator) {
                OperatorGeofence(container.tickets, container.location).run()
            } else {
                ImpactDetector.impacts(this@MonitoringService).collect { onImpact() }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** On screen: a dialog in the app. In the background: a notification that opens the report form. */
    private fun onImpact() {
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            ImpactDetector.detectedInApp.tryEmit(Unit)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val openReport = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ROAD_DEFECT, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_IMPACT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.impact_dialog_title))
            .setContentText(getString(R.string.impact_dialog_text))
            .setContentIntent(openReport)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(IMPACT_ID, notification)
    }

    companion object {
        private const val EXTRA_OPERATOR = "operator"
        private const val CHANNEL_MONITORING = "monitoring"
        private const val CHANNEL_IMPACT = "impact"
        private const val ONGOING_ID = 1
        private const val IMPACT_ID = 2

        /** Needs the location permission (a location foreground service), so callers check it first. */
        fun start(context: Context, operator: Boolean) {
            ContextCompat.startForegroundService(context, Intent(context, MonitoringService::class.java).putExtra(EXTRA_OPERATOR, operator))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitoringService::class.java))
        }

        private fun createChannels(context: Context) {
            val manager = NotificationManagerCompat.from(context)
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL_MONITORING, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(context.getString(R.string.monitoring_channel)).build(),
            )
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL_IMPACT, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.impact_dialog_title)).build(),
            )
        }
    }
}
