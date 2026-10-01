package com.omarea.common.shell

object KernelProrp {
    fun getProp(propName: String): String {
        return KeepShellPublic.doCmdSync("if [[ -e \"$propName\" ]]; then cat \"$propName\"; fi;")
    }

    fun getProp(propName: String, grep: String): String {
        return KeepShellPublic.doCmdSync("if [[ -e \"$propName\" ]]; then cat \"$propName\" | grep \"$grep\"; fi;")
    }

    fun setProp(propName: String, value: String): Boolean {
        return KeepShellPublic.doCmdSync(
                "chmod 664 \"$propName\" 2 > /dev/null\n" +
                "echo \"$value\" > \"$propName\""
        ) != "error"
    }
}