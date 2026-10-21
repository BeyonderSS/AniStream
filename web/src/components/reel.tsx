const REEL = "M30.00,54.00 a24,24 0 1,0 48,0 a24,24 0 1,0 -48,0 Z M49.80,39.50 a4.2,4.2 0 1,0 8.4,0 a4.2,4.2 0 1,0 -8.4,0 Z M63.59,49.52 a4.2,4.2 0 1,0 8.4,0 a4.2,4.2 0 1,0 -8.4,0 Z M58.32,65.73 a4.2,4.2 0 1,0 8.4,0 a4.2,4.2 0 1,0 -8.4,0 Z M41.28,65.73 a4.2,4.2 0 1,0 8.4,0 a4.2,4.2 0 1,0 -8.4,0 Z M36.01,49.52 a4.2,4.2 0 1,0 8.4,0 a4.2,4.2 0 1,0 -8.4,0 Z M50.5,48.5 L60,54 L50.5,59.5 Z";
export const ReelMark = ({ className }: { className?: string }) => (
  <svg viewBox="30 30 48 48" className={className} aria-hidden><path fill="#F2B45A" fillRule="evenodd" d={REEL} /></svg>
);

