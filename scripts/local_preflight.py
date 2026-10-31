#!/usr/bin/env python3
"""Read-only checks for the local development/demo workflow; never prints private values."""
import argparse,hashlib,json,os,re,shutil,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def config_checks(env):
    checks=[];warnings=[]
    def add(name,ok):checks.append({'name':name,'ok':bool(ok)})
    add('Local demo binds only to loopback',env.get('SERVER_ADDRESS','127.0.0.1') in {'127.0.0.1','localhost','::1'})
    port=env.get('PORT','8080');add('Port is an integer from 1 to 65535',bool(re.fullmatch(r'[0-9]{1,5}',port)) and 1<=int(port)<=65535)
    url=env.get('DATABASE_URL','jdbc:h2:file:./data/community;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE')
    parts=url.split(';');allowed={'MODE':{'MYSQL'},'DATABASE_TO_LOWER':{'TRUE','FALSE'},'DB_CLOSE_ON_EXIT':{'TRUE','FALSE'},'DB_CLOSE_DELAY':None,'LOCK_TIMEOUT':None}
    valid=parts[0].startswith('jdbc:h2:file:') and bool(parts[0][len('jdbc:h2:file:'):].strip()) and not any(x in url for x in ['\r','\n','://'])
    for part in parts[1:]:
        if not part.strip():continue
        key,sep,value=part.partition('=');key=key.strip().upper();value=value.strip().upper()
        valid=valid and bool(sep) and key in allowed and (value in allowed[key] if key in allowed and allowed[key] is not None else bool(re.fullmatch(r'-?[0-9]{1,8}',value)))
    add('Local workflow uses a file H2 database with supported, locking-safe options',valid)
    seed=env.get('DEMO_SEED','false').lower();add('DEMO_SEED is true or false',seed in {'true','false'})
    if seed=='true':
        pw=env.get('DEMO_PASSWORD','');add('Demo password is explicitly set and at least 12 characters',len(pw)>=12 and len(pw.encode())<=72 and bool(pw.strip()))
        warnings.append('Demo seeding creates test accounts. Use a disposable local database; this is not a production setup.')
    if not env.get('DATABASE_PASSWORD'):warnings.append('No database encryption is implied. Protect local database files and backups; configure a database password for non-disposable data.')
    secure=env.get('COOKIE_SECURE','false').lower();add('COOKIE_SECURE is true or false',secure in {'true','false'})
    if secure=='true':warnings.append('Secure cookies require HTTPS; plain local HTTP cannot persist sign-in cookies.')
    return checks,warnings

def verify_source(root=ROOT):
    path=root/'LOCAL-SOURCE-MANIFEST.json'
    try:
        manifest=json.loads(path.read_text());files=manifest['files'];seen=set()
        if not isinstance(files,list) or not files:return False
        for item in files:
            name=item['path'];p=Path(name)
            if p.is_absolute() or '..' in p.parts or name in seen:return False
            seen.add(name);target=root/p
            if not target.resolve().is_relative_to(root.resolve()) or target.is_symlink() or not target.is_file() or hashlib.sha256(target.read_bytes()).hexdigest()!=item['sha256']:return False
        return True
    except (OSError,ValueError,KeyError,TypeError):return False

def major(tool,arg,pattern):
    if not shutil.which(tool):return None
    try:
        p=subprocess.run([tool,arg],capture_output=True,text=True,timeout=15);m=re.search(pattern,p.stdout+p.stderr)
        return int(m.group(1)) if p.returncode==0 and m else None
    except (OSError,subprocess.TimeoutExpired):return None

def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime-only',action='store_true');p.add_argument('--verify-source',action='store_true');a=p.parse_args(argv)
    checks,warnings=config_checks(os.environ)
    java=major('java','-version',r'version "(\d+)');checks.append({'name':'JDK/Java 21 or newer available','ok':java is not None and java>=21})
    if not a.runtime_only:
        node=major('node','--version',r'v(\d+)');checks.extend([{'name':'Node.js 22 or newer available','ok':node is not None and node>=22},{'name':'npm available','ok':shutil.which('npm') is not None},{'name':'Maven wrapper available','ok':(ROOT/'backend/mvnw').is_file()}])
    if a.verify_source:checks.append({'name':'Every source manifest checksum matches','ok':verify_source()})
    ok=all(x['ok'] for x in checks);print(json.dumps({'ok':ok,'checks':checks,'warnings':warnings,'scope':'local-only; no network, no files changed, no private configuration values printed'},ensure_ascii=False,indent=2));return 0 if ok else 2
if __name__=='__main__':raise SystemExit(main())
