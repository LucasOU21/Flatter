package com.example.flatter

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.flatter.databinding.ActivityHomeBinding
import com.lorentzos.flingswipe.SwipeFlingAdapterView

class HomeActivity : AppCompatActivity() {
    private val TAG = "FlattrDebug"
    private lateinit var binding: ActivityHomeBinding
    private lateinit var viewModel: HomeViewModel
    private lateinit var listingAdapter: ListingArrayAdapter
    private var listados = mutableListOf<ListingModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Starting onCreate")
        try {
            super.onCreate(savedInstanceState)
            Log.d(TAG, "After super.onCreate")

            binding = ActivityHomeBinding.inflate(layoutInflater)
            Log.d(TAG, "After binding initialization")

            setContentView(binding.root)
            Log.d(TAG, "After setContentView")

            // Inicializar ViewModel
            viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
            Log.d(TAG, "After viewModel initialization")

            // Inicializar adaptador
            listingAdapter = ListingArrayAdapter(this, R.layout.item_listing, listados)
            Log.d(TAG, "After adapter initialization")

            // Configurar SwipeFlingAdapterView
            Log.d(TAG, "Before configurarSwipeView")
            configurarSwipeView()
            Log.d(TAG, "After configurarSwipeView")

            // Configurar botones
            configurarBotones()
            Log.d(TAG, "After configurarBotones")

            // Observar listados
            observarListados()
            Log.d(TAG, "After observarListados")

            // Cargar listados iniciales
            viewModel.cargarListados()
            Log.d(TAG, "After cargarListados")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "Error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun configurarSwipeView() {
        try {
            // Correct ID reference to match XML
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
        } catch (e: Exception) {
            Log.e(TAG, "Error in configurarSwipeView", e)
        }
    }

    private fun configurarBotones() {
        try {
            // Botón rechazar (swipe izquierda)
            binding.btnReject.setOnClickListener {
                binding.swipeView.getTopCardListener()?.selectLeft()
            }

            // Botón aceptar (swipe derecha)
            binding.btnAccept.setOnClickListener {
                binding.swipeView.getTopCardListener()?.selectRight()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in configurarBotones", e)
        }
    }

    private fun observarListados() {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error in observarListados", e)
        }
    }
}