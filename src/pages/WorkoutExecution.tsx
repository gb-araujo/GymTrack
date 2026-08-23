import { useEffect, useRef, useState } from 'react';
import {
  X, ChevronLeft, ChevronRight, Check, Plus, Trash2,
  RefreshCw, Trophy, Timer, SkipForward
} from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { Button } from '../components/ui/Button';
import { NumberInput } from '../components/ui/NumberInput';
import type { SetRecord } from '../types';
import { ExerciseSubstitute } from '../components/workout/ExerciseSubstitute';
import { SessionSummary } from '../components/workout/SessionSummary';

function formatTime(seconds: number) {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

function ElapsedTimer({ startTime }: { startTime: number }) {
  const [elapsed, setElapsed] = useState(0);
  useEffect(() => {
    const update = () => setElapsed(Math.floor((Date.now() - startTime) / 1000));
    update();
    const id = setInterval(update, 1000);
    return () => clearInterval(id);
  }, [startTime]);
  return <span>{formatTime(elapsed)}</span>;
}

export function WorkoutExecution() {
  const {
    activeSession,
    setScreen,
    updateSetRecord,
    addSet,
    removeSet,
    completeExercise,
    uncompleteExercise,
    setCurrentExercise,
    completeSession,
    abandonSession,
    startRestTimer,
    stopRestTimer,
    tickRestTimer,
    activePlan,
  } = useAppStore();

  const [showSubstitute, setShowSubstitute] = useState(false);
  const [showAbortConfirm, setShowAbortConfirm] = useState(false);
  const restRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (!activeSession) return;
    if (activeSession.restTimer !== null) {
      restRef.current = setInterval(tickRestTimer, 1000);
    } else {
      if (restRef.current) clearInterval(restRef.current);
    }
    return () => { if (restRef.current) clearInterval(restRef.current); };
  }, [activeSession?.restTimer !== null]);

  if (!activeSession) {
    return null;
  }

  const { session, currentExerciseIndex, restTimer } = activeSession;

  if (session.completed) {
    return <SessionSummary onFinish={() => { setScreen('home'); }} />;
  }

  const currentExercise = session.exercises[currentExerciseIndex];
  const totalExercises = session.exercises.length;
  const completedExercises = session.exercises.filter(e => e.completed).length;

  const dayExercise = activePlan?.days
    .find(d => d.id === session.workoutDayId)
    ?.exercises.find(we => we.id === currentExercise.workoutExerciseId);

  const restDefault = dayExercise?.restSeconds ?? 90;

  const handleCompleteSet = (setIndex: number) => {
    const s = currentExercise.sets[setIndex];
    if (s.completed) {
      updateSetRecord(currentExerciseIndex, setIndex, { completed: false });
    } else {
      updateSetRecord(currentExerciseIndex, setIndex, { completed: true });
      startRestTimer(restDefault, currentExercise.id);
    }
  };

  const handleCompleteExercise = () => {
    completeExercise(currentExerciseIndex);
    stopRestTimer();
    if (currentExerciseIndex < totalExercises - 1) {
      setCurrentExercise(currentExerciseIndex + 1);
    }
  };

  const handleFinish = async () => {
    await completeSession();
  };

  const allDone = completedExercises === totalExercises;

  return (
    <div className="min-h-screen bg-[#0a0a14] flex flex-col">
      {/* Top bar */}
      <div className="flex items-center justify-between px-4 pt-5 pb-3 border-b border-slate-800/60">
        <button
          onClick={() => setShowAbortConfirm(true)}
          className="p-2 rounded-xl hover:bg-slate-800 text-slate-400 transition-colors"
        >
          <X size={22} />
        </button>

        <div className="text-center">
          <p className="text-xs text-slate-500 font-medium">
            Treino {session.workoutDayLabel} — {session.workoutDayName}
          </p>
          <p className="text-sm font-bold text-white">
            {completedExercises}/{totalExercises} exercícios
          </p>
        </div>

        <div className="text-sm font-mono text-slate-400 bg-slate-900 px-3 py-1.5 rounded-xl">
          <ElapsedTimer startTime={session.startTime} />
        </div>
      </div>

      {/* Progress bar */}
      <div className="h-1 bg-slate-800 relative">
        <div
          className="h-full bg-indigo-500 transition-all duration-500"
          style={{ width: `${(completedExercises / totalExercises) * 100}%` }}
        />
      </div>

      {/* Exercise tabs */}
      <div className="flex gap-2 px-4 py-3 overflow-x-auto scrollbar-hide">
        {session.exercises.map((ex, i) => (
          <button
            key={ex.id}
            onClick={() => setCurrentExercise(i)}
            className={`flex-shrink-0 px-3 py-1.5 rounded-xl text-xs font-semibold transition-all ${
              i === currentExerciseIndex
                ? 'bg-indigo-600 text-white'
                : ex.completed
                ? 'bg-emerald-900/40 text-emerald-400 border border-emerald-800/50'
                : 'bg-slate-800 text-slate-400'
            }`}
          >
            {i + 1}
          </button>
        ))}
      </div>

      {/* Rest timer overlay */}
      {restTimer !== null && (
        <div className="mx-4 mb-3 bg-slate-900 border border-indigo-500/30 rounded-2xl p-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Timer size={18} className="text-indigo-400" />
              <span className="text-sm text-slate-400">Descanso</span>
            </div>
            <span className="text-3xl font-black text-white font-mono">
              {formatTime(restTimer)}
            </span>
          </div>
          <div className="flex items-center gap-2 mt-3">
            <button
              onClick={() => startRestTimer(Math.max(0, restTimer - 15), currentExercise.id)}
              className="flex-1 py-2 bg-slate-800 hover:bg-slate-700 rounded-xl text-sm text-slate-300 font-medium transition-colors"
            >
              −15s
            </button>
            <button
              onClick={stopRestTimer}
              className="flex-1 py-2 bg-slate-800 hover:bg-slate-700 rounded-xl text-sm text-slate-300 font-medium transition-colors flex items-center justify-center gap-1"
            >
              <SkipForward size={14} />
              Pular
            </button>
            <button
              onClick={() => startRestTimer(restTimer + 15, currentExercise.id)}
              className="flex-1 py-2 bg-slate-800 hover:bg-slate-700 rounded-xl text-sm text-slate-300 font-medium transition-colors"
            >
              +15s
            </button>
          </div>
        </div>
      )}

      {/* Main exercise content */}
      <div className="flex-1 overflow-y-auto px-4 pb-4">
        {/* Exercise header */}
        <div className="flex items-start justify-between mb-4">
          <div>
            <div className="flex items-center gap-2 mb-1">
              {currentExercise.completed && (
                <span className="inline-flex items-center gap-1 text-xs bg-emerald-900/50 text-emerald-400 px-2 py-0.5 rounded-full border border-emerald-800/50">
                  <Check size={10} />
                  Concluído
                </span>
              )}
              {currentExercise.substitutedFrom && (
                <span className="text-xs text-slate-500 bg-slate-800 px-2 py-0.5 rounded-full">
                  Substituído
                </span>
              )}
            </div>
            <h2 className="text-2xl font-black text-white leading-tight">
              {currentExercise.name}
            </h2>
            {dayExercise && (
              <p className="text-sm text-slate-500 mt-1">
                {dayExercise.sets} séries · {dayExercise.repMin}–{dayExercise.repMax} reps
                {dayExercise.restSeconds ? ` · ${dayExercise.restSeconds}s descanso` : ''}
              </p>
            )}
          </div>
          <button
            onClick={() => setShowSubstitute(true)}
            className="p-2 rounded-xl hover:bg-slate-800 text-slate-500 transition-colors"
          >
            <RefreshCw size={18} />
          </button>
        </div>

        {/* Sets */}
        <div className="space-y-3 mb-4">
          {currentExercise.sets.map((set, setIndex) => (
            <SetRow
              key={set.id}
              set={set}
              setIndex={setIndex}
              onComplete={() => handleCompleteSet(setIndex)}
              onUpdateWeight={(w) => updateSetRecord(currentExerciseIndex, setIndex, { weight: w })}
              onUpdateReps={(r) => updateSetRecord(currentExerciseIndex, setIndex, { reps: r })}
              onRemove={() => removeSet(currentExerciseIndex, setIndex)}
              canRemove={currentExercise.sets.length > 1}
            />
          ))}
        </div>

        {/* Add set */}
        <button
          onClick={() => addSet(currentExerciseIndex)}
          className="w-full py-3 border border-dashed border-slate-700 rounded-xl text-slate-500 hover:border-indigo-500 hover:text-indigo-400 transition-colors flex items-center justify-center gap-2 text-sm mb-4"
        >
          <Plus size={16} />
          Adicionar série
        </button>

        {/* Complete / Navigate */}
        {!currentExercise.completed ? (
          <Button
            variant="success"
            size="lg"
            fullWidth
            onClick={handleCompleteExercise}
            className="mb-3"
          >
            <span className="flex items-center justify-center gap-2">
              <Check size={18} />
              Concluir Exercício
            </span>
          </Button>
        ) : (
          <Button
            variant="ghost"
            size="md"
            fullWidth
            onClick={() => uncompleteExercise(currentExerciseIndex)}
            className="mb-3 border border-slate-700"
          >
            Editar exercício
          </Button>
        )}

        {/* Navigation arrows */}
        <div className="flex gap-3 mb-4">
          <button
            disabled={currentExerciseIndex === 0}
            onClick={() => setCurrentExercise(currentExerciseIndex - 1)}
            className="flex-1 py-3 bg-slate-900 border border-slate-800 rounded-xl text-slate-400 hover:bg-slate-800 disabled:opacity-30 disabled:cursor-not-allowed transition-all flex items-center justify-center gap-1 text-sm"
          >
            <ChevronLeft size={16} />
            Anterior
          </button>
          <button
            disabled={currentExerciseIndex === totalExercises - 1}
            onClick={() => setCurrentExercise(currentExerciseIndex + 1)}
            className="flex-1 py-3 bg-slate-900 border border-slate-800 rounded-xl text-slate-400 hover:bg-slate-800 disabled:opacity-30 disabled:cursor-not-allowed transition-all flex items-center justify-center gap-1 text-sm"
          >
            Próximo
            <ChevronRight size={16} />
          </button>
        </div>

        {/* Finish button */}
        {allDone && (
          <Button
            variant="primary"
            size="xl"
            fullWidth
            onClick={handleFinish}
            className="animate-pulse-scale"
          >
            <span className="flex items-center justify-center gap-2">
              <Trophy size={20} />
              Finalizar Treino
            </span>
          </Button>
        )}
      </div>

      {/* Substitute modal */}
      {showSubstitute && (
        <ExerciseSubstitute
          exerciseId={currentExercise.exerciseId}
          exerciseName={currentExercise.name}
          exerciseIndex={currentExerciseIndex}
          onClose={() => setShowSubstitute(false)}
        />
      )}

      {/* Abort confirm */}
      {showAbortConfirm && (
        <div className="fixed inset-0 bg-black/70 z-50 flex items-end justify-center p-4 animate-fade-in">
          <div className="bg-slate-900 border border-slate-700 rounded-2xl p-6 w-full max-w-sm">
            <h3 className="text-lg font-bold text-white mb-2">Abandonar treino?</h3>
            <p className="text-slate-400 text-sm mb-5">
              O progresso deste treino não será salvo.
            </p>
            <div className="flex gap-3">
              <Button
                variant="ghost"
                size="md"
                fullWidth
                onClick={() => setShowAbortConfirm(false)}
                className="border border-slate-700"
              >
                Continuar
              </Button>
              <Button
                variant="danger"
                size="md"
                fullWidth
                onClick={() => {
                  abandonSession();
                  setScreen('home');
                }}
              >
                Abandonar
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

interface SetRowProps {
  set: SetRecord;
  setIndex: number;
  onComplete: () => void;
  onUpdateWeight: (w: number) => void;
  onUpdateReps: (r: number) => void;
  onRemove: () => void;
  canRemove: boolean;
}

function SetRow({ set, setIndex, onComplete, onUpdateWeight, onUpdateReps, onRemove, canRemove }: SetRowProps) {
  return (
    <div
      className={`rounded-2xl border transition-all ${
        set.completed
          ? 'bg-emerald-950/30 border-emerald-800/40'
          : 'bg-slate-900 border-slate-800'
      }`}
    >
      <div className="flex items-center gap-3 p-3">
        {/* Set number */}
        <div
          className={`w-8 h-8 rounded-xl flex items-center justify-center text-sm font-bold flex-shrink-0 ${
            set.completed ? 'bg-emerald-600 text-white' : 'bg-slate-800 text-slate-400'
          }`}
        >
          {setIndex + 1}
        </div>

        {/* Weight */}
        <div className="flex-1">
          <p className="text-[10px] text-slate-500 mb-1">CARGA (kg)</p>
          <NumberInput
            value={set.weight}
            onChange={onUpdateWeight}
            step={2.5}
            min={0}
            max={500}
          />
        </div>

        {/* Reps */}
        <div className="flex-1">
          <p className="text-[10px] text-slate-500 mb-1">REPS</p>
          <NumberInput
            value={set.reps}
            onChange={onUpdateReps}
            step={1}
            min={1}
            max={100}
          />
        </div>

        {/* Complete button */}
        <button
          onClick={onComplete}
          className={`w-10 h-10 rounded-xl flex items-center justify-center transition-all flex-shrink-0 ${
            set.completed
              ? 'bg-emerald-600 text-white'
              : 'bg-slate-800 hover:bg-slate-700 text-slate-400 active:scale-95'
          }`}
        >
          <Check size={18} />
        </button>
      </div>

      {canRemove && (
        <button
          onClick={onRemove}
          className="w-full py-1.5 border-t border-slate-800/50 text-xs text-slate-600 hover:text-red-400 transition-colors flex items-center justify-center gap-1"
        >
          <Trash2 size={11} />
          Remover série
        </button>
      )}
    </div>
  );
}
