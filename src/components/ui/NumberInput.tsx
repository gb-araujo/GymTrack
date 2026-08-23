import { useState } from 'react';

interface NumberInputProps {
  value: number;
  onChange: (value: number) => void;
  min?: number;
  max?: number;
  step?: number;
  suffix?: string;
  className?: string;
  big?: boolean;
}

export function NumberInput({
  value,
  onChange,
  min = 0,
  max = 9999,
  step = 1,
  suffix,
  className = '',
  big = false,
}: NumberInputProps) {
  const [focused, setFocused] = useState(false);

  const adjust = (delta: number) => {
    const newVal = Math.min(max, Math.max(min, value + delta));
    onChange(newVal);
  };

  const textSize = big ? 'text-3xl' : 'text-xl';

  return (
    <div className={`flex items-center gap-1 ${className}`}>
      <button
        onPointerDown={() => adjust(-step)}
        className="w-9 h-9 rounded-xl bg-slate-700 hover:bg-slate-600 active:bg-slate-800 active:scale-95 text-slate-300 font-bold text-lg transition-all flex items-center justify-center"
      >
        −
      </button>
      <div className="relative flex items-center justify-center">
        <input
          type="number"
          value={focused ? undefined : value}
          defaultValue={value}
          onFocus={() => setFocused(true)}
          onBlur={(e) => {
            setFocused(false);
            const v = parseFloat(e.target.value);
            if (!isNaN(v)) onChange(Math.min(max, Math.max(min, v)));
          }}
          onChange={(e) => {
            const v = parseFloat(e.target.value);
            if (!isNaN(v)) onChange(Math.min(max, Math.max(min, v)));
          }}
          className={`${textSize} font-bold text-white text-center bg-slate-800 rounded-xl border border-slate-700 focus:border-indigo-500 outline-none transition-colors w-20 py-1.5`}
          step={step}
          min={min}
          max={max}
        />
        {suffix && (
          <span className="absolute right-2 text-slate-500 text-xs">{suffix}</span>
        )}
      </div>
      <button
        onPointerDown={() => adjust(step)}
        className="w-9 h-9 rounded-xl bg-slate-700 hover:bg-slate-600 active:bg-slate-800 active:scale-95 text-slate-300 font-bold text-lg transition-all flex items-center justify-center"
      >
        +
      </button>
    </div>
  );
}
