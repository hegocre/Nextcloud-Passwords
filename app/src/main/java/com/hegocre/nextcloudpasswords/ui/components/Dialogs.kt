package com.hegocre.nextcloudpasswords.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.ui.theme.ContentAlpha
import com.hegocre.nextcloudpasswords.ui.theme.NextcloudPasswordsTheme

@Composable
fun MasterPasswordDialog(
    masterPassword: String,
    setMasterPassword: (String) -> Unit,
    savePassword: Boolean,
    setSavePassword: (Boolean) -> Unit,
    onOkClick: () -> Unit,
    errorText: String = "",
    onDismissRequest: (() -> Unit)? = null
) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    Dialog(
        onDismissRequest = { onDismissRequest?.invoke() },
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = contentColorFor(backgroundColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                OutlinedTextFieldWithCaption(
                    text = masterPassword,
                    onValueChange = setMasterPassword,
                    visualTransformation = if (showPassword)
                        VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardType = KeyboardType.Password,
                    label = stringResource(R.string.enter_master_password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword)
                                    Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(R.string.show_password)
                            )
                        }
                    },
                    errorText = errorText
                )

                CompositionLocalProvider(
                    LocalContentColor provides LocalContentColor.current.copy(alpha = ContentAlpha.medium)
                ) {
                    Row {
                        Checkbox(
                            checked = savePassword,
                            onCheckedChange = setSavePassword,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        Text(
                            text = "Save password",
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        setSavePassword(!savePassword)
                                    }
                                },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                TextButton(
                    onClick = onOkClick,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        }
    }
}

@Composable
fun LogOutDialog(
    onDismissRequest: (() -> Unit)? = null,
    onConfirmButton: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDismissRequest?.invoke() },
        title = { Text(text = stringResource(R.string.log_out)) },
        text = { Text(text = stringResource(R.string.are_you_sure_log_out)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmButton()
                    onDismissRequest?.invoke()
                }
            ) {
                Text(text = stringResource(R.string.log_out))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismissRequest?.invoke() }) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        }
    )
}

@Composable
fun DeleteElementDialog(
    onDismissRequest: (() -> Unit)? = null,
    onConfirmButton: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDismissRequest?.invoke() },
        title = { Text(text = stringResource(R.string.delete)) },
        text = { Text(text = stringResource(R.string.are_you_sure_delete_element)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmButton()
                    onDismissRequest?.invoke()
                }
            ) {
                Text(text = stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismissRequest?.invoke() }) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
        }
    )
}

@Preview
@Composable
fun MasterPasswordDialogPreview() {
    NextcloudPasswordsTheme {
        MasterPasswordDialog(
            masterPassword = "",
            setMasterPassword = {},
            savePassword = false,
            setSavePassword = {},
            onOkClick = {}
        )
    }
}

@Preview
@Composable
fun LogOutDialogPreview() {
    NextcloudPasswordsTheme {
        LogOutDialog {

        }
    }
}

@Preview
@Composable
fun DeleteDialogPreview() {
    NextcloudPasswordsTheme {
        DeleteElementDialog {

        }
    }
}