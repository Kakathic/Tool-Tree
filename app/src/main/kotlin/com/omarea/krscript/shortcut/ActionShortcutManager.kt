package com.omarea.krscript.shortcut

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.omarea.common.shared.ObjectStorage
import com.omarea.krscript.model.NodeInfoBase
import com.omarea.krscript.model.PageNode

class ActionShortcutManager(private val context: Context) {

    fun addShortcut(intent: Intent, drawable: Drawable, config: NodeInfoBase): Boolean {
        if (intent.hasExtra("page")) {
            val pageNode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("page", PageNode::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("page") as? PageNode
            }
            
            pageNode?.let {
                intent.putExtra("shortcutId", saveShortcutTarget(it))
                intent.removeExtra("page")
            }
        }

        val shortcutIntent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(context.packageName, intent.component!!.className)
            putExtras(intent)
            flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }

        val bitmap = drawableToBitmap(drawable)
        val shortcutId = "addin_${config.index}"
        
        val shortcutInfo = ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(config.title)
            .setIcon(IconCompat.createWithBitmap(bitmap))
            .setIntent(shortcutIntent)
            .build()

        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            
            val successCallback = PendingIntent.getBroadcast(
                context, 0, Intent(context, ActionShortcutManager::class.java), flags
            )

            return ShortcutManagerCompat.requestPinShortcut(
                context, shortcutInfo, successCallback.intentSender
            )
        }
        
        return false
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) return drawable.bitmap
        
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun saveShortcutTarget(pageNode: PageNode): String {
        val id = System.currentTimeMillis().toString()
        ObjectStorage<PageNode>(context).save(pageNode, id)
        return id
    }

    fun getShortcutTarget(shortcutId: String): PageNode? {
        return ObjectStorage<PageNode>(context).load(shortcutId)
    }
}
