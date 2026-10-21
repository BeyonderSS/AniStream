package com.anistream.core.account.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anistream.core.account.AccountRepository
import com.anistream.core.account.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

data class AccountUi(val busy: Boolean = false, val error: String? = null, val message: String? = null)

/** Shared by the phone account screen and the profile picker. */
@HiltViewModel
class AccountViewModel @Inject constructor(val repo: AccountRepository) : ViewModel() {
    val session = repo.session
    val profiles = repo.profiles
    val pickerPending = repo.pickerPending

    private val _ui = MutableStateFlow(AccountUi())
    val ui: StateFlow<AccountUi> = _ui.asStateFlow()

    private fun run(done: String? = null, block: suspend () -> Unit) {
        _ui.value = AccountUi(busy = true)
        viewModelScope.launch {
            _ui.value = try { block(); AccountUi(message = done) } catch (e: Exception) { AccountUi(error = e.message ?: "Something went wrong") }
        }
    }

    fun signIn(username: String, password: String, register: Boolean) =
        run { if (register) repo.register(username, password) else repo.login(username, password) }

    fun signOut() = run { repo.logout() }
    fun select(p: Profile) = repo.selectProfile(p.id)
    fun refresh() = run { repo.refreshProfiles() }
    fun claim(code: String) = run("TV signed in") { repo.claimPair(code) }
    fun save(id: String?, name: String, avatar: String, kids: Boolean) =
        run { if (id == null) repo.createProfile(name, avatar, kids) else repo.updateProfile(id, name, avatar, kids) }
    fun delete(id: String) = run { repo.deleteProfile(id) }
    fun clearStatus() = _ui.update { AccountUi() }
}
