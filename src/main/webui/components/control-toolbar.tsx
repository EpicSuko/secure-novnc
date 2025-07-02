"use client"

import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import {
  Power,
  Maximize2,
  Minimize2,
  Keyboard,
  Copy,
  ClipboardPasteIcon as Paste,
} from "lucide-react"

interface ControlToolbarProps {
  isConnected: boolean
  isFullscreen: boolean
  onDisconnect: () => void
  onToggleFullscreen: () => void
  onSendCtrlAltDel: () => void
  onCopy: () => void
  onPaste: () => void
}

export function ControlToolbar({
  isConnected,
  isFullscreen,
  onDisconnect,
  onToggleFullscreen,
  onSendCtrlAltDel,
  onCopy,
  onPaste,
}: ControlToolbarProps) {
  return (
    <div className="flex items-center gap-2 p-2 bg-muted/50 dark:bg-gray-800/50 border-b dark:border-gray-700 overflow-x-auto">
      <Button
        variant="outline"
        size="sm"
        onClick={onDisconnect}
        disabled={!isConnected}
        className="text-red-600 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300 dark:border-gray-600 bg-transparent"
      >
        <Power className="w-4 h-4 mr-2" />
        Disconnect
      </Button>

      <Separator orientation="vertical" className="h-6 dark:bg-gray-600" />

      <Button
        variant="outline"
        size="sm"
        onClick={onSendCtrlAltDel}
        disabled={!isConnected}
        className="dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700 bg-transparent"
      >
        <Keyboard className="w-4 h-4 mr-2" />
        Ctrl+Alt+Del
      </Button>

      <Separator orientation="vertical" className="h-6 dark:bg-gray-600" />

      <Button
        variant="outline"
        size="sm"
        onClick={onCopy}
        disabled={!isConnected}
        className="dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700 bg-transparent"
      >
        <Copy className="w-4 h-4 mr-2" />
        Copy
      </Button>

      <Button
        variant="outline"
        size="sm"
        onClick={onPaste}
        disabled={!isConnected}
        className="dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700 bg-transparent"
      >
        <Paste className="w-4 h-4 mr-2" />
        Paste
      </Button>

      <Separator orientation="vertical" className="h-6 dark:bg-gray-600" />

      <Button
        variant="outline"
        size="sm"
        onClick={onToggleFullscreen}
        className="dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700 bg-transparent"
      >
        {isFullscreen ? <Minimize2 className="w-4 h-4 mr-2" /> : <Maximize2 className="w-4 h-4 mr-2" />}
        {isFullscreen ? "Exit Fullscreen" : "Fullscreen"}
      </Button>
    </div>
  )
}
