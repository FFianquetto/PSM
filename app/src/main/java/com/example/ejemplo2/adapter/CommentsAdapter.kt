package com.example.ejemplo2.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.ejemplo2.R
import com.example.ejemplo2.data.api.ApiService
import java.text.SimpleDateFormat
import java.util.*

class CommentsAdapter(
    private var comments: List<ApiService.CommentData> = emptyList(),
    private val onVoteClick: (ApiService.CommentData, Int, Int) -> Unit = { _, _, _ -> }, // comment, position, voteType
    private val onReplyClick: (Long, String) -> Unit = { _, _ -> }, // commentId, replyText
    private val onReplyVoteClick: (ApiService.ReplyData, Long, Int) -> Unit = { _, _, _ -> } // reply, commentId, voteType
) : RecyclerView.Adapter<CommentsAdapter.CommentViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy 'a las' HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("America/Mexico_City")
    }
    private val repliesMap = mutableMapOf<Long, List<ApiService.ReplyData>>() // commentId -> replies

    class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userNameTextView: TextView = itemView.findViewById(R.id.commentUserName)
        val commentTextTextView: TextView = itemView.findViewById(R.id.commentText)
        val commentDateTextView: TextView = itemView.findViewById(R.id.commentDate)
        val likeButton: ImageView = itemView.findViewById(R.id.commentLikeButton)
        val dislikeButton: ImageView = itemView.findViewById(R.id.commentDislikeButton)
        val likesCountTextView: TextView = itemView.findViewById(R.id.commentLikesCount)
        val dislikesCountTextView: TextView = itemView.findViewById(R.id.commentDislikesCount)
        val replyButton: TextView = itemView.findViewById(R.id.commentReplyButton)
        val repliesContainer: LinearLayout = itemView.findViewById(R.id.repliesContainer)
        val replyFormContainer: LinearLayout = itemView.findViewById(R.id.replyFormContainer)
        val replyEditText: EditText = itemView.findViewById(R.id.replyEditText)
        val submitReplyButton: Button = itemView.findViewById(R.id.submitReplyButton)
        val cancelReplyButton: Button = itemView.findViewById(R.id.cancelReplyButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        
        // Nombre del usuario
        holder.userNameTextView.text = comment.getFullName()
        
        // Texto del comentario
        holder.commentTextTextView.text = comment.commentText
        
        // Formatear fecha
        val date = Date(comment.createdAt)
        holder.commentDateTextView.text = dateFormat.format(date)
        
        // Conteos de likes/dislikes
        holder.likesCountTextView.text = comment.likes.toString()
        holder.dislikesCountTextView.text = comment.dislikes.toString()
        
        // Configurar estado de los botones de voto
        updateVoteButtons(holder, comment.voteType)
        
        // Configurar listeners de los botones
        holder.likeButton.setOnClickListener {
            val newVoteType = if (comment.voteType == 1) -1 else 1
            onVoteClick(comment, position, newVoteType)
        }
        
        holder.dislikeButton.setOnClickListener {
            val newVoteType = if (comment.voteType == 0) -1 else 0
            onVoteClick(comment, position, newVoteType)
        }
        
        // Configurar botón de responder
        holder.replyButton.setOnClickListener {
            if (holder.replyFormContainer.visibility == View.VISIBLE) {
                holder.replyFormContainer.visibility = View.GONE
            } else {
                holder.replyFormContainer.visibility = View.VISIBLE
                holder.replyEditText.requestFocus()
            }
        }
        
        // Configurar botones del formulario de respuesta
        holder.submitReplyButton.setOnClickListener {
            val replyText = holder.replyEditText.text.toString().trim()
            if (replyText.isNotEmpty()) {
                onReplyClick(comment.id, replyText)
                holder.replyEditText.text.clear()
                holder.replyFormContainer.visibility = View.GONE
            }
        }
        
        holder.cancelReplyButton.setOnClickListener {
            holder.replyEditText.text.clear()
            holder.replyFormContainer.visibility = View.GONE
        }
        
        // Mostrar respuestas si existen
        val replies = repliesMap[comment.id] ?: emptyList()
        if (replies.isNotEmpty()) {
            holder.repliesContainer.visibility = View.VISIBLE
            // Limpiar contenedor antes de agregar nuevos elementos
            holder.repliesContainer.removeAllViews()
            
            replies.forEach { reply ->
                val replyView = LayoutInflater.from(holder.itemView.context)
                    .inflate(R.layout.item_reply, holder.repliesContainer, false)
                
                val replyUserName = replyView.findViewById<TextView>(R.id.replyUserName)
                val replyText = replyView.findViewById<TextView>(R.id.replyText)
                val replyDate = replyView.findViewById<TextView>(R.id.replyDate)
                val replyLikeButton = replyView.findViewById<ImageView>(R.id.replyLikeButton)
                val replyDislikeButton = replyView.findViewById<ImageView>(R.id.replyDislikeButton)
                val replyLikesCount = replyView.findViewById<TextView>(R.id.replyLikesCount)
                val replyDislikesCount = replyView.findViewById<TextView>(R.id.replyDislikesCount)
                
                // Obtener los contenedores padre (LinearLayouts clickables)
                val replyLikeContainer = replyLikeButton.parent as? LinearLayout
                val replyDislikeContainer = replyDislikeButton.parent as? LinearLayout
                
                replyUserName.text = reply.getFullName()
                replyText.text = reply.replyText
                val replyDateObj = Date(reply.createdAt)
                replyDate.text = dateFormat.format(replyDateObj)
                
                // Configurar conteos de likes/dislikes
                replyLikesCount.text = reply.likes.toString()
                replyDislikesCount.text = reply.dislikes.toString()
                
                // Configurar estado de los botones de voto de respuesta
                updateReplyVoteButtons(replyView, reply.voteType)
                
                // Configurar listeners de los botones de voto de respuesta
                replyLikeContainer?.setOnClickListener {
                    val newVoteType = if (reply.voteType == 1) -1 else 1
                    onReplyVoteClick(reply, comment.id, newVoteType)
                }
                
                replyDislikeContainer?.setOnClickListener {
                    val newVoteType = if (reply.voteType == 0) -1 else 0
                    onReplyVoteClick(reply, comment.id, newVoteType)
                }
                
                holder.repliesContainer.addView(replyView)
            }
        } else {
            holder.repliesContainer.visibility = View.GONE
        }
    }
    
    private fun updateVoteButtons(holder: CommentViewHolder, voteType: Int) {
        // Actualizar botón de like
        val likeColor = if (voteType == 1) {
            R.color.primary_blue
        } else {
            R.color.medium_gray
        }
        holder.likeButton.setColorFilter(ContextCompat.getColor(holder.itemView.context, likeColor))
        
        // Actualizar botón de dislike
        val dislikeColor = if (voteType == 0) {
            R.color.primary_blue
        } else {
            R.color.medium_gray
        }
        holder.dislikeButton.setColorFilter(ContextCompat.getColor(holder.itemView.context, dislikeColor))
    }
    
    private fun updateReplyVoteButtons(replyView: View, voteType: Int) {
        val replyLikeButton = replyView.findViewById<ImageView>(R.id.replyLikeButton)
        val replyDislikeButton = replyView.findViewById<ImageView>(R.id.replyDislikeButton)
        
        // Actualizar botón de like
        val likeColor = if (voteType == 1) {
            R.color.primary_blue
        } else {
            R.color.medium_gray
        }
        replyLikeButton.setColorFilter(ContextCompat.getColor(replyView.context, likeColor))
        
        // Actualizar botón de dislike
        val dislikeColor = if (voteType == 0) {
            R.color.primary_blue
        } else {
            R.color.medium_gray
        }
        replyDislikeButton.setColorFilter(ContextCompat.getColor(replyView.context, dislikeColor))
    }
    
    fun updateReplyVoteStatus(commentId: Long, replyId: Long, voteType: Int, likes: Int, dislikes: Int) {
        val replies = repliesMap[commentId]?.toMutableList() ?: return
        val replyIndex = replies.indexOfFirst { it.id == replyId }
        if (replyIndex >= 0) {
            replies[replyIndex] = replies[replyIndex].copy(
                voteType = voteType,
                likes = likes,
                dislikes = dislikes
            )
            repliesMap[commentId] = replies
            // Encontrar la posición del comentario y actualizar
            val position = comments.indexOfFirst { it.id == commentId }
            if (position >= 0) {
                notifyItemChanged(position)
            }
        }
    }
    
    fun updateVoteStatus(position: Int, voteType: Int, likes: Int, dislikes: Int) {
        if (position >= 0 && position < comments.size) {
            val updatedComments = comments.toMutableList()
            updatedComments[position] = comments[position].copy(
                voteType = voteType,
                likes = likes,
                dislikes = dislikes
            )
            comments = updatedComments
            notifyItemChanged(position)
        }
    }
    
    fun updateRepliesForComment(commentId: Long, replies: List<ApiService.ReplyData>) {
        repliesMap[commentId] = replies
        // Encontrar la posición del comentario y actualizar
        val position = comments.indexOfFirst { it.id == commentId }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = comments.size

    fun updateComments(newComments: List<ApiService.CommentData>) {
        comments = newComments
        notifyDataSetChanged()
    }
}

