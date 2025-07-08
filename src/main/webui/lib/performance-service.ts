export interface VNCPerformanceStats {
  timestamp: number
  userId: string
  userConnections: number
  totalConnections: number
  userBytesReceived: number
  userBytesSent: number
  userMessages: number
  userAverageLatency: number
  userThroughput: number
  connections: Record<string, ConnectionStats>
}

export interface ConnectionStats {
  state: string
  connected: boolean
  duration: number
  bytesReceived: number
  bytesSent: number
  messageCount: number
  averageLatency: number
  browserToProxyLatency: number
  proxyToVNCLatency: number
  totalEndToEndLatency: number
  throughput: number
  lastActivity: number
  lastLatencyUpdate: number
  clientBufferSize: number
  serverBufferSize: number
}

class PerformanceService {
  private baseUrl: string

  constructor() {
    this.baseUrl = '/api/vnc/performance'
  }

  async getPerformanceStats(sessionId: string): Promise<VNCPerformanceStats | null> {
    try {
      const response = await fetch(`${this.baseUrl}?sessionId=${encodeURIComponent(sessionId)}`)
      if (!response.ok) {
        if (response.status === 401) {
          throw new Error('Session expired or invalid')
        } else if (response.status === 400) {
          throw new Error('Session ID is required')
        } else {
          throw new Error(`HTTP error! status: ${response.status}`)
        }
      }
      return await response.json()
    } catch (error) {
      console.error('Failed to fetch performance stats:', error)
      return null
    }
  }

  async getPerformanceSummary(sessionId: string): Promise<string | null> {
    try {
      const response = await fetch(`${this.baseUrl}/summary?sessionId=${encodeURIComponent(sessionId)}`)
      if (!response.ok) {
        if (response.status === 401) {
          throw new Error('Session expired or invalid')
        } else if (response.status === 400) {
          throw new Error('Session ID is required')
        } else {
          throw new Error(`HTTP error! status: ${response.status}`)
        }
      }
      return await response.text()
    } catch (error) {
      console.error('Failed to fetch performance summary:', error)
      return null
    }
  }

  // --- add these at the top of the class ---
  private cachedStats: VNCPerformanceStats | null = null
  private cacheTimestamp: number = 0
  private readonly CACHE_TTL = 1000 // 1 second cache
  private currentSessionId: string | null = null

  private async getCachedPerformanceStats(sessionId: string): Promise<VNCPerformanceStats | null> {
    const now = Date.now()
    
    // Clear cache if session ID changed
    if (this.currentSessionId !== sessionId) {
      this.cachedStats = null
      this.cacheTimestamp = 0
      this.currentSessionId = sessionId
    }
    
    if (this.cachedStats && (now - this.cacheTimestamp) < this.CACHE_TTL) {
      return this.cachedStats
    }

    this.cachedStats = await this.getPerformanceStats(sessionId)
    this.cacheTimestamp = now
    return this.cachedStats
  }

  // --- existing methods, updated to use the cache ---
  async getConnectionLatency(sessionId: string): Promise<number | null> {
    try {
      const stats = await this.getCachedPerformanceStats(sessionId)
      if (!stats) return null

      // Return the user's average latency
      return stats.userAverageLatency
    } catch (error) {
      console.error('Failed to get connection latency:', error)
      return null
    }
  }

  async getConnectionStats(sessionId: string): Promise<ConnectionStats | null> {
    try {
      const stats = await this.getCachedPerformanceStats(sessionId)
      if (!stats) return null
      
      // Return the first connection's stats (since user should only have one active connection)
      const connectionEntries = Object.entries(stats.connections)
      if (connectionEntries.length > 0) {
        return connectionEntries[0][1]
      }
      return null
    } catch (error) {
      console.error('Failed to get connection stats:', error)
      return null
    }
  }

  async getUserStats(sessionId: string): Promise<{
    userId: string
    userConnections: number
    userBytesReceived: number
    userBytesSent: number
    userMessages: number
    userAverageLatency: number
    userThroughput: number
  } | null> {
    try {
      const stats = await this.getCachedPerformanceStats(sessionId)
      if (!stats) return null
      
      return {
        userId: stats.userId,
        userConnections: stats.userConnections,
        userBytesReceived: stats.userBytesReceived,
        userBytesSent: stats.userBytesSent,
        userMessages: stats.userMessages,
        userAverageLatency: stats.userAverageLatency,
        userThroughput: stats.userThroughput
      }
    } catch (error) {
      console.error('Failed to get user stats:', error)
      return null
    }
  }

  // Clear cache when needed (e.g., on logout)
  clearCache(): void {
    this.cachedStats = null
    this.cacheTimestamp = 0
    this.currentSessionId = null
  }
}

export const performanceService = new PerformanceService() 