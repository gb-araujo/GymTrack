import { useState, useEffect } from 'react';
import type { ReactNode } from 'react';
import { Plus, ChevronRight, Trash2, Copy, Download, Upload } from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import type { WorkoutPlan } from '../types';
import { PlanEditor } from './PlanEditor';
import { nanoid } from '../utils/nanoid';

export function WorkoutPlans() {
  const { activePlan, savePlan, setActivePlan, deletePlan, getAllPlans } = useAppStore();
  const [plans, setPlans] = useState<WorkoutPlan[]>([]);
  const [editingPlan, setEditingPlan] = useState<WorkoutPlan | null>(null);
  const [showImport, setShowImport] = useState(false);
  const [importText, setImportText] = useState('');
  const [importError, setImportError] = useState('');

  useEffect(() => {
    loadPlans();
  }, [activePlan]);

  const loadPlans = async () => {
    const all = await getAllPlans();
    setPlans(all);
  };

  const handleNew = () => {
    const newPlan: WorkoutPlan = {
      id: nanoid(),
      name: 'Nova Ficha',
      days: [],
      currentDayIndex: 0,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      isActive: false,
    };
    setEditingPlan(newPlan);
  };

  const handleDuplicate = async (plan: WorkoutPlan) => {
    const copy: WorkoutPlan = {
      ...plan,
      id: nanoid(),
      name: `${plan.name} (cópia)`,
      currentDayIndex: 0,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      isActive: false,
      days: plan.days.map(d => ({
        ...d,
        id: nanoid(),
        exercises: d.exercises.map(e => ({ ...e, id: nanoid() })),
      })),
    };
    await savePlan(copy);
    loadPlans();
  };

  const handleDelete = async (id: string) => {
    if (confirm('Excluir esta ficha?')) {
      await deletePlan(id);
      loadPlans();
    }
  };

  const handleExport = (plan: WorkoutPlan) => {
    const data = JSON.stringify({ schemaVersion: 1, workoutPlan: plan }, null, 2);
    const blob = new Blob([data], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${plan.name.replace(/\s+/g, '_')}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleImport = () => {
    setImportError('');
    try {
      const parsed = JSON.parse(importText);
      if (!parsed.schemaVersion || !parsed.workoutPlan) {
        setImportError('Formato inválido: campos schemaVersion e workoutPlan são obrigatórios.');
        return;
      }
      const plan = parsed.workoutPlan as WorkoutPlan;
      if (!plan.name || !Array.isArray(plan.days)) {
        setImportError('Estrutura do plano inválida.');
        return;
      }
      const newPlan: WorkoutPlan = {
        ...plan,
        id: nanoid(),
        currentDayIndex: 0,
        createdAt: Date.now(),
        updatedAt: Date.now(),
        isActive: false,
      };
      savePlan(newPlan).then(() => {
        loadPlans();
        setShowImport(false);
        setImportText('');
      });
    } catch {
      setImportError('JSON inválido. Verifique a formatação.');
    }
  };

  if (editingPlan) {
    return (
      <PlanEditor
        plan={editingPlan}
        onSave={async (p) => {
          await savePlan(p);
          loadPlans();
          setEditingPlan(null);
        }}
        onCancel={() => setEditingPlan(null)}
      />
    );
  }

  return (
    <div className="pb-28 px-4 pt-6 max-w-lg mx-auto animate-fade-in">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-white">Fichas de Treino</h1>
        <div className="flex gap-2">
          <button
            onClick={() => setShowImport(true)}
            className="p-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-400 transition-colors"
          >
            <Upload size={18} />
          </button>
          <button
            onClick={handleNew}
            className="p-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white transition-colors"
          >
            <Plus size={18} />
          </button>
        </div>
      </div>

      {plans.length === 0 ? (
        <div className="text-center py-12">
          <p className="text-slate-400 mb-4">Nenhuma ficha criada</p>
          <Button onClick={handleNew}>Criar ficha</Button>
        </div>
      ) : (
        <div className="space-y-3">
          {plans.map(plan => (
            <PlanCard
              key={plan.id}
              plan={plan}
              isActive={plan.id === activePlan?.id}
              onEdit={() => setEditingPlan(plan)}
              onActivate={() => setActivePlan(plan.id)}
              onDuplicate={() => handleDuplicate(plan)}
              onDelete={() => handleDelete(plan.id)}
              onExport={() => handleExport(plan)}
            />
          ))}
        </div>
      )}

      {/* Import modal */}
      {showImport && (
        <div className="fixed inset-0 bg-black/70 z-50 flex items-end justify-center p-4 animate-fade-in">
          <div className="bg-slate-900 border border-slate-700 rounded-2xl w-full max-w-sm p-5">
            <h3 className="text-lg font-bold text-white mb-3">Importar Ficha (JSON)</h3>
            <textarea
              value={importText}
              onChange={e => setImportText(e.target.value)}
              placeholder={'{\n  "schemaVersion": 1,\n  "workoutPlan": { ... }\n}'}
              className="w-full h-40 bg-slate-800 border border-slate-700 rounded-xl p-3 text-sm text-slate-300 font-mono outline-none focus:border-indigo-500 mb-2 resize-none"
            />
            {importError && (
              <p className="text-red-400 text-xs mb-2">{importError}</p>
            )}
            <div className="flex gap-3">
              <Button
                variant="ghost"
                fullWidth
                onClick={() => { setShowImport(false); setImportError(''); setImportText(''); }}
                className="border border-slate-700"
              >
                Cancelar
              </Button>
              <Button variant="primary" fullWidth onClick={handleImport}>
                Importar
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

interface PlanCardProps {
  plan: WorkoutPlan;
  isActive: boolean;
  onEdit: () => void;
  onActivate: () => void;
  onDuplicate: () => void;
  onDelete: () => void;
  onExport: () => void;
}

function PlanCard({ plan, isActive, onEdit, onActivate, onDuplicate, onDelete, onExport }: PlanCardProps) {
  const [showActions, setShowActions] = useState(false);

  return (
    <Card padding={false}>
      <div
        className="flex items-center gap-3 p-4 cursor-pointer"
        onClick={() => setShowActions(!showActions)}
      >
        <div className="flex-1">
          <div className="flex items-center gap-2 mb-0.5">
            <p className="font-bold text-white">{plan.name}</p>
            {isActive && (
              <span className="text-[10px] bg-indigo-600 text-white px-2 py-0.5 rounded-full font-semibold">
                ATIVA
              </span>
            )}
          </div>
          <p className="text-sm text-slate-500">
            {plan.days.length} treinos ·{' '}
            {plan.days.map(d => d.label).join(' / ')}
          </p>
        </div>
        <ChevronRight
          size={18}
          className={`text-slate-600 transition-transform ${showActions ? 'rotate-90' : ''}`}
        />
      </div>

      {showActions && (
        <div className="border-t border-slate-800 grid grid-cols-2 divide-x divide-y divide-slate-800">
          <ActionBtn label="Editar" onClick={onEdit} />
          {!isActive && <ActionBtn label="Ativar" onClick={onActivate} highlight />}
          <ActionBtn label="Duplicar" onClick={onDuplicate} icon={<Copy size={13} />} />
          <ActionBtn label="Exportar" onClick={onExport} icon={<Download size={13} />} />
          <ActionBtn label="Excluir" onClick={onDelete} danger icon={<Trash2 size={13} />} />
        </div>
      )}
    </Card>
  );
}

function ActionBtn({
  label,
  onClick,
  danger,
  highlight,
  icon,
}: {
  label: string;
  onClick: () => void;
  danger?: boolean;
  highlight?: boolean;
  icon?: ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      className={`py-3 text-sm font-medium transition-colors flex items-center justify-center gap-1.5 ${
        danger
          ? 'text-red-400 hover:bg-red-950/30'
          : highlight
          ? 'text-indigo-400 hover:bg-indigo-950/30'
          : 'text-slate-400 hover:bg-slate-800'
      }`}
    >
      {icon}
      {label}
    </button>
  );
}
