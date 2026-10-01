package com.omarea.krscript.model

open class ClickableNode(currentPageConfigPath: String) : NodeInfoBase(currentPageConfigPath) {
    var iconPath = ""
    var iconSh: String = ""
    var iconGifNum: Int = 0
    var iconGifTime: Int = 300
    var iconGifAutoplay: Boolean = true
    var iconGifLoopCount: Int = 0
    var iconRealGif: Boolean = false

    var logoPath = ""
    var photoPath = ""
    var photoSh: String = ""
    var photoRealSize: Boolean = false
    var photoGifNum: Int = 0
    var photoGifTime: Int = 300
    var photoGifAutoplay: Boolean = true
    var photoGifLoopCount: Int = 0
    var photoRealGif: Boolean = false
    var bgPath = ""
    var bgSh: String = ""

    var allowShortcut:Boolean? = null

    var locked: Boolean = false
    var lockMessage: String = ""
    var lockShell: String = ""

    var targetSdkVersion = 0
    var minSdkVersion = 0
    var maxSdkVersion = 100
}
