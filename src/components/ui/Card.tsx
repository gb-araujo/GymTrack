import type { ReactNode } from 'react';

interface CardProps {
  children: ReactNode;
  className?: string;
  onClick?: () => void;
  padding?: boolean;
}

export function Card({ children, className = '', onClick, padding = true }: CardProps) {
  const Tag = onClick ? 'button' : 'div';
  return (
    <Tag
      className={`
        bg-slate-900 border border-slate-800 rounded-2xl
        ${padding ? 'p-4' : ''}
        ${onClick ? 'cursor-pointer hover:bg-slate-800 active:scale-98 transition-all w-full text-left' : ''}
        ${className}
      `}
      onClick={onClick}
    >
      {children}
    </Tag>
  );
}
