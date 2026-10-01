package com.omarea.krscript.ui

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.omarea.common.ui.AdapterAppChooser
import com.omarea.common.ui.DialogAppChooser
import com.tool.tree.R
import com.omarea.krscript.model.ActionParamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import com.tool.tree.ThemeModeState

class ParamsAppChooserRender(
    private var actionParamInfo: ActionParamInfo,
    private var context: FragmentActivity,
    private val onValueChanged: (() -> Unit)? = null
) : DialogAppChooser.Callback {

    private val darkMode: Boolean = ThemeModeState.isDarkMode()
    private lateinit var valueView: TextView
    private lateinit var nameView: TextView
    private lateinit var packages: ArrayList<AdapterAppChooser.AppInfo>

    private val collator: Collator = Collator.getInstance(Locale.getDefault())

    private val selectedValues: Set<String>
        get() = if (actionParamInfo.multiple) {
            valueView.text.toString()
                .split(actionParamInfo.separator)
                .filter { it.isNotEmpty() }
                .toSet()
        } else {
            valueView.text.toString()
                .takeIf { it.isNotEmpty() }
                ?.let { setOf(it) }
                ?: emptySet()
        }

    fun render(): View {
        val layout = LayoutInflater.from(context)
            .inflate(R.layout.kr_param_app, null)

        valueView = layout.findViewById(R.id.kr_param_app_package)
        nameView = layout.findViewById(R.id.kr_param_app_name)

        setTextView()
        resolveCurrentAppName()

        layout.findViewById<View>(R.id.kr_param_app_btn).setOnClickListener {
            openAppChooser()
        }
        nameView.setOnClickListener {
            openAppChooser()
        }

        valueView.tag = actionParamInfo.name
        return layout
    }

    private fun openAppChooser() {
        packages = ArrayList()
    
        val dialog = DialogAppChooser(
            darkMode,
            packages,
            actionParamInfo.multiple,
            this
        )
    
        dialog.show(context.supportFragmentManager, "app-chooser")
        dialog.showLoading(true)
    
        loadPackagesAsync(dialog, actionParamInfo.type == "packages")
    }

    private fun insertSorted(
        list: MutableList<AdapterAppChooser.AppInfo>,
        item: AdapterAppChooser.AppInfo
    ) {
        val isSelected =
            item.packageName != null &&
            selectedValues.contains(item.packageName)
    
        item.selected = isSelected
    
        var low = 0
        var high = list.size
        val name = item.appName ?: ""
    
        while (low < high) {
            val mid = (low + high) ushr 1
            val m = list[mid]
    
            val mSelected =
                m.packageName != null &&
                selectedValues.contains(m.packageName)
    
            when {
                mSelected != isSelected ->
                    if (isSelected) high = mid else low = mid + 1
    
                collator.compare(m.appName ?: "", name) < 0 ->
                    low = mid + 1
    
                else -> high = mid
            }
        }
    
        list.add(low, item)
    }

    private fun loadPackagesAsync(
        dialog: DialogAppChooser,
        includeMissing: Boolean
    ) {
        val pm = context.packageManager

        val filterSet = actionParamInfo.optionsFromShell
            ?.mapNotNull { it.value }
            ?.toHashSet()

        context.lifecycleScope.launch(Dispatchers.IO) {
            val apps = pm.getInstalledApplications(PackageManager.MATCH_ALL)

            val result = HashMap<String, AdapterAppChooser.AppInfo>(apps.size)
            val batch = ArrayList<AdapterAppChooser.AppInfo>(20)

            for ((index, app) in apps.withIndex()) {
                val pkg = app.packageName

                if (filterSet == null || filterSet.contains(pkg)) {
                    val info = AdapterAppChooser.AppInfo().apply {
                        packageName = pkg
                        appName = app.loadLabel(pm)?.toString()
                    }
                    result[pkg] = info
                    batch.add(info)
                }

                if (batch.size == 20 || index == apps.lastIndex) {
                    val copy = ArrayList(batch)
                    batch.clear()

                    withContext(Dispatchers.Main) {
                        for (info in copy) {
                            if (packages.any { it.packageName == info.packageName }) continue
                            insertSorted(packages, info)
                        }
                        dialog.notifyDataChanged()
                    }
                }
            }

            if (includeMissing && actionParamInfo.optionsFromShell != null) {
                val missing = ArrayList<AdapterAppChooser.AppInfo>()
                for (item in actionParamInfo.optionsFromShell!!) {
                    val pkg = item.value
                    if (pkg != null && !result.containsKey(pkg)) {
                        missing.add(
                            AdapterAppChooser.AppInfo().apply {
                                packageName = pkg
                                appName = item.title
                            }
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    for (info in missing) {
                        insertSorted(packages, info)
                    }
                    dialog.notifyDataChanged()
                }
            }

            withContext(Dispatchers.Main) {
                dialog.notifyDataChanged()
                dialog.showLoading(false)
            }
        }
    }

    private fun setTextView() {
        if (actionParamInfo.multiple) {
            val values = ActionParamsLayoutRender
                .getParamValues(actionParamInfo)
                ?.joinToString(actionParamInfo.separator)
                ?: ""

            valueView.text = values
            nameView.text = values
        } else {
            val value = ActionParamsLayoutRender
                .getParamValues(actionParamInfo)
                ?.firstOrNull()
                ?: ""

            valueView.text = value
            nameView.text = value
        }
    }

    private fun resolveCurrentAppName() {
        val pm = context.packageManager
    
        if (actionParamInfo.multiple) {
            val pkgs = valueView.text.toString()
                .split(actionParamInfo.separator)
                .filter { it.isNotEmpty() }
    
            val names = ArrayList<String>(pkgs.size)
            for (pkg in pkgs) {
                try {
                    val app = pm.getApplicationInfo(pkg, 0)
                    names.add(app.loadLabel(pm)?.toString() ?: pkg)
                } catch (_: Exception) {
                    names.add(pkg)
                }
            }
            nameView.text = names.joinToString("，")
        } else {
            val pkg = valueView.text.toString()
            if (pkg.isNotEmpty()) {
                try {
                    val app = pm.getApplicationInfo(pkg, 0)
                    nameView.text = app.loadLabel(pm)?.toString() ?: pkg
                } catch (_: Exception) {
                    nameView.text = pkg
                }
            }
        }
    }

    override fun onConfirm(apps: List<AdapterAppChooser.AppInfo>) {
        if (actionParamInfo.multiple) {
            valueView.text =
                apps.joinToString(actionParamInfo.separator) { it.packageName ?: "" }
            nameView.text =
                apps.joinToString("，") { it.appName ?: it.packageName ?: "" }
        } else {
            val item = apps.firstOrNull()
            valueView.text = item?.packageName ?: ""
            nameView.text = item?.appName ?: item?.packageName ?: ""
        }
        onValueChanged?.invoke()
    }
}