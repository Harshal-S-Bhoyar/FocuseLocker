package com.focuslock.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import com.focuslock.ui.components.PrimaryButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focuslock.R

/**
 * Screen where users can add custom keywords to block.
 * Enforces the 50 keyword limit visually and delegates to the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordSelectionScreen(
    onNavigateNext: () -> Unit,
    viewModel: KeywordSelectionViewModel = hiltViewModel()
) {
    val keywords by viewModel.keywords.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    
    var inputText by remember { mutableStateOf("") }
    val maxKeywords = viewModel.maxKeywords

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.keyword_selection_title)) }
            )
        },
        bottomBar = {
            PrimaryButton(
                label = stringResource(R.string.next),
                onClick = onNavigateNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(R.dimen.spacing_lg))
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(dimensionResource(R.dimen.spacing_lg))
        ) {
            // Input Row for adding new keywords
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { 
                        inputText = it
                        if (error != null) viewModel.clearError() 
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.keyword_input_label)) },
                    singleLine = true,
                    isError = error != null
                )
                Spacer(modifier = Modifier.width(dimensionResource(R.dimen.spacing_sm)))
                PrimaryButton(
                    label = stringResource(R.string.keyword_add),
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.addKeyword(inputText)
                            // We only clear the input field if it's not a duplicate/error
                            // But for a beginner-friendly UI, we'll clear it immediately and rely on state updates
                            inputText = ""
                        }
                    },
                    enabled = keywords.size < maxKeywords && inputText.isNotBlank()
                )
            }
            
            // Error Message Display
            error?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = dimensionResource(R.dimen.spacing_xs))
                )
            }
            
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_lg)))
            
            // Counter indicating limit
            Text(
                text = pluralStringResource(R.plurals.keyword_counter, keywords.size, keywords.size, maxKeywords),
                style = MaterialTheme.typography.bodyMedium,
                color = if (keywords.size >= maxKeywords) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_sm)))
            
            // List of currently added Keywords
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_sm))
            ) {
                items(keywords, key = { it.id }) { keyword ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = dimensionResource(R.dimen.card_elevation_low))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = dimensionResource(R.dimen.spacing_lg),
                                    vertical = dimensionResource(R.dimen.spacing_sm)
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = keyword.keyword,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            IconButton(onClick = { viewModel.removeKeyword(keyword.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.keyword_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
