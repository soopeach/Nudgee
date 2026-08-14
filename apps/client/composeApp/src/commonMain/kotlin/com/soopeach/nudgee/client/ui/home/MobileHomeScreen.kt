package com.soopeach.nudgee.client.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.soopeach.nudgee.client.NudgeeColors

private enum class TaskFilter(val label: String) {
    Today("Today"),
    Upcoming("Upcoming"),
    Done("Done"),
}

private data class MobileTask(
    val id: Int,
    val title: String,
    val reminderLabel: String,
    val completed: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileHomeScreen() {
    var tasks by remember {
        mutableStateOf(
            listOf(
                MobileTask(1, "Send project update", "Today · 4:30 PM"),
                MobileTask(2, "Pick up eggs", "Today · 6:00 PM"),
                MobileTask(3, "Read for 20 minutes", "Tomorrow · 8:00 AM"),
                MobileTask(4, "Book a dentist appointment", "Yesterday · completed", completed = true),
            ),
        )
    }
    var selectedFilter by remember { mutableStateOf(TaskFilter.Today) }
    var isQuickAddVisible by remember { mutableStateOf(false) }

    val incompleteCount = tasks.count { !it.completed }
    val completedCount = tasks.count { it.completed }
    val progress = if (tasks.isEmpty()) 0f else completedCount.toFloat() / tasks.size
    val filteredTasks = when (selectedFilter) {
        TaskFilter.Today -> tasks.filter { !it.completed && it.reminderLabel.startsWith("Today") }
        TaskFilter.Upcoming -> tasks.filter { !it.completed }
        TaskFilter.Done -> tasks.filter { it.completed }
    }

    Scaffold(
        containerColor = NudgeeColors.softSurface,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isQuickAddVisible = true },
                containerColor = NudgeeColors.lavender,
                contentColor = NudgeeColors.ink,
                shape = CircleShape,
            ) {
                Text("+", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { MobileHeader(incompleteCount = incompleteCount) }
            item { ProgressCard(completedCount = completedCount, totalCount = tasks.size, progress = progress) }
            item {
                FilterRow(
                    selectedFilter = selectedFilter,
                    onSelect = { selectedFilter = it },
                )
            }
            item {
                Text(
                    text = when (selectedFilter) {
                        TaskFilter.Today -> "Today’s nudges"
                        TaskFilter.Upcoming -> "Coming up"
                        TaskFilter.Done -> "Completed"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = NudgeeColors.ink,
                )
            }

            if (filteredTasks.isEmpty()) {
                item { EmptyTaskState(filter = selectedFilter) }
            } else {
                items(filteredTasks, key = { it.id }) { task ->
                    MobileTaskCard(
                        task = task,
                        onCheckedChange = { checked ->
                            tasks = tasks.map { item ->
                                if (item.id == task.id) item.copy(completed = checked) else item
                            }
                        },
                    )
                }
            }
        }
    }

    if (isQuickAddVisible) {
        QuickAddSheet(
            onDismiss = { isQuickAddVisible = false },
            onAdd = { title, reminderLabel ->
                tasks = listOf(
                    MobileTask(
                        id = (tasks.maxOfOrNull { it.id } ?: 0) + 1,
                        title = title,
                        reminderLabel = reminderLabel,
                    ),
                ) + tasks
                selectedFilter = TaskFilter.Today
                isQuickAddVisible = false
            },
        )
    }
}

@Composable
private fun MobileHeader(incompleteCount: Int) {
    Column {
        Text("Nudgee", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = NudgeeColors.ink)
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (incompleteCount == 0) "You’re all caught up."
            else "$incompleteCount little nudge${if (incompleteCount == 1) "" else "s"} left for today.",
            style = MaterialTheme.typography.bodyLarge,
            color = NudgeeColors.mutedInk,
        )
    }
}

@Composable
private fun ProgressCard(completedCount: Int, totalCount: Int, progress: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NudgeeColors.mint),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Today’s rhythm", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "$completedCount of $totalCount complete",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = NudgeeColors.ink,
            )
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .clip(CircleShape),
                color = NudgeeColors.lavender,
                trackColor = Color.White.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun FilterRow(selectedFilter: TaskFilter, onSelect: (TaskFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TaskFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
            )
        }
    }
}

@Composable
private fun MobileTaskCard(task: MobileTask, onCheckedChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = task.completed, onCheckedChange = onCheckedChange)
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (task.completed) NudgeeColors.mutedInk else NudgeeColors.ink,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                )
                Spacer(Modifier.height(4.dp))
                Text(task.reminderLabel, style = MaterialTheme.typography.bodySmall, color = NudgeeColors.mutedInk)
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (task.completed) NudgeeColors.mint else NudgeeColors.sky, CircleShape),
            )
        }
    }
}

@Composable
private fun EmptyTaskState(filter: TaskFilter) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.72f)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("✦", style = MaterialTheme.typography.headlineMedium, color = NudgeeColors.lavender)
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (filter) {
                    TaskFilter.Today -> "Nothing else for today."
                    TaskFilter.Upcoming -> "Your next nudge is waiting to be added."
                    TaskFilter.Done -> "Completed tasks will live here."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = NudgeeColors.mutedInk,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var reminderLabel by remember { mutableStateOf("Today · 6:00 PM") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Add a little nudge", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("What do you need to do?") },
            )
            Spacer(Modifier.height(16.dp))
            Text("Remind me", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = NudgeeColors.ink)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("In 1 hour", "Today · 6:00 PM", "Tomorrow · 9:00 AM").forEach { option ->
                    SuggestionChip(
                        onClick = { reminderLabel = option },
                        label = { Text(option) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onAdd(title.trim(), reminderLabel) },
                modifier = Modifier.fillMaxWidth(),
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NudgeeColors.lavender,
                    contentColor = NudgeeColors.ink,
                ),
            ) {
                Text("Add task", fontWeight = FontWeight.Bold)
            }
        }
    }
}
