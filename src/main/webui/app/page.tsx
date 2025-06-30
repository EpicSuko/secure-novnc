"use client"

import { useState } from "react"
import { LoginForm } from "../components/login-form"
import VNCClient from "../vnc-client"
import { ThemeProvider } from "@/components/theme-provider"
import { authService, AuthResponse } from "../lib/auth-service"

interface User {
  username: string
  isAuthenticated: boolean
  sessionId?: string
  wsUrl?: string
}

export default function Page() {
  const [user, setUser] = useState<User | null>(null)

  const handleLogin = async (username: string, password: string): Promise<boolean> => {
    try {
      const authResponse: AuthResponse = await authService.authenticate(username, password)
      
      if (authResponse.success && authResponse.sessionId) {
        console.log('Authentication successful:', authResponse);
        setUser({
          username: authResponse.userId,
          isAuthenticated: true,
          sessionId: authResponse.sessionId,
          wsUrl: authResponse.wsUrl
        })
        return true
      }
      
      return false
    } catch (error) {
      console.error('Authentication error:', error)
      throw error // Re-throw to let the login form handle the error message
    }
  }

  const handleLogout = async () => {
    if (user?.sessionId) {
      await authService.logout(user.sessionId)
    }
    setUser(null)
  }

  return (
    <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
      {!user?.isAuthenticated ? (
        <LoginForm onLogin={handleLogin} />
      ) : (
        <VNCClient 
          username={user.username} 
          onLogout={() => handleLogout()}
          sessionId={user.sessionId}
        />
      )}
    </ThemeProvider>
  )
}
