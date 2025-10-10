package com.example.ejemplo2.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ejemplo2.R
import com.example.ejemplo2.data.entity.User
import java.text.SimpleDateFormat
import java.util.*

class UsersAdapter(private val users: List<User>) : RecyclerView.Adapter<UsersAdapter.UserViewHolder>() {

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textViewUserName: TextView = itemView.findViewById(R.id.textViewUserName)
        val textViewUserEmail: TextView = itemView.findViewById(R.id.textViewUserEmail)
        val textViewUserAlias: TextView = itemView.findViewById(R.id.textViewUserAlias)
        val textViewRegistrationDate: TextView = itemView.findViewById(R.id.textViewRegistrationDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]
        
        holder.textViewUserName.text = "${user.name} ${user.lastName}"
        holder.textViewUserEmail.text = user.email
        holder.textViewUserAlias.text = "@${user.alias}"
        
        // Formatear fecha de registro
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val registrationDate = Date(user.createdAt)
        holder.textViewRegistrationDate.text = "Registrado: ${dateFormat.format(registrationDate)}"
    }

    override fun getItemCount(): Int = users.size
}
