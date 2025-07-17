package com.bitchat.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Main ChatScreen - REFACTORED to use component-based architecture
 * This is now a coordinator that orchestrates the following UI components:
 * - ChatHeader: App bar, navigation, peer counter
 * - MessageComponents: Message display and formatting
 * - InputComponents: Message input and command suggestions
 * - SidebarComponents: Navigation drawer with channels and people
 * - DialogComponents: Password prompts and modals
 * - ChatUIUtils: Utility functions for formatting and colors
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val colorScheme = MaterialTheme.colorScheme
    val messages by viewModel.messages.observeAsState(emptyList())
    val connectedPeers by viewModel.connectedPeers.observeAsState(emptyList())
    val nickname by viewModel.nickname.observeAsState("")
    val selectedPrivatePeer by viewModel.selectedPrivateChatPeer.observeAsState()
    val currentChannel by viewModel.currentChannel.observeAsState()
    val joinedChannels by viewModel.joinedChannels.observeAsState(emptySet())
    val hasUnreadChannels by viewModel.unreadChannelMessages.observeAsState(emptyMap())
    val hasUnreadPrivateMessages by viewModel.unreadPrivateMessages.observeAsState(emptySet())
    val privateChats by viewModel.privateChats.observeAsState(emptyMap())
    val channelMessages by viewModel.channelMessages.observeAsState(emptyMap())
    val showSidebar by viewModel.showSidebar.observeAsState(false)
    val showCommandSuggestions by viewModel.showCommandSuggestions.observeAsState(false)
    val commandSuggestions by viewModel.commandSuggestions.observeAsState(emptyList())
    val showAppInfo by viewModel.showAppInfo.observeAsState(false)

    var messageText by remember { mutableStateOf(TextFieldValue("")) }
    var showPasswordPrompt by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }

    // Show password dialog when needed
    LaunchedEffect(showPasswordPrompt) {
        showPasswordDialog = showPasswordPrompt
    }

    val isConnected by viewModel.isConnected.observeAsState(false)
    val passwordPromptChannel by viewModel.passwordPromptChannel.observeAsState(null)

    // Determine what messages to show
    val displayMessages = when {
        selectedPrivatePeer != null -> privateChats[selectedPrivatePeer] ?: emptyList()
        currentChannel != null -> channelMessages[currentChannel] ?: emptyList()
        else -> messages
    }

    // Use WindowInsets to handle keyboard properly
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val headerHeight = 36.dp

        // Main content area that responds to keyboard/window insets
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .windowInsetsPadding(WindowInsets.ime) // This handles keyboard insets
        ) {
            // Header spacer - creates space for the floating header
            Spacer(modifier = Modifier.height(headerHeight))

            // Messages area - takes up available space, will compress when keyboard appears
            Box(modifier = Modifier.weight(1f)) {
                MessagesList(
                    messages = displayMessages,
                    currentUserNickname = nickname,
                    meshService = viewModel.meshService,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Input area - stays at bottom
            ChatInputSection(
                messageText = messageText,
                onMessageTextChange = { newText ->
                    messageText = newText
                    viewModel.updateCommandSuggestions(newText.text)
                },
                onSend = {
                    if (messageText.text.trim().isNotEmpty()) {
                        viewModel.sendMessage(messageText.text.trim())
                        messageText = TextFieldValue("")
                    }
                },
                showCommandSuggestions = showCommandSuggestions,
                commandSuggestions = commandSuggestions,
                onSuggestionClick = { suggestion: CommandSuggestion ->
                    messageText = TextFieldValue(viewModel.selectCommandSuggestion(suggestion))
                },
                selectedPrivatePeer = selectedPrivatePeer,
                currentChannel = currentChannel,
                nickname = nickname,
                colorScheme = colorScheme
            )
        }

        // Floating header - positioned absolutely at top, ignores keyboard
        ChatFloatingHeader(
            headerHeight = headerHeight,
            selectedPrivatePeer = selectedPrivatePeer,
            currentChannel = currentChannel,
            nickname = nickname,
            viewModel = viewModel,
            colorScheme = colorScheme,
            onSidebarToggle = { viewModel.showSidebar() },
            onShowAppInfo = { viewModel.showAppInfo() },
            onPanicClear = { viewModel.panicClearAllData() }
        )

        // Sidebar overlay
        AnimatedVisibility(
            visible = showSidebar,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300, easing = EaseOutCubic)
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(250, easing = EaseInCubic)
            ) + fadeOut(animationSpec = tween(250)),
            modifier = Modifier.zIndex(2f)
        ) {
            SidebarOverlay(
                viewModel = viewModel,
                onDismiss = { viewModel.hideSidebar() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // Dialogs
    ChatDialogs(
        showPasswordDialog = showPasswordDialog,
        passwordPromptChannel = passwordPromptChannel,
        passwordInput = passwordInput,
        onPasswordChange = { passwordInput = it },
        onPasswordConfirm = {
            if (passwordInput.isNotEmpty()) {
                val success = viewModel.joinChannel(passwordPromptChannel!!, passwordInput)
                if (success) {
                    showPasswordDialog = false
                    passwordInput = ""
                }
            }
        },
        onPasswordDismiss = {
            showPasswordDialog = false
            passwordInput = ""
        },
        showAppInfo = showAppInfo,
        onAppInfoDismiss = { viewModel.hideAppInfo() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInputSection(
    messageText: TextFieldValue,
    onMessageTextChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    showCommandSuggestions: Boolean,
    commandSuggestions: List<CommandSuggestion>,
    onSuggestionClick: (CommandSuggestion) -> Unit,
    selectedPrivatePeer: String?,
    currentChannel: String?,
    nickname: String,
    colorScheme: ColorScheme
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colorScheme.background,
        shadowElevation = 8.dp
    ) {
        Column {
            Divider(color = colorScheme.outline.copy(alpha = 0.3f))

            // Command suggestions box
            if (showCommandSuggestions && commandSuggestions.isNotEmpty()) {
                CommandSuggestionsBox(
                    suggestions = commandSuggestions,
                    onSuggestionClick = onSuggestionClick,
                    modifier = Modifier.fillMaxWidth()
                )

                Divider(color = colorScheme.outline.copy(alpha = 0.2f))
            }

            MessageInput(
                value = messageText,
                onValueChange = onMessageTextChange,
                onSend = onSend,
                selectedPrivatePeer = selectedPrivatePeer,
                currentChannel = currentChannel,
                nickname = nickname,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatFloatingHeader(
    headerHeight: Dp,
    selectedPrivatePeer: String?,
    currentChannel: String?,
    nickname: String,
    viewModel: ChatViewModel,
    colorScheme: ColorScheme,
    onSidebarToggle: () -> Unit,
    onShowAppInfo: () -> Unit,
    onPanicClear: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .zIndex(1f)
            .windowInsetsPadding(WindowInsets.statusBars), // Only respond to status bar
        color = colorScheme.background.copy(alpha = 0.95f),
        shadowElevation = 8.dp
    ) {
        TopAppBar(
            title = {
                ChatHeaderContent(
                    selectedPrivatePeer = selectedPrivatePeer,
                    currentChannel = currentChannel,
                    nickname = nickname,
                    viewModel = viewModel,
                    onBackClick = {
                        when {
                            selectedPrivatePeer != null -> viewModel.endPrivateChat()
                            currentChannel != null -> viewModel.switchToChannel(null)
                        }
                    },
                    onSidebarClick = onSidebarToggle,
                    onTripleClick = onPanicClear,
                    onShowAppInfo = onShowAppInfo
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )
    }

    // Divider under header
    Divider(
        color = colorScheme.outline.copy(alpha = 0.3f),
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = headerHeight)
            .zIndex(1f)
    )
}

@Composable
private fun ChatDialogs(
    showPasswordDialog: Boolean,
    passwordPromptChannel: String?,
    passwordInput: String,
    onPasswordChange: (String) -> Unit,
    onPasswordConfirm: () -> Unit,
    onPasswordDismiss: () -> Unit,
    showAppInfo: Boolean,
    onAppInfoDismiss: () -> Unit
) {
    // Password dialog
    PasswordPromptDialog(
        show = showPasswordDialog,
        channelName = passwordPromptChannel,
        passwordInput = passwordInput,
        onPasswordChange = onPasswordChange,
        onConfirm = onPasswordConfirm,
        onDismiss = onPasswordDismiss
    )

    // App info dialog
    AppInfoDialog(
        show = showAppInfo,
        onDismiss = onAppInfoDismiss
    )
}
