/**
 * Leaflet 只在打开“选择养殖场位置”对话框时才动态加载。
 * 这里集中引入 JS 与样式，Vite 会把 Leaflet 及其 CSS 拆成独立异步 chunk，
 * 不影响用户端首屏体积。
 */
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

export default L
