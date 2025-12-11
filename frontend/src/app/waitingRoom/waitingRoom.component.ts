import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { Subject, takeUntil } from 'rxjs';
import { GameRoom } from '../models/game-room.model';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../services/auth.service';

@Component({
  selector: 'app-waiting-room',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './waitingRoom.component.html',
  styleUrls: ['./waitingRoom.component.css']
})
export class WaitingRoomComponent implements OnInit, OnDestroy {
  newRoomName: string = '';
  rooms: GameRoom[] = [];
  selectedRoomId: string | null = null;
  private readonly destroy$ = new Subject<void>();
  private eventSource: EventSource | null = null;

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
    private readonly auth: AuthService
  ) {}

  ngOnInit(): void {
    this.loadRooms();
    this.startRoomsSse();
  }

  loadRooms(): void {
    this.http.get<GameRoom[]>(`${environment.apiUrl}/rooms`).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: rooms => {
        // Map API response to your GameRoom interface
        this.rooms = rooms.map(r => ({
          roomId: r.roomId,
          roomName: r.roomName,
          playerIds: r.playerIds,
          status: r.status.toLowerCase() as 'waiting' | 'started',
          maxPlayer: BigInt(r.maxPlayer),
          wave: r.wave,
          startedAt: r.startedAt,
          endedAt: r.endedAt,
          hostId: ''
        }));
        console.log("rooms", this.rooms);
      },
      error: err => console.error('Cannot load rooms', err)
    });
  }

  private startRoomsSse(): void {
    const token = this.auth.getToken();
    if (!token) return;

    const url = `${environment.apiUrl}/rooms/stream?token=${encodeURIComponent(token)}`;
    this.eventSource = new EventSource(url);

    this.eventSource.onmessage = (evt) => {
      // Default event name => we expect named events; ignore generic
    };

    // Handle named events
    const bind = (eventName: string, handler: (data: any) => void) => {
      this.eventSource?.addEventListener(eventName, (evt: MessageEvent) => {
        try {
          const parsed = JSON.parse(evt.data);
          handler(parsed);
        } catch {
          // ignore
        }
      });
    };

    bind('room.created', (e) => this.upsertRoom(e.payload as GameRoom));
    bind('room.updated', (e) => this.upsertRoom(e.payload as GameRoom));
    bind('room.started', (e) => this.upsertRoom(e.payload as GameRoom));
    bind('room.deleted', (e) => {
      const id = (e.payload as string) || (e.payload?.roomId as string);
      if (!id) return;
      this.rooms = this.rooms.filter(r => r.roomId !== id);
    });

    this.eventSource.onerror = () => {
      // Try to reconnect with a simple backoff
      this.eventSource?.close();
      this.eventSource = null;
      setTimeout(() => this.startRoomsSse(), 2000);
    };
  }

  private upsertRoom(apiRoom: any): void {
    if (!apiRoom) return;
    const mapped: GameRoom = {
      roomId: apiRoom.roomId,
      roomName: apiRoom.roomName,
      playerIds: apiRoom.playerIds ?? [],
      status: (apiRoom.status || 'waiting').toLowerCase() as 'waiting' | 'started',
      maxPlayer: BigInt(apiRoom.maxPlayer ?? 2),
      wave: apiRoom.wave ?? 0,
      startedAt: apiRoom.startedAt ?? null,
      endedAt: apiRoom.endedAt ?? null,
      hostId: ''
    };

    const idx = this.rooms.findIndex(r => r.roomId === mapped.roomId);
    if (idx === -1) {
      // Only show waiting rooms in this list if that is the intended behavior
      this.rooms = [...this.rooms, mapped];
    } else {
      const copy = this.rooms.slice();
      copy[idx] = mapped;
      this.rooms = copy;
    }
  }

  createRoom(): void {
    const roomName = this.newRoomName.trim();
    if (!roomName) return;
    const body = { name: roomName };

    this.http.post<GameRoom>(`${environment.apiUrl}/rooms`, body).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: room => {
        this.rooms.push(room);
        this.newRoomName = '';
        this.loadRooms();
      },
      error: err => console.error('Cannot create room', err)
    });
  }

  joinRoom(roomId: string): void {
    this.http.post(`${environment.apiUrl}/rooms/${roomId}/join`, {}).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: () => {
        this.selectedRoomId = roomId;
        this.router.navigate(['/game/space-invaders'], { queryParams: { roomId } });
      },
      error: err => console.error('Cannot join room', err)
    });
  }

  deleteRoom(roomId: string): void {
    this.http.delete(`${environment.apiUrl}/rooms/${roomId}/delete`, {}).pipe(
      takeUntil(this.destroy$)
    ).subscribe({
      next: () => {
        this.rooms = this.rooms.filter(room => room.roomId !== roomId);
        console.log("Deleted room: ", roomId);
      },
      error: err => console.error('Cannot delete room', err)
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
  }
}
