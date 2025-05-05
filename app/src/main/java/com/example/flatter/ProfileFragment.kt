// app/src/main/java/com/example/flatter/ProfileFragment.kt
package com.example.flatter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.example.flatter.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: UserViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializar ViewModel
        viewModel = ViewModelProvider(requireActivity())[UserViewModel::class.java]

        // Configurar listeners
        configurarListeners()

        // Observar datos del perfil
        observarDatos()

        // Cargar datos de perfil
        viewModel.cargarPerfilUsuario()
    }

    private fun configurarListeners() {
        // Botón de guardar cambios
        binding.btnSaveProfile.setOnClickListener {
            guardarPerfil()
        }

        // Botón de cambiar foto
        binding.tvEditPhoto.setOnClickListener {
            // Aquí implementaríamos la lógica para seleccionar una foto de la galería
            // o tomar una foto con la cámara
            Toast.makeText(context, "Función de cambiar foto no implementada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observarDatos() {
        viewModel.userProfile.observe(viewLifecycleOwner) { perfil ->
            mostrarDatosPerfil(perfil)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { estaCargando ->
            // Aquí podríamos mostrar un indicador de carga
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrEmpty()) {
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mostrarDatosPerfil(perfil: UserModel) {
        binding.etFullName.setText(perfil.fullName)
        binding.etEmail.setText(perfil.email)
        binding.etPhone.setText(perfil.phone)
        binding.etBio.setText(perfil.bio)
        binding.etMaxBudget.setText(perfil.maxBudget.toString())

        // Cargar imagen de perfil
        Glide.with(requireContext())
            .load(perfil.profileImageUrl)
            .placeholder(R.drawable.default_profile_img)
            .error(R.drawable.default_profile_img)
            .into(binding.ivProfilePicture)
    }

    private fun guardarPerfil() {
        // Validar datos
        val nombre = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val telefono = binding.etPhone.text.toString().trim()
        val bio = binding.etBio.text.toString().trim()
        val presupuestoStr = binding.etMaxBudget.text.toString().trim()

        // Verificar que los campos obligatorios no estén vacíos
        if (nombre.isEmpty() || email.isEmpty()) {
            Toast.makeText(context, "Por favor, completa los campos obligatorios", Toast.LENGTH_SHORT).show()
            return
        }

        // Convertir presupuesto a número
        val presupuesto = try {
            presupuestoStr.toDouble()
        } catch (e: NumberFormatException) {
            Toast.makeText(context, "Por favor, ingresa un presupuesto válido", Toast.LENGTH_SHORT).show()
            return
        }

        // Obtener perfil actual y actualizarlo
        val perfilActual = viewModel.userProfile.value ?: return
        val perfilActualizado = perfilActual.copy(
            fullName = nombre,
            email = email,
            phone = telefono,
            bio = bio,
            maxBudget = presupuesto
        )

        // Guardar perfil actualizado
        viewModel.actualizarPerfil(perfilActualizado)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}