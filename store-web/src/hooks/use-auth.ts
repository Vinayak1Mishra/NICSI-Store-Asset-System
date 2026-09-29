'use client';

import { useState, useEffect, useCallback } from 'react';
import { api } from '@/lib/api-client';

export interface AuthUser {
  userId: string;
  username: string;
  displayName: string;
  roles: string[];
  permissions: string[];
}

export const MOCK_ROLES = [
  { username: 'admin', label: 'Admin (Full Access)', role: 'ROLE_ADMIN' },
  { username: 'store.manager', label: 'Store Manager', role: 'ROLE_STORE_MANAGER' },
  { username: 'store.officer', label: 'Store Officer', role: 'ROLE_STORE_OFFICER' },
  { username: 'store.operator', label: 'Store Operator', role: 'ROLE_STORE_OPERATOR' },
  { username: 'auditor', label: 'Auditor (Read-Only)', role: 'ROLE_AUDITOR' },
  { username: 'employee.john', label: 'Employee (Self-Service)', role: 'ROLE_EMPLOYEE' },
  { username: 'finance.officer', label: 'Finance Officer', role: 'ROLE_FINANCE' },
  { username: 'asset.manager', label: 'Asset Manager', role: 'ROLE_ASSET_MANAGER' },
];

export function useAuth() {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const login = useCallback(async (username: string) => {
    try {
      const response = await api.post<{ token: string; user: AuthUser }>(
        `/api/store/dev/token?username=${username}`,
        {}
      );
      if (typeof window !== 'undefined') {
        sessionStorage.setItem('nicsi_token', response.token);
        sessionStorage.setItem('nicsi_user', JSON.stringify(response.user));
      }
      setUser(response.user);
      return response.user;
    } catch (err) {
      console.warn('Backend unavailable, using local mock auth fallback:', err);
      // Fallback mock user if backend is not yet started
      const mock: AuthUser = {
        userId: '11111111-1111-1111-1111-111111111111',
        username,
        displayName: username === 'admin' ? 'System Administrator' : username,
        roles: [username === 'admin' ? 'ROLE_ADMIN' : 'ROLE_STORE_OFFICER'],
        permissions: ['ITEM_VIEW', 'ITEM_CREATE', 'ITEM_UPDATE', 'STORE_ADMIN'],
      };
      if (typeof window !== 'undefined') {
        sessionStorage.setItem('nicsi_token', 'dev-mock-token');
        sessionStorage.setItem('nicsi_user', JSON.stringify(mock));
      }
      setUser(mock);
      return mock;
    }
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    const token = sessionStorage.getItem('nicsi_token');
    const userData = sessionStorage.getItem('nicsi_user');
    if (token && userData) {
      try {
        setUser(JSON.parse(userData));
        setIsLoading(false);
        return;
      } catch {
        // Parse error, reset
      }
    }
    // Auto-login as admin by default in dev environment
    login('admin').finally(() => setIsLoading(false));
  }, [login]);

  const logout = useCallback(() => {
    if (typeof window !== 'undefined') {
      sessionStorage.removeItem('nicsi_token');
      sessionStorage.removeItem('nicsi_user');
    }
    setUser(null);
  }, []);

  return {
    user,
    isLoading,
    login,
    logout,
    isAuthenticated: !!user,
    hasPermission: (perm: string) => user?.permissions?.includes(perm) ?? false,
    hasRole: (role: string) => user?.roles?.includes(role) ?? false,
  };
}
