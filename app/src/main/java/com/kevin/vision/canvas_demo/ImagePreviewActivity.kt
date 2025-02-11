package com.kevin.vision.canvas_demo

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import android.widget.TextView

class ImagePreviewActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var imageCounterText: TextView
    private lateinit var adapter: ImagePreviewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)

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
    }

    private fun updateImageCounter(current: Int, total: Int) {
        imageCounterText.text = "${current + 1}/$total"
    }

    companion object {
        const val EXTRA_IMAGE_URIS = "extra_image_uris"
        const val EXTRA_START_POSITION = "extra_start_position"
    }
}