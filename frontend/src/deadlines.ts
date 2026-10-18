export type DueState = 'OPEN' | 'DUE_SOON' | 'CLOSED';
export function dueState(dueAt: string, now = Date.now()): DueState {
  const remaining=Date.parse(dueAt)-now;
  return remaining<=0?'CLOSED':remaining<=86400000?'DUE_SOON':'OPEN';
}
export function dueLabel(state:DueState){return {OPEN:'进行中',DUE_SOON:'24 小时内截止',CLOSED:'已截止'}[state];}
