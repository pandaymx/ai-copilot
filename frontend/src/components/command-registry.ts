import type { AppRouterInstance } from "next/dist/shared/lib/app-router-context.shared-runtime";

export interface CommandContext {
  router: AppRouterInstance;
  setTheme?: (theme: string) => void;
  currentTheme?: string;
  close: () => void;
}

export interface CommandItem {
  id: string;
  title: string;
  description?: string;
  group: "action" | "navigation" | "settings" | "theme";
  keywords: string[];
  shortcut?: string;
  iconName: string;
  run: (ctx: CommandContext) => void;
}

export const STATIC_COMMANDS: CommandItem[] = [
  // 快捷操作
  {
    id: "new-chat",
    title: "新建会话 (New Chat)",
    description: "重置当前对话上下文并开启新会话",
    group: "action",
    keywords: ["new", "chat", "session", "新建", "会话", "对话"],
    shortcut: "Cmd+N",
    iconName: "Plus",
    run: ({ router, close }) => {
      close();
      router.push("/");
    },
  },
  {
    id: "prompt-templates",
    title: "Prompt 提示词模板库",
    description: "浏览与一键选用各领域专业 AI 提示词模板",
    group: "action",
    keywords: ["prompt", "template", "提示词", "模板", "预设"],
    iconName: "BookTemplate",
    run: ({ router, close }) => {
      close();
      router.push("/prompt-templates");
    },
  },
  {
    id: "conversation-insights",
    title: "历史对话洞察与分析",
    description: "查看话题聚类、五维质量评分与用户满意度走势",
    group: "action",
    keywords: [
      "insight",
      "analytics",
      "dashboard",
      "洞察",
      "分析",
      "评分",
      "大屏",
    ],
    iconName: "BarChart3",
    run: ({ router, close }) => {
      close();
      router.push("/insights");
    },
  },

  // 导航跳转
  {
    id: "nav-knowledge",
    title: "RAG 知识库管理",
    description: "管理知识文档、混合向量索引与检索切片",
    group: "navigation",
    keywords: ["rag", "knowledge", "doc", "知识库", "文档", "向量"],
    iconName: "Brain",
    run: ({ router, close }) => {
      close();
      router.push("/knowledge");
    },
  },
  {
    id: "nav-workflows",
    title: "工作流编排工作台",
    description: "可视化构建多步骤 Agent 智能体工作流",
    group: "navigation",
    keywords: ["workflow", "agent", "工作流", "编排", "流程"],
    iconName: "Workflow",
    run: ({ router, close }) => {
      close();
      router.push("/workflows");
    },
  },
  {
    id: "nav-tools",
    title: "扩展工具与插件中心",
    description: "管理外发邮件、数据库、日历与自定义工具",
    group: "navigation",
    keywords: ["tool", "plugin", "工具", "插件", "邮件", "数据库"],
    iconName: "Wrench",
    run: ({ router, close }) => {
      close();
      router.push("/tools");
    },
  },
  {
    id: "nav-eval",
    title: "大模型对齐与评测台",
    description: "多模型盲测比对与 5 维自动打分评测",
    group: "navigation",
    keywords: ["eval", "evaluation", "judge", "评测", "打分", "对比"],
    iconName: "Award",
    run: ({ router, close }) => {
      close();
      router.push("/evaluation");
    },
  },
  {
    id: "nav-memory",
    title: "长期记忆与画像",
    description: "管理跨会话个性化记忆切片与事实画像",
    group: "navigation",
    keywords: ["memory", "profile", "记忆", "画像", "长期"],
    iconName: "Database",
    run: ({ router, close }) => {
      close();
      router.push("/memory");
    },
  },

  // 设置管理
  {
    id: "setting-api-keys",
    title: "API 密钥与模型凭据管理",
    description: "配置各厂商 API Key (BYOK) 与健康连通性检测",
    group: "settings",
    keywords: ["api", "key", "provider", "密钥", "凭据", "厂商"],
    iconName: "KeyRound",
    run: ({ router, close }) => {
      close();
      router.push("/settings/api-keys");
    },
  },
  {
    id: "setting-mcp",
    title: "MCP Server 接入配置",
    description: "将 Copilot 能力一键接入 Cursor、Claude Desktop 等客户端",
    group: "settings",
    keywords: ["mcp", "server", "cursor", "claude", "接入"],
    iconName: "Server",
    run: ({ router, close }) => {
      close();
      router.push("/settings/mcp-server");
    },
  },
  {
    id: "setting-webhooks",
    title: "Webhook 订阅与事件通知",
    description: "配置外部系统事件广播与 HMAC-SHA256 签名推送",
    group: "settings",
    keywords: ["webhook", "event", "push", "推送", "事件", "通知"],
    iconName: "Webhook",
    run: ({ router, close }) => {
      close();
      router.push("/settings/webhooks");
    },
  },
  {
    id: "setting-usage",
    title: "Token 用量与配额监控",
    description: "查看实时 Token 消耗、预算限额与统计图表",
    group: "settings",
    keywords: ["usage", "token", "quota", "用量", "额度", "预算"],
    iconName: "Activity",
    run: ({ router, close }) => {
      close();
      router.push("/usage");
    },
  },
  {
    id: "setting-admin",
    title: "用户权限与 RBAC 控制台",
    description: "管理员用户列表、角色分配与账号状态管理",
    group: "settings",
    keywords: ["admin", "rbac", "user", "role", "权限", "用户", "管理"],
    iconName: "Users",
    run: ({ router, close }) => {
      close();
      router.push("/admin/users");
    },
  },

  // 外观主题
  {
    id: "theme-toggle",
    title: "切换深色 / 浅色模式",
    description: "在暗黑模式与明亮模式之间快速切换",
    group: "theme",
    keywords: [
      "theme",
      "dark",
      "light",
      "mode",
      "主题",
      "暗黑",
      "明亮",
      "白天",
      "黑夜",
    ],
    iconName: "Moon",
    run: ({ setTheme, currentTheme, close }) => {
      if (setTheme) {
        setTheme(currentTheme === "dark" ? "light" : "dark");
      }
      close();
    },
  },
  {
    id: "theme-system",
    title: "跟随系统外观设置",
    description: "自动适配操作系统的明暗外观主题",
    group: "theme",
    keywords: ["theme", "system", "auto", "系统", "跟随", "自动"],
    iconName: "SunMedium",
    run: ({ setTheme, close }) => {
      if (setTheme) {
        setTheme("system");
      }
      close();
    },
  },
];
