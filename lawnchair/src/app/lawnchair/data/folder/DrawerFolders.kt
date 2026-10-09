package app.lawnchair.data.folder

import android.content.Context
import app.lawnchair.data.folder.service.FolderService
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Bridges edits made to an open folder back to the app drawer's own storage.
 *
 * Drawer folders live in Lawnchair's Room database, while [FolderInfo] edits are written through
 * the launcher's ModelWriter to the workspace database. A drawer folder is not in that database at
 * all, so without this its renames were dropped on the next reload.
 */
object DrawerFolders {

    /**
     * True for a folder that belongs to the app drawer. Drawer folders are built in memory and
     * left with the default [ItemInfo.NO_ID] container, whereas a workspace folder always sits in
     * a real container such as the desktop or the dock.
     */
    @JvmStatic
    fun isDrawerFolder(info: FolderInfo): Boolean = info.container == ItemInfo.NO_ID

    /** Persists a rename made in the open folder; a no-op for workspace folders. */
    @JvmStatic
    fun renameIfDrawerFolder(context: Context, info: FolderInfo, title: String) {
        if (!isDrawerFolder(info)) return
        val service = FolderService.INSTANCE.get(context)
        CoroutineScope(Dispatchers.Main).launch {
            service.renameFolderInfo(info.id, title)
        }
    }
}
