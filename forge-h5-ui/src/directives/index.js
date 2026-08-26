import loadingDirective, { loadingService } from './modules/loading'
import permissionDirective from './modules/permission'

export function setupDirectives(app) {
  app.directive('loading', loadingDirective)
  app.directive('permission', permissionDirective)
  app.config.globalProperties.$loading = loadingService
}

export { loadingService }
