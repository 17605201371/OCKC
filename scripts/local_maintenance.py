#!/usr/bin/env python3
"""Offline Linux H2 snapshots only. Never connects to a server or overwrites a database."""
import argparse, contextlib, datetime, fcntl, hashlib, io, json, os, stat, tarfile, tempfile
from pathlib import Path
MAX_BYTES=2*1024**3
FORMAT='ockc-local-h2-backup-v1'
class MaintenanceError(Exception): pass

def safe_path(raw):
    if '://' in str(raw) or '\x00' in str(raw): raise MaintenanceError('只接受本地文件路径，不接受连接地址')
    p=Path(raw).absolute()
    if '..' in p.parts: raise MaintenanceError('路径不能包含上级目录跳转')
    for part in [p,*p.parents]:
        if part.is_symlink(): raise MaintenanceError('为避免误读或覆盖，路径及父目录不能是符号链接')
    return p

def target_path(raw):
    p=safe_path(raw)
    if not p.parent.is_dir(): raise MaintenanceError('目标父目录必须已存在')
    if p.exists(): raise MaintenanceError('目标已存在；不会覆盖现有文件')
    return p

def digest(stream):
    h=hashlib.sha256();n=0
    while chunk:=stream.read(1024*1024):
        n+=len(chunk)
        if n>MAX_BYTES: raise MaintenanceError('数据库超过本地工具 2 GiB 上限')
        h.update(chunk)
    return h.hexdigest(),n

@contextlib.contextmanager
def offline_database(raw, confirmed):
    if not confirmed: raise MaintenanceError('须先停止全部数据库使用者，并显式传入 --confirm-stopped')
    p=safe_path(raw)
    if not p.name.endswith('.mv.db'): raise MaintenanceError('只支持 H2 的 .mv.db 文件；不接受 MySQL、内存库或连接 URL')
    try: fd=os.open(p,os.O_RDWR|os.O_NOFOLLOW)
    except OSError as e: raise MaintenanceError('数据库不存在或不可安全读取') from e
    try:
        st=os.fstat(fd)
        if not stat.S_ISREG(st.st_mode) or st.st_nlink!=1: raise MaintenanceError('数据库必须是无硬链接的普通文件')
        if st.st_size<=0 or st.st_size>MAX_BYTES: raise MaintenanceError('数据库为空或超过本地工具 2 GiB 上限')
        try: fcntl.lockf(fd,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except OSError as e: raise MaintenanceError('数据库正在使用或不能取得独占锁；请完全停止应用后再备份') from e
        with os.fdopen(fd,'rb',closefd=False) as source: yield p,source,st
        now=os.fstat(fd)
        if (st.st_size,st.st_mtime_ns,st.st_ino)!=(now.st_size,now.st_mtime_ns,now.st_ino): raise MaintenanceError('数据库在备份期间变化，本次备份无效')
    finally: os.close(fd)

def inspect_archive(raw, sink=None):
    p=safe_path(raw)
    if not p.is_file() or p.stat().st_size>MAX_BYTES+1024*1024: raise MaintenanceError('备份文件不存在或超出容量上限')
    manifest=None;db_hash=None;db_size=None;seen=set()
    try:
        with tarfile.open(p,'r:gz') as archive:
            for member in archive:
                if member.name not in {'manifest.json','database.mv.db'} or member.name in seen or not member.isfile(): raise MaintenanceError('备份包含额外、重复或不安全条目')
                seen.add(member.name)
                if member.size<=0 or member.size>MAX_BYTES: raise MaintenanceError('备份条目容量不正确')
                stream=archive.extractfile(member)
                if member.name=='manifest.json':
                    if member.size>16384: raise MaintenanceError('备份清单过大')
                    manifest=json.loads(stream.read())
                else:
                    h=hashlib.sha256();db_size=0
                    while chunk:=stream.read(1024*1024):
                        db_size+=len(chunk)
                        if db_size>MAX_BYTES: raise MaintenanceError('解压数据超出容量上限')
                        h.update(chunk)
                        if sink is not None: sink.write(chunk)
                    db_hash=h.hexdigest()
            if seen!={'manifest.json','database.mv.db'}: raise MaintenanceError('备份条目不完整')
        if not isinstance(manifest,dict) or manifest.get('format')!=FORMAT or type(manifest.get('bytes')) is not int or manifest.get('bytes')!=db_size or manifest.get('sha256')!=db_hash: raise MaintenanceError('备份格式、字节数或校验和不匹配')
        return {'format':FORMAT,'bytes':db_size,'sha256':db_hash,'createdAt':manifest.get('createdAt'),'verified':True}
    except (OSError,tarfile.TarError,ValueError,UnicodeError,EOFError) as e: raise MaintenanceError('备份损坏或不是受支持的快照文件') from e

def backup(database,output,confirmed=False):
    out=target_path(output)
    with tempfile.TemporaryDirectory(prefix='.ockc-backup-',dir=out.parent) as temp:
        staged=Path(temp)/'snapshot.tar.gz'
        with offline_database(database,confirmed) as (_,source,_):
            sha,size=digest(source);source.seek(0)
            info={'format':FORMAT,'createdAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'bytes':size,'sha256':sha,'sensitive':True,'warning':'含账号、作品和图片；不加密。仅在完全停机后备份，在新路径恢复。校验和不证明来源可信。'}
            with open(staged,'xb') as f:
                os.chmod(staged,0o600)
                with tarfile.open(fileobj=f,mode='w:gz') as archive:
                    raw=json.dumps(info,ensure_ascii=False).encode();meta=tarfile.TarInfo('manifest.json');meta.size=len(raw);meta.mode=0o600;archive.addfile(meta,io.BytesIO(raw))
                    db=tarfile.TarInfo('database.mv.db');db.size=size;db.mode=0o600;archive.addfile(db,source)
                f.flush();os.fsync(f.fileno())
            result=inspect_archive(staged)
        # Database lock and final unchanged-file check have both completed before publication.
        try: os.link(staged,out)  # atomic no-clobber, even if a different process creates the target
        except OSError as e: raise MaintenanceError('无法创建备份目标；不会覆盖现有文件') from e
    return result

def restore(archive,destination,confirmed=False):
    if not confirmed: raise MaintenanceError('恢复前须停止目标应用，并显式传入 --confirm-stopped')
    dest=target_path(destination)
    if not dest.name.endswith('.mv.db'): raise MaintenanceError('恢复目标必须是新的 .mv.db 文件')
    base=Path(str(dest)[:-6])
    if any(Path(str(base)+suffix).exists() for suffix in ['.lock.db','.trace.db','.temp.db','.newFile','.tempFile']): raise MaintenanceError('目标旁存在数据库运行文件；请选择全新的目录和文件名')
    with tempfile.TemporaryDirectory(prefix='.ockc-restore-',dir=dest.parent) as temp:
        staged=Path(temp)/'database.mv.db'
        with open(staged,'xb') as f:
            os.chmod(staged,0o600);result=inspect_archive(archive,f);f.flush();os.fsync(f.fileno())
        try: os.link(staged,dest)
        except OSError as e: raise MaintenanceError('恢复目标已存在或不可创建；未覆盖原文件') from e
    return result

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__);sub=parser.add_subparsers(dest='action',required=True)
    b=sub.add_parser('backup');b.add_argument('--database',required=True);b.add_argument('--output',required=True);b.add_argument('--confirm-stopped',action='store_true')
    v=sub.add_parser('verify');v.add_argument('--archive',required=True)
    r=sub.add_parser('restore');r.add_argument('--archive',required=True);r.add_argument('--destination',required=True);r.add_argument('--confirm-stopped',action='store_true')
    a=parser.parse_args(argv)
    try:
        result=backup(a.database,a.output,a.confirm_stopped) if a.action=='backup' else restore(a.archive,a.destination,a.confirm_stopped) if a.action=='restore' else inspect_archive(a.archive)
        print(json.dumps({'ok':True,**result},ensure_ascii=False));return 0
    except (MaintenanceError,OSError) as e: print(json.dumps({'ok':False,'message':str(e)},ensure_ascii=False));return 2
if __name__=='__main__': raise SystemExit(main())
