/** Serialize StarIO10 open/print/close so kitchen KOTs cannot overlap on one printer. */
let tail: Promise<unknown> = Promise.resolve();

export function enqueueStarJob<T>(work: () => Promise<T>): Promise<T> {
  const run = tail.then(work, work);
  tail = run.then(
    () => undefined,
    () => undefined,
  );
  return run;
}
