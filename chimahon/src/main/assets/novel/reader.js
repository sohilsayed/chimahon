window.hoshiReader = {
    isRtl: false,
    continuousMode: false,
    pageHeight: 0,
    pageWidth: 0,
    // Character counting regex: keeps alphanumeric and CJK scripts (Japanese, Chinese, Korean).
    // Added \p{Script=Hangul} to support Korean and clarified the CJK ideograph property.
    ttuRegexNegated: /[^0-9A-Za-z○◯々-〇〻ぁ-ゖゝ-ゞァ-ヺー０-９Ａ-Ｚａ-ｚｦ-ﾝ\p{Radical}\p{Unified_Ideograph}\p{Script=Hangul}]+/gimu,

    isVertical: function() {
        return window.getComputedStyle(document.body).writingMode === "vertical-rl";
    },

    countChars: function(text) {
        return Array.from(text.replace(this.ttuRegexNegated, '')).length;
    },

    createWalker: function(rootNode) {
        const root = rootNode || document.body;
        return document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
            acceptNode: (n) => this.isFurigana(n) ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT
        });
    },

    getScrollContext: function() {
        var vertical = this.isVertical();
        var scrollEl = document.body;
        var pageSize = Math.max(1, vertical ? (this.pageHeight || window.innerHeight) : (this.pageWidth || window.innerWidth));
        var totalSize = vertical ? scrollEl.scrollHeight : scrollEl.scrollWidth;
        var maxScroll = Math.max(0, totalSize - pageSize);
        return { vertical: vertical, scrollEl: scrollEl, pageSize: pageSize, maxScroll: maxScroll };
    },

    getPagePosition: function(context) {
        return context.vertical ? context.scrollEl.scrollTop : context.scrollEl.scrollLeft;
    },

    lockRootViewport: function() {
        var root = document.documentElement;
        if (root.scrollTop !== 0) root.scrollTop = 0;
        if (root.scrollLeft !== 0) root.scrollLeft = 0;
        if (window.scrollX !== 0 || window.scrollY !== 0) window.scrollTo(0, 0);
    },

    setPagePosition: function(context, position) {
        var clamped = Math.min(Math.max(0, position), context.maxScroll);
        if (context.vertical) {
            context.scrollEl.scrollTop = clamped;
        } else {
            context.scrollEl.scrollLeft = clamped;
        }
        this.lockRootViewport();
    },

    paginate: function(direction) {
        var context = this.getScrollContext();
        if (context.pageSize <= 0) return 'limit';
        var currentScroll = this.getPagePosition(context);

        if (direction === 'forward') {
            if (currentScroll < context.maxScroll - 1) {
                var target = Math.min(Math.round((currentScroll + context.pageSize) / context.pageSize) * context.pageSize, context.maxScroll);
                if (target <= currentScroll + 1) return 'limit';
                this.setPagePosition(context, target);
                return 'scrolled';
            }
            return 'limit';
        } else {
            if (currentScroll > 1) {
                var target = Math.max(0, Math.floor((currentScroll - 1) / context.pageSize) * context.pageSize);
                this.setPagePosition(context, target);
                return 'scrolled';
            }
            return 'limit';
        }
    },

    calculateProgress: function() {
        if (!this.continuousMode) {
            var context = this.getScrollContext();
            if (context.maxScroll <= 0) return 0;
            return this.getPagePosition(context) / context.maxScroll;
        }

        var vertical = this.isVertical();
        var walker = this.createWalker();
        var totalChars = 0;
        var exploredChars = 0;
        var node;

        while (node = walker.nextNode()) {
            var nodeLen = this.countChars(node.textContent);
            totalChars += nodeLen;

            if (nodeLen > 0) {
                var range = document.createRange();
                range.selectNodeContents(node);
                var rect = range.getBoundingClientRect();
                if (vertical ? (rect.left > window.innerWidth) : (rect.bottom < 0)) {
                    exploredChars += nodeLen;
                }
            }
        }

        return totalChars > 0 ? exploredChars / totalChars : 0;
    },

    restoreProgress: function(progress, isRtl) {
        this.isRtl = !!isRtl;
        var p = Math.min(1, Math.max(0, progress));

        if (!this.continuousMode) {
            var context = this.getScrollContext();
            if (context.pageSize <= 0) {
                this.notifyRestoreComplete();
                return;
            }

            if (p >= 0.99) {
                this.setPagePosition(context, context.maxScroll);
                this.notifyRestoreComplete();
                return;
            }

            var walker = this.createWalker();
            var totalChars = 0;
            var node;
            while (node = walker.nextNode()) {
                totalChars += this.countChars(node.textContent);
            }

            if (totalChars <= 0) {
                this.setPagePosition(context, Math.round(context.maxScroll * p));
                this.notifyRestoreComplete();
                return;
            }

            var targetCharCount = Math.ceil(totalChars * p);
            var runningSum = 0;
            var targetNode = null;
            var targetOffset = 0;

            walker = this.createWalker();
            while (node = walker.nextNode()) {
                var nodeLen = this.countChars(node.textContent);
                if ((runningSum + nodeLen) > targetCharCount) {
                    targetNode = node;
                    targetOffset = Math.max(0, targetCharCount - runningSum);
                    break;
                }
                runningSum += nodeLen;
            }

            if (targetNode) {
                var range = document.createRange();
                var targetText = targetNode.textContent || '';
                var offset = 0;
                var charIdx = 0;
                while (charIdx < targetOffset && offset < targetText.length) {
                    var codePointLen = String.fromCodePoint(targetText.codePointAt(offset)).length;
                    offset += codePointLen;
                    charIdx++;
                }
                range.setStart(targetNode, offset);
                range.setEnd(targetNode, Math.min(targetText.length, offset + 1));
                var rect = range.getBoundingClientRect();
                var currentScroll = this.getPagePosition(context);
                var anchor = (context.vertical ? rect.top : rect.left) + currentScroll;
                var aligned = Math.floor(Math.max(0, anchor) / context.pageSize) * context.pageSize;
                this.setPagePosition(context, aligned);
            } else {
                this.setPagePosition(context, Math.round(context.maxScroll * p));
            }

            this.notifyRestoreComplete();
            return;
        }

        // Character-based restoration for continuous mode
        var walker = this.createWalker();
        var totalChars = 0;
        var node;
        while (node = walker.nextNode()) {
            totalChars += this.countChars(node.textContent);
        }

        if (totalChars <= 0) {
            this.notifyRestoreComplete();
            return;
        }

        var targetCharCount = Math.ceil(totalChars * p);
        var runningSum = 0;
        var targetNode = null;

        walker = this.createWalker();
        while (node = walker.nextNode()) {
            runningSum += this.countChars(node.textContent);
            targetNode = node;
            if (runningSum > targetCharCount) {
                break;
            }
        }

        if (targetNode) {
            var el = targetNode.parentElement;
            if (el) {
                el.scrollIntoView({
                    block: p >= 0.999999 ? 'end' : 'start',
                    behavior: 'instant'
                });
            }
        }

        requestAnimationFrame(() => {
            document.body.style.transform = 'translateZ(0)';
            requestAnimationFrame(() => {
                document.body.style.transform = '';
                this.notifyRestoreComplete();
            });
        });
    },

    notifyRestoreComplete: function() {
        requestAnimationFrame(() => {
            document.body.style.transform = 'translateZ(0)';
            requestAnimationFrame(() => {
                document.body.style.transform = '';
                if (window.ReaderAndroid && window.ReaderAndroid.restoreCompleted) {
                    window.ReaderAndroid.restoreCompleted(window.__readerRestoreEpoch || 0);
                    return;
                }
                if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.restoreCompleted) {
                    window.webkit.messageHandlers.restoreCompleted.postMessage(null);
                }
            });
        });
    },

    registerCopyText: function() {
        if (window._hoshiCopy) return;
        window._hoshiCopy = true;
        document.addEventListener('copy', function(e) {
            var text = window.getSelection() ? window.getSelection().toString() : '';
            if (text) {
                e.preventDefault();
                e.clipboardData.setData('text/plain', text);
            }
        }, true);
    },

    scanDelimiters: '。、！？…‥「」『』（）()【】〈〉《》〔〕｛｝{}［］[]・：；:;，,.─\n\r',
    sentenceDelimiters: '。！？.!?\n\r',

    isScanBoundary: function(char) {
        return /^[\s\u3000]$/.test(char) || this.scanDelimiters.includes(char);
    },

    isFurigana: function(node) {
        const el = node.nodeType === Node.TEXT_NODE ? node.parentElement : node;
        return !!el?.closest('rt, rp');
    },

    findParagraph: function(node) {
        let el = node.nodeType === Node.TEXT_NODE ? node.parentElement : node;
        return el?.closest('p, div, .glossary-content') || null;
    },

    getCaretRange: function(x, y) {
        if (document.caretPositionFromPoint) {
            const pos = document.caretPositionFromPoint(x, y);
            if (!pos) return null;
            const range = document.createRange();
            range.setStart(pos.offsetNode, pos.offset);
            range.collapse(true);
            return range;
        } else if (document.caretRangeFromPoint) {
            return document.caretRangeFromPoint(x, y);
        }
        return null;
    },

    getCharacterAtPoint: function(x, y) {
        const range = this.getCaretRange(x, y);
        if (!range || !range.startContainer) return null;
        const node = range.startContainer;
        if (node.nodeType !== Node.TEXT_NODE || this.isFurigana(node)) return null;

        const text = node.textContent;
        const caret = range.startOffset;

        // Try precise hit, then slight left/right offsets to handle character edges
        for (const offset of [caret, caret - 1]) {
            if (offset < 0 || offset >= text.length) continue;
            const charRange = document.createRange();
            charRange.setStart(node, offset);
            charRange.setEnd(node, offset + 1);
            const rects = charRange.getClientRects();
            for (const rect of rects) {
                if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
                    if (this.isScanBoundary(text[offset])) return null;
                    return { node, offset };
                }
            }
        }
        return null;
    },

    getSentence: function(startNode, startOffset) {
        const container = this.findParagraph(startNode) || document.body;
        const walker = this.createWalker(container);
        const trailingSentenceChars = '」』）】!?！？…';

        walker.currentNode = startNode;
        const partsBefore = [];
        let node = startNode;
        let limit = startOffset;

        while (node) {
            const text = node.textContent;
            let foundStart = false;
            for (let i = limit - 1; i >= 0; i--) {
                if (this.sentenceDelimiters.includes(text[i])) {
                    partsBefore.push(text.slice(i + 1, limit));
                    foundStart = true;
                    break;
                }
            }
            if (foundStart) break;
            partsBefore.push(text.slice(0, limit));
            node = walker.previousNode();
            if (node) limit = node.textContent.length;
        }

        walker.currentNode = startNode;
        const partsAfter = [];
        node = startNode;
        let start = startOffset;

        while (node) {
            const text = node.textContent;
            let foundEnd = false;
            for (let i = start; i < text.length; i++) {
                if (this.sentenceDelimiters.includes(text[i])) {
                    let end = i + 1;
                    while (end < text.length) {
                        if (!trailingSentenceChars.includes(text[end])) break;
                        end += 1;
                    }
                    partsAfter.push(text.slice(start, end));
                    foundEnd = true;
                    break;
                }
            }
            if (foundEnd) break;
            partsAfter.push(text.slice(start));
            node = walker.nextNode();
            start = 0;
        }

        return (partsBefore.reverse().join('') + partsAfter.join('')).trim();
    },

    handleTap: function(clientX, clientY) {
        const hit = this.getCharacterAtPoint(clientX, clientY);
        if (!hit) {
            this.clearSelection();
            if (window.ReaderAndroid && window.ReaderAndroid.onBackgroundTap) {
                window.ReaderAndroid.onBackgroundTap(clientX, clientY);
            }
            return false;
        }

        if (this.selectionStartNode === hit.node && this.selectionStartOffset === hit.offset) {
            this.clearSelection();
            if (window.ReaderAndroid && window.ReaderAndroid.onBackgroundTap) {
                window.ReaderAndroid.onBackgroundTap(clientX, clientY);
            }
            return false;
        }

        const container = this.findParagraph(hit.node) || document.body;
        const walker = this.createWalker(container);

        let word = '';
        let node = hit.node;
        let offset = hit.offset;
        let ranges = [];
        let reachedSentenceBreak = false;

        walker.currentNode = node;
        while (!reachedSentenceBreak && node) {
            const content = node.textContent;
            let start = offset;
            while (offset < content.length) {
                const char = content[offset];
                if (this.sentenceDelimiters.includes(char)) {
                    reachedSentenceBreak = true;
                    break;
                }
                word += char;
                offset++;
            }
            if (offset > start) ranges.push({ node: node, start: start, end: offset });
            if (reachedSentenceBreak || offset < content.length) break;
            node = walker.nextNode();
            offset = 0;
        }
        word = word.trim();

        if (word.length > 0) {
            this.clearSelection();
            this.selectionStartNode = hit.node;
            this.selectionStartOffset = hit.offset;
            this.selectionRanges = ranges;
            const sentence = this.getSentence(hit.node, hit.offset);

            // Use Hoshi's approach: calculate bounding box based ONLY on the first character
            // This prevents the popup from jumping far away when a long phrase is selected.
            let minX = clientX, minY = clientY, maxX = clientX, maxY = clientY;
            if (ranges.length > 0) {
                const first = ranges[0];
                const range = document.createRange();
                range.setStart(first.node, first.start);
                range.setEnd(first.node, first.start + 1); // Only 1 character

                const rects = Array.from(range.getClientRects());
                const rect = rects.find(r => clientX >= r.left && clientX <= r.right && clientY >= r.top && clientY <= r.bottom)
                            || range.getBoundingClientRect();

                minX = rect.left;
                minY = rect.top;
                maxX = rect.right;
                maxY = rect.bottom;
            }

            if (window.ReaderAndroid && window.ReaderAndroid.onTextSelected) {
                window.ReaderAndroid.onTextSelected(word, sentence, minX, minY, maxX - minX, maxY - minY);
                return true;
            }
        }

        this.clearSelection();
        if (window.ReaderAndroid && window.ReaderAndroid.onBackgroundTap) {
            window.ReaderAndroid.onBackgroundTap(clientX, clientY);
        }
        return false;
    },

    highlightSelection: function(charCount) {
        if (!this.selectionRanges || !this.selectionRanges.length || !CSS.highlights) return;
        var highlights = [];
        var remaining = charCount;
        for (var i = 0; i < this.selectionRanges.length; i++) {
            var r = this.selectionRanges[i];
            if (remaining <= 0) break;
            var end = r.start;
            while (end < r.end && remaining > 0) {
                var char = String.fromCodePoint(r.node.textContent.codePointAt(end));
                end += char.length;
                remaining--;
            }
            var range = document.createRange();
            range.setStart(r.node, r.start);
            range.setEnd(r.node, end);
            highlights.push(range);
        }
        CSS.highlights.set('hoshi-selection', new Highlight(...highlights));
    },

    /**
     * Return selection rects as JSON array [{x,y,width,height}, ...]
     * for painting in a native Compose overlay.  Falls back to CSS.highlight
     * if the bridge is not available.
     */
    getSelectionRects: function(charCount, startOffset) {
        if (!this.selectionRanges || !this.selectionRanges.length) return [];
        if (startOffset === undefined) startOffset = 0;
        var remaining = startOffset;
        var skipRange = -1;
        var skipEnd = 0;

        // Skip `startOffset` characters through the ranges
        for (var i = 0; i < this.selectionRanges.length; i++) {
            var r = this.selectionRanges[i];
            var avail = r.end - r.start;
            if (remaining < avail) {
                skipRange = i;
                skipEnd = r.start + remaining;
                break;
            }
            remaining -= avail;
        }
        if (skipRange < 0) return [];

        // Now build ranges from the skip point, limited to charCount
        var ranges = [];
        remaining = charCount;
        for (var i = skipRange; i < this.selectionRanges.length; i++) {
            var r = this.selectionRanges[i];
            if (remaining <= 0) break;
            var start = (i === skipRange) ? skipEnd : r.start;
            var end = start;
            while (end < r.end && remaining > 0) {
                var ch = String.fromCodePoint(r.node.textContent.codePointAt(end));
                end += ch.length;
                remaining--;
            }
            var range = document.createRange();
            range.setStart(r.node, start);
            range.setEnd(r.node, end);
            ranges.push(range);
        }

        var rects = [];
        for (var ri = 0; ri < ranges.length; ri++) {
            var clientRects = ranges[ri].getClientRects();
            for (var ci = 0; ci < clientRects.length; ci++) {
                var cr = clientRects[ci];
                rects.push({x: cr.x, y: cr.y, width: cr.width, height: cr.height});
            }
        }

        return rects;
    },

    clearSelection: function() {
        if (CSS.highlights && CSS.highlights.has('hoshi-selection')) {
            CSS.highlights.get('hoshi-selection').clear();
        }
        this.selectionRanges = null;
        this.selectionStartNode = null;
        this.selectionStartOffset = null;
    },

    registerTextSelection: function() {
        // Disabled for now as per user request
    }
};
