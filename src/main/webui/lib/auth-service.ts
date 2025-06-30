import axios from 'axios';

export interface AuthRequest {
  username: string;
  preAuthPassword: string;
}

export interface AuthResponse {
  sessionId: string;
  wsUrl: string;
  success: boolean;
  message: string;
  userId: string;
  timestamp: number;
}

export interface SessionResponse {
  valid: boolean;
  message: string;
  wsUrl: string;
  userId: string;
  lastActivity: number;
  timestamp: number;
}

class AuthService {
  private baseURL: string;

  constructor() {
    // Use relative URL for same-origin requests
    this.baseURL = '/api/vnc';
  }

  async authenticate(username: string, password: string): Promise<AuthResponse> {
    try {
      const response = await axios.post<AuthResponse>(`${this.baseURL}/authenticate`, {
        username,
        preAuthPassword: password
      } as AuthRequest);

      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error)) {
        if (error.response?.status === 401) {
          throw new Error('Invalid username or password');
        } else if (error.response?.status === 400) {
          throw new Error(error.response.data?.message || 'Invalid request');
        } else {
          throw new Error('Authentication failed. Please try again.');
        }
      }
      throw new Error('Network error. Please check your connection.');
    }
  }

  async validateSession(sessionId: string): Promise<SessionResponse> {
    try {
      const response = await axios.get<SessionResponse>(`${this.baseURL}/session/${sessionId}/validate`);
      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        throw new Error('Session expired or invalid');
      }
      throw new Error('Failed to validate session');
    }
  }

  async logout(sessionId: string): Promise<void> {
    try {
      await axios.delete(`${this.baseURL}/session/${sessionId}`);
    } catch (error) {
      console.error('Logout error:', error);
      // Don't throw error for logout as it's not critical
    }
  }
}

export const authService = new AuthService(); 