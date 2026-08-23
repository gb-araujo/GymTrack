import { Calendar, Clock, Dumbbell, Trophy, ChevronRight, Layers } from 'lucide-react';
import { useAppStore } from '../store/appStore';
import { Card } from '../components/ui/Card';
import { format } from 'date-fns';
import { ptBR } from 'date-fns/locale';

function formatDuration(min?: number) {
  if (!min) return '';
  return min >= 60 ? `${Math.floor(min / 60)}h ${min % 60}min` : `${min}min`;
}

export function History() {
  const { sessions, setScreen, setSelectedSession } = useAppStore();

  const grouped: Record<string, typeof sessions> = {};
  sessions.forEach(s => {
    const key = format(s.startTime, 'MMMM yyyy', { locale: ptBR });
    if (!grouped[key]) grouped[key] = [];
    grouped[key].push(s);
  });

  return (
    <div className="pb-28 px-4 pt-6 max-w-lg mx-auto animate-fade-in">
      <h1 className="text-2xl font-bold text-white mb-6">Histórico</h1>

      {sessions.length === 0 ? (
        <div className="text-center py-16">
          <Calendar size={48} className="text-slate-700 mx-auto mb-3" />
          <p className="text-slate-400">Nenhum treino registrado ainda</p>
          <p className="text-slate-600 text-sm mt-1">Complete seu primeiro treino para ver o histórico</p>
        </div>
      ) : (
        <div className="space-y-6">
          {Object.entries(grouped).map(([month, monthSessions]) => (
            <div key={month}>
              <h2 className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-3 capitalize">
                {month} · {monthSessions.length} treino{monthSessions.length !== 1 ? 's' : ''}
              </h2>
              <div className="space-y-2">
                {monthSessions.map(session => (
                  <Card
                    key={session.id}
                    onClick={() => {
                      setSelectedSession(session.id);
                      setScreen('history-detail');
                    }}
                  >
                    <div className="flex items-start justify-between">
                      <div className="flex-1">
                        <div className="flex items-center gap-2 mb-1">
                          <span className="text-lg font-black text-white">
                            {session.workoutDayLabel}
                          </span>
                          <span className="text-sm font-semibold text-slate-300">
                            — {session.workoutDayName}
                          </span>
                          {session.prsAchieved && session.prsAchieved.length > 0 && (
                            <Trophy size={13} className="text-yellow-400" />
                          )}
                        </div>
                        <div className="flex items-center gap-3 text-xs text-slate-500">
                          <span className="capitalize">
                            {format(session.startTime, "d MMM · HH:mm", { locale: ptBR })}
                          </span>
                          {session.duration && (
                            <span className="flex items-center gap-1">
                              <Clock size={11} />
                              {formatDuration(session.duration)}
                            </span>
                          )}
                          <span className="flex items-center gap-1">
                            <Dumbbell size={11} />
                            {session.exercises.length} ex.
                          </span>
                          {session.totalSets && (
                            <span className="flex items-center gap-1">
                              <Layers size={11} />
                              {session.totalSets} séries
                            </span>
                          )}
                        </div>
                        {session.totalVolume && session.totalVolume > 0 && (
                          <p className="text-xs text-slate-600 mt-1">
                            Volume: {session.totalVolume >= 1000
                              ? `${(session.totalVolume / 1000).toFixed(1)}t`
                              : `${session.totalVolume.toFixed(0)}kg`}
                          </p>
                        )}
                      </div>
                      <ChevronRight size={16} className="text-slate-700 mt-1" />
                    </div>
                  </Card>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
