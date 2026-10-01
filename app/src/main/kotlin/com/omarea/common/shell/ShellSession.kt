package com.omarea.common.shell

interface ShellSession {
    val isIdle: Boolean
    fun doCmdSync(cmd: String): String
    fun doCmdSync(shellCommand: String, shellTranslation: ShellTranslation): String
    fun checkRoot(): Boolean
    fun tryExit()
}
