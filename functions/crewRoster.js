/**
 * 명단 판정 (순수 함수 + 기본 명단 읽기).
 *
 * 왜 서버가 판정하나 — 가입(createUserWithEmailAndPassword)은 앱 안에서만 명단을 확인하므로,
 * 앱 밖에서 아무 8자리@sinjeong.app 계정이나 만들 수 있다. 매뉴얼은 실명이 든 자료라
 * "로그인했다"만으로는 부족하고 "명단에 있는 직원"까지 서버가 확인해야 한다.
 *
 * ※ functions/crew_ids.txt 는 app/src/main/assets/crew_ids.txt 의 **복사본**이다.
 *   배포 패키지에는 functions/ 만 올라가서 assets 를 읽을 수 없다.
 *   한쪽만 고치면 앱과 서버 판정이 어긋나므로 반드시 둘을 같이 고친다(selfcheck 가 대조한다).
 */
const fs = require("fs");
const path = require("path");

/** 앱(CrewRepository.loadRoster)과 같은 규칙: 줄 trim 후 8자리 숫자만 남긴다. */
function parseCrewIds(text) {
  return new Set(
    String(text || "")
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter((line) => /^[0-9]{8}$/.test(line))
  );
}

let baseCache = null;
/** 기본 명단(복사본). 콜드스타트에 한 번만 읽는다. */
function baseRoster() {
  if (!baseCache) {
    baseCache = parseCrewIds(fs.readFileSync(path.join(__dirname, "crew_ids.txt"), "utf8"));
  }
  return baseCache;
}

/**
 * 앱과 같은 식: (기본 명단 ∪ extraIds) − removedIds.
 * 앱은 조회 실패 시 기본 명단만으로 통과시키지만(터널 오프라인 대비), 여기서는
 * 조회 실패 자체를 부르는 쪽이 unavailable 로 되돌린다 — 실패를 '통과'로 쓰지 않는다.
 */
function isOnRoster(empNo, { base, extraIds = [], removedIds = [] }) {
  const no = String(empNo || "").trim();
  if (!/^[0-9]{8}$/.test(no)) return false;
  const norm = (arr) => (Array.isArray(arr) ? arr.map((v) => String(v == null ? "" : v).trim()) : []);
  if (norm(removedIds).includes(no)) return false;
  return base.has(no) || norm(extraIds).includes(no);
}

module.exports = { parseCrewIds, baseRoster, isOnRoster };
