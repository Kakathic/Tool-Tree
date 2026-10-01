package com.omarea.common.shell

import android.os.Handler

open class ShellEvents {
    protected var processHandler: Handler? = null
    var PROCESS_EVENT_STAR = 0
    var PROCESS_EVENT_CONTENT = 1
    var PROCESS_EVENT_ERROR_CONTENT = 2
    var PROCESS_EVENT_EXIT = -1
}
