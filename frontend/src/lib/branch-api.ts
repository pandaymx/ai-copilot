export interface BranchSummary {
  branchId: string;
  sessionId: string;
  branchLabel: string;
  parentBranchId: string | null;
  forkFromMessageId: string | null;
  messageCount: number;
  createdAt: number;
  updatedAt: number;
}

export interface DiffMessageItem {
  id: string;
  branchId: string;
  role: string;
  content: string;
  createdAt: number;
  diffStatus: "UNCHANGED" | "ADDED" | "MODIFIED";
}

export interface BranchDiff {
  branchA: string;
  branchB: string;
  commonAncestorMessageId: string | null;
  branchAMessages: DiffMessageItem[];
  branchBMessages: DiffMessageItem[];
}

export interface MergeResult {
  success: boolean;
  targetBranchId: string;
  mergedMessageCount: number;
  message: string;
}

export interface TreeNode {
  id: string;
  branchId: string;
  branchLabel: string;
  parentId: string | null;
  role: string;
  content: string;
  createdAt: number;
  children: TreeNode[];
}

export interface ConversationTree {
  sessionId: string;
  activeBranchId: string;
  branches: BranchSummary[];
  rootNodes: TreeNode[];
}

export async function fetchBranches(
  sessionId: string,
): Promise<BranchSummary[]> {
  const res = await fetch(
    `/api/sessions/${encodeURIComponent(sessionId)}/branches`,
  );
  if (!res.ok) {
    throw new Error(`获取分支列表失败: ${res.statusText}`);
  }
  return res.json();
}

export async function createBranch(
  sessionId: string,
  forkFromMessageId?: string,
  label?: string,
): Promise<BranchSummary> {
  const res = await fetch(
    `/api/sessions/${encodeURIComponent(sessionId)}/branches`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ forkFromMessageId, label }),
    },
  );
  if (!res.ok) {
    const err = await res.text();
    throw new Error(err || "创建分支失败");
  }
  return res.json();
}

export async function fetchConversationTree(
  sessionId: string,
  activeBranchId?: string,
): Promise<ConversationTree> {
  const params = new URLSearchParams();
  if (activeBranchId) params.set("activeBranchId", activeBranchId);

  const res = await fetch(
    `/api/sessions/${encodeURIComponent(sessionId)}/branches/tree?${params.toString()}`,
  );
  if (!res.ok) {
    throw new Error(`获取对话树失败: ${res.statusText}`);
  }
  return res.json();
}

export async function diffBranches(
  sessionId: string,
  branchA: string,
  branchB: string,
): Promise<BranchDiff> {
  const params = new URLSearchParams({ branchA, branchB });
  const res = await fetch(
    `/api/sessions/${encodeURIComponent(sessionId)}/branches/diff?${params.toString()}`,
  );
  if (!res.ok) {
    throw new Error(`分支对比失败: ${res.statusText}`);
  }
  return res.json();
}

export async function mergeBranches(
  sessionId: string,
  sourceBranchId: string,
  targetBranchId: string,
): Promise<MergeResult> {
  const res = await fetch(
    `/api/sessions/${encodeURIComponent(sessionId)}/branches/merge`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ sourceBranchId, targetBranchId }),
    },
  );
  if (!res.ok) {
    throw new Error("合并分支失败");
  }
  return res.json();
}
