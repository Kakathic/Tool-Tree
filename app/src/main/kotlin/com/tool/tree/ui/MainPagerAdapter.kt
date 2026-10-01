package com.tool.tree.ui

import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.omarea.krscript.ui.ActionListFragment

class MainPagerAdapter(private val activity: AppCompatActivity) {

    private val fragmentList = ArrayList<ActionListFragment?>()
    private val fragmentTitles = ArrayList<String>()
    private val containers = ArrayList<FrameLayout>()
    private var pager: SwipePager? = null

    fun attach(pager: SwipePager) {
        this.pager = pager
    }

    fun addFragment(fragment: ActionListFragment, title: String) {
        val position = fragmentList.size
        fragmentList.add(fragment)
        fragmentTitles.add(title)

        val container = FrameLayout(activity).apply { id = View.generateViewId() }
        containers.add(container)
        pager?.addPage(container)

        if (activity.isFinishing || activity.isDestroyed) return
        activity.supportFragmentManager.beginTransaction()
            .add(container.id, fragment, "tool_tree_page_$position")
            .commitNowAllowingStateLoss()
    }

    fun replaceFragment(position: Int, fragment: ActionListFragment) {
        if (position !in containers.indices) return
        if (activity.isFinishing || activity.isDestroyed) return
        fragmentList[position] = fragment
        activity.supportFragmentManager.beginTransaction()
            .replace(containers[position].id, fragment, "tool_tree_page_$position")
            .commitNowAllowingStateLoss()
    }

    fun getFragment(position: Int): ActionListFragment? = fragmentList.getOrNull(position)

    fun getTitle(position: Int): String = fragmentTitles.getOrElse(position) { "" }

    fun getItemCount(): Int = fragmentList.size
}
