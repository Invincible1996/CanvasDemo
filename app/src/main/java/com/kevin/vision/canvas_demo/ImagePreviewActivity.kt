package com.kevin.vision.canvas_demo

import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import android.widget.TextView
import com.google.android.material.appbar.MaterialToolbar

class ImagePreviewActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var imageCounterText: TextView
    private lateinit var adapter: ImagePreviewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_image_preview)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewPager = findViewById(R.id.imageViewPager)
        imageCounterText = findViewById(R.id.imageCounterText)
        adapter = ImagePreviewAdapter()
        viewPager.adapter = adapter

        // 获取传递过来的图片URI列表和起始位置
        val imageUris = intent.getParcelableArrayListExtra<Uri>(EXTRA_IMAGE_URIS) ?: arrayListOf()
        val startPosition = intent.getIntExtra(EXTRA_START_POSITION, 0)

        adapter.setImages(imageUris)
        viewPager.setCurrentItem(startPosition, false)
        updateImageCounter(startPosition, imageUris.size)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateImageCounter(position, imageUris.size)
            }
        })

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
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

    private fun updateImageCounter(current: Int, total: Int) {
        imageCounterText.text = "${current + 1}/$total"
    }

    companion object {
        const val EXTRA_IMAGE_URIS = "extra_image_uris"
        const val EXTRA_START_POSITION = "extra_start_position"
    }
}