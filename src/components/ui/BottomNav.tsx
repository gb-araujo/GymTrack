import { Home, Dumbbell, History, TrendingUp, Target } from 'lucide-react';
import { useAppStore } from '../../store/appStore';

const tabs = [
  { id: 'home', label: 'Início', Icon: Home },
  { id: 'workout-plans', label: 'Fichas', Icon: Dumbbell },
  { id: 'history', label: 'Histórico', Icon: History },
  { id: 'progress', label: 'Progresso', Icon: TrendingUp },
  { id: 'goals', label: 'Metas', Icon: Target },
];

export function BottomNav() {
  const { currentScreen, setScreen, activeSession } = useAppStore();

  if (activeSession && !activeSession.session.completed) return null;

  return (
    <nav className="fixed bottom-0 left-0 right-0 bg-slate-950/95 backdrop-blur-md border-t border-slate-800 z-40 safe-area-bottom">
      <div className="flex items-center justify-around px-2 pt-2 pb-3 max-w-lg mx-auto">
        {tabs.map(({ id, label, Icon }) => {
          const active = currentScreen === id;
          return (
            <button
              key={id}
              onClick={() => setScreen(id)}
              className={`flex flex-col items-center gap-1 px-3 py-1 rounded-xl transition-all ${
                active
                  ? 'text-indigo-400'
                  : 'text-slate-500 hover:text-slate-300'
              }`}
            >
              <Icon size={22} strokeWidth={active ? 2.5 : 1.8} />
              <span className={`text-[10px] font-medium ${active ? 'text-indigo-400' : ''}`}>
                {label}
              </span>
            </button>
          );
        })}
      </div>
    </nav>
  );
}
