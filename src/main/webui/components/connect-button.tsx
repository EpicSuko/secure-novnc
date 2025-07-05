"use client"

import { Button } from "@/components/ui/button"
import { Monitor } from "lucide-react"

interface ConnectButtonProps {
  onConnect: (config: ConnectionConfig) => void
  isConnected: boolean
}

export interface ConnectionConfig {
  host: string
  port: string
  password: string
  viewOnly: boolean
  shared: boolean
  quality: number
}

export function ConnectButton({ onConnect, isConnected }: ConnectButtonProps) {
  const handleConnect = () => {
    // Use default connection settings for auto-connect
    const defaultConfig: ConnectionConfig = {
      host: "localhost", // Will be overridden by wsConfig from server
      port: "8080",      // Will be overridden by wsConfig from server
      password: "",      // No password needed for WebSocket proxy
      viewOnly: false,
      shared: true,
      quality: 6,
    }
    
    onConnect(defaultConfig)
  }

  return (
    <Button 
      variant="outline" 
      size="sm"
      onClick={handleConnect}
      disabled={isConnected}
      className="dark:border-gray-600 dark:text-gray-200 hover:dark:bg-gray-700"
    >
      <Monitor className="w-4 h-4 mr-2" />
      {isConnected ? "Connected" : "Connect"}
    </Button>
  )
} 