import { request } from '@/utils'

export function imageGenerate(data) {
  return request.post('/ai/image-generate', data)
}

export function imageGeneratePage(params) {
  return request.get('/ai/image-generate/page', { params })
}

export function imageGenerateGetResult(id) {
  return request.get(`/ai/image-generate/${id}`)
}

export function voiceAsr(formData) {
  return request.post('/ai/voice/asr', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function voiceTts(data) {
  return request.post('/ai/voice/tts', data)
}
