/**
 * functions 순수 함수 자체검사. 네트워크·Firebase 없이 도는 assert 스크립트다.
 *   node functions/selfcheck.js
 * 테스트 러너(jest 등)를 새로 들이지 않으려고 이렇게 둔다.
 */
const assert = require("assert");
const fs = require("fs");
const path = require("path");
const { extractJson, sanitizeGuide } = require("./askGuide.core");
const { parseCrewIds, isOnRoster } = require("./crewRoster");

// ── askGuide ────────────────────────────────────────────────────
const sources = [
  {
    id: "m1",
    kind: "manual",
    body: "① 즉시 비상제동을 체결한다.\n② 관제사에게   즉시 보고한다.\n③ 승객 안내방송을 한다.",
  },
  { id: "r1", kind: "reg", body: "제42조(비상조치) 기관사는 지체 없이 조치하여야 한다." },
];

// 1) 코드펜스·군말이 붙어도 JSON 만 꺼낸다
const fenced =
  "네, 정리했습니다.\n```json\n" +
  '{"found":true,"conclusion":"먼저 비상제동을 체결하세요.",' +
  '"steps":[{"quote":"① 즉시 비상제동을 체결한다.","sourceId":"m1",' +
  '"highlights":[{"text":"비상제동","kind":"action"}]}],"cautions":["무리하게 이동하지 마세요."]}' +
  "\n```\n이상입니다.";
const a = sanitizeGuide(extractJson(fenced), sources);
assert.strictEqual(a.found, true);
assert.strictEqual(a.steps.length, 1);
assert.strictEqual(a.steps[0].verified, true);
assert.deepStrictEqual(a.steps[0].highlights, [{ text: "비상제동", kind: "action" }]);
assert.deepStrictEqual(a.cautions, ["무리하게 이동하지 마세요."]);

// 2) 공백·줄바꿈만 다른 quote 는 verified 여야 한다 (원문 "관제사에게   즉시")
const b = sanitizeGuide(
  { found: true, conclusion: "보고하세요.", steps: [{ quote: "② 관제사에게 즉시 보고한다.", sourceId: "m1" }] },
  sources
);
assert.strictEqual(b.steps[0].verified, true);
assert.deepStrictEqual(b.steps[0].highlights, []);

// 3) 없는 sourceId 를 쓴 step 은 통째로 버린다 → 남는 게 없으면 found:false
const c = sanitizeGuide(
  { found: true, conclusion: "…", steps: [{ quote: "① 즉시 비상제동을 체결한다.", sourceId: "없는id" }] },
  sources
);
assert.strictEqual(c.steps.length, 0);
assert.strictEqual(c.found, false);

// 4) 모델이 문구를 고쳐 쓰면 verified:false 로 표시한다(버리지는 않는다)
const d = sanitizeGuide(
  { found: true, conclusion: "…", steps: [{ quote: "즉시 비상 제동을 체결합니다.", sourceId: "m1" }] },
  sources
);
assert.strictEqual(d.steps.length, 1);
assert.strictEqual(d.steps[0].verified, false);

// 5) quote 의 부분 문자열이 아닌 highlight, 이상한 kind 는 버린다. 2개까지만 남는다.
const e = sanitizeGuide(
  {
    found: true,
    conclusion: "…",
    steps: [
      {
        quote: "② 관제사에게   즉시 보고한다.",
        sourceId: "m1",
        highlights: [
          { text: "기관사에게", kind: "contact" }, // quote 밖 → 제거
          { text: "관제사에게", kind: "몰라" }, // 잘못된 kind → 제거
          { text: "관제사에게", kind: "contact" },
          { text: "보고한다", kind: "action" },
          { text: "즉시", kind: "caution" }, // 3번째 → 잘림
        ],
      },
    ],
  },
  sources
);
assert.deepStrictEqual(e.steps[0].highlights, [
  { text: "관제사에게", kind: "contact" },
  { text: "보고한다", kind: "action" },
]);

// 6) 찾지 못한 경우
const f = sanitizeGuide({ found: false, conclusion: "제공된 자료에서 찾지 못했습니다.", steps: [] }, sources);
assert.strictEqual(f.found, false);
assert.deepStrictEqual(f.steps, []);
assert.deepStrictEqual(f.cautions, []);

// 7) 파싱 불가 출력
assert.strictEqual(extractJson("죄송합니다. 답변할 수 없습니다."), null);
assert.strictEqual(extractJson('```json\n{"found": true,\n```'), null); // 깨진 JSON
// 엉뚱한 모양(배열 등)은 안쪽 객체가 잡힐 수 있지만, 정리를 거치면 안전하게 "못 찾음"이 된다
assert.deepStrictEqual(sanitizeGuide(extractJson('[{"q":"배열은 아니다"}]'), sources), {
  found: false,
  conclusion: "",
  steps: [],
  cautions: [],
});

// 8) 단계 10개 상한
const many = {
  found: true,
  conclusion: "…",
  steps: Array.from({ length: 14 }, () => ({ quote: "제42조(비상조치)", sourceId: "r1" })),
};
assert.strictEqual(sanitizeGuide(many, sources).steps.length, 10);

// 8-1) 공백 글자 집합 — 앱(ManualFormat.normalizeWs)과 **같은 표**여야 한다.
//      같은 사례가 app/src/test/.../ManualFormatTest.kt 에도 있다. 한쪽만 고치면
//      서버는 "원문 일치"인데 앱은 "AI 정리"로 뜨는(또는 그 반대) 어긋남이 생긴다.
//      JS 의 \s = 탭·LF·VT·FF·CR·공백·NBSP·U+1680·U+2000~200A·U+2028·U+2029·U+202F·U+205F·U+3000·U+FEFF
const { normalizeSpace } = require("./askGuide.core");
// 보이지 않는 글자를 소스에 그대로 두면 편집기가 망가뜨린다 — 코드포인트 숫자로 만든다.
const cp = (n) => String.fromCharCode(n);
const WS_SAME = [0x09, 0x0a, 0x0b, 0x0c, 0x0d, 0x20, 0xa0, 0x1680, 0x2000, 0x200a,
  0x2028, 0x2029, 0x202f, 0x205f, 0x3000, 0xfeff].map(cp);
for (const ws of WS_SAME) {
  assert.strictEqual(normalizeSpace(`가${ws}${ws}나${ws}`), "가 나", `U+${ws.charCodeAt(0).toString(16)} 는 공백이다`);
}
// 공백이 **아닌** 것들 — 코틀린 trim()/isWhitespace 는 이것들을 공백으로 보므로 앱에서 따로 막았다
for (const notWs of [0x85, 0x1c, 0x1f, 0x200b, 0x180e].map(cp)) {
  assert.strictEqual(normalizeSpace(`가${notWs}나${notWs}`), `가${notWs}나${notWs}`);
}
// 전각 공백·NBSP 로 띄운 원문을 보통 공백으로 인용해도 일치다
const wsSources = [
  { id: "w1", kind: "manual", body: `열차무선방호${cp(0x3000)}실시 및${cp(0xa0)}운전관제 급보` },
];
const ws1 = sanitizeGuide(
  { found: true, conclusion: "…", steps: [{ quote: "열차무선방호 실시 및 운전관제 급보", sourceId: "w1" }] },
  wsSources
);
assert.strictEqual(ws1.steps[0].verified, true);
// 자르기(300자)가 정규화보다 먼저다: 300자 밖에서만 달라지는 인용은 일치로 남는다
const longBody = "가".repeat(299) + "나다라";
const ws2 = sanitizeGuide(
  { found: true, conclusion: "…", steps: [{ quote: "가".repeat(299) + "나XX", sourceId: "L" }] },
  [{ id: "L", kind: "reg", body: longBody }]
);
assert.strictEqual(ws2.steps[0].quote.length, 300);
assert.strictEqual(ws2.steps[0].verified, true);

// 8-2) 흐름도 글 표식이 든 본문(매뉴얼 스키마 v2). 앱이 alt 구간을 두 표식 줄로 감싸 보낸다.
//      대조는 "모델에 준 본문" 기준이라 표식이 있어도 그대로 맞아야 하고,
//      표식 줄을 건너뛰어 앞뒤를 이어 붙인 인용은 원문이 아니다(실제로 그 자리에 표식 줄이 있다).
const FLOW_OPEN = "〔흐름도 글 — 도형 안 문구 모음. 나열 순서는 절차 순서가 아님〕";
const FLOW_CLOSE = "〔흐름도 글 끝〕";
const flowSources = [
  {
    id: "s014",
    kind: "manual",
    body:
      "기관사 | 열차무선방호 실시 및\n운전관제 급보\n사고흐름도\n" +
      `${FLOW_OPEN}\n상황 발생\n관제보고 및 안내방송\nYES\nNO\n해당 칸 출입문 차단\n${FLOW_CLOSE}\n` +
      "역무원 | 승객 대피 유도",
  },
];
const flow = sanitizeGuide(
  {
    found: true,
    conclusion: "순서는 흐름도 그림을 확인하세요.",
    steps: [
      { quote: "열차무선방호 실시 및 운전관제 급보", sourceId: "s014" }, // 표 줄
      { quote: "관제보고 및 안내방송", sourceId: "s014", highlights: [{ text: "안내방송", kind: "action" }] }, // 흐름도 구간 안
      { quote: "역무원 | 승객 대피 유도", sourceId: "s014" }, // 표식 뒤의 표 줄
      { quote: "사고흐름도 상황 발생", sourceId: "s014" }, // 표식 줄을 건너뛴 인용 → 원문 아님
    ],
  },
  flowSources
);
assert.deepStrictEqual(
  flow.steps.map((s) => s.verified),
  [true, true, true, false]
);
assert.deepStrictEqual(flow.steps[1].highlights, [{ text: "안내방송", kind: "action" }]);
// 표식 글자는 앱(ManualFormat.FLOW_OPEN/FLOW_CLOSE)·서버 프롬프트(index.js)·이 검사 셋이 같아야 한다.
// 한쪽만 고치면 프롬프트가 가리키는 줄이 본문에 없어서 "나열 순서를 절차로 읽지 말라"는 규칙이 허공에 뜬다.
const appFormat = fs.readFileSync(
  path.join(__dirname, "..", "app", "src", "main", "java", "com", "sinjeong", "safety", "data", "ManualFormat.kt"),
  "utf8"
);
const serverIndex = fs.readFileSync(path.join(__dirname, "index.js"), "utf8");
for (const [name, text] of [["ManualFormat.kt", appFormat], ["index.js", serverIndex]]) {
  assert.ok(text.includes(`"${FLOW_OPEN}"`), `${name} 의 흐름도 글 여는 표식이 selfcheck 와 다릅니다`);
  assert.ok(text.includes(`"${FLOW_CLOSE}"`), `${name} 의 흐름도 글 닫는 표식이 selfcheck 와 다릅니다`);
}

// ── 명단 판정 (verifyCrew) ──────────────────────────────────────
// 9) functions/crew_ids.txt 는 app/src/main/assets/crew_ids.txt 의 복사본이어야 한다.
//    배포 패키지에 assets 가 없어 복사해 두는데, 한쪽만 고치면 앱과 서버 판정이 어긋난다.
const assetIds = fs.readFileSync(
  path.join(__dirname, "..", "app", "src", "main", "assets", "crew_ids.txt"),
  "utf8"
);
const copyIds = fs.readFileSync(path.join(__dirname, "crew_ids.txt"), "utf8");
assert.strictEqual(
  copyIds,
  assetIds,
  "functions/crew_ids.txt 가 app/src/main/assets/crew_ids.txt 와 다릅니다 — 둘을 같이 고치세요"
);

// 10) 파싱: 8자리 숫자만, 공백·빈 줄·잡소리는 버린다 (앱 loadRoster 와 같은 규칙)
const parsed = parseCrewIds("21702453\r\n  21703253  \n\n7digit1\n217032530\n이름\n21703303");
assert.deepStrictEqual([...parsed].sort(), ["21702453", "21703253", "21703303"]);

// 11) 실제 명단은 282명, 전부 8자리
const base = parseCrewIds(copyIds);
assert.strictEqual(base.size, 282);
assert.ok([...base].every((id) => /^[0-9]{8}$/.test(id)));

// 12) 판정 네 갈래
const someone = [...base][0];
assert.strictEqual(isOnRoster(someone, { base }), true, "기본 명단에 있는 직원");
assert.strictEqual(
  isOnRoster("99999999", { base, extraIds: ["99999999"] }),
  true,
  "extraIds 에만 있는 신입"
);
assert.strictEqual(
  isOnRoster(someone, { base, removedIds: [someone] }),
  false,
  "removedIds 의 퇴직자는 기본 명단에 있어도 차단"
);
assert.strictEqual(
  isOnRoster("99999999", { base, extraIds: ["99999999"], removedIds: ["99999999"] }),
  false,
  "extra 와 removed 에 동시에 있으면 removed 가 이긴다"
);
assert.strictEqual(isOnRoster("88888888", { base }), false, "명단 밖 사번은 차단");
assert.strictEqual(isOnRoster("", { base }), false);
assert.strictEqual(isOnRoster("admin", { base }), false, "admin 은 사번이 아니라 index.js 가 따로 통과시킨다");
// 숫자가 아닌 값이 섞여 들어와도 터지지 않는다 (config/roster 는 사람이 콘솔에서 고친다)
assert.strictEqual(isOnRoster("99999999", { base, extraIds: [99999999, null] }), true);

console.log("selfcheck: askGuide 8항목 + 공백·자르기 대조 + 흐름도 글 표식 + 명단 4항목 모두 통과");
