package com.tool.tree

import java.io.Serializable

class AppUpdateInfo(
    val apkUrl: String,
    val changelogUrl: String,
    val sha256: String,
    val apkSize: Long = -1,
    val apkFileName: String,
    val changelogText: String? = null
) : Serializable
