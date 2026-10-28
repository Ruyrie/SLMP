import { afterEach, describe, expect, it, vi } from 'vitest'
import { applySecurityConfig, regeoToFields, wgs84ToGcj02 } from '../apps/user/src/views/farm/components/amap'
import { qrDataUrl } from '../apps/user/src/views/herd/qr'

afterEach(() => {
  vi.unstubAllEnvs()
})

describe('坐标转换 WGS-84 → GCJ-02', () => {
  it('国内坐标偏移在几百米量级', () => {
    const [lng, lat] = wgs84ToGcj02(116.397428, 39.90923)
    expect(lng).toBeCloseTo(116.403669, 3)
    expect(lat).toBeCloseTo(39.910634, 3)
  })

  it('境外坐标不转换', () => {
    expect(wgs84ToGcj02(2.3522, 48.8566)).toEqual([2.3522, 48.8566])
  })
})

describe('逆地理结果回填', () => {
  it('取省市区、乡镇、adcode 和地址', () => {
    expect(regeoToFields({
      formattedAddress: '内蒙古自治区赤峰市松山区太平地镇',
      addressComponent: { province: '内蒙古自治区', city: '赤峰市', district: '松山区', township: '太平地镇', adcode: '150404' }
    })).toEqual({
      address: '内蒙古自治区赤峰市松山区太平地镇',
      province: '内蒙古自治区',
      city: '赤峰市',
      district: '松山区',
      township: '太平地镇',
      adcode: '150404'
    })
  })

  it('直辖市 city 为空数组时用省名，没有行政区时返回 null', () => {
    expect(regeoToFields({ addressComponent: { province: '北京市', city: [], district: '朝阳区', adcode: '110105' } })?.city).toBe('北京市')
    expect(regeoToFields({ addressComponent: { province: [], adcode: [] } })).toBeNull()
  })
})

describe('高德安全密钥', () => {
  it('开发环境用 securityJsCode，生产环境走 /_AMapService 代理', () => {
    const dev: Record<string, unknown> = {}
    vi.stubEnv('DEV', true)
    vi.stubEnv('VITE_AMAP_SECURITY_CODE', 'dev-code')
    applySecurityConfig(dev)
    expect(dev._AMapSecurityConfig).toEqual({ securityJsCode: 'dev-code' })

    const prod: Record<string, unknown> = {}
    vi.stubEnv('DEV', false)
    applySecurityConfig(prod)
    expect(prod._AMapSecurityConfig).toEqual({ serviceHost: `${window.location.origin}/_AMapService` })
  })
})

describe('批次二维码按内容直接生成', () => {
  it('生成 PNG dataURL，内容为空时报“二维码尚未生成”', async () => {
    await expect(qrDataUrl('https://zmt.example/s/b/k7Rm2xQ9pT', 64)).resolves.toMatch(/^data:image\/png;base64,/)
    await expect(qrDataUrl('  ')).rejects.toThrow('二维码尚未生成')
  })
})
