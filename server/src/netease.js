import { weapi } from "./crypto.js";

const NETEASE_BASE = "https://music.163.com/weapi";

// Configurable upstream behaviour via env (used by tests too):
//   UPSTREAM_TIMEOUT_MS — per-attempt timeout (default 12s)
//   UPSTREAM_RETRIES    — retries for transient network/5xx failures (default 2)
const TIMEOUT_MS = Math.max(500, Number(process.env.UPSTREAM_TIMEOUT_MS ?? 12_000));
const MAX_RETRIES = Math.min(5, Math.max(0, Number(process.env.UPSTREAM_RETRIES ?? 2)));

const ENDPOINTS = {
  search: "/search/get",
  songDetail: "/v3/song/detail",
  songUrl: "/song/enhance/player/url/v1",
  lyric: "/song/lyric",
  playlist: "/v6/playlist/detail",
  toplist: "/toplist/detail",
  songLike: "/song/like",
  // New endpoints for v1.1 features
  recommendSongs: "/v3/discovery/recommend/songs",
  recommendPlaylists: "/personalized/playlist",
  artistSongs: "/v1/artist/songs",
  newSong: "/personalized/newsong",
  // v1.7: playlist plaza (category list + hot playlists by category)
  playlistCatlist: "/playlist/catlist",
  topPlaylist: "/top/playlist",
  // v1.7: personal FM
  personalFm: "/personal_fm",
};

/** Network-ish failures worth retrying (fetch throws, timeouts, 5xx, 429). */
function isRetryable(result) {
  if (result === "throw") return true;
  if (!result || typeof result !== "object") return false;
  return (
    result.status >= 500 ||
    (result.data && (result.data.code === 429 || result.data.code >= 500 && result.data.code < 600))
  );
}

async function neteaseRequest(type, payload, extraHeaders = {}, { retries = MAX_RETRIES, retry = true, plain = false } = {}) {
  const path = ENDPOINTS[type] ?? type;
  const url = plain ? "https://music.163.com/api" + path : NETEASE_BASE + path;
  const body = plain ? payload : weapi(payload);
  const headers = {
    "Content-Type": "application/x-www-form-urlencoded",
    "User-Agent":
      "Mozilla/5.0 (Linux; Android 13; HHMusic) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    Referer: "https://music.163.com",
    Origin: "https://music.163.com",
    "X-Real-IP": "220.181.108.0",
    ...extraHeaders,
  };

  let lastErr;
  for (let attempt = 0; attempt <= (retry ? retries : 0); attempt++) {
    let status, text;
    try {
      const res = await fetch(url, {
        method: "POST",
        headers,
        body: new URLSearchParams(body).toString(),
        signal: AbortSignal.timeout(TIMEOUT_MS),
      });
      status = res.status;
      text = await res.text();
    } catch (err) {
      if (!retry || attempt >= retries) {
        return {
          status: 0,
          data: {
            code: -1,
            msg: `upstream unreachable: ${err.name ?? "error"}${err.message ? `: ${err.message}` : ""}`,
          },
        };
      }
      continue; // transient network failure / timeout -> retry with backoff
    }

    let json;
    try {
      json = JSON.parse(text);
    } catch {
      json = { code: status, raw: text };
    }
    const result = { status, data: json };
    // Strictly non-retryable upstream verdicts (4xx with a real business code)
    // short-circuit; transient 5xx/429 retry with short backoff.
    if (retry && attempt < retries && isRetryable(result)) {
      await new Promise((r) => setTimeout(r, 300 * (attempt + 1)));
      continue;
    }
    return result;
  }
}

export async function searchSongs(keyword, limit = 30, offset = 0) {
  return neteaseRequest("search", { s: keyword, type: 1, limit, offset });
}

export async function searchArtists(keyword, limit = 30, offset = 0) {
  return neteaseRequest("search", { s: keyword, type: 100, limit, offset });
}

export async function getSongDetail(ids) {
  const idList = Array.isArray(ids) ? ids : [ids];
  const c = idList.map((id) => ({ id }));
  return neteaseRequest("songDetail", {
    c: JSON.stringify(c),
    ids: idList.join(","),
  });
}

export async function getSongUrl(id, level = "exhigh") {
  const idsValue = "[" + String(id) + "]";
  return neteaseRequest("songUrl", { ids: idsValue, level, encodeType: "flac" });
}

export async function getLyric(id) {
  return neteaseRequest("lyric", { id, tv: -1, lv: -1, rv: -1, kv: -1 });
}

export async function getPlaylistDetail(id) {
  return neteaseRequest("playlist", { id, n: 1000, s: 8 });
}

export async function getToplists() {
  return neteaseRequest("toplist", {});
}

export async function likeSong(id, like = true) {
  // Mutating call: never retry, or we could double-like/unlike.
  return neteaseRequest("songLike", { trackId: id, like }, { Cookie: "os=android" }, { retry: false });
}

/* ----- New: recommendations & artist songs ----- */

export async function getRecommendSongs(limit = 30) {
  return neteaseRequest("recommendSongs", { limit });
}

export async function getRecommendPlaylists(limit = 12) {
  return neteaseRequest("recommendPlaylists", { limit });
}

export async function getArtistSongs(id, limit = 50, offset = 0, order = "hot") {
  return neteaseRequest("artistSongs", {
    id,
    limit,
    offset,
    order, // hot | time
    total: true,
  });
}

/** Artist discography: weapi POST /weapi/artist/albums/{id} → {hotAlbums, more}. */
export async function getArtistAlbums(id, limit = 50, offset = 0) {
  return neteaseRequest(`/artist/albums/${id}`, { limit, offset, total: true });
}

/** Album detail: plain POST /api/v1/album/{id} → {album, songs}. */
export async function getAlbumDetail(id) {
  return neteaseRequest(`/v1/album/${id}`, { total: true }, {}, { plain: true });
}

/** Real-time hot search keywords: weapi POST /weapi/search/hot {type: 1111}. */
export async function getHotSearches(type = 1111) {
  return neteaseRequest("/search/hot", { type });
}

export async function getNewSongs(limit = 30) {
  return neteaseRequest("newSong", { type: 0, areaId: 0, limit });
}

/* ----- v1.7: playlist plaza ----- */

/** All playlist categories: weapi POST /weapi/playlist/catlist → {sub, categories}. */
export async function getPlaylistCatlist() {
  return neteaseRequest("playlistCatlist", {});
}

/**
 * Hot/new playlists by category: weapi POST /weapi/top/playlist.
 * `cat` "全部" means all genres; `order` is "hot" or "new".
 */
export async function getTopPlaylists(cat = "全部", limit = 30, offset = 0, order = "hot") {
  return neteaseRequest("topPlaylist", { cat, limit, offset, order });
}

/** Personal FM: weapi POST /weapi/personal_fm → {data: [song...]}. */
export async function getPersonalFm() {
  return neteaseRequest("personalFm", {});
}

/**
 * Cheap liveness probe against NetEase, used by GET /api/health. Does NOT
 * depend on the weapi signing machinery — a plain GET is enough to tell
 * whether we have network reachability + a working DNS/TLS path.
 */
export async function pingUpstream() {
  try {
    const res = await fetch("https://music.163.com/api/linux/forward/echo", {
      method: "GET",
      headers: { "User-Agent": "HHMusic/1.4" },
      signal: AbortSignal.timeout(5000),
    });
    return res.status >= 200 && res.status < 500; // 404/403 still means "reachable"
  } catch {
    return false;
  }
}

export { neteaseRequest };
