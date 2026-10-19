import copy
import http.client
import json
import os
import tempfile
import threading
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch

from server import ApiError, Server, Store


class SyncTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.store=Store(os.path.join(self.temp.name,'test.db'))
        self.a=self.store.auth(self.credentials('alice'),True)
        self.b=self.store.auth(self.credentials('bob'),True)

    def tearDown(self):
        self.temp.cleanup()

    def credentials(self,name):
        return {'username':name,'password':'a-long-password','device_id':str(uuid.uuid4())}

    def record(self,title='讲座'):
        return {'id':str(uuid.uuid4()),'revision':str(uuid.uuid4()),'ancestors':[],
                'kind':'event','title':title,'deleted':False,'start_at':'2026-10-20T14:00:00','completed':''}

    def push(self,user,record,base=0,cursor=0):
        return self.store.sync(user,{'cursor':cursor,'mutations':[{'base_version':base,'record':record}]})

    def test_registration_identity_and_hashed_secrets(self):
        self.assertNotEqual(self.a['user_id'],self.b['user_id'])
        self.assertEqual(self.store.session(self.a['token'])['user_id'],self.a['user_id'])
        with self.store.connect() as db:
            self.assertNotEqual(db.execute('SELECT password FROM users LIMIT 1').fetchone()[0],'a-long-password')
            self.assertNotEqual(db.execute('SELECT token_hash FROM sessions LIMIT 1').fetchone()[0],self.a['token'])

    def test_username_normalization_duplicate(self):
        with self.assertRaises(ApiError) as error:self.store.auth(self.credentials('ALICE'),True)
        self.assertEqual(error.exception.status,409)

    def test_invalid_credentials(self):
        body=self.credentials('alice');body['password']='wrong-password'
        with self.assertRaises(ApiError):self.store.auth(body)

    def test_registration_controls(self):
        with patch.dict(os.environ,{'DAYFLOW_REGISTRATION':'false'}):
            with self.assertRaises(ApiError):self.store.auth(self.credentials('newuser'),True)
        with patch.dict(os.environ,{'DAYFLOW_INVITE_CODE':'secret-invite'}):
            with self.assertRaises(ApiError):self.store.auth(self.credentials('newuser'),True)
            body=self.credentials('newuser');body['invite_code']='secret-invite'
            self.assertIn('user_id',self.store.auth(body,True))

    def test_account_isolation_even_for_identical_record_ids(self):
        r=self.record();self.push(self.a,r)
        self.assertEqual(self.store.sync(self.b,{'cursor':0})['changes'],[])
        r2=copy.deepcopy(r);r2['title']='B private';self.push(self.b,r2)
        self.assertEqual(self.store.sync(self.a,{'cursor':0})['changes'][0]['record']['title'],'讲座')

    def test_conflict_does_not_overwrite(self):
        r=self.record();self.push(self.a,r)
        remote=copy.deepcopy(r);remote['title']='Device 2';remote['revision']=str(uuid.uuid4())
        self.push(self.a,remote,1)
        stale=copy.deepcopy(r);stale['title']='Offline device';stale['revision']=str(uuid.uuid4())
        result=self.push(self.a,stale,1)
        self.assertEqual(len(result['conflicts']),1)
        self.assertEqual(result['conflicts'][0]['record']['title'],'Device 2')

    def test_lost_response_retry_is_idempotent(self):
        r=self.record();first=self.push(self.a,r);second=self.push(self.a,r)
        self.assertEqual(first['accepted'],second['accepted'])
        self.assertEqual(len(self.store.sync(self.a,{'cursor':0})['changes']),1)

    def test_tombstone_propagation(self):
        r=self.record();self.push(self.a,r);r['deleted']=True;r['revision']=str(uuid.uuid4());self.push(self.a,r,1)
        changes=self.store.sync(self.a,{'cursor':0})['changes']
        self.assertTrue(changes[-1]['record']['deleted'])
        self.assertEqual(changes[-1]['version'],2)

    def test_pagination_excludes_own_push_until_next_pull(self):
        rows=[self.record(str(i)) for i in range(220)]
        for offset in range(0,220,100):
            self.store.sync(self.a,{'cursor':0,'mutations':[{'record':r,'base_version':0} for r in rows[offset:offset+100]]})
        page=self.store.sync(self.a,{'cursor':0});self.assertTrue(page['has_more']);self.assertEqual(len(page['changes']),200)
        last=self.store.sync(self.a,{'cursor':page['cursor']});self.assertEqual(len(last['changes']),20);self.assertFalse(last['has_more'])
        self.assertEqual(self.store.sync(self.a,{'cursor':last['cursor']})['changes'],[])

    def test_invalid_batch_is_atomic(self):
        r=self.record()
        with self.assertRaises(ApiError):self.store.sync(self.a,{'mutations':[{'record':r,'base_version':0},{'record':{'id':'bad'},'base_version':0}]})
        self.assertEqual(self.store.sync(self.a,{'cursor':0})['changes'],[])

    def test_stale_cursor_rejected(self):
        with self.assertRaises(ApiError) as error:self.store.sync(self.a,{'cursor':100})
        self.assertEqual(error.exception.status,409)

    def test_parallel_updates_have_single_winner(self):
        r=self.record();self.push(self.a,r)
        results=[]
        def update(title):
            row=copy.deepcopy(r);row['title']=title;row['revision']=str(uuid.uuid4());results.append(self.push(self.a,row,1))
        threads=[threading.Thread(target=update,args=(str(i),)) for i in range(6)]
        for t in threads:t.start()
        for t in threads:t.join()
        self.assertEqual(sum(len(r['accepted']) for r in results),1)
        self.assertEqual(sum(len(r['conflicts']) for r in results),5)

    def test_rate_limit(self):
        for _ in range(20):self.store.limit('peer')
        with self.assertRaises(ApiError) as error:self.store.limit('peer')
        self.assertEqual(error.exception.status,429)

    def test_http_auth_sync_logout_and_malformed_body(self):
        server=Server(('127.0.0.1',0),self.store);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        connection=http.client.HTTPConnection('127.0.0.1',server.server_port)
        def call(method,path,body=None,token=''):
            headers={'Content-Type':'application/json'}
            if token:headers['Authorization']='Bearer '+token
            connection.request(method,path,json.dumps(body) if body is not None else None,headers)
            response=connection.getresponse();return response.status,json.loads(response.read())
        try:
            self.assertEqual(call('GET','/health')[0],200)
            self.assertEqual(call('POST','/v1/sync',{'cursor':0})[0],401)
            self.assertEqual(call('POST','/v1/sync',[],self.a['token'])[0],400)
            self.assertEqual(call('POST','/v1/sync',{'cursor':0},self.a['token'])[0],200)
            self.assertEqual(call('POST','/v1/auth/logout',{},self.a['token'])[0],200)
            self.assertEqual(call('GET','/v1/me',token=self.a['token'])[0],401)
        finally:connection.close();server.shutdown();server.server_close();thread.join()

    def test_update_distribution_allowlist(self):
        folder=Path(self.temp.name)/'updates';folder.mkdir();(folder/'android-update.json').write_text('{"payload":"test"}')
        server=Server(('127.0.0.1',0),self.store);thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        connection=http.client.HTTPConnection('127.0.0.1',server.server_port)
        try:
            with patch.dict(os.environ,{'DAYFLOW_UPDATES_DIR':str(folder)}):
                connection.request('GET','/updates/android-update.json');response=connection.getresponse();self.assertEqual(response.status,200);self.assertEqual(json.loads(response.read()),{'payload':'test'})
                connection.request('GET','/updates/../server.py');response=connection.getresponse();self.assertEqual(response.status,404);response.read()
                connection.request('GET','/updates/missing.apk');response=connection.getresponse();self.assertEqual(response.status,404);response.read()
        finally:connection.close();server.shutdown();server.server_close();thread.join()


if __name__=='__main__':unittest.main(verbosity=2)
