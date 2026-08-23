import { test, describe } from "node:test";
import assert from "node:assert/strict";
import { createApp, MAX_LIMIT } from "../src/app.js";

/** Prebuilt response shape used by the real netease.js wrappers. */
const ok = (data, status = 200) => ({ status, data });

async function withServer(app, fn) {
  const server = app.listen(0);
  await new Promise((resolve) => server.once("listening", resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    await fn(base);
  } finally {
    server.closeAllConnections?.();
    await new Promise((resolve) => server.close(resolve));
  }
}

async function getJson(base, path, headers = {}) {
  const res = await fetch(base + path, { headers });
  return { status: res.status, body: await res.json() };
}

async function postJson(base, path, body) {
  const res = await fetch(base + path, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  return { status: res.status, body: await res.json() };
}

function makeApp(overrides = {}) {
  return createApp({
    searchSongs: async () =>
      ok({
        result: {
          songCount: 777,
          songs: [
            { id: 1, name: "晴天", ar: [{ id: 11, name: "周杰伦" }], al: { id: 111, name: "叶惠美", picUrl: "x" }, dt: 269000, fee: 0 },
            { id: 2, name: "七里香", artists: [{ id: 11, name: "周杰伦" }], album: { id: 112, name: "七里香" }, duration: 260000, fee: 1 },
          ],
        },
      }),
    searchArtists: async () =>
      ok({
        result: {
          artistCount: 2,
          artists: [
            { id: 12, name: "周杰伦", img1v1Url: "https://p.music.163.com/a.jpg" },
            { id: 13, name: "林俊杰", picUrl: "https://p.music.163.com/b.jpg" },
          ],
        },
      }),
    getSongDetail: async () => ok({ songs: [{ id: 1, name: "晴天" }] }),
    getSongUrl: async (id) => ok({ data: [{ id, url: null, br: 0 }] }),
    getLyric: async () => ok({ lrc: { lyric: "[00:01.00]hi" } }),
    getPlaylistDetail: async () => ok({ playlist: { id: 9, name: "p", tracks: [{ id: 3, name: "t" }] } }),
    getToplists: async () => ok({ list: [{ id: 1, name: "热歌榜", coverImgUrl: "c" }] }),
    getRecommendSongs: async () => ok({ data: { dailySongs: [{ id: 4, name: "r" }] } }),
    getRecommendPlaylists: async () => ok({ result: [{ id: 5, name: "pl" }] }),
    getArtistSongs: async () => ok({ total: 9, songs: [{ id: 6, name: "a" }] }),
    getHotSearches: async () =>
      ok({ result: { hots: [{ first: "周杰伦" }, { first: " 林俊杰 " }, { first: "" }, {}] } }),
    getArtistAlbums: async () =>
      ok({
        more: true,
        hotAlbums: [
          { id: 111, name: "叶惠美", picUrl: "https://p.music.163.com/al.jpg", publishTime: 1056969600000, size: 11 },
          { id: 222, name: "无封面", blurPicUrl: "https://p.music.163.com/blur.jpg", publishTime: 0, size: 10 },
        ],
      }),
    getAlbumDetail: async () =>
      ok({
        album: {
          id: 18879,
          name: "叶惠美",
          picUrl: "https://p.music.163.com/cover.jpg",
          description: "第四张创作专辑",
          publishTime: 1056969600000,
        },
        songs: [{ id: 185811, name: "以父之名", ar: [{ id: 42, name: "周杰伦" }], al: { id: 18879, name: "叶惠美" }, dt: 342000 }],
      }),
    getNewSongs: async () => ok({ result: [{ song: { id: 7, name: "n" } }] }),
    likeSong: async (id, like) => ok({ code: 200, data: { code: 200, id, like } }),
    ...overrides,
  });
}

describe("error contract", () => {
  test("unknown route returns a JSON 404", async () => {
    await withServer(makeApp(), async (base) => {
      const { status, body } = await getJson(base, "/api/nope");
      assert.equal(status, 404);
      assert.equal(body.code, 404);
      assert.equal(typeof body.msg, "string");
    });
  });

  test("unhandled upstream rejection yields JSON 500 with requestId", async () => {
    const app = createApp({ searchSongs: async () => { throw new Error("boom"); } });
    await withServer(app, async (base) => {
      const { status, body } = await getJson(base, "/api/search?s=周");
      assert.equal(status, 500);
      assert.equal(body.code, 500);
      assert.equal(body.msg, "internal error");
      assert.equal(typeof body.requestId, "string");
      assert.ok(body.requestId.length > 0);
    });
  });

  test("404 responses carry a requestId", async () => {
    await withServer(makeApp(), async (base) => {
      const { body } = await getJson(base, "/api/nope");
      assert.equal(typeof body.requestId, "string");
    });
  });

  test("timeout middleware responds 504 JSON", async () => {
    const prev = process.env.REQUEST_TIMEOUT_MS;
    process.env.REQUEST_TIMEOUT_MS = "100";
    const app = createApp({
      searchSongs: () => new Promise(() => { /* never resolves */ }),
    });
    try {
      await withServer(app, async (base) => {
        const { status, body } = await getJson(base, "/api/search?s=x");
        assert.equal(status, 504);
        assert.equal(body.code, 504);
      });
    } finally {
      if (prev === undefined) delete process.env.REQUEST_TIMEOUT_MS;
      else process.env.REQUEST_TIMEOUT_MS = prev;
    }
  });
});

describe("search", () => {
  test("missing keyword -> 400", async () => {
    await withServer(makeApp(), async (base) => {
      const { status, body } = await getJson(base, "/api/search");
      assert.equal(status, 400);
      assert.equal(body.code, 400);
    });
  });

  test("normalizes songs and passes songCount through", async () => {
    await withServer(makeApp(), async (base) => {
      const { status, body } = await getJson(base, "/api/search?s=周杰伦&limit=2");
      assert.equal(status, 200);
      assert.equal(body.songCount, 777);
      assert.equal(body.songs.length, 2);
      assert.deepEqual(body.songs[0], {
        id: 1,
        name: "晴天",
        artists: [{ id: 11, name: "周杰伦" }],
        album: { id: 111, name: "叶惠美", picUrl: "x" },
        duration: 269000,
        fee: 0,
      });
    });
  });
});

describe("artist routes", () => {
  test("artist search normalizes artists and passes total through", async () => {
    await withServer(makeApp(), async (base) => {
      const { status, body } = await getJson(base, "/api/artist/search?s=周杰伦&limit=10");
      assert.equal(status, 200);
      assert.equal(body.total, 2);
      assert.deepEqual(body.artists, [
        { id: 12, name: "周杰伦", picUrl: "https://p.music.163.com/a.jpg" },
        { id: 13, name: "林俊杰", picUrl: "https://p.music.163.com/b.jpg" },
      ]);
    });
  });

  test("artist search missing keyword -> 400", async () => {
    await withServer(makeApp(), async (base) => {
      const { status, body } = await getJson(base, "/api/artist/search");
      assert.equal(status, 400);
      assert.equal(body.code, 400);
    });
  });

  test("artist songs passes total and normalized songs", async () => {
    await withServer(makeApp(), async (base) => {
      const { status, body } = await getJson(base, "/api/artist/songs?id=6&limit=5&order=hot");
      assert.equal(status, 200);
      assert.equal(body.total, 9);
      assert.deepEqual(body.songs, [{ id: 6, name: "a", artists: [], album: {}, duration: 0, fee: 0 }]);
    });
  });

  test("artist songs missing id -> 400", async () => {
    await withServer(makeApp(), async (base) => {
      const { status, body } = await getJson(base, "/api/artist/songs");
      assert.equal(status, 400);
      assert.equal(body.code, 400);
    });
  });
});

describe("artist albums & album detail routes", () => {
  test("artist albums normalizes hotAlbums and passes more through", async () => {
    await withServer(makeApp(), async (base) => {
      const { status, body } = await getJson(base, "/api/artist/albums?id=42&limit=10&offset=5");
      assert.equal(status, 200);
      assert.equal(body.more, true);
      assert.deepEqual(body.hotAlbums, [
        { id: 111, name: "叶惠美", picUrl: "https://p.music.163.com/al.jpg", publishTime: 1056969600000, songCount: 11 },
        { id: 222, name: "无封面", picUrl: "https://p.music.163.com/blur.jpg", publishTime: 0, songCount: 10 },
      ]);
    });
  });

  test("artist albums falls back to real upstream when not stubbed", async () => {
    // No getArtistAlbums stub -> createApp must wire the real netease wrapper.
    const app = createApp({});
    await withServer(app, async (base) => {
      const { status } = await getJson(base, "/api/artist/albums?id=0");
      assert.equal(status, 400); // validation happens before any upstream call
    });
  });

  test("artist albums missing id -> 400", async () => {
    await withServer(makeApp(), async (base) => {
      const { status, body } = await getJson(base, "/api/artist/albums");
      assert.equal(status, 400);
      assert.equal(body.code, 400);
    });
  });

  test("album detail returns metadata plus normalized songs", async () => {
    await withServer(makeApp(), async (base) => {
      const { status, body } = await getJson(base, "/api/album/detail?id=18879");
      assert.equal(status, 200);
      assert.equal(body.id, 18879);
      assert.equal(body.name, "叶惠美");
      assert.equal(body.coverImgUrl, "https://p.music.163.com/cover.jpg");
      assert.equal(body.publishTime, 1056969600000);
      assert.deepEqual(body.songs, [
        {
          id: 185811,
          name: "以父之名",
          artists: [{ id: 42, name: "周杰伦" }],
          album: { id: 18879, name: "叶惠美" },
          duration: 342000,
          fee: 0,
        },
      ]);
    });
  });

  test("album detail missing id -> 400", async () => {
    await withServer(makeApp(), async (base) => {
      const { status } = await getJson(base, "/api/album/detail");
      assert.equal(status, 400);
    });
  });
});

describe("user playlists & subscribe routes", () => {
  test("user playlists normalizes rows and passes specialType through", async () => {
    await withServer(
      makeApp({
        getUserPlaylists: async () =>
          ok({
            playlist: [
              {
                id: 111,
                name: "我喜欢的音乐",
                coverImgUrl: "https://p.music.163.com/liked.jpg",
                trackCount: 42,
                specialType: 5,
                creator: { userId: 9, nickname: "我" },
              },
              { id: 222, name: "歌单2", trackCount: 0, creator: null },
            ],
          }),
      }),
      async (base) => {
        const { status, body } = await getJson(base, "/api/user/playlists?uid=9");
        assert.equal(status, 200);
        assert.deepEqual(body.playlists, [
          {
            id: 111,
            name: "我喜欢的音乐",
            coverImgUrl: "https://p.music.163.com/liked.jpg",
            trackCount: 42,
            creator: { id: 9, nickname: "我" },
            specialType: 5,
          },
          {
            id: 222,
            name: "歌单2",
            coverImgUrl: null,
            trackCount: 0,
            creator: null,
            specialType: 0,
          },
        ]);
      }
    );
  });

  test("user playlists missing uid -> 400", async () => {
    await withServer(makeApp(), async (base) => {
      const { status } = await getJson(base, "/api/user/playlists");
      assert.equal(status, 400);
    });
  });

  test("subscribe requires id and t in {1,2}", async () => {
    await withServer(
      makeApp({
        subscribePlaylist: async (id, t) => ok({ code: 200, id, t }),
      }),
      async (base) => {
        const missing = await postJson(base, "/api/playlist/subscribe", {});
        assert.equal(missing.status, 400);

        const badT = await postJson(base, "/api/playlist/subscribe", { id: 5, t: 7 });
        assert.equal(badT.status, 400);

        const okSub = await postJson(base, "/api/playlist/subscribe", { id: 5, t: 1 });
        assert.equal(okSub.status, 200);
        assert.equal(okSub.body.code, 200);
        assert.equal(okSub.body.id, 5);
        assert.equal(okSub.body.t, 1);
      }
    );
  });

  test("subscribe falls back to real upstream when not stubbed (validation first)", async () => {
    // No stub -> real netease wrapper wired; validation must reject before any call.
    const app = createApp({});
    await withServer(app, async (base) => {
      const { status } = await postJson(base, "/api/playlist/subscribe", {});
      assert.equal(status, 400);
    });
  });
});

describe("song url (VIP / copyright responses)", () => {
  test("upstream returning null url stays 200 with url:null", async () => {
    await withServer(makeApp(), async (base) => {
      const { status, body } = await getJson(base, "/api/song/url?id=1");
      assert.equal(status, 200);
      assert.equal(body.url, null);
      assert.equal(body.id, 1);
    });
  });

  test("missing id -> 400", async () => {
    await withServer(makeApp(), async (base) => {
      const { status } = await getJson(base, "/api/song/url");
      assert.equal(status, 400);
    });
  });
});

describe("misc routes", () => {
  test("health check", async () => {
    await withServer(makeApp(), async (base) => {
      const { status, body } = await getJson(base, "/api/health");
      assert.equal(status, 200);
      assert.equal(body.ok, true);
    });
  });

  test("hot search trims keywords and drops empties", async () => {
    await withServer(makeApp(), async (base) => {
      const { status, body } = await getJson(base, "/api/search/hot");
      assert.equal(status, 200);
      assert.deepEqual(body.hots, ["周杰伦", "林俊杰"]);
    });
  });

  test("like without id -> 400", async () => {
    await withServer(makeApp(), async (base) => {
      const { status } = await postJson(base, "/api/song/like", {});
      assert.equal(status, 400);
    });
  });
});

describe("CORS", () => {
  test("requests without Origin are allowed by default", async () => {
    await withServer(makeApp(), async (base) => {
      const { status } = await getJson(base, "/api/health");
      assert.equal(status, 200);
    });
  });

  test("cross-origin browser requests are rejected unless whitelisted", async () => {
    const prev = process.env.ALLOWED_ORIGINS;
    delete process.env.ALLOWED_ORIGINS;
    try {
      await withServer(makeApp(), async (base) => {
        const { status } = await getJson(base, "/api/health", { Origin: "https://evil.example" });
        assert.equal(status, 403);
      });
    } finally {
      if (prev !== undefined) process.env.ALLOWED_ORIGINS = prev;
    }
  });

  test("whitelisted origins are allowed", async () => {
    const prev = process.env.ALLOWED_ORIGINS;
    process.env.ALLOWED_ORIGINS = "https://app.example.com";
    try {
      await withServer(makeApp(), async (base) => {
        const { status } = await getJson(base, "/api/health", { Origin: "https://app.example.com" });
        assert.equal(status, 200);
      });
    } finally {
      if (prev === undefined) delete process.env.ALLOWED_ORIGINS;
      else process.env.ALLOWED_ORIGINS = prev;
    }
  });
});
