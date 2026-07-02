import { useState, useEffect, useCallback } from "react";
import { Plus, Trash2, RefreshCw, Loader2, CheckCircle, AlertCircle, Key, Zap, Cpu } from "lucide-react";
import { llmProviderApi } from "../api/llmProvider";
import type { LlmProvider, LlmProviderTestResult, LlmDefaultProvider } from "../types";

export function LlmProviderView() {
  const [providers, setProviders] = useState<LlmProvider[]>([]);
  const [defaultProvider, setDefaultProvider] = useState<LlmDefaultProvider | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [testResults, setTestResults] = useState<Record<string, LlmProviderTestResult>>({});
  const [showAddForm, setShowAddForm] = useState(false);
  const [newProvider, setNewProvider] = useState({ id: "", baseUrl: "", apiKey: "", model: "" });

  const loadProviders = useCallback(async () => {
    try {
      const [providersRes, defaultRes] = await Promise.all([
        llmProviderApi.list(),
        llmProviderApi.getDefaultProvider(),
      ]);
      setProviders(providersRes?.data ?? providersRes ?? []);
      setDefaultProvider(defaultRes?.data ?? defaultRes ?? null);
    } catch {
      setError("加载提供商列表失败");
    }
  }, []);

  useEffect(() => { loadProviders(); }, [loadProviders]);

  const handleTest = async (id: string) => {
    setLoading(true);
    try {
      const res = await llmProviderApi.test(id);
      const data = res?.data ?? res;
      setTestResults((prev) => ({ ...prev, [id]: data }));
      setSuccessMsg(`${id} 测试完成`);
    } catch {
      setError(`${id} 测试失败`);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm(`确定删除 ${id}？`)) return;
    try {
      await llmProviderApi.delete(id);
      setSuccessMsg(`${id} 已删除`);
      await loadProviders();
    } catch {
      setError(`${id} 删除失败`);
    }
  };

  const handleReload = async () => {
    try {
      await llmProviderApi.reload();
      setSuccessMsg("配置已重新加载");
      await loadProviders();
    } catch {
      setError("重新加载失败");
    }
  };

  const handleAdd = async () => {
    if (!newProvider.id || !newProvider.baseUrl || !newProvider.apiKey) {
      setError("请填写完整信息");
      return;
    }
    setLoading(true);
    try {
      await llmProviderApi.create(newProvider);
      setSuccessMsg(`${newProvider.id} 已添加`);
      setShowAddForm(false);
      setNewProvider({ id: "", baseUrl: "", apiKey: "", model: "" });
      await loadProviders();
    } catch {
      setError("添加失败");
    } finally {
      setLoading(false);
    }
  };

  const handleSetDefault = async (id: string) => {
    try {
      await llmProviderApi.updateDefaultProvider({ defaultChatProviderId: id });
      setSuccessMsg(`${id} 已设为默认`);
      await loadProviders();
    } catch {
      setError("设置默认失败");
    }
  };

  return (
    <div className="space-y-6">
      {/* 消息提示 */}
      {error && (
        <div className="flex items-center gap-2 text-sm text-red-600 bg-red-50 border border-red-200 rounded-xl px-4 py-3">
          <AlertCircle className="w-4 h-4 shrink-0" />{error}
        </div>
      )}
      {successMsg && (
        <div className="flex items-center gap-2 text-sm text-emerald-600 bg-emerald-50 border border-emerald-200 rounded-xl px-4 py-3">
          <CheckCircle className="w-4 h-4 shrink-0" />{successMsg}
        </div>
      )}

      {/* 操作栏 */}
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-sm font-extrabold text-slate-800">AI 提供商管理</h3>
          <p className="text-xs text-slate-400 mt-1">
            默认提供商: {defaultProvider?.defaultChatProviderId ?? "—"}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handleReload}
            className="flex items-center gap-1.5 text-xs font-bold text-slate-600 bg-slate-100 px-3 py-2 rounded-xl hover:bg-slate-200 cursor-pointer"
          >
            <RefreshCw className="w-3.5 h-3.5" />重新加载
          </button>
          <button
            onClick={() => setShowAddForm(!showAddForm)}
            className="flex items-center gap-1.5 text-xs font-bold text-white bg-primary px-3 py-2 rounded-xl hover:bg-primary-dark cursor-pointer"
          >
            <Plus className="w-3.5 h-3.5" />添加提供商
          </button>
        </div>
      </div>

      {/* 添加表单 */}
      {showAddForm && (
        <div className="bg-white rounded-2xl border border-slate-200 p-6">
          <h4 className="text-sm font-bold text-slate-800 mb-4">新增提供商</h4>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-bold text-slate-600 mb-1.5">提供商 ID</label>
              <input
                value={newProvider.id}
                onChange={(e) => setNewProvider((p) => ({ ...p, id: e.target.value }))}
                placeholder="如: openai, azure"
                className="w-full text-sm border border-slate-200 rounded-xl px-4 py-2.5 outline-none focus:border-primary"
              />
            </div>
            <div>
              <label className="block text-xs font-bold text-slate-600 mb-1.5">API 地址</label>
              <input
                value={newProvider.baseUrl}
                onChange={(e) => setNewProvider((p) => ({ ...p, baseUrl: e.target.value }))}
                placeholder="https://api.openai.com/v1"
                className="w-full text-sm border border-slate-200 rounded-xl px-4 py-2.5 outline-none focus:border-primary"
              />
            </div>
            <div>
              <label className="block text-xs font-bold text-slate-600 mb-1.5">API Key</label>
              <input
                type="password"
                value={newProvider.apiKey}
                onChange={(e) => setNewProvider((p) => ({ ...p, apiKey: e.target.value }))}
                placeholder="sk-..."
                className="w-full text-sm border border-slate-200 rounded-xl px-4 py-2.5 outline-none focus:border-primary"
              />
            </div>
            <div>
              <label className="block text-xs font-bold text-slate-600 mb-1.5">模型</label>
              <input
                value={newProvider.model}
                onChange={(e) => setNewProvider((p) => ({ ...p, model: e.target.value }))}
                placeholder="qwen3.5-flash"
                className="w-full text-sm border border-slate-200 rounded-xl px-4 py-2.5 outline-none focus:border-primary"
              />
            </div>
          </div>
          <div className="flex justify-end gap-2 mt-4">
            <button
              onClick={() => setShowAddForm(false)}
              className="text-xs font-bold text-slate-500 px-4 py-2 rounded-xl hover:bg-slate-100 cursor-pointer"
            >
              取消
            </button>
            <button
              onClick={handleAdd}
              disabled={loading}
              className="flex items-center gap-1.5 text-xs font-bold text-white bg-primary px-4 py-2 rounded-xl hover:bg-primary-dark cursor-pointer disabled:opacity-50"
            >
              {loading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : null}保存
            </button>
          </div>
        </div>
      )}

      {/* 提供商列表 */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {providers.map((p) => {
          const isDefault = p.id === (defaultProvider?.defaultChatProviderId);
          const testResult = testResults[p.id];
          return (
            <div
              key={p.id}
              className={`bg-white rounded-2xl border p-5 transition ${
                isDefault ? "border-primary ring-2 ring-primary/20" : "border-slate-200"
              }`}
            >
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2">
                  <Cpu className="w-5 h-5 text-primary" />
                  <span className="text-sm font-extrabold text-slate-800">{p.id}</span>
                  {isDefault && (
                    <span className="text-[10px] bg-primary/10 text-primary px-2 py-0.5 rounded-full font-bold">
                      默认
                    </span>
                  )}
                </div>
                <div className="flex items-center gap-1">
                  <button
                    onClick={() => handleTest(p.id)}
                    disabled={loading}
                    className="p-2 rounded-lg hover:bg-slate-100 cursor-pointer disabled:opacity-50"
                    title="测试连接"
                  >
                    <Zap className="w-4 h-4 text-amber-500" />
                  </button>
                  {!isDefault && (
                    <button
                      onClick={() => handleSetDefault(p.id)}
                      className="p-2 rounded-lg hover:bg-slate-100 cursor-pointer"
                      title="设为默认"
                    >
                      <Key className="w-4 h-4 text-slate-400" />
                    </button>
                  )}
                  <button
                    onClick={() => handleDelete(p.id)}
                    className="p-2 rounded-lg hover:bg-red-50 cursor-pointer"
                    title="删除"
                  >
                    <Trash2 className="w-4 h-4 text-red-400" />
                  </button>
                </div>
              </div>
              <div className="space-y-1.5 text-xs text-slate-500">
                <p>模型: <span className="font-bold text-slate-700">{p.model ?? "—"}</span></p>
                <p>地址: <span className="font-mono">{p.baseUrl ?? "—"}</span></p>
                <p>嵌入: <span className="font-bold">{p.supportsEmbedding ? "是" : "否"}</span></p>
              </div>

              {/* 测试结果 */}
              {testResult && (
                <div className={`mt-3 p-3 rounded-xl text-xs ${
                  testResult.success ? "bg-emerald-50 text-emerald-700" : "bg-red-50 text-red-600"
                }`}>
                  <p className="font-bold">{testResult.success ? "连接成功" : "连接失败"}</p>
                  {testResult.message && <p className="mt-0.5">{testResult.message}</p>}
                  {testResult.latencyMs && <p className="mt-0.5">延迟: {testResult.latencyMs}ms</p>}
                </div>
              )}
            </div>
          );
        })}
      </div>

      {providers.length === 0 && !loading && (
        <p className="text-sm text-slate-400 text-center py-12">暂无提供商，请添加</p>
      )}
    </div>
  );
}
