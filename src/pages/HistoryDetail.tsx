import { ArrowLeft, Clock, Dumbbell, Layers, Zap, Trophy, Check } from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { format } from 'date-fns';
import { ptBR } from 'date-fns/locale';

function formatDuration(min?: number) {
  if (!min) return '—';
  return min >= 60 ? `${Math.floor(min / 60)}h ${min % 60}min` : `${min}min`;
}

export function HistoryDetail() {
  const { sessions, selectedSessionId, setScreen, previousScreen, personalRecords } = useAppStore();
  const session = sessions.find(s => s.id === selectedSessionId);

  if (!session) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <p className="text-slate-400">Treino não encontrado</p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#0a0a14] animate-fade-in">
      {/* Header */}
      <div className="flex items-center gap-3 px-4 pt-5 pb-3 border-b border-slate-800">
        <button
          onClick={() => setScreen(previousScreen || 'history')}
          className="p-2 rounded-xl hover:bg-slate-800 text-slate-400"
        >
          <ArrowLeft size={20} />
        </button>
        <div className="flex-1">
          <p className="text-xs text-slate-500">
            {format(session.startTime, "EEEE, d 'de' MMMM 'de' yyyy", { locale: ptBR })}
          </p>
          <h1 className="text-lg font-bold text-white">
            Treino {session.workoutDayLabel} — {session.workoutDayName}
          </h1>
        </div>
      </div>

      <div className="px-4 py-4 pb-8 max-w-lg mx-auto space-y-4">
        {/* Stats row */}
        <div className="grid grid-cols-4 gap-2">
          {[
            { icon: <Clock size={14} className="text-indigo-400" />, label: 'Duração', value: formatDuration(session.duration) },
            { icon: <Dumbbell size={14} className="text-blue-400" />, label: 'Exercícios', value: String(session.exercises.length) },
            { icon: <Layers size={14} className="text-purple-400" />, label: 'Séries', value: String(session.totalSets ?? '—') },
            {
              icon: <Zap size={14} className="text-yellow-400" />,
              label: 'Volume',
              value: session.totalVolume
                ? session.totalVolume >= 1000
                  ? `${(session.totalVolume / 1000).toFixed(1)}t`
                  : `${session.totalVolume.toFixed(0)}kg`
                : '—',
            },
          ].map(stat => (
            <div key={stat.label} className="bg-slate-900 border border-slate-800 rounded-xl p-2.5 text-center">
              <div className="flex justify-center mb-1">{stat.icon}</div>
              <p className="text-base font-black text-white">{stat.value}</p>
              <p className="text-[10px] text-slate-500">{stat.label}</p>
            </div>
          ))}
        </div>

        {/* PRs */}
        {session.prsAchieved && session.prsAchieved.length > 0 && (
          <div className="bg-yellow-950/30 border border-yellow-800/40 rounded-2xl p-4">
            <div className="flex items-center gap-2 mb-2">
              <Trophy size={16} className="text-yellow-400" />
              <span className="font-bold text-yellow-300 text-sm">Recordes Pessoais</span>
            </div>
            {session.prsAchieved.map(id => {
              const pr = personalRecords.get(id);
              return (
                <p key={id} className="text-sm text-yellow-200">
                  🏆 {pr?.exerciseName ?? id}
                  {pr ? ` · ${pr.weight}kg × ${pr.reps}` : ''}
                </p>
              );
            })}
          </div>
        )}

        {/* Exercises */}
        <div className="space-y-3">
          {session.exercises.map(ex => {
            const completedSets = ex.sets.filter(s => s.completed);
            const volume = completedSets.reduce((sum, s) => sum + s.weight * s.reps, 0);
            return (
              <div key={ex.id} className="bg-slate-900 border border-slate-800 rounded-2xl p-4">
                <div className="flex items-center justify-between mb-3">
                  <div>
                    <p className="font-bold text-white">{ex.name}</p>
                    {ex.substitutedFrom && (
                      <p className="text-xs text-slate-500">Substituiu outro exercício</p>
                    )}
                  </div>
                  {volume > 0 && (
                    <p className="text-xs text-slate-500">{volume.toFixed(0)} kg vol.</p>
                  )}
                </div>
                <div className="space-y-1.5">
                  {completedSets.map((set, i) => (
                    <div key={set.id} className="flex items-center gap-3">
                      <div className="w-6 h-6 rounded-lg bg-emerald-900/50 text-emerald-400 flex items-center justify-center">
                        <Check size={12} />
                      </div>
                      <span className="text-sm text-slate-300">
                        Série {i + 1}
                      </span>
                      <span className="text-sm font-bold text-white ml-auto">
                        {set.weight > 0 ? `${set.weight} kg` : 'Peso corp.'} × {set.reps}
                      </span>
                    </div>
                  ))}
                  {completedSets.length === 0 && (
                    <p className="text-xs text-slate-600">Nenhuma série registrada</p>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
