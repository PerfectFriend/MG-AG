package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class SimpleXPushService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(context: Context) {
            try {
                val intent = Intent(context, SimpleXPushService::class.java)
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, SimpleXPushService::class.java)
                context.stopService(intent)
            } catch (_: Exception) {}
        }
    }
}
