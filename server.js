'use strict';

const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { randomBytes } = require('node:crypto');

const PORT = Number(process.env.PORT || 3000);
const PUBLIC = path.join(__dirname, 'public');
const MAX_BODY = 4096;
const MAX_MESSAGE = 500;
const SESSION_TIMEOUT = 30 * 60 * 1000;
const INTERESTS = new Set(['Gaming', 'Music', 'Movies', 'Sports', 'Anime', 'Art', 'Tech', 'Books', 'Study', 'Travel']);
const MIME = { '.html':'text/html; charset=utf-8', '.css':'text/css; charset=utf-8', '.js':'text/javascript; charset=utf-8', '.svg':'image/svg+xml' };

const sessions = new Map();
const waiting = new Set();
const reports = [];
const ipCreation = new Map();

function token() { return randomBytes(24).toString('hex'); }
function json(res, code, data) { res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' }); res.end(JSON.stringify(data)); }
function error(res, code, message) { return json(res, code, { error: message }); }
function headers(res) {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'no-referrer');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
  res.setHeader('Content-Security-Policy', "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; connect-src 'self'");
}
function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', chunk => { data += chunk; if (Buffer.byteLength(data) > MAX_BODY) { reject(new Error('Request too large')); req.destroy(); } });
    req.on('end', () => { try { resolve(JSON.parse(data || '{}')); } catch { reject(new Error('Invalid JSON')); } });
    req.on('error', reject);
  });
}
function emit(session, evt) {
  if (!session) return;
  session.events.push(evt);
  if (session.events.length > 40) session.events.splice(0, session.events.length - 40);
  if (session.waiter) { const fn = session.waiter; session.waiter = null; fn(); }
}
function flush(session, res) {
  json(res, 200, { events: session.events.splice(0), status: session.status });
}
function requireSession(res, body) {
  if (!body || typeof body.token !== 'string' || !sessions.has(body.token)) {
    error(res, 401, 'Session expired. Refresh to try again.');
    return null;
  }
  const session = sessions.get(body.token);
  session.lastSeen = Date.now();
  return session;
}
function disconnect(session, reason = 'left') {
  waiting.delete(session.token);
  const partner = session.partner && sessions.get(session.partner);
  session.partner = null;
  session.status = 'idle';
  if (partner && partner.partner === session.token) {
    partner.partner = null;
    partner.status = 'idle';
    emit(partner, { type: 'partner-left', reason });
  }
}
function findPartner(session) {
  const people = Array.from(waiting)
    .map(t => sessions.get(t)).filter(other => other && other.token !== session.token && other.status === 'searching' &&
      !session.blocked.has(other.token) && !other.blocked.has(session.token));
  if (!people.length) return;
  const shared = people.filter(other => other.interests.some(i => session.interests.includes(i)));
  const pool = shared.length ? shared : people;
  const partner = pool[Math.floor(Math.random() * pool.length)];
  waiting.delete(session.token); waiting.delete(partner.token);
  session.partner = partner.token; partner.partner = session.token;
  session.status = 'connected'; partner.status = 'connected';
  const common = session.interests.filter(i => partner.interests.includes(i));
  emit(session, { type: 'matched', common });
  emit(partner, { type: 'matched', common });
}
function start(session, interests) {
  if (session.partner || session.status === 'searching') disconnect(session);
  session.interests = Array.isArray(interests) ? [...new Set(interests)].filter(x => INTERESTS.has(x)).slice(0, 5) : [];
  session.status = 'searching'; session.queueSince = Date.now();
  waiting.add(session.token);
  emit(session, { type: 'searching' });
  findPartner(session);
}
function filterMessage(text) {
  if (/(https?:\/\/|www\.|\b(?:discord\.gg|t\.me|instagram\.com|snapchat\.com|wa\.me)\b)/iu.test(text)) return 'Links and invitations are disabled for safety.';
  if (/\b[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}\b/u.test(text)) return 'Do not share email addresses.';
  if (/(?:\+?\d[\s().-]*){8,}/u.test(text)) return 'Do not share phone numbers or other long numbers.';
  if (/(?:^|\s)@[A-Za-z0-9_.]{3,}/u.test(text)) return 'Please do not share social media handles.';
  // A light demo-only filter, NOT a substitute for real automated moderation and human review.
  if (/\b(?:nudes?|porn|sext|suicide|kill yourself|send pics|send photos)\b/iu.test(text)) return 'That message is not allowed here.';
  return null;
}
async function handleApi(req, res, route) {
  if (req.method !== 'POST') return error(res, 405, 'POST required');
  let body;
  try { body = await readBody(req); } catch { return error(res, 400, 'Invalid request'); }

  if (route === '/api/session') {
    const ip = (req.socket.remoteAddress || 'unknown');
    const now = Date.now();
    const history = (ipCreation.get(ip) || []).filter(t => now - t < 3600000);
    if (history.length >= 40) return error(res, 429, 'Too many sessions from this connection.');
    history.push(now); ipCreation.set(ip, history);
    const key = token();
    sessions.set(key, { token: key, createdAt: now, lastSeen: now, status: 'idle', partner: null,
      interests: [], blocked: new Set(), events: [], waiter: null, messages: [], msgTimes: [], skipTimes: [] });
    return json(res, 200, { token: key, online: sessions.size });
  }

  const session = requireSession(res, body);
  if (!session) return;
  if (route === '/api/poll') {
    if (session.events.length) return flush(session, res);
    // Long polling: no dependency or WebSocket package required.
    let done = false;
    const finish = () => {
      if (done) return;
      done = true; clearTimeout(timeout);
      res.removeListener('close', cancel);
      if (session.waiter === finish) session.waiter = null;
      if (!res.destroyed) flush(session, res);
    };
    const cancel = () => { if (done) return; done = true; clearTimeout(timeout); if (session.waiter === finish) session.waiter = null; };
    if (session.waiter) session.waiter();
    session.waiter = finish;
    const timeout = setTimeout(finish, 18000);
    res.on('close', cancel);
    return;
  }
  if (route === '/api/start' || route === '/api/next') {
    const now = Date.now();
    session.skipTimes = session.skipTimes.filter(t => now - t < 60000);
    if (session.skipTimes.length >= 15) return error(res, 429, 'Please slow down before looking for another chat.');
    session.skipTimes.push(now);
    start(session, body.interests);
    return json(res, 200, { ok: true, status: session.status });
  }
  if (route === '/api/cancel') {
    disconnect(session);
    return json(res, 200, { ok: true });
  }
  if (route === '/api/message') {
    if (session.status !== 'connected' || !session.partner) return error(res, 409, 'Not connected to a stranger.');
    if (typeof body.text !== 'string') return error(res, 400, 'Invalid message.');
    const msg = body.text.trim();
    if (!msg || msg.length > MAX_MESSAGE) return error(res, 400, 'Messages must be 1–500 characters.');
    const reject = filterMessage(msg);
    if (reject) return error(res, 422, reject);
    const now = Date.now();
    session.msgTimes = session.msgTimes.filter(t => now - t < 60000);
    if (session.msgTimes.length >= 25 || session.msgTimes.filter(t => now - t < 2500).length >= 4)
      return error(res, 429, 'You are sending messages too quickly.');
    session.msgTimes.push(now);
    const partner = sessions.get(session.partner);
    if (!partner || partner.partner !== session.token) return error(res, 409, 'Stranger has disconnected.');
    emit(partner, { type: 'message', text: msg, at: now });
    return json(res, 200, { ok: true });
  }
  if (route === '/api/report') {
    const other = session.partner;
    if (other) {
      session.blocked.add(other);
      reports.push({ timestamp: Date.now(), reason: ['harassment', 'sexual', 'personal', 'spam', 'other'].includes(body.reason) ? body.reason : 'other', reportedSession: other });
      if (reports.length > 500) reports.shift();
    }
    disconnect(session, 'reported');
    return json(res, 200, { ok: true, note: 'Report recorded in this demo. A public service needs active moderation.' });
  }
  if (route === '/api/leave') {
    disconnect(session);
    if (session.waiter) session.waiter();
    sessions.delete(session.token);
    return json(res, 200, { ok: true });
  }
  return error(res, 404, 'Endpoint not found');
}

const server = http.createServer((req, res) => {
  headers(res);
  let pathname;
  try { pathname = new URL(req.url, 'http://localhost').pathname; } catch { return error(res, 400, 'Bad URL'); }
  if (pathname.startsWith('/api/')) { handleApi(req, res, pathname).catch(() => { if (!res.headersSent) error(res, 500, 'Unexpected error'); }); return; }
  if (req.method !== 'GET' && req.method !== 'HEAD') return error(res, 405, 'Method not allowed');
  const routes = { '/': 'index.html', '/index.html': 'index.html', '/styles.css': 'styles.css', '/app.js': 'app.js' };
  const file = routes[pathname];
  if (!file) return error(res, 404, 'Not found');
  const filepath = path.join(PUBLIC, file);
  fs.readFile(filepath, (err, content) => {
    if (err) return error(res, 404, 'File not found');
    res.writeHead(200, { 'Content-Type': MIME[path.extname(filepath)] || 'application/octet-stream', 'Cache-Control': 'no-store' });
    res.end(req.method === 'HEAD' ? undefined : content);
  });
});

setInterval(() => {
  const now = Date.now();
  for (const s of sessions.values()) {
    if (now - s.lastSeen > SESSION_TIMEOUT) {
      disconnect(s);
      if (s.waiter) s.waiter();
      sessions.delete(s.token);
    }
  }
  for (const [ip, times] of ipCreation) {
    const recent = times.filter(t => now - t < 3600000);
    if (recent.length) ipCreation.set(ip, recent); else ipCreation.delete(ip);
  }
}, 60000).unref();

server.listen(PORT, () => console.log(`VoxChat running at http://localhost:${PORT}`));
