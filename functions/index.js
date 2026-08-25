const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { initializeApp } = require("firebase-admin/app");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

// Anthropic API 키. 코드·저장소에 두지 않고 Secret Manager에 보관한다.
// 등록:  firebase functions:secrets:set ANTHROPIC_API_KEY --project sinjeongsafety
//
// ※ 함정 — 키를 새로 넣은 뒤에는 반드시 이 함수들이 '실제로' 재배포돼야 한다.
//   배포된 함수는 그때의 비밀 '버전'을 고정해 물고 있어서, 콘솔에서 키만 바꾸면
//   서버는 옛 키를 계속 쓴다. 그런데 코드가 그대로면 CLI가 "No changes detected"로
//   건너뛰어 버려 재배포가 안 된다. 그럴 때는 아래 숫자를 올려 해시를 바꾼다.
//   KEY_ROTATION = 2  (2026-08-22 키 교체)
const anthropicKey = defineSecret("ANTHROPIC_API_KEY");

/**
 * posts 컬렉션에 새 문서가 생기면 "new_posts" 토픽 구독자 전원에게 푸시 발송
 * 리전: 서울 (asia-northeast3)
 */
exports.notifyNewPost = onDocumentCreated(
  { document: "posts/{postId}", region: "asia-northeast3" },
  async (event) => {
    const post = event.data?.data();
    if (!post) return;

    const title = `📢 ${post.category || "새 안전정보"}`;
    const body = post.title || "새 게시물이 등록되었습니다";

    await getMessaging().send({
      topic: "new_posts",
      notification: { title, body },
      data: { postId: event.params.postId, title, body },
      android: {
        priority: "high",
        notification: { channelId: "new_posts", icon: "ic_notification" },
      },
    });
    console.log(`푸시 발송 완료: ${body}`);
  }
);

// ── AI 기능 공통 ─────────────────────────────────────────────────
// 셋 다 같은 뼈대다: 로그인 확인 → Claude 호출 → 텍스트 반환.
// 앱이 아니라 여기서 키를 쥐고 있으므로 APK를 뜯어도 키가 새지 않는다.

const AI_OPTS = { region: "asia-northeast3", secrets: [anthropicKey], timeoutSeconds: 120 };

/** 로그인한 사용자(승무원·관리자)만 AI를 쓸 수 있다. 익명 호출로 요금이 새는 것을 막는다. */
function requireAuth(request) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "로그인이 필요합니다");
  }
}

/** Claude 호출. 안전분류기가 거부하면 Opus 계열로 자동 우회(fallbacks)한다. */
async function askClaude({ system, user, maxTokens = 2000 }) {
  const Anthropic = require("@anthropic-ai/sdk");
  const client = new Anthropic({ apiKey: anthropicKey.value() });
  const response = await client.beta.messages.create({
    model: "claude-opus-5",
    max_tokens: maxTokens,
    betas: ["server-side-fallback-2026-07-01"],
    fallbacks: "default",
    system,
    messages: [{ role: "user", content: user }],
  });
  if (response.stop_reason === "refusal") {
    throw new HttpsError("failed-precondition", "답변할 수 없는 요청입니다");
  }
  return response.content
    .filter((b) => b.type === "text")
    .map((b) => b.text)
    .join("");
}

/**
 * 규정 자연어 질문 (2단계 AI 답변).
 * 앱이 기기 안 검색(RegulationSearch)으로 추린 조문을 함께 보내면,
 * 그 조문만 근거로 답한다. 626조문 전체를 서버에 둘 필요가 없고,
 * 근거가 눈에 보여야 승무원이 답을 믿고 원문을 확인할 수 있다.
 */
exports.askRegulation = onCall(AI_OPTS, async (request) => {
  requireAuth(request);
  const question = String(request.data?.question || "").trim();
  const articles = Array.isArray(request.data?.articles) ? request.data.articles : [];
  if (!question || question.length > 500) {
    throw new HttpsError("invalid-argument", "질문을 확인해주세요");
  }
  if (articles.length === 0 || articles.length > 8) {
    throw new HttpsError("invalid-argument", "근거 조문이 없습니다");
  }

  const context = articles
    .map((a) => `[${a.n} ${a.t}]\n${String(a.b || "").slice(0, 2000)}`)
    .join("\n\n");

  const answer = await askClaude({
    system:
      "당신은 서울교통공사 신정승무사업소의 베테랑 지도승무원입니다. " +
      "후배 기관사가 규정을 물으면 제공된 조문만 근거로 답합니다.\n\n" +
      "답변 구조(이 순서를 지키세요):\n" +
      "1) 결론 — 질문에 대한 답을 한두 문장으로 먼저. 조치 순서를 묻는 질문이면 번호 매긴 단계로.\n" +
      "2) 근거 — 어느 조문의 어느 내용인지. 조문 번호를 반드시 인용하고, 원문 표현을 살려서.\n" +
      "3) 실무 유의 — 제공된 조문 안에 주의사항·예외·함께 봐야 할 내용이 있으면 한두 줄. 없으면 생략.\n\n" +
      "규칙:\n" +
      "- 제공된 조문 밖의 내용은 절대 지어내지 마세요. 조문으로 답이 안 되면 " +
      "'제공된 조문에서는 확인되지 않습니다. 검색어를 바꿔 보시거나 원문을 확인하세요'라고 말하세요.\n" +
      "- 질문과 무관한 조문이 섞여 있으면 무시하세요(검색이 기계적으로 골라온 것입니다).\n" +
      "- 마크다운 기호(**, ##) 없이 일반 텍스트로. 번호와 줄바꿈만 쓰세요.\n" +
      "- 터널·승강장에서 급히 읽는 사람입니다. 짧은 문장, 존댓말.\n" +
      "- 마지막 줄: '※ 정확한 내용은 원문 조문을 확인하세요.'",
    user: `승무원 질문: ${question}\n\n관련 조문:\n${context}`,
  });
  return { answer };
});

/** 게시물 3줄 요약. 글쓰기 화면에서 관리자가 검토 후 붙인다(AI 결과는 초안). */
exports.summarizePost = onCall(AI_OPTS, async (request) => {
  requireAuth(request);
  const title = String(request.data?.title || "").trim();
  const content = String(request.data?.content || "").trim();
  if (!content || content.length > 20000) {
    throw new HttpsError("invalid-argument", "본문을 확인해주세요");
  }

  const summary = await askClaude({
    system:
      "지하철 승무원용 안전정보 게시물을 요약합니다. " +
      "핵심만 정확히 3줄로, 각 줄은 '- '로 시작하세요. " +
      "숫자·역명·호선은 원문 그대로 유지하고, 원문에 없는 내용을 만들지 마세요.",
    user: `제목: ${title}\n\n본문:\n${content}`,
    maxTokens: 1000,
  });
  return { summary };
});

/**
 * 사고사례 퀴즈 생성. "읽었다"가 아니라 "이해했다"를 확인하기 위한 문제.
 * JSON으로 받아 파싱 실패 시 에러를 돌려준다(관리자가 검토 후 게시하는 초안이다).
 */
exports.generateQuiz = onCall(AI_OPTS, async (request) => {
  requireAuth(request);
  const title = String(request.data?.title || "").trim();
  const content = String(request.data?.content || "").trim();
  if (!content || content.length > 20000) {
    throw new HttpsError("invalid-argument", "본문을 확인해주세요");
  }

  // OX 로 출제한다. 현장에서 출무 전에 빨리 풀어야 하므로 4지선다보다 OX 가 맞고,
  // 찍기 확률이 50%라 문항을 3개로 늘려 균형을 잡는다. (사용자 결정 2026-08-25)
  const text = await askClaude({
    system:
      "지하철 승무원 안전교육 출제위원입니다. 주어진 사고사례·안전정보에서 " +
      "실무에 중요한 핵심을 확인하는 OX 문제 3개를 만드세요. " +
      "문제는 평서문으로 쓰고 맞으면 O, 틀리면 X 가 정답입니다. " +
      "X 가 정답인 문제는 본문 내용을 살짝 비틀어 만드세요(예: 순서 바꾸기, 조건 바꾸기). " +
      "정답이 O만 3개 또는 X만 3개가 되지 않게 섞으세요. " +
      "본문에 명시된 내용만 출제하고, 다음 JSON 배열만 출력하세요(다른 텍스트 금지): " +
      '[{"q":"문제 문장","choices":["O","X"],"answer":0,"explain":"해설"}] ' +
      "answer는 0=O, 1=X 입니다.",
    user: `제목: ${title}\n\n본문:\n${content}`,
    maxTokens: 2000,
  });

  // 모델이 JSON 앞뒤에 군말을 붙이는 경우를 대비해 배열 부분만 잘라 파싱한다.
  const match = text.match(/\[[\s\S]*\]/);
  let questions;
  try {
    questions = JSON.parse(match ? match[0] : text);
  } catch (e) {
    throw new HttpsError("internal", "문제 생성에 실패했습니다. 다시 시도해주세요");
  }
  if (!Array.isArray(questions) || questions.length === 0) {
    throw new HttpsError("internal", "문제 생성에 실패했습니다. 다시 시도해주세요");
  }
  return { questions };
});
