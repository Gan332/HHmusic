// Generates lib/client.js from lib/client.tpl.js + data/presets.json.
// Run from the repo root: node scripts/gen-client.mjs
import { readFileSync, writeFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const data = JSON.parse(readFileSync(join(root, 'data', 'presets.json'), 'utf8'))

const template = readFileSync(join(root, 'lib', 'client.tpl.js'), 'utf8')
const client = template.replace('__PRESETS__', JSON.stringify(data.presets, null, 2))
writeFileSync(join(root, 'lib', 'client.js'), client)

console.log(`generated lib/client.js with ${data.presets.length} presets`)
