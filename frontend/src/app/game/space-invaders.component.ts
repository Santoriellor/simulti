import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../services/auth.service';
import { environment } from '../../environments/environment';
import {ActivatedRoute, Router} from '@angular/router';
import { take } from 'rxjs';

interface GameState {
  players: any[];
  invaders: any[];
  invaderBullets: any[];
  shields: any[];
  ufo: any | null;
  level: number;
  gameOver?: boolean;
}

// ===== PIXEL SPRITES (CLASSIC SPACE INVADERS) =====

// Player cannon (11 × 8)
const PLAYER_SPRITE = [
  "00000111000",
  "00011111100",
  "00111111110",
  "01111111111",
  "11111111111",
  "11111111111",
  "00100100100",
  "00100100100"
];

// Invader type 1 (squid) – 2 frames
const INVADER1_A = [
  "0011001100",
  "0001111000",
  "0111111110",
  "1111111111",
  "1101101101",
  "1100000111",
  "0001100000",
  "0010010000"
];

const INVADER1_B = [
  "0011001100",
  "0001111000",
  "0111111110",
  "1111111111",
  "1101101101",
  "1100000111",
  "0010010010",
  "0100000001"
];

// Invader type 2 (crab)
const INVADER2_A = [
  "00111100",
  "01111110",
  "11111111",
  "11011011",
  "11111111",
  "00100100",
  "01000010",
  "10000001"
];

const INVADER2_B = [
  "00111100",
  "01111110",
  "11111111",
  "11011011",
  "11111111",
  "01001010",
  "10000001",
  "00000000"
];

// Invader type 3 (octopus)
const INVADER3_A = [
  "00111100",
  "01111110",
  "11111111",
  "11000011",
  "11000011",
  "11111111",
  "01100110",
  "11000011"
];

const INVADER3_B = [
  "00111100",
  "01111110",
  "11111111",
  "11000011",
  "11011011",
  "01100110",
  "00111100",
  "00011000"
];

// UFO (simple)
const UFO_SPRITE = [
  "0011111000",
  "0111111110",
  "1111111111",
  "1111111111",
  "0111111110",
  "0011111000"
];

// Shields (16 × 8 mask)
const SHIELD_MASK = [
  "0001111111111000",
  "0111111111111110",
  "1111111111111111",
  "1111111111111111",
  "1111111111111111",
  "1111111111111111",
  "1111111111111111",
  "0011111111111100"
];

@Component({
  selector: 'app-space-invaders-online',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './space-invaders.component.html',
  styleUrls: ['./space-invaders.component.css']
})
export class SpaceInvadersComponent implements OnInit, OnDestroy {
  @ViewChild('gameCanvas', { static: true }) canvasRef!: ElementRef<HTMLCanvasElement>;

  private ctx!: CanvasRenderingContext2D;
  private ws!: WebSocket;

  private token: string | null = null;
  private currentUserId: string | null = null;
  private roomId: string | null = null;

  private animationTick = 0;

  private virtualWidth = 480;
  private virtualHeight = 600;
  private dpr = Math.max(1, Math.floor(window.devicePixelRatio || 1));

  // --- Default full state ---
  state: GameState = {
    players: [],
    invaders: [],
    invaderBullets: [],
    shields: [],
    ufo: null,
    level: 1,
    gameOver: false
  };

  constructor(
    private readonly authService: AuthService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.token = this.authService.getToken();
    this.currentUserId = this.authService.getCurrentUser()?.id ?? null;

    const canvas = this.canvasRef.nativeElement;
    this.ctx = canvas.getContext('2d')!;
    this.resizeCanvas();

    this.route.queryParams.pipe(take(1)).subscribe(params => {
      this.roomId = params['roomId'];
      this.connectWebSocket();
    });

    globalThis.addEventListener('keydown', this.handleKeyDown);
    globalThis.addEventListener('keyup', this.handleKeyUp);

    // optional: handle window resize
    window.addEventListener('resize', this.onResize);
  }

  // ----- UI helpers for template (HUD) -----
  get mePlayer() {
    if (!this.state?.players?.length) return null;
    return (
      this.state.players.find(p => p.userId === this.currentUserId) ??
      this.state.players[0] ?? null
    );
  }

  get otherPlayer() {
    if (!this.state?.players?.length) return null;
    const others = this.state.players.filter(p => p.userId !== this.currentUserId);
    return others[0] ?? null;
  }

  getArray(n: number) {
    const count = Math.max(0, Math.floor(n || 0));
    return Array(count).fill(0);
  }

  private onResize = () => {
    this.resizeCanvas();
    this.draw(); // redraw everything
  };

  private resizeCanvas() {
    const canvas = this.canvasRef.nativeElement;
    // Ensure CSS size fits container, preserve aspect ratio from CSS
    canvas.style.width = '100%';
    canvas.style.height = 'auto';

    // Match backing store to DPR for crisp rendering
    this.dpr = Math.max(1, Math.floor(window.devicePixelRatio || 1));
    canvas.width = this.virtualWidth * this.dpr;
    canvas.height = this.virtualHeight * this.dpr;

    // Set transform so we can draw in virtual units
    this.ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
  }

  private connectWebSocket() {
    if (!this.token || !this.roomId) {
      console.warn('Cannot connect WebSocket: missing token or roomId');
      return;
    }

    const websocketUrl =
      `${environment.wsUrl}/ws/space-invaders?token=${this.token}&roomId=${this.roomId}`;

    this.ws = new WebSocket(websocketUrl);

    this.ws.onmessage = (event) => {
      let data;

      try {
        data = JSON.parse(event.data);
      } catch {
        return;
      }

      if (data.type === 'state') {
        this.state = {
          players: data.payload.players ?? [],
          invaders: data.payload.invaders ?? [],
          invaderBullets: data.payload.invaderBullets ?? [],
          shields: data.payload.shields ?? [],
          ufo: data.payload.ufo ?? null,
          level: data.payload.level ?? 1,
          gameOver: data.payload.gameOver ?? false
        };
        this.draw();
      }
    };

    this.ws.onclose = () => console.warn('WebSocket disconnected');
  }

  // Input handling --------------------

  private readonly keys = { left: false, right: false, fire: false };

  private readonly handleKeyDown = (e: KeyboardEvent) => {
    // ESC to quit room and return to waiting room
    if (e.key === 'Escape') {
      this.quitRoomAndNavigateBack();
      return;
    }

    if (this.isMeAlive() && !this.isOverallGameOver()) {
      if (e.key === 'ArrowLeft') this.keys.left = true;
      if (e.key === 'ArrowRight') this.keys.right = true;
      if (e.key === ' ' || e.code === 'Space') this.keys.fire = true;
    }
    this.sendInput();
  };

  private readonly handleKeyUp = (e: KeyboardEvent) => {
    if (e.key === 'ArrowLeft') this.keys.left = false;
    if (e.key === 'ArrowRight') this.keys.right = false;
    if (e.key === ' ' || e.code === 'Space') this.keys.fire = false;
    this.sendInput();
  };

  private sendInput() {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      const payload = (this.isMeAlive() && !this.isOverallGameOver())
        ? this.keys
        : { left: false, right: false, fire: false };
      this.ws.send(JSON.stringify({ type: 'input', payload }));
    }
  }

  private isMeAlive(): boolean {
    const me = this.mePlayer;
    if (!me) return false;
    const lives = Number(me.lives ?? 0);
    return lives > 0;
  }

  private isOverallGameOver(): boolean {
    if (this.state?.gameOver) return true;
    const players = this.state?.players ?? [];
    if (players.length === 0) return false;
    // consider game over if all players have 0 lives
    const allDead = players.every(p => (Number(p?.lives ?? 0) <= 0));
    return allDead;
  }

  private quitRoomAndNavigateBack() {
    try {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify({ type: 'quit' }));
        // close socket locally to stop incoming messages
        this.ws.close();
      }
    } catch (e) {
      // ignore
    } finally {
      // navigate back to waiting / home
      this.router.navigate(['/waitingRoom']);
    }
  }

  // DRAWING ------------------------------

  private drawSprite(
    sprite: string[],
    x: number,
    y: number,
    scale: number,
    color: string = 'white'
  ) {
    this.ctx.fillStyle = color;
    for (let row = 0; row < sprite.length; row++) {
      for (let col = 0; col < sprite[row].length; col++) {
        if (sprite[row][col] === '1') {
          this.ctx.fillRect(
            x + col * scale,
            y + row * scale,
            scale,
            scale
          );
        }
      }
    }
  }

  private draw() {
    const ctx = this.ctx;
    const canvas = this.canvasRef.nativeElement;

    // Clear canvas in virtual coordinates (transform already set to DPR)
    ctx.save();
    ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    ctx.clearRect(0, 0, this.virtualWidth, this.virtualHeight);
    ctx.restore();
    ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);

    this.animate();

    const drawScaled = (x: number, y: number) => ({ x, y }); // draw in virtual units
    const scaleSprite = (size: number) => size; // sprite size is in virtual units

    // === Draw invaders ===
    for (const inv of this.state.invaders || []) {
      if (!inv.alive) continue;

      const spriteA = inv.type === 1 ? INVADER1_A : inv.type === 2 ? INVADER2_A : INVADER3_A;
      const spriteB = inv.type === 1 ? INVADER1_B : inv.type === 2 ? INVADER2_B : INVADER3_B;
      const frame = this.animationTick % 20 < 10 ? spriteA : spriteB;

      const pos = drawScaled(inv.x, inv.y);
      this.drawSprite(frame, pos.x, pos.y, scaleSprite(2), 'lime');
    }

    // === Draw UFO ===
    if (this.state.ufo) {
      const pos = drawScaled(this.state.ufo.x, this.state.ufo.y);
      this.drawSprite(UFO_SPRITE, pos.x, pos.y, scaleSprite(2), 'magenta');
    }

    // === Draw shields ===
    for (const shield of this.state.shields || []) {
      if (shield.hp <= 0) continue;
      const chunkW = (shield.w / SHIELD_MASK[0].length);
      const chunkH = (shield.h / SHIELD_MASK.length);

      ctx.fillStyle = '#3ea86b';
      SHIELD_MASK.forEach((row, r) => {
        row.split('').forEach((pix, c) => {
          if (pix === "1") {
            const posX = shield.x + c * chunkW;
            const posY = shield.y + r * chunkH;
            ctx.fillRect(posX, posY, chunkW, chunkH);
          }
        });
      });
    }

    // === Draw players ===
    for (const player of this.state.players || []) {
      const pos = drawScaled(player.x, player.y);
      const scale = scaleSprite(2);
      const isMe = player.userId === this.currentUserId;
      this.drawSprite(PLAYER_SPRITE, pos.x, pos.y, scale, isMe ? '#00FF00' : '#3399FF');

      // Name
      ctx.fillStyle = '#FFFFFF';
      ctx.font = `${10}px Arial`;
      ctx.fillText(player.username, pos.x, pos.y - 4);

      // Player shot
      if (player.shot) {
        ctx.fillStyle = 'yellow';
        const shotPos = drawScaled(player.shot.x + 2, player.shot.y);
        ctx.fillRect(shotPos.x, shotPos.y, 2, player.shot.h);
      }
    }

    // === Draw invader bullets ===
    ctx.fillStyle = 'red';
    for (const b of this.state.invaderBullets || []) {
      const pos = drawScaled(b.x, b.y);
      ctx.fillRect(pos.x, pos.y, 3, b.h);
    }

    // === Big GAME OVER overlay ===
    if (this.isOverallGameOver()) {
      ctx.save();
      ctx.fillStyle = 'rgba(0,0,0,0.5)';
      ctx.fillRect(0, 0, this.virtualWidth, this.virtualHeight);

      ctx.fillStyle = '#FF3333';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.font = `bold ${48}px Arial`;
      ctx.fillText('GAME OVER', this.virtualWidth / 2, this.virtualHeight / 2);
      ctx.restore();
    }
  }

  private animate() {
    this.animationTick++;
  }

  ngOnDestroy(): void {
    this.ws?.close();
    globalThis.removeEventListener('keydown', this.handleKeyDown);
    globalThis.removeEventListener('keyup', this.handleKeyUp);
    window.removeEventListener('resize', this.onResize);
  }
}
