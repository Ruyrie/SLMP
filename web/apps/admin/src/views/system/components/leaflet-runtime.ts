/**
 * 管理端分布地图：Leaflet 只在概览页挂载时异步加载，
 * JS 与样式拆成独立 chunk，不增加管理端首屏体积。
 */
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

export default L
