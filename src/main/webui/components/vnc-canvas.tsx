"use client"

import { useEffect, useRef, useState, useCallback } from "react"
import { Card } from "@/components/ui/card"

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

export function VNCCanvas({
  isConnected,
  viewOnly,
  host,
  port,
  password,
  sessionId,
  onConnectionChange,
  onMouseMove,
  onMouseClick,
  onKeyPress,
}: VNCCanvasProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const rfbRef = useRef<any>(null)
  const [connectionStatus, setConnectionStatus] = useState("Disconnected")
  const [mousePosition, setMousePosition] = useState({ x: 0, y: 0 })

  // Initialize noVNC when component mounts and connection is requested
  useEffect(() => {
    const initNoVNC = async () => {
      if (typeof window === "undefined") return

      try {
        // Only connect if we have connection details and should be connected
        if (!isConnected || !host || !port) {
          // Clean up existing connection if disconnecting
          if (rfbRef.current) {
            rfbRef.current.disconnect()
            rfbRef.current = null
          }
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

        const url = `ws://${host}:${port}/websockify/${sessionId}`

        // Create new RFB connection
        const rfb = new RFB(containerRef.current, url, {
          credentials: { password: password || "" },
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

        // Store methods for external access
        if (containerRef.current) {
          ;(containerRef.current as any).sendCtrlAltDel = () => rfb.sendCtrlAltDel()
          ;(containerRef.current as any).sendClipboard = (text: string) => rfb.clipboardPasteFrom(text)
        }
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
  }, [isConnected, host, port, password, viewOnly, onConnectionChange])

  // Handle view-only mode changes
  useEffect(() => {
    if (rfbRef.current) {
      rfbRef.current.viewOnly = viewOnly
    }
  }, [viewOnly])

  // Expose methods for parent component
  const sendCtrlAltDel = useCallback(() => {
    if (rfbRef.current) {
      rfbRef.current.sendCtrlAltDel()
    }
  }, [])

  const sendClipboard = useCallback((text: string) => {
    if (rfbRef.current) {
      rfbRef.current.clipboardPasteFrom(text)
    }
  }, [])

  // Store methods on container for external access
  useEffect(() => {
    if (containerRef.current) {
      ;(containerRef.current as any).sendCtrlAltDel = sendCtrlAltDel
      ;(containerRef.current as any).sendClipboard = sendClipboard
    }
  }, [sendCtrlAltDel, sendClipboard])

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
}
