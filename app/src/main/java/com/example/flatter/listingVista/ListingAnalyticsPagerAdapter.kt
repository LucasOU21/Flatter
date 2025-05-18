package com.example.flatter.listingVista

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.flatter.R
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry

class ListingAnalyticsPagerAdapter(
    private val listings: List<ListingAnalyticsModel>,
    private val onStatusChange: (String, String) -> Unit
) : RecyclerView.Adapter<ListingAnalyticsPagerAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemListingAnalyticsBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemListingAnalyticsBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val listing = listings[position]
        with(holder.binding) {
            // Set basic info
            tvTitle.text = listing.title

            // Load image
            Glide.with(ivListingImage.context)
                .load(listing.imageUrl)
                .placeholder(R.drawable.placeholder_img2)
                .into(ivListingImage)

            // Set analytics values - now just the numbers
            tvLikes.text = listing.likes.toString()
            tvViews.text = listing.views.toString()
            tvChats.text = listing.chatCount.toString()

            // Set up bar chart
            setupBarChart(barChart, listing)

            // Set up active switch with better feedback
            val isActive = listing.status == "active"
            switchActive.isChecked = isActive
            tvActiveStatus.text = if (isActive) "Activo" else "Inactivo"

            // Change the text color based on active status
            if (isActive) {
                tvActiveStatus.setTextColor(ContextCompat.getColor(root.context, R.color.colorAccent))
            } else {
                tvActiveStatus.setTextColor(ContextCompat.getColor(root.context, R.color.gray_dark))
            }

            // Set toggle listener (use a null listener first to avoid triggering while setting up)
            switchActive.setOnCheckedChangeListener(null)
            switchActive.setOnCheckedChangeListener { _, isChecked ->
                val newStatus = if (isChecked) "active" else "inactive"
                tvActiveStatus.text = if (isChecked) "Activo" else "Inactivo"

                // Update text color on change
                if (isChecked) {
                    tvActiveStatus.setTextColor(ContextCompat.getColor(root.context, R.color.colorAccent))
                } else {
                    tvActiveStatus.setTextColor(ContextCompat.getColor(root.context, R.color.gray_dark))
                }

                onStatusChange(listing.id, newStatus)
            }
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
            barChart.context.getColor(R.color.colorReject),  // Red for likes
            barChart.context.getColor(R.color.colorPrimary), // Blue for views
            barChart.context.getColor(R.color.colorAccent)   // Accent for chats
        )

        val data = BarData(dataSet)
        data.barWidth = 0.9f

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