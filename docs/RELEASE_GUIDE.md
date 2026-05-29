# Pulpit Ink - 새 버전 출시 및 배포 자동화 가이드 (v1.5.0)

이 가이드는 Pulpit Ink 안드로이드 앱의 새로운 버전 출시 준비를 마치고 배포용 서명 AAB 번들 및 로컬라이징 출시 노트를 원클릭으로 Windows 바탕화면에 추출해내는 절차를 정리합니다.

---

## 1. 출시 준비 메타데이터 구조

Pulpit Ink의 메타데이터 및 릴리즈 체크 리스트는 다음 디렉터리에 집중되어 있습니다:
- **버전 번호**: [app/build.gradle.kts](file:///d:/Project/pulpit-ink-android/app/build.gradle.kts) (`versionName` 및 `versionCode`)
- **출시 노트**: [play-console/release-notes/](file:///d:/Project/pulpit-ink-android/play-console/release-notes/)
  - `1.5.0-ko-KR.txt`: 한국어 스토어용 업데이트 요약 정보
  - `1.5.0-en-US.txt`: 영어 스토어용 업데이트 요약 정보

---

## 1-A. (필수) 번들 모델 바이너리 확보

기본 Whisper 모델(`ggml-base.bin`, 약 142 MB)은 **Play Asset Delivery(fast-follow)** 로
앱 번들에 포함되어, 사용자가 앱 설치 직후 별도 조작 없이 자동으로 내려받습니다.

이 142 MB 바이너리는 **git에 커밋하지 않으므로**(`.gitignore` 처리됨), 릴리즈 번들을
빌드하기 **전에 반드시** 아래 스크립트로 받아서 `:base_model` 에셋 팩 안에 채워야 합니다.
파일이 없으면 에셋 팩이 비어 배포 후 모델 자동 다운로드가 동작하지 않습니다.

```powershell
pwsh -File scripts/fetch-base-model.ps1
```

- 이미 올바른 크기의 파일이 있으면 건너뜁니다(재실행 안전). 다시 받으려면 `-Force`.
- 위치: `base_model/src/main/assets/models/ggml-base.bin`

> [!IMPORTANT]
> `installDebug`/사이드로드 등 **Play를 거치지 않는 설치**에서는 fast-follow 에셋이
> 전달되지 않습니다. 이 경우 앱은 온보딩 화면에서 Hugging Face 직접 다운로드로
> fallback 합니다. 정식 Play 배포(AAB)에서만 자동 번들 전달이 동작합니다.

---

## 2. 바탕화면 배포 자동화 스크립트 실행

새 릴리즈 빌드(AAB)를 생성하고 릴리즈 노트를 Windows 바탕화면으로 즉각 DUMP 복사하기 위해, 루트 디렉터리에 배포 전용 파워쉘 스크립트가 준비되어 있습니다:
- **스크립트 경로**: [scripts/export_play_console_assets.ps1](file:///d:/Project/pulpit-ink-android/scripts/export_play_console_assets.ps1)

### 실행 방법

1. **PowerShell** 또는 **Android Studio Terminal**을 엽니다.
2. 프로젝트 루트 디렉터리(`d:\Project\pulpit-ink-android`)로 이동합니다.
3. 다음 명령어를 실행하여 원클릭 빌드 및 바탕화면 추출을 수행합니다:

```powershell
# 빌드와 바탕화면 복사를 원클릭으로 수행 (자동 서명 적용)
.\scripts\export_play_console_assets.ps1
```

> [!TIP]
> 만약 수동으로 이미 빌드를 완료한 상태여서 **복사 작업만 빠르게 수행**하고 싶다면, `-SkipBuild` 플래그를 추가하면 빌드 단계를 건너뛰고 1초만에 바탕화면으로 파일만 추출합니다:
> ```powershell
> .\scripts\export_play_console_assets.ps1 -SkipBuild
> ```

---

## 3. 추출 완료 아티팩트 검증

스크립트가 성공적으로 종료되면, Windows **바탕화면(Desktop)** 에 다음과 같은 3개의 릴리즈 자산 파일이 자동으로 안전 생성됩니다:

1. `PulpitInk-v1.5.0-vc7.aab`
   - Play Store에 즉시 업로드 가능한 프로덕션 서명 릴리즈 번들입니다.
2. `PulpitInk-v1.5.0-vc7-release-notes-ko.txt`
   - 한국어 버전 Play Store 업데이트 상세 텍스트입니다.
3. `PulpitInk-v1.5.0-vc7-release-notes-en.txt`
   - 영어 버전 Play Store 업데이트 상세 텍스트입니다.

---

## 4. Play Console 출시 업로드 절차

1. **Google Play Console** 로그인 -> Pulpit Ink 앱 선택
2. **출시** -> **프로덕션** 진입 -> **새 출시 만들기** 클릭
3. 바탕화면에 복사된 `PulpitInk-v1.5.0-vc7.aab` 파일을 드래그 앤 드롭하여 업로드합니다.
4. **출시 노트** 입력창에 바탕화면의 `release-notes-ko.txt` 내용(한국어 로케일) 및 `release-notes-en.txt` 내용(영어 로케일)을 복사해서 붙여넣습니다.
5. **출시 검토 및 시작**을 누르면 정식 배포 준비가 완벽히 완료됩니다.

---

## 5. 글로벌 출시 노트 작성 규칙 (글로벌 규칙 박제)

> [!IMPORTANT]
> **[사용자 중심 출시 노트 작성 규칙 (User-Centric Release Notes Rule)]**
> 플레이 스토어 출시 노트는 일반 사용자를 대상으로 하므로, 개발자 중심의 기술 용어를 배제하고 기능적 편의 위주로 쉽고 부드럽게 정제하여 작성해야 합니다.
> - **배제해야 할 기술적/개발자 용어 예시**: `Compose`, `AnimatedContent`, `FGS`, `BOM`, `레이아웃/개행 버그`, `Gradle`, `컴파일`, `UI Screens` 등
> - **지향해야 할 사용자 친화적 표현 예시**: 
>   * *기술적*: "AnimatedContent를 사용한 슬라이딩 화면 전환 애니메이션 구현" ➡️ *사용자 중심*: "화면을 열고 닫을 때 부드럽게 흘러가는 슬라이딩 연출을 더해 네이티브 특유의 고급스러운 감성을 높였습니다."
>   * *기술적*: "텍스트 겹침/개행 버그 및 뷰포트 Wrapping 문제 해결" ➡️ *사용자 중심*: "화면이 좁은 스마트폰에서도 글자가 찌그러지거나 잘리지 않고 깔끔하게 정돈되어 보이도록 레이아웃을 다듬었습니다."
> - **핵심**: 사용자가 이번 버전을 설치했을 때 피부로 느끼는 실질적인 UI/UX 및 기능적 이점을 위주로 설명할 것.

