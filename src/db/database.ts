import Dexie, { type Table } from 'dexie';
import type {
  Exercise,
  WorkoutPlan,
  WorkoutSession,
  PersonalRecord,
  BodyMeasurement,
  Goal,
  Achievement,
} from '../types';

export class TrackDatabase extends Dexie {
  exercises!: Table<Exercise>;
  workoutPlans!: Table<WorkoutPlan>;
  workoutSessions!: Table<WorkoutSession>;
  personalRecords!: Table<PersonalRecord>;
  bodyMeasurements!: Table<BodyMeasurement>;
  goals!: Table<Goal>;
  achievements!: Table<Achievement>;

  constructor() {
    super('TrackDB');

    this.version(1).stores({
      exercises: 'id, name, primaryMuscle, category, isCustom',
      workoutPlans: 'id, name, isActive, createdAt',
      workoutSessions: 'id, workoutPlanId, workoutDayId, startTime, completed',
      personalRecords: 'exerciseId, date',
      bodyMeasurements: 'id, date',
      goals: 'id, type, createdAt',
      achievements: 'id',
    });
  }
}

export const db = new TrackDatabase();
