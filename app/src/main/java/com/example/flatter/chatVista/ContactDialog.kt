package com.example.flatter.chatVista

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.bumptech.glide.Glide
import com.example.flatter.R
import com.example.flatter.databinding.DialogContactBinding
import com.example.flatter.homeVista.ListingModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class ContactDialog(
    context: Context,
    private val listing: ListingModel,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onMessageSent: () -> Unit
) : Dialog(context) {

    private lateinit var binding: DialogContactBinding
    private val chatService = ChatService()
    private val chatManager = ChatManager(context, lifecycleScope)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Remove default dialog title
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        // Use the automatically generated binding class
        binding = DialogContactBinding.inflate(LayoutInflater.from(context))
        setContentView(binding.root)

        // Set dialog width to match parent
        window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // Setup UI with listing data
        setupUI()

        // Setup listeners
        setupListeners()
    }

    private fun setupUI() {
        // Set listing title
        binding.tvListingTitle.text = listing.title

        // Format and set price
        val formattedPrice = NumberFormat.getCurrencyInstance(Locale("es", "ES"))
            .format(listing.price)
        binding.tvListingPrice.text = context.getString(R.string.precio_por_mes, formattedPrice)

        // Set owner name
        binding.tvOwnerName.text = listing.userName

        // Load owner profile image
        Glide.with(context)
            .load(listing.userProfileImageUrl)
            .placeholder(R.drawable.default_profile_img)
            .error(R.drawable.default_profile_img)
            .into(binding.ivOwnerProfile)
    }

    private fun setupListeners() {
        // Send button click
        binding.btnSend.setOnClickListener {
            sendMessage(binding.etMessage.text.toString())
        }

        // Quick message 1
        binding.tvQuickMessage1.setOnClickListener {
            sendMessage(binding.tvQuickMessage1.text.toString())
        }

        // Quick message 2
        binding.tvQuickMessage2.setOnClickListener {
            sendMessage(binding.tvQuickMessage2.text.toString())
        }

        // Quick message 3
        binding.tvQuickMessage3.setOnClickListener {
            sendMessage(binding.tvQuickMessage3.text.toString())
        }
    }

    private fun sendMessage(messageText: String) {
        if (messageText.isBlank()) {
            Toast.makeText(context, "Por favor, introduce un mensaje", Toast.LENGTH_SHORT).show()
            return
        }

        dismiss() // Close dialog

        // Start chat with listing owner
        lifecycleScope.launch {
            try {
                // Create or get chat with listing information
                val chatId = chatService.getOrCreateChat(
                    otherUserId = listing.userId,
                    listingId = listing.id,
                    listingTitle = listing.title
                )

                // Send the message
                chatService.sendMessage(chatId, messageText)

                // Navigate to chats fragment
                onMessageSent()

                // Show success message
                Toast.makeText(context, "Mensaje enviado con éxito", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error al enviar mensaje: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}