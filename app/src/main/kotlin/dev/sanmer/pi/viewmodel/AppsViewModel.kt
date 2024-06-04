package dev.sanmer.pi.viewmodel

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sanmer.hidden.compat.PackageInfoCompat.isOverlayPackage
import dev.sanmer.hidden.compat.UserHandleCompat
import dev.sanmer.hidden.compat.delegate.AppOpsManagerDelegate
import dev.sanmer.pi.Compat
import dev.sanmer.pi.model.IPackageInfo
import dev.sanmer.pi.model.IPackageInfo.Companion.toIPackageInfo
import dev.sanmer.pi.receiver.PackageReceiver
import dev.sanmer.pi.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AppsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel(), AppOpsManagerDelegate.AppOpsCallback {
    private val isProviderAlive get() = Compat.isAlive
    private val pmCompat get() = Compat.packageManager
    private val aom by lazy {
        AppOpsManagerDelegate(
            Compat.appOpsService
        )
    }

    var isSearch by mutableStateOf(false)
        private set
    private val keyFlow = MutableStateFlow("")

    private val packagesFlow = MutableStateFlow(listOf<IPackageInfo>())
    private val cacheFlow = MutableStateFlow(listOf<IPackageInfo>())
    private val appsFlow = MutableStateFlow(listOf<IPackageInfo>())
    val apps get() = appsFlow.asStateFlow()

    var isLoading by mutableStateOf(true)
        private set

    override fun opChanged(op: Int, uid: Int, packageName: String) {
        Timber.d("opChanged: $packageName")

        viewModelScope.launch {
            packagesFlow.value = getPackages()
        }
    }

    init {
        Timber.d("AppsViewModel init")
        packagesObserver()
        dataObserver()
        keyObserver()
    }

    private fun packagesObserver() {
        combine(
            Compat.isAliveFlow,
            PackageReceiver.eventFlow
        ) { isAlive, _ ->
            if (!isAlive) return@combine

            if (isLoading) {
                aom.startWatchingMode(
                    AppOpsManagerDelegate.OP_REQUEST_INSTALL_PACKAGES,
                    null,
                    this
                )
            }

            packagesFlow.value = getPackages()

        }.launchIn(viewModelScope)

        addCloseable {
            if (isProviderAlive) {
                aom.stopWatchingMode(this)
            }
        }
    }

    private fun dataObserver() {
        combine(
            userPreferencesRepository.data,
            packagesFlow,
        ) { preferences, source ->
            if (source.isEmpty()) return@combine

            cacheFlow.value = source.map { pi ->
                pi.copy(
                    isRequester = preferences.requester == pi.packageName,
                    isExecutor = preferences.executor == pi.packageName
                )
            }.sortedByDescending { it.lastUpdateTime }
                .sortedByDescending { it.isAuthorized }
                .sortedByDescending { it.isExecutor || it.isRequester }

            isLoading = false

        }.launchIn(viewModelScope)
    }

    private fun keyObserver() {
        combine(
            keyFlow,
            cacheFlow
        ) { key, source ->

            appsFlow.value = source
                .filter {
                    if (key.isNotBlank()) {
                        it.appLabel.contains(key, ignoreCase = true)
                                || it.packageName.contains(key, ignoreCase = true)
                    } else {
                        true
                    }
                }

        }.launchIn(viewModelScope)
    }

    private suspend fun getPackages() = withContext(Dispatchers.IO) {
        if (!isProviderAlive) return@withContext emptyList()

        val allPackages = pmCompat.getInstalledPackages(
            PackageManager.GET_PERMISSIONS, UserHandleCompat.myUserId()
        ).list

        allPackages.filter {
            !it.isOverlayPackage
        }.map {
            it.toIPackageInfo(
                isAuthorized = it.isAuthorized()
            )
        }
    }

    fun search(key: String) {
        keyFlow.value = key
    }

    fun openSearch() {
        isSearch = true
    }

    fun closeSearch() {
        isSearch = false
        keyFlow.value = ""
    }

    private fun PackageInfo.isAuthorized() = aom.checkOpNoThrow(
        op = AppOpsManagerDelegate.OP_REQUEST_INSTALL_PACKAGES,
        uid = applicationInfo.uid,
        packageName = packageName
    ) == AppOpsManagerDelegate.MODE_ALLOWED
}