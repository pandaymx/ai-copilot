"use client";

import { Code2, Plus, Sliders, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";

export interface SchemaProperty {
  id: string;
  name: string;
  type: "string" | "number" | "integer" | "boolean" | "array" | "object";
  description: string;
  required: boolean;
}

interface SchemaBuilderProps {
  value: string;
  onChange: (schemaJson: string) => void;
}

interface JsonPropDef {
  type?: "string" | "number" | "integer" | "boolean" | "array" | "object";
  description?: string;
}

export function SchemaBuilder({ value, onChange }: SchemaBuilderProps) {
  const [mode, setMode] = useState<"visual" | "json">("visual");
  const [properties, setProperties] = useState<SchemaProperty[]>([]);
  const [jsonText, setJsonText] = useState(
    value || '{\n  "type": "object",\n  "properties": {},\n  "required": []\n}',
  );
  const [jsonError, setJsonError] = useState<string | null>(null);

  // 解析初始 value 为可视化字段列表
  useEffect(() => {
    try {
      const parsed = JSON.parse(value || "{}");
      if (parsed.type === "object" && parsed.properties) {
        const requiredList: string[] = Array.isArray(parsed.required)
          ? parsed.required
          : [];
        const list: SchemaProperty[] = Object.entries(
          parsed.properties as Record<string, JsonPropDef>,
        ).map(([name, prop], idx) => ({
          id: `prop-${idx}-${name}`,
          name,
          type: prop.type || "string",
          description: prop.description || "",
          required: requiredList.includes(name),
        }));
        setProperties(list);
        setJsonText(JSON.stringify(parsed, null, 2));
        setJsonError(null);
      }
    } catch {
      setJsonText(value);
    }
  }, [value]);

  const updateFromProperties = (newProps: SchemaProperty[]) => {
    setProperties(newProps);
    const propertiesObj: Record<
      string,
      { type: string; description?: string }
    > = {};
    const requiredArr: string[] = [];

    for (const prop of newProps) {
      if (prop.name.trim()) {
        propertiesObj[prop.name.trim()] = {
          type: prop.type,
          description: prop.description.trim() || undefined,
        };
        if (prop.required) {
          requiredArr.push(prop.name.trim());
        }
      }
    }

    const schemaObj = {
      type: "object",
      properties: propertiesObj,
      required: requiredArr.length > 0 ? requiredArr : undefined,
    };

    const formatted = JSON.stringify(schemaObj, null, 2);
    setJsonText(formatted);
    onChange(formatted);
  };

  const handleAddProperty = () => {
    const nextProps: SchemaProperty[] = [
      ...properties,
      {
        id: `prop-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
        name: "",
        type: "string",
        description: "",
        required: false,
      },
    ];
    updateFromProperties(nextProps);
  };

  const handleRemoveProperty = (id: string) => {
    const nextProps = properties.filter((p) => p.id !== id);
    updateFromProperties(nextProps);
  };

  const handlePropChange = <K extends keyof SchemaProperty>(
    id: string,
    field: K,
    val: SchemaProperty[K],
  ) => {
    const nextProps = properties.map((p) => {
      if (p.id === id) {
        return { ...p, [field]: val };
      }
      return p;
    });
    updateFromProperties(nextProps);
  };

  const handleJsonChange = (text: string) => {
    setJsonText(text);
    try {
      const parsed = JSON.parse(text);
      setJsonError(null);
      onChange(text);
      if (parsed.type === "object" && parsed.properties) {
        const requiredList: string[] = Array.isArray(parsed.required)
          ? parsed.required
          : [];
        const list: SchemaProperty[] = Object.entries(
          parsed.properties as Record<string, JsonPropDef>,
        ).map(([name, prop], idx) => ({
          id: `prop-${idx}-${name}`,
          name,
          type: prop.type || "string",
          description: prop.description || "",
          required: requiredList.includes(name),
        }));
        setProperties(list);
      }
    } catch (e: unknown) {
      setJsonError(e instanceof Error ? e.message : "JSON 格式不合法");
    }
  };

  return (
    <div className="space-y-3 rounded-xl border border-zinc-200/80 bg-zinc-50/50 p-3.5 dark:border-zinc-800 dark:bg-zinc-900/40">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Sliders className="size-4 text-indigo-500" />
          <span className="text-xs font-semibold text-zinc-800 dark:text-zinc-200">
            参数定义 (JSON Schema)
          </span>
        </div>
        <div className="flex items-center gap-1 rounded-lg bg-zinc-200/70 p-0.5 dark:bg-zinc-800">
          <button
            type="button"
            onClick={() => setMode("visual")}
            className={`rounded-md px-2 py-1 text-[11px] font-medium transition-all ${
              mode === "visual"
                ? "bg-white text-zinc-900 shadow-2xs dark:bg-zinc-700 dark:text-zinc-100"
                : "text-zinc-500 hover:text-zinc-800 dark:text-zinc-400 dark:hover:text-zinc-200"
            }`}
          >
            可视化设计
          </button>
          <button
            type="button"
            onClick={() => setMode("json")}
            className={`flex items-center gap-1 rounded-md px-2 py-1 text-[11px] font-medium transition-all ${
              mode === "json"
                ? "bg-white text-zinc-900 shadow-2xs dark:bg-zinc-700 dark:text-zinc-100"
                : "text-zinc-500 hover:text-zinc-800 dark:text-zinc-400 dark:hover:text-zinc-200"
            }`}
          >
            <Code2 className="size-3" />
            <span>JSON 源码</span>
          </button>
        </div>
      </div>

      {mode === "visual" ? (
        <div className="space-y-2.5">
          {properties.length === 0 ? (
            <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-zinc-200 py-6 text-center dark:border-zinc-800">
              <p className="text-xs text-zinc-500 dark:text-zinc-400">
                暂未定义任何入参（此工具调用无需参数）
              </p>
              <button
                type="button"
                onClick={handleAddProperty}
                className="mt-2.5 flex items-center gap-1 rounded-lg bg-indigo-500/10 px-3 py-1.5 text-xs font-semibold text-indigo-600 transition-colors hover:bg-indigo-500/20 dark:bg-indigo-500/20 dark:text-indigo-400"
              >
                <Plus className="size-3.5" />
                <span>添加第一个参数</span>
              </button>
            </div>
          ) : (
            <div className="space-y-2">
              {properties.map((prop) => (
                <div
                  key={prop.id}
                  className="flex flex-wrap items-center gap-2 rounded-lg border border-zinc-200/70 bg-white p-2.5 shadow-2xs dark:border-zinc-800 dark:bg-zinc-900"
                >
                  <input
                    type="text"
                    value={prop.name}
                    onChange={(e) =>
                      handlePropChange(prop.id, "name", e.target.value)
                    }
                    placeholder="参数名 (如 city)"
                    className="flex-1 min-w-[110px] rounded-md border border-zinc-200 bg-zinc-50 px-2.5 py-1.5 text-xs font-mono text-zinc-900 focus:border-indigo-500 focus:bg-white focus:outline-none dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
                  />
                  <select
                    value={prop.type}
                    onChange={(e) =>
                      handlePropChange(
                        prop.id,
                        "type",
                        e.target.value as SchemaProperty["type"],
                      )
                    }
                    className="w-24 rounded-md border border-zinc-200 bg-zinc-50 px-2 py-1.5 text-xs text-zinc-900 focus:border-indigo-500 focus:bg-white focus:outline-none dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
                  >
                    <option value="string">string</option>
                    <option value="number">number</option>
                    <option value="integer">integer</option>
                    <option value="boolean">boolean</option>
                    <option value="array">array</option>
                    <option value="object">object</option>
                  </select>
                  <input
                    type="text"
                    value={prop.description}
                    onChange={(e) =>
                      handlePropChange(prop.id, "description", e.target.value)
                    }
                    placeholder="参数说明（供 LLM 理解）"
                    className="flex-2 min-w-[140px] rounded-md border border-zinc-200 bg-zinc-50 px-2.5 py-1.5 text-xs text-zinc-900 focus:border-indigo-500 focus:bg-white focus:outline-none dark:border-zinc-700 dark:bg-zinc-800 dark:text-zinc-100"
                  />
                  <label className="flex items-center gap-1.5 text-xs text-zinc-600 dark:text-zinc-400 cursor-pointer select-none">
                    <input
                      type="checkbox"
                      checked={prop.required}
                      onChange={(e) =>
                        handlePropChange(prop.id, "required", e.target.checked)
                      }
                      className="rounded border-zinc-300 text-indigo-600 focus:ring-indigo-500"
                    />
                    <span>必填</span>
                  </label>
                  <button
                    type="button"
                    onClick={() => handleRemoveProperty(prop.id)}
                    className="p-1 text-zinc-400 transition-colors hover:text-rose-500"
                    title="删除参数"
                  >
                    <Trash2 className="size-4" />
                  </button>
                </div>
              ))}

              <div className="flex justify-end pt-1">
                <button
                  type="button"
                  onClick={handleAddProperty}
                  className="flex items-center gap-1 rounded-lg border border-dashed border-zinc-300 px-2.5 py-1.5 text-xs font-medium text-zinc-600 transition-colors hover:border-indigo-500 hover:text-indigo-600 dark:border-zinc-700 dark:text-zinc-400 dark:hover:border-indigo-400 dark:hover:text-indigo-300"
                >
                  <Plus className="size-3.5" />
                  <span>添加参数</span>
                </button>
              </div>
            </div>
          )}
        </div>
      ) : (
        <div className="space-y-1.5">
          <textarea
            value={jsonText}
            onChange={(e) => handleJsonChange(e.target.value)}
            rows={7}
            className="w-full rounded-lg border border-zinc-300 bg-zinc-950 p-3 font-mono text-xs text-emerald-400 focus:border-indigo-500 focus:outline-none dark:border-zinc-700"
          />
          {jsonError && (
            <p className="text-[11px] font-medium text-rose-500">
              ⚠️ {jsonError}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
