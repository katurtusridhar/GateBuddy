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
    assert.equal(debugCatalog.releases.length >= 5, true, 'releases must contain all 5 cataloged versions');

    // Baseline v1.0.1 (code 2)
    const baseline = debugCatalog.releases.find(r => r.versionCode === 2);
    assert.ok(baseline, 'Baseline release v1.0.1 (code 2) must exist');
    assert.equal(baseline.versionName, '1.0.1');
    assert.equal(baseline.apkSizeBytes, 21040765);
    assert.equal(baseline.sha256, '3EBCE6420763E897B1DCC25A44FE286385D059C8587B76A5394A2798A140C40D');

    // Historical v1.0.2 (code 3)
    const rel102 = debugCatalog.releases.find(r => r.versionCode === 3);
    assert.ok(rel102, 'Release v1.0.2 (code 3) must exist');
    assert.equal(rel102.versionName, '1.0.2');
    assert.equal(rel102.apkSizeBytes, 20892324);
    assert.equal(rel102.sha256, '837AACEBF0708A0E2B995455D3D7573D46020220CD9BF56AE593C418F96793B7');

    // Baseline v1.0.3 (code 4)
    const rel103 = debugCatalog.releases.find(r => r.versionCode === 4);
    assert.ok(rel103, 'Release v1.0.3 (code 4) must exist');
    assert.equal(rel103.versionName, '1.0.3');
    assert.equal(rel103.apkSizeBytes, 20892324);
    assert.equal(rel103.sha256, '75F9509D3340B22E18E1637CE1D51952F26DD56229561C910CDBEF8DB3671AD7');

    // Rollback Candidate v1.0.2-r2 (code 7)
    const rollback = debugCatalog.releases.find(r => r.versionCode === 7);
    assert.ok(rollback, 'Rollback release v1.0.2-r2 (code 7) must exist');
    assert.equal(rollback.releaseType, 'rollback');
    assert.equal(rollback.behaviorVersion, '1.0.2');
    assert.equal(rollback.restoresBehaviorVersion, '1.0.2');
    assert.equal(rollback.apkSizeBytes, 20908708);
    assert.equal(rollback.sha256, '7C6B0A40F65BD53F4C3733B0C64BDAB6959512DB84C0F3D20927F5221E8EA2A9');
    assert.equal(rollback.signingCertificate.sha256Fingerprint, '78:49:6C:7B:D8:F3:41:1F:90:36:12:97:14:39:EB:81:67:47:A4:4C:ED:09:CE:F4:31:BB:45:A5:D4:7F:F2:73');

    // Forward Update Progression Candidate v1.0.5 (code 8)
    const forward8 = debugCatalog.releases.find(r => r.versionCode === 8);
    assert.ok(forward8, 'Forward release v1.0.5 (code 8) must exist');
    assert.equal(forward8.releaseType, 'standard');
    assert.equal(forward8.versionName, '1.0.5');
    assert.equal(forward8.apkSizeBytes, 20908712);
    assert.equal(forward8.sha256, 'D1A457675E7DFADAD6F49AFA68E2847A8D36449F46807017D3B94786734DA29C');
    assert.equal(forward8.signingCertificate.sha256Fingerprint, '78:49:6C:7B:D8:F3:41:1F:90:36:12:97:14:39:EB:81:67:47:A4:4C:ED:09:CE:F4:31:BB:45:A5:D4:7F:F2:73');

    // Forward Update Candidate v1.0.4 (code 6)
    const candidate = debugCatalog.releases.find(r => r.versionCode === 6);
    assert.ok(candidate, 'Candidate release v1.0.4 (code 6) must exist');
    assert.equal(candidate.releaseType, 'standard');
    assert.equal(candidate.versionName, '1.0.4');
    assert.equal(candidate.apkSizeBytes, 20908712);
    assert.equal(candidate.sha256, 'B880A0B7CC5A2D6A9BBF835143578F146A1CAB750F83BAC26641CCA07504A35D');
    assert.equal(candidate.signingCertificate.sha256Fingerprint, '78:49:6C:7B:D8:F3:41:1F:90:36:12:97:14:39:EB:81:67:47:A4:4C:ED:09:CE:F4:31:BB:45:A5:D4:7F:F2:73');
    assert.equal(candidate.database.databaseVersion, 3);

    // Forward Update Candidate v1.0.6 (code 9)
    const rel106 = debugCatalog.releases.find(r => r.versionCode === 9);
    assert.ok(rel106, 'Candidate release v1.0.6 (code 9) must exist');
    assert.equal(rel106.releaseType, 'standard');
    assert.equal(rel106.versionName, '1.0.6');
    assert.equal(rel106.apkSizeBytes, 20941480);
    assert.equal(rel106.sha256, 'FCD4593E701ECC483D21F42A81E11406AD17C0DE15766439DB899911E328FC78');
    assert.equal(rel106.signingCertificate.sha256Fingerprint, '78:49:6C:7B:D8:F3:41:1F:90:36:12:97:14:39:EB:81:67:47:A4:4C:ED:09:CE:F4:31:BB:45:A5:D4:7F:F2:73');
    assert.equal(rel106.database.databaseVersion, 3);

    // Forward Update Candidate v1.0.7 (code 10)
    const rel107 = debugCatalog.releases.find(r => r.versionCode === 10);
    assert.ok(rel107, 'Candidate release v1.0.7 (code 10) must exist');
    assert.equal(rel107.releaseType, 'standard');
    assert.equal(rel107.versionName, '1.0.7');
    assert.equal(rel107.apkSizeBytes, 20941480);
    assert.equal(rel107.sha256, 'ca34d2dd925cde56622df29d56d1d3821d11f05fd3204629180852e6de1527c3');
    assert.equal(rel107.signingCertificate.sha256Fingerprint, '78:49:6C:7B:D8:F3:41:1F:90:36:12:97:14:39:EB:81:67:47:A4:4C:ED:09:CE:F4:31:BB:45:A5:D4:7F:F2:73');
    assert.equal(rel107.database.databaseVersion, 3);

    // Forward Update Candidate v1.0.8 (code 11)
    const rel108 = debugCatalog.releases.find(r => r.versionCode === 11);
    assert.ok(rel108, 'Candidate release v1.0.8 (code 11) must exist');
    assert.equal(rel108.releaseType, 'standard');
    assert.equal(rel108.versionName, '1.0.8');
    assert.equal(rel108.apkSizeBytes, 20941476);
    assert.equal(rel108.sha256, '16a800341bbdea1d3c7c7807687cbdb323c9a9c79d11a983636e3e64f299c41c');
    assert.equal(rel108.signingCertificate.sha256Fingerprint, '78:49:6C:7B:D8:F3:41:1F:90:36:12:97:14:39:EB:81:67:47:A4:4C:ED:09:CE:F4:31:BB:45:A5:D4:7F:F2:73');
    assert.equal(rel108.database.databaseVersion, 3);

    // Forward Update Candidate v1.0.9 (code 12)
    const rel109 = debugCatalog.releases.find(r => r.versionCode === 12);
    assert.ok(rel109, 'Candidate release v1.0.9 (code 12) must exist');
    assert.equal(rel109.releaseType, 'standard');
    assert.equal(rel109.versionName, '1.0.9');
    assert.equal(rel109.apkSizeBytes, 20941480);
    assert.equal(rel109.sha256, 'ac2704adfce4ac4c0bf8642e92961a2853f71ad47bf8ffab87fbb27b61ef9d21');
    assert.equal(rel109.signingCertificate.sha256Fingerprint, '78:49:6C:7B:D8:F3:41:1F:90:36:12:97:14:39:EB:81:67:47:A4:4C:ED:09:CE:F4:31:BB:45:A5:D4:7F:F2:73');
    assert.equal(rel109.database.databaseVersion, 3);

    // Forward Update Candidate v1.0.10 (code 13)
    const rel1010 = debugCatalog.releases.find(r => r.versionCode === 13);
    assert.ok(rel1010, 'Candidate release v1.0.10 (code 13) must exist');
    assert.equal(rel1010.releaseType, 'standard');
    assert.equal(rel1010.versionName, '1.0.10');
    assert.equal(rel1010.apkSizeBytes, 20941476);
    assert.equal(rel1010.sha256, 'abebe0483bacb2e495a1c3cbbca4b107191fc72caa0836b5adce1e9cb6c5d27f');
    assert.equal(rel1010.signingCertificate.sha256Fingerprint, '78:49:6C:7B:D8:F3:41:1F:90:36:12:97:14:39:EB:81:67:47:A4:4C:ED:09:CE:F4:31:BB:45:A5:D4:7F:F2:73');
    assert.equal(rel1010.database.databaseVersion, 3);

    // Forward Update Candidate v1.0.11 (code 14)
    const rel1011 = debugCatalog.releases.find(r => r.versionCode === 14);
    assert.ok(rel1011, 'Candidate release v1.0.11 (code 14) must exist');
    assert.equal(rel1011.releaseType, 'standard');
    assert.equal(rel1011.versionName, '1.0.11');
    assert.equal(rel1011.apkSizeBytes, 20941476);
    assert.equal(rel1011.sha256, '6d036e98631bdafb1dd5cdb21b9ff690eecacb8b9339e2015c6eb4fe029c17ca');
    assert.equal(rel1011.signingCertificate.sha256Fingerprint, '78:49:6C:7B:D8:F3:41:1F:90:36:12:97:14:39:EB:81:67:47:A4:4C:ED:09:CE:F4:31:BB:45:A5:D4:7F:F2:73');
    assert.equal(rel1011.database.databaseVersion, 3);

    // Recommended update MUST be the standard forward release v1.0.11 (never a rollback)
    assert.ok(debugCatalog.recommendedUpdate, 'recommendedUpdate must be present');
    assert.equal(debugCatalog.recommendedUpdate.versionCode, 14);
    assert.equal(debugCatalog.recommendedUpdate.releaseType, 'standard');
    assert.equal(debugCatalog.recommendedUpdate.sha256, rel1011.sha256);
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
