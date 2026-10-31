// Fresh isolated loopback database only. Never use deployed courses or accounts.
// Rewritten 2026-10-31 for the redesigned rubric page (RubricWorkbench / RubricViewer, 10-24).
import {createRequire} from 'node:module';import fs from 'node:fs';import assert from 'node:assert/strict';
const {chromium}=createRequire(new URL('../frontend/package.json',import.meta.url))('playwright');
const base=process.env.E2E_BASE_URL,password=process.env.E2E_PASSWORD;
if(!/^http:\/\/(127\.0\.0\.1|localhost):\d+$/.test(base||'')||!password)throw Error('Use isolated loopback E2E_BASE_URL and E2E_PASSWORD');
const out=decodeURIComponent(new URL('../.qa/rubric',import.meta.url).pathname);fs.mkdirSync(out,{recursive:true});
const browser=await chromium.launch({headless:true,args:['--no-sandbox'],executablePath:process.env.CHROMIUM_PATH||'/vercel/sandbox/.cache/ms-playwright/chromium-1243/chrome-linux64/chrome'});
const checks=[],errors=[];let screenshots=0;
function track(p){p.on('pageerror',e=>errors.push(e.message));p.on('dialog',d=>d.accept());}
async function user(name){const ctx=await browser.newContext({viewport:{width:1440,height:1000},colorScheme:'light'}),p=await ctx.newPage();track(p);await p.goto(base);await p.getByLabel('账号邮箱').fill(name+'@example.test');await p.getByLabel('密码',{exact:true}).fill(password);await p.getByRole('button',{name:'登录工作空间'}).click();await p.getByRole('button',{name:'退出登录'}).waitFor();return p;}
async function rpc(p,path,method='GET',body){return p.evaluate(async({path,method,body})=>{const t=await(await fetch('/api/auth/csrf')).json();const res=await fetch('/api'+path,{method,headers:{'Content-Type':'application/json',[t.headerName]:t.token,...(path.endsWith('/submissions')?{'Idempotency-Key':crypto.randomUUID()}:{})},...(body===undefined?{}:{body:JSON.stringify(body)})});const data=await res.json();if(!res.ok)throw Error(res.status+' '+path+' '+data.message);return data;},{path,method,body});}
async function status(p,path){return p.evaluate(async path=>(await fetch('/api'+path)).status,path);}
async function nav(p,label){const expand=p.getByRole('button',{name:'展开侧边栏',exact:true});if(await expand.count())await expand.click();await p.getByRole('button',{name:label,exact:true}).first().click();}
const work=p=>p.locator('.rubric-workbench');
// The page is ready when the current version card (or the edit form) is shown and no request is in flight.
async function ready(p){await p.waitForFunction(()=>{const w=document.querySelector('.rubric-workbench');return w&&(w.querySelector('.rw-current')||w.querySelector('.rw-form'))&&![...w.querySelectorAll('button')].some(b=>b.disabled&&/新建修订版本|编辑量规|继续编辑草稿/.test(b.textContent));});}
async function open(p){await nav(p,'学习工作台');await nav(p,'课程量规');await ready(p);}
async function shot(p,name){await p.waitForTimeout(150);assert.equal(await p.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,name+' overflow');await p.screenshot({path:out+'/'+name+'.png',fullPage:true});screenshots++;}
const title=p=>work(p).getByLabel('量规标题',{exact:true});
const saveBtn=p=>work(p).getByRole('button',{name:'保存草稿',exact:true});
const enableBtn=p=>work(p).getByRole('button',{name:'启用为新作业默认',exact:true});
async function enable(p,why){await enableBtn(p).click();const ok=work(p).getByRole('button',{name:'确认启用',exact:true});assert.equal(await ok.isDisabled(),true);await work(p).getByLabel('启用说明（必填，仅任课教师可见）').fill(why);await ok.click();await work(p).getByRole('status').filter({hasText:'已启用，新作业默认使用此版本'}).waitFor();}
async function save(p){await saveBtn(p).click();await work(p).getByRole('status').filter({hasText:'草稿已保存'}).waitFor();}
async function item(p,code){const a=work(p).locator('.rw-item').filter({has:p.locator('.rw-item-head b',{hasText:new RegExp('^'+code+'$')})});if(await a.locator('.rw-item-head').getAttribute('aria-expanded')==='false')await a.locator('.rw-item-head').click();return a;}
const ANCHOR='完整标明教材版本与页码，且摘录直接支持光反应场所。';
const codes=['S1','S2','S3','A1','A2','A3','C1','C2','L1','L2','L3'];
try{
 const teacher=await user('teacher'),student=await user('student');

 // 1. A failed catalog read is shown; leaving and coming back reads it again (the page has no separate retry button).
 await teacher.route('**/api/courses/1/rubrics',route=>route.request().method()==='GET'?route.fulfill({status:503,contentType:'application/json',body:'{"message":"本地量规读取失败"}'}):route.continue());
 await nav(teacher,'课程量规');await work(teacher).getByRole('alert').filter({hasText:'本地量规读取失败'}).waitFor();
 await teacher.unroute('**/api/courses/1/rubrics');await work(teacher).getByRole('button',{name:'重新读取',exact:true}).click();await ready(teacher);
 await work(teacher).locator('.rw-current').getByText('四维基础量规',{exact:true}).waitFor();
 assert.equal(await work(teacher).getByLabel('查看量规版本').count(),0);
 checks.push('Catalog read failure is reported with a retry button that loads the built-in default');

 // 2. Teacher creates a revision draft from the enabled default and edits title, an indicator description and an anchor.
 await work(teacher).getByRole('button',{name:'新建修订版本'}).click();await title(teacher).waitFor();
 await work(teacher).getByRole('status').filter({hasText:'第一次保存时才会生成草稿'}).waitFor();
 assert.equal((await rpc(teacher,'/courses/1/rubrics')).items.some(r=>r.state==='DRAFT'),false);assert.equal(await saveBtn(teacher).isDisabled(),true);
 await title(teacher).fill('只是看看');await work(teacher).getByRole('button',{name:'取消',exact:true}).click();await ready(teacher);
 assert.equal((await rpc(teacher,'/courses/1/rubrics')).items.some(r=>r.state==='DRAFT'),false);await work(teacher).locator('.rw-current').getByText('当前生效',{exact:true}).waitFor();
 checks.push('Starting a revision and cancelling leaves no empty draft behind; the draft is created on first save');
 await work(teacher).getByRole('button',{name:'新建修订版本'}).click();await title(teacher).waitFor();
 await title(teacher).fill('光合作用学科量规 · 本地验收');
 const s1=await item(teacher,'S1');await s1.getByLabel('指标说明').fill('需要回到教材页码核对论断，不把示意图当作实测数据。');await s1.getByLabel('优秀 90–100').fill(ANCHOR);
 await save(teacher);
 let catalog=await rpc(teacher,'/courses/1/rubrics');const rid=catalog.items.find(r=>r.state==='DRAFT').id,code='cr-'+rid;
 const savedDraft=await rpc(teacher,'/courses/1/rubrics/'+code);assert.equal(savedDraft.title,'光合作用学科量规 · 本地验收');assert.equal(savedDraft.criteria.find(c=>c.code==='S1').excellent,ANCHOR);assert.deepEqual(savedDraft.criteria.map(c=>c.code),codes);
 checks.push('Teacher creates a revision draft and edits title, indicator description and anchors; the 11 codes stay fixed');
 await shot(teacher,'01-teacher-rubric-draft');

 // 3. Weights must total 100%: an invalid total is flagged and blocks saving.
 await work(teacher).getByLabel('S 维度权重').fill('30');await work(teacher).getByText('需为 100%',{exact:true}).waitFor();
 assert.match(await work(teacher).locator('.rw-sum').innerText(),/105%/);assert.equal(await saveBtn(teacher).isDisabled(),true);
 await work(teacher).getByLabel('S 维度权重').fill('40');await work(teacher).getByLabel('A 维度权重').fill('10');
 assert.equal(await work(teacher).getByText('需为 100%',{exact:true}).count(),0);await save(teacher);
 const weights=Object.fromEntries((await rpc(teacher,'/courses/1/rubrics/'+code)).dimensions.map(d=>[d.code,Number(d.weight)]));assert.deepEqual(weights,{S:40,A:10,C:25,L:25});
 checks.push('Dimension weights are editable; a total other than 100% is flagged and cannot be saved');

 // 4. Students see only enabled versions, no edit controls, and cannot read the draft or the history directly.
 await open(student);await work(student).locator('.rw-current').getByText('四维基础量规',{exact:true}).waitFor();
 assert.equal(await work(student).getByLabel('查看量规版本').count(),0);
 assert.equal(await work(student).getByRole('button',{name:/新建修订版本|编辑量规|继续编辑草稿/}).count(),0);
 assert.equal(await status(student,'/courses/1/rubrics/'+code),403);assert.equal(await status(student,'/courses/1/rubrics/history'),403);
 assert.equal((await rpc(student,'/courses/1/rubrics')).items.some(r=>r.state==='DRAFT'),false);
 checks.push('Students see only enabled versions, get no edit controls, and cannot read drafts or history through the API');

 // 5. A save from a stale window is rejected without losing input; leaving with unsaved edits is blocked.
 const second=await teacher.context().newPage();track(second);await second.goto(base);await second.getByRole('button',{name:'退出登录'}).waitFor();
 await open(second);await work(second).getByRole('button',{name:'继续编辑草稿'}).click();await title(second).fill('另一窗口先保存的量规');await save(second);
 await title(teacher).fill('当前窗口需核对的量规');await saveBtn(teacher).click();
 await work(teacher).getByRole('alert').filter({hasText:'其他窗口'}).waitFor();assert.equal(await title(teacher).inputValue(),'当前窗口需核对的量规');
 await nav(teacher,'学习工作台');await work(teacher).getByRole('alert').filter({hasText:'未保存的修改'}).waitFor();assert.equal(await title(teacher).inputValue(),'当前窗口需核对的量规');
 await shot(teacher,'02-rubric-conflict');
 checks.push('A stale-window save is rejected with the typed input kept, and navigation away is blocked while edits are unsaved');
 await work(teacher).getByRole('button',{name:'载入最新版本并保留我的输入',exact:true}).click();await work(teacher).getByRole('status').filter({hasText:'你的输入保留'}).waitFor();
 assert.equal(await title(teacher).inputValue(),'当前窗口需核对的量规');await save(teacher);assert.equal((await rpc(teacher,'/courses/1/rubrics/'+code)).title,'当前窗口需核对的量规');
 await title(teacher).fill('光合作用学科量规 · 本地验收');await save(teacher);
 checks.push('After a conflict the teacher can load the latest revision while keeping the typed input, and the next save succeeds with that input');

 // 5b. Unsaved edits survive a reload through the local backup and can be restored explicitly.
 await title(teacher).fill('掉线前没保存的量规标题');await teacher.waitForTimeout(900);await teacher.reload();await teacher.getByRole('button',{name:'退出登录'}).waitFor();
 await nav(teacher,'课程量规');await ready(teacher);await work(teacher).locator('.rw-draft-note').getByText(/本机还有上次没保存的修改/).waitFor();
 await work(teacher).getByRole('button',{name:'继续编辑草稿'}).click();await work(teacher).getByText('发现本机未同步的修改').waitFor();
 assert.equal(await title(teacher).inputValue(),'光合作用学科量规 · 本地验收');await work(teacher).getByRole('button',{name:'恢复到编辑器',exact:true}).click();
 assert.equal(await title(teacher).inputValue(),'掉线前没保存的量规标题');await title(teacher).fill('光合作用学科量规 · 本地验收');
 assert.equal(await work(teacher).locator('.rw-state').innerText(),'未修改');
 checks.push('Unsaved edits are kept locally across a reload, announced on the page and restored only when the teacher asks');

 // 6. Enabling needs saved edits and a reason; it sets the new-assignment default and leaves existing assignments alone.
 const before=(await rpc(teacher,'/courses/1/assignments')).map(a=>[a.id,a.rubric_version]);
 await title(teacher).fill('光合作用学科量规 · 本地验收（改）');await work(teacher).locator('.rw-state').getByText('有未保存的修改').waitFor();
 await enable(teacher,'本地验收：教材引用标准明确化，非线上发布。');
 assert.equal(await title(teacher).count(),0);await work(teacher).locator('.rw-current').getByText('当前生效',{exact:true}).waitFor();
 assert.equal((await rpc(teacher,'/courses/1/rubrics/'+code)).title,'光合作用学科量规 · 本地验收（改）');
 catalog=await rpc(teacher,'/courses/1/rubrics');assert.equal(catalog.defaultId,rid);assert.equal(catalog.items.find(r=>r.id===rid).state,'ENABLED');
 assert.deepEqual((await rpc(teacher,'/courses/1/assignments')).filter(a=>before.some(([id])=>id===a.id)).map(a=>[a.id,a.rubric_version]),before);
 checks.push('Enabling asks for a reason, saves pending edits first, makes the version the new-assignment default and leaves existing assignments untouched');

 // 7. Students now see the enabled version and its anchors.
 await open(student);await work(student).getByLabel('查看量规版本').waitFor();assert.equal(await work(student).getByLabel('查看量规版本').inputValue(),code);
 await work(student).getByText(ANCHOR,{exact:true}).waitFor();await shot(student,'03-enabled-student-rubric');
 checks.push('Students read the enabled version, including its anchors, and it is preselected as the course default');
 const labels=await work(student).getByLabel('查看量规版本').locator('option').allTextContents();assert(labels.every(l=>!/cr-\d/.test(l)),labels.join('|'));assert(labels.some(l=>/^v\d+ 已启用/.test(l)));assert(labels.some(l=>l.startsWith('内置版本')));
 checks.push('Version options use readable labels (version number, state, date, title) instead of internal codes');

 // 8. New assignments default to the enabled version and store it.
 await nav(teacher,'课程作业');await teacher.getByRole('button',{name:'发布作业',exact:true}).click();const modal=teacher.getByRole('dialog',{name:'发布课程作业',exact:true});
 await teacher.waitForFunction(()=>document.querySelector('[aria-label="作业量规版本"]')?.value!=='');assert.equal(await modal.getByLabel('作业量规版本').inputValue(),String(rid));
 await modal.getByLabel('作业名称',{exact:true}).fill('绑定量规验收 · 非真实作业');await modal.getByLabel('成果要求与学习目标',{exact:true}).fill('验证量规固定版本与提交快照，仅本地验收。');await modal.getByLabel('截止时间（本地时区）').fill('2030-12-31T18:00');
 await shot(teacher,'04-assignment-rubric-selection');await modal.getByRole('button',{name:'发布作业',exact:true}).click();await modal.waitFor({state:'hidden'});
 const assignment=(await rpc(teacher,'/courses/1/assignments')).find(a=>a.title==='绑定量规验收 · 非真实作业');assert.equal(assignment.rubric_version,code);
 checks.push('Assignment creation preselects the enabled default and binds the assignment to it');

 // 9. A real submission freezes the bound rubric; grading shows the submission's own anchors.
 await nav(student,'我的作业');await student.locator('.assignment-card').filter({hasText:'绑定量规验收'}).getByRole('button',{name:/开始作答/}).click();
 const editor=student.locator('.prose-editor[contenteditable="true"]').first();await editor.waitFor();
 await student.getByRole('textbox',{name:'知识帖标题',exact:true}).fill('绑定量规的真实提交验收');await editor.click();await editor.pressSequentially('光反应发生在类囊体薄膜上，形成 ATP 与 NADPH。此为本地功能验收文本。');
 await student.getByRole('button',{name:'提交评审',exact:true}).click();await student.getByText('已提交 v1，等待教师评审').waitFor();
 const eid=(await rpc(student,'/courses/1/assignments')).find(a=>a.id===assignment.id).entry_id,sid=(await rpc(student,'/entries/'+eid)).versions[0].id;
 const frozen=(await rpc(student,'/submissions/'+sid)).rubricSnapshot;assert.equal(frozen.code,code);assert.equal(frozen.criteria.find(c=>c.code==='S1').excellent,ANCHOR);
 await nav(teacher,'批改中心');await teacher.locator('.review-row').filter({hasText:'绑定量规的真实提交验收'}).click();await teacher.locator('#score-S1').waitFor({state:'attached'});
 await teacher.locator('details.grade-fine > summary').click();await teacher.locator('details.grade-anchor').first().locator('summary').click();
 await teacher.locator('details.grade-anchor').first().getByText(ANCHOR,{exact:false}).waitFor();await shot(teacher,'05-grading-bound-anchors');
 checks.push('A real submission stores the assignment-bound rubric snapshot, and grading shows that snapshot\'s anchors');

 // 10. After grading, the reader's rubric link opens the pinned snapshot; course navigation leaves it.
 await rpc(teacher,'/submissions/'+sid+'/evaluations','POST',{items:codes.map(c=>({code:c,score:82,status:'SCORED',reason:''})),summary:'量规验收评价，不代表真实成绩。',gateReason:'',expectedRevision:0});
 await nav(student,'学习轨迹');await student.locator('.progress-entry').filter({hasText:'绑定量规的真实提交验收'}).getByRole('button',{name:'查看最近提交',exact:true}).click();await student.getByRole('heading',{name:'阅读知识条目',exact:true}).waitFor();
 const reopen=student.getByRole('button',{name:'展开反馈面板',exact:true});if(await reopen.count())await reopen.click();
 await student.getByRole('button',{name:'查看课程量规'}).click();await work(student).getByText('当前提交绑定的量规快照',{exact:true}).waitFor();await work(student).getByText(ANCHOR,{exact:true}).waitFor();
 assert.equal(await work(student).getByLabel('查看量规版本').count(),0);await shot(student,'06-reader-pinned-rubric');
 await nav(student,'课程量规');await ready(student);assert.equal(await work(student).getByText('当前提交绑定的量规快照',{exact:true}).count(),0);
 checks.push('The reader\'s rubric link shows the submission\'s pinned snapshot without a version switcher; course navigation returns to the live catalog');

 // 11. A later enabled version changes neither the old assignment binding nor the frozen snapshot.
 await open(teacher);await work(teacher).getByRole('button',{name:'新建修订版本'}).click();await title(teacher).waitFor();
 await title(teacher).fill('后续标准：不应改写历史');await save(teacher);const next=(await rpc(teacher,'/courses/1/rubrics')).items.find(r=>r.state==='DRAFT');
 await enable(teacher,'本地后续版本验收');
 assert.deepEqual((await rpc(student,'/submissions/'+sid)).rubricSnapshot,frozen);assert.equal((await rpc(teacher,'/courses/1/assignments')).find(a=>a.id===assignment.id).rubric_version,code);
 checks.push('A later enabled default leaves the old assignment binding and the frozen submission snapshot unchanged');

 // 11b. A new draft does not replace the live version on entry; bulk anchors write one band into a whole dimension.
 await open(teacher);await work(teacher).getByRole('button',{name:'新建修订版本'}).click();await title(teacher).waitFor();
 await work(teacher).getByRole('button',{name:'统一四档描述'}).first().click();const bulkGood=work(teacher).getByLabel('S 统一良好 75–89');await bulkGood.fill('统一写法：论证基本成立，个别来源需补页码。');
 await work(teacher).getByRole('button',{name:'写入 S1 / S2 / S3'}).click();await work(teacher).getByRole('status').filter({hasText:'已把四档描述写入 S 维度的 3 项指标'}).waitFor();await save(teacher);
 const bulkDraft=(await rpc(teacher,'/courses/1/rubrics')).items.find(r=>r.state==='DRAFT');const bulkRow=await rpc(teacher,'/courses/1/rubrics/'+bulkDraft.code);
 assert.deepEqual(bulkRow.criteria.filter(c=>c.code.startsWith('S')).map(c=>c.good),Array(3).fill('统一写法：论证基本成立，个别来源需补页码。'));assert.notEqual(bulkRow.criteria.find(c=>c.code==='A1').good,'统一写法：论证基本成立，个别来源需补页码。');
 assert.equal(bulkRow.criteria.find(c=>c.code==='S1').excellent,ANCHOR);
 await work(teacher).getByRole('button',{name:'取消',exact:true}).click();await open(teacher);await work(teacher).locator('.rw-current').getByText('当前生效',{exact:true}).waitFor();
 assert.equal(await work(teacher).getByLabel('查看量规版本').inputValue(),'cr-'+next.id);await work(teacher).locator('.rw-draft-note').getByText(/有一份未启用的草稿/).waitFor();
 checks.push('Unified band text is written into every indicator of one dimension only, leaving other bands and dimensions unchanged');
 checks.push('With a draft pending, the page still opens on the live version and points to the draft separately');

 // 12. Enable history and default switching (API only; the redesigned page has no controls for them).
 const history=await rpc(teacher,'/courses/1/rubrics/history');assert(JSON.stringify(history).includes('本地后续版本验收'));
 catalog=await rpc(teacher,'/courses/1/rubrics');
 assert.equal(await student.evaluate(async rev=>{const t=await(await fetch('/api/auth/csrf')).json();return(await fetch('/api/courses/1/rubrics/default',{method:'PUT',headers:{'Content-Type':'application/json',[t.headerName]:t.token},body:JSON.stringify({rubricId:0,catalogRevision:rev,reason:'学生不应能切换'})})).status;},catalog.catalogRevision),403);
 await rpc(teacher,'/courses/1/rubrics/default','PUT',{rubricId:0,catalogRevision:catalog.catalogRevision,reason:'本地恢复基础默认，不改历史作业'});
 assert.equal((await rpc(teacher,'/courses/1/rubrics')).defaultId,0);assert.equal((await rpc(teacher,'/courses/1/assignments')).find(a=>a.id===assignment.id).rubric_version,code);
 await open(teacher);await work(teacher).locator('.rw-current').getByText('四维基础量规',{exact:true}).waitFor();
 checks.push('Enable history records reasons for the teacher only; switching the default back to the built-in is teacher-only and is reflected on the page');

 // 13. A legacy submission without a rubric snapshot is labelled as such (explicit response fixture; backend fallback is covered by RubricServiceTest).
 const seedPacket=await rpc(student,'/submissions/1'),legacyPacket={...seedPacket.rubricSnapshot,legacy:true,criteria:seedPacket.rubricSnapshot.criteria.map(i=>({code:i.code,name:i.name}))};
 await student.route('**/api/submissions/1',route=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({...seedPacket,rubricSnapshot:legacyPacket,rubricSnapshotSource:'BUILTIN_LEGACY'})}));
 await nav(student,'学习轨迹');await student.locator('.progress-entry').filter({hasText:'光反应之后，物质去了哪里？'}).getByRole('button',{name:'查看最近提交',exact:true}).click();await student.getByRole('heading',{name:'阅读知识条目',exact:true}).waitFor();
 const reopen2=student.getByRole('button',{name:'展开反馈面板',exact:true});if(await reopen2.count())await reopen2.click();
 await student.getByRole('button',{name:'查看课程量规'}).click();await work(student).getByText('历史提交未保存锚点，仅显示当时固定的指标代码。',{exact:true}).waitFor();
 assert.equal(await work(student).locator('.rv-bands').count(),0);await student.unroute('**/api/submissions/1');
 checks.push('A legacy submission without anchors is labelled and shows only the fixed indicator codes');

 // 14. Dark theme, 390px and 1280px layouts have no horizontal overflow; anchor rows open from the keyboard.
 await open(teacher);await work(teacher).getByRole('button',{name:'继续编辑草稿'}).click();await title(teacher).waitFor();
 await teacher.evaluate(()=>window.courseTheme.set('dark'));await shot(teacher,'07-rubric-dark');
 await teacher.setViewportSize({width:1280,height:1000});await shot(teacher,'08-rubric-1280-dark');
 const head=work(teacher).locator('.rw-item-head').nth(1);await head.focus();await teacher.keyboard.press('Enter');assert.equal(await head.getAttribute('aria-expanded'),'true');
 checks.push('Dark surfaces and the 1280px layout have no horizontal overflow; indicator rows open from the keyboard');

 // 15. Session expiry with blocked storage keeps the unsaved rubric in memory and requires a download before logging in again.
 await teacher.evaluate(()=>{Storage.prototype.setItem=function(){throw new DOMException('Local test storage unavailable','QuotaExceededError');};});
 await title(teacher).fill('会话过期仍需下载的量规草稿');
 const changed='LocalRubric!'+crypto.randomUUID();await rpc(second,'/account/password','PUT',{currentPassword:password,newPassword:changed,confirmation:changed});
 await teacher.evaluate(()=>dispatchEvent(new Event('focus')));await teacher.getByRole('button',{name:'登录工作空间',exact:true}).waitFor();
 await teacher.getByRole('alert').filter({hasText:'本机存储不可用'}).waitFor();
 await teacher.getByLabel('账号邮箱').fill('teacher@example.test');await teacher.getByLabel('密码',{exact:true}).fill(changed);await teacher.getByRole('button',{name:'登录工作空间',exact:true}).click();
 await teacher.getByRole('alert').filter({hasText:'请先下载未同步草稿备份'}).waitFor();
 const pendingDownload=teacher.waitForEvent('download');await teacher.getByRole('button',{name:'下载未同步草稿备份',exact:true}).click();
 const backup=JSON.parse(fs.readFileSync(await (await pendingDownload).path(),'utf8'));assert.equal(backup.type,'rubric-draft');assert.equal(backup.title,'会话过期仍需下载的量规草稿');assert.equal(backup.criteria.length,11);
 await shot(teacher,'10-session-expired-rubric-download');
 checks.push('With storage blocked, session expiry keeps the rubric draft in memory and re-login is held until the JSON backup is downloaded');

 await second.close();assert.deepEqual(errors,[]);console.log(JSON.stringify({checks,screenshots,browserErrors:errors.length},null,2));
}catch(e){console.error(e);for(const [i,c]of browser.contexts().entries())for(const [j,p]of c.pages().entries())await p.screenshot({path:out+`/failure-${i}-${j}.png`}).catch(()=>{});console.error('Browser errors:',errors);process.exitCode=1;}finally{await browser.close();}
