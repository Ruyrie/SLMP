// 服务端 Jackson 全局 SNAKE_CASE，SpringDoc 生成的属性名却是 Java  camelCase。
// 生成前端类型前把 components.schemas 里的属性名统一转成 snake_case，保证与真实 JSON 一致。
import { readFileSync, writeFileSync } from 'node:fs'

const src = process.argv[2] || '../docs/api/openapi.json'
const out = process.argv[3] || '../docs/api/openapi.snake.json'
const doc = JSON.parse(readFileSync(src, 'utf8'))

function snake(name) {
  return name
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1_$2')
    .toLowerCase()
}

function walk(node, seen) {
  if (!node || typeof node !== 'object' || seen.has(node)) return
  seen.add(node)
  if (Array.isArray(node)) {
    for (const item of node) walk(item, seen)
    return
  }
  if (node.properties && typeof node.properties === 'object') {
    const next = {}
    const renamed = new Map()
    for (const [key, value] of Object.entries(node.properties)) {
      const snakeKey = snake(key)
      renamed.set(key, snakeKey)
      next[snakeKey] = value
    }
    node.properties = next
    if (Array.isArray(node.required)) {
      node.required = node.required.map((key) => renamed.get(key) || snake(key))
    }
  }
  for (const [key, value] of Object.entries(node)) {
    if (key === 'properties' || key === 'required') continue
    walk(value, seen)
  }
}

for (const schema of Object.values(doc.components?.schemas || {})) {
  walk(schema, new Set())
}

writeFileSync(out, JSON.stringify(doc, null, 2), 'utf8')
console.log(`snake_case OpenAPI written: ${out}`)
