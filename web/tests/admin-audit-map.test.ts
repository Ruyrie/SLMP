import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import AuditLocationMap from '../apps/admin/src/views/audit/components/AuditLocationMap.vue'
import { locationMarker } from '../apps/admin/src/views/audit/components/location'

const farm = { longitude: 118.9123456, latitude: 42.3412345 }
const pages: VueWrapper[] = []

function mountMap(markers = [locationMarker(farm, '北湾牛场')!]) {
  const page = mount(AuditLocationMap, {
    props: { markers },
    attachTo: document.body,
    global: { stubs: { ZwIcon: true } }
  })
  pages.push(page)
  return page
}

async function waitForMap(page: VueWrapper) {
  await vi.waitFor(() => expect(page.find('.leaflet-tile').exists()).toBe(true))
}

beforeEach(() => {
  // 真实 Leaflet 需要可测量的容器；jsdom 不执行 CSS 布局。
  vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(360)
  vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(220)
})

afterEach(() => {
  pages.splice(0).forEach((page) => page.unmount())
  vi.restoreAllMocks()
  vi.useRealTimers()
})

describe('audit map coordinates', () => {
  it.each([
    undefined, null, {}, { longitude: null, latitude: 42 }, { longitude: 118, latitude: '' },
    { longitude: ' ', latitude: 42 }, { longitude: 118, latitude: NaN },
    { longitude: Infinity, latitude: 42 }, { longitude: 181, latitude: 42 }, { longitude: 118, latitude: 90 }
  ])('does not fabricate a position from missing or invalid coordinates: %j', (location) => {
    expect(locationMarker(location, '位置')).toBeNull()
  })

  it('preserves precise API coordinates, numeric strings and valid zero coordinates', () => {
    expect(locationMarker(farm, '北湾牛场')).toMatchObject(farm)
    expect(locationMarker({ longitude: '118.9123456', latitude: '42.3412345' }, '位置')).toMatchObject(farm)
    expect(locationMarker({ longitude: 0, latitude: 0 }, '位置')).toMatchObject({ longitude: 0, latitude: 0 })
  })
})

describe('audit location map with real Leaflet', () => {
  it('requests actual map tiles around the farm and supports zoom and recenter', async () => {
    const page = mountMap()
    await waitForMap(page)
    const firstTile = page.get<HTMLImageElement>('.leaflet-tile').element
    const tileUrl = new URL(firstTile.src)
    expect(tileUrl.hostname).toMatch(/^webrd0[1-4]\.is\.autonavi\.com$/)
    expect(tileUrl.searchParams.get('z')).toBe('15')
    // 118.91E / 42.34N at z15 is near x27207 / y12123, not a percentage on an SVG.
    expect(Number(tileUrl.searchParams.get('x'))).toBeGreaterThan(27000)
    expect(Number(tileUrl.searchParams.get('y'))).toBeGreaterThan(12000)
    expect(page.get('.leaflet-marker-icon').attributes('title')).toBe('北湾牛场')
    await page.get('.leaflet-tile').trigger('load')
    expect(page.text()).not.toContain('正在加载地图')
    await page.get('.leaflet-control-zoom-in').trigger('click')
    expect(page.findAll<HTMLImageElement>('.leaflet-tile').some((tile) => new URL(tile.element.src).searchParams.get('z') === '16')).toBe(true)
    await page.get('button[aria-label="回到养殖场位置"]').trigger('click')
    expect(page.findAll<HTMLImageElement>('.leaflet-tile').some((tile) => new URL(tile.element.src).searchParams.get('z') === '15')).toBe(true)
  })

  it('fits current and applied positions and updates when another application is opened', async () => {
    const page = mountMap([
      locationMarker(farm, '当前位置', 'neutral')!,
      locationMarker({ longitude: 118.95, latitude: 42.36 }, '申请位置')!
    ])
    await waitForMap(page)
    expect(page.findAll('.leaflet-marker-icon')).toHaveLength(2)
    expect(page.findAll('.leaflet-tooltip').map((tooltip) => tooltip.text())).toEqual(['当前位置', '申请位置'])
    await page.setProps({ markers: [locationMarker({ longitude: 116.4, latitude: 39.9 }, '<img src=x onerror=alert(1)>')!] })
    expect(page.findAll('.leaflet-marker-icon')).toHaveLength(1)
    expect(page.get('.leaflet-tooltip').text()).toBe('<img src=x onerror=alert(1)>')
    expect(page.get('.leaflet-tooltip').find('img').exists()).toBe(false)
  })

  it('shows missing coordinates without requesting a fabricated map, and can receive coordinates later', async () => {
    const page = mountMap([])
    await flushPromises()
    expect(page.text()).toContain('未提供有效经纬度')
    expect(page.find('.leaflet-container').exists()).toBe(false)
    await page.setProps({ markers: [locationMarker(farm, '北湾牛场')!] })
    await waitForMap(page)
    await page.setProps({ markers: [] })
    expect(page.find('.leaflet-container').exists()).toBe(false)
    expect(page.text()).toContain('未提供有效经纬度')
    expect(page.find('[role="status"]').exists()).toBe(false)
  })

  it('reports a tile outage and retries with a fresh map instead of displaying a schematic', async () => {
    const page = mountMap()
    await waitForMap(page)
    for (const tile of page.findAll('.leaflet-tile').slice(0, 3)) await tile.trigger('error')
    expect(page.get('[role="alert"]').text()).toContain('地图底图加载失败')
    await page.get('.alm-error button').trigger('click')
    await waitForMap(page)
    await page.get('.leaflet-tile').trigger('load')
    expect(page.find('[role="alert"]').exists()).toBe(false)
    expect(page.find('.zw-map-svg').exists()).toBe(false)
  })

  it('times out stalled tile requests and clears the deadline after unmount', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const page = mountMap()
    await vi.waitFor(() => expect(page.find('.leaflet-tile').exists()).toBe(true))
    await vi.advanceTimersByTimeAsync(8000)
    expect(page.get('[role="alert"]').text()).toContain('地图底图加载失败')
    page.unmount()
    pages.splice(pages.indexOf(page), 1)
    expect(vi.getTimerCount()).toBe(0)
  })
})
