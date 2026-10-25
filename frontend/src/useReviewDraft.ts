import { reactive, watch, nextTick, onBeforeUnmount } from 'vue';
import { api, ApiFailure } from './api';
export function useReviewDraft(snapshot: () => any, apply: (data: any) => void, active: () => boolean, userId: () => number) {
  const state = reactive({ loading:false, loaded:false, dirty:false, saving:false, conflict:false, error:'', savedAt:'', revision:0, baseRevision:0, stale:null as any, backup:null as any });
  let sid=0, baseline='', timer:ReturnType<typeof setTimeout>|undefined, flight:Promise<boolean>|null=null;
  const serialize=()=>JSON.stringify(snapshot());
  const key=()=>`ockc-review-backup-${userId()}-${sid}`;
  function localBackup() { try{localStorage.setItem(key(),JSON.stringify({snapshot:snapshot(),baseRevision:state.baseRevision,savedAt:new Date().toISOString()}));}catch{} }
  function clearBackup() { try{localStorage.removeItem(key());}catch{} }
  function changed() {
    if(!active()||!state.loaded||state.loading)return;
    state.dirty=serialize()!==baseline;
    if(state.dirty){localBackup();clearTimeout(timer);if(!state.conflict&&!state.stale&&!state.backup)timer=setTimeout(save,1200);}
  }
  watch(serialize,changed);
  async function load(id:number) {
    clearTimeout(timer);sid=id;Object.assign(state,{loading:true,loaded:false,dirty:false,saving:false,conflict:false,error:'',savedAt:'',revision:0,stale:null,backup:null});
    try {
      const data=await api(`/submissions/${id}/review-draft`);if(sid!==id)return;
      state.baseRevision=data.evaluationRevision;
      const d=data.draft;if(d){state.revision=d.revision;state.savedAt=d.updated_at;if(d.stale)state.stale=d;else apply({items:d.items,summary:d.summary,gateReason:d.gate_reason,stepScores:d.stepScores});}
      await nextTick();baseline=serialize();
      try{const local=JSON.parse(localStorage.getItem(key())||'null');if(local?.snapshot && JSON.stringify(local.snapshot)!==baseline)state.backup=local;else if(local)clearBackup();}catch{}
      state.loaded=true;
    }catch(e:any){state.error=e.message||'无法加载评分草稿';}finally{state.loading=false;}
  }
  async function save():Promise<boolean> {
    clearTimeout(timer);
    if(flight)return flight;
    if(!state.loaded||state.stale||state.backup||state.conflict)return false;
    if(!state.dirty)return true;
    const id=sid, sent=snapshot(), text=JSON.stringify(sent), revision=state.revision, baseEvaluationRevision=state.baseRevision;
    state.saving=true;state.error='';
    flight=(async()=>{
      try{
        const result=await api(`/submissions/${id}/review-draft`,'PUT',{...sent,revision,baseEvaluationRevision});
        if(sid!==id)return false;
        state.revision=result.draft.revision;state.savedAt=result.draft.updated_at;baseline=text;state.dirty=serialize()!==text;
        if(!state.dirty)clearBackup();else{localBackup();timer=setTimeout(save,1200);}return true;
      }catch(e:any){if(sid!==id)return false;state.error=e.message||'评分草稿保存失败';state.conflict=e instanceof ApiFailure&&e.status===409;localBackup();return false;}
      finally{state.saving=false;flight=null;}
    })();return flight;
  }
  async function flush(){for(let i=0;i<5&&(state.dirty||state.saving);i++){if(!await save())return false;}return !state.dirty&&!state.saving;}
  async function restore(data:any){
    const value=data.snapshot || {items:data.items,summary:data.summary,gateReason:data.gate_reason};
    state.stale=null;state.backup=null;state.error='';apply(value);await nextTick();changed();return save();
  }
  async function discardStale(){
    try{await api(`/submissions/${sid}/review-draft`,'DELETE',{revision:state.revision});state.revision=0;state.stale=null;state.savedAt='';state.error='';}
    catch(e:any){state.error=e.message;}
  }
  function discardBackup(){clearBackup();state.backup=null;}
  function published(){clearTimeout(timer);clearBackup();state.dirty=false;state.conflict=false;state.error='';state.loaded=false;}
  function suspend(){let recovery:any=null;if(state.dirty&&sid){const data={snapshot:snapshot(),baseRevision:state.baseRevision,savedAt:new Date().toISOString()};let persisted=false;try{const text=JSON.stringify(data);localStorage.setItem(key(),text);persisted=localStorage.getItem(key())===text;}catch{}recovery={submissionId:sid,data,persisted};}clearTimeout(timer);sid=0;state.loaded=false;state.dirty=false;return recovery;}
  onBeforeUnmount(()=>clearTimeout(timer));
  return {state,load,save,flush,restore,discardStale,discardBackup,published,suspend};
}
