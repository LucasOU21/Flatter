package com.example.flatter.chatVista

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.bumptech.glide.Glide
import com.example.flatter.R
import com.example.flatter.databinding.DialogContactBinding
import com.example.flatter.homeVista.ListingModel
import com.example.flatter.utils.FlatterToast
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
            val message = binding.etMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
            } else {
                FlatterToast.showError(context, "Por favor, escribe un mensaje")
            }
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
            FlatterToast.showError(context, "Por favor, introduce un mensaje")
            return
        }

        dismiss() // Close dialog

        // Show loading message
        FlatterToast.showShort(context, "Enviando mensaje...")

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
                val success = chatService.sendMessage(chatId, messageText)

                if (success) {
                    // Show success message
                    FlatterToast.showSuccess(context, "Mensaje enviado con éxito")

                    // Open conversation immediately
                    val intent = Intent(context, ConversationActivity::class.java).apply {
                        putExtra("CHAT_ID", chatId)
                        putExtra("OTHER_USER_NAME", listing.userName)
                        putExtra("OTHER_USER_ID", listing.userId)
                        putExtra("OTHER_USER_PROFILE_PIC", listing.userProfileImageUrl)
                        putExtra("LISTING_ID", listing.id)
                        putExtra("LISTING_TITLE", listing.title)
                        putExtra("IS_NEW_CHAT", true)
                    }
                    context.startActivity(intent)

                    // Navigate to chats fragment
                    onMessageSent()
                } else {
                    FlatterToast.showError(context, "Error al enviar mensaje. Inténtalo de nuevo.")
                }
            } catch (e: Exception) {
                FlatterToast.showError(context, "Error al enviar mensaje: ${e.message}")
            }
        }
    }
}