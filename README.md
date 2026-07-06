# 신정승무사업소 안전앱 — 슬기로운 승무생활 🚇

서울교통공사 신정승무사업소 직원용 **실시간 안전 정보 공유 앱**

- 일반 직원: 로그인 없이 앱 실행 즉시 게시물 열람 (읽기 전용)
- 관리자: 방패 아이콘 → 로그인 후 글 작성 / 수정 / 삭제
- Firebase Firestore 실시간 연동 → 모든 기기에 즉시 반영

## 기술 스택
| 구분 | 내용 |
|---|---|
| 언어/UI | Kotlin + Jetpack Compose (Material 3) |
| 백엔드 | Firebase Firestore (실시간), Firebase Auth (관리자), Firebase Storage (첨부파일) |
| 최소 지원 | Android 8.0 (API 26) 이상 |
| 패키지 | `com.sinjeong.safety` |

## 화면 구성
1. **홈**: 헤더(초록점 + 방패) → 마스코트 배너 → 검색창 → 카테고리 4종 → 태그 칩 → 피드(NEW 뱃지, 최신순)
2. **상세**: 태그/카테고리/제목/작성자/작성일시/본문 (관리자: 수정·삭제 버튼)
3. **로그인**: 아이디/비밀번호 (아이디만 입력하면 `@sinjeong.app` 자동 부착)
4. **글쓰기/수정**: 카테고리 선택 → 태그 선택 → 제목/내용 → 등록

---

## 🔥 Firebase 설정 (최초 1회, 약 10분)

이 프로젝트에는 `google-services.json`이 **포함되어 있지 않습니다** (보안상 직접 생성 필요).

### 1단계: Firebase 프로젝트 생성
1. https://console.firebase.google.com → "프로젝트 추가"
2. 이름: `sinjeong-safety` (아무거나 가능), 애널리틱스는 꺼도 됨

### 2단계: Android 앱 등록
1. 프로젝트 개요 → Android 아이콘 클릭
2. 패키지 이름: **`com.sinjeong.safety`** (정확히 일치해야 함)
3. `google-services.json` 다운로드 → **`app/` 폴더에 복사**

### 3단계-A: Storage 활성화 (첨부파일용)
1. 빌드 → Storage → "시작하기" (프로덕션 모드)
2. "규칙(Rules)" 탭 → 이 저장소의 `storage.rules` 내용을 붙여넣고 게시

### 3단계: Firestore 활성화
1. 빌드 → Firestore Database → "데이터베이스 만들기"
2. 위치: `asia-northeast3 (Seoul)` 권장
3. **프로덕션 모드**로 시작
4. "규칙" 탭 → 이 저장소의 `firestore.rules` 내용을 붙여넣고 게시

### 4단계: 관리자 계정 생성 (Firebase Auth)
1. 빌드 → Authentication → 시작하기 → **이메일/비밀번호** 사용 설정
2. "사용자" 탭 → 사용자 추가
   - 이메일: `admin@sinjeong.app` (앱에서는 아이디 `admin`만 입력하면 됨)
   - 비밀번호: 원하는 비밀번호 (6자 이상)
3. 관리자를 여러 명 두려면 사용자를 더 추가하면 됨

### 5단계: 빌드
Android Studio에서 프로젝트 열기 → Gradle Sync → Run ▶

---

## 데이터 구조 (Firestore `posts` 컬렉션)
```
posts/{자동ID}
 ├ category   : "인적오류 주의개소" | "교육영상" | "운전규정" | "전달사항"
 ├ tag        : "안전교육" | "운행지시" | "일반전달"
 ├ title      : String
 ├ content    : String
 ├ authorName : String (이메일 @ 앞부분)
 ├ authorUid  : String
 ├ attachments: [ { name, url, mimeType, size } ]   ← 사진/PDF/문서 첨부
 ├ createdAt  : serverTimestamp
 └ updatedAt  : serverTimestamp
```

## NEW 뱃지 동작
- 작성 후 **3일 이내** + **내 기기에서 아직 안 연 글**에만 표시
- 읽음 상태는 기기 로컬(SharedPreferences)에 저장 → 직원별로 독립적

## 첨부파일 기능 (네이버 밴드 스타일)
- 글쓰기에서 사진(JPG/PNG), PDF, DOC/DOCX, XLS/XLSX, HWP 등 첨부 가능 (파일당 최대 20MB)
- 사진: 피드 카드에 썸네일 3장 + "+N", 상세 화면 2열 갤러리 (탭하면 원본)
- 문서: 상세 화면에서 파일 칩 표시, 탭하면 다운로드/열기
- 저장 위치: Firebase Storage `attachments/`

## 조회수
- 게시물을 처음 연 기기마다 조회수 +1 (기기당 1회, 중복 집계 없음)
- 로그인 없이도 집계되도록 보안 규칙에서 `views` 필드 +1 수정만 예외 허용

## 🔔 FCM 푸시 알림 (새 글 등록 시 전 직원에게)
동작 방식: 앱이 `new_posts` 토픽을 자동 구독 → 새 글이 Firestore에 등록되면
Cloud Functions가 토픽으로 푸시 발송 → 모든 기기에 알림 표시.

### 배포 방법 (최초 1회)
1. **Blaze(종량제) 요금제 전환** — Cloud Functions는 Blaze 필요.
   콘솔 좌측 하단 "업그레이드" (무료 할당량이 커서 이 규모에서는 사실상 0원)
2. PC에 Firebase CLI 설치: `npm install -g firebase-tools`
3. 프로젝트 루트에서:
   ```bash
   firebase login
   firebase use --add        # 만든 프로젝트 선택
   cd functions && npm install && cd ..
   firebase deploy --only functions
   ```
4. 앱 재실행 → 다른 기기에서 관리자가 새 글 등록 → 푸시 도착 확인
- 안드로이드 13+ 는 첫 실행 시 알림 권한 팝업에서 "허용" 필요

## 🐙 GitHub + CI
- `.github/workflows/android-ci.yml` 포함 — push할 때마다 디버그 APK 자동 빌드
- 필요한 Secret: `GOOGLE_SERVICES_JSON` (google-services.json을 base64 인코딩한 값)
  ```bash
  base64 -w0 app/google-services.json   # 이 출력값을 Secret에 등록
  ```

## 다음 단계 (예정)
- [ ] AI 요약 기능
- [ ] FCM 푸시 알림 (새 글 등록 시)
