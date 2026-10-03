# 슬기로운 승무생활 (신정승무사업소 안전앱)

Android(Kotlin/Compose) + Firebase(SinjeongSafety/sinjeongsafety 프로젝트). 승무원용 안전정보·규정 앱.
사용자(카스)는 비개발자 기관사입니다. 코드 조각 대신 **동작하는 APK와 스크린샷**으로 보고하세요.

## 현재 상태 (2026-08-22 기준 — 숫자는 build.gradle.kts를 믿을 것)

- **버전 숫자는 항상 build.gradle.kts에서 직접 확인할 것** (이 문서의 스냅샷은 금방 낡는다. v1.3.4/21까지 나감)
- v1.0.5 이후: 확인 현황+CSV/PDF → 질의응답 게시판 → 날씨(현황 API·위치 옵션·위치 특보) →
  출무점호(계층 파싱·첨부·지난 점호 불러오기·월별 정리) → AI(규정 답변+3줄 요약) →
  댓글(실명제) → 검색창 BasicTextField → 로그아웃 게이트 수정(사번 인증된 기기는 안 막음)
- **홈 출무점호 카드 선택 순서: 오늘 자 > 내용 있는 최신 > 최신.** 이 순서를 깨면
  "올렸는데 안 보인다"(오늘 자가 밀림) 또는 "텅 빈 카드"(빈 문서가 최신) 사고가 재발한다. 둘 다 실제로 났다.
- 퀴즈(generateQuiz): 한 번 거부했다가 **2026-08-25 사용자가 다시 요청해 앱 연결 완료**("1번 적용시켜줘봐").
  관리자가 원하는 글에만 달고, 틀려도 해설 보고 확인 처리(통과 강제 없음), 정답률은 확인 현황·CSV·PDF에 기록.
- **Cloud Functions 배포됨**(asia-northeast3, Node22): notifyNewPost(푸시—2026-08-22에야 첫 배포됨),
  askRegulation / summarizePost / generateQuiz(OX 1문항) / extractImageText(공문 사진→본문, v1.13.0
  "사진 글로 정리" 버튼이 호출 — base64 는 **NO_WRAP** 필수, 기본값은 줄바꿈이 섞여 서버가 디코드 실패).
  Anthropic API 키는 Secret Manager `ANTHROPIC_API_KEY`. 모델 claude-opus-5. 로그인 사용자만 호출 가능.
- **askGuide (v1.17.0, 범위별 AI 질문)** — `askRegulation` 은 1.16.x 앱이 계속 쓰므로 **지우지 말 것**.
  요청 `{ question(1~500자), mode: "reg"|"manual"|"all", sources: [{ id(≤80), kind: "reg"|"manual",
  label(≤60), title(≤200), body }] }`. sources 1~8개, `kind=="manual"` 은 최대 4개.
  **본문은 서버가 자른다 — reg 2000자 / manual 6000자.**
  응답 `{ found, conclusion, steps: [{ quote, sourceId, highlights: [{ text, kind:
  "action"|"contact"|"caution" }], verified }], cautions[0~3] }`.
  핵심은 **quote 가 출처 본문의 글자 그대로**여야 한다는 것이다(요약·맞춤법 교정도 금지).
  비상 상황에서 AI 가 매끄럽게 고쳐 쓴 문장은 위험하므로, 서버가 공백을 정규화(연속 공백·줄바꿈 →
  공백 하나)한 뒤 **모델에 실제로 준 잘린 본문**과 대조해 step 마다 `verified` 를 붙인다.
  앱도 다시 검사하지만 서버 값이 기본이다. quote 300자·10단계·하이라이트 quote 당 2개 상한.
  파싱·정리·대조는 `functions/askGuide.core.js` 순수 함수에 있고
  `node functions/selfcheck.js` 로 네트워크 없이 검사한다(테스트 러너 없음, assert 만).
  없는 sourceId 를 쓴 step 과 quote 의 부분 문자열이 아닌 highlight 는 버린다. max_tokens 3000,
  JSON 파싱 실패 시 1회 재시도 후 `internal`.
- **비상대응 현장조치 매뉴얼은 Storage 에만 둔다.** 실명이 들어 있어 **공개 저장소·APK 에 커밋 금지**
  (변환 산출물은 저장소 밖 `..\manual_build\`). 업로드 파일은 셋: `manual/manual.json` +
  `manual/today.json`(오늘의 비상조치 해설 — 선택 파일, 없어도 매뉴얼은 정상) + `manual/images/*`.
  **올릴 것은 `..\manual_build\upload\manual\` 폴더 그대로다** — 2026.9판 스키마 v2 기준 **파일 66개
  (manual.json + today.json + images 64개), 4,936,186바이트**, `config/manual.files` = 66.
  그림은 참조되는 64개만 올린다(`img_008/009.png` 는 v2 에서 더는 안 쓴다. 대신 `flow_*.png` 8개·`print_s008_*.png` 2개가 생겼다).
  **`config/manual` 의 `files`·`bytes` 는 올리는 시점에 다시 센다**(today.json 이 바뀌면 바이트가 달라진다.
  앱은 `version`·`edition` 만 읽지만 값이 낡으면 다음 사람이 "덜 올라갔나" 하고 헤맨다).
  today.json 문구만 고쳐 다시 올릴 때도 **`version` 을 올려야** 기기가 다시 받는다(통째 교체라 약 4.9MB 를 새로 받는다).
  버킷 `sinjeongsafety.firebasestorage.app`(로컬 `app/google-services.json` 의 `appspot.com` 은 껍데기라 틀림).
  `storage.rules` 의 `match /manual/{allPaths=**}`: 쓰기는 `false`(업로드는 소유자가 콘솔에서 —
  규칙이 아니라 소유자 권한으로 통과한다), **읽기는 `request.auth.token.crew == true`**.
  앱이 새 판을 감지하는 기준은 Firestore `config/manual`
  `{ version, edition, files, bytes }`(config 는 rosterNames 말고는 공개 읽기, 쓰기는 콘솔).
- **매뉴얼 읽기에는 `crew` 커스텀 클레임이 필요하다 (verifyCrew, v1.17.0).** 이메일 정규식만으로는
  못 막는다 — 가입(`createUserWithEmailAndPassword`)이 **앱 안에서만** 명단을 확인하므로 앱 밖에서
  아무 8자리@sinjeong.app 계정이나 만들 수 있다. 로그인·가입 흐름은 건드리지 않고 매뉴얼 접근만 막았다.
  `verifyCrew`(asia-northeast3, 요청 본문 없음 → `{ ok: Boolean }`, AI 를 안 써서 secrets 없음):
  `admin@sinjeong.app` 은 무조건 통과, 사번은 **(functions/crew_ids.txt ∪ `config/roster.extraIds`)
  − `removedIds`** 로 판정해 `setCustomUserClaims(uid, {...기존, crew:true/false})`.
  Firestore 조회 실패 시에는 클레임을 건드리지 않고 `unavailable` 을 던진다(실패를 통과로도 차단으로도 쓰지 않는다).
  **앱은 매뉴얼을 내려받기 전에 verifyCrew 를 부르고, ok 면 `getIdToken(true)` 로 토큰을 강제 갱신한 뒤
  다운로드한다** — 갱신 전 토큰에는 새 클레임이 없어 403 이 난다.
  한계: 이미 받은 클레임은 **다음 verifyCrew 호출 때** 지워진다. 퇴직 처리 직후에도 기존 ID 토큰이
  살아 있는 동안(최대 1시간)은 매뉴얼을 볼 수 있다. 즉시 끊어야 하면 콘솔에서 계정을 지운다.
- **`app/src/main/assets/crew_ids.txt` 를 고치면 `functions/crew_ids.txt` 복사본도 같이 고칠 것.**
  배포 패키지에는 `functions/` 만 올라가 서버가 assets 를 읽을 수 없어 복사해 둔 것이다.
  한쪽만 고치면 앱과 서버의 명단 판정이 어긋난다 — `node functions/selfcheck.js` 가 두 파일을 대조한다.
- Firebase CLI가 이 PC에 kass 계정으로 로그인돼 있어 `firebase deploy --only functions` 직접 가능.
- 확인 현황: `posts/{글}/confirms/{사번}`. 미확인 = 명단(assets+config/roster) − 확인 사번.
- **직원 실명: `config/rosterNames` = `{names: {사번: 이름}}` 282명 (2026-08-25 업로드 완료).**
  관리자만 읽는다(전 직원 실명이라 일반 공개 금지). 실명은 저장소·APK에 두지 않는다 — Firestore 에만.
  ※ 콘솔 없이 문서를 쓰는 방법: CLI 에 문서 쓰기 명령이 **없고**, 규칙상 admin@sinjeong.app 만 쓸 수
  있으며 그 비밀번호는 다루지 않는다. Admin SDK 권한으로 한 번 쓰고 곧바로 지우는 임시 onRequest
  함수를 배포하는 방식으로 해결했다(랜덤 키로 보호, 커밋 금지, 끝나면 `functions:delete`).
- **명단 관리(관리자 전용, 설정 > 관리 > 직원 명단 관리, v1.8.0)**: `config/roster` 의
  `extraIds`(신입) / `removedIds`(퇴직) 델타를 앱에서 고친다. 실제 명단 = (assets 기본 + extra) − removed.
  함정 셋 — ① 퇴직 시 `extraIds` 를 지우면 안 된다(기본 명단에 없는 신입을 복귀시킬 때 영영 사라진다).
  ② 이름은 퇴직해도 `rosterNames` 에 남긴다(과거 통계가 그 이름을 쓴다).
  ③ 퇴직자 로그인 차단은 **`removedIds` 명시적 포함**만 근거로 한다. "명단에 없으면 차단" 으로 만들면
  오프라인(터널)에서 `config/roster` 를 못 읽을 때 신입사원이 통째로 갇힌다 — 조회 실패는 통과시킨다.
- 직원 포인트(관리자 전용, 설정 > 관리): 확인 1 / 퀴즈 정답 1 / 댓글 1 / 답변 2점, 월별 집계.
  `collectionGroup` 조회라 **컬렉션 그룹 색인(firestore.indexes.json 의 fieldOverrides)** 과
  **`{path=**}` 재귀 와일드카드 규칙**이 둘 다 있어야 한다. 중첩 규칙은 collectionGroup 에 안 걸린다.
- 배포: 플레이 비공개 테스트(테스터 11명) + 카톡 APK(zip). 산출물 이름 관례:
  `C:\Users\admin\Downloads\슬기로운승무생활_v{버전}.apk` + 같은 이름 `.zip`

## 이 저장소에서 작업하는 법 (스킬 문서보다 이 절이 최신)

anthropic-skills:sinjeong-safety-app 스킬에는 "사용자가 GitHub 웹에서 붙여넣기로만 작업"이라고
돼 있는데, **이 PC의 Claude 세션은 그럴 필요 없습니다**:

- 이 폴더가 곧 클론입니다. **직접 수정 → 커밋 → `git push origin main`** 하면 됩니다.
- 푸시하면 GitHub Actions가 자동으로 debug APK + release AAB를 빌드합니다(서명 키는 Secrets).
  `gh run watch`로 초록불 확인 → `gh run download`로 산출물 회수.
- **로컬 빌드도 됩니다**: Android SDK·JDK17 설치돼 있음. 단 **저장소에 gradle wrapper가 없고**
  경로에 한글이 있어 AGP가 거부하므로, 파일을 고치지 말고 `-P`로 넘길 것(되돌릴 게 없어 안전):
  `C:\Users\admin\.gradle\wrapper\dists\gradle-9.0.0-bin\d6wjpkvcgsg3oed0qlfss3wgl\gradle-9.0.0\bin\gradle.bat -Pandroid.overridePathCheck=true --console=plain :app:assembleDebug`
- 사용자가 GitHub 웹에서 직접 커밋하는 경우가 있으니 **작업 시작·푸시 직전에 `git pull` 필수**.
- 스킬의 절대 규칙은 그대로 유효: 패키지 `com.sinjeong.safety` 고정 / Firebase는 `sinjeongsafety`만
  (`sinjeong-safety`는 미사용 중복) / versionCode는 실제 값 확인 후 +1 / 서명 키 재생성 금지 /
  또타 마스코트 신규 생성 금지 / 전달 전 import 감사.
- **Play 정책 필수 (지우면 심사 반려)**: 설정 → 앱 정보의 "개인정보처리방침"·"계정 삭제 요청" 링크(v1.16.2)와
  `docs/privacy.html`·`docs/delete-account.html`(GitHub Pages). 파일명 바꾸지 말 것. 앱이 모으는 데이터가
  바뀌면(새 권한·새 저장 필드·새 외부 API) privacy.html과 Play Console 데이터 보안 양식을 같이 고친다.
- **계정 삭제 요청 처리 (페이지가 "10일 이내 삭제"를 약속함)**: 사번 N 기준, Firebase 콘솔(소유자라 규칙 무관)에서
  ① Authentication의 `N@sinjeong.app` ② `crew/N` ③ 모든 `posts/*/confirms/N` ④ `comments`·`answers` 중 authorEmpNo==N
  ⑤ `questions` 중 authorEmpNo==N — **지우기 전에** `images[].url`의 사진을 Storage에서 삭제(URL의 `/o/` 뒤를
  URL 디코드한 게 경로, 예 `attachments/…`). 앱의 질문 삭제는 사진 파일을 지우지 않는다 ⑥ `config/rosterNames`의 `names.N`
  ⑦ 처리 결과를 요청 메일에 회신.

## 아키텍처 요점

- 화면: HomeScreen(피드+카테고리+규정검색 배너) / DetailScreen / WriteScreen / LoginScreen /
  RegulationScreen(규정 뷰어 3단, 자체 state + BackHandler) / **RegulationAskScreen(규정 검색, v1.0.3)**
- 규정 데이터: `assets/regulations.json` **9권 912조문** (키 n/t/b, 책이름→조문배열). 오프라인 동작이
  설계 원칙(터널 대비). 새 책을 추가하면 `RegulationRepository.bookMeta`·`RegulationSearch` 가중치/단축명·
  `RegulationAskScreen.bookBadgeColors` 세 곳에 같이 등록해야 화면에 나온다.
  - **원문 무손실 원칙(2026-08-26 전수 정리 때 확립)**: 이 파일 수정은 공백·줄바꿈 삽입만 허용.
    낱말을 붙이거나 만들지 말 것 — 사용자가 "30분출 고"를 "30분 출고"로 고치자고 했지만 실제로는
    표의 **다른 칸**(30분 = 승계 소요시간, 출 고 = 다음 항목)이었다. 추측으로 붙였으면 오정보가 됐다.
  - **부칙 조문 29건은 `n` 이 본문과 중복**(부칙 제1조가 책 하나에 여러 개). `n` 을 바꾸지 말 것 —
    `RegulationSearch` 가 `num` 완전일치 가산점을 쓰고, 바꿔도 부칙끼리 또 겹친다. 구분은 `t` 의 `[부칙] ` 표시로.
  - **뭉갠 구간은 전부 해소됐다(2026-08-26, v1.16.1). 무공백 40자 이상 런 0건.**
    사용자가 원본 HWPX 4개(운전취급규정·취업규칙·인사규정·전동차승무원업무예규)를 줘서
    표 49개를 격자(`rowAddr`/`colAddr`/`rowSpan`/`colSpan`)로 복원했고, 셀 값을 원본과 전건 대조했다.
    **원본을 덮어쓰기 전에 반드시 "같은 판인지" 먼저 대조할 것**(표 제외 문단 공백 제거 후 비교).
    규정은 개정이 잦아 다른 판을 덮어쓰면 앱이 틀린 내용을 갖게 된다.
    함정 둘: ① 표가 원본에서 **누워 있을 수 있다** — 운전취급규정 제102조는 3행×15열(곡선반경이 열 방향)
    이라 세로로 돌릴 때 속도 값이 옆 칸으로 밀릴 수 있다. 열 단위로 다시 대조해야 잡힌다.
    ② `<hp:tbl>` 이 아니라 **도형**(`hp:rect`+`hp:drawText`)인 표가 있다(제197·364조). 셀 경계가 없으니
    좌표로 배치만 복원하고, 확정 안 되는 라벨은 붙이지 말 것.
  - **`<hp:fwSpace/>` 유실**: 변환기가 한글의 고정폭 공백을 버려 낱말이 붙는다(예규 제105조가 그 사례).
    `<hp:t>` 안쪽만 이어붙이면 사라지므로 평문 추출 때 공백 한 칸으로 치환할 것. 산문에 45곳 더 있으나
    40자 런을 만들지 않아 두었다.
  - **남은 것 둘(둘 다 원문 무손실 원칙 밖이라 사용자 확인 필요)**: 취업규칙 제25조 장기재직휴가 표는
    변환기가 **통째로 누락**해 JSON 에 아예 없다(복원이 아니라 '내용 추가'가 된다).
    인사규정 색인 70·71은 부칙 제2조 ② 항 한 문단이 셋으로 쪼개진 것 — 올바른 병합은 912→**910**이다
    (911 아님). 조문 수를 하드코딩한 로직은 없고 주석 두 곳만 낡는다.
- 검색 엔진: `data/RegulationSearch.kt` — 동의어 28그룹·불용어·idf·점수식, **30점 미만이면
  "못 찾았어요" 안전장치**. 2단계(AI 답변) 확장 시 이 파일을 RAG 재료로 재사용하는 설계.
- ~~단방향 게시판이 확정 설계~~ → **2026-08-22 사용자가 뒤집었습니다. 게시물 댓글 있음**
  (`posts/{글}/comments`, 실명제, 작성은 로그인한 사람만, 삭제는 관리자 또는 본인).
  승무원 로그인이 사번+실명이라 익명 우려가 사라진 것이 이유입니다.

## 함정

- **Play 심사 반려(2026-10, v1.16.2/51 "반응하지 않는 UI 요소") — 로그인 실패 안내가 키보드 뒤에 숨어 있었다.**
  승무원 로그인 실패를 MainActivity 스낵바로만 보냈는데, M3 `Scaffold` 는 IME 인셋을 모르고 targetSdk 35+ 는
  창이 키보드만큼 줄지 않으므로 스낵바가 **키보드 뒤에** 그려졌다. 심사자(PIN 칸 포커스 상태로 로그인 탭)에게는
  "눌러도 아무 일이 없다"로 보였다(에뮬레이터에서 재현 — logcat 에는 로그인 시도가 찍히는데 화면은 그대로).
  덤으로 둘: ① 같은 실패가 4초 안에 반복되면 `UiMessage`(data class)가 같은 값이라 StateFlow 가 안 흘러
  화면 `loading` 이 영영 안 풀렸다 → 스낵바는 **보여 주기 전에** `consumeMessage()`. ② 로그인 게이트 상태에서는
  NavHost 가 화면에 없어 "관리자" 가 `nav.navigate` 해도 백스택만 바뀌고 화면은 그대로였다 → 게이트 안에서 직접 바꿔 보여 준다.
  교훈: **버튼은 누르면 반드시 그 화면 안에 보이는 반응이 있어야 한다**(즉시 진행 표시 + 버튼 아래 실패 사유).
  스낵바에만 맡기지 말 것. `SnackbarHost` 에는 `Modifier.imePadding()`(v1.17.1). 인증은 `withTimeout(15초)`,
  명단·이름 같은 부수 조회는 `withTimeoutOrNull(8초)` 로 null → 통과. 오프라인 Firestore **쓰기**는 실패하지 않고
  매달리므로 로그인 뒤 `lastLoginAt` 같은 기록은 `await` 하지 않는다. 자릿수 모자람은 비활성 버튼이 아니라
  누를 때 안내로(비활성 버튼도 심사자에게는 "무반응"이다). 심사 계정 99999999 는 assets 에 없고 `config/roster.extraIds` 에만 있다.
- **로컬 빌드 APK를 사용자에게 주면 안 됩니다.** `app/google-services.json` 은 git에 없고(추적 안 됨)
  로컬 파일은 project_number·app_id가 전부 0, 키가 `AIzaSyDUMMYDUMMY` 인 **껍데기**입니다. 진짜는
  GitHub Secrets `GOOGLE_SERVICES_JSON` 에만 있고 CI가 빌드할 때 복원합니다. 껍데기로 빌드해도 앱은
  켜지고 Firestore 읽기도 되는데 **로그인만 "API key not valid" 로 실패**합니다. 실제로 v1.0.6~1.0.8을
  이렇게 잘못 전달한 적이 있습니다. 전달용은 항상
  `gh run download <runId> -n sinjeong-safety-debug-apk`. 로컬 빌드는 컴파일 검증 전용.

- **피드(HomeScreen)는 밴드식이다(v1.12.0).** 본문 6줄+더보기, 사진·영상 포스터·PDF 첫 쪽 인라인,
  **영상은 눌러서 재생**(자동재생 금지 — 데이터·배터리·스크롤이 다 나빠진다).
  함정 셋: ① 펼침·재생 상태는 목록 **바깥**에서 글 id 로 들 것. 카드 안 `remember` 는 LazyColumn 이
  재활용할 때 엉뚱한 카드에 얹힌다. ② 화면 밖 이탈 시 반드시 release — 안 하면 소리만 계속 난다
  (`adb shell dumpsys audio | grep -i player` 로 셀 것. 재생 전 0 → 중 1 → 이탈 후 0 이어야 한다).
  ③ **재생 완료 시 "퀴즈 풀고 확인" 버튼을 띄울 것.** 확인·퀴즈가 상세에만 있어 피드에서만 보면
  확인 기록이 안 남고 확인 현황·포인트·서명부가 통째로 빈다. 전체화면은 피드에 넣지 않았다.
- **관리 메뉴 제한(직원 포인트·명단 관리)은 `crewEmpNo` 로 판정하면 안 된다.**
  `CrewRepository.currentEmpNo()` 는 관리자 세션이면 **무조건 null** 을 돌려주므로, 관리자 모드로 들어간
  본인 화면에서 메뉴가 사라진다(같은 실행 중엔 옛 값이 남아 "어제는 되고 오늘은 안 되는" 형태로 나온다).
  승무원 인증 성공 시점의 사번을 prefs `crew_emp_no` 에 남기고 그걸로 판정한다
  (`CrewRepository.DEV_EMP_NOS`, 사번만 — 실명은 코드에 두지 않는다). 화면 차원의 제한이며
  `firestore.rules` 는 그대로다(규칙까지 좁히면 관리자 계정이 확인 현황·명단을 못 읽어 깨진다).
- **첨부(Attachment)에 필드를 추가하면 여섯 곳을 같이 고칠 것.** 손으로 직렬화하는 구조라 하나만
  빠뜨려도 저장은 되는데 다시 읽을 때 사라진다(예전에 `links` 가 실제로 그렇게 사라졌다):
  `PostRepository.addPost` / `updatePost` / `updatePost` 의 `keep` 집합 / `attachmentUrlsOf` /
  `BriefingRepository.save` / `BriefingRepository.toBriefing`. 뒤의 둘 중 `toBriefing` 이 손수 읽는 자리다.
  `keep` 을 빠뜨리면 **첨부를 그대로 두고 저장만 해도** 파일이 '안 쓰는 것'으로 지워지고,
  `attachmentUrlsOf` 를 빠뜨리면 글을 지워도 Storage 에 파일이 남는다.
- **미디어 변환 함정 둘(둘 다 실측으로 잡았다. 검은 화면이 나오면 여기를 의심할 것):**
  ① 동영상 썸네일은 `OPTION_CLOSEST` 로 뽑는다. 흔히 쓰는 `OPTION_CLOSEST_SYNC` 는 "가장 가까운
  키프레임"을 주는데, 교육영상들의 키프레임이 0초 다음 8.33초라 2초를 요청해도 0초(페이드인 전
  **검은 화면**)가 돌아온다.
  ② `PdfRenderer` 로 굽기 전에 **흰 바탕을 깔 것**(`drawColor(WHITE)`). PDF 배경은 투명이라
  안 깔면 검은 본문 글씨가 검은 화면에 통째로 묻힌다.
  그리고 운전정보 공문은 **A4 가로**다 — 가로 1080 은 92dpi 라 글씨가 뭉갠다. 긴 변 1600(137dpi)을 쓴다.
- **입력창을 새로 만들면 반드시 `Modifier.imePadding()` 을 붙일 것.** 매니페스트의
  `windowSoftInputMode="adjustResize"` 는 **더 이상 동작하지 않는다** — targetSdk 35+ 부터 안드로이드가
  edge-to-edge 를 강제하고 36 에선 opt-out 도 없어서, 창이 키보드만큼 줄지 않고 IME 는 앱이 직접
  소비해야 하는 인셋으로만 온다. 안 붙이면 입력창이 키보드에 통째로 덮여 "쓰는 내용이 안 보인다"가 된다
  (2026-08-25 실제 신고, 댓글창). 붙이는 위치: 하단 고정 바는 `background` **뒤**에 `.imePadding()`
  (키보드 위 여백까지 같은 색), 스크롤 화면은 `verticalScroll` **앞**에. 목록 화면은 LazyColumn 에.
  창 단위 설정(`decorFitsSystemWindows`)으로 한 번에 고치려 들지 말 것 — 이미 imePadding 을 가진
  화면들과 이중 패딩이 나고 Scaffold 상단 여백 규칙까지 흔든다.
- **윈도우 중복 다운로드 파일명(`이름 (2).kt`)이 커밋되면 Redeclaration으로 빌드 전체가 깨집니다.**
  한 번 사고 났었음(그때 (2) 쪽이 최신본인 경우도 있었으니 지우기 전에 diff 확인).
- 채널·상단바: MainActivity Scaffold가 상태표시줄 여백을 이미 넣으므로 개별 화면에서 중복 padding 금지.
- 아카이브(지난 자료 보기) 연도 제목 깨짐 수정이 별도 세션에서 진행됐을 수 있음 — pull로 확인.

## 남은 것 / 대기

- 2단계 AI 답변(규정 챗봇): 사용자가 1단계 반응 보고 결정. Anthropic API 키 + 중계 서버 필요.
- 플레이 업로드는 항상 사용자 몫 (Actions에서 release-aab 받아 콘솔에 올림).
- 규정 데이터 갱신 절차는 스킬 문서 참조(HWPX → JSON).

## 이웃 프로젝트

신정승무캘린더: `C:\Users\admin\Downloads\07_프로젝트\SinjeongCrewCalendar` (별도 CLAUDE.md 있음).
캘린더 상단바에서 이 앱을 실행하는 연결 아이콘이 있음(패키지명으로 연동). 상호 간섭 없음.

## v1.17.0 비상대응 매뉴얼 — 앱 쪽

서버(Storage·규칙·askGuide/verifyCrew)는 다른 세션이 맡았다. 여기는 앱 쪽 메모다.

- **매뉴얼은 저장소·APK 에 없다.** 실명·연락처가 들어 있어 Cloud Storage(`manual/`)에서
  로그인한 직원만 받아 `filesDir/manual/` 에 둔다(외부 저장소·공유 캐시 금지).
  백업 제외는 `res/xml/backup_rules.xml`(11 이하) + `data_extraction_rules.xml`(12+) **둘 다** 있어야 한다.
  로그아웃·퇴직 차단 시 `ManualRepository.wipe` 로 지운다(MainViewModel 의 네 갈래 — 아래 wipe 항목).
- **교체는 통째로만.** 임시 폴더(`manual.tmp`)에 manual.json + today.json + 그림 64개를 전부 받고
  성공했을 때만 `manual/` 과 갈아끼운다. 반쪽 자료가 남으면 정작 터널에서 그림 없는 매뉴얼을 본다.
  **순서는 `manual`→`manual.old` 로 치우기 → `manual.tmp`→`manual` 들이기 → `manual.old` 삭제.**
  1차 구현은 옛 폴더를 **지운 다음** 옮겼는데, 그 사이에 앱이 죽거나 rename 이 실패하면 기기에 아무것도
  안 남는다(그리고 터널에서는 다시 못 받는다). `isDownloaded` 가 부를 때마다 `recover` 로 `manual.old` 만
  남은 상태를 되살린다. 폴더 이름을 늘리면 `res/xml` 백업 제외 규칙 **두 파일**에도 넣을 것.
- **wipe 는 sync·load 와 겹친다.** 받는 도중 로그아웃하면 sync 가 끝까지 받아 로그아웃된 기기에 실명 자료를
  되살릴 수 있었다. `wipeGen`(지울 때마다 +1)을 sync 시작 때 잡아 두고 갈아끼우기 직전에 uid 와 함께 다시 본다.
  `load` 도 캐시보다 **파일을 먼저** 본다 — 캐시만 믿으면 지운 뒤에도 메모리의 실명 자료가 화면에 나온다.
  wipe 가 걸린 자리는 넷: 관리자 종료(`logout`) / 승무원 로그아웃 / 퇴직 사번 로그인 차단 /
  관리자 폼에 비관리자 계정을 넣었을 때의 `repo.logout()`.
- **받을 게 없어도 7일마다 verifyCrew 를 다시 부른다**(`reverifyIfDue`). 확인을 내려받을 때만 하면,
  이미 받은 퇴직자 기기는 새 판이 나올 때까지 실명 자료를 계속 들고 있다(퇴직 차단은 재로그인 때만 걸린다).
  여기서도 통신 실패(null)는 아무것도 하지 않는다. 응답에 `ok` 가 없거나 Boolean 이 아닌 것도 **null** 이다
  (`?: false` 로 뭉개면 서버가 한 번 이상한 답을 줬을 때 멀쩡한 직원의 매뉴얼이 지워진다).
- **`verifyCrew` 실패와 "명단에 없음"을 뭉뚱그리지 말 것.** 통신 실패(null)는 기기 저장본을
  그대로 두고 다음 기회에 재시도하고, `ok=false` 일 때만 지운다. 하나로 묶으면 터널에서
  잠깐 못 물어본 사람의 매뉴얼이 사라진다(퇴직자 차단이 `removedIds` 명시 포함만 보는 것과 같은 이유).
  Storage 403 은 토큰에 `crew` 클레임이 아직 없는 것이므로 `getIdToken(true)` 후 1회만 재시도한다.
- **섹션 본문은 WebView 다.** 표 290개(그중 57개는 셀 안의 표)에 병합 셀이 많아 Compose 로 그리지 않는다.
  JS 끔·`blockNetworkLoads`·링크 이동 차단, 파일 접근은 그림용으로만 켠다.
  `allowFileAccess=true` 는 "앱이 읽을 수 있는 모든 파일"(로그인 토큰이 든 shared_prefs 포함)을 여는 설정이라
  `shouldInterceptRequest` 로 **`filesDir/manual/images/` 밖의 요청은 빈 응답**으로 막는다
  (`/data/user/0` 과 `/data/data` 는 같은 곳의 다른 이름이라 양쪽 다 canonical 로 풀어 비교한다).
  그림 파일명은 `ManualFormat.isSafeImageName`(영숫자·`_`·`-`·`.`, `..` 금지)을 통과한 것만
  내려받고(`imageFiles`) `<img>` 로 낸다 — 이름이 `File(dir, name)` 에 그대로 들어가기 때문이다.
  `update` 에서 같은 HTML 을 다시 싣지 말 것 — 읽던 자리가 맨 위로 튕긴다.
  "사고흐름도" 단독 p 를 h1 로 그리는 처리(`isFlowTitle`)는 v1 자료용이다 — v2 에서는 s014 도 `h` 라 걸리는 곳이 0곳이다(해가 없어 남겨 둠).
- **한컴 전용문자(PUA)는 표시할 때만 바꾼다**(`ManualFormat.clean`). 저장 자료·검색 색인은 원문 그대로다.
  U+F09E→`·` / U+F0EF→`⇦` / U+F0F0→`⇨` / U+F0F3→`⇔` / U+F03DA→`▢`(보조 평면이라 서러게이트 쌍).
  **PUA 화살표 함정 — ⇦ ⇨ ⇔ 를 다른 화살표로 바꾸면 보고 방향이 달라 보인다.** 1차 구현은 "상자 사이를 잇는 화살표"라는
  쓰임만 보고 ↗ ↘ → 로 넣었고, 136~138쪽 상황보고체계도에서 왼쪽 보고(⇦)가 ↗ 로, 양방향(⇔)이 → 로 보였다(독립 감사 결함 D).
  지금 값은 감사가 PDF 글꼴 코드(Wingdings 0xEF/0xF0/0xF3/0x9E)와 확대 렌더링으로 하나씩 확인한 글리프다
  (`..\manual_build\audit\fidelity_audit.md`). **새 PUA 가 나오면 짐작으로 넣지 말고 원본 인쇄본에서 글리프를 확인할 것.**
  모르는 PUA 는 그대로 둔다. 표의 키는 코드포인트 숫자다(PUA 글자를 소스에 그대로 두면 안 보여서 검수가 안 된다) —
  테스트도 치환 결과를 코드포인트 숫자로 못박는다. 변환기도 같은 치환을 해서 v2 자료에는 PUA 가 남지 않는다(앱의 표는 안전망).
  AI 답변 카드의 인용문도 조각마다 `clean` 을 거친다(형광 자리는 원문 기준으로 잡고, 보여 줄 때만 바꾼다).
- **manual.json 스키마 v2 (최종 계약은 `..\manual_build\SCHEMA.md`).** v1 자료도 그대로 읽힌다.
  SCHEMA.md §3 의 "PUA 치환표를 지운다"·"isFlowTitle 은 필요 없다"는 따르지 않았다 — 둘 다 해가 없는 안전망으로 남긴다는 결정이다.
  ① 표 셀에 선택 필드 `blocks`(그 셀의 내용을 순서대로: p/table/image/h). 있으면 화면은 td 안에 **재귀로** 그린다
  (표 안의 표는 `<table class="in">` — 가로 스크롤 상자와 머리 행 칠은 바깥 표에만). 없으면 예전처럼 `text`.
  셀 `text` 는 그 셀의 **전체 평문**(중첩 표 글 포함)이라, 평문을 만들 때 `blocks` 안으로 글을 찾으러 들어가지 않는다(두 번 나온다).
  blocks 가 있는데 그릴 게 하나도 안 나오면(빈 문단·막힌 그림) `text` 로 되돌아간다. 중첩 한도 `MAX_DEPTH`=4.
  ② image 에 선택 필드 `alt`·`flow`. `flow:true` = 도형으로 그린 흐름도를 인쇄본에서 그림으로 뜬 것, `alt` = 도형 안 문구를
  위→아래·왼→오로 이은 글. **화면에는 그림만** 낸다(alt 는 `<img alt>` 속성으로만). 도형 글을 본문처럼 늘어놓으면
  YES/NO 분기가 있는 흐름도가 **절차 순서처럼 읽힌다.** 앱은 `flow` 로 화면을 가르지 않는다.
  ③ **평문이 둘이다.** (가) `ManualFormat.plainText` = p/h 글 + 셀 `text` + 그림 캡션·`alt` — 검색 색인(`asBook`),
  오늘의 비상조치 keyStep 대조, 실명·오기 판정. (나) `ManualFormat.aiBody` = (가)와 같되 alt 구간을
  `〔흐름도 글 — 도형 안 문구 모음. 나열 순서는 절차 순서가 아님〕` … `〔흐름도 글 끝〕` 두 줄로 감싼 것 — askGuide 에 보내는 본문
  (`RegulationAskScreen.buildSources` 가 섹션을 되짚어 만든다). 인용문 대조는 서버도 앱도 **"보낸 본문"** 기준이라 저절로 일관된다.
  표식 글자는 앱(`FLOW_OPEN`/`FLOW_CLOSE`)·서버 프롬프트(`functions/index.js` 의 같은 이름)·`selfcheck.js` **세 곳이 같아야 하고
  selfcheck 가 대조한다.** 서버 규칙(`GUIDE_FLOW_TEXT`, manual·all 범위만): 그 구간은 인용해도 되지만 나열 순서로 절차를 추정하지 말 것,
  순서는 표에 근거, 흐름도에만 있는 내용이면 conclusion 에 "순서는 흐름도 그림을 확인하세요", 표식 줄은 quote 로 복사 금지.
  ④ 섹션의 그림 목록(`imageFiles` = 내려받기 목록·답변 화면 썸네일)은 **셀 `blocks` 안까지 재귀로** 모은다
  (`flow_*.png` 도 `images/` 에 온다). 빠뜨리면 그 그림은 내려받지도 않아 깨진 그림으로 나온다. 이름 화이트리스트는 그대로.
  ⑤ 옛(v1) 변환기가 셀에 끼워 넣던 `[표]`·`[그림: …]` 111곳은 **화면에서만** 걸러 낸다(`clean`). 평문·인용 대조는 건드리지 않는다.
  ⑥ **흐름도가 셀 안에 든 경우(확정, SCHEMA.md §1)**: 셀 `text` 는 안쪽 흐름도의 `alt` 까지 담는다. 상황 23-1·23-2(s029·s030)는
  1×1 표의 셀이 `blocks:[image(flow)]` 이고 **셀 `text` == alt** 다. (가) 평문에는 셀 `text` 그대로 **한 번만** 들어가고,
  (나) AI 본문에서는 그 글을 표 줄에서 덜어 내 **반드시 표식 안으로** 옮긴다(표 줄에 표식 없이 섞이면 모델이 상자 글의 나열 순서를
  절차 순서로 읽는다). 글자 그대로 못 덜어 내면(공백만 다름) 그 셀 글 전체를 흐름도 글로 본다 — 표 글 몇 줄이 표식 안에 들어가는 쪽이 안전하다.
  ⑦ **그림 한 장을 감싼 1×1 표(31곳, 상황별 "사고흐름도" 그림)는 테두리 없이 그림만 그린다**(`imageOnlyTable`). 셀 안에 두면 그림 폭이
  셀에 갇혀(`td img{max-width:78vw}`) 흐름도 글씨가 더 작아진다. 셀에 제 글이 따로 있으면 표로 둔다.
  **2026.9판 v2 수치(스윕이 확인)**: 섹션 **91**(v1 의 71개는 id·제목 그대로, 합쳐져 있던 번호 소절이 s072~s091 로 독립) /
  표 290 = 최상위 233 + 셀 안 57 / 그림 블록 68·파일 64(`flow_` 8, `print_` 2) / h 158 / blocks 가진 셀 92칸 / 중첩 1겹 / PUA 0.
- **섹션 순서는 `sections` 배열 순서다. id 는 열쇠일 뿐이다**(SCHEMA.md §5). v2 에서 새로 생긴 s072~s091 은 문서 중간중간에
  놓이므로 **id 로 정렬하거나 "sNNN" 의 숫자 크기로 순서를 가정하면 목록이 뒤섞인다.** 지금 앱에 그런 코드는 없다(2026-09-19 확인:
  목록·그룹·상황 바로가기는 `doc.visibleSections` 의 배열 순서, 오늘의 비상조치는 today.json `items` 순서, 섹션 찾기는 id 일치 조회,
  정렬은 검색 점수·형광 구간 시작 위치·걸린 낱말 길이 셋뿐). 이전/다음 넘기기를 만들게 되면 배열 위치로 할 것.
- **글이 0자인 섹션이 있다(s076·s090) — 그러나 빈 화면이 아니다.** s076 은 그림 한 장이 든 표, s090 은 그림 한 장(비상대응지도)이다.
  "평문이 비면 숨김" 으로 만들면 이 그림들이 목록에서 사라진다(SCHEMA.md §5 가 하지 말라고 못박은 것). 숨기는 기준은
  `ManualFormat.hasContent`(그릴 것이 하나라도 있는가 — 글·표 글·이름이 안전한 그림)이고 목록·검색 색인·상황 바로가기는
  `ManualDoc.visibleSections` 를 쓴다. 2026.9판에는 그릴 것이 없는 섹션이 0개다. 바로가기(id)로 그런 섹션에 들어오면 안내문을 띄운다.
  AI 에는 보낼 글이 없는 섹션을 근거로 넣지 않는다(`buildSources` 가 빈 본문을 뺀다).
- **AI(askGuide)에 실명 섹션을 보내지 않는다.** 공개 개인정보처리방침이 "비상연락망·변경연혁 등 직원 이름·전화번호가
  담긴 부분은 AI 로 보내지 않습니다"라고 약속한다 — **이 문장이 참이어야 한다.** 기준은 "의심스러우면 뺀다"
  (빼서 잃는 것은 AI 근거 한 건이고 뷰어·검색에는 그대로 나온다). 부르는 쪽은 **`ManualFormat.sensitiveIds(doc)`** 를 쓸 것
  (섹션 하나만 보는 `isSensitive` 로는 갈라져 나온 소절을 못 잡는다). 규칙:
  ① 표지·변경연혁 ② 제목·경로의 낱말(연락망·연락처·변경연혁·임무카드) ③ 전화번호 꼴 5개 이상 — **지역번호 없이 번호만 적은 꼴
  (NNNN-NNNN)도 센다**(경찰서 연락망 62개가 전부 이 꼴이라 예전 정규식으로는 0개로 세어졌다) ④ 휴대전화 번호는 하나만 있어도
  ⑤ 표의 머리글이 성명·이름·성함·담당자(칸 글에서 공백을 뺀 뒤 통째로 같을 때. 셀 안의 표까지) ⑥ **①~③에 걸린 섹션과 같은 장
  (같은 `path`, 두 단계 이상)의 소절은 같은 취급** — v2 에서 소절이 갈라져 나오면서 연락망 장에 제목에 낱말도 번호도 없는 소절이 생겼다.
  표준운영절차는 path 가 한 단계라 물려 주지 않는다(섹션 하나 때문에 상황 34개가 통째로 빠지면 안 된다). ④⑤만 걸린 섹션도 물려 주지 않는다.
  2026.9판 v2 기준 **10개**가 빠진다: s001·s042(담당자 열 — 이번 판에는 직책만 적혀 있지만 이름이 채워질 수 있는 칸)·s044·s063·s064·
  s065·s089·s090(⑥ — s065 에서 갈라져 나온 그림뿐인 소절)·s067·s068. 전송 대상 81개에 남은 전화번호 꼴은 s071 의 부서 전화·FAX 2개뿐이다.
  **"전송 대상에 남은 직원 이름 0건" 은 `ManualRealDataTest` 가 기계로 확인한다**: 제외 섹션의 사람을 적는 자리(라벨–값 행
  "성 명 | 값" / 한 칸의 "이름 줄 + 전화 줄" / 성명 표의 이름 꼴 칸)에서 이름 꼴을 메모리로만 거둬, 전송 대상의 AI 본문에서
  붙여 쓴 꼴·**한 글자씩 띄어 쓴 꼴**(임무카드가 이렇게 적는다) 모두로 찾는다. 같은 자리에 적힌 직책·부서 낱말 16개는 허용 목록.
  함정: 끝 글자로 낱말을 거르는 식(…처·…실·…기)은 **이름의 흔한 끝 글자(호·원·식·기·성)와 겹쳐 진짜 이름이 빠진다** — 쓰지 말 것.
  새 판을 받으면 이 테스트부터 돌리고, 깨지면 가린 꼴과 섹션 id 로 매뉴얼의 그 자리를 직접 본 뒤에 허용 목록에 넣을지 정한다
  (허용 목록에 사람 이름을 넣지 말 것 · 이름을 로그·보고·커밋에 옮기지 말 것).
  본문 자르는 길이(reg 2000 / manual 6000자)는 **서버와 같아야 한다** — 다르면 인용문 대조가 전부 어긋난다.
- **`Categories.REGULATION` 의 값("운전규정")은 절대 바꾸지 말 것**(Firestore 저장값·옛 앱 호환).
  화면 표시는 `Categories.label()` 을 거쳐 "운전규정/비상조치" 로 나온다.
- 검색은 매뉴얼을 규정집 한 권처럼 꾸며(`ManualRepository.asBook`) 기존 `RegulationSearch` 에 그대로 태운다.
  범위 제한(규정/비상조치/전체)은 **search 에 넘기는 책 목록을 거르는 것**이지 새 인자가 아니다.
  책 목록 인스턴스는 화면에서 `remember` 로 고정한다 — 매번 새 리스트면 색인을 헛되이 다시 만든다.
  섹션을 되짚는 열쇠는 `RegArticle.sid`(섹션 id)다. 매뉴얼은 `num` 이 빈 항목이 38개라 번호로는 못 찾는다.
- **인용문 대조의 공백 기준은 서버 JS 의 `\s` 글자 집합을 앱에 그대로 적어 둔 것이다**(`ManualFormat.JS_WS`).
  코틀린 `Regex("\\s+")` 를 쓰면 안 된다 — JVM 테스트에서는 ASCII 공백 6개, 안드로이드(ICU)에서는 또 다른 집합,
  서버는 NBSP·전각 공백(U+3000)·U+FEFF 까지라 **세 환경이 전부 다르다**. 2026.9판에 전각 공백이 실제로 있다.
  끝 다듬기도 `trim()` 이 아니라 `trim(' ')`(코틀린 trim 은 U+0085·U+001C~1F 까지 깎는다).
  같은 사례표가 `functions/selfcheck.js` 8-1) 과 `ManualFormatTest` 에 있다 — **한쪽을 고치면 다른 쪽도.**
  본문 자르기는 `ManualFormat.cutBody`(끝에 걸린 반쪽 서러게이트를 뗀다)만 쓴다.
  ※ 이 세션의 편집 도구는 역슬래시-u 이스케이프(NBSP·전각 공백 등)를 **보이지 않는 실제 글자로 바꿔 저장**한 적이 있다.
  보이지 않는 글자는 소스에 두지 말고 코드포인트 숫자로 만들 것(`String.fromCharCode(0x3000)`, `Character.toChars`).
- **매뉴얼 원본(HWP) 자체에 오기가 있다 — "운전"이 "종합"으로 잘못 일괄 치환됐다**(종합실=운전실, 주의종합=주의운전,
  확인종합=확인운전, 종합지시=운전지시, 정상종합, 동력종합 … 현장 기관사인 사용자가 오기임을 확인). **앱은 고치지 않고
  원문 그대로 보여 준다.** 대신 셋을 했다: ① 매뉴얼 목록(판 표시 아래)과 섹션 본문 맨 아래에 회색 안내 한 줄
  (`ManualFormat.TYPO_NOTICE`) — 평문에 "종합실"·"주의종합"이 **있을 때만**(`hasDrivingTypo`) 뜨므로 수정본이 오면 저절로 사라진다.
  ② askGuide 는 manual·all 범위에서만 `GUIDE_MANUAL_TYPO` 규칙을 붙인다: quote·highlight 는 잘못 적힌 그대로 복사,
  conclusion·cautions 에서만 바른 말. **quote 검증은 건드리지 않는다**(고쳐 쓴 quote 는 '원문 일치'가 아닌 게 맞다).
  ③ `RegulationSearch.SYN` 에 운전실↔종합실 등 4그룹 — **통째 낱말만** 넣을 것. "종합" 한 낱말을 넣으면 종합관제·종합평정
  같은 바른 말까지 끌려온다. 이 네 낱말은 규정 9권에 0회라 규정 검색은 그대로다(`ManualTypoTest` 가 그 전제를 지킨다).
  '종합관제'는 본래 맞는 말이다. 수정본 매뉴얼이 올라오면 ②③은 지워도 된다(테스트가 "오기가 없다"고 알려 준다).
- **AI 답변 요청은 한 번에 한 건**(`MainViewModel.guideJob`). `clearAiAnswer()` 가 진행 중인 요청을 끊는다 —
  안 끊으면 범위를 바꾸거나 새 질문을 한 뒤 10초쯤 지나 **앞 질문의 답**이 새 결과 위에 붙는다.
- **오늘의 비상조치 (v1.17.0)** — 규정 화면 맨 위 "오늘의 규정" 자리에서 옆으로 넘기는 두 번째 카드.
  자료는 `manual/today.json` `{edition, items:[{sectionId, title, hook, easy, keySteps[3~5], role}]}`,
  매뉴얼과 **함께** 받아 같은 폴더에 둔다(`ManualRepository.loadToday`). 서버에 없으면(404) 그냥 넘어가고,
  통신 실패는 넘기지 않는다(today.json 만 빠진 채 "받기 완료"가 되면 다음 판까지 카드가 안 나온다).
  카드가 생기는 조건: 로그인 + 매뉴얼·today.json 이 기기에 있음 + **평일** + `edition` 이 manual.json 과 같음 +
  고른 항목의 섹션이 매뉴얼에 있음. 하나라도 아니면 예전 그대로 오늘의 규정 한 장(페이저·점 없음).
  항목은 오늘의 규정과 **같은 셈법**(`ManualToday.weekdayIndex` — 2026-01-01 부터 전날까지의 평일 수)으로
  `index % items.size`. `RegulationRepository.todayRegulation` 도 이 함수를 쓴다.
  keySteps 는 "원문 그대로"라는 약속이라 기기의 섹션 평문과 대조해(askGuide 와 같은 정규화) 맞을 때만
  `✓ 원문 일치`, 아니면 회색 `원문 대조 안 됨` — today.json 과 manual.json 이 어긋나면 여기서 티가 난다.
  **카드 헤드라인은 `ManualToday.cardTitle` 로 앞 번호·뒤 사고 코드 묶음을 뗀 것**(v1.17.1, 사용자 요청 "가독성") —
  표시 전용이다. today.json·manual.json·뷰어 제목·AI 출처 라벨은 원문 그대로. 뗀 번호는 역할 옆 라벨에 "상황 25 · 기관사" 로 남긴다
  (22·22-1, 23-1·23-2 는 코드를 떼면 제목이 같아진다). 코드 꼴이 새로 나오면 `ManualRealDataTest` 가 91개 제목으로 잡는다.
  번호 원+배지 컴포넌트는 답변 화면과 공용(`QuoteStep`). 높이가 다른 두 장이라 페이저 높이를
  **두 장의 제 높이 사이로 넘기는 위치만큼 보간**한다(`TodayPager` 의 layout 수정자 — 안 하면 넘길 때 아래가 덜컥 뛴다).
- 순수 로직 점검: `app/src/test/.../ManualFormatTest.kt`·`ManualTodayTest.kt`·`ManualRealDataTest.kt`·`ManualTypoTest.kt`·`ManualV2Test.kt` (junit4, 42건).
  `ManualRealDataTest` 는 **실제 매뉴얼 91개 섹션 × 밝은/어두운 화면**(182회)을 생성기에 통과시켜 HTML 을 XML 파서로
  검사한다(태그 짝·이스케이프·소제목/표/그림 개수·전용문자 잔류 — 표 셀의 `blocks` 안까지 재귀로 센다 · 참조 그림 64개가 디스크에 있는지 ·
  오늘의 비상조치 keyStep 전부가 v2 평문과 일치하는지 · AI 전송 대상에 직원 이름이 남지 않았는지). 자료는 저장소 밖 `..\manual_build\` 에서
  찾고(`-DmanualBuildDir=` / `MANUAL_BUILD_DIR` 로 지정 가능) **없으면 건너뛴다**(CI 에는 없다).
  파싱을 `ManualJson`(순수)으로 떼어 낸 이유가 이것이다 — `ManualRepository` 는 Firebase 를 물고 있어 JVM 에서 못 부른다.
  org.json 은 `testImplementation("org.json:json:20240303")` 이 채운다(android.jar 의 것은 껍데기).
  **한글 경로 때문에 `:app:testDebugUnitTest` 는 워커가 클래스를 못 읽어 실패한다**(코드 문제가 아니다).
  `:app:compileDebugUnitTestKotlin` 으로 컴파일한 뒤
  `java -cp app/build/tmp/kotlin-classes/debugUnitTest;...debug;junit;hamcrest;kotlin-stdlib;json-20240303 org.junit.runner.JUnitCore com.sinjeong.safety.data.ManualFormatTest com.sinjeong.safety.data.ManualTodayTest com.sinjeong.safety.data.ManualRealDataTest com.sinjeong.safety.data.ManualTypoTest com.sinjeong.safety.data.ManualV2Test` 로 돌린다
  (jar 들은 `~/.gradle/caches/modules-2/files-2.1/` 아래에 있다. 저장소 루트에서 돌려야 `..\manual_build` 를 찾는다).
