package com.kayayam.simplemeditation

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.json.JSONObject

// --- Модели данных ---
data class MeditationItemData(val title: String, val description: String, val content: String)
data class SubCategoryData(val name: String, val items: List<MeditationItemData>)
data class CategoryData(
    val id: String,
    val name: String,
    val subtitle: String,
    val items: List<MeditationItemData>? = null,
    val subcategories: List<SubCategoryData>? = null
)

object JsonLoader {
    fun loadMeditations(context: Context): List<CategoryData> {
        return try {
            val jsonString = context.assets.open("meditations.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)
            val categoriesArray = root.getJSONArray("categories")
            val categories = mutableListOf<CategoryData>()

            for (i in 0 until categoriesArray.length()) {
                val catObj = categoriesArray.getJSONObject(i)

                val items = if (catObj.has("items")) {
                    val itemsArray = catObj.getJSONArray("items")
                    List(itemsArray.length()) { j ->
                        val item = itemsArray.getJSONObject(j)
                        MeditationItemData(item.getString("title"), item.getString("description"), item.getString("content"))
                    }
                } else null

                val subCats = if (catObj.has("subcategories")) {
                    val subArray = catObj.getJSONArray("subcategories")
                    List(subArray.length()) { j ->
                        val sub = subArray.getJSONObject(j)
                        val subItems = sub.getJSONArray("items")
                        SubCategoryData(
                            sub.getString("name"),
                            List(subItems.length()) { k ->
                                val subItem = subItems.getJSONObject(k)
                                MeditationItemData(subItem.getString("title"), subItem.getString("description"), subItem.getString("content"))
                            }
                        )
                    }
                } else null

                categories.add(CategoryData(catObj.getString("id"), catObj.getString("name"), catObj.getString("subtitle"), items, subCats))
            }
            categories
        } catch (e: Exception) { emptyList() }
    }
}

@Composable
fun MarkdownText(text: String, themeData: ThemeData) {
    val lines = text.split("\n")
    Column(modifier = Modifier.fillMaxWidth()) {
        lines.forEach { line ->
            when {
                line.startsWith("# ") -> {
                    Text(line.removePrefix("# "), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = themeData.accentColor, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                }
                line.startsWith("## ") -> {
                    Text(line.removePrefix("## "), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = themeData.textColor, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                else -> {
                    val annotatedString = buildAnnotatedString {
                        val parts = line.split("**")
                        parts.forEachIndexed { index, part ->
                            if (index % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
                        }
                    }
                    Text(annotatedString, fontSize = 17.sp, lineHeight = 26.sp, color = themeData.textColor.copy(alpha = 0.85f), modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}

@Composable
fun MeditationMenu(showMenu: Boolean, onDismiss: () -> Unit, onBack: () -> Unit, themeData: ThemeData) {
    val context = LocalContext.current
    val categories = remember { JsonLoader.loadMeditations(context) }

    var selectedCategory by remember { mutableStateOf<CategoryData?>(null) }
    var selectedSubCategory by remember { mutableStateOf<SubCategoryData?>(null) }
    var selectedMeditation by remember { mutableStateOf<MeditationItemData?>(null) }

    if (showMenu) {
        Dialog(onDismissRequest = {
            if (selectedMeditation != null) selectedMeditation = null
            else if (selectedSubCategory != null) selectedSubCategory = null
            else if (selectedCategory != null) selectedCategory = null
            else onDismiss()
        }) {
            Surface(modifier = Modifier.fillMaxSize().padding(16.dp), shape = MaterialTheme.shapes.extraLarge, color = themeData.backgroundColor) {
                Column(modifier = Modifier.padding(24.dp)) {
                    when {
                        // 1. Контент медитации
                        selectedMeditation != null -> {
                            IconButton(onClick = { selectedMeditation = null }) { Icon(Icons.Default.ArrowBack, null, tint = themeData.accentColor) }
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                item { MarkdownText(selectedMeditation?.content ?: "", themeData) }
                            }
                        }

                        // 2. Список практик внутри подкатегории
                        selectedSubCategory != null -> {
                            IconButton(onClick = { selectedSubCategory = null }) { Icon(Icons.Default.ArrowBack, null, tint = themeData.accentColor) }
                            Text(selectedSubCategory?.name ?: "", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = themeData.textColor)
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                val itemsList = selectedSubCategory?.items ?: emptyList()
                                items(itemsList) { item ->
                                    MeditationItem(item.title, item.description, themeData) { selectedMeditation = item }
                                }
                            }
                        }

                        // 3. Список подкатегорий ИЛИ практик внутри категории
                        selectedCategory != null -> {
                            IconButton(onClick = { selectedCategory = null }) { Icon(Icons.Default.ArrowBack, null, tint = themeData.accentColor) }
                            Text(selectedCategory?.name ?: "", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = themeData.textColor)
                            Spacer(modifier = Modifier.height(8.dp))

                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                // Отрисовываем подкатегории, если они есть
                                selectedCategory?.subcategories?.let { subs ->
                                    items(subs) { sub ->
                                        MenuItem(sub.name, themeData) { selectedSubCategory = sub }
                                    }
                                }
                                // Отрисовываем прямые айтемы, если они есть
                                selectedCategory?.items?.let { its ->
                                    items(its) { item ->
                                        MeditationItem(item.title, item.description, themeData) { selectedMeditation = item }
                                    }
                                }
                            }
                        }

                        // 4. Главный экран библиотеки
                        else -> {
                            TextButton(onClick = onBack) { Text("← Закрыть", color = themeData.accentColor) }
                            Text("Библиотека смыслов", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = themeData.textColor)
                            Spacer(modifier = Modifier.height(16.dp))
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(categories) { category ->
                                    MenuItem(category.name, themeData, subtitle = category.subtitle) { selectedCategory = category }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MenuItem(title: String, themeData: ThemeData, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null) { onClick() }.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = themeData.textColor)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = themeData.secondaryTextColor)
        }
    }
}

@Composable
fun MeditationItem(name: String, description: String, themeData: ThemeData, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(remember { MutableInteractionSource() }, null) { onClick() },
        colors = CardDefaults.cardColors(containerColor = themeData.buttonColor.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(name, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = themeData.textColor)
            Text(description, fontSize = 14.sp, color = themeData.secondaryTextColor)
        }
    }
}