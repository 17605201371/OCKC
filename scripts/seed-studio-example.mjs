/** Creates only an explicitly labelled demo assignment in a demo course, through real APIs. */
import { createRequire } from 'node:module';
import fs from 'node:fs';
const { request, chromium } = createRequire(new URL('../frontend/package.json', import.meta.url))('playwright');
const base = process.env.DEMO_BASE_URL, password = process.env.E2E_PASSWORD;
if (!base || !password || process.env.ALLOW_DEMO_WRITE !== 'true') throw Error('Set DEMO_BASE_URL, E2E_PASSWORD and ALLOW_DEMO_WRITE=true. Demo databases only.');
const out = decodeURIComponent(new URL('../.qa/studio',import.meta.url).pathname);fs.mkdirSync(out,{recursive:true});
const browser = await chromium.launch({headless:true,args:['--no-sandbox'],...(process.env.CHROMIUM_PATH ? {executablePath:process.env.CHROMIUM_PATH} : {})});
const image = await browser.newPage({viewport:{width:1280,height:610}});await image.goto(new URL('./fixtures/photosynthesis.svg',import.meta.url).href);await image.screenshot({path:out+'/diagram.png'});await browser.close();
async function client(email) {
 const ctx=await request.newContext({baseURL:base});let token=await(await ctx.get('/api/auth/csrf')).json();
 const login=await ctx.post('/api/auth/login',{form:{username:email,password},headers:{[token.headerName]:token.token}});if(!login.ok())throw Error('Demo sign-in failed');
 token=await(await ctx.get('/api/auth/csrf')).json();
 return { ctx, token, async call(path,method='GET',data,key){const r=await ctx.fetch('/api'+path,{method,headers:{[token.headerName]:token.token,...(key?{'Idempotency-Key':key}:{})},...(data===undefined?{}:{data})});const body=await r.json();if(!r.ok())throw Error(path+': '+body.message);return body;} };
}
const teacher=await client('teacher@example.test'), student=await client('student@example.test');
try {
 const courses=await teacher.call('/courses'),course=courses.find(c=>c.title==='教学设计 · 演示课程');if(!course)throw Error('Default demo course not found; refusing writes');
 const title='新版图文编辑体验 · 演示作业';let assignment=(await teacher.call(`/courses/${course.id}/assignments`)).find(a=>a.title===title);
 if(!assignment)assignment=await teacher.call(`/courses/${course.id}/assignments`,'POST',{title,requirements:'功能演示：体验图文、表格、公式与证据关联。示例图为平台自绘，分数与批注均为演示记录，不代表真实学习评价。正式写作请替换为本人内容并补充可核验来源。',dueAt:new Date(Date.now()+30*86400000).toISOString()});
 let e=await student.call(`/assignments/${assignment.id}/entries`,'POST');
 if(e.versions.length){console.log('Demo already exists; existing draft left unchanged.');process.exitCode=0;}
 else {
  const r=await student.ctx.post(`/api/entries/${e.id}/media`,{headers:{[student.token.headerName]:student.token.token},multipart:{file:{name:'photosynthesis.png',mimeType:'image/png',buffer:fs.readFileSync(out+'/diagram.png')}}});if(!r.ok())throw Error('Demo image upload failed');const media=await r.json();
  const text=(value)=>({type:'text',text:value}),p=(id,value)=>({type:'paragraph',attrs:{blockId:id},...(value?{content:[text(value)]}:{})}),h=(id,value)=>({type:'heading',attrs:{blockId:id,level:2},content:[text(value)]});
  const cell=(id,value,header=false)=>({type:header?'tableHeader':'tableCell',attrs:{colspan:1,rowspan:1,colwidth:null},content:[p(id,value)]});
  const content={type:'doc',content:[
   h('studio-light','一、光反应发生在类囊体薄膜上'),
   p('studio-process','光反应是光合作用的第一阶段，发生在叶绿体的类囊体薄膜上。色素分子吸收光能后，水被光解，氧气释放；电子经传递最终参与 NADP⁺ 的还原，生成 NADPH，同时伴随 ATP 的合成。'),
   {type:'figure',attrs:{blockId:'studio-figure',src:media.src,alt:'光反应与卡尔文循环之间的物质和能量联系',caption:'图 1　光反应提供 ATP 与 NADPH；ADP、Pi 和 NADP⁺ 返回并再利用。',source:'平台自绘功能演示素材；正式知识帖请补充可核验的教材、文献或原图出处。'}},
   h('studio-cycle','二、把两个阶段放回同一条物质链'),
   p('studio-coupling','卡尔文循环在叶绿体基质中进行，消耗 ATP 和 NADPH，参与二氧化碳固定后的还原及受体再生等过程。ATP 被利用后形成 ADP 和无机磷酸，NADPH 被氧化后形成 NADP⁺，这些物质可以再参与光反应。'),
   {type:'table',content:[{type:'tableRow',content:[cell('st-h1','光反应产物',true),cell('st-h2','使用后的物质',true),cell('st-h3','在后续过程中的作用',true)]},{type:'tableRow',content:[cell('st-c1','ATP'),cell('st-c2','ADP + Pi'),cell('st-c3','供能，参与还原与受体再生')]},{type:'tableRow',content:[cell('st-c4','NADPH'),cell('st-c5','NADP⁺'),cell('st-c6','提供还原力，参与碳化合物还原')]}]},
   {type:'mathBlock',attrs:{blockId:'studio-math',latex:'\\mathrm{ATP} + \\mathrm{H_2O} \\rightarrow \\mathrm{ADP} + \\mathrm{P_i}'}},
   h('studio-boundary','三、物质再利用，不等于能量无限循环'),
   {type:'callout',content:[p('studio-note','关键区分：物质可以在两个阶段之间再利用；能量需要由光持续输入，并在转化中以热等形式散失。不能将两阶段的物质联系描述为能量的无限循环。')]},
   p('studio-source','演示提示：以上内容用于展示排版和交互，不替代教材。正式提交时请为关键论断补充原文摘录与可核验来源，并区分事实与自己的解释。'),p('studio-end','')
  ]};
  const evidence=[{paragraphId:'studio-figure',sourceTitle:'平台自绘示意图 · 功能演示素材',sourceUrl:'',excerpt:'图示用两个方向的箭头，分别表达 ATP/NADPH 的使用与 ADP、Pi、NADP⁺ 的返回。',claim:'作者使用自绘图说明两个阶段之间的物质联系；本记录不作为权威学科证据。'}];
  e=await student.call(`/entries/${e.id}/draft`,'PUT',{title:'光合作用的光反应：光能如何转化为化学能',content,evidence,revision:e.revision});
  const v=await student.call(`/entries/${e.id}/submissions`,'POST',{revision:e.revision},'studio-example-version-one');
  const frozen=await teacher.call(`/submissions/${v.id}`);for(const ev of frozen.evidence)await teacher.call(`/evidence/${ev.id}/verifications`,'POST',{verdict:'PASS',reason:'功能演示：已核对自绘图与示例图注相符，不代表对权威教材或学科事实的真实性审定。'});
  const scores={S:77,A:90,C:62,L:75};const items=['S1','S2','S3','A1','A2','A3','C1','C2','L1','L2','L3'].map(code=>({code,score:scores[code[0]],status:'SCORED',reason:''}));
  await teacher.call(`/submissions/${v.id}/evaluations`,'POST',{items,gateReason:'',summary:'功能演示评分，非真实学习评价。正式知识帖应补充可核验来源，并说明图示如何支持正文论断。',expectedRevision:0});
  await teacher.call(`/submissions/${v.id}/feedback`,'POST',{paragraphId:'studio-figure',indicator:'S3',body:'演示批注：请为图示补充实际教材版本与页码，并说明它支持正文哪一个论断。'});
  await student.call(`/submissions/${v.id}/publication`,'POST');const pub=(await teacher.call(`/courses/${course.id}/publications?pending=true`)).find(p=>p.submission_id===v.id);await teacher.call(`/publications/${pub.id}/actions`,'POST',{action:'APPROVE',body:''});
  console.log('Created labelled demo assignment, image, immutable version, demonstration evaluation and published course card.');
 }
} finally {await teacher.ctx.dispose();await student.ctx.dispose();}
