export interface GameRoom {
  roomId: string;
  roomName: string;
  playerIds: string[];
  status: 'waiting' | 'started';
  maxPlayer: bigint;
  wave: number
  startedAt: null | Date;
  endedAt: null | Date;
  hostId: string;
}
