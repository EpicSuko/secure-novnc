"use client"

import { useEffect, useRef, useState, useCallback, forwardRef, useImperativeHandle } from "react"
import { Card } from "@/components/ui/card"
import NoVncClient from "@novnc/novnc/lib/rfb"
import { ConfigResponse } from "@/lib/config-service"
import { ConnectionConfig } from "./connect-button"

interface VNCCanvasProps {
  isConnected: boolean
  viewOnly: boolean
  host?: string
  port?: string
  password?: string
  sessionId?: string
  wsConfig?: ConfigResponse | null
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
  wsConfig,
  onConnectionChange,
  onMouseMove,
  onMouseClick,
  onKeyPress,
}, ref) => {
  const containerRef = useRef<HTMLDivElement>(null)
  const rfbRef = useRef<NoVncClient | null>(null)
  const [connectionStatus, setConnectionStatus] = useState("Disconnected")
  const [mousePosition, setMousePosition] = useState({ x: 0, y: 0 })

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

        // Check if we have WebSocket config for auto-connect
        if (!wsConfig && (!host || !port)) {
          console.log("No connection details available")
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

        // Use WebSocket config for auto-connect if available, otherwise use manual config
        let url: string
        if (wsConfig && sessionId) {
          const protocol = wsConfig.protocol || 'ws'
          url = `${protocol}://${wsConfig.wsHost}:${wsConfig.wsPort}${wsConfig.wsUrl}`
          console.log("Using auto-connect WebSocket URL:", url)
        } else if (host && port && sessionId) {
          url = `ws://${host}:${port}/websockify/${sessionId}`
          console.log("Using manual WebSocket URL:", url)
        } else {
          console.error("No valid connection configuration available")
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

        rfb.addEventListener("disconnect", (e: any) => {
          setConnectionStatus("Disconnected")
          onConnectionChange?.(false)
          console.log("Disconnected from VNC server:", e.detail.clean ? "Clean" : "Unclean")
        })

        rfb.addEventListener("credentialsrequired", () => {
          setConnectionStatus("Authentication Required")
          console.log("Credentials required")
        })

        rfb.addEventListener("securityfailure", (e: any) => {
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
  }, [isConnected, host, port, password, viewOnly, wsConfig, sessionId, onConnectionChange])

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
