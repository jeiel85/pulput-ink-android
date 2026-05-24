# 그래픽 자산 체크리스트

Play Console 메인 스토어 등록정보에 업로드해야 할 이미지·동영상 자산 목록.

## 필수 자산

| 자산 | 규격 | 보유 여부 | 비고 |
|---|---|---|---|
| 앱 아이콘 | 512 × 512 PNG (32-bit, 알파 채널 허용) | ❌ 미보유 | 현재 `app/src/main/res/mipmap-*/ic_launcher.webp`에 런처 아이콘 존재. Play Console용 512×512 PNG를 별도로 추출 필요. |
| 그래픽 이미지 (Feature graphic) | 1024 × 500 JPG/PNG (24-bit, 알파 없음) | ❌ 미보유 | 스토어 상단 배너. 앱 이름 + "설교를 글로" 같은 한 줄 카피 권장. |
| 전화 스크린샷 | 최소 2개, 최대 8개. 16:9 또는 9:16, 320~3840px | ❌ 미보유 | 주요 화면 3~5장 권장: 홈 / 녹음 / 전사 / 아웃라인 / 보관함 검색. |

## 선택 자산

| 자산 | 규격 | 비고 |
|---|---|---|
| 7인치 태블릿 스크린샷 | 최소 1개 (권장) | 태블릿 지원 시 가산점. |
| 10인치 태블릿 스크린샷 | 최소 1개 (권장) | 〃 |
| 홍보 동영상 | YouTube URL | 30초~2분 권장. 미보유. |

## 자산 생성 절차 메모

1. **앱 아이콘 (512×512)** — `app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp`를 192×192에서 업스케일 또는 디자인 원본(존재한다면)에서 재출력.
2. **스크린샷** — 에뮬레이터에서 한국어 로케일로 부팅 → 주요 화면을 시연하며 `adb shell screencap -p /sdcard/sc.png` 또는 Android Studio Logcat 카메라 버튼으로 캡처. 적정 해상도: 1080×1920 또는 1440×3120.
3. **그래픽 이미지** — Figma / Canva에서 1024×500 캔버스 → 앱 아이콘 + "Pulpit Ink — 설교 녹음·전사" + 짧은 카피.

## 폴더 구조 제안

생성된 자산은 다음 경로에 정리한다.

```
play-console/
  assets/
    icon-512.png
    feature-graphic-1024x500.png
    screenshots/
      phone/
        01-home.png
        02-recording.png
        03-transcript.png
        04-outline.png
        05-search.png
      tablet-7/
      tablet-10/
```

## 진행 순서 권장

1. 아이콘 → 그래픽 이미지 → 스크린샷 순서.
2. 아이콘만 먼저 준비되어도 내부 테스트 트랙은 시작 가능 (그래픽 자산은 프로덕션 출시 직전까지만 준비하면 됨).
