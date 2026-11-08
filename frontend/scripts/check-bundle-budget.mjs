/**
 * The bundle budget.
 *
 * The build fails when a chunk grows past its limit, so a dependency that
 * quietly doubles the download does not reach production unnoticed.
 *
 * The limits are on the gzipped size — what the browser actually downloads.
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { gzipSync } from 'node:zlib'
import { join } from 'node:path'

const DIST = new URL('../dist/', import.meta.url).pathname

/** The budget, in bytes of gzipped output. */
const BUDGET = {
  /** The entry chunk: React, the router and the shell. */
  entry: 100 * 1024,
  /** Any single lazily-loaded chunk. */
  chunk: 100 * 1024,
  /** The whole download a first-time visitor has to make. */
  total: 250 * 1024,
}

function walk(directory) {
  const files = []
  for (const name of readdirSync(directory)) {
    const path = join(directory, name)
    if (statSync(path).isDirectory()) {
      files.push(...walk(path))
    } else {
      files.push(path)
    }
  }
  return files
}

const assets = walk(join(DIST, 'assets'))

const chunks = assets
  .filter((path) => path.endsWith('.js'))
  .map((path) => {
    const contents = readFileSync(path)
    return {
      name: path.slice(DIST.length),
      raw: contents.length,
      gzip: gzipSync(contents).length,
    }
  })
  .sort((a, b) => b.gzip - a.gzip)

const css = assets
  .filter((path) => path.endsWith('.css'))
  .map((path) => {
    const contents = readFileSync(path)
    return { name: path.slice(DIST.length), gzip: gzipSync(contents).length }
  })

const totalGzip =
  chunks.reduce((sum, chunk) => sum + chunk.gzip, 0) +
  css.reduce((sum, sheet) => sum + sheet.gzip, 0)

console.log('The bundle:')
for (const chunk of chunks) {
  console.log(
    `  ${chunk.name.padEnd(40)} ${format(chunk.raw)} raw, ${format(chunk.gzip)} gzip`,
  )
}
for (const sheet of css) {
  console.log(`  ${sheet.name.padEnd(40)} ${format(sheet.gzip)} gzip`)
}
console.log(`  ${'TOTAL'.padEnd(40)} ${format(totalGzip)} gzip`)
console.log('')

const failures = []

const entry = chunks.find((chunk) => chunk.name.includes('index-'))
if (entry && entry.gzip > BUDGET.entry) {
  failures.push(
    `the entry chunk is ${format(entry.gzip)} gzipped, over the ${format(BUDGET.entry)} budget`,
  )
}

for (const chunk of chunks) {
  if (chunk === entry) {
    continue
  }
  if (chunk.gzip > BUDGET.chunk) {
    failures.push(
      `${chunk.name} is ${format(chunk.gzip)} gzipped, over the ${format(BUDGET.chunk)} budget`,
    )
  }
}

if (totalGzip > BUDGET.total) {
  failures.push(
    `the whole download is ${format(totalGzip)} gzipped, over the ${format(BUDGET.total)} budget`,
  )
}

if (failures.length > 0) {
  console.error('The bundle is over budget:')
  for (const failure of failures) {
    console.error(`  ${failure}`)
  }
  process.exit(1)
}

console.log(
  `Within budget: the whole download is ${format(totalGzip)} of the ${format(BUDGET.total)} allowed.`,
)

function format(bytes) {
  return `${(bytes / 1024).toFixed(1)} kB`
}
