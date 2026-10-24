// @ts-nocheck
import { h } from 'vue'
import { createVuetify } from 'vuetify'
import QRCode from 'qrcode'
import ZwAvatar from './components/ZwAvatar.vue'

/* 智牧通 Web 公共组件库（由原型 zw.js 迁移；Vue 模板在运行时编译） */
const ZW: any = { pages: {} }
ZW.version = '1.0.0'


  /* ---------- 工具 ---------- */
  ZW.page = function (def) { ZW.pages[def.id] = def; return def; };
  ZW.n = function (v, d) {
    if (v === null || v === undefined || v === '') return '';
    var x = Number(v);
    if (isNaN(x)) return String(v);
    return x.toLocaleString('en-US', { minimumFractionDigits: d || 0, maximumFractionDigits: d || 0 });
  };
  function cssVar(name, fb) {
    var v = getComputedStyle(document.documentElement).getPropertyValue('--' + name).trim();
    return v || fb;
  }
  ZW.cssVar = cssVar;

  /* ---------- 统一示例数据（与 App 文档一致） ---------- */
  ZW.data = {
    today: '10月19日 周一',
    farms: [
      { name: '东山猪场', role: '所有者', sub: '所有者 · 育肥猪 · 870 头', status: 'current' },
      { name: '西岭鸡场', role: '场长', sub: '场长 · 白羽肉鸡 · 18,600 只' },
      { name: '南坡羊场', role: '员工', sub: '员工 · 负责 2 个栏舍' },
      { name: '北湾牛场', role: '所有者', sub: '所有者 · 审核通过前只能修改注册信息', status: 'pending' }
    ],
    people: {
      owner: { name: '王建国', role: '所有者', phone: '138****6620' },
      manager: { name: '刘芳', role: '场长', phone: '137****2185' },
      staff: { name: '李明', role: '员工', phone: '135****7731' },
      admin: { name: '周敏', role: '超级管理员' }
    }
  };

  /* ---------- 菜单 ---------- */
  ZW.menus = {
    farmer: [
      { key: 'WU-T01', label: '工作台', icon: 'space_dashboard', match: ['WU-T01'] },
      { key: 'WU-T03', label: '养殖日历', icon: 'calendar_month' },
      { key: 'WU-T04', label: '消息中心', icon: 'notifications', badge: 5 },
      { group: '生产管理', icon: 'fence', children: [
        { key: 'WU-L01', label: '存栏总览' },
        { key: 'WU-L02', label: '栏舍管理' },
        { key: 'WU-L03', label: '批次档案', match: ['WU-L03', 'WU-L04', 'WU-L05'] },
        { key: 'WU-B01', label: '繁育管理', match: ['WU-B01', 'WU-B02', 'WU-B03'] },
        { key: 'WU-G01', label: '饲喂管理', match: ['WU-G01', 'WU-G02'] },
        { key: 'WU-G03', label: '称重与生长' }
      ] },
      { group: '防疫健康', icon: 'health_and_safety', children: [
        { key: 'WU-I01', label: '日常巡检', match: ['WU-I01', 'WU-I02'] },
        { key: 'WU-I03', label: '异常与诊断', match: ['WU-I03', 'WU-I04'] },
        { key: 'WU-D01', label: '免疫计划' },
        { key: 'WU-D02', label: '用药与休药' },
        { key: 'WU-D03', label: '消毒管理' }
      ] },
      { group: '物资库存', icon: 'inventory_2', children: [
        { key: 'WU-M01', label: '饲料库存' },
        { key: 'WU-M02', label: '兽药与疫苗', match: ['WU-M02', 'WU-M03'] }
      ] },
      { group: '经营管理', icon: 'payments', children: [
        { key: 'WU-E01', label: '经营概览' },
        { key: 'WU-E02', label: '收支记账' },
        { key: 'WU-E03', label: '出栏单', match: ['WU-E03', 'WU-E04'] },
        { key: 'WU-E05', label: '批次核算' },
        { key: 'WU-E06', label: '市场行情' },
        { key: 'WU-E07', label: '经营报表' }
      ] },
      { group: '学习与助手', icon: 'auto_awesome', children: [
        { key: 'WU-K01', label: 'AI 养殖助手', match: ['WU-K01', 'WU-K02'] },
        { key: 'WU-K03', label: '养殖课堂', match: ['WU-K03', 'WU-K04'] }
      ] },
      { group: '设置', icon: 'settings', children: [
        { key: 'WU-F03', label: '养殖场信息', match: ['WU-F03', 'WU-F02'] },
        { key: 'WU-F04', label: '成员管理' },
        { key: 'WU-F01', label: '我的养殖场' },
        { key: 'WU-U01', label: '我的修改申请' },
        { key: 'WU-A04', label: '个人中心' }
      ] }
    ],
    staff: [
      { key: 'WU-T02', label: '工作台', icon: 'space_dashboard' },
      { key: 'WU-T03', label: '养殖日历', icon: 'calendar_month' },
      { key: 'WU-T04', label: '消息中心', icon: 'notifications', badge: 2 },
      { group: '我的栏舍', icon: 'fence', children: [
        { key: 'WU-L03', label: '负责栏舍与批次', match: ['WU-L03', 'WU-L04', 'WU-L05'] },
        { key: 'WU-B02', label: '待产与待出雏' },
        { key: 'WU-G01', label: '饲喂任务' },
        { key: 'WU-G03', label: '称重登记' }
      ] },
      { group: '防疫健康', icon: 'health_and_safety', children: [
        { key: 'WU-I01', label: '日常巡检', match: ['WU-I01', 'WU-I02'] },
        { key: 'WU-I03', label: '异常上报', match: ['WU-I03', 'WU-I04'] },
        { key: 'WU-D01', label: '免疫接种' },
        { key: 'WU-D02', label: '用药与休药' },
        { key: 'WU-D03', label: '消毒任务' }
      ] },
      { group: '学习与助手', icon: 'auto_awesome', children: [
        { key: 'WU-K01', label: 'AI 养殖助手', match: ['WU-K01', 'WU-K02'] },
        { key: 'WU-K03', label: '养殖课堂', match: ['WU-K03', 'WU-K04'] }
      ] },
      { group: '设置', icon: 'settings', children: [
        { key: 'WU-F01', label: '我的养殖场' },
        { key: 'WU-A04', label: '个人中心' }
      ] }
    ],
    admin: [
      { key: 'WA-S02', label: '平台概览', icon: 'dashboard' },
      { group: '审核中心', icon: 'fact_check', children: [
        { key: 'WA-R01', label: '养殖场审核', badge: 12 },
        { key: 'WA-R02', label: '资料变更审核', badge: 3 },
        { key: 'WA-R03', label: '修改申请审核', badge: 8 },
        { key: 'WA-R04', label: '账号找回', badge: 2 }
      ] },
      { group: '内容运营', icon: 'campaign', children: [
        { key: 'WA-C01', label: '通知发布' },
        { key: 'WA-C02', label: '市场行情发布' },
        { key: 'WA-C03', label: '养殖课堂管理' },
        { key: 'WA-C04', label: '知识库管理' },
        { key: 'WA-C05', label: 'AI 问答与更正' }
      ] },
      { group: '基础数据', icon: 'database', children: [
        { key: 'WA-D01', label: '品类品种与标准' },
        { key: 'WA-D02', label: '免疫程序模板' },
        { key: 'WA-D03', label: '兽药疫苗目录' },
        { key: 'WA-D04', label: '巡检项目模板' },
        { key: 'WA-D05', label: '其他字典' }
      ] },
      { group: '用户与系统', icon: 'manage_accounts', children: [
        { key: 'WA-S03', label: '用户账号' },
        { key: 'WA-S04', label: '养殖场管理' },
        { key: 'WA-S05', label: '管理员与权限' },
        { key: 'WA-S06', label: '操作日志' }
      ] }
    ]
  };

  /* ---------- Vuetify：W1 主题 ---------- */
  var zhHans = {
    badge: '徽章', open: '展开', close: '关闭', dismiss: '关闭',
    confirmEdit: { ok: '确定', cancel: '取消' },
    dataIterator: { noResultsText: '没有符合条件的结果', loadingText: '加载中…' },
    dataTable: { itemsPerPageText: '每页条数：', ariaLabel: { sortDescending: '：降序排列。', sortAscending: '：升序排列。', sortNone: '：未排序。', activateNone: '点击以移除排序。', activateDescending: '点击以降序排列。', activateAscending: '点击以升序排列。' }, sortBy: '排序方式' },
    dataFooter: { itemsPerPageText: '每页条数：', itemsPerPageAll: '全部', nextPage: '下一页', prevPage: '上一页', firstPage: '首页', lastPage: '尾页', pageText: '{0}-{1} 共 {2}' },
    dateRangeInput: { divider: '至' },
    datePicker: { itemsSelected: '已选 {0} 天', range: { title: '选择日期', header: '输入日期' }, title: '选择日期', header: '输入日期', input: { placeholder: '输入日期' } },
    noDataText: '暂无数据',
    carousel: { prev: '上一张', next: '下一张', ariaLabel: { delimiter: '第 {0} 张，共 {1} 张' } },
    calendar: { moreEvents: '还有 {0} 项', today: '今天' },
    input: { clear: '清除{0}', prependAction: '{0}前置操作', appendAction: '{0}后置操作', otp: '请输入第 {0} 位验证码' },
    fileInput: { counter: '{0} 个文件', counterSize: '{0} 个文件（共 {1}）' },
    fileUpload: { title: '把文件拖到这里', divider: '或', browse: '选择文件' },
    timePicker: { am: '上午', pm: '下午', title: '选择时间' },
    pagination: { ariaLabel: { root: '分页导航', next: '下一页', previous: '上一页', page: '转到第 {0} 页', currentPage: '当前第 {0} 页', first: '首页', last: '尾页' } },
    stepper: { next: '下一步', prev: '上一步' },
    rating: { ariaLabel: { item: '{0} / {1} 分' } },
    loading: '加载中…',
    infiniteScroll: { loadMore: '加载更多', empty: '没有更多了' }
  };

  var iconAliases = {
    collapse: 'expand_less', complete: 'check', cancel: 'cancel', close: 'close', delete: 'cancel', clear: 'cancel',
    success: 'check_circle', info: 'info', warning: 'warning', error: 'error', prev: 'chevron_left', next: 'chevron_right',
    checkboxOn: 'check_box:fill', checkboxOff: 'check_box_outline_blank', checkboxIndeterminate: 'indeterminate_check_box:fill',
    delimiter: 'circle:fill', sortAsc: 'arrow_upward', sortDesc: 'arrow_downward', expand: 'expand_more', menu: 'menu',
    subgroup: 'arrow_drop_down', dropdown: 'keyboard_arrow_down', radioOn: 'radio_button_checked', radioOff: 'radio_button_unchecked',
    edit: 'edit', ratingEmpty: 'star', ratingFull: 'star:fill', ratingHalf: 'star_half', loading: 'progress_activity',
    first: 'first_page', last: 'last_page', unfold: 'unfold_more', file: 'attach_file', plus: 'add', minus: 'remove',
    calendar: 'calendar_month', treeviewCollapse: 'arrow_drop_down', treeviewExpand: 'arrow_right', eyeDropper: 'colorize',
    upload: 'upload', color: 'palette'
  };
  var msoSet = {
    component: function (props) {
      var name = String(props.icon || '');
      var fill = false;
      if (name.slice(-5) === ':fill') { fill = true; name = name.slice(0, -5); }
      return h(props.tag || 'i', { class: ['zw-mso', fill ? 'zw-mso--fill' : null] }, name);
    }
  };

  ZW.createVuetify = function () {
    var c = function (n, fb) { return cssVar(n, fb); };
    var rgb = function (hex) {
      hex = hex.replace('#', '');
      return [0, 2, 4].map(function (i) { return parseInt(hex.slice(i, i + 2), 16); }).join(',');
    };
    return createVuetify({
      theme: {
        defaultTheme: 'zw',
        themes: {
          zw: {
            dark: false,
            colors: {
              background: c('bg-page', '#F0F2F5'),
              surface: c('bg-card', '#FFFFFF'),
              'surface-bright': c('bg-card', '#FFFFFF'),
              'surface-light': c('fill-1', '#F7F8FA'),
              'surface-variant': c('inverse-surface', '#1D2129'),
              'on-surface-variant': c('inverse-on-surface', '#F2F3F5'),
              'on-background': c('text-1', '#1D2129'),
              'on-surface': c('text-1', '#1D2129'),
              primary: c('primary', '#0B57D0'),
              'on-primary': c('on-primary', '#FFFFFF'),
              'primary-darken-1': c('primary-hover', '#0A4DB8'),
              secondary: c('text-2', '#4E5969'),
              'on-secondary': '#FFFFFF',
              tertiary: c('tertiary', '#6E4FA0'),
              error: c('error', '#B3261E'),
              'on-error': c('on-error', '#FFFFFF'),
              warning: c('warning', '#8A5100'),
              'on-warning': '#FFFFFF',
              success: c('success', '#146C2E'),
              'on-success': '#FFFFFF',
              info: c('primary', '#0B57D0'),
              'on-info': '#FFFFFF'
            },
            variables: {
              'border-color': rgb(c('text-1', '#1D2129')),
              'border-opacity': 0.12,
              'high-emphasis-opacity': 1,
              'medium-emphasis-opacity': 0.78,
              'disabled-opacity': 0.38,
              'hover-opacity': 0.04,
              'focus-opacity': 0.08,
              'activated-opacity': 0.08,
              'pressed-opacity': 0.12
            }
          }
        }
      },
      locale: { locale: 'zhHans', fallback: 'zhHans', messages: { zhHans: zhHans } },
      date: { locale: { zhHans: 'zh-CN' } },
      icons: { defaultSet: 'mso', aliases: iconAliases, sets: { mso: msoSet } },
      defaults: {
        global: { ripple: false },
        VBtn: { variant: 'flat' },
        VTextField: { variant: 'outlined', density: 'compact', hideDetails: 'auto', color: 'primary' },
        VTextarea: { variant: 'outlined', density: 'compact', hideDetails: 'auto', color: 'primary', rows: 3, autoGrow: false, persistentCounter: true },
        VSelect: { variant: 'outlined', density: 'compact', hideDetails: 'auto', color: 'primary', menuIcon: 'keyboard_arrow_down' },
        VAutocomplete: { variant: 'outlined', density: 'compact', hideDetails: 'auto', color: 'primary', menuIcon: 'keyboard_arrow_down' },
        VCombobox: { variant: 'outlined', density: 'compact', hideDetails: 'auto', color: 'primary' },
        VCheckbox: { density: 'compact', hideDetails: true, color: 'primary' },
        VCheckboxBtn: { density: 'compact', color: 'primary' },
        VRadioGroup: { density: 'compact', hideDetails: true, color: 'primary' },
        VRadio: { density: 'compact', color: 'primary' },
        VSwitch: { density: 'compact', hideDetails: true, color: 'primary', inset: true },
        VCard: { variant: 'flat' },
        VList: { density: 'compact' },
        VTabs: { color: 'primary', density: 'compact', sliderColor: 'primary' },
        VDataTable: { hover: true, itemsPerPage: 20 },
        VDataTableServer: { hover: true, itemsPerPage: 20 },
        VMenu: { offset: 4 },
        VTooltip: { location: 'top', maxWidth: 280 },
        VSnackbar: { location: 'top', timeout: -1 },
        VProgressLinear: { color: 'primary' },
        VSlider: { color: 'primary', hideDetails: true, density: 'compact' }
      }
    });
  };

  /* ---------- 组件 ---------- */
  var C = {};

  C.ZwIcon = {
    props: { name: String, size: { type: [Number, String], default: 20 }, fill: Boolean, color: String },
    template: '<span class="zw-mso" :class="{ \'zw-mso--fill\': fill }" :style="{ fontSize: size + \'px\', color: color ? \'var(--\' + color + \')\' : null }" aria-hidden="true">{{ name }}<slot/></span>'
  };

  C.ZwAvatar = ZwAvatar;

  C.ZwTag = {
    props: { type: { type: String, default: 'neutral' }, icon: String, dot: Boolean, size: { type: String, default: 'default' } },
    template: '<span class="zw-tag" :class="[\'zw-tag--\' + type, size === \'small\' ? \'zw-tag--small\' : null]"><span v-if="dot" class="zw-tag-dot"></span><zw-icon v-if="icon" :name="icon" :size="14"/><slot/></span>'
  };

  C.ZwNotice = {
    props: { type: { type: String, default: 'info' }, icon: String, title: String },
    computed: {
      ico: function () {
        if (this.icon) return this.icon;
        return { info: 'info', warning: 'warning', error: 'error', success: 'check_circle', ai: 'auto_awesome' }[this.type] || 'info';
      }
    },
    template: '<div class="zw-notice" :class="\'zw-notice--\' + type" role="status"><zw-icon :name="ico" :size="20" class="zw-notice-ico" :fill="type === \'error\' || type === \'warning\'"/><div class="zw-notice-body"><div v-if="title" class="zw-notice-title">{{ title }}</div><div class="zw-notice-text"><slot/></div></div><div v-if="$slots.action" class="zw-notice-action"><slot name="action"/></div></div>'
  };

  C.ZwPageHead = {
    props: { title: String, desc: String, back: [Boolean, String] },
    template: '<div class="zw-page-head"><div class="zw-page-head-main"><div class="zw-page-head-l">' +
      '<button v-if="back" class="zw-back" type="button" aria-label="返回"><zw-icon name="arrow_back" :size="20"/></button>' +
      '<div><div class="zw-page-title"><span>{{ title }}</span><slot name="tags"/></div><div v-if="desc || $slots.desc" class="zw-page-desc"><slot name="desc">{{ desc }}</slot></div></div></div>' +
      '<div v-if="$slots.actions" class="zw-page-actions"><slot name="actions"/></div></div><slot/></div>'
  };

  C.ZwCard = {
    props: { title: String, sub: String, pad: { type: Boolean, default: true }, flush: Boolean },
    template: '<section class="zw-card"><header v-if="title || $slots.title || $slots.extra" class="zw-card-head"><div class="zw-card-title"><slot name="title">{{ title }}</slot><span v-if="sub" class="zw-card-sub">{{ sub }}</span></div><div v-if="$slots.extra" class="zw-card-extra"><slot name="extra"/></div></header><div :class="pad && !flush ? \'zw-card-body\' : \'zw-card-body--flush\'"><slot/></div><footer v-if="$slots.foot" class="zw-card-foot"><slot name="foot"/></footer></section>'
  };

  C.ZwStat = {
    props: { label: String, value: [String, Number], unit: String, icon: String, foot: String, delta: String, deltaType: { type: String, default: 'flat' }, deltaTone: String, tone: String, info: String },
    template: '<div class="zw-stat" :class="tone ? \'zw-stat--\' + tone : null"><div class="zw-stat-label"><zw-icon v-if="icon" :name="icon" :size="18"/><span>{{ label }}</span><v-tooltip v-if="info" :text="info"><template #activator="{ props }"><zw-icon v-bind="props" name="help" :size="16" class="zw-stat-info"/></template></v-tooltip></div>' +
      '<div class="zw-stat-value"><span class="stat-l num">{{ value }}</span><span v-if="unit" class="zw-stat-unit">{{ unit }}</span><slot name="value"/></div>' +
      '<div v-if="foot || delta || $slots.foot" class="zw-stat-foot"><span v-if="delta" class="zw-delta" :class="deltaTone ? \'zw-delta--\' + deltaTone : \'zw-delta--\' + deltaType"><zw-icon v-if="deltaType !== \'flat\'" :name="deltaType === \'up\' ? \'arrow_upward\' : \'arrow_downward\'" :size="14"/>{{ delta }}</span><span v-if="foot">{{ foot }}</span><slot name="foot"/></div></div>'
  };

  C.ZwDesc = {
    props: { cols: { type: [Number, String], default: 3 }, labelWidth: { type: [Number, String], default: 0 }, bordered: Boolean },
    template: '<div class="zw-desc" :class="{ \'zw-desc--bordered\': bordered }" :style="{ gridTemplateColumns: \'repeat(\' + cols + \', minmax(0, 1fr))\', \'--zw-desc-lw\': labelWidth ? labelWidth + \'px\' : null }"><slot/></div>'
  };
  C.ZwDescItem = {
    props: { label: String, span: { type: [Number, String], default: 1 } },
    template: '<div class="zw-desc-item" :style="{ gridColumn: \'span \' + span }"><div class="zw-desc-label">{{ label }}</div><div class="zw-desc-value"><slot/></div></div>'
  };

  C.ZwFormItem = {
    props: { label: String, required: Boolean, hint: String, error: String, optional: Boolean, inline: Boolean },
    template: '<div class="zw-form-item" :class="{ \'zw-form-item--error\': error, \'zw-form-item--inline\': inline }"><label v-if="label" class="zw-form-label"><span v-if="required" class="zw-req">*</span>{{ label }}<span v-if="optional" class="zw-optional">（选填）</span><slot name="label-extra"/></label><div class="zw-form-control"><slot/><div v-if="error" class="zw-form-error">{{ error }}</div><div v-else-if="hint || $slots.hint" class="zw-form-hint"><slot name="hint">{{ hint }}</slot></div></div></div>'
  };

  C.ZwPager = {
    props: { total: { type: Number, default: 0 }, page: { type: Number, default: 1 }, size: { type: Number, default: 20 } },
    emits: ['update:page', 'update:size', 'change'],
    computed: {
      pages: function () { return Math.max(1, Math.ceil(this.total / this.size)); },
      list: function () {
        var n = this.pages, p = this.page, out = [];
        if (n <= 7) { for (var i = 1; i <= n; i++) out.push(i); return out; }
        out.push(1);
        if (p > 4) out.push('…');
        for (var j = Math.max(2, p - 1); j <= Math.min(n - 1, p + 1); j++) out.push(j);
        if (p < n - 3) out.push('…');
        out.push(n);
        return out;
      }
    },
    methods: {
      go: function (target) {
        if (target === '…' || target < 1 || target > this.pages || target === this.page) return;
        this.$emit('update:page', target);
        this.$emit('change', { page: target, size: this.size });
      },
      setSize: function (size) {
        this.$emit('update:size', size);
        this.$emit('update:page', 1);
        this.$emit('change', { page: 1, size: size });
      }
    },
    template: '<div class="zw-pager"><span class="zw-pager-total">共 {{ total.toLocaleString(\'en-US\') }} 条</span><button class="zw-pager-btn" :disabled="page <= 1" type="button" aria-label="上一页" @click="go(page - 1)"><zw-icon name="chevron_left" :size="18"/></button><template v-for="(x, i) in list" :key="i"><span v-if="x === \'…\'" class="zw-pager-ell">…</span><button v-else class="zw-pager-btn num" :class="{ on: x === page }" type="button" @click="go(x)">{{ x }}</button></template><button class="zw-pager-btn" :disabled="page >= pages" type="button" aria-label="下一页" @click="go(page + 1)"><zw-icon name="chevron_right" :size="18"/></button><v-menu location="top end"><template #activator="{ props: menuProps }"><button v-bind="menuProps" class="zw-pager-size" type="button">{{ size }} 条/页<zw-icon name="keyboard_arrow_down" :size="16"/></button></template><v-list density="compact"><v-list-item v-for="s in [20, 50, 100]" :key="s" @click="setSize(s)"><v-list-item-title>{{ s }} 条/页</v-list-item-title></v-list-item></v-list></v-menu></div>'
  };
  C.ZwEmpty = {
    props: { icon: { type: String, default: 'inbox' }, title: String, desc: String, tone: String },
    template: '<div class="zw-empty" :class="tone ? \'zw-empty--\' + tone : null"><div class="zw-empty-ico"><zw-icon :name="icon" :size="40"/></div><div v-if="title" class="zw-empty-title">{{ title }}</div><div v-if="desc" class="zw-empty-desc">{{ desc }}</div><div v-if="$slots.default" class="zw-empty-actions"><slot/></div></div>'
  };

  C.ZwAi = {
    props: { title: { type: String, default: 'AI 分析' }, disclaimer: { type: String, default: '由 AI 生成，仅供参考' }, sub: String },
    template: '<section class="zw-ai"><header class="zw-ai-head"><span class="zw-ai-badge"><zw-icon name="auto_awesome" :size="18" fill/></span><span class="zw-ai-title">{{ title }}</span><span v-if="sub" class="zw-ai-sub">{{ sub }}</span><span class="zw-ai-extra"><slot name="extra"/></span></header><div class="zw-ai-body"><slot/></div><footer class="zw-ai-foot"><zw-icon name="info" :size="14"/><span>{{ disclaimer }}</span><span class="zw-ai-actions"><slot name="actions"/></span></footer></section>'
  };

  /* 示意图（代替真实照片 / 视频画面） */
  var SCENES = {
    pen: '<rect class="s-wall" width="160" height="100"/><rect class="s-light" x="110" y="10" width="36" height="20" rx="2"/><rect class="s-floor" y="64" width="160" height="36"/><path class="s-rail" d="M0 56H160M0 44H160M24 36V64M64 36V64M104 36V64M144 36V64"/><ellipse class="s-body" cx="50" cy="80" rx="22" ry="9"/><ellipse class="s-body2" cx="104" cy="84" rx="19" ry="8"/>',
    pig: '<rect class="s-wall" width="160" height="100"/><rect class="s-floor" y="62" width="160" height="38"/><ellipse class="s-body" cx="78" cy="70" rx="44" ry="20"/><circle class="s-body2" cx="122" cy="62" r="13"/><rect class="s-body2" x="48" y="82" width="7" height="12" rx="2"/><rect class="s-body2" x="98" y="82" width="7" height="12" rx="2"/>',
    sick: '<rect class="s-wall" width="160" height="100"/><rect class="s-floor" y="58" width="160" height="42"/><ellipse class="s-body" cx="80" cy="78" rx="46" ry="14"/><circle class="s-spot" cx="66" cy="74" r="4"/><circle class="s-spot" cx="84" cy="80" r="3"/><circle class="s-spot" cx="98" cy="73" r="3.5"/><circle class="s-body2" cx="128" cy="76" r="10"/>',
    chick: '<rect class="s-wall" width="160" height="100"/><rect class="s-floor" y="60" width="160" height="40"/><circle class="s-chick" cx="40" cy="74" r="8"/><circle class="s-chick" cx="64" cy="80" r="8"/><circle class="s-chick" cx="92" cy="72" r="8"/><circle class="s-chick" cx="116" cy="82" r="8"/><circle class="s-chick" cx="76" cy="66" r="7"/>',
    feed: '<rect class="s-wall" width="160" height="100"/><rect class="s-floor" y="60" width="160" height="40"/><rect class="s-rail2" x="20" y="58" width="120" height="16" rx="3"/><rect class="s-feed" x="26" y="60" width="108" height="8" rx="2"/><ellipse class="s-body" cx="54" cy="86" rx="18" ry="7"/><ellipse class="s-body2" cx="106" cy="88" rx="16" ry="6"/>',
    cow: '<rect class="s-wall" width="160" height="100"/><rect class="s-floor" y="62" width="160" height="38"/><rect class="s-body2" x="38" y="44" width="72" height="32" rx="10"/><rect class="s-body2" x="104" y="36" width="24" height="22" rx="6"/><rect class="s-rail" x="44" y="72" width="6" height="18"/><rect class="s-rail" x="96" y="72" width="6" height="18"/>',
    doc: '<rect class="s-paper-bg" width="160" height="100"/><rect class="s-paper" x="40" y="8" width="80" height="86" rx="2"/><rect class="s-line" x="50" y="20" width="44" height="4"/><rect class="s-line2" x="50" y="32" width="60" height="3"/><rect class="s-line2" x="50" y="40" width="56" height="3"/><rect class="s-line2" x="50" y="48" width="60" height="3"/><rect class="s-line2" x="50" y="56" width="40" height="3"/><circle class="s-stamp" cx="98" cy="76" r="10"/>',
    drug: '<rect class="s-paper-bg" width="160" height="100"/><rect class="s-box" x="44" y="26" width="52" height="58" rx="3"/><rect class="s-paper" x="50" y="40" width="40" height="18" rx="1"/><rect class="s-bottle" x="104" y="40" width="20" height="44" rx="4"/><rect class="s-cap" x="106" y="32" width="16" height="10" rx="2"/>',
    course: '<rect class="s-wall2" width="160" height="100"/><rect class="s-floor" y="66" width="160" height="34"/><path class="s-rail" d="M0 58H160M30 40V66M80 40V66M130 40V66"/><ellipse class="s-body" cx="60" cy="82" rx="20" ry="8"/><ellipse class="s-body2" cx="110" cy="85" rx="16" ry="7"/>'
  };
  ZW.scene = function (kind) { return SCENES[kind] || SCENES.pen; };

  C.ZwThumb = {
    props: { kind: { type: String, default: 'pen' }, w: { type: [Number, String], default: 96 }, h: { type: [Number, String], default: 72 }, video: Boolean, duration: String, label: String, progress: Number, error: Boolean, removable: Boolean, radius: { type: [Number, String], default: 2 } },
    computed: { svg: function () { return ZW.scene(this.kind); } },
    template: '<div class="zw-thumb" :class="{ \'zw-thumb--error\': error }" :style="{ width: typeof w === \'number\' ? w + \'px\' : w, height: typeof h === \'number\' ? h + \'px\' : h, borderRadius: radius + \'px\' }"><svg class="zw-scene" viewBox="0 0 160 100" preserveAspectRatio="xMidYMid slice" v-html="svg"></svg>' +
      '<span v-if="video" class="zw-thumb-play"><zw-icon name="play_arrow" :size="20" fill/></span><span v-if="duration" class="zw-thumb-dur num">{{ duration }}</span><span v-if="label" class="zw-thumb-label">{{ label }}</span>' +
      '<span v-if="progress !== undefined && progress !== null" class="zw-thumb-mask"><v-progress-circular :model-value="progress" :size="32" :width="3" color="white"/><span class="num">{{ progress }}%</span></span>' +
      '<span v-if="error" class="zw-thumb-mask zw-thumb-mask--error"><zw-icon name="refresh" :size="20"/><span>上传失败</span><span>点击重试</span></span>' +
      '<button v-if="removable" class="zw-thumb-del" type="button" aria-label="删除"><zw-icon name="close" :size="14"/></button><slot/></div>'
  };

  C.ZwUpload = {
    props: { title: { type: String, default: '点击或拖拽文件到这里上传' }, hint: String, items: { type: Array, default: function () { return []; } }, max: Number, compact: Boolean, thumbW: { type: Number, default: 104 }, thumbH: { type: Number, default: 78 }, dragover: Boolean, showCount: Boolean },
    template: '<div class="zw-upload"><div class="zw-upload-grid"><zw-thumb v-for="(it, i) in items" :key="i" :kind="it.kind || \'pen\'" :w="thumbW" :h="thumbH" :video="it.video" :duration="it.duration" :progress="it.progress" :error="it.error" :label="it.label" removable><span v-if="it.tag" class="zw-thumb-tag" :class="\'zw-thumb-tag--\' + (it.tagType || \'primary\')">{{ it.tag }}</span></zw-thumb>' +
      '<div v-if="!max || items.length < max" class="zw-upload-drop" :class="{ \'zw-upload-drop--compact\': compact || items.length, \'zw-upload-drop--over\': dragover }" :style="items.length || compact ? { width: thumbW + \'px\', height: thumbH + \'px\' } : null"><zw-icon :name="items.length || compact ? \'add\' : \'cloud_upload\'" :size="items.length || compact ? 24 : 32"/><div v-if="!items.length && !compact" class="zw-upload-title">{{ title }}</div><div v-if="!items.length && !compact && hint" class="zw-upload-hint">{{ hint }}</div><div v-if="items.length || compact" class="zw-upload-add">上传<span v-if="showCount && max" class="num zw-upload-count">{{ items.length }} / {{ max }}</span></div></div></div>' +
      '<div v-if="(items.length || compact) && hint" class="zw-upload-hint zw-upload-hint--below">{{ hint }}</div></div>'
  };

  /* 示意地图（高德浅色自定义样式的示意） */
  C.ZwMap = {
    props: { height: { type: [Number, String], default: 360 }, markers: { type: Array, default: function () { return []; } }, pick: Boolean, controls: { type: Boolean, default: true }, regions: Boolean, legend: Array, scale: { type: Boolean, default: true },
      labels: { type: Array, default: function () { return [{ x: 60, y: 160, text: '太平地镇' }, { x: 420, y: 160, text: 'S206 省道' }, { x: 300, y: 372, text: '英金河' }]; } } },
    template: '<div class="zw-map" :style="{ height: typeof height === \'number\' ? height + \'px\' : height }"><svg class="zw-map-svg" viewBox="0 0 800 450" preserveAspectRatio="xMidYMid slice">' +
      '<rect class="m-land" width="800" height="450"/>' +
      '<g v-if="regions"><path class="m-region" d="M0 0H330L300 150L360 260L250 450H0Z"/><path class="m-region m-region--2" d="M330 0H600L560 180L360 260L300 150Z"/><path class="m-region m-region--3" d="M600 0H800V320L560 180Z"/><path class="m-region" d="M360 260L560 180L800 320V450H250Z"/></g>' +
      '<g v-else><rect class="m-block" x="40" y="40" width="150" height="90" rx="4"/><rect class="m-block" x="230" y="30" width="120" height="110" rx="4"/><rect class="m-block" x="420" y="60" width="170" height="80" rx="4"/><rect class="m-block" x="630" y="40" width="130" height="120" rx="4"/><rect class="m-block" x="60" y="210" width="140" height="120" rx="4"/><rect class="m-block" x="260" y="250" width="160" height="90" rx="4"/><rect class="m-block" x="640" y="240" width="120" height="150" rx="4"/><rect class="m-block" x="470" y="300" width="120" height="110" rx="4"/></g>' +
      '<path class="m-water" d="M-10 380C90 350 160 410 260 395S420 330 520 360S700 430 810 400V460H-10Z"/>' +
      '<path class="m-road m-road--main" d="M0 170H800M400 0V450"/><path class="m-road" d="M0 60C200 80 300 40 520 30S760 50 800 40M210 0L230 450M600 0L620 450M0 280C160 260 280 300 400 290S650 250 800 270"/>' +
      '<text v-for="(l, i) in labels" :key="i" class="m-label" :x="l.x" :y="l.y">{{ l.text }}</text></svg>' +
      '<div v-for="(m, i) in markers" :key="i" class="zw-map-marker" :class="\'zw-map-marker--\' + (m.type || \'primary\')" :style="{ left: m.x + \'%\', top: m.y + \'%\' }"><span v-if="m.count" class="zw-map-cluster num">{{ m.count }}</span><zw-icon v-else name="location_on" :size="m.size || 32" fill/><span v-if="m.label" class="zw-map-tip">{{ m.label }}</span></div>' +
      '<div v-if="pick" class="zw-map-pin"><zw-icon name="location_on" :size="40" fill/><span class="zw-map-pin-dot"></span></div>' +
      '<div v-if="controls" class="zw-map-ctrl"><button type="button" aria-label="放大"><zw-icon name="add" :size="18"/></button><button type="button" aria-label="缩小"><zw-icon name="remove" :size="18"/></button><button type="button" aria-label="定位到当前位置" class="zw-map-loc"><zw-icon name="my_location" :size="18"/></button></div>' +
      '<div v-if="legend" class="zw-map-legend"><span v-for="(l, i) in legend" :key="i"><i :class="\'zw-dot zw-dot--\' + l.type"></i>{{ l.label }}</span></div>' +
      '<div v-if="scale" class="zw-map-scale"><span></span>500 米</div><div class="zw-map-brand">高德地图（示意）</div><slot/></div>'
  };

  /* ---------- 图表（SVG） ---------- */
  function niceTicks(min, max, count) {
    if (min === max) { max = min + 1; }
    var span = max - min, step = Math.pow(10, Math.floor(Math.log10(span / count)));
    var err = (count * step) / span;
    if (err <= 0.15) step *= 10; else if (err <= 0.35) step *= 5; else if (err <= 0.75) step *= 2;
    var lo = Math.floor(min / step) * step, hi = Math.ceil(max / step) * step, out = [];
    for (var v = lo; v <= hi + step / 2; v += step) out.push(Math.round(v * 1e6) / 1e6);
    return out;
  }
  ZW.niceTicks = niceTicks;
  var measure = {
    data: function () { return { w: 0 }; },
    mounted: function () {
      var self = this;
      var upd = function () { self.w = self.$el.clientWidth || self.width || 600; };
      upd();
      if (window.ResizeObserver) { this._ro = new ResizeObserver(upd); this._ro.observe(this.$el); }
    },
    beforeUnmount: function () { if (this._ro) this._ro.disconnect(); }
  };

  C.ZwLineChart = {
    mixins: [measure],
    props: { labels: Array, series: Array, height: { type: Number, default: 240 }, unit: String, yMin: Number, yMax: Number, ticks: { type: Number, default: 5 }, legend: { type: Boolean, default: true }, dots: { type: Boolean, default: true }, width: Number, xEvery: { type: Number, default: 1 }, highlight: Number },
    computed: {
      g: function () {
        var W = this.w || this.width || 600, H = this.height, L = 48, R = 16, T = 12, B = 28;
        var all = [];
        (this.series || []).forEach(function (s) { s.data.forEach(function (v) { if (v !== null && v !== undefined) all.push(v); }); });
        var mn = this.yMin !== undefined ? this.yMin : Math.min.apply(null, all), mx = this.yMax !== undefined ? this.yMax : Math.max.apply(null, all);
        var tk = niceTicks(mn, mx, this.ticks), lo = tk[0], hi = tk[tk.length - 1];
        var n = (this.labels || []).length, pw = W - L - R, ph = H - T - B;
        var x = function (i) { return L + (n <= 1 ? pw / 2 : (pw * i) / (n - 1)); };
        var y = function (v) { return T + ph - ((v - lo) / (hi - lo || 1)) * ph; };
        var ser = (this.series || []).map(function (s) {
          var d = '', pts = [];
          s.data.forEach(function (v, i) { if (v === null || v === undefined) return; d += (d ? 'L' : 'M') + x(i).toFixed(1) + ' ' + y(v).toFixed(1); pts.push({ x: x(i), y: y(v), v: v, i: i }); });
          var area = s.area && pts.length ? d + 'L' + pts[pts.length - 1].x.toFixed(1) + ' ' + (T + ph) + 'L' + pts[0].x.toFixed(1) + ' ' + (T + ph) + 'Z' : '';
          return { name: s.name, d: d, area: area, pts: pts, color: s.color || 'chart-1', dashed: s.dashed, dots: s.dots !== undefined ? s.dots : true };
        });
        var dec = 0; tk.forEach(function (v) { var p = String(v).split('.')[1]; if (p) dec = Math.max(dec, p.length); });
        var fmt = function (v) { return v.toLocaleString('en-US', { minimumFractionDigits: dec, maximumFractionDigits: dec }); };
        return { W: W, H: H, L: L, R: R, T: T, B: B, ticks: tk.map(function (v) { return { v: v, y: y(v), t: fmt(v) }; }), x: x, y: y, ser: ser, n: n, ph: ph };
      }
    },
    template: '<div class="zw-chart"><div v-if="legend && series && series.length > 1" class="zw-chart-legend"><span v-for="s in series" :key="s.name"><i :class="s.dashed ? \'zw-lg-dash\' : \'zw-lg-line\'" :style="{ \'--c\': \'var(--\' + (s.color || \'chart-1\') + \')\' }"></i>{{ s.name }}</span><span v-if="unit" class="zw-chart-unit">单位：{{ unit }}</span></div><div v-else-if="unit" class="zw-chart-legend"><span class="zw-chart-unit">单位：{{ unit }}</span></div>' +
      '<svg :width="g.W" :height="g.H" :viewBox="\'0 0 \' + g.W + \' \' + g.H" class="zw-chart-svg"><g class="zw-chart-grid"><line v-for="t in g.ticks" :key="\'g\' + t.v" :x1="g.L" :x2="g.W - g.R" :y1="t.y" :y2="t.y"/></g>' +
      '<g class="zw-chart-axis"><text v-for="t in g.ticks" :key="\'t\' + t.v" :x="g.L - 8" :y="t.y + 4" text-anchor="end">{{ t.t }}</text><template v-for="(lb, i) in labels" :key="\'x\' + i"><text v-if="i % xEvery === 0 || i === labels.length - 1" :x="g.x(i)" :y="g.H - 8" text-anchor="middle">{{ lb }}</text></template></g>' +
      '<line v-if="highlight !== undefined" class="zw-chart-hl" :x1="g.x(highlight)" :x2="g.x(highlight)" :y1="g.T" :y2="g.T + g.ph"/>' +
      '<g v-for="s in g.ser" :key="s.name"><path v-if="s.area" :d="s.area" class="zw-chart-area" :style="{ fill: \'var(--\' + s.color + \')\' }"/><path :d="s.d" class="zw-chart-line" :class="{ dashed: s.dashed }" :style="{ stroke: \'var(--\' + s.color + \')\' }"/><template v-if="dots && s.dots && !s.dashed"><circle v-for="p in s.pts" :key="p.i" :cx="p.x" :cy="p.y" r="3" class="zw-chart-dot" :style="{ stroke: \'var(--\' + s.color + \')\' }"/></template></g><slot :g="g"/></svg></div>'
  };

  C.ZwBarChart = {
    mixins: [measure],
    props: { labels: Array, series: Array, height: { type: Number, default: 240 }, unit: String, stacked: Boolean, ticks: { type: Number, default: 5 }, legend: { type: Boolean, default: true }, yMax: Number, values: Boolean, width: Number },
    computed: {
      g: function () {
        var W = this.w || this.width || 600, H = this.height, L = 48, R = 12, T = 16, B = 28;
        var ss = this.series || [], n = (this.labels || []).length, self = this;
        var mx = 0;
        for (var i = 0; i < n; i++) {
          if (self.stacked) { var s = 0; ss.forEach(function (x) { s += x.data[i] || 0; }); mx = Math.max(mx, s); }
          else ss.forEach(function (x) { mx = Math.max(mx, x.data[i] || 0); });
        }
        if (this.yMax !== undefined) mx = this.yMax;
        var tk = niceTicks(0, mx, this.ticks), hi = tk[tk.length - 1];
        var pw = W - L - R, ph = H - T - B, band = pw / Math.max(n, 1);
        var gw = Math.min(band * 0.6, self.stacked ? 40 : 18 * ss.length + 8), bw = self.stacked ? gw : (gw - 2 * (ss.length - 1)) / Math.max(ss.length, 1);
        var y = function (v) { return T + ph - (v / (hi || 1)) * ph; };
        var bars = [];
        for (var k = 0; k < n; k++) {
          var cx = L + band * k + band / 2, acc = 0;
          ss.forEach(function (x, j) {
            var v = x.data[k] || 0;
            if (self.stacked) { bars.push({ x: cx - gw / 2, y: y(acc + v), w: gw, h: y(acc) - y(acc + v), c: x.color || 'chart-' + (j + 1), v: v, k: k }); acc += v; }
            else bars.push({ x: cx - gw / 2 + j * (bw + 2), y: y(v), w: bw, h: T + ph - y(v), c: x.color || 'chart-' + (j + 1), v: v, k: k });
          });
        }
        return { W: W, H: H, L: L, R: R, T: T, ph: ph, band: band, ticks: tk.map(function (v) { return { v: v, y: y(v) }; }), bars: bars };
      }
    },
    template: '<div class="zw-chart"><div v-if="legend && series && series.length > 1" class="zw-chart-legend"><span v-for="(s, j) in series" :key="s.name"><i class="zw-lg-box" :style="{ \'--c\': \'var(--\' + (s.color || \'chart-\' + (j + 1)) + \')\' }"></i>{{ s.name }}</span><span v-if="unit" class="zw-chart-unit">单位：{{ unit }}</span></div><div v-else-if="unit" class="zw-chart-legend"><span class="zw-chart-unit">单位：{{ unit }}</span></div>' +
      '<svg :width="g.W" :height="g.H" :viewBox="\'0 0 \' + g.W + \' \' + g.H" class="zw-chart-svg"><g class="zw-chart-grid"><line v-for="t in g.ticks" :key="\'g\' + t.v" :x1="g.L" :x2="g.W - g.R" :y1="t.y" :y2="t.y"/></g><g class="zw-chart-axis"><text v-for="t in g.ticks" :key="\'t\' + t.v" :x="g.L - 8" :y="t.y + 4" text-anchor="end">{{ t.v.toLocaleString(\'en-US\') }}</text><text v-for="(lb, i) in labels" :key="\'x\' + i" :x="g.L + g.band * i + g.band / 2" :y="g.H - 8" text-anchor="middle">{{ lb }}</text></g>' +
      '<rect v-for="(b, i) in g.bars" :key="i" :x="b.x" :y="b.y" :width="Math.max(b.w, 1)" :height="Math.max(b.h, 0)" rx="2" :style="{ fill: \'var(--\' + b.c + \')\' }"/><template v-if="values"><text v-for="(b, i) in g.bars" :key="\'v\' + i" class="zw-chart-val" :x="b.x + b.w / 2" :y="b.y - 4" text-anchor="middle">{{ b.v.toLocaleString(\'en-US\') }}</text></template></svg></div>'
  };

  C.ZwDonut = {
    props: { items: Array, size: { type: Number, default: 160 }, thickness: { type: Number, default: 22 }, title: String, value: String, legend: { type: Boolean, default: true } },
    computed: {
      arcs: function () {
        var tot = 0; (this.items || []).forEach(function (x) { tot += x.value; });
        var r = (this.size - this.thickness) / 2, c = 2 * Math.PI * r, off = 0;
        var palette = ['chart-1', 'chart-2', 'chart-3', 'chart-ref', 'fill-3'];
        return (this.items || []).map(function (x, i) {
          var len = tot ? (x.value / tot) * c : 0, a = { r: r, dash: Math.max(len - 2, 0) + ' ' + (c - Math.max(len - 2, 0)), off: -off, color: x.color || palette[i % palette.length], label: x.label, pct: tot ? ((x.value / tot) * 100).toFixed(1) + '%' : '0%', value: x.value, text: x.text };
          off += len; return a;
        });
      }
    },
    template: '<div class="zw-donut"><div class="zw-donut-fig" :style="{ width: size + \'px\', height: size + \'px\' }"><svg :width="size" :height="size" :viewBox="\'0 0 \' + size + \' \' + size"><circle :cx="size / 2" :cy="size / 2" :r="(size - thickness) / 2" fill="none" class="zw-donut-track" :stroke-width="thickness"/><circle v-for="(a, i) in arcs" :key="i" :cx="size / 2" :cy="size / 2" :r="a.r" fill="none" :stroke-width="thickness" :stroke-dasharray="a.dash" :stroke-dashoffset="a.off" :style="{ stroke: \'var(--\' + a.color + \')\' }" :transform="\'rotate(-90 \' + size / 2 + \' \' + size / 2 + \')\'"/></svg><div class="zw-donut-center"><div class="zw-donut-value num">{{ value }}</div><div class="zw-donut-title">{{ title }}</div></div></div>' +
      '<ul v-if="legend" class="zw-donut-legend"><li v-for="(a, i) in arcs" :key="i"><i :style="{ background: \'var(--\' + a.color + \')\' }"></i><span class="zw-donut-l">{{ a.label }}</span><span class="num zw-donut-v">{{ a.text || a.pct }}</span></li></ul></div>'
  };

  C.ZwSteps = {
    props: { items: Array, small: Boolean },
    template: '<ol class="zw-steps" :class="{ \'zw-steps--small\': small }"><li v-for="(s, i) in items" :key="i" class="zw-step" :class="\'zw-step--\' + (s.status || \'wait\')"><span class="zw-step-dot"><zw-icon v-if="s.icon" :name="s.icon" :size="16"/><zw-icon v-else-if="s.status === \'done\'" name="check" :size="16"/><zw-icon v-else-if="s.status === \'error\'" name="close" :size="16"/><span v-else class="num">{{ i + 1 }}</span></span><div class="zw-step-text"><div class="zw-step-title">{{ s.title }}</div><div v-if="s.desc" class="zw-step-desc">{{ s.desc }}</div></div><span v-if="i < items.length - 1" class="zw-step-line"></span></li></ol>'
  };

  C.ZwTimeline = {
    props: { items: Array },
    template: '<ul class="zw-tl"><li v-for="(it, i) in items" :key="i" class="zw-tl-item"><span class="zw-tl-dot" :class="\'zw-tl-dot--\' + (it.tone || \'neutral\')"><zw-icon v-if="it.icon" :name="it.icon" :size="14"/></span><div class="zw-tl-main"><div class="zw-tl-head"><span class="zw-tl-title">{{ it.title }}</span><zw-tag v-if="it.tag" :type="it.tagType || \'neutral\'" size="small">{{ it.tag }}</zw-tag><span class="zw-tl-time num">{{ it.time }}</span></div><div v-if="it.desc" class="zw-tl-desc">{{ it.desc }}</div></div></li></ul>'
  };

  C.ZwVideo = {
    props: { kind: { type: String, default: 'course' }, time: { type: String, default: '1:24' }, duration: { type: String, default: '3:42' }, progress: { type: Number, default: 0.38 }, buffered: { type: Number, default: 0.64 }, speed: { type: String, default: '1.0x' }, quality: { type: String, default: '720P' }, playing: { type: Boolean, default: true }, menu: String, big: Boolean, caption: String, simple: Boolean, cover: Boolean },
    computed: { svg: function () { return ZW.scene(this.kind); } },
    template: '<div class="zw-video" :class="{ \'zw-video--cover\': cover }"><svg class="zw-scene zw-video-scene" viewBox="0 0 160 90" preserveAspectRatio="xMidYMid slice" v-html="svg"></svg><span v-if="!playing || big || cover" class="zw-video-big"><zw-icon :name="playing && !cover ? \'pause\' : \'play_arrow\'" :size="36" fill/></span><span v-if="cover" class="zw-thumb-dur num zw-video-covdur">{{ duration }}</span><div v-if="caption" class="zw-video-caption">{{ caption }}</div>' +
      '<div v-if="!cover" class="zw-video-ctrl"><div class="zw-video-bar"><i class="buf" :style="{ width: buffered * 100 + \'%\' }"></i><i class="fill" :style="{ width: progress * 100 + \'%\' }"></i><b :style="{ left: progress * 100 + \'%\' }"></b></div><div class="zw-video-row"><zw-icon :name="playing ? \'pause\' : \'play_arrow\'" :size="24" fill/><zw-icon name="replay_10" :size="22"/><zw-icon name="forward_10" :size="22"/><zw-icon v-if="!simple" name="skip_next" :size="24" fill/><span class="num zw-video-time">{{ time }} / {{ duration }}</span><zw-icon name="volume_up" :size="22"/><span class="zw-video-grow"></span>' +
      '<span class="zw-video-txt" :class="{ on: menu === \'speed\' }">{{ speed }}<div v-if="menu === \'speed\'" class="zw-video-menu"><div v-for="s in [\'2.0x\', \'1.5x\', \'1.25x\', \'1.0x\', \'0.75x\']" :key="s" :class="{ on: s === speed }">{{ s }}</div></div></span>' +
      '<span v-if="!simple" class="zw-video-txt" :class="{ on: menu === \'quality\' }">{{ quality }}<div v-if="menu === \'quality\'" class="zw-video-menu"><div v-for="q in [\'720P 高清\', \'480P 流畅\']" :key="q" :class="{ on: q.indexOf(quality) === 0 }">{{ q }}</div></div></span>' +
      '<zw-icon v-if="!simple" name="picture_in_picture_alt" :size="22"/><zw-icon name="fullscreen" :size="24"/></div></div></div>'
  };

  C.ZwDrawer = {
    props: { modelValue: Boolean, title: String, sub: String, width: { type: [Number, String], default: 560 } },
    emits: ['update:modelValue'],
    template: '<v-navigation-drawer :model-value="modelValue" @update:model-value="$emit(\'update:modelValue\', $event)" location="right" temporary :width="Number(width)" class="zw-drawer" :scrim="true"><div class="zw-drawer-wrap"><header class="zw-drawer-head"><div><div class="zw-drawer-title">{{ title }}</div><div v-if="sub" class="zw-drawer-sub">{{ sub }}</div></div><button class="zw-icon-btn" type="button" aria-label="关闭" @click="$emit(\'update:modelValue\', false)"><zw-icon name="close" :size="20"/></button></header><div class="zw-drawer-body"><slot/></div><footer v-if="$slots.foot" class="zw-drawer-foot"><slot name="foot"/></footer></div></v-navigation-drawer>'
  };

  C.ZwDialog = {
    props: { modelValue: Boolean, title: String, width: { type: [Number, String], default: 420 }, icon: String, tone: { type: String, default: 'warning' }, noClose: Boolean, persistent: Boolean },
    emits: ['update:modelValue'],
    template: '<v-dialog :model-value="modelValue" @update:model-value="$emit(\'update:modelValue\', $event)" :max-width="Number(width)" :width="Number(width)" :persistent="persistent || noClose"><div class="zw-dialog"><header class="zw-dialog-head"><zw-icon v-if="icon" :name="icon" :size="22" :class="\'zw-dialog-ico zw-dialog-ico--\' + tone" fill/><div class="zw-dialog-title">{{ title }}</div><button v-if="!noClose" class="zw-icon-btn" type="button" aria-label="关闭" @click="$emit(\'update:modelValue\', false)"><zw-icon name="close" :size="20"/></button></header><div class="zw-dialog-body" :class="{ \'zw-dialog-body--ico\': icon }"><slot/></div><footer v-if="$slots.actions" class="zw-dialog-actions"><slot name="actions"/></footer></div></v-dialog>'
  };

  C.ZwSnack = {
    props: { text: String, action: String, modelValue: { type: Boolean, default: true }, type: String },
    template: '<v-snackbar :model-value="modelValue" class="zw-snack" :timeout="-1" location="top" content-class="zw-snack-content"><span class="zw-snack-inner"><zw-icon v-if="type" :name="type === \'error\' ? \'error\' : \'check_circle\'" :size="18" fill :class="\'zw-snack-ico--\' + type"/>{{ text }}</span><template v-if="action" #actions><button class="zw-snack-action" type="button">{{ action }}</button></template></v-snackbar>'
  };

  C.ZwSeg = {
    props: { items: Array, modelValue: [String, Number], size: String },
    emits: ['update:modelValue'],
    template: '<div class="zw-seg" :class="{ \'zw-seg--small\': size === \'small\' }" role="radiogroup"><button v-for="it in items" :key="it.value" type="button" class="zw-seg-item" :class="{ on: it.value === modelValue }" @click="$emit(\'update:modelValue\', it.value)"><zw-icon v-if="it.icon" :name="it.icon" :size="16"/>{{ it.label }}<span v-if="it.count !== undefined" class="zw-seg-count num">{{ typeof it.count === \'number\' ? it.count.toLocaleString(\'en-US\') : it.count }}</span></button></div>'
  };

  C.ZwCompare = {
    props: { rows: Array, beforeTitle: { type: String, default: '修改前' }, afterTitle: { type: String, default: '修改后' }, tagType: { type: String, default: 'primary' } },
    template: '<table class="zw-compare"><thead><tr><th style="width:22%">字段</th><th style="width:34%">{{ beforeTitle }}</th><th>{{ afterTitle }}</th></tr></thead><tbody><tr v-for="(r, i) in rows" :key="i" :class="{ changed: r.before !== r.after }"><td class="zw-compare-k">{{ r.label }}</td><td class="zw-compare-b"><span :class="{ del: r.before !== r.after }">{{ r.before }}</span></td><td class="zw-compare-a"><span>{{ r.after }}</span><zw-tag v-if="r.before !== r.after" :type="tagType" size="small">已修改</zw-tag></td></tr></tbody></table>'
  };

  C.ZwQr = {
    props: { size: { type: Number, default: 120 }, seed: { type: String, default: 'ZM2610-031' } },
    data: function () { return { qrUrl: '' }; },
    computed: {
      cells: function () {
        var n = 25, out = [], s = 0;
        for (var i = 0; i < this.seed.length; i++) s = (s * 31 + this.seed.charCodeAt(i)) >>> 0;
        var rnd = function () { s = (s * 1103515245 + 12345) >>> 0; return (s >>> 16) & 1; };
        var finder = function (r, c) {
          var f = [[0, 0], [0, n - 7], [n - 7, 0]];
          for (var k = 0; k < 3; k++) {
            var rr = r - f[k][0], cc = c - f[k][1];
            if (rr >= -1 && rr <= 7 && cc >= -1 && cc <= 7) {
              if (rr < 0 || rr > 6 || cc < 0 || cc > 6) return 0;
              if (rr === 0 || rr === 6 || cc === 0 || cc === 6) return 1;
              if (rr >= 2 && rr <= 4 && cc >= 2 && cc <= 4) return 1;
              return 0;
            }
          }
          return -1;
        };
        for (var r = 0; r < n; r++) for (var c = 0; c < n; c++) { var f = finder(r, c); if (f === 1 || (f === -1 && rnd())) out.push([c, r]); }
        return out;
      }
    },
    mounted: function () { this.renderQr(); },
    watch: {
      seed: function () { this.renderQr(); },
      size: function () { this.renderQr(); }
    },
    methods: {
      renderQr: function () {
        var self = this;
        var text = String(this.seed || ' ').trim() || ' ';
        QRCode.toDataURL(text, { margin: 1, width: Math.max(64, Number(this.size) || 120), errorCorrectionLevel: 'M' })
          .then(function (url) { self.qrUrl = url; })
          .catch(function () { self.qrUrl = ''; });
      }
    },
    template: '<span class="zw-qr" :style="{ width: size + \'px\', height: size + \'px\' }"><img v-if="qrUrl" :src="qrUrl" :width="size" :height="size" alt="批次二维码" style="display:block;width:100%;height:100%;border-radius:2px" /><svg v-else :width="size" :height="size" viewBox="-2 -2 29 29" role="img" aria-label="批次二维码"><rect x="-2" y="-2" width="29" height="29" fill="#FFFFFF"/><rect v-for="(p, i) in cells" :key="i" :x="p[0]" :y="p[1]" width="1.02" height="1.02" fill="#1D2129"/></svg></span>'
  };
  /* ---------- 外壳：侧边菜单 + 顶栏 + 内容区 ---------- */
  ZW.components = C;
  ZW.install = function (app) {
    Object.keys(C).forEach(function (k) {
      var kebab = k.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase();
      app.component(kebab, C[k]);
    });
  };

  
export { ZW }
export const zwComponents = C

export function installZw(app: any): void {
  ZW.install(app)
}

export function createZwVuetify() {
  return ZW.createVuetify()
}
