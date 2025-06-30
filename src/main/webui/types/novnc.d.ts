declare module '@novnc/novnc/lib/rfb' {
  export default class RFB {
    constructor(target: HTMLElement, url: string, options?: {
      credentials?: { password?: string };
      repeaterID?: string;
      shared?: boolean;
    });
    
    viewOnly: boolean;
    
    get scaleViewport(): boolean;
    set scaleViewport(scale: boolean): void;
    
    get resizeSession(): boolean;
    set resizeSession(resize: boolean): void;
    
    addEventListener(event: string, handler: (e: any) => void): void;
    disconnect(): void;
    sendCtrlAltDel(): void;
    clipboardPasteFrom(text: string): void;
  }
} 