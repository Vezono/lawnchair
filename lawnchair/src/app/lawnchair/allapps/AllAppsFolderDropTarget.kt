package app.lawnchair.allapps

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import app.lawnchair.data.folder.service.FolderService
import app.lawnchair.views.HomeScreenDropTarget
import com.android.launcher3.BubbleTextView
import com.android.launcher3.DropTarget
import com.android.launcher3.Launcher
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ComponentKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Lets a drag that stays in the app drawer be dropped onto another app to create a drawer folder,
 * or onto an existing drawer folder to add to it -- the same gesture the home screen has always
 * had for workspace folders.
 *
 * Also scrolls the list when the drag nears its top or bottom edge, without which only the apps
 * already on screen could be reached.
 */
class AllAppsFolderDropTarget(private val launcher: Launcher) : DropTarget {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val folderService get() = FolderService.INSTANCE.get(launcher)

    /** The icon currently under the finger, scaled up to show it would receive the drop. */
    private var hovered: View? = null

    private val recyclerView get() = launcher.appsView?.activeRecyclerView

    override fun isDropEnabled(): Boolean =
        HomeScreenDropTarget.isDraggingFromDrawer(launcher) && recyclerView != null

    override fun getHitRectRelativeToDragLayer(outRect: Rect) {
        val rv = recyclerView ?: run { outRect.setEmpty(); return }
        launcher.dragLayer.getDescendantRectRelativeToSelf(rv, outRect)
    }

    override fun onDragEnter(d: DropTarget.DragObject) = Unit

    override fun onDragOver(d: DropTarget.DragObject) {
        setHovered(findDropCandidate(d))
        edgeScroll(d)
    }

    override fun onDragExit(d: DropTarget.DragObject) = setHovered(null)

    override fun prepareAccessibilityDrop() = Unit

    override fun acceptDrop(d: DropTarget.DragObject): Boolean = findDropCandidate(d) != null

    override fun onDrop(d: DropTarget.DragObject, options: com.android.launcher3.dragndrop.DragOptions) {
        val candidate = findDropCandidate(d)
        setHovered(null)
        val draggedKey = componentKeyOf(d.dragInfo) ?: return
        when (val target = candidate?.let { tagOf(it) }) {
            is FolderInfo -> addToFolder(target, draggedKey)
            is AppInfo -> createFolder(target, draggedKey)
            else -> Unit
        }
    }

    /** Existing folder: append the app, keeping what is already in it. */
    private fun addToFolder(folder: FolderInfo, draggedKey: ComponentKey) {
        val existing = folder.getAppContents().mapNotNull { componentKeyOf(it)?.toString() }
        val dragged = draggedKey.toString()
        if (dragged in existing) return
        scope.launch {
            folderService.updateFolderWithItems(folder.id, folder.title?.toString().orEmpty(), existing + dragged)
        }
    }

    /** Two loose apps: make a folder holding both, named after the one dropped onto. */
    private fun createFolder(target: AppInfo, draggedKey: ComponentKey) {
        val targetKey = componentKeyOf(target)?.toString() ?: return
        val dragged = draggedKey.toString()
        if (targetKey == dragged) return
        val title = target.title?.toString().orEmpty()
        scope.launch {
            val id = folderService.saveFolderInfo(title).toInt()
            folderService.updateFolderWithItems(id, title, listOf(targetKey, dragged))
        }
    }

    /** The icon under the finger, if it can take the drop. */
    private fun findDropCandidate(d: DropTarget.DragObject): View? {
        val rv = recyclerView ?: return null
        val hit = Rect()
        launcher.dragLayer.getDescendantRectRelativeToSelf(rv, hit)
        val child = rv.findChildViewUnder((d.x - hit.left).toFloat(), (d.y - hit.top).toFloat())
            ?: return null
        val tag = tagOf(child) ?: return null
        val draggedKey = componentKeyOf(d.dragInfo)
        return when {
            tag is FolderInfo -> child
            // Dropping an app on itself is a no-op, not a one-app folder.
            tag is AppInfo && componentKeyOf(tag) != draggedKey -> child
            else -> null
        }
    }

    /** Drawer folders are a FolderIcon inside a container, so look one level in as well. */
    private fun tagOf(view: View): ItemInfo? {
        (view.tag as? ItemInfo)?.let { return it }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child is FolderIcon || child is BubbleTextView) {
                    return child.tag as? ItemInfo
                }
            }
        }
        return null
    }

    private fun setHovered(view: View?) {
        if (hovered === view) return
        hovered?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(HOVER_ANIM_MS)?.start()
        hovered = view
        hovered?.animate()?.scaleX(HOVER_SCALE)?.scaleY(HOVER_SCALE)?.setDuration(HOVER_ANIM_MS)?.start()
    }

    /** Scroll the list when the drag is held near its top or bottom edge. */
    private fun edgeScroll(d: DropTarget.DragObject) {
        val rv = recyclerView ?: return
        val hit = Rect()
        launcher.dragLayer.getDescendantRectRelativeToSelf(rv, hit)
        val zone = (hit.height() * EDGE_SCROLL_ZONE).toInt()
        val delta = when {
            d.y < hit.top + zone -> -EDGE_SCROLL_STEP_PX
            d.y > hit.bottom - zone -> EDGE_SCROLL_STEP_PX
            else -> return
        }
        rv.scrollBy(0, delta)
    }

    private fun componentKeyOf(info: ItemInfo?): ComponentKey? {
        val component = info?.targetComponent ?: return null
        return ComponentKey(component, info.user)
    }

    companion object {
        private const val HOVER_SCALE = 1.15f
        private const val HOVER_ANIM_MS = 120L
        private const val EDGE_SCROLL_ZONE = 0.12f
        private const val EDGE_SCROLL_STEP_PX = 18
    }
}
