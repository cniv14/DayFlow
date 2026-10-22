"""DayFlow optional sync server. Python 3.12+, no third-party dependencies.

The app works without this service. Run behind a TLS reverse proxy on the cloud.
Records are isolated by account, IDs are client UUIDs, versions are server CAS.
"""
import argparse
import hashlib
import hmac
import json
import io
import tempfile
import os
import re
import secrets
import sqlite3
import threading
import time
import uuid
from pathlib import Path
from contextlib import contextmanager
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

MAX_BODY = 2 * 1024 * 1024  # Unauthenticated control requests only.
MAX_SYNC_BODY = max(0, int(os.getenv("DAYFLOW_MAX_SYNC_BYTES", "0")))  # 0: no product size quota.
KINDS = {"task", "event", "note", "inbox", "course", "project", "type"}
TOKEN_TTL = 30 * 86400


class ApiError(Exception):
    def __init__(self, status, message):
        self.status, self.message = status, message


class Store:
    def __init__(self, path):
        self.path = path
        with self.connect() as db:
            db.executescript('''
                PRAGMA journal_mode=WAL;
                CREATE TABLE IF NOT EXISTS users(
                    id TEXT PRIMARY KEY, username TEXT NOT NULL UNIQUE,
                    salt TEXT NOT NULL, password TEXT NOT NULL);
                CREATE TABLE IF NOT EXISTS devices(
                    user_id TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL,
                    PRIMARY KEY(user_id,id));
                CREATE TABLE IF NOT EXISTS sessions(
                    token_hash TEXT PRIMARY KEY, user_id TEXT NOT NULL,
                    device_id TEXT NOT NULL, expires INTEGER NOT NULL);
                CREATE TABLE IF NOT EXISTS records(
                    user_id TEXT NOT NULL, id TEXT NOT NULL, version INTEGER NOT NULL,
                    body TEXT NOT NULL, PRIMARY KEY(user_id,id));
                CREATE TABLE IF NOT EXISTS changes(
                    seq INTEGER PRIMARY KEY AUTOINCREMENT, user_id TEXT NOT NULL,
                    record_id TEXT NOT NULL, version INTEGER NOT NULL, body TEXT NOT NULL);
                CREATE INDEX IF NOT EXISTS changes_user_seq ON changes(user_id,seq);
                CREATE TABLE IF NOT EXISTS attempts(
                    key TEXT PRIMARY KEY, start INTEGER NOT NULL, count INTEGER NOT NULL);
            ''')

    @contextmanager
    def connect(self):
        db = sqlite3.connect(self.path, timeout=15)
        db.row_factory = sqlite3.Row
        try:
            with db:
                yield db
        finally:
            db.close()

    def limit(self, key):
        now = int(time.time())
        with self.connect() as db:
            db.execute("BEGIN IMMEDIATE")
            db.execute("DELETE FROM attempts WHERE start<?", (now - 900,))
            row = db.execute("SELECT count FROM attempts WHERE key=?", (key,)).fetchone()
            if row and row['count'] >= 20:
                raise ApiError(429, "尝试次数过多，请15分钟后再试")
            db.execute("INSERT INTO attempts VALUES(?,?,1) ON CONFLICT(key) DO UPDATE SET count=count+1", (key, now))

    def runtime_limit(self, user_id):
        # Account-wide, persistent quota shared by all sessions/devices and restarts.
        now = int(time.time())
        window = max(60, int(os.getenv('DAYFLOW_RUNTIME_WINDOW_SECONDS', '3600')))
        maximum = max(1, int(os.getenv('DAYFLOW_RUNTIME_REQUESTS', '10')))
        with self.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            db.execute('CREATE TABLE IF NOT EXISTS runtime_requests(user_id TEXT PRIMARY KEY, start INTEGER NOT NULL, count INTEGER NOT NULL)')
            db.execute('DELETE FROM runtime_requests WHERE start<=?', (now-window,))
            row = db.execute('SELECT start,count FROM runtime_requests WHERE user_id=?', (user_id,)).fetchone()
            if row and row['count'] >= maximum:
                error = ApiError(429, '热更新请求过多，请稍后再试')
                error.retry_after = max(1, row['start'] + window - now)
                raise error
            db.execute('INSERT INTO runtime_requests VALUES(?,?,1) ON CONFLICT(user_id) DO UPDATE SET count=count+1', (user_id, now))

    @staticmethod
    def password_hash(password, salt):
        return hashlib.scrypt(password.encode(), salt=bytes.fromhex(salt), n=16384, r=8, p=1).hex()

    def auth(self, body, register=False):
        username = str(body.get('username', '')).strip().lower()
        password = body.get('password', '')
        device_id = str(body.get('device_id', ''))
        if not re.fullmatch(r'[a-z0-9_.-]{3,40}', username):
            raise ApiError(400, '用户名需为3–40位英文字母、数字、点、横线或下划线')
        if not isinstance(password, str) or not 10 <= len(password) <= 128:
            raise ApiError(400, '密码长度需为10–128个字符')
        try:
            uuid.UUID(device_id)
        except ValueError:
            raise ApiError(400, 'device_id 必须是 UUID')
        with self.connect() as db:
            if register:
                if os.getenv('DAYFLOW_REGISTRATION', 'true').lower() != 'true':
                    raise ApiError(403, '服务器已关闭注册')
                invite = os.getenv('DAYFLOW_INVITE_CODE', '')
                if invite and not hmac.compare_digest(invite, str(body.get('invite_code', ''))):
                    raise ApiError(403, '邀请码无效')
                salt = secrets.token_hex(16)
                try:
                    db.execute('INSERT INTO users VALUES(?,?,?,?)',
                               (str(uuid.uuid4()), username, salt, self.password_hash(password, salt)))
                except sqlite3.IntegrityError:
                    raise ApiError(409, '用户名不可用')
            row = db.execute('SELECT * FROM users WHERE username=?', (username,)).fetchone()
            # Same password derivation cost for an unknown account.
            hashed = self.password_hash(password, row['salt'] if row else '00' * 16)
            if not row or not hmac.compare_digest(row['password'], hashed):
                raise ApiError(401, '用户名或密码错误')
            name = str(body.get('device_name', 'Device'))[:80]
            db.execute('INSERT INTO devices VALUES(?,?,?) ON CONFLICT(user_id,id) DO UPDATE SET name=excluded.name',
                       (row['id'], device_id, name))
            token = secrets.token_urlsafe(32)
            expires = int(time.time()) + TOKEN_TTL
            db.execute('DELETE FROM sessions WHERE expires<?', (int(time.time()),))
            db.execute('INSERT INTO sessions VALUES(?,?,?,?)',
                       (hashlib.sha256(token.encode()).hexdigest(), row['id'], device_id, expires))
            return {'token': token, 'user_id': row['id'], 'device_id': device_id, 'expires_at': expires}

    def session(self, token):
        with self.connect() as db:
            row = db.execute('SELECT * FROM sessions WHERE token_hash=? AND expires>?',
                             (hashlib.sha256(token.encode()).hexdigest(), int(time.time()))).fetchone()
        if not row:
            raise ApiError(401, '登录已失效，请重新登录')
        return dict(row)

    @staticmethod
    def validate_record(record):
        if not isinstance(record, dict):
            raise ApiError(400, '事项必须是对象')
        try:
            uuid.UUID(str(record.get('id', '')))
        except ValueError:
            raise ApiError(400, '事项 id 必须是 UUID')
        if not isinstance(record.get('kind'), str) or record.get('kind') not in KINDS:
            raise ApiError(400, '未知事项类型')
        if not isinstance(record.get('title'), str) or len(record['title']) > 1000:
            raise ApiError(400, '标题缺失或过长')
        if '_dayflow_preview' in record:
            raise ApiError(400, '设备私有预览不能上传')
        try:
            uuid.UUID(record.get('revision', ''))
            ancestors = record.get('ancestors', [])
            if not isinstance(ancestors, list) or len(ancestors) > 256:
                raise ValueError()
            for revision in ancestors:
                uuid.UUID(revision)
        except (ValueError, TypeError, AttributeError):
            raise ApiError(400, '无效修订或祖先历史')
        if 'content' in record and not isinstance(record['content'], str):
            raise ApiError(400, 'content 必须为字符串')
        if not isinstance(record.get('deleted', False), bool):
            raise ApiError(400, 'deleted 必须为布尔值')
        for key in ('start_at', 'end_at', 'completed'):
            if key in record and not isinstance(record[key], str):
                raise ApiError(400, key + ' 必须为字符串')
        return record

    def sync(self, user, body):
        mutations = body.get('mutations', [])
        cursor = body.get('cursor', 0)
        if not isinstance(mutations, list) or len(mutations) > 100 or type(cursor) is not int or cursor < 0:
            raise ApiError(400, '每批最多100条，cursor 为非负整数')
        # Validate entire batch before writing anything.
        for mutation in mutations:
            if not isinstance(mutation, dict) or type(mutation.get('base_version')) is not int or mutation['base_version'] < 0:
                raise ApiError(400, 'base_version 必须为非负整数')
            self.validate_record(mutation.get('record'))
        accepted, conflicts = [], []
        with self.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            uid = user['user_id']
            # Snapshot excludes this push. Clients acknowledge accepted versions themselves.
            high = db.execute('SELECT COALESCE(MAX(seq),0) FROM changes WHERE user_id=?', (uid,)).fetchone()[0]
            if cursor > high:
                raise ApiError(409, '同步游标超出服务器范围，请重置同步状态')
            # Page by bytes as well as count so compressed image notes cannot
            # create multi-megabyte responses or force clients to load all data.
            changes, page_bytes = [], 0
            for change in db.execute('SELECT seq,version,body FROM changes WHERE user_id=? AND seq>? AND seq<=? ORDER BY seq LIMIT 200',
                                     (uid, cursor, high)):
                size = len(change['body'].encode('utf-8')) + 128
                if changes and page_bytes + size > 512 * 1024:
                    break
                changes.append(change)
                page_bytes += size
            next_cursor = changes[-1]['seq'] if changes else high
            for mutation in mutations:
                record = dict(mutation['record'])
                rid = record['id']
                row = db.execute('SELECT version,body FROM records WHERE user_id=? AND id=?', (uid, rid)).fetchone()
                version = row['version'] if row else 0
                # Exact retry is idempotent even if the response was lost.
                if row and json.loads(row['body']) == record:
                    accepted.append({'id': rid, 'version': version})
                    continue
                if mutation['base_version'] != version:
                    conflicts.append({'id': rid, 'version': version, 'record': json.loads(row['body']) if row else None})
                    continue
                version += 1
                encoded = json.dumps(record, ensure_ascii=False, separators=(',', ':'))
                db.execute('INSERT INTO records VALUES(?,?,?,?) ON CONFLICT(user_id,id) DO UPDATE SET version=excluded.version,body=excluded.body',
                           (uid, rid, version, encoded))
                db.execute('INSERT INTO changes(user_id,record_id,version,body) VALUES(?,?,?,?)', (uid, rid, version, encoded))
                accepted.append({'id': rid, 'version': version})
            return {'accepted': accepted, 'conflicts': conflicts,
                    'changes': [{'seq': r['seq'], 'version': r['version'], 'record': json.loads(r['body'])} for r in changes],
                    'cursor': next_cursor, 'has_more': next_cursor < high}


class Server(ThreadingHTTPServer):
    daemon_threads = True
    def __init__(self, address, store):
        self.store = store
        self.slots = threading.BoundedSemaphore(8)
        super().__init__(address, Handler)

    def process_request(self, request, client_address):
        # Limit active request threads, including expensive password hashing.
        if not self.slots.acquire(blocking=False):
            request.close()
            return
        try:
            super().process_request(request, client_address)
        except Exception:
            self.slots.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self.slots.release()


class Handler(BaseHTTPRequestHandler):
    server_version = 'DayFlow/1.2'
    def setup(self):
        super().setup()
        self.connection.settimeout(15)

    def log_message(self, fmt, *args):
        # No request URLs, content, credentials or tokens in access logs.
        pass

    def respond(self, status, body, retry_after=None):
        data = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(data)))
        if retry_after is not None:
            self.send_header('Retry-After', str(retry_after))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.end_headers()
        self.wfile.write(data)

    def user(self):
        header = self.headers.get('Authorization', '')
        if not header.startswith('Bearer '):
            raise ApiError(401, '需要登录')
        return self.server.store.session(header[7:])

    def do_GET(self):
        self.dispatch(False)

    def do_POST(self):
        self.dispatch(True)

    def dispatch(self, post):
        try:
            path = urlparse(self.path).path
            store = self.server.store
            if not post and path.startswith('/updates/'):
                self.serve_update(path)
                return
            body = {}
            # Authenticate before accepting a potentially large sync body.
            sync_user = self.user() if post and path == '/v1/sync' else None
            if post:
                if self.headers.get('Transfer-Encoding'):
                    raise ApiError(400, '不支持分块请求')
                try:
                    length = int(self.headers.get('Content-Length', 0))
                except ValueError:
                    raise ApiError(400, '无效长度')
                limit = MAX_SYNC_BODY if sync_user else MAX_BODY
                if length <= 0 or (limit and length > limit):
                    raise ApiError(413, '请求体为空或超过服务器管理员配置')
                try:
                    # Spool incoming bytes to disk instead of holding an extra full
                    # byte array while decoding JSON. A page can contain one large note.
                    with tempfile.SpooledTemporaryFile(max_size=1024 * 1024, dir=Path(store.path).parent) as incoming:
                        remaining = length
                        while remaining:
                            block = self.rfile.read(min(65536, remaining))
                            if not block:
                                raise ApiError(400, '请求体未传输完整')
                            incoming.write(block)
                            remaining -= len(block)
                        incoming.seek(0)
                        body = json.load(io.TextIOWrapper(incoming, encoding='utf-8'))
                except (ValueError, UnicodeError, RecursionError):
                    raise ApiError(400, '无效 JSON')
                if not isinstance(body, dict):
                    raise ApiError(400, '请求体必须为对象')
            if path == '/health' and not post:
                result = {'ok': True, 'protocol': 1, 'service': 'dayflow', 'capabilities': ['types', 'recurrence', 'opaque_fields', 'large_records', 'cas', 'runtime_modules_v1'], 'version': '1.2.0', 'limits': {'sync_body_bytes': MAX_SYNC_BODY, 'record_bytes': 0, 'mutations_per_batch': 100, 'changes_per_page': 200}}
            elif path in ('/v1/auth/register', '/v1/auth/login') and post:
                store.limit('auth:' + self.client_address[0])
                result = store.auth(body, path.endswith('register'))
            elif path == '/v1/sync' and post:
                result = store.sync(sync_user, body)
            elif path == '/v1/runtime-update' and not post:
                user = self.user()
                store.runtime_limit(user['user_id'])
                folder = Path(os.getenv('DAYFLOW_UPDATES_DIR', 'updates')).resolve()
                file = folder / 'runtime-update.json'
                if file.is_symlink() or not file.is_file() or file.resolve().parent != folder:
                    raise ApiError(404, '尚未发布功能热更新包')
                with file.open('rb') as incoming:
                    raw = incoming.read(12 * 1024 * 1024 + 1)
                if len(raw) > 12 * 1024 * 1024:
                    raise ApiError(503, '热更新包超过客户端支持范围')
                try:
                    result = json.loads(raw)
                except (ValueError, UnicodeError):
                    raise ApiError(503, '热更新包格式错误')
                if not isinstance(result, dict) or not isinstance(result.get('payload'), str) or not isinstance(result.get('signature'), str):
                    raise ApiError(503, '热更新包格式错误')
            elif path == '/v1/me' and not post:
                user = self.user()
                with store.connect() as db:
                    result = {'user_id': user['user_id'], 'devices': [dict(r) for r in db.execute('SELECT id,name FROM devices WHERE user_id=?', (user['user_id'],))]}
            elif path == '/v1/auth/logout' and post:
                user = self.user()
                with store.connect() as db:
                    db.execute('DELETE FROM sessions WHERE token_hash=?', (user['token_hash'],))
                result = {'ok': True}
            else:
                raise ApiError(404, '接口不存在')
            self.respond(200, result)
        except ApiError as error:
            self.respond(error.status, {'error': error.message}, getattr(error, 'retry_after', None))
        except (BrokenPipeError, ConnectionResetError, TimeoutError):
            pass
        except Exception:
            self.respond(500, {'error': '服务内部错误'})

    def serve_update(self, path):
        allowed = {'android-update.json', 'windows-update.json', 'DayFlow-android.apk', 'DayFlow-windows.zip'}
        name = path.removeprefix('/updates/')
        if name not in allowed:
            raise ApiError(404, '更新资源不存在')
        folder = Path(os.getenv('DAYFLOW_UPDATES_DIR', 'updates')).resolve()
        file = folder / name
        if file.is_symlink() or not file.is_file() or file.resolve().parent != folder:
            raise ApiError(404, '尚未发布更新包')
        with file.open('rb') as stream:
            self.send_response(200)
            self.send_header('Content-Type', 'application/json' if name.endswith('.json') else 'application/octet-stream')
            self.send_header('Content-Length', str(os.fstat(stream.fileno()).st_size))
            self.send_header('Cache-Control', 'no-cache')
            self.send_header('X-Content-Type-Options', 'nosniff')
            self.end_headers()
            while chunk := stream.read(65536):
                self.wfile.write(chunk)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--host', default=os.getenv('DAYFLOW_HOST', '0.0.0.0'))
    parser.add_argument('--port', type=int, default=int(os.getenv('DAYFLOW_PORT', '8765')))
    parser.add_argument('--db', default=os.getenv('DAYFLOW_DB', 'dayflow.db'))
    args = parser.parse_args()
    print(f'DayFlow sync listening on {args.host}:{args.port}', flush=True)
    Server((args.host, args.port), Store(args.db)).serve_forever()
