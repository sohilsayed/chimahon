(function () {
  'use strict';

  const DEBUG = false;
  const DEBUG_VERBOSE_NODES = false;
  const SCOPED_STYLE_CACHE_LIMIT = 24;
  const scopedStyleCache = new Map();

  const HAS_NATIVE_SCOPE = (() => {
    try {
      if (!window.CSS || !CSS.supports) return false;
      return CSS.supports('selector(:scope)') && (() => {
        try {
          const sheet = new CSSStyleSheet();
          sheet.replaceSync('@scope (.test) { .a { color: red; } }');
          return sheet.cssRules.length > 0;
        } catch (e) {
          return false;
        }
      })();
    } catch (e) {
      return false;
    }
  })();

  const HAS_CONSTRUCTED_SHEETS = (() => {
    try {
      const sheet = new CSSStyleSheet();
      sheet.replaceSync('body { color: red; }');
      return sheet.cssRules.length > 0;
    } catch (e) {
      return false;
    }
  })();

  function toDebugText(value) {
    if (typeof value === 'string') return value;
    if (value === null) return 'null';
    if (typeof value === 'undefined') return 'undefined';
    if (typeof value === 'number' || typeof value === 'boolean') return String(value);
    try {
      return JSON.stringify(value);
    } catch (e) {
      return String(value);
    }
  }

  function debugLog(...args) {
    if (!DEBUG) return;
    console.log('[DictionaryRenderJS][debug]', args.map(toDebugText).join(' '));
  }

  function debugWarn(...args) {
    if (!DEBUG) return;
    console.warn('[DictionaryRenderJS][debug]', args.map(toDebugText).join(' '));
  }

  function decodeBase64Utf8(base64) {
    const binary = atob(base64);
    const len = binary.length;
    const bytes = new Uint8Array(len);
    for (let i = 0; i < len; i++) bytes[i] = binary.charCodeAt(i);
    return new TextDecoder('utf-8').decode(bytes);
  }

  function sanitizeDictionaryStyles(cssText) {
    if (!cssText || typeof cssText !== 'string') return '';
    return cssText;
  }

  function splitSelectorList(selectorText) {
    const out = [];
    let current = '';
    let parenDepth = 0;
    let bracketDepth = 0;
    let quote = null;

    for (let i = 0; i < selectorText.length; i++) {
      const ch = selectorText[i];
      const prev = i > 0 ? selectorText[i - 1] : '';

      if (quote) {
        current += ch;
        if (ch === quote && prev !== '\\') quote = null;
        continue;
      }

      if (ch === '"' || ch === "'") {
        quote = ch;
        current += ch;
        continue;
      }

      if (ch === '(') parenDepth++;
      if (ch === ')') parenDepth = Math.max(0, parenDepth - 1);
      if (ch === '[') bracketDepth++;
      if (ch === ']') bracketDepth = Math.max(0, bracketDepth - 1);

      if (ch === ',' && parenDepth === 0 && bracketDepth === 0) {
        if (current.trim()) out.push(current.trim());
        current = '';
        continue;
      }

      current += ch;
    }

    if (current.trim()) out.push(current.trim());
    return out;
  }

  function prefixSelectorList(selectorText, scopeSelector) {
    return splitSelectorList(selectorText)
      .map((sel) => {
        const trimmed = sel.trim();

        // Dictionary CSS often defines custom properties at :root/body/html.
        // Prefixing as "[scope] :root" is invalid, so inject scope right after
        // the root/html/body head selector.
        const rootHeadMatch = trimmed.match(/^((?:\:root|html|body)(?:[\w\-\[\]="'().:#]*)?)([\s\S]*)$/i);
        if (rootHeadMatch) {
          const head = rootHeadMatch[1];
          const tail = rootHeadMatch[2] || '';

          if (!tail.trim() && /^(?:\:root|html|body)$/i.test(head)) {
            return scopeSelector;
          }

          return `${head} ${scopeSelector}${tail}`;
        }

        return `${scopeSelector} ${trimmed}`;
      })
      .join(', ');
  }

  function getCachedScopedStyle(dictName, css, scopeSelector) {
    const cacheKey = `${dictName}\u0000${css}\u0000${HAS_NATIVE_SCOPE}`;
    if (scopedStyleCache.has(cacheKey)) {
      return {value: scopedStyleCache.get(cacheKey), cacheHit: true};
    }

    const value = buildDictionaryScopedStyle(css, scopeSelector);
    scopedStyleCache.set(cacheKey, value);
    if (scopedStyleCache.size > SCOPED_STYLE_CACHE_LIMIT) {
      const oldestKey = scopedStyleCache.keys().next().value;
      if (oldestKey) scopedStyleCache.delete(oldestKey);
    }
    return {value, cacheHit: false};
  }

  function findMatchingBrace(text, openBraceIndex) {
    let depth = 0;
    let quote = null;

    for (let i = openBraceIndex; i < text.length; i++) {
      const ch = text[i];
      const prev = i > 0 ? text[i - 1] : '';

      if (quote) {
        if (ch === quote && prev !== '\\') quote = null;
        continue;
      }

      if (ch === '"' || ch === "'") {
        quote = ch;
        continue;
      }

      if (ch === '{') depth++;
      if (ch === '}') {
        depth--;
        if (depth === 0) return i;
      }
    }

    return -1;
  }

  function scopeCssBlocks(cssText, scopeSelector) {
    let i = 0;
    let out = '';

    while (i < cssText.length) {
      while (i < cssText.length && /\s/.test(cssText[i])) i++;
      if (i >= cssText.length) break;

      if (cssText.startsWith('/*', i)) {
        const end = cssText.indexOf('*/', i + 2);
        if (end === -1) break;
        i = end + 2;
        continue;
      }

      let preludeStart = i;
      while (i < cssText.length && cssText[i] !== '{' && cssText[i] !== ';') i++;
      if (i >= cssText.length) break;

      const stopChar = cssText[i];
      const prelude = cssText.slice(preludeStart, i).trim();
      if (!prelude) {
        i++;
        continue;
      }

      if (stopChar === ';') {
        out += `${prelude};\n`;
        i++;
        continue;
      }

      const openBraceIndex = i;
      const closeBraceIndex = findMatchingBrace(cssText, openBraceIndex);
      if (closeBraceIndex === -1) {
        debugWarn('scopeCssBlocks.unmatchedBrace', {scopeSelector, prelude});
        break;
      }

      const body = cssText.slice(openBraceIndex + 1, closeBraceIndex);
      if (prelude.startsWith('@')) {
        if (/^@(media|supports|layer|document)\b/i.test(prelude)) {
          const scopedInner = scopeCssBlocks(body, scopeSelector);
          out += `${prelude} {\n${scopedInner}\n}\n`;
        } else {
          out += `${prelude} {\n${body}\n}\n`;
        }
      } else {
        const scopedSelectors = prefixSelectorList(prelude, scopeSelector);
        out += `${scopedSelectors} {\n${body}\n}\n`;
      }

      i = closeBraceIndex + 1;
    }

    return out.trim();
  }

  function scopeCssWithCSSOM(cssText, scopeSelector) {
    const sheet = new CSSStyleSheet();
    sheet.replaceSync(cssText);
    const parts = [];
    processRulesFromCSSOM(sheet.cssRules, scopeSelector, parts);
    return parts.join('\n');
  }

  function processRulesFromCSSOM(rules, scope, out) {
    for (let i = 0; i < rules.length; i++) {
      const rule = rules[i];
      const type = rule.type;

      if (type === CSSRule.STYLE_RULE) {
        const prefixed = rule.selectorText
          .split(',')
          .map(s => prefixSelectorForCSSOM(s.trim(), scope))
          .join(', ');
        out.push(`${prefixed} { ${rule.style.cssText} }`);
      } else if (type === CSSRule.MEDIA_RULE) {
        const inner = [];
        processRulesFromCSSOM(rule.cssRules, scope, inner);
        out.push(`@media ${rule.media.mediaText} {\n${inner.join('\n')}\n}`);
      } else if (type === CSSRule.SUPPORTS_RULE) {
        const inner = [];
        processRulesFromCSSOM(rule.cssRules, scope, inner);
        const condition = rule.conditionText || '';
        out.push(`@supports ${condition} {\n${inner.join('\n')}\n}`);
      } else {
        out.push(rule.cssText);
      }
    }
  }

  function prefixSelectorForCSSOM(sel, scope) {
    // :root / html / body alone → scope selector
    if (/^(:root|html|body)$/i.test(sel)) {
      return scope;
    }

    // :root[data-theme="dark"] .foo → :root[data-theme="dark"] [scope] .foo
    const rootWithAttr = sel.match(/^(:root|html|body)(\[[^\]]+\])([\s\S]*)$/i);
    if (rootWithAttr) {
      const head = rootWithAttr[1];
      const attr = rootWithAttr[2];
      const tail = rootWithAttr[3] || '';
      if (!tail.trim()) {
        return `${head}${attr} ${scope}`;
      }
      return `${head}${attr} ${scope}${tail}`;
    }

    // :root .foo → [scope] .foo
    const rootDesc = sel.match(/^(:root|html|body)\s+([\s\S]+)$/i);
    if (rootDesc) {
      return `${scope} ${rootDesc[2]}`;
    }

    // Regular selector → [scope] selector
    return `${scope} ${sel}`;
  }

  function buildDictionaryScopedStyle(css, scopeSelector) {
    const trimmed = css.trim();
    if (!trimmed) return '';

    // Declarations-only chunks can be wrapped directly.
    if (!trimmed.includes('{')) {
      const wrapped = `${scopeSelector} {\n${trimmed}\n}`;
      debugLog('buildDictionaryScopedStyle.declarationsOnly', {
        scopeSelector,
        inputLength: trimmed.length,
        outputLength: wrapped.length,
      });
      return wrapped;
    }

    // Three-tier scoping: native @scope > CSSOM > string parser
    if (HAS_NATIVE_SCOPE) {
      const scoped = `@scope (${scopeSelector}) {\n${trimmed}\n}`;
      debugLog('buildDictionaryScopedStyle.nativeScope', {
        scopeSelector,
        inputLength: trimmed.length,
        outputLength: scoped.length,
      });
      return scoped;
    }

    if (HAS_CONSTRUCTED_SHEETS) {
      try {
        const scoped = scopeCssWithCSSOM(trimmed, scopeSelector);
        if (scoped) {
          debugLog('buildDictionaryScopedStyle.cssom', {
            scopeSelector,
            inputLength: trimmed.length,
            outputLength: scoped.length,
          });
          return scoped;
        }
      } catch (e) {
        debugWarn('buildDictionaryScopedStyle.cssomFailed', {
          scopeSelector,
          error: e.message,
        });
      }
    }

    // Fallback: string-based CSS parser
    const scoped = scopeCssBlocks(trimmed, scopeSelector);
    if (scoped) {
      debugLog('buildDictionaryScopedStyle.fallback', {
        scopeSelector,
        inputLength: trimmed.length,
        outputLength: scoped.length,
      });
      return scoped;
    }

    const lastResort = `${scopeSelector} {\n${trimmed}\n}`;
    debugWarn('buildDictionaryScopedStyle.lastResort', {
      scopeSelector,
      inputLength: trimmed.length,
      outputLength: lastResort.length,
    });
    return lastResort;
  }

  function buildScopedDictionaryCss(stylesPayload) {
    const parts = [];
    debugLog('buildScopedDictionaryCss.start', {
      styleEntries: Array.isArray(stylesPayload) ? stylesPayload.length : 0
    });

    for (const styleEntry of stylesPayload) {
      if (typeof styleEntry === 'string') {
        const legacyCss = sanitizeDictionaryStyles(styleEntry);
        if (legacyCss) {
          parts.push(legacyCss);
          debugLog('buildScopedDictionaryCss.legacy', {
            cssLength: legacyCss.length,
            hasBackground: /background/i.test(legacyCss)
          });
        }
        continue;
      }
      if (!styleEntry || typeof styleEntry !== 'object') continue;

      const dictName = typeof styleEntry.dictName === 'string' ? styleEntry.dictName : '';
      const rawCss = typeof styleEntry.styles === 'string' ? styleEntry.styles : '';
      const css = sanitizeDictionaryStyles(rawCss);
      if (!dictName || !css) continue;

      const escapedName = dictName.replace(/"/g, '\\"');
      const selector = `[data-dictionary="${escapedName}"]`;
      const {value: scoped, cacheHit} = getCachedScopedStyle(dictName, css, selector);
      parts.push(scoped);
      debugLog('buildScopedDictionaryCss.dictionary', {
        dictName,
        cssLength: css.length,
        scopedLength: scoped.length,
        cacheHit,
        hasBackground: /background/i.test(css)
      });
    }
    const method = HAS_NATIVE_SCOPE ? 'native-scope' : (HAS_CONSTRUCTED_SHEETS ? 'cssom' : 'string-parser');
    const output = parts.join('\n\n');
    debugLog('buildScopedDictionaryCss.done', {outputLength: output.length, method});
    return output;
  }

  function trimFloat(value) {
    if (!Number.isFinite(value)) return '0';
    const rounded = Math.round(value * 10000) / 10000;
    return Number.isInteger(rounded) ? String(rounded) : String(rounded);
  }

  function sanitizeHref(href) {
    if (!href) return null;
    const value = String(href).trim();
    if (/^javascript:/i.test(value)) return null;
    return value;
  }

  function applyDataAttributes(node, data) {
    if (!data || typeof data !== 'object') return;
    for (const key of Object.keys(data)) {
      const value = data[key];
      if (value === null || typeof value === 'undefined') continue;
      if (key === 'class') {
        const classText = String(value);
        classText.split(' ').map((s) => s.trim()).filter(Boolean).forEach((klass) => node.classList.add(klass));
        // Keep legacy class selectors and data-sc-class selectors both working.
        node.setAttribute('data-sc-class', classText);
        if (DEBUG_VERBOSE_NODES) {
          debugLog('applyDataAttributes.class', {
            tag: node.tagName,
            className: node.className,
            source: classText
          });
        }
        continue;
      }
      const attrName = `data-sc-${key}`;
      // Keep empty-string values to preserve attribute-presence selectors.
      node.setAttribute(attrName, String(value));

      if (key === 'content' || key === 'headword' || key === 'example' || key === 'meaning') {
        debugLog('applyDataAttributes.attr', {
          tag: node.tagName,
          attrName,
          value: String(value)
        });
      }
    }
  }

  function applyStyle(node, style) {
    if (!style || typeof style !== 'object') return;
    const map = {
      fontStyle: 'fontStyle',
      fontWeight: 'fontWeight',
      fontSize: 'fontSize',
      color: 'color',
      background: 'background',
      backgroundColor: 'backgroundColor',
      textDecorationStyle: 'textDecorationStyle',
      textDecorationColor: 'textDecorationColor',
      borderColor: 'borderColor',
      borderStyle: 'borderStyle',
      borderRadius: 'borderRadius',
      borderWidth: 'borderWidth',
      clipPath: 'clipPath',
      verticalAlign: 'verticalAlign',
      textAlign: 'textAlign',
      textEmphasis: 'textEmphasis',
      textShadow: 'textShadow',
      margin: 'margin',
      padding: 'padding',
      paddingTop: 'paddingTop',
      paddingLeft: 'paddingLeft',
      paddingRight: 'paddingRight',
      paddingBottom: 'paddingBottom',
      whiteSpace: 'whiteSpace',
      wordBreak: 'wordBreak',
      cursor: 'cursor',
      listStyleType: 'listStyleType'
    };

    for (const [sourceKey, cssKey] of Object.entries(map)) {
      const value = style[sourceKey];
      if (typeof value === 'string' && value.length > 0) {
        node.style[cssKey] = value;
      }
    }

    const emSpacing = ['marginTop', 'marginLeft', 'marginRight', 'marginBottom'];
    for (const key of emSpacing) {
      const value = style[key];
      if (typeof value === 'number') {
        node.style[key] = `${value}em`;
      } else if (typeof value === 'string' && value.length > 0) {
        node.style[key] = value;
      }
    }

    const decorationLine = style.textDecorationLine;
    if (Array.isArray(decorationLine)) {
      node.style.textDecoration = decorationLine.join(' ');
    } else if (typeof decorationLine === 'string' && decorationLine.length > 0) {
      node.style.textDecoration = decorationLine;
    }
    if (typeof style.textDecoration === 'string' && style.textDecoration.length > 0) {
      node.style.textDecoration = style.textDecoration;
    }
  }

  function parseMaybeJson(text) {
    const trimmed = text.trim();
    if (!(trimmed.startsWith('{') || trimmed.startsWith('['))) return null;
    try {
      return JSON.parse(trimmed);
    } catch (e) {
      return null;
    }
  }

  function mediaCandidates(path) {
    const list = new Set();
    const add = (value) => {
      if (!value) return;
      list.add(value);
      list.add(value.replace(/\\/g, '/'));
      if (value.startsWith('./')) list.add(value.slice(2));
      if (value.startsWith('/')) list.add(value.slice(1));
    };
    add(path);
    try {
      add(decodeURIComponent(path));
    } catch (e) {
      // ignore malformed URI paths
    }
    return Array.from(list);
  }

  function resolveMediaSrc(mediaMap, dictName, path) {
    if (!path) return null;
    for (const candidate of mediaCandidates(path)) {
      const key = `${dictName}\u0000${candidate}`;
      if (Object.prototype.hasOwnProperty.call(mediaMap, key)) {
        return mediaMap[key];
      }
    }
    return null;
  }

  function createTag(label, category) {
    const span = document.createElement('span');
    span.className = 'tag';
    span.dataset.category = category;

    const inner = document.createElement('span');
    inner.className = 'tag-label';

    const content = document.createElement('span');
    content.className = 'tag-label-content';
    content.textContent = label;

    inner.appendChild(content);
    span.appendChild(inner);
    return span;
  }

  function buildGlossaryNode(rawGlossary, dictName, mediaMap) {
    const parsed = parseMaybeJson(rawGlossary);
    if (parsed === null) {
      const span = document.createElement('span');
      span.className = 'gloss-plain-text';
      if (containsJapaneseText(rawGlossary)) {
        span.lang = 'ja';
      }
      span.textContent = rawGlossary;
      return span;
    }
    return renderStructured(parsed, dictName, mediaMap);
  }

  function containsJapaneseText(text) {
    return typeof text === 'string' && /[\u3040-\u30ff\u3400-\u4dbf\u4e00-\u9fff]/.test(text);
  }

  function getNodeText(node) {
    if (node === null || typeof node === 'undefined') return '';
    if (typeof node === 'string' || typeof node === 'number' || typeof node === 'boolean') {
      return String(node);
    }
    if (Array.isArray(node)) {
      return node.map((item) => getNodeText(item)).join('');
    }
    if (typeof node !== 'object') return '';
    if (node && node.data && node.data.content === 'attribution') return '';
    if (node.type === 'structured-content') return getNodeText(node.content);
    return getNodeText(node.content);
  }

  function normalizeTableContent(content) {
    const items = Array.isArray(content) ? content : [content];
    const hasRows = items.some((item) => item && item.tag === 'tr');
    if (!hasRows) return content;
    const hasSections = items.some((item) => item && (item.tag === 'tbody' || item.tag === 'thead' || item.tag === 'tfoot'));
    if (hasSections) return content;
    return [{tag: 'tbody', content: items}];
  }

  function renderStructured(content, dictName, mediaMap) {
    const fragment = document.createDocumentFragment();
    appendStructured(fragment, content, dictName, mediaMap, null);
    const wrapper = document.createElement('span');
    wrapper.className = 'structured-content';
    wrapper.appendChild(fragment);
    return wrapper;
  }

  // Registry-based dispatch for known tags; unknown valid tags still fall back to generic renderer.
  const GENERIC_ELEMENT_TAGS = [
    'br', 'ruby', 'rt', 'rp', 'table', 'thead', 'tbody', 'tfoot', 'tr', 'th', 'td',
    'div', 'span', 'ol', 'ul', 'li', 'details', 'summary', 'p', 'strong', 'em',
    'b', 'i', 'u', 'small', 'sub', 'sup', 'code', 'pre', 'blockquote', 's', 'mark',
    'ins', 'del', 'kbd', 'samp', 'var', 'time', 'q', 'cite', 'abbr'
  ];

  function getTagInfo(node, fallbackTag) {
    const raw = typeof node.tag === 'string' && node.tag.trim().length > 0
      ? node.tag.trim()
      : fallbackTag;
    const lower = raw.toLowerCase();
    return {raw, lower};
  }

  function createGenericElementHandler(defaultTagLower) {
    return (node, dictName, mediaMap, language) => {
      const {raw, lower} = getTagInfo(node, defaultTagLower);
      return createGenericStructuredElement(node, raw, lower, dictName, mediaMap, language);
    };
  }

  function createElementRegistry() {
    const registry = {
      img: (node, dictName, mediaMap) => createImageNode(node, dictName, mediaMap),
      a: (node, dictName, mediaMap, language) => createLinkNode(node, dictName, mediaMap, language)
    };

    for (const tag of GENERIC_ELEMENT_TAGS) {
      registry[tag] = createGenericElementHandler(tag);
    }

    return Object.freeze(registry);
  }

  const ELEMENT_REGISTRY = createElementRegistry();

  function isValidTagName(tagName) {
    return /^[a-z][a-z0-9:-]*$/i.test(tagName);
  }

  const SVG_NAMESPACE = 'http://www.w3.org/2000/svg';
  const svgTags = new Set([
    'svg', 'g', 'path', 'rect', 'circle', 'ellipse', 'line', 'polyline', 'polygon',
    'text', 'tspan', 'textpath', 'defs', 'symbol', 'use', 'marker', 'pattern',
    'mask', 'clippath', 'lineargradient', 'radialgradient', 'stop', 'view', 'foreignobject'
  ]);

  const svgAttributeMap = {
    viewBox: 'viewBox',
    preserveAspectRatio: 'preserveAspectRatio',
    width: 'width',
    height: 'height',
    x: 'x',
    y: 'y',
    x1: 'x1',
    y1: 'y1',
    x2: 'x2',
    y2: 'y2',
    cx: 'cx',
    cy: 'cy',
    r: 'r',
    rx: 'rx',
    ry: 'ry',
    d: 'd',
    points: 'points',
    fill: 'fill',
    stroke: 'stroke',
    strokeWidth: 'stroke-width',
    strokeLinecap: 'stroke-linecap',
    strokeLinejoin: 'stroke-linejoin',
    strokeMiterlimit: 'stroke-miterlimit',
    strokeDasharray: 'stroke-dasharray',
    strokeDashoffset: 'stroke-dashoffset',
    transform: 'transform',
    opacity: 'opacity',
    fillRule: 'fill-rule',
    clipRule: 'clip-rule',
    xmlns: 'xmlns',
    href: 'href'
  };

  function isSvgTag(tagNameLower) {
    return svgTags.has(tagNameLower);
  }

  function createElementForTag(tagName, isSvgElement) {
    return isSvgElement
      ? document.createElementNS(SVG_NAMESPACE, tagName)
      : document.createElement(tagName);
  }

  function applySvgAttributes(element, node) {
    for (const [sourceKey, attrName] of Object.entries(svgAttributeMap)) {
      const value = node[sourceKey];
      if (typeof value === 'string' || typeof value === 'number') {
        element.setAttribute(attrName, String(value));
      }
    }

    // Keep SVG sizing behavior data-driven from dictionary styles/content.
  }

  function applyCommonStructuredAttributes(element, node) {
    if (node.lang && typeof node.lang === 'string') {
      element.lang = node.lang;
    }
    if (node.title && typeof node.title === 'string') {
      element.title = node.title;
    }
    applyDataAttributes(element, node.data);
    applyStyle(element, node.style);
  }

  function createGenericStructuredElement(node, tagNameRaw, tagNameLower, dictName, mediaMap, language) {
    const svgElement = isSvgTag(tagNameLower);
    const elementTagName = svgElement ? tagNameRaw : tagNameLower;
    const element = createElementForTag(elementTagName, svgElement);
    element.classList.add(`gloss-sc-${tagNameLower}`);

    if (svgElement) {
      applySvgAttributes(element, node);
    }

    if (node.lang && typeof node.lang === 'string') {
      language = node.lang;
    }

    if ((tagNameLower === 'th' || tagNameLower === 'td') && Number.isFinite(node.colSpan)) {
      element.colSpan = node.colSpan;
    }
    if ((tagNameLower === 'th' || tagNameLower === 'td') && Number.isFinite(node.rowSpan)) {
      element.rowSpan = node.rowSpan;
    }
    if (tagNameLower === 'details' && node.open === true) {
      element.setAttribute('open', '');
    }

    applyCommonStructuredAttributes(element, node);

    if (DEBUG_VERBOSE_NODES && node && node.data && (Object.prototype.hasOwnProperty.call(node.data, 'content') || Object.prototype.hasOwnProperty.call(node.data, 'class'))) {
      debugLog('createGenericStructuredElement.node', {
        dictName,
        tag: tagNameLower,
        className: element.className,
        dataContent: Object.prototype.hasOwnProperty.call(node.data, 'content') ? String(node.data.content) : '',
        dataClass: Object.prototype.hasOwnProperty.call(node.data, 'class') ? String(node.data.class) : ''
      });
    }

    const contentToRender = tagNameLower === 'table' ? normalizeTableContent(node.content) : node.content;

    if (tagNameLower !== 'br') {
      appendStructured(element, contentToRender, dictName, mediaMap, language);
    }

    if (tagNameLower === 'table') {
      const wrapper = document.createElement('div');
      wrapper.className = 'gloss-sc-table-container';
      wrapper.appendChild(element);
      return wrapper;
    }

    return element;
  }

  function renderStructuredObjectNode(content, dictName, mediaMap, language) {
    if (!content || typeof content !== 'object') return null;
    if (content.data && content.data.content === 'attribution') return null;

    let node = content;
    if (node.type === 'structured-content') {
      return {kind: 'nested', content: node.content};
    }
    if (node.type === 'image' && !node.tag) {
      node = Object.assign({}, node, {tag: 'img'});
    }

    const tagNameRaw = typeof node.tag === 'string' ? node.tag.trim() : '';
    if (!tagNameRaw) {
      return {kind: 'nested', content: node.content};
    }

    if (!isValidTagName(tagNameRaw)) {
      return {kind: 'nested', content: node.content};
    }

    const tagNameLower = tagNameRaw.toLowerCase();
    const handler = ELEMENT_REGISTRY[tagNameLower];
    if (handler) {
      return {kind: 'node', node: handler(node, dictName, mediaMap, language)};
    }

    return {
      kind: 'node',
      node: createGenericStructuredElement(node, tagNameRaw, tagNameLower, dictName, mediaMap, language)
    };
  }

  function appendStructured(parent, content, dictName, mediaMap, language) {
    if (content === null || typeof content === 'undefined') return;

    if (typeof content === 'string' || typeof content === 'number' || typeof content === 'boolean') {
      parent.appendChild(document.createTextNode(String(content)));
      return;
    }

    if (Array.isArray(content)) {
      const isStringArray = content.every((item) => typeof item === 'string');
      const insideSpan = parent && parent.tagName === 'SPAN';
      if (isStringArray && content.length > 1 && !insideSpan) {
        const ul = document.createElement('ul');
        ul.classList.add('glossary-list');
        content.forEach((child) => {
          const li = document.createElement('li');
          li.appendChild(document.createTextNode(child));
          ul.appendChild(li);
        });
        parent.appendChild(ul);
        return;
      }

      for (const item of content) appendStructured(parent, item, dictName, mediaMap, language);
      return;
    }

    if (typeof content !== 'object') return;

    const rendered = renderStructuredObjectNode(content, dictName, mediaMap, language);
    if (!rendered) return;
    if (rendered.kind === 'nested') {
      appendStructured(parent, rendered.content, dictName, mediaMap, language);
      return;
    }
    parent.appendChild(rendered.node);
  }

  function createLinkNode(node, dictName, mediaMap, language) {
    const href = sanitizeHref(node.href);
    const internal = href && href.startsWith('?');

    const a = document.createElement('a');
    a.className = 'gloss-link gloss-sc-a';
    a.dataset.external = String(!internal);
    if (href) {
      a.href = href;
      if (!internal) {
        a.target = '_blank';
        a.rel = 'noreferrer noopener';
      }
    }
    if (typeof node.title === 'string') a.title = node.title;
    if (typeof node.lang === 'string') {
      a.lang = node.lang;
      language = node.lang;
    }

    applyCommonStructuredAttributes(a, node);

    if (DEBUG_VERBOSE_NODES) {
      debugLog('createLinkNode', {dictName, href, internal});
    }

    const text = document.createElement('span');
    text.className = 'gloss-link-text';
    appendStructured(text, node.content, dictName, mediaMap, language);
    a.appendChild(text);
    return a;
  }

  function createImageNode(node, dictName, mediaMap) {
    const path = typeof node.path === 'string' ? node.path : (typeof node.src === 'string' ? node.src : '');
    const src = resolveMediaSrc(mediaMap, dictName, path) || path;

    if (DEBUG_VERBOSE_NODES) {
      debugLog('createImageNode', {
        dictName,
        path,
        resolved: src,
        resolvedFromMap: src !== path
      });
    }

    const preferredWidth = Number(node.preferredWidth ?? (node.data && node.data.preferredWidth));
    const preferredHeight = Number(node.preferredHeight ?? (node.data && node.data.preferredHeight));
    const width = Number(node.width ?? (node.data && node.data.width) ?? 100);
    const height = Number(node.height ?? (node.data && node.data.height) ?? 100);

    const ratioWidth = Number.isFinite(preferredWidth) && preferredWidth > 0 ? preferredWidth : width;
    const ratioHeight = Number.isFinite(preferredHeight) && preferredHeight > 0 ? preferredHeight : height;
    const invAspectRatio = Math.max(0.01, ratioHeight / Math.max(1, ratioWidth));

    const units = typeof node.sizeUnits === 'string'
      ? node.sizeUnits
      : (node.data && typeof node.data.sizeUnits === 'string' ? node.data.sizeUnits : 'em');

    const usedWidth = Number.isFinite(preferredWidth) && preferredWidth > 0
      ? preferredWidth
      : (Number.isFinite(preferredHeight) && preferredHeight > 0 ? preferredHeight / invAspectRatio : width);

    const imageRendering = typeof node.imageRendering === 'string'
      ? node.imageRendering
      : (node.pixelated === true ? 'pixelated' : 'auto');

    const link = document.createElement('a');
    link.className = 'gloss-image-link gloss-sc-a';
    link.dataset.path = path;
    link.dataset.imageLoadState = src && src.startsWith('data:') ? 'loaded' : 'unloaded';
    link.dataset.hasAspectRatio = 'true';
    link.dataset.imageRendering = imageRendering;
    link.dataset.appearance = typeof node.appearance === 'string' ? node.appearance : 'auto';
    link.dataset.background = String(node.background !== false);
    if (src) link.href = src;
    if (typeof node.title === 'string') link.title = node.title;

    applyCommonStructuredAttributes(link, node);

    const container = document.createElement('span');
    container.className = 'gloss-image-container';
    container.style.width = `${trimFloat(usedWidth)}${units}`;
    if (typeof node.border === 'string') container.style.border = node.border;
    if (typeof node.borderRadius === 'string') container.style.borderRadius = node.borderRadius;

    const sizer = document.createElement('span');
    sizer.className = 'gloss-image-sizer';
    sizer.style.paddingTop = `${trimFloat(invAspectRatio * 100)}%`;
    container.appendChild(sizer);

    const bg = document.createElement('span');
    bg.className = 'gloss-image-background';
    if (src) bg.style.setProperty('--image', `url("${src}")`);
    container.appendChild(bg);

    const img = document.createElement('img');
    img.className = 'gloss-image gloss-sc-img';
    if (src) img.src = src;
    img.loading = 'lazy';
    img.decoding = 'async';
    img.style.imageRendering = imageRendering;
    if (typeof node.title === 'string') img.alt = node.title;
    container.appendChild(img);

    link.appendChild(container);
    return link;
  }

  function createHeadwordNode(expression, reading) {
    const headword = document.createElement('span');
    headword.className = 'headword';

    if (reading && reading !== expression) {
      const ruby = document.createElement('ruby');
      ruby.className = 'headword-text-container';

      const termNode = document.createElement('span');
      termNode.className = 'headword-term';
      termNode.textContent = expression;

      const rt = document.createElement('rt');
      rt.className = 'headword-reading';
      rt.textContent = reading;

      ruby.appendChild(termNode);
      ruby.appendChild(rt);
      headword.appendChild(ruby);
      return headword;
    }

    const termNode = document.createElement('span');
    termNode.className = 'headword-term';
    termNode.textContent = expression;
    headword.appendChild(termNode);

    if (reading) {
      const readingNode = document.createElement('span');
      readingNode.className = 'headword-reading';
      readingNode.textContent = reading;
      headword.appendChild(readingNode);
    }

    return headword;
  }

  function appendInflectionSection(body, rules, process) {
    if (!rules && !process) return;

    const chains = document.createElement('ul');
    chains.className = 'inflection-rule-chains';
    if (rules) {
      const li = document.createElement('li');
      li.textContent = rules;
      chains.appendChild(li);
    }
    if (process) {
      const li = document.createElement('li');
      li.textContent = process;
      chains.appendChild(li);
    }
    body.appendChild(chains);
  }

  function createDefinitionItem(glossary, mediaMap) {
    const definitionItem = document.createElement('li');
    definitionItem.className = 'definition-item';
    const dictName = String(glossary.dictName || '');
    definitionItem.dataset.dictionary = dictName;

    const tags = document.createElement('div');
    tags.className = 'definition-tag-list';

    if (glossary.dictName) tags.appendChild(createTag(glossary.dictName, 'dictionary'));
    if (glossary.termTags) {
      String(glossary.termTags).split(',').map((s) => s.trim()).filter(Boolean)
        .forEach((tag) => tags.appendChild(createTag(tag, 'partOfSpeech')));
    }
    if (glossary.definitionTags) {
      String(glossary.definitionTags).split(',').map((s) => s.trim()).filter(Boolean)
        .forEach((tag) => tags.appendChild(createTag(tag, 'default')));
    }
    definitionItem.appendChild(tags);

    const glossContent = document.createElement('div');
    glossContent.className = 'gloss-content';
    glossContent.appendChild(buildGlossaryNode(String(glossary.glossary || ''), dictName, mediaMap));
    definitionItem.appendChild(glossContent);

    return definitionItem;
  }

  function appendDefinitionsSection(body, glossaries, mediaMap) {
    const defSection = document.createElement('div');
    defSection.className = 'entry-body-section';
    const definitionList = document.createElement('ol');
    definitionList.className = 'definition-list';

    for (const glossary of glossaries) {
      definitionList.appendChild(createDefinitionItem(glossary, mediaMap));
    }

    defSection.appendChild(definitionList);
    body.appendChild(defSection);
  }

  function appendFrequenciesSection(body, frequencies, showHarmonic) {
    if (frequencies.length === 0) return;

    // If showHarmonic is true, calculate and display harmonic mean instead of full list
    if (showHarmonic) {
      const numbers = [];
      const seen = new Set();
      for (const group of frequencies) {
        if (seen.has(group.dictName)) continue;
        seen.add(group.dictName);
        const items = Array.isArray(group.frequencies) ? group.frequencies : [];
        for (const item of items) {
          if (item && item.value > 0) {
            numbers.push(item.value);
            break;
          }
        }
      }
      
      if (numbers.length > 0) {
        const n = numbers.length;
        let harmonic = 0;
        for (const num of numbers) {
          harmonic += 1 / num;
        }
        harmonic = Math.floor(n / harmonic);
        
        const section = document.createElement('div');
        section.className = 'entry-body-section';
        const tag = document.createElement('span');
        tag.className = 'tag';
        tag.setAttribute('data-category', 'frequency');
        tag.innerHTML = '<span class="tag-label">freq</span><span class="tag-body">harmonic: ' + harmonic + '</span>';
        section.appendChild(tag);
        body.appendChild(section);
      }
      return;
    }

    // Default: show full frequency list
    const section = document.createElement('div');
    section.className = 'entry-body-section';
    const list = document.createElement('ol');
    list.className = 'frequency-group-list';

    for (const group of frequencies) {
      const li = document.createElement('li');
      li.className = 'frequency-group-item';

      li.appendChild(createTag(String(group.dictName || ''), 'frequency'));

      const span = document.createElement('span');
      const items = Array.isArray(group.frequencies) ? group.frequencies : [];
      items.forEach((item, idx) => {
        const value = item && item.displayValue ? item.displayValue : String(item && item.value ? item.value : '');
        const itemNode = document.createElement('span');
        itemNode.className = 'frequency-item';
        itemNode.textContent = value;
        span.appendChild(itemNode);
        if (idx !== items.length - 1) span.appendChild(document.createTextNode(' '));
      });

      li.appendChild(span);
      list.appendChild(li);
    }

    section.appendChild(list);
    body.appendChild(section);
  }

  function appendPitchesSection(body, pitches) {
    if (pitches.length === 0) return;

    const section = document.createElement('div');
    section.className = 'entry-body-section';
    const list = document.createElement('ol');
    list.className = 'pronunciation-group-list';

    for (const group of pitches) {
      const li = document.createElement('li');
      li.className = 'pronunciation-group';
      li.appendChild(createTag(String(group.dictName || ''), 'pronunciation-dictionary'));
      const note = document.createElement('span');
      const positions = Array.isArray(group.pitchPositions) ? group.pitchPositions.join(', ') : '-';
      note.textContent = ` [${positions || '-'}]`;
      li.appendChild(note);
      list.appendChild(li);
    }

    section.appendChild(list);
    body.appendChild(section);
  }

  function renderEntry(result, mediaMap, showFrequencyHarmonic, existingExpressions) {
    const existingSet = Array.isArray(existingExpressions) ? existingExpressions : [];
    const article = document.createElement('article');
    article.className = 'entry';
    article.dataset.index = String(result.index || 0);
    
    // Add data-dictionary attribute for scoped CSS
    const dictName = result.term && result.term.glossaries && result.term.glossaries[0] && result.term.glossaries[0].dictName;
    if (dictName) {
      article.dataset.dictionary = dictName;
    }

    const body = document.createElement('div');
    body.className = 'entry-body';
    article.appendChild(body);

    const expression = (result.term && result.term.expression) || result.matched || '';
    const reading = result.term && result.term.reading ? result.term.reading : '';

    const headSection = document.createElement('div');
    headSection.className = 'entry-body-section entry-headword-row';
    headSection.appendChild(createHeadwordNode(expression, reading));

    // Anki add button
    const ankiBtn = document.createElement('button');
    // Check if expression is already in Anki (from payload)
    const isAlreadyAdded = existingSet.includes(expression);
    ankiBtn.className = isAlreadyAdded ? 'anki-add-btn anki-added' : 'anki-add-btn';
    ankiBtn.textContent = '+';
    ankiBtn.title = isAlreadyAdded ? 'Already in Anki' : 'Add to Anki';
    ankiBtn.setAttribute('data-index', String(result.index || 0));
    ankiBtn.setAttribute('data-expression', expression);
    ankiBtn.onclick = (e) => {
      e.stopPropagation();
      if (typeof AnkiBridge !== 'undefined') {
        AnkiBridge.addToAnki(ankiBtn.getAttribute('data-index'));
      }
    };
    headSection.appendChild(ankiBtn);

    body.appendChild(headSection);

    const rules = result.term && result.term.rules ? result.term.rules : '';
    const process = Array.isArray(result.process) ? result.process.join(' -> ') : '';
    appendInflectionSection(body, rules, process);

    const glossaries = (result.term && Array.isArray(result.term.glossaries)) ? result.term.glossaries : [];
    appendDefinitionsSection(body, glossaries, mediaMap);

    const frequencies = result.term && Array.isArray(result.term.frequencies) ? result.term.frequencies : [];
    appendFrequenciesSection(body, frequencies, showFrequencyHarmonic);

    const pitches = result.term && Array.isArray(result.term.pitches) ? result.term.pitches : [];
    appendPitchesSection(body, pitches);

    return article;
  }

  function renderHeader(text) {
    if (!text) return;
    const container = document.getElementById('entries');
    if (!container) return;
    const existing = container.querySelector('.popup-header');
    if (existing) {
      existing.textContent = text;
      return;
    }
    const header = document.createElement('div');
    header.className = 'popup-header';
    header.textContent = text;
    container.insertBefore(header, container.firstChild);
  }

  function render(payload) {
    console.log('[DictionaryRenderJS] render called, payload type:', typeof payload, 'keys:', payload ? Object.keys(payload).join(',') : 'null');
    
    if (!payload || typeof payload !== 'object') {
      console.error('[DictionaryRenderJS] render: invalid payload:', payload);
      return;
    }
    
    const started = performance.now();
    const root = document.documentElement;
    root.setAttribute('data-theme', payload.isDark ? 'dark' : 'light');

    debugLog('render.start', {
      isDark: !!payload.isDark,
      styles: Array.isArray(payload.styles) ? payload.styles.length : 0,
      results: Array.isArray(payload.results) ? payload.results.length : 0
    });

    const styleNode = document.getElementById('dictionary-styles');
    if (styleNode) {
      const stylesPayload = Array.isArray(payload.styles) ? payload.styles : [];

      // Create a cache key based on dict names and CSS lengths
      const stylesKey = stylesPayload
        .map(s => `${s.dictName}:${(s.styles || '').length}`)
        .join('|');

      if (styleNode.dataset.cacheKey !== stylesKey) {
        const cssText = buildScopedDictionaryCss(stylesPayload);
        styleNode.textContent = cssText;
        styleNode.dataset.cacheKey = stylesKey;
        debugLog('render.stylesApplied', {styleTextLength: cssText.length});
      } else {
        debugLog('render.stylesCached', {cacheKey: stylesKey});
      }
    }

    const container = document.getElementById('entries');
    if (!container) return;
    container.textContent = '';

    const mediaMap = payload.mediaDataUris || {};
    const results = Array.isArray(payload.results) ? payload.results : [];
    const existingExpressions = Array.isArray(payload.existingExpressions) ? payload.existingExpressions : [];

    if (results.length === 0) {
      const empty = document.createElement('div');
      empty.className = 'hoshi-empty';
      empty.textContent = payload.placeholder || '';
      container.appendChild(empty);
    } else {
      const fragment = document.createDocumentFragment();
      for (const result of results) {
        fragment.appendChild(renderEntry(result, mediaMap, payload.showFrequencyHarmonic, existingExpressions));
      }
      container.appendChild(fragment);
    }

    const elapsed = Math.round(performance.now() - started);
    console.log('[DictionaryRenderJS] render_ms=', elapsed, 'results=', results.length);
  }

  window.DictionaryRenderer = {
  render,
  renderHeader,

  renderFromBase64(base64) {
    const json = decodeBase64Utf8(base64);
    const payload = JSON.parse(json);
    render(payload);
  },
  
  renderFromBridge() {
    try {
      const json = PayloadBridge.getJson();
      console.log('[DictionaryRenderJS] renderFromBridge: json length=' + (json ? json.length : 'null/undefined'));
      if (!json) {
        console.error('[DictionaryRenderJS] Bridge returned empty or null');
        return;
      }
      const payload = JSON.parse(json);
      console.log('[DictionaryRenderJS] renderFromBridge: parsed payload, results=' + (payload.results ? payload.results.length : 0));
      render(payload);
    } catch (e) {
      console.error('[DictionaryRenderJS] renderFromBridge error:', e.message);
    }
  },
  
  clear() {
    const container = document.getElementById('entries');
    if (container) container.textContent = '';
    const styleNode = document.getElementById('dictionary-styles');
    if (styleNode) {
      styleNode.textContent = '';
      delete styleNode.dataset.cacheKey;
    }
  }
};
})();

