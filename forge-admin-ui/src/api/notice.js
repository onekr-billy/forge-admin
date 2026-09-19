import { request } from '@/utils/request'

export const noticeApi = {
  page: (pageNum = 1, pageSize = 50) => request.get('/system/notice/user/page', {
    params: { pageNum, pageSize },
    needTip: false,
  }),
  unreadCount: () => request.get('/system/notice/user/unread-count', { needTip: false }),
  detail: noticeId => request.get(`/system/notice/user/${encodeURIComponent(noticeId)}`, { needTip: false }),
  markRead: noticeId => request.post('/system/notice/markAsRead', null, {
    params: { noticeId },
    needTip: false,
  }),
}
