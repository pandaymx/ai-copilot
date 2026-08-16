import { GlobalRegistrator } from "@happy-dom/global-registrator";

if (typeof document === "undefined") {
  GlobalRegistrator.register();
}
(
  globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
).IS_REACT_ACT_ENVIRONMENT = true;

import { afterEach, beforeEach, describe, expect, it, mock } from "bun:test";
import { act } from "react";
import { createRoot } from "react-dom/client";
import WorkflowsPage from "@/app/workflows/page";
import {
  executeWorkflowStream,
  fetchWorkflows,
  type WorkflowDefinition,
  type WorkflowEvent,
} from "@/lib/workflow-api";

const originalFetch = globalThis.fetch;

describe("Workflow API Client Tests", () => {
  beforeEach(() => {
    // @ts-expect-error
    global.fetch = mock();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("should fetch workflows list", async () => {
    const mockList = [
      { id: "tpl-1", name: "研究报告生成", nodes: [], edges: [] },
    ];
    // @ts-expect-error
    global.fetch = mock(() =>
      Promise.resolve(new Response(JSON.stringify(mockList))),
    );

    const workflows = await fetchWorkflows();
    expect(workflows).toHaveLength(1);
    expect(workflows[0].name).toBe("研究报告生成");
  });

  it("should parse SSE events in executeWorkflowStream", async () => {
    const sseData = [
      'data: {"type":"workflow_started","executionId":"exec-1","workflowId":"tpl-1"}',
      'data: {"type":"node_started","nodeId":"n1","nodeName":"输入","nodeType":"INPUT"}',
      'data: {"type":"node_finished","nodeId":"n1","output":"ok","durationMs":10}',
      'data: {"type":"workflow_completed","executionId":"exec-1","finalOutputs":{"output":"最终报告"}}',
    ].join("\n\n");

    const stream = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode(sseData));
        controller.close();
      },
    });

    // @ts-expect-error
    global.fetch = mock(() =>
      Promise.resolve(
        new Response(stream, {
          headers: { "Content-Type": "text/event-stream" },
        }),
      ),
    );

    const receivedEvents: WorkflowEvent[] = [];
    await executeWorkflowStream("tpl-1", { query: "test" }, (ev) => {
      receivedEvents.push(ev);
    });

    expect(receivedEvents).toHaveLength(4);
    expect(receivedEvents[0].type).toBe("workflow_started");
    expect(receivedEvents[1].type).toBe("node_started");
    expect(receivedEvents[2].type).toBe("node_finished");
    expect(receivedEvents[3].type).toBe("workflow_completed");
  });
});

describe("WorkflowsPage Component Tests", () => {
  afterEach(() => {
    globalThis.fetch = originalFetch;
  });

  it("should render workflow studio layout and nodes", async () => {
    const mockWorkflows: WorkflowDefinition[] = [
      {
        id: "tpl-deep-research",
        name: "深度研究报告生成",
        description: "端到端自动化深度研报撰写",
        nodes: [
          {
            id: "node_input",
            name: "输入研究主题",
            type: "INPUT",
            config: {},
            position: { x: 100, y: 100 },
          },
          {
            id: "node_search",
            name: "检索行业动态",
            type: "TOOL",
            config: { toolName: "web_search" },
            position: { x: 300, y: 100 },
          },
          {
            id: "node_writer",
            name: "撰写深度研报正文",
            type: "LLM",
            config: { promptTemplate: "撰写研报" },
            position: { x: 500, y: 100 },
          },
        ],
        edges: [
          {
            id: "e1",
            sourceNodeId: "node_input",
            targetNodeId: "node_search",
          },
          {
            id: "e2",
            sourceNodeId: "node_search",
            targetNodeId: "node_writer",
          },
        ],
        defaultInputs: { topic: "AI 趋势" },
      },
    ];

    // @ts-expect-error
    global.fetch = mock((url: string) => {
      if (url.includes("/api/workflows/executions")) {
        return Promise.resolve(new Response(JSON.stringify([])));
      }
      return Promise.resolve(new Response(JSON.stringify(mockWorkflows)));
    });

    const container = document.createElement("div");
    const root = createRoot(container);

    await act(async () => {
      root.render(<WorkflowsPage />);
    });

    // 验证标题与组件元素
    expect(container.textContent).toContain("AI Workflow Studio");
    expect(container.textContent).toContain("DAG 编排引擎");
    expect(container.textContent).toContain("输入研究主题");
    expect(container.textContent).toContain("检索行业动态");
    expect(container.textContent).toContain("撰写深度研报正文");
  });
});
