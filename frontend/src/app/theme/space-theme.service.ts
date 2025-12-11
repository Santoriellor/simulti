import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class SpaceThemeService {
  private readonly styleId = 'space-invaders-theme';

  applyTheme(): void {
    if (document.getElementById(this.styleId)) {
      return;
    }
    const style = document.createElement('style');
    style.id = this.styleId;
    style.textContent = `
      :root {
        --si-bg: #000000;
        --si-fg: #00ff66;
        --si-fg-dim: #00cc55;
        --si-accent: #00e56e;
        --si-danger: #ff3b3b;
        --si-border: rgba(0,255,102,0.45);
        --si-shadow: 0 0 8px rgba(0,255,102,0.35), 0 0 18px rgba(0,255,102,0.15);
        --si-font: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
      }

      html, body {
        height: 100%;
        background: radial-gradient(1200px 800px at 50% -20%, rgba(0,255,102,0.2), transparent 60%) #000;
        color: var(--si-fg);
        font-family: var(--si-font);
      }

      a {
        color: var(--si-accent);
        text-decoration: none;
      }
      a:hover { text-decoration: none; }

      h1, h2, h3, h4 {
        color: var(--si-fg);
        text-shadow: 0 0 6px rgba(0,255,102,0.5);
        letter-spacing: 1px;
      }

      /* Panneaux / cartes génériques */
      .si-panel, .content, .user-info {
        background: rgba(0, 20, 0, 0.45);
        border: 2px solid var(--si-border);
        border-radius: 10px;
        box-shadow: var(--si-shadow);
        backdrop-filter: blur(2px);
      }

      /* Boutons néon */
      .btn, button, .logout-btn, .play-btn, .link-btn {
        display: inline-block;
        appearance: none;
        background: transparent;
        color: var(--si-fg);
        border: 2px solid var(--si-fg);
        padding: 10px 16px;
        border-radius: 10px;
        font-weight: 700;
        letter-spacing: 0.5px;
        box-shadow: var(--si-shadow);
        transition: transform 0.1s ease, box-shadow 0.2s ease, background-color 0.2s ease, color 0.2s ease;
        cursor: pointer;
        text-decoration: none;
      }
      .btn:hover, button:hover, .logout-btn:hover, .play-btn:hover, .link-btn:hover {
        transform: translateY(-1px);
        box-shadow: 0 0 10px rgba(0,255,102,0.6), 0 0 22px rgba(0,255,102,0.25);
        background: rgba(0,255,102,0.08);
      }
      .btn-danger, .logout-btn {
        border-color: var(--si-danger);
        color: var(--si-danger);
        box-shadow: 0 0 8px rgba(255,59,59,0.35), 0 0 18px rgba(255,59,59,0.15);
      }
      .btn-danger:hover, .logout-btn:hover {
        box-shadow: 0 0 10px rgba(255,59,59,0.6), 0 0 22px rgba(255,59,59,0.25);
        background: rgba(255,59,59,0.08);
      }

      .btn-xs {
        padding: 6px 10px;
        font-size: 12px;
        border-radius: 8px;
      }

      .btn:disabled {
        opacity: 0.45;
        cursor: not-allowed;
        border-color: #555;
        color: #555;
        background: #222;
        box-shadow: none;
        filter: grayscale(60%);
      }

      .menu-btn {
        width: 200px;
        border-color: #00ff66;
        color: #00ff66;
      }

      /* Inputs / formulaires */
      input, select, textarea {
        background: rgba(0, 20, 0, 0.6);
        border: 2px solid var(--si-border);
        color: var(--si-fg);
        padding: 10px 12px;
        border-radius: 8px;
        outline: none;
        width: 100%;
        box-shadow: inset 0 0 8px rgba(0,255,102,0.08);
      }
      input::placeholder, textarea::placeholder { color: rgba(0,255,102,0.5); }
      input:focus, select:focus, textarea:focus {
        border-color: var(--si-fg);
        box-shadow: 0 0 8px rgba(0,255,102,0.45);
      }

      /* Tables (leaderboard) */
      table.si-table {
        width: 100%;
        border-collapse: collapse;
        border: 2px solid var(--si-border);
        border-radius: 10px;
        overflow: hidden;
        box-shadow: var(--si-shadow);
        background: rgba(0, 20, 0, 0.45);
      }
      table.si-table th, table.si-table td {
        padding: 12px;
        border-bottom: 1px solid rgba(0,255,102,0.15);
      }
      table.si-table th {
        text-align: left;
        color: var(--si-fg);
      }
      table.si-table tr:hover {
        background: rgba(0,255,102,0.05);
      }

      /* Layout commun */
      .si-container {
        width: 320px;
        height: 600px;
        margin: 0 auto;
        padding: 20px;
        border-radius: 15px;
        border: 2px solid var(--si-border);
        background: rgba(0, 20, 0, 0.45);
        box-shadow: var(--si-shadow);
      }
      header.si-header {
        display: flex;
        gap: 12px;
        align-items: center;
        justify-content: space-between;
        padding: 16px 0;
        border-bottom: 2px solid var(--si-border);
        margin-bottom: 20px;
      }
      .si-actions {
        display: flex;
        gap: 10px;
        align-items: center;
        flex-wrap: wrap;
      }
    `;
    document.head.appendChild(style);
  }
}
