import { useState } from 'react';
import { Plus, Target, Trash2, Trophy, X } from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { Card } from '../components/ui/Card';
import { Button } from '../components/ui/Button';
import { db } from '../db/database';
import type { Goal } from '../types';
import { nanoid } from '../utils/nanoid';

const goalTypes = [
  { value: 'workout_count', label: 'Número de treinos', unit: 'treinos' },
  { value: 'weekly_frequency', label: 'Frequência semanal', unit: 'treinos/semana' },
  { value: 'exercise_weight', label: 'Carga em exercício', unit: 'kg' },
  { value: 'body_weight', label: 'Peso corporal', unit: 'kg' },
  { value: 'volume', label: 'Volume total', unit: 'kg' },
];

export function Goals() {
  const { totalWorkouts, sessions } = useAppStore();
  const [goals, setGoals] = useState<Goal[]>([]);
  const [showNew, setShowNew] = useState(false);
  const [newGoal, setNewGoal] = useState({
    type: 'workout_count',
    title: '',
    target: 10,
    unit: 'treinos',
  });

  // Load goals on mount
  useState(() => {
    db.goals.toArray().then(setGoals);
  });

  const getCurrentValue = (goal: Goal): number => {
    if (goal.type === 'workout_count') return totalWorkouts;
    if (goal.type === 'weekly_frequency') {
      const weekStart = Date.now() - 7 * 24 * 3600000;
      return sessions.filter(s => s.startTime >= weekStart).length;
    }
    if (goal.type === 'volume') {
      return sessions.reduce((sum, s) => sum + (s.totalVolume ?? 0), 0);
    }
    return goal.current;
  };

  const handleAdd = async () => {
    if (!newGoal.title.trim()) return;
    const goal: Goal = {
      id: nanoid(),
      type: newGoal.type as Goal['type'],
      title: newGoal.title,
      target: newGoal.target,
      current: 0,
      unit: newGoal.unit,
      createdAt: Date.now(),
    };
    await db.goals.add(goal);
    setGoals(await db.goals.toArray());
    setShowNew(false);
    setNewGoal({ type: 'workout_count', title: '', target: 10, unit: 'treinos' });
  };

  const handleDelete = async (id: string) => {
    await db.goals.delete(id);
    setGoals(await db.goals.toArray());
  };

  return (
    <div className="pb-28 px-4 pt-6 max-w-lg mx-auto animate-fade-in">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-white">Metas</h1>
        <button
          onClick={() => setShowNew(true)}
          className="p-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white transition-colors"
        >
          <Plus size={18} />
        </button>
      </div>

      {goals.length === 0 ? (
        <div className="text-center py-12">
          <Target size={48} className="text-slate-700 mx-auto mb-3" />
          <p className="text-slate-400">Nenhuma meta criada</p>
          <p className="text-slate-600 text-sm mt-1">Defina metas para manter o foco</p>
          <Button className="mt-4" onClick={() => setShowNew(true)}>
            Criar meta
          </Button>
        </div>
      ) : (
        <div className="space-y-3">
          {goals.map(goal => {
            const current = getCurrentValue(goal);
            const pct = Math.min(100, Math.round((current / goal.target) * 100));
            const done = pct >= 100;
            return (
              <Card key={goal.id}>
                <div className="flex items-start justify-between mb-2">
                  <div>
                    <div className="flex items-center gap-2">
                      {done && <Trophy size={14} className="text-yellow-400" />}
                      <p className="font-semibold text-white">{goal.title}</p>
                    </div>
                    <p className="text-xs text-slate-500 mt-0.5">
                      {goalTypes.find(t => t.value === goal.type)?.label}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <div className="text-right">
                      <p className="font-black text-white text-lg">{current.toFixed(0)}</p>
                      <p className="text-xs text-slate-500">/ {goal.target} {goal.unit}</p>
                    </div>
                    <button
                      onClick={() => handleDelete(goal.id)}
                      className="text-slate-600 hover:text-red-400 p-1"
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                </div>
                <div className="h-2 bg-slate-800 rounded-full overflow-hidden">
                  <div
                    className={`h-full rounded-full transition-all duration-700 ${
                      done ? 'bg-yellow-500' : 'bg-indigo-500'
                    }`}
                    style={{ width: `${pct}%` }}
                  />
                </div>
                <p className={`text-xs mt-1 text-right ${done ? 'text-yellow-400' : 'text-slate-500'}`}>
                  {pct}%{done ? ' — Concluída! 🎉' : ''}
                </p>
              </Card>
            );
          })}
        </div>
      )}

      {/* New goal modal */}
      {showNew && (
        <div className="fixed inset-0 bg-black/70 z-50 flex items-end justify-center p-4 animate-fade-in">
          <div className="bg-slate-900 border border-slate-700 rounded-2xl w-full max-w-sm p-5">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-bold text-white">Nova Meta</h3>
              <button onClick={() => setShowNew(false)} className="text-slate-500 hover:text-slate-300">
                <X size={20} />
              </button>
            </div>

            <div className="space-y-3">
              <div>
                <label className="text-xs text-slate-500 block mb-1">Título</label>
                <input
                  value={newGoal.title}
                  onChange={e => setNewGoal(g => ({ ...g, title: e.target.value }))}
                  placeholder="Ex: Chegar a 100 treinos"
                  className="w-full bg-slate-800 border border-slate-700 rounded-xl px-3 py-2.5 text-white text-sm outline-none focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="text-xs text-slate-500 block mb-1">Tipo</label>
                <select
                  value={newGoal.type}
                  onChange={e => {
                    const t = goalTypes.find(g => g.value === e.target.value);
                    setNewGoal(g => ({ ...g, type: e.target.value, unit: t?.unit ?? '' }));
                  }}
                  className="w-full bg-slate-800 border border-slate-700 rounded-xl px-3 py-2.5 text-white text-sm outline-none focus:border-indigo-500"
                >
                  {goalTypes.map(t => (
                    <option key={t.value} value={t.value}>{t.label}</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="text-xs text-slate-500 block mb-1">
                  Meta ({newGoal.unit})
                </label>
                <input
                  type="number"
                  value={newGoal.target}
                  onChange={e => setNewGoal(g => ({ ...g, target: parseInt(e.target.value) || 0 }))}
                  className="w-full bg-slate-800 border border-slate-700 rounded-xl px-3 py-2.5 text-white text-sm outline-none focus:border-indigo-500"
                  min={1}
                />
              </div>

              <div className="flex gap-3 pt-2">
                <Button
                  variant="ghost"
                  fullWidth
                  onClick={() => setShowNew(false)}
                  className="border border-slate-700"
                >
                  Cancelar
                </Button>
                <Button variant="primary" fullWidth onClick={handleAdd}>
                  Criar
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
