// app/src/main/java/com/example/flatter/ProfileFragment.kt
package com.example.flatter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.flatter.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

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

        // Cargar datos de perfil (en una implementación real, obtendríamos estos datos desde Firebase)
        cargarDatosPerfil()

        // Configurar listener para botón de guardar
        binding.btnSaveProfile.setOnClickListener {
            guardarPerfil()
        }

        // Configurar listener para cambiar foto
        binding.tvEditPhoto.setOnClickListener {
            // Aquí implementaríamos la lógica para seleccionar una foto de la galería
            // o tomar una foto con la cámara
            Toast.makeText(context, "Función no implementada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cargarDatosPerfil() {
        // En una implementación real, obtendríamos estos datos desde Firebase
        // Por ahora, usaremos datos de ejemplo
        binding.etFullName.setText("Ana García")
        binding.etEmail.setText("ana.garcia@ejemplo.com")
        binding.etPhone.setText("+34 612 345 678")
        binding.etBio.setText("Estudiante de arquitectura, 25 años. Me gusta el cine, la música y viajar.")
        binding.etMaxBudget.setText("800")
    }

    private fun guardarPerfil() {
        // Validar datos
        val nombre = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val telefono = binding.etPhone.text.toString().trim()
        val bio = binding.etBio.text.toString().trim()
        val presupuesto = binding.etMaxBudget.text.toString().trim()

        // Verificar que los campos obligatorios no estén vacíos
        if (nombre.isEmpty() || email.isEmpty()) {
            Toast.makeText(context, "Por favor, completa los campos obligatorios", Toast.LENGTH_SHORT).show()
            return
        }

        // En una implementación real, guardaríamos estos datos en Firebase
        // Por ahora, solo mostraremos un mensaje de éxito
        Toast.makeText(context, "Perfil guardado correctamente", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}