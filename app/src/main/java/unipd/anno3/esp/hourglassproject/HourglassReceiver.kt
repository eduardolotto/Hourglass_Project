package unipd.anno3.esp.hourglassproject

import android.Manifest
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat

class HourglassReceiver: BroadcastReceiver() {

    companion object {
        private const val CHANNEL_ID = "hourglassChannel"
        private const val NOTIFICATION_ID = 16263
    }

    override fun onReceive(context: Context, intent: Intent?) {

        // Build the notification
        val notificationBuilder: Notification.Builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                Notification.Builder(context, CHANNEL_ID)
            else Notification.Builder(context)
        notificationBuilder.setSmallIcon(R.mipmap.ic_launcher)
        notificationBuilder.setContentTitle("My Hourglass")
        notificationBuilder.setContentText("The upper bulb of the hourglass is empty!")
        notificationBuilder.setAutoCancel(true)
        val notification = notificationBuilder.build() // Requires API level 16

        val notificationManagerCompat : NotificationManagerCompat = NotificationManagerCompat.from(context)

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return

        notificationManagerCompat.notify(NOTIFICATION_ID, notification)

    }

}