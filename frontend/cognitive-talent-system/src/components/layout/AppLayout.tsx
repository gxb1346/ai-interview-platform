import { useAuth } from '../../hooks/useAuth';
import { Sidebar } from './Sidebar';
import { Header } from './Header';

export function AppLayout({ children, currentView, onNavigate }) {
  const { user, logout } = useAuth();

  const viewTitles = {
    DASHBOARD: '仪表盘',
    RESUME_ANALYSIS: '简历分析',
    RESUME_MANAGE: '简历管理',
    TALENT_POOL: '人才库',
    INTERVIEW_CENTER: '面试中心',
    MOCK_INTERVIEW: '模拟面试',
    INTERVIEW_RECORDS: '面试记录',
    VOICE_INTERVIEW: '语音面试',
    SCHEDULE: '面试日程',
    LLM_PROVIDER: 'AI 配置',
    KNOWLEDGE_BASE: '知识库',
    KNOWLEDGE_QA: '知识问答',
    SETTINGS: '设置',
  };

  return (
    <div className='flex h-screen overflow-hidden bg-slate-50 font-sans'>
      <Sidebar currentView={currentView} onNavigate={onNavigate} onLogout={logout} />
      <main className='flex-1 flex flex-col overflow-hidden'>
        <Header
          title={viewTitles[currentView] || currentView}
          userName={user?.displayName || user?.username}
        />
        <div className='flex-1 overflow-auto p-6 max-w-7xl mx-auto w-full'>
          {children}
        </div>
      </main>
    </div>
  );
}
