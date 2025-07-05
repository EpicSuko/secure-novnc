import axios from 'axios';

export interface ConfigResponse {
  wsHost: string;
  wsPort: number;
  wsUrl: string;
  protocol: string;
  success: boolean;
  message: string;
  timestamp: number;
}

class ConfigService {
  private baseURL: string;

  constructor() {
    // Use relative URL for same-origin requests
    this.baseURL = '/api/vnc';
  }

  async getConfig(sessionId: string): Promise<ConfigResponse> {
    try {
      const response = await axios.get<ConfigResponse>(`${this.baseURL}/config/${sessionId}`);
      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error)) {
        if (error.response?.status === 401) {
          throw new Error('Invalid or expired session');
        } else if (error.response?.status === 404) {
          throw new Error('Configuration not found');
        } else {
          throw new Error(error.response?.data?.message || 'Failed to retrieve configuration');
        }
      }
      throw new Error('Network error. Please check your connection.');
    }
  }

  /**
   * Builds the complete WebSocket URL for VNC connection
   */
  buildWebSocketUrl(config: ConfigResponse): string {
    if (!config.wsHost || !config.wsPort) {
      throw new Error('Invalid configuration: missing host or port');
    }
    const protocol = config.protocol || 'ws';
    return `${protocol}://${config.wsHost}:${config.wsPort}${config.wsUrl}`;
  }}

export const configService = new ConfigService(); 