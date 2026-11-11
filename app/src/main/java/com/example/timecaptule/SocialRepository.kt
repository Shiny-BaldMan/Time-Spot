package com.example.timecaptule

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.Locale

data class Friend(val uid: String, val nickname: String)
data class FriendRequest(
    val id: String,
    val fromUid: String,
    val toUid: String,
    val fromNickname: String,
    val toNickname: String
)

/** Nickname lookup, friend requests, and mutual friend relationships. */
class SocialRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun currentPid(): String? = auth.currentUser?.uid
    private fun currentNickname(): String? = auth.currentUser?.email?.substringBefore('@')
    private fun pairId(first: String, second: String) = listOf(first, second).sorted().joinToString("__")
    private fun requestId(from: String, to: String) = "${from}__${to}"

    fun sendFriendRequest(nicknameInput: String): Task<Friend> {
        val uid = currentPid() ?: return Tasks.forException(IllegalStateException("로그인이 필요해요."))
        val nickname = nicknameInput.trim().lowercase(Locale.ROOT)
        if (!nickname.matches(Regex("[a-z0-9_]{2,20}"))) {
            return Tasks.forException(IllegalArgumentException("닉네임은 영문·숫자·밑줄 2~20자로 입력해 주세요."))
        }
        val myNickname = currentNickname() ?: return Tasks.forException(IllegalStateException("계정 닉네임을 확인할 수 없어요."))
        return db.collection("friendLookup").document(nickname).get(Source.SERVER).continueWithTask lookup@{ lookup ->
            if (!lookup.isSuccessful) return@lookup Tasks.forException(lookup.exception ?: IllegalStateException("닉네임을 찾지 못했어요."))
            val targetUid = lookup.result.getString("uid")
                ?: return@lookup Tasks.forException(IllegalArgumentException("해당 닉네임의 사용자를 찾지 못했어요."))
            if (targetUid == uid) return@lookup Tasks.forException(IllegalArgumentException("내 닉네임으로 친구 요청을 보낼 수 없어요."))

            val friendshipRef = db.collection("friendships").document(pairId(uid, targetUid))
            val outgoingRef = db.collection("friendRequests").document(requestId(uid, targetUid))
            val incomingRef = db.collection("friendRequests").document(requestId(targetUid, uid))
            val relationTask = friendshipRef.get(Source.SERVER)
            val outgoingTask = outgoingRef.get(Source.SERVER)
            val incomingTask = incomingRef.get(Source.SERVER)
            Tasks.whenAll(relationTask, outgoingTask, incomingTask).continueWithTask checks@{ checks ->
                if (!checks.isSuccessful) return@checks Tasks.forException(checks.exception ?: IllegalStateException("친구 요청 상태를 확인하지 못했어요."))
                if (relationTask.result.exists()) return@checks Tasks.forException(IllegalStateException("이미 친구인 사용자예요."))
                if (outgoingTask.result.exists()) return@checks Tasks.forException(IllegalStateException("이미 보낸 요청이 있어요."))
                if (incomingTask.result.exists()) return@checks Tasks.forException(IllegalStateException("상대가 먼저 보낸 요청이 있어요. 받은 요청에서 수락해 주세요."))
                val targetNickname = lookup.result.getString("nickname") ?: nickname
                outgoingRef.set(mapOf(
                    "fromUid" to uid,
                    "toUid" to targetUid,
                    "fromNickname" to myNickname,
                    "toNickname" to targetNickname,
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp()
                )).continueWith { saved ->
                    if (!saved.isSuccessful) throw saved.exception ?: IllegalStateException("친구 요청을 보내지 못했어요.")
                    Friend(targetUid, targetNickname)
                }
            }
        }
    }

    fun incomingRequests(): Task<List<FriendRequest>> {
        val uid = currentPid() ?: return Tasks.forException(IllegalStateException("로그인이 필요해요."))
        return db.collection("friendRequests").whereEqualTo("toUid", uid)
            .whereEqualTo("status", "pending").get(Source.SERVER).continueWith { task ->
                if (!task.isSuccessful) throw task.exception ?: IllegalStateException("친구 요청을 불러오지 못했어요.")
                task.result.documents.mapNotNull { it.toFriendRequest() }
            }
    }

    fun acceptRequest(request: FriendRequest): Task<Void> {
        val uid = currentPid() ?: return Tasks.forException(IllegalStateException("로그인이 필요해요."))
        if (request.toUid != uid) return Tasks.forException(IllegalArgumentException("이 요청을 수락할 수 없어요."))
        val requestRef = db.collection("friendRequests").document(request.id)
        val friendshipRef = db.collection("friendships").document(pairId(request.fromUid, request.toUid))
        val batch = db.batch()
        batch.update(requestRef, mapOf("status" to "accepted", "respondedAt" to FieldValue.serverTimestamp()))
        batch.set(friendshipRef, mapOf(
            "uidA" to request.fromUid,
            "nicknameA" to request.fromNickname,
            "uidB" to request.toUid,
            "nicknameB" to request.toNickname,
            "requestId" to request.id,
            "createdAt" to FieldValue.serverTimestamp()
        ))
        return batch.commit()
    }

    fun rejectRequest(request: FriendRequest): Task<Void> {
        val uid = currentPid() ?: return Tasks.forException(IllegalStateException("로그인이 필요해요."))
        if (request.toUid != uid) return Tasks.forException(IllegalArgumentException("이 요청을 거절할 수 없어요."))
        return db.collection("friendRequests").document(request.id)
            .update(mapOf("status" to "rejected", "respondedAt" to FieldValue.serverTimestamp()))
    }

    fun listFriends(): Task<List<Friend>> {
        val uid = currentPid() ?: return Tasks.forException(IllegalStateException("로그인이 필요해요."))
        val asA = db.collection("friendships").whereEqualTo("uidA", uid).get(Source.SERVER)
        val asB = db.collection("friendships").whereEqualTo("uidB", uid).get(Source.SERVER)
        return Tasks.whenAll(asA, asB).continueWith { task ->
            if (!task.isSuccessful) throw task.exception ?: IllegalStateException("친구 목록을 불러오지 못했어요.")
            (asA.result.documents.mapNotNull { document ->
                val friendUid = document.getString("uidB") ?: return@mapNotNull null
                Friend(friendUid, document.getString("nicknameB") ?: return@mapNotNull null)
            } + asB.result.documents.mapNotNull { document ->
                val friendUid = document.getString("uidA") ?: return@mapNotNull null
                Friend(friendUid, document.getString("nicknameA") ?: return@mapNotNull null)
            }).distinctBy { it.uid }.sortedBy { it.nickname.lowercase(Locale.ROOT) }
        }
    }

    fun removeFriend(friendUid: String): Task<Void> {
        val uid = currentPid() ?: return Tasks.forException(IllegalStateException("로그인이 필요해요."))
        return db.collection("friendships").document(pairId(uid, friendUid)).delete()
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toFriendRequest(): FriendRequest? {
        val fromUid = getString("fromUid") ?: return null
        val toUid = getString("toUid") ?: return null
        val fromNickname = getString("fromNickname") ?: return null
        val toNickname = getString("toNickname") ?: return null
        return FriendRequest(id, fromUid, toUid, fromNickname, toNickname)
    }
}
