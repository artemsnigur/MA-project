package com.example.cityfixmaas.ui.operator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cityfixmaas.R
import com.example.cityfixmaas.data.model.Category
import com.example.cityfixmaas.data.repository.CategoryRepository
import com.example.cityfixmaas.di.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(private val repository: CategoryRepository) : ViewModel() {

    val categories = repository.observeCategories(activeOnly = false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveCategory(id: String = "", name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.saveCategory(Category(id = id, name = name.trim()))
        }
    }

    fun setActive(id: String, active: Boolean) {
        viewModelScope.launch {
            repository.setActive(id, active)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { CategoriesViewModel(appContainer().categories) }
        }
    }
}

@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel = viewModel(factory = CategoriesViewModel.Factory),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var editingCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val editingCategory = categories.find { it.id == editingCategoryId }
    var isAddingNew by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { isAddingNew = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.category_add))
            }
        },
    ) { innerPadding ->
        if (categories.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.categories_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(categories, key = { it.id }) { category ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    category.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    if (category.active) stringResource(R.string.category_active) else stringResource(R.string.category_inactive),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (category.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { editingCategoryId = category.id }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.category_edit),
                                    )
                                }
                                Switch(
                                    checked = category.active,
                                    onCheckedChange = { viewModel.setActive(category.id, it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Category Dialog
    if (isAddingNew) {
        CategoryEditDialog(
            title = stringResource(R.string.category_add),
            initialName = "",
            onConfirm = { name ->
                viewModel.saveCategory(name = name)
                isAddingNew = false
            },
            onDismiss = { isAddingNew = false },
        )
    }

    // Edit Category Dialog
    editingCategory?.let { category ->
        CategoryEditDialog(
            title = stringResource(R.string.category_edit),
            initialName = category.name,
            onConfirm = { newName ->
                viewModel.saveCategory(id = category.id, name = newName)
                editingCategoryId = null
            },
            onDismiss = { editingCategoryId = null },
        )
    }
}

@Composable
private fun CategoryEditDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.category_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
