import express from "express";
import cors from "cors";
import { randomUUID } from "node:crypto";
import {
  searchSongs,
  searchArtists,
  getSongDetail,
  getSongUrl,
  getLyric,
  getPlaylistDetail,
  getToplists,
  getRecommendSongs,
  getRecommendPlaylists,
  getArtistSongs,
  getArtistAlbums,
  getAlbumDetail,
  getHotSearches,
  getNewSongs,
  getPlaylistCatlist,
  getTopPlaylists,
  getPersonalFm,
  getUserPlaylists,
  subscribePlaylist,
  likeSong,
  pingUpstream,
} from "./netease.js";

/** Hard cap for every page-size query param. */
export const MAX_LIMIT = 100;

/** Wrap an async route handler so rejections reach the global error middleware. */
const asyncHandler = (fn) => (req, res, next) =>
  Promise.resolve(fn(req, res, next)).catch(next);

/**
 * Build the Express app. `deps` lets tests stub the NetEase upstream functions;
 * anything not provided falls back to the real implementation.
 */
export function createApp(deps = {}) {
  const api = {
    searchSongs,
    searchArtists,
    getSongDetail,
    getSongUrl,
    getLyric,
    getPlaylistDetail,
    getToplists,
    getRecommendSongs,
    getRecommendPlaylists,
    getArtistSongs,
    getArtistAlbums,
    getAlbumDetail,
    getHotSearches,
    getNewSongs,
    getPlaylistCatlist,
    getTopPlaylists,
    getPersonalFm,
    getUserPlaylists,
    subscribePlaylist,
    likeSong,
    ...deps,
  };

  const app = express();

  // Per-request id (echoed in every error body) + basic access log with timing.
  const logRequests = process.env.LOG_REQUESTS !== "0";
  app.use((req, res, next) => {
    req.requestId = randomUUID();
    const start = Date.now();
    res.on("finish", () => {
      if (logRequests) {
        const ms = Date.now() - start;
        const status = res.statusCode;
        const marker = status >= 500 ? "ERR " : status >= 400 ? "WARN" : "OK  ";
        console.log(`${new Date().toISOString()} ${marker} ${req.method} ${req.originalUrl} -> ${status} (${ms}ms) id=${req.requestId}`);
      }
    });
    next();
  });

  // CORS: native clients (Android OkHttp) send no Origin header and are always
  // allowed. Any Origin not whitelisted via ALLOWED_ORIGINS (comma-separated)
  // is hard-rejected with a JSON 403 — browsers can't proxy us by default.
  const allowedOrigins = (process.env.ALLOWED_ORIGINS ?? "")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  app.use((req, res, next) => {
    const origin = req.headers.origin;
    if (origin && !allowedOrigins.includes(origin)) {
      return res.status(403).json({ code: 403, msg: "origin not allowed", requestId: req.requestId });
    }
    next();
  });
  app.use(cors({ origin: allowedOrigins.length ? allowedOrigins : false }));
  app.use(express.json({ limit: "100kb" }));

  // Upstream timeout: respond with a stable JSON error instead of hanging.
  const timeoutMs = Math.max(1, Number(process.env.REQUEST_TIMEOUT_MS ?? 15000));
  app.use((req, res, next) => {
    res.setTimeout(timeoutMs, () => {
      res.status(504).json({ code: 504, msg: "upstream timeout" });
    });
    next();
  });

  function sendResult(res, result) {
    if (!result || typeof result !== "object") {
      return res.status(502).json({ code: 502, msg: "upstream error" });
    }
    const code = result.status && result.status >= 400 ? result.status : 200;
    return res.status(code).json(result.data ?? result);
  }

  function toInt(v, def = 0) {
    const n = parseInt(v, 10);
    return Number.isFinite(n) ? n : def;
  }

  const limitParam = (v, def) => Math.min(Math.max(toInt(v, def), 1), MAX_LIMIT);

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

  function normalizeSongsFrom(list = []) {
    return list.map(normalizeSongNetease);
  }

  app.get(
    "/api/search",
    asyncHandler(async (req, res) => {
      const keyword = (req.query.s ?? req.query.keyword ?? "").toString().trim();
      if (!keyword) return res.status(400).json({ code: 400, msg: "missing keyword" });
      const limit = limitParam(req.query.limit, 30);
      const offset = Math.max(0, toInt(req.query.offset, 0));
      const r = await api.searchSongs(keyword, limit, offset);
      sendResult(res, {
        status: 200,
        data: {
          code: 200,
          songCount: r.data?.result?.songCount ?? 0,
          songs: normalizeSongsFrom(r.data?.result?.songs),
        },
      });
    })
  );

  app.get(
    "/api/song/detail",
    asyncHandler(async (req, res) => {
      const ids = (req.query.ids ?? "").toString();
      if (!ids) return res.status(400).json({ code: 400, msg: "missing ids" });
      const r = await api.getSongDetail(ids.split(",").map((x) => toInt(x)));
      sendResult(res, { status: 200, data: { code: 200, songs: normalizeSongsFrom(r.data?.songs) } });
    })
  );

  app.get(
    "/api/song/url",
    asyncHandler(async (req, res) => {
      const id = toInt(req.query.id, 0);
      if (!id) return res.status(400).json({ code: 400, msg: "missing id" });
      const level = (req.query.level ?? "exhigh").toString();
      const r = await api.getSongUrl(id, level);
      const d = (r.data?.data ?? [])[0] ?? {};
      sendResult(res, {
        status: 200,
        data: {
          code: 200,
          id: d.id,
          url: d.url ?? null,
          br: d.br,
          size: d.size,
          type: d.type,
          md5: d.md5,
          // If url is null the track is usually copyrighted/VIP-only on NetEase.
          freeTrialInfo: d.freeTrialInfo ?? null,
        },
      });
    })
  );

  app.get(
    "/api/lyric",
    asyncHandler(async (req, res) => {
      const id = toInt(req.query.id, 0);
      if (!id) return res.status(400).json({ code: 400, msg: "missing id" });
      const r = await api.getLyric(id);
      const lyc = r.data ?? {};
      sendResult(res, {
        status: 200,
        data: {
          code: 200,
          lrc: lyc.lrc?.lyric ?? "",
          tlyric: lyc.tlyric?.lyric ?? "",
          romalrc: lyc.romalrc?.lyric ?? "",
          yrc: lyc.yrc?.lyric ?? "",
        },
      });
    })
  );

  app.get(
    "/api/playlist/detail",
    asyncHandler(async (req, res) => {
      const id = toInt(req.query.id, 0);
      if (!id) return res.status(400).json({ code: 400, msg: "missing id" });
      const r = await api.getPlaylistDetail(id);
      const pl = r.data?.playlist ?? {};
      sendResult(res, {
        status: 200,
        data: {
          code: 200,
          id: pl.id,
          name: pl.name,
          coverImgUrl: pl.coverImgUrl,
          creator: { id: pl.creator?.id, nickname: pl.creator?.nickname },
          tracks: normalizeSongsFrom(pl.tracks),
        },
      });
    })
  );

  app.get(
    "/api/toplist",
    asyncHandler(async (_req, res) => {
      const r = await api.getToplists();
      const list = (r.data?.list ?? []).map((t) => ({
        id: t.id,
        name: t.name,
        coverImgUrl: t.coverImgUrl,
        description: t.description,
        updateFrequency: t.updateFrequency,
      }));
      sendResult(res, { status: 200, data: { code: 200, list } });
    })
  );

  app.post(
    "/api/song/like",
    asyncHandler(async (req, res) => {
      const id = toInt(req.body?.id, 0);
      if (!id) return res.status(400).json({ code: 400, msg: "missing id" });
      const like = req.body?.like ?? true;
      const r = await api.likeSong(id, like);
      sendResult(res, { status: 200, data: { code: 200, id, like, raw: r.data?.code } });
    })
  );

  app.get(
    "/api/recommend/songs",
    asyncHandler(async (req, res) => {
      const limit = limitParam(req.query.limit, 30);
      const r = await api.getRecommendSongs(limit);
      const songs = normalizeSongsFrom(r.data?.data?.dailySongs ?? r.data?.recommend);
      sendResult(res, { status: 200, data: { code: 200, songs } });
    })
  );

  app.get(
    "/api/recommend/playlists",
    asyncHandler(async (req, res) => {
      const limit = limitParam(req.query.limit, 12);
      const r = await api.getRecommendPlaylists(limit);
      const list = (r.data?.result ?? []).map((p) => ({
        id: p.id,
        name: p.name,
        picUrl: p.picUrl,
        playcount: p.playcount ?? p.playCount,
        creator: { nickname: (p.creator ?? {}).nickname },
      }));
      sendResult(res, { status: 200, data: { code: 200, list } });
    })
  );

  app.get(
    "/api/artist/search",
    asyncHandler(async (req, res) => {
      const keyword = (req.query.s ?? req.query.keyword ?? "").toString().trim();
      if (!keyword) return res.status(400).json({ code: 400, msg: "missing keyword" });
      const limit = limitParam(req.query.limit, 30);
      const offset = Math.max(0, toInt(req.query.offset, 0));
      const r = await api.searchArtists(keyword, limit, offset);
      const result = r.data?.result ?? {};
      const artists = (result.artists ?? []).map((a) => ({
        id: a.id,
        name: a.name,
        picUrl: a.img1v1Url ?? a.picUrl ?? null,
      }));
      sendResult(res, {
        status: 200,
        data: {
          code: 200,
          total: result.artistCount ?? result.count ?? 0,
          artists,
        },
      });
    })
  );

  app.get(
    "/api/artist/songs",
    asyncHandler(async (req, res) => {
      const id = toInt(req.query.id, 0);
      if (!id) return res.status(400).json({ code: 400, msg: "missing id" });
      const limit = limitParam(req.query.limit, 50);
      const offset = Math.max(0, toInt(req.query.offset, 0));
      const order = (req.query.order ?? "hot").toString();
      const r = await api.getArtistSongs(id, limit, offset, order);
      sendResult(res, {
        status: 200,
        data: {
          code: 200,
          total: r.data?.total ?? 0,
          songs: normalizeSongsFrom(r.data?.songs),
        },
      });
    })
  );

  app.get(
    "/api/artist/albums",
    asyncHandler(async (req, res) => {
      const id = toInt(req.query.id, 0);
      if (!id) return res.status(400).json({ code: 400, msg: "missing id" });
      const limit = limitParam(req.query.limit, 50);
      const offset = Math.max(0, toInt(req.query.offset, 0));
      const r = await api.getArtistAlbums(id, limit, offset);
      const albums = (r.data?.hotAlbums ?? []).map((a) => ({
        id: a.id,
        name: a.name,
        picUrl: a.picUrl ?? a.blurPicUrl ?? null,
        publishTime: a.publishTime ?? 0,
        songCount: a.size ?? 0,
      }));
      sendResult(res, {
        status: 200,
        data: { code: 200, more: r.data?.more ?? false, hotAlbums: albums },
      });
    })
  );

  app.get(
    "/api/album/detail",
    asyncHandler(async (req, res) => {
      const id = toInt(req.query.id, 0);
      if (!id) return res.status(400).json({ code: 400, msg: "missing id" });
      const r = await api.getAlbumDetail(id);
      const d = r.data ?? {};
      sendResult(res, {
        status: 200,
        data: {
          code: 200,
          id: d.album?.id ?? id,
          name: d.album?.name ?? "",
          coverImgUrl: d.album?.picUrl ?? null,
          description: d.album?.description ?? null,
          publishTime: d.album?.publishTime ?? 0,
          songs: normalizeSongsFrom(d.songs),
        },
      });
    })
  );

  app.get(
    "/api/new/song",
    asyncHandler(async (req, res) => {
      const limit = limitParam(req.query.limit, 30);
      const r = await api.getNewSongs(limit);
      const items = normalizeSongsFrom(
        (r.data?.result ?? r.data?.data ?? []).map((it) => it.song ?? it)
      );
      sendResult(res, { status: 200, data: { code: 200, songs: items } });
    })
  );

  app.get(
    "/api/search/hot",
    asyncHandler(async (_req, res) => {
      const r = await api.getHotSearches();
      const hots = (r.data?.result?.hots ?? [])
        .map((h) => (h?.first ?? "").toString().trim())
        .filter(Boolean);
      sendResult(res, { status: 200, data: { code: 200, hots } });
    })
  );

  // v1.7: playlist plaza — category list + hot/new playlists per category.
  app.get(
    "/api/playlist/catlist",
    asyncHandler(async (_req, res) => {
      const r = await api.getPlaylistCatlist();
      const subs = (r.data?.sub ?? []).map((c) => ({
        name: c.name,
        category: c.category,
        hot: c.hot ?? false,
      }));
      const categories = Object.entries(r.data?.categories ?? {}).map(([k, v]) => ({
        id: Number(k),
        name: v,
      }));
      sendResult(res, {
        status: 200,
        data: { code: 200, categories, sub: subs },
      });
    })
  );

  app.get(
    "/api/top/playlist",
    asyncHandler(async (req, res) => {
      const cat = (req.query.cat ?? "全部").toString();
      const limit = limitParam(req.query.limit, 30);
      const offset = Math.max(0, toInt(req.query.offset, 0));
      const order = (req.query.order ?? "hot").toString() === "new" ? "new" : "hot";
      const r = await api.getTopPlaylists(cat, limit, offset, order);
      const list = (r.data?.playlists ?? []).map((p) => ({
        id: p.id,
        name: p.name,
        picUrl: p.coverImgUrl ?? p.picUrl ?? null,
        playcount: p.playcount ?? p.playCount ?? 0,
        creator: { nickname: (p.creator ?? {}).nickname },
      }));
      sendResult(res, {
        status: 200,
        data: { code: 200, total: r.data?.total ?? 0, more: r.data?.more ?? false, list },
      });
    })
  );

  // v1.7: personal FM — a small batch of songs for radio-style playback.
  app.get(
    "/api/personal/fm",
    asyncHandler(async (_req, res) => {
      const r = await api.getPersonalFm();
      sendResult(res, {
        status: 200,
        data: { code: 200, songs: normalizeSongsFrom(r.data?.data) },
      });
    })
  );

  // v1.8: the logged-in user's cloud playlists (session rides the MUSIC_U
  // cookie forwarded by the client). specialType 5 = "我喜欢的音乐".
  app.get(
    "/api/user/playlists",
    asyncHandler(async (req, res) => {
      const uid = toInt(req.query.uid, 0);
      if (!uid) return res.status(400).json({ code: 400, msg: "missing uid" });
      const limit = limitParam(req.query.limit, 30);
      const offset = Math.max(0, toInt(req.query.offset, 0));
      const r = await api.getUserPlaylists(uid, limit, offset);
      const playlists = (r.data?.playlist ?? []).map((p) => ({
        id: p.id,
        name: p.name,
        coverImgUrl: p.coverImgUrl ?? null,
        trackCount: p.trackCount ?? 0,
        creator: p.creator
          ? { id: p.creator.userId ?? p.creator.id, nickname: p.creator.nickname }
          : null,
        specialType: p.specialType ?? 0,
      }));
      sendResult(res, { status: 200, data: { code: 200, playlists } });
    })
  );

  // v1.8: cloud playlist subscribe (t=1) / unsubscribe (t=2).
  app.post(
    "/api/playlist/subscribe",
    asyncHandler(async (req, res) => {
      const id = toInt(req.body?.id, 0);
      if (!id) return res.status(400).json({ code: 400, msg: "missing id" });
      const t = toInt(req.body?.t, 1);
      if (t !== 1 && t !== 2) {
        return res.status(400).json({ code: 400, msg: "t must be 1 or 2" });
      }
      const r = await api.subscribePlaylist(id, t);
      sendResult(res, { status: 200, data: { code: 200, id, t, raw: r.data?.code } });
    })
  );

  app.get("/api/health", async (_req, res) => {
    // Upstream reachability is checked with a short timeout; the endpoint stays
    // 200 even when NetEase is unreachable so orchestrators can probe liveness,
    // while `upstream` tells actual service health.
    const upstreamOk = await pingUpstream().catch(() => false);
    res.json({
      code: 200,
      ok: true,
      upstream: upstreamOk ? "ok" : "down",
      ts: Date.now(),
    });
  });

  // Unknown routes -> JSON, never the HTML fallback.
  app.use((req, res) =>
    res.status(404).json({ code: 404, msg: "not found", requestId: req.requestId })
  );

  // Global error handler: always JSON, never a default Express HTML page.
  // eslint-disable-next-line no-unused-vars
  app.use((err, req, res, next) => {
    console.error(`[HHMusic server error] id=${req.requestId}`, err?.message ?? err);
    if (res.headersSent) {
      return next(err);
    }
    res.status(500).json({ code: 500, msg: "internal error", requestId: req.requestId });
  });

  return app;
}
