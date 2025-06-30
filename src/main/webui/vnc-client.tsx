"use client"

import { useState, useCallback, useRef } from "react"
import { ConnectionDialog, type ConnectionConfig } from "./components/connection-dialog"
import { ControlToolbar } from "./components/control-toolbar"
import { VNCCanvas } from "./components/vnc-canvas"
import { StatusBar } from "./components/status-bar"
import { Monitor } from "lucide-react"
import { UserMenu } from "./components/user-menu"
import { ThemeToggle } from "./components/theme-toggle"

interface VNCClientProps {
  username: string
  onLogout: () => void
  sessionId?: string
}

export default function VNCClient({ username, onLogout, sessionId }: VNCClientProps) {
  const [isConnected, setIsConnected] = useState(false)
  const [connectionStatus, setConnectionStatus] = useState("Disconnected")
  const [isFullscreen, setIsFullscreen] = useState(false)
  const [isMuted, setIsMuted] = useState(false)
  const [config, setConfig] = useState<ConnectionConfig | null>(null)
  const [latency, setLatency] = useState<number>()

  const canvasRef = useRef<HTMLDivElement>(null)

  const handleConnect = useCallback((newConfig: ConnectionConfig) => {
    setConfig(newConfig)
    setConnectionStatus("Connecting...")
    setIsConnected(true) // This will trigger noVNC connection
  }, [])

  const handleDisconnect = useCallback(() => {
    setIsConnected(false)
    setConnectionStatus("Disconnected")
    setLatency(undefined)
  }, [])

  const handleRestart = useCallback(() => {
    if (!isConnected) return
    setConnectionStatus("Restarting...")
    setTimeout(() => {
      setConnectionStatus("Connected")
    }, 3000)
  }, [isConnected])

  const handleToggleFullscreen = useCallback(() => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen()
      setIsFullscreen(true)
    } else {
      document.exitFullscreen()
      setIsFullscreen(false)
    }
  }, [])

  const handleToggleMute = useCallback(() => {
    setIsMuted(!isMuted)
  }, [isMuted])

  const handleSendCtrlAltDel = useCallback(() => {
    if (!isConnected) return
    const canvas = document.querySelector("[data-novnc-canvas]") as any
    if (canvas && canvas.sendCtrlAltDel) {
      canvas.sendCtrlAltDel()
    }
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
      const canvas = document.querySelector("[data-novnc-canvas]") as any
      if (canvas && canvas.sendClipboard) {
        canvas.sendClipboard(text)
      }
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

  const handleConnectionChange = useCallback((connected: boolean) => {
    if (connected) {
      setConnectionStatus("Connected")
      setLatency(Math.floor(Math.random() * 50) + 10)
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
          <ConnectionDialog onConnect={handleConnect} isConnected={isConnected} />
          <UserMenu username={username} onLogout={onLogout} />
        </div>
      </div>

      {/* Control Toolbar */}
      <div className="flex-shrink-0">
        <ControlToolbar
          isConnected={isConnected}
          isFullscreen={isFullscreen}
          isMuted={isMuted}
          onDisconnect={handleDisconnect}
          onRestart={handleRestart}
          onToggleFullscreen={handleToggleFullscreen}
          onToggleMute={handleToggleMute}
          onSendCtrlAltDel={handleSendCtrlAltDel}
          onCopy={handleCopy}
          onPaste={handlePaste}
        />
      </div>

      {/* Main Canvas Area */}
      <div className="flex-1 min-h-0 p-2">
        <VNCCanvas
          isConnected={isConnected}
          viewOnly={config?.viewOnly || false}
          host={config?.host}
          port={config?.port}
          password={config?.password}
          sessionId={sessionId}
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
        />
      </div>
    </div>
  )
}
