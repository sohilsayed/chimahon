window.hoshiReader = {
    isRtl: false,
    continuousMode: false,
    ttuRegexNegated: /[^0-9A-Za-z○◯々-〇〻ぁ-ゖゝ-ゞァ-ヺー０-９Ａ-Ｚａ-ｚｦ-ﾝ\p{Radical}\p{Unified_Ideograph}]+/gimu,

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

    paginate: function(direction) {
        var el = document.scrollingElement || document.documentElement;
        var ph = window.innerHeight;
        var pw = window.innerWidth;
        var vOver = el.scrollHeight - ph > 1;
        var hOver = el.scrollWidth - pw > 1;

        if (!this._logged) {
            this._logged = true;
            console.log('[hoshi] scrollH=' + el.scrollHeight + ' scrollW=' + el.scrollWidth +
                ' innerH=' + ph + ' innerW=' + pw + ' vOver=' + vOver + ' hOver=' + hOver);
        }

        if (vOver) {
            var y = Math.round(window.scrollY);
            var maxY = el.scrollHeight - ph;
            if (direction === 'forward' && y + ph <= maxY + 1) {
                window.scrollTo(0, y + ph);
                return 'scrolled';
            }
            if (direction === 'backward' && y > 1) {
                window.scrollTo(0, Math.max(0, y - ph));
                return 'scrolled';
            }
            return 'limit';
        }

        if (hOver) {
            var x = window.scrollX;
            var maxX = el.scrollWidth - pw;
            if (this.isRtl) {
                var absX = Math.abs(x);
                if (direction === 'forward' && absX + pw <= maxX + 1) {
                    window.scrollTo(x - pw, 0);
                    return 'scrolled';
                }
                if (direction === 'backward' && absX > 1) {
                    window.scrollTo(Math.min(0, x + pw), 0);
                    return 'scrolled';
                }
            } else {
                if (direction === 'forward' && x + pw <= maxX + 1) {
                    window.scrollTo(x + pw, 0);
                    return 'scrolled';
                }
                if (direction === 'backward' && x > 1) {
                    window.scrollTo(Math.max(0, x - pw), 0);
                    return 'scrolled';
                }
            }
            return 'limit';
        }

        return 'limit';
    },

    calculateProgress: function() {
        if (!this.continuousMode) {
            var el = document.scrollingElement || document.documentElement;
            var ph = window.innerHeight;
            var pw = window.innerWidth;
            var vMax = el.scrollHeight - ph;
            var hMax = el.scrollWidth - pw;
            if (vMax > 1) return vMax > 0 ? window.scrollY / vMax : 0;
            if (hMax > 1) return hMax > 0 ? Math.abs(window.scrollX) / hMax : 0;
            return 0;
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
                // If the node is entirely above (or to the right of) the viewport, count it as explored
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
            var el = document.scrollingElement || document.documentElement;
            var ph = window.innerHeight;
            var pw = window.innerWidth;
            var vMax = el.scrollHeight - ph;
            var hMax = el.scrollWidth - pw;

            if (vMax > 1) {
                var target = Math.round(vMax * p);
                target = Math.round(target / ph) * ph;
                window.scrollTo(0, Math.min(target, vMax));
            } else if (hMax > 1) {
                var target = Math.round(hMax * p);
                target = Math.round(target / pw) * pw;
                if (this.isRtl) {
                    window.scrollTo(-Math.min(target, hMax), 0);
                } else {
                    window.scrollTo(Math.min(target, hMax), 0);
                }
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
                if (window.HoshiAndroid && window.HoshiAndroid.restoreCompleted) {
                    window.HoshiAndroid.restoreCompleted();
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
            if (window.HoshiAndroid && window.HoshiAndroid.onBackgroundTap) {
                window.HoshiAndroid.onBackgroundTap(clientX, clientY);
            }
            return false;
        }

        if (this.selectionStartNode === hit.node && this.selectionStartOffset === hit.offset) {
            this.clearSelection();
            if (window.HoshiAndroid && window.HoshiAndroid.onBackgroundTap) {
                window.HoshiAndroid.onBackgroundTap(clientX, clientY);
            }
            return false;
        }

        const container = this.findParagraph(hit.node) || document.body;
        const walker = this.createWalker(container);
        const maxLength = 40;

        let word = '';
        let node = hit.node;
        let offset = hit.offset;
        let ranges = [];

        walker.currentNode = node;
        while (word.length < maxLength && node) {
            const content = node.textContent;
            let start = offset;
            while (offset < content.length && word.length < maxLength) {
                const char = content[offset];
                if (this.isScanBoundary(char)) break;
                word += char;
                offset++;
            }
            if (offset > start) ranges.push({ node: node, start: start, end: offset });
            if (offset < content.length || word.length >= maxLength) break;
            node = walker.nextNode();
            offset = 0;
        }

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

            if (window.HoshiAndroid && window.HoshiAndroid.onTextSelected) {
                window.HoshiAndroid.onTextSelected(word, sentence, minX, minY, maxX - minX, maxY - minY);
                return true;
            }
        }

        this.clearSelection();
        if (window.HoshiAndroid && window.HoshiAndroid.onBackgroundTap) {
            window.HoshiAndroid.onBackgroundTap(clientX, clientY);
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