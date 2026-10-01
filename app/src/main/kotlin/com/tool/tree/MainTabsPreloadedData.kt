package com.tool.tree

import com.omarea.krscript.model.NodeInfoBase
import java.io.Serializable

class MainTabsPreloadedData(
    val favorites: ArrayList<NodeInfoBase>?,
    val pages: ArrayList<NodeInfoBase>?,
    val tab3Items: ArrayList<NodeInfoBase>?,
    val tab4Items: ArrayList<NodeInfoBase>?
) : Serializable