import { useState, useEffect } from 'react';
import { Trophy, TrendingUp, BarChart2 } from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { Card } from '../components/ui/Card';
import {
  LineChart, Line, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid
} from 'recharts';
import { format } from 'date-fns';
import { ptBR } from 'date-fns/locale';

type Tab = 'records' | 'volume' | 'frequency';

export function Progress() {
  const { personalRecords, sessions, totalWorkouts } = useAppStore();
  const [tab, setTab] = useState<Tab>('records');
  const [selectedExercise, setSelectedExercise] = useState<string | null>(null);
  const [exerciseHistory, setExerciseHistory] = useState<{ date: number; weight: number; reps: number }[]>([]);

  const prs = Array.from(personalRecords.values()).sort((a, b) => b.date - a.date);

  useEffect(() => {
    if (!selectedExercise) return;
    const { getExerciseHistory } = useAppStore.getState();
    getExerciseHistory(selectedExercise).then(history => {
      const data = history
        .reverse()
        .map(h => {
          const maxSet = h.sets
            .filter(s => s.completed)
            .reduce((best, s) => (s.weight > best.weight ? s : best), { weight: 0, reps: 0 });
          return { date: h.date, weight: maxSet.weight, reps: maxSet.reps };
        })
        .filter(d => d.weight > 0);
      setExerciseHistory(data);
    });
  }, [selectedExercise]);

  // Volume per workout (last 10)
  const volumeData = sessions
    .slice(0, 10)
    .reverse()
    .map(s => ({
      date: format(s.startTime, 'd/MM', { locale: ptBR }),
      volume: Math.round((s.totalVolume ?? 0) / 1000 * 10) / 10,
      label: `${s.workoutDayLabel} — ${s.workoutDayName}`,
    }));

  // Frequency (last 8 weeks)
  const weeklyData: { week: string; count: number }[] = [];
  for (let i = 7; i >= 0; i--) {
    const start = Date.now() - i * 7 * 24 * 3600000;
    const end = start + 7 * 24 * 3600000;
    const count = sessions.filter(s => s.startTime >= start && s.startTime < end).length;
    weeklyData.push({ week: `S-${i}`, count });
  }

  const tabs = [
    { id: 'records' as Tab, label: 'PRs', icon: Trophy },
    { id: 'volume' as Tab, label: 'Volume', icon: BarChart2 },
    { id: 'frequency' as Tab, label: 'Frequência', icon: TrendingUp },
  ];

  return (
    <div className="pb-28 px-4 pt-6 max-w-lg mx-auto animate-fade-in">
      <h1 className="text-2xl font-bold text-white mb-4">Progresso</h1>

      {/* Summary */}
      <div className="grid grid-cols-3 gap-3 mb-6">
        <StatMini label="Treinos" value={String(totalWorkouts)} />
        <StatMini
          label="Este mês"
          value={String(
            sessions.filter(s => {
              const d = new Date(s.startTime);
              const n = new Date();
              return d.getMonth() === n.getMonth() && d.getFullYear() === n.getFullYear();
            }).length,
          )}
        />
        <StatMini label="PRs" value={String(personalRecords.size)} />
      </div>

      {/* Tabs */}
      <div className="flex gap-2 mb-5 bg-slate-900 rounded-xl p-1">
        {tabs.map(t => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            className={`flex-1 py-2 rounded-lg text-xs font-semibold transition-all flex items-center justify-center gap-1.5 ${
              tab === t.id ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <t.icon size={13} />
            {t.label}
          </button>
        ))}
      </div>

      {/* PRs tab */}
      {tab === 'records' && (
        <div>
          {prs.length === 0 ? (
            <div className="text-center py-12">
              <Trophy size={48} className="text-slate-700 mx-auto mb-3" />
              <p className="text-slate-400">Nenhum recorde ainda</p>
              <p className="text-slate-600 text-sm">Complete treinos para registrar seus PRs</p>
            </div>
          ) : (
            <div className="space-y-2">
              {prs.map(pr => (
                <Card
                  key={pr.exerciseId}
                  onClick={() => setSelectedExercise(pr.exerciseId === selectedExercise ? null : pr.exerciseId)}
                >
                  <div className="flex items-center justify-between mb-1">
                    <div className="flex items-center gap-2">
                      <Trophy size={14} className="text-yellow-400" />
                      <p className="font-semibold text-white">{pr.exerciseName}</p>
                    </div>
                    <div className="text-right">
                      <p className="text-lg font-black text-white">
                        {pr.weight > 0 ? `${pr.weight} kg` : 'Peso Corp.'}
                      </p>
                      <p className="text-xs text-slate-500">× {pr.reps} reps</p>
                    </div>
                  </div>

                  {selectedExercise === pr.exerciseId && exerciseHistory.length > 1 && (
                    <div className="mt-3 pt-3 border-t border-slate-800">
                      <p className="text-xs text-slate-500 mb-2">Evolução de carga</p>
                      <ResponsiveContainer width="100%" height={100}>
                        <LineChart data={exerciseHistory}>
                          <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                          <XAxis
                            dataKey="date"
                            tickFormatter={d => format(d, 'd/MM')}
                            tick={{ fontSize: 10, fill: '#64748b' }}
                          />
                          <YAxis tick={{ fontSize: 10, fill: '#64748b' }} />
                          <Tooltip
                            contentStyle={{ background: '#1e293b', border: 'none', borderRadius: 8, fontSize: 12 }}
                            formatter={(v: unknown) => [`${v} kg`, 'Carga']}
                            labelFormatter={l => format(Number(l), 'd/MM/yy')}
                          />
                          <Line
                            type="monotone"
                            dataKey="weight"
                            stroke="#6366f1"
                            strokeWidth={2}
                            dot={{ r: 3, fill: '#6366f1' }}
                          />
                        </LineChart>
                      </ResponsiveContainer>
                    </div>
                  )}
                </Card>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Volume tab */}
      {tab === 'volume' && (
        <div>
          {volumeData.length === 0 ? (
            <div className="text-center py-12">
              <BarChart2 size={48} className="text-slate-700 mx-auto mb-3" />
              <p className="text-slate-400">Sem dados de volume ainda</p>
            </div>
          ) : (
            <Card>
              <p className="text-sm font-semibold text-slate-300 mb-3">Volume por Treino (toneladas)</p>
              <ResponsiveContainer width="100%" height={200}>
                <LineChart data={volumeData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                  <XAxis dataKey="date" tick={{ fontSize: 11, fill: '#64748b' }} />
                  <YAxis tick={{ fontSize: 11, fill: '#64748b' }} />
                  <Tooltip
                    contentStyle={{ background: '#1e293b', border: 'none', borderRadius: 8, fontSize: 12 }}
                    formatter={(v: unknown) => [`${v}t`, 'Volume']}
                  />
                  <Line
                    type="monotone"
                    dataKey="volume"
                    stroke="#6366f1"
                    strokeWidth={2.5}
                    dot={{ r: 4, fill: '#6366f1' }}
                  />
                </LineChart>
              </ResponsiveContainer>
            </Card>
          )}
        </div>
      )}

      {/* Frequency tab */}
      {tab === 'frequency' && (
        <div>
          <Card>
            <p className="text-sm font-semibold text-slate-300 mb-3">Treinos por Semana</p>
            <ResponsiveContainer width="100%" height={180}>
              <LineChart data={weeklyData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                <XAxis dataKey="week" tick={{ fontSize: 11, fill: '#64748b' }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: '#64748b' }} />
                <Tooltip
                  contentStyle={{ background: '#1e293b', border: 'none', borderRadius: 8, fontSize: 12 }}
                  formatter={(v: unknown) => [v as number, 'Treinos']}
                />
                <Line
                  type="monotone"
                  dataKey="count"
                  stroke="#10b981"
                  strokeWidth={2.5}
                  dot={{ r: 4, fill: '#10b981' }}
                />
              </LineChart>
            </ResponsiveContainer>
          </Card>

          <div className="mt-4 space-y-2">
            <StatRow
              label="Média semanal"
              value={`${(weeklyData.reduce((s, w) => s + w.count, 0) / Math.max(weeklyData.filter(w => w.count > 0).length, 1)).toFixed(1)} treinos`}
            />
            <StatRow label="Total de treinos" value={String(totalWorkouts)} />
            <StatRow
              label="Melhor semana"
              value={`${Math.max(...weeklyData.map(w => w.count))} treinos`}
            />
          </div>
        </div>
      )}
    </div>
  );
}

function StatMini({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl p-3 text-center">
      <p className="text-xl font-black text-white">{value}</p>
      <p className="text-xs text-slate-500">{label}</p>
    </div>
  );
}

function StatRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between bg-slate-900 border border-slate-800 rounded-xl px-4 py-3">
      <span className="text-sm text-slate-400">{label}</span>
      <span className="font-bold text-white">{value}</span>
    </div>
  );
}
