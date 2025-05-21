package com.example.flatter.chatVista

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.flatter.R
import com.example.flatter.databinding.ActivityConversationBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
    private var isOwner: Boolean = false

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
        isOwner = currentUserId != otherUserId

        // Set up toolbar with listing title
        setupToolbar()

        // Set up RecyclerView
        setupRecyclerView()

        // Show chat action options inside the chat
        if (isNewChat) {
            showChatActionBanner()
        }

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

        // Show listing title in the toolbar
        supportActionBar?.title = listingTitle

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

    private fun showChatActionBanner() {
        // Show action banner based on whether user is owner or requester
        // Fix: Access the chatActionBanner view as a ViewGroup or specific view type
        val actionBanner = findViewById<View>(R.id.chatActionBanner)
        actionBanner.visibility = View.VISIBLE

        // Get references to the views in the banner
        val tvBannerMessage = findViewById<TextView>(R.id.tvBannerMessage)
        val btnBannerAction1 = findViewById<Button>(R.id.btnBannerAction1)
        val btnBannerAction2 = findViewById<Button>(R.id.btnBannerAction2)

        if (isOwner) {
            // Owner sees info about the requester with accept/decline options
            tvBannerMessage.text = "A $otherUserName le interesa tu anuncio"
            btnBannerAction1.text = "Aceptar"
            btnBannerAction2.text = "Rechazar"

            btnBannerAction1.setOnClickListener {
                // Accept chat request
                updateChatStatus("accepted")
                actionBanner.visibility = View.GONE
            }

            btnBannerAction2.setOnClickListener {
                // Decline chat request
                updateChatStatus("declined")
                sendDeclineNotification()
                finish()
            }
        } else {
            // Requester sees chat info with continue/cancel options
            tvBannerMessage.text = "Tu solicitud ha sido enviada a $otherUserName"
            btnBannerAction1.text = "Continuar"
            btnBannerAction2.text = "Cancelar"

            btnBannerAction1.setOnClickListener {
                // Continue with chat (just hide the banner)
                actionBanner.visibility = View.GONE
            }

            btnBannerAction2.setOnClickListener {
                // Cancel chat request
                updateChatStatus("cancelled")
                finish()
            }
        }
    }

    private fun updateChatStatus(status: String) {
        val db = FirebaseFirestore.getInstance()

        db.collection("chats").document(chatId)
            .update("status", status)
            .addOnSuccessListener {
                if (status != "accepted") {
                    val statusMessage = when(status) {
                        "declined" -> "Chat rechazado"
                        "cancelled" -> "Chat cancelado"
                        else -> "Estado actualizado"
                    }
                    Toast.makeText(this, statusMessage, Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al actualizar estado: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendDeclineNotification() {
        // Create a notification to let the requester know their chat was declined
        val db = FirebaseFirestore.getInstance()
        val notification = hashMapOf(
            "userId" to otherUserId,
            "title" to "Solicitud de chat rechazada",
            "message" to "Tu solicitud de chat para \"$listingTitle\" ha sido rechazada por el propietario",
            "timestamp" to com.google.firebase.Timestamp.now(),
            "read" to false,
            "type" to "chat_declined"
        )

        db.collection("notifications").add(notification)
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al enviar notificación: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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