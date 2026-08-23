import { X } from 'lucide-react';
import { useAppStore } from '../../store/appStore';
import { getSuggestions } from '../../data/exercises';

interface Props {
  exerciseId: string;
  exerciseName: string;
  exerciseIndex: number;
  onClose: () => void;
}

export function ExerciseSubstitute({ exerciseId, exerciseName, exerciseIndex, onClose }: Props) {
  const { exercises, substituteExercise } = useAppStore();
  const suggestions = getSuggestions(exerciseId, exercises);

  const handleSubstitute = (newId: string) => {
    substituteExercise(exerciseIndex, newId);
    onClose();
  };

  return (
    <div className="fixed inset-0 bg-black/70 z-50 flex items-end justify-center animate-fade-in">
      <div className="bg-slate-900 border border-slate-700 rounded-t-3xl w-full max-w-lg max-h-[70vh] flex flex-col">
        <div className="flex items-center justify-between p-5 border-b border-slate-800">
          <div>
            <h3 className="text-lg font-bold text-white">Substituir exercício</h3>
            <p className="text-sm text-slate-400">{exerciseName}</p>
          </div>
          <button onClick={onClose} className="p-2 rounded-xl hover:bg-slate-800 text-slate-400">
            <X size={20} />
          </button>
        </div>

        <div className="overflow-y-auto flex-1 p-4 space-y-2">
          {suggestions.length === 0 ? (
            <p className="text-center text-slate-500 py-8">Nenhuma sugestão disponível</p>
          ) : (
            suggestions.map((ex) => {
              const isEquivalent =
                ex.primaryMuscle === exercises.find(e => e.id === exerciseId)?.primaryMuscle &&
                ex.movementPattern === exercises.find(e => e.id === exerciseId)?.movementPattern;

              return (
                <button
                  key={ex.id}
                  onClick={() => handleSubstitute(ex.id)}
                  className="w-full p-4 bg-slate-800 hover:bg-slate-700 rounded-xl text-left transition-all active:scale-98"
                >
                  <div className="flex items-start justify-between">
                    <div>
                      <p className="font-semibold text-white">{ex.name}</p>
                      <p className="text-xs text-slate-400 mt-0.5">
                        {ex.primaryMuscle} · {ex.equipment}
                      </p>
                    </div>
                    <span
                      className={`text-xs px-2 py-0.5 rounded-full mt-0.5 ${
                        isEquivalent
                          ? 'bg-emerald-900/50 text-emerald-400'
                          : 'bg-slate-700 text-slate-400'
                      }`}
                    >
                      {isEquivalent ? 'Equivalente' : 'Alternativa'}
                    </span>
                  </div>
                </button>
              );
            })
          )}
        </div>

        <div className="p-4 border-t border-slate-800">
          <button
            onClick={onClose}
            className="w-full py-3 bg-slate-800 hover:bg-slate-700 rounded-xl text-slate-300 text-sm font-medium transition-colors"
          >
            Cancelar
          </button>
        </div>
      </div>
    </div>
  );
}
