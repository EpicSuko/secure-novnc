import { performanceService } from './performance-service'

export interface LatencyMeasurement {
  browserToProxy: number
  proxyToVNC: number
  totalEndToEnd: number
  timestamp: number
}

export interface LatencyMessage {
  type: string
  clientTimestamp: number
  serverTimestamp: number
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
  private cacheInitialized: boolean = false
  private readonly PROXY_LATENCY_CACHE_TTL = 5000 // 5 seconds cache TTL
  private readonly MAX_STALENESS_THRESHOLD = 30000 // 30 seconds maximum staleness
  private fetchingProxyLatency: boolean = false
  private currentFetchPromise: Promise<number> | null = null

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
   * Handles concurrent requests by reusing ongoing fetch promises
   * Prevents use of excessively stale data when fetch fails
   */
  private async getProxyLatency(): Promise<number> {
    const currentTime = Date.now()
    
    // Check if cache is still valid
    if (currentTime - this.cachedProxyLatencyTimestamp < this.PROXY_LATENCY_CACHE_TTL) {
      return this.cachedProxyLatency
    }
    
    // Check if a fetch is already in progress
    if (this.fetchingProxyLatency && this.currentFetchPromise) {
      return await this.currentFetchPromise
    }
    
    // Check if cached data is too stale to use as fallback
    const cacheAge = currentTime - this.cachedProxyLatencyTimestamp
    if (cacheAge > this.MAX_STALENESS_THRESHOLD) {
      console.warn('Proxy latency cache is too stale, forcing fresh fetch')
    }
    
    // Start new fetch
    this.fetchingProxyLatency = true
    this.currentFetchPromise = this.performProxyLatencyFetch(currentTime)
    
    try {
      const result = await this.currentFetchPromise
      return result
    } finally {
      this.fetchingProxyLatency = false
      this.currentFetchPromise = null
    }
  }

  /**
   * Perform the actual proxy latency fetch operation
   */
  private async performProxyLatencyFetch(currentTime: number): Promise<number> {
    if (!this.sessionId) {
      throw new Error('No session ID available for proxy latency fetch')
    }
    
    try {
      const connectionStats = await performanceService.getConnectionStats(this.sessionId)
      if (connectionStats) {
        this.cachedProxyLatency = connectionStats.proxyToVNCLatency
        this.cachedProxyLatencyTimestamp = currentTime
        this.cacheInitialized = true
        return this.cachedProxyLatency
      } else {
        throw new Error('Performance service returned null or undefined connection stats')
      }
    } catch (error) {
      console.error('Failed to fetch proxy-to-VNC latency:', error)
      
      // Check if we can use cached data as fallback
      const cacheAge = currentTime - this.cachedProxyLatencyTimestamp
      if (cacheAge <= this.MAX_STALENESS_THRESHOLD) {
        console.warn('Using cached proxy latency as fallback due to fetch failure')
        return this.cachedProxyLatency
      } else {
        throw new Error(`Proxy latency fetch failed and cache is too stale (${cacheAge}ms old)`)
      }
    }
  }

  private async calculateLatency(pongMessage: LatencyMessage) {
    const clientTimestamp = pongMessage.clientTimestamp
    const currentTime = Date.now()
    
    // Calculate round-trip time
    const roundTripTime = currentTime - clientTimestamp
    
    // Calculate one-way latency (approximation)
    const browserToProxy = Math.round(roundTripTime / 2)
    
    // Get proxy-to-VNC latency (cached or fetched)
    let proxyToVNC = 0
    try {
      proxyToVNC = await this.getProxyLatency()
    } catch (error) {
      console.error('Failed to get proxy-to-VNC latency:', error)
      // Use 0 as fallback when we can't get proxy latency
      proxyToVNC = 0
    }
    
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
        this.cacheInitialized = true
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
    this.cacheInitialized = false
    this.fetchingProxyLatency = false
    this.currentFetchPromise = null
  }

  /**
   * Get cache statistics for debugging
   */
  getCacheStats(): { cachedValue: number; cacheAge: number; isStale: boolean; isFetching: boolean; isTooStale: boolean; cacheInitialized: boolean } {
    const currentTime = Date.now()
    
    // If cache is not initialized, return meaningful default values
    if (!this.cacheInitialized) {
      return {
        cachedValue: 0,
        cacheAge: 0,
        isStale: false,
        isFetching: this.fetchingProxyLatency,
        isTooStale: false,
        cacheInitialized: false
      }
    }
    
    const cacheAge = currentTime - this.cachedProxyLatencyTimestamp
    const isStale = cacheAge >= this.PROXY_LATENCY_CACHE_TTL
    const isTooStale = cacheAge > this.MAX_STALENESS_THRESHOLD
    
    return {
      cachedValue: this.cachedProxyLatency,
      cacheAge,
      isStale,
      isFetching: this.fetchingProxyLatency,
      isTooStale,
      cacheInitialized: true
    }
  }
}

export const latencyService = new LatencyService() 