package app.lawnchair.util

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.widget.Toast
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.R
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ComponentKey

/**
 * Requires the device credential before a protected app opens.
 *
 * Authentication is delegated to the system rather than to a PIN of our own: there is no sensible
 * way for a launcher to store and verify its own passcode, and the device already has one the user
 * trusts. The prompt accepts the device PIN, pattern or password, and a registered fingerprint or
 * face where the user has one.
 */
object AppProtection {

    /**
     * Component keys cleared to launch once. A successful prompt adds one and the launch that
     * follows consumes it, so every subsequent launch asks again.
     */
    private val granted = mutableSetOf<String>()

    @JvmStatic
    fun isProtected(context: Context, item: ItemInfo?): Boolean {
        val key = keyOf(item) ?: return false
        return key in PreferenceManager2.getInstance(context).protectedApps.firstCached()
    }

    /** True when this launch has to be held back for authentication. */
    @JvmStatic
    fun shouldPrompt(context: Context, item: ItemInfo?): Boolean {
        if (!isProtected(context, item)) return false
        val key = keyOf(item) ?: return false
        // Consume a grant left by a successful prompt, so the retried launch goes through.
        return !granted.remove(key)
    }

    /** Asks for the device credential and runs [onSuccess] only if it is given. */
    @JvmStatic
    fun prompt(activity: Activity, item: ItemInfo?, onSuccess: Runnable) {
        val key = keyOf(item) ?: return
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isDeviceSecure) {
            // Nothing to authenticate against. Refuse rather than open it unprotected.
            toast(activity, R.string.app_protection_needs_screen_lock)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            toast(activity, R.string.app_protection_unsupported)
            return
        }

        val builder = BiometricPrompt.Builder(activity)
            .setTitle(activity.getString(R.string.app_protection_prompt_title))
            .setDescription(item?.title?.toString().orEmpty())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.DEVICE_CREDENTIAL or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }

        builder.build().authenticate(
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    granted.add(key)
                    onSuccess.run()
                }
            },
        )
    }

    private fun toast(context: Context, resId: Int) {
        Toast.makeText(context, resId, Toast.LENGTH_LONG).show()
    }

    private fun keyOf(item: ItemInfo?): String? {
        val component = item?.targetComponent ?: return null
        return ComponentKey(component, item.user).toString()
    }
}
