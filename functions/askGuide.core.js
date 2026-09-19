/**
 * askGuide 의 순수 함수들 (네트워크·Firebase 의존 없음).
 * index.js 가 가져다 쓰고, askGuide.selfcheck.js 가 그대로 검사한다.
 * 모델 출력은 믿을 수 없으므로 "파싱 → 정리 → 원문 대조" 세 단계를 여기서 다 끝낸다.
 */

const MAX_STEPS = 10;
const MAX_QUOTE = 300;
const MAX_HIGHLIGHTS = 2;
const MAX_CAUTIONS = 3;
const HIGHLIGHT_KINDS = new Set(["action", "contact", "caution"]);

/** 연속 공백·줄바꿈을 공백 하나로. 원문 대조는 이 모양으로만 한다. */
function normalizeSpace(value) {
  return String(value == null ? "" : value).replace(/\s+/g, " ").trim();
}

/**
 * 모델이 코드펜스나 군말을 붙여도 JSON 객체 하나만 꺼낸다. 실패하면 null.
 * (generateQuiz 가 배열에 쓰는 것과 같은 수법 — 여기는 객체라 { ~ } 로 자른다)
 */
function extractJson(text) {
  const trimmed = String(text || "")
    .trim()
    .replace(/^```[a-zA-Z]*\s*/, "")
    .replace(/```\s*$/, "");
  const start = trimmed.indexOf("{");
  const end = trimmed.lastIndexOf("}");
  if (start < 0 || end <= start) return null;
  try {
    const parsed = JSON.parse(trimmed.slice(start, end + 1));
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : null;
  } catch (e) {
    return null;
  }
}

/**
 * 모델이 준 객체를 앱이 믿을 수 있는 모양으로 깎는다.
 * - sources 에 없는 sourceId 를 쓴 step 은 통째로 버린다(근거를 못 여는 단계는 무의미).
 * - highlights 는 quote 의 부분 문자열이 아니면 버린다(앱이 하이라이트를 못 찾아 깨진다).
 * - verified: 공백 정규화 후 quote 가 그 출처 본문(모델에 준 잘린 본문)에 그대로 있는지.
 *   앱도 다시 검사하지만 서버 값이 기본이다.
 * sources 는 { id, body } 를 가진 배열(= 모델에 실제로 준 것).
 */
function sanitizeGuide(raw, sources) {
  const bodyById = new Map(
    (Array.isArray(sources) ? sources : []).map((s) => [String(s.id), normalizeSpace(s.body)])
  );

  const steps = [];
  for (const step of Array.isArray(raw?.steps) ? raw.steps : []) {
    if (!step || typeof step !== "object") continue;
    const sourceId = String(step.sourceId || "");
    if (!bodyById.has(sourceId)) continue;
    const quote = String(step.quote || "").slice(0, MAX_QUOTE);
    if (!quote.trim()) continue;

    const highlights = [];
    for (const h of Array.isArray(step.highlights) ? step.highlights : []) {
      if (!h || typeof h !== "object") continue;
      const text = String(h.text || "");
      const kind = String(h.kind || "");
      if (!text.trim() || !HIGHLIGHT_KINDS.has(kind)) continue;
      if (!quote.includes(text)) continue;
      highlights.push({ text, kind });
      if (highlights.length >= MAX_HIGHLIGHTS) break;
    }

    steps.push({
      quote,
      sourceId,
      highlights,
      verified: bodyById.get(sourceId).includes(normalizeSpace(quote)),
    });
    if (steps.length >= MAX_STEPS) break;
  }

  const cautions = (Array.isArray(raw?.cautions) ? raw.cautions : [])
    .filter((c) => typeof c === "string" && c.trim())
    .slice(0, MAX_CAUTIONS)
    .map((c) => c.slice(0, 300));

  return {
    found: raw?.found === true && steps.length > 0,
    conclusion: String(raw?.conclusion || "").slice(0, 600),
    steps,
    cautions,
  };
}

module.exports = { normalizeSpace, extractJson, sanitizeGuide, MAX_STEPS, MAX_QUOTE };
