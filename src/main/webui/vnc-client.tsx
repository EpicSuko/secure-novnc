"use client"

import { useState, useCallback, useRef, useEffect } from "react"
import { ConnectButton, type ConnectionConfig } from "./components/connect-button"
import { ControlToolbar } from "./components/control-toolbar"
import { VNCCanvas, type VNCCanvasRef } from "./components/vnc-canvas"
import { StatusBar } from "./components/status-bar"
import { Monitor } from "lucide-react"
import { UserMenu } from "./components/user-menu"
import { ThemeToggle } from "./components/theme-toggle"
import { Alert, AlertDescription } from "./components/ui/alert"
import { AlertCircle } from "lucide-react"

import { latencyService, type LatencyMeasurement } from "./lib/latency-service"
import { configService, type ConfigResponse } from "./lib/config-service"

interface VNCClientProps {
  username: string
  onLogout: () => void
  sessionId?: string
}

export default function VNCClient({ username, onLogout, sessionId }: VNCClientProps) {
  const [isConnected, setIsConnected] = useState(false)
  const [connectionStatus, setConnectionStatus] = useState("Disconnected")
  const [isFullscreen, setIsFullscreen] = useState(false)
  const [config, setConfig] = useState<ConnectionConfig | null>(null)
  const [wsConfig, setWsConfig] = useState<ConfigResponse | null>(null)
  const [latency, setLatency] = useState<number>()
  const [isLatencyLoading, setIsLatencyLoading] = useState(false)
  const [latencyDetails, setLatencyDetails] = useState<LatencyMeasurement | null>(null)
  const [connectionError, setConnectionError] = useState<string | null>(null)

  const vncRef = useRef<VNCCanvasRef>(null)
  const isMountedRef = useRef(true)

  const handleConnect = useCallback((newConfig: ConnectionConfig) => {
    setConfig(newConfig)
    setConnectionStatus("Connecting...")
    setConnectionError(null)
    
    // If we have a sessionId, try to get the WebSocket config for auto-connect
    if (sessionId) {
      configService.getConfig(sessionId)
        .then(config => {
          // Check if component is still mounted before updating state
          if (isMountedRef.current) {
            setWsConfig(config)
            // Set isConnected to true to trigger VNCCanvas connection
            // The actual connection state will be managed by VNCCanvas through onConnectionChange
            setIsConnected(true)
          }
        })
        .catch(error => {
          // Check if component is still mounted before updating state
          if (isMountedRef.current) {
            console.error('Failed to get WebSocket config:', error)
            setConnectionError('Failed to establish connection. Please try again.')
            setConnectionStatus("Connection Failed")
          }
        })
    } else {
      // No sessionId, use manual connection
      // Set isConnected to true to trigger VNCCanvas connection with manual config
      setIsConnected(true)
    }
  }, [sessionId])

  const handleDisconnect = useCallback(() => {
    setIsConnected(false)
    setConnectionStatus("Disconnected")
    setLatency(undefined)
  }, [])

  const handleToggleFullscreen = useCallback(() => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen()
      setIsFullscreen(true)
    } else {
      document.exitFullscreen()
      setIsFullscreen(false)
    }
  }, [])

  const handleSendCtrlAltDel = useCallback(() => {
    if (!isConnected) return
    vncRef.current?.sendCtrlAltDel()
  }, [isConnected])

  const handleCopy = useCallback(async () => {
    try {
      const text = await navigator.clipboard.readText()
      console.log("Copied from clipboard:", text)
    } catch (err) {
      console.error("Failed to read clipboard:", err)
    }
  }, [])

  const handlePaste = useCallback(async () => {
    try {
      const text = await navigator.clipboard.readText()
      vncRef.current?.rfb?.clipboardPasteFrom(text)
    } catch (err) {
      console.error("Failed to paste to clipboard:", err)
    }
  }, [])

  const handleMouseMove = useCallback((x: number, y: number) => {
    // In a real implementation, send mouse coordinates to VNC server
    console.log(`Mouse moved to: ${x}, ${y}`)
  }, [])

  const handleMouseClick = useCallback((x: number, y: number, button: number) => {
    // In a real implementation, send mouse click to VNC server
    console.log(`Mouse clicked at: ${x}, ${y}, button: ${button}`)
  }, [])

  const handleKeyPress = useCallback((key: string, keyCode: number) => {
    // In a real implementation, send key press to VNC server
    console.log(`Key pressed: ${key} (${keyCode})`)
  }, [])

  const serverInfo = isConnected
    ? {
        name: "Remote Desktop",
        width: 1024,
        height: 768,
        pixelFormat: "RGB888",
      }
    : undefined

  // Cleanup effect to track component mount state
  useEffect(() => {
    isMountedRef.current = true
    return () => {
      isMountedRef.current = false
    }
  }, [])

  // Start latency measurement when connected
  useEffect(() => {
    if (!isConnected || !sessionId) {
      setLatency(undefined)
      setLatencyDetails(null)
      latencyService.disconnect()
      return
    }

    // Start WebSocket-based latency measurement (now includes proxy-to-VNC latency)
    latencyService.connect(sessionId, (measurement) => {
      setLatencyDetails(measurement)
      // Use total end-to-end latency as the primary latency value
      setLatency(measurement.totalEndToEnd)
    })

    // Periodically refresh proxy-to-VNC latency for more frequent updates
    const refreshProxyLatency = async () => {
      try {
        setIsLatencyLoading(true)
        const proxyLatency = await latencyService.refreshProxyLatency()
        if (proxyLatency !== null && latencyDetails) {
          // Update the proxy-to-VNC latency
          const updatedMeasurement: LatencyMeasurement = {
            ...latencyDetails,
            proxyToVNC: proxyLatency,
            totalEndToEnd: latencyDetails.browserToProxy + proxyLatency
          }
          setLatencyDetails(updatedMeasurement)
          setLatency(updatedMeasurement.totalEndToEnd)
        }
      } catch (error) {
        console.error('Failed to refresh proxy latency:', error)
      } finally {
        setIsLatencyLoading(false)
      }
    }

    // Refresh proxy latency every 10 seconds for more frequent updates
    const interval = setInterval(refreshProxyLatency, 10000)

    return () => {
      clearInterval(interval)
      latencyService.disconnect()
    }
  }, [isConnected, sessionId])

  const handleConnectionChange = useCallback((connected: boolean) => {
    if (connected) {
      setConnectionStatus("Connected")
      setConnectionError(null)
      // Latency will be fetched by the useEffect above
    } else {
      setIsConnected(false)
      setConnectionStatus("Disconnected")
      setLatency(undefined)
    }
  }, [])

  return (
    <div className="flex flex-col h-screen bg-background overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between p-3 border-b dark:border-gray-700 dark:bg-gray-900/50 flex-shrink-0">
        <div className="flex items-center gap-2">
          <Monitor className="w-5 h-5 dark:text-white" />
          <h1 className="text-lg font-semibold dark:text-white">VNC Client</h1>
        </div>
        <div className="flex items-center gap-3">
          <ThemeToggle />
          <ConnectButton onConnect={handleConnect} isConnected={isConnected} />
          <UserMenu username={username} onLogout={onLogout} />
        </div>
      </div>

      {/* Control Toolbar */}
      <div className="flex-shrink-0">
        <ControlToolbar
          isConnected={isConnected}
          isFullscreen={isFullscreen}
          onDisconnect={handleDisconnect}
          onToggleFullscreen={handleToggleFullscreen}
          onSendCtrlAltDel={handleSendCtrlAltDel}
          onCopy={handleCopy}
          onPaste={handlePaste}
        />
      </div>

      {/* Connection Error Alert */}
      {connectionError && (
        <div className="flex-shrink-0 p-2">
          <Alert variant="destructive" className="dark:border-red-800 dark:bg-red-900/20">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription className="dark:text-red-300">{connectionError}</AlertDescription>
          </Alert>
        </div>
      )}

      {/* Main Canvas Area */}
      <div className="flex-1 min-h-0 p-2">
        <VNCCanvas
          ref={vncRef}
          isConnected={isConnected}
          viewOnly={config?.viewOnly || false}
          host={config?.host}
          port={config?.port}
          password={config?.password}
          sessionId={sessionId}
          wsConfig={wsConfig}
          onConnectionChange={handleConnectionChange}
          onMouseMove={handleMouseMove}
          onMouseClick={handleMouseClick}
          onKeyPress={handleKeyPress}
        />
      </div>

      {/* Status Bar */}
      <div className="flex-shrink-0">
        <StatusBar
          isConnected={isConnected}
          connectionStatus={connectionStatus}
          serverInfo={serverInfo}
          encrypted={true}
          latency={latency}
          isLatencyLoading={isLatencyLoading}
          latencyDetails={latencyDetails || undefined}
        />
      </div>
    </div>
  )
}
