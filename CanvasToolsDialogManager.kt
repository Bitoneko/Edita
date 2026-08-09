package com.bitoneko.kouecanvas

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout

object CanvasToolsDialogManager {

    private val recentColorsList = ArrayList<Int>()

    fun showToolsSelectorDialog(act: Activity, cv: InteractiveCanvasView, imgSelectedTool: ImageView) {
        val density = act.resources.displayMetrics.density
        val scroll = ScrollView(act)
        val root = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt()) }
        scroll.addView(root)

        val items = arrayOf<Triple<String, String, ToolMode>>(
            Triple("Brush", "ic_brush", ToolMode.BRUSH),
            Triple("Eraser", "ic_eraser", ToolMode.ERASER),
            Triple("Pan", "ic_hand", ToolMode.PAN),
            Triple("Fill", "ic_fill", ToolMode.FILL),
            Triple("Image Stamp", "ic_image", ToolMode.STAMP_IMAGE),
            Triple("Text Stamp", "ic_text", ToolMode.STAMP_TEXT),
            Triple("Color Picker", "ic_pipette", ToolMode.PIPPETE)
        )
        val dialog = MaterialAlertDialogBuilder(act).setTitle("Tools").setView(scroll).create()
        for (item in items) {
            val row = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
                val v = android.util.TypedValue()
                act.theme.resolveAttribute(android.R.attr.selectableItemBackground, v, true)
                setBackgroundResource(v.resourceId); isClickable = true
            }
            val iv = ImageView(act).apply { layoutParams = LinearLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt()).apply { rightMargin = (16 * density).toInt() }; val iconId = act.resources.getIdentifier(item.second, "drawable", act.packageName); setImageResource(if (iconId != 0) iconId else android.R.drawable.ic_menu_info_details); setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN) }
            val tv = TextView(act).apply { text = item.first; setTextColor(Color.WHITE); textSize = 16f }
            row.addView(iv); row.addView(tv)
            row.setOnClickListener {
                cv.currentTool = item.third
                val activeIconId = act.resources.getIdentifier(item.second, "drawable", act.packageName)
                if (activeIconId != 0) { imgSelectedTool.setImageResource(activeIconId); imgSelectedTool.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN) }
                dialog.dismiss()
            }
            root.addView(row)
        }
        dialog.show()
    }
    fun showActiveToolSettingsDialog(act: Activity, cv: InteractiveCanvasView) {
        val density = act.resources.displayMetrics.density
        val scroll = ScrollView(act)
        when (cv.currentTool) {
            ToolMode.BRUSH -> showSliderDialog(act, "Brush Size", cv.brushSize.toInt(), 1, 200, "Brush Config") { cv.brushSize = it.toFloat() }
            ToolMode.ERASER -> showSliderDialog(act, "Eraser Size", cv.eraserSize.toInt(), 1, 200, "Eraser Config") { cv.eraserSize = it.toFloat() }
            ToolMode.FILL -> showSliderDialog(act, "Color Tolerance", cv.fillTolerance, 0, 150, "Fill Config") { cv.fillTolerance = it }
            ToolMode.STAMP_TEXT -> {
                val layout = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), (16 * density).toInt()) }
                scroll.addView(layout)
                
                val et = EditText(act).apply { 
                    hint = "Text For Stamp"
                    setText(cv.stampText)
                    isFocusableInTouchMode = true
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    setSingleLine(false)
                    setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION)
                    requestFocus()
                }
                layout.addView(et)
                
                val tvSize = TextView(act).apply { text = "Text Size: ${cv.stampTextSize.toInt()}"; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * density).toInt() } }
                val sbSize = SeekBar(act).apply { max = 300; progress = cv.stampTextSize.toInt(); setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { cv.stampTextSize = p.toFloat(); tvSize.text = "Text Size: $p" }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })}
                val tvRot = TextView(act).apply { text = "Rotation Angle: ${cv.stampRotation.toInt()}°"; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * density).toInt() } }
                val sbRot = SeekBar(act).apply { max = 360; progress = cv.stampRotation.toInt(); setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { cv.stampRotation = p.toFloat(); tvRot.text = "Rotation Angle: $p°" }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })}
                layout.addView(tvSize); layout.addView(sbSize); layout.addView(tvRot); layout.addView(sbRot)
                
                val textDialog = MaterialAlertDialogBuilder(act).setTitle("Text Stamp Config").setView(scroll).setPositiveButton("Save") { _, _ -> 
                    cv.stampText = et.text.toString()
                    val imm = act.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(et.windowToken, 0)
                }.create()
                
                textDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                textDialog.show()
            }
            ToolMode.STAMP_IMAGE -> {
                val layout = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), (16 * density).toInt()) }
                scroll.addView(layout)
                val btnLoad = Button(act).apply { text = "Bind Photo From Gallery" }
                btnLoad.setOnClickListener { val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply { type = "image/*" }; act.startActivityForResult(intent, 999) }
                layout.addView(btnLoad)
                
                val tvScaleX = TextView(act).apply { text = "Width Scale: ${(cv.stampScaleX * 100).toInt()}%"; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * density).toInt() } }
                val sbScaleX = SeekBar(act).apply { max = 490; progress = ((cv.stampScaleX * 100).toInt() - 10); setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { val sc = (p + 10) / 100f; cv.stampScaleX = sc; tvScaleX.text = "Width Scale: ${(sc * 100).toInt()}%" }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })}
                
                val tvScaleY = TextView(act).apply { text = "Height Scale: ${(cv.stampScaleY * 100).toInt()}%"; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * density).toInt() } }
                val sbScaleY = SeekBar(act).apply { max = 490; progress = ((cv.stampScaleY * 100).toInt() - 10); setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { val sc = (p + 10) / 100f; cv.stampScaleY = sc; tvScaleY.text = "Height Scale: ${(sc * 100).toInt()}%" }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })}
                
                val tvRot = TextView(act).apply { text = "Rotation Angle: ${cv.stampRotation.toInt()}°"; setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * density).toInt() } }
                val sbRot = SeekBar(act).apply { max = 360; progress = cv.stampRotation.toInt(); setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { cv.stampRotation = p.toFloat(); tvRot.text = "Rotation Angle: $p°" }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })}
                layout.addView(tvScaleX); layout.addView(sbScaleX); layout.addView(tvScaleY); layout.addView(sbScaleY); layout.addView(tvRot); layout.addView(sbRot)
                MaterialAlertDialogBuilder(act).setTitle("Image Stamp Config").setView(scroll).setPositiveButton("Save", null).show()
            }
            else -> {}
        }
    }

    private fun showSliderDialog(act: Activity, labelText: String, currentProgress: Int, minVal: Int, maxVal: Int, dialogTitle: String, onProgressChangedAction: (Int) -> Unit) {
        val density = act.resources.displayMetrics.density
        val scroll = ScrollView(act)
        val root = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), (24 * density).toInt()) }
        scroll.addView(root)
        val tv = TextView(act).apply { text = "$labelText: $currentProgress"; setTextColor(Color.WHITE) }
        val sb = SeekBar(act).apply {
            max = maxVal - minVal; progress = currentProgress - minVal
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = (12 * density).toInt() }
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { val real = p + minVal; onProgressChangedAction(real); tv.text = "$labelText: $real" }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        root.addView(tv); root.addView(sb)
        MaterialAlertDialogBuilder(act).setTitle(dialogTitle).setView(scroll).setPositiveButton("OK", null).show()
    }

    fun showResolutionDialog(act: Activity, cv: InteractiveCanvasView) {
        val density = act.resources.displayMetrics.density
        val scroll = ScrollView(act)
        val root = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), (24 * density).toInt()) }
        scroll.addView(root)
        val etWidth = EditText(act).apply { hint = "Width"; setInputType(android.text.InputType.TYPE_CLASS_NUMBER); setText(cv.canvasWidth.toString()) }
        val etHeight = EditText(act).apply { hint = "Height"; setInputType(android.text.InputType.TYPE_CLASS_NUMBER); setText(cv.canvasHeight.toString()) }
        root.addView(etWidth); root.addView(etHeight)

        MaterialAlertDialogBuilder(act).setTitle("Resize Canvas (From Center)").setView(scroll).setPositiveButton("Resize") { d, _ ->
            try {
                val w = etWidth.text.toString().trim().toInt().coerceIn(100, 4096)
                val h = etHeight.text.toString().trim().toInt().coerceIn(100, 4096)
                cv.resizeCanvasFromCenter(w, h)
            } catch(e: Exception) {}
            d.dismiss()
        }.setNegativeButton("Cancel", null).show()
    }
    fun showColorPickerDialog(act: Activity, cv: InteractiveCanvasView, imgColor: ImageView) {
        if (act.isFinishing || act.isDestroyed) return
        val d = act.resources.displayMetrics.density
        val outerScroll = ScrollView(act)
        val container = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        outerScroll.addView(container)

        val prefs = act.getSharedPreferences("canvas_prefs", Activity.MODE_PRIVATE)
        if (recentColorsList.isEmpty()) {
            val savedColorsStr = prefs.getString("recent_colors_palette", "") ?: ""
            if (savedColorsStr.isNotEmpty()) {
                savedColorsStr.split(",").forEach { 
                    try { recentColorsList.add(it.toInt()) } catch(e: Exception){}
                }
            }
        }

        val tabLayout = TabLayout(act).apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { gravity = Gravity.CENTER }; addTab(newTab().setText("Presets")); addTab(newTab().setText("Recent")); addTab(newTab().setText("HSV Pad")); addTab(newTab().setText("HEX")) }
        container.addView(tabLayout)
        val contentFrame = FrameLayout(act).apply { layoutParams = LinearLayout.LayoutParams(-1, (240 * d).toInt()); setPadding((16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt(), (16 * d).toInt()) }
        container.addView(contentFrame)

        var currentSelectedColor = cv.brushColor
        var activeAlpha = Color.alpha(cv.brushColor)
        val hsv = FloatArray(3)
        Color.colorToHSV(currentSelectedColor, hsv)

        val bottomRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt()) }
        val currentBox = View(act).apply { layoutParams = LinearLayout.LayoutParams((80 * d).toInt(), (40 * d).toInt()).apply { rightMargin = (24 * d).toInt() }; setBackgroundColor(cv.brushColor) }
        val selectedBox = View(act).apply { layoutParams = LinearLayout.LayoutParams((80 * d).toInt(), (40 * d).toInt()); setBackgroundColor(cv.brushColor) }
        bottomRow.addView(TextView(act).apply { text = "Current: "; setTextColor(Color.WHITE) })
        bottomRow.addView(currentBox)
        bottomRow.addView(TextView(act).apply { text = "Selected: "; setTextColor(Color.WHITE) })
        bottomRow.addView(selectedBox)
        container.addView(bottomRow)

        fun updateTabContent(position: Int) {
            contentFrame.removeAllViews()
            when (position) {
                0 -> {
                    val grid = GridLayout(act).apply { columnCount = 4; layoutParams = FrameLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER } }
                    val colors = intArrayOf(Color.BLACK, Color.WHITE, Color.GRAY, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.DKGRAY, Color.LTGRAY, Color.TRANSPARENT)
                    for (c in colors) {
                        val circle = View(act).apply {
                            layoutParams = GridLayout.LayoutParams().apply { width = (44 * d).toInt(); height = (44 * d).toInt(); setMargins((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt()) }
                            val baseBg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c); setStroke((1 * d).toInt(), 0xFF666666.toInt()) }
                            val rippleColor = android.content.res.ColorStateList.valueOf(if (c == Color.WHITE) 0x22000000 else 0x44FFFFFF)
                            background = android.graphics.drawable.RippleDrawable(rippleColor, baseBg, null)
                            isClickable = true
                            isFocusable = true
                            setOnClickListener { currentSelectedColor = c; selectedBox.setBackgroundColor(c) }
                        }
                        grid.addView(circle)
                    }
                    contentFrame.addView(grid)
                }
                1 -> {
                    if (recentColorsList.isEmpty()) {
                        contentFrame.addView(TextView(act).apply { text = "No recent colors yet."; setTextColor(Color.GRAY); gravity = Gravity.CENTER })
                    } else {
                        val recentScroll = ScrollView(act).apply { 
                            layoutParams = FrameLayout.LayoutParams(-1, -1)
                            setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
                            setClipToPadding(false)
                        }
                        val recentGrid = GridLayout(act).apply { columnCount = 4; layoutParams = FrameLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL } }
                        recentColorsList.forEach { c ->
                            val circle = View(act).apply {
                                layoutParams = GridLayout.LayoutParams().apply { width = (44 * d).toInt(); height = (44 * d).toInt(); setMargins((8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt(), (8 * d).toInt()) }
                                val baseBg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c); setStroke((1 * d).toInt(), 0xFF666666.toInt()) }
                                val rippleColor = android.content.res.ColorStateList.valueOf(if (c == Color.WHITE) 0x22000000 else 0x44FFFFFF)
                                background = android.graphics.drawable.RippleDrawable(rippleColor, baseBg, null)
                                isClickable = true
                                isFocusable = true
                                setOnClickListener { currentSelectedColor = c; selectedBox.setBackgroundColor(c) }
                                setOnLongClickListener {
                                    MaterialAlertDialogBuilder(act)
                                        .setTitle("Delete Color")
                                        .setMessage("Remove this color from recent palette?")
                                        .setPositiveButton("Delete") { _, _ ->
                                            recentColorsList.remove(c)
                                            val outStr = recentColorsList.joinToString(",")
                                            prefs.edit().putString("recent_colors_palette", outStr).apply()
                                            updateTabContent(1)
                                        }
                                        .setNegativeButton("Cancel", null)
                                        .show()
                                    true
                                }
                            }
                            recentGrid.addView(circle)
                        }
                        recentScroll.addView(recentGrid)
                        contentFrame.addView(recentScroll)
                    }
                }
                2 -> {
                    val rootHsv = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
                    val sbHue = SeekBar(act).apply { max = 360; progress = hsv[0].toInt(); setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { hsv[0] = p.toFloat(); currentSelectedColor = Color.HSVToColor(activeAlpha, hsv); selectedBox.setBackgroundColor(currentSelectedColor) }
                        override fun onStartTrackingTouch(s: SeekBar?) {}
                        override fun onStopTrackingTouch(s: SeekBar?) {}
                    })}
                    val sbSat = SeekBar(act).apply { max = 100; progress = (hsv[1] * 100).toInt(); setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { hsv[1] = p / 100f; currentSelectedColor = Color.HSVToColor(activeAlpha, hsv); selectedBox.setBackgroundColor(currentSelectedColor) }
                        override fun onStartTrackingTouch(s: SeekBar?) {}
                        override fun onStopTrackingTouch(s: SeekBar?) {}
                    })}
                    val sbVal = SeekBar(act).apply { max = 100; progress = (hsv[2] * 100).toInt(); setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { hsv[2] = p / 100f; currentSelectedColor = Color.HSVToColor(activeAlpha, hsv); selectedBox.setBackgroundColor(currentSelectedColor) }
                        override fun onStartTrackingTouch(s: SeekBar?) {}
                        override fun onStopTrackingTouch(s: SeekBar?) {}
                    })}
                    val sbAlpha = SeekBar(act).apply { max = 255; progress = activeAlpha; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) { activeAlpha = p; currentSelectedColor = Color.HSVToColor(activeAlpha, hsv); selectedBox.setBackgroundColor(currentSelectedColor) }
                        override fun onStartTrackingTouch(s: SeekBar?) {}
                        override fun onStopTrackingTouch(s: SeekBar?) {}
                    })}
                    rootHsv.addView(TextView(act).apply { text = "Hue"; setTextColor(Color.WHITE) }); rootHsv.addView(sbHue)
                    rootHsv.addView(TextView(act).apply { text = "Saturation"; setTextColor(Color.WHITE) }); rootHsv.addView(sbSat)
                    rootHsv.addView(TextView(act).apply { text = "Value"; setTextColor(Color.WHITE) }); rootHsv.addView(sbVal)
                    rootHsv.addView(TextView(act).apply { text = "Alpha"; setTextColor(Color.WHITE) }); rootHsv.addView(sbAlpha)
                    contentFrame.addView(rootHsv)
                }
                3 -> {
                    val hexLayout = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
                    val et = EditText(act).apply { 
                        hint = "#FFFFFFFF"
                        setText(String.format("#%08X", currentSelectedColor))
                        isFocusableInTouchMode = true
                        inputType = android.text.InputType.TYPE_CLASS_TEXT
                        setSingleLine(true)
                    }
                    et.addTextChangedListener(object : android.text.TextWatcher {
                        override fun afterTextChanged(s: android.text.Editable?) { try { val c = Color.parseColor(s.toString().trim()); currentSelectedColor = c; Color.colorToHSV(c, hsv); activeAlpha = Color.alpha(c); selectedBox.setBackgroundColor(c) } catch (e: Exception) {} }
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    })
                    hexLayout.addView(et)
                    contentFrame.addView(hexLayout)

                    et.requestFocus()
                    et.postDelayed({
                        val imm = act.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
                    }, 200)
                }
            }
        }

        val paletteDialog = MaterialAlertDialogBuilder(act)
            .setTitle("Advanced Palette")
            .setView(outerScroll)
            .setPositiveButton("Apply") { _, _ -> 
                cv.brushColor = currentSelectedColor
                imgColor.setColorFilter(currentSelectedColor, android.graphics.PorterDuff.Mode.SRC_IN)
                
                if (recentColorsList.contains(currentSelectedColor)) {
                    recentColorsList.remove(currentSelectedColor)
                }
                
                recentColorsList.add(0, currentSelectedColor)
                
                val outStr = recentColorsList.joinToString(",")
                prefs.edit().putString("recent_colors_palette", outStr).apply()
            }
            .setNegativeButton("Cancel", null)
            .create()

        paletteDialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        paletteDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { 
                if (tab.position != 3) {
                    val imm = act.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(tabLayout.windowToken, 0)
                }
                updateTabContent(tab.position) 
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        
        updateTabContent(0)
        paletteDialog.show()
    }
}