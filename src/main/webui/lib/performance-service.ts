export interface VNCPerformanceStats {
  timestamp: number
  activeConnections: number
  totalConnections: number
  totalBytesReceived: number
  totalBytesSent: number
  totalMessages: number
  averageLatency: number
  totalThroughput: number
  vncServerHost: string
  vncServerPort: number
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

  async getPerformanceStats(): Promise<VNCPerformanceStats | null> {
    try {
      const response = await fetch(this.baseUrl)
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }
      return await response.json()
    } catch (error) {
      console.error('Failed to fetch performance stats:', error)
      return null
    }
  }
  // --- add these at the top of the class ---
  private cachedStats: VNCPerformanceStats | null = null
  private cacheTimestamp: number = 0
  private readonly CACHE_TTL = 1000 // 1 second cache

  private async getCachedPerformanceStats(): Promise<VNCPerformanceStats | null> {
    const now = Date.now()
    if (this.cachedStats && (now - this.cacheTimestamp) < this.CACHE_TTL) {
      return this.cachedStats
    }

    this.cachedStats = await this.getPerformanceStats()
    this.cacheTimestamp = now
    return this.cachedStats
  }

  // --- existing methods, updated to use the cache ---
  async getConnectionLatency(sessionId?: string): Promise<number | null> {
    try {
      const stats = await this.getCachedPerformanceStats()
      if (!stats) return null

      // If sessionId is provided, try to get specific connection latency
      if (sessionId && stats.connections[sessionId]) {
        return stats.connections[sessionId].averageLatency
      }
      
      // Otherwise return the overall average latency
      return stats.averageLatency
    } catch (error) {
      console.error('Failed to get connection latency:', error)
      return null
    }
  }

  async getConnectionStats(sessionId: string): Promise<ConnectionStats | null> {
    try {
      const stats = await this.getCachedPerformanceStats()
      if (!stats) return null
      return stats.connections[sessionId] || null
    } catch (error) {
      console.error('Failed to get connection stats:', error)
      return null
    }
  }
}

export const performanceService = new PerformanceService() 