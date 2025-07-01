import { Badge } from "@/components/ui/badge"
import { Separator } from "@/components/ui/separator"
import { Wifi, WifiOff, Lock, Unlock, Loader2 } from "lucide-react"
import { useState, useEffect } from "react"

interface StatusBarProps {
  isConnected: boolean
  connectionStatus: string
  serverInfo?: {
    name: string
    width: number
    height: number
    pixelFormat: string
  }
  encrypted: boolean
  latency?: number
  isLatencyLoading?: boolean
  latencyDetails?: {
    browserToProxy: number
    proxyToVNC: number
    totalEndToEnd: number
    timestamp: number
  }
}

export function StatusBar({ isConnected, connectionStatus, serverInfo, encrypted, latency, isLatencyLoading, latencyDetails }: StatusBarProps) {
  const [currentTime, setCurrentTime] = useState(new Date())

  useEffect(() => {
    let timer: number | null = null

    const updateTime = () => {
      setCurrentTime(new Date())
    }

    const startTimer = () => {
      if (timer) clearInterval(timer)
      timer = window.setInterval(updateTime, 60000) // Update every 60 seconds
    }

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        updateTime() // Update immediately when becoming visible
        startTimer()
      } else {
        if (timer) {
          clearInterval(timer)
          timer = null
        }
      }
    }

    // Initial update
    updateTime()
    
    // Start timer
    startTimer()

    // Add visibility change listener
    document.addEventListener('visibilitychange', handleVisibilityChange)

    return () => {
      if (timer) clearInterval(timer)
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
  }, [])

  return (
    <div className="flex items-center justify-between p-2 bg-muted/30 dark:bg-gray-800/30 border-t dark:border-gray-700 text-sm min-h-[40px]">
      <div className="flex items-center gap-4">
        <div className="flex items-center gap-2">
          {isConnected ? (
            <Wifi className="w-4 h-4 text-green-600 dark:text-green-400" />
          ) : (
            <WifiOff className="w-4 h-4 text-red-600 dark:text-red-400" />
          )}
          <Badge
            variant={isConnected ? "default" : "destructive"}
            className={isConnected ? "dark:bg-green-700 dark:text-green-100" : "dark:bg-red-800 dark:text-red-100"}
          >
            {connectionStatus}
          </Badge>
        </div>

        {serverInfo && (
          <>
            <Separator orientation="vertical" className="h-4 dark:bg-gray-600" />
            <span className="text-muted-foreground dark:text-gray-400">
              {serverInfo.name} - {serverInfo.width}x{serverInfo.height}
            </span>
          </>
        )}

        <div className="flex items-center gap-2">
          {encrypted ? (
            <Lock className="w-4 h-4 text-green-600 dark:text-green-400" />
          ) : (
            <Unlock className="w-4 h-4 text-yellow-600 dark:text-yellow-400" />
          )}
          <span className="text-muted-foreground dark:text-gray-400">{encrypted ? "Encrypted" : "Unencrypted"}</span>
        </div>
      </div>

      <div className="flex items-center gap-4">
        {latency !== undefined && (
          <div className="flex items-center gap-2">
            <div className="flex items-center gap-x-3">
              <span className="text-muted-foreground dark:text-gray-400 text-xs">
                {`Total: ${latency}ms`}
              </span>
              {latencyDetails && (
                <span className="text-xs text-muted-foreground/70 dark:text-gray-500 flex items-center gap-x-1">
                  <span title="Browser to Proxy">B→P: {latencyDetails.browserToProxy}ms</span>
                  <span>|</span>
                  <span title="Proxy to VNC">P→V: {latencyDetails.proxyToVNC}ms</span>
                </span>
              )}
            </div>
            {isLatencyLoading ? (
              <span title="Measuring latency...">
                <Loader2 className="w-3 h-3 animate-spin" />
              </span>
            ) : (
              <div className={`w-2 h-2 rounded-full ${
                latency < 20 ? 'bg-green-500' : 
                latency < 50 ? 'bg-yellow-500' : 
                'bg-red-500'
              }`} title={`${latency < 20 ? 'Excellent' : latency < 50 ? 'Good' : 'Poor'} latency`} />
            )}
          </div>
        )}
        <span className="text-muted-foreground dark:text-gray-400">{currentTime.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
      </div>
    </div>
  )
}
