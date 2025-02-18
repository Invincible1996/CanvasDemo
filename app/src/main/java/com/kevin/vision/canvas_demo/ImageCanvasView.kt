package com.kevin.vision.canvas_demo

import android.animation.ValueAnimator
import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.os.Environment
import android.provider.MediaStore
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
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

    private val density = context.resources.displayMetrics.density
    private val controlPointRadius = 12f * density  // 视觉大小跟随屏幕密度
    private val controlPointTouchRadius = 60f * density  // 触摸区域跟随屏幕密度
    private val controlPointPaint = Paint().apply {
        color = Color.WHITE  // 使用白色填充
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 2f
        setShadowLayer(4f * density, 0f, 2f * density, Color.parseColor("#66000000"))  // 添加阴影效果
    }
    private val controlPointStrokePaint = Paint().apply {
        color = Color.parseColor("#2196F3")  // Material蓝色
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    // 枚举定义控制点位置
    private enum class ControlPoint {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, NONE
    }
    private var activeControlPoint = ControlPoint.NONE
    private var hoveredControlPoint: ControlPoint = ControlPoint.NONE
    private val controlPointHoverPaint = Paint().apply {
        color = Color.parseColor("#1A4FC3F7")  // 使用浅蓝色，更符合Material Design
        style = Paint.Style.FILL
    }
    
    private val controlPointHoverStrokePaint = Paint().apply {
        color = Color.parseColor("#4C4FC3F7")  // 稍深的蓝色边框
        style = Paint.Style.STROKE
        strokeWidth = 2f * density  // 边框宽度跟随屏幕密度
    }

    private val TOUCH_SLOP = 8f * density  // 触摸阈值也跟随屏幕密度
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var hasMovedBeyondSlop = false

    private var hoverAlpha = 0f
    private val hoverAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 150 // 150ms 的动画时长
        interpolator = FastOutSlowInInterpolator()
        addUpdateListener { animator ->
            hoverAlpha = animator.animatedValue as Float
            invalidate()
        }
    }

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
                if (coloredRect == touchedRect || coloredRect == draggedRect) {
                    selectedPaint.strokeWidth = coloredRect.strokeWidth
                    canvas.drawRect(coloredRect.rect, selectedPaint)
                    canvas.drawRect(coloredRect.rect, overlayPaint)
                    // 为选中的矩形绘制控制点
                    if (!isDrawMode) {
                        drawControlPoints(canvas, coloredRect.rect)
                    }
                } else {
                    normalPaint.color = coloredRect.color
                    normalPaint.strokeWidth = coloredRect.strokeWidth
                    canvas.drawRect(coloredRect.rect, normalPaint)
                }
            }
            
            // Draw current rectangle if exists
            currentRect?.let {
                normalPaint.color = normalColor
                normalPaint.strokeWidth = currentStrokeWidth
                canvas.drawRect(it, normalPaint)
            }
            
            canvas.restore()
        }
    }

    private fun drawControlPoints(canvas: Canvas, rect: RectF) {
        // 临时关闭硬件加速以支持阴影
        val wasHardwareAccelerated = canvas.isHardwareAccelerated
        if (wasHardwareAccelerated) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        val points = arrayOf(
            ControlPoint.TOP_LEFT to (rect.left to rect.top),
            ControlPoint.TOP_RIGHT to (rect.right to rect.top),
            ControlPoint.BOTTOM_LEFT to (rect.left to rect.bottom),
            ControlPoint.BOTTOM_RIGHT to (rect.right to rect.bottom)
        )
        
        points.forEach { (controlPoint, coords) ->
            val (x, y) = coords
            // 如果是当前悬停的控制点，先绘制提示圈
            if (controlPoint == hoveredControlPoint) {
                controlPointHoverPaint.alpha = (hoverAlpha * 26).toInt()  // 0x1A = 26
                controlPointHoverStrokePaint.alpha = (hoverAlpha * 76).toInt()  // 0x4C = 76
                canvas.drawCircle(x, y, controlPointTouchRadius / scaleFactor, controlPointHoverPaint)
                canvas.drawCircle(x, y, controlPointTouchRadius / scaleFactor, controlPointHoverStrokePaint)
            }
            // 绘制控制点
            canvas.drawCircle(x, y, controlPointRadius / scaleFactor, controlPointPaint)
            canvas.drawCircle(x, y, controlPointRadius / scaleFactor, controlPointStrokePaint)
        }

        // 恢复硬件加速
        if (wasHardwareAccelerated) {
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }
    }

    private fun findTouchedControlPoint(x: Float, y: Float, rect: RectF): ControlPoint {
        val points = mapOf(
            ControlPoint.TOP_LEFT to (rect.left to rect.top),
            ControlPoint.TOP_RIGHT to (rect.right to rect.top),
            ControlPoint.BOTTOM_LEFT to (rect.left to rect.bottom),
            ControlPoint.BOTTOM_RIGHT to (rect.right to rect.bottom)
        )

        for ((point, coords) in points) {
            val (px, py) = coords
            if (isPointNearControl(x, y, px, py)) {
                return point
            }
        }
        return ControlPoint.NONE
    }

    private fun isPointNearControl(x: Float, y: Float, cx: Float, cy: Float): Boolean {
        val touchRadius = controlPointTouchRadius / scaleFactor
        val dx = x - cx
        val dy = y - cy
        // 使用更宽松的触摸检测
        return (dx * dx + dy * dy) <= touchRadius * touchRadius
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        
        val x = event.x / scaleFactor
        val y = event.y / scaleFactor
        
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = x
                initialTouchY = y
                hasMovedBeyondSlop = false
                if (!isDrawMode) {
                    touchedRect = findTouchedRectangle(x, y)
                    draggedRect = touchedRect
                    draggedRect?.let { rect ->
                        activeControlPoint = findTouchedControlPoint(x, y, rect.rect)
                    }
                }
                if (isDrawMode || (draggedRect == null && activeControlPoint == ControlPoint.NONE)) {
                    currentRect = RectF(x, y, x, y)
                }
                lastTouchX = x
                lastTouchY = y
                invalidate()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // 第二个手指按下时，取消所有当前的操作状态
                currentRect = null
                draggedRect = null
                activeControlPoint = ControlPoint.NONE
                hoveredControlPoint = ControlPoint.NONE
                isScaling = true
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isScaling && event.pointerCount == 1) {
                    draggedRect?.let { coloredRect ->
                        if (activeControlPoint == ControlPoint.NONE) {
                            val newHoveredPoint = findTouchedControlPoint(x, y, coloredRect.rect)
                            if (hoveredControlPoint != newHoveredPoint) {
                                hoveredControlPoint = newHoveredPoint
                                // 开始动画
                                if (hoveredControlPoint != ControlPoint.NONE) {
                                    hoverAnimator.cancel()
                                    hoverAnimator.start()
                                } else {
                                    hoverAnimator.reverse()
                                }
                            }
                        }
                        if (activeControlPoint != ControlPoint.NONE) {
                            // 检查移动距离是否超过阈值
                            if (!hasMovedBeyondSlop) {
                                val dx = x - initialTouchX
                                val dy = y - initialTouchY
                                val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                                if (distance > TOUCH_SLOP) {
                                    hasMovedBeyondSlop = true
                                }
                            }
                            
                            // 只有在超过阈值后才进行缩放
                            if (hasMovedBeyondSlop) {
                                resizeRectWithinBounds(coloredRect.rect, x, y, activeControlPoint)
                            }
                        } else {
                            // 移动整个矩形不需要阈值判断
                            val dx = x - lastTouchX
                            val dy = y - lastTouchY
                            moveRectWithinBounds(coloredRect.rect, dx, dy)
                        }
                    } ?: currentRect?.let { rect ->
                        rect.right = x
                        rect.bottom = y
                    }
                    
                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                }
                // 如果在缩放，不处理移动逻辑
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // 当抬起一个手指时，保持缩放状态直到所有手指都离开
                if (event.pointerCount > 2) {  // 如果还有超过2个手指
                    isScaling = true
                }
            }
            MotionEvent.ACTION_UP -> {
                // 所有手指都离开了
                hasMovedBeyondSlop = false
                if (!isScaling && draggedRect == null && currentRect != null) {
                    normalizeRect(currentRect!!)
                    // 使用当前保存的画笔宽度创建新的矩形
                    rectangles.add(ColoredRect(currentRect!!, normalColor, currentStrokeWidth))
                    deletedRectangles.clear()  // 添加新矩形时清空已删除列表
                }
                currentRect = null
                touchedRect = null  // 清除触摸状态
                draggedRect = null
                activeControlPoint = ControlPoint.NONE
                if (hoveredControlPoint != ControlPoint.NONE) {
                    hoverAnimator.reverse()
                }
                hoveredControlPoint = ControlPoint.NONE
                isScaling = false  // 重置缩放状态
                invalidate()
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

    private fun resizeRectWithinBounds(rect: RectF, newX: Float, newY: Float, controlPoint: ControlPoint) {
        image?.let { img ->
            // 使用更高的精度来处理坐标，避免抖动
            val minSize = 20f * density  // 最小尺寸也跟随屏幕密度
            
            // 添加一个小的缓冲区，避免在边界处的抖动
            val buffer = 1f * density
            
            when (controlPoint) {
                ControlPoint.TOP_LEFT -> {
                    rect.left = newX.coerceIn(buffer, rect.right - minSize)
                    rect.top = newY.coerceIn(buffer, rect.bottom - minSize)
                }
                ControlPoint.TOP_RIGHT -> {
                    rect.right = newX.coerceIn(rect.left + minSize, img.width - buffer)
                    rect.top = newY.coerceIn(buffer, rect.bottom - minSize)
                }
                ControlPoint.BOTTOM_LEFT -> {
                    rect.left = newX.coerceIn(buffer, rect.right - minSize)
                    rect.bottom = newY.coerceIn(rect.top + minSize, img.height - buffer)
                }
                ControlPoint.BOTTOM_RIGHT -> {
                    rect.right = newX.coerceIn(rect.left + minSize, img.width - buffer)
                    rect.bottom = newY.coerceIn(rect.top + minSize, img.height - buffer)
                }
                ControlPoint.NONE -> {}
            }
        }
    }

    private fun moveRectWithinBounds(rect: RectF, dx: Float, dy: Float) {
        image?.let { img ->
            val newLeft = rect.left + dx
            val newTop = rect.top + dy
            val newRight = rect.right + dx
            val newBottom = rect.bottom + dy
            
            if (newLeft >= 0 && newRight <= img.width &&
                newTop >= 0 && newBottom <= img.height) {
                rect.offset(dx, dy)
            }
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var lastScaleFactor = 1f

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            lastScaleFactor = scaleFactor
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // 添加平滑处理，避免突然的缩放变化
            val targetScale = lastScaleFactor * detector.scaleFactor
            val smoothFactor = 0.5f  // 平滑系数
            scaleFactor = scaleFactor + (targetScale - scaleFactor) * smoothFactor
            scaleFactor = scaleFactor.coerceIn(0.1f, 5.0f)
            invalidate()
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
            lastScaleFactor = scaleFactor
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
