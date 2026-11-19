package org.onedroid.radiowave.presentation.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.onedroid.radiowave.app.theme.Shapes
import org.onedroid.radiowave.app.theme.compactFeedWidth
import org.onedroid.radiowave.app.theme.extraSmall
import org.onedroid.radiowave.app.theme.medium
import org.onedroid.radiowave.app.theme.small
import org.onedroid.radiowave.app.utils.shareLink
import org.onedroid.radiowave.domain.Radio
import org.onedroid.radiowave.presentation.home.components.ErrorMsgView
import org.onedroid.radiowave.presentation.home.components.Feed
import org.onedroid.radiowave.presentation.home.components.FeedTitle
import org.onedroid.radiowave.presentation.home.components.FullScreenDialog
import org.onedroid.radiowave.presentation.home.components.HomeTopAppBar
import org.onedroid.radiowave.presentation.home.components.RadioGridItem
import org.onedroid.radiowave.presentation.home.components.RadioHorizontalGridItem
import org.onedroid.radiowave.presentation.home.components.RadioSearchResult
import org.onedroid.radiowave.presentation.home.components.row
import org.onedroid.radiowave.presentation.home.components.single
import org.onedroid.radiowave.presentation.home.components.title
import radiowave.composeapp.generated.resources.Res
import radiowave.composeapp.generated.resources.about_app
import radiowave.composeapp.generated.resources.app_name
import radiowave.composeapp.generated.resources.contact_email
import radiowave.composeapp.generated.resources.contact_label
import radiowave.composeapp.generated.resources.developer
import radiowave.composeapp.generated.resources.github_label
import radiowave.composeapp.generated.resources.github_url
import radiowave.composeapp.generated.resources.ic_launcher
import radiowave.composeapp.generated.resources.last_updated_date
import radiowave.composeapp.generated.resources.last_updated_label
import radiowave.composeapp.generated.resources.license_label
import radiowave.composeapp.generated.resources.license_type
import radiowave.composeapp.generated.resources.maintainer
import radiowave.composeapp.generated.resources.recently_updated
import radiowave.composeapp.generated.resources.version_label
import radiowave.composeapp.generated.resources.version_number
import radiowave.composeapp.generated.resources.webpage_label
import radiowave.composeapp.generated.resources.webpage_url

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    rootNavController: NavController,
    viewModel: HomeViewModel = koinViewModel()
) {
    val gridState = rememberLazyGridState()
    val activeStationScope = if (viewModel.showFavoritesOnly) viewModel.savedRadios else viewModel.filteredRadios
    val uniqueRadios = viewModel.filteredRadios.distinctBy { it.id }
    val radiosSize = uniqueRadios.size
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val bottomSheetState = rememberBottomSheetScaffoldState()
    val scope = rememberCoroutineScope()
    val urlHandler = LocalUriHandler.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val deviceConfiguration = LocalConfiguration.current
    val useWideRadioLayout = deviceConfiguration.orientation == Configuration.ORIENTATION_LANDSCAPE && deviceConfiguration.screenWidthDp >= 700

    // The initial catalog load happens in ViewModel init. Skip the first
    // ON_RESUME event to avoid duplicate startup requests, then refresh every
    // time the app returns to the foreground.
    DisposableEffect(lifecycleOwner, viewModel) {
        var hasResumedOnce = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (hasResumedOnce) viewModel.refreshStations()
                else hasResumedOnce = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(activeStationScope) {
        viewModel.playerRepository.updateStationQueue(activeStationScope, viewModel.selectedRadio?.id)
    }

    // Open the Bookingwep radio console as the app's landing screen.
    LaunchedEffect(viewModel.selectedRadio?.id) {
        if (viewModel.selectedRadio != null) bottomSheetState.bottomSheetState.expand()
    }

    BottomSheetScaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        scaffoldState = bottomSheetState,
        sheetContainerColor = Color(0xFF0B1728),
        sheetMaxWidth = if (useWideRadioLayout) Dp.Unspecified else BottomSheetDefaults.SheetMaxWidth,
        sheetContentColor = Color(0xFFF5F0E6),
        sheetSwipeEnabled = false,
        containerColor = Color(0xFF081321),
        sheetContent = {
            viewModel.selectedRadio?.let {
                viewModel.playerRepository.PLayerUI(
                    radio = it,
                    stations = activeStationScope,
                    savedStations = viewModel.savedRadios,
                    categories = viewModel.categories,
                    selectedCategory = viewModel.selectedCategory,
                    isSaved = viewModel.isSaved,
                    isTuning = viewModel.isTuning,
                    playerMessage = viewModel.playerMessage,
                    isAutoSearch = viewModel.isAutoSearch,
                    onCategoryClick = viewModel::selectCategory,
                    onFavoritesClick = viewModel::toggleFavoritesOnly,
                    onStationClick = viewModel::selectedRadio,
                    onStationFavoriteClick = { viewModel.toggleSavedRadio(it) },
                    onPreviousClick = viewModel::playPrevious,
                    onNextClick = viewModel::playNext,
                    onAutoSearchClick = viewModel::toggleAutoSearch,
                    onWebpageClick = {
                        it.homepage?.let { it1 -> urlHandler.openUri(it1) }
                    },
                    onShareClick = {
                        shareLink(it.playableUrl)
                    },
                    onSaveClick = {
                        viewModel.saveRadio(it)
                    },
                    onRetryClick = viewModel::retrySelectedRadio,
                    onCloseClick = {
                        viewModel.closePlayer()
                        scope.launch { bottomSheetState.bottomSheetState.hide() }
                    }
                )
            }
        },
        sheetPeekHeight = if (viewModel.selectedRadio != null) BottomSheetDefaults.SheetPeekHeight else 0.dp,
        topBar = { }
    ) {
        // Keep the original catalogue UI out of the first frame. The radio
        // console is the app's landing screen; show only a dark loading surface
        // until the station catalogue selects its initial station.
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF081321)),
            contentAlignment = Alignment.Center
        ) {
            if (viewModel.selectedRadio == null) {
                CircularProgressIndicator(color = Color(0xFFC5A059))
            }
        }
    }

    if (viewModel.isAboutDialogShowing) {
        ShowAboutDialog(
            onDismiss = { viewModel.toggleAboutDialog() }
        )
    }

}

@Composable
private fun ShowAboutDialog(
    onDismiss: () -> Unit,
) {
    FullScreenDialog(
        onDismissRequest = { onDismiss() },
        modifier = Modifier.padding(16.dp).clip(Shapes.medium),
        actions = {
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(onClick = { onDismiss() }) {
                    Text("Close")
                }
            }
        },
        content = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Image(
                    modifier = Modifier.size(100.dp).padding(bottom = medium),
                    painter = painterResource(Res.drawable.ic_launcher),
                    contentDescription = "App Icon"
                )
                Text(
                    style = MaterialTheme.typography.titleLarge,
                    text = stringResource(Res.string.app_name)
                )
                Text(
                    style = MaterialTheme.typography.bodyMedium,
                    text = stringResource(Res.string.developer)
                )
                Text(
                    modifier = Modifier.padding(bottom = small),
                    style = MaterialTheme.typography.bodyMedium,
                    text = stringResource(Res.string.maintainer)
                )
                Spacer(
                    modifier = Modifier.height(2.dp).fillMaxWidth(0.8f)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    modifier = Modifier.padding(vertical = medium),
                    style = MaterialTheme.typography.bodyMedium,
                    text = stringResource(Res.string.about_app)
                )

                Text(
                    style = MaterialTheme.typography.labelLarge,
                    text = stringResource(Res.string.last_updated_label)
                )
                Text(
                    style = MaterialTheme.typography.bodyMedium,
                    text = stringResource(Res.string.last_updated_date)
                )

                Text(
                    modifier = Modifier.padding(top = medium),
                    style = MaterialTheme.typography.labelLarge,
                    text = stringResource(Res.string.contact_label)
                )
                Text(
                    style = MaterialTheme.typography.bodyMedium,
                    text = stringResource(Res.string.contact_email)
                )

                Text(
                    modifier = Modifier.padding(top = medium),
                    style = MaterialTheme.typography.labelLarge,
                    text = stringResource(Res.string.webpage_label)
                )
                Text(
                    style = MaterialTheme.typography.bodyMedium,
                    text = stringResource(Res.string.webpage_url)
                )

                Text(
                    modifier = Modifier.padding(top = medium),
                    style = MaterialTheme.typography.labelLarge,
                    text = stringResource(Res.string.github_label)
                )
                Text(
                    style = MaterialTheme.typography.bodyMedium,
                    text = stringResource(Res.string.github_url)
                )

                Text(
                    modifier = Modifier.padding(top = medium),
                    style = MaterialTheme.typography.labelLarge,
                    text = stringResource(Res.string.license_label)
                )
                Text(
                    style = MaterialTheme.typography.bodyMedium,
                    text = stringResource(Res.string.license_type)
                )

                Text(
                    modifier = Modifier.padding(top = medium),
                    style = MaterialTheme.typography.labelLarge,
                    text = stringResource(Res.string.version_label)
                )
                Text(
                    style = MaterialTheme.typography.bodyMedium,
                    text = stringResource(Res.string.version_number)
                )
            }
        }
    )
}