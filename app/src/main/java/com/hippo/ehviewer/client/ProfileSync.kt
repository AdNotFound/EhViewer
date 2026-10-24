package com.hippo.ehviewer.client

import android.widget.Toast
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.ui.MainActivity
import com.hippo.util.launchIO
import com.hippo.util.withUIContext
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import okhttp3.HttpUrl.Companion.toHttpUrl

object ProfileSync {
    private var syncJob: Job? = null

    fun cancel() {
        syncJob?.cancel()
        syncJob = null
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun sync(showToastOnFailure: Boolean = true) {
        if (!EhCookieStore.hasSignedIn()) return
        if (syncJob?.isActive == true) return

        syncJob = launchIO {
            var success = false
            runCatching {
                EhEngine.getProfile().run {
                    if (EhCookieStore.hasSignedIn()) {
                        Settings.putDisplayName(displayName)
                        Settings.putAvatar(avatar)
                        success = true
                    }
                }
            }

            if (success) {
                withUIContext {
                    (EhApplication.application.topActivity as? MainActivity)?.updateProfile()
                }
            } else if (EhCookieStore.hasSignedIn()) {
                val memberId = EhCookieStore.getCookieValue(EhUrl.HOST_E.toHttpUrl(), EhCookieStore.KEY_IPB_MEMBER_ID)
                if (!memberId.isNullOrEmpty()) {
                    Settings.putDisplayName(memberId)
                }
                withUIContext {
                    (EhApplication.application.topActivity as? MainActivity)?.updateProfile()
                    if (showToastOnFailure) {
                        Toast.makeText(
                            EhApplication.application,
                            R.string.sign_in_profile_failed_toast,
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }
}
