package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.db.PluginConfigEntity
import com.example.data.model.PluginType
import com.example.data.model.RecipeType
import com.example.ui.ConsoleState
import com.example.ui.components.PluginControlCard
import com.example.ui.components.SystemHealthStrip

@Composable
fun ConsoleScreen(
    state: ConsoleState,
    computeRuntimeState: (PluginConfigEntity) -> com.example.data.model.PluginRuntimeState,
    onTogglePlugin: (PluginType, Boolean) -> Unit,
    onOpenPluginConfig: (PluginType) -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenRecipeImporter: () -> Unit,
    onToggleRecipe: (String, Boolean) -> Unit,
    onDeleteRecipe: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val orderedTypes = listOf(
        PluginType.CALL_LOGGER,
        PluginType.NETWORK_MONITOR,
        PluginType.GPS_LOGGER,
        PluginType.FOLDER_WATCHER
    )
    val configMap = state.configs.associateBy { it.pluginId }
    val armedCount = state.configs.count { it.enabled }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Column {
                Text(
                    text = "DroidPulse Admin",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Set-and-forget micro-plugin telemetry switchboard",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SystemHealthStrip(
                health = state.health,
                armedCount = armedCount,
                pendingPushCount = state.pendingPushCount,
                darkMode = state.darkMode,
                onRequestBatteryExemption = onRequestBatteryExemption,
                onToggleTheme = onToggleTheme
            )
        }

        item {
            Text(
                text = "CORE MICRO-PLUGINS (TAP TO CONFIGURE)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        items(orderedTypes, key = { it.id }) { type ->
            val cfg = configMap[type.id] ?: PluginConfigEntity(pluginId = type.id)
            val count = when (type) {
                PluginType.CALL_LOGGER -> state.callCount
                PluginType.NETWORK_MONITOR -> state.networkCount
                PluginType.GPS_LOGGER -> state.locationCount
                PluginType.FOLDER_WATCHER -> state.watchedFilesCount
            }
            PluginControlCard(
                config = cfg,
                recordCount = count,
                runtimeState = computeRuntimeState(cfg),
                onToggle = { enabled -> onTogglePlugin(type, enabled) },
                onOpenConfig = { onOpenPluginConfig(type) }
            )
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HOT-LOADED JSON RECIPES",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Extend VoIP matchers & folder webhook rules from GitHub/URL",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onOpenRecipeImporter,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("import_recipe_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Import", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (state.recipes.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "No JSON recipes loaded yet. New OS sensors arrive via APK updates; extra VoIP packages or webhooks load here without updating.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(state.recipes, key = { it.recipeId }) { recipe ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = recipe.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = RecipeType.fromId(recipe.recipeType).label +
                                    if (recipe.sourceUrl.isNotBlank()) " • ${recipe.sourceUrl}" else " • Local JSON",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = recipe.enabled,
                            onCheckedChange = { onToggleRecipe(recipe.recipeId, it) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        )
                        IconButton(
                            onClick = { onDeleteRecipe(recipe.recipeId) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete recipe ${recipe.name}",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}
