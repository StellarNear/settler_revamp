export function formatElapsed(milliseconds: number | null) {
  if (milliseconds === null) return "Unknown";
  if (milliseconds < 1_000) return `${milliseconds} ms`;
  if (milliseconds < 60_000)
    return `${Number((milliseconds / 1_000).toFixed(1))} s`;

  const totalSeconds = Math.floor(milliseconds / 1_000);
  const days = Math.floor(totalSeconds / 86_400);
  const hours = Math.floor((totalSeconds % 86_400) / 3_600);
  const minutes = Math.floor((totalSeconds % 3_600) / 60);
  const seconds = totalSeconds % 60;
  if (days) return `${days} d ${hours} h ${minutes} min ${seconds} s`;
  if (hours) return `${hours} h ${minutes} min ${seconds} s`;
  return `${minutes} min ${seconds} s`;
}
