"use client";

import { useCallback, useEffect, useState } from "react";
import { useCollaboration } from "@/hooks/useCollaboration";
import {
  fetchMeApi,
  inviteParticipantApi,
  listParticipantsApi,
  removeParticipantApi,
} from "@/lib/api";
import type { Participant, ParticipantRole } from "@/lib/collab-types";

const ROLE_COLOR: Record<ParticipantRole, string> = {
  OWNER: "#F59E0B",
  EDITOR: "#22C55E",
  VIEWER: "#3B82F6",
};

const ROLE_LABEL: Record<ParticipantRole, string> = {
  OWNER: "所有者",
  EDITOR: "可编辑",
  VIEWER: "只读",
};

function avatarColor(userId: string): string {
  let h = 0;
  for (let i = 0; i < userId.length; i++)
    h = (h * 31 + userId.charCodeAt(i)) % 360;
  return `hsl(${h} 65% 55%)`;
}

function initials(userId: string): string {
  const clean = userId.replace(/[^a-zA-Z0-9]/g, "");
  return clean.slice(0, 2).toUpperCase() || "?";
}

export function CollaborationIndicator({ sessionId }: { sessionId: string }) {
  const collab = useCollaboration();
  const [me, setMe] = useState<string | null>(null);
  const [participants, setParticipants] = useState<Participant[]>([]);
  const [inviteOpen, setInviteOpen] = useState(false);
  const [targetId, setTargetId] = useState("");
  const [inviteRole, setInviteRole] = useState<ParticipantRole>("EDITOR");

  const refresh = useCallback(async () => {
    const list = await listParticipantsApi(sessionId);
    if (list) setParticipants(list);
  }, [sessionId]);

  useEffect(() => {
    if (!sessionId) return;
    void fetchMeApi().then(setMe);
    void refresh();
  }, [sessionId, refresh]);

  const onlineSet = new Set(collab.online);
  const display = participants.length > 0 ? participants : [];

  return (
    <div className="flex items-center gap-3">
      {display.length > 0 && (
        <div className="flex items-center -space-x-2">
          {display.slice(0, 5).map((p) => {
            const isOnline = onlineSet.has(p.userId);
            const isMe = me === p.userId;
            return (
              <div
                key={p.userId}
                title={`${p.userId}${isMe ? "（我）" : ""} · ${ROLE_LABEL[p.role]}${isOnline ? " · 在线" : ""}`}
                className="relative"
              >
                <div
                  className="flex h-8 w-8 items-center justify-center rounded-full border-2 border-white/70 text-[11px] font-semibold text-white shadow-md backdrop-blur"
                  style={{
                    background: avatarColor(p.userId),
                    boxShadow: isOnline
                      ? `0 0 0 2px ${ROLE_COLOR[p.role]}55`
                      : undefined,
                    opacity: isOnline ? 1 : 0.55,
                  }}
                >
                  {initials(p.userId)}
                </div>
                <span
                  className="absolute -bottom-0.5 -right-0.5 h-2.5 w-2.5 rounded-full border border-white"
                  style={{ background: ROLE_COLOR[p.role] }}
                />
              </div>
            );
          })}
          {display.length > 5 && (
            <div className="flex h-8 w-8 items-center justify-center rounded-full border-2 border-white/70 bg-slate-500/80 text-[10px] font-semibold text-white">
              +{display.length - 5}
            </div>
          )}
        </div>
      )}

      {collab.typingUsers.length > 0 && (
        <div className="flex items-center gap-1.5 text-xs text-slate-400">
          <span className="flex gap-0.5">
            {[0, 1, 2].map((i) => (
              <span
                key={i}
                className="h-1.5 w-1.5 animate-pulse rounded-full bg-cyan-400"
                style={{ animationDelay: `${i * 0.2}s` }}
              />
            ))}
          </span>
          {collab.typingUsers.length} 人正在输入…
        </div>
      )}

      {collab.role === "OWNER" && (
        <button
          type="button"
          onClick={() => setInviteOpen(true)}
          className="rounded-full bg-gradient-to-r from-blue-600 to-cyan-500 px-3 py-1 text-xs font-medium text-white shadow-lg transition hover:opacity-90"
        >
          + 邀请协作
        </button>
      )}

      {inviteOpen && (
        <button
          type="button"
          aria-label="关闭邀请弹窗"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
          onClick={(e) => {
            if (e.target === e.currentTarget) setInviteOpen(false);
          }}
        >
          <div className="w-[360px] rounded-2xl border border-white/10 bg-slate-900/80 p-5 text-left shadow-2xl backdrop-blur-xl">
            <h3 className="mb-3 text-base font-semibold text-slate-100">
              邀请协作者
            </h3>
            <input
              value={targetId}
              onChange={(e) => setTargetId(e.target.value)}
              placeholder="输入协作者 userId"
              className="mb-3 w-full rounded-lg border border-white/10 bg-slate-800/60 px-3 py-2 text-sm text-slate-100 outline-none focus:border-cyan-400"
            />
            <div className="mb-4 flex gap-2">
              {(["EDITOR", "VIEWER"] as ParticipantRole[]).map((r) => (
                <button
                  type="button"
                  key={r}
                  onClick={() => setInviteRole(r)}
                  className={`flex-1 rounded-lg border px-3 py-1.5 text-xs font-medium transition ${
                    inviteRole === r
                      ? "border-cyan-400 bg-cyan-400/10 text-cyan-200"
                      : "border-white/10 text-slate-400"
                  }`}
                >
                  {ROLE_LABEL[r]}
                </button>
              ))}
            </div>
            <div className="mb-4 space-y-1.5">
              {participants
                .filter((p) => p.role !== "OWNER")
                .map((p) => (
                  <div
                    key={p.userId}
                    className="flex items-center justify-between rounded-lg bg-slate-800/50 px-3 py-1.5 text-sm text-slate-300"
                  >
                    <span>{p.userId}</span>
                    <button
                      type="button"
                      onClick={() =>
                        removeParticipantApi(sessionId, p.userId).then(refresh)
                      }
                      className="text-xs text-red-400 hover:text-red-300"
                    >
                      移除
                    </button>
                  </div>
                ))}
            </div>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setInviteOpen(false)}
                className="rounded-lg px-3 py-1.5 text-sm text-slate-400 hover:text-slate-200"
              >
                取消
              </button>
              <button
                type="button"
                onClick={async () => {
                  if (!targetId.trim()) return;
                  await inviteParticipantApi(
                    sessionId,
                    targetId.trim(),
                    inviteRole,
                  );
                  setTargetId("");
                  await refresh();
                }}
                className="rounded-lg bg-gradient-to-r from-blue-600 to-cyan-500 px-4 py-1.5 text-sm font-medium text-white hover:opacity-90"
              >
                确认邀请
              </button>
            </div>
          </div>
        </button>
      )}
    </div>
  );
}
