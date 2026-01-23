package com.gridee.parking.utils

import android.app.Activity
import android.content.Intent
import android.util.Log
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
    
    companion object {
        private const val TAG = "GoogleSignInManager"
    }
    
    init {
        Log.d(TAG, "=== Initializing GoogleSignInManager ===")
        
        val webClientId = activity.getString(R.string.default_web_client_id)
        Log.d(TAG, "Web Client ID: ${webClientId.take(20)}...${webClientId.takeLast(10)}")
        
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
            .build()
        
        Log.d(TAG, "GoogleSignInOptions configured:")
        Log.d(TAG, "  - requestIdToken: YES")
        Log.d(TAG, "  - requestEmail: YES")
        Log.d(TAG, "  - requestProfile: YES")
        
        googleSignInClient = GoogleSignIn.getClient(activity, gso)
        Log.d(TAG, "GoogleSignInClient created successfully")
        
        // Check if already signed in
        val lastSignedInAccount = GoogleSignIn.getLastSignedInAccount(activity)
        if (lastSignedInAccount != null) {
            Log.d(TAG, "User already signed in: ${lastSignedInAccount.email}")
            Log.d(TAG, "  - Display Name: ${lastSignedInAccount.displayName}")
            Log.d(TAG, "  - ID: ${lastSignedInAccount.id}")
        } else {
            Log.d(TAG, "No existing signed-in account")
        }
        
        Log.d(TAG, "=== GoogleSignInManager initialized ===")
    }
    
    /**
     * Launches sign-in and forces the account chooser by revoking the cached session first.
     * revokeAccess() clears the default account selection so the picker is shown every time.
     */
    fun launchSignIn(launcher: ActivityResultLauncher<Intent>) {
        Log.d(TAG, ">>> launchSignIn() called")
        
        Log.d(TAG, "Step 1: Revoking access to clear cached account...")
        googleSignInClient.revokeAccess().addOnCompleteListener { revokeTask ->
            if (revokeTask.isSuccessful) {
                Log.d(TAG, "✅ Access revoked successfully")
            } else {
                Log.w(TAG, "⚠️ Access revoke failed: ${revokeTask.exception?.message}")
            }
            
            Log.d(TAG, "Step 2: Signing out to clear session...")
            googleSignInClient.signOut().addOnCompleteListener { signOutTask ->
                if (signOutTask.isSuccessful) {
                    Log.d(TAG, "✅ Sign out successful")
                } else {
                    Log.w(TAG, "⚠️ Sign out failed: ${signOutTask.exception?.message}")
                }
                
                Log.d(TAG, "Step 3: Launching Google Sign-In intent...")
                val signInIntent = googleSignInClient.signInIntent
                Log.d(TAG, "Sign-in intent created: $signInIntent")
                
                try {
                    launcher.launch(signInIntent)
                    Log.d(TAG, "✅ Sign-in intent launched successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to launch sign-in intent: ${e.message}", e)
                }
            }
        }
    }
    
    fun handleSignInResult(data: Intent?): GoogleSignInResult {
        Log.d(TAG, ">>> handleSignInResult() called")
        Log.d(TAG, "Intent data: $data")
        
        if (data == null) {
            Log.e(TAG, "❌ Intent data is NULL - user might have cancelled")
            return GoogleSignInResult.Error("Sign in cancelled - no data received")
        }
        
        return try {
            Log.d(TAG, "Attempting to get signed-in account from intent...")
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
            
            Log.d(TAG, "Task received:")
            Log.d(TAG, "  - isSuccessful: ${task.isSuccessful}")
            Log.d(TAG, "  - isComplete: ${task.isComplete}")
            Log.d(TAG, "  - isCanceled: ${task.isCanceled}")
            
            val account = task.getResult(ApiException::class.java)
            
            Log.d(TAG, "✅ Sign-in SUCCESSFUL!")
            Log.d(TAG, "Account details:")
            Log.d(TAG, "  - Email: ${account?.email}")
            Log.d(TAG, "  - Display Name: ${account?.displayName}")
            Log.d(TAG, "  - ID: ${account?.id}")
            Log.d(TAG, "  - ID Token: ${account?.idToken?.take(30)}...")
            Log.d(TAG, "  - Photo URL: ${account?.photoUrl}")
            
            GoogleSignInResult.Success(account)
        } catch (e: ApiException) {
            Log.e(TAG, "❌ Sign-in FAILED with ApiException")
            Log.e(TAG, "  - Status Code: ${e.statusCode}")
            Log.e(TAG, "  - Status Message: ${e.statusMessage}")
            Log.e(TAG, "  - Message: ${e.message}")
            Log.e(TAG, "  - Localized Message: ${e.localizedMessage}", e)
            
            // Common error codes explanation
            val errorExplanation = when (e.statusCode) {
                10 -> "DEVELOPER_ERROR - Check SHA-1 fingerprint in Firebase Console"
                12500 -> "SIGN_IN_FAILED - General sign-in failure"
                12501 -> "SIGN_IN_CANCELLED - User cancelled the sign-in"
                7 -> "NETWORK_ERROR - Network connection failed"
                else -> "Unknown error code"
            }
            Log.e(TAG, "  - Explanation: $errorExplanation")
            
            GoogleSignInResult.Error("Google sign in failed: ${e.statusCode} - $errorExplanation")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Unexpected exception during sign-in: ${e.message}", e)
            GoogleSignInResult.Error("Unexpected error: ${e.message}")
        }
    }
    
    fun signOut() {
        Log.d(TAG, ">>> signOut() called")
        googleSignInClient.signOut().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "✅ Sign out successful")
            } else {
                Log.e(TAG, "❌ Sign out failed: ${task.exception?.message}")
            }
        }
    }
    
    fun revokeAccess() {
        Log.d(TAG, ">>> revokeAccess() called")
        googleSignInClient.revokeAccess().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "✅ Access revoked successfully")
            } else {
                Log.e(TAG, "❌ Revoke access failed: ${task.exception?.message}")
            }
        }
    }
    
    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(activity)
        val signedIn = account != null
        Log.d(TAG, ">>> isSignedIn() = $signedIn")
        if (signedIn) {
            Log.d(TAG, "  - Email: ${account?.email}")
        }
        return signedIn
    }
}

sealed class GoogleSignInResult {
    data class Success(val account: GoogleSignInAccount) : GoogleSignInResult()
    data class Error(val message: String) : GoogleSignInResult()
}
