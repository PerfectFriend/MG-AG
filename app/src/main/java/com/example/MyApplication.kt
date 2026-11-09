package com.example

import android.app.Application
import android.util.Log
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
            Log.i("MyApplication", "BouncyCastle PQC provider registered successfully")
        } catch (e: Throwable) {
            Log.w("MyApplication", "Could not register BouncyCastle provider: ${e.message}")
        }
    }
}
