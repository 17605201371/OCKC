import contextlib,hashlib,io,json,os,stat,subprocess,sys,tarfile,tempfile,unittest
from pathlib import Path
import local_maintenance as m
import local_preflight as p

class LocalMaintenanceTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name);self.db=self.root/'community.mv.db';self.data=b'Local test data only\x00\xff'*200;self.db.write_bytes(self.data);self.archive=self.root/'snapshot.tar.gz'
    def tearDown(self):self.tmp.cleanup()
    def backup(self):return m.backup(self.db,self.archive,True)
    def bad_archive(self,members):
        path=self.root/'bad.tar.gz'
        with tarfile.open(path,'w:gz') as t:
            for name,value,kind in members:
                x=tarfile.TarInfo(name);x.type=kind;x.size=len(value) if kind==tarfile.REGTYPE else 0;x.linkname='/tmp/outside'
                t.addfile(x,io.BytesIO(value) if x.isfile() else None)
        return path
    def test_roundtrip_digest_bytes_and_private_modes(self):
        result=self.backup();target=self.root/'restored.mv.db';m.restore(self.archive,target,True);self.assertEqual(target.read_bytes(),self.data);self.assertEqual(result['sha256'],hashlib.sha256(self.data).hexdigest());self.assertEqual(stat.S_IMODE(self.archive.stat().st_mode),0o600);self.assertEqual(stat.S_IMODE(target.stat().st_mode),0o600)
    def test_explicit_stop_confirmation_required(self):
        with self.assertRaises(m.MaintenanceError):m.backup(self.db,self.archive)
        self.assertFalse(self.archive.exists());self.backup()
        with self.assertRaises(m.MaintenanceError):m.restore(self.archive,self.root/'new.mv.db')
    def test_output_and_restore_never_overwrite(self):
        self.backup();before=self.archive.read_bytes()
        with self.assertRaises(m.MaintenanceError):self.backup()
        self.assertEqual(before,self.archive.read_bytes())
        with self.assertRaises(m.MaintenanceError):m.restore(self.archive,self.db,True)
        self.assertEqual(self.db.read_bytes(),self.data)
    def test_cross_process_posix_lock_rejects_running_database(self):
        holder=subprocess.Popen([sys.executable,'-c','import fcntl,sys,time; f=open(sys.argv[1],"r+b");fcntl.lockf(f,fcntl.LOCK_EX);print("ready",flush=True);time.sleep(30)',str(self.db)],stdout=subprocess.PIPE,text=True)
        try:
            self.assertEqual(holder.stdout.readline().strip(),'ready')
            with self.assertRaisesRegex(m.MaintenanceError,'独占锁'):self.backup()
            self.assertFalse(self.archive.exists())
        finally:holder.terminate();holder.wait(timeout=5);holder.stdout.close()
    def test_source_and_parent_symlinks_rejected(self):
        link=self.root/'link.mv.db';link.symlink_to(self.db)
        with self.assertRaises(m.MaintenanceError):m.backup(link,self.archive,True)
        folder=self.root/'linked';folder.symlink_to(self.root,target_is_directory=True)
        with self.assertRaises(m.MaintenanceError):m.backup(folder/'community.mv.db',self.archive,True)
    def test_source_hardlink_rejected(self):
        os.link(self.db,self.root/'linked.mv.db')
        with self.assertRaises(m.MaintenanceError):self.backup()
    def test_remote_wrong_extension_empty_and_limit_rejected(self):
        for value in ['jdbc:h2:tcp://remote/db','https://example.test/db','./file.db']:
            with self.assertRaises(m.MaintenanceError):m.backup(value,self.archive,True)
        self.db.write_bytes(b'')
        with self.assertRaises(m.MaintenanceError):self.backup()
        self.db.write_bytes(self.data);old=m.MAX_BYTES;m.MAX_BYTES=32
        try:
            with self.assertRaises(m.MaintenanceError):self.backup()
        finally:m.MAX_BYTES=old
    def test_archive_rejects_path_traversal_and_extra_member(self):
        for name in ['../escape','/tmp/escape','dir/database.mv.db','unexpected']:
            path=self.bad_archive([(name,b'evil',tarfile.REGTYPE)])
            with self.assertRaises(m.MaintenanceError):m.inspect_archive(path)
    def test_archive_rejects_symbolic_hard_links_and_duplicate_names(self):
        for kind in [tarfile.SYMTYPE,tarfile.LNKTYPE,tarfile.DIRTYPE]:
            with self.assertRaises(m.MaintenanceError):m.inspect_archive(self.bad_archive([('database.mv.db',b'',kind)]))
        with self.assertRaises(m.MaintenanceError):m.inspect_archive(self.bad_archive([('manifest.json',b'{}',tarfile.REGTYPE),('manifest.json',b'{}',tarfile.REGTYPE)]))
    def test_corrupt_hash_no_destination_or_partial_files(self):
        raw=json.dumps({'format':m.FORMAT,'bytes':3,'sha256':'0'*64}).encode();bad=self.bad_archive([('manifest.json',raw,tarfile.REGTYPE),('database.mv.db',b'db!',tarfile.REGTYPE)])
        with self.assertRaises(m.MaintenanceError):m.restore(bad,self.root/'new.mv.db',True)
        self.assertFalse((self.root/'new.mv.db').exists());self.assertEqual(list(self.root.glob('.ockc-*')),[])
    def test_truncated_and_invalid_manifest_rejected(self):
        self.archive.write_bytes(b'not gzip')
        with self.assertRaises(m.MaintenanceError):m.inspect_archive(self.archive)
        bad=self.bad_archive([('manifest.json',b'[]',tarfile.REGTYPE),('database.mv.db',b'abc',tarfile.REGTYPE)])
        with self.assertRaises(m.MaintenanceError):m.inspect_archive(bad)
    def test_existing_sidecar_blocks_restore(self):
        self.backup();(self.root/'new.lock.db').write_text('running')
        with self.assertRaises(m.MaintenanceError):m.restore(self.archive,self.root/'new.mv.db',True)
    def test_failed_final_source_check_never_publishes(self):
        original=m.offline_database
        @contextlib.contextmanager
        def broken(*a):
            with original(*a) as payload:yield payload
            raise m.MaintenanceError('changed')
        m.offline_database=broken
        try:
            with self.assertRaises(m.MaintenanceError):self.backup()
            self.assertFalse(self.archive.exists());self.assertEqual(list(self.root.glob('.ockc-*')),[])
        finally:m.offline_database=original

class PreflightTest(unittest.TestCase):
    def test_safe_defaults_and_no_values_in_report(self):
        checks,warnings=p.config_checks({});self.assertTrue(all(x['ok'] for x in checks));self.assertTrue(warnings)
        secret='PrivateTestOnly!Long';checks,warnings=p.config_checks({'DEMO_SEED':'true','DEMO_PASSWORD':secret,'DATABASE_PASSWORD':secret});self.assertTrue(all(x['ok'] for x in checks));self.assertNotIn(secret,json.dumps([checks,warnings]))
    def test_seed_short_blank_and_oversized_password_rejected(self):
        for value in ['', 'short',' '*12,'中'*25]:self.assertTrue(any(not x['ok'] for x in p.config_checks({'DEMO_SEED':'true','DEMO_PASSWORD':value})[0]))
    def test_public_bind_invalid_port_and_network_database_rejected(self):
        for env in [{'SERVER_ADDRESS':'0.0.0.0'},{'PORT':'0'},{'PORT':'65536'},{'PORT':'x'},{'DATABASE_URL':'jdbc:mysql://remote/db'},{'DATABASE_URL':'jdbc:h2:file:db;INIT=RUNSCRIPT FROM bad'},{'DATABASE_URL':'jdbc:h2:file:db;AUTO_SERVER=TRUE'},{'DEMO_SEED':'maybe'},{'COOKIE_SECURE':'maybe'},{'DATABASE_URL':'jdbc:h2:file:'},{'DATABASE_URL':'jdbc:h2:file:db;FILE_LOCK=NO'},{'DATABASE_URL':'jdbc:h2:file:db; AUTO_SERVER = TRUE'}]:self.assertTrue(any(not x['ok'] for x in p.config_checks(env)[0]),env)
    def test_source_manifest_valid_tamper_missing_and_escape(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'source.txt').write_text('hello');item={'path':'source.txt','sha256':hashlib.sha256(b'hello').hexdigest()};manifest=root/'LOCAL-SOURCE-MANIFEST.json';manifest.write_text(json.dumps({'files':[item]}));self.assertTrue(p.verify_source(root));(root/'source.txt').write_text('changed');self.assertFalse(p.verify_source(root));item['path']='../outside';manifest.write_text(json.dumps({'files':[item]}));self.assertFalse(p.verify_source(root));manifest.unlink();self.assertFalse(p.verify_source(root))
    def test_source_duplicate_and_symlink_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'file').write_text('hello');item={'path':'file','sha256':hashlib.sha256(b'hello').hexdigest()};manifest=root/'LOCAL-SOURCE-MANIFEST.json';manifest.write_text(json.dumps({'files':[item,item]}));self.assertFalse(p.verify_source(root));(root/'link').symlink_to(root/'file');item['path']='link';manifest.write_text(json.dumps({'files':[item]}));self.assertFalse(p.verify_source(root))
if __name__=='__main__':unittest.main()
