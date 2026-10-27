package com.tool.tree

sealed class AppUpdateCheckResult {
    class Available(val info: AppUpdateInfo) : AppUpdateCheckResult()
    class UpToDate(val changelogText: String?) : AppUpdateCheckResult()
    object Failed : AppUpdateCheckResult()
}
