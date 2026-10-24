import { createRequire } from 'node:module';
import fs from 'node:fs';
import assert from 'node:assert/strict';
const {chromium}=createRequire(new URL('../frontend/package.json',import.meta.url))('playwright');
const base=process.env.E2E_BASE_URL || 'http://127.0.0.1:8084', password=process.env.E2E_PASSWORD;
if(!password) throw Error('Set E2E_PASSWORD');
const out=new URL('../.qa/studio',import.meta.url).pathname; fs.mkdirSync(out,{recursive:true});
const browser=await chromium.launch({headless:true,args:['--no-sandbox'], executablePath:process.env.CHROMIUM_PATH || '/vercel/sandbox/.cache/ms-playwright/chromium-1243/chrome-linux64/chrome'});
const page=await browser.newPage({viewport:{width:1600,height:1100},colorScheme:'light'});
const checks=[],errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
async function capture(name){await page.waitForTimeout(300);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false,name+' overflow');await page.screenshot({path:out+'/'+name+'.png',fullPage:true});}
async function login(who){await page.goto(base);await page.getByLabel('账号邮箱').fill(who+'@example.test');await page.getByLabel('密码',{exact:true}).fill(password);await page.getByRole('button',{name:'登录工作空间'}).click();await page.getByRole('button',{name:'退出登录'}).waitFor();}
async function endDocument(){await page.locator('.prose-editor[contenteditable=true]').evaluate(el=>el.editor.commands.focus('end'));await page.waitForFunction(()=>{const el=document.querySelector('.prose-editor[contenteditable=true]'),editor=el?.editor;return editor&&document.activeElement===el&&editor.state.selection.empty&&editor.state.selection.to===editor.state.doc.content.size-1;});assert.equal(await page.locator('.prose-editor [data-block-id="p-gap"]').count(),1);await page.keyboard.press('Enter');}
try{
 const image=await browser.newPage({viewport:{width:1280,height:610},deviceScaleFactor:1});await image.goto(new URL('./fixtures/photosynthesis.svg',import.meta.url).href);await image.screenshot({path:out+'/diagram.png'});await image.close();
 await login('student');await page.getByRole('button',{name:'继续创作',exact:true}).click();await page.getByRole('textbox',{name:'知识帖标题'}).waitFor();
 assert.equal(await page.locator('.studio-outline').count(),1);assert.equal(await page.locator('.studio-outline nav button').count(),1);checks.push('Three-column studio; headings-only outline');
 await capture('01-studio-original');
 // Insert near the beginning, rather than hiding the core feature at the bottom of the document.
 await page.locator('.prose-editor > p').first().evaluate(el=>{el.focus();const r=document.createRange();r.selectNodeContents(el);r.collapse(false);const s=getSelection();s.removeAllRanges();s.addRange(r);});
 await page.keyboard.press('Enter');await page.getByRole('button',{name:'插入图片',exact:true}).click();await page.getByLabel('选择图片文件').setInputFiles(out+'/diagram.png');
 await page.getByLabel('图片说明（替代文本）').fill('光反应与卡尔文循环之间的物质联系');
 await page.getByLabel('图注',{exact:true}).fill('光反应与卡尔文循环：ATP、NADPH 及其返回物质');
 await page.getByLabel('图片来源',{exact:true}).fill('系统演示自绘示意图；正式写作请补充可核验教材来源');
 await page.getByRole('button',{name:'插入图片',exact:true}).last().click();
 await page.locator('.knowledge-figure img').waitFor();assert.equal(await page.locator('.knowledge-figure img').evaluate(el=>el.complete&&el.naturalWidth>0),true);checks.push('Real raster upload, rendered image, caption and source');
 await page.getByRole('button',{name:'编辑图注',exact:true}).click();await page.getByLabel('图注',{exact:true}).fill('图 1　物质循环与能量转化，是两个不同的问题');await page.getByRole('button',{name:'更新图片',exact:true}).click();await page.getByText('图 1　物质循环与能量转化，是两个不同的问题',{exact:true}).waitFor();checks.push('Edit existing image caption');
 await page.getByRole('button',{name:'挂证据',exact:true}).last().click();await page.getByRole('dialog',{name:'挂载论断证据'}).waitFor();assert.match(await page.getByLabel('关联正文段落').locator('option:checked').textContent(),/图片/);await page.getByRole('button',{name:'取消',exact:true}).click();checks.push('Figure evidence anchor');
 await page.locator('.knowledge-figure img').click();await page.locator('.toolbar-evidence').click();await page.getByRole('dialog',{name:'挂载论断证据'}).waitFor();assert.match(await page.getByLabel('关联正文段落').locator('option:checked').textContent(),/图片/);await page.getByRole('button',{name:'取消',exact:true}).click();checks.push('Selected image uses the correct toolbar evidence anchor');
 const body=page.locator('.prose-editor[contenteditable=true]');await body.click();await page.keyboard.press('Control+a');await page.keyboard.press('Backspace');await page.waitForTimeout(2000);await page.getByText('有证据关联的正文已删除，请撤销删除，或在「论断与证据」中移除失效证据后再保存。',{exact:true}).waitFor();await page.keyboard.press('Control+z');await page.locator('.knowledge-figure').waitFor();await page.waitForTimeout(2100);checks.push('Deleting anchored content is blocked from autosave until restored; undo preserves anchors');

 await capture('02-studio-image');
 await endDocument();await page.keyboard.type('## 返回物质与反应条件');await page.keyboard.press('Space');await page.keyboard.press('Enter');
 await page.getByRole('button',{name:'插入公式',exact:true}).click();await page.getByRole('button',{name:'确认公式',exact:true}).click();await page.getByText('请填写有效的 LaTeX 公式',{exact:true}).waitFor();await page.getByRole('button',{name:'ATP 转化',exact:true}).click();await page.getByRole('button',{name:'确认公式',exact:true}).click();await page.locator('.knowledge-math .katex').waitFor();checks.push('LaTeX validation, live preview and rendered formula');
 await endDocument();await page.getByRole('button',{name:'插入表格',exact:true}).click();await page.locator('.prose-editor th p').first().click();await page.keyboard.type('物质');await page.keyboard.press('Tab');await page.keyboard.type('使用后');await page.getByRole('button',{name:'下方加行',exact:true}).click();assert.equal(await page.locator('.prose-editor tr').count(),4);checks.push('Editable table and row operation');
 await endDocument();await page.getByRole('button',{name:'插入提示块',exact:true}).click();await page.locator('.knowledge-callout').waitFor();checks.push('Callout block');
 await page.getByRole('button',{name:'保存草稿',exact:true}).click();await page.waitForTimeout(2200);
 // Authenticate JSON request through same browser cookie jar; verify stored structures, not DOM only.
 let entry=await page.evaluate(async()=>{const a=await(await fetch('/api/courses/1/assignments')).json();return await(await fetch('/api/entries/'+a[0].entry_id)).json();});
 fs.writeFileSync(out+'/saved-content.json',JSON.stringify(entry.content));
 const raw=JSON.stringify(entry.content);for(const t of ['figure','mathBlock','table','callout'])assert.ok(raw.includes('"type":"'+t+'"'));checks.push('Rich blocks persisted through draft API');
 await page.reload();await page.getByRole('button',{name:'继续创作',exact:true}).click();await page.locator('.knowledge-figure').waitFor();await page.locator('.knowledge-math .katex').waitFor();assert.equal(await page.locator('.prose-editor tr').count(),4);checks.push('Save/reload round-trip');
 await page.evaluate(()=>scrollTo(0,0));await capture('03-studio-1600');
 await page.setViewportSize({width:1440,height:1000});await capture('04-studio-1440');await page.setViewportSize({width:1280,height:1000});await capture('05-studio-1280');
 await page.getByRole('button',{name:'评分与反馈',exact:true}).click();await page.getByRole('button',{name:'写作大纲',exact:true}).click();await capture('06-focus');
 await page.getByRole('button',{name:'评分与反馈',exact:true}).click();await page.getByRole('button',{name:'写作大纲',exact:true}).click();
 await page.evaluate(()=>{localStorage.setItem('ockc-theme','dark');window.courseTheme.set('dark')});await capture('07-dark');await page.evaluate(()=>{localStorage.setItem('ockc-theme','light');document.documentElement.dataset.theme='light'});
 const download=page.waitForEvent('download');await page.getByRole('button',{name:'导出 Markdown',exact:true}).click();const f=await download;await f.saveAs(out+'/export.md');const md=fs.readFileSync(out+'/export.md','utf8');assert.ok(md.includes('![光反应'));assert.ok(md.includes('$$'));assert.ok(md.includes('| ---'));checks.push('Markdown export includes image reference, math, table');
 await page.getByRole('button',{name:'提交评审',exact:true}).click();await page.getByText('已提交 v2，等待教师评审').waitFor();checks.push('Rich document immutable submission');
 await page.getByRole('button',{name:'展开侧边栏',exact:true}).click();await page.getByRole('button',{name:'学习轨迹',exact:true}).click();await page.getByRole('heading',{name:'学习轨迹',exact:true}).waitFor();await page.getByText('我的作品档案',{exact:true}).waitFor();await capture('08-progress-student');checks.push('Student real-data progress page');
 await page.getByRole('button',{name:'退出登录',exact:true}).click();await login('teacher');await page.getByRole('button',{name:/批改中心/}).first().click();await page.locator('.review-row').filter({hasText:'林同学'}).filter({hasText:/v2(?:\s|$)/}).click();await page.locator('.knowledge-figure').waitFor();await page.locator('.knowledge-math .katex').waitFor();await page.setViewportSize({width:1440,height:1000});await capture('09-grading-rich');checks.push('Teacher reads frozen image, formula, table');
 await page.getByRole('button',{name:'展开侧边栏',exact:true}).click();await page.getByRole('button',{name:'教学进展',exact:true}).click();await page.getByRole('heading',{name:'教学进展',exact:true}).waitFor();await capture('10-progress-teacher');checks.push('Teacher course progress page');
 async function rpc(path, method='GET', body) { return await page.evaluate(async ({path,method,body})=>{const token=await(await fetch('/api/auth/csrf')).json();const r=await fetch('/api'+path,{method,headers:{'Content-Type':'application/json',[token.headerName]:token.token},...(body===undefined?{}:{body:JSON.stringify(body)})});const data=await r.json();if(!r.ok)throw Error(JSON.stringify(data));return data;},{path,method,body}); }
 const frozen=(await rpc('/entries/1')).versions[0];let v=await rpc('/submissions/'+frozen.id);
 for(const ev of v.evidence) if(ev.state==='PENDING') await rpc('/evidence/'+ev.id+'/verifications','POST',{verdict:'PASS',reason:'自动化测试核对演示材料，不代表学术真实性评价'});
 const items=['S1','S2','S3','A1','A2','A3','C1','C2','L1','L2','L3'].map(code=>({code,score:84,status:'SCORED',reason:''}));
 await rpc('/submissions/'+v.id+'/evaluations','POST',{items,gateReason:'',summary:'自动化图文功能验收，不代表真实学习评价。',expectedRevision:0});
 const figure=v.content.content.find(n=>n.type==='figure');
 await rpc('/submissions/'+v.id+'/feedback','POST',{paragraphId:figure.attrs.blockId,indicator:'S3',body:'自动化演示批注：请说明图中箭头与正文论断的对应关系。'});checks.push('Teacher grades rich version and anchors feedback to image');
 await page.getByRole('button',{name:'退出登录',exact:true}).click();await login('student');await rpc('/submissions/'+v.id+'/publication','POST');
 await page.getByRole('button',{name:'退出登录',exact:true}).click();await login('teacher');
 const pub=(await rpc('/courses/1/publications?pending=true')).find(p=>p.submission_id===v.id);await rpc('/publications/'+pub.id+'/actions','POST',{action:'APPROVE',body:''});
 await page.getByRole('button',{name:'退出登录',exact:true}).click();await login('peer');await page.getByRole('button',{name:'知识社区',exact:true}).click();await page.locator('.knowledge-cover img').waitFor();await capture('11-community-rich');
 await page.getByRole('button',{name:'打开图文知识帖',exact:true}).first().click();await page.locator('.knowledge-figure').waitFor();await page.locator('.knowledge-math .katex').waitFor();assert.equal(await page.getByRole('button',{name:'编辑图注',exact:true}).count(),0);await capture('12-reader-rich');
 assert.equal((await page.request.get(base+figure.attrs.src)).status(),200);checks.push('Approval pins rich version; course peer can read image, formula and table without edit controls');
 assert.deepEqual(errors,[]);checks.push('No JavaScript errors');console.log(JSON.stringify({checks, screenshots:12},null,2));
}catch(e){await page.screenshot({path:out+'/failure.png',fullPage:true});console.error(e);console.error('page errors',errors);process.exitCode=1;}finally{await browser.close();}
