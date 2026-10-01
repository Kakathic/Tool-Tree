package com.omarea.krscript

import android.content.Context
import android.os.Process
import android.os.UserManager

class FileOwner(private val context: Context) {

    fun getUserId(): Int {
        val um = context.getSystemService(Context.USER_SERVICE) as UserManager
        val userHandle = android.os.Process.myUserHandle()

        var value = 0
        try {
            value = um.getSerialNumberForUser(userHandle).toInt()
        } catch (ignored: Exception) {
        }
        return value
    }

    fun getFileOwner(): String {
        val androidUid = getUserId()
        return "u" + androidUid + "_a" + ((android.os.Process.myUid() % 100000) - Process.FIRST_APPLICATION_UID)
    }
}
