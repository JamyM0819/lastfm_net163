// ==UserScript==
// @name         Last.fm 广告屏蔽（通用，非仅 Google）
// @namespace    lastfm.no-ads
// @version      4.0
// @description  屏蔽 last.fm 上的所有广告（Google + Freestar/AppNexus/PubMatic 等所有广告商），横幅/侧边栏/全屏插页/弹窗
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

    const isAdUrl = (u) => {
        u = String(u || '').toLowerCase();
        return AD_HOSTS.some(h => u.includes(h));
    };

    const isAdContainer = (el) => {
        if (!el) return false;
        const id = String(el.id || '');
        const cls = String(el.className || '');
        const s = id + ' ' + cls;
        // last.fm 已知广告位 + Google/通用特征
        if (/sidebar-ad-container|sticky-ad-container|full-bleed-ad-container|recs-feed-item--ad|related-ads|tonefuze|adSkin|freestar|adsbygoogle|vignette|google-auto-placed/i.test(s)) return true;
        // 独立的 ad / ads / advert / advertisement / sponsored 词（被分隔符包围，避免误伤 load/read/header）
        if (/(^|[-_\s])(ad|ads|advert|advertisement|sponsored)([-_\s]|$)/i.test(s)) return true;
        // camelCase 前缀：adSlot / adsContainer 等
        if (/\b(ad|ads)[A-Z]/.test(s)) return true;
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
            const kids = node.querySelectorAll('iframe, ins, div, aside, section');
            for (const k of kids) if (isAdNode(k)) return true;
        }
        return false;
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
        [id*="-ad-"], [class*="-ad-"], [id*="-ads-"], [class*="-ads-"]
        { display: none !important; visibility: hidden !important; }
    `;
    const style = document.createElement('style');
    style.textContent = css;
    (document.head || document.documentElement).appendChild(style);

    // 4. 清理 + 观察（动态/异步加载的广告）
    const sweep = () => {
        document.querySelectorAll('iframe, ins, div, aside, section').forEach(el => {
            if (isAdNode(el)) el.remove();
        });
    };

    const mo = new MutationObserver(sweep);
    const boot = () => {
        mo.observe(document.documentElement, {
            childList: true, subtree: true,
            attributes: true, attributeFilter: ['src', 'srcdoc', 'class', 'id']
        });
        setInterval(sweep, 2000);
        sweep();
    };
    if (document.documentElement) boot();
    else document.addEventListener('DOMContentLoaded', boot);
    window.addEventListener('load', () => { sweep(); setTimeout(sweep, 1000); setTimeout(sweep, 3000); });
})();
