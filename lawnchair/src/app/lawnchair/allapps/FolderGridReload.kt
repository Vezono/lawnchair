package app.lawnchair.allapps

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.lawnchair.preferences2.ReloadHelper

/**
 * Coalesces the grid reload that follows a drawer folder change.
 *
 * Every all-apps list observes the folder flow separately -- the main list, the search list and
 * one more -- so a single folder edit asked for three full device profile rebuilds in the same
 * frame, around 240ms of main thread work and a visible freeze. Requests arriving inside the
 * coalescing window collapse into one.
 */
internal object FolderGridReload {

    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    /**
     * Writing a folder touches both the Folders and the FolderItems table, and the flow observes
     * both, so Room invalidates twice -- the first carrying the new title with the old items.
     * Waiting briefly collapses those into a single reload of the settled state.
     */
    private const val COALESCE_MS = 150L

    fun request(context: Context) {
        pending?.let(handler::removeCallbacks)
        val reload = Runnable {
            pending = null
            ReloadHelper(context).reloadGrid()
        }
        pending = reload
        handler.postDelayed(reload, COALESCE_MS)
    }
}
