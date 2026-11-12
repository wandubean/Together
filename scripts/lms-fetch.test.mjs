import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { once } from 'node:events';
import { spawn } from 'node:child_process';
import { mkdtemp, readFile, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { LmsClient, AuthError, toAssignment, toAnnouncement, contextClient } from './lms-fetch.mjs';

async function serverTest(run, handler) {
  const server = createServer(handler);
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  const base = `http://127.0.0.1:${server.address().port}/`;
  try { await run(base); } finally { await new Promise(r => server.close(r)); }
}
function json(res, value, status = 200, headers = {}) {
  res.writeHead(status, { 'Content-Type': 'application/json', ...headers });
  res.end(`while(1);${JSON.stringify(value)}`);
}

test('LMS의 여러 Set-Cookie 헤더가 줄바꿈으로 합쳐져도 응답을 처리한다', async () => {
  let disposed = false;
  const client = contextClient('https://learning.hanyang.ac.kr/', { get: async () => ({
    headers: () => ({ 'content-type': 'application/json', 'set-cookie': 'a=one; Path=/\nb=two; Path=/' }),
    status: () => 200, body: async () => Buffer.from('while(1);{"id":1}'),
    dispose: async () => { disposed = true; },
  }) });
  const result = await client.get(client.url('users/self'));
  assert.equal(result.body.id, 1);
  assert.ok(disposed);
});

test('HTTP 수집: 인증 헤더, 페이지 순회, 비공개 제외, 앱 모델 변환', async () => {
  await serverTest(async base => {
    const result = await new LmsClient({ base, cookie: 'session=test' }).collect();
    assert.deepEqual(result.courses, [{ id: 1, name: '테스트 과목', code: 'TEST' }]);
    assert.deepEqual(result.assignments.map(a => a.id), [10, 12]);
    assert.equal(result.assignments[0].submissionState, 'NOT_SUBMITTED');
    assert.equal(result.assignments[0].dueAt, '2026-11-12T01:00:00.000Z');
    assert.equal(result.assignments[1].submissionState, 'SUBMITTED');
    assert.deepEqual(result.fetchedCourseIds, [1]);
    assert.deepEqual(result.failedCourseIds, []);
    assert.ok(!JSON.stringify(result).includes('session=test'));
  }, (req, res) => {
    assert.equal(req.headers.cookie, 'session=test');
    const url = new URL(req.url, `http://${req.headers.host}`);
    if (url.pathname.endsWith('users/self')) return json(res, { id: 5, name: '학생' });
    if (url.pathname.endsWith('/discussion_topics')) return json(res, []);
    if (url.pathname.endsWith('/courses')) {
      if (url.searchParams.has('page')) return json(res, [{ id: 2, access_restricted_by_date: true }]);
      return json(res, [{ id: 1, name: '테스트 과목', course_code: 'TEST' }], 200,
        { Link: `<http://${req.headers.host}/api/v1/courses?page=2>; rel="next"` });
    }
    assert.equal(url.searchParams.get('include[]'), 'submission');
    if (url.searchParams.has('page')) return json(res, [{ id: 12, submission: { workflow_state: 'submitted' } }]);
    json(res, [{ id: 10, due_at: '2026-11-12T10:00:00+09:00', submission: { workflow_state: 'unsubmitted' } },
      { id: 11, published: false }], 200,
    { Link: `<http://${req.headers.host}/api/v1/courses/1/assignments?include[]=submission&page=2>; rel="next"` });
  });
});

test('인증 실패와 로그인 리다이렉트를 구분하고 외부로 쿠키를 보내지 않는다', async () => {
  for (const status of [401, 403, 404, 302]) {
    await serverTest(async base => {
      await assert.rejects(new LmsClient({ base }).collect(), AuthError);
    }, (_req, res) => {
      res.writeHead(status, { Location: 'https://example.com/login' }); res.end();
    });
  }
});

test('조회 실패한 과목은 성공 목록에 포함하지 않는다', async () => {
  await serverTest(async base => {
    const result = await new LmsClient({ base }).collect();
    assert.deepEqual(result.failedCourseIds, [2]);
    assert.deepEqual(result.fetchedCourseIds, [1]);
    assert.deepEqual(result.assignments, []);
  }, (req, res) => {
    if (req.url.includes('users/self')) return json(res, { id: 1 });
    if (req.url.includes('/courses?')) return json(res, [{ id: 1, name: '성공' }, { id: 2, name: '실패' }]);
    json(res, [], req.url.includes('/2/') ? 500 : 200);
  });
});

test('페이지 반복, 상한, 외부 도메인 링크는 불완전한 결과 대신 오류로 처리한다', async () => {
  for (const kind of ['loop', 'limit', 'external']) {
    let calls = 0;
    const client = new LmsClient({ maxPages: 2, transport: async url => {
      calls++;
      const next = kind === 'external' ? 'https://example.com/api/v1/courses' :
        kind === 'loop' ? String(url) : `https://learning.hanyang.ac.kr/api/v1/courses?page=${calls + 1}`;
      return new Response('[]', { headers: { 'content-type': 'application/json', link: `<${next}>; rel="next"` } });
    } });
    await assert.rejects(client.paged(client.url('courses')));
    assert.ok(calls <= 2);
  }
});

test('깨진 JSON, HTML, 잘못된 목록 형식을 감지한다', async () => {
  for (const [body, type] of [['{', 'application/json'], ['<html>', 'text/html'], ['{}', 'application/json']]) {
    const client = new LmsClient({ transport: async () => new Response(body, { headers: { 'content-type': type } }) });
    await assert.rejects(client.paged(client.url('courses')));
  }
});

test('누락된 제출 상태와 잘못된 날짜는 UNKNOWN과 null로 변환한다', () => {
  const a = toAssignment({ id: 1, due_at: 'invalid' }, { id: 2, name: '과목' });
  assert.equal(a.submissionState, 'UNKNOWN');
  assert.equal(a.dueAt, null);
  assert.equal(a.name, '(제목 없음)');
});

test('CLI가 JSON을 저장하고 부분 실패 종료코드 2를 반환한다', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'lms-test-'));
  try {
    await serverTest(async base => {
      const output = join(dir, 'result.json');
      const child = spawn(process.execPath, [fileURLToPath(new URL('./lms-fetch.mjs', import.meta.url)),
        '--base', base, '--out', output], {
        env: { ...process.env, LMS_COOKIE: 'session=test', LMS_TOKEN: '' }, stdio: 'ignore',
      });
      const [code] = await once(child, 'exit');
      assert.equal(code, 2);
      const result = JSON.parse(await readFile(output, 'utf8'));
      assert.deepEqual(result.failedCourseIds, [2]);
      assert.deepEqual(result.fetchedCourseIds, [1]);
    }, (req, res) => {
      if (req.url.includes('users/self')) return json(res, { id: 1 });
      if (req.url.includes('/courses?')) return json(res, [{ id: 1, name: '성공' }, { id: 2, name: '실패' }]);
      json(res, [], req.url.includes('/2/') ? 403 : 200);
    });
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('CLI 인증 실패는 기존 결과를 덮어쓰지 않는다', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'lms-test-'));
  try {
    await serverTest(async base => {
      const output = join(dir, 'result.json');
      await writeFile(output, 'previous result');
      const child = spawn(process.execPath, [fileURLToPath(new URL('./lms-fetch.mjs', import.meta.url)),
        '--base', base, '--out', output], {
        env: { ...process.env, LMS_COOKIE: 'session=test', LMS_TOKEN: '' }, stdio: 'ignore',
      });
      const [code] = await once(child, 'exit');
      assert.equal(code, 1);
      assert.equal(await readFile(output, 'utf8'), 'previous result');
    }, (_req, res) => json(res, {}, 401));
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('저장한 세션으로 브라우저 없이 실제 HTTP 수집과 재실행을 수행한다', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'lms-session-test-'));
  try {
    await serverTest(async base => {
      const session = join(dir, 'session.json');
      const output = join(dir, 'result.json');
      await writeFile(session, JSON.stringify({ cookies: [{ name: 'session', value: 'first',
        domain: '127.0.0.1', path: '/', expires: -1, httpOnly: true, secure: false, sameSite: 'Lax' }], origins: [] }));
      for (let run = 0; run < 2; run++) {
        const child = spawn(process.execPath, [fileURLToPath(new URL('./lms-fetch.mjs', import.meta.url)),
          '--base', base, '--session', session, '--out', output], {
          env: { ...process.env, LMS_COOKIE: '', LMS_TOKEN: '' }, stdio: 'pipe',
        });
        let stderr = '';
        child.stderr.on('data', data => { stderr += data; });
        const [code] = await once(child, 'exit');
        assert.equal(code, 0, stderr);
        const result = JSON.parse(await readFile(output, 'utf8'));
        assert.equal(result.assignments[0].id, 10);
        assert.equal(result.assignments[0].submissionState, 'NOT_SUBMITTED');
        assert.deepEqual(result.fetchedCourseIds, [1]);
        const saved = JSON.parse(await readFile(session, 'utf8'));
        assert.equal(saved.cookies.find(c => c.name === 'session').value, 'renewed');
      }
    }, (req, res) => {
      if (!['session=first', 'session=renewed'].includes(req.headers.cookie)) return json(res, {}, 401);
      if (req.url.includes('users/self')) return json(res, { id: 1 }, 200, { 'Set-Cookie': 'session=renewed; Path=/; HttpOnly' });
      if (req.url.includes('/courses?')) return json(res, [{ id: 1, name: '과목' }]);
      if (req.url.includes('/discussion_topics')) return json(res, []);
      json(res, [{ id: 10, name: '과제', submission: { workflow_state: 'unsubmitted' } }]);
    });
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('세션 파일이 없으면 명확하게 최초 로그인을 안내한다', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'lms-session-test-'));
  try {
    const child = spawn(process.execPath, [fileURLToPath(new URL('./lms-fetch.mjs', import.meta.url)),
      '--session', join(dir, 'missing.json')], {
      env: { ...process.env, LMS_COOKIE: '', LMS_TOKEN: '' }, stdio: 'pipe',
    });
    let stderr = '';
    child.stderr.on('data', data => { stderr += data; });
    const [code] = await once(child, 'exit');
    assert.equal(code, 1);
    assert.match(stderr, /--login/);
  } finally { await rm(dir, { recursive: true, force: true }); }
});

test('공지의 모든 페이지와 본문, 날짜, 작성자, 첨부 링크를 수집한다', async () => {
  await serverTest(async base => {
    const result = await new LmsClient({ base }).collect();
    assert.deepEqual(result.announcements.map(a => a.id), [20, 21]);
    assert.deepEqual(result.fetchedAnnouncementCourseIds, [1]);
    assert.deepEqual(result.failedAnnouncementCourseIds, []);
    const notice = result.announcements[0];
    assert.equal(notice.courseName, '과목');
    assert.equal(notice.message, '<p>오래된 공지도 수집</p>');
    assert.equal(notice.postedAt, '2026-03-01T00:00:00.000Z');
    assert.equal(notice.htmlUrl, 'https://example.com/notice/20');
    assert.equal(notice.author.displayName, '교수');
    assert.deepEqual(notice.attachments, [{ id: 3, name: '안내.pdf', url: 'https://example.com/file', contentType: 'application/pdf' }]);
    assert.equal(result.assignments.length, 1);
  }, (req, res) => {
    const url = new URL(req.url, `http://${req.headers.host}`);
    if (url.pathname.endsWith('/users/self')) return json(res, { id: 1 });
    if (url.pathname.endsWith('/courses')) return json(res, [{ id: 1, name: '과목' }]);
    if (url.pathname.endsWith('/assignments')) return json(res, [{ id: 10 }]);
    assert.equal(url.pathname, '/api/v1/courses/1/discussion_topics');
    assert.equal(url.searchParams.get('only_announcements'), 'true');
    if (url.searchParams.has('page')) return json(res, [{ id: 21, title: '다음 페이지' }, { id: 22, published: false }]);
    json(res, [{ id: 20, title: '공지', message: '<p>오래된 공지도 수집</p>',
      posted_at: '2026-03-01T09:00:00+09:00', html_url: 'https://example.com/notice/20',
      author: { id: 8, display_name: '교수' }, attachments: [{ id: 3, display_name: '안내.pdf',
        url: 'https://example.com/file', 'content-type': 'application/pdf' }] }], 200,
    { Link: `<http://${req.headers.host}/api/v1/courses/1/discussion_topics?only_announcements=true&page=2>; rel="next"` });
  });
});

test('공지와 과제의 조회 실패를 독립적으로 기록하고 성공한 결과를 유지한다', async () => {
  for (const failedResource of ['assignments', 'discussion_topics']) {
    await serverTest(async base => {
      const result = await new LmsClient({ base }).collect();
      const assignmentFailed = failedResource === 'assignments';
      assert.deepEqual(result.failedCourseIds, assignmentFailed ? [1] : []);
      assert.deepEqual(result.failedAnnouncementCourseIds, assignmentFailed ? [] : [1]);
      assert.deepEqual(result.fetchedCourseIds, assignmentFailed ? [] : [1]);
      assert.deepEqual(result.fetchedAnnouncementCourseIds, assignmentFailed ? [1] : []);
      assert.equal(result.assignments.length, assignmentFailed ? 0 : 1);
      assert.equal(result.announcements.length, assignmentFailed ? 1 : 0);
      assert.equal(result.errors[0].resource, assignmentFailed ? 'assignments' : 'announcements');
    }, (req, res) => {
      if (req.url.includes('/users/self')) return json(res, { id: 1 });
      if (req.url.includes('/courses?')) return json(res, [{ id: 1, name: '과목' }]);
      json(res, [{ id: 20 }], req.url.includes(`/${failedResource}?`) ? 403 : 200);
    });
  }
});

test('공지 필드 누락과 잘못된 날짜를 처리하고 잘못된 ID는 거부한다', () => {
  const course = { id: 1, name: '과목' };
  const notice = toAnnouncement({ id: 1, posted_at: 'invalid', attachments: null }, course);
  assert.equal(notice.title, '(제목 없음)');
  assert.equal(notice.message, null);
  assert.equal(notice.postedAt, null);
  assert.equal(notice.author, null);
  assert.deepEqual(notice.attachments, []);
  assert.throws(() => toAnnouncement({ id: 'bad' }, course));
});

test('CLI는 공지만 실패한 경우에도 부분 실패 종료코드 2를 반환한다', async () => {
  const dir = await mkdtemp(join(tmpdir(), 'lms-test-'));
  try {
    await serverTest(async base => {
      const output = join(dir, 'result.json');
      const child = spawn(process.execPath, [fileURLToPath(new URL('./lms-fetch.mjs', import.meta.url)),
        '--base', base, '--out', output], {
        env: { ...process.env, LMS_COOKIE: 'session=test', LMS_TOKEN: '' }, stdio: 'ignore',
      });
      const [code] = await once(child, 'exit');
      assert.equal(code, 2);
      const result = JSON.parse(await readFile(output, 'utf8'));
      assert.deepEqual(result.failedCourseIds, []);
      assert.deepEqual(result.failedAnnouncementCourseIds, [1]);
      assert.equal(result.assignments.length, 1);
    }, (req, res) => {
      if (req.url.includes('users/self')) return json(res, { id: 1 });
      if (req.url.includes('/courses?')) return json(res, [{ id: 1, name: '과목' }]);
      json(res, [{ id: 10 }], req.url.includes('/discussion_topics?') ? 500 : 200);
    });
  } finally { await rm(dir, { recursive: true, force: true }); }
});
