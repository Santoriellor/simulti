import { AfterViewInit, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';

type Inv = { x: number; y: number; w: number; h: number; frame: number; type: number; alive: boolean };
type Star = { x: number; y: number; speed: number; size: number; alpha: number };

@Component({
  selector: 'app-backdrop',
  standalone: true,
  template: `<canvas #bgCanvas class="bg-canvas"></canvas>`,
  styles: [`
    :host {
      position: absolute;
      inset: 0;
      display: block;
      pointer-events: none;
      opacity: .65;
    }
    .bg-canvas {
      width: 100%;
      height: 100%;
      display: block;
    }
  `]
})
export class BackdropComponent implements AfterViewInit, OnDestroy {
  @ViewChild('bgCanvas', { static: true }) canvasRef!: ElementRef<HTMLCanvasElement>;
  private ctx!: CanvasRenderingContext2D;
  private rafId: number | null = null;
  private lastTs = 0;

  private stars: Star[] = [];
  private invaders: Inv[] = [];
  private dir = 1;
  private stepPending = false;
  private animTimer = 0;

  private resizeObs?: ResizeObserver;

  ngAfterViewInit(): void {
    this.setupCanvas();
    this.initStars(140);
    this.initInvaders();

    this.lastTs = performance.now();
    this.rafId = requestAnimationFrame(this.loop);

    // resize adaptatif
    this.resizeObs = new ResizeObserver(() => this.setupCanvas());
    this.resizeObs.observe(this.canvasRef.nativeElement.parentElement || this.canvasRef.nativeElement);
  }

  ngOnDestroy(): void {
    if (this.rafId) cancelAnimationFrame(this.rafId);
    this.rafId = null;
    this.resizeObs?.disconnect();
  }

  private setupCanvas() {
    const canvas = this.canvasRef.nativeElement;
    const parent = canvas.parentElement || canvas;
    const w = parent.clientWidth || window.innerWidth;
    const h = parent.clientHeight || window.innerHeight;
    canvas.width = Math.max(320, Math.floor(w));
    canvas.height = Math.max(320, Math.floor(h));
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('2D context non disponible');
    this.ctx = ctx;
  }

  private initStars(n: number) {
    const c = this.canvasRef.nativeElement;
    this.stars = Array.from({ length: n }, () => ({
      x: Math.random() * c.width,
      y: Math.random() * c.height,
      speed: 10 + Math.random() * 35,
      size: Math.random() * 1.2 + 0.3,
      alpha: 0.2 + Math.random() * 0.8
    }));
  }

  private initInvaders() {
    this.invaders = [];
    const c = this.canvasRef.nativeElement;
    const cols = 10, rows = 4;
    const spacingX = Math.max(24, Math.min(44, c.width / (cols + 3)));
    const spacingY = 28;
    const gridW = (cols - 1) * spacingX + 20;
    const startX = (c.width - gridW) / 2;
    const startY = Math.max(60, Math.min(160, c.height * 0.18));
    for (let r = 0; r < rows; r++) {
      for (let col = 0; col < cols; col++) {
        const x = startX + col * spacingX;
        const y = startY + r * spacingY;
        const type = r === 0 ? 0 : r < 2 ? 1 : 2;
        this.invaders.push({ x, y, w: 20, h: 14, frame: 0, type, alive: true });
      }
    }
    this.dir = 1;
    this.stepPending = false;
    this.animTimer = 0;
  }

  private loop = (ts: number) => {
    const dt = Math.min(0.033, (ts - this.lastTs) / 1000);
    this.lastTs = ts;
    this.update(dt);
    this.draw();
    this.rafId = requestAnimationFrame(this.loop);
  };

  private update(dt: number) {
    const c = this.canvasRef.nativeElement;

    // étoiles
    for (const s of this.stars) {
      s.y += s.speed * dt;
      if (s.y > c.height) {
        s.y = -5;
        s.x = Math.random() * c.width;
        s.speed = 10 + Math.random() * 35;
        s.size = Math.random() * 1.2 + 0.3;
        s.alpha = 0.2 + Math.random() * 0.8;
      }
    }

    // invaders (mouvement lent, décoratif)
    const speed = 16; // px/s
    let minX = Infinity, maxX = -Infinity;
    for (const inv of this.invaders) {
      if (!inv.alive) continue;
      minX = Math.min(minX, inv.x);
      maxX = Math.max(maxX, inv.x + inv.w);
    }
    if (!this.stepPending) {
      if (this.dir > 0 && maxX + speed * dt >= c.width - 24) this.stepPending = true;
      if (this.dir < 0 && minX - speed * dt <= 24) this.stepPending = true;
    }
    if (this.stepPending) {
      for (const inv of this.invaders) inv.y += 8;
      this.dir *= -1;
      this.stepPending = false;
    } else {
      for (const inv of this.invaders) inv.x += this.dir * speed * dt;
    }

    // animation frame
    this.animTimer += dt;
    if (this.animTimer > 0.5) {
      this.animTimer = 0;
      for (const inv of this.invaders) inv.frame = inv.frame ? 0 : 1;
    }
  }

  private draw() {
    const c = this.canvasRef.nativeElement;
    const ctx = this.ctx;

    ctx.clearRect(0, 0, c.width, c.height);

    // légère nébuleuse
    const gradient = ctx.createRadialGradient(c.width * 0.5, c.height * 0.1, 10, c.width * 0.5, c.height * 0.1, c.width * 0.8);
    gradient.addColorStop(0, 'rgba(0,255,102,0.3)');
    gradient.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, c.width, c.height);

    // étoiles
    for (const s of this.stars) {
      ctx.fillStyle = `rgba(255,255,255,${0.4 * s.alpha})`;
      ctx.fillRect(s.x, s.y, s.size, s.size);
    }

    // invaders (teinte verte atténuée)
    for (const inv of this.invaders) {
      this.drawInvader(inv);
    }
  }

  private drawInvader(inv: Inv) {
    const ctx = this.ctx;
    ctx.fillStyle = 'rgba(0,255,102,0.6)';
    const f = inv.frame;
    const pattern =
      inv.type === 0
        ? [
            '....###....',
            '...#####...',
            '..#######..',
            '..##.#.##..',
            '..#######..',
            '.#.#.#.#.#.',
          ]
        : inv.type === 1
        ? [
            '...#####...',
            '..#######..',
            '.###.#.###.',
            '###########',
            '.###.#.###.',
          ]
        : [
            '....###....',
            '..#######..',
            '.#########.',
            '.##.###.##.',
            '.#########.',
            '..#.#.#.#..',
          ];
    const scaleX = inv.w / pattern[0].length;
    const scaleY = inv.h / pattern.length;
    for (let y = 0; y < pattern.length; y++) {
      const row = pattern[y];
      for (let x = 0; x < row.length; x++) {
        if (row[x] === '#') {
          const ox = f ? (y % 2 === 0 ? 0 : 1) : (y % 2 === 0 ? 1 : 0);
          ctx.fillRect(inv.x + (x + (ox % 2)) * scaleX, inv.y + y * scaleY, Math.max(1, scaleX - 0.5), Math.max(1, scaleY - 0.5));
        }
      }
    }
  }
}
