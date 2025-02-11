package com.kevin.vision.canvas_demo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar

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
        val undoButton = findViewById<Button>(R.id.undoButton)
        val redoButton = findViewById<Button>(R.id.redoButton)
        val toggleModeButton = findViewById<Button>(R.id.toggleModeButton)
        val saveButton = findViewById<Button>(R.id.saveButton)

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
            toggleModeButton.text =
                if (imageCanvasView.isInDrawMode()) "切换到移动" else "切换到绘制"
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

        // 设置初始模式文字
        toggleModeButton.text = if (imageCanvasView.isInDrawMode()) "切换到移动" else "切换到绘制"

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
}
