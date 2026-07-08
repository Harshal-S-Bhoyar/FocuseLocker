package com.focuslock.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.focuslock.R
import com.focuslock.domain.model.BlockedCategory

/**
 * Screen where users can select which categories they want to block.
 * This is the first step in the session setup flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelectionScreen(
    onNavigateNext: () -> Unit,
    viewModel: CategorySelectionViewModel = hiltViewModel()
) {
    // Collect the flow of categories as state
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val autoMode by viewModel.autoMode.collectAsStateWithLifecycle() // ADD

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.category_selection_title)) }
            )
        },
        bottomBar = {
            com.focuslock.ui.components.PrimaryButton(
                onClick = onNavigateNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(R.dimen.spacing_lg)),
                label = stringResource(R.string.next)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(dimensionResource(R.dimen.spacing_lg)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_lg))
        ) {
            // ADD: Auto Mode card at top
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(dimensionResource(R.dimen.spacing_lg)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.auto_mode_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.auto_mode_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)                        }
                        Switch(checked = autoMode, onCheckedChange = viewModel::onAutoModeToggled)
                    }
                }
            }

            items(categories, key = { it.categoryKey }) { category ->
                // Filter out GENERAL and AUTO_BLOCKED from UI — internal categories
                if (category.categoryKey != "AUTO_BLOCKED" && category.categoryKey != "GENERAL") {
                    CategoryItem(
                        category = category,
                        onToggle = { isActive ->
                            viewModel.onCategoryToggled(category.categoryKey, isActive)
                        },
                        enabled = !autoMode // grey out when auto-mode on
                    )
                }
            }
        }
    }
}

/**
 * A reusable component to display a single category item with a toggle switch.
 */
@Composable
fun CategoryItem(
    category: BlockedCategory,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = dimensionResource(R.dimen.card_elevation_medium))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(R.dimen.spacing_lg)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_xs)))
                Text(
                    text = stringResource(R.string.category_example_domains, category.exampleDomains),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = category.isActive,
                onCheckedChange = onToggle,
                enabled = enabled
            )
        }
    }
}
