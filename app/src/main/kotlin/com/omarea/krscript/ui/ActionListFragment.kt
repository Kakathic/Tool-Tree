package com.omarea.krscript.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.omarea.krscript.WebLauncher
import androidx.lifecycle.lifecycleScope
import com.omarea.common.model.SelectItem
import com.omarea.common.ui.DialogFullScreen
import com.omarea.common.ui.DialogHelper
import com.omarea.common.ui.DialogItemChooser
import com.omarea.common.ui.ProgressBarDialog
import com.omarea.common.ui.ThemeMode
import com.omarea.krscript.BgTaskThread
import com.omarea.krscript.HiddenTaskThread
import com.omarea.krscript.downloader.DownloadTaskHelper
import com.tool.tree.R
import com.omarea.krscript.TryOpenActivity
import com.omarea.krscript.config.IconPathAnalysis
import com.omarea.krscript.config.ActionShowMemory
import com.omarea.krscript.config.ActionParamMemory
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.model.*
import com.omarea.krscript.shortcut.ActionShortcutManager
import com.tool.tree.ThemeModeState
import kotlinx.coroutines.*

class ActionListFragment : androidx.fragment.app.Fragment(), PageLayoutRender.OnItemClickListener {
    companion object {
        private const val PROGRESSIVE_PLACEHOLDER_COUNT_DEFAULT = 1

        fun create(
                actionInfos: ArrayList<NodeInfoBase>?,
                krScriptActionHandler: KrScriptActionHandler? = null,
                autoRunTask: AutoRunTask? = null,
                themeMode: ThemeMode? = null,
                onRendered: (() -> Unit)? = null): ActionListFragment {
            val fragment = ActionListFragment()
            fragment.setListData(actionInfos, krScriptActionHandler, autoRunTask, themeMode, onRendered)
            return fragment
        }

        fun createProgressive(
                krScriptActionHandler: KrScriptActionHandler? = null,
                autoRunTask: AutoRunTask? = null,
                themeMode: ThemeMode? = null,
                placeholderCount: Int = PROGRESSIVE_PLACEHOLDER_COUNT_DEFAULT): ActionListFragment {
            val fragment = ActionListFragment()
            fragment.progressiveMode = true
            fragment.placeholderCount = placeholderCount
            fragment.setListData(ArrayList(), krScriptActionHandler, autoRunTask, themeMode)
            return fragment
        }
    }

    private var actionInfos: ArrayList<NodeInfoBase>? = null
    private lateinit var progressBarDialog: ProgressBarDialog
    private var activeLoadJob: Job? = null
    private var krScriptActionHandler: KrScriptActionHandler? = null
    private var autoRunTask: AutoRunTask? = null
    private var themeMode: ThemeMode? = null
    private var pageLayoutRender: PageLayoutRender? = null
    private lateinit var rootGroup: ListItemGroup
    private var onRendered: (() -> Unit)? = null

    private var progressiveMode = false
    private var placeholderCount = PROGRESSIVE_PLACEHOLDER_COUNT_DEFAULT
    private val pendingProgressiveItems = ArrayList<NodeInfoBase>()

    private var lastClickTime: Long = 0

    private fun checkAndLockClick(): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime < 800) {
            return false
        }
        lastClickTime = currentTime
        return true
    }

    private fun setListData(
        actionInfos: ArrayList<NodeInfoBase>?,
        krScriptActionHandler: KrScriptActionHandler? = null,
        autoRunTask: AutoRunTask? = null,
        themeMode: ThemeMode? = null,
        onRendered: (() -> Unit)? = null) {
        this.actionInfos = actionInfos
        this.krScriptActionHandler = krScriptActionHandler
        this.autoRunTask = autoRunTask
        this.themeMode = themeMode
        this.onRendered = onRendered
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.kr_action_list_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        this.progressBarDialog = ProgressBarDialog(this.requireActivity())
        if (progressiveMode) {
            setupProgressiveRoot()
        } else {
            renderInterface()
        }
    }

    private fun renderInterface() {
        val context = context ?: run { onRendered?.invoke(); return }
        val currentActionInfos = actionInfos ?: run { onRendered?.invoke(); return }
        rootGroup = ListItemGroup(context, true, GroupNode(""))
        pageLayoutRender = PageLayoutRender(context, currentActionInfos, this, rootGroup)
        val layout = rootGroup.getView()
        val rootView = (this.view?.findViewById<ScrollView?>(R.id.kr_content))
        rootView?.removeAllViews()
        rootView?.addView(layout)
        triggerAction(autoRunTask)
        onRendered?.invoke()
    }

    private fun setupProgressiveRoot() {
        val context = context ?: return
        val currentActionInfos = actionInfos ?: ArrayList()
        rootGroup = ListItemGroup(context, true, GroupNode(""))
        pageLayoutRender = PageLayoutRender(context, currentActionInfos, this, rootGroup)
        val layout = rootGroup.getView()
        val rootView = (this.view?.findViewById<ScrollView?>(R.id.kr_content))
        rootView?.removeAllViews()
        rootView?.addView(layout)

        pageLayoutRender?.addLoadingPlaceholders(placeholderCount)

        if (pendingProgressiveItems.isNotEmpty()) {
            val queued = ArrayList(pendingProgressiveItems)
            pendingProgressiveItems.clear()
            queued.forEach { pageLayoutRender?.appendNode(it) }
        }
    }

    fun appendProgressiveItem(item: NodeInfoBase) {
        val render = pageLayoutRender
        if (render != null) {
            render.appendNode(item)
        } else {
            pendingProgressiveItems.add(item)
        }
    }

    fun finishProgressiveList() {
        if (::rootGroup.isInitialized) {
            pageLayoutRender?.clearLoadingPlaceholders()
            rootGroup.triggerUpdate()
        }
        triggerAction(autoRunTask)
    }

    fun finishPrebuiltList() {
        if (::rootGroup.isInitialized) {
            pageLayoutRender?.clearLoadingPlaceholders()
            triggerAction(autoRunTask)
        }
    }

    fun appendLateItem(group: GroupNode?, node: NodeInfoBase, index: Int) {
        pageLayoutRender?.insertNode(group, node, index)
    }

    fun updateData(
        newItems: List<NodeInfoBase>,
        actionHandler: KrScriptActionHandler?,
        themeMode: ThemeMode?,
        onRendered: (() -> Unit)? = null
    ) {
        this.actionInfos = ArrayList(newItems)
        this.krScriptActionHandler = actionHandler
        this.themeMode = themeMode
        this.progressiveMode = false
        this.onRendered = onRendered
        if (isAdded && view != null) {
            renderInterface()
        } else {
            onRendered?.invoke()
        }
    }

    private fun triggerAction(autoRunTask: AutoRunTask?) {
        autoRunTask?.run {
            if (!key.isNullOrEmpty()) {
                onCompleted(rootGroup.triggerActionByKey(key!!))
            }
        }
    }

    private fun checkSdkCompatibility(clickableNode: ClickableNode): Boolean {
        val currentSDK = Build.VERSION.SDK_INT
        if (clickableNode.targetSdkVersion > 0 && currentSDK != clickableNode.targetSdkVersion) {
            DialogHelper.helpInfo(requireContext(), getString(R.string.kr_sdk_discrepancy), getString(R.string.kr_sdk_discrepancy_message).format(clickableNode.targetSdkVersion))
            return false
        } else if (currentSDK > clickableNode.maxSdkVersion) {
            DialogHelper.helpInfo(requireContext(), getString(R.string.kr_sdk_overtop), getString(R.string.kr_sdk_message).format(clickableNode.minSdkVersion, clickableNode.maxSdkVersion))
            return false
        } else if (currentSDK < clickableNode.minSdkVersion) {
            DialogHelper.helpInfo(requireContext(), getString(R.string.kr_sdk_too_low), getString(R.string.kr_sdk_message).format(clickableNode.minSdkVersion, clickableNode.maxSdkVersion))
            return false
        }
        return true
    }

    private fun nodeUnlockedAsync(clickableNode: ClickableNode, onUnlocked: () -> Unit) {
        if (!checkSdkCompatibility(clickableNode)) return

        if (clickableNode.lockShell.isEmpty()) {
            if (clickableNode.locked) {
                val msg = clickableNode.lockMessage.ifEmpty { getString(R.string.kr_lock_message) }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            } else {
                onUnlocked()
            }
            return
        }

        val progressBar = activity?.findViewById<android.widget.ProgressBar>(R.id.page_load_progress)
        progressBar?.apply {
            isIndeterminate = true
            visibility = View.VISIBLE
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val message = ScriptEnvironmen.executeResultRoot(requireContext(), clickableNode.lockShell, clickableNode)
            withContext(Dispatchers.Main) {
                progressBar?.visibility = View.GONE
                if (!isAdded) return@withContext
                val unlocked = message == "unlock" || message == "unlocked" || message == "false" || message == "0"
                if (!unlocked) {
                    val msg = if (message.isNotEmpty()) message else getString(R.string.kr_lock_message)
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                } else {
                    onUnlocked()
                }
            }
        }
    }

    override fun onSwitchClick(item: SwitchNode, onCompleted: Runnable) {
        if (!checkAndLockClick()) return
        nodeUnlockedAsync(item) {
            val toValue = !item.checked
            if (item.confirm) {
                DialogHelper.warning(requireActivity(), item.title, item.desc, { switchExecute(item, toValue, onCompleted) })
            } else if (item.warning.isNotEmpty()) {
                DialogHelper.warning(requireActivity(), item.title, item.warning, { switchExecute(item, toValue, onCompleted) })
            } else {
                switchExecute(item, toValue, onCompleted)
            }
        }
    }

    private fun switchExecute(switchNode: SwitchNode, toValue: Boolean, onExit: Runnable) {
        val script = switchNode.setState ?: return
        actionExecute(switchNode, script, onExit, object : java.util.HashMap<String, String>() {
            init { put("state", if (toValue) "1" else "0") }
        })
    }

    override fun onPageClick(item: PageNode, onCompleted: Runnable) {
        if (!checkAndLockClick()) return
        if (context != null && item.link.isNotEmpty()) {
            nodeUnlockedAsync(item) {
                try {
                    context?.let { WebLauncher.open(it, item.link) }
                } catch (ex: Exception) {
                    Toast.makeText(context, context?.getString(R.string.kr_slice_activity_fail), Toast.LENGTH_SHORT).show()
                }
            }
        } else if (context != null && item.activity.isNotEmpty()) {
            nodeUnlockedAsync(item) {
                TryOpenActivity(requireContext(), item.activity).tryOpen()
            }
        } else {
            if (!checkSdkCompatibility(item)) return
            krScriptActionHandler?.onSubPageClick(item)
        }
    }

    override fun onItemLongClick(clickableNode: ClickableNode) {
        if (clickableNode.key.isEmpty()) {
            DialogHelper.alert(this.requireActivity(), getString(R.string.kr_shortcut_create_fail), getString(R.string.kr_ushortcut_nsupported))
        } else {
            krScriptActionHandler?.addToFavorites(clickableNode, object : KrScriptActionHandler.AddToFavoritesHandler {
                override fun onAddToFavorites(clickableNode: ClickableNode, intent: Intent?) {
                    if (intent != null) {
                        DialogHelper.confirm(activity!!, getString(R.string.kr_shortcut_create), String.format(getString(R.string.kr_shortcut_create_desc), clickableNode.title), {
                            val result = ActionShortcutManager(context!!).addShortcut(intent, IconPathAnalysis().loadLogo(context!!, clickableNode), clickableNode)
                            if (!result) Toast.makeText(context, R.string.kr_shortcut_create_fail, Toast.LENGTH_SHORT).show()
                            else Toast.makeText(context, getString(R.string.kr_shortcut_create_success), Toast.LENGTH_SHORT).show()
                        })
                    }
                }
            })
        }
    }

    override fun onEditorClick(item: EditorNode, onCompleted: Runnable) {
        if (!checkAndLockClick()) return
        nodeUnlockedAsync(item) {
            val context = context ?: return@nodeUnlockedAsync
            if (item.file.isEmpty()) {
                Toast.makeText(context, getString(R.string.editor_file_missing), Toast.LENGTH_SHORT).show()
                return@nodeUnlockedAsync
            }
            com.tool.tree.TextEditorActivity.start(
                context, item.file, item.title, item.desc, item.wrap, item.pageConfigDir, item.placeholder,
                item.readonly, item.needInput, item.value, item.valueSh
            )
            onCompleted.run()
        }
    }

    override fun onPickerClick(item: PickerNode, onCompleted: Runnable) {
        if (!checkAndLockClick()) return
        nodeUnlockedAsync(item) {
            if (item.confirm) {
                DialogHelper.warning(requireActivity(), item.title, item.desc, { pickerExecute(item, onCompleted) })
            } else if (item.warning.isNotEmpty()) {
                DialogHelper.warning(requireActivity(), item.title, item.warning, { pickerExecute(item, onCompleted) })
            } else {
                pickerExecute(item, onCompleted)
            }
        }
    }

    private fun pickerExecute(item: PickerNode, onCompleted: Runnable) {
        val paramInfo = ActionParamInfo().apply {
            options = item.options
            optionsSh = item.optionsSh
            separator = item.separator
        }

        progressBarDialog.setCancelCallback {
            activeLoadJob?.cancel()
        }
        progressBarDialog.showDialog(getString(R.string.kr_param_options_load) + " ");

        activeLoadJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val scripts = LinkedHashMap<String, String>()
            if (!item.getState.isNullOrEmpty()) {
                scripts["state"] = item.getState!!
            }
            if (paramInfo.optionsSh.isNotEmpty()) {
                scripts["options"] = paramInfo.optionsSh
            }

            val shellResults = if (scripts.isNotEmpty()) {
                ScriptEnvironmen.executeMultipleResultRoot(requireContext(), scripts, item)
            } else {
                LinkedHashMap()
            }

            shellResults["state"]?.let { paramInfo.valueFromShell = it }

            val options = parseOptionsResult(paramInfo, shellResults["options"])
            val optionsSorted = if (options != null) {
                ActionParamsLayoutRender.setParamOptionsSelectedStatus(paramInfo, options)
                options
            } else null

            withContext(Dispatchers.Main) {
                progressBarDialog.hideDialog()
                if (optionsSorted != null && optionsSorted.isNotEmpty()) {
                    val darkMode = ThemeModeState.isDarkMode()
                    DialogItemChooser(darkMode, optionsSorted, item.multiple, object : DialogItemChooser.Callback {
                        override fun onConfirm(selected: List<SelectItem>, status: BooleanArray) {
                            val value = if (item.multiple) {
                                selected.joinToString(item.separator ?: "") { "" + it.value }
                            } else {
                                if (selected.isNotEmpty()) "" + selected[0].value else ""
                            }
                            if (value.isNotEmpty() || !item.multiple) {
                                pickerExecute(item, value, onCompleted)
                            } else {
                                Toast.makeText(context, getString(R.string.picker_select_none), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }).show(requireActivity().supportFragmentManager, "picker-item-chooser")
                } else {
                    Toast.makeText(context, getString(R.string.picker_not_item), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun pickerExecute(pickerNode: PickerNode, toValue: String, onExit: Runnable) {
        val script = pickerNode.setState ?: return
        actionExecute(pickerNode, script, onExit, hashMapOf("state" to toValue))
    }

    override fun onDownloadClick(item: DownloadNode, listItemView: ListItemDownload, onCompleted: Runnable) {
        if (item.urlSh.isNotEmpty() && !item.urlResolved) {
            resolveDownloadUrlThenClick(item, listItemView, onCompleted)
            return
        }

        val session = if (item.url.isNotBlank()) DownloadTaskHelper.getSession(item.url) else null
        if (session != null) {
            when (session.status) {
                DownloadTaskHelper.Status.DOWNLOADING -> {
                    listItemView.cancelIfDownloading()
                    return
                }
                DownloadTaskHelper.Status.PAUSED -> {
                    DownloadTaskHelper.resumeByUrl(requireContext(), item.url)
                    return
                }
                DownloadTaskHelper.Status.ERROR -> {
                    DownloadTaskHelper.cancel(session)
                }
                DownloadTaskHelper.Status.COMPLETING -> {
                    return
                }
                DownloadTaskHelper.Status.COMPLETED -> {
                    DownloadTaskHelper.cancel(session)
                }
                DownloadTaskHelper.Status.IDLE -> {
                }
            }
        }

        if (listItemView.isBusy) {
            listItemView.cancelIfDownloading()
            return
        }
        if (!checkAndLockClick()) return
        nodeUnlockedAsync(item) {
            if (item.confirm) {
                DialogHelper.warning(requireActivity(), item.title, item.desc, { downloadExecute(item, listItemView, onCompleted) })
            } else if (item.warning.isNotEmpty()) {
                DialogHelper.warning(requireActivity(), item.title, item.warning, { downloadExecute(item, listItemView, onCompleted) })
            } else {
                downloadExecute(item, listItemView, onCompleted)
            }
        }
    }

    private fun resolveDownloadUrlThenClick(item: DownloadNode, listItemView: ListItemDownload, onCompleted: Runnable) {
        val progressBar = activity?.findViewById<android.widget.ProgressBar>(R.id.page_load_progress)
        progressBar?.apply {
            isIndeterminate = true
            visibility = View.VISIBLE
        }
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val resolved = ScriptEnvironmen.executeResultRoot(requireContext(), item.urlSh, item).trim()
            withContext(Dispatchers.Main) {
                progressBar?.visibility = View.GONE
                if (!isAdded) return@withContext
                if (resolved.isEmpty()) {
                    Toast.makeText(context, getString(R.string.kr_download_create_fail), Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                item.url = resolved
                item.urlResolved = true
                onDownloadClick(item, listItemView, onCompleted)
            }
        }
    }

    private fun downloadExecute(item: DownloadNode, listItemView: ListItemDownload, onExit: Runnable) {
        if (!isAdded) return
        DownloadTaskHelper.start(requireContext(), viewLifecycleOwner.lifecycleScope, item, listItemView) {
            krScriptActionHandler?.onActionCompleted(item)
            onExit.run()
        }
    }

    override fun onActionClick(item: ActionNode, onCompleted: Runnable, isAutoShow: Boolean) {
        if (!checkAndLockClick()) return
        nodeUnlockedAsync(item) {
            val onCancel = if (isAutoShow) Runnable { requireActivity().finish() } else null
            val cancelable = !isAutoShow
            if (item.confirm) {
                DialogHelper.warning(requireActivity(), item.title, item.desc, { actionExecute(item, onCompleted, isAutoShow) }, onCancel, cancelable)
            } else if (item.warning.isNotEmpty() && (item.params == null || item.params?.isEmpty() == true)) {
                DialogHelper.warning(requireActivity(), item.title, item.warning, { actionExecute(item, onCompleted, isAutoShow) }, onCancel, cancelable)
            } else {
                actionExecute(item, onCompleted, isAutoShow)
            }
        }
    }

    private fun actionExecute(action: ActionNode, onExit: Runnable, isAutoShow: Boolean = false) {
        val script = action.setState ?: return

        if (action.params != null && action.params!!.isNotEmpty()) {
            val actionParamInfos = action.params!!
            val layoutInflater = LayoutInflater.from(requireContext())
            val linearLayout = layoutInflater.inflate(R.layout.kr_params_list, null) as LinearLayout

            progressBarDialog.setCancelCallback {
                activeLoadJob?.cancel()
            }
            progressBarDialog.showDialog(getString(R.string.onloading))

            activeLoadJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    progressBarDialog.showDialog(getString(R.string.kr_param_options_load) + " ");
                }

                val scripts = LinkedHashMap<String, String>()
                for (param in actionParamInfos) {
                    param.valueFromShell = null

                    val name = param.name ?: continue
                    if (!param.valueShell.isNullOrEmpty()) {
                        scripts["value:$name"] = param.valueShell!!
                    }
                    if (param.optionsSh.isNotEmpty()) {
                        scripts["options:$name"] = param.optionsSh
                    }
                    if (!param.titleSh.isNullOrEmpty()) {
                        scripts["title:$name"] = param.titleSh!!
                    }
                    if (!param.labelSh.isNullOrEmpty()) {
                        scripts["label:$name"] = param.labelSh!!
                    }
                    if (!param.descSh.isNullOrEmpty()) {
                        scripts["desc:$name"] = param.descSh!!
                    }
                    if (!param.descOnSh.isNullOrEmpty()) {
                        scripts["desc-on:$name"] = param.descOnSh!!
                    }
                    if (!param.placeholderSh.isNullOrEmpty()) {
                        scripts["placeholder:$name"] = param.placeholderSh!!
                    }
                    if (!param.readonlySh.isNullOrEmpty()) {
                        scripts["readonly:$name"] = param.readonlySh!!
                    }
                }

                val shellResults = if (scripts.isNotEmpty()) {
                    ScriptEnvironmen.executeMultipleResultRoot(requireContext(), scripts, action)
                } else {
                    LinkedHashMap()
                }

                for (param in actionParamInfos) {
                    val name = param.name ?: continue
                    shellResults["value:$name"]?.let { param.valueFromShell = it }
                    param.optionsFromShell = parseOptionsResult(param, shellResults["options:$name"])
                    shellResults["title:$name"]?.let { param.title = it }
                    shellResults["label:$name"]?.let { param.label = it }
                    shellResults["desc:$name"]?.let { param.desc = it }
                    shellResults["desc-on:$name"]?.let { param.descOn = it }
                    shellResults["placeholder:$name"]?.let { param.placeholder = it }
                    shellResults["readonly:$name"]?.let { param.readonly = it.trim() == "1" }
                    if (param.valueFromShell == null) {
                        ActionParamMemory.load(requireContext(), action, param)?.let { param.valueFromShell = it }
                    }
                }

                withContext(Dispatchers.Main) {
                    progressBarDialog.showDialog(getString(R.string.kr_params_render))
                    val render = ActionParamsLayoutRender(linearLayout, requireActivity())
                    render.renderList(actionParamInfos, object : ParamsFileChooserRender.FileChooserInterface {
                        override fun openFileChooser(callback: ParamsFileChooserRender.FileSelectedInterface): Boolean {
                            return krScriptActionHandler?.openFileChooser(callback) ?: false
                        }
                    })
                    progressBarDialog.hideDialog()

                    val customRunner = krScriptActionHandler?.openParamsPage(action, linearLayout) {
                        try {
                            val paramsValue = render.readParamsValue(actionParamInfos)
                            ActionParamMemory.save(requireContext(), action, actionParamInfos, paramsValue)
                            if (isAutoShow) ActionShowMemory.markConfirmed(requireContext(), action)
                            actionExecute(action, script, onExit, paramsValue)
                        } catch (ex: Exception) {
                            Toast.makeText(requireContext(), "" + ex.message, Toast.LENGTH_LONG).show()
                        }
                    }

                    if (customRunner != true) {
                        val isLongList = actionParamInfos.size > 4
                        val dialogView = LayoutInflater.from(context).inflate(if (isLongList) R.layout.kr_dialog_params else R.layout.kr_dialog_params_small, null)
                        val center = dialogView.findViewById<ViewGroup>(R.id.kr_params_center)
                        center.removeAllViews()
                        center.addView(linearLayout)

                        val cancelable = !isAutoShow

                        val darkMode = themeMode?.isDarkMode ?: false
                        val dialog = if (isLongList) {
                            AlertDialog.Builder(requireContext(), if (darkMode) R.style.kr_full_screen_dialog_dark else R.style.kr_full_screen_dialog_light)
                                .setView(dialogView).setCancelable(cancelable).create().apply {
                                    setCanceledOnTouchOutside(cancelable)
                                    show()
                                    window?.let { DialogHelper.applyEdgeToEdge(it, darkMode, dialogView) }
                                }
                        } else {
                            DialogHelper.customDialog(requireActivity(), dialogView, cancelable).dialog
                        }
                        if (isLongList) {
                            if (cancelable) {
                                val binding = DialogFullScreen.bindSwipeToDismiss(requireActivity(), dialog) { dialog.dismiss() }
                                dialog.setOnDismissListener { binding?.release(dialog) }
                            } else {
                                dialog.window?.let { DialogHelper.setWindowBlurBg(it, requireActivity()) }
                            }
                        }

                        dialogView.findViewById<TextView>(R.id.title).text = action.title
                        dialogView.findViewById<TextView>(R.id.desc).apply { if (action.desc.isEmpty()) visibility = View.GONE else text = action.desc }
                        if (action.warning.isEmpty()) {
                            dialogView.findViewById<View>(R.id.warn_layout).visibility = View.GONE
                        } else {
                            dialogView.findViewById<TextView>(R.id.warn).text = action.warning
                        }

                        RowsRenderHelper.bind(
                            requireContext(),
                            dialogView.findViewById<TextView>(R.id.kr_rows),
                            dialogView.findViewById<android.widget.ImageView>(R.id.kr_rows_photo),
                            action.paramsRows,
                            action
                        )

                        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener {
                            dialog?.dismiss()
                            if (isAutoShow) {
                                requireActivity().finish()
                            }
                        }
                        dialogView.findViewById<View>(R.id.btn_confirm).setOnClickListener {
                            try {
                                val paramsValue = render.readParamsValue(actionParamInfos)
                                ActionParamMemory.save(requireContext(), action, actionParamInfos, paramsValue)
                                if (isAutoShow) ActionShowMemory.markConfirmed(requireContext(), action)
                                actionExecute(action, script, onExit, paramsValue)
                                dialog?.dismiss()
                            } catch (ex: Exception) {
                                Toast.makeText(requireContext(), "" + ex.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
            return
        }
        if (isAutoShow) ActionShowMemory.markConfirmed(requireContext(), action)
        actionExecute(action, script, onExit, null)
    }

    private fun parseOptionsResult(actionParamInfo: ActionParamInfo, shellResult: String?): ArrayList<SelectItem>? {
        val options = ArrayList<SelectItem>()
        val result = shellResult ?: ""

        if (!(result == "error" || result == "null" || result.isEmpty())) {
            for (item in result.split("\n").filter { it.isNotEmpty() }) {
                if (item.contains("|")) {
                    val itemSplit = item.split("|")
                    options.add(SelectItem().apply {
                        value = itemSplit[0]
                        title = if (itemSplit.size > 1) itemSplit[1] else itemSplit[0]
                    })
                } else {
                    options.add(SelectItem().apply { title = item; value = item })
                }
            }
        } else if (actionParamInfo.options != null) {
            options.addAll(actionParamInfo.options!!)
        } else return null

        return options
    }

    var hiddenTaskRunning = false
    private fun actionExecute(nodeInfo: RunnableNode, script: String, onExit: Runnable, params: HashMap<String, String>?) {
        val context = requireContext()
        val onDismiss = Runnable { krScriptActionHandler?.onActionCompleted(nodeInfo) }

        when (nodeInfo.shell) {
            RunnableNode.shellModeBgTask -> {
                BgTaskThread.startTask(context, script, params, nodeInfo, onExit, onDismiss)
            }
            RunnableNode.shellModeHidden -> {
                if (hiddenTaskRunning) {
                    Toast.makeText(context, getString(R.string.kr_hidden_task_running), Toast.LENGTH_SHORT).show()
                } else {
                    hiddenTaskRunning = true
                    val hiddenDismiss = Runnable {
                        hiddenTaskRunning = false
                        onDismiss.run()
                    }
                    HiddenTaskThread.startTask(context, script, params, nodeInfo, onExit, hiddenDismiss)
                }
            }
            else -> {
                val darkMode = themeMode?.isDarkMode ?: false
                val dialog = DialogLogFragment.create(nodeInfo, onExit, onDismiss, script, params, darkMode)
                dialog.isCancelable = false
                dialog.show(parentFragmentManager, "")
            }
        }
    }
}