import type { ReactNode } from 'react';
import { Trophy, Dumbbell, Clock, Layers, Zap, CheckCircle } from 'lucide-react';
import { useAppStore } from '../../store/appStore';
import { Button } from '../ui/Button';

function formatDuration(minutes?: number) {
  if (!minutes) return '—';
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (h > 0) return `${h}h ${m}min`;
  return `${m} min`;
}

interface Props {
  onFinish: () => void;
}

export function SessionSummary({ onFinish }: Props) {
  const { activeSession, exercises } = useAppStore();
  if (!activeSession) return null;

  const { session } = activeSession;
  const prs = session.prsAchieved ?? [];
  const totalVolume = session.totalVolume ?? 0;
  const totalSets = session.totalSets ?? 0;
  const exerciseCount = session.exercises.length;

  return (
    <div className="min-h-screen bg-[#0a0a14] flex flex-col items-center justify-center px-6 py-12 animate-fade-in">
      {/* Celebration header */}
      <div className="text-center mb-8 animate-bounce-in">
        <div className="text-6xl mb-3">🎉</div>
        <h1 className="text-3xl font-black text-white mb-1">Treino Concluído!</h1>
        <p className="text-slate-400">
          Treino {session.workoutDayLabel} — {session.workoutDayName}
        </p>
      </div>

      {/* Stats grid */}
      <div className="grid grid-cols-2 gap-3 w-full max-w-sm mb-6">
        <StatCard
          icon={<Clock size={18} className="text-indigo-400" />}
          label="Duração"
          value={formatDuration(session.duration)}
        />
        <StatCard
          icon={<Dumbbell size={18} className="text-blue-400" />}
          label="Exercícios"
          value={String(exerciseCount)}
        />
        <StatCard
          icon={<Layers size={18} className="text-purple-400" />}
          label="Séries"
          value={String(totalSets)}
        />
        <StatCard
          icon={<Zap size={18} className="text-yellow-400" />}
          label="Volume"
          value={
            totalVolume >= 1000
              ? `${(totalVolume / 1000).toFixed(1)}t`
              : `${totalVolume.toFixed(0)} kg`
          }
        />
      </div>

      {/* PRs */}
      {prs.length > 0 && (
        <div className="w-full max-w-sm mb-6 bg-yellow-950/30 border border-yellow-800/40 rounded-2xl p-4">
          <div className="flex items-center gap-2 mb-3">
            <Trophy size={18} className="text-yellow-400" />
            <span className="font-bold text-yellow-300">
              {prs.length} novo{prs.length > 1 ? 's' : ''} PR{prs.length > 1 ? 's' : ''}!
            </span>
          </div>
          <div className="space-y-1">
            {prs.map(id => {
              const ex = exercises.find(e => e.id === id);
              const sessionEx = session.exercises.find(e => e.exerciseId === id);
              const maxSet = sessionEx?.sets
                .filter(s => s.completed)
                .reduce((best, s) => (s.weight > best.weight ? s : best), { weight: 0, reps: 0 });
              return (
                <p key={id} className="text-sm text-yellow-200">
                  🏆 {ex?.name ?? id}
                  {maxSet && maxSet.weight > 0 ? ` · ${maxSet.weight}kg × ${maxSet.reps}` : ''}
                </p>
              );
            })}
          </div>
        </div>
      )}

      {/* Exercise list */}
      <div className="w-full max-w-sm mb-8 space-y-2">
        {session.exercises.map(ex => {
          const completedSets = ex.sets.filter(s => s.completed);
          return (
            <div
              key={ex.id}
              className="flex items-center justify-between bg-slate-900 rounded-xl px-4 py-3"
            >
              <div className="flex items-center gap-2">
                <CheckCircle size={14} className="text-emerald-400" />
                <span className="text-sm text-white">{ex.name}</span>
              </div>
              <span className="text-xs text-slate-500">
                {completedSets.length} séries
              </span>
            </div>
          );
        })}
      </div>

      <Button variant="primary" size="xl" fullWidth onClick={onFinish} className="max-w-sm">
        Finalizar
      </Button>
    </div>
  );
}

function StatCard({
  icon,
  label,
  value,
}: {
  icon: ReactNode;
  label: string;
  value: string;
}) {
  return (
    <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4">
      <div className="flex items-center gap-2 mb-1">
        {icon}
        <span className="text-xs text-slate-400">{label}</span>
      </div>
      <p className="text-2xl font-black text-white">{value}</p>
    </div>
  );
}
