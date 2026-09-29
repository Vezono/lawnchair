package app.lawnchair.util

import android.content.ComponentName
import android.content.Context
import android.os.UserHandle
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import com.android.launcher3.util.ComponentKey

/**
 * Per-app opt-out from anything that surfaces an app's activity elsewhere in the launcher --
 * its long-press shortcuts, its entries in drawer search, and the predicted apps row.
 *
 * Hiding an app from the drawer does not cover this: a hidden app can still leak what it is used
 * for through a shortcut list or a prediction.
 */
object AppSuggestions {

    @JvmStatic
    fun isMuted(context: Context, component: ComponentName?, user: UserHandle?): Boolean {
        if (component == null || user == null) return false
        return isMuted(context, ComponentKey(component, user))
    }

    @JvmStatic
    fun isMuted(context: Context, key: ComponentKey): Boolean =
        key.toString() in PreferenceManager2.getInstance(context).noSuggestionApps.firstCached()

    /** Convenience for call sites that only know a package name, such as prediction keys. */
    @JvmStatic
    fun isPackageMuted(context: Context, packageName: String): Boolean =
        PreferenceManager2.getInstance(context).noSuggestionApps.firstCached()
            .any { it.substringBefore('/') == packageName }
}
