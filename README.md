<div align="center">

<img src="play-console/assets/feature-graphic-1024x500.png" alt="Pulpit Ink" width="900">

# Pulpit Ink

**Sermons, transcribed in ink.**
설교를 녹음하면 AI가 글로, 성경 구절로, 아웃라인으로 옮겨 줍니다.

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)](https://www.android.com/)
[![Min SDK](https://img.shields.io/badge/minSdk-24-blue)](https://developer.android.com/about/versions/nougat)
[![Target SDK](https://img.shields.io/badge/targetSdk-36-blue)](https://developer.android.com/about/versions/16)
[![Kotlin](https://img.shields.io/badge/kotlin-jetpack--compose-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/license-Proprietary-lightgrey)](#license)

[**🌐 jeiel85.github.io/pulput-ink-android**](https://jeiel85.github.io/pulput-ink-android/) &nbsp;·&nbsp; [개인정보처리방침](https://jeiel85.github.io/pulput-ink-android/privacy-policy.html) &nbsp;·&nbsp; [Play Console 자료](play-console/README.md)

</div>

---

## 무엇을 하는 앱인가

Pulpit Ink는 설교자·신학생·평신도 사역자를 위한 **모바일 설교 비서**입니다.
강단에서 마이크 한 번을 누르면, 이후 흐름은 모두 AI가 이어받습니다.

```
🎙  Record  →  ✦ Whisper 전사  →  ✝ Gemini 구절 정리  →  ≡ 아웃라인 생성  →  📁 보관
```

- 녹음·전사·정리·아웃라인을 한 앱 안에서
- **로컬 Whisper 모델**(tiny / base / small)로 인터넷 없이도 전사 가능
- 분석·광고·로그인·계정 SDK **일체 없음**
- 모든 사용자 데이터는 단말 내 Room DB에만 저장

---

## 주요 기능

| | |
|---|---|
| 🎙 **One-tap 녹음** | 백그라운드 녹음 없음. 사용자가 시작·종료를 통제. |
| ✦ **AI 자동 전사** | OpenAI Whisper API(클라우드) + 단말 내 로컬 ggml 모델 둘 다 지원. |
| ✝ **성경 구절 정리** | Gemini가 본문 속 인용을 `창세기 1:1`·`John 3:16` 표준 표기로 정규화. |
| ≡ **아웃라인 자동 생성** | 서론·본론·결론·핵심 메시지·적용점을 마크다운으로 자동 작성. |
| ⌕ **로컬 검색** | 제목·본문·태그로 즉시 검색. |
| ⇪ **편집·복사·공유** | 블록 단위 편집, 클립보드, 외부 앱 공유. |

---

## 기술 스택

- **UI**: Jetpack Compose (Material 3), Compose Navigation
- **State**: ViewModel + StateFlow + Compose Lifecycle
- **DB**: Room (KSP)
- **Network**: Retrofit + OkHttp + Moshi (kotlin-codegen)
- **AI / STT**: OpenAI Whisper API + 단말 내 [whisper.cpp](https://github.com/ggerganov/whisper.cpp) ggml 모델
- **AI / Text**: Google Gemini API
- **Async**: kotlinx.coroutines
- **Secrets**: `secrets-gradle-plugin` (`.env` / `.env.example`)
- **Test**: JUnit, Robolectric, Roborazzi, Compose UI Test

---

## 디렉터리 구조

```
pulpit-ink-android/
├─ app/
│  ├─ src/main/
│  │  ├─ java/com/example/
│  │  │  ├─ data/api/          ← Gemini · Whisper API 클라이언트
│  │  │  ├─ data/repository/   ← SermonRepository (Room + API 조합)
│  │  │  ├─ ui/screens/        ← Home / Recording / Detail / Whisper Manager
│  │  │  ├─ ui/viewmodel/      ← SermonViewModel
│  │  │  └─ ui/theme/          ← Material 3 테마, 타이포
│  │  └─ res/
│  │     ├─ values/            ← strings.xml (en)
│  │     └─ values-ko/         ← strings.xml (ko)
│  └─ build.gradle.kts
├─ docs/                       ← GitHub Pages 랜딩 + 개인정보처리방침
├─ play-console/               ← Play Store 등록 자료 일체
│  ├─ store-listing-ko.md      ← 한국어 스토어 등록정보
│  ├─ store-listing-en.md      ← 영어 스토어 등록정보
│  ├─ app-content.md           ← 정책/콘텐츠 양식 답변
│  ├─ data-safety.md           ← 데이터 보안 양식
│  ├─ content-rating.md        ← IARC 콘텐츠 등급
│  ├─ assets/                  ← 아이콘·그래픽 이미지·생성 스크립트
│  └─ release-notes/           ← 버전별 출시 노트
├─ .keystore/                  ← (gitignored) 릴리즈 키스토어 + 비번 백업
└─ .env.example                ← API 키 템플릿
```

---

## 빌드 & 실행

### 1. 사전 준비

- [Android Studio](https://developer.android.com/studio) Hedgehog 이상
- JDK 11+ (Android Studio 내장 사용 가능)

### 2. 저장소 클론 후 API 키 설정

```powershell
git clone https://github.com/jeiel85/pulput-ink-android.git
cd pulput-ink-android
copy .env.example .env       # 또는 cp .env.example .env (POSIX)
```

`.env` 파일을 열어 본인의 키로 채워 넣습니다.

```
GEMINI_API_KEY=AIza...               # https://aistudio.google.com/app/apikey
OPENAI_API_KEY=sk-...                # https://platform.openai.com/api-keys (선택)
```

> Whisper 로컬 모델만 쓰면 `OPENAI_API_KEY`는 비워둬도 동작합니다.

### 3. Android Studio에서 실행

1. Android Studio → **Open** → 이 디렉터리 선택
2. Gradle sync 완료 대기
3. 디바이스/에뮬레이터 선택 → ▶ Run

### 4. 명령줄 빌드

```powershell
# 디버그 APK
.\gradlew :app:assembleDebug

# 릴리즈 AAB (Play Store 업로드용, 자동 서명됨)
.\gradlew :app:bundleRelease
```

결과물: `app/build/outputs/bundle/release/app-release.aab`

---

## 릴리즈 서명

릴리즈 키스토어와 비밀번호는 [`.keystore/`](.keystore/) 하위에 보관되며 git에서 무시됩니다.
루트의 `keystore.properties`(gitignored)가 다음 항목을 읽도록 [app/build.gradle.kts](app/build.gradle.kts)가 구성되어 있습니다.

```properties
storeFile=.keystore/pulpitink-release.jks
storePassword=...
keyAlias=pulpitink
keyPassword=...
```

키스토어 분실은 **앱 업데이트 불가**로 직결됩니다. 별도 안전 저장소(1Password, 외장 SSD 등)에도 사본을 두십시오.
자세한 내용은 `.keystore/KEYSTORE_BACKUP.md` 참고 (로컬 전용).

---

## Play Store 등록

최초 등록에 필요한 모든 메타데이터·답변·이미지가 [`play-console/`](play-console/) 폴더에 준비되어 있습니다.

| 자료 | 위치 |
|---|---|
| 한/영 스토어 등록정보 | [store-listing-ko.md](play-console/store-listing-ko.md) · [store-listing-en.md](play-console/store-listing-en.md) |
| 정책 양식 답변 | [app-content.md](play-console/app-content.md) |
| 데이터 보안 | [data-safety.md](play-console/data-safety.md) |
| 콘텐츠 등급 | [content-rating.md](play-console/content-rating.md) |
| 그래픽 자산 | [assets/](play-console/assets/) (512 아이콘, 1024×500 그래픽 이미지) |
| GitHub Pages 게시 절차 | [github-pages-setup.md](play-console/github-pages-setup.md) |
| 전체 흐름 | [play-console/README.md](play-console/README.md) |

---

## 개인정보 보호

- 사용자 음성·전사·아웃라인은 모두 **단말 내 Room DB**에만 저장
- 분석·광고·로그인 SDK 일체 없음
- AI 기능 호출 시에만 사용자 등록 API 키로 OpenAI / Google에 HTTPS 전송
- 자세한 내용: [개인정보처리방침](https://jeiel85.github.io/pulput-ink-android/privacy-policy.html)

---

## License

Proprietary — © 2026 AI Studio. All rights reserved.
별도 안내 없는 한 본 저장소의 코드·자산은 라이선스가 부여되지 않습니다. 협업 문의는 [pedaiah85@gmail.com](mailto:pedaiah85@gmail.com)으로 주세요.
