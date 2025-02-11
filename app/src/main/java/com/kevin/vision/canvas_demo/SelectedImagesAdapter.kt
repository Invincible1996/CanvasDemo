package com.kevin.vision.canvas_demo

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class SelectedImagesAdapter : RecyclerView.Adapter<SelectedImagesAdapter.ImageViewHolder>() {
    private val images = mutableListOf<Uri>()

    fun addImage(uri: Uri) {
        images.add(uri)
        notifyItemInserted(images.size - 1)
    }

    fun getImages(): List<Uri> = images.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_selected_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(images[position])
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, ImagePreviewActivity::class.java).apply {
                putParcelableArrayListExtra(ImagePreviewActivity.EXTRA_IMAGE_URIS, ArrayList(images))
                putExtra(ImagePreviewActivity.EXTRA_START_POSITION, position)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = images.size

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.selectedImageView)

        fun bind(uri: Uri) {
            imageView.setImageURI(uri)
        }
    }
}