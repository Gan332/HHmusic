import express from "express";
import cors from "cors";
import {
  searchSongs,
  getSongDetail,
  getSongUrl,
  getLyric,
  getPlaylistDetail,
  getToplists, getRecommendSongs, getRecommendPlaylists, getArtistSongs, getNewSongs,
  likeSong,
} from "./netease.js";

const app = express();
app.use(cors());
app.use(express.json());

const asyncHandler = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

function sendResult(res, result) {
  if (!result || typeof result !== "object") {
    return res.status(502).json({ code: 502, msg: "upstream error" });
  }
  const status = result.status && result.status >= 400 ? result.status : 200;
  return res.status(status).json(result.data ?? result);
}

function toInt(v, def = 0) {
  const n = parseInt(v, 10);
  return Number.isFinite(n) ? n : def;
}

/* ----------------------------- Search ----------------------------- */
/** Normalize a song from NetEase's various shapes (search/detail/recommend) into our stable Song. */
function normalizeSongNetease(s) {
  const al = s.al ?? s.album ?? {};
  const ar = s.ar ?? s.artists ?? [];
  return {
    id: s.id,
    name: s.name,
    artists: ar.map((a) => ({ id: a.id, name: a.name })),
    album: { id: al.id, name: al.name, picUrl: al.picUrl },
    duration: s.dt ?? s.duration ?? 0,
    fee: s.fee ?? 0,
  };
}

app.get("/api/search", asyncHandler(async (req, res) => {
  const keyword = (req.query.s ?? req.query.keyword ?? "").toString().trim();
  if (!keyword) return res.status(400).json({ code: 400, msg: "missing keyword" });
  const limit = toInt(req.query.limit, 30);
  const offset = toInt(req.query.offset, 0);
  const r = await searchSongs(keyword, limit, offset);
  const songs = r.data?.result?.songs ?? [];
  const normalized = songs.map(normalizeSongNetease);
  sendResult(res, { status: r.status, data: { code: 200, songCount: r.data?.result?.songCount ?? 0, songs: normalized } });
}));

/* ------------------------- Song detail ---------------------------- */
app.get("/api/song/detail", asyncHandler(async (req, res) => {
  const ids = (req.query.ids ?? "").toString();
  if (!ids) return res.status(400).json({ code: 400, msg: "missing ids" });
  const idList = ids.split(",").map((x) => toInt(x)).filter((x) => x > 0);
  if (!idList.length) return res.status(400).json({ code: 400, msg: "missing valid ids" });
  const r = await getSongDetail(idList);
  const songs = (r.data?.songs ?? []).map(normalizeSongNetease);
  sendResult(res, { status: r.status, data: { code: 200, songs } });
}));

/* --------------------------- Song URL ----------------------------- */
app.get("/api/song/url", asyncHandler(async (req, res) => {
  const id = toInt(req.query.id, 0);
  if (!id) return res.status(400).json({ code: 400, msg: "missing id" });
  const level = (req.query.level ?? "exhigh").toString();
  const r = await getSongUrl(id, level);
  const d = (r.data?.data ?? [])[0] ?? {};
  sendResult(res, {
    status: r.status,
    data: {
      code: 200,
      id: d.id,
      url: d.url,
      br: d.br,
      size: d.size,
      type: d.type,
      md5: d.md5,
      freeTrialInfo: d.freeTrialInfo ?? null,
    },
  });
}));

/* ----------------------------- Lyric ------------------------------ */
app.get("/api/lyric", asyncHandler(async (req, res) => {
  const id = toInt(req.query.id, 0);
  if (!id) return res.status(400).json({ code: 400, msg: "missing id" });
  const r = await getLyric(id);
  const lyc = r.data ?? {};
  sendResult(res, {
    status: r.status,
    data: {
      code: 200,
      lrc: lyc.lrc?.lyric ?? "",
      tlyric: lyc.tlyric?.lyric ?? "",
      romalrc: lyc.romalrc?.lyric ?? "",
      yrc: lyc.yrc?.lyric ?? "",
    },
  });
}));

/* ---------------------------- Playlist ---------------------------- */
app.get("/api/playlist/detail", asyncHandler(async (req, res) => {
  const id = toInt(req.query.id, 0);
  if (!id) return res.status(400).json({ code: 400, msg: "missing id" });
  const r = await getPlaylistDetail(id);
  const pl = r.data?.playlist ?? {};
  const tracks = (pl.tracks ?? []).map(normalizeSongNetease);
  sendResult(res, {
    status: r.status,
    data: {
      code: 200,
      id: pl.id,
      name: pl.name,
      coverImgUrl: pl.coverImgUrl,
      creator: { id: pl.creator?.id, nickname: pl.creator?.nickname },
      tracks,
    },
  });
}));

/* ----------------------------- Toplist ---------------------------- */
app.get("/api/toplist", asyncHandler(async (req, res) => {
  const r = await getToplists();
  const list = (r.data?.list ?? []).map((t) => ({
    id: t.id,
    name: t.name,
    coverImgUrl: t.coverImgUrl,
    description: t.description,
    updateFrequency: t.updateFrequency,
  }));
  sendResult(res, { status: r.status, data: { code: 200, list } });
}));

/* ----------------------------- Like song -------------------------- */
app.post("/api/song/like", asyncHandler(async (req, res) => {
  const id = toInt(req.body?.id, 0);
  if (!id) return res.status(400).json({ code: 400, msg: "missing id" });
  const like = req.body?.like !== false && req.body?.like !== "false";
  const r = await likeSong(id, like);
  sendResult(res, { status: r.status, data: { code: 200, id, like, raw: r.data?.code } });
}));

/* ----------------------- Recommend songs (每日推荐) -------------------- */
app.get("/api/recommend/songs", asyncHandler(async (req, res) => {
  const limit = toInt(req.query.limit, 30);
  const r = await getRecommendSongs(limit);
  const songs = (r.data?.data?.dailySongs ?? r.data?.recommend ?? []).map(normalizeSongNetease);
  sendResult(res, { status: r.status, data: { code: 200, songs } });
}));

/* ------------------- Recommend playlists (推荐歌单) ------------------- */
app.get("/api/recommend/playlists", asyncHandler(async (req, res) => {
  const limit = toInt(req.query.limit, 12);
  const r = await getRecommendPlaylists(limit);
  const list = (r.data?.result ?? []).map((p) => ({
    id: p.id,
    name: p.name,
    picUrl: p.picUrl,
    playcount: p.playcount ?? p.playCount,
    creator: { nickname: (p.creator ?? {}).nickname },
  }));
  sendResult(res, { status: r.status, data: { code: 200, list } });
}));

/* ------------------------- Artist hot songs -------------------------- */
app.get("/api/artist/songs", asyncHandler(async (req, res) => {
  const id = toInt(req.query.id, 0);
  if (!id) return res.status(400).json({ code: 400, msg: "missing id" });
  const limit = toInt(req.query.limit, 50);
  const offset = toInt(req.query.offset, 0);
  const order = (req.query.order ?? "hot").toString();
  const r = await getArtistSongs(id, limit, offset, order);
  const songs = (r.data?.songs ?? []).map(normalizeSongNetease);
  sendResult(res, { status: r.status, data: { code: 200, total: r.data?.total ?? songs.length, songs } });
}));

/* --------------------------- New songs ------------------------------- */
app.get("/api/new/song", asyncHandler(async (req, res) => {
  const limit = toInt(req.query.limit, 30);
  const r = await getNewSongs(limit);
  const items = (r.data?.result ?? r.data?.data ?? []).map((it) => {
    const s = it.song ?? it;
    return normalizeSongNetease(s);
  });
  sendResult(res, { status: r.status, data: { code: 200, songs: items } });
}));

app.get("/api/health", (_req, res) => res.json({ code: 200, ok: true, ts: Date.now() }));

app.use((err, _req, res, _next) => {
  console.error("[HHMusic server] error:", err);
  res.status(500).json({ code: 500, msg: "internal server error" });
});

const PORT = Number(process.env.PORT ?? 3000);
const server = app.listen(PORT, () => {
  console.log(`[HHMusic server] listening on http://localhost:${PORT}`);
});
server.on("error", (err) => {
  if (err.code === "EADDRINUSE") {
    console.error(`[HHMusic server] port ${PORT} is already in use`);
    process.exit(1);
  }
  throw err;
});
