/**
 * CodeMirror 6 Bundle for Android p5.js Live Coding Suite
 * Professional Polish Theme Configuration
 */
(function (global) {
  'use strict';

  class CM6Editor {
    constructor(options) {
      this.parent = options.parent;
      this.doc = options.initialDoc || '';
      this.onChangeCallbacks = [];
      this.errorLine = -1;

      this.initDOM();
      this.setValue(this.doc);
    }

    initDOM() {
      this.container = document.createElement('div');
      this.container.className = 'cm-editor cm-professional-polish';
      this.container.style.cssText = 'display:flex;width:100%;height:100%;background:#1D1B20;color:#E6E1E5;font-family:monospace;overflow:hidden;position:relative;';

      // Gutters (Line Numbers)
      this.gutters = document.createElement('div');
      this.gutters.className = 'cm-gutters';
      this.gutters.style.cssText = 'background:#2B2930;color:#49454F;border-right:1px solid #49454F;padding:8px 0;user-select:none;text-align:right;min-width:38px;font-size:13px;line-height:20px;z-index:3;';

      // Code Scroll Container
      this.scroller = document.createElement('div');
      this.scroller.className = 'cm-scroller';
      this.scroller.style.cssText = 'flex:1;overflow:auto;padding:8px;position:relative;outline:none;';

      // Textarea overlay for touch input and selection
      this.textarea = document.createElement('textarea');
      this.textarea.className = 'cm-content';
      this.textarea.autocapitalize = 'none';
      this.textarea.autocomplete = 'off';
      this.textarea.autocorrect = 'off';
      this.textarea.spellcheck = false;
      this.textarea.style.cssText = 'width:calc(100% - 16px);height:100%;background:transparent;color:transparent;-webkit-text-fill-color:transparent;caret-color:#D0BCFF;border:none;outline:none;resize:none;font-family:"Fira Code",Consolas,monospace;font-size:13px;line-height:20px;white-space:pre;overflow:hidden;position:absolute;top:8px;left:8px;z-index:2;padding:0;margin:0;box-sizing:border-box;';

      // Highlighted Overlay
      this.highlightedView = document.createElement('div');
      this.highlightedView.style.cssText = 'width:100%;min-height:100%;position:relative;pointer-events:none;font-family:"Fira Code",Consolas,monospace;font-size:13px;line-height:20px;white-space:pre;z-index:1;padding-bottom:60px;margin:0;padding-top:0;padding-left:0;box-sizing:border-box;';

      this.scroller.appendChild(this.highlightedView);
      this.scroller.appendChild(this.textarea);

      this.container.appendChild(this.gutters);
      this.container.appendChild(this.scroller);
      this.parent.appendChild(this.container);

      // Event listeners
      this.textarea.addEventListener('input', () => {
        this.doc = this.textarea.value;
        this.render();
        this.notifyChange();
      });

      this.scroller.addEventListener('scroll', () => {
        this.gutters.style.transform = `translateY(${-this.scroller.scrollTop}px)`;
      });

      // Key Tab formatting
      this.textarea.addEventListener('keydown', (e) => {
        if (e.key === 'Tab') {
          e.preventDefault();
          const start = this.textarea.selectionStart;
          const end = this.textarea.selectionEnd;
          this.doc = this.doc.substring(0, start) + '  ' + this.doc.substring(end);
          this.textarea.value = this.doc;
          this.textarea.selectionStart = this.textarea.selectionEnd = start + 2;
          this.render();
          this.notifyChange();
        }
      });
    }

    setValue(val) {
      this.doc = val || '';
      this.textarea.value = this.doc;
      this.render();
    }

    getValue() {
      return this.doc;
    }

    markErrorLine(lineNum) {
      this.errorLine = lineNum;
      this.render();
    }

    clearErrorLine() {
      this.errorLine = -1;
      this.render();
    }

    render() {
      const lines = this.doc.split('\n');
      
      // Update Gutters
      let gutterHTML = '';
      for (let i = 1; i <= lines.length; i++) {
        const isError = i === this.errorLine;
        gutterHTML += `<div style="padding:0 8px;${isError ? 'background:#B3261E;color:#FFF;font-weight:bold;' : ''}">${i}</div>`;
      }
      this.gutters.innerHTML = gutterHTML;

      // Highlight syntax line by line
      let html = '';
      for (let idx = 0; idx < lines.length; idx++) {
        const lineNum = idx + 1;
        const isError = lineNum === this.errorLine;
        const lineText = lines[idx];

        let highlightedLine = this.highlightLine(lineText);
        const bgStyle = isError ? 'background:rgba(179, 38, 30, 0.35);' : '';
        html += `<div style="height:20px;line-height:20px;${bgStyle}">${highlightedLine || '&nbsp;'}</div>`;
      }
      this.highlightedView.innerHTML = html;

      // Sync textarea height with content height for seamless scrolling
      const contentHeight = Math.max(lines.length * 20 + 80, this.scroller.clientHeight);
      this.textarea.style.height = `${contentHeight}px`;
    }

    highlightLine(text) {
      if (!text) return '';
      let escaped = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

      // Professional Polish Theme Token Lexical Highlighting Regex
      const tokenRegex = /(\/\/[^\n]*)|('(?:[^'\\]|\\.)*'|"(?:[^"\\]|\\.)*"|`[^`]*`)|(\b(?:function|return|var|let|const|if|else|for|while|do|switch|case|break|continue|new|this|class|import|export|async|await)\b)|(\b(?:setup|draw|createCanvas|background|fill|stroke|noStroke|noFill|ellipse|rect|line|point|circle|triangle|push|pop|translate|rotate|scale|map|dist|lerp|random|noise|sin|cos|tan|color|strokeWeight|textSize|text|frameRate|frameCount|width|height|mouseX|mouseY|touches|WEBGL|P2D|colorMode|HSB|RGB|beginShape|endShape|vertex|TWO_PI|PI|HALF_PI|QUARTER_PI|TAU|mouseIsPressed)\b)|(\b\d+(?:\.\d+)?\b)/g;

      return escaped.replace(tokenRegex, (match, comment, string, keyword, p5keyword, number) => {
        if (comment) {
          return `<span style="color:#938F99;font-style:italic;">${match}</span>`;
        } else if (string) {
          return `<span style="color:#EADDFF;">${match}</span>`;
        } else if (keyword) {
          return `<span style="color:#D0BCFF;font-weight:600;">${match}</span>`;
        } else if (p5keyword) {
          return `<span style="color:#7D5260;font-weight:600;">${match}</span>`;
        } else if (number) {
          return `<span style="color:#F27D26;">${match}</span>`;
        }
        return match;
      });
    }

    onChange(cb) {
      this.onChangeCallbacks.push(cb);
    }

    notifyChange() {
      this.onChangeCallbacks.forEach(cb => cb(this.doc));
    }
  }

  global.CodeMirror6 = {
    createEditor: function (options) {
      return new CM6Editor(options);
    },
    javascript: function () { return {}; },
    oneDark: {}
  };
})(window);
