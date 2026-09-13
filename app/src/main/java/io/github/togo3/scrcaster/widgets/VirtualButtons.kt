package io.github.togo3.scrcaster.widgets

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.togo3.scrcaster.constants.UiSpacing
import io.github.togo3.scrcaster.storage.Storage.appSettings
import io.github.togo3.scrcaster.ui.confirm
import io.github.togo3.scrcaster.ui.contextClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import kotlin.ranges.coerceAtLeast

class VirtualButtonBar(
    private val outsideActions: List<VirtualButtonAction>,
    private val moreActions: List<VirtualButtonAction>,
) {
    enum class FullscreenDock {
        TOP,
        BOTTOM,
        LEFT,
        RIGHT,
    }

    private enum class ActionPopupDestination {
        Actions,
        Passwords,
    }

    @Composable
    fun Preview(
        enabled: Boolean,
        showText: Boolean,
        onAction: (VirtualButtonAction) -> Unit,
        modifier: Modifier = Modifier,
        passwordPopupContent: (@Composable (onDismissRequest: () -> Unit) -> Unit)? = null,
        popupBottomPadding: Dp = 0.dp,
    ) {
        val haptic = LocalHapticFeedback.current

        val activeContainerColor = colorScheme.primary
        val disabledContainerColor = colorScheme.primary.copy(alpha = 0.35f)
        val activeContentColor = colorScheme.onPrimary
        val disabledContentColor = colorScheme.onPrimary.copy(alpha = 0.45f)

        var showMorePopup by remember { mutableStateOf(false) }

        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.Medium),
        ) {
            outsideActions.forEach { action ->
                var showPasswordPopup by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = {
                            haptic.contextClick()
                            when (action) {
                                VirtualButtonAction.MORE -> {
                                    showMorePopup = true
                                }

                                VirtualButtonAction.PASSWORD_INPUT
                                    if passwordPopupContent != null -> {
                                    showPasswordPopup = true
                                }

                                else -> onAction(action)
                            }
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            color = activeContainerColor,
                            disabledColor = disabledContainerColor,
                        ),
                        insideMargin = PaddingValues(0.dp),
                    ) {
                        val contentColor =
                            if (enabled) activeContentColor
                            else disabledContentColor
                        Icon(
                            imageVector = action.icon,
                            contentDescription = stringResource(action.titleResId),
                            modifier = Modifier.size(18.dp),
                            tint = contentColor,
                        )
                        if (showText) {
                            Spacer(Modifier.width(UiSpacing.Small))
                            Text(stringResource(action.titleResId), color = contentColor)
                        }
                    }
                    if (action == VirtualButtonAction.MORE) {
                        ActionPopup(
                            show = showMorePopup,
                            actions = moreActions,
                            onDismiss = { showMorePopup = false },
                            onAction = {
                                onAction(it)
                                showMorePopup = false
                            },
                            passwordPopupContent = passwordPopupContent,
                            renderInRootScaffold = false,
                            popupBottomPadding = popupBottomPadding,
                        )
                    }
                    if (
                        action == VirtualButtonAction.PASSWORD_INPUT &&
                        passwordPopupContent != null
                    ) {
                        OverlayListPopup(
                            show = showPasswordPopup,
                            popupPositionProvider =
                                rememberBottomSafeContextMenuPositionProvider(popupBottomPadding),
                            alignment = PopupPositionProvider.Align.TopEnd,
                            onDismissRequest = { showPasswordPopup = false },
                            renderInRootScaffold = false,
                            enableWindowDim = false,
                        ) {
                            passwordPopupContent { showPasswordPopup = false }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun Fullscreen(
        onAction: suspend (VirtualButtonAction) -> Unit,
        modifier: Modifier = Modifier,
        dock: FullscreenDock = FullscreenDock.BOTTOM,
        reverseOrder: Boolean = false,
        thickness: Dp = 16.dp,
        passwordPopupContent: (@Composable (onDismissRequest: () -> Unit) -> Unit)? = null,
    ) {
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        var showMorePopup by remember { mutableStateOf(false) }
        var showPasswordPopup by remember { mutableStateOf(false) }

        val isVertical = dock == FullscreenDock.LEFT || dock == FullscreenDock.RIGHT
        val visibleActions =
            if (reverseOrder) outsideActions.asReversed()
            else outsideActions
        val containerModifier =
            if (isVertical) modifier
                .width(thickness)
                .fillMaxHeight()
            else modifier
                .fillMaxWidth()
                .height(thickness)

        val buttonModifier =
            if (isVertical) Modifier
                .fillMaxSize()
            else Modifier
                .fillMaxWidth()
                .height(thickness)

        if (isVertical) Column(
            modifier = containerModifier,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            visibleActions.forEach { action ->
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = {
                            haptic.contextClick()
                            when (action) {
                                VirtualButtonAction.MORE -> {
                                    showMorePopup = true
                                }

                                VirtualButtonAction.PASSWORD_INPUT
                                    if passwordPopupContent != null -> {
                                    showPasswordPopup = true
                                }

                                else -> scope.launch { onAction(action) }
                            }
                        },
                        modifier = buttonModifier,
                        cornerRadius = 0.dp,
                        minHeight = thickness,
                        insideMargin = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            color = Color.Black.copy(alpha = 0.1f),
                        ),
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = stringResource(action.titleResId),
                            tint = Color.White,
                        )
                    }

                    if (action == VirtualButtonAction.MORE) {
                        ActionPopup(
                            show = showMorePopup,
                            actions = moreActions,
                            onDismiss = { showMorePopup = false },
                            onAction = {
                                if (it == VirtualButtonAction.PASSWORD_INPUT
                                    && passwordPopupContent != null
                                ) showPasswordPopup = true
                                else onAction(it)

                                showMorePopup = false
                            },
                            passwordPopupContent = passwordPopupContent,
                            renderInRootScaffold = true,
                        )
                    }
                }
            }
        }
        else Row(
            modifier = containerModifier,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            visibleActions.forEach { action ->
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = {
                            haptic.contextClick()
                            when (action) {
                                VirtualButtonAction.MORE -> {
                                    showMorePopup = true
                                }

                                VirtualButtonAction.PASSWORD_INPUT
                                    if passwordPopupContent != null -> {
                                    showPasswordPopup = true
                                }

                                else -> scope.launch { onAction(action) }
                            }
                        },
                        modifier = buttonModifier,
                        cornerRadius = 0.dp,
                        minHeight = thickness,
                        insideMargin = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            color = Color.Black.copy(alpha = 0.1f),
                        ),
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = stringResource(action.titleResId),
                            tint = Color.White,
                        )
                    }

                    if (action == VirtualButtonAction.MORE) {
                        ActionPopup(
                            show = showMorePopup,
                            actions = moreActions,
                            onDismiss = { showMorePopup = false },
                            onAction = {
                                if (it == VirtualButtonAction.PASSWORD_INPUT
                                    && passwordPopupContent != null
                                ) showPasswordPopup = true
                                else onAction(it)

                                showMorePopup = false
                            },
                            passwordPopupContent = passwordPopupContent,
                            renderInRootScaffold = true,
                        )
                    }
                }
            }
        }

        if (passwordPopupContent != null) {
            OverlayListPopup(
                show = showPasswordPopup,
                popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                alignment = PopupPositionProvider.Align.TopEnd,
                onDismissRequest = { showPasswordPopup = false },
                renderInRootScaffold = true,
                enableWindowDim = false,
            ) {
                passwordPopupContent { showPasswordPopup = false }
            }
        }
    }

    @Composable
    fun FloatingBall(
        actions: List<VirtualButtonAction>,
        onAction: suspend (VirtualButtonAction) -> Unit,
        modifier: Modifier = Modifier,
        passwordPopupContent: (@Composable (onDismissRequest: () -> Unit) -> Unit)? = null,
    ) {
        val scope = rememberCoroutineScope()
        val taskScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
        val haptic = LocalHapticFeedback.current
        var showActions by remember { mutableStateOf(false) }
        var showPasswordPopup by remember { mutableStateOf(false) }
        val asBundleShared by appSettings.bundleState.collectAsState()
        val asBundleSharedLatest by rememberUpdatedState(asBundleShared)
        var offsetXFraction by rememberSaveable(asBundleShared.fullscreenFloatingButtonXFraction) {
            mutableFloatStateOf(asBundleShared.fullscreenFloatingButtonXFraction)
        }
        var offsetYFraction by rememberSaveable(asBundleShared.fullscreenFloatingButtonYFraction) {
            mutableFloatStateOf(asBundleShared.fullscreenFloatingButtonYFraction)
        }
        DisposableEffect(Unit) {
            onDispose {
                taskScope.launch {
                    val latest = asBundleSharedLatest
                    if (
                        offsetXFraction != latest.fullscreenFloatingButtonXFraction ||
                        offsetYFraction != latest.fullscreenFloatingButtonYFraction
                    ) {
                        appSettings.saveBundle(
                            latest.copy(
                                fullscreenFloatingButtonXFraction = offsetXFraction,
                                fullscreenFloatingButtonYFraction = offsetYFraction,
                            ),
                        )
                    }
                }.invokeOnCompletion { taskScope.cancel() }
            }
        }

        BoxWithConstraints(
            modifier = modifier.fillMaxSize(),
        ) {
            val ballSize = asBundleShared.fullscreenFloatingButtonSizeDp.dp
            val ringSize = ballSize / 2
            val ringWidth = ballSize / 24
            val backgroundAlpha =
                (asBundleShared.fullscreenFloatingButtonBackgroundAlphaPercent / 100f)
                    .coerceIn(0.1f, 1f)
            val ringAlpha =
                (asBundleShared.fullscreenFloatingButtonRingAlphaPercent / 100f)
                    .coerceIn(0f, 1f)
            val maxX = (maxWidth - ballSize).coerceAtLeast(0.dp)
            val maxY = (maxHeight - ballSize).coerceAtLeast(0.dp)
            val currentX =
                maxX * offsetXFraction.coerceIn(0f, 1f)
            val currentY =
                maxY * offsetYFraction.coerceIn(0f, 1f)
            val popupAlignment =
                if (offsetXFraction > 0.5f) PopupPositionProvider.Align.TopEnd
                else PopupPositionProvider.Align.TopStart

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            currentX.roundToPx(),
                            currentY.roundToPx(),
                        )
                    }
                    .size(ballSize)
                    .pointerInput(maxX, maxY) {
                        var dragStartXFraction = offsetXFraction
                        var dragStartYFraction = offsetYFraction
                        detectDragGestures(
                            onDragStart = {
                                dragStartXFraction = offsetXFraction
                                dragStartYFraction = offsetYFraction
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            val nextX = (maxX.toPx() * dragStartXFraction + dragAmount.x)
                                .coerceIn(0f, maxX.toPx())
                            val nextY = (maxY.toPx() * dragStartYFraction + dragAmount.y)
                                .coerceIn(0f, maxY.toPx())
                            val nextXFraction =
                                if (maxX > 0.dp) nextX / maxX.toPx()
                                else 0f
                            val nextYFraction =
                                if (maxY > 0.dp) nextY / maxY.toPx()
                                else 0f
                            dragStartXFraction = nextXFraction
                            dragStartYFraction = nextYFraction
                            offsetXFraction = nextXFraction
                            offsetYFraction = nextYFraction
                        }
                    },
            ) {
                Button(
                    modifier = Modifier.fillMaxSize(),
                    onClick = {
                        haptic.contextClick()
                        showActions = true
                    },
                    cornerRadius = ballSize / 2,
                    minHeight = ballSize,
                    insideMargin = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        color = Color.Black.copy(alpha = backgroundAlpha),
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .size(ringSize)
                            .clip(CircleShape)
                            .then(
                                if (ringAlpha > 0f) {
                                    Modifier.border(
                                        ringWidth,
                                        Color.White.copy(alpha = ringAlpha),
                                        CircleShape,
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }

                ActionPopup(
                    show = showActions,
                    actions = actions,
                    onDismiss = { showActions = false },
                    onAction = {
                        if (it == VirtualButtonAction.PASSWORD_INPUT &&
                            passwordPopupContent != null
                        ) showPasswordPopup = true
                        else scope.launch { onAction(it) }

                        showActions = false
                    },
                    passwordPopupContent = passwordPopupContent,
                    renderInRootScaffold = true,
                    popupAlignment = popupAlignment,
                )

                if (passwordPopupContent != null) {
                    OverlayListPopup(
                        show = showPasswordPopup,
                        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                        alignment = popupAlignment,
                        onDismissRequest = { showPasswordPopup = false },
                        renderInRootScaffold = true,
                        enableWindowDim = false,
                    ) {
                        passwordPopupContent { showPasswordPopup = false }
                    }
                }
            }
        }
    }

    @Composable
    private fun ActionPopup(
        show: Boolean,
        actions: List<VirtualButtonAction>,
        onDismiss: () -> Unit,
        onAction: suspend (VirtualButtonAction) -> Unit,
        passwordPopupContent: (@Composable (onDismissRequest: () -> Unit) -> Unit)? = null,
        renderInRootScaffold: Boolean,
        popupAlignment: PopupPositionProvider.Align = PopupPositionProvider.Align.TopEnd,
        popupBottomPadding: Dp = 0.dp,
    ) {
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val spinnerItems = actions.map { action ->
            val title = stringResource(action.titleResId)
            DropdownItem(
                icon = {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = title,
                        modifier = Modifier
                            .padding(end = UiSpacing.ContentVertical),
                    )
                },
                title = title,
            )
        }

        NavOverlayListPopup(
            show = show,
            startDestination = ActionPopupDestination.Actions,
            popupAlignment = popupAlignment,
            onDismiss = onDismiss,
            renderInRootScaffold = renderInRootScaffold,
            popupBottomPadding = popupBottomPadding,
        ) { destination, navigateTo, dismiss ->
            ListPopupColumn {
                if (destination == ActionPopupDestination.Actions)
                    spinnerItems.forEachIndexed { index, entry ->
                        SpinnerItemImpl(
                            entry = entry,
                            entryCount = spinnerItems.size,
                            isSelected = false,
                            index = index,
                            spinnerColors = DropdownDefaults.dropdownColors(),
                            dialogMode = false,
                            onSelectedIndexChange = { selectedIdx ->
                                haptic.confirm()
                                val selectedAction = actions[selectedIdx]
                                if (
                                    selectedAction == VirtualButtonAction.PASSWORD_INPUT &&
                                    passwordPopupContent != null
                                ) {
                                    navigateTo(ActionPopupDestination.Passwords)
                                } else {
                                    scope.launch { onAction(selectedAction) }
                                    dismiss()
                                }
                            },
                        )
                    }
                else if (passwordPopupContent != null)
                    passwordPopupContent { dismiss() }
                else
                    dismiss()
            }
        }
    }

    @Composable
    private fun <Destination> NavOverlayListPopup(
        show: Boolean,
        startDestination: Destination,
        popupAlignment: PopupPositionProvider.Align,
        onDismiss: () -> Unit,
        renderInRootScaffold: Boolean,
        popupBottomPadding: Dp = 0.dp,
        content: @Composable (
            destination: Destination,
            navigateTo: (Destination) -> Unit,
            dismiss: () -> Unit,
        ) -> Unit,
    ) {
        var destination by remember(show, startDestination) { mutableStateOf(startDestination) }
        OverlayListPopup(
            show = show,
            popupPositionProvider =
                rememberBottomSafeContextMenuPositionProvider(popupBottomPadding),
            alignment = popupAlignment,
            onDismissRequest = onDismiss,
            renderInRootScaffold = renderInRootScaffold,
            enableWindowDim = false,
        ) {
            content(destination, { destination = it }, onDismiss)
        }
    }

    @Composable
    private fun rememberBottomSafeContextMenuPositionProvider(
        bottomPadding: Dp,
    ): PopupPositionProvider = remember(bottomPadding) {
        if (bottomPadding <= 0.dp) {
            ListPopupDefaults.ContextMenuPositionProvider
        } else {
            BottomSafeContextMenuPositionProvider(bottomPadding)
        }
    }

    private class BottomSafeContextMenuPositionProvider(
        private val bottomPadding: Dp,
    ): PopupPositionProvider {
        private val delegate = ListPopupDefaults.ContextMenuPositionProvider

        override fun calculatePosition(
            anchorBounds: IntRect,
            windowBounds: IntRect,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize,
            popupMargin: IntRect,
            alignment: PopupPositionProvider.Align,
        ): IntOffset = delegate.calculatePosition(
            anchorBounds = anchorBounds,
            windowBounds = windowBounds,
            layoutDirection = layoutDirection,
            popupContentSize = popupContentSize,
            popupMargin = popupMargin,
            alignment = alignment,
        )

        override fun getMargins(): PaddingValues = PaddingValues(bottom = bottomPadding)
    }
}
