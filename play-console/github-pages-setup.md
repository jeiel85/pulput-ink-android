# GitHub Pages 게시 절차 — 개인정보처리방침 URL 활성화

Play Console에 입력할 개인정보처리방침 URL을 활성화하기 위해 GitHub Pages를 켜는 절차다.

## 게시될 URL

| 페이지 | URL |
|---|---|
| 홈 | `https://jeiel85.github.io/pulput-ink-android/` |
| 개인정보처리방침 | `https://jeiel85.github.io/pulput-ink-android/privacy-policy.html` |

> Repo 이름이 `pulput-ink-android` (오타가 아니라 현재 원격 그대로). 변경하고 싶으면 GitHub 측 repo 이름을 먼저 바꾸고 위 URL도 갱신할 것.

## 사전 준비 (이미 완료됨)

- `docs/index.html` — 홈 페이지
- `docs/privacy-policy.html` — 한/영 개인정보처리방침
- `docs/.nojekyll` — Jekyll 처리 비활성화 (정적 HTML 그대로 서빙)

## 활성화 절차

1. 변경사항을 커밋·푸시한다.
   ```powershell
   git add docs/ play-console/
   git commit -m "Add Play Console docs and privacy policy site"
   git push origin main
   ```

2. **Repo가 Public인지 확인**한다. (GitHub Pages 무료 플랜은 Public repo만 지원. Private이면 GitHub Pro 이상 필요.)
   - GitHub 웹: Settings → General → Danger Zone → "Change repository visibility"

3. GitHub 웹에서 **Settings → Pages** 진입.

4. **Build and deployment**
   - Source: **Deploy from a branch**
   - Branch: **main** / 폴더: **/docs**
   - Save 클릭.

5. 1~2분 후 페이지 상단에 "Your site is live at `https://jeiel85.github.io/pulput-ink-android/`" 안내 표시. 위 URL로 접속해 한국어/영어 본문이 모두 정상 출력되는지 확인.

6. Play Console → 정책 → 앱 콘텐츠 → 개인정보처리방침에 다음 URL 입력:
   ```
   https://jeiel85.github.io/pulput-ink-android/privacy-policy.html
   ```

## 정책 본문 수정이 필요할 경우

`docs/privacy-policy.html`을 직접 수정 → 커밋·푸시. GitHub Pages가 자동으로 1~2분 내에 재배포한다. 원본 마크다운은 [privacy-policy.md](privacy-policy.md)에 유지하고 있으므로 양쪽을 함께 갱신할 것.

## 트러블슈팅

- **404가 뜨는 경우** — Pages 설정이 `main` / `/docs`로 되어 있는지, 파일이 실제로 main 브랜치에 푸시되었는지 확인.
- **CSS가 깨지거나 빈 페이지** — `.nojekyll` 파일이 docs/ 안에 함께 커밋되었는지 확인.
- **HTTPS 인증서 오류** — Pages 활성화 직후 몇 분간 발생 가능. 잠시 후 재시도.
