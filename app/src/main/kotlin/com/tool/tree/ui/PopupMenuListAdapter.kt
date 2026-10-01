package com.tool.tree.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.tool.tree.R

enum class PopupRowTypeIcon { SCRIPT, CHECKBOX, PAGE, LINK, REFRESH, DROPDOWN, FILE, FOLDER, NONE }

class PopupMenuRow(
    val title: String,
    val leftIcon: Drawable?,
    val typeIcon: PopupRowTypeIcon,
    val checked: Boolean,
    val onClick: () -> Unit
)

class PopupMenuListAdapter(
    private val context: Context,
    private val rows: List<PopupMenuRow>
) : BaseAdapter() {
    private val defaultTint: ColorStateList? by lazy {
        val ta = context.obtainStyledAttributes(intArrayOf(R.attr.toolbarIconTint))
        val tint = ta.getColorStateList(0)
        ta.recycle()
        tint
    }

    private val accentTint: ColorStateList? by lazy {
        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.colorAccent))
    }

    override fun getCount(): Int = rows.size
    override fun getItem(position: Int): PopupMenuRow = rows[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.popup_menu_list_item, parent, false)
        val row = rows[position]

        view.findViewById<TextView>(R.id.popup_item_title).text = row.title

        val iconLeft = view.findViewById<ImageView>(R.id.popup_item_icon_left)

        if (row.leftIcon != null) {
            iconLeft.setImageDrawable(row.leftIcon)
            iconLeft.imageTintList =
                if (row.typeIcon == PopupRowTypeIcon.CHECKBOX && row.checked) accentTint
                else defaultTint
            iconLeft.visibility = View.VISIBLE
        } else {
            when (row.typeIcon) {
                PopupRowTypeIcon.CHECKBOX -> {
                    iconLeft.setImageResource(if (row.checked) R.drawable.checkbox_true else R.drawable.checkbox_false)
                    iconLeft.imageTintList = if (row.checked) accentTint else defaultTint
                    iconLeft.visibility = View.VISIBLE
                }
                PopupRowTypeIcon.PAGE -> {
                    iconLeft.setImageResource(R.drawable.kr_page)
                    iconLeft.imageTintList = defaultTint
                    iconLeft.visibility = View.VISIBLE
                }
                PopupRowTypeIcon.LINK -> {
                    iconLeft.setImageResource(R.drawable.kr_link)
                    iconLeft.imageTintList = defaultTint
                    iconLeft.visibility = View.VISIBLE
                }
                PopupRowTypeIcon.REFRESH -> {
                    iconLeft.setImageResource(R.drawable.kr_refresh)
                    iconLeft.imageTintList = defaultTint
                    iconLeft.visibility = View.VISIBLE
                }
                PopupRowTypeIcon.DROPDOWN -> {
                    iconLeft.setImageResource(R.drawable.kr_down)
                    iconLeft.imageTintList = defaultTint
                    iconLeft.visibility = View.VISIBLE
                }
                PopupRowTypeIcon.FILE -> {
                    iconLeft.setImageResource(R.drawable.kr_file)
                    iconLeft.imageTintList = defaultTint
                    iconLeft.visibility = View.VISIBLE
                }
                PopupRowTypeIcon.FOLDER -> {
                    iconLeft.setImageResource(R.drawable.kr_folder)
                    iconLeft.imageTintList = defaultTint
                    iconLeft.visibility = View.VISIBLE
                }
                PopupRowTypeIcon.SCRIPT -> {
                    iconLeft.setImageResource(R.drawable.kr_script)
                    iconLeft.imageTintList = defaultTint
                    iconLeft.visibility = View.VISIBLE
                }
                PopupRowTypeIcon.NONE -> {
                    iconLeft.visibility = View.GONE
                }
            }
        }

        view.findViewById<View>(R.id.popup_item_divider).visibility =
            if (position == rows.size - 1) View.GONE else View.VISIBLE

        return view
    }
}
