import { request } from '@/utils'

export function createSsoTicket(data) {
  return request.post('/auth/sso/ticket', data)
}
