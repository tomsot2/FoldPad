package com.foldgamepad

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.foldgamepad.service.OverlayService
import com.foldgamepad.util.ConfigManager

class AppPickerActivity : AppCompatActivity() {

    private data class AppInfo(val label: String, val packageName: String, val icon: Drawable)

    private lateinit var rv:     RecyclerView
    private lateinit var search: EditText
    private var allApps:  List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        rv     = findViewById(R.id.rv_apps)
        search = findViewById(R.id.et_search)
        rv.layoutManager = GridLayoutManager(this, 4)

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filter(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loadApps()
    }

    private fun loadApps() {
        Thread {
            val pm     = packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            allApps = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
                .map { ri -> AppInfo(ri.loadLabel(pm).toString(), ri.activityInfo.packageName, ri.loadIcon(pm)) }
                .filter { it.packageName != packageName }
                .sortedBy { it.label.lowercase() }
            runOnUiThread { filter("") }
        }.start()
    }

    private fun filter(query: String) {
        val list = if (query.isBlank()) allApps else allApps.filter { it.label.contains(query, ignoreCase = true) }
        rv.adapter = AppAdapter(list) { selected ->
            val cfg = ConfigManager.load(this).copy(gamePackage = selected.packageName, gameName = selected.label)
            ConfigManager.save(this, cfg)
            OverlayService.instance?.launchGame(selected.packageName, selected.label)
            Toast.makeText(this, "${selected.label} selected", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private inner class AppAdapter(
        private val apps: List<AppInfo>,
        private val onClick: (AppInfo) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon:  ImageView = v.findViewById(R.id.iv_icon)
            val label: TextView  = v.findViewById(R.id.tv_label)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val app = apps[position]
            holder.icon.setImageDrawable(app.icon)
            holder.label.text = app.label
            holder.itemView.setOnClickListener { onClick(app) }
        }

        override fun getItemCount() = apps.size
    }
}
