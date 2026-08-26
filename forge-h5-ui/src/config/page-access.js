import { HOME_PAGE, LOGIN_PAGE } from '@/utils/route'

export const PAGE_ACCESS_RULES = [
  {
    path: LOGIN_PAGE,
    public: true,
    redirectWhenAuthenticated: HOME_PAGE,
  },
]

export const DEFAULT_PAGE_ACCESS = {
  requiresLogin: true,
  permissions: [],
  permissionMode: 'any',
  denyRedirect: HOME_PAGE,
}

