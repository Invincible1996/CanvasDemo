package com.kevin.vision.canvas_demo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.graphics.Color

class CanvasActivity : AppCompatActivity() {
    private lateinit var imageCanvasView: ImageCanvasView
    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_canvas)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        imageCanvasView = findViewById(R.id.imageCanvasView)
        val undoButton = findViewById<MaterialButton>(R.id.undoButton)
        val redoButton = findViewById<MaterialButton>(R.id.redoButton)
        val toggleModeButton = findViewById<MaterialButton>(R.id.toggleModeButton)
        val colorPickerButton = findViewById<MaterialButton>(R.id.colorPickerButton)
        val saveButton = findViewById<MaterialButton>(R.id.saveButton)
        val brushSizeButton = findViewById<MaterialButton>(R.id.brushSizeButton)

        // Load sample image (you need to add an image to res/drawable)
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.sample_image)
        imageCanvasView.setImage(bitmap)

        undoButton.setOnClickListener {
            imageCanvasView.undoLastRectangle()
        }

        redoButton.setOnClickListener {
            imageCanvasView.redoLastRectangle()
        }

        toggleModeButton.setOnClickListener {
            imageCanvasView.toggleMode()
            toggleModeButton.setIconResource(
                if (imageCanvasView.isInDrawMode()) R.drawable.ic_edit else R.drawable.ic_move
            )
        }

        colorPickerButton.setOnClickListener {
            showColorPickerDialog()
        }

        brushSizeButton.setOnClickListener {
            showStrokeWidthDialog()
        }

        saveButton.setOnClickListener {
            if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
                // Android 9 (P) 及以下版本需要请求存储权限
                if (checkStoragePermission()) {
                    saveImage()
                } else {
                    requestStoragePermission()
                }
            } else {
                // Android 10 及以上版本不需要存储权限
                saveImage()
            }
        }

        // 设置初始模式图标
        toggleModeButton.setIconResource(
            if (imageCanvasView.isInDrawMode()) R.drawable.ic_edit else R.drawable.ic_move
        )

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.canvas)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun saveImage() {
        if (imageCanvasView.saveImageToGallery()) {
            Toast.makeText(this, "图片已保存到相册", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveImage()
            } else {
                Toast.makeText(this, "需要存储权限才能保存图片", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showColorPickerDialog() {
        val colors = arrayOf(
            Color.RED,
            Color.BLUE,
            Color.GREEN,
            Color.YELLOW,
            Color.CYAN,
            Color.MAGENTA
        )
        val colorNames = arrayOf("红色", "蓝色", "绿色", "黄色", "青色", "品红")
        val currentColor = imageCanvasView.getCurrentColor()

        // 找到最长的颜色名长度
        val maxNameLength = colorNames.maxOf { it.length }

        val items = Array(colors.size) { i ->
            val isSelected = colors[i] == currentColor
            val namePadding = " ".repeat(maxNameLength - colorNames[i].length + 2)  // 动态计算填充空格
            android.text.SpannableString("⬤ ${colorNames[i]}$namePadding${if (isSelected) "✓" else ""}").apply {
                // 颜色圆点样式
                setSpan(
                    android.text.style.ForegroundColorSpan(colors[i]),
                    0,
                    1,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    android.text.style.RelativeSizeSpan(1.5f),
                    0,
                    1,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                
                // 如果是当前选中的颜色，将勾选标记设置为蓝色
                if (isSelected) {
                    setSpan(
                        android.text.style.ForegroundColorSpan(Color.BLUE),
                        length - 1,
                        length,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("选择颜色")
            .setItems(items) { _, which ->
                imageCanvasView.setBothColors(colors[which], Color.BLUE)
            }
            .show()
    }

    private fun showStrokeWidthDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_stroke_width, null)
        val slider = view.findViewById<com.google.android.material.slider.Slider>(R.id.strokeWidthSlider)
        
        // 设置滑块的初始值为当前画笔宽度
        slider.value = imageCanvasView.getCurrentStrokeWidth()
        
        MaterialAlertDialogBuilder(this)
            .setTitle("设置画笔大小")
            .setView(view)
            .setPositiveButton("确定") { _, _ ->
                imageCanvasView.setStrokeWidth(slider.value)
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
