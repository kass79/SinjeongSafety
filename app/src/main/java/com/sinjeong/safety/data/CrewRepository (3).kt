package com.sinjeong.safety.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * 승무원 로그인 담당.
 *
 * 설계 메모
 * - 사번 8자리를 "사번@sinjeong.app" 이메일로 바꿔 Firebase Auth 계정을 만든다.
 *   비밀번호(PIN 6자리)는 Firebase가 서버에서 보관하므로 앱이 PIN 값을 저장하지 않는다.
 * - 명단(assets/crew_ids.txt)에는 사번만 들어간다. 이름을 넣으면 APK에서 전 직원 명부를
 *   통째로 빼낼 수 있으므로 이름은 본인이 등록할 때 입력받아 Firestore crew 문서에 저장한다.
 * - PIN 분실 시에는 관리자가 Firebase 콘솔에서 해당 계정을 삭제한다. crew 문서는 남아 있으므로
 *   재등록해도 이름·기록이 유지된다.
 */
class CrewRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val crewRef = db.collection("crew")

    /** 사번 명단 (assets에서 1회 읽어 캐시) */
    private var roster: Set<String>? = null

    fun loadRoster(context: Context): Set<String> {
        roster?.let { return it }
        val set = try {
            context.assets.open("crew_ids.txt").bufferedReader().useLines { lines ->
                lines.map { it.trim() }.filter { it.length == 8 && it.all(Char::isDigit) }.toSet()
            }
        } catch (e: Exception) {
            emptySet()
        }
        roster = set
        return set
    }

    /**
     * 명단 확인.
     * 기본 명단은 앱에 들어 있고, 인사이동분만 Firestore  config/roster  문서에서 읽는다.
     *   extraIds  : 새로 들어온 사람 (추가)
     *   removedIds: 나간 사람 (제외)
     * 서버를 못 읽으면 앱에 든 기본 명단만으로 판단한다.
     */
    suspend fun isInRoster(context: Context, empNo: String): Boolean {
        val no = empNo.trim()
        val base = loadRoster(context)

        val doc = runCatching {
            db.collection("config").document("roster").get().await()
        }.getOrNull()

        val extra = (doc?.get("extraIds") as? List<*>)
            ?.mapNotNull { it?.toString()?.trim() }?.toSet() ?: emptySet()
        val removed = (doc?.get("removedIds") as? List<*>)
            ?.mapNotNull { it?.toString()?.trim() }?.toSet() ?: emptySet()

        if (removed.contains(no)) return false
        return base.contains(no) || extra.contains(no)
    }

    // ── 계정 상태 ────────────────────────────────────────────────
    private fun emailOf(empNo: String) = "${empNo.trim()}@$CREW_DOMAIN"

    /** 현재 로그인한 계정의 사번. 관리자이거나 비로그인이면 null */
    fun currentEmpNo(): String? {
        val email = auth.currentUser?.email ?: return null
        if (email == ADMIN_EMAIL) return null
        val local = email.substringBefore("@")
        return if (local.length == 8 && local.all(Char::isDigit)) local else null
    }

    fun isCrewLoggedIn(): Boolean = currentEmpNo() != null

    /** 최초 등록: 계정 생성 후 crew 문서에 이름을 기록한다. */
    suspend fun register(empNo: String, name: String, pin: String) {
        val no = empNo.trim()
        auth.createUserWithEmailAndPassword(emailOf(no), pin).await()
        crewRef.document(no).set(
            mapOf(
                "empNo" to no,
                "name" to name.trim(),
                "createdAt" to FieldValue.serverTimestamp(),
                "lastLoginAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    /** 로그인. 이름이 이미 있으면 그대로 두고 접속 시각만 갱신한다. */
    suspend fun signIn(empNo: String, pin: String) {
        val no = empNo.trim()
        auth.signInWithEmailAndPassword(emailOf(no), pin).await()
        runCatching {
            crewRef.document(no).set(
                mapOf("lastLoginAt" to FieldValue.serverTimestamp()),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
        }
    }

    /**
     * 관리자가 계정을 지운 뒤 다시 등록하는 경우.
     * crew 문서에 이름이 남아 있으면 그 이름을 돌려준다. 없으면 null.
     */
    suspend fun savedName(empNo: String): String? = runCatching {
        crewRef.document(empNo.trim()).get().await().getString("name")
    }.getOrNull()

    // ── 게시글 즐겨찾기 ──────────────────────────────────────
    /** 계정에 저장된 즐겨찾기 목록. 로그인 상태가 아니면 빈 값. */
    suspend fun loadFavorites(): Set<String> {
        val no = currentEmpNo() ?: return emptySet()
        val doc = runCatching { crewRef.document(no).get().await() }.getOrNull()
        return (doc?.get("favorites") as? List<*>)
            ?.mapNotNull { it?.toString() }?.toSet() ?: emptySet()
    }

    /** 즐겨찾기 목록을 통째로 저장한다. */
    suspend fun saveFavorites(ids: Set<String>) {
        val no = currentEmpNo() ?: return
        crewRef.document(no).set(
            mapOf("favorites" to ids.toList()),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    fun signOut() = auth.signOut()

    /**
     * 로그인 강제 스위치. Firestore  config/auth  문서의  requireLogin  값을 읽는다.
     * 값을 못 읽으면 false(=강제하지 않음)로 둔다. 앱이 열리지 않는 사고를 막기 위한 안전값이다.
     */
    suspend fun requireLogin(): Boolean = runCatching {
        db.collection("config").document("auth").get().await().getBoolean("requireLogin") ?: false
    }.getOrDefault(false)

    companion object {
        const val CREW_DOMAIN = "sinjeong.app"
        const val ADMIN_EMAIL = "admin@sinjeong.app"
    }
}
