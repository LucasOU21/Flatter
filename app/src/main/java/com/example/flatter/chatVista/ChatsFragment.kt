package com.example.flatter.chatVista

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.flatter.databinding.FragmentChatsBinding
import com.google.firebase.auth.FirebaseAuth
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
            }
            startActivity(intent)
        }

        binding.rvChats.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}