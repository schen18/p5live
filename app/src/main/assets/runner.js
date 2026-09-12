/**
 * p5.js Live Coding Suite - Runner, Dual Microphone Audio Pipeline & Native Android Bridge
 */

// Global Audio Reactive State Store
window._currentAudioLevel = 0;
window._currentAudioSpectrum = new Array(16).fill(0);

window.updateAudioData = function (volume, spectrumArray) {
  if (typeof volume === 'number') {
    window._currentAudioLevel = volume;
  }
  if (Array.isArray(spectrumArray)) {
    window._currentAudioSpectrum = spectrumArray;
  }
};

// Default Interactive Generative Touch-Reactive Particle System Sketch
const defaultSketchCode = `// Interactive Generative Particle Swarm
let particles = [];
let colorHue = 0;

function setup() {
  createCanvas(windowWidth, windowHeight);
  colorMode(HSB, 360, 100, 100, 1);
  
  // Initialize particles
  for (let i = 0; i < 60; i++) {
    particles.push(new Particle(random(width), random(height)));
  }
}

function draw() {
  // Semi-transparent background for glowing trail effect
  background(15, 0.15);
  colorHue = (colorHue + 0.5) % 360;

  let targetX = mouseIsPressed ? mouseX : width / 2 + cos(frameCount * 0.03) * 120;
  let targetY = mouseIsPressed ? mouseY : height / 2 + sin(frameCount * 0.03) * 120;

  // Draw interactive target ripple
  noFill();
  stroke((colorHue + 180) % 360, 80, 100, 0.4);
  strokeWeight(2);
  circle(targetX, targetY, 30 + sin(frameCount * 0.1) * 10);

  for (let i = 0; i < particles.length; i++) {
    particles[i].update(targetX, targetY);
    particles[i].display();

    // Connect nearby particles with glowing lines
    for (let j = i + 1; j < particles.length; j++) {
      let d = dist(particles[i].x, particles[i].y, particles[j].x, particles[j].y);
      if (d < 80) {
        stroke(colorHue, 70, 100, map(d, 0, 80, 0.6, 0));
        strokeWeight(1);
        line(particles[i].x, particles[i].y, particles[j].x, particles[j].y);
      }
    }
  }
}

class Particle {
  constructor(x, y) {
    this.x = x;
    this.y = y;
    this.vx = random(-2, 2);
    this.vy = random(-2, 2);
    this.size = random(6, 12);
    this.hue = random(360);
  }

  update(tx, ty) {
    // Attraction force towards target
    let dx = tx - this.x;
    let dy = ty - this.y;
    let d = sqrt(dx * dx + dy * dy);
    if (d > 0) {
      this.vx += (dx / d) * 0.15;
      this.vy += (dy / d) * 0.15;
    }

    this.vx *= 0.96; // Friction
    this.vy *= 0.96;

    this.x += this.vx;
    this.y += this.vy;

    this.hue = (this.hue + 1) % 360;
  }

  display() {
    noStroke();
    fill(this.hue, 80, 100, 0.9);
    circle(this.x, this.y, this.size);
  }
}
`;

let editor = null;
let currentSketch = null;
let fpsReportTimer = null;

// AudioIn Class Definition
class AudioInClass {
  constructor() {
    this.enabled = false;
    this.audioCtx = null;
    this.analyser = null;
    this.dataArray = null;
  }

  async start(callback) {
    this.enabled = true;

    // 1. Trigger Native Android Microphone Capture
    if (window.AndroidBridge && window.AndroidBridge.startMic) {
      window.AndroidBridge.startMic();
    }

    // 2. Web Audio API getUserMedia Fallback
    try {
      if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia && !this.analyser) {
        const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
        const AudioContextClass = window.AudioContext || window.webkitAudioContext;
        if (AudioContextClass) {
          this.audioCtx = new AudioContextClass();
          if (this.audioCtx.state === 'suspended') {
            await this.audioCtx.resume();
          }
          const source = this.audioCtx.createMediaStreamSource(stream);
          this.analyser = this.audioCtx.createAnalyser();
          this.analyser.fftSize = 64;
          source.connect(this.analyser);
          this.dataArray = new Uint8Array(this.analyser.frequencyBinCount);
        }
      }
    } catch (err) {
      console.log('Web Audio getUserMedia fallback:', err.message);
    }

    if (callback) callback();
  }

  stop() {
    this.enabled = false;
    if (window.AndroidBridge && window.AndroidBridge.stopMic) {
      window.AndroidBridge.stopMic();
    }
    if (this.audioCtx) {
      try { this.audioCtx.close(); } catch (e) {}
      this.audioCtx = null;
      this.analyser = null;
    }
  }

  getLevel() {
    if (!this.enabled) {
      this.start();
    }

    // Web Audio Analyser calculation
    if (this.analyser && this.dataArray) {
      this.analyser.getByteFrequencyData(this.dataArray);
      let sum = 0;
      for (let i = 0; i < this.dataArray.length; i++) {
        sum += this.dataArray[i];
      }
      const webAudioVol = (sum / this.dataArray.length) / 255.0;
      if (webAudioVol > 0.01) {
        return webAudioVol;
      }
    }

    // Native Android Bridge calculation
    if (window.AndroidBridge && window.AndroidBridge.getAudioVolume) {
      try {
        const vol = window.AndroidBridge.getAudioVolume();
        if (typeof vol === 'number' && vol > 0) return vol;
      } catch (e) {}
    }

    return window._currentAudioLevel || 0;
  }

  getSpectrum() {
    if (!this.enabled) {
      this.start();
    }

    // Web Audio Analyser spectrum calculation
    if (this.analyser && this.dataArray) {
      this.analyser.getByteFrequencyData(this.dataArray);
      const spec = [];
      const numBands = 16;
      const step = Math.floor(this.dataArray.length / numBands) || 1;
      let hasWebAudioData = false;

      for (let i = 0; i < numBands; i++) {
        const val = (this.dataArray[i * step] || 0) / 255.0;
        spec.push(val);
        if (val > 0.01) hasWebAudioData = true;
      }

      if (hasWebAudioData) return spec;
    }

    // Native Android Bridge spectrum
    if (window.AndroidBridge && window.AndroidBridge.getAudioSpectrum) {
      try {
        const jsonStr = window.AndroidBridge.getAudioSpectrum();
        if (jsonStr) {
          const parsed = JSON.parse(jsonStr);
          if (Array.isArray(parsed) && parsed.length > 0) return parsed;
        }
      } catch (e) {}
    }

    return window._currentAudioSpectrum || new Array(16).fill(0);
  }
}

// Bind AudioIn globally
window.AudioIn = AudioInClass;
if (typeof p5 !== 'undefined') {
  p5.AudioIn = AudioInClass;
}

// Initialize CodeMirror 6 Editor and Bridge Pipeline
window.addEventListener('DOMContentLoaded', () => {
  if (typeof p5 !== 'undefined') {
    p5.AudioIn = AudioInClass;
  }

  const mount = document.getElementById('editor-mount');

  if (window.CodeMirror6) {
    editor = window.CodeMirror6.createEditor({
      parent: mount,
      initialDoc: defaultSketchCode
    });

    let debounceTimer = null;
    editor.onChange((newCode) => {
      clearTimeout(debounceTimer);
      debounceTimer = setTimeout(() => {
        window.runSketch();
      }, 800);
    });
  }

  const originalLog = console.log;
  const originalError = console.error;

  console.log = function (...args) {
    originalLog.apply(console, args);
    const msg = args.map(a => typeof a === 'object' ? JSON.stringify(a) : a).join(' ');
    if (window.AndroidBridge && window.AndroidBridge.logFromJS) {
      window.AndroidBridge.logFromJS('[LOG] ' + msg);
    }
  };

  console.error = function (...args) {
    originalError.apply(console, args);
    const msg = args.map(a => typeof a === 'object' ? JSON.stringify(a) : a).join(' ');
    if (window.AndroidBridge && window.AndroidBridge.logFromJS) {
      window.AndroidBridge.logFromJS('[ERROR] ' + msg);
    }
  };

  window.onerror = function (msg, url, lineNo, columnNo, error) {
    const errorText = error ? error.message : msg;
    const lineNumber = lineNo || 0;
    
    showErrorBanner(`Line ${lineNumber}: ${errorText}`);
    
    if (editor && lineNumber > 0) {
      editor.markErrorLine(lineNumber);
    }

    if (window.AndroidBridge && window.AndroidBridge.onJSError) {
      window.AndroidBridge.onJSError(errorText, lineNumber);
    }
    return true;
  };

  window.runSketch();

  if (window.AndroidBridge && window.AndroidBridge.onSketchReady) {
    window.AndroidBridge.onSketchReady();
  }
});

// Dynamic p5 Instance Mode Evaluator
window.runSketch = function (codeOverride) {
  hideErrorBanner();
  if (editor) editor.clearErrorLine();

  const code = codeOverride || (editor ? editor.getValue() : defaultSketchCode);

  if (fpsReportTimer) {
    clearInterval(fpsReportTimer);
    fpsReportTimer = null;
  }

  if (currentSketch) {
    try {
      currentSketch.remove();
      currentSketch = null;
    } catch (e) {
      console.warn('Error removing sketch instance:', e);
    }
  }

  const canvasContainer = document.getElementById('canvas-container');
  canvasContainer.innerHTML = '';

  try {
    const sketchWrapper = function (p) {
      p.windowWidth = canvasContainer.clientWidth || window.innerWidth;
      p.windowHeight = canvasContainer.clientHeight || window.innerHeight;

      // Attach AudioIn and p5 constructor reference to p5 instance context
      p.p5 = window.p5;
      p.AudioIn = window.AudioIn || (typeof p5 !== 'undefined' ? p5.AudioIn : AudioInClass);
      p.getAudioLevel = function () {
        const ai = new (p.AudioIn || window.AudioIn)();
        return ai.getLevel();
      };
      p.getAudioSpectrum = function () {
        const ai = new (p.AudioIn || window.AudioIn)();
        return ai.getSpectrum();
      };

      const userCodeFunc = new Function('p', `
        with (p) {
          let AudioIn = p.AudioIn || window.AudioIn;
          let getAudioLevel = p.getAudioLevel;
          let getAudioSpectrum = p.getAudioSpectrum;

          ${code}
          
          if (typeof setup === 'function') p.setup = setup;
          if (typeof draw === 'function') p.draw = draw;
          if (typeof mousePressed === 'function') p.mousePressed = mousePressed;
          if (typeof mouseDragged === 'function') p.mouseDragged = mouseDragged;
          if (typeof mouseReleased === 'function') p.mouseReleased = mouseReleased;
          if (typeof touchStarted === 'function') p.touchStarted = touchStarted;
          if (typeof touchEnded === 'function') p.touchEnded = touchEnded;
        }
      `);

      userCodeFunc(p);
    };

    currentSketch = new p5(sketchWrapper, 'canvas-container');

    fpsReportTimer = setInterval(() => {
      if (currentSketch && typeof currentSketch.frameRate === 'function') {
        const fps = currentSketch.frameRate();
        if (window.AndroidBridge && window.AndroidBridge.onFpsReport) {
          window.AndroidBridge.onFpsReport(fps);
        }
      }
    }, 1000);

    if (window.AndroidBridge && window.AndroidBridge.logFromJS) {
      window.AndroidBridge.logFromJS('Sketch evaluated successfully.');
    }
  } catch (err) {
    const errorMsg = err.message || 'Unknown evaluation error';
    let lineNum = 0;

    if (err.stack) {
      const match = err.stack.match(/<anonymous>:(\d+):(\d+)/);
      if (match) lineNum = parseInt(match[1], 10) - 6;
    }

    showErrorBanner(`Compilation Error: ${errorMsg}`);
    if (editor && lineNum > 0) editor.markErrorLine(lineNum);

    if (window.AndroidBridge && window.AndroidBridge.onJSError) {
      window.AndroidBridge.onJSError(errorMsg, lineNum);
    }
  }
};

window.stopSketch = function () {
  if (fpsReportTimer) {
    clearInterval(fpsReportTimer);
    fpsReportTimer = null;
  }
  if (currentSketch) {
    currentSketch.noLoop();
  }
  if (window.AndroidBridge && window.AndroidBridge.stopMic) {
    window.AndroidBridge.stopMic();
  }
  if (window.AndroidBridge && window.AndroidBridge.onFpsReport) {
    window.AndroidBridge.onFpsReport(0);
  }
};

window.resetSketch = function () {
  window.runSketch();
};

window.getCode = function () {
  return editor ? editor.getValue() : defaultSketchCode;
};

window.setCode = function (newCode) {
  if (editor) {
    editor.setValue(newCode);
    window.runSketch();
  }
};

window.setCodeFromBase64 = function (base64Str) {
  try {
    const binaryString = atob(base64Str);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
      bytes[i] = binaryString.charCodeAt(i);
    }
    const decodedCode = new TextDecoder('utf-8').decode(bytes);
    window.setCode(decodedCode);
  } catch (e) {
    console.error('Failed to decode Base64 code:', e);
  }
};

window.toggleEditorVisibility = function (visible) {
  const container = document.getElementById('editor-container');
  if (visible === undefined) {
    container.classList.toggle('collapsed');
  } else if (visible) {
    container.classList.remove('collapsed');
  } else {
    container.classList.add('collapsed');
  }
};

window.exportCanvasData = function () {
  const canvas = document.querySelector('canvas');
  if (canvas) {
    const dataUrl = canvas.toDataURL('image/png');
    if (window.AndroidBridge && window.AndroidBridge.onCanvasExported) {
      window.AndroidBridge.onCanvasExported(dataUrl);
    }
    return dataUrl;
  }
  return null;
};

function showErrorBanner(msg) {
  const banner = document.getElementById('error-banner');
  const msgEl = document.getElementById('error-message');
  if (banner && msgEl) {
    msgEl.innerText = msg;
    banner.style.display = 'block';
  }
}

function hideErrorBanner() {
  const banner = document.getElementById('error-banner');
  if (banner) banner.style.display = 'none';
}
