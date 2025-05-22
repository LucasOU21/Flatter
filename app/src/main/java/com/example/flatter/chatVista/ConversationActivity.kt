package com.example.flatter.chatVista

import android.os.Bundle
import android.util.Log
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
import com.example.flatter.utils.FlatterToast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
            FlatterToast.showError(this, "Error: Chat no válido")
            finish()
            return
        }

        // Get the listing owner ID to determine if the current user is the listing owner
        lifecycleScope.launch {
            try {
                if (listingId.isNotEmpty()) {
                    val listingDoc = FirebaseFirestore.getInstance()
                        .collection("listings")
                        .document(listingId)
                        .get()
                        .await()

                    val listingOwnerId = listingDoc.getString("userId") ?: ""
                    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

                    // Set isListingOwner based on whether the current user is the listing owner
                    isListingOwner = (currentUserId == listingOwnerId)

                    Log.d("ConversationActivity", "Current user: $currentUserId, Listing owner: $listingOwnerId, isListingOwner: $isListingOwner")

                    // Update UI based on owner status
                    setupUiBasedOnRole()
                } else {
                    isListingOwner = false
                    setupUiBasedOnRole()
                }
            } catch (e: Exception) {
                Log.e("ConversationActivity", "Error determining listing owner: ${e.message}")
                isListingOwner = false
                setupUiBasedOnRole()
            }
        }

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

    private fun setupUiBasedOnRole() {
        Log.d("ConversationActivity", "Setting up UI - Status: $chatStatus, isListingOwner: $isListingOwner")

        when (chatStatus) {
            "pending" -> {
                // For pending chats, show input for both users but with different behavior
                binding.layoutInput.visibility = View.VISIBLE

                if (isListingOwner) {
                    // Listing owner can respond to accept the chat implicitly
                    // No need to hide input, they can respond which will accept the chat
                    Log.d("ConversationActivity", "Listing owner can respond to pending chat")
                } else {
                    // Chat requester can also send messages while pending
                    Log.d("ConversationActivity", "Chat requester can send while pending")
                }
            }
            "accepted" -> {
                // For accepted chats, show input for both users
                binding.layoutInput.visibility = View.VISIBLE
                Log.d("ConversationActivity", "Chat accepted, input visible for both users")
            }
            "declined", "cancelled" -> {
                // For declined or cancelled chats, hide input
                binding.layoutInput.visibility = View.GONE
                Log.d("ConversationActivity", "Chat declined/cancelled, input hidden")
            }
            else -> {
                // Default case, show input
                binding.layoutInput.visibility = View.VISIBLE
                Log.d("ConversationActivity", "Default case, input visible")
            }
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
            Log.d("ConversationActivity", "Chat status updated to: $status, isListingOwner: $isListingOwner")

            // Update UI based on status
            when (status) {
                "pending" -> {
                    if (isNewChat && isListingOwner) {
                        showChatActionBanner()
                    }
                    setupUiBasedOnRole()
                }
                "accepted" -> {
                    // Hide any banner and enable normal chat
                    binding.chatActionBanner.root.visibility = View.GONE
                    binding.layoutInput.visibility = View.VISIBLE
                    setupUiBasedOnRole()
                }
                "declined", "cancelled" -> {
                    // Show appropriate message and close
                    val message = if (status == "declined") {
                        "Chat rechazado por el propietario"
                    } else {
                        "Chat cancelado por el usuario"
                    }
                    FlatterToast.showError(this, message)
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

            // Show both buttons for listing owner
            btnAction1.visibility = View.VISIBLE
            btnAction2.visibility = View.VISIBLE

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
            // Requester sees chat info with continue option only
            tvBannerMessage.text = "Tu solicitud ha sido enviada a $otherUserName"
            btnAction1.text = "Continuar"

            // Hide reject button for requester
            btnAction2.visibility = View.GONE

            btnAction1.setOnClickListener {
                // Continue with chat (just hide the banner)
                actionBanner.visibility = View.GONE
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

    private fun sendMessage() {
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

            // If this is a message from the listing owner and chat is pending, accept the chat
            if (success && isListingOwner && chatStatus == "pending") {
                Log.d("ConversationActivity", "Listing owner sent message, accepting chat")
                chatService.updateChatStatus(chatId, "accepted")
            }

            binding.progressBar.visibility = View.GONE

            if (!success) {
                // Replace with custom error toast
                FlatterToast.showError(
                    this@ConversationActivity,
                    "Error al enviar mensaje"
                )
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