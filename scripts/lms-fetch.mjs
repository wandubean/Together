import { mkdir, rename, writeFile, readFile, chmod } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';
import { createInterface } from 'node:readline';

export const DEFAULT_BASE = 'https://learning.hanyang.ac.kr/';
export class AuthError extends Error {}
const DEFAULT_SESSION = fileURLToPath(new URL('../.local/lms-session.json', import.meta.url));

async function saveJson(path, value) {
  path = resolve(path);
  await mkdir(dirname(path), { recursive: true });
  const temporary = `${path}.${process.pid}.tmp`;
  await writeFile(temporary, JSON.stringify(value, null, 2) + '\n', { mode: 0o600 });
  await chmod(temporary, 0o600);
  await rename(temporary, path);
}

export function contextClient(base, context) {
  return new LmsClient({ base, transport: async (url, options) => {
    const res = await context.get(String(url), {
      headers: options.headers, maxRedirects: 0, timeout: 30_000,
    });
    try {
      // Playwright는 여러 Set-Cookie를 줄바꿈으로 합친다. 쿠키는 요청 컨텍스트가
      // 이미 반영했으므로 Fetch Response에는 파싱에 필요한 헤더만 전달한다.
      const source = res.headers();
      const headers = {};
      for (const name of ['content-type', 'link']) {
        if (source[name]) headers[name] = source[name];
      }
      return new Response([204, 205, 304].includes(res.status()) ? null : await res.body(), {
        status: res.status(), headers,
      });
    } finally { await res.dispose(); }
  } });
}

async function savedSessionClient(base, sessionFile) {
  let state;
  try { state = JSON.parse(await readFile(sessionFile, 'utf8')); }
  catch (error) {
    if (error.code === 'ENOENT') throw new AuthError('저장된 세션이 없습니다. 최초 한 번 --login으로 학교 로그인이 필요합니다.');
    throw new AuthError('세션 파일을 읽을 수 없습니다. --login으로 다시 로그인하세요.');
  }
  const { request } = await import('playwright-core');
  const context = await request.newContext({ storageState: state });
  return {
    client: contextClient(base, context),
    save: async () => saveJson(sessionFile, await context.storageState()),
    close: () => context.dispose(),
  };
}

export class LmsClient {
  constructor({ base = DEFAULT_BASE, cookie, token, transport = fetch, maxPages = 100 } = {}) {
    this.base = new URL(base);
    if (this.base.protocol !== 'https:' && !['localhost', '127.0.0.1'].includes(this.base.hostname)) {
      throw new Error('LMS 주소는 HTTPS여야 합니다.');
    }
    this.headers = { Accept: 'application/json' };
    if (cookie) this.headers.Cookie = cookie;
    if (token) this.headers.Authorization = `Bearer ${token}`;
    this.transport = transport;
    this.maxPages = maxPages;
  }

  url(path, query = {}) {
    const url = new URL(`api/v1/${path}`, this.base);
    url.search = new URLSearchParams({ per_page: '100', ...query }).toString();
    return url;
  }

  async get(url) {
    url = new URL(url, this.base);
    if (url.origin !== this.base.origin || !url.pathname.startsWith('/api/v1/')) {
      throw new Error('허용되지 않은 API/페이지 주소입니다.');
    }
    const response = await this.transport(url, {
      headers: this.headers, redirect: 'manual', signal: AbortSignal.timeout(30_000),
    });
    if ([301, 302, 303, 307, 308, 401].includes(response.status) ||
        ([403, 404].includes(response.status) && url.pathname === '/api/v1/users/self')) {
      throw new AuthError('LMS 로그인이 필요하거나 세션이 만료되었습니다.');
    }
    if (!response.ok) throw new Error(`LMS HTTP ${response.status}`);
    if (!response.headers.get('content-type')?.toLowerCase().includes('json')) {
      throw new AuthError('JSON 대신 로그인/HTML 응답을 받았습니다.');
    }
    let body;
    try {
      body = JSON.parse((await response.text()).replace(/^\uFEFF?\s*while\(1\);/, '').trim());
    } catch {
      throw new Error('LMS JSON 응답을 해석할 수 없습니다.');
    }
    return { body, link: response.headers.get('link') };
  }

  async paged(url) {
    const items = [], visited = new Set();
    while (url) {
      const key = String(url);
      if (visited.has(key) || visited.size >= this.maxPages) {
        throw new Error('페이지 반복/상한 초과: 불완전한 목록을 저장하지 않습니다.');
      }
      visited.add(key);
      const { body, link } = await this.get(url);
      if (!Array.isArray(body)) throw new Error('LMS 목록 응답이 배열이 아닙니다.');
      items.push(...body);
      url = null;
      for (const match of (link ?? '').matchAll(/<([^>]+)>\s*;\s*rel="([^"]+)"/g)) {
        if (match[2].split(/\s+/).includes('next')) url = new URL(match[1], this.base);
      }
    }
    return items;
  }

  async collect() {
    const { body: user } = await this.get(this.url('users/self'));
    if (!Number.isSafeInteger(user?.id)) throw new Error('사용자 확인 응답이 올바르지 않습니다.');
    const courses = (await this.paged(this.url('courses', { enrollment_state: 'active' })))
      .filter(c => !c.access_restricted_by_date && c.name)
      .map(c => ({ id: c.id, name: c.name, code: c.course_code ?? null }));
    const assignments = [], announcements = [], fetchedCourseIds = [], failedCourseIds = [], errors = [];
    const fetchedAnnouncementCourseIds = [], failedAnnouncementCourseIds = [];
    for (const course of courses) {
      try {
        const rows = await this.paged(this.url(`courses/${course.id}/assignments`, {
          'include[]': 'submission', order_by: 'due_at',
        }));
        assignments.push(...rows.filter(a => a.published !== false).map(a => toAssignment(a, course)));
        fetchedCourseIds.push(course.id);
      } catch (error) {
        if (error instanceof AuthError) throw error;
        failedCourseIds.push(course.id);
        // 응답 본문/쿠키/개인 과제 내용은 진단 로그에 남기지 않는다.
        errors.push({ courseId: course.id, resource: 'assignments', message: error.message });
      }
      // 공지와 과제는 독립적으로 조회한다. 어느 한쪽 실패로 다른 결과를 버리지 않는다.
      try {
        const rows = await this.paged(this.url(`courses/${course.id}/discussion_topics`, {
          only_announcements: 'true', order_by: 'recent_activity',
        }));
        announcements.push(...rows.filter(a => a.published !== false).map(a => toAnnouncement(a, course)));
        fetchedAnnouncementCourseIds.push(course.id);
      } catch (error) {
        if (error instanceof AuthError) throw error;
        failedAnnouncementCourseIds.push(course.id);
        errors.push({ courseId: course.id, resource: 'announcements', message: error.message });
      }
    }
    return { user: { id: user.id, name: user.name ?? null }, courses, assignments, announcements,
      fetchedCourseIds, failedCourseIds, fetchedAnnouncementCourseIds, failedAnnouncementCourseIds,
      fetchedAt: new Date().toISOString(), errors };
  }
}

function instant(value) {
  return value && Number.isFinite(Date.parse(value)) ? new Date(value).toISOString() : null;
}

export function toAssignment(a, course) {
  if (!Number.isSafeInteger(a.id)) throw new Error('과제 ID가 올바르지 않습니다.');
  const submission = a.submission;
  const state = submission?.submitted_at || ['submitted', 'pending_review', 'graded'].includes(submission?.workflow_state)
    ? 'SUBMITTED' : submission?.workflow_state === 'unsubmitted' ? 'NOT_SUBMITTED' : 'UNKNOWN';
  return { id: a.id, courseId: course.id, courseName: course.name, name: a.name ?? '(제목 없음)',
    description: a.description ?? null, dueAt: instant(a.due_at), lockAt: instant(a.lock_at),
    htmlUrl: a.html_url ?? null, submissionState: state, submittedAt: instant(submission?.submitted_at) };
}

export function toAnnouncement(a, course) {
  if (!Number.isSafeInteger(a.id)) throw new Error('공지 ID가 올바르지 않습니다.');
  return {
    id: a.id, courseId: course.id, courseName: course.name, title: a.title ?? '(제목 없음)',
    message: a.message ?? null, postedAt: instant(a.posted_at), updatedAt: instant(a.updated_at),
    htmlUrl: a.html_url ?? null,
    author: a.author ? { id: a.author.id ?? null, displayName: a.author.display_name ?? null } : null,
    attachments: (a.attachments ?? []).map(file => ({
      id: file.id ?? null, name: file.display_name ?? file.filename ?? null,
      url: file.url ?? null, contentType: file['content-type'] ?? file.content_type ?? null,
    })),
  };
}

async function launchBrowser(channel, headless = false) {
  const { chromium } = await import('playwright-core');
  return chromium.launch({ channel, headless, args: ['--start-maximized'] });
}

async function browserClient(base, channel, sessionFile, credentials) {
  const browser = await launchBrowser(channel, Boolean(credentials));
  try {
    const context = await browser.newContext({ viewport: null });
    const page = await context.newPage();
    if (!credentials) await page.bringToFront();
    await page.goto(new URL('login', base).href, { waitUntil: 'domcontentloaded' });
    if (credentials) {
      await page.waitForURL('https://api.hanyang.ac.kr/oauth/login', { timeout: 30_000 });
      await page.locator('#uid').waitFor();
      if (new URL(page.url()).origin !== 'https://api.hanyang.ac.kr') throw new AuthError('학교 인증 서버 주소가 다릅니다.');
      // SSO는 비동기로 받은 공개키로 입력을 암호화한다. 폼이 보이는 것만으로
      // 준비 완료를 판단하면 암호화 준비 안내를 인증 실패로 오인하게 된다.
      await page.waitForFunction(() => typeof window._public_key !== 'undefined' &&
        Boolean(window._public_key) && Boolean(window._public_key_nm), null, { timeout: 30_000 });
      page.on('dialog', async dialog => { await dialog.dismiss(); });
      let loginCode;
      // 성공 즉시 페이지가 이동하므로 네트워크 이벤트 이후 body를 읽으면
      // 사라질 수 있다. 응답을 브라우저에 돌려주기 전에 결과 코드만 확인한다.
      await page.route('https://api.hanyang.ac.kr/oauth/login_submit.json', async route => {
        const response = await route.fetch({ maxRedirects: 0, maxRetries: 0 });
        const outcome = await response.json();
        loginCode = String(outcome.code);
        await route.fulfill({ response });
      });
      await page.locator('#uid').fill(credentials.username);
      await page.locator('#upw').fill(credentials.password);
      credentials.username = '';
      credentials.password = '';
      const submitted = page.waitForResponse(r =>
        r.url() === 'https://api.hanyang.ac.kr/oauth/login_submit.json' && r.request().method() === 'POST');
      await page.locator('#login_btn').click();
      await submitted;
      if (!['200', '504'].includes(loginCode)) {
        throw new AuthError(`학교 서버가 로그인을 거절했습니다 (응답 코드 ${String(loginCode).replace(/[^0-9]/g, '').slice(0, 3)}).`);
      }
      console.log('학교 인증 요청을 전송했습니다. 인증 완료 후 수집합니다.');
    } else {
      await page.bringToFront();
      console.log('학교 로그인을 완료하면 자동으로 수집합니다. 최대 5분간 기다립니다.');
    }
    const client = contextClient(base, context.request);
    const deadline = Date.now() + 300_000;
    while (Date.now() < deadline) {
      if (!browser.isConnected()) throw new Error('로그인 브라우저가 닫혔습니다.');
      try {
        const { body } = await client.get(client.url('users/self'));
        if (Number.isSafeInteger(body?.id)) {
          await saveJson(sessionFile, await context.storageState());
          return { client, save: async () => saveJson(sessionFile, await context.storageState()),
            close: () => browser.close() };
        }
      } catch (error) {
        if (!(error instanceof AuthError)) throw error;
      }
      await new Promise(r => setTimeout(r, 2000));
    }
    throw new AuthError('로그인 대기 시간이 초과되었습니다.');
  } catch (error) {
    await browser.close();
    throw error;
  }
}

export async function main() {
  const { values } = parseArgs({ options: {
    login: { type: 'boolean' }, probe: { type: 'boolean' }, help: { type: 'boolean' },
    'login-stdin': { type: 'boolean' },
    session: { type: 'string', default: DEFAULT_SESSION },
    base: { type: 'string', default: DEFAULT_BASE },
    channel: { type: 'string', default: 'chrome' },
    out: { type: 'string', default: fileURLToPath(new URL('../.local/lms-data.json', import.meta.url)) },
  } });
  if (values.help) {
    console.log('node scripts/lms-fetch.mjs --login [--out PATH] (기본 브라우저: Chrome)\n'
      + '로그인 후에는 옵션 없이 실행하면 저장된 세션으로 수집합니다.\n'
      + '--session PATH: 세션 파일 지정. LMS_COOKIE / LMS_TOKEN 환경변수도 지원.\n'
      + '--login-stdin: 인증정보 JSON을 표준입력으로 받아 창 없이 최초 로그인.\n'
      + '--probe: 세션 없이 서버 응답 확인.');
    return;
  }
  if (resolve(values.out) === resolve(values.session)) throw new Error('결과 파일과 세션 파일 경로는 달라야 합니다.');
  let session;
  try {
    let credentials;
    if (values['login-stdin']) {
      console.log('표준입력으로 인증정보를 기다립니다 (JSON 한 줄).');
      if (process.stdin.isTTY) process.stdin.setRawMode(true);
      const input = createInterface({ input: process.stdin, terminal: false });
      let line;
      try {
        line = await new Promise((resolve, reject) => {
          input.once('line', resolve);
          input.once('close', () => reject(new AuthError('인증정보를 받기 전에 표준입력이 닫혔습니다.')));
        });
      } finally {
        input.close();
        if (process.stdin.isTTY) process.stdin.setRawMode(false);
        process.stdin.pause();
      }
      try { credentials = JSON.parse(line); } catch { throw new AuthError('인증정보 입력 형식이 올바르지 않습니다.'); }
      line = '';
      if (typeof credentials.username !== 'string' || typeof credentials.password !== 'string') {
        throw new AuthError('아이디와 비밀번호 입력이 필요합니다.');
      }
    }
    session = values.login || credentials ? await browserClient(values.base, values.channel, values.session, credentials)
      : values.probe || process.env.LMS_COOKIE || process.env.LMS_TOKEN ? {
      client: new LmsClient({ base: values.base,
        cookie: values.probe ? undefined : process.env.LMS_COOKIE,
        token: values.probe ? undefined : process.env.LMS_TOKEN }),
      close: async () => {},
    } : await savedSessionClient(values.base, values.session);
    const result = await session.client.collect();
    const output = resolve(values.out);
    await saveJson(output, result);
    await session.save?.();
    const failed = new Set([...result.failedCourseIds, ...result.failedAnnouncementCourseIds]);
    console.log(`과목 ${result.courses.length}개, 공지 ${result.announcements.length}개, 과제 ${result.assignments.length}개, 실패 과목 ${failed.size}개\n저장: ${output}`);
    if (failed.size) process.exitCode = 2;
  } finally {
    await session?.close();
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(error => {
    console.error(`${error instanceof AuthError ? '인증 오류' : '수집 실패'}: ${error.message}`);
    process.exitCode = 1;
  });
}
