package com.bipminder.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private fun statusOf(i: Item, now: Long) = when { i.completed -> 2; i.isOverdue(now) -> 3; else -> 1 }

@Composable
fun TasksScreen(
    all: List<Item>, onOpen: (Item) -> Unit, onToggle: (Item) -> Unit,
    onDuplicate: (Item) -> Unit, onDelete: (Item) -> Unit
) {
    var status by rememberSaveable { mutableStateOf(0) }
    var typeF by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()

    val list = remember(all, status, typeF, query, sort) {
        val q = query.trim().lowercase()
        all.filter { i ->
            val typeOk = when (typeF) { 1 -> i.type == Type.TASK; 2 -> i.type == Type.EVENT; 3 -> i.type == Type.NOTE; else -> true }
            val statusOk = if (status == 0) true else i.type != Type.NOTE && statusOf(i, now) == status
            val qOk = q.isEmpty() || i.title.lowercase().contains(q) || i.description.lowercase().contains(q) ||
                i.tags.lowercase().contains(q) || i.category.lowercase().contains(q)
            typeOk && statusOk && qOk
        }.let { l ->
            when (sort) {
                1 -> l.sortedWith(compareByDescending<Item> { it.priority }.thenBy { it.day }.thenBy { it.startMin })
                2 -> l.sortedBy { it.title.lowercase() }
                3 -> l.sortedByDescending { it.createdAt }
                else -> l.sortedWith(compareBy<Item> { it.completed }.thenBy { it.day }.thenBy { if (it.allDay) 0 else it.startMin })
            }
        }
    }
    val sortNames = listOf("Date", "Priority", "Title", "Newest")

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Tasks") {
            CircleBtn(Icons.Outlined.Search, { searching = !searching; if (!searching) query = "" }, desc = "Search")
            Box {
                CircleBtn(Icons.Outlined.Sort, { sortMenu = true }, desc = "Sort")
                DropdownMenu(sortMenu, { sortMenu = false }, modifier = Modifier.background(C.card)) {
                    sortNames.forEachIndexed { i, n ->
                        DropdownMenuItem(
                            text = { BText("Sort: $n", 14, if (sort == i) 600 else 400, if (sort == i) C.accent else C.text) },
                            onClick = { sort = i; sortMenu = false })
                    }
                }
            }
        }
        if (searching) { SearchField(query, { query = it }, { searching = false; query = "" }); Spacer(Modifier.height(10.dp)) }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val names = listOf("All", "In Progress", "Completed", "Overdue")
            items(names.size) { i -> Pill(names[i], status == i, { status = i }) }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val names = listOf("All types", "Tasks", "Events", "Notes")
            items(names.size) { i -> Pill(names[i], typeF == i, { typeF = i }) }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (list.isEmpty()) {
                item {
                    if (query.isNotBlank()) EmptyState(Icons.Outlined.Search, "No matches", "Try a different search or filter.")
                    else EmptyState(Icons.Outlined.Checklist, "Nothing here yet", "Tap + to add a task, event or note.")
                }
            } else {
                items(list, key = { it.id }) { i ->
                    ItemCard(i, i.day, true, true, { onOpen(i) }, { onToggle(i) }, { onDuplicate(i) }, { onDelete(i) })
                }
            }
        }
    }
}


