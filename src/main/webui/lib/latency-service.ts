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
 * - Proxy-to-VNC latency (fetched from performance API)
 */
class LatencyService {
  private ws: WebSocket | null = null
  private sessionId: string | null = null
  private pingInterval: NodeJS.Timeout | null = null
  private onLatencyUpdate: ((latency: LatencyMeasurement) => void) | null = null
  private reconnectAttempts = 0
  private maxReconnectAttempts = 5

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

  private async calculateLatency(pongMessage: any) {
    const clientTimestamp = pongMessage.clientTimestamp
    const serverTimestamp = pongMessage.serverTimestamp
    const currentTime = Date.now()
    
    // Calculate round-trip time
    const roundTripTime = currentTime - clientTimestamp
    
    // Calculate one-way latency (approximation)
    const browserToProxy = Math.round(roundTripTime / 2)
    
    // Fetch proxy-to-VNC latency from the performance API
    let proxyToVNC = 0
    if (this.sessionId) {
      try {
        const connectionStats = await performanceService.getConnectionStats(this.sessionId)
        if (connectionStats) {
          proxyToVNC = connectionStats.proxyToVNCLatency
        }
      } catch (error) {
        console.error('Failed to fetch proxy-to-VNC latency:', error)
      }
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
      return connectionStats?.proxyToVNCLatency || null
    } catch (error) {
      console.error('Failed to refresh proxy-to-VNC latency:', error)
      return null
    }
  }
}

export const latencyService = new LatencyService() 