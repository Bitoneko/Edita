package com.bitoneko.kouecanvas

import android.app.Activity
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView
import java.io.File

class InteractiveCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface CanvasHistoryListener {
        fun onHistoryChanged(canUndo: Boolean, canRedo: Boolean)
    }

    interface ColorPickedListener {
        fun onColorPicked(color: Int)
    }

    sealed class DrawAction {
        data class BaseImage(val layerId: String, val bitmap: Bitmap) : DrawAction()
        data class Line(val layerId: String, val path: Path, val color: Int, val width: Float, val isEraser: Boolean) : DrawAction()
        data class Fill(val layerId: String, val x: Int, val y: Int, val color: Int) : DrawAction()
        data class TextStamp(val layerId: String, val text: String, val x: Float, val y: Float, val size: Float, val rotation: Float, val color: Int) : DrawAction()
        data class ImageStamp(val layerId: String, val bitmap: Bitmap, val x: Float, val y: Float, val scaleX: Float, val scaleY: Float, val rotation: Float) : DrawAction()
        
        data class AddLayer(val layerId: String, val name: String) : DrawAction()
        data class DeleteLayer(val layerId: String, val name: String, val cachedBitmapSnapshot: Bitmap) : DrawAction()
    }

    var historyListener: CanvasHistoryListener? = null
    var colorPickedListener: ColorPickedListener? = null
    var currentTool = ToolMode.BRUSH
    var brushColor = Color.BLACK
    var brushSize = 10f
    var eraserSize = 20f
    var fillTolerance = 15
    
    var stampText = "Edita 2"
    var stampTextSize = 40f
    var stampBitmap: Bitmap? = null
    var stampScaleX = 1.0f
    var stampScaleY = 1.0f
    var stampRotation = 0f
    
    var layersList = ArrayList<CanvasLayer>()
    var activeLayerIndex = 0

    var canvasWidth = 500
    var canvasHeight = 500
    private var projectId: String = ""
    private var projectName: String = "New Canvas Project"
    private var scaleFactor = 1.0f
    private var offsetX = 0.0f
    private var offsetY = 0.0f
    private var lastTouchX = 0.0f
    private var lastTouchY = 0.0f
    private var activePointerId = -1

    private val undoActions = ArrayList<DrawAction>()
    private val redoActions = ArrayList<DrawAction>()
    private var isMultiTouchDrawingBlocked = false

    val currentDrawPath = Path()
    private var checkerPaint = Paint()
    private val layerBlendPaint = Paint().apply { isAntiAlias = true; isDither = true; isFilterBitmap = true }
    private val borderPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }

    val drawPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        initCheckerboardPattern()
    }

    private fun initCheckerboardPattern() {
        val size = 16
        val bitmap = Bitmap.createBitmap(size * 2, size * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val p = Paint()
        p.color = Color.parseColor("#FFFFFFFF")
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        canvas.drawRect(size.toFloat(), size.toFloat(), (size * 2).toFloat(), (size * 2).toFloat(), p)
        p.color = Color.parseColor("#FFDDDDDD")
        canvas.drawRect(size.toFloat(), 0f, (size * 2).toFloat(), size.toFloat(), p)
        canvas.drawRect(0f, size.toFloat(), size.toFloat(), (size * 2).toFloat(), p)
        checkerPaint.shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
    fun initCanvasProject(id: String, name: String, width: Int, height: Int) {
        this.projectId = id
        this.projectName = name
        this.canvasWidth = width
        this.canvasHeight = height
        
        layersList.forEach { it.bitmap.recycle() }
        layersList.clear()
        undoActions.clear()
        redoActions.clear()
        
        val pFile = File(context.filesDir, "preview_$id.png")
        if (pFile.exists()) {
            val opt = BitmapFactory.Options().apply { inMutable = true }
            val savedBmp = BitmapFactory.decodeFile(pFile.absolutePath, opt)
            if (savedBmp != null) {
                this.canvasWidth = savedBmp.width
                this.canvasHeight = savedBmp.height
                layersList.add(CanvasLayer("layer_0", "Layer 1", savedBmp))
                undoActions.add(DrawAction.BaseImage("layer_0", savedBmp.copy(savedBmp.config, true)))
            }
        }
        
        if (layersList.isEmpty()) {
            val defaultBmp = Bitmap.createBitmap(this.canvasWidth, this.canvasHeight, Bitmap.Config.ARGB_8888)
            Canvas(defaultBmp).drawColor(Color.WHITE)
            layersList.add(CanvasLayer("layer_0", "Layer 1", defaultBmp))
            undoActions.add(DrawAction.BaseImage("layer_0", defaultBmp.copy(defaultBmp.config, true)))
        }
        
        activeLayerIndex = 0
        notifyHistoryListener()
        invalidate()
    }

    fun forceSetDimensionsAndBitmap(w: Int, h: Int, bmp: Bitmap) {
        this.canvasWidth = w
        this.canvasHeight = h
        layersList.forEach { it.bitmap.recycle() }
        layersList.clear()
        layersList.add(CanvasLayer("layer_0", "Layer 1", bmp))
        activeLayerIndex = 0
        undoActions.clear()
        redoActions.clear()
        undoActions.add(DrawAction.BaseImage("layer_0", bmp.copy(bmp.config, true)))
        notifyHistoryListener()
        saveCurrentStateToDiskAsync()
        invalidate()
    }

    fun resizeCanvasFromCenter(newW: Int, newH: Int) {
        if (newW <= 0 || newH <= 0 || layersList.isEmpty()) return
        val dx = (newW - canvasWidth) / 2
        val dy = (newH - canvasHeight) / 2
        
        layersList.forEach { layer ->
            val newBmp = Bitmap.createBitmap(newW, newH, Bitmap.Config.ARGB_8888)
            val c = Canvas(newBmp)
            c.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            c.drawBitmap(layer.bitmap, dx.toFloat(), dy.toFloat(), null)
            layer.bitmap.recycle()
            layer.bitmap = newBmp
        }
        this.canvasWidth = newW
        this.canvasHeight = newH
        saveCurrentStateToDiskAsync()
        invalidate()
    }
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = Math.max(0.05f, Math.min(scaleFactor, 20.0f))
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!scaleDetector.isInProgress && (currentTool == ToolMode.PAN || e2.pointerCount == 2)) {
                offsetX -= distanceX
                offsetY -= distanceY
                invalidate()
            }
            return true
        }
    })

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        gestureDetector.onTouchEvent(ev)
        val action = ev.action and MotionEvent.ACTION_MASK
        val totalPointers = ev.pointerCount

        if (totalPointers > 1) {
            isMultiTouchDrawingBlocked = true
            currentDrawPath.reset()
            invalidate()
            return true
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            val wasBlocked = isMultiTouchDrawingBlocked
            isMultiTouchDrawingBlocked = false
            activePointerId = -1
            if (wasBlocked && currentTool != ToolMode.PAN) {
                currentDrawPath.reset()
                invalidate()
                return true
            }
        }

        if (currentTool != ToolMode.PAN && !isMultiTouchDrawingBlocked && totalPointers == 1 && activeLayerIndex < layersList.size) {
            val activeLayer = layersList[activeLayerIndex]
            if (activeLayer.isVisible) {
                val modelX = (ev.x - (offsetX + (width - canvasWidth * scaleFactor) / 2f)) / scaleFactor
                val modelY = (ev.y - (offsetY + (height - canvasHeight * scaleFactor) / 2f)) / scaleFactor
                val layerCanvas = Canvas(activeLayer.bitmap)

                when (action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = ev.x
                        lastTouchY = ev.y
                        if (currentTool == ToolMode.PIPPETE) {
                            val px = modelX.toInt()
                            val py = modelY.toInt()
                            if (px in 0 until activeLayer.bitmap.width && py in 0 until activeLayer.bitmap.height) {
                                val color = activeLayer.bitmap.getPixel(px, py)
                                brushColor = color
                                colorPickedListener?.onColorPicked(color)
                            }
                        } else if (currentTool != ToolMode.FILL && currentTool != ToolMode.STAMP_IMAGE && currentTool != ToolMode.STAMP_TEXT) {
                            setupPaintForCurrentTool()
                            currentDrawPath.reset()
                            currentDrawPath.moveTo(modelX, modelY)
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (currentTool == ToolMode.PIPPETE) {
                            val px = modelX.toInt()
                            val py = modelY.toInt()
                            if (px in 0 until activeLayer.bitmap.width && py in 0 until activeLayer.bitmap.height) {
                                val color = activeLayer.bitmap.getPixel(px, py)
                                brushColor = color
                                colorPickedListener?.onColorPicked(color)
                            }
                        } else if (currentTool != ToolMode.FILL && currentTool != ToolMode.STAMP_IMAGE && currentTool != ToolMode.STAMP_TEXT) {
                            currentDrawPath.lineTo(modelX, modelY)
                            invalidate()
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        val totalDeltaX = Math.abs(ev.x - lastTouchX)
                        val totalDeltaY = Math.abs(ev.y - lastTouchY)
                        val isIntentionalTap = totalDeltaX < 10f && totalDeltaY < 10f

                        val currentLayerId = activeLayer.id

                        when (currentTool) {
                            ToolMode.FILL -> {
                                if (isIntentionalTap) {
                                    performFloodFillScanline(activeLayer.bitmap, modelX.toInt(), modelY.toInt(), brushColor)
                                    undoActions.add(DrawAction.Fill(currentLayerId, modelX.toInt(), modelY.toInt(), brushColor))
                                    redoActions.clear()
                                    notifyHistoryListener()
                                    saveCurrentStateToDiskAsync()
                                    invalidate()
                                }
                            }
                            ToolMode.STAMP_IMAGE -> {
                                if (isIntentionalTap) {
                                    stampBitmap?.let { bmp ->
                                        val matrix = Matrix().apply {
                                            postTranslate(-bmp.width / 2f, -bmp.height / 2f)
                                            postScale(stampScaleX, stampScaleY)
                                            postRotate(stampRotation)
                                            postTranslate(modelX, modelY)
                                        }
                                        layerCanvas.drawBitmap(bmp, matrix, Paint(Paint.ANTI_ALIAS_FLAG))
                                        undoActions.add(DrawAction.ImageStamp(currentLayerId, bmp, modelX, modelY, stampScaleX, stampScaleY, stampRotation))
                                        redoActions.clear()
                                        notifyHistoryListener()
                                        saveCurrentStateToDiskAsync()
                                        invalidate()
                                    }
                                }
                            }
                            ToolMode.STAMP_TEXT -> {
                                if (isIntentionalTap) {
                                    val tPaint = Paint().apply { color = brushColor; textSize = stampTextSize; isAntiAlias = true }
                                    layerCanvas.save()
                                    layerCanvas.rotate(stampRotation, modelX, modelY)
                                    
                                    val lines = stampText.split("\n")
                                    var currentY = modelY
                                    val leading = tPaint.fontSpacing
                                    
                                    lines.forEach { line ->
                                        layerCanvas.drawText(line, modelX, currentY, tPaint)
                                        currentY += leading
                                    }
                                    layerCanvas.restore()
                                    undoActions.add(DrawAction.TextStamp(currentLayerId, stampText, modelX, modelY, stampTextSize, stampRotation, brushColor))
                                    redoActions.clear()
                                    notifyHistoryListener()
                                    saveCurrentStateToDiskAsync()
                                    invalidate()
                                }
                            }
                            ToolMode.PIPPETE -> {}
                            else -> {
                                currentDrawPath.lineTo(modelX, modelY)
                                layerCanvas.drawPath(currentDrawPath, drawPaint)
                                val isEraser = currentTool == ToolMode.ERASER
                                undoActions.add(DrawAction.Line(currentLayerId, Path(currentDrawPath), brushColor, if (isEraser) eraserSize else brushSize, isEraser))
                                redoActions.clear()
                                notifyHistoryListener()
                                currentDrawPath.reset()
                                saveCurrentStateToDiskAsync()
                                invalidate()
                            }
                        }
                    }
                }
                return true
            }
        }
        return true
    }

    private fun setupPaintForCurrentTool() {
        if (currentTool == ToolMode.ERASER || brushColor == Color.TRANSPARENT) {
            drawPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            drawPaint.strokeWidth = if (currentTool == ToolMode.ERASER) eraserSize else brushSize
        } else {
            drawPaint.xfermode = null
            drawPaint.color = brushColor
            drawPaint.strokeWidth = brushSize
        }
    }
    private fun performFloodFillScanline(bmp: Bitmap, startX: Int, startY: Int, targetColor: Int) {
        if (startX !in 0 until bmp.width || startY !in 0 until bmp.height) return
        val srcColor = bmp.getPixel(startX, startY)
        if (srcColor == targetColor) return
        
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        
        val stackX = IntArray(w * h)
        val stackY = IntArray(w * h)
        var top = 0
        
        stackX[top] = startX
        stackY[top] = startY
        top++
        
        while (top > 0) {
            top--
            val cx = stackX[top]
            val cy = stackY[top]
            
            var left = cx
            while (left >= 0 && colorMatch(pixels[cy * w + left], srcColor, fillTolerance)) { left-- }
            left++
            
            var right = cx
            while (right < w && colorMatch(pixels[cy * w + right], srcColor, fillTolerance)) { right++ }
            right--
            
            for (x in left..right) { pixels[cy * w + x] = targetColor }
            
            var scanUp = false
            var scanDown = false
            for (x in left..right) {
                if (cy > 0) {
                    val idxUp = (cy - 1) * w + x
                    val upMatch = colorMatch(pixels[idxUp], srcColor, fillTolerance) && pixels[idxUp] != targetColor
                    if (upMatch && !scanUp) {
                        if (top < stackX.size) { stackX[top] = x; stackY[top] = cy - 1; top++ }
                        scanUp = true
                    } else if (!upMatch && scanUp) { scanUp = false }
                }
                if (cy < h - 1) {
                    val idxDown = (cy + 1) * w + x
                    val downMatch = colorMatch(pixels[idxDown], srcColor, fillTolerance) && pixels[idxDown] != targetColor
                    if (downMatch && !scanDown) {
                        if (top < stackX.size) { stackX[top] = x; stackY[top] = cy + 1; top++ }
                        scanDown = true
                    } else if (!downMatch && scanDown) { scanDown = false }
                }
            }
        }
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    private fun colorMatch(c1: Int, c2: Int, tol: Int): Boolean {
        if (c1 == c2) return true
        if ((c1 shr 24 and 0xFF) == 0 && (c2 shr 24 and 0xFF) == 0) return true
        
        val r1 = (c1 shr 16) and 0xFF
        val g1 = (c1 shr 8) and 0xFF
        val b1 = c1 and 0xFF
        val a1 = (c1 shr 24) and 0xFF

        val r2 = (c2 shr 16) and 0xFF
        val g2 = (c2 shr 8) and 0xFF
        val b2 = c2 and 0xFF
        val a2 = (c2 shr 24) and 0xFF

        if (Math.abs(a1 - a2) > tol) return false
        return Math.abs(r1 - r2) <= tol && Math.abs(g1 - g2) <= tol && Math.abs(b1 - b2) <= tol
    }

    fun historyLogAddLayer(layerId: String, name: String) {
        undoActions.add(DrawAction.AddLayer(layerId, name))
        redoActions.clear()
        notifyHistoryListener()
    }

    fun historyLogDeleteLayer(layerId: String, name: String, currentBitmap: Bitmap) {
        val snapshot = currentBitmap.copy(currentBitmap.config, true)
        undoActions.add(DrawAction.DeleteLayer(layerId, name, snapshot))
        redoActions.clear()
        notifyHistoryListener()
    }

    fun performUndo() {
        if (undoActions.size > 0) {
            val lastAction = undoActions.last()
            if (lastAction is DrawAction.BaseImage && undoActions.size == 1) return
            redoActions.add(undoActions.removeAt(undoActions.size - 1))
            rebuildCanvasFromActions()
        }
    }

    fun performRedo() {
        if (redoActions.isNotEmpty()) {
            undoActions.add(redoActions.removeAt(redoActions.size - 1))
            rebuildCanvasFromActions()
        }
    }

        private val saveExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private fun rebuildCanvasFromActions() {
        if (layersList.isEmpty()) return
        
        layersList.forEach { it.bitmap.recycle() }
        layersList.clear()
        
        val defaultBmp = Bitmap.createBitmap(this.canvasWidth, this.canvasHeight, Bitmap.Config.ARGB_8888)
        layersList.add(CanvasLayer("layer_0", "Layer 1", defaultBmp))
        activeLayerIndex = 0

        val p = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }

        undoActions.forEach { action ->
            when (action) {
                is DrawAction.AddLayer -> {
                    val newBmp = Bitmap.createBitmap(this.canvasWidth, this.canvasHeight, Bitmap.Config.ARGB_8888)
                    layersList.add(CanvasLayer(action.layerId, action.name, newBmp))
                }
                is DrawAction.DeleteLayer -> {
                    layersList.removeAll { it.id == action.layerId }
                }
                is DrawAction.BaseImage, is DrawAction.Line, is DrawAction.Fill, is DrawAction.TextStamp, is DrawAction.ImageStamp -> {
                    val targetLayerId = when (action) {
                        is DrawAction.BaseImage -> action.layerId
                        is DrawAction.Line -> action.layerId
                        is DrawAction.Fill -> action.layerId
                        is DrawAction.TextStamp -> action.layerId
                        is DrawAction.ImageStamp -> action.layerId
                        else -> "layer_0"
                    }
                    
                    var targetLayer = layersList.find { it.id == targetLayerId }
                    if (targetLayer == null && action is DrawAction.BaseImage) {
                        val restoredBmp = Bitmap.createBitmap(this.canvasWidth, this.canvasHeight, Bitmap.Config.ARGB_8888)
                        targetLayer = CanvasLayer(targetLayerId, "Restored Layer", restoredBmp)
                        layersList.add(targetLayer)
                    }
                    
                    targetLayer?.let { layer ->
                        val canvas = Canvas(layer.bitmap)
                        when (action) {
                            is DrawAction.BaseImage -> canvas.drawBitmap(action.bitmap, 0f, 0f, null)
                            is DrawAction.Line -> {
                                p.color = action.color; p.strokeWidth = action.width
                                p.xfermode = if (action.isEraser) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
                                canvas.drawPath(action.path, p)
                            }
                            is DrawAction.Fill -> performFloodFillScanline(layer.bitmap, action.x, action.y, action.color)
                            is DrawAction.TextStamp -> {
                                val tPaint = Paint().apply { color = action.color; textSize = action.size; isAntiAlias = true }
                                canvas.save(); canvas.rotate(action.rotation, action.x, action.y)
                                val lines = action.text.split("\n")
                                var currentY = action.y
                                lines.forEach { line -> canvas.drawText(line, action.x, currentY, tPaint); currentY += tPaint.fontSpacing }
                                canvas.restore()
                            }
                            is DrawAction.ImageStamp -> {
                                val matrix = Matrix().apply { postTranslate(-action.bitmap.width / 2f, -action.bitmap.height / 2f); postScale(action.scaleX, action.scaleY); postRotate(action.rotation); postTranslate(action.x, action.y) }
                                canvas.drawBitmap(action.bitmap, matrix, Paint(Paint.ANTI_ALIAS_FLAG))
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
        
        if (activeLayerIndex >= layersList.size) activeLayerIndex = layersList.size - 1
        if (activeLayerIndex < 0) activeLayerIndex = 0
        
        notifyHistoryListener()
        saveCurrentStateToDiskAsync()
        invalidate()
    }

    fun saveCurrentStateToDiskAsync() {
        if (layersList.isEmpty() || projectId.isEmpty()) return
        val compositeBmp = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val compCanvas = Canvas(compositeBmp)
        layersList.forEach { if (it.isVisible) compCanvas.drawBitmap(it.bitmap, 0f, 0f, null) }
        saveExecutor.execute {
            try {
                CanvasProjectManager.autoSaveCanvasWorkspace(context, projectId, projectName, canvasWidth, canvasHeight, compositeBmp)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                compositeBmp.recycle()
            }
        }
    }

    private fun notifyHistoryListener() { 
        val canUndo = undoActions.size > 1 || (undoActions.size == 1 && undoActions.first() !is DrawAction.BaseImage)
        historyListener?.onHistoryChanged(canUndo, redoActions.isNotEmpty()) 
    }

    fun releaseMemoryOnDestroy() {
        saveExecutor.shutdown()
        try { saveExecutor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS) } catch (e: Exception) {}
        layersList.forEach { it.bitmap.recycle() }
        layersList.clear()
        undoActions.clear()
        redoActions.clear()
        stampBitmap?.recycle()
        stampBitmap = null
        System.gc()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#FF1C1B1F"))
        canvas.save()
        canvas.translate(offsetX + (width - canvasWidth * scaleFactor) / 2f, offsetY + (height - canvasHeight * scaleFactor) / 2f)
        canvas.scale(scaleFactor, scaleFactor)
        canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), checkerPaint)
        layersList.forEach { layer ->
            if (layer.isVisible) { layerBlendPaint.alpha = layer.alpha; canvas.drawBitmap(layer.bitmap, 0f, 0f, layerBlendPaint) }
        }
        if (currentTool != ToolMode.FILL && currentTool != ToolMode.PAN && currentTool != ToolMode.PIPPETE && activeLayerIndex < layersList.size) {
            val isEraser = currentTool == ToolMode.ERASER
            val livePaint = Paint().apply {
                isAntiAlias = true; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
                strokeWidth = if (isEraser) eraserSize else brushSize
                color = if (isEraser) Color.TRANSPARENT else brushColor
                xfermode = if (isEraser) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
            }
            canvas.drawPath(currentDrawPath, livePaint)
        }
        canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), borderPaint)
        canvas.restore()
    }
}
