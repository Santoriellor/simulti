import {Component, OnDestroy, OnInit} from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import {catchError, of, Subject} from 'rxjs';
import {RouterLink} from '@angular/router';
import {environment} from '../../environments/environment';

type ScoreRow = { player: string; score: number };

@Component({
  selector: 'app-leaderboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './leaderboard.component.html',
  styleUrls: ['./leaderboard.component.css']
})
export class LeaderboardComponent implements OnInit, OnDestroy {
  loading = false;
  error: string | null = null;
  rows: ScoreRow[] = [];
  private readonly destroy$ = new Subject<void>();

  constructor(private readonly http: HttpClient) {}

  ngOnInit(): void {
    this.fetch();
  }

  fetch(): void {
    this.loading = true;
    this.error = null;
    this.http.get<ScoreRow[]>(`${environment.apiUrl}/leaderboard`)
      .pipe(
        catchError(err => {
          // Fallback d'exemple en cas d'API indisponible
          const sample: ScoreRow[] = [
            { player: 'ALICE', score: 3810 },
            { player: 'BOB', score: 2940 },
            { player: 'CHARLIE', score: 2760 }
          ];
          console.warn('Cannot fetch leaderboard: using fallback', err);
          return of(sample);
        })
      )
      .subscribe(rows => {
        this.rows = rows
          .slice()
          .sort((a, b) => b.score - a.score)
          .slice(0, 50);
        this.loading = false;
      });
  }

  trackByName(_i: number, row: ScoreRow) {
    return row.player + ':' + row.score;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
