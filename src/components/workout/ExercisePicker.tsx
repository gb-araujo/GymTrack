import { useState } from 'react';
import { X, Search } from 'lucide-react';
import { useAppStore } from '../../store/appStore';

interface Props {
  onSelect: (id: string, name: string) => void;
  onClose: () => void;
}

const muscles = ['Todos', 'Peito', 'Costas', 'Perna', 'Ombro', 'Bíceps', 'Tríceps', 'Abdômen'];

export function ExercisePicker({ onSelect, onClose }: Props) {
  const { exercises } = useAppStore();
  const [search, setSearch] = useState('');
  const [muscle, setMuscle] = useState('Todos');

  const filtered = exercises.filter(ex => {
    const matchSearch = ex.name.toLowerCase().includes(search.toLowerCase());
    const matchMuscle =
      muscle === 'Todos' ||
      ex.primaryMuscle.includes(muscle) ||
      ex.primaryMuscle === muscle;
    return matchSearch && matchMuscle;
  });

  return (
    <div className="fixed inset-0 bg-black/70 z-50 flex flex-col animate-fade-in">
      <div className="flex-1 flex flex-col bg-slate-900 mt-16 rounded-t-3xl">
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-slate-800">
          <h3 className="text-lg font-bold text-white">Selecionar Exercício</h3>
          <button onClick={onClose} className="p-2 rounded-xl hover:bg-slate-800 text-slate-400">
            <X size={20} />
          </button>
        </div>

        {/* Search */}
        <div className="px-4 pt-3 pb-2">
          <div className="relative">
            <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500" />
            <input
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Buscar exercício..."
              className="w-full bg-slate-800 border border-slate-700 rounded-xl pl-9 pr-4 py-2.5 text-white text-sm outline-none focus:border-indigo-500"
              autoFocus
            />
          </div>
        </div>

        {/* Muscle filter */}
        <div className="flex gap-2 px-4 pb-3 overflow-x-auto">
          {muscles.map(m => (
            <button
              key={m}
              onClick={() => setMuscle(m)}
              className={`flex-shrink-0 px-3 py-1 rounded-full text-xs font-medium transition-colors ${
                muscle === m
                  ? 'bg-indigo-600 text-white'
                  : 'bg-slate-800 text-slate-400 hover:bg-slate-700'
              }`}
            >
              {m}
            </button>
          ))}
        </div>

        {/* List */}
        <div className="flex-1 overflow-y-auto px-4 pb-6 space-y-1">
          {filtered.map(ex => (
            <button
              key={ex.id}
              onClick={() => onSelect(ex.id, ex.name)}
              className="w-full p-3 bg-slate-800 hover:bg-slate-700 rounded-xl text-left transition-colors"
            >
              <div className="flex items-center justify-between">
                <div>
                  <p className="font-medium text-white text-sm">{ex.name}</p>
                  <p className="text-xs text-slate-400 mt-0.5">
                    {ex.primaryMuscle} · {ex.equipment}
                    {ex.isCustom && ' · Personalizado'}
                  </p>
                </div>
                <span className="text-xs text-slate-600">{ex.category}</span>
              </div>
            </button>
          ))}

          {filtered.length === 0 && (
            <p className="text-center text-slate-500 py-8">Nenhum exercício encontrado</p>
          )}
        </div>
      </div>
    </div>
  );
}
