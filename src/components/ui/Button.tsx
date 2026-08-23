import type { ButtonHTMLAttributes, ReactNode } from 'react';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger' | 'success';
  size?: 'sm' | 'md' | 'lg' | 'xl';
  fullWidth?: boolean;
  children: ReactNode;
}

const variants = {
  primary: 'bg-indigo-600 hover:bg-indigo-500 active:bg-indigo-700 text-white',
  secondary: 'bg-slate-700 hover:bg-slate-600 active:bg-slate-800 text-white',
  ghost: 'bg-transparent hover:bg-slate-800 active:bg-slate-700 text-slate-300',
  danger: 'bg-red-600 hover:bg-red-500 active:bg-red-700 text-white',
  success: 'bg-emerald-600 hover:bg-emerald-500 active:bg-emerald-700 text-white',
};

const sizes = {
  sm: 'px-3 py-1.5 text-sm rounded-lg',
  md: 'px-4 py-2.5 text-sm rounded-xl',
  lg: 'px-6 py-3.5 text-base rounded-xl',
  xl: 'px-6 py-4 text-lg rounded-2xl',
};

export function Button({
  variant = 'primary',
  size = 'md',
  fullWidth = false,
  children,
  className = '',
  disabled,
  ...props
}: ButtonProps) {
  return (
    <button
      className={`
        font-semibold transition-all duration-150 active:scale-95
        ${variants[variant]}
        ${sizes[size]}
        ${fullWidth ? 'w-full' : ''}
        ${disabled ? 'opacity-40 cursor-not-allowed' : 'cursor-pointer'}
        ${className}
      `}
      disabled={disabled}
      {...props}
    >
      {children}
    </button>
  );
}
