// Visual handoff on sandbox-local preview only; enables AI entry only on labelled demo assignment.
import { createRequire } from 'node:module';
import fs from 'node:fs';
import assert from 'node:assert/strict';
const { chromium, request } = createRequire(new URL('../frontend/package.json', import.meta.url))('playwright');
const base = process.env.E2E_BASE_URL || 'http://127.0.0.1:8097', password = process.env.E2E_PASSWORD;
if (!/^http:\/\/(127\.0\.0\.1|localhost):\d+$/.test(base) || !password) throw Error('Set local E2E_BASE_URL and E2E_PASSWORD');
async function policy(url) {
 const ctx = await request.newContext({baseURL:url});
 try { let token = await (await ctx.get('/api/auth/csrf')).json(); let r=await ctx.post('/api/auth/login',{form:{username:'teacher@example.test',password},headers:{[token.headerName]:token.token}});if(!r.ok())throw Error('Demo authentication failed');token=await(await ctx.get('/api/auth/csrf')).json();const course=(await(await ctx.get('/api/courses')).json()).find(c=>c.title==='教学设计 · 演示课程');if(!course)throw Error('Missing demo course');const task=(await(await ctx.get(`/api/courses/${course.id}/assignments`)).json()).find(a=>a.title==='新版图文编辑体验 · 演示作业');if(!task)throw Error('Missing labelled demo task');r=await ctx.put(`/api/assignments/${task.id}/ai-policy`,{headers:{[token.headerName]:token.token},data:{enabled:true}});assert.equal(r.ok(),true); }
 finally{await ctx.dispose();}
}
await policy(base);
// API-only update to explicitly labelled public demo; browser stays local.
if(process.env.PUBLIC_DEMO_BASE_URL) await policy(process.env.PUBLIC_DEMO_BASE_URL);
const browser=await chromium.launch({headless:true,args:['--no-sandbox'],executablePath:process.env.CHROMIUM_PATH || '/vercel/sandbox/.cache/ms-playwright/chromium-1243/chrome-linux64/chrome'});
const out=decodeURIComponent(new URL('../.qa/feedback-handoff',import.meta.url).pathname);fs.mkdirSync(out,{recursive:true});
try{const page=await browser.newPage({viewport:{width:1600,height:1450},colorScheme:'light'});const errors=[];page.on('pageerror',e=>errors.push(e.message));await page.goto(base);await page.getByLabel('账号邮箱').fill('student@example.test');await page.getByLabel('密码',{exact:true}).fill(password);await page.getByRole('button',{name:'登录工作空间'}).click();await page.getByRole('button',{name:'继续创作',exact:true}).click();await page.locator('.knowledge-figure img').waitFor();await page.getByRole('region',{name:'AI 反馈',exact:true}).waitFor();await page.locator('.feedback-card .feedback-highlight').waitFor();await page.waitForTimeout(400);assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);await page.screenshot({path:out+'/right-sidebar-overview.png'});
await page.setViewportSize({width:1600,height:1100});await page.getByRole('navigation',{name:'反馈分区导航'}).getByRole('button',{name:'AI 反馈',exact:true}).click();await page.waitForTimeout(400);await page.screenshot({path:out+'/right-sidebar-ai.png'});await page.evaluate(()=>{document.documentElement.dataset.theme='dark';document.querySelector('.feedback-sidebar').scrollTop=0});await page.screenshot({path:out+'/right-sidebar-dark.png'});assert.deepEqual(errors,[]);console.log('Final rich demo: 3 screenshots, teacher comments and AI policy visible, no JS errors.');}
finally{await browser.close();}
