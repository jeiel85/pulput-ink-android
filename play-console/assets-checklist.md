# 그래픽 자산 체크리스트

Play Console 메인 스토어 등록정보에 업로드해야 할 이미지·동영상 자산 목록.

## 필수 자산

| 자산 | 규격 | 보유 여부 | 파일 |
|---|---|---|---|
| 앱 아이콘 | 512 × 512 PNG (32-bit, 알파 채널 허용) | ✅ 준비됨 | [assets/icon-512.png](assets/icon-512.png) |
| 그래픽 이미지 (Feature graphic) | 1024 × 500 JPG/PNG (24-bit) | ✅ 준비됨 | [assets/feature-graphic-1024x500.png](assets/feature-graphic-1024x500.png) |
| 스크린샷 (휴대전화 또는 태블릿) | 최소 2개, 최대 8개 | ✅ 준비됨 (10인치 태블릿 7장) | [assets/screenshots/tablet-10/](assets/screenshots/tablet-10/) |

> Play Console은 휴대전화 스크린샷이 없어도 태블릿 스크린샷만으로 등록 가능하지만, 휴대전화 스크린샷이 있으면 휴대전화 사용자 대상 노출이 더 잘됩니다. 휴대전화 스크린샷이 필요해지면 동일한 자동 캡처 흐름을 폰 디바이스에서 다시 실행하면 됩니다.

## 준비된 스크린샷 (10인치 태블릿, 2560 × 1600 가로)

| # | 파일 | 화면 |
|---|---|---|
| 1 | `01-home-empty.png` | 빈 보관함 — "설교 보관함이 비어 있습니다" + 검색바 + 설교 녹음 CTA |
| 2 | `02-whisper-manager.png` | OpenAI Whisper 모델 데스크 — Tiny / Base (사용 중) / Small 다운로드 UI |
| 3 | `03-recording-configure.png` | 설교 메타데이터 설정 — 제목·신학적 주제 입력 + 마이크 버튼 |
| 4 | `04-recording-live.png` | 실시간 녹음 — 보라색 파형, 빨간 카운터 `00:02`, 취소/전사 요청 버튼 |
| 5 | `05-after-record.png` | 보관함 with 설교 항목 — Gemini가 생성한 제목·요약·성경 구절 태그 + "전사 완료" 배지 |
| 6 | `06-detail-brief.png` | 디테일 — 4탭(요약/전사 에디터/AI 개요서/AI 작업) + 추출된 성경 구절 + 오디오 플레이어 |
| 7 | `07-detail-outline.png` | AI 아웃라인 — 이사야 40:31 인용 + 서론/본론 마크다운 |

## 선택 자산

| 자산 | 규격 | 비고 |
|---|---|---|
| 휴대전화 스크린샷 | 9:16, 1080×1920 권장 | 추후 필요시 폰에 같은 빌드 설치 후 동일 캡처 흐름 재실행. |
| 7인치 태블릿 스크린샷 | 16:10 | 현재 10인치 자산이 7인치 자리에도 그대로 통과 가능. |
| 홍보 동영상 | YouTube URL | 30초~2분. 미보유. |

## 폴더 구조 (현재)

```
play-console/assets/
├─ icon-512.png                    ← Play Store 아이콘
├─ icon-1024.png                   ← 고해상도 마스터
├─ adaptive-foreground-1024.png    ← Android adaptive icon (참고용)
├─ adaptive-background-1024.png    ← 〃
├─ feature-graphic-1024x500.png    ← Play Store 그래픽 이미지
├─ landing-hero-1600x900.png       ← GitHub Pages 히어로 (used in docs/)
├─ generate_assets.py              ← 모든 브랜드 자산 재생성 스크립트
└─ screenshots/
   └─ tablet-10/
      ├─ 01-home-empty.png
      ├─ 02-whisper-manager.png
      ├─ 03-recording-configure.png
      ├─ 04-recording-live.png
      ├─ 05-after-record.png
      ├─ 06-detail-brief.png
      └─ 07-detail-outline.png
```

## 자산 재생성

브랜드 자산(아이콘·그래픽·히어로)을 디자인 수정 후 다시 만들고 싶으면:

```powershell
python play-console/assets/generate_assets.py
```

스크립트 한 번 실행으로 모든 PNG가 재생성됩니다. 스크린샷은 디바이스에서 직접 캡처해야 합니다.
