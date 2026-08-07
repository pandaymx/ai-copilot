import { type NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const BACKEND_BASE_URL = process.env.BACKEND_URL || "http://localhost:8084";

// 代理层基础 CORS 响应头
const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
  "Access-Control-Allow-Headers":
    "Content-Type, Authorization, X-Requested-With",
};

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

export async function OPTIONS() {
  return new NextResponse(null, {
    status: 204,
    headers: {
      ...CORS_HEADERS,
      "Access-Control-Max-Age": "86400",
    },
  });
}

export async function POST(
  req: NextRequest,
  { params }: { params: Promise<{ path: string[] }> },
) {
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
          ...CORS_HEADERS,
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
      headers: {
        "Content-Type": req.headers.get("content-type") || "application/json",
        Accept: req.headers.get("accept") || "text/event-stream",
      },
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
        headers: {
          ...CORS_HEADERS,
          "Content-Type": "text/event-stream; charset=utf-8",
          "Cache-Control": "no-cache, no-transform",
          "X-Accel-Buffering": "no",
          "X-Content-Type-Options": "nosniff",
          Connection: "keep-alive",
        },
      });
    }

    return new Response(backendRes.body, {
      status: backendRes.status,
      headers: {
        ...CORS_HEADERS,
        "Content-Type":
          backendRes.headers.get("content-type") || "application/json",
      },
    });
  } catch (err) {
    return NextResponse.json(
      { error: true, message: (err as Error).message },
      { status: 500, headers: CORS_HEADERS },
    );
  }
}

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ path: string[] }> },
) {
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
          ...CORS_HEADERS,
          "Retry-After": "60",
        },
      },
    );
  }

  const { path } = await params;
  const targetUrl = getTargetUrl(path, req.nextUrl.search);

  try {
    const backendRes = await fetch(targetUrl, {
      headers: {
        Accept: req.headers.get("accept") || "application/json",
      },
    });
    const data = await backendRes.json();
    return NextResponse.json(data, {
      status: backendRes.status,
      headers: CORS_HEADERS,
    });
  } catch (err) {
    return NextResponse.json(
      { error: true, message: (err as Error).message },
      { status: 500, headers: CORS_HEADERS },
    );
  }
}
