package com.example.flatter.listingVista

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.flatter.R
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry

class ListingAnalyticsAdapter(
    private val listings: List<ListingAnalyticsModel>,
    private val onStatusChange: (String, String) -> Unit
) : RecyclerView.Adapter<ListingAnalyticsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivListingImage: ImageView = view.findViewById(R.id.ivListingImage)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val barChart: BarChart = view.findViewById(R.id.barChart)
        val tvLikes: TextView = view.findViewById(R.id.tvLikes)
        val tvViews: TextView = view.findViewById(R.id.tvViews)
        val tvChats: TextView = view.findViewById(R.id.tvChats)
        val switchActive: SwitchCompat = view.findViewById(R.id.switchActive)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_listing_analytics, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val listing = listings[position]

        // Set basic info
        holder.tvTitle.text = listing.title

        // Load image
        Glide.with(holder.ivListingImage.context)
            .load(listing.imageUrl)
            .placeholder(R.drawable.placeholder_img2)
            .into(holder.ivListingImage)

        // Set analytics values
        holder.tvLikes.text = "Me gusta: ${listing.likes}"
        holder.tvViews.text = "Vistas: ${listing.views}"
        holder.tvChats.text = "Chats: ${listing.chatCount}"

        // Set up bar chart
        setupBarChart(holder.barChart, listing)

        // Set up active switch
        holder.switchActive.isChecked = listing.status == "active"
        holder.switchActive.setOnCheckedChangeListener { _, isChecked ->
            val newStatus = if (isChecked) "active" else "inactive"
            onStatusChange(listing.id, newStatus)
        }
    }

    private fun setupBarChart(barChart: BarChart, listing: ListingAnalyticsModel) {
        // Create bar entries
        val entries = ArrayList<BarEntry>()
        entries.add(BarEntry(0f, listing.likes.toFloat()))
        entries.add(BarEntry(1f, listing.views.toFloat()))
        entries.add(BarEntry(2f, listing.chatCount.toFloat()))

        val dataSet = BarDataSet(entries, "Estadísticas")
        dataSet.setColors(
            barChart.context.getColor(R.color.colorAccept),
            barChart.context.getColor(R.color.colorPrimary),
            barChart.context.getColor(R.color.colorAccent)
        )

        val data = BarData(dataSet)
        data.barWidth =.9f

        barChart.data = data
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.axisLeft.setDrawGridLines(false)
        barChart.axisRight.setDrawGridLines(false)
        barChart.xAxis.setDrawGridLines(false)

        // Fix the ValueFormatter implementation
        barChart.xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return when (value.toInt()) {
                    0 -> "Likes"
                    1 -> "Vistas"
                    2 -> "Chats"
                    else -> ""
                }
            }
        }

        barChart.animateY(1000)
        barChart.invalidate()
    }

    override fun getItemCount() = listings.size
}