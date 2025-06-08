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

        //remove default dialog title
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        //use the automatically generated binding class
        binding = DialogContactBinding.inflate(LayoutInflater.from(context))
        setContentView(binding.root)

        //set dialog width to match parent
        window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        //setup UI with listing data
        setupUI()

        //setup listeners
        setupListeners()
    }

    private fun setupUI() {
        //set listing title
        binding.tvListingTitle.text = listing.title

        //format and set price
        val formattedPrice = NumberFormat.getCurrencyInstance(Locale("es", "ES"))
            .format(listing.price)
        binding.tvListingPrice.text = context.getString(R.string.precio_por_mes, formattedPrice)

        //set owner name
        binding.tvOwnerName.text = listing.userName

        //load owner profile image
        Glide.with(context)
            .load(listing.userProfileImageUrl)
            .placeholder(R.drawable.default_profile_img)
            .error(R.drawable.default_profile_img)
            .into(binding.ivOwnerProfile)
    }

    private fun setupListeners() {
        //send button click
        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
            } else {
                FlatterToast.showError(context, "Por favor, escribe un mensaje")
            }
        }

        //quick message
        binding.tvQuickMessage1.setOnClickListener {
            sendMessage(binding.tvQuickMessage1.text.toString())
        }


        binding.tvQuickMessage2.setOnClickListener {
            sendMessage(binding.tvQuickMessage2.text.toString())
        }


        binding.tvQuickMessage3.setOnClickListener {
            sendMessage(binding.tvQuickMessage3.text.toString())
        }
    }

    private fun sendMessage(messageText: String) {
        if (messageText.isBlank()) {
            FlatterToast.showError(context, "Por favor, introduce un mensaje")
            return
        }

        dismiss() //dialog close


        FlatterToast.showShort(context, "Enviando mensaje...")

        //start chat with listing owner
        lifecycleScope.launch {
            try {
                //create or get chat with listing information
                val chatId = chatService.getOrCreateChat(
                    otherUserId = listing.userId,
                    listingId = listing.id,
                    listingTitle = listing.title
                )

                //send the message
                val success = chatService.sendMessage(chatId, messageText)

                if (success) {
                    //show success message
                    FlatterToast.showSuccess(context, "Mensaje enviado con éxito")

                    //open conversation immediately
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

                    //navigate to chats fragment
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