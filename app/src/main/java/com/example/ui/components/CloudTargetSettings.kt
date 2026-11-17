package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.CloudCredentials
import com.example.data.model.CloudPushTarget

@Composable
fun CloudTargetSettings(
    selectedTarget: CloudPushTarget,
    credentials: CloudCredentials,
    isBusy: Boolean,
    onSelectTarget: (CloudPushTarget) -> Unit,
    onSaveCredentials: (CloudCredentials) -> Unit,
    onTestConnection: (CloudPushTarget) -> Unit,
    onLaunchDriveOAuth: (String) -> Unit
) {
    var draft by remember(credentials) { mutableStateOf(credentials) }

    LaunchedEffect(credentials) {
        draft = credentials
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "CLOUD PUSH DESTINATION",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            CloudPushTarget.entries.forEach { target ->
                FilterChip(
                    selected = selectedTarget == target,
                    onClick = { onSelectTarget(target) },
                    label = { Text(target.label, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("push_target_${target.id}")
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(12.dp)
            ) {
                when (selectedTarget) {
                    CloudPushTarget.GOOGLE_DRIVE -> {
                        Text(
                            text = "Google Drive REST v3",
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedTextField(
                            value = draft.driveClientId,
                            onValueChange = { draft = draft.copy(driveClientId = it) },
                            label = { Text("Google OAuth Client ID (PKCE)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = { onLaunchDriveOAuth(draft.driveClientId) },
                            enabled = !isBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("drive_oauth_button")
                        ) {
                            Icon(Icons.Filled.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Connect via Google OAuth")
                        }
                        OutlinedTextField(
                            value = draft.driveAccessToken,
                            onValueChange = { draft = draft.copy(driveAccessToken = it) },
                            label = { Text("Access Token (Auto-filled or Paste)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = draft.driveFolderId,
                            onValueChange = { draft = draft.copy(driveFolderId = it) },
                            label = { Text("Target Folder ID (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Upload full file (off = JSON event)", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = draft.driveUploadFileContent,
                                onCheckedChange = { draft = draft.copy(driveUploadFileContent = it) }
                            )
                        }
                    }

                    CloudPushTarget.DROPBOX -> {
                        Text(
                            text = "Dropbox Content API v2",
                            style = MaterialTheme.typography.titleMedium
                        )
                        OutlinedTextField(
                            value = draft.dropboxAccessToken,
                            onValueChange = { draft = draft.copy(dropboxAccessToken = it) },
                            label = { Text("Dropbox App Access Token") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = draft.dropboxRemoteFolder,
                            onValueChange = { draft = draft.copy(dropboxRemoteFolder = it) },
                            label = { Text("Remote Folder Path (e.g. /DroidPulse)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Upload full file (off = JSON event)", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = draft.dropboxUploadFileContent,
                                onCheckedChange = { draft = draft.copy(dropboxUploadFileContent = it) }
                            )
                        }
                    }

                    CloudPushTarget.FTP -> {
                        Text(
                            text = "FTP / FTPS Server",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = draft.ftpHost,
                                onValueChange = { draft = draft.copy(ftpHost = it) },
                                label = { Text("Host / IP") },
                                singleLine = true,
                                modifier = Modifier.weight(2f)
                            )
                            OutlinedTextField(
                                value = draft.ftpPort.toString(),
                                onValueChange = {
                                    val p = it.toIntOrNull() ?: 21
                                    draft = draft.copy(ftpPort = p)
                                },
                                label = { Text("Port") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = draft.ftpUsername,
                                onValueChange = { draft = draft.copy(ftpUsername = it) },
                                label = { Text("Username") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = draft.ftpPassword,
                                onValueChange = { draft = draft.copy(ftpPassword = it) },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = draft.ftpRemoteDir,
                            onValueChange = { draft = draft.copy(ftpRemoteDir = it) },
                            label = { Text("Remote Directory") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Passive Mode", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = draft.ftpUsePassive,
                                onCheckedChange = { draft = draft.copy(ftpUsePassive = it) }
                            )
                            Text("FTPS (TLS)", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = draft.ftpUseFtps,
                                onCheckedChange = { draft = draft.copy(ftpUseFtps = it) }
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Upload full file (off = JSON event)", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = draft.ftpUploadFileContent,
                                onCheckedChange = { draft = draft.copy(ftpUploadFileContent = it) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { onSaveCredentials(draft) },
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("save_cloud_creds_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save")
                    }
                    OutlinedButton(
                        onClick = {
                            onSaveCredentials(draft)
                            onTestConnection(selectedTarget)
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("test_cloud_conn_button")
                    ) {
                        Icon(Icons.Filled.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Test")
                    }
                }
            }
        }
    }
}
