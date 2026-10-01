package com.omarea.common.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.tool.tree.R
import kotlinx.coroutines.*
import java.util.*

class AdapterAppChooser(
    private val context: Context,
    private var apps: ArrayList<AppInfo>,
    private val multiple: Boolean
) : BaseAdapter(), Filterable {

    interface SelectStateListener {
        fun onSelectChange(selected: List<AppInfo>)
    }

    open class AppInfo {
        var appName: String? = null
        var packageName: String? = null
        var notFound: Boolean = false
        var selected: Boolean = false
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private var selectStateListener: SelectStateListener? = null
    private var filter: Filter? = null

    internal var filterApps: ArrayList<AppInfo> = apps
    private val mLock = Any()

    private class ArrayFilter(private val adapter: AdapterAppChooser) : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val results = FilterResults()
            val prefix = constraint?.toString()?.lowercase(Locale.getDefault()) ?: ""

            if (prefix.isEmpty()) {
                synchronized(adapter.mLock) {
                    results.values = ArrayList(adapter.apps)
                    results.count = adapter.apps.size
                }
                return results
            }

            val selected = adapter.getSelectedItems()
            val newValues = ArrayList<AppInfo>()

            for (item in adapter.apps) {
                if (selected.contains(item)) {
                    newValues.add(item)
                    continue
                }

                val name = item.appName?.lowercase(Locale.getDefault()) ?: ""
                val pkg = item.packageName?.lowercase(Locale.getDefault()) ?: ""

                if (name.contains(prefix) || pkg.contains(prefix)) {
                    newValues.add(item)
                }
            }

            results.values = newValues
            results.count = newValues.size
            return results
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            if (results?.values is ArrayList<*>) {
                @Suppress("UNCHECKED_CAST")
                adapter.filterApps = results.values as ArrayList<AppInfo>
                adapter.notifyDataSetChanged()
            }
        }
    }

    override fun getFilter(): Filter {
        if (filter == null) filter = ArrayFilter(this)
        return filter!!
    }

    private val iconCaches = LruCache<String, Drawable>(100)

    override fun getCount(): Int = filterApps.size

    override fun getItem(position: Int): AppInfo = filterApps[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        val convertView = view ?: View.inflate(
            context,
            if (multiple) R.layout.app_multiple_chooser_item
            else R.layout.app_single_chooser_item,
            null
        )

        updateRow(position, convertView)
        return convertView
    }

    private fun loadIcon(app: AppInfo): Deferred<Drawable?> {
        return scope.async(Dispatchers.IO) {
            val pkg = app.packageName ?: return@async null

            iconCaches.get(pkg)?.let { return@async it }

            if (!app.notFound) {
                try {
                    val info = context.packageManager.getPackageInfo(pkg, 0)
                    val icon = info.applicationInfo?.loadIcon(context.packageManager)
                    if (icon != null) {
                        iconCaches.put(pkg, icon)
                        return@async icon
                    }
                } catch (_: Exception) {
                    app.notFound = true
                }
            }
            null
        }
    }

    fun updateRow(position: Int, convertView: View) {
        val item = getItem(position)

        val holder = (convertView.tag as? ViewHolder)
            ?: ViewHolder(convertView).also { convertView.tag = it }

        holder.itemTitle.text = item.appName ?: ""
        holder.itemDesc.text = item.packageName ?: ""
        holder.checkBox?.isChecked = item.selected

        convertView.setOnClickListener {
            if (multiple) {
                item.selected = !item.selected
                holder.checkBox?.isChecked = item.selected
            } else {
                apps.forEach { it.selected = false }
                item.selected = true
                notifyDataSetChanged()
            }
            selectStateListener?.onSelectChange(getSelectedItems())
        }

        val pkg = item.packageName
        holder.imgView.tag = pkg

        scope.launch {
            val icon = loadIcon(item).await()
            if (icon != null && holder.imgView.tag == pkg) {
                holder.imgView.setImageDrawable(icon)
            }
        }
    }

    fun setSelectAllState(allSelected: Boolean) {
        filterApps.forEach { it.selected = allSelected }
        notifyDataSetChanged()
        selectStateListener?.onSelectChange(getSelectedItems())
    }

    fun getSelectedItems(): List<AppInfo> =
        apps.filter { it.selected }

    fun setSelectStateListener(listener: SelectStateListener?) {
        this.selectStateListener = listener
    }

    fun release() {
        job.cancel()
        iconCaches.evictAll()
        selectStateListener = null
    }

    class ViewHolder(view: View) {
        val itemTitle: TextView = view.findViewById(R.id.ItemTitle)
        val itemDesc: TextView = view.findViewById(R.id.ItemDesc)
        val imgView: ImageView = view.findViewById(R.id.ItemIcon)
        val checkBox: CompoundButton? = view.findViewById(R.id.ItemChecBox)
    }
}