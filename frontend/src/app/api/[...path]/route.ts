import { type NextRequest, NextResponse } from "next/server";

export const dynamic = "force-dynamic";

const BACKEND_BASE_URL =
  process.env.BACKEND_URL || "http://localhost:8084/api/:path*";

function getTargetUrl(pathSegments: string[]): string {
  const cleanBase = BACKEND_BASE_URL.replace(/\/api\/:path\*/, "").replace(
    /\/$/,
    "",
  );
  return `${cleanBase}/api/${pathSegments.join("/")}`;
}

export async function POST(
  req: NextRequest,
  { params }: { params: Promise<{ path: string[] }> },
) {
  const { path } = await params;
  const targetUrl = getTargetUrl(path);

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
        "Content-Type":
          backendRes.headers.get("content-type") || "application/json",
      },
    });
  } catch (err) {
    return NextResponse.json(
      { error: true, message: (err as Error).message },
      { status: 500 },
    );
  }
}

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ path: string[] }> },
) {
  const { path } = await params;
  const targetUrl = getTargetUrl(path);

  try {
    const backendRes = await fetch(targetUrl, {
      headers: {
        Accept: req.headers.get("accept") || "application/json",
      },
    });
    const data = await backendRes.json();
    return NextResponse.json(data, { status: backendRes.status });
  } catch (err) {
    return NextResponse.json(
      { error: true, message: (err as Error).message },
      { status: 500 },
    );
  }
}
