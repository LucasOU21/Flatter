// app/src/main/java/com/example/flatter/HomeFragment.kt
package com.example.flatter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.flatter.databinding.FragmentHomeBinding
import java.text.NumberFormat
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: HomeViewModel
    private lateinit var listingImageAdapter: ListingImageAdapter
    private var currentListingIndex = 0
    private var listings = mutableListOf<ListingModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializar ViewModel
        viewModel = ViewModelProvider(requireActivity())[HomeViewModel::class.java]

        // Configurar botones
        configurarBotones()

        // Observar listados
        observarListados()

        // Cargar listados iniciales
        viewModel.cargarListados()
    }

    private fun configurarBotones() {
        // Botón rechazar
        binding.btnReject.setOnClickListener {
            if (listings.isNotEmpty() && currentListingIndex < listings.size) {
                val currentListing = listings[currentListingIndex]
                viewModel.rechazarListado(currentListing.id)

                // Mostrar siguiente listado
                mostrarSiguienteListado()
            }
        }

        // Botón aceptar
        binding.btnAccept.setOnClickListener {
            if (listings.isNotEmpty() && currentListingIndex < listings.size) {
                val currentListing = listings[currentListingIndex]
                viewModel.aceptarListado(currentListing.id)

                // Mostrar mensaje de coincidencia
                Toast.makeText(
                    requireContext(),
                    "¡Coincidencia! Ponte en contacto con ${currentListing.userName}",
                    Toast.LENGTH_SHORT
                ).show()

                // Mostrar siguiente listado
                mostrarSiguienteListado()
            }
        }
    }

    private fun observarListados() {
        viewModel.listados.observe(viewLifecycleOwner) { nuevosListados ->
            if (nuevosListados.isNotEmpty()) {
                // Actualizar la lista de listados
                listings.clear()
                listings.addAll(nuevosListados)
                currentListingIndex = 0

                // Mostrar el primer listado
                mostrarListadoActual()
            } else {
                // No hay listados disponibles
                mostrarMensajeNoListados()
            }
        }

        viewModel.cargando.observe(viewLifecycleOwner) { estaCargando ->
            binding.progressBar.visibility = if (estaCargando) View.VISIBLE else View.GONE
            if (estaCargando) {
                binding.cardListing.visibility = View.INVISIBLE
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { mensajeError ->
            if (!mensajeError.isNullOrEmpty()) {
                Toast.makeText(requireContext(), mensajeError, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun mostrarListadoActual() {
        if (listings.isEmpty() || currentListingIndex >= listings.size) {
            mostrarMensajeNoListados()
            return
        }

        binding.cardListing.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE

        val listing = listings[currentListingIndex]

        // Configurar textos del listado
        binding.tvTitle.text = listing.title
        binding.tvLocation.text = listing.location
        binding.tvDescription.text = listing.description
        binding.tvUserName.text = requireContext().getString(R.string.publicado_por, listing.userName)
        binding.tvPublishedDate.text = requireContext().getString(R.string.publicado_fecha, listing.publishedDate)

        // Formatear precio con moneda
        val formattedPrice = NumberFormat.getCurrencyInstance(Locale("es", "ES"))
            .format(listing.price)
        binding.tvPrice.text = requireContext().getString(R.string.precio_por_mes, formattedPrice)

        // Configurar detalles
        binding.tvBedrooms.text = requireContext().resources.getQuantityString(
            R.plurals.bedroom_count,
            listing.bedrooms,
            listing.bedrooms
        )
        binding.tvBathrooms.text = requireContext().resources.getQuantityString(
            R.plurals.bathroom_count,
            listing.bathrooms,
            listing.bathrooms
        )
        binding.tvArea.text = requireContext().getString(R.string.area_metros, listing.area)

        // Cargar imagen de perfil del usuario
        Glide.with(requireContext())
            .load(listing.userProfileImageUrl)
            .placeholder(R.drawable.default_profile_img)
            .error(R.drawable.default_profile_img)
            .into(binding.ivUserProfile)

        // Configurar carrusel de imágenes
        configurarCarruselImagenes(listing.imageUrls)
    }

    private fun configurarCarruselImagenes(imageUrls: List<String>) {
        // Configurar adaptador de imágenes
        listingImageAdapter = ListingImageAdapter(imageUrls)
        binding.viewPagerImages.adapter = listingImageAdapter

        // Configurar indicador de puntos
        configurarIndicadorPuntos(imageUrls.size)

        // Manejar cambios de página
        binding.viewPagerImages.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                actualizarIndicadorPuntos(position)
            }
        })
    }

    private fun configurarIndicadorPuntos(count: Int) {
        binding.dotsIndicator.removeAllViews()

        // Crear puntos indicadores
        val dots = Array(count) { android.widget.ImageView(requireContext()) }

        for (i in dots.indices) {
            dots[i] = android.widget.ImageView(requireContext())
            dots[i].setImageResource(
                if (i == 0) R.drawable.radio_button_checked_24
                else R.drawable.radio_button_unchecked_24
            )

            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(8, 0, 8, 0)
            binding.dotsIndicator.addView(dots[i], params)
        }
    }

    private fun actualizarIndicadorPuntos(position: Int) {
        val count = binding.dotsIndicator.childCount

        for (i in 0 until count) {
            val dot = binding.dotsIndicator.getChildAt(i) as android.widget.ImageView
            dot.setImageResource(
                if (i == position) R.drawable.radio_button_checked_24
                else R.drawable.radio_button_unchecked_24
            )
        }
    }

    private fun mostrarSiguienteListado() {
        currentListingIndex++

        // Comprobar si necesitamos cargar más listados
        if (currentListingIndex >= listings.size - 2) {
            viewModel.cargarMasListados()
        }

        if (currentListingIndex < listings.size) {
            mostrarListadoActual()
        } else {
            mostrarMensajeNoListados()
        }
    }

    private fun mostrarMensajeNoListados() {
        binding.cardListing.visibility = View.INVISIBLE
        binding.progressBar.visibility = View.GONE
        Toast.makeText(requireContext(), getString(R.string.no_listings_available), Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}