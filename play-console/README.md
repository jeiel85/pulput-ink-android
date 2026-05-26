# Play Console 최초 등록 가이드 — Pulpit Ink

이 폴더는 Google Play Console에 **Pulpit Ink** 앱을 최초 등록할 때 필요한 모든 메타데이터, 답변, 자산 체크리스트를 보관한다. 등록은 [`pedaiah85@gmail.com`](https://play.google.com/console) 개발자 계정으로 진행한다(기본 계정 `jeiel85@gmail.com`의 개발자 등록은 2021-10-20 해지됨 — 사용 금지).

## 패키지 정보 스냅샷

| 항목 | 값 |
|---|---|
| Application ID | `io.pulpit.ink` |
| 앱 이름 | Pulpit Ink |
| Version Code | 2 |
| Version Name | 1.1.0 |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 |
| 권한 | `INTERNET`, `RECORD_AUDIO` |
| 서명 키 | `.keystore/pulpitink-release.jks` (alias `pulpitink`) — 자세한 정보는 [KEYSTORE_BACKUP.md](../.keystore/KEYSTORE_BACKUP.md) |

## 파일 색인

| 파일 | 내용 |
|---|---|
| [store-listing-ko.md](store-listing-ko.md) | 한국어 스토어 등록정보 (제목/짧은설명/전체설명) |
| [store-listing-en.md](store-listing-en.md) | 영어 스토어 등록정보 |
| [app-content.md](app-content.md) | 앱 콘텐츠 양식 답변 (개인정보·광고·대상연령·데이터보안·정부앱 등) |
| [data-safety.md](data-safety.md) | 데이터 보안 양식 상세 답변 |
| [content-rating.md](content-rating.md) | IARC 콘텐츠 등급 설문 답변 |
| [privacy-policy.md](privacy-policy.md) | 개인정보처리방침 본문 (게시 후 URL 등록 필요) |
| [release-notes/](release-notes/) | 버전별 출시 노트 (ko, en-US) |
| [assets-checklist.md](assets-checklist.md) | 그래픽 자산 준비 체크리스트 |

## 최초 등록 순서 (Play Console 흐름)

1. **개발자 계정 결제 확인** — 일회성 USD 25. `pedaiah85@gmail.com`으로 로그인 후 [Play Console](https://play.google.com/console) 진입 시 안내됨.
2. **앱 만들기** — 앱 이름 / 기본 언어(한국어) / 앱·게임 선택(앱) / 무료·유료(무료) / 정책 동의.
3. **앱 설정 → 앱 콘텐츠** — [app-content.md](app-content.md) 답변을 그대로 입력.
   - 개인정보처리방침 URL (게시 후 입력)
   - 광고 포함 여부, 앱 액세스 권한, 콘텐츠 등급, 대상 연령, 뉴스 앱 여부, COVID-19 추적, 정부 앱, 금융 기능
4. **데이터 보안 양식** — [data-safety.md](data-safety.md) 그대로 입력.
5. **스토어 등록정보 → 메인 스토어 등록정보** — [store-listing-ko.md](store-listing-ko.md) → 한국어. 영어 추가 후 [store-listing-en.md](store-listing-en.md) 붙여넣기.
6. **그래픽 자산 업로드** — [assets-checklist.md](assets-checklist.md) 참고.
7. **앱 카테고리** — 도서/참고자료(Books & Reference) 또는 라이프스타일. 권장: **도서/참고자료**.
8. **연락처 정보** — 이메일 필수.
9. **테스트 트랙 (내부 테스트)** — AAB 업로드 → 테스터 1명 이상 추가 후 실행 검증.
10. **프로덕션 출시** — 전 항목 ✅ 되면 출시 신청 → 심사 (보통 1~7일).

## 빌드 아티팩트 생성 및 릴리즈 파일 배포 표준규격 (SOP)

AAB(Android App Bundle, Play Console 필수)는 다음 명령으로 생성한다.

```powershell
.\gradlew :app:bundleRelease
```

### 📌 릴리즈 파일 명명법 및 배포 규격 (박제)

다시는 까먹지 않도록 정리된 BrioDo, MarkLeaf 등 타 안드로이드 프로젝트들과 완전히 동일한 **정식 릴리즈 배포 공식 규격**입니다.

1. **산출물 파일명 정의**:
   - **AAB 파일**: `<AppName>-v<VersionName>-vc<VersionCode>.aab` (예: `PulpitInk-v1.2.0-vc3.aab`)
   - **릴리즈 노트 파일**: `<AppName>-v<VersionName>-vc<VersionCode>-release-notes.txt` (예: `PulpitInk-v1.2.0-vc3-release-notes.txt`)

2. **통합 다국어 출시 노트 (Combined Release Notes) 형식**:
   Play Console 업로드 시 한눈에 보고 붙여넣을 수 있도록 다국어 태그 형식을 적용합니다.
   ```xml
   <ko-KR>
   - 한국어 출시 내용
   </ko-KR>
   <en-US>
   - English release content
   </en-US>
   ```

3. **자동 배포 대상 경로**:
   릴리즈 빌드 완료 시 개발자가 직접 폴더를 탐색하지 않고 웹 업로드가 가능하도록 **바탕 화면에 자동 내보내기**합니다.
   - **OneDrive 바탕 화면**: `C:\Users\jeiel\OneDrive\바탕 화면\`
   - **로컬 바탕 화면**: `C:\Users\jeiel\Desktop\`
   - **프로젝트 내 저장소**: `d:\Project\pulpit-ink-android\.build-outputs\`

## 결정된 값

- **개인정보처리방침 URL**: `https://jeiel85.github.io/pulput-ink-android/privacy-policy.html`
  - 게시 방법은 [github-pages-setup.md](github-pages-setup.md) 참고. `docs/privacy-policy.html` 파일이 이미 준비됨.
- **연락처 이메일**: `pedaiah85@gmail.com`
- **앱 카테고리**: 도서/참고자료 (Books & Reference)

## 남은 미정 — 자산만

- **스크린샷 / 그래픽 이미지 / 512×512 아이콘** — 현재 미보유. [assets-checklist.md](assets-checklist.md) 참고. 내부 테스트 트랙은 아이콘만 있어도 시작 가능.
