package com.example.flatter.listingVista

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.flatter.databinding.ItemCreateListingImageBinding

class CreateListingImageAdapter(
    private val images: List<Uri>,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<CreateListingImageAdapter.ImageViewHolder>() {

    class ImageViewHolder(val binding: ItemCreateListingImageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemCreateListingImageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val image = images[position]
        holder.binding.imageView.setImageURI(image)

        holder.binding.btnDelete.setOnClickListener {
            onDeleteClick(position)
        }
    }

    override fun getItemCount(): Int = images.size
}