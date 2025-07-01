import { performanceService } from './performance-service'

export interface LatencyMeasurement {
  browserToProxy: number
  proxyToVNC: number
  totalEndToEnd: number
  timestamp: number
}

/**
 * Handles WebSocket-based latency measurement with integrated proxy-to-VNC latency fetching
 * Provides complete end-to-end latency measurements by combining:
 * - Browser-to-proxy latency (measured via WebSocket ping/pong)
 * - Proxy-to-VNC latency (fetched from performance API with 5-second caching)
 */
class LatencyService {
  private ws: WebSocket | null = null
  private sessionId: string | null = null
  private pingInterval: NodeJS.Timeout | null = null
  private onLatencyUpdate: ((latency: LatencyMeasurement) => void) | null = null
  private reconnectAttempts = 0
  private maxReconnectAttempts = 5
  
  // Cache for proxy-to-VNC latency to reduce API calls
  private cachedProxyLatency: number = 0
  private cachedProxyLatencyTimestamp: number = 0
  private readonly PROXY_LATENCY_CACHE_TTL = 5000 // 5 seconds cache TTL

  connect(sessionId: string, onUpdate: (latency: LatencyMeasurement) => void) {
    this.sessionId = sessionId
    this.onLatencyUpdate = onUpdate
    
    this.createWebSocket()
  }

  private createWebSocket() {
    if (!this.sessionId) return

    try {
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      const host = window.location.host
      const wsUrl = `${protocol}//${host}/latency/${this.sessionId}`
      
      this.ws = new WebSocket(wsUrl)
      
      this.ws.onopen = () => {
        console.log('Latency WebSocket connected')
        this.reconnectAttempts = 0
        this.startPingInterval()
      }
      
      this.ws.onmessage = async (event) => {
        await this.handleMessage(event.data)
      }
      
      this.ws.onclose = () => {
        console.log('Latency WebSocket closed')
        this.stopPingInterval()
        this.attemptReconnect()
      }
      
      this.ws.onerror = (error) => {
        console.error('Latency WebSocket error:', error)
      }
      
    } catch (error) {
      console.error('Failed to create latency WebSocket:', error)
    }
  }

  private async handleMessage(data: string) {
    try {
      const message = JSON.parse(data)
      
      if (message.type === 'pong') {
        await this.calculateLatency(message)
      }
    } catch (error) {
      console.error('Failed to parse latency message:', error)
    }
  }

  /**
   * Get cached proxy-to-VNC latency or fetch from API if cache is stale
   */
  private async getProxyLatency(): Promise<number> {
    const currentTime = Date.now()
    
    // Check if cache is still valid
    if (currentTime - this.cachedProxyLatencyTimestamp < this.PROXY_LATENCY_CACHE_TTL) {
      return this.cachedProxyLatency
    }
    
    // Cache is stale, fetch new data
    if (this.sessionId) {
      try {
        const connectionStats = await performanceService.getConnectionStats(this.sessionId)
        if (connectionStats) {
          this.cachedProxyLatency = connectionStats.proxyToVNCLatency
          this.cachedProxyLatencyTimestamp = currentTime
          return this.cachedProxyLatency
        }
      } catch (error) {
        console.error('Failed to fetch proxy-to-VNC latency:', error)
      }
    }
    
    // Return cached value if fetch failed
    return this.cachedProxyLatency
  }

  private async calculateLatency(pongMessage: any) {
    const clientTimestamp = pongMessage.clientTimestamp
    const serverTimestamp = pongMessage.serverTimestamp
    const currentTime = Date.now()
    
    // Calculate round-trip time
    const roundTripTime = currentTime - clientTimestamp
    
    // Calculate one-way latency (approximation)
    const browserToProxy = Math.round(roundTripTime / 2)
    
    // Get proxy-to-VNC latency (cached or fetched)
    const proxyToVNC = await this.getProxyLatency()
    
    const totalEndToEnd = browserToProxy + proxyToVNC
    
    const measurement: LatencyMeasurement = {
      browserToProxy,
      proxyToVNC,
      totalEndToEnd,
      timestamp: currentTime
    }
    
    if (this.onLatencyUpdate) {
      this.onLatencyUpdate(measurement)
    }
  }

  private startPingInterval() {
    this.stopPingInterval()
    
    this.pingInterval = setInterval(() => {
      this.sendPing()
    }, 5000) // Send ping every 5 seconds
  }

  private stopPingInterval() {
    if (this.pingInterval) {
      clearInterval(this.pingInterval)
      this.pingInterval = null
    }
  }

  private sendPing() {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      const pingMessage = {
        type: 'ping',
        timestamp: Date.now()
      }
      
      this.ws.send(JSON.stringify(pingMessage))
    }
  }

  private attemptReconnect() {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++
      const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 10000) // Exponential backoff, max 10s
      
      console.log(`Attempting to reconnect latency WebSocket in ${delay}ms (attempt ${this.reconnectAttempts})`)
      
      setTimeout(() => {
        this.createWebSocket()
      }, delay)
    } else {
      console.error('Max reconnection attempts reached for latency WebSocket')
    }
  }

  disconnect() {
    this.stopPingInterval()
    
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
    
    this.sessionId = null
    this.onLatencyUpdate = null
    this.reconnectAttempts = 0
    
    // Clear cache when disconnecting
    this.clearProxyLatencyCache()
  }

  isConnected(): boolean {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN
  }

  /**
   * Manually refresh proxy-to-VNC latency from performance API
   * This can be called periodically to get updated latency data
   */
  async refreshProxyLatency(): Promise<number | null> {
    if (!this.sessionId) return null
    
    try {
      const connectionStats = await performanceService.getConnectionStats(this.sessionId)
      if (connectionStats) {
        // Update cache with new value
        this.cachedProxyLatency = connectionStats.proxyToVNCLatency
        this.cachedProxyLatencyTimestamp = Date.now()
        return connectionStats.proxyToVNCLatency
      }
      return null
    } catch (error) {
      console.error('Failed to refresh proxy-to-VNC latency:', error)
      return null
    }
  }

  /**
   * Clear the proxy-to-VNC latency cache
   */
  private clearProxyLatencyCache(): void {
    this.cachedProxyLatency = 0
    this.cachedProxyLatencyTimestamp = 0
  }

  /**
   * Get cache statistics for debugging
   */
  getCacheStats(): { cachedValue: number; cacheAge: number; isStale: boolean } {
    const currentTime = Date.now()
    const cacheAge = currentTime - this.cachedProxyLatencyTimestamp
    const isStale = cacheAge >= this.PROXY_LATENCY_CACHE_TTL
    
    return {
      cachedValue: this.cachedProxyLatency,
      cacheAge,
      isStale
    }
  }
}

export const latencyService = new LatencyService() 