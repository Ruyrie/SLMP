import { readFileSync } from 'node:fs'
const a = JSON.parse(readFileSync('D:/Android_Program/SLMP/logs/diag/api.json', 'utf-8'))
const m = new Map()
for (const r of a) {
  if (r.status !== 200 || r.code !== 0) continue
  const cur = m.get(r.path) || { path: r.path, max: 0, lists: new Set() }
  cur.max = Math.max(cur.max, r.size || 0)
  for (const x of r.listKeys || []) cur.lists.add(x)
  m.set(r.path, cur)
}
const rows = [...m.values()].sort((x, y) => x.path.localeCompare(y.path))
for (const r of rows) console.log(String(r.max).padStart(7), r.path, [...r.lists].slice(0, 4).join(','))
