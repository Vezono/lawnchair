package app.lawnchair.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.android.launcher3.ButtonDropTarget
import com.android.launcher3.DropTarget
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate
import com.android.launcher3.model.data.ItemInfo

/**
 * Drop target shown while dragging out of the app drawer. Dragging onto it leaves the drawer and
 * brings up the home screen *without ending the drag*, so the app can then be placed in a chosen
 * cell. It is a hand-off rather than a drop: [acceptDrop] is false, so releasing on the button
 * itself simply returns the app to the drawer.
 */
class HomeScreenDropTarget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ButtonDropTarget(context, attrs, defStyleAttr) {

    override fun onFinishInflate() {
        super.onFinishInflate()
        setDrawable(R.drawable.ic_home_screen)
    }

    override fun setupItemInfo(info: ItemInfo) = Unit

    /** Only meaningful for a drag that started in the drawer; on the home screen we are already there. */
    override fun supportsDrop(info: ItemInfo): Boolean = isDraggingFromDrawer(context)

    private var handedOver = false

    override fun onDragStart(dragObject: DropTarget.DragObject, options: DragOptions) {
        super.onDragStart(dragObject, options)
        handedOver = false
    }

    // onDragEnter is final in ButtonDropTarget, so the hand-off runs from the hover callback.
    override fun onDragOver(d: DropTarget.DragObject) {
        super.onDragOver(d)
        if (handedOver) return
        val launcher = Launcher.getLauncher(context)
        if (!launcher.isInState(LauncherState.ALL_APPS)) return
        handedOver = true
        // The drag session keeps running, so the app follows the finger onto the home screen and
        // is placed by an ordinary workspace drop.
        launcher.stateManager.goToState(LauncherState.SPRING_LOADED)
    }

    /** Releasing on the button itself is treated as a cancel; the app is still in the drawer. */
    override fun completeDrop(d: DropTarget.DragObject) = Unit

    override fun getSupportedAccessibilityAction(info: ItemInfo, view: View?): Int =
        LauncherAccessibilityDelegate.INVALID

    override fun getAccessibilityAction(): Int = LauncherAccessibilityDelegate.INVALID

    override fun onAccessibilityDrop(view: View?, item: ItemInfo, action: Int) = Unit

    companion object {
        /**
         * True when the launcher is showing the app drawer, which is where drawer drags now stay
         * for their whole duration.
         */
        @JvmStatic
        fun isDraggingFromDrawer(context: Context): Boolean =
            Launcher.getLauncher(context).isInState(LauncherState.ALL_APPS)
    }
}
