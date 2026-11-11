package com.example.timecaptule

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.Date

data class SharedCapsule(
    val id: String,
    val ownerUid: String,
    val ownerNickname: String,
    val title: String,
    val place: String,
    val content: String,
    val opensAtMillis: Long,
    val sharedWith: List<String>
)

/** Firestore-backed capsules. Shared reads are limited to owner-added friends and unlocked capsules. */
class CapsuleRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun create(
        title: String,
        place: String,
        content: String,
        opensAtMillis: Long,
        sharedWith: List<String>
    ): Task<Void> {
        val user = auth.currentUser ?: return Tasks.forException(IllegalStateException("로그인이 필요해요."))
        if (sharedWith.distinct().size > 8) return Tasks.forException(IllegalArgumentException("캡슐 하나는 최대 8명과 공유할 수 있어요."))
        val capsuleRef = db.collection("capsules").document()
        val data = mapOf(
            "ownerUid" to user.uid,
            "ownerNickname" to (user.email?.substringBefore('@') ?: "사용자"),
            "title" to title,
            "place" to place,
            "content" to content,
            "opensAt" to Timestamp(Date(opensAtMillis)),
            "sharedWith" to sharedWith.distinct(),
            "createdAt" to FieldValue.serverTimestamp()
        )
        val batch = db.batch().set(capsuleRef, data)
        sharedWith.distinct().forEach { friendUid ->
            val shareRef = db.collection("users").document(friendUid)
                .collection("sharedCapsules").document(capsuleRef.id)
            batch.set(shareRef, mapOf(
                "capsuleId" to capsuleRef.id,
                "ownerUid" to user.uid,
                "opensAt" to Timestamp(Date(opensAtMillis)),
                "createdAt" to FieldValue.serverTimestamp()
            ))
        }
        return batch.commit()
    }

    fun listVisible(): Task<List<SharedCapsule>> {
        val uid = auth.currentUser?.uid ?: return Tasks.forException(IllegalStateException("로그인이 필요해요."))
        val mine = db.collection("capsules").whereEqualTo("ownerUid", uid).get(Source.SERVER)
        val sharedRefs = db.collection("users").document(uid).collection("sharedCapsules")
            .whereLessThanOrEqualTo("opensAt", Timestamp.now())
            .get(Source.SERVER)

        return Tasks.whenAll(mine, sharedRefs).continueWithTask { loaded ->
            if (!loaded.isSuccessful) return@continueWithTask Tasks.forException(loaded.exception ?: IllegalStateException("캡슐을 불러오지 못했어요."))
            val ownCapsules = mine.result.documents.mapNotNull { it.toCapsule() }
            val sharedReads: List<Task<SharedCapsule?>> = sharedRefs.result.documents.map { reference ->
                db.collection("capsules").document(reference.id).get(Source.SERVER).continueWith { read ->
                    // A removed friendship makes a stale share reference unreadable; skip it safely.
                    if (!read.isSuccessful) null else read.result.toCapsule()
                }
            }
            if (sharedReads.isEmpty()) {
                return@continueWithTask Tasks.forResult(ownCapsules.sortedByDescending { it.opensAtMillis })
            }
            Tasks.whenAllSuccess<SharedCapsule?>(*sharedReads.toTypedArray()).continueWith { read ->
                if (!read.isSuccessful) throw read.exception ?: IllegalStateException("공유 캡슐을 불러오지 못했어요.")
                (ownCapsules + read.result.filterNotNull()).distinctBy { it.id }
                    .sortedByDescending { it.opensAtMillis }
            }
        }
    }

    private fun DocumentSnapshot.toCapsule(): SharedCapsule? {
        val opensAt = getTimestamp("opensAt") ?: return null
        return SharedCapsule(
            id = id,
            ownerUid = getString("ownerUid") ?: return null,
            ownerNickname = getString("ownerNickname") ?: "사용자",
            title = getString("title") ?: return null,
            place = getString("place") ?: "",
            content = getString("content") ?: "",
            opensAtMillis = opensAt.toDate().time,
            sharedWith = get("sharedWith") as? List<String> ?: emptyList()
        )
    }
}
