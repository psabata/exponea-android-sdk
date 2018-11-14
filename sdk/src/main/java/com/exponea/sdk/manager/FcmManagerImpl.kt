package com.exponea.sdk.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.support.v4.app.NotificationCompat
import com.exponea.sdk.Exponea
import com.exponea.sdk.models.ExponeaConfiguration
import com.exponea.sdk.models.NotificationData
import com.exponea.sdk.services.ExponeaPushReceiver
import com.exponea.sdk.util.Logger
import com.exponea.sdk.util.fromJson
import com.google.gson.Gson
import org.json.JSONArray
import java.io.IOException
import java.net.URL
import kotlin.concurrent.thread


class FcmManagerImpl(
        private val context: Context,
        private val configuration: ExponeaConfiguration
) : FcmManager {

    private val requestCode = 1
    private var pendingIntent: PendingIntent? = null
    private var smallIconRes = -1

    override fun showNotification(
            title: String,
            message: String,
            data: NotificationData?,
            id: Int,
            manager: NotificationManager,
            messageData: HashMap<String, String>
    ) {
        Logger.d(this, "showNotification")

        val i = ExponeaPushReceiver.getClickIntent(context, id, data, messageData)

        pendingIntent = PendingIntent.getBroadcast(
                context, requestCode,
                i, PendingIntent.FLAG_UPDATE_CURRENT
        )

        // If push icon was not provided in the configuration, default one will be used
        smallIconRes = configuration.pushIcon ?: android.R.drawable.ic_dialog_info

        // Icon id was provided but was invalid
        try {
            context.resources.getResourceName(smallIconRes)
        } catch (exception: Resources.NotFoundException) {
            Logger.e(this, "Invalid icon resource: $smallIconRes")
            smallIconRes = android.R.drawable.ic_dialog_info
        }

        val notification = NotificationCompat.Builder(context, configuration.pushChannelId)
                .setContentText(message)
                .setContentTitle(title)
                .setContentIntent(pendingIntent)
                .setChannelId(configuration.pushChannelId)
                .setSmallIcon(smallIconRes)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))

        handlePayload(notification, messageData)

        manager.notify(id, notification.build())
    }

    private fun handlePayload(notification: NotificationCompat.Builder, messageData: HashMap<String, String>) {
        handlePayloadImage(notification, messageData)
        handlePayloadSound(notification, messageData)
        handlePayloadButtons(notification, messageData)
        handlePayloadActions(notification, messageData)
        handlePayloadAttributes(messageData)
    }

    override fun createNotificationChannel(
            manager: NotificationManager
    ) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = configuration.pushChannelName
            val description = configuration.pushChannelDescription
            val importance = configuration.pushNotificationImportance
            val channel = NotificationChannel(configuration.pushChannelId, name, importance)

            channel.description = description
            channel.setShowBadge(true)
            // Remove the default notification sound as it can be customized via payload and we
            // can't change it after setting it
            channel.setSound(null, null)
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            manager.createNotificationChannel(channel)
        }
    }

    private fun handlePayloadImage(notification: NotificationCompat.Builder, messageData: HashMap<String, String>) {
        //Load the image in the payload and add as a big picture in the notification
        if (messageData["image"] != null) {
            val bigImageBitmap = getBitmapFromUrl(messageData["image"]!!)
            //verify if the image was successfully loaded
            if (bigImageBitmap != null) {
                notification.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bigImageBitmap))
            }
        }
    }

    private fun handlePayloadSound(notification: NotificationCompat.Builder, messageData: HashMap<String, String>) {
        // remove default notification sound
        notification.setSound(null)

        // set the uri for the default sound
        var soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // if the raw file exists, use it as custom sound
        if (messageData["sound"] != null && context.resources.getIdentifier(messageData["sound"], "raw", context.packageName) != 0) {
            soundUri =  Uri.parse("android.resource://" + context.packageName + "/raw/" + messageData["sound"])
        }

        // Manually play the notification sound
        RingtoneManager.getRingtone(context, soundUri)?.also { it.play() }
    }

    private fun handlePayloadButtons(notification: NotificationCompat.Builder, messageData: HashMap<String, String>) {
        if (messageData["actions"] != null) {
            val buttonsArray = JSONArray(messageData["actions"])

            //if we have a button payload, verify each button action
            for (i in 0 until buttonsArray.length()) {
                val item: Map<String, String> = Gson().fromJson(buttonsArray[i].toString())

                //create the button intent based in the action
                val actionEnum = ACTIONS.find(item["action"])
                val pi = generateActionPendingIntent(actionEnum, item["url"])
                notification.addAction(0, item["title"], pi)
            }
        }
    }

    private fun handlePayloadActions(notification: NotificationCompat.Builder, messageData: HashMap<String, String>) {
        //handle the notification body click action
        if (messageData["action"] != null) {
            val action = messageData["action"]
            val actionEnum = ACTIONS.find(action)
            var url = messageData["url"]

            if (url != null && actionEnum != null) {
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "http://$url"
                }
                val pi = generateActionPendingIntent(actionEnum, url)
                notification.setContentIntent(pi)
            }
        }
    }

    private fun handlePayloadAttributes(messageData: HashMap<String, String>) {
        if (messageData["attributes"] != null) {
            val item: Map<String, String> = Gson().fromJson(messageData["attributes"]!!)
            Logger.w(this, item.toString())

            if (Exponea.notificationDataCallback == null) {
                Exponea.component.pushNotificationRepository.setExtraData(item)
                return
            }
            Handler(Looper.getMainLooper()).post {
                Exponea.notificationDataCallback?.invoke(item)
            }
        }
    }

    private fun generateActionPendingIntent(action: ACTIONS?, url: String? = null): PendingIntent? {
        return when (action) {
            ACTIONS.APP -> pendingIntent
            ACTIONS.BROWSER -> {
                val actionIntent = Intent(Intent.ACTION_VIEW)
                actionIntent.data = Uri.parse(url)
                PendingIntent.getActivity(context, 0, actionIntent, 0)
            }
            ACTIONS.DEEPLINK -> {
                val deepIntent = Intent(Intent.ACTION_VIEW)
                deepIntent.data = Uri.parse(url)
                deepIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                PendingIntent.getActivity(context, 0, deepIntent, 0)
            }
            else -> pendingIntent
        }
    }

    private fun getBitmapFromUrl(url: String): Bitmap? {
        var bmp: Bitmap? = null
        thread {
            try {
                val options = BitmapFactory.Options()
                options.inSampleSize = 4 //reduce the image to avoid OOM in low end devices
                val input = URL(url).openStream()
                bmp = BitmapFactory.decodeStream(input, null, options)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }.join()
        return bmp
    }

    private enum class ACTIONS(val value: String) {
        APP("app"),
        BROWSER("browser"),
        DEEPLINK("deeplink");

        companion object {
            fun find(value: String?) = ACTIONS.values().find { it.value == value }
        }
    }
}