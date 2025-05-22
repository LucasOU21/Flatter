package com.example.flatter.chatVista

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.flatter.databinding.FragmentChatsBinding
import com.example.flatter.utils.FlatterToast
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

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
            binding.tvNoChats.text = "Por favor, inicia sesión para ver tus chats"
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

                // Format chat previews to show status and display accordingly
                val formattedChats = formatChatsForDisplay(chatsList)
                chatAdapter.submitList(formattedChats)
            }
        }.launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun formatChatsForDisplay(chatsList: List<ChatPreview>): List<ChatPreview> {
        // This function could format or filter chats based on status
        // For now, we'll just return all chats, but we could filter out declined ones, etc.
        return chatsList
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatPreviewAdapter { chatPreview ->
            // Handle chat click differently based on status
            when (chatPreview.status) {
                "pending" -> {
                    // For pending chats, ask if they want to continue or cancel
                    if (isChatInitiator(chatPreview)) {
                        showPendingChatOptions(chatPreview)
                    } else {
                        // If we're the listing owner, open chat normally
                        openConversation(chatPreview)
                    }
                }
                "accepted" -> {
                    // For accepted chats, open conversation directly
                    openConversation(chatPreview)
                }
                "declined", "cancelled" -> {
                    // For declined or cancelled chats, show status and option to delete
                    showDeclinedChatOptions(chatPreview)
                }
                else -> openConversation(chatPreview)
            }
        }

        binding.rvChats.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = chatAdapter
        }

        // Set up swipe to delete
        setupSwipeToDelete()
    }

    private fun isChatInitiator(chatPreview: ChatPreview): Boolean {
        // If there's no lastMessage, then the current user is likely the initiator
        // This is a simple heuristic that might need refinement based on your app's logic
        return chatPreview.lastMessage.isEmpty()
    }

    private fun showPendingChatOptions(chatPreview: ChatPreview) {
        AlertDialog.Builder(requireContext())
            .setTitle("Chat pendiente")
            .setMessage("Este chat está pendiente de aceptación. ¿Qué deseas hacer?")
            .setPositiveButton("Continuar") { _, _ ->
                openConversation(chatPreview)
            }
            .setNegativeButton("Cancelar chat") { _, _ ->
                lifecycleScope.launch {
                    chatService.updateChatStatus(chatPreview.chatId, "cancelled")
                    FlatterToast.showSuccess(requireContext(), "Chat cancelado")
                }
            }
            .show()
    }

    private fun showDeclinedChatOptions(chatPreview: ChatPreview) {
        val statusText = if (chatPreview.status == "declined") "rechazado" else "cancelado"

        AlertDialog.Builder(requireContext())
            .setTitle("Chat $statusText")
            .setMessage("Este chat ha sido $statusText. ¿Deseas eliminarlo?")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    chatService.deleteChat(chatPreview.chatId)
                    FlatterToast.showSuccess(requireContext(), "Chat eliminado")
                }
            }
            .setNegativeButton("Volver", null)
            .show()
    }

    private fun openConversation(chatPreview: ChatPreview) {
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

    private fun setupSwipeToDelete() {
        val swipeHandler = object : SwipeToDeleteCallback(requireContext()) {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.absoluteAdapterPosition
                val chatToRemove = chatAdapter.currentList[position]

                // Ask for confirmation before removing
                AlertDialog.Builder(requireContext())
                    .setTitle("Eliminar chat")
                    .setMessage("¿Estás seguro que quieres eliminar este chat?")
                    .setPositiveButton("Eliminar") { _, _ ->
                        removeChat(chatToRemove.chatId)
                    }
                    .setNegativeButton("Cancelar") { _, _ ->
                        // Cancel swipe action by refreshing the adapter
                        chatAdapter.notifyItemChanged(position)
                    }
                    .show()
            }
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(binding.rvChats)
    }

    private fun removeChat(chatId: String) {
        lifecycleScope.launch {
            val success = chatService.deleteChat(chatId)
            if (success) {
                FlatterToast.showSuccess(requireContext(), "Chat eliminado")
            } else {
                FlatterToast.showError(requireContext(), "Error al eliminar chat")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}