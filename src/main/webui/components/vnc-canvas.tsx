"use client"

import { useEffect, useRef, useState, useCallback, forwardRef, useImperativeHandle } from "react"
import { Card } from "@/components/ui/card"
import NoVncClient from "@novnc/novnc/lib/rfb"

interface VNCCanvasProps {
  isConnected: boolean
  viewOnly: boolean
  host?: string
  port?: string
  password?: string
  sessionId?: string
  onConnectionChange?: (connected: boolean) => void
  onMouseMove?: (x: number, y: number) => void
  onMouseClick?: (x: number, y: number, button: number) => void
  onKeyPress?: (key: string, keyCode: number) => void
}

export interface VNCCanvasRef {
  rfb: NoVncClient | null
  sendCtrlAltDel: () => void
  sendClipboard: (text: string) => void
  disconnect: () => void
}

export const VNCCanvas = forwardRef<VNCCanvasRef, VNCCanvasProps>(({
  isConnected,
  viewOnly,
  host,
  port,
  password,
  sessionId,
  onConnectionChange,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  onMouseMove,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  onMouseClick,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  onKeyPress,
}, ref) => {
  const containerRef = useRef<HTMLDivElement>(null)
  const rfbRef = useRef<NoVncClient | null>(null)
  const [connectionStatus, setConnectionStatus] = useState("Disconnected")
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const [mousePosition, setMousePosition] = useState({ x: 0, y: 0 })

  // Function to parse current URL and build WebSocket URL
  const buildWebSocketUrl = useCallback((sessionId: string): string => {
    if (typeof window === "undefined") {
      throw new Error("Cannot build WebSocket URL on server side")
    }

    const currentUrl = new URL(window.location.href)
    const protocol = currentUrl.protocol === 'https:' ? 'wss' : 'ws'
    const wsHost = currentUrl.hostname
    const wsPort = currentUrl.port || (protocol === 'wss' ? '443' : '80')
    const portSuffix = (wsPort === '443' && protocol === 'wss') || (wsPort === '80' && protocol === 'ws') ? '' : `:${wsPort}`
    
    return `${protocol}://${wsHost}${portSuffix}/websockify/${sessionId}`
  }, [])

  // Expose methods and RFB instance to parent components
  useImperativeHandle(ref, () => ({
    get rfb() {
      return rfbRef.current
    },
    sendCtrlAltDel: () => {
      if (rfbRef.current) {
        rfbRef.current.sendCtrlAltDel()
      }
    },
    sendClipboard: (text: string) => {
      if (rfbRef.current) {
        rfbRef.current.clipboardPasteFrom(text)
      }
    },
    disconnect: () => {
      if (rfbRef.current) {
        rfbRef.current.disconnect()
        rfbRef.current = null
      }
    }
  }), [])

  // Initialize noVNC when component mounts and connection is requested
  useEffect(() => {
    const initNoVNC = async () => {
      if (typeof window === "undefined") return

      try {
        // Only connect if we have connection details and should be connected
        if (!isConnected) {
          // Clean up existing connection if disconnecting
          if (rfbRef.current) {
            rfbRef.current.disconnect()
            rfbRef.current = null
          }
          return
        }

        // Check if we have sessionId for auto-connect
        if (!sessionId) {
          console.log("No sessionId available for connection")
          return
        }

        // Dynamically import noVNC
        const { default: RFB } = await import("@novnc/novnc/lib/rfb")

        if (!containerRef.current) return

        // Clear any existing connection
        if (rfbRef.current) {
          rfbRef.current.disconnect()
          rfbRef.current = null
        }

        // Clear container
        containerRef.current.innerHTML = ""

        // Build WebSocket URL from current location
        let url: string
        try {
          url = buildWebSocketUrl(sessionId)
        } catch (error) {
          console.error("Failed to build WebSocket URL:", error)
          setConnectionStatus("Configuration Error")
          onConnectionChange?.(false)
          return
        }

        // Create new RFB connection
        const rfb = new RFB(containerRef.current, url, {
          credentials: { 
            username: "",
            password: password || "",
            target: ""
          },
          repeaterID: "",
          shared: true,
        })

        // Set view-only mode
        rfb.viewOnly = viewOnly

        rfb.scaleViewport = true
        rfb.resizeSession = true

        // Event handlers
        rfb.addEventListener("connect", () => {
          setConnectionStatus("Connected")
          onConnectionChange?.(true)
          console.log("Connected to VNC server")
        })

        rfb.addEventListener("disconnect", (e: CustomEvent) => {
          setConnectionStatus("Disconnected")
          onConnectionChange?.(false)
          console.log("Disconnected from VNC server:", e.detail.clean ? "Clean" : "Unclean")
        })

        rfb.addEventListener("credentialsrequired", () => {
          setConnectionStatus("Authentication Required")
          console.log("Credentials required")
        })

        rfb.addEventListener("securityfailure", (e: CustomEvent) => {
          setConnectionStatus("Security Failure")
          onConnectionChange?.(false)
          console.error("Security failure:", e.detail)
        })

        rfbRef.current = rfb
      } catch (error) {
        console.error("Failed to initialize noVNC:", error)
        setConnectionStatus("Failed to load noVNC")
        onConnectionChange?.(false)
      }
    }

    initNoVNC()

    // Cleanup on unmount
    return () => {
      if (rfbRef.current) {
        rfbRef.current.disconnect()
        rfbRef.current = null
      }
    }
  }, [isConnected, host, port, password, viewOnly, sessionId, onConnectionChange, buildWebSocketUrl])

  // Handle view-only mode changes
  useEffect(() => {
    if (rfbRef.current) {
      rfbRef.current.viewOnly = viewOnly
    }
  }, [viewOnly])

  return (
    <Card className="h-full w-full p-4 dark:bg-gray-900/50 dark:border-gray-700 overflow-hidden">
      <div className="relative h-full w-full">
        <div
          ref={containerRef}
          className="h-full w-full"
          style={{
            background: isConnected ? "transparent" : "#f3f4f6",
          }}
        />

        {!isConnected && (
          <div className="absolute inset-0 flex items-center justify-center bg-gray-100 dark:bg-gray-800 bg-opacity-90 dark:bg-opacity-90 rounded">
            <div className="text-center">
              <div className="text-2xl text-gray-500 dark:text-gray-400 mb-2">
                {connectionStatus === "Disconnected" ? "Not Connected" : connectionStatus}
              </div>
              <div className="text-sm text-gray-400 dark:text-gray-500">
                {connectionStatus === "Disconnected"
                  ? "Click Connect to establish a VNC connection"
                  : "Establishing connection..."}
              </div>
            </div>
          </div>
        )}

        {viewOnly && isConnected && (
          <div className="absolute top-2 left-2 bg-yellow-500 dark:bg-yellow-600 text-black dark:text-white px-2 py-1 rounded text-xs font-semibold">
            VIEW ONLY
          </div>
        )}
      </div>
    </Card>
  )
})

VNCCanvas.displayName = "VNCCanvas"
