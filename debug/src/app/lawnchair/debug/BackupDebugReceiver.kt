package app.lawnchair.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import app.lawnchair.backup.LawnchairBackup
import app.lawnchair.data.AppDatabase
import app.lawnchair.data.iconoverride.IconOverride
import app.lawnchair.icons.picker.IconType
import app.lawnchair.icons.picker.IconPickerItem
import com.android.launcher3.util.ComponentKey
import android.os.Process
import app.lawnchair.preferences2.PreferenceManager2
import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.core.setBlocking
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Debug-only hook that drives backup and restore from adb, so the round trip can be exercised
 * without going through the storage access framework picker.
 *
 *   adb shell am broadcast -a app.lawnchair.debug.STATE   -n <pkg>/app.lawnchair.debug.BackupDebugReceiver
 *   adb shell am broadcast -a app.lawnchair.debug.BACKUP  -n <pkg>/... --es path /sdcard/x.backup
 *   adb shell am broadcast -a app.lawnchair.debug.RESTORE -n <pkg>/... --es path /sdcard/x.backup
 *   adb shell am broadcast -a app.lawnchair.debug.WIPE_FOLDERS -n <pkg>/...
 *
 * Results are written to logcat under [TAG].
 */
class BackupDebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val path = intent.getStringExtra("path")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_STATE -> dumpState(context)
                    ACTION_BACKUP -> backup(context, requireNotNull(path))
                    ACTION_RESTORE -> restore(context, requireNotNull(path))
                    ACTION_WIPE_FOLDERS -> wipeFolders(context)
                    ACTION_SET_ICON -> setIconOverride(context)
                    ACTION_ADD_TO_FOLDER -> addAppToFolder(context, intent.getStringExtra("component"))
                    ACTION_TOGGLE_HIDDEN -> toggleHidden(context, intent.getStringExtra("component"))
                    else -> Log.w(TAG, "unknown action ${intent.action}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "${intent.action} failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun dumpState(context: Context) {
        val db = AppDatabase.INSTANCE.get(context)
        val folders = db.folderDao().getAllFoldersWithItems().first()
        val hidden = PreferenceManager2.getInstance(context).hiddenApps.firstBlocking()
        Log.i(TAG, "STATE folders=${folders.size} " +
            folders.joinToString { "${it.folder.title}(${it.items.size} items)" })
        Log.i(TAG, "STATE hiddenApps=${hidden.size} ${hidden.sorted()}")
        val overrides = db.iconOverrideDao().observeAll().first()
        Log.i(TAG, "STATE iconOverrides=${overrides.size} " +
            overrides.joinToString { "${it.target.componentName.packageName}->${it.iconPickerItem.drawableName}" })
        val dir = context.getDatabasePath("preferences").parentFile
        Log.i(TAG, "STATE dbFiles=" + dir?.listFiles()?.joinToString { "${it.name}:${it.length()}" })
    }

    private suspend fun backup(context: Context, path: String) {
        val file = resolve(context, path)
        file.parentFile?.mkdirs()
        file.delete()
        file.createNewFile()
        val blank = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        LawnchairBackup.create(
            context = context,
            contents = LawnchairBackup.INCLUDE_LAYOUT_AND_SETTINGS,
            screenshotBitmap = blank,
            fileUri = Uri.fromFile(file),
        )
        Log.i(TAG, "BACKUP wrote ${file.length()} bytes to $path")
    }

    private suspend fun restore(context: Context, path: String) {
        val backup = LawnchairBackup(context, Uri.fromFile(resolve(context, path)))
        backup.readInfoAndPreview()
        backup.restore(LawnchairBackup.INCLUDE_LAYOUT_AND_SETTINGS)
        Log.i(TAG, "RESTORE done from $path")
    }

    /** Hides or unhides an app, which removes or restores one icon in the drawer list. */
    private fun toggleHidden(context: Context, component: String?) {
        val key = component ?: return
        val prefs = PreferenceManager2.getInstance(context)
        val current = prefs.hiddenApps.firstBlocking()
        val next = if (key in current) current - key else current + key
        prefs.hiddenApps.setBlocking(next)
        Log.i(TAG, "TOGGLE_HIDDEN $key hidden=${key !in current} total=${next.size}")
    }

    /** Writes exactly what a drawer folder drop writes, without the drag machinery. */
    private suspend fun addAppToFolder(context: Context, component: String?) {
        val dao = AppDatabase.INSTANCE.get(context).folderDao()
        val service = app.lawnchair.data.folder.service.FolderService.INSTANCE.get(context)
        val folder = dao.getAllFoldersWithItems().first().firstOrNull() ?: run {
            Log.w(TAG, "ADD_TO_FOLDER no folders")
            return
        }
        val key = component ?: "com.android.settings/com.android.settings.Settings#0"
        val existing = dao.getFolderItems(folder.folder.id).mapNotNull { it.componentKey }
        if (key in existing) {
            Log.i(TAG, "ADD_TO_FOLDER $key already in ${folder.folder.title}")
            return
        }
        service.updateFolderWithItems(folder.folder.id, folder.folder.title, existing + key)
        Log.i(TAG, "ADD_TO_FOLDER added $key to ${folder.folder.title}, now ${existing.size + 1}")
    }

    private suspend fun setIconOverride(context: Context) {
        val dao = AppDatabase.INSTANCE.get(context).iconOverrideDao()
        val target = ComponentKey(
            android.content.ComponentName("com.android.settings", "com.android.settings.Settings"),
            Process.myUserHandle(),
        )
        dao.insert(
            IconOverride(
                target = target,
                iconPickerItem = IconPickerItem(
                    packPackageName = "com.example.iconpack",
                    drawableName = "debug_test_icon",
                    label = "Debug Test Icon",
                    type = IconType.Normal,
                ),
            ),
        )
        Log.i(TAG, "SET_ICON inserted override for $target")
    }

    /** Absolute paths are used as-is; anything else lands in the app's external files dir. */
    private fun resolve(context: Context, path: String): File =
        if (path.startsWith("/")) File(path) else File(context.getExternalFilesDir(null), path)

    private suspend fun wipeFolders(context: Context) {
        val dao = AppDatabase.INSTANCE.get(context).folderDao()
        val ids = dao.getAllFolderIds()
        ids.forEach { dao.deleteFolder(it) }
        Log.i(TAG, "WIPE_FOLDERS removed $ids")
    }

    companion object {
        private const val TAG = "LcBackupDebug"
        private const val ACTION_STATE = "app.lawnchair.debug.STATE"
        private const val ACTION_BACKUP = "app.lawnchair.debug.BACKUP"
        private const val ACTION_RESTORE = "app.lawnchair.debug.RESTORE"
        private const val ACTION_WIPE_FOLDERS = "app.lawnchair.debug.WIPE_FOLDERS"
        private const val ACTION_SET_ICON = "app.lawnchair.debug.SET_ICON"
        private const val ACTION_ADD_TO_FOLDER = "app.lawnchair.debug.ADD_TO_FOLDER"
        private const val ACTION_TOGGLE_HIDDEN = "app.lawnchair.debug.TOGGLE_HIDDEN"
    }
}
