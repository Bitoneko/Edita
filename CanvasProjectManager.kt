package com.bitoneko.kouecanvas

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.io.File
import java.io.FileOutputStream

class CanvasProjectAdapter(
    private val ctx: Context,
    private var dataset: ArrayList<ProjectItem>
) : BaseAdapter() {
    override fun getCount(): Int = dataset.size
    override fun getItem(position: Int): ProjectItem = dataset[position]
    override fun getItemId(position: Int): Long = position.toLong()

    fun updateData(newDataset: ArrayList<ProjectItem>) {
        this.dataset = newDataset
        notifyDataSetChanged()
        
        if (ctx is Activity) {
            val emptyContainerId = ctx.resources.getIdentifier("empty_container", "id", ctx.packageName)
            if (emptyContainerId != 0) {
                val emptyView = ctx.findViewById<View>(emptyContainerId)
                emptyView?.visibility = if (dataset.size > 0) View.GONE else View.VISIBLE
            }
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(ctx).inflate(
            ctx.resources.getIdentifier("project_list_element", "layout", ctx.packageName).takeIf { it != 0 }
                ?: android.R.layout.simple_list_item_2, parent, false
        )
        val item = getItem(position)
        val pkg = ctx.packageName
        
        val txtNameId = ctx.resources.getIdentifier("txt_name", "id", pkg)
        val imgPreviewId = ctx.resources.getIdentifier("img_preview", "id", pkg)
        
        if (txtNameId != 0) {
            view.findViewById<TextView>(txtNameId)?.text = item.name
        }
        if (imgPreviewId != 0) {
            val imgView = view.findViewById<ImageView>(imgPreviewId)
            if (imgView != null) {
                val f = File(item.previewPath)
                if (f.exists()) {
                    imgView.setImageBitmap(BitmapFactory.decodeFile(f.absolutePath))
                } else {
                    imgView.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }
        }
        return view
    }
}

object CanvasProjectManager {
    
    fun generateUniqueName(context: Context, baseName: String): String {
        val list = ProjectStorageManager.getAllProjects(context)
        var uniqueName = baseName
        var index = 1
        while (list.any { it.name.equals(uniqueName, ignoreCase = true) }) {
            uniqueName = "$baseName ($index)"
            index++
        }
        return uniqueName
    }

    fun setupProjectsList(act: Activity, listView: ListView, fab: ExtendedFloatingActionButton) {
        val context = act.applicationContext
        val items = ProjectStorageManager.getAllProjects(context)
        val adapter = CanvasProjectAdapter(act, items)
        listView.adapter = adapter
        
        val emptyContainerId = act.resources.getIdentifier("empty_container", "id", act.packageName)
        if (emptyContainerId != 0) {
            val emptyView = act.findViewById<View>(emptyContainerId)
            emptyView?.visibility = if (items.size > 0) View.GONE else View.VISIBLE
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val currentItems = ProjectStorageManager.getAllProjects(context)
            if (position >= 0 && position < currentItems.size) {
                val p = currentItems[position]
                val i = Intent(act, CanvasActivity::class.java).apply {
                    putExtra("PROJECT_ID", p.id)
                    putExtra("PROJECT_NAME", p.name)
                }
                act.startActivity(i)
            }
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            val currentItems = ProjectStorageManager.getAllProjects(context)
            if (position >= 0 && position < currentItems.size) {
                val p = currentItems[position]
                MaterialAlertDialogBuilder(act)
                    .setTitle("Delete Project")
                    .setMessage("Are you sure you want to permanently delete '${p.name}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        ProjectStorageManager.deleteProject(context, p.id)
                        val updatedItems = ProjectStorageManager.getAllProjects(context)
                        adapter.updateData(updatedItems)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            true
        }
        listView.setOnScrollListener(object : AbsListView.OnScrollListener {
            private var lastFirstVisibleItem = 0
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {}
            override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                val density = act.resources.getDisplayMetrics().density
                val rootVg = act.window.decorView.findViewById<ViewGroup>(android.R.id.content) ?: return
                
                if (firstVisibleItem > lastFirstVisibleItem) {
                    if (fab.isExtended) {
                        fab.shrink()
                        val travelDistance = (rootVg.width / 2f) - (fab.width / 4f)
                        fab.animate()
                            .translationX(travelDistance)
                            .setDuration(200)
                            .start()
                    }
                } else if (firstVisibleItem < lastFirstVisibleItem) {
                    if (!fab.isExtended) {
                        fab.extend()
                        fab.animate()
                            .translationX(0f)
                            .setDuration(200)
                            .start()
                    }
                }
                lastFirstVisibleItem = firstVisibleItem
            }
        })
    }

    fun openNewProjectDialog(act: Activity, fab: ExtendedFloatingActionButton?, listView: ListView?) {
        val density = act.resources.displayMetrics.density
        val input = EditText(act).apply {
            hint = "New Canvas Project"
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
        }
        
        val container = android.widget.FrameLayout(act).apply {
            setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
            addView(input)
        }

        MaterialAlertDialogBuilder(act)
            .setTitle("Create New Project")
            .setView(container)
            .setPositiveButton("Create") { _, _ ->
                var enteredName = input.text.toString().trim()
                if (enteredName.isEmpty()) {
                    enteredName = "New Canvas Project"
                }
                val uniqueName = generateUniqueName(act, enteredName)
                val projId = System.currentTimeMillis().toString()
                
                val emptyBmp = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(emptyBmp)
                
                autoSaveCanvasWorkspace(act, projId, uniqueName, 500, 500, emptyBmp)
                emptyBmp.recycle()
                
                if (listView != null) {
                    refreshListView(act, listView)
                }
                
                val intent = Intent(act, CanvasActivity::class.java).apply {
                    putExtra("PROJECT_NAME", uniqueName)
                    putExtra("PROJECT_ID", projId)
                }
                act.startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun refreshListView(act: Activity, listView: ListView) {
        val items = ProjectStorageManager.getAllProjects(act.applicationContext)
        
        val emptyContainerId = act.resources.getIdentifier("empty_container", "id", act.packageName)
        if (emptyContainerId != 0) {
            val emptyView = act.findViewById<View>(emptyContainerId)
            emptyView?.visibility = if (items.size > 0) View.GONE else View.VISIBLE
        }

        val adapter = listView.adapter
        if (adapter is CanvasProjectAdapter) {
            adapter.updateData(items)
        } else {
            listView.adapter = CanvasProjectAdapter(act, items)
        }
    }

        fun autoSaveCanvasWorkspace(context: Context, id: String, name: String, width: Int, height: Int, canvasBitmap: Bitmap) {
        try {
            val pFile = File(context.filesDir, "preview_$id.png")
            val out = FileOutputStream(pFile)
            canvasBitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            out.flush()
            out.close()

            val item = ProjectItem(
                id = id,
                name = name,
                width = width,
                height = height,
                lastModified = System.currentTimeMillis(),
                previewPath = pFile.absolutePath
            )
            ProjectStorageManager.saveProjectMetadata(context, item)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
