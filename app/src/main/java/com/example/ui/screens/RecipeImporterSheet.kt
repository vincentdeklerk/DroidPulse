package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.plugins.recipes.PluginRecipeImporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeImporterSheet(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onImportUrl: (String) -> Unit,
    onImportJson: (String) -> Unit
) {
    BackHandler(onBack = onDismiss)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var urlInput by remember { mutableStateOf("") }
    var jsonInput by remember { mutableStateOf(PluginRecipeImporter.SAMPLE_DISCORD_VOIP_RECIPE) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Import JSON Plugin Recipe",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Hot-load VoIP package matchers or folder webhook rules safely without executable code.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close importer")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Text(
                text = "LOAD FROM GITHUB RAW OR SERVER URL",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("https://raw.githubusercontent.com/.../recipe.json") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recipe_url_input")
            )
            Button(
                onClick = { onImportUrl(urlInput) },
                enabled = !isBusy && urlInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("fetch_recipe_url_button")
            ) {
                Text("Fetch & Install Recipe")
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Text(
                text = "QUICK TEMPLATES OR PASTE JSON",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { jsonInput = PluginRecipeImporter.SAMPLE_DISCORD_VOIP_RECIPE },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Text("Discord VoIP")
                }
                OutlinedButton(
                    onClick = { jsonInput = PluginRecipeImporter.SAMPLE_WEBHOOK_RULE_RECIPE },
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                ) {
                    Text("PDF Webhook")
                }
            }

            OutlinedTextField(
                value = jsonInput,
                onValueChange = { jsonInput = it },
                label = { Text("Recipe JSON") },
                textStyle = MaterialTheme.typography.bodySmall,
                minLines = 6,
                maxLines = 12,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recipe_json_input")
            )

            Button(
                onClick = {
                    onImportJson(jsonInput)
                    onDismiss()
                },
                enabled = !isBusy && jsonInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("install_recipe_json_button")
            ) {
                Spacer(Modifier.width(4.dp))
                Text("Install JSON Recipe")
            }
        }
    }
}
