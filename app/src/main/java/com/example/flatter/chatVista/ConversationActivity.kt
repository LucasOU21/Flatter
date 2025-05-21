package com.example.flatter.chatVista

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.flatter.R
import com.example.flatter.databinding.ActivityConversationBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ConversationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConversationBinding
    private lateinit var chatService: ChatService
    private lateinit var messageAdapter: MessageAdapter

    private var chatId: String = ""
    private var otherUserName: String = ""
    private var otherUserId: String = ""
    private var otherUserProfilePic: String = ""
    private var listingId: String = ""
    private var listingTitle: String = ""
    private var isNewChat: Boolean = false
    private var isListingOwner: Boolean = false
    private var chatStatus: String = "pending"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize chat service
        chatService = ChatService()

        // Get chat details from intent
        chatId = intent.getStringExtra("CHAT_ID") ?: ""
        otherUserName = intent.getStringExtra("OTHER_USER_NAME") ?: "Usuario"
        otherUserId = intent.getStringExtra("OTHER_USER_ID") ?: ""
        otherUserProfilePic = intent.getStringExtra("OTHER_USER_PROFILE_PIC") ?: ""
        listingId = intent.getStringExtra("LISTING_ID") ?: ""
        listingTitle = intent.getStringExtra("LISTING_TITLE") ?: "Anuncio"
        isNewChat = intent.getBooleanExtra("IS_NEW_CHAT", false)

        // Check if we have valid data
        if (chatId.isEmpty() || otherUserId.isEmpty()) {
            Toast.makeText(this, "Error: Chat no válido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Determine if the current user is the owner of the listing
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        isListingOwner = currentUserId != otherUserId

        // Set up toolbar with user name and listing title
        setupToolbar()

        // Set up RecyclerView
        setupRecyclerView()

        // Subscribe to chat status changes
        subscribeToChatStatus()

        // Subscribe to message updates
        chatService.getChatMessagesFlow(chatId).onEach { messages ->
            messageAdapter.submitList(messages)

            // Scroll to bottom if there are messages
            if (messages.isNotEmpty()) {
                binding.rvMessages.scrollToPosition(messages.size - 1)
            }

            // Mark messages as read
            lifecycleScope.launch {
                chatService.markMessagesAsRead(chatId)
            }
        }.launchIn(lifecycleScope)

        // Set up send button
        binding.btnSend.setOnClickListener {
            sendMessage()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // Hide default title
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Set user name and listing title in our custom layout
        binding.tvUserName.text = otherUserName

        // Show listing title if available
        if (listingTitle.isNotEmpty() && listingTitle != "Anuncio") {
            binding.tvListingTitle.text = listingTitle
            binding.tvListingTitle.visibility = View.VISIBLE
        } else {
            binding.tvListingTitle.visibility = View.GONE
        }

        // Load other user's profile pic in toolbar
        Glide.with(this)
            .load(otherUserProfilePic)
            .placeholder(R.drawable.default_profile_img)
            .error(R.drawable.default_profile_img)
            .circleCrop()
            .into(binding.ivProfilePic)
    }

    private fun setupRecyclerView() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        messageAdapter = MessageAdapter(currentUserId)

        binding.rvMessages.apply {
            layoutManager = LinearLayoutManager(this@ConversationActivity).apply {
                stackFromEnd = true
            }
            adapter = messageAdapter
        }
    }

    private fun subscribeToChatStatus() {
        chatService.getChatStatusFlow(chatId).onEach { status ->
            chatStatus = status

            // Update UI based on status
            when (status) {
                "pending" -> {
                    if (isNewChat) {
                        showChatActionBanner()
                    }
                }
                "accepted" -> {
                    // Hide any banner and enable normal chat
                    binding.chatActionBanner.root.visibility = View.GONE
                    binding.layoutInput.visibility = View.VISIBLE
                }
                "declined", "cancelled" -> {
                    // Show appropriate message and close
                    val message = if (status == "declined") {
                        "Chat rechazado por el propietario"
                    } else {
                        "Chat cancelado por el usuario"
                    }
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }.launchIn(lifecycleScope)
    }

    private fun showChatActionBanner() {
        // Show action banner based on whether user is owner or requester
        val actionBanner = binding.chatActionBanner.root
        actionBanner.visibility = View.VISIBLE

        // Get references to the banner views
        val tvBannerMessage = binding.chatActionBanner.tvBannerMessage
        val btnAction1 = binding.chatActionBanner.btnBannerAction1
        val btnAction2 = binding.chatActionBanner.btnBannerAction2

        if (isListingOwner) {
            // Owner sees info about the requester with accept/decline options
            tvBannerMessage.text = "A $otherUserName le interesa tu anuncio"
            btnAction1.text = "Aceptar"
            btnAction2.text = "Rechazar"

            btnAction1.setOnClickListener {
                lifecycleScope.launch {
                    // Accept chat request
                    chatService.updateChatStatus(chatId, "accepted")
                    actionBanner.visibility = View.GONE
                    binding.layoutInput.visibility = View.VISIBLE
                }
            }

            btnAction2.setOnClickListener {
                showDeclineConfirmation()
            }
        } else {
            // Requester sees chat info with continue/cancel options
            tvBannerMessage.text = "Tu solicitud ha sido enviada a $otherUserName"
            btnAction1.text = "Continuar"
            btnAction2.text = "Cancelar"

            btnAction1.setOnClickListener {
                // Continue with chat (just hide the banner)
                actionBanner.visibility = View.GONE
            }

            btnAction2.setOnClickListener {
                showCancelConfirmation()
            }
        }
    }

    private fun showDeclineConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Rechazar chat")
            .setMessage("¿Estás seguro que quieres rechazar este chat? Esta acción no se puede deshacer.")
            .setPositiveButton("Rechazar") { _, _ ->
                lifecycleScope.launch {
                    chatService.updateChatStatus(chatId, "declined")
                    finish()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showCancelConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Cancelar chat")
            .setMessage("¿Estás seguro que quieres cancelar este chat? Esta acción no se puede deshacer.")
            .setPositiveButton("Cancelar chat") { _, _ ->
                lifecycleScope.launch {
                    chatService.updateChatStatus(chatId, "cancelled")
                    finish()
                }
            }
            .setNegativeButton("Volver", null)
            .show()
    }

    private fun sendMessage() {
        // Only allow sending messages if chat is accepted or we're the owner
        if (chatStatus != "accepted" && !isListingOwner) {
            Toast.makeText(this, "El chat aún no ha sido aceptado", Toast.LENGTH_SHORT).show()
            return
        }

        val messageText = binding.etMessage.text.toString().trim()

        if (messageText.isEmpty()) {
            return
        }

        // Clear input field
        binding.etMessage.setText("")

        // Show loading state
        binding.progressBar.visibility = View.VISIBLE

        // Send message
        lifecycleScope.launch {
            val success = chatService.sendMessage(chatId, messageText)

            // If this is the first message and we're the listing owner, automatically accept the chat
            if (success && isListingOwner && chatStatus == "pending") {
                chatService.updateChatStatus(chatId, "accepted")
            }

            binding.progressBar.visibility = View.GONE

            if (!success) {
                Toast.makeText(
                    this@ConversationActivity,
                    "Error al enviar mensaje",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}