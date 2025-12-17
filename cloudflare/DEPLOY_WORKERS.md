# Cloudflare Pages Functions 구성 (백엔드 프록시)

이 문서는 Cloudflare Pages에서 `_worker.js`를 사용해 백엔드(Spring 서버 등)로 요청을 프록시하는 방법을 설명합니다.

## 파일 위치
- 레포지토리: `cloudflare/_worker.js`
- GitHub Actions에서 `pages/_worker.js`로 복사되어 배포됩니다.

## 환경 변수 설정
Cloudflare Pages 프로젝트의 **Environment Variables**에 다음 값을 추가하세요.
- `BACKEND_URL`: 백엔드 서버의 베이스 URL (예: `https://api.example.com` 또는 `https://your-render-app.onrender.com`)

## 라우팅 규칙
- 다음 경로는 백엔드로 프록시됩니다:
  - `/api/*`
  - `/oauth2/*`
  - `/logout`
- 그 외 경로는 정적 자산(`index.html`, `static/*`)으로 처리됩니다.

## CORS
- 동일 도메인에서 사용하면 CORS가 필요 없습니다.
- 다른 도메인에 백엔드를 두는 경우, `_worker.js`에서 기본적인 CORS 헤더를 설정했습니다. 필요에 따라 `Access-Control-Allow-Origin`을 구체 도메인으로 제한하세요.

## 배포 흐름
1. `main` 브랜치에 푸시 → GitHub Actions가 `pages/` 디렉터리를 준비(`index.html`, `static`, `_worker.js`).
2. 아티팩트 업로드 후 Cloudflare Pages로 퍼블리시.
3. Pages 도메인에서 `/api/*`, `/oauth2/*`, `/logout` 요청이 `BACKEND_URL`로 프록시됩니다.

## 백엔드 배포 선택지
- Docker 없이: Render(Java), Railway(buildpacks), Azure App Service(Java), Google App Engine(Standard) 등.
- Docker 기반: Cloud Run, Fly.io, Koyeb 등.

## 트러블슈팅
- 500 오류: `BACKEND_URL` 설정 누락 여부 확인.
- 인증/쿠키: 동일 도메인 사용 시 기본 전달됩니다. 서브도메인 조합 시 쿠키 도메인/보안 설정을 확인하세요.
- 경로 매칭: 프록시 경로가 요구사항과 다르면 `_worker.js`에서 조건을 수정하세요.