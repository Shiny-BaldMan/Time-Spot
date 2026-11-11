package com.example.timecaptule

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.Locale

/** Nicknames are permanent login IDs; passwords are handled only by Firebase Auth. */
class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    val currentUser: FirebaseUser? get() = auth.currentUser
    val nickname: String? get() = currentUser?.email?.substringBefore('@')

    fun normalizeNickname(value: String) = value.trim().lowercase(Locale.ROOT)
    fun isValidNickname(value: String) = value.matches(Regex("[a-z0-9_]{2,20}"))
    private fun email(nickname: String) = "$nickname@login.timespot.example"

    fun signUp(nickname: String, password: String): Task<Void> =
        auth.createUserWithEmailAndPassword(email(nickname), password)
            .onSuccessTask { ensureProfile() }

    fun signIn(nickname: String, password: String): Task<Void> =
        auth.signInWithEmailAndPassword(email(nickname), password)
            .onSuccessTask { ensureProfile() }

    /** Retried on login/startup if Auth succeeded but the first Firestore write failed. */
    fun ensureProfile(): Task<Void> {
        val user = currentUser ?: return Tasks.forException(IllegalStateException("로그인이 필요해요."))
        val name = nickname ?: return Tasks.forException(IllegalStateException("계정 정보를 확인할 수 없어요."))
        val profile = db.collection("users").document(user.uid)
        val lookup = db.collection("friendLookup").document(name)
        return db.runTransaction { transaction ->
            val existingProfile = transaction.get(profile)
            val existingLookup = transaction.get(lookup)
            if (existingLookup.exists() && existingLookup.getString("uid") != user.uid) {
                throw IllegalStateException("이미 사용 중인 닉네임이에요.")
            }
            if (!existingProfile.exists()) {
                transaction.set(profile, mapOf(
                    "pid" to user.uid,
                    "nickname" to name,
                    "createdAt" to FieldValue.serverTimestamp()
                ))
            } else if (existingProfile.getString("pid") != user.uid) {
                transaction.update(profile, "pid", user.uid)
            }
            if (!existingLookup.exists()) {
                transaction.set(lookup, mapOf("uid" to user.uid, "nickname" to name))
            }
            null
        }
    }

    fun signOut() = auth.signOut()

    fun errorMessage(error: Exception?): String = when (error) {
        is FirebaseNetworkException -> "인터넷 연결을 확인한 뒤 다시 시도해 주세요."
        is FirebaseTooManyRequestsException -> "요청이 너무 많아요. 잠시 후 다시 시도해 주세요."
        is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> "이미 사용 중인 닉네임이에요. 로그인해 주세요."
            "ERROR_WEAK_PASSWORD" -> "비밀번호는 8자 이상으로 설정해 주세요."
            "ERROR_OPERATION_NOT_ALLOWED" -> "Firebase에서 이메일/비밀번호 로그인을 활성화해 주세요."
            "ERROR_USER_DISABLED" -> "사용이 중지된 계정이에요."
            "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" ->
                "닉네임 또는 비밀번호가 일치하지 않아요."
            else -> "인증에 실패했어요. 잠시 후 다시 시도해 주세요."
        }
        is FirebaseFirestoreException -> when (error.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "사용자 정보에 접근할 수 없어요. Firestore 규칙 설정을 확인해 주세요."
            FirebaseFirestoreException.Code.UNAVAILABLE -> "서버에 연결할 수 없어요. 인터넷 연결을 확인해 주세요."
            else -> "사용자 정보를 저장하지 못했어요. 다시 로그인하면 재시도할 수 있어요."
        }
        is IllegalArgumentException, is IllegalStateException -> error.message ?: "요청을 확인해 주세요."
        else -> "요청을 완료하지 못했어요. 잠시 후 다시 시도해 주세요."
    }
}
