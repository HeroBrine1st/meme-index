package ru.herobrine1st.memeindex.ui.screen

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import ru.herobrine1st.memeindex.decompose.component.SearchComponent
import ru.herobrine1st.memeindex.decompose.component.SearchComponent.SearchRequestState
import ru.herobrine1st.memeindex.resources.Res
import ru.herobrine1st.memeindex.resources.action_copy_text
import ru.herobrine1st.memeindex.resources.action_copy_url
import ru.herobrine1st.memeindex.resources.action_share
import ru.herobrine1st.memeindex.resources.copy_text_success
import ru.herobrine1st.memeindex.resources.copy_url_success
import ru.herobrine1st.memeindex.resources.error
import ru.herobrine1st.memeindex.resources.search_full_text
import ru.herobrine1st.memeindex.resources.search_placeholder
import ru.herobrine1st.memeindex.resources.search_vector_image
import ru.herobrine1st.memeindex.resources.search_vector_text
import ru.herobrine1st.memeindex.setText
import ru.herobrine1st.memeindex.shared.model.server.endpoint.Search
import ru.herobrine1st.memeindex.shared.model.server.endpoint.Search.Response.ResultType

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun SearchScreen(component: SearchComponent) {
    val scope = rememberCoroutineScope()

    var lastSnackbarJob by remember { mutableStateOf<Job?>(null) } // not used as state but is mutated
    val snackbarHostState = remember { SnackbarHostState() }
    var expanded by rememberSaveable { mutableStateOf(false) }
    // TODO: the query text is not preserved across recreation (textFieldState is not saveable; only `expanded` is)
    val textFieldState = rememberTextFieldState()
    var fullTextSearch by remember { mutableStateOf(true) }
    var vectorTextSearch by remember { mutableStateOf(true) }
    var vectorImageSearch by remember { mutableStateOf(true) }
    val currentQuery by remember {
        derivedStateOf {
            Search.Request(
                query = textFieldState.text.toString().trim(),
                fullText = fullTextSearch,
                vectorText = vectorTextSearch,
                vectorImage = vectorImageSearch,
            )
        }
    }
    val searchState by remember { component.getResultsFlow { currentQuery } }
        .collectAsState(
            SearchRequestState(
                isLoading = false,
                isLastError = false,
                query = currentQuery,
                lastSuccess = null,
            ),
        )
    val barHorizontalPadding by animateDpAsState(
        if (expanded) 0.dp else 4.dp,
        animationSpec = if (expanded) tween(
            durationMillis = 600,
            delayMillis = 100,
            easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f),
        ) else tween(
            durationMillis = 350,
            delayMillis = 100,
            easing = CubicBezierEasing(0.0f, 1.0f, 0.0f, 1.0f),
        ),
    )

    Scaffold(
        topBar = {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = textFieldState.text.toString(),
                        onQueryChange = {
                            textFieldState.edit { replace(0, length, it) }
                        },
                        onSearch = { expanded = false },
                        expanded = expanded,
                        onExpandedChange = {
                            if (!it) expanded = false
                        },
                        modifier = Modifier.padding(4.dp - barHorizontalPadding),
                        placeholder = { Text(stringResource(Res.string.search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { expanded = !expanded }) {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.rotate(animateFloatAsState(if (expanded) 180f else 0f).value),
                                )
                            }
                        },
                    )
                },
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = barHorizontalPadding,
                ),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = fullTextSearch,
                                onValueChange = { fullTextSearch = it },
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(Res.string.search_full_text))
                        Switch(
                            checked = fullTextSearch,
                            onCheckedChange = null,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = vectorTextSearch,
                                onValueChange = { vectorTextSearch = it },
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(Res.string.search_vector_text))
                        Switch(
                            checked = vectorTextSearch,
                            onCheckedChange = null,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = vectorImageSearch,
                                onValueChange = { vectorImageSearch = it },
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(Res.string.search_vector_image))
                        Switch(
                            checked = vectorImageSearch,
                            onCheckedChange = null,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
    ) { paddingValues ->
        val state: PullToRefreshState = rememberPullToRefreshState()

        Box(
            Modifier.pullToRefresh(
                state = state,
                isRefreshing = searchState.isLoading,
                onRefresh = {},
                enabled = false,
            ),
        ) {
            LazyVerticalStaggeredGrid(
                contentPadding = paddingValues,
                columns = StaggeredGridCells.Adaptive(180.dp),
                verticalItemSpacing = 4.dp,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (searchState.isLastError) item(span = StaggeredGridItemSpan.FullLine) {
                    Text(stringResource(Res.string.error))
                    Icon(Icons.Default.Error, contentDescription = null)
                }

                val lastItems = searchState.lastSuccess?.result
                if (lastItems != null) items(
                    lastItems,
                    span = {
                        if (it.type == ResultType.Text) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane
                    },
                ) { item ->
                    var menuExpanded by remember { mutableStateOf(false) }
                    val uriHandler = LocalUriHandler.current
                    val clipboard = LocalClipboard.current

                    val copyToClipboard = {
                        clipboard.setText(item.text ?: "No text provided", "Item text")
                        lastSnackbarJob?.cancel()
                        lastSnackbarJob = scope.launch {
                            snackbarHostState.showSnackbar(getString(Res.string.copy_text_success))
                        }
                    }
                    val openUrl = {
                        uriHandler.openUri(item.url.toString())
                    }

                    ElevatedCard(
                        modifier = Modifier.combinedClickable(
                            onClick = if (item.type == ResultType.Text) copyToClipboard else openUrl,
                            onLongClick = { menuExpanded = true },
                        ),
                    ) {
                        Box {
                            var aspectRatio by remember { mutableFloatStateOf(1f) }
                            if (item.type == ResultType.Text) {
                                Text(item.text ?: "No text provided", modifier = Modifier.padding(4.dp))
                            } else AsyncImage(
                                model = ImageRequest.Builder(LocalPlatformContext.current)
                                    .data(item.url.toString())
                                    .build(),
                                contentDescription = item.text,
                                modifier = Modifier.fillMaxSize().aspectRatio(aspectRatio),
                                onSuccess = {
                                    aspectRatio = it.result.image.run { width.toFloat() / height.toFloat() }
                                },
                            )

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                if (item.text != null) DropdownMenuItem(
                                    text = {
                                        Text(stringResource(Res.string.action_copy_text))
                                    },
                                    onClick = {
                                        clipboard.setText(item.text!!, "Item text")
                                        lastSnackbarJob?.cancel()
                                        lastSnackbarJob = scope.launch {
                                            snackbarHostState.showSnackbar(getString(Res.string.copy_text_success))
                                        }
                                    },
                                )
                                if (item.type != ResultType.Text) DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.action_copy_url)) },
                                    onClick = {
                                        clipboard.setText(item.url.toString(), "URL")
                                        lastSnackbarJob?.cancel()
                                        lastSnackbarJob = scope.launch {
                                            snackbarHostState.showSnackbar(getString(Res.string.copy_url_success))
                                        }
                                        menuExpanded = false
                                    },
                                )
                                if (component.isSharingSupported) DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.action_share)) },
                                    onClick = {
                                        component.shareItem(item) {
                                            // TODO on failure
                                        }
                                        menuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Indicator(
                modifier = Modifier.align(Alignment.TopCenter).padding(paddingValues),
                isRefreshing = searchState.isLoading,
                state = state,
            )
        }
    }
}

