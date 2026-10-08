package com.omarea.krscript.ui

import com.omarea.krscript.model.ActionParamInfo

object ParamsApplyScript {
    fun merge(
        script: String,
        actionParamInfos: List<ActionParamInfo>,
        render: ActionParamsLayoutRender
    ): String {
        val parts = ArrayList<String>()
        for (info in actionParamInfos) {
            val name = info.name ?: continue
            val apply = info.applyScript?.trim()
            if (apply.isNullOrEmpty()) continue
            if (info.readonly) continue
            if (render.isParamHidden(name) && !info.dependIncludeHidden) continue
            parts.add(apply)
        }
        if (parts.isEmpty()) return script
        parts.add(script)
        return parts.joinToString("\n")
    }
}
