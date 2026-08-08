import { type NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const BACKEND_BASE_URL = process.env.BACKEND_URL || "http://localhost:8084";

function getForwardHeaders(req: NextRequest): HeadersInit {
  const headers = new Headers();
  req.headers.forEach((value, key) => {
    if (key.toLowerCase() !== "host") {
      headers.set(key, value);
    }
  });
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
      "Content-Type, Authorization, X-Requested-With",
    Vary: "Origin",
  };
}

// 代理层内存滑动窗口限流：每个 IP 允许 60 次/分钟
const RATE_LIMIT_WINDOW_MS = 60 * 1000;
const MAX_REQUESTS_PER_WINDOW = 60;
const ipRequestLogs = new Map<string, number[]>();

function isRateLimited(clientIp: string): boolean {
  const now = Date.now();
  const timestamps = ipRequestLogs.get(clientIp) || [];
  const validTimestamps = timestamps.filter(
    (t) => now - t < RATE_LIMIT_WINDOW_MS,
  );

  if (validTimestamps.length >= MAX_REQUESTS_PER_WINDOW) {
    return true;
  }

  validTimestamps.push(now);
  ipRequestLogs.set(clientIp, validTimestamps);

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
    const backendRes = await fetch(targetUrl, {
      method: "POST",
      headers: getForwardHeaders(req),
      body: bodyText,
    });

    const isSse =
      backendRes.headers.get("content-type")?.includes("text/event-stream") ??
      path.includes("stream");

    if (isSse && backendRes.body) {
      const { readable, writable } = new TransformStream();
      backendRes.body.pipeTo(writable).catch(() => {});

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
