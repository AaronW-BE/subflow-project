package org.dpdns.alwaysup.subflow.data.repository

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import org.dpdns.alwaysup.subflow.BuildConfig
import org.dpdns.alwaysup.subflow.data.remote.GoogleLoginRequest
import org.dpdns.alwaysup.subflow.data.remote.GuestLoginRequest
import org.dpdns.alwaysup.subflow.data.remote.PurchaseReportRequest
import org.dpdns.alwaysup.subflow.data.remote.SubFlowApiService
import org.dpdns.alwaysup.subflow.domain.model.ProTier
import org.dpdns.alwaysup.subflow.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/** Distinguishes "user backed out" from "something broke" so the UI can stay quiet. */
class SignInCancelledException : Exception("Sign-in cancelled by user")

/** Raised when no web client id was compiled in, so the button should not be offered. */
class SignInNotConfiguredException : Exception("Google sign-in is not configured")

class AuthRepository(
    private val context: Context,
    private val api: SubFlowApiService = SubFlowApiService.create()
) {
    private val prefs = context.getSharedPreferences("subflow_auth_prefs", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<UserProfile?>(loadSavedUser())
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val _authToken = MutableStateFlow<String?>(prefs.getString("auth_token", null))
    val authToken: StateFlow<String?> = _authToken.asStateFlow()

    private val _isPro = MutableStateFlow(prefs.getBoolean("is_pro", false))
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _proTier = MutableStateFlow(ProTier.fromKey(prefs.getString("pro_tier", "free") ?: "free"))
    val proTier: StateFlow<ProTier> = _proTier.asStateFlow()

    val isGoogleSignInAvailable: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    fun updateProStatus(isPro: Boolean, tier: ProTier = ProTier.ANNUAL) {
        val resolvedTier = if (isPro) tier else ProTier.FREE
        _isPro.value = isPro
        _proTier.value = resolvedTier
        prefs.edit()
            .putBoolean("is_pro", isPro)
            .putString("pro_tier", resolvedTier.key)
            .apply()

        _currentUser.value = _currentUser.value?.copy(isPro = isPro, proTier = resolvedTier)
    }

    /**
     * A stable, non-reversible id for this install, handed to Play as the
     * obfuscated account id. Play requires it to contain no personal data.
     */
    fun obfuscatedAccountId(): String? {
        val existing = prefs.getString("obfuscated_account_id", null)
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString().replace("-", "").take(32)
        prefs.edit().putString("obfuscated_account_id", generated).apply()
        return generated
    }

    suspend fun signInAsGuest(): Result<UserProfile> {
        val guestId = prefs.getString("guest_id", null)
            ?: ("guest_" + UUID.randomUUID().toString().take(8)).also {
                prefs.edit().putString("guest_id", it).apply()
            }
        if (!BuildConfig.BACKEND_ENABLED) return Result.success(saveOfflineGuest(guestId))
        return try {
            val res = api.loginGuest(GuestLoginRequest(guestId))
            if (res.isSuccessful && res.body() != null) {
                val user = res.body()!!.user.toDomain()
                saveSession(user, res.body()!!.token)
                Result.success(user)
            } else {
                Result.success(saveOfflineGuest(guestId))
            }
        } catch (e: Exception) {
            // Local-first: never block the app on the sync server being reachable.
            Result.success(saveOfflineGuest(guestId))
        }
    }

    private fun saveOfflineGuest(guestId: String): UserProfile {
        val user = UserProfile(
            id = guestId,
            email = "",
            name = "",
            picture = "",
            authProvider = "guest",
            isPro = _isPro.value,
            proTier = _proTier.value
        )
        saveSession(user, "")
        return user
    }

    /**
     * Google sign-in through Credential Manager. Requires an Activity context -
     * Credential Manager renders a bottom sheet, which a application context
     * cannot host.
     */
    suspend fun signInWithGoogle(activityContext: Context): Result<UserProfile> {
        if (!isGoogleSignInAvailable) {
            return Result.failure(SignInNotConfiguredException())
        }
        return try {
            val option = GetSignInWithGoogleOption
                .Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .setNonce(UUID.randomUUID().toString())
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()

            val response = CredentialManager.create(activityContext).getCredential(activityContext, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                authenticateWithGoogleIdToken(googleCredential)
            } else {
                Result.failure(IllegalStateException("Unexpected credential type"))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(SignInCancelledException())
        } catch (e: NoCredentialException) {
            Result.failure(SignInCancelledException())
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager failure", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in failure", e)
            Result.failure(e)
        }
    }

    private suspend fun authenticateWithGoogleIdToken(
        credential: GoogleIdTokenCredential
    ): Result<UserProfile> {
        // The server is the only place an id token can be trusted, but the app
        // stays usable if the server is unreachable - the local vault is the
        // source of truth either way.
        val localProfile = UserProfile(
            id = credential.id,
            email = credential.id,
            name = credential.displayName ?: credential.givenName ?: credential.id.substringBefore('@'),
            picture = credential.profilePictureUri?.toString().orEmpty(),
            authProvider = "google",
            isPro = _isPro.value,
            proTier = _proTier.value
        )
        return try {
            val res = api.loginGoogle(GoogleLoginRequest(credential.idToken))
            if (res.isSuccessful && res.body() != null) {
                val body = res.body()!!
                // Entitlement stays owned by Play, not by the sync server.
                val user = body.user.toDomain().copy(isPro = _isPro.value, proTier = _proTier.value)
                saveSession(user, body.token)
                Result.success(user)
            } else {
                saveSession(localProfile, "")
                Result.success(localProfile)
            }
        } catch (e: Exception) {
            saveSession(localProfile, "")
            Result.success(localProfile)
        }
    }

    /** Best-effort record of a Play purchase so the backend can reconcile revenue. */
    suspend fun reportPurchase(productId: String, purchaseToken: String, orderId: String?) {
        if (!BuildConfig.BACKEND_ENABLED) return
        val token = _authToken.value
        if (token.isNullOrBlank() || productId.isBlank()) return
        runCatching {
            api.reportPurchase(
                token = "Bearer $token",
                req = PurchaseReportRequest(
                    productId = productId,
                    purchaseToken = purchaseToken,
                    orderId = orderId.orEmpty(),
                    packageName = context.packageName
                )
            )
        }.onFailure { Log.w(TAG, "Purchase report failed (will retry next launch): ${it.message}") }
    }

    private fun saveSession(user: UserProfile, token: String) {
        _currentUser.value = user
        _authToken.value = token.ifBlank { null }
        prefs.edit()
            .putString("user_id", user.id)
            .putString("user_email", user.email)
            .putString("user_name", user.name)
            .putString("user_picture", user.picture)
            .putString("auth_provider", user.authProvider)
            .putString("auth_token", token.ifBlank { null })
            .apply()
    }

    private fun loadSavedUser(): UserProfile? {
        val id = prefs.getString("user_id", null) ?: return null
        return UserProfile(
            id = id,
            email = prefs.getString("user_email", "").orEmpty(),
            name = prefs.getString("user_name", "").orEmpty(),
            picture = prefs.getString("user_picture", "").orEmpty(),
            authProvider = prefs.getString("auth_provider", "guest") ?: "guest",
            isPro = prefs.getBoolean("is_pro", false),
            proTier = ProTier.fromKey(prefs.getString("pro_tier", "free") ?: "free")
        )
    }

    /**
     * Signs out of the sync account. The Pro entitlement is deliberately kept:
     * it belongs to the Google Play account, not to the SubFlow session, and
     * wiping it here would lock a paying user out of what they bought.
     */
    fun signOut() {
        prefs.edit()
            .remove("user_id")
            .remove("user_email")
            .remove("user_name")
            .remove("user_picture")
            .remove("auth_provider")
            .remove("auth_token")
            .apply()
        _currentUser.value = null
        _authToken.value = null
    }

    private companion object {
        const val TAG = "SubFlowAuth"
    }
}
