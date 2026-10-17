// Optional local-only visual review of the explicitly labelled demo assignment.
import { createRequire } from 'node:module';
import fs from 'node:fs';
const { chromium }=createRequire(new URL('../frontend/package.json',import.meta.url))('playwright');
const base=process.env.E2E_BASE_URL || 'http://127.0.0.1:8090';
if(!/^http:\/\/(127\.0\.0\.1|localhost):\d+$/.test(base))throw Error('Local previews only');
if(!process.env.E2E_PASSWORD)throw Error('Set E2E_PASSWORD');
const out=new URL('../.qa/studio-handoff',import.meta.url).pathname;fs.mkdirSync(out,{recursive:true});
const browser=await chromium.launch({headless:true,args:['--no-sandbox'],...(process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{})});
const page=await browser.newPage({viewport:{width:1600,height:1100},colorScheme:'light'});const errors=[];page.on('pageerror',e=>errors.push(e.message));
try{
 await page.goto(base);await page.getByLabel('账号邮箱').fill('student@example.test');await page.getByLabel('密码',{exact:true}).fill(process.env.E2E_PASSWORD);await page.getByRole('button',{name:'登录工作空间'}).click();await page.getByRole('button',{name:'继续创作',exact:true}).click();await page.locator('.knowledge-figure img').waitFor();await page.waitForTimeout(500);await page.evaluate(()=>scrollTo(0,0));await page.screenshot({path:out+'/workbench.png'});
 await page.setViewportSize({width:1280,height:1000});await page.screenshot({path:out+'/workbench-1280.png'});
 await page.setViewportSize({width:1600,height:1100});await page.getByRole('button',{name:'展开侧边栏',exact:true}).click();await page.getByRole('button',{name:'学习轨迹',exact:true}).click();await page.getByText('我的作品档案',{exact:true}).waitFor();await page.waitForTimeout(300);await page.screenshot({path:out+'/progress.png',fullPage:true});
 await page.getByRole('button',{name:'知识社区',exact:true}).click();await page.locator('.knowledge-cover img').first().waitFor();await page.waitForTimeout(300);await page.screenshot({path:out+'/community.png'});
 if(errors.length)throw Error(errors.join('\n'));console.log('Local demo visual review: 4 screenshots, no script errors.');
}finally{await browser.close();}
