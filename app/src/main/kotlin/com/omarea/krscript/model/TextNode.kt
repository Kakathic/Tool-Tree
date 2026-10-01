package com.omarea.krscript.model

import android.text.Layout
import java.io.Serializable

class TextNode(currentPageConfigPath: String) : NodeInfoBase(currentPageConfigPath) {
    val rows = ArrayList<TextRow>()

    class TextRow : Serializable {
        internal var size: Int = -1
        internal var color: Int = -1
        internal var bgColor: Int = -1
        internal var bold: Boolean = false
        internal var italic: Boolean = false
        internal var underline: Boolean = false
        internal var strikethrough: Boolean = false
        internal var monospace: Boolean = false
        internal var letterSpacing: Float = 0f
        internal var lineHeight: Float = 0f
        internal var marginTop: Int = 0
        internal var marginBottom: Int = 0
        internal var alpha: Float = -1f
        internal var breakRow: Boolean = false
        internal var line: Boolean = false
        internal var copy: Boolean = false
        internal var align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
        internal var link: String = ""
        internal var activity: String = ""
        internal var text: String = ""
        internal var dynamicTextSh: String = ""
        internal var markdown: Boolean = false
        internal var onClickScript: String = ""
        internal var toastResult: Boolean = false
        internal var photo: String = ""
        internal var photoSh: String = ""
        internal var photoRealSize: Boolean = false
        internal var photoGifNum: Int = 0
        internal var photoGifTime: Int = 300
        internal var photoGifAutoplay: Boolean = true
        internal var photoGifLoopCount: Int = 0
        internal var photoRealGif: Boolean = false

        internal var icon: String = ""
        internal var iconSh: String = ""
        internal var iconPosition: String = "before"
        internal var iconSize: Int = 0
        internal var iconGifNum: Int = 0
        internal var iconGifTime: Int = 300
        internal var iconGifAutoplay: Boolean = true
        internal var iconGifLoopCount: Int = 0
        internal var iconRealGif: Boolean = false
        internal var htmlFile: String = ""
        internal var htmlUrl: String = ""
        internal var htmlHeight: Int = 0

        internal var toggle: String = ""
        internal var checked: Boolean = false
        internal var onChangeSh: String = ""

        internal var confirm: String = ""
        internal var refreshInterval: Int = 0
        internal var resetTarget: Int = -1
        internal var progress: Float = -1f
        internal var progressSh: String = ""
        internal var progressMax: Float = 100f
        internal var progressColor: Int = -1
        internal var progressTrackColor: Int = -1
        internal var progressWidth: Int = 120
        internal var progressHeight: Int = 8
        internal var flash: Boolean = true
        internal var flashColor: Int = -1
    }
}