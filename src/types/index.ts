export interface Exercise {
  id: string;
  name: string;
  primaryMuscle: string;
  secondaryMuscles: string[];
  equipment: string;
  movementPattern: string;
  category: string;
  difficulty: 'beginner' | 'intermediate' | 'advanced';
  instructions?: string;
  isCustom?: boolean;
  createdAt?: number;
}

export interface WorkoutExercise {
  id: string;
  exerciseId: string;
  exercise?: Exercise;
  sets: number;
  repMin: number;
  repMax: number;
  restSeconds: number;
  initialWeight?: number;
  notes?: string;
  order: number;
}

export interface WorkoutDay {
  id: string;
  label: string; // A, B, C...
  name: string;  // Peito, Costas...
  exercises: WorkoutExercise[];
  estimatedMinutes?: number;
  order: number;
}

export interface WorkoutPlan {
  id: string;
  name: string;
  days: WorkoutDay[];
  currentDayIndex: number;
  createdAt: number;
  updatedAt: number;
  isActive: boolean;
}

export interface SetRecord {
  id: string;
  weight: number;
  reps: number;
  completed: boolean;
  notes?: string;
  timestamp?: number;
}

export interface SessionExercise {
  id: string;
  exerciseId: string;
  workoutExerciseId: string;
  name: string;
  sets: SetRecord[];
  completed: boolean;
  substitutedFrom?: string;
  order: number;
}

export interface WorkoutSession {
  id: string;
  workoutPlanId: string;
  workoutDayId: string;
  workoutDayLabel: string;
  workoutDayName: string;
  exercises: SessionExercise[];
  startTime: number;
  endTime?: number;
  duration?: number;
  completed: boolean;
  totalVolume?: number;
  totalSets?: number;
  prsAchieved?: string[];
}

export interface PersonalRecord {
  exerciseId: string;
  exerciseName: string;
  weight: number;
  reps: number;
  volume: number;
  date: number;
  sessionId: string;
}

export interface BodyMeasurement {
  id: string;
  date: number;
  weight?: number;
  chest?: number;
  waist?: number;
  hips?: number;
  leftArm?: number;
  rightArm?: number;
  leftThigh?: number;
  rightThigh?: number;
  leftCalf?: number;
  rightCalf?: number;
  custom?: Record<string, number>;
}

export interface Goal {
  id: string;
  type: 'workout_count' | 'weekly_frequency' | 'exercise_weight' | 'body_weight' | 'body_measurement' | 'volume';
  title: string;
  target: number;
  current: number;
  unit: string;
  exerciseId?: string;
  createdAt: number;
  completedAt?: number;
}

export interface Achievement {
  id: string;
  title: string;
  description: string;
  icon: string;
  unlockedAt?: number;
  condition: {
    type: string;
    value: number;
  };
}

export type ActiveScreen =
  | 'home'
  | 'workout-execution'
  | 'workout-plans'
  | 'plan-editor'
  | 'exercise-library'
  | 'history'
  | 'history-detail'
  | 'progress'
  | 'goals'
  | 'exercise-detail';
