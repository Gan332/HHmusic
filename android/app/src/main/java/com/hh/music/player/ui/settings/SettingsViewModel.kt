package com.hh.music.player.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hh.music.player.data.local.LocalStore
import com.hh.music.player.network.DirectNcmClient
import com.hh.music.player.network.LoginClient
import com.hh.music.player.network.QrPollStatus
import com.hh.music.player.ui.theme.AppThemeColor
import com.hh.music.player.ui.theme.AppThemeMode
import com.hh.music.player.ui.theme.LyricFontScale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** QR-login dialog lifecycle. */
sealed interface LoginUiState {
    /** Dialog closed / finished. */
    data object Idle : LoginUiState

    /** Fetching the unikey for a fresh QR code. */
    data object Generating : LoginUiState

    /** QR ready and shown; nobody scanned yet. */
    data class AwaitingScan(val qrKey: String) : LoginUiState

    /** Phone scanned; waiting for the user to confirm there. */
    data object AwaitingConfirm : LoginUiState

    /** Terminal failure inside the dialog (expired QR rejected, bad cookie, network…). */
    data class Error(val message: String) : LoginUiState

    /** Login completed; the dialog closes and the account card refreshes. */
    data object Success : LoginUiState
}

data class SettingsState(
    val useBackend: Boolean = false,
    val audioQuality: String = "exhigh",
    val aboutVersion: String = "1.6"
)

class SettingsViewModel(private val store: LocalStore) : ViewModel() {

    val useBackend: StateFlow<Boolean> =
        store.useBackend.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val audioQuality: StateFlow<String> =
        store.audioQuality.stateIn(viewModelScope, SharingStarted.Eagerly, "exhigh")

    val progressStyle: StateFlow<String> =
        store.progressStyle.stateIn(viewModelScope, SharingStarted.Eagerly, "slider")

    val themeMode: StateFlow<AppThemeMode> =
        store.themeMode.map { AppThemeMode.from(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, AppThemeMode.SYSTEM)

    val themeColor: StateFlow<AppThemeColor> =
        store.themeColor.map { AppThemeColor.from(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, AppThemeColor.GREEN)

    val dynamicColor: StateFlow<Boolean> =
        store.dynamicColor.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val autoCache: StateFlow<Boolean> =
        store.autoCache.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val cacheCapMb: StateFlow<Int> =
        store.cacheCapMb.stateIn(viewModelScope, SharingStarted.Eagerly, 1024)

    val showLyricTranslation: StateFlow<Boolean> =
        store.showLyricTranslation.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val showLyricRomanization: StateFlow<Boolean> =
        store.showLyricRomanization.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val lyricFontScale: StateFlow<LyricFontScale> =
        store.lyricFontScale.map { LyricFontScale.from(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, LyricFontScale.MEDIUM)

    // ---- v1.8 account ----

    val userId: StateFlow<Long> =
        store.userId.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val nickname: StateFlow<String> =
        store.nickname.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val avatarUrl: StateFlow<String> =
        store.avatarUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private var pollJob: Job? = null

    fun setUseBackend(value: Boolean) { viewModelScope.launch { store.setUseBackend(value) } }
    fun setAudioQuality(value: String) { viewModelScope.launch { store.setAudioQuality(value) } }
    fun setProgressStyle(value: String) { viewModelScope.launch { store.setProgressStyle(value) } }
    fun setThemeMode(value: String) { viewModelScope.launch { store.setThemeMode(value) } }
    fun setThemeColor(value: String) { viewModelScope.launch { store.setThemeColor(value) } }
    fun setDynamicColor(value: Boolean) { viewModelScope.launch { store.setDynamicColor(value) } }
    fun setAutoCache(value: Boolean) { viewModelScope.launch { store.setAutoCache(value) } }
    fun setCacheCapMb(value: Int) { viewModelScope.launch { store.setCacheCapMb(value) } }
    fun setShowLyricTranslation(value: Boolean) { viewModelScope.launch { store.setShowLyricTranslation(value) } }
    fun setShowLyricRomanization(value: Boolean) { viewModelScope.launch { store.setShowLyricRomanization(value) } }
    fun setLyricFontScale(value: String) { viewModelScope.launch { store.setLyricFontScale(value) } }

    // ---- login flows ----

    /** Kick off a fresh QR code and start polling its status. */
    fun startQrLogin() {
        stopPolling()
        _loginState.value = LoginUiState.Generating
        viewModelScope.launch {
            LoginClient.createQrKey(kotlinx.coroutines.Dispatchers.IO)
                .onSuccess { key ->
                    _loginState.value = LoginUiState.AwaitingScan(key)
                    startPolling(key)
                }
                .onFailure { e ->
                    _loginState.value = LoginUiState.Error(e.message ?: "网络异常，请稍后重试")
                }
        }
    }

    private fun startPolling(qrKey: String) {
        stopPolling(keepState = true)
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (!isActive) return@launch
                when (val status = LoginClient.pollQr(qrKey, kotlinx.coroutines.Dispatchers.IO).getOrNull()) {
                    is QrPollStatus.Success -> {
                        completeLogin(status.musicU.takeIf { it.isNotBlank() })
                        return@launch
                    }
                    QrPollStatus.WaitingForConfirm -> _loginState.value = LoginUiState.AwaitingConfirm
                    QrPollStatus.Expired -> {
                        _loginState.value = LoginUiState.Error("二维码已过期，请点击刷新")
                        return@launch
                    }
                    else -> Unit // still waiting for scan; keep polling
                }
            }
        }
    }

    /** Persist the session token, then cache the profile. */
    private suspend fun completeLogin(musicU: String?) {
        if (musicU.isNullOrBlank()) {
            _loginState.value = LoginUiState.Error("登录成功但未取得会话凭证，请重试")
            return
        }
        // Apply immediately (don't wait for AppContainer's flow collector) so the
        // very next profile fetch already carries the fresh cookie.
        DirectNcmClient.setCookie("MUSIC_U=$musicU")
        store.setLoginCookie(musicU)
        val account = runCatching { LoginClient.fetchAccount(kotlinx.coroutines.Dispatchers.IO) }.getOrNull()
        if (account != null) {
            store.setAccount(account.userId, account.nickname, account.avatarUrl)
            _loginState.value = LoginUiState.Success
        } else {
            _loginState.value = LoginUiState.Error("已扫码但获取账号信息失败，请重试")
        }
    }

    /**
     * Cookie-paste fallback: accept either a raw MUSIC_U token or a full cookie
     * string copied from a browser. Invalid tokens are detected via the profile
     * probe and rolled back.
     */
    fun loginWithCookie(pasted: String) {
        val trimmed = pasted.trim()
        val token = LoginClient.extractMusicU(listOf(trimmed))
            ?: trimmed.takeIf { it.length >= 32 && !it.contains(';') && !it.contains(' ') }
        if (token.isNullOrBlank()) {
            _loginState.value = LoginUiState.Error("未识别到 MUSIC_U，请粘贴完整 Cookie 或纯令牌")
            return
        }
        viewModelScope.launch {
            DirectNcmClient.setCookie("MUSIC_U=$token")
            store.setLoginCookie(token)
            val account = runCatching { LoginClient.fetchAccount(kotlinx.coroutines.Dispatchers.IO) }.getOrNull()
            if (account == null) {
                DirectNcmClient.setCookie(null)
                store.clearAccount()
                _loginState.value = LoginUiState.Error("Cookie 无效或已过期")
            } else {
                store.setAccount(account.userId, account.nickname, account.avatarUrl)
                _loginState.value = LoginUiState.Success
            }
        }
    }

    /** Regenerate the QR after an expired/error state. */
    fun retryFromError() = startQrLogin()

    /** Close/cancel any in-flight login attempt. */
    fun cancelLogin() {
        stopPolling()
        _loginState.value = LoginUiState.Idle
    }

    fun logout() {
        stopPolling()
        viewModelScope.launch {
            DirectNcmClient.setCookie(null)
            store.clearAccount()
        }
    }

    private fun stopPolling(keepState: Boolean = false) {
        pollJob?.cancel()
        pollJob = null
        if (!keepState && _loginState.value != LoginUiState.Idle) {
            // Only reset transient states; keep Error visible until dismissed.
            if (_loginState.value !is LoginUiState.Error) _loginState.value = LoginUiState.Idle
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
    }
}
