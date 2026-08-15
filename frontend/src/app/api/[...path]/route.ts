import { type NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const BACKEND_BASE_URL = process.env.BACKEND_URL || "http://localhost:8084";

function getForwardHeaders(req: NextRequest): HeadersInit {
  const headers = new Headers();
  // 仅透传显式白名单内的客户端头，避免把任意客户端可控头（含认证相关头）
  // 转发到后端造成伪造后门。后端采信的受信任头一律由网关注入值覆盖。
  const ALLOWED_FORWARD_HEADERS = new Set([
    "content-type",
    "authorization",
    "accept",
    "x-requested-with",
    "x-forwarded-for",
    "x-real-ip",
  ]);
  req.headers.forEach((value, key) => {
    if (ALLOWED_FORWARD_HEADERS.has(key.toLowerCase())) {
      headers.set(key, value);
    }
  });
  // 受信任身份头 X-User-Id 是认证边界：生产环境下仅由上游网关 Caddy
  // (basic_auth) 注入，后端据此做多租户隔离，绝不信任客户端请求中的 X-User-Id。
  // 若直连 frontend 而无 Caddy（如本地开发），可设 PROXY_TRUST_X_USER_ID=true
  // 临时放行透传以便调试；生产部署必须保持 false（默认），否则存在身份伪造后门。
  if (process.env.PROXY_TRUST_X_USER_ID === "true") {
    const userId = req.headers.get("x-user-id");
    if (userId) {
      headers.set("X-User-Id", userId);
    }
  }
  return headers;
}

function copyBackendHeaders(
  backendRes: Response,
  extraHeaders?: Record<string, string>,
): Headers {
  const headers = new Headers();
  backendRes.headers.forEach((value, key) => {
    if (key.toLowerCase() !== "content-encoding") {
      headers.set(key, value);
    }
  });
  if (extraHeaders) {
    Object.entries(extraHeaders).forEach(([k, v]) => {
      headers.set(k, v);
    });
  }
  return headers;
}

function getFallbackCorsHeaders(req?: NextRequest): Record<string, string> {
  const allowedOriginEnv = process.env.CORS_ALLOWED_ORIGINS;
  const requestOrigin = req?.headers.get("origin");

  let allowOrigin = "*";
  if (allowedOriginEnv && allowedOriginEnv !== "*") {
    const allowedList = allowedOriginEnv.split(",").map((o) => o.trim());
    if (requestOrigin && allowedList.includes(requestOrigin)) {
      allowOrigin = requestOrigin;
    } else {
      allowOrigin = allowedList[0] || "*";
    }
  }

  return {
    "Access-Control-Allow-Origin": allowOrigin,
    "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers":
      "Content-Type, Authorization, X-Requested-With, X-User-Id",
    Vary: "Origin",
  };
}

// 代理层内存滑动窗口限流：每个 IP 允许 120 次/分钟
const RATE_LIMIT_WINDOW_MS = 60 * 1000;
const MAX_REQUESTS_PER_WINDOW = 120;
const ipRequestLogs = new Map<string, number[]>();

function isRateLimited(clientIp: string): boolean {
  const now = Date.now();
  const timestamps = ipRequestLogs.get(clientIp) || [];
  const validTimestamps = timestamps.filter(
    (t) => now - t < RATE_LIMIT_WINDOW_MS,
  );

  ipRequestLogs.set(clientIp, validTimestamps);

  if (validTimestamps.length >= MAX_REQUESTS_PER_WINDOW) {
    return true;
  }

  validTimestamps.push(now);

  // 定期清理过期的 IP 缓存记录，防止内存泄露
  if (ipRequestLogs.size > 1000) {
    for (const [ip, tsList] of ipRequestLogs.entries()) {
      if (tsList.every((t) => now - t >= RATE_LIMIT_WINDOW_MS)) {
        ipRequestLogs.delete(ip);
      }
    }
  }

  return false;
}

function getClientIp(req: NextRequest): string {
  return (
    req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ||
    req.headers.get("x-real-ip") ||
    "127.0.0.1"
  );
}

// RAG 入库/重入库涉及文本切块与多次 Embedding 请求，耗时较长，给予更长超时。
const RAG_INGEST_TIMEOUT_MS = 60_000;

function isRagIngestPath(path: string[]): boolean {
  return (
    path.length >= 2 &&
    path[0] === "rag" &&
    (path[1] === "ingest" || path[1] === "reingest")
  );
}

/**
 * 合并「客户端断开」与「长时间未响应超时」两个信号。
 * RAG 入库等耗时端点超时后中止后端请求，避免代理连接长期挂死。
 */
function createProxySignal(
  req: NextRequest,
  timeoutMs?: number,
): { signal: AbortSignal; cleanup: () => void } {
  const controller = new AbortController();
  const onClientAbort = () => controller.abort();
  req.signal.addEventListener("abort", onClientAbort);

  let timer: ReturnType<typeof setTimeout> | undefined;
  if (timeoutMs && timeoutMs > 0) {
    timer = setTimeout(() => controller.abort(), timeoutMs);
  }

  const cleanup = () => {
    req.signal.removeEventListener("abort", onClientAbort);
    if (timer) clearTimeout(timer);
  };

  return { signal: controller.signal, cleanup };
}

function getTargetUrl(pathSegments: string[], search: string): string {
  const cleanBase = BACKEND_BASE_URL.replace(/\/api\/:path\*/, "")
    .replace(/\/api$/, "")
    .replace(/\/$/, "");
  const target = `${cleanBase}/api/${pathSegments.join("/")}`;
  return search ? `${target}${search}` : target;
}

export async function OPTIONS(
  req: NextRequest,
  { params }: { params: Promise<{ path: string[] }> },
) {
  const { path } = await params;
  const targetUrl = getTargetUrl(path, req.nextUrl.search);
  try {
    const backendRes = await fetch(targetUrl, {
      method: "OPTIONS",
      headers: getForwardHeaders(req),
    });
    return new NextResponse(null, {
      status: backendRes.status,
      headers: copyBackendHeaders(backendRes, {
        "Access-Control-Max-Age": "86400",
      }),
    });
  } catch {
    const fallbackCors = getFallbackCorsHeaders(req);
    return new NextResponse(null, {
      status: 204,
      headers: {
        ...fallbackCors,
        "Access-Control-Max-Age": "86400",
      },
    });
  }
}

export async function POST(
  req: NextRequest,
  { params }: { params: Promise<{ path: string[] }> },
) {
  const fallbackCors = getFallbackCorsHeaders(req);
  const clientIp = getClientIp(req);
  if (isRateLimited(clientIp)) {
    return NextResponse.json(
      {
        error: true,
        message: "请求过于频繁，请稍后再试 (429 Rate Limit Exceeded)",
      },
      {
        status: 429,
        headers: {
          ...fallbackCors,
          "Retry-After": "60",
        },
      },
    );
  }

  const { path } = await params;
  const targetUrl = getTargetUrl(path, req.nextUrl.search);

  try {
    const bodyText = await req.text();
    // 合并「客户端断开」与「耗时超时」信号：RAG 入库给予更长超时，
    // 避免大文件切块 + 多次 Embedding 期间代理连接长期挂死。
    const { signal, cleanup } = createProxySignal(
      req,
      isRagIngestPath(path) ? RAG_INGEST_TIMEOUT_MS : undefined,
    );

    const backendRes = await fetch(targetUrl, {
      method: "POST",
      headers: getForwardHeaders(req),
      body: bodyText,
      signal,
    });

    const isSse =
      backendRes.headers.get("content-type")?.includes("text/event-stream") ??
      path.includes("stream");

    if (isSse && backendRes.body) {
      const { readable, writable } = new TransformStream();
      // 代理写端关闭（客户端已断开或超时）时取消监听并中止后端请求，释放上游资源。
      const pipeDone = backendRes.body.pipeTo(writable);
      pipeDone.catch(() => cleanup()).finally(cleanup);
      if (req.signal.aborted) {
        cleanup();
      }

      return new Response(readable, {
        status: backendRes.status,
        headers: copyBackendHeaders(backendRes, {
          "Content-Type": "text/event-stream; charset=utf-8",
          "Cache-Control": "no-cache, no-transform",
          "X-Accel-Buffering": "no",
          "X-Content-Type-Options": "nosniff",
          Connection: "keep-alive",
        }),
      });
    }

    cleanup();

    return new Response(backendRes.body, {
      status: backendRes.status,
      headers: copyBackendHeaders(backendRes),
    });
  } catch (err) {
    return NextResponse.json(
      { error: true, message: (err as Error).message },
      { status: 500, headers: fallbackCors },
    );
  }
}

export async function DELETE(
  req: NextRequest,
  { params }: { params: Promise<{ path: string[] }> },
) {
  const fallbackCors = getFallbackCorsHeaders(req);
  const { path } = await params;
  const targetUrl = getTargetUrl(path, req.nextUrl.search);

  try {
    const backendRes = await fetch(targetUrl, {
      method: "DELETE",
      headers: getForwardHeaders(req),
    });
    return new Response(backendRes.body, {
      status: backendRes.status,
      headers: copyBackendHeaders(backendRes),
    });
  } catch (err) {
    return NextResponse.json(
      { error: true, message: (err as Error).message },
      { status: 500, headers: fallbackCors },
    );
  }
}

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ path: string[] }> },
) {
  const fallbackCors = getFallbackCorsHeaders(req);
  const clientIp = getClientIp(req);
  if (isRateLimited(clientIp)) {
    return NextResponse.json(
      {
        error: true,
        message: "请求过于频繁，请稍后再试 (429 Rate Limit Exceeded)",
      },
      {
        status: 429,
        headers: {
          ...fallbackCors,
          "Retry-After": "60",
        },
      },
    );
  }

  const { path } = await params;
  const targetUrl = getTargetUrl(path, req.nextUrl.search);

  try {
    const backendRes = await fetch(targetUrl, {
      method: "GET",
      headers: getForwardHeaders(req),
    });
    return new Response(backendRes.body, {
      status: backendRes.status,
      headers: copyBackendHeaders(backendRes),
    });
  } catch (err) {
    return NextResponse.json(
      { error: true, message: (err as Error).message },
      { status: 500, headers: fallbackCors },
    );
  }
}

export async function PUT(
  req: NextRequest,
  { params }: { params: Promise<{ path: string[] }> },
) {
  const fallbackCors = getFallbackCorsHeaders(req);
  const clientIp = getClientIp(req);
  if (isRateLimited(clientIp)) {
    return NextResponse.json(
      {
        error: true,
        message: "请求过于频繁，请稍后再试 (429 Rate Limit Exceeded)",
      },
      {
        status: 429,
        headers: {
          ...fallbackCors,
          "Retry-After": "60",
        },
      },
    );
  }

  const { path } = await params;
  const targetUrl = getTargetUrl(path, req.nextUrl.search);

  try {
    const bodyText = await req.text();
    const backendRes = await fetch(targetUrl, {
      method: "PUT",
      headers: getForwardHeaders(req),
      body: bodyText,
    });
    return new Response(backendRes.body, {
      status: backendRes.status,
      headers: copyBackendHeaders(backendRes),
    });
  } catch (err) {
    return NextResponse.json(
      { error: true, message: (err as Error).message },
      { status: 500, headers: fallbackCors },
    );
  }
}
