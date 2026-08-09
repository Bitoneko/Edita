package com.bitoneko.kouecanvas

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ProjectItem(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val lastModified: Long,
    val previewPath: String
)

object ProjectStorageManager {
    private const val PREFS_NAME = "edita2_projects_prefs"
    private const val KEY_PROJECTS = "projects_json_list"

    fun getAllProjects(context: Context): ArrayList<ProjectItem> {
        val list = ArrayList<ProjectItem>()
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonStr = prefs.getString(KEY_PROJECTS, "[]") ?: "[]"
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ProjectItem(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        width = obj.getInt("width"),
                        height = obj.getInt("height"),
                        lastModified = obj.getLong("lastModified"),
                        previewPath = obj.getString("previewPath")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list.sortByDescending { it.lastModified }
        return list
    }

    fun saveProjectMetadata(context: Context, item: ProjectItem) {
        try {
            val currentList = getAllProjects(context)
            currentList.removeAll { it.id == item.id }
            currentList.add(item)
            
            val array = JSONArray()
            for (proj in currentList) {
                val obj = JSONObject()
                obj.put("id", proj.id)
                obj.put("name", proj.name)
                obj.put("width", proj.width)
                obj.put("height", proj.height)
                obj.put("lastModified", proj.lastModified)
                obj.put("previewPath", proj.previewPath)
                array.put(obj)
            }
            
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PROJECTS, array.toString())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteProject(context: Context, id: String) {
        try {
            val currentList = getAllProjects(context)
            val target = currentList.find { it.id == id }
            if (target != null) {
                val previewFile = File(target.previewPath)
                if (previewFile.exists()) previewFile.delete()
                
                val layersDir = File(context.filesDir, "project_layers_$id")
                if (layersDir.exists()) layersDir.deleteRecursively()
                
                currentList.removeAll { it.id == id }
                val array = JSONArray()
                for (proj in currentList) {
                    val obj = JSONObject()
                    obj.put("id", proj.id)
                    obj.put("name", proj.name)
                    obj.put("width", proj.width)
                    obj.put("height", proj.height)
                    obj.put("lastModified", proj.lastModified)
                    obj.put("previewPath", proj.previewPath)
                    array.put(obj)
                }
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_PROJECTS, array.toString())
                    .apply()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getProjectLayersDirectory(context: Context, id: String): File {
        val dir = File(context.filesDir, "project_layers_$id")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
