export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // 프록시 대상 경로 정의: 백엔드로 넘길 라우트
    const shouldProxy = (
      url.pathname.startsWith('/api/') ||
      url.pathname.startsWith('/oauth2/') ||
      url.pathname === '/logout'
    );

    if (shouldProxy) {
      if (!env.BACKEND_URL) {
        return new Response('BACKEND_URL is not configured', { status: 500 });
      }

      const backend = new URL(env.BACKEND_URL);
      backend.pathname = url.pathname;
      backend.search = url.search;

      // CORS 프리플라이트 처리(필요 시)
      if (request.method === 'OPTIONS') {
        return new Response(null, {
          status: 204,
          headers: {
            'Access-Control-Allow-Origin': url.origin,
            'Access-Control-Allow-Methods': 'GET,HEAD,POST,PUT,PATCH,DELETE,OPTIONS',
            'Access-Control-Allow-Headers': request.headers.get('Access-Control-Request-Headers') || '*',
            'Access-Control-Allow-Credentials': 'true'
          }
        });
      }

      // 원본 요청을 백엔드로 전달
      const backendReq = new Request(backend.toString(), request);
      const response = await fetch(backendReq);

      // 동일 도메인 사용 시 CORS 불필요. 필요하다면 헤더 보정
      const resHeaders = new Headers(response.headers);
      resHeaders.set('Access-Control-Allow-Origin', url.origin);
      resHeaders.set('Access-Control-Allow-Credentials', 'true');

      return new Response(response.body, {
        status: response.status,
        headers: resHeaders
      });
    }

    // 정적 자산은 Pages가 제공하는 ASSETS 바인딩으로 처리
    return env.ASSETS.fetch(request);
  }
};