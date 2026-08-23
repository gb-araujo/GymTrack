import { create } from 'zustand';
import { db } from '../db/database';
import { builtinExercises } from '../data/exercises';
import { samplePlan } from '../data/samplePlan';
import type {
  Exercise,
  WorkoutPlan,
  WorkoutSession,
  SessionExercise,
  SetRecord,
  PersonalRecord,
} from '../types';

interface ActiveSession {
  session: WorkoutSession;
  currentExerciseIndex: number;
  restTimer: number | null; // seconds remaining
  restTimerExerciseId?: string;
  startedAt: number;
}

interface AppState {
  // Data
  exercises: Exercise[];
  activePlan: WorkoutPlan | null;
  sessions: WorkoutSession[];
  personalRecords: Map<string, PersonalRecord>;
  activeSession: ActiveSession | null;
  totalWorkouts: number;

  // UI
  currentScreen: string;
  previousScreen: string;
  selectedSessionId: string | null;
  selectedExerciseId: string | null;

  // Actions
  initialize: () => Promise<void>;
  setScreen: (screen: string) => void;

  // Plan actions
  savePlan: (plan: WorkoutPlan) => Promise<void>;
  setActivePlan: (planId: string) => Promise<void>;
  deletePlan: (planId: string) => Promise<void>;
  getAllPlans: () => Promise<WorkoutPlan[]>;

  // Session actions
  startSession: () => Promise<string | null>;
  updateSetRecord: (exerciseIndex: number, setIndex: number, data: Partial<SetRecord>) => void;
  addSet: (exerciseIndex: number) => void;
  removeSet: (exerciseIndex: number, setIndex: number) => void;
  completeExercise: (exerciseIndex: number) => void;
  uncompleteExercise: (exerciseIndex: number) => void;
  completeSession: () => Promise<void>;
  abandonSession: () => void;
  setCurrentExercise: (index: number) => void;
  substituteExercise: (exerciseIndex: number, newExerciseId: string) => void;

  // Rest timer
  startRestTimer: (seconds: number, exerciseId: string) => void;
  stopRestTimer: () => void;
  tickRestTimer: () => void;

  // Exercise actions
  saveCustomExercise: (exercise: Exercise) => Promise<void>;
  deleteCustomExercise: (id: string) => Promise<void>;

  // Misc
  setSelectedSession: (id: string | null) => void;
  setSelectedExercise: (id: string | null) => void;
  getExerciseById: (id: string) => Exercise | undefined;
  getExerciseHistory: (exerciseId: string) => Promise<{ date: number; sets: SetRecord[]; sessionId: string }[]>;
}

export const useAppStore = create<AppState>((set, get) => ({
  exercises: [],
  activePlan: null,
  sessions: [],
  personalRecords: new Map(),
  activeSession: null,
  totalWorkouts: 0,
  currentScreen: 'home',
  previousScreen: 'home',
  selectedSessionId: null,
  selectedExerciseId: null,

  initialize: async () => {
    // Seed exercises if empty
    const count = await db.exercises.count();
    if (count === 0) {
      await db.exercises.bulkAdd(builtinExercises);
    }

    // Seed sample plan if no plans exist
    const planCount = await db.workoutPlans.count();
    if (planCount === 0) {
      await db.workoutPlans.add(samplePlan);
    }

    // Load data
    const exercises = await db.exercises.toArray();
    const plans = await db.workoutPlans.where('isActive').equals(1).toArray();
    const activePlan = plans[0] || (await db.workoutPlans.toArray())[0] || null;
    const sessions = await db.workoutSessions
      .where('completed')
      .equals(1)
      .reverse()
      .sortBy('startTime');

    const prs = await db.personalRecords.toArray();
    const prMap = new Map<string, PersonalRecord>();
    prs.forEach(pr => {
      const existing = prMap.get(pr.exerciseId);
      if (!existing || pr.weight > existing.weight || (pr.weight === existing.weight && pr.reps > existing.reps)) {
        prMap.set(pr.exerciseId, pr);
      }
    });

    set({
      exercises,
      activePlan,
      sessions,
      personalRecords: prMap,
      totalWorkouts: sessions.length,
    });
  },

  setScreen: (screen) => {
    set(state => ({ previousScreen: state.currentScreen, currentScreen: screen }));
  },

  savePlan: async (plan) => {
    await db.workoutPlans.put({ ...plan, updatedAt: Date.now() });
    const plans = await db.workoutPlans.where('isActive').equals(1).toArray();
    const activePlan = plans[0] || null;
    set({ activePlan });
  },

  setActivePlan: async (planId) => {
    await db.workoutPlans.toCollection().modify({ isActive: false });
    await db.workoutPlans.update(planId, { isActive: true });
    const plan = await db.workoutPlans.get(planId);
    set({ activePlan: plan || null });
  },

  deletePlan: async (planId) => {
    await db.workoutPlans.delete(planId);
    const plans = await db.workoutPlans.toArray();
    const active = plans.find(p => p.isActive) || plans[0] || null;
    set({ activePlan: active });
  },

  getAllPlans: async () => {
    return await db.workoutPlans.toArray();
  },

  startSession: async () => {
    const { activePlan, exercises } = get();
    if (!activePlan) return null;

    const dayIndex = activePlan.currentDayIndex % activePlan.days.length;
    const day = activePlan.days[dayIndex];
    if (!day) return null;

    const sessionExercises: SessionExercise[] = day.exercises
      .sort((a, b) => a.order - b.order)
      .map(we => {
        const exercise = exercises.find(e => e.id === we.exerciseId);
        const sets: SetRecord[] = Array.from({ length: we.sets }, (_, i) => ({
          id: `set_${Date.now()}_${i}`,
          weight: we.initialWeight ?? 0,
          reps: we.repMax,
          completed: false,
        }));
        return {
          id: `se_${Date.now()}_${we.id}`,
          exerciseId: we.exerciseId,
          workoutExerciseId: we.id,
          name: exercise?.name ?? we.exerciseId,
          sets,
          completed: false,
          order: we.order,
        };
      });

    const sessionId = `session_${Date.now()}`;
    const session: WorkoutSession = {
      id: sessionId,
      workoutPlanId: activePlan.id,
      workoutDayId: day.id,
      workoutDayLabel: day.label,
      workoutDayName: day.name,
      exercises: sessionExercises,
      startTime: Date.now(),
      completed: false,
    };

    await db.workoutSessions.add(session);

    set({
      activeSession: {
        session,
        currentExerciseIndex: 0,
        restTimer: null,
        startedAt: Date.now(),
      },
    });

    return sessionId;
  },

  updateSetRecord: (exerciseIndex, setIndex, data) => {
    set(state => {
      if (!state.activeSession) return state;
      const session = { ...state.activeSession.session };
      const exercises = [...session.exercises];
      const exercise = { ...exercises[exerciseIndex] };
      const sets = [...exercise.sets];
      sets[setIndex] = { ...sets[setIndex], ...data };
      exercise.sets = sets;
      exercises[exerciseIndex] = exercise;
      session.exercises = exercises;
      return {
        activeSession: { ...state.activeSession, session },
      };
    });
  },

  addSet: (exerciseIndex) => {
    set(state => {
      if (!state.activeSession) return state;
      const session = { ...state.activeSession.session };
      const exercises = [...session.exercises];
      const exercise = { ...exercises[exerciseIndex] };
      const lastSet = exercise.sets[exercise.sets.length - 1];
      const newSet: SetRecord = {
        id: `set_${Date.now()}`,
        weight: lastSet?.weight ?? 0,
        reps: lastSet?.reps ?? 10,
        completed: false,
      };
      exercise.sets = [...exercise.sets, newSet];
      exercises[exerciseIndex] = exercise;
      session.exercises = exercises;
      return { activeSession: { ...state.activeSession, session } };
    });
  },

  removeSet: (exerciseIndex, setIndex) => {
    set(state => {
      if (!state.activeSession) return state;
      const session = { ...state.activeSession.session };
      const exercises = [...session.exercises];
      const exercise = { ...exercises[exerciseIndex] };
      exercise.sets = exercise.sets.filter((_, i) => i !== setIndex);
      exercises[exerciseIndex] = exercise;
      session.exercises = exercises;
      return { activeSession: { ...state.activeSession, session } };
    });
  },

  completeExercise: (exerciseIndex) => {
    set(state => {
      if (!state.activeSession) return state;
      const session = { ...state.activeSession.session };
      const exercises = [...session.exercises];
      const exercise = { ...exercises[exerciseIndex], completed: true };
      // Mark all sets as completed that haven't been
      exercise.sets = exercise.sets.map(s => ({ ...s, completed: true }));
      exercises[exerciseIndex] = exercise;
      session.exercises = exercises;
      return { activeSession: { ...state.activeSession, session } };
    });
  },

  uncompleteExercise: (exerciseIndex) => {
    set(state => {
      if (!state.activeSession) return state;
      const session = { ...state.activeSession.session };
      const exercises = [...session.exercises];
      exercises[exerciseIndex] = { ...exercises[exerciseIndex], completed: false };
      session.exercises = exercises;
      return { activeSession: { ...state.activeSession, session } };
    });
  },

  completeSession: async () => {
    const { activeSession, activePlan } = get();
    if (!activeSession || !activePlan) return;

    const endTime = Date.now();
    const duration = Math.round((endTime - activeSession.session.startTime) / 1000 / 60);

    let totalVolume = 0;
    let totalSets = 0;
    const prsAchieved: string[] = [];

    for (const ex of activeSession.session.exercises) {
      for (const s of ex.sets) {
        if (s.completed) {
          totalVolume += s.weight * s.reps;
          totalSets++;
        }
      }

      // Check PRs
      const completedSets = ex.sets.filter(s => s.completed);
      if (completedSets.length > 0) {
        const maxWeight = Math.max(...completedSets.map(s => s.weight));
        const maxReps = Math.max(...completedSets.filter(s => s.weight === maxWeight).map(s => s.reps));

        const existing = get().personalRecords.get(ex.exerciseId);
        const isPR = !existing || maxWeight > existing.weight || (maxWeight === existing.weight && maxReps > existing.reps);

        if (isPR) {
          prsAchieved.push(ex.exerciseId);
          const newPR: PersonalRecord = {
            exerciseId: ex.exerciseId,
            exerciseName: ex.name,
            weight: maxWeight,
            reps: maxReps,
            volume: maxWeight * maxReps,
            date: endTime,
            sessionId: activeSession.session.id,
          };
          await db.personalRecords.put(newPR);
        }
      }
    }

    const completedSession: WorkoutSession = {
      ...activeSession.session,
      endTime,
      duration,
      completed: true,
      totalVolume,
      totalSets,
      prsAchieved,
    };

    await db.workoutSessions.put(completedSession);

    // Advance cycle
    const newDayIndex = (activePlan.currentDayIndex + 1) % activePlan.days.length;
    await db.workoutPlans.update(activePlan.id, { currentDayIndex: newDayIndex });

    const updatedPlan = { ...activePlan, currentDayIndex: newDayIndex };
    const updatedSessions = [completedSession, ...get().sessions];

    // Update PR map
    const newPrMap = new Map(get().personalRecords);
    for (const id of prsAchieved) {
      const ex = completedSession.exercises.find(e => e.exerciseId === id);
      if (ex) {
        const completedSets = ex.sets.filter(s => s.completed);
        const maxWeight = Math.max(...completedSets.map(s => s.weight));
        const maxReps = Math.max(...completedSets.filter(s => s.weight === maxWeight).map(s => s.reps));
        newPrMap.set(id, {
          exerciseId: id,
          exerciseName: ex.name,
          weight: maxWeight,
          reps: maxReps,
          volume: maxWeight * maxReps,
          date: endTime,
          sessionId: completedSession.id,
        });
      }
    }

    set({
      activeSession: { ...activeSession, session: completedSession },
      activePlan: updatedPlan,
      sessions: updatedSessions,
      totalWorkouts: updatedSessions.length,
      personalRecords: newPrMap,
    });
  },

  abandonSession: () => {
    set({ activeSession: null });
  },

  setCurrentExercise: (index) => {
    set(state => ({
      activeSession: state.activeSession
        ? { ...state.activeSession, currentExerciseIndex: index }
        : null,
    }));
  },

  substituteExercise: (exerciseIndex, newExerciseId) => {
    const { exercises } = get();
    const newExercise = exercises.find(e => e.id === newExerciseId);
    if (!newExercise) return;

    set(state => {
      if (!state.activeSession) return state;
      const session = { ...state.activeSession.session };
      const exs = [...session.exercises];
      const old = exs[exerciseIndex];
      exs[exerciseIndex] = {
        ...old,
        exerciseId: newExerciseId,
        name: newExercise.name,
        substitutedFrom: old.substitutedFrom ?? old.exerciseId,
      };
      session.exercises = exs;
      return { activeSession: { ...state.activeSession, session } };
    });
  },

  startRestTimer: (seconds, exerciseId) => {
    set(state => ({
      activeSession: state.activeSession
        ? { ...state.activeSession, restTimer: seconds, restTimerExerciseId: exerciseId }
        : null,
    }));
  },

  stopRestTimer: () => {
    set(state => ({
      activeSession: state.activeSession
        ? { ...state.activeSession, restTimer: null, restTimerExerciseId: undefined }
        : null,
    }));
  },

  tickRestTimer: () => {
    set(state => {
      if (!state.activeSession || state.activeSession.restTimer === null) return state;
      const newTime = state.activeSession.restTimer - 1;
      if (newTime <= 0) {
        return {
          activeSession: { ...state.activeSession, restTimer: null, restTimerExerciseId: undefined },
        };
      }
      return {
        activeSession: { ...state.activeSession, restTimer: newTime },
      };
    });
  },

  saveCustomExercise: async (exercise) => {
    const ex = { ...exercise, isCustom: true, createdAt: Date.now() };
    await db.exercises.put(ex);
    const exercises = await db.exercises.toArray();
    set({ exercises });
  },

  deleteCustomExercise: async (id) => {
    await db.exercises.delete(id);
    const exercises = await db.exercises.toArray();
    set({ exercises });
  },

  setSelectedSession: (id) => set({ selectedSessionId: id }),
  setSelectedExercise: (id) => set({ selectedExerciseId: id }),

  getExerciseById: (id) => {
    return get().exercises.find(e => e.id === id);
  },

  getExerciseHistory: async (exerciseId) => {
    const allSessions = await db.workoutSessions.where('completed').equals(1).toArray();
    const history: { date: number; sets: SetRecord[]; sessionId: string }[] = [];
    for (const session of allSessions) {
      const ex = session.exercises.find(e => e.exerciseId === exerciseId);
      if (ex) {
        history.push({ date: session.startTime, sets: ex.sets, sessionId: session.id });
      }
    }
    return history.sort((a, b) => b.date - a.date);
  },
}));
