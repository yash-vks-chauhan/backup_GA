package com.gridee.parking.utils

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.gridee.parking.R
import androidx.activity.result.ActivityResultLauncher

class GoogleSignInManager(private val activity: Activity) {
    
    private val googleSignInClient: GoogleSignInClient
    
    init {
        
        val webClientId = activity.getString(R.string.default_web_client_id)
        
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
            .build()
        
        
        googleSignInClient = GoogleSignIn.getClient(activity, gso)
        
    }
    
    /**
     * Launches sign-in and forces the account chooser by revoking the cached session first.
     * revokeAccess() clears the default account selection so the picker is shown every time.
     */
    fun launchSignIn(launcher: ActivityResultLauncher<Intent>) {
        
        googleSignInClient.revokeAccess().addOnCompleteListener {
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                
                try {
                    launcher.launch(signInIntent)
                } catch (_: RuntimeException) {
                    // The host may have left a launchable lifecycle state; the user can retry.
                }
            }
        }
    }
    
    fun handleSignInResult(data: Intent?): GoogleSignInResult {
        
        if (data == null) {
            return GoogleSignInResult.Cancelled
        }
        
        return try {
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
            
            
            val account = task.getResult(ApiException::class.java)
            
            
            GoogleSignInResult.Success(account)
        } catch (e: ApiException) {
            
            if (e.statusCode == 12501) {
                GoogleSignInResult.Cancelled
            } else {
                val userError = AuthErrorMapper.fromGoogleSignInCode(e.statusCode)
                GoogleSignInResult.Error(userError.message)
            }
        } catch (e: Exception) {
            GoogleSignInResult.Error("Google sign-in failed. Please try again.")
        }
    }
    
    fun signOut() {
        googleSignInClient.signOut()
    }
    
    fun revokeAccess() {
        googleSignInClient.revokeAccess()
    }
    
    fun isSignedIn(): Boolean {
        return GoogleSignIn.getLastSignedInAccount(activity) != null
    }
}

sealed class GoogleSignInResult {
    data class Success(val account: GoogleSignInAccount) : GoogleSignInResult()
    data class Error(val message: String) : GoogleSignInResult()
    object Cancelled : GoogleSignInResult()
}
