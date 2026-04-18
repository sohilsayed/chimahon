window.hoshiReader = {
    isRtl: false,
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
        var el = document.scrollingElement || document.documentElement;
        var ph = window.innerHeight;
        var pw = window.innerWidth;
        var vMax = el.scrollHeight - ph;
        var hMax = el.scrollWidth - pw;
        if (vMax > 1) return vMax > 0 ? window.scrollY / vMax : 0;
        if (hMax > 1) return hMax > 0 ? Math.abs(window.scrollX) / hMax : 0;
        return 0;
    },

    restoreProgress: function(progress, isRtl) {
        this.isRtl = !!isRtl;
        var el = document.scrollingElement || document.documentElement;
        var ph = window.innerHeight;
        var pw = window.innerWidth;
        var vMax = el.scrollHeight - ph;
        var hMax = el.scrollWidth - pw;
        var p = Math.min(1, Math.max(0, progress));

        console.log('[hoshi] restore: progress=' + p + ' vMax=' + vMax + ' hMax=' + hMax);

        if (vMax > 1) {
            var target = Math.round(vMax * p);
            // Align to page boundary
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
    },

    notifyRestoreComplete: function() {
        if (window.HoshiAndroid && window.HoshiAndroid.restoreCompleted) {
            window.HoshiAndroid.restoreCompleted();
            return;
        }
        if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.restoreCompleted) {
            window.webkit.messageHandlers.restoreCompleted.postMessage(null);
        }
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

    createWalker: function(rootNode) {
        const root = rootNode || document.body;
        return document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
            acceptNode: (n) => this.isFurigana(n) ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT
        });
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

        walker.currentNode = node;
        while (word.length < maxLength && node) {
            const content = node.textContent;
            while (offset < content.length && word.length < maxLength) {
                const char = content[offset];
                if (this.isScanBoundary(char)) break;
                word += char;
                offset++;
            }
            if (offset < content.length || word.length >= maxLength) break;
            node = walker.nextNode();
            offset = 0;
        }

        if (word.length > 0) {
            const sentence = this.getSentence(hit.node, hit.offset);
            if (window.HoshiAndroid && window.HoshiAndroid.onTextSelected) {
                window.HoshiAndroid.onTextSelected(word, sentence, clientX, clientY);
                return true;
            }
        }

        if (window.HoshiAndroid && window.HoshiAndroid.onBackgroundTap) {
            window.HoshiAndroid.onBackgroundTap(clientX, clientY);
        }
        return false;
    },

    registerTextSelection: function() {
        // Disabled for now as per user request
    }
};