import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcryptjs';
import { pool } from './db.js';

const app = express();
const allowedOrigins = (process.env.CORS_ORIGIN || '').split(',').map(v => v.trim()).filter(Boolean);
app.use(helmet());
app.use(cors({ origin: allowedOrigins.length ? allowedOrigins : false }));
app.use(express.json({ limit: '1mb' }));

const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET || JWT_SECRET.length < 32) throw new Error('JWT_SECRET must be configured with at least 32 characters');

function auth(req, res, next) {
  const token = (req.headers.authorization || '').replace(/^Bearer\s+/i, '');
  try {
    const payload = jwt.verify(token, JWT_SECRET);
    req.user = { id: payload.sub, role: payload.role };
    next();
  } catch {
    return res.status(401).json({ error: 'unauthorized' });
  }
}
function adminOnly(req, res, next) {
  if (req.user?.role !== 'admin') return res.status(403).json({ error: 'admin_required' });
  next();
}
function validHttpsUrl(value) {
  if (!value) return true;
  try { return new URL(value).protocol === 'https:'; } catch { return false; }
}

app.get('/', (_req, res) => res.json({ name: 'VISION PRO API', health: '/health' }));
app.get('/health', async (_req, res) => {
  try {
    await pool.query('SELECT 1');
    res.json({ ok: true, service: 'VISION PRO API', database: 'connected' });
  } catch (error) {
    console.error('Health check failed:', error.message);
    res.status(503).json({ ok: false, error: 'database_unavailable' });
  }
});

app.post('/api/auth/register', async (req, res) => {
  try {
    const { email, password, displayName } = req.body || {};
    if (typeof email !== 'string' || !/^\S+@\S+\.\S+$/.test(email) ||
        typeof password !== 'string' || password.length < 10 ||
        (displayName != null && (typeof displayName !== 'string' || displayName.length > 80))) {
      return res.status(400).json({ error: 'valid_email_password_min_10_chars_and_display_name_required' });
    }
    const hash = await bcrypt.hash(password, 12);
    const { rows } = await pool.query(
      'INSERT INTO app_users(email,password_hash,display_name) VALUES($1,$2,$3) RETURNING id,email,display_name,role',
      [email.trim().toLowerCase(), hash, displayName?.trim() || null]
    );
    const user = rows[0];
    const token = jwt.sign({ sub: user.id, role: user.role }, JWT_SECRET, { expiresIn: '7d' });
    res.status(201).json({ user, token });
  } catch (error) {
    if (error.code === '23505') return res.status(409).json({ error: 'email_already_registered' });
    console.error('Registration failed:', error.message);
    res.status(500).json({ error: 'registration_failed' });
  }
});

app.post('/api/auth/login', async (req, res) => {
  try {
    const { email, password } = req.body || {};
    if (typeof email !== 'string' || typeof password !== 'string') {
      return res.status(400).json({ error: 'email_and_password_required' });
    }
    const { rows } = await pool.query(
      'SELECT id,email,password_hash,display_name,role,is_active FROM app_users WHERE email=$1',
      [email.trim().toLowerCase()]
    );
    const user = rows[0];
    if (!user || !user.is_active || !user.password_hash || !(await bcrypt.compare(password, user.password_hash))) {
      return res.status(401).json({ error: 'invalid_credentials' });
    }
    const token = jwt.sign({ sub: user.id, role: user.role }, JWT_SECRET, { expiresIn: '7d' });
    res.json({ user: { id: user.id, email: user.email, display_name: user.display_name, role: user.role }, token });
  } catch (error) {
    console.error('Login failed:', error.message);
    res.status(500).json({ error: 'login_failed' });
  }
});

app.get('/api/me', auth, async (req, res) => {
  try {
    const { rows } = await pool.query(
      'SELECT id,email,display_name,role FROM app_users WHERE id=$1 AND is_active=true',
      [req.user.id]
    );
    if (!rows[0]) return res.status(404).json({ error: 'user_not_found' });
    res.json({ user: rows[0] });
  } catch (error) {
    console.error('Profile lookup failed:', error.message);
    res.status(500).json({ error: 'profile_lookup_failed' });
  }
});

app.get('/api/catalog/:type', async (req, res) => {
  const type = req.params.type;
  if (!['live', 'movie', 'series'].includes(type)) return res.status(400).json({ error: 'invalid_type' });
  try {
    const { rows } = await pool.query(
      'SELECT id,title,description,poster_url,backdrop_url,content_type,stream_url,release_year,duration_seconds,category_id FROM content_items WHERE content_type=$1 AND is_published=true ORDER BY created_at DESC LIMIT 300',
      [type]
    );
    res.json({ items: rows });
  } catch (error) {
    console.error('Catalog query failed:', error.message);
    res.status(500).json({ error: 'catalog_unavailable' });
  }
});

app.get('/api/categories/:type', async (req, res) => {
  const type = req.params.type;
  if (!['live', 'movie', 'series'].includes(type)) return res.status(400).json({ error: 'invalid_type' });
  try {
    const { rows } = await pool.query(
      'SELECT id,name,slug,content_type,sort_order FROM content_categories WHERE content_type=$1 AND is_active=true ORDER BY sort_order,name',
      [type]
    );
    res.json({ categories: rows });
  } catch (error) {
    console.error('Categories query failed:', error.message);
    res.status(500).json({ error: 'categories_unavailable' });
  }
});

// Admin content management. Content must be owned, licensed, or public-domain.
app.post('/api/admin/content', auth, adminOnly, async (req, res) => {
  try {
    const { title, description, posterUrl, backdropUrl, contentType, streamUrl, releaseYear, categoryId, sourceType, rightsConfirmed } = req.body || {};
    if (typeof title !== 'string' || !title.trim() || !['live', 'movie', 'series'].includes(contentType) ||
        !['owned', 'licensed', 'public_domain'].includes(sourceType) || rightsConfirmed !== true ||
        !validHttpsUrl(streamUrl) || !validHttpsUrl(posterUrl) || !validHttpsUrl(backdropUrl)) {
      return res.status(400).json({ error: 'valid_content_and_rights_confirmation_required' });
    }
    const { rows } = await pool.query(
      `INSERT INTO content_items(title,description,poster_url,backdrop_url,content_type,stream_url,release_year,category_id,source_type,is_published)
       VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,true)
       RETURNING id,title,description,poster_url,backdrop_url,content_type,stream_url,release_year,category_id,source_type,is_published`,
      [title.trim(), description || null, posterUrl || null, backdropUrl || null, contentType, streamUrl || null, releaseYear || null, categoryId || null, sourceType]
    );
    res.status(201).json({ item: rows[0] });
  } catch (error) {
    console.error('Content creation failed:', error.message);
    res.status(500).json({ error: 'content_creation_failed' });
  }
});

app.patch('/api/admin/content/:id', auth, adminOnly, async (req, res) => {
  try {
    const { title, description, posterUrl, backdropUrl, streamUrl, releaseYear, categoryId, isPublished } = req.body || {};
    if ((streamUrl !== undefined && !validHttpsUrl(streamUrl)) ||
        (posterUrl !== undefined && !validHttpsUrl(posterUrl)) ||
        (backdropUrl !== undefined && !validHttpsUrl(backdropUrl))) {
      return res.status(400).json({ error: 'https_urls_only' });
    }
    const { rows } = await pool.query(
      `UPDATE content_items SET
       title=COALESCE($2,title), description=COALESCE($3,description),
       poster_url=COALESCE($4,poster_url), backdrop_url=COALESCE($5,backdrop_url),
       stream_url=COALESCE($6,stream_url), release_year=COALESCE($7,release_year),
       category_id=COALESCE($8,category_id), is_published=COALESCE($9,is_published), updated_at=now()
       WHERE id=$1 RETURNING id,title,content_type,is_published`,
      [req.params.id, title ?? null, description ?? null, posterUrl ?? null, backdropUrl ?? null, streamUrl ?? null, releaseYear ?? null, categoryId ?? null, typeof isPublished === 'boolean' ? isPublished : null]
    );
    if (!rows[0]) return res.status(404).json({ error: 'content_not_found' });
    res.json({ item: rows[0] });
  } catch (error) {
    console.error('Content update failed:', error.message);
    res.status(500).json({ error: 'content_update_failed' });
  }
});

app.delete('/api/admin/content/:id', auth, adminOnly, async (req, res) => {
  try {
    const result = await pool.query('DELETE FROM content_items WHERE id=$1', [req.params.id]);
    if (!result.rowCount) return res.status(404).json({ error: 'content_not_found' });
    res.status(204).end();
  } catch (error) {
    console.error('Content deletion failed:', error.message);
    res.status(500).json({ error: 'content_deletion_failed' });
  }
});

app.post('/api/playlists', auth, async (req, res) => {
  try {
    const { name, playlistUrl, sourceFormat = 'm3u', rightsConfirmed } = req.body || {};
    if (typeof name !== 'string' || !name.trim() || !rightsConfirmed || !['m3u', 'xtream'].includes(sourceFormat) || !validHttpsUrl(playlistUrl)) {
      return res.status(400).json({ error: 'valid_name_https_url_and_rights_confirmation_required' });
    }
    const { rows } = await pool.query(
      'INSERT INTO user_playlists(user_id,name,playlist_url,source_format,rights_confirmed) VALUES($1,$2,$3,$4,true) RETURNING id,name,source_format,rights_confirmed,created_at',
      [req.user.id, name.trim(), playlistUrl || null, sourceFormat]
    );
    res.status(201).json({ playlist: rows[0] });
  } catch (error) {
    console.error('Playlist creation failed:', error.message);
    res.status(500).json({ error: 'playlist_creation_failed' });
  }
});

app.use((err, _req, res, _next) => {
  console.error('Unhandled request error:', err);
  res.status(500).json({ error: 'internal_server_error' });
});

const port = Number(process.env.PORT || 10000);
app.listen(port, '0.0.0.0', () => console.log(`VISION PRO API listening on ${port}`));
