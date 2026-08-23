import { Flame, Play, ChevronRight, Trophy, Zap } from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { formatDistanceToNow } from 'date-fns';
import { ptBR } from 'date-fns/locale';

function formatDuration(minutes?: number) {
  if (!minutes) return '';
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h > 0) return `${h}h ${m}min`;
  return `${m} min`;
}

export function Home() {
  const { activePlan, sessions, totalWorkouts, setScreen, startSession, personalRecords } =
    useAppStore();

  const nextDay = activePlan
    ? activePlan.days[activePlan.currentDayIndex % activePlan.days.length]
    : null;

  const lastSession = sessions[0];
  const prs = Array.from(personalRecords.values()).slice(0, 3);

  const handleStart = async () => {
    const id = await startSession();
    if (id) setScreen('workout-execution');
  };

  // Recent sessions mini-history (last 7 indicators)
  const recent = sessions.slice(0, 7);

  return (
    <div className="pb-28 px-4 pt-6 max-w-lg mx-auto animate-fade-in">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <p className="text-slate-400 text-sm">Bem-vindo de volta</p>
          <h1 className="text-2xl font-bold text-white">Track</h1>
        </div>
        <div className="flex flex-col items-center bg-slate-900 rounded-2xl px-4 py-2 border border-slate-800">
          <Flame size={20} className="text-orange-400 mb-0.5" />
          <span className="text-2xl font-black text-white leading-none">{totalWorkouts}</span>
          <span className="text-[10px] text-slate-400 mt-0.5">treinos</span>
        </div>
      </div>

      {/* Recent activity dots */}
      {recent.length > 0 && (
        <div className="flex items-center gap-1.5 mb-6">
          {Array.from({ length: 7 }).map((_, i) => (
            <div
              key={i}
              className={`h-2 flex-1 rounded-full ${i < recent.length ? 'bg-indigo-500' : 'bg-slate-800'}`}
            />
          ))}
          <span className="text-xs text-slate-500 ml-1">últimos treinos</span>
        </div>
      )}

      {/* Next workout card */}
      {nextDay && activePlan ? (
        <Card className="mb-4" padding={false}>
          <div className="p-5">
            <div className="flex items-center justify-between mb-1">
              <span className="text-xs font-semibold text-indigo-400 uppercase tracking-wider">
                Próximo Treino
              </span>
              {nextDay.estimatedMinutes && (
                <span className="text-xs text-slate-500">~{nextDay.estimatedMinutes} min</span>
              )}
            </div>
            <div className="flex items-baseline gap-2 mt-1 mb-1">
              <span className="text-4xl font-black text-white">{nextDay.label}</span>
              <span className="text-xl font-bold text-slate-300">— {nextDay.name}</span>
            </div>
            <p className="text-slate-500 text-sm mb-4">
              {nextDay.exercises.length} exercícios · {activePlan.name}
            </p>
            <Button
              variant="primary"
              size="xl"
              fullWidth
              onClick={handleStart}
              className="flex items-center justify-center gap-2"
            >
              <Play size={20} fill="currentColor" />
              Iniciar Treino
            </Button>
          </div>
        </Card>
      ) : (
        <Card className="mb-4 text-center py-8">
          <p className="text-slate-400 mb-3">Nenhuma ficha ativa</p>
          <Button onClick={() => setScreen('workout-plans')}>Criar Ficha</Button>
        </Card>
      )}

      {/* Last session */}
      {lastSession && (
        <Card
          className="mb-4"
          onClick={() => {
            useAppStore.getState().setSelectedSession(lastSession.id);
            setScreen('history-detail');
          }}
        >
          <div className="flex items-start justify-between">
            <div>
              <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                Último Treino
              </span>
              <div className="flex items-baseline gap-2 mt-1">
                <span className="text-2xl font-black text-white">{lastSession.workoutDayLabel}</span>
                <span className="text-base font-semibold text-slate-300">
                  — {lastSession.workoutDayName}
                </span>
              </div>
              <p className="text-sm text-slate-500 mt-1">
                {formatDistanceToNow(lastSession.startTime, { addSuffix: true, locale: ptBR })} ·{' '}
                {lastSession.exercises.length} exercícios{' '}
                {lastSession.duration ? `· ${formatDuration(lastSession.duration)}` : ''}
              </p>
              {lastSession.prsAchieved && lastSession.prsAchieved.length > 0 && (
                <div className="flex items-center gap-1 mt-1.5">
                  <Trophy size={13} className="text-yellow-400" />
                  <span className="text-xs text-yellow-400">
                    {lastSession.prsAchieved.length} PR
                    {lastSession.prsAchieved.length > 1 ? 's' : ''}!
                  </span>
                </div>
              )}
            </div>
            <ChevronRight size={18} className="text-slate-600 mt-1" />
          </div>
        </Card>
      )}

      {/* Personal Records */}
      {prs.length > 0 && (
        <div className="mb-4">
          <div className="flex items-center justify-between mb-2">
            <h2 className="text-sm font-semibold text-slate-400 uppercase tracking-wider flex items-center gap-1.5">
              <Trophy size={14} className="text-yellow-400" />
              Recordes Pessoais
            </h2>
            <button
              className="text-xs text-indigo-400"
              onClick={() => setScreen('progress')}
            >
              Ver todos
            </button>
          </div>
          <div className="space-y-2">
            {prs.map(pr => (
              <Card key={pr.exerciseId} className="!py-3">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-semibold text-white">{pr.exerciseName}</p>
                    <p className="text-xs text-slate-500">
                      {formatDistanceToNow(pr.date, { addSuffix: true, locale: ptBR })}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="text-lg font-black text-white">
                      {pr.weight > 0 ? `${pr.weight} kg` : 'Peso Corp.'}
                    </p>
                    <p className="text-xs text-slate-500">× {pr.reps} reps</p>
                  </div>
                </div>
              </Card>
            ))}
          </div>
        </div>
      )}

      {/* Quick stats */}
      {sessions.length > 0 && (
        <Card>
          <div className="flex items-center gap-2 mb-3">
            <Zap size={16} className="text-indigo-400" />
            <h2 className="text-sm font-semibold text-slate-300">Estatísticas</h2>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div className="text-center">
              <p className="text-2xl font-black text-white">{totalWorkouts}</p>
              <p className="text-xs text-slate-500">Total</p>
            </div>
            <div className="text-center">
              <p className="text-2xl font-black text-white">
                {sessions.filter(s => {
                  const d = new Date(s.startTime);
                  const now = new Date();
                  return (
                    d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear()
                  );
                }).length}
              </p>
              <p className="text-xs text-slate-500">Este mês</p>
            </div>
            <div className="text-center">
              <p className="text-2xl font-black text-white">{personalRecords.size}</p>
              <p className="text-xs text-slate-500">PRs</p>
            </div>
          </div>
        </Card>
      )}
    </div>
  );
}
