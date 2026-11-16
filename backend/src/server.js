import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import { pool } from './db.js';

const app = express();
app.use(helmet());
app.use(cors({ origin: process.env.CORS_ORIGIN === '*' ? true : (process.env.CORS_ORIGIN || '').split(',') }));
app.use(express.json({ limit: '1mb' }));
const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET) throw new Error('JWT_SECRET is required');

function auth(req, res, next) {
  const token = (req.headers.authorization || '').replace(/^Bearer\\s+/i, '');
  try { req.user = jwt.verify(token, JWT_SECRET); next(); }
  catch { res.status(401).json({ error: 'unauthorized' }); }
}
app.get('/health', async (_req, res) => {
  try { await pool.query('SELECT 1'); res.json({ ok: true, service: 'VISION PRO API' }); }
  catch { res.status(503).json({ ok: false, error: 'database_unavailable' }); }
});
app.post('/api/auth/register', async (req, res) => {
  const { email, password, displayName } = req.body || {};
  if (!email || !/^\\S+@\\S+\\.\\S+$/.test(email) || !password || password.length < 10)
    return res.status(400).json({ error: 'valid_email_and_password_min_10_chars_required' });
  try {
    const hash = await bcrypt.hash(password, 12);
    const { rows } = await pool.query(
      'INSERT INTO app_users(email,password_hash,display_name) VALUES($1,$2,$3) RETURNING id,email,display_name,role',
      [email.toLowerCase(), hash, displayName || null]);
    const user = rows[0];
    const token = jwt.sign({ sub: user.id, role: user.role }, JWT_SECRET, { expiresIn: '7d' });
    res.status(201).json({ user, token });
  } catch (e) {
    if (e.code === '23505') return res.status(409).json({ error: 'email_already_registered' });
    res.status(500).json({ error: 'registration_failed' });
  }
});
app.post('/api/auth/login', async (req, res) => {
  const { email, password } = req.body || {};
  const { rows } = await pool.query('SELECT id,email,password_hash,display_name,role,is_active FROM app_users WHERE email=$1', [(email || '').toLowerCase()]);
  const u = rows[0];
  if (!u || !u.is_active || !u.password_hash || !(await bcrypt.compare(password || '', u.password_hash)))
    return res.status(401).json({ error: 'invalid_credentials' });
  const token = jwt.sign({ sub: u.id, role: u.role }, JWT_SECRET, { expiresIn: '7d' });
  res.json({ user: { id:u.id,email:u.email,display_name:u.display_name,role:u.role }, token });
});
app.get('/api/catalog/:type', async (req, res) => {
  const type = req.params.type;
  if (!['live','movie','series'].includes(type)) return res.status(400).json({ error: 'invalid_type' });
  const { rows } = await pool.query(
    'SELECT id,title,description,poster_url,backdrop_url,content_type,stream_url,release_year,duration_seconds,category_id FROM content_items WHERE content_type=$1 AND is_published=true ORDER BY created_at DESC LIMIT 300', [type]);
  res.json({ items: rows });
});
app.get('/api/categories/:type', async (req, res) => {
  const type = req.params.type;
  if (!['live','movie','series'].includes(type)) return res.status(400).json({ error: 'invalid_type' });
  const { rows } = await pool.query('SELECT id,name,slug,content_type,sort_order FROM content_categories WHERE content_type=$1 AND is_active=true ORDER BY sort_order,name', [type]);
  res.json({ categories: rows });
});
app.get('/api/me', auth, async (req, res) => {
  const { rows } = await pool.query('SELECT id,email,display_name,role FROM app_users WHERE id=$1 AND is_active=true', [req.user.sub]);
  if (!rows[0]) return res.status(404).json({ error: 'user_not_found' });
  res.json({ user: rows[0] });
});
app.post('/api/playlists', auth, async (req, res) => {
  const { name, playlistUrl, sourceFormat = 'm3u', rightsConfirmed } = req.body || {};
  if (!name || !rightsConfirmed) return res.status(400).json({ error: 'name_and_rights_confirmation_required' });
  const { rows } = await pool.query(
    'INSERT INTO user_playlists(user_id,name,playlist_url,source_format,rights_confirmed) VALUES($1,$2,$3,$4,true) RETURNING id,name,source_format,rights_confirmed,created_at',
    [req.user.sub, name, playlistUrl || null, sourceFormat]);
  res.status(201).json({ playlist: rows[0] });
});
app.use((err, _req, res, _next) => { console.error(err); res.status(500).json({ error: 'internal_server_error' }); });
const port = Number(process.env.PORT || 10000);
app.listen(port, '0.0.0.0', () => console.log(`VISION PRO API listening on ${port}`));
