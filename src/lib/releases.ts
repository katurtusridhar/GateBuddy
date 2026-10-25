import debugCatalog from "@/data/releases/debug-catalog.json";
import stableCatalog from "@/data/releases/stable-catalog.json";

export interface ReleaseCatalogQuery {
  channel: string | null;
  package: string | null;
  ifNoneMatch?: string | null;
}

export interface ReleaseCatalogResult {
  status: number;
  body: any;
  headers: Record<string, string>;
}

export function generateETag(content: string): string {
  let hash = 0;
  for (let i = 0; i < content.length; i++) {
    const char = content.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash |= 0;
  }
  return `W/"gb-rel-${Math.abs(hash).toString(16)}-v${content.length}"`;
}

export function resolveReleaseCatalog(query: ReleaseCatalogQuery): ReleaseCatalogResult {
  const { channel, package: pkg, ifNoneMatch } = query;

  if (!channel || !pkg) {
    return {
      status: 400,
      body: {
        error: "Bad Request",
        message: "Missing required query parameters: 'channel' and 'package'. Example: /api/releases?channel=debug&package=com.gatecse.companion.debug",
      },
      headers: { "Content-Type": "application/json; charset=utf-8" },
    };
  }

  let catalog: any = null;

  if (channel === "debug" && pkg === "com.gatecse.companion.debug") {
    catalog = debugCatalog;
  } else if (channel === "stable" && pkg === "com.gatecse.companion") {
    catalog = stableCatalog;
  } else {
    return {
      status: 404,
      body: {
        error: "Not Found",
        message: `No release catalog available for channel='${channel}' and package='${pkg}'.`,
      },
      headers: { "Content-Type": "application/json; charset=utf-8" },
    };
  }

  const catalogString = JSON.stringify(catalog);
  const etag = generateETag(catalogString);

  if (ifNoneMatch && ifNoneMatch === etag) {
    return {
      status: 304,
      body: null,
      headers: {
        "ETag": etag,
        "Cache-Control": "public, s-maxage=60, stale-while-revalidate=300",
      },
    };
  }

  return {
    status: 200,
    body: catalog,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "ETag": etag,
      "Cache-Control": "public, s-maxage=60, stale-while-revalidate=300",
    },
  };
}
