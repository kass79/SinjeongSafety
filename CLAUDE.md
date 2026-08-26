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
- 검색 엔진: `data/RegulationSearch.kt` — 동의어 24그룹·불용어·idf·점수식, **30점 미만이면
  "못 찾았어요" 안전장치**. 2단계(AI 답변) 확장 시 이 파일을 RAG 재료로 재사용하는 설계.
- ~~단방향 게시판이 확정 설계~~ → **2026-08-22 사용자가 뒤집었습니다. 게시물 댓글 있음**
  (`posts/{글}/comments`, 실명제, 작성은 로그인한 사람만, 삭제는 관리자 또는 본인).
  승무원 로그인이 사번+실명이라 익명 우려가 사라진 것이 이유입니다.

## 함정

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
