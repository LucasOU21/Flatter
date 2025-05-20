package com.example.flatter.chatVista

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize chat service
        chatService = ChatService()

        // Get chat details from intent
        chatId = intent.getStringExtra("CHAT_ID") ?: ""
        otherUserName = intent.getStringExtra("OTHER_USER_NAME") ?: "Chat"
        otherUserId = intent.getStringExtra("OTHER_USER_ID") ?: ""
        otherUserProfilePic = intent.getStringExtra("OTHER_USER_PROFILE_PIC") ?: ""

        // Check if we have valid data
        if (chatId.isEmpty() || otherUserId.isEmpty()) {
            Toast.makeText(this, "Error: Invalid chat", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = otherUserName

        // Load other user's profile pic in toolbar
        Glide.with(this)
            .load(otherUserProfilePic)
            .placeholder(R.drawable.default_profile_img)
            .error(R.drawable.default_profile_img)
            .circleCrop()
            .into(binding.ivProfilePic)

        // Set up RecyclerView
        setupRecyclerView()

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
                    "Error sending message",
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