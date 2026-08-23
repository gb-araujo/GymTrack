import { useEffect } from 'react';
import { useAppStore } from './store/appStore';
import { BottomNav } from './components/ui/BottomNav';
import { Home } from './pages/Home';
import { WorkoutExecution } from './pages/WorkoutExecution';
import { WorkoutPlans } from './pages/WorkoutPlans';
import { History } from './pages/History';
import { HistoryDetail } from './pages/HistoryDetail';
import { Progress } from './pages/Progress';
import { Goals } from './pages/Goals';

export default function App() {
  const { initialize, currentScreen, activeSession } = useAppStore();

  useEffect(() => {
    initialize();
  }, []);

  // Always show workout execution when there's an active non-completed session
  const showExecution =
    activeSession && !activeSession.session.completed;

  const screen = showExecution ? 'workout-execution' : currentScreen;

  return (
    <div className="min-h-screen bg-[#0a0a14]">
      {screen === 'home' && <Home />}
      {screen === 'workout-execution' && <WorkoutExecution />}
      {screen === 'workout-plans' && <WorkoutPlans />}
      {screen === 'history' && <History />}
      {screen === 'history-detail' && <HistoryDetail />}
      {screen === 'progress' && <Progress />}
      {screen === 'goals' && <Goals />}
      <BottomNav />
    </div>
  );
}
