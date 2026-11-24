package com.gogether.app.core.data.cloud

import android.content.Context
import com.gogether.app.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage

/** Explicit modes: a failed network never changes the selected backend to local Demo. */
data class FirebaseServices(val app: FirebaseApp, val auth: FirebaseAuth, val firestore: FirebaseFirestore,
                            val functions: FirebaseFunctions, val storage: FirebaseStorage,
                            val emulatorCallableUrl: String? = null)

object FirebaseRuntime {
    fun services(context: Context, name: String = "gogether-${BuildConfig.BACKEND_MODE}"): FirebaseServices {
        val existing = FirebaseApp.getApps(context).find { it.name == name }
        val app = existing ?: FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID).setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY).setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET).build(), name)
        val auth = FirebaseAuth.getInstance(app)
        val firestore = FirebaseFirestore.getInstance(app)
        val functions = FirebaseFunctions.getInstance(app, BuildConfig.FIREBASE_REGION)
        val storage = FirebaseStorage.getInstance(app)
        if (existing == null) {
            firestore.firestoreSettings = FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build()
            if (BuildConfig.BACKEND_MODE == "emulator") {
                val host = BuildConfig.FIREBASE_EMULATOR_HOST
                auth.useEmulator(host, 9099)
                firestore.useEmulator(host, 8080)
                functions.useEmulator(host, 5001)
                storage.useEmulator(host, 9199)
            }
        }
        val emulatorUrl = if (BuildConfig.BACKEND_MODE == "emulator")
            "http://${BuildConfig.FIREBASE_EMULATOR_HOST}:5001/${BuildConfig.FIREBASE_PROJECT_ID}/${BuildConfig.FIREBASE_REGION}/gogether" else null
        return FirebaseServices(app, auth, firestore, functions, storage, emulatorUrl)
    }
}
