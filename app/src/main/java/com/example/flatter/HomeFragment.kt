// app/src/main/java/com/example/flatter/HomeFragment.kt
package com.example.flatter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.flatter.databinding.FragmentHomeBinding
import com.lorentzos.flingswipe.SwipeFlingAdapterView

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: HomeViewModel
    private lateinit var listingAdapter: ListingArrayAdapter
    private var listados = mutableListOf<ListingModel>()

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

        // Inicializar adaptador
        listingAdapter = ListingArrayAdapter(requireContext(), R.layout.item_listing, listados)

        // Configurar SwipeFlingAdapterView
        configurarSwipeView()

        // Configurar botones
        configurarBotones()

        // Observar listados
        observarListados()

        // Cargar listados iniciales si no hay datos
        if (listados.isEmpty()) {
            viewModel.cargarListados()
        }
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
                    requireContext(),
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
        viewModel.listados.observe(viewLifecycleOwner) { nuevosListados ->
            if (nuevosListados.isNotEmpty()) {
                // Actualizar la lista de listados
                listados.clear()
                listados.addAll(nuevosListados)
                listingAdapter.notifyDataSetChanged()
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { mensajeError ->
            if (!mensajeError.isNullOrEmpty()) {
                Toast.makeText(requireContext(), mensajeError, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}