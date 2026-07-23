import { weapi } from "./crypto.js";

const NETEASE_BASE = "https://music.163.com/weapi";

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
  artistDetail: "/artist/desc",
};

async function neteaseRequest(type, payload, extraHeaders = {}) {
  const path = ENDPOINTS[type] ?? type;
  const url = NETEASE_BASE + path;
  const body = weapi(payload);
  const headers = {
    "Content-Type": "application/x-www-form-urlencoded",
    "User-Agent":
      "Mozilla/5.0 (Linux; Android 13; HHMusic) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    Referer: "https://music.163.com",
    Origin: "https://music.163.com",
    "X-Real-IP": "220.181.108.0",
    ...extraHeaders,
  };

  const res = await fetch(url, {
    method: "POST",
    headers,
    body: new URLSearchParams(body).toString(),
  });

  const text = await res.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    json = { code: res.status, raw: text };
  }
  return { status: res.status, data: json };
}

export async function searchSongs(keyword, limit = 30, offset = 0) {
  return neteaseRequest("search", { s: keyword, type: 1, limit, offset });
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
  return neteaseRequest("songLike", { trackId: id, like }, { Cookie: "os=android" });
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

export async function getNewSongs(limit = 30) {
  return neteaseRequest("newSong", { type: 0, areaId: 0, limit });
}

export { neteaseRequest };
