package com.example.flatter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.flatter.databinding.ItemListingBinding
import java.text.NumberFormat
import java.util.Locale

class ListingArrayAdapter(
    context: Context,
    resource: Int,
    private val listings: List<ListingModel>
) : ArrayAdapter<ListingModel>(context, resource, listings) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val listing = getItem(position) ?: return View(context)

        // Usar ViewBinding para mejorar el rendimiento y evitar findViewById
        val binding: ItemListingBinding
        val view: View

        if (convertView == null) {
            binding = ItemListingBinding.inflate(LayoutInflater.from(context), parent, false)
            view = binding.root
            view.tag = binding
        } else {
            view = convertView
            binding = view.tag as ItemListingBinding
        }

        // Configurar textos del listado
        binding.tvTitle.text = listing.title
        binding.tvLocation.text = listing.location
        binding.tvDescription.text = listing.description
        binding.tvUserName.text = context.getString(R.string.publicado_por, listing.userName)
        binding.tvPublishedDate.text = context.getString(R.string.publicado_fecha, listing.publishedDate)

        // Formatear precio con moneda
        val formattedPrice = NumberFormat.getCurrencyInstance(Locale("es", "ES"))
            .format(listing.price)
        binding.tvPrice.text = context.getString(R.string.precio_por_mes, formattedPrice)

        // Configurar detalles
        binding.tvBedrooms.text = context.resources.getQuantityString(
            R.plurals.bedroom_count,
            listing.bedrooms,
            listing.bedrooms
        )
        binding.tvBathrooms.text = context.resources.getQuantityString(
            R.plurals.bathroom_count,
            listing.bathrooms,
            listing.bathrooms
        )
        binding.tvArea.text = context.getString(R.string.area_metros, listing.area)

        // Cargar imagen de perfil del usuario
        Glide.with(context)
            .load(listing.userProfileImageUrl)
            .placeholder(R.drawable.default_profile_img)
            .error(R.drawable.default_profile_img)
            .into(binding.ivUserProfile)

        // Configurar carrusel de imágenes
        setupImageCarousel(listing.imageUrls, binding)

        return view
    }

    private fun setupImageCarousel(imageUrls: List<String>, binding: ItemListingBinding) {
        // Configurar adaptador de imágenes
        val imageAdapter = ListingImageAdapter(imageUrls)
        binding.viewPagerImages.adapter = imageAdapter

        // Configurar indicador de puntos
        setupDotsIndicator(imageUrls.size, binding)

        // Manejar cambios de página
        binding.viewPagerImages.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateDotsIndicator(position, binding)
            }
        })
    }

    private fun setupDotsIndicator(count: Int, binding: ItemListingBinding) {
        binding.dotsIndicator.removeAllViews()

        // Crear puntos indicadores
        val dots = Array(count) { ImageView(context) }

        for (i in dots.indices) {
            dots[i] = ImageView(context)
            dots[i].setImageResource(
                if (i == 0) R.drawable.radio_button_unchecked_24
                else R.drawable.radio_button_unchecked_24
            )

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(8, 0, 8, 0)
            binding.dotsIndicator.addView(dots[i], params)
        }
    }

    private fun updateDotsIndicator(position: Int, binding: ItemListingBinding) {
        val count = binding.dotsIndicator.childCount

        for (i in 0 until count) {
            val dot = binding.dotsIndicator.getChildAt(i) as ImageView
            dot.setImageResource(
                if (i == position) R.drawable.radio_button_checked_24
                else R.drawable.radio_button_checked_24
            )
        }
    }
}