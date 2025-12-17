# Mood Melody 배포 가이드 (Cloudflare Pages + GitHub)

이 프로젝트는 백엔드(Spring/Kotlin)와 템플릿을 포함하고 있지만, Cloudflare Pages는 정적 사이트만 호스팅합니다. 따라서 현재는 `index.html`을 랜딩 페이지로 배포하고, 백엔드 기능(예: `/oauth2/authorization/google`)은 별도 도메인/서버에서 제공해야 합니다.

## 1) Cloudflare Pages 프로젝트 생성
- Cloudflare 대시보드 → Pages → Create Project → **Create** → **Direct Upload** 또는 **Connect to Git** 중 선택
  - 본 워크플로는 GitHub Actions에서 업로드하므로 **Direct Upload 방식**을 권장합니다.
- 프로젝트 이름을 `mood-melody`로 설정하세요. (워크플로의 `projectName`과 일치해야 합니다)

## 2) API 토큰 및 계정 ID 준비
- Cloudflare 대시보드 → My Profile → API Tokens → Create Token → **Pages** 권한 포함 토큰 생성
- 계정 ID(Account ID)는 대시보드 우측 상단 또는 Workers/Pages 설정에서 확인 가능합니다.

## 3) GitHub 리포지토리 시크릿 추가
- GitHub 리포지토리 → Settings → Secrets and variables → Actions → New repository secret
  - `CLOUDFLARE_API_TOKEN` : 위에서 생성한 토큰 값
  - `CLOUDFLARE_ACCOUNT_ID` : Cloudflare Account ID
  - `GITHUB_TOKEN` : 기본 제공 (Actions에서 자동 주입), 별도 등록 불필요

## 4) 워크플로 동작
- 브랜치 `main`에 푸시/PR 시:
  - `src/main/resources/templates/index.html`을 `pages/` 폴더로 복사합니다.
  - `src/main/resources/static` 폴더가 비어있지 않다면 함께 복사합니다.
  - 아티팩트 `site`를 업로드 후, Cloudflare Pages로 퍼블리시합니다.

## 5) 중요한 제한사항
- `home.html` 등 Thymeleaf, Spring Security 태그(`th:*`, `sec:*`)가 들어있는 템플릿은 **서버 렌더링이 필요**하므로 Cloudflare Pages에서 바로 동작하지 않습니다.
- `index.html`의 `/oauth2/authorization/google` 링크는 **백엔드 도메인**으로 연결되도록 수정하거나, Cloudflare Pages Functions/Workers로 프록시를 구성해야 합니다.

## 6) 다음 단계(선택 사항)
- 별도 백엔드 배포(예: Render, Railway, Fly.io 등) 후, Cloudflare Pages 도메인에서 해당 백엔드를 프록시하도록 설정합니다.
- Pages Functions(`/_workers.js`)를 추가해 특정 경로(`/oauth2/*`, `/api/*`)를 백엔드로 프록시합니다.
- 완전한 프론트엔드/백엔드 분리를 원한다면, 정적 프론트엔드 리포지토리를 분리하고 이 리포지토리를 백엔드 전용으로 관리하는 방식을 고려하세요.

## 7) 확인 방법
- `main`에 푸시하면 Actions가 성공적으로 실행되는지 확인합니다.
- Cloudflare Pages 프로젝트 `mood-melody`의 Deployments에서 배포 성공 여부를 확인합니다.
- 배포된 도메인에서 `index.html` 랜딩 페이지 로드 확인 후, 백엔드 링크 동작은 별도 서버/프록시 구성에 따라 확인합니다.

## 8) 백엔드 환경 변수 설정
- 백엔드 호스팅(Render/Railway 등)에 다음 변수를 추가하세요:
  - `GEMINI_API_KEY` : 제공된 키 값
  - `GEMINI_MODEL` : 기본 `gemini-1.5-flash`(선택)
- 로컬 개발에서는 `src/main/resources/application-local.yml`에 키가 설정되어 있으므로 다음으로 실행하세요:
  - `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun`