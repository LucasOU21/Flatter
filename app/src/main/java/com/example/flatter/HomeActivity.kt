package com.example.flatter

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.flatter.databinding.ActivityHomeBinding
import com.example.flatter.databinding.ItemListingBinding
import com.lorentzos.flingswipe.SwipeFlingAdapterView

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var viewModel: HomeViewModel
    private lateinit var listingAdapter: ListingArrayAdapter
    private var listados = mutableListOf<ListingModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar ViewModel
        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        // Inicializar adaptador
        listingAdapter = ListingArrayAdapter(this, R.layout.item_listing, listados)

        // Configurar SwipeFlingAdapterView
        configurarSwipeView()

        // Configurar botones
        configurarBotones()

        // Observar listados
        observarListados()

        // Cargar listados iniciales
        viewModel.cargarListados()
    }

    private fun configurarSwipeView() {
        binding.swipeView.adapter = listingAdapter
        binding.swipeView.setFlingListener(object : SwipeFlingAdapterView.onFlingListener {
            override fun removeFirstObjectInAdapter() {
                if (listados.isNotEmpty()) {
                    listados.removeAt(0)
                    listingAdapter.notifyDataSetChanged()
                }
            }

            override fun onLeftCardExit(dataObject: Any) {
                // Rechazar listado
                val listado = dataObject as ListingModel
                viewModel.rechazarListado(listado.id)
            }

            override fun onRightCardExit(dataObject: Any) {
                // Aceptar listado
                val listado = dataObject as ListingModel
                viewModel.aceptarListado(listado.id)

                // Aquí podrías mostrar un mensaje de match o abrir chat
                Toast.makeText(
                    this@HomeActivity,
                    "¡Coincidencia! Ponte en contacto con ${listado.userName}",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onAdapterAboutToEmpty(itemsInAdapter: Int) {
                // Cargar más listados cuando quedan pocos
                if (itemsInAdapter < 5) {
                    viewModel.cargarMasListados()
                }
            }

            override fun onScroll(scrollProgressPercent: Float) {
                // Opcional: implementar efectos visuales durante el arrastre
                // Por ejemplo, cambiar opacidad de vistas de aceptar/rechazar
            }
        })
    }

    private fun configurarBotones() {
        // Botón rechazar (swipe izquierda)
        binding.btnReject.setOnClickListener {
            binding.swipeView.getTopCardListener()?.selectLeft()
        }

        // Botón aceptar (swipe derecha)
        binding.btnAccept.setOnClickListener {
            binding.swipeView.getTopCardListener()?.selectRight()
        }
    }

    private fun observarListados() {
        viewModel.listados.observe(this) { nuevosListados ->
            if (nuevosListados.isNotEmpty()) {
                // Actualizar la lista de listados
                listados.clear()
                listados.addAll(nuevosListados)
                listingAdapter.notifyDataSetChanged()
            }
        }

        viewModel.cargando.observe(this) { estaCargando ->
            // Aquí podrías mostrar un indicador de carga
        }

        viewModel.error.observe(this) { mensajeError ->
            if (!mensajeError.isNullOrEmpty()) {
                Toast.makeText(this, mensajeError, Toast.LENGTH_LONG).show()
            }
        }
    }
}