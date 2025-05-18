package com.example.flatter.listingVista

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.example.flatter.R
import com.github.mikephil.charting.charts.BarChart

class ItemListingAnalyticsBinding(view: View) {
    val ivListingImage: ImageView = view.findViewById(R.id.ivListingImage)
    val tvTitle: TextView = view.findViewById(R.id.tvTitle)
    val tvActiveStatus: TextView = view.findViewById(R.id.tvActiveStatus)
    val barChart: BarChart = view.findViewById(R.id.barChart)
    val tvLikes: TextView = view.findViewById(R.id.tvLikes)
    val tvViews: TextView = view.findViewById(R.id.tvViews)
    val tvChats: TextView = view.findViewById(R.id.tvChats)
    val switchActive: SwitchCompat = view.findViewById(R.id.switchActive)
    val root: View = view

    companion object {
        fun inflate(inflater: LayoutInflater, parent: ViewGroup, attachToParent: Boolean): ItemListingAnalyticsBinding {
            val view = inflater.inflate(R.layout.item_listing_analytics, parent, attachToParent)
            return ItemListingAnalyticsBinding(view)
        }
    }
}