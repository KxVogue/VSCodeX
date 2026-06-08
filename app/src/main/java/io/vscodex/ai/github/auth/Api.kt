/*
 * This file is part of VSCodeX.
 *
 * VSCodeX is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * VSCodeX is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with VSCodeX.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package io.vscodex.ai.github.auth

import androidx.compose.ui.platform.UriHandler
import androidx.core.content.edit
import com.google.gson.Gson
import io.vscodex.ai.BuildConfig
import io.vscodex.ai.KEY_GIT_USER_ACCESS_TOKEN
import io.vscodex.ai.KEY_GIT_USER_INFO
import io.vscodex.ai.extensions.isNotNull
import io.vscodex.ai.github.User
import io.vscodex.ai.preferences.encryptedPrefs
import io.vscodex.ai.utils.awaitResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object Api {
    private val retrofits: (String) -> Retrofit = { url ->
        Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val gitHubClient: GitHubClient by lazy {
        retrofits("https://github.com/").create(GitHubClient::class.java)
    }

    val gitHubApiClient: GitHubClient by lazy {
        retrofits("https://api.github.com/").create(GitHubClient::class.java)
    }

    val startLogin: (UriHandler) -> Unit = { uriHandler ->
        val clientId = BuildConfig.CLIENT_ID
        val callback = BuildConfig.OAUTH_REDIRECT_URL
        val url =
            "https://github.com/login/oauth/authorize?client_id=$clientId&redirect_uri=$callback&scope=repo"

        uriHandler.openUri(url)
    }

    suspend fun exchangeCodeForToken(
        code: String,
        onSuccess: suspend CoroutineScope.(AccessToken) -> Unit = {},
        onFailure: suspend CoroutineScope.(Throwable) -> Unit = {}
    ) {
        withContext(Dispatchers.IO) {
            gitHubClient.getAccessToken(
                clientId = BuildConfig.CLIENT_ID,
                clientSecret = BuildConfig.CLIENT_SECRET,
                code = code
            ).awaitResult().onSuccess { accessToken ->
                withContext(Dispatchers.Main) {
                    onSuccess(accessToken)
                }
            }.onFailure { throwable ->
                withContext(Dispatchers.Main) {
                    onFailure(throwable)
                }
            }
        }
    }

    suspend fun getUser(
        token: String,
        onSuccess: suspend CoroutineScope.(User) -> Unit = {},
        onFailure: suspend CoroutineScope.(Throwable) -> Unit = {}
    ) {
        withContext(Dispatchers.IO) {
            gitHubApiClient.getUserInfo(
                token = "Bearer $token"
            ).awaitResult().onSuccess { user ->
                withContext(Dispatchers.Main) {
                    onSuccess(user)
                }
            }.onFailure { throwable ->
                withContext(Dispatchers.Main) {
                    onFailure(throwable)
                }
            }
        }
    }

    val saveUser: (UserInfo) -> Unit = { userInfo ->
        encryptedPrefs.edit(commit = true) {
            putString(KEY_GIT_USER_INFO, Gson().toJson(userInfo.user))
            putString(
                "$KEY_GIT_USER_ACCESS_TOKEN${userInfo.user.username}",
                Gson().toJson(userInfo.accessToken)
            )
        }
    }

    val removeUserFromDevice: (User) -> Unit = {
        encryptedPrefs.edit(commit = true) {
            remove(KEY_GIT_USER_INFO)
            remove("$KEY_GIT_USER_ACCESS_TOKEN${it.username}")
        }
    }

    val getUserInfo = {
        val userJson = encryptedPrefs.getString(KEY_GIT_USER_INFO, null)
        val user = userJson?.let { Gson().fromJson(it, User::class.java) }

        if (user.isNotNull()) UserInfo(user!!, getUserAccessToken(user)) else null
    }

    val getUserAccessToken: (User) -> AccessToken = { user ->
        val accessTokenJson =
            encryptedPrefs.getString("$KEY_GIT_USER_ACCESS_TOKEN${user.username}", "")
        Gson().fromJson(accessTokenJson, AccessToken::class.java)
    }
}

data class UserInfo(val user: User, val accessToken: AccessToken)
