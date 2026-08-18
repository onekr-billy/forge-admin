/**
 * RSA 加密工具
 * 使用 jsencrypt-ext 进行 RSA 加密
 */
import JSEncryptModule from 'jsencrypt-ext'
// jsencrypt-ext 是 CJS/UMD 包，命名导出 { JSEncrypt }；兼容 vite8(rolldown) 的默认导入互操作
const JSEncrypt = JSEncryptModule.JSEncrypt || JSEncryptModule.default?.JSEncrypt || JSEncryptModule

function wrapPublicKeyPem(publicKey) {
  if (!publicKey || publicKey.includes('-----BEGIN')) {
    return publicKey
  }
  const lines = publicKey.match(/.{1,64}/g)?.join('\n') || publicKey
  return `-----BEGIN PUBLIC KEY-----\n${lines}\n-----END PUBLIC KEY-----`
}

/**
 * RSA 加密
 * @param {string} data 要加密的数据
 * @param {string} publicKey RSA公钥（Base64格式）
 * @returns {string} 加密后的数据（Base64格式）
 */
export function rsaEncrypt(data, publicKey) {
  const encrypt = new JSEncrypt()
  encrypt.setPublicKey(wrapPublicKeyPem(publicKey))
  const encrypted = encrypt.encrypt(data)
  if (!encrypted) {
    throw new Error('RSA 加密失败')
  }
  return encrypted
}

/**
 * RSA 解密（通常只在后端使用）
 * @param {string} data 加密的数据（Base64格式）
 * @param {string} privateKey RSA私钥（Base64格式）
 * @returns {string} 解密后的数据
 */
export function rsaDecrypt(data, privateKey) {
  const decrypt = new JSEncrypt()
  decrypt.setPrivateKey(privateKey)
  const decrypted = decrypt.decrypt(data)
  if (!decrypted) {
    throw new Error('RSA 解密失败')
  }
  return decrypted
}
