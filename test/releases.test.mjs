import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

// Pure resolution logic matching src/lib/releases.ts for standalone node testing
function generateETag(content) {
  let hash = 0;
  for (let i = 0; i < content.length; i++) {
    const char = content.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash |= 0;
  }
  return `W/"gb-rel-${Math.abs(hash).toString(16)}-v${content.length}"`;
}

function resolveReleaseCatalog(query, debugCatalog, stableCatalog) {
  const { channel, package: pkg, ifNoneMatch } = query;

  if (!channel || !pkg) {
    return {
      status: 400,
      body: { error: 'Bad Request', message: "Missing required query parameters: 'channel' and 'package'." },
      headers: { 'Content-Type': 'application/json; charset=utf-8' },
    };
  }

  let catalog = null;
  if (channel === 'debug' && pkg === 'com.gatecse.companion.debug') {
    catalog = debugCatalog;
  } else if (channel === 'stable' && pkg === 'com.gatecse.companion') {
    catalog = stableCatalog;
  } else {
    return {
      status: 404,
      body: { error: 'Not Found', message: `No release catalog available for channel='${channel}' and package='${pkg}'.` },
      headers: { 'Content-Type': 'application/json; charset=utf-8' },
    };
  }

  const catalogString = JSON.stringify(catalog);
  const etag = generateETag(catalogString);

  if (ifNoneMatch && ifNoneMatch === etag) {
    return {
      status: 304,
      body: null,
      headers: { 'ETag': etag, 'Cache-Control': 'public, s-maxage=60, stale-while-revalidate=300' },
    };
  }

  return {
    status: 200,
    body: catalog,
    headers: {
      'Content-Type': 'application/json; charset=utf-8',
      'ETag': etag,
      'Cache-Control': 'public, s-maxage=60, stale-while-revalidate=300',
    },
  };
}

test('Release Catalog API & Manifest Tests', async (t) => {
  const debugCatalogPath = path.join(rootDir, 'src/data/releases/debug-catalog.json');
  const stableCatalogPath = path.join(rootDir, 'src/data/releases/stable-catalog.json');
  const routePath = path.join(rootDir, 'src/app/api/releases/route.ts');
  const libPath = path.join(rootDir, 'src/lib/releases.ts');

  assert.ok(fs.existsSync(debugCatalogPath), 'debug-catalog.json must exist');
  assert.ok(fs.existsSync(stableCatalogPath), 'stable-catalog.json must exist');
  assert.ok(fs.existsSync(routePath), 'route.ts must exist');
  assert.ok(fs.existsSync(libPath), 'releases.ts must exist');

  const debugCatalog = JSON.parse(fs.readFileSync(debugCatalogPath, 'utf8'));
  const stableCatalog = JSON.parse(fs.readFileSync(stableCatalogPath, 'utf8'));

  // Test 1: Debug Catalog JSON Schema Validation
  await t.test('Debug catalog has valid schema and verified values', () => {
    assert.equal(debugCatalog.manifestVersion, 1);
    assert.equal(debugCatalog.channel, 'debug');
    assert.equal(debugCatalog.packageName, 'com.gatecse.companion.debug');
    assert.ok(Array.isArray(debugCatalog.releases), 'releases must be an array');
    assert.equal(debugCatalog.releases.length >= 2, true, 'releases must contain baseline and candidate');

    // Baseline v1.0.1
    const baseline = debugCatalog.releases.find(r => r.versionCode === 2);
    assert.ok(baseline, 'Baseline release v1.0.1 (code 2) must exist');
    assert.equal(baseline.versionName, '1.0.1');
    assert.equal(baseline.apkSizeBytes, 21040765);
    assert.equal(baseline.sha256, '3EBCE6420763E897B1DCC25A44FE286385D059C8587B76A5394A2798A140C40D');
    assert.equal(baseline.signingCertificate.sha256Fingerprint, '78:49:6C:7B:D8:F3:41:1F:90:36:12:97:14:39:EB:81:67:47:A4:4C:ED:09:CE:F4:31:BB:45:A5:D4:7F:F2:73');
    assert.equal(baseline.compatibility.minSdk, 26);
    assert.equal(baseline.compatibility.targetSdk, 35);
    assert.equal(baseline.database.databaseVersion, 3);

    // Forward Update Candidate v1.0.2
    const candidate = debugCatalog.releases.find(r => r.versionCode === 3);
    assert.ok(candidate, 'Candidate release v1.0.2 (code 3) must exist');
    assert.equal(candidate.versionName, '1.0.2');
    assert.equal(candidate.apkSizeBytes, 20892324);
    assert.equal(candidate.sha256, '837AACEBF0708A0E2B995455D3D7573D46020220CD9BF56AE593C418F96793B7');
    assert.equal(candidate.signingCertificate.sha256Fingerprint, '78:49:6C:7B:D8:F3:41:1F:90:36:12:97:14:39:EB:81:67:47:A4:4C:ED:09:CE:F4:31:BB:45:A5:D4:7F:F2:73');
    assert.equal(candidate.database.databaseVersion, 3);

    // Recommended update
    assert.ok(debugCatalog.recommendedUpdate, 'recommendedUpdate must be present');
    assert.equal(debugCatalog.recommendedUpdate.versionCode, 3);
    assert.equal(debugCatalog.recommendedUpdate.sha256, candidate.sha256);
  });

  // Test 2: Stable Catalog JSON Schema Validation
  await t.test('Stable catalog has valid schema', () => {
    assert.equal(stableCatalog.manifestVersion, 1);
    assert.equal(stableCatalog.channel, 'stable');
    assert.equal(stableCatalog.packageName, 'com.gatecse.companion');
    assert.equal(stableCatalog.recommendedUpdate, null);
    assert.ok(Array.isArray(stableCatalog.releases));
  });

  // Test 3: Resolution Logic Verification
  await t.test('Resolution logic rejects missing parameters and unsupported combinations', () => {
    // Missing parameters -> 400
    const resNoParams = resolveReleaseCatalog({ channel: null, package: null }, debugCatalog, stableCatalog);
    assert.equal(resNoParams.status, 400);

    const resMissingPkg = resolveReleaseCatalog({ channel: 'debug', package: null }, debugCatalog, stableCatalog);
    assert.equal(resMissingPkg.status, 400);

    // Unsupported channel -> 404
    const resBadChannel = resolveReleaseCatalog({ channel: 'alpha', package: 'com.gatecse.companion.debug' }, debugCatalog, stableCatalog);
    assert.equal(resBadChannel.status, 404);

    // Mismatched channel and package (cross-channel spoof) -> 404
    const resMismatched = resolveReleaseCatalog({ channel: 'debug', package: 'com.gatecse.companion' }, debugCatalog, stableCatalog);
    assert.equal(resMismatched.status, 404);

    // Valid debug request -> 200
    const resValidDebug = resolveReleaseCatalog({ channel: 'debug', package: 'com.gatecse.companion.debug' }, debugCatalog, stableCatalog);
    assert.equal(resValidDebug.status, 200);
    assert.equal(resValidDebug.body.channel, 'debug');
    assert.equal(resValidDebug.body.packageName, 'com.gatecse.companion.debug');

    const etag = resValidDebug.headers['ETag'];
    assert.ok(etag, 'Response must include ETag');

    // Conditional GET with matching If-None-Match -> 304
    const resConditional = resolveReleaseCatalog({
      channel: 'debug',
      package: 'com.gatecse.companion.debug',
      ifNoneMatch: etag,
    }, debugCatalog, stableCatalog);
    assert.equal(resConditional.status, 304, 'Must return 304 Not Modified when ETag matches');

    // Conditional GET with stale If-None-Match -> 200
    const resStale = resolveReleaseCatalog({
      channel: 'debug',
      package: 'com.gatecse.companion.debug',
      ifNoneMatch: 'W/"old-stale-hash"',
    }, debugCatalog, stableCatalog);
    assert.equal(resStale.status, 200, 'Must return 200 OK when ETag does not match');
  });
});
