package com.kevin.vision.canvas_demo

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.os.Environment
import android.provider.MediaStore
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

class ImageCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var image: Bitmap? = null
    private data class ColoredRect(val rect: RectF, val color: Int, val strokeWidth: Float)
    private val rectangles = mutableListOf<ColoredRect>()
    private val deletedRectangles = mutableListOf<ColoredRect>()  // 存储已删除的矩形
    private var currentRect: RectF? = null
    private var draggedRect: ColoredRect? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var scaleFactor = 1f
    private var isDrawMode = true  // 添加绘制模式标志
    private var isScaling = false  // 添加缩放状态标志
    private var normalColor = Color.RED  // 添加颜色变量
    private var selectedColor = Color.BLUE  // 添加选中状态的颜色变量
    private val normalPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val selectedPaint = Paint().apply {
        color = selectedColor
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    fun setStrokeWidth(width: Float) {
        normalPaint.strokeWidth = width
        selectedPaint.strokeWidth = width
        // 将当前画笔宽度保存为类成员变量
        currentStrokeWidth = width
        invalidate()
    }

    private var currentStrokeWidth = 5f  // 添加新的成员变量来保存当前画笔宽度

    private val matrix = Matrix()
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val overlayPaint = Paint().apply {
        color = Color.parseColor("#33000000")  // 半透明黑色
        style = Paint.Style.FILL
    }
    private var touchedRect: ColoredRect? = null  // 记录当前触摸的矩形

    fun setImage(bitmap: Bitmap) {
        image = bitmap
        invalidate()
    }

    fun undoLastRectangle() {
        if (rectangles.isNotEmpty()) {
            val removedRect = rectangles.removeAt(rectangles.size - 1)
            deletedRectangles.add(removedRect)  // 保存删除的矩形
            invalidate()
        }
    }

    // 添加还原方法
    fun redoLastRectangle() {
        if (deletedRectangles.isNotEmpty()) {
            val redoRect = deletedRectangles.removeAt(deletedRectangles.size - 1)
            rectangles.add(redoRect)
            invalidate()
        }
    }

    // 添加切换模式的方法
    fun toggleMode() {
        isDrawMode = !isDrawMode
        invalidate()
    }

    // 获取当前模式
    fun isInDrawMode() = isDrawMode

    fun saveImageToGallery(): Boolean {
        val bitmap = drawToBitmap() ?: return false
        return try {
            val filename = "Canvas_${System.currentTimeMillis()}.png"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            
            uri?.let { imageUri ->
                resolver.openOutputStream(imageUri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
                true
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun drawToBitmap(): Bitmap? {
        if (width == 0 || height == 0) return null
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 绘制当前视图内容到bitmap
        image?.let { img ->
            canvas.save()
            canvas.scale(scaleFactor, scaleFactor)
            canvas.drawBitmap(img, matrix, null)
            
            for (coloredRect in rectangles) {
                normalPaint.color = coloredRect.color
                canvas.drawRect(coloredRect.rect, normalPaint)
            }
            
            currentRect?.let {
                normalPaint.color = normalColor
                canvas.drawRect(it, normalPaint)
            }
            
            canvas.restore()
        }
        
        return bitmap
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        image?.let { img ->
            canvas.save()
            canvas.scale(scaleFactor, scaleFactor)
            canvas.drawBitmap(img, matrix, null)
            
            // Draw all rectangles
            for (coloredRect in rectangles) {
                // 选择画笔：如果是当前触摸的矩形使用蓝色，否则使用原始颜色
                if (coloredRect == touchedRect || coloredRect == draggedRect) {
                    selectedPaint.strokeWidth = coloredRect.strokeWidth
                    canvas.drawRect(coloredRect.rect, selectedPaint)
                    canvas.drawRect(coloredRect.rect, overlayPaint)
                } else {
                    normalPaint.color = coloredRect.color
                    normalPaint.strokeWidth = coloredRect.strokeWidth
                    canvas.drawRect(coloredRect.rect, normalPaint)
                }
            }
            
            // Draw current rectangle if exists
            currentRect?.let {
                normalPaint.color = normalColor
                normalPaint.strokeWidth = currentStrokeWidth  // 使用当前设置的画笔宽度
                canvas.drawRect(it, normalPaint)
            }
            
            canvas.restore()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        
        val x = event.x / scaleFactor
        val y = event.y / scaleFactor
        
        when (event.action and MotionEvent.ACTION_MASK) {  // 使用ACTION_MASK来正确处理多点触控
            MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount == 1 && !isScaling) {  // 只有在非缩放状态下才处理绘制
                    if (!isDrawMode) {
                        touchedRect = findTouchedRectangle(x, y)
                        draggedRect = touchedRect
                    }
                    if (isDrawMode || draggedRect == null) {
                        currentRect = RectF(x, y, x, y)
                    }
                }
                lastTouchX = x
                lastTouchY = y
                invalidate()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // 第二个手指按下时，取消当前的绘制操作
                currentRect = null
                draggedRect = null
                isScaling = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isScaling && event.pointerCount == 1) {  // 只有在非缩放状态且单指时才处理移动
                    val dx = x - lastTouchX
                    val dy = y - lastTouchY
                    
                    draggedRect?.let { coloredRect ->
                        // Move rectangle within image bounds
                        val rect = coloredRect.rect
                        val newLeft = rect.left + dx
                        val newTop = rect.top + dy
                        val newRight = rect.right + dx
                        val newBottom = rect.bottom + dy
                        
                        image?.let { img ->
                            if (newLeft >= 0 && newRight <= img.width &&
                                newTop >= 0 && newBottom <= img.height) {
                                rect.offset(dx, dy)
                            }
                        }
                    } ?: currentRect?.let { rect ->
                        rect.right = x
                        rect.bottom = y
                    }
                    
                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isScaling && draggedRect == null && currentRect != null) {
                    normalizeRect(currentRect!!)
                    // 使用当前保存的画笔宽度创建新的矩形
                    rectangles.add(ColoredRect(currentRect!!, normalColor, currentStrokeWidth))
                    deletedRectangles.clear()  // 添加新矩形时清空已删除列表
                }
                currentRect = null
                touchedRect = null  // 清除触摸状态
                isScaling = false  // 重置缩放状态
                invalidate()
            }
            MotionEvent.ACTION_POINTER_UP -> {
                isScaling = false  // 当抬起一个手指时重置缩放状态
            }
        }
        return true
    }

    private fun normalizeRect(rect: RectF) {
        if (rect.right < rect.left) {
            val temp = rect.left
            rect.left = rect.right
            rect.right = temp
        }
        if (rect.bottom < rect.top) {
            val temp = rect.top
            rect.top = rect.bottom
            rect.bottom = temp
        }
    }

    private fun findTouchedRectangle(x: Float, y: Float): ColoredRect? {
        for (coloredRect in rectangles.asReversed()) {
            if (coloredRect.rect.contains(x, y)) {
                return coloredRect
            }
        }
        return null
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true  // 开始缩放时设置标志
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = max(0.1f, min(scaleFactor, 5.0f))
            invalidate()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false  // 结束缩放时重置标志
        }
    }

    // 添加设置普通状态画笔颜色的方法
    fun setNormalColor(color: Int) {
        normalColor = color
        normalPaint.color = color
        invalidate()
    }

    // 添加设置选中状态画笔颜色的方法
    fun setSelectedColor(color: Int) {
        selectedColor = color
        selectedPaint.color = color
        invalidate()
    }

    // 添加同时设置两种状态画笔颜色的方法
    fun setBothColors(normalColor: Int, selectedColor: Int) {
        setNormalColor(normalColor)
        setSelectedColor(selectedColor)
    }

    // 添加获取当前画笔宽度的方法
    fun getCurrentStrokeWidth(): Float {
        return currentStrokeWidth
    }

    // 获取当前正常状态的颜色
    fun getCurrentColor(): Int {
        return normalColor
    }
}
