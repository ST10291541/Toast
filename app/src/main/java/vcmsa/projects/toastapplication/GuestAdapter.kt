package vcmsa.projects.toastapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GuestAdapter(private val guestList: List<Guest>) :
    RecyclerView.Adapter<GuestAdapter.GuestViewHolder>() {

    class GuestViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvGuestName)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val tvDietary: TextView = view.findViewById(R.id.tvDietary)
        val tvMusic: TextView = view.findViewById(R.id.tvMusic)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GuestViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_guest, parent, false)
        return GuestViewHolder(view)
    }

    override fun onBindViewHolder(holder: GuestViewHolder, position: Int) {
        val guest = guestList[position]
        holder.tvName.text = guest.userName
        holder.tvStatus.text = "RSVP: ${guest.status}"
        holder.tvDietary.text = "Dietary: ${guest.dietaryChoice}"
        holder.tvMusic.text = "Music: ${guest.musicChoice}"
    }

    override fun getItemCount() = guestList.size
}
