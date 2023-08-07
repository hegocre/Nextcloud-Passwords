package com.hegocre.nextcloudpasswords.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.hegocre.nextcloudpasswords.R
import com.hegocre.nextcloudpasswords.data.folder.Folder
import com.hegocre.nextcloudpasswords.data.password.Password
import com.hegocre.nextcloudpasswords.ui.theme.*
import com.hegocre.nextcloudpasswords.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers

data class ListDecryptionState<T>(
    val decryptedList: List<T>? = null,
    val isLoading: Boolean = false
)

@Suppress("deprecation")
@Composable
fun RefreshListBody(
    isRefreshing: Boolean,
    onRefresh: () -> Unit = {},
    indicatorPadding: PaddingValues = PaddingValues(all = 0.dp),
    content: @Composable () -> Unit = {}
) {
    SwipeRefresh(
        state = rememberSwipeRefreshState(isRefreshing = isRefreshing),
        onRefresh = onRefresh,
        indicator = { rState, refreshTrigger ->
            SwipeRefreshIndicator(
                state = rState,
                refreshTriggerDistance = refreshTrigger,
                contentColor = MaterialTheme.colorScheme.primary,
                backgroundColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
            )
        },
        indicatorPadding = indicatorPadding
    ) {
        content()
    }
}

@Composable
fun MixedLazyColumn(
    passwords: List<Password>? = null,
    folders: List<Folder>? = null,
    onPasswordClick: ((Password) -> Unit)? = null,
    onFolderClick: ((Folder) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(all = 0.dp)
) {
    val context = LocalContext.current
    val shouldShowIcon by PreferencesManager.getInstance(context).getShowIcons()
        .collectAsState(initial = false, context = Dispatchers.IO)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        folders?.let {
            items(items = it, key = { folder -> folder.id }) { folder ->
                FolderRow(
                    folder = folder,
                    onFolderClick = onFolderClick
                )
            }
        }
        passwords?.let {
            items(items = it, key = { password -> password.id }) { folder ->
                PasswordRow(
                    password = folder,
                    shouldShowIcon = shouldShowIcon,
                    onPasswordClick = onPasswordClick
                )
            }
        }
    }
}

@Composable
fun PasswordRow(
    password: Password,
    modifier: Modifier = Modifier,
    shouldShowIcon: Boolean = false,
    onPasswordClick: ((Password) -> Unit)? = null
) {
    ListItem(
        modifier = modifier.clickable { onPasswordClick?.invoke(password) },
        headlineContent = {
            Text(
                text = password.label,
            )
        },
        supportingContent = if (password.username.isNotBlank()) {
            {
                Text(
                    text = password.username,
                )
            }
        } else null,
        trailingContent = if (password.status != 3) {
            {
                Icon(
                    imageVector = Icons.TwoTone.Security,
                    contentDescription = "Security",
                    modifier = Modifier
                        .size(40.dp)
                        .padding(all = 8.dp),
                    tint = (if (MaterialTheme.colorScheme.isLight()) {
                        when (password.status) {
                            0 -> Green500
                            1 -> Amber500
                            2 -> Red500
                            else -> Color.Unspecified
                        }
                    } else {
                        when (password.status) {
                            0 -> Green200
                            1 -> Amber200
                            2 -> Red200
                            else -> Color.Unspecified
                        }
                    })
                )
            }
        } else null,
        leadingContent = if (shouldShowIcon) {
            {
                val context = LocalContext.current
                val imageBitmap by password.faviconBitmap.collectAsState()
                if (imageBitmap == null && !password.isFaviconLoading) {
                    LaunchedEffect(Unit) {
                        password.loadFavicon(context)
                    }
                }
                Crossfade(
                    targetState = imageBitmap,
                    animationSpec = tween(250),
                    label = "Icon"
                ) { image ->
                    if (image == null) {
                        Image(
                            modifier = Modifier
                                .size(45.dp)
                                .padding(8.dp),
                            imageVector = Icons.TwoTone.Lock,
                            contentDescription = stringResource(R.string.site_favicon),
                            colorFilter = ColorFilter.tint(
                                MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = ContentAlpha.medium
                                )
                            )
                        )
                    } else {
                        imageBitmap?.let {
                            Image(
                                modifier = Modifier
                                    .size(45.dp)
                                    .padding(all = 8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                bitmap = it,
                                contentDescription = stringResource(R.string.site_favicon)
                            )
                        }
                    }
                }
            }
        } else null,

        )
}

@Composable
fun FolderRow(
    folder: Folder,
    modifier: Modifier = Modifier,
    onFolderClick: ((Folder) -> Unit)? = null
) {
    ListItem(
        leadingContent = {
            Image(
                imageVector = Icons.Filled.Folder,
                contentDescription = stringResource(R.string.site_favicon),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.medium)),
                modifier = Modifier
                    .size(45.dp)
                    .padding(8.dp)
            )
        },
        headlineContent = {
            Text(text = folder.label)
        },
        modifier = modifier.clickable { onFolderClick?.invoke(folder) }
    )
}