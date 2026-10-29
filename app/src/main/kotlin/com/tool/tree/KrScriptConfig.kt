package com.tool.tree

import android.content.Context
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.model.PageNode
import java.nio.charset.Charset

class KrScriptConfig {
    private val EXECUTOR_CORE_DEFAULT = "file:///android_asset/root/executor.sh"
    private val PAGE_LIST_CONFIG_DEFAULT = "file:///android_asset/root/more.toml"
    private val FAVORITE_CONFIG_DEFAULT = "file:///android_asset/root/favorites.toml"
    private val CUSTOM_TAB3_DEFAULT = "file:///android_asset/root/tab3.toml"
    private val CUSTOM_TAB4_DEFAULT = "file:///android_asset/root/tab4.toml"
    private val BEFORE_START_SH_DEFAULT = ""

    fun init(context: Context): KrScriptConfig {
        if (configInfo == null) {
            val info = HashMap<String, String>()
            info[EXECUTOR_CORE] = EXECUTOR_CORE_DEFAULT
            info[PAGE_LIST_CONFIG] = PAGE_LIST_CONFIG_DEFAULT
            info[FAVORITE_CONFIG] = FAVORITE_CONFIG_DEFAULT
            info[CUSTOM_TAB3_CONFIG] = CUSTOM_TAB3_DEFAULT
            info[CUSTOM_TAB4_CONFIG] = CUSTOM_TAB4_DEFAULT
            info[TOOLKIT_DIR] = TOOLKIT_DIR_DEFAULT
            info[BEFORE_START_SH] = BEFORE_START_SH_DEFAULT
            configInfo = info

            try {
                var fileName = context.getString(R.string.kr_script_config)
                if (fileName.startsWith(ASSETS_FILE)) {
                    fileName = fileName.substring(ASSETS_FILE.length)
                }
                val inputStream = context.assets.open(fileName)
                val bytes = ByteArray(inputStream.available())
                inputStream.read(bytes)
                val rows = String(bytes, Charset.defaultCharset()).split("\n")
                for (row in rows) {
                    val rowText = row.trim()
                    if (!rowText.startsWith("#") && rowText.contains("=")) {
                        val separator = rowText.indexOf("=")
                        val key = rowText.substring(0, separator).trim()
                        val value = rowText.substring(separator + 2, rowText.length - 1).trim()
                        info.remove(key)
                        info[key] = value
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }

            ScriptEnvironmen.init(context, getExecutorCore(), getToolkitDir())
        }

        return this
    }

    fun getVariables(): HashMap<String, String>? {
        return configInfo
    }

    private fun getExecutorCore(): String {
        val info = configInfo
        if (info != null && info.containsKey(EXECUTOR_CORE)) {
            return info[EXECUTOR_CORE] ?: EXECUTOR_CORE_DEFAULT
        }
        return EXECUTOR_CORE_DEFAULT
    }

    private fun getToolkitDir(): String {
        val info = configInfo
        if (info != null && info.containsKey(TOOLKIT_DIR)) {
            return info[TOOLKIT_DIR] ?: TOOLKIT_DIR_DEFAULT
        }
        return TOOLKIT_DIR_DEFAULT
    }

    fun getPageListConfig(): PageNode {
        val info = configInfo
        if (info != null) {
            val pageInfo = PageNode("")
            if (info.containsKey(PAGE_LIST_CONFIG_SH)) {
                pageInfo.pageConfigSh = info[PAGE_LIST_CONFIG_SH] ?: ""
            }
            if (info.containsKey(PAGE_LIST_CONFIG)) {
                pageInfo.pageConfigPath = info[PAGE_LIST_CONFIG] ?: ""
            }
            return pageInfo
        }
        return PageNode("")
    }

    fun getFavoriteConfig(): PageNode {
        val info = configInfo
        if (info != null) {
            val pageInfo = PageNode("")
            if (info.containsKey(FAVORITE_CONFIG_SH)) {
                pageInfo.pageConfigSh = info[FAVORITE_CONFIG_SH] ?: ""
            }
            if (info.containsKey(FAVORITE_CONFIG)) {
                pageInfo.pageConfigPath = info[FAVORITE_CONFIG] ?: ""
            }
            return pageInfo
        }
        return PageNode("")
    }

    fun getCustomTab3Config(): PageNode {
        val info = configInfo
        if (info != null) {
            val pageInfo = PageNode("")
            if (info.containsKey("custom_tab3_config_sh")) {
                pageInfo.pageConfigSh = info["custom_tab3_config_sh"] ?: ""
            }
            if (info.containsKey(CUSTOM_TAB3_CONFIG)) {
                pageInfo.pageConfigPath = info[CUSTOM_TAB3_CONFIG] ?: ""
            }
            return pageInfo
        }
        return PageNode("")
    }

    fun getCustomTab4Config(): PageNode {
        val info = configInfo
        if (info != null) {
            val pageInfo = PageNode("")
            if (info.containsKey("custom_tab4_config_sh")) {
                pageInfo.pageConfigSh = info["custom_tab4_config_sh"] ?: ""
            }
            if (info.containsKey(CUSTOM_TAB4_CONFIG)) {
                pageInfo.pageConfigPath = info[CUSTOM_TAB4_CONFIG] ?: ""
            }
            return pageInfo
        }
        return PageNode("")
    }

    fun getBeforeStartSh(): String {
        val info = configInfo
        if (info != null && info.containsKey(BEFORE_START_SH)) {
            return info[BEFORE_START_SH] ?: BEFORE_START_SH_DEFAULT
        }
        return BEFORE_START_SH_DEFAULT
    }

    companion object {
        private const val ASSETS_FILE = "file:///android_asset/"

        private const val TOOLKIT_DIR = "toolkit_dir"
        private const val TOOLKIT_DIR_DEFAULT = "file:///android_asset/home"

        private const val EXECUTOR_CORE = "executor_core"
        private const val PAGE_LIST_CONFIG = "page_list_config"
        private const val PAGE_LIST_CONFIG_SH = "page_list_config_sh"
        private const val FAVORITE_CONFIG = "favorite_config"
        private const val FAVORITE_CONFIG_SH = "favorite_config_sh"
        private const val CUSTOM_TAB3_CONFIG = "custom_tab3_config"
        private const val CUSTOM_TAB3_CONFIG_SH = "custom_tab3_config_sh"
        private const val CUSTOM_TAB4_CONFIG = "custom_tab4_config"
        private const val CUSTOM_TAB4_CONFIG_SH = "custom_tab4_config_sh"
        private const val BEFORE_START_SH = "before_start_sh"

        private var configInfo: HashMap<String, String>? = null
    }
}
