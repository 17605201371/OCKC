#!/usr/bin/env python3
"""Destructive local demonstration-database smoke test. Never run against real courses."""
import os, json, urllib.request, urllib.error, urllib.parse, http.cookiejar, uuid, datetime
BASE=os.environ.get('E2E_BASE_URL','http://127.0.0.1:8080')
PASSWORD=os.environ.get('E2E_PASSWORD')
if not PASSWORD: raise SystemExit('Set E2E_PASSWORD to the local demonstration password')
checks=[]
def check(name,condition):
    assert condition,name
    checks.append(name)
class Client:
    def __init__(self,email):
        self.http=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf=self.call('/auth/csrf')[1]
        body=urllib.parse.urlencode({'username':email,'password':PASSWORD}).encode()
        req=urllib.request.Request(BASE+'/api/auth/login',body,{'Content-Type':'application/x-www-form-urlencoded',self.csrf['headerName']:self.csrf['token']},method='POST')
        with self.http.open(req) as r: assert r.status==200
        self.csrf=self.call('/auth/csrf')[1]
    def call(self,path,method='GET',body=None,key=None):
        headers={'Content-Type':'application/json'}
        if method!='GET':headers[self.csrf['headerName']]=self.csrf['token']
        if key:headers['Idempotency-Key']=key
        req=urllib.request.Request(BASE+'/api'+path,None if body is None else json.dumps(body).encode(),headers,method=method)
        try:
            with self.http.open(req) as r:return r.status,json.load(r)
        except urllib.error.HTTPError as e:return e.code,json.load(e)
t=Client('teacher@example.test');s=Client('student@example.test');p=Client('peer@example.test');o=Client('outsider@example.test')
cid=next(c['id'] for c in t.call('/courses')[1] if c['title']=='教学设计 · 演示课程')
due=(datetime.datetime.now(datetime.timezone.utc)+datetime.timedelta(days=1)).isoformat().replace('+00:00','Z')
status,a=t.call(f'/courses/{cid}/assignments','POST',{'title':'E2E · '+uuid.uuid4().hex[:8],'requirements':'隔离演示数据：验证完整闭环','dueAt':due});check('teacher creates real assignment',status==200)
status,e=s.call(f'/assignments/{a["id"]}/entries','POST');check('student starts owned entry',status==200)
eid=e['id'];block=uuid.uuid4().hex
content={'type':'doc','content':[{'type':'paragraph','attrs':{'blockId':block},'content':[{'type':'text','text':'第一版论断，等待对照来源核验。'}]}]}
evidence=[{'paragraphId':block,'sourceTitle':'E2E 本地测试来源','sourceUrl':'','excerpt':'供自动化测试使用的摘录，不作为学术事实依据。','claim':'这是演示论断。'}]
body={'title':'E2E 测试作品','content':content,'evidence':evidence,'revision':0}
status,d=s.call(f'/entries/{eid}/draft','PUT',body);check('draft save increments revision',status==200 and d['revision']==1)
check('stale draft rejected',s.call(f'/entries/{eid}/draft','PUT',body)[0]==409)
key=uuid.uuid4().hex
status,v=s.call(f'/entries/{eid}/submissions','POST',{'revision':1},key);sid=v['id'];check('submission creates version one',status==200 and v['version_no']==1)
check('submission is idempotent',s.call(f'/entries/{eid}/submissions','POST',{'revision':0},key)[1]['id']==sid)
check('private peer submission inaccessible',p.call(f'/submissions/{sid}')[0]==403)
check('outsider course inaccessible',o.call(f'/courses/{cid}/assignments')[0]==403)
_,v=s.call(f'/submissions/{sid}');ev=v['evidence'][0]['id']
items=[{'code':i,'score':94,'status':'SCORED','reason':''} for i in ['S1','S2','S3','A1','A2','A3','C1','C2','L1','L2','L3']]
grade={'items':items,'gateReason':'E2E 已证实严重缺陷测试','summary':'E2E 总评：补充条件。','expectedRevision':0}
check('pending evidence blocks formal grade',t.call(f'/submissions/{sid}/evaluations','POST',grade)[0]==422)
check('author cannot verify own evidence',s.call(f'/evidence/{ev}/verifications','POST',{'verdict':'PASS','reason':'自核应拒绝'})[0]==403)
check('peer verifies evidence',p.call(f'/evidence/{ev}/verifications','POST',{'verdict':'PASS','reason':'E2E 对照测试摘录，不代表真实学术核验'})[0]==200)
status,g=t.call(f'/submissions/{sid}/evaluations','POST',grade);check('quality gate preserves raw and caps final',status==200 and g['q_raw']==94 and g['q_final']==89)
check('student cannot publish grade',s.call(f'/submissions/{sid}/evaluations','POST',grade)[0]==403)
check('stale grading rejected',t.call(f'/submissions/{sid}/evaluations','POST',grade)[0]==409)
status,f=t.call(f'/submissions/{sid}/feedback','POST',{'paragraphId':block,'indicator':'C2','body':'请补充适用条件。'});check('anchored teacher feedback created',status==200)
check('student replies to feedback',s.call(f'/feedback/{f["id"]}/actions','POST',{'action':'REPLY','body':'将在下一版说明适用条件。'})[0]==200)
check('student cannot self-resolve',s.call(f'/feedback/{f["id"]}/actions','POST',{'action':'RESOLVE','body':''})[0]==403)
check('teacher resolves feedback',t.call(f'/feedback/{f["id"]}/actions','POST',{'action':'RESOLVE','body':'已核对回应'})[0]==200)
check('author requests publication',s.call(f'/submissions/{sid}/publication','POST')[0]==200)
request=next(x for x in t.call(f'/courses/{cid}/publications?pending=true')[1] if x['submission_id']==sid)
check('teacher approves pinned version',t.call(f'/publications/{request["id"]}/actions','POST',{'action':'APPROVE','body':''})[0]==200)
check('peer may read approved version',p.call(f'/submissions/{sid}')[0]==200)
check('published still denies outsider',o.call(f'/submissions/{sid}')[0]==403)
check('author withdraws publication',s.call(f'/publications/{request["id"]}/actions','POST',{'action':'WITHDRAW','body':''})[0]==200)
check('withdrawal closes peer access',p.call(f'/submissions/{sid}')[0]==403)
# A new draft changes the text but must not mutate the previous submitted version.
content['content'][0]['content'][0]['text']='第二版论断，补全适用条件。'
body.update(content=content,revision=1)
status,d=s.call(f'/entries/{eid}/draft','PUT',body);check('revision two saves',status==200 and d['revision']==2)
check('old version text immutable',s.call(f'/submissions/{sid}')[1]['content']['content'][0]['content'][0]['text']=='第一版论断，等待对照来源核验。')
status,v2=s.call(f'/entries/{eid}/submissions','POST',{'revision':2},uuid.uuid4().hex);sid2=v2['id'];check('second immutable submission created',status==200 and v2['version_no']==2)
_,v2=s.call(f'/submissions/{sid2}');check('paragraph diff links previous text',v2['changes'][0]['type']=='CHANGED' and v2['changes'][0]['before'].startswith('第一版'))
check('evidence re-verifies each version',v2['evidence'][0]['state']=='PENDING')
print(json.dumps({'status':'PASS','count':len(checks),'checks':checks},ensure_ascii=False,indent=2))
