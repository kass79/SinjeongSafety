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
  askRegulation / summarizePost / generateQuiz(앱 연결은 quiz만 남음). Anthropic API 키는
  Secret Manager `ANTHROPIC_API_KEY`. 모델 claude-opus-5. 로그인 사용자만 호출 가능.
- Firebase CLI가 이 PC에 kass 계정으로 로그인돼 있어 `firebase deploy --only functions` 직접 가능.
- 확인 현황: `posts/{글}/confirms/{사번}`. 미확인 = 명단(assets+config/roster) − 확인 사번.
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
