import { createApp } from "./app.js";

const app = createApp();
const HOST = process.env.HOST ?? "0.0.0.0";
const PORT = Number(process.env.PORT ?? 3000);
app.listen(PORT, HOST, () => {
  console.log(`[HHMusic server] listening on http://${HOST}:${PORT}`);
});