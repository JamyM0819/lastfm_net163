// ==UserScript==
// @name         Last.fm 广告屏蔽（通用，非仅 Google）
// @namespace    lastfm.no-ads
// @version      5.2
// @description  屏蔽 last.fm 上的所有广告（Google + Freestar/AppNexus/PubMatic 等所有广告商），横幅/侧边栏/全屏插页/弹窗，并折叠广告移除后残留的空位
// @match        https://www.last.fm/*
// @match        https://last.fm/*
// @run-at       document-start
// @grant        none
// ==/UserScript==

(function () {
    'use strict';

    // 广告 / 测量 / 反作弊 服务域名（单一来源，CSS 与 DOM 判断共用）
    const AD_HOSTS = [
        // Google 系
        'doubleclick', 'googlesyndication', 'googleadservices', 'googletagservices',
        'adservice.google', 'securepubads', 'pagead2',
        // last.fm 实际广告栈：Freestar + 各 SSP（来自 last.fm/ads.txt 与拦截日志）
        'freestar', 'blockthrough', 'confiant-integrations', 'moatads',
        'adnxs', 'pubmatic', 'rubiconproject', 'openx', 'contextweb',
        'amazon-adsystem', 'adform', 'media.net', 'triplelift', 'gumgum',
        'teads', 'nativo', 'sharethrough', 'seedtag', 'smartadserver',
        'yieldmo', 'revcontent', 'inmobi', 'ogury', 'snigel',
        'indexexchange', 'sovrn', 'criteo', 'smaato',
        'insticator', 'minutemedia', 'vidazoo', 'aniview', 'primis',
        'cbsi.com', 'strangeclocks', 'casalemedia'
    ];

    // 折叠样式：把空壳广告位的宽高/内外边距全部清零
    const COLLAPSE_STYLE = 'height:0!important;min-height:0!important;max-height:0!important;margin:0!important;padding:0!important;border:0!important;overflow:hidden!important;';
    // 绝不折叠的顶层容器
    const PROTECTED = new Set(['HTML', 'BODY', 'MAIN', 'HEADER', 'FOOTER', 'NAV']);
    // last.fm 混淆广告位特征：iai（inline ad insertion）、广告尺寸标记（如 728x90）
    const IAI_RE = /(^|[-_])(iai)([-_]|$)/i;
    const AD_SIZE_RE = /\d{2,4}x\d{2,4}/i;
    const AD_SIZE_DATA_RE = /__\d{2,4}x\d{2,4}/i;

    const isAdUrl = (u) => {
        u = String(u || '').toLowerCase();
        return AD_HOSTS.some(h => u.includes(h));
    };

    const isAdContainer = (el) => {
        if (!el) return false;
        const id = String(el.id || '');
        const cls = String(el.className || '');
        const name = typeof el.getAttribute === 'function' ? String(el.getAttribute('name') || '') : '';
        const s = id + ' ' + cls + ' ' + name;
        // last.fm 已知广告位 + Google/通用特征
        if (/sidebar-ad-container|sticky-ad-container|full-bleed-ad-container|recs-feed-item--ad|related-ads|tonefuze|adSkin|freestar|adsbygoogle|vignette|google-auto-placed/i.test(s)) return true;
        // 独立的 ad / ads / advert / advertisement / sponsored 词（被分隔符包围，避免误伤 load/read/header）
        if (/(^|[-_\s])(ad|ads|advert|advertisement|sponsored)([-_\s]|$)/i.test(s)) return true;
        // camelCase 前缀：adSlot / adsContainer 等
        if (/\b(ad|ads)[A-Z]/.test(s)) return true;
        // Freestar 标记 / sticky footer / 关闭广告按钮
        if (el.hasAttribute && el.hasAttribute('data-fs-ancillary')) return true;
        if (/(sticky[-_]?footer|sticky[-_]?bar|adhesion|floor[-_]?ad)/i.test(s)) return true;
        const aria = typeof el.getAttribute === 'function'
            ? String(el.getAttribute('aria-label') || '') + ' ' + String(el.getAttribute('title') || '')
            : '';
        if (/close\s*ad|关闭广告/i.test(aria)) return true;
        // last.fm 混淆广告位：id/name 含 _iai（inline ad insertion）
        if (IAI_RE.test(id + ' ' + name)) return true;
        // 广告尺寸标记：id/name 里的 728x90；data-* 属性里的 __728x90
        if (AD_SIZE_RE.test(id) || AD_SIZE_RE.test(name)) return true;
        if (el.attributes) {
            for (const attr of el.attributes) {
                if (attr.name && attr.name.startsWith('data-') && AD_SIZE_DATA_RE.test(String(attr.value))) return true;
            }
        }
        return false;
    };

    const isAdNode = (el) => {
        if (!el || el.nodeType !== 1) return false;
        if (el.tagName === 'IFRAME') {
            if (isAdUrl(el.src) || isAdUrl(el.getAttribute('srcdoc'))) return true;
            const id = String(el.id || '').toLowerCase();
            if (/^(aswift|google_ads_iframe|google_vignette)/.test(id)) return true;
        }
        if (el.tagName === 'INS' && String(el.className || '').includes('adsbygoogle')) return true;
        return isAdContainer(el);
    };

    const containsAd = (node) => {
        if (!node) return false;
        if (isAdNode(node)) return true;
        if (typeof node.querySelectorAll === 'function') {
            const kids = node.querySelectorAll('iframe, ins, div, aside, section, button');
            for (const k of kids) if (isAdNode(k)) return true;
        }
        return false;
    };

    // 容器内是否还有可见内容（文字 / 有实际尺寸的元素）。
    // 用于判断“广告被移除后，这个容器是否只剩空壳，可以折叠”。
    const hasVisibleContent = (el) => {
        for (const node of el.childNodes) {
            if (node.nodeType === 3 && node.textContent.trim()) return true;
            if (node.nodeType === 1) {
                const tag = node.tagName;
                if (/^(SCRIPT|STYLE|LINK|META|NOSCRIPT|TEMPLATE)$/.test(tag)) continue;
                const cs = getComputedStyle(node);
                if (cs.display === 'none' || cs.visibility === 'hidden') continue;
                const r = node.getBoundingClientRect();
                if (r.width > 1 && r.height > 1) return true;
            }
        }
        return false;
    };

    // 从 startEl 向上逐层折叠“只剩空壳”的容器（最多 5 层，跳过受保护标签）。
    // 记录原始 inline style 到 data 属性，之后内容回来时可以恢复。
    const collapseEmptyAncestors = (startEl) => {
        let el = startEl;
        let depth = 0;
        while (el && depth < 5) {
            if (PROTECTED.has(el.tagName)) break;
            if (!el.hasAttribute('data-lfm-ad-collapsed') && !hasVisibleContent(el)) {
                el.setAttribute('data-lfm-ad-collapsed', el.getAttribute('style') || '');
                el.style.cssText += ';' + COLLAPSE_STYLE;
            }
            el = el.parentElement;
            depth++;
        }
    };

    // 1. 拦截 window.open 弹窗
    const _open = window.open;
    window.open = function (url) {
        if (isAdUrl(url)) return null;
        return _open.apply(this, arguments);
    };

    // 2. 拦截 DOM 插入（应对 shadow DOM 内的广告 iframe）
    const proto = Element.prototype;
    const guard = (orig) => function (...args) {
        for (const a of args) {
            if (a && a.nodeType === 1 && containsAd(a)) return null;
        }
        return orig.apply(this, args);
    };
    ['appendChild', 'insertBefore', 'replaceChild', 'append', 'prepend'].forEach(m => {
        if (typeof proto[m] === 'function') proto[m] = guard(proto[m]);
    });

    // 3. CSS 兜底：广告位容器 + 通用模式 + 各广告商 iframe（由 AD_HOSTS 生成）
    const iframeRules = AD_HOSTS.map(h => 'iframe[src*="' + h + '"]').join(', ');
    const css = `
        ${iframeRules},
        iframe[id^="aswift"], iframe[id^="google_ads_iframe"],
        .sidebar-ad-container, .sticky-ad-container, .full-bleed-ad-container,
        #adSkinLeft, #adSkinRight,
        .recs-feed-item--ad, .related-ads, .tonefuze,
        ins.adsbygoogle, .adsbygoogle,
        [id*="google_vignette"], [class*="google_vignette"],
        [id*="vignette"], [class*="vignette"],
        [class*="google-auto-placed"], [class*="google_auto_placed"],
        [id*="google_ads"], [class*="google_ads"], [id*="google-ads"], [class*="google-ads"],
        [class*="freestar"], [class*="advert"], [class*="sponsored"],
        [id*="-ad-"], [class*="-ad-"], [id*="-ads-"], [class*="-ads-"],
        [id*="_iai"], [name*="_iai"], [id*="-iai"], [name*="-iai"],
        [data-fs-ancillary], [id*="sticky_footer"], [name*="sticky_footer"],
        [id*="sticky-footer"], [name*="sticky-footer"],
        [aria-label*="Close Ad" i], [aria-label*="关闭广告"]
        { display: none !important; visibility: hidden !important; }

        .sidebar-ad-container, .sticky-ad-container, .full-bleed-ad-container,
        [class*="ad-container"]:empty, [class*="ads-container"]:empty,
        [class*="ad-slot"]:empty, [class*="adSlot"]:empty,
        [class*="leaderboard"]:empty, [class*="billboard"]:empty
        { height: 0 !important; min-height: 0 !important; max-height: 0 !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; }
    `;
    const style = document.createElement('style');
    style.textContent = css;
    (document.head || document.documentElement).appendChild(style);

    // 4. 清理 + 折叠 + 观察（动态/异步加载的广告）
    const sweep = () => {
        // 之前被折叠的容器如果重新出现了可见内容，恢复原始样式
        document.querySelectorAll('[data-lfm-ad-collapsed]').forEach(el => {
            if (hasVisibleContent(el)) {
                const orig = el.getAttribute('data-lfm-ad-collapsed') || '';
                el.removeAttribute('data-lfm-ad-collapsed');
                el.setAttribute('style', orig);
            }
        });

        document.querySelectorAll('iframe, ins, div, aside, section, button').forEach(el => {
            if (isAdNode(el)) {
                const parent = el.parentElement;
                el.remove();
                collapseEmptyAncestors(parent);
            }
        });
    };

    const mo = new MutationObserver(sweep);
    const boot = () => {
        mo.observe(document.documentElement, {
            childList: true, subtree: true,
            attributes: true, attributeFilter: ['src', 'srcdoc', 'class', 'id', 'style']
        });
        setInterval(sweep, 2000);
        sweep();
    };
    if (document.documentElement) boot();
    else document.addEventListener('DOMContentLoaded', boot);
    window.addEventListener('load', () => { sweep(); setTimeout(sweep, 1000); setTimeout(sweep, 3000); });
})();
