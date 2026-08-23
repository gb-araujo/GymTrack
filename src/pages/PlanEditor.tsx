import { useState } from 'react';
import { ArrowLeft, Plus, Trash2, GripVertical, ChevronDown, ChevronUp } from 'lucide-react';
import type { WorkoutPlan, WorkoutDay, WorkoutExercise } from '../types';
import { Button } from '../components/ui/Button';
import { useAppStore } from '../store/appStore';
import { nanoid } from '../utils/nanoid';
import { ExercisePicker } from '../components/workout/ExercisePicker';

interface Props {
  plan: WorkoutPlan;
  onSave: (plan: WorkoutPlan) => void;
  onCancel: () => void;
}

export function PlanEditor({ plan: initialPlan, onSave, onCancel }: Props) {
  const [plan, setPlan] = useState<WorkoutPlan>(initialPlan);
  const [pickingForDay, setPickingForDay] = useState<string | null>(null);
  const [expandedDays, setExpandedDays] = useState<Set<string>>(new Set(initialPlan.days.map(d => d.id)));

  const updatePlanName = (name: string) => setPlan(p => ({ ...p, name }));

  const addDay = () => {
    const labels = ['A', 'B', 'C', 'D', 'E', 'F'];
    const nextLabel = labels[plan.days.length] ?? String(plan.days.length + 1);
    const day: WorkoutDay = {
      id: nanoid(),
      label: nextLabel,
      name: '',
      exercises: [],
      order: plan.days.length,
    };
    setPlan(p => ({ ...p, days: [...p.days, day] }));
    setExpandedDays(s => new Set([...s, day.id]));
  };

  const removeDay = (dayId: string) => {
    setPlan(p => ({
      ...p,
      days: p.days
        .filter(d => d.id !== dayId)
        .map((d, i) => ({ ...d, order: i })),
    }));
  };

  const updateDay = (dayId: string, updates: Partial<WorkoutDay>) => {
    setPlan(p => ({
      ...p,
      days: p.days.map(d => (d.id === dayId ? { ...d, ...updates } : d)),
    }));
  };

  const addExercise = (dayId: string, exerciseId: string, _exerciseName: string) => {
    const we: WorkoutExercise = {
      id: nanoid(),
      exerciseId,
      sets: 3,
      repMin: 8,
      repMax: 12,
      restSeconds: 90,
      initialWeight: 0,
      order: plan.days.find(d => d.id === dayId)?.exercises.length ?? 0,
    };
    setPlan(p => ({
      ...p,
      days: p.days.map(d =>
        d.id === dayId ? { ...d, exercises: [...d.exercises, we] } : d,
      ),
    }));
  };

  const removeExercise = (dayId: string, weId: string) => {
    setPlan(p => ({
      ...p,
      days: p.days.map(d =>
        d.id === dayId
          ? { ...d, exercises: d.exercises.filter(e => e.id !== weId).map((e, i) => ({ ...e, order: i })) }
          : d,
      ),
    }));
  };

  const updateExercise = (dayId: string, weId: string, updates: Partial<WorkoutExercise>) => {
    setPlan(p => ({
      ...p,
      days: p.days.map(d =>
        d.id === dayId
          ? { ...d, exercises: d.exercises.map(e => (e.id === weId ? { ...e, ...updates } : e)) }
          : d,
      ),
    }));
  };

  const toggleDay = (id: string) => {
    setExpandedDays(s => {
      const n = new Set(s);
      n.has(id) ? n.delete(id) : n.add(id);
      return n;
    });
  };

  const { exercises } = useAppStore();

  return (
    <div className="min-h-screen bg-[#0a0a14] flex flex-col">
      {/* Header */}
      <div className="flex items-center gap-3 px-4 pt-5 pb-3 border-b border-slate-800">
        <button
          onClick={onCancel}
          className="p-2 rounded-xl hover:bg-slate-800 text-slate-400"
        >
          <ArrowLeft size={20} />
        </button>
        <h1 className="text-lg font-bold text-white flex-1">Editor de Ficha</h1>
        <Button size="sm" onClick={() => onSave(plan)}>
          Salvar
        </Button>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-4 pb-8 space-y-4">
        {/* Plan name */}
        <input
          value={plan.name}
          onChange={e => updatePlanName(e.target.value)}
          placeholder="Nome da ficha"
          className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white font-bold text-lg outline-none focus:border-indigo-500"
        />

        {/* Days */}
        {plan.days.map(day => (
          <div key={day.id} className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden">
            {/* Day header */}
            <div className="flex items-center gap-3 p-4">
              <GripVertical size={16} className="text-slate-600" />
              <div className="flex-1 flex items-center gap-2">
                <input
                  value={day.label}
                  onChange={e => updateDay(day.id, { label: e.target.value })}
                  className="w-10 bg-slate-800 border border-slate-700 rounded-lg px-2 py-1 text-white font-bold text-center outline-none focus:border-indigo-500"
                  maxLength={2}
                />
                <input
                  value={day.name}
                  onChange={e => updateDay(day.id, { name: e.target.value })}
                  placeholder="Nome do treino (ex: Peito)"
                  className="flex-1 bg-slate-800 border border-slate-700 rounded-lg px-3 py-1.5 text-white outline-none focus:border-indigo-500 text-sm"
                />
              </div>
              <button onClick={() => toggleDay(day.id)} className="p-1 text-slate-500">
                {expandedDays.has(day.id) ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
              </button>
              <button
                onClick={() => removeDay(day.id)}
                className="p-1 text-slate-600 hover:text-red-400"
              >
                <Trash2 size={16} />
              </button>
            </div>

            {expandedDays.has(day.id) && (
              <div className="border-t border-slate-800 p-3 space-y-2">
                {day.exercises.map(we => {
                  const ex = exercises.find(e => e.id === we.exerciseId);
                  return (
                    <div key={we.id} className="bg-slate-800 rounded-xl p-3">
                      <div className="flex items-center justify-between mb-2">
                        <p className="font-medium text-white text-sm">{ex?.name ?? we.exerciseId}</p>
                        <button
                          onClick={() => removeExercise(day.id, we.id)}
                          className="text-slate-600 hover:text-red-400"
                        >
                          <Trash2 size={14} />
                        </button>
                      </div>
                      <div className="grid grid-cols-4 gap-2">
                        <ExProp
                          label="Séries"
                          value={we.sets}
                          onChange={v => updateExercise(day.id, we.id, { sets: v })}
                          min={1}
                          max={10}
                        />
                        <ExProp
                          label="Min reps"
                          value={we.repMin}
                          onChange={v => updateExercise(day.id, we.id, { repMin: v })}
                          min={1}
                          max={50}
                        />
                        <ExProp
                          label="Max reps"
                          value={we.repMax}
                          onChange={v => updateExercise(day.id, we.id, { repMax: v })}
                          min={1}
                          max={50}
                        />
                        <ExProp
                          label="Descanso"
                          value={we.restSeconds}
                          onChange={v => updateExercise(day.id, we.id, { restSeconds: v })}
                          min={0}
                          max={600}
                          step={15}
                        />
                      </div>
                      <div className="mt-2">
                        <label className="text-xs text-slate-500">Carga inicial (kg)</label>
                        <input
                          type="number"
                          value={we.initialWeight ?? 0}
                          onChange={e => updateExercise(day.id, we.id, { initialWeight: parseFloat(e.target.value) || 0 })}
                          className="w-full mt-1 bg-slate-700 border border-slate-600 rounded-lg px-2 py-1 text-white text-sm outline-none focus:border-indigo-500"
                        />
                      </div>
                    </div>
                  );
                })}

                <button
                  onClick={() => setPickingForDay(day.id)}
                  className="w-full py-2.5 border border-dashed border-slate-700 rounded-xl text-slate-500 hover:border-indigo-500 hover:text-indigo-400 text-sm flex items-center justify-center gap-2 transition-colors"
                >
                  <Plus size={15} />
                  Adicionar exercício
                </button>
              </div>
            )}
          </div>
        ))}

        <button
          onClick={addDay}
          className="w-full py-4 border border-dashed border-slate-700 rounded-2xl text-slate-500 hover:border-indigo-500 hover:text-indigo-400 flex items-center justify-center gap-2 transition-colors"
        >
          <Plus size={18} />
          Adicionar treino
        </button>
      </div>

      {/* Exercise picker */}
      {pickingForDay && (
        <ExercisePicker
          onSelect={(id, name) => {
            addExercise(pickingForDay, id, name);
            setPickingForDay(null);
          }}
          onClose={() => setPickingForDay(null)}
        />
      )}
    </div>
  );
}

function ExProp({
  label,
  value,
  onChange,
  min,
  max,
  step = 1,
}: {
  label: string;
  value: number;
  onChange: (v: number) => void;
  min: number;
  max: number;
  step?: number;
}) {
  return (
    <div>
      <p className="text-[10px] text-slate-500 mb-1">{label}</p>
      <input
        type="number"
        value={value}
        onChange={e => onChange(Math.min(max, Math.max(min, parseInt(e.target.value) || 0)))}
        step={step}
        min={min}
        max={max}
        className="w-full bg-slate-700 border border-slate-600 rounded-lg px-2 py-1 text-white text-sm text-center outline-none focus:border-indigo-500"
      />
    </div>
  );
}
