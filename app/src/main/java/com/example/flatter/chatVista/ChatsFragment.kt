package com.example.flatter.chatVista

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.flatter.databinding.FragmentChatsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class ChatsFragment : Fragment() {

    private var _binding: FragmentChatsBinding? = null
    private val binding get() = _binding!!

    private lateinit var chatService: ChatService
    private lateinit var chatAdapter: ChatPreviewAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize chat service
        chatService = ChatService()

        // Set up RecyclerView
        setupRecyclerView()

        Log.d("ChatDebug", "Current user ID: ${FirebaseAuth.getInstance().currentUser?.uid ?: "Not logged in"}")

        // Check if user is logged in, if not, show message
        if (FirebaseAuth.getInstance().currentUser == null) {
            binding.tvNoChats.text = "Please log in to see your chats"
            binding.tvNoChats.visibility = View.VISIBLE
            binding.rvChats.visibility = View.GONE
            return
        }

        // Subscribe to chat updates
        chatService.getUserChatsFlow().onEach { chatsList ->
            if (chatsList.isEmpty()) {
                binding.tvNoChats.visibility = View.VISIBLE
                binding.rvChats.visibility = View.GONE
            } else {
                binding.tvNoChats.visibility = View.GONE
                binding.rvChats.visibility = View.VISIBLE
                chatAdapter.submitList(chatsList)
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatPreviewAdapter { chatPreview ->
            // Open conversation activity when a chat is clicked
            val intent = Intent(requireContext(), ConversationActivity::class.java).apply {
                putExtra("CHAT_ID", chatPreview.chatId)
                putExtra("OTHER_USER_NAME", chatPreview.otherUserName)
                putExtra("OTHER_USER_ID", chatPreview.otherUserId)
                putExtra("OTHER_USER_PROFILE_PIC", chatPreview.otherUserProfilePic)
                putExtra("IS_NEW_CHAT", chatPreview.lastMessage.isEmpty())

                // Add listing-related extras
                putExtra("LISTING_ID", chatPreview.listingId)
                putExtra("LISTING_TITLE", chatPreview.listingTitle)
            }
            startActivity(intent)
        }

        binding.rvChats.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
        }

        // Set up swipe to delete
        setupSwipeToDelete()
    }

    private fun setupSwipeToDelete() {
        val swipeHandler = object : SwipeToDeleteCallback(requireContext()) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.absoluteAdapterPosition
                val chatToRemove = chatAdapter.currentList[position]

                removeChat(chatToRemove.chatId)
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(binding.rvChats)
    }

    private fun removeChat(chatId: String) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // We're not deleting the actual chat, just removing it from the user's chat list
        db.collection("users").document(currentUserId)
            .collection("chats").document(chatId)
            .delete()
            .addOnSuccessListener {
                Log.d("ChatsFragment", "Chat removed from user's list")
            }
            .addOnFailureListener { e ->
                Log.e("ChatsFragment", "Error removing chat: ${e.message}")
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}