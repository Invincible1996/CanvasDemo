package com.kevin.vision.canvas_demo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.MenuItem
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import java.io.File
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.IOException

class ImageSelectionActivity : AppCompatActivity() {
    private lateinit var adapter: SelectedImagesAdapter
    private var currentPhotoUri: Uri? = null
    private val client = OkHttpClient()

    private val pickMultipleImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { uri ->
            adapter.addImage(uri)
        }
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            currentPhotoUri?.let { uri ->
                adapter.addImage(uri)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        val shouldShowRationale = permissions.any { permission ->
            shouldShowRequestPermissionRationale(permission.key)
        }

        when {
            allGranted -> {
                // 所有权限都获取到了，继续操作
                openCamera()
            }
            shouldShowRationale -> {
                // 用户拒绝了权限，但没有勾选"不再询问"，显示解释对话框
                showPermissionExplanationDialog()
            }
            else -> {
                // 用户拒绝了权限，并且勾选了"不再询问"，引导用户去设置页面开启权限
                showSettingsDialog()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_image_selection)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        setupButtons()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupRecyclerView() {
        adapter = SelectedImagesAdapter()
        val recyclerView = findViewById<RecyclerView>(R.id.selectedImagesRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.selectFromGalleryButton).setOnClickListener {
            pickMultipleImages.launch("image/*")
        }

        findViewById<Button>(R.id.takePhotoButton).setOnClickListener {
            checkCameraPermissionAndOpen()
        }

        findViewById<Button>(R.id.uploadButton).setOnClickListener {
            uploadImages()
        }
    }

    private fun checkCameraPermissionAndOpen() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        // 检查是否需要显示读写权限
        val permissionsToRequest = if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
            permissions
        } else {
            arrayOf(Manifest.permission.CAMERA)
        }

        when {
            permissionsToRequest.all { permission ->
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            } -> {
                // 已有所有权限，直接打开相机
                openCamera()
            }
            permissionsToRequest.any { permission ->
                shouldShowRequestPermissionRationale(permission)
            } -> {
                // 至少有一个权限被拒绝过，显示解释
                showPermissionExplanationDialog()
            }
            else -> {
                // 首次请求权限
                requestPermissionLauncher.launch(permissionsToRequest)
            }
        }
    }

    private fun openCamera() {
        val photoFile = File(externalCacheDir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.provider",
            photoFile
        )
        currentPhotoUri = uri
        takePhoto.launch(uri)
    }

    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要相机和存储权限")
            .setMessage("我们需要相机权限来拍照，存储权限来保存照片。这些权限仅用于图片上传功能。")
            .setPositiveButton("重试") { _, _ ->
                checkCameraPermissionAndOpen()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要权限")
            .setMessage("相机和存储权限对于拍照功能是必需的。请在设置中开启这些权限。")
            .setPositiveButton("去设置") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(this)
        }
    }

    private fun uploadImages() {
        val images = adapter.getImages()
        if (images.isEmpty()) {
            Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show()
            return
        }

        // 创建MultipartBody.Builder来构建请求体
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        
        // 添加所有图片到请求体
        images.forEachIndexed { index, uri ->
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val file = File(cacheDir, "image_$index")
                file.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                
                val requestBody = file.asRequestBody("image/*".toMediaTypeOrNull())
                builder.addFormDataPart("images", "image_$index.jpg", requestBody)
            }
        }

        // 创建请求
        val request = Request.Builder()
            .url("YOUR_UPLOAD_URL") // 替换为实际的上传URL
            .post(builder.build())
            .build()

        // 发送请求
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@ImageSelectionActivity, "上传失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(this@ImageSelectionActivity, "上传成功", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@ImageSelectionActivity, "上传失败: ${response.code}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
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
}