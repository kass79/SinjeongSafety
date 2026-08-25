package com.sinjeong.safety.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
    suspend fun effectiveRoster(context: Context): Set<String> {
        val base = loadRoster(context)

        val doc = runCatching {
            db.collection("config").document("roster").get().await()
        }.getOrNull()

        val extra = (doc?.get("extraIds") as? List<*>)
            ?.mapNotNull { it?.toString()?.trim() }?.toSet() ?: emptySet()
        val removed = (doc?.get("removedIds") as? List<*>)
            ?.mapNotNull { it?.toString()?.trim() }?.toSet() ?: emptySet()

        return (base + extra) - removed
    }

    suspend fun isInRoster(context: Context, empNo: String): Boolean =
        effectiveRoster(context).contains(empNo.trim())

    // ── 명단 관리 (관리자 전용) ──────────────────────────────────
    // config/roster       : { extraIds: [...], removedIds: [...] }   인사이동 델타
    // config/rosterNames  : { names: { 사번: 이름 } }                 전 직원 실명
    // 두 문서 모두 없을 수 있으므로 쓰기는 전부 set + merge 로 한다.
    private val rosterDoc get() = db.collection("config").document("roster")
    private val namesDoc get() = db.collection("config").document("rosterNames")

    /**
     * 사번 → 이름. 규칙상 관리자만 읽을 수 있다(전 직원 실명이라 일반 공개 금지).
     * 실명은 저장소·APK 어디에도 두지 않는다 — Firestore 에만 있다.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun rosterNames(): Map<String, String> = try {
        val names = namesDoc.get().await().get("names") as? Map<String, Any?>
        names.orEmpty()
            .mapNotNull { (k, v) -> v?.toString()?.takeIf { it.isNotBlank() }?.let { k to it } }
            .toMap()
    } catch (e: Exception) {
        Log.e("CrewRepository", "config/rosterNames 조회 실패", e)
        emptyMap()
    }

    /**
     * 퇴직 처리된 사번 목록. **조회에 실패하면 null 을 돌려준다.**
     * 부르는 쪽(로그인 차단)은 null 이면 막지 않아야 한다. 이 앱은 터널을 대비한 오프라인
     * 동작이 설계 원칙이라, config/roster 를 못 읽는 상황이 정상적으로 생긴다.
     * 그때 "명단에 없으면 차단"으로 만들면 extraIds 에만 있는 신입사원이 통째로 갇힌다.
     */
    suspend fun removedIds(): Set<String>? = runCatching {
        (rosterDoc.get().await().get("removedIds") as? List<*>)
            ?.mapNotNull { it?.toString()?.trim() }?.toSet() ?: emptySet()
    }.getOrNull()

    /**
     * 신입사원 등록. 이미 명단에 있으면 아무것도 하지 않고 false 를 돌려준다.
     *
     * 이름은 점 표기가 아니라 중첩 맵으로 넣는다. update() 와 달리 set() 에
     * "names.12345678" 을 넘기면 점이 들어간 **필드 이름 하나**가 새로 생겨 버린다.
     * merge 는 중첩 맵을 깊게 합치므로 기존 282명은 그대로 남는다.
     */
    suspend fun addCrew(context: Context, empNo: String, name: String): Boolean {
        val no = empNo.trim()
        if (isInRoster(context, no)) return false
        rosterDoc.set(
            mapOf(
                "extraIds" to FieldValue.arrayUnion(no),
                // 재입사·오처리 정정: 퇴직 목록에 남아 있으면 빼준다
                "removedIds" to FieldValue.arrayRemove(no)
            ),
            SetOptions.merge()
        ).await()
        namesDoc.set(mapOf("names" to mapOf(no to name.trim())), SetOptions.merge()).await()
        return true
    }

    /**
     * 퇴직 처리.
     * - 이름은 rosterNames 에서 **지우지 않는다.** 과거 확인 기록·포인트 집계가 그 이름을 쓴다.
     * - extraIds 도 건드리지 않는다. effectiveRoster 가 (기본 + extra) - removed 라
     *   removedIds 한 줄이면 충분하고, 여기서 extraIds 까지 지우면 나중에 복귀시킬 때
     *   기본 명단에 없는 신입사원이 명단에서 영영 사라진다.
     */
    suspend fun retireCrew(empNo: String) {
        rosterDoc.set(
            mapOf("removedIds" to FieldValue.arrayUnion(empNo.trim())),
            SetOptions.merge()
        ).await()
    }

    /** 퇴직 취소(복귀) */
    suspend fun unretireCrew(empNo: String) {
        rosterDoc.set(
            mapOf("removedIds" to FieldValue.arrayRemove(empNo.trim())),
            SetOptions.merge()
        ).await()
    }

    /**
     * 사번 → 이름. 등록하면서 이름을 남긴 사람만 들어 있다.
     * 보안 규칙상 관리자만 crew 컬렉션 전체를 읽을 수 있으므로,
     * 승무원이 부르면 빈 값이 돌아온다(확인 현황은 관리자 화면이다).
     */
    suspend fun allNames(): Map<String, String> = runCatching {
        crewRef.get().await().documents.mapNotNull { d ->
            val n = d.getString("name")?.trim()
            if (n.isNullOrBlank()) null else d.id to n
        }.toMap()
    }.getOrDefault(emptyMap())

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

        /**
         * 직원 포인트 현황·직원 명단 관리를 여는 두 사람의 사번. 사용자 지정 2026-08-25.
         * 관리자 계정(ADMIN_EMAIL)이라도 이 사번으로 확인한 기기가 아니면 두 메뉴가 보이지 않는다.
         * 이름은 적지 않는다 — 실명은 저장소·APK 에 두지 않고 Firestore config/rosterNames 에만 둔다.
         */
        val DEV_EMP_NOS = setOf("21713087", "22200311")
    }
}
