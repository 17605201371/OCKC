export type ReviewQuery={q:string;assignmentId:string;authorId:string;state:string;drafts:string;evidence:string;versions:string;sort:string;page:number};
// 默认只列每篇作品的最新版本：历史版本已经不能评分，留在队列里只会让人重复打开同一篇。
export function defaultReviewQuery():ReviewQuery{return {q:'',assignmentId:'0',authorId:'0',state:'ALL',drafts:'ALL',evidence:'ALL',versions:'LATEST',sort:'oldest',page:1};}
export function reviewParams(value:ReviewQuery,next=false):URLSearchParams{const p=new URLSearchParams();for(const key of ['q','assignmentId','authorId','state','drafts','evidence','versions','sort'] as const)p.set(key,value[key]);if(!next)p.set('page',String(value.page));return p;}

export function reviewAdvanceBlocked(state:{loaded:boolean;loading:boolean;stale:unknown;backup:unknown;conflict:boolean},teacher:boolean):boolean{return teacher&&(!state.loaded||state.loading||!!state.stale||!!state.backup||state.conflict);}
