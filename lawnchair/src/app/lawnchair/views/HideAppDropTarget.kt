package app.lawnchair.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import app.lawnchair.preferences2.PreferenceManager2
import com.android.launcher3.ButtonDropTarget
import com.android.launcher3.DropTarget
import com.android.launcher3.R
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ComponentKey
import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.core.setBlocking

/**
 * Drop target that hides an app from the app drawer, alongside the existing Cancel and Uninstall
 * targets. Dropping here adds the app to [PreferenceManager2.hiddenApps], which is the same list
 * the Hidden apps settings screen edits.
 */
class HideAppDropTarget @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ButtonDropTarget(context, attrs, defStyleAttr) {

    private val preferenceManager2 = PreferenceManager2.getInstance(context)

    override fun onFinishInflate() {
        super.onFinishInflate()
        setDrawable(R.drawable.ic_visibility_slash)
    }

    override fun setupItemInfo(info: ItemInfo) = Unit

    /** Only apps can be hidden, and only ones not hidden already. */
    override fun supportsDrop(info: ItemInfo): Boolean {
        if (!HomeScreenDropTarget.isDraggingFromDrawer(context)) return false
        val key = componentKeyOf(info) ?: return false
        return key.toString() !in preferenceManager2.hiddenApps.firstBlocking()
    }

    override fun getSupportedAccessibilityAction(info: ItemInfo, view: View?): Int =
        if (supportsDrop(info)) accessibilityAction else LauncherAccessibilityDelegate.INVALID

    override fun getAccessibilityAction(): Int = R.id.action_hide_app

    override fun onAccessibilityDrop(view: View?, item: ItemInfo, action: Int) = hide(item)

    override fun completeDrop(d: DropTarget.DragObject) = hide(d.dragInfo)

    private fun hide(info: ItemInfo) {
        val key = componentKeyOf(info) ?: return
        val current = preferenceManager2.hiddenApps.firstBlocking()
        preferenceManager2.hiddenApps.setBlocking(current + key.toString())
    }

    private fun componentKeyOf(info: ItemInfo): ComponentKey? {
        val component = info.targetComponent ?: return null
        return ComponentKey(component, info.user)
    }
}
