package com.tetragon.app.utils.languageChangeUtils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.tetragon.app.R

open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.getLanguage(newBase)
        val context = LocaleHelper.setLocaleContext(newBase, lang)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Automatically scan for live updates every time any activity enters the foreground
        if (this.javaClass.simpleName != "SplashScreenActivity") {
            checkAppUpdateStatus { /* Standalone auto-blocking in background activities */ }
        }
    }

    /**
     * Queries Firestore to determine if the local client version meets the server minimums.
     * Invokes [onResult] with true if an update dialog is actively blocking execution, false otherwise.
     */
    protected fun checkAppUpdateStatus(onResult: (isUpdateRequired: Boolean) -> Unit) {
        val currentVersionCode = try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            0
        }

        val db = FirebaseFirestore.getInstance()
        db.collection("system").document("leaderboard").get()
            .addOnSuccessListener { document ->
                if (isFinishing || isDestroyed) {
                    onResult(false)
                    return@addOnSuccessListener
                }

                if (document.exists()) {
                    val requiredVersionCode = document.getLong("appVersionCode")?.toInt() ?: 0

                    if (currentVersionCode > 0 && requiredVersionCode > currentVersionCode) {
                        showUpdateDialog()
                        onResult(true)
                    } else {
                        onResult(false)
                    }
                } else {
                    onResult(false)
                }
            }
            .addOnFailureListener {
                // If network drops, fall through safely to let app run offline
                onResult(false)
            }
    }

    private fun showUpdateDialog() {
        if (isFinishing || isDestroyed) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_update_app, null)
        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false) // Force user to stay pinned here

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        val btnUpdate = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_update_action)

        btnUpdate.setOnClickListener {
            val appPackageName = packageName
            val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appPackageName")).apply {
                setPackage("com.android.vending")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

            try {
                startActivity(playStoreIntent)
            } catch (anfe: android.content.ActivityNotFoundException) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(webIntent)
            }
            finish()
        }
    }
}