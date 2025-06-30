"use client"

import { useState } from "react"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Checkbox } from "@/components/ui/checkbox"
import { Settings } from "lucide-react"

interface ConnectionDialogProps {
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

export function ConnectionDialog({ onConnect, isConnected }: ConnectionDialogProps) {
  const [config, setConfig] = useState<ConnectionConfig>({
    host: "localhost",
    port: "5900",
    password: "",
    viewOnly: false,
    shared: true,
    quality: 6,
  })

  const handleConnect = () => {
    onConnect(config)
  }

  return (
    <Dialog>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm">
          <Settings className="w-4 h-4 mr-2" />
          {isConnected ? "Settings" : "Connect"}
        </Button>
      </DialogTrigger>
      <DialogContent className="sm:max-w-md dark:bg-gray-900 dark:border-gray-700">
        <DialogHeader>
          <DialogTitle className="dark:text-white">VNC Connection</DialogTitle>
        </DialogHeader>
        <div className="grid gap-4 py-4">
          <div className="grid grid-cols-4 items-center gap-4">
            <Label htmlFor="host" className="text-right dark:text-gray-200">
              Host
            </Label>
            <Input
              id="host"
              value={config.host}
              onChange={(e) => setConfig({ ...config, host: e.target.value })}
              className="col-span-3 dark:bg-gray-800 dark:border-gray-600 dark:text-white"
              placeholder="localhost"
            />
          </div>
          <div className="grid grid-cols-4 items-center gap-4">
            <Label htmlFor="port" className="text-right dark:text-gray-200">
              Port
            </Label>
            <Input
              id="port"
              value={config.port}
              onChange={(e) => setConfig({ ...config, port: e.target.value })}
              className="col-span-3 dark:bg-gray-800 dark:border-gray-600 dark:text-white"
              placeholder="5900"
            />
          </div>
          <div className="grid grid-cols-4 items-center gap-4">
            <Label htmlFor="password" className="text-right dark:text-gray-200">
              Password
            </Label>
            <Input
              id="password"
              type="password"
              value={config.password}
              onChange={(e) => setConfig({ ...config, password: e.target.value })}
              className="col-span-3 dark:bg-gray-800 dark:border-gray-600 dark:text-white"
            />
          </div>
          <div className="flex items-center space-x-2">
            <Checkbox
              id="viewOnly"
              checked={config.viewOnly}
              onCheckedChange={(checked) => setConfig({ ...config, viewOnly: checked as boolean })}
              className="dark:border-gray-600"
            />
            <Label htmlFor="viewOnly" className="dark:text-gray-200">
              View Only
            </Label>
          </div>
          <div className="flex items-center space-x-2">
            <Checkbox
              id="shared"
              checked={config.shared}
              onCheckedChange={(checked) => setConfig({ ...config, shared: checked as boolean })}
              className="dark:border-gray-600"
            />
            <Label htmlFor="shared" className="dark:text-gray-200">
              Shared Connection
            </Label>
          </div>
        </div>
        <div className="flex justify-end gap-2">
          <Button
            onClick={handleConnect}
            disabled={!config.host || !config.port}
            className="dark:bg-blue-700 dark:hover:bg-blue-600"
          >
            {isConnected ? "Reconnect" : "Connect"}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
