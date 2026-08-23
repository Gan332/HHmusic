/**
 * dsh-fonts — host half.
 *
 * Serves the bundled webfonts over the harness web server so the browser half
 * can load them offline (same origin, no CORS). The rest of the feature lives
 * in the browser half (`./client`): the FontRegistry, the settings row, and
 * the CSS-variable application.
 */
import { existsSync, readFileSync } from 'node:fs'
import { dirname, join, normalize, basename } from 'node:path'
import { fileURLToPath } from 'node:url'

const FONT_DIR = join(dirname(fileURLToPath(import.meta.url)), '..', 'data', 'fonts')
const ROUTE = '/plugins/dsh-fonts/fonts'

export const inject = ['webServer']

export function apply(ctx) {
  const dispose = ctx.webServer.register({
    kind: 'prefix',
    path: ROUTE,
    handler(req, res) {
      const raw = (req.url ?? '').split('?')[0].slice(ROUTE.length)
      const name = basename(normalize(decodeURIComponent(raw)))
      if (!/^[a-z0-9][a-z0-9.-]*\.(woff2|txt)$/i.test(name)) {
        res.statusCode = 404
        res.end('not found')
        return
      }
      const file = join(FONT_DIR, name)
      if (!existsSync(file)) {
        res.statusCode = 404
        res.end('not found')
        return
      }
      res.statusCode = 200
      res.setHeader('content-type', name.endsWith('.woff2') ? 'font/woff2' : 'text/plain; charset=utf-8')
      res.setHeader('cache-control', 'public, max-age=31536000, immutable')
      res.end(readFileSync(file))
    },
  })
  ctx.on('dispose', dispose)
}
