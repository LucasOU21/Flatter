package com.example.flatter.listingVista

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.flatter.R

class DeleteListingsAdapter(
    private val listings: List<DeleteListingModel>,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<DeleteListingsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivListingImage: ImageView = view.findViewById(R.id.ivListingImage)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val btnDelete: Button = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_delete_listing, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val listing = listings[position]
        holder.tvTitle.text = listing.title

        //load
        Glide.with(holder.ivListingImage.context)
            .load(listing.imageUrl)
            .placeholder(R.drawable.placeholder_img2)
            .into(holder.ivListingImage)
        holder.btnDelete.setOnClickListener {
            onDeleteClick(listing.id)
        }
    }

    override fun getItemCount() = listings.size
}