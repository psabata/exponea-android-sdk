package com.exponea.sdk.util

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.util.*

fun Call.enqueue(onResponse: (Call, Response) -> Unit, onFailure: (Call, IOException) -> Unit) {
    this.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            onFailure(call, e)
        }

        override fun onResponse(call: Call, response: Response) {
            onResponse(call, response)
        }
    })
}

fun Context.addAppStateCallbacks(onOpen: () -> Unit, onClosed: () -> Unit) {
    (this as Application).registerActivityLifecycleCallbacks(object :
            Application.ActivityLifecycleCallbacks {
        private var activityCount: Int = 0
        override fun onActivityResumed(activity: Activity?) {
            onOpen()
            activityCount++
        }

        override fun onActivityStarted(activity: Activity?) {}
        override fun onActivityDestroyed(activity: Activity?) {}
        override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {}
        override fun onActivityStopped(activity: Activity?) {}
        override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {}
        override fun onActivityPaused(activity: Activity?) {
            activityCount--
            if (activityCount <= 0) {
                onClosed()
            }
        }
    })
    this.registerComponentCallbacks(object : ComponentCallbacks2 {
        override fun onLowMemory() {}

        override fun onConfigurationChanged(newConfig: Configuration?) {}

        override fun onTrimMemory(level: Int) {
            if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
                onClosed()
            }
        }
    })
}

fun Double.toDate(): Date {
    return Date((this * 1000).toLong())
}

fun currentTimeSeconds(): Double {
    return Date().time / 1000.0
}

inline fun <reified T> Gson.fromJson(json: String) = this.fromJson<T>(json, object: TypeToken<T>() {}.type)
