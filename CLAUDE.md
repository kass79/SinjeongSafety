# 슬기로운 승무생활 (신정승무사업소 안전앱)

Android(Kotlin/Compose) + Firebase(SinjeongSafety/sinjeongsafety 프로젝트). 승무원용 안전정보·규정 앱.
사용자(카스)는 비개발자 기관사입니다. 코드 조각 대신 **동작하는 APK와 스크린샷**으로 보고하세요.

## 현재 상태 (2026-08-22 기준 — 숫자는 build.gradle.kts를 믿을 것)

- `main` 최신 `7688491`, **versionCode 5 / versionName 1.0.4**
- 최근 변경: 규정에 물어보기(오프라인 검색, v1.0.3) → 규정 뷰어 시스템 뒤로가기 한 단계씩(v1.0.4)
- 배포: 플레이 비공개 테스트(테스터 11명) + 카톡 APK(zip). 산출물 이름 관례:
  `C:\Users\admin\Downloads\슬기로운승무생활_v{버전}.apk` + 같은 이름 `.zip`

## 이 저장소에서 작업하는 법 (스킬 문서보다 이 절이 최신)

anthropic-skills:sinjeong-safety-app 스킬에는 "사용자가 GitHub 웹에서 붙여넣기로만 작업"이라고
돼 있는데, **이 PC의 Claude 세션은 그럴 필요 없습니다**:

- 이 폴더가 곧 클론입니다. **직접 수정 → 커밋 → `git push origin main`** 하면 됩니다.
- 푸시하면 GitHub Actions가 자동으로 debug APK + release AAB를 빌드합니다(서명 키는 Secrets).
  `gh run watch`로 초록불 확인 → `gh run download`로 산출물 회수.
- **로컬 빌드도 됩니다**: Android SDK·JDK17 설치돼 있음. 단 경로에 한글이 있어 AGP가 거부하므로
  빌드할 때만 `gradle.properties`에 `android.overridePathCheck=true`를 넣고 **커밋 전에 되돌릴 것**.
- 사용자가 GitHub 웹에서 직접 커밋하는 경우가 있으니 **작업 시작·푸시 직전에 `git pull` 필수**.
- 스킬의 절대 규칙은 그대로 유효: 패키지 `com.sinjeong.safety` 고정 / Firebase는 `sinjeongsafety`만
  (`sinjeong-safety`는 미사용 중복) / versionCode는 실제 값 확인 후 +1 / 서명 키 재생성 금지 /
  또타 마스코트 신규 생성 금지 / 전달 전 import 감사.

## 아키텍처 요점

- 화면: HomeScreen(피드+카테고리+규정검색 배너) / DetailScreen / WriteScreen / LoginScreen /
  RegulationScreen(규정 뷰어 3단, 자체 state + BackHandler) / **RegulationAskScreen(규정 검색, v1.0.3)**
- 규정 데이터: `assets/regulations.json` 626조문 (키 n/t/b). 오프라인 동작이 설계 원칙(터널 대비).
- 검색 엔진: `data/RegulationSearch.kt` — 동의어 24그룹·불용어·idf·점수식, **30점 미만이면
  "못 찾았어요" 안전장치**. 2단계(AI 답변) 확장 시 이 파일을 RAG 재료로 재사용하는 설계.
- 단방향 게시판이 확정 설계 — 댓글·양방향 제안 금지(사용자가 명시 거부).

## 함정

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
