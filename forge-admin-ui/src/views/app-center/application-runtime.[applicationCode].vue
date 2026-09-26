<template>
  <div class="application-runtime-page" :class="{ 'is-loading': loading, 'is-ready': !loading && application }">
    <!-- 骨架屏加载态（与路由 Suspense fallback 同款，避免 chunk 加载完又闪一下） -->
    <ApplicationRuntimeSkeleton v-if="loading" />
    <template v-else-if="application">
      <header v-if="!formDesignerMode" class="runtime-header">
        <div class="runtime-brand">
          <n-button quaternary circle :aria-label="resolveRuntimeBackLabel()" @click="handleRuntimeBack">
            <template #icon>
              <NIcon><ArrowBackOutline /></NIcon>
            </template>
          </n-button>
          <button type="button" class="runtime-breadcrumb" :title="resolveRuntimeBackLabel()" @click="handleRuntimeBack">
            <span>{{ resolveRuntimeBackLabel() }}</span>
            <span aria-hidden="true">›</span>
          </button>
          <div class="runtime-brand-copy">
            <span class="runtime-brand-app-icon" aria-hidden="true"><NIcon><FolderOpenOutline /></NIcon></span>
            <div class="runtime-design-title">
              <span>{{ resolveRuntimeBrandEyebrow() }}</span>
              <strong>{{ application.applicationName || '未命名应用' }}</strong>
            </div>
            <span class="runtime-brand-status">{{ resolveRuntimeBrandStatus() }}</span>
          </div>
        </div>
        <nav class="runtime-app-tabs" aria-label="应用导航">
          <!-- 非编辑模式：应用级 Tab -->
          <template v-if="!editing && !isDraftPreviewMode()">
            <button type="button" class="runtime-app-tab" :class="{ active: runtimeViewMode === 'pages' }" @click="switchRuntimeView('pages')">
              页面管理
            </button>
            <button type="button" class="runtime-app-tab" :class="{ active: runtimeViewMode === 'process' }" @click="switchRuntimeView('process')">
              业务流程
            </button>
            <button type="button" class="runtime-app-tab" :class="{ active: runtimeViewMode === 'enhance' }" @click="switchRuntimeView('enhance')">
              增强
            </button>
            <button type="button" class="runtime-app-tab" :class="{ active: runtimeViewMode === 'settings' }" @click="switchRuntimeView('settings')">
              应用设置
            </button>
            <button type="button" class="runtime-app-tab" @click="switchRuntimeView('publish')">
              应用发布
            </button>
          </template>
          <!-- 编辑模式：页面级 Tab -->
          <template v-else>
            <button
              v-if="showPageDesignTab"
              type="button"
              class="runtime-app-tab"
              :class="{ active: isPageDesignTabActive }"
              @click="switchPageDesignTab('page')"
            >
              页面设计
            </button>
            <button
              v-if="showFormDesignTab"
              type="button"
              class="runtime-app-tab"
              :class="{ active: activePageDesignTab === 'form' }"
              @click="switchPageDesignTab('form')"
            >
              表单设计
            </button>
            <button
              v-if="showListDesignTab"
              type="button"
              class="runtime-app-tab"
              :class="{ active: activePageDesignTab === 'list' }"
              @click="switchPageDesignTab('list')"
            >
              列表设计
            </button>
            <button type="button" class="runtime-app-tab" :class="{ active: activePageDesignTab === 'settings' }" @click="switchPageDesignTab('settings')">
              页面设置
            </button>
            <button type="button" class="runtime-app-tab" :class="{ active: activePageDesignTab === 'publish' }" @click="switchPageDesignTab('publish')">
              发布
            </button>
          </template>
        </nav>
        <div v-if="!formDesignerMode" class="runtime-header-actions">
          <n-popover
            v-if="editing"
            trigger="click"
            placement="bottom-end"
            :show-arrow="false"
            :disabled="!pageBuilderResourceActive"
          >
            <template #trigger>
              <n-button
                quaternary
                :disabled="!pageBuilderResourceActive"
                title="页面表单资产"
              >
                <NIcon><FolderOpenOutline /></NIcon>
                页面资源
              </n-button>
            </template>
            <div class="application-form-assets-popover">
              <div class="application-form-assets-popover-head">
                <div>
                  <strong>页面表单资产</strong>
                  <small>统一设计字段、布局和录入体验</small>
                </div>
                <n-button size="tiny" type="primary" @click="createStandaloneFormAsset">
                  新建表单
                </n-button>
              </div>
              <button
                v-for="asset in formAssets"
                :key="asset.id"
                type="button"
                class="application-form-asset-row"
                @click="openFormAssetDesigner(asset.id)"
              >
                <span>
                  <strong>{{ asset.name }}</strong>
                  <small>{{ resolveFormAssetFields(asset).length }} 个字段</small>
                </span>
                <span>编辑</span>
              </button>
              <n-empty v-if="!formAssets.length" size="small" description="还没有页面表单，先创建一个" />
            </div>
          </n-popover>
          <n-button
            v-if="editing || (canEditApplication && dirty)"
            :disabled="!dirty && !embeddedDesignerDirty"
            :loading="saving || embeddedDesignerSaving"
            title="保存当前设计草稿"
            secondary
            @click="saveCurrentDesignerSection"
          >
            <template #icon>
              <NIcon><SaveOutline /></NIcon>
            </template>
            保存草稿
          </n-button>
          <n-button v-if="editing" quaternary :disabled="!previewableResourceActive" title="预览草稿" @click="openDraftPreview">
            <NIcon><EyeOutline /></NIcon>
            预览
          </n-button>
          <n-dropdown v-if="editing" trigger="click" placement="bottom-end" :options="runtimeHeaderMoreOptions" @select="handleRuntimeHeaderMoreSelect">
            <n-button quaternary circle aria-label="更多操作">
              <template #icon>
                <NIcon><EllipsisHorizontalOutline /></NIcon>
              </template>
            </n-button>
          </n-dropdown>
        </div>
      </header>

      <ApplicationDesignerResourceTree
        v-if="!editing && false"
        :groups="designerResourceGroups"
        :active-group-key="designerSection"
        :active-key="activeDesignerResource?.key || ''"
        :application-name="application.applicationName"
        :node-menu-options="resolveResourceNodeMenuOptions"
        @select="selectDesignerResource"
        @create-page="openPageTypeSelector()"
        @node-action="handleResourceNodeAction"
      />

      <section v-if="showFormDesignWorkbench" class="application-form-asset-workbench">
        <DesignerAsyncLoader
          v-if="designerTransitionLoading"
          title="正在准备页面设计"
          description="正在保存页面草稿并挂载表单资产，请稍候"
        />
        <template v-else>
          <!-- 表单设计器模式：极简顶栏（返回 + 数据对象名 + 保存） -->
          <div v-if="formDesignerMode" class="form-designer-topbar">
            <div class="form-designer-topbar-left">
              <n-button quaternary circle size="small" title="返回页面设计" @click="returnToPageDesigner">
                <template #icon>
                  <NIcon><ArrowBackOutline /></NIcon>
                </template>
              </n-button>
              <template v-if="activePageShapeDesign">
                <span class="form-designer-topbar-label">数据对象</span>
                <n-input
                  v-model:value="activePageShapeDesign.objectName"
                  size="tiny"
                  class="form-designer-topbar-name"
                  maxlength="100"
                  placeholder="对象名称"
                  @update:value="syncActivePageShapeObject"
                />
              </template>
              <span v-else class="form-designer-topbar-title">{{ activeFormAsset?.name || '表单设计' }}</span>
            </div>
            <div class="form-designer-topbar-actions">
              <span v-if="activeFormDataState.status === 'error'" class="form-data-save-error">{{ activeFormDataState.message }}</span>
              <n-button size="small" secondary @click="returnToPageDesigner">
                返回
              </n-button>
              <n-button
                size="small"
                type="primary"
                :disabled="!canSaveActiveFormDesigner || saving"
                :loading="saving"
                @click="saveActiveFormDesigner(true)"
              >
                {{ activeFormDataState.status === 'error' ? '重试' : '保存' }}
              </n-button>
            </div>
          </div>
          <div v-if="activeFormAsset" class="application-form-asset-designer">
            <ForgeFormDesigner
              :key="activeFormAsset.id"
              :model-value="activeFormDesignerSchema"
              :fields="activeFormFields"
              :object-code="activePageShapeDesign?.objectCode || activeFormDesignerContext?.objectCode || application.applicationCode"
              :object-name="activePageShapeDesign?.objectName || activeFormDesignerContext?.objectName || activeFormAsset.name"
              :relations="activeFormDesignerRelations"
              :actions="activeFormDesignerActions"
              :enable-sections-view="false"
              :derive-sections-from-layout="true"
              @update:model-value="updateActiveFormDesignerSchema"
            />
          </div>
          <n-empty v-else description="当前页面还没有表单，先创建一个再设计字段">
            <template #extra>
              <n-button type="primary" @click="createFormAssetForCurrentPage">
                创建表单
              </n-button>
            </template>
          </n-empty>
        </template>
      </section>

      <section v-else-if="editing && activePageDesignTab === 'list'" class="application-design-section">
        <BusinessObjectDesignerPage
          v-if="pageDesignObject"
          :key="`page-list:${pageDesignObject.objectId || pageDesignObject.objectCode}`"
          ref="embeddedDesignerRef"
          embedded
          :embedded-object-code="pageDesignObject.objectCode"
          :embedded-object-id="pageDesignObject.objectId"
          :embedded-suite-code="application.suiteCode"
          initial-panel="list"
          :embedded-nav-panels="['list']"
          @dirty-change="embeddedDesignerDirty = $event"
          @saved="handleEmbeddedDesignerSaved"
        />
        <n-empty v-else description="当前页面尚未绑定数据对象，无法设计列表">
          <template #extra>
            <n-button type="primary" @click="openObjectSetup">
              配置数据对象
            </n-button>
          </template>
        </n-empty>
      </section>

      <!-- 编辑模式：页面设置（须排在自由布局画布之前，避免 editing 兜底抢占） -->
      <section v-else-if="editing && activePageDesignTab === 'settings'" class="runtime-inline-panel">
        <PageDesignSettingsPanel
          v-if="currentNode"
          :node="currentNode"
          :application="application"
          :objects="objects"
          :layout-polluted="currentPageLayoutPolluted"
          :restoring="restoringObjectPageLayout"
          @update="patchCurrentPageNode"
          @restore-layout="restoreCurrentObjectPageLayout"
        />
        <n-empty v-else description="请先选择要设置的页面" />
      </section>

      <!-- 编辑模式：发布 -->
      <section v-else-if="editing && activePageDesignTab === 'publish'" class="runtime-inline-panel">
        <PageDesignPublishPanel
          v-if="currentNode"
          :application="application"
          :node="currentNode"
          :page-id="currentNode.id"
          :page-title="currentNode.title"
          :dirty="dirty"
          :saving="saving"
          :config-key="pageDesignObject?.configKey || currentNode.objectRef?.configKey || ''"
          :objects="objects"
          @save="saveCurrentDesignerSection"
          @preview="openDraftPreview"
          @update="patchCurrentPageNode"
        />
        <n-empty v-else description="请先选择要发布的页面" />
      </section>

      <div
        v-else-if="(!editing && runtimeViewMode === 'pages') || showFreeLayoutCanvas"
        class="runtime-body"
        :class="{
          'configuring': editing && configPanelVisible,
          'sidebar-collapsed': sidebarCollapsed,
          'editing-canvas': showFreeLayoutCanvas,
        }"
      >
        <aside
          v-if="!editing"
          class="runtime-navigation base-app-sidebar__vertical no-page-group"
          :class="{ collapsed: sidebarCollapsed }"
        >
          <div class="title_wrapper">
            <div class="application-sidebar-title base-app-sidebar__title_bar">
              <div class="base-app-title-wrapper">
                <span class="application-icon-slot" aria-hidden="true">
                  <AuthImage :src="tenantStore.systemLogo" :fallback="defaultLogo" alt="" />
                </span>
                <n-input
                  v-if="renamingApplication"
                  ref="renameInputRef"
                  v-model:value="renameApplicationValue"
                  size="tiny"
                  class="sidebar-rename-input"
                  :disabled="renameSaving"
                  placeholder="应用名称"
                  @keydown.enter.prevent="confirmRenameApplication"
                  @keydown.escape.prevent="cancelRenameApplication"
                  @blur="confirmRenameApplication"
                />
                <strong
                  v-else
                  class="base-app-title-content"
                  :class="{ editable: canEditApplication && !editing }"
                  :title="canEditApplication && !editing ? '点击修改应用名称' : ''"
                  @click="canEditApplication && !editing && startRenameApplication()"
                >{{ application.applicationName }}</strong>
              </div>
              <div class="sidebar-title-actions">
                <button class="sidebar-collapse-hint" type="button" :aria-label="sidebarCollapsed ? '展开页面菜单' : '收起页面菜单'" :title="sidebarCollapsed ? '展开页面菜单' : '收起页面菜单'" @click="sidebarCollapsed = !sidebarCollapsed">
                  {{ sidebarCollapsed ? '»' : '«' }}
                </button>
              </div>
            </div>
          </div>
          <div class="navigation-list scroll_wrapper">
            <div class="list_wrapper base-app-sidebar__list_vertical">
              <div
                v-for="item in pageManagementSystemPages"
                :key="item.id"
                class="navigation-row base-app-sidebar__node_vertical"
                :class="{ 'base-app-sidebar__node_selected': item.id === selectedNodeId }"
              >
                <button
                  class="navigation-page"
                  :class="{ active: item.id === selectedNodeId }"
                  type="button"
                  @click="selectPageManagementNode(item.id)"
                >
                  <span class="navigation-icon-slot" aria-hidden="true">
                    <NIcon v-if="item.icon"><component :is="resolveSystemPageIcon(item.icon)" /></NIcon>
                  </span>
                  <span>{{ item.title }}</span>
                </button>
                <button
                  v-if="item.id === WORKBENCH_PAGE_ID && canEditApplication"
                  type="button"
                  class="navigation-action navigation-edit-icon"
                  title="设计个人工作台"
                  @click.stop="enterWorkbenchDesign()"
                >
                  <NIcon size="14">
                    <CreateOutline />
                  </NIcon>
                </button>
              </div>
              <div class="navigation-section-divider" />
              <template v-for="item in navigationNodes" :key="item.id">
                <div class="navigation-row base-app-sidebar__node_vertical" :class="{ 'base-app-sidebar__node_selected': item.id === selectedNodeId, 'is-nav-hidden': !isNavigationVisible(item) }" :style="{ paddingLeft: `${12 + item.depth * 16}px` }">
                  <button
                    v-if="item.type === 'page'"
                    class="navigation-page"
                    :class="{ active: item.id === selectedNodeId }"
                    type="button"
                    @click="selectPageManagementNode(item.id)"
                  >
                    <span v-if="item.icon" class="navigation-icon-slot" aria-hidden="true">
                      <IconRenderer v-if="item.icon" :icon="item.icon" :size="16" />
                    </span>
                    <span>{{ item.title }}</span>
                  </button>
                  <button
                    v-else
                    type="button"
                    class="navigation-group"
                    :class="{ collapsed: isGroupCollapsed(item.id) }"
                    :aria-expanded="!isGroupCollapsed(item.id)"
                    @click="toggleGroupExpanded(item.id)"
                  >
                    <NIcon size="12" class="navigation-group-chevron">
                      <ArrowDownOutline />
                    </NIcon>
                    <span>{{ item.title }}</span>
                  </button>
                  <!-- 页面项的编辑按钮（hover 显示） -->
                  <button
                    v-if="item.type === 'page' && canEditApplication"
                    type="button"
                    class="navigation-action navigation-edit-icon"
                    title="设计页面"
                    @click.stop="enterPageDesign(item.id)"
                  >
                    <NIcon size="14">
                      <CreateOutline />
                    </NIcon>
                  </button>
                  <!-- “更多”下拉菜单（hover 显示），包含重命名/图标/隐藏/复制/移动/删除 -->
                  <n-dropdown
                    v-if="canEditApplication"
                    trigger="click"
                    placement="bottom-end"
                    :options="resolveNavigationMoreOptions(item)"
                    @select="key => handleNavigationMoreSelect(key, item)"
                  >
                    <button type="button" class="navigation-action navigation-more-icon" :aria-label="`${item.title}更多操作`" title="更多操作" @click.stop>
                      <span aria-hidden="true">•••</span>
                    </button>
                  </n-dropdown>
                </div>
              </template>
            </div>
          </div>
          <div v-if="canEditApplication" class="new_node_wrapper">
            <n-popover v-model:show="newNodePopoverVisible" trigger="click" placement="right-end" :show-arrow="false">
              <template #trigger>
                <button type="button" class="navigation-create base-app-sidebar__new_node_vertical">
                  <span>+</span>新建
                </button>
              </template>
              <div class="new-node-popover">
                <button type="button" class="new-node-choice" @click="openPageTypeSelector()">
                  <span class="new-node-choice-icon"><NIcon><DocumentTextOutline /></NIcon></span>
                  <span><strong>新建页面</strong><small>创建空白页后按需添加组件</small></span>
                </button>
                <button type="button" class="new-node-choice" @click="createQuickNode('group')">
                  <span class="new-node-choice-icon group"><NIcon><FolderOpenOutline /></NIcon></span>
                  <span><strong>新建页面组</strong><small>用于归类多个页面</small></span>
                </button>
              </div>
            </n-popover>
          </div>
          <div v-if="iconPickerVisible" class="navigation-icon-picker">
            <div class="navigation-icon-picker-head">
              <span>选择图标</span>
              <button type="button" aria-label="关闭图标选择" @click="iconPickerVisible = false">
                ×
              </button>
            </div>
            <IconSelector v-model="navigationIconValue" />
          </div>
        </aside>

        <main class="runtime-main">
          <section v-if="currentSystemPage && !editing" class="page-surface">
            <PageManagementSystemView
              :view="currentSystemPage.view"
              :title="currentSystemPage.title"
              :navigation-routes="systemPageNavigationRoutes"
              :workbench-page="workbenchPage"
              :objects="objects"
              :application-id="String(application?.id || '')"
              :application-code="application?.applicationCode || ''"
            />
          </section>
          <section v-else-if="!currentNode" class="application-empty-state">
            <div class="application-empty-intro">
              <div>
                <span class="application-empty-eyebrow">页面管理</span>
                <h1>开始设计你的第一个页面</h1>
                <p>选择页面形态，直接在页面上添加字段，系统会自动为你生成数据表。</p>
                <n-space v-if="canEditApplication">
                  <n-button type="primary" class="application-first-page-button" @click="openPageTypeSelector()">
                    创建数据页
                  </n-button>
                  <n-button secondary @click="openCustomPageSelector()">
                    创建自由布局页面
                  </n-button>
                  <n-button secondary @click="openExcelPageImport()">
                    从 Excel 创建页面
                  </n-button>
                </n-space>
              </div>
              <button v-if="editing" type="button" class="application-create-group-card" @click="createQuickNode('group')">
                <span class="application-create-group-icon" aria-hidden="true"><NIcon><FolderOpenOutline /></NIcon></span>
                <span>
                  <strong>新建页面组</strong>
                  <small>用于组织多个页面</small>
                </span>
                <i aria-hidden="true">→</i>
              </button>
            </div>
            <section v-if="editing" class="application-template-section" aria-label="页面模板">
              <div class="application-empty-section-head">
                <span class="application-section-kicker"><NIcon><AppsOutline /></NIcon>页面模板</span>
                <span>选择后立即创建</span>
              </div>
              <div class="application-template-grid">
                <button
                  v-for="template in pageTemplateOptions"
                  :key="template.key"
                  type="button"
                  class="application-template-card"
                  :class="{ selected: selectedPageTemplateKey === template.key }"
                  @click="selectIntroTemplate(template.key)"
                >
                  <span class="application-template-icon" :class="`kind-${template.key}`" aria-hidden="true">
                    <NIcon><component :is="resolvePageTemplateIcon(template)" /></NIcon>
                  </span>
                  <span>
                    <strong>{{ template.label }}</strong>
                    <small>{{ template.description }}</small>
                    <em>立即创建 <i aria-hidden="true">→</i></em>
                  </span>
                </button>
              </div>
            </section>
            <section v-if="editing" class="application-component-section" aria-label="常用组件">
              <div class="application-empty-section-head">
                <span class="application-section-kicker"><NIcon><AddOutline /></NIcon>常用组件</span>
                <span>创建空白页并直接放入组件</span>
              </div>
              <div class="application-component-grid">
                <button v-for="item in recommendedComponents" :key="item.blockType" type="button" @click="createPageFromTemplate('blank', item.blockType)">
                  <span class="empty-component-icon" :class="`kind-${resolveComponentPickerGroup(item)}`" aria-hidden="true">
                    <NIcon><component :is="resolveEmptyGuideIcon(item)" /></NIcon>
                  </span>
                  <span>{{ item.title }}</span>
                </button>
              </div>
            </section>
            <span v-else class="application-empty-readonly">页面尚未配置</span>
          </section>
          <section v-else-if="!editing" class="page-surface is-fill">
            <n-alert
              v-if="currentPageLayoutPolluted"
              type="warning"
              :bordered="false"
              class="page-layout-pollution-alert"
              title="该列表/表单页被自由布局组件污染"
            >
              <div class="page-layout-pollution-alert__body">
                <span>中间预览里的自由布局内容可以一键清掉，恢复为原来的列表+表单（AiCrud）布局。</span>
                <n-button type="warning" size="small" :loading="restoringObjectPageLayout" @click="restoreCurrentObjectPageLayout">
                  恢复列表/表单布局
                </n-button>
              </div>
            </n-alert>
            <PortalPageRenderer
              :key="`portal:${portalCrudConfigRevision}`"
              :node="currentNode"
              :page="currentPage"
              :objects="objects"
              :entries="workspaceEntries"
              :extensions="workspaceExtensions"
              :application-id="String(application?.id || '')"
              :application-code="application?.applicationCode || ''"
              :page-id="currentNode?.id || ''"
              :configurable="false"
              :design-preview="usePortalDesignPreview"
              :crud-config-revision="portalCrudConfigRevision"
              :seed-runtime-crud-props="portalCrudSeed"
              :form-fields-resolver="resolvePortalFormFields"
              fill-host
            />
          </section>
          <section v-else class="page-surface">
            <n-alert
              v-if="currentPageLayoutPolluted"
              type="warning"
              :bordered="false"
              class="page-layout-pollution-alert"
              title="检测到自由布局组件污染了对象页"
            >
              <div class="page-layout-pollution-alert__body">
                <span>点下方按钮可清空这些组件，并恢复为列表+表单页标准布局。</span>
                <n-button type="warning" size="small" :loading="restoringObjectPageLayout" @click="restoreCurrentObjectPageLayout">
                  恢复列表/表单布局
                </n-button>
              </div>
            </n-alert>
            <section v-if="currentNode?.pageType === 'object'" class="object-page-card">
              <strong>{{ currentNode.objectRef?.objectName || currentNode.title || '未绑定数据对象' }}</strong>
              <p>{{ currentNode.objectRef?.valid === false ? '绑定的数据对象已不可用，请重新选择。' : '该页面复用已有对象的列表、表单、详情和数据管理配置。' }}</p>
              <n-space v-if="editing && currentNode.objectRef?.objectCode" size="small">
                <n-button type="primary" secondary @click="openFormAssetDesignerForPage(currentNode.id)">
                  编辑表单设计
                </n-button>
                <n-button v-if="currentPageLayoutPolluted" secondary type="warning" :loading="restoringObjectPageLayout" @click="restoreCurrentObjectPageLayout">
                  恢复列表/表单布局
                </n-button>
              </n-space>
            </section>

            <div v-if="editing && currentNode" class="canvas-component-anchor" :class="{ 'moving': componentButtonMoveCtx, 'is-default-position': !hasCustomComponentButtonPosition }" :style="componentButtonStyle" @pointerdown.capture="startComponentButtonMove">
              <n-popover v-model:show="componentPopoverVisible" trigger="click" placement="top-start" :show-arrow="false">
                <template #trigger>
                  <button type="button" class="component-add-trigger" aria-label="添加组件" title="添加组件">
                    <span class="component-add-icon" aria-hidden="true">+</span>
                    <span class="component-add-label">添加组件</span>
                  </button>
                </template>
                <div class="component-popover">
                  <n-input v-model:value="componentKeyword" clearable size="small" placeholder="搜索组件" class="component-search-input" />
                  <div v-if="componentPickerGroups.length" class="component-picker-groups">
                    <section v-for="group in componentPickerGroups" :key="group.key" class="component-picker-group">
                      <h3>{{ group.label }}</h3>
                      <div class="component-picker-grid">
                        <button
                          v-for="item in group.items"
                          :key="item.blockType"
                          type="button"
                          :draggable="false"
                          @pointerdown.stop="startCatalogPointerDrag($event, item)"
                          @dragstart="handleComponentCatalogDragStart($event, item)"
                          @dragend="handleComponentCatalogDragEnd"
                          @click="handleComponentCatalogClick(item, $event)"
                        >
                          <span class="component-icon-slot" :class="`kind-${group.key}`" aria-hidden="true">
                            <img v-if="resolveComponentIcon(item)" :src="resolveComponentIcon(item)" alt="">
                            <svg v-else-if="group.key === 'list'" viewBox="0 0 24 24"><path d="M7 6h11M7 12h11M7 18h11M3.5 6h.01M3.5 12h.01M3.5 18h.01" /></svg>
                            <svg v-else-if="group.key === 'chart'" viewBox="0 0 24 24"><path d="M4 19V5m0 14h16M8 16v-4m4 4V8m4 8V6" /></svg>
                            <svg v-else-if="group.key === 'view'" viewBox="0 0 24 24"><rect x="4" y="5" width="16" height="14" rx="2" /><path d="M4 9h16M8 13h8" /></svg>
                            <svg v-else viewBox="0 0 24 24"><path d="M12 4v16M4 12h16" /><circle cx="12" cy="12" r="7" /></svg>
                          </span>
                          <span class="component-item-copy">
                            <span class="component-item-heading">
                              <strong>{{ item.title }}</strong>
                              <small v-if="item.techTitle" :title="`技术组件：${item.techTitle}`">{{ item.techTitle }}</small>
                            </span>
                            <span class="component-item-desc">{{ item.desc }}</span>
                          </span>
                        </button>
                      </div>
                    </section>
                  </div>
                  <n-empty v-else size="small" description="没有匹配的组件" />
                </div>
              </n-popover>
            </div>

            <div class="application-grid-host">
              <draggable
                :model-value="pageBlocks"
                item-key="id"
                handle=".page-block-drag-handle"
                class="application-page-flow"
                :class="{ 'is-editing': editing, 'is-flow-stack': pageFlowStackMode }"
                :style="{ minHeight: `${pageFlowHeight}px`, padding: pageFlowStackMode ? pageCanvasPaddingCss : '0px' }"
                :disabled="true"
                :animation="180"
                :force-fallback="true"
                fallback-class="page-block-drag-shadow"
                :fallback-on-body="true"
                :fallback-tolerance="2"
                ghost-class="page-block-ghost"
                chosen-class="page-block-chosen"
                @dragenter="handlePageFlowDragOver"
                @dragover="handlePageFlowDragOver"
                @drop="handlePageFlowDrop"
                @click="handlePageFlowBlankClick"
                @update:model-value="updatePageBlocks"
              >
                <template #item="{ element: block }">
                  <section
                    class="application-page-block"
                    :class="{ selected: editing && selectedPageBlockId === block.id, editing, dragging: draggingPageBlockId === block.id, 'is-resize-collision': resizeCollisionBlockIds.includes(block.id) }"
                    :style="resolvePageBlockShellStyle(block)"
                    :data-page-block-id="block.id"
                    :data-page-block-type="block.blockType"
                    :data-grid-container-id="isSimpleBodyContainer(block) ? block.id : undefined"
                    :data-grid-cell-key="isSimpleBodyContainer(block) ? '__body__' : undefined"
                    :data-page-container-id="isSimpleBodyContainer(block) ? block.id : undefined"
                    :data-page-container-type="isSimpleBodyContainer(block) ? block.blockType : undefined"
                    @click.stop="selectPageBlock(block.id); openPageBlockConfiguration(block)"
                  >
                    <div v-if="editing" class="page-block-node-overlay">
                      <span
                        class="page-block-drag-handle"
                        title="拖动区块"
                        @pointerdown.stop="startPageBlockMove(block, $event)"
                        @click.stop
                      >
                        <svg width="1em" height="1em" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                          <path d="M8.25 6.5a1.75 1.75 0 1 0 0-3.5 1.75 1.75 0 0 0 0 3.5Zm0 7.25a1.75 1.75 0 1 0 0-3.5 1.75 1.75 0 0 0 0 3.5Zm1.75 5.5a1.75 1.75 0 1 1-3.5 0 1.75 1.75 0 0 1 0 3.5Z M14.753 6.5a1.75 1.75 0 1 0 0-3.5 1.75 1.75 0 0 0 0 3.5ZM16.5 12a1.75 1.75 0 1 1-3.5 0 1.75 1.75 0 0 1 3.5 0Zm-1.747 9a1.75 1.75 0 1 0 0-3.5 1.75 1.75 0 0 0 0 3.5Z" fill="currentColor" />
                        </svg>
                      </span>
                      <n-dropdown
                        trigger="click"
                        placement="bottom-end"
                        :options="resolvePageBlockMoreOptions(block)"
                        @select="key => handlePageBlockMoreSelect(key, block)"
                      >
                        <button
                          type="button"
                          class="page-block-menu-trigger"
                          title="更多操作"
                          aria-label="更多操作"
                          @click.stop
                          @mousedown.stop
                        >
                          <svg width="1em" height="1em" viewBox="0 0 512 512" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                            <circle cx="256" cy="256" r="32" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32" />
                            <circle cx="416" cy="256" r="32" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32" />
                            <circle cx="96" cy="256" r="32" fill="none" stroke="currentColor" stroke-miterlimit="10" stroke-width="32" />
                          </svg>
                        </button>
                      </n-dropdown>
                      <div v-if="backgroundPickerBlockId === block.id && blockBackgroundPickerVisible" class="page-block-color-picker page-block-color-picker-floating" @click.stop>
                        <div class="page-block-color-picker-head">
                          <button type="button" class="page-block-color-picker-reset" @click="updatePageBlockBackgroundColor(block, 'transparent')">
                            恢复默认
                          </button>
                          <button type="button" aria-label="关闭颜色选择器" @click="blockBackgroundPickerVisible = false">
                            ×
                          </button>
                        </div>
                        <div class="page-block-color-presets" aria-label="推荐颜色">
                          <button type="button" class="transparent" title="透明" @click="updatePageBlockBackgroundColor(block, 'transparent')" />
                          <button v-for="color in pageBlockRecommendedColors" :key="color" type="button" :style="{ background: color }" @click="updatePageBlockBackgroundColor(block, color)" />
                        </div>
                        <n-color-picker
                          :value="resolvePageBlockBackgroundColor(block)"
                          :show-alpha="true"
                          :modes="['hex']"
                          @update:value="updatePageBlockBackgroundColor(block, $event)"
                        />
                        <button type="button" class="page-block-color-picker-transparent" @click="updatePageBlockBackgroundColor(block, 'transparent')">
                          设为透明
                        </button>
                      </div>
                    </div>
                    <template v-if="editing && selectedPageBlockId === block.id">
                      <button
                        v-for="anchor in pageBlockResizeAnchors"
                        :key="anchor"
                        type="button"
                        class="page-block-resize-anchor"
                        :class="`anchor-${anchor}`"
                        title="调整组件大小"
                        @pointerdown.stop="startPageBlockResize(block, $event, anchor)"
                      />
                    </template>
                    <GridBlockRenderer
                      :block="resolvePagePreviewBlock(block)"
                      :fields="resolvePageBlockFields(block)"
                      :runtime-crud-props="resolvePageBlockRuntimeCrudProps(block)"
                      :runtime-crud-loading="isPageBlockRuntimeCrudLoading(block)"
                      :runtime-tree-active-key="runtimeTreeActiveKeyByBlockId[block.id] || '__all__'"
                      :data-source-configured="isPageBlockDataSourceConfigured(block)"
                      :runtime-interactive="!editing && !isDraftMode"
                      :block-fields-resolver="resolvePageBlockFields"
                      :runtime-crud-props-resolver="resolvePageBlockRuntimeCrudProps"
                      :runtime-crud-loading-resolver="isPageBlockRuntimeCrudLoading"
                      :data-source-configured-resolver="isPageBlockDataSourceConfigured"
                      show-data-source-guide
                      :selected="false"
                      :selected-block-id="selectedPageBlockId"
                      :inline-text-editing="editing"
                      :readonly="!editing"
                      :catalog-drag-block-type="catalogDragBlockType"
                      :active-drop-cell="activePageFlowGridTarget"
                      :active-drop-container="activePageFlowContainerTarget"
                      :nested-moving-block-id="nestedMovingPageBlockId"
                      @block-activate="selectPageBlock"
                      @inline-text-update="handleInlineTextUpdate"
                      @child-block-select="handleNestedPageBlockSelect"
                      @child-block-menu-select="handleNestedPageBlockMenuSelect"
                      @child-block-move-start="handleNestedPageBlockMoveStart"
                      @child-block-resize-start="handleNestedPageBlockResizeStart"
                      @tab-drop="handlePageFlowTabDrop"
                      @grid-cell-drop="handlePageFlowGridCellDrop"
                      @grid-cell-insert="handlePageFlowGridCellDrop"
                      @container-insert="handlePageFlowContainerInsert"
                      @container-clear="handlePageFlowContainerClear"
                      @request-data-source="handlePageBlockDataSourceRequest"
                      @runtime-tree-select="handleRuntimeTreeSelect"
                    />
                  </section>
                </template>
              </draggable>
              <div
                v-if="dragPreview"
                class="page-block-drag-ghost"
                :style="{ left: `${dragPreview.x}px`, top: `${dragPreview.y}px`, width: `${dragPreview.width}px`, height: `${dragPreview.height}px` }"
                aria-hidden="true"
              >
                <span>{{ dragPreviewBlock?.label || dragPreviewBlock?.blockType || '组件' }}</span>
              </div>
              <div
                v-if="pageAlignGuides.visible"
                class="page-align-guides"
                aria-hidden="true"
              >
                <div
                  v-if="pageAlignGuides.cross"
                  class="page-align-cross-v"
                  :style="{ left: `${pageAlignGuides.cross.x}px` }"
                />
                <div
                  v-if="pageAlignGuides.cross"
                  class="page-align-cross-h"
                  :style="{ top: `${pageAlignGuides.cross.y}px` }"
                />
                <div
                  v-for="(x, idx) in pageAlignGuides.x"
                  :key="`snap-x-${idx}-${x}`"
                  class="page-align-snap-v"
                  :style="{ left: `${x}px` }"
                />
                <div
                  v-for="(y, idx) in pageAlignGuides.y"
                  :key="`snap-y-${idx}-${y}`"
                  class="page-align-snap-h"
                  :style="{ top: `${y}px` }"
                />
              </div>
              <section v-if="editing && !pageBlocks.length" class="grid-empty-guide">
                <div class="empty-guide-copy">
                  <span class="empty-guide-eyebrow">页面搭建</span>
                  <h2>从一个组件开始</h2>
                  <p>选择常用组件，页面会立刻呈现最终效果；后续仍可自由拖动、调整尺寸和配置数据。</p>
                </div>
                <div class="page-recommendations">
                  <button v-for="item in recommendedComponents" :key="item.blockType" type="button" @click="appendPageBlock(item.blockType)">
                    <span class="empty-component-icon" :class="`kind-${resolveComponentPickerGroup(item)}`" aria-hidden="true">
                      <NIcon><component :is="resolveEmptyGuideIcon(item)" /></NIcon>
                    </span>
                    <span>{{ item.title }}</span>
                  </button>
                </div>
                <div class="empty-guide-preview" aria-hidden="true">
                  <div class="empty-guide-page-sheet">
                    <div class="empty-guide-sheet-head">
                      <i />
                      <span />
                      <em />
                    </div>
                    <div class="empty-guide-sheet-title">
                      <b />
                      <span />
                    </div>
                    <div class="empty-guide-sheet-metrics">
                      <i /><i /><i />
                    </div>
                    <div class="empty-guide-sheet-content">
                      <div class="empty-guide-sheet-list">
                        <i /><i /><i /><i />
                      </div>
                      <div class="empty-guide-sheet-chart">
                        <i /><i /><i /><i /><i />
                      </div>
                    </div>
                  </div>
                  <span class="empty-guide-float-card float-list"><NIcon><ListOutline /></NIcon></span>
                  <span class="empty-guide-float-card float-chart"><NIcon><BarChartOutline /></NIcon></span>
                  <span class="empty-guide-float-card float-filter"><NIcon><FunnelOutline /></NIcon></span>
                </div>
              </section>
            </div>
          </section>
        </main>
        <aside v-if="editing && configPanelVisible" class="runtime-inspector">
          <div class="runtime-inspector-head">
            <div class="runtime-inspector-tabs" role="tablist" aria-label="组件配置类型">
              <button type="button" :class="{ active: inspectorTab === 'properties' }" role="tab" :aria-selected="inspectorTab === 'properties'" @click="inspectorTab = 'properties'">
                <NIcon><SettingsOutline /></NIcon>属性
              </button>
              <button type="button" :class="{ active: inspectorTab === 'data' }" role="tab" :aria-selected="inspectorTab === 'data'" @click="inspectorTab = 'data'">
                <NIcon><FolderOpenOutline /></NIcon>数据
              </button>
            </div>
            <button type="button" class="runtime-inspector-close" aria-label="收起配置面板" title="收起配置面板" @click="configPanelVisible = false">
              ×
            </button>
          </div>
          <div v-if="!selectedPageBlock && inspectorTab === 'properties'" class="page-canvas-padding-panel">
            <div class="page-canvas-padding-head">
              <strong>页面内边距</strong>
              <span>作用于画布与最终运行页，默认 24</span>
            </div>
            <div class="page-canvas-padding-grid">
              <label>
                <span>上</span>
                <n-input-number
                  size="small"
                  :value="pageCanvasPadding.top"
                  :min="0"
                  :max="120"
                  :show-button="false"
                  @update:value="updatePageCanvasPadding({ top: $event })"
                />
              </label>
              <label>
                <span>右</span>
                <n-input-number
                  size="small"
                  :value="pageCanvasPadding.right"
                  :min="0"
                  :max="120"
                  :show-button="false"
                  @update:value="updatePageCanvasPadding({ right: $event })"
                />
              </label>
              <label>
                <span>下</span>
                <n-input-number
                  size="small"
                  :value="pageCanvasPadding.bottom"
                  :min="0"
                  :max="120"
                  :show-button="false"
                  @update:value="updatePageCanvasPadding({ bottom: $event })"
                />
              </label>
              <label>
                <span>左</span>
                <n-input-number
                  size="small"
                  :value="pageCanvasPadding.left"
                  :min="0"
                  :max="120"
                  :show-button="false"
                  @update:value="updatePageCanvasPadding({ left: $event })"
                />
              </label>
            </div>
            <n-button size="tiny" secondary @click="updatePageCanvasPadding(DEFAULT_PAGE_PADDING)">
              恢复默认 24
            </n-button>
          </div>
          <div v-if="selectedPageBlock && inspectorTab === 'data'" class="application-form-source-config">
            <div class="application-form-source-head">
              <strong>{{ selectedPageBlockSupportsDataSource ? '业务数据' : '组件数据' }}</strong>
              <span>{{ selectedPageBlockSupportsDataSource ? '选择业务对象后，画布会自动生成可用字段' : selectedPageBlock.label }}</span>
            </div>
            <div v-if="selectedPageBlockSupportsDataSource" class="page-data-source-selector">
              <div class="page-data-source-selector-head">
                <span>业务对象</span>
                <small>{{ selectedPageBlockUsesObjectRuntime ? '已连接' : '未选择' }}</small>
              </div>
              <n-select
                size="small"
                filterable
                :value="selectedPageBlockRuntimeObjectId || null"
                :options="runtimeObjectFormOptions"
                :disabled="!runtimeObjectFormOptions.length"
                placeholder="选择业务对象"
                @update:value="updateSelectedPageBlockRuntimeObject"
              />
              <div class="page-data-source-actions">
                <n-button
                  v-if="selectedPageBlockUsesObjectRuntime"
                  size="tiny"
                  secondary
                  @click="openObjectDesigner(selectedPageBlockObjectDesignerPanel, selectedPageBlockRuntimeObjectRef)"
                >
                  在对象设计器中精调
                </n-button>
                <n-button
                  v-if="selectedPageBlockIsCrud"
                  size="tiny"
                  secondary
                  @click="openSelectedBlockFormDesigner"
                >
                  设计数据表单
                </n-button>
                <n-button v-if="!runtimeObjectFormOptions.length" size="tiny" secondary @click="openObjectSetup">
                  管理业务对象
                </n-button>
              </div>
            </div>
            <template v-if="selectedPageBlockIsCrud">
              <div v-if="selectedPageBlockUsesObjectRuntime" class="crud-data-storage-card ready">
                <div class="crud-object-source-icon">
                  <NIcon><CubeOutline /></NIcon>
                </div>
                <div>
                  <strong>表单数据已准备完成</strong>
                  <p>当前页面可以新增、编辑、查询和保存数据。</p>
                </div>
              </div>
              <div v-else class="crud-data-storage-card" :class="selectedPageBlockFormDataState.status">
                <div class="crud-object-source-icon">
                  <NIcon><CubeOutline /></NIcon>
                </div>
                <div>
                  <strong>{{ selectedPageBlockFormDataState.status === 'error' ? '表单数据暂未准备完成' : selectedPageBlockFormAsset ? '保存表单后自动准备数据' : '先选择或新建一个表单' }}</strong>
                  <p>{{ selectedPageBlockFormDataState.message || (selectedPageBlockFormAsset ? '无需另外配置数据模型，系统会自动完成数据存储和页面连接。' : '表单决定要录入和展示哪些字段。') }}</p>
                </div>
                <div v-if="selectedPageBlockFormDataState.status === 'error'" class="crud-object-source-actions">
                  <n-button size="small" type="primary" :loading="saving" @click="saveDraft">
                    重新准备
                  </n-button>
                </div>
              </div>
              <div v-if="!selectedPageBlockUsesObjectRuntime" class="page-form-draft-card">
                <div class="page-form-draft-head">
                  <span>关联表单</span>
                  <small>表单中的字段会直接用于列表、查询和新增编辑。</small>
                </div>
                <n-select
                  size="small"
                  :value="selectedPageBlockFormAssetId || null"
                  :options="pageFormAssetOptions"
                  placeholder="选择已经设计好的表单"
                  @update:value="updateSelectedBlockFormAsset"
                />
                <div class="form-asset-actions">
                  <n-button size="tiny" type="primary" secondary @click="createFormAssetForSelectedBlock">
                    新建表单
                  </n-button>
                  <n-button size="tiny" :disabled="!selectedPageBlockFormAssetId" @click="editSelectedBlockFormAsset">
                    编辑表单
                  </n-button>
                </div>
              </div>
            </template>
            <template v-else-if="supportsFormAsset(selectedPageBlock) && !selectedPageBlockUsesObjectRuntime">
              <n-popover v-model:show="formAssetSelectorOpen" trigger="click" placement="bottom-start" :show-arrow="false" :to="false">
                <template #trigger>
                  <button type="button" class="form-asset-selector-trigger" :class="{ active: formAssetSelectorOpen }">
                    <NIcon><FolderOpenOutline /></NIcon>
                    <span>{{ selectedPageBlockFormAsset?.name || '选择已经设计好的表单' }}</span>
                    <span v-if="selectedPageBlockFormAssetId" class="form-asset-selector-open" title="编辑表单" role="button" tabindex="0" @click.stop="editSelectedBlockFormAsset">↗</span>
                    <span class="form-asset-selector-arrow" aria-hidden="true">{{ formAssetSelectorOpen ? '⌃' : '⌄' }}</span>
                  </button>
                </template>
                <div class="form-asset-selector-menu">
                  <n-input v-model:value="formAssetSelectorKeyword" clearable size="small" placeholder="搜索">
                    <template #prefix>
                      ⌕
                    </template>
                  </n-input>
                  <button
                    v-for="asset in filteredFormAssets"
                    :key="asset.id"
                    type="button"
                    class="form-asset-selector-option"
                    :class="{ selected: asset.id === selectedPageBlockFormAssetId }"
                    @click="selectFormAssetFromPicker(asset.id)"
                  >
                    <NIcon><FolderOpenOutline /></NIcon>
                    <span>{{ asset.name }}</span>
                    <span v-if="asset.id === selectedPageBlockFormAssetId" class="form-asset-selector-check">✓</span>
                  </button>
                  <n-empty v-if="!filteredFormAssets.length" size="small" description="没有匹配的表单" />
                </div>
              </n-popover>
              <p v-if="formAssets.length === 1" class="form-asset-default-hint">
                当前应用只有一个表单，已自动关联。
              </p>
              <div class="form-asset-actions">
                <n-button size="tiny" type="primary" secondary @click="createFormAssetForSelectedBlock">
                  新建表单
                </n-button>
                <n-button size="tiny" :disabled="!selectedPageBlockFormAssetId" @click="editSelectedBlockFormAsset">
                  编辑表单
                </n-button>
              </div>
            </template>
            <n-empty v-else size="small" description="该组件没有可绑定的数据表单" />
          </div>
          <div v-if="selectedPageBlockIsCrud && !selectedPageBlockUsesObjectRuntime && inspectorTab === 'properties'" class="crud-property-source-notice">
            <NIcon><InformationCircleOutline /></NIcon>
            <span><strong>当前使用表单字段。</strong>字段选择、隐藏和排序可以直接保存；系统会在保存表单后自动准备数据存储。</span>
            <n-button text type="primary" @click="inspectorTab = 'data'">
              查看表单数据
            </n-button>
          </div>
          <ListPageGridDesigner
            v-if="inspectorTab === 'properties' && selectedPageBlock"
            panel-only
            :model-value="designerGridLayout"
            :model-schema="applicationGridModelSchema"
            :fields="selectedPageBlockFields"
            :form-designer-schema="selectedPageBlockFormDesignerSchema"
            :active-block-id="selectedPageBlockId"
            @update:model-value="updateCurrentGridLayout"
          />
        </aside>
      </div>

      <!-- 编辑模式：页面设置 -->
      <section v-else-if="editing && activePageDesignTab === 'settings'" class="runtime-inline-panel">
        <PageDesignSettingsPanel
          v-if="currentNode"
          :node="currentNode"
          :application="application"
          :objects="objects"
          @update="patchCurrentPageNode"
        />
        <n-empty v-else description="请先选择要设置的页面" />
      </section>

      <!-- 编辑模式：发布 -->
      <section v-else-if="editing && activePageDesignTab === 'publish'" class="runtime-inline-panel">
        <PageDesignPublishPanel
          v-if="currentNode"
          :application="application"
          :node="currentNode"
          :page-id="currentNode.id"
          :page-title="currentNode.title"
          :dirty="dirty"
          :saving="saving"
          :config-key="pageDesignObject?.configKey || currentNode.objectRef?.configKey || ''"
          :objects="objects"
          @save="saveCurrentDesignerSection"
          @preview="openDraftPreview"
          @update="patchCurrentPageNode"
        />
        <n-empty v-else description="请先选择要发布的页面" />
      </section>

      <section
        v-show="!editing && (runtimeViewMode === 'process' || runtimeViewMode === 'enhance' || runtimeViewMode === 'settings')"
        class="runtime-inline-panel"
        :class="{
          'runtime-process-panel': runtimeViewMode === 'process',
          'runtime-enhance-panel': runtimeViewMode === 'enhance',
        }"
      >
        <!-- keep-alive：二次进入 Tab 不重挂；首次慢主要是 async chunk + 列表接口，空闲时预取 chunk -->
        <keep-alive>
          <ApplicationProcessPanel
            v-if="runtimeViewMode === 'process'"
            :key="'runtime-process'"
            :application="application"
            :initial-objects="processSelectableObjects"
            @changed="refreshWorkspaceMetadata"
            @navigate="handleProcessPanelNavigate"
            @open-designer="openProcessDesigner"
          />
          <ApplicationExtensionsPanel
            v-else-if="runtimeViewMode === 'enhance'"
            :key="'runtime-enhance'"
            embedded
            :application="application"
            :initial-extensions="workspaceExtensions"
            :initial-objects="objects"
            :initial-entries="workspaceEntries"
            :initial-pages="builder?.nodes || []"
            :context-page-id="enhanceContextPageId"
            @changed="handleExtensionsChanged"
            @open-designer="openEmbeddedObjectActions"
          />
          <ApplicationSettingsPanel
            v-else-if="runtimeViewMode === 'settings'"
            :key="'runtime-settings'"
            :application="application"
            @saved="refreshWorkspaceMetadata"
          />
        </keep-alive>
      </section>
    </template>
    <n-result
      v-else
      status="warning"
      :title="loadErrorTitle"
      :description="loadError || '没有找到可用的应用运行配置。'"
      class="runtime-load-result"
    >
      <template #footer>
        <n-space justify="center">
          <n-button @click="router.push('/app-center')">
            返回应用中心
          </n-button>
          <n-button v-if="canEditApplication && !isDraftMode" secondary @click="editing = true">
            进入页面设计
          </n-button>
          <n-button type="primary" @click="load">
            重新加载
          </n-button>
        </n-space>
      </template>
    </n-result>
    <n-modal v-model:show="navigationActionVisible" preset="card" :title="navigationActionTitle" style="width: 420px">
      <n-form label-placement="top">
        <n-form-item v-if="navigationActionMode === 'rename'" label="名称">
          <n-input v-model:value="navigationActionForm.title" maxlength="40" show-count />
        </n-form-item>
        <n-form-item v-if="navigationActionMode === 'icon'" label="图标">
          <IconSelector v-model:value="navigationActionForm.icon" />
        </n-form-item>
        <n-form-item v-if="navigationActionMode === 'move'" label="移动到页面组">
          <n-select v-model:value="navigationActionForm.parentId" clearable :options="moveGroupOptions" placeholder="顶级菜单" />
        </n-form-item>
        <template v-if="navigationActionMode === 'delete'">
          <n-alert type="error" :bordered="false" class="navigation-action-danger">
            <div class="navigation-action-tip">
              <strong>危险操作</strong>
              <p>{{ navigationDeleteTip }}</p>
              <ul v-if="navigationDeleteImpact.impactedObjects.length" class="navigation-action-impact">
                <li v-for="item in navigationDeleteImpact.impactedObjects" :key="String(item.objectId || item.objectCode)">
                  {{ item.objectName || item.objectCode }}
                  <template v-if="item.objectCode">
                    （{{ item.objectCode }}）
                  </template>
                  <template v-if="item.tableName">
                    · 物理表 <code>{{ item.tableName }}</code>
                  </template>
                </li>
              </ul>
              <p class="navigation-action-tip-secondary">
                数据库物理表及表内业务数据不会被删除；如需删表请由管理员在数据源侧另行处理。
              </p>
            </div>
          </n-alert>
          <n-form-item v-if="navigationActionHasChildren" label="子项处理">
            <n-radio-group v-model:value="navigationActionForm.deleteStrategy">
              <n-radio value="delete-children">
                同时删除子项
              </n-radio>
              <n-radio value="move-children">
                移动到指定页面组
              </n-radio>
            </n-radio-group>
          </n-form-item>
          <n-form-item v-if="navigationActionHasChildren && navigationActionForm.deleteStrategy === 'move-children'" label="目标页面组">
            <n-select v-model:value="navigationActionForm.targetParentId" clearable :options="moveGroupOptions" placeholder="顶级菜单" />
          </n-form-item>
        </template>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="navigationActionVisible = false">
            取消
          </n-button>
          <n-button :type="navigationActionMode === 'delete' ? 'error' : 'primary'" @click="confirmNavigationAction">
            {{ navigationActionMode === 'delete' ? '确认删除' : '确认' }}
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="copyBlockVisible" preset="card" title="复制组件到其他页面" style="width: 420px">
      <n-form label-placement="top">
        <n-form-item label="目标页面">
          <n-select v-model:value="copyBlockTargetPageId" :options="copyBlockPageOptions" filterable placeholder="选择要复制到的页面" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="copyBlockVisible = false">
            取消
          </n-button>
          <n-button type="primary" :disabled="!copyBlockTargetPageId" @click="copySelectedBlockToOtherPage">
            复制
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <n-drawer v-model:show="objectSetupVisible" width="min(1080px, 96vw)">
      <n-drawer-content title="高级数据设置" closable>
        <ApplicationObjectsPanel
          v-if="application"
          :application="application"
          :initial-objects="objects"
          :open-designer-after-create="false"
          @changed="handleApplicationObjectsChanged"
          @open-designer="payload => openObjectDesigner(payload.panel, payload)"
        />
      </n-drawer-content>
    </n-drawer>

    <PageTypeSelector
      v-model:show="pageTypeSelectorVisible"
      :default-parent-id="pageTypeSelectorParentId"
      :default-page-type="pageTypeSelectorDefaultType"
      @confirm="handlePageTypeSelection"
    />

    <PageSystemMenuMountDialog
      v-model:show="systemMenuMountVisible"
      :node="systemMenuMountNode"
      :page-title="systemMenuMountNode?.title || ''"
      :saving="saving"
      @confirm="confirmSystemMenuMount"
      @unmount="unmountSystemMenu"
    />

    <n-modal v-model:show="exitEditingVisible" preset="dialog" title="退出编辑">
      尚有未保存的页面、导航或组件调整。退出后将丢失这些修改。
      <template #action>
        <n-space justify="end">
          <n-button @click="exitEditingVisible = false">
            继续编辑
          </n-button>
          <n-button type="error" @click="discardAndExitEditing">
            放弃修改并退出
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal
      v-model:show="embeddedProcessDesignerVisible"
      :mask-closable="false"
      :auto-focus="false"
      class="embedded-process-designer-modal"
    >
      <div class="embedded-process-designer-shell">
        <BusinessProcessPage
          v-if="embeddedProcessDesignerVisible"
          embedded
          :process-id="embeddedProcessDesignerId"
          @close="embeddedProcessDesignerVisible = false"
          @saved="handleEmbeddedProcessDesignerSaved"
        />
      </div>
    </n-modal>
  </div>
</template>

<script setup>
import { AddOutline, AppsOutline, ArrowBackOutline, ArrowDownOutline, ArrowRedoOutline, ArrowUndoOutline, ArrowUpOutline, BarChartOutline, CheckboxOutline, CheckmarkDoneOutline, ColorFillOutline, CopyOutline, CreateOutline, CubeOutline, DocumentTextOutline, DuplicateOutline, EllipsisHorizontalOutline, ExpandOutline, EyeOffOutline, EyeOutline, FolderOpenOutline, FunnelOutline, GitBranchOutline, GitNetworkOutline, GridOutline, InformationCircleOutline, ListOutline, MoveOutline, NotificationsOutline, PaperPlaneOutline, PeopleOutline, ReaderOutline, RemoveOutline, ResizeOutline, SaveOutline, SettingsOutline, SquareOutline, StatsChartOutline, SwapHorizontalOutline, TextOutline, TrashOutline } from '@vicons/ionicons5'
import { NIcon, useMessage } from 'naive-ui'
import { computed, defineAsyncComponent, h, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import draggable from 'vuedraggable'
import { businessObjectDesigner } from '@/api/business-app'
import { businessApplicationRuntimeByCode, businessApplicationWorkspaceByCode, designBusinessApplicationPage, initializeBusinessApplicationExcel, previewBusinessApplicationExcel, provisionBusinessApplicationFormData, updateBusinessApplication } from '@/api/business-application'
import defaultLogo from '@/assets/images/logo.png'
import AuthImage from '@/components/common/AuthImage.vue'
import IconRenderer from '@/components/IconRenderer.vue'
import { createGridBlock, DATA_FIELD_BLOCK_TYPES, isDataFieldBlockType, listPageBlockCatalog, resolveListPageBlockMeta } from '@/components/lowcode-builder/page/page-schema'
import {
  alignSearchSchemaWithLeftTree,
  buildLeftTreeFilterParams,
  findTreePanelProps,
} from '@/components/lowcode-builder/shared/runtime-tree-table'
import { isPageWidgetComponentKey } from '@/components/lowcode-builder/shared/page-widget-schema'
import { useTenantStore, useUserStore } from '@/store'
import ApplicationDesignerResourceTree from '@/views/app-center/components/ApplicationDesignerResourceTree.vue'
import DesignerAsyncLoader from '@/views/app-center/components/designer/DesignerAsyncLoader.vue'
import PageSystemMenuMountDialog from '@/views/app-center/components/designer/PageSystemMenuMountDialog.vue'
import {
  applyPageMountFieldsToNode,
  isPageSystemMenuMounted,
} from '@/views/app-center/components/designer/page-system-menu-mount'
import ApplicationRuntimeSkeleton from '@/views/app-center/components/portal/ApplicationRuntimeSkeleton.vue'
import { buildAutoFieldAssets, createFieldFromComponent } from '@/views/app-center/components/designer/form-first/autoFieldRegistry'
import { createDefaultFormDesignerSchema, isFieldComponent, normalizeFormDesignerSchema, presentFormDesignerSchema } from '@/views/app-center/components/designer/form-first/formDesignerSchema'
import { filterNavigationNodesByClient } from '@/views/app-center/components/portal/portal-navigation-runtime'
import { collectPortalPageCrudTargets, warmPortalPageCrudProps } from '@/views/app-center/components/portal/portal-page-crud-warm'
import PortalPageRenderer from '@/views/app-center/components/portal/PortalPageRenderer.vue'
import {
  buildApplicationDesignerResourceGroups,
  findApplicationDesignerResource,
  normalizeApplicationDesignerSection,
  resolveApplicationDesignerObject,
  resolveObjectDesignerNavigationTarget,
} from './application-designer-navigation'
import { createApplicationRuntimeLoadCoordinator, resolveApplicationRuntimeLoadKey, shouldUseApplicationWorkspaceLoad } from './application-runtime-load'
import {
  createInAppFormAsset,
  createNavigationNode,
  hasPendingLegacyObjectPageMigration,
  mergeInAppBuilderOptions,
  moveNavigationNode,
  normalizeInAppBuilder,
  isOrphanPageFormObject,
  removeNavigationNode,
  resolveNavigationDeleteImpact,
  updateInAppFormAsset,
} from './in-app-builder/in-app-builder-schema'
import { bindProvisionedFormData, collectFormDataProvisionTargets, mergePageFieldCatalogs } from './in-app-builder/page-form-data-provisioning'
import { buildBusinessObjectDesignerPayloadFromFormAsset, normalizeObjectDesignerFieldCatalog, syncFormBoundFieldRefs } from './in-app-builder/page-form-object-promotion'
import {
  isPageManagementSystemPageId,
  PAGE_MANAGEMENT_SYSTEM_PAGES,
  resolvePageManagementSelection,
  resolvePageManagementSystemPage,
} from './in-app-builder/page-management'
import {
  createPageShapeBuilder,
  ensureFreeLayoutPageNode,
  isObjectBoundPageLayoutPolluted,
  resolvePageShapeFromNode,
  restoreObjectBoundPageLayout,
} from './in-app-builder/page-shape-design'
import {
  createWorkbenchVirtualNode,
  ensureWorkbenchPageInBuilder,
  isWorkbenchPageId,
  WORKBENCH_PAGE_ID,
} from './in-app-builder/workbench-page'
import { inAppPageTemplateCatalog, resolveInAppPageTemplate } from './in-app-builder/page-template-catalog'
import { findPageBlockInTree, mapPageBlocksInTree, removePageBlockFromTree, visitPageBlocksInTree } from './runtime-modules/page-block-tree'
import { resolvePageBlockShellStyle as computePageBlockShellStyle, readPageBlockLength, resolveDefaultPageBlockHeight, resolveDefaultPageBlockYFromItems, resolvePageBlockFlowGeometry, resolveRootPageBlockCollisions, shouldUsePageFlowStack, normalizePagePadding, resolvePagePaddingCss, migratePageFlowToContentCoords, canvasToContentFlowPoint, contentToCanvasFlowPoint, DEFAULT_PAGE_PADDING } from './runtime-modules/page-flow-geometry'
import { flattenNodes, hasPermission, isNavigationVisible } from './runtime-modules/runtime-navigation-utils'
import { useBuilderHistory } from './runtime-modules/use-builder-history'
import { useRuntimeCrudConfig } from './runtime-modules/use-runtime-crud-config'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const tenantStore = useTenantStore()
const formComponentIconModules = import.meta.glob('/src/assets/images/form/*.png', { import: 'default' })
const formComponentIconCache = reactive({})
const formComponentIconFileByBlockType = {
  'search-form': 'chaxunbiaodan',
  'toolbar': 'caozuogongjulan',
  'back-button': 'fanhuishangyiye',
  'page-title': 'yemianbiaoti',
  'grid-layout': 'shangebuju',
  'detail-info': 'xiangqingxinxi',
  'AiCrudPage': 'crud',
  'AiTable': 'zhinengbiaoge',
  'AiForm': 'zhinengbiaodan',
  'data-table': 'shujuliebiao',
  'tree-panel': 'shaixuanshu',
  'stats-strip': 'zhibiaokapian',
  'workspace-summary-metrics': 'zhibiaokapian',
  'info-panel': 'tishimianban',
  'custom-html': 'shuomingwenben',
  'action-button': 'button',
  'button-group': 'buttongroup',
  'tag-list': 'biaoqianliebiao',
  'steps': 'buzhoutiao',
  'timeline': 'shijianxian',
  'empty-state': 'kognzhuangtai',
  'card': 'kapianrongqi',
  'tabs': 'tabs',
  'divider': 'fengexian',
  'spacer': 'liubaizhanwei',
  'signature-pad': 'qianming',
  'step-form': 'fenbubiaodan',
  'text-title': 'biaoti',
  'paragraph': 'duanluo',
  'statistic': 'tongjishuzhi',
  'link': 'lianjie',
  'text-tip': 'wenzitishi',
  'audio-player': 'yinpinbofangqi',
  'video-player': 'shipinbofangqi',
  'avatar': 'touxiang',
  'iframe': 'neiqianyemian',
  'box-layout': 'gezibuju',
  'space': 'jianju',
  'sub-table-tabs': 'zibiaotab',
  'section-divider': 'fenzubiaoti',
  'transfer': 'chuansuokuang',
  'watermark': 'shuiyin',
  'vue-component': 'vue',
  'markdown': 'md',
  'barcode': 'tiaoxingma',
  'qrcode': 'erweima',
  'calendar': 'rili',
  'code': 'daima',
  'countdown': 'daojishi',
  'descriptions': 'miaoshu',
  'announcement': 'gognshi',
  'list': 'shujuliebiao',
  'log': 'log',
  'number-animation': 'shuzidonghua',
  'breadcrumb': 'mianbaoxie',
  'menu': 'caidan',
  'pagination': 'fenye',
}
const userStore = useUserStore()
const asyncPanelLoader = {
  // 设计页 chunk 较大；delay>0 会先空白再出 loader，表现为「白屏一会儿」
  delay: 0,
  loadingComponent: DesignerAsyncLoader,
}
const ApplicationObjectsPanel = defineAsyncComponent({
  ...asyncPanelLoader,
  loader: () => import('./application-workspace/ApplicationObjectsPanel.vue'),
})
const ApplicationExtensionsPanel = defineAsyncComponent({
  ...asyncPanelLoader,
  loader: () => import('./application-workspace/ApplicationExtensionsPanel.vue'),
})

const ApplicationProcessPanel = defineAsyncComponent({
  ...asyncPanelLoader,
  loader: () => import('./application-workspace/ApplicationProcessPanel.vue'),
})
const ApplicationSettingsPanel = defineAsyncComponent({
  ...asyncPanelLoader,
  loader: () => import('./components/ApplicationSettingsPanel.vue'),
})

/** 页面管理空闲时预拉三个 Tab 的 JS chunk，避免首次点击才开始下载大包。 */
const runtimeWorkspacePanelImporters = [
  () => import('./application-workspace/ApplicationProcessPanel.vue'),
  () => import('./application-workspace/ApplicationExtensionsPanel.vue'),
  () => import('./components/ApplicationSettingsPanel.vue'),
]
let runtimeWorkspacePanelsPrefetchScheduled = false

function prefetchRuntimeWorkspacePanels() {
  if (runtimeWorkspacePanelsPrefetchScheduled || typeof window === 'undefined')
    return
  runtimeWorkspacePanelsPrefetchScheduled = true
  const run = () => {
    runtimeWorkspacePanelImporters.forEach((load) => {
      void load().catch(() => {})
    })
  }
  if (typeof window.requestIdleCallback === 'function')
    window.requestIdleCallback(run, { timeout: 2500 })
  else
    window.setTimeout(run, 800)
}
const BusinessObjectDesignerPage = defineAsyncComponent({
  ...asyncPanelLoader,
  loader: () => import('./object-designer.[objectCode].vue'),
})
const BusinessProcessPage = defineAsyncComponent({
  ...asyncPanelLoader,
  loader: () => import('./business-process.[processId].vue'),
})
// 设计态与低频视图组件按需加载：运行态用户打开应用时不再同步下载整套设计器代码
const ForgeFormDesigner = defineAsyncComponent({
  ...asyncPanelLoader,
  loader: () => import('@/views/app-center/components/designer/forge-form-designer/ForgeFormDesigner.vue'),
})
const ListPageGridDesigner = defineAsyncComponent({
  ...asyncPanelLoader,
  loader: () => import('@/components/lowcode-builder/page/ListPageGridDesigner.vue'),
})
const IconSelector = defineAsyncComponent({
  ...asyncPanelLoader,
  loader: () => import('@/components/IconSelector.vue'),
})
const PageTypeSelector = defineAsyncComponent({
  ...asyncPanelLoader,
  loader: () => import('@/views/app-center/components/designer/PageTypeSelector.vue'),
})
const PageDesignSettingsPanel = defineAsyncComponent({
  ...asyncPanelLoader,
  loader: () => import('@/views/app-center/components/designer/PageDesignSettingsPanel.vue'),
})
const PageDesignPublishPanel = defineAsyncComponent({
  ...asyncPanelLoader,
  loader: () => import('@/views/app-center/components/designer/PageDesignPublishPanel.vue'),
})
const PageManagementSystemView = defineAsyncComponent({
  ...asyncPanelLoader,
  loader: () => import('@/views/app-center/components/portal/PageManagementSystemView.vue'),
})
const GridBlockRenderer = defineAsyncComponent({
  delay: 0,
  loadingComponent: DesignerAsyncLoader,
  loader: () => import('@/components/lowcode-builder/page/GridBlockRenderer.vue'),
})

const application = ref(null)
const objects = ref([])
const builder = ref(null)
// 左侧页面删掉后，只由该页面创建的业务对象不能再出现在流程选择里。
const processSelectableObjects = computed(() => (objects.value || []).filter(item => !isOrphanPageFormObject(item, builder.value)))
const loadError = ref('')
// 组件一挂载就显示骨架，避免「白屏 → 再出骨架」；load() finally 会关掉
const loading = ref(true)
const saving = ref(false)
const restoringObjectPageLayout = ref(false)
const editing = ref(route.query.edit === '1')
const runtimeViewMode = ref(resolveRuntimeView(route.query.view)) // 'pages' | 'process' | 'enhance' | 'settings'
const exitEditingVisible = ref(false)
const selectedNodeId = ref('')
const enhanceContextPageId = computed(() => {
  const fromQuery = String(route.query.pageId || '').trim()
  if (fromQuery && !isPageManagementSystemPageId(fromQuery))
    return fromQuery
  const selected = String(selectedNodeId.value || '').trim()
  if (selected && !isPageManagementSystemPageId(selected))
    return selected
  return ''
})
const newNodePopoverVisible = ref(false)
const selectedPageTemplateKey = ref('blank')
const iconPickerVisible = ref(false)
const iconPickerNodeId = ref('')
const navigationActionVisible = ref(false)
const navigationActionMode = ref('')
const navigationActionNodeId = ref('')
const navigationActionForm = ref({ title: '', icon: '', parentId: null, deleteStrategy: 'delete-children', targetParentId: null })
const systemMenuMountVisible = ref(false)
const systemMenuMountNodeId = ref('')
const systemMenuMountNode = computed(() => (builder.value?.nodes || []).find(item => item.id === systemMenuMountNodeId.value) || null)
const componentPopoverVisible = ref(false)
const componentKeyword = ref('')
const savedSignature = ref('')
const selectedPageBlockId = ref('')
const draggingPageBlockId = ref('')
const dragPreview = ref(null)
const pageAlignGuides = ref({ visible: false, cross: null, x: [], y: [] })
const PAGE_ALIGN_SNAP_PX = 6
const configPanelVisible = ref(false)
const inspectorTab = ref('properties')
const componentButtonPosition = ref({ x: null, y: null })
const componentButtonMoveCtx = ref(null)
const catalogDragBlockType = ref('')
const suppressCatalogClick = ref(false)
const activePageFlowTabTarget = ref(null)
const activePageFlowGridTarget = ref(null)
const activePageFlowContainerTarget = ref(null)
const resizeCollisionBlockIds = ref([])
let catalogPointerDragCtx = null
// 表单设计器的对象设计器上下文（fields/relations/actions），按对象缓存；字段目录供字段资产货架使用。
const formDesignerObjectContextByObjectId = ref({})
const formDesignerObjectContextLoadingIds = reactive(new Set())
const formDesignerMode = ref(false)
// 新建页面后草稿保存、路由切换和表单资产挂载不是同一个 tick；资产就绪前保留设计器占位，避免出现空白区域。
const designerTransitionLoading = ref(false)
// 树节点高亮按区块维护；右表筛选按当前页面维护。
// 左树与右表通常是两个不同区块，不能用同一个 blockId 关联，否则点击树后过滤会落在树区块自身而不是右表。
const runtimeTreeFilter = ref({})
const runtimeTreeFilterByBlockId = ref({})
const runtimeTreeActiveKeyByBlockId = ref({})
const formDesignerFromPageManagement = ref(false)
const activeFormAssetId = ref('')
const activePageShapeDesign = ref(null)
const pageTypeSelectorVisible = ref(false)
const pageTypeSelectorParentId = ref(null)
const pageTypeSelectorDefaultType = ref('form')
const sidebarCollapsed = ref(false)
const renamingApplication = ref(false)
const renameApplicationValue = ref('')
const renameSaving = ref(false)
const renameInputRef = ref(null)
const collapsedGroupIds = ref(new Set())
const copyBlockVisible = ref(false)
const copyBlockId = ref('')
const copyBlockTargetPageId = ref('')
const blockBackgroundPickerVisible = ref(false)
const backgroundPickerBlockId = ref('')
const formAssetSelectorOpen = ref(false)
const formAssetSelectorKeyword = ref('')
const formDataProvisioningByAssetId = ref({})
const objectSetupVisible = ref(false)
const selectedDesignerResourceKey = ref(String(route.query.designResource || ''))
const workspaceExtensions = ref([])
const workspaceEntries = ref([])
/** 表单/对象保存后递增，驱动 PortalPageRenderer 丢弃旧 CRUD 缓存并重新 render */
const portalCrudConfigRevision = ref(0)
/** 骨架阶段预热的首屏 CRUD props，交给 PortalPageRenderer 避免壳出后内容空白 */
const portalCrudSeed = ref({})
const isDraftMode = computed(() => route.query.edit === '1' || route.query.draft === '1')
const {
  runtimeCrudPropsByObjectId,
  runtimeCrudLoadingObjectIds,
  runtimeCrudUnavailableObjectIds,
  loadRuntimeCrudProps,
  resetRuntimeCrudConfig,
} = useRuntimeCrudConfig({
  application,
  workspaceEntries,
  pageId: computed(() => String(selectedNodeId.value || route.query.pageId || '').trim()),
  // 草稿/编辑态才允许降级读取对象设计器字段目录做静态预览
  canLoadDesignerSchema: computed(() => editing.value || isDraftMode.value || canEditApplication.value),
})
const activeFlowContext = ref({})
const embeddedDesignerRef = ref(null)
const embeddedDesignerDirty = ref(false)
const embeddedDesignerSaving = ref(false)
const embeddedProcessDesignerVisible = ref(false)
const embeddedProcessDesignerId = ref('')
const applicationRuntimeLoadCoordinator = createApplicationRuntimeLoadCoordinator(load)

const componentPickerGroupOptions = [
  { key: 'list', label: '数据' },
  { key: 'chart', label: '图表' },
  { key: 'view', label: '展示' },
  { key: 'other', label: '其他' },
]
const runtimeHeaderMoreOptions = computed(() => [
  ...(editing.value
    ? [
        { label: '撤销', key: 'undo', icon: () => renderNavigationMenuIcon(ArrowUndoOutline), disabled: !canUndo.value || !pageBuilderResourceActive.value },
        { label: '重做', key: 'redo', icon: () => renderNavigationMenuIcon(ArrowRedoOutline), disabled: !canRedo.value || !pageBuilderResourceActive.value },
        { type: 'divider', key: 'header-more-divider-1' },
      ]
    : []),
  { label: '高级数据设置', key: 'object-design', icon: () => renderNavigationMenuIcon(ReaderOutline) },
  ...(editing.value
    ? [
        { type: 'divider', key: 'header-more-divider-2' },
        { label: '退出设计', key: 'exit-editing', icon: () => renderNavigationMenuIcon(ArrowBackOutline) },
      ]
    : []),
])
const pageManagementSystemPages = PAGE_MANAGEMENT_SYSTEM_PAGES
const systemPageIconMap = {
  'grid': GridOutline,
  'checkbox': CheckboxOutline,
  'checkmark-done': CheckmarkDoneOutline,
  'paper-plane': PaperPlaneOutline,
  'people': PeopleOutline,
  'notifications': NotificationsOutline,
}
function resolveSystemPageIcon(icon) {
  return systemPageIconMap[icon] || DocumentTextOutline
}
const currentSystemPage = computed(() => resolvePageManagementSystemPage(selectedNodeId.value))
const workbenchPage = computed(() => builder.value?.pages?.[WORKBENCH_PAGE_ID] || null)
const systemPageNavigationRoutes = computed(() => ({
  todo: '/workspace/todo',
  done: '/workspace/done',
  sent: '/workspace/started',
  started: '/workspace/started',
  cc: '/workspace/cc',
}))
const designerResourceGroups = computed(() => buildApplicationDesignerResourceGroups({
  objects: objects.value,
  designersByObjectId: {},
  pages: builder.value?.nodes || [],
  extensions: workspaceExtensions.value,
}))
const activeDesignerResource = computed(() => {
  const routeResourceKey = selectedDesignerResourceKey.value
    || String(route.query.designResource || '')
    || (route.query.pageId ? `page-custom:${String(route.query.pageId)}` : '')
  return findApplicationDesignerResource(
    designerResourceGroups.value,
    routeResourceKey,
    route.query.designSection,
  )
})
const designerSection = computed(() => activeDesignerResource.value?.groupKey || normalizeApplicationDesignerSection(route.query.designSection))
const pageBuilderResourceActive = computed(() => {
  if (activeDesignerResource.value?.kind === 'page-custom')
    return true
  // 个人工作台不在 nodes 资源树里，但仍按自由布局页预览
  return isWorkbenchPageId(route.query.pageId || selectedNodeId.value)
})

// 工作台编辑者需要维护管理端和移动端两套页面树；正式门户仍由
// application-portal.vue 按当前客户端过滤。该权限计算放在导航树之前，
// 避免导航树首次求值时拿到旧的客户端过滤结果。
const canEditApplication = computed(() => userStore.isAdmin || hasPermission(userStore.permissions, 'ai:businessApplication:edit') || hasPermission(userStore.apiPermissions, 'ai:businessApplication:edit') || hasPermission(userStore.getDataPermission, 'ai:businessApplication:edit'))
/** 编辑/草稿，或应用工作台内有编辑权限时：走草稿 CRUD（含新增），不要求先发布应用 */
const usePortalDesignPreview = computed(() => (
  editing.value
  || isDraftMode.value
  || canEditApplication.value
))
const activeDesignerObject = computed(() => resolveApplicationDesignerObject(objects.value, activeDesignerResource.value?.objectId))
// 对象页面（表单页/列表页/数据结构）也应能预览，落到对象自身的 CRUD 运行页。
const activeResourceConfigKey = computed(() => String(activeDesignerObject.value?.configKey || '').trim())
const previewableResourceActive = computed(() => {
  if (pageBuilderResourceActive.value)
    return true
  const kind = activeDesignerResource.value?.kind || ''
  const objectBacked = kind.startsWith('page-') || kind.startsWith('data-')
  return objectBacked && Boolean(activeResourceConfigKey.value)
})
const legacyBlockTypeMap = { 'intro': 'page-title', 'metric-card': 'stats-strip', 'business-list': 'AiCrudPage', 'business-form': 'AiForm', 'todo': 'info-panel', 'chart': 'stats-strip', 'text': 'custom-html', 'image': 'info-panel', 'columns': 'grid-layout', 'divider': 'divider' }
const pageBlockResizeAnchors = ['top-left', 'top', 'top-right', 'right', 'bottom-right', 'bottom', 'bottom-left', 'left']
const pageBlockRecommendedColors = ['#3370ff', '#8b5cf6', '#14b8a6', '#f59e0b', '#f97316', '#ef4444', '#4e5969', '#edf4ff', '#f3efff', '#e6fffb', '#fff7e6', '#fff1f0', '#f2f3f5']

const groupOptions = computed(() => (builder.value?.nodes || []).filter(item => item.type === 'group').map(item => ({ label: item.title, value: item.id })))
const navigationActionNode = computed(() => builder.value?.nodes.find(item => item.id === navigationActionNodeId.value) || null)
const iconPickerNode = computed(() => builder.value?.nodes.find(item => item.id === iconPickerNodeId.value) || null)
const navigationIconValue = computed({
  get: () => iconPickerNode.value?.icon || '',
  set: (icon) => {
    if (!iconPickerNode.value)
      return
    builder.value = {
      ...builder.value,
      nodes: builder.value.nodes.map(item => item.id === iconPickerNode.value.id ? { ...item, icon } : item),
    }
    iconPickerVisible.value = false
    scheduleNavigationSave()
  },
})
const navigationActionTitle = computed(() => ({ rename: '重命名', move: '移动到', delete: '危险操作：删除页面或页面组' }[navigationActionMode.value] || '页面操作'))
const navigationActionHasChildren = computed(() => navigationActionNode.value?.type === 'group' && builder.value?.nodes.some(item => item.parentId === navigationActionNode.value.id))
const navigationDeleteImpact = computed(() => {
  if (navigationActionMode.value !== 'delete' || !navigationActionNode.value) {
    return {
      removedPageIds: [],
      impactedObjects: [],
      danger: false,
    }
  }
  const strategy = navigationActionHasChildren.value
    ? {
        type: navigationActionForm.value.deleteStrategy,
        targetParentId: navigationActionForm.value.targetParentId,
      }
    : undefined
  return resolveNavigationDeleteImpact(builder.value, navigationActionNode.value.id, strategy, objects.value)
})
const navigationDeleteTip = computed(() => {
  if (navigationActionHasChildren.value)
    return '该页面组含有子项，请选择处理方式。删除后导航与页面设计无法恢复。'
  const pageCount = navigationDeleteImpact.value.removedPageIds.length
  const objectCount = navigationDeleteImpact.value.impactedObjects.length
  if (objectCount > 0) {
    return `将删除 ${pageCount || 1} 个页面，并清理 ${objectCount} 个关联业务对象配置（多为页面表单托管对象）。删除后无法恢复。`
  }
  return '删除后无法恢复。页面设计与导航配置将被移除。'
})
const moveGroupOptions = computed(() => groupOptions.value.filter(item => item.value !== navigationActionNodeId.value && !isNavigationGroupDescendant(item.value, navigationActionNodeId.value)))
const navigationNodes = computed(() => {
  const nodes = builder.value?.nodes || []
  // 普通管理端运行用户只展示 pc/BOTH 菜单；H5 菜单由移动端客户端读取。
  // 编辑态和有应用编辑权限的工作台用户保留完整页面树，方便配置、
  // 维护移动端挂载页面；正式门户仍在 application-portal.vue 中按客户端隔离。
  const clientNodes = editing.value || canEditApplication.value
    ? nodes
    : filterNavigationNodesByClient(nodes, 'pc')
  // 有编辑权限的用户在工作台始终能看到所有页面（包括隐藏的），方便恢复显示
  // 普通用户在门户运行时只看到可见的页面
  const shouldShowAll = editing.value || canEditApplication.value
  return flattenNodes(shouldShowAll ? clientNodes : clientNodes.filter(isNavigationVisible), null, 0, collapsedGroupIds.value)
})
function readRouteDesignTab() {
  const raw = route.query.designTab
  return String(Array.isArray(raw) ? raw[0] : (raw ?? '')).trim()
}

const currentNode = computed(() => {
  const preferredId = editing.value
    ? String(route.query.pageId || selectedNodeId.value || '').trim()
    : String(selectedNodeId.value || route.query.pageId || '').trim()
  // 个人工作台：编辑态用虚拟节点驱动自由布局画布
  if (editing.value && isWorkbenchPageId(preferredId))
    return createWorkbenchVirtualNode()
  if (isPageManagementSystemPageId(selectedNodeId.value) && !isWorkbenchPageId(preferredId))
    return null
  // 当前内容不能依赖侧栏的折叠状态；分组收起后仍应保留已选页面。
  // 客户端过滤只用于确定可访问范围，不能把 collapsedGroupIds 带进内容解析。
  const nodes = editing.value || canEditApplication.value
    ? builder.value?.nodes || []
    : filterNavigationNodesByClient(builder.value?.nodes || [], 'pc')
  // 页面管理态以左侧选中为准，避免 URL 残留 pageId 导致「高亮 A、预览 B」
  // 编辑态优先 URL pageId（与 designResource 对齐）
  const matched = nodes.find(item => item.id === preferredId)
    || (preferredId !== String(selectedNodeId.value || '')
      ? nodes.find(item => item.id === selectedNodeId.value)
      : null)
  if (matched)
    return matched
  const designTab = readRouteDesignTab()
  // 编辑态 URL 已明确指向某页时，不要静默落到首页对象页（刷新后会出现空白对象卡）
  if (editing.value && preferredId && ['page', 'list', 'form', 'settings', 'publish'].includes(designTab))
    return null
  if (editing.value && designTab === 'page')
    return null
  return (!editing.value ? null : nodes.find(item => item.id === builder.value?.homePageId)) || null
})
const currentPage = computed(() => {
  if (!currentNode.value)
    return null
  if (isWorkbenchPageId(currentNode.value.id))
    return builder.value?.pages?.[WORKBENCH_PAGE_ID] || null
  return builder.value?.pages?.[currentNode.value.id] || null
})
const currentPageLayoutPolluted = computed(() => {
  if (!canEditApplication.value || !currentNode.value)
    return false
  return isObjectBoundPageLayoutPolluted(currentNode.value, currentPage.value)
})
const currentPageManagementTitle = computed(() => currentSystemPage.value?.title || currentNode.value?.title || '页面管理')
const pageDesignObject = computed(() => {
  const objectRef = currentNode.value?.objectRef
  if (!objectRef?.objectId && !objectRef?.objectCode)
    return null
  return objects.value.find(item => String(item.objectId) === String(objectRef.objectId) || item.objectCode === objectRef.objectCode)
    || {
      objectId: objectRef.objectId,
      objectCode: objectRef.objectCode,
      objectName: objectRef.objectName,
    }
})
const currentGridLayout = computed(() => {
  const layout = currentPage.value?.layout || {}
  if (layout.gridLayout && Array.isArray(layout.gridLayout.items))
    return layout.gridLayout
  return {
    cols: 12,
    rowHeight: 32,
    gap: 8,
    designWidth: 1366,
    layoutType: 'simple-crud',
    items: (layout.items || []).map((item, index) => createLegacyBlock(item, index)).filter(Boolean),
  }
})
const pageBlocks = computed(() => currentGridLayout.value.items || [])
const pageFlowStackMode = computed(() => shouldUsePageFlowStack(pageBlocks.value, {
  pageId: String(route.query.pageId || selectedNodeId.value || currentNode.value?.id || ''),
}))
const pageCanvasPadding = computed(() => normalizePagePadding(
  currentGridLayout.value?.pagePadding ?? currentPage.value?.layout?.pagePadding,
  DEFAULT_PAGE_PADDING,
))
const pageCanvasPaddingCss = computed(() => resolvePagePaddingCss(pageCanvasPadding.value))
const designerGridLayout = computed(() => currentGridLayout.value)
const pageFlowHeight = computed(() => {
  // 通栏文档流：高度随内容走，只保底可拖放区域
  if (pageFlowStackMode.value) {
    const editingFloor = typeof window !== 'undefined'
      ? Math.max(960, Math.round(window.innerHeight - 120))
      : 960
    return editing.value ? editingFloor : 680
  }
  const pad = pageCanvasPadding.value
  const useContentCoords = currentGridLayout.value?.pageFlowCoordSpace === 'content'
  const contentBottom = pageBlocks.value.reduce((bottom, block, index) => {
    const y = Number(block.props?.style?.pageFlowY)
    const height = Number(block.props?.style?.pageFlowHeight)
    const contentY = Number.isFinite(y) && y >= 0 ? y : resolveDefaultPageBlockYFromItems(pageBlocks.value, index)
    const canvasTop = useContentCoords
      ? pad.top + contentY
      : (Number.isFinite(y) && y >= 0 ? y : Math.max(pad.top, contentY))
    return Math.max(bottom, canvasTop + (height > 0 ? height : resolveDefaultPageBlockHeight(block)))
  }, pad.top)
  // 下边距计入画布高度，编辑态再预留可拖放空白
  const editingFloor = typeof window !== 'undefined'
    ? Math.max(960, Math.round(window.innerHeight - 120))
    : 960
  return Math.max(
    contentBottom + pad.bottom + (editing.value ? 280 : 48),
    editing.value ? editingFloor : 680,
  )
})
const hasCustomComponentButtonPosition = computed(() => Number.isFinite(componentButtonPosition.value.x) && Number.isFinite(componentButtonPosition.value.y))
const componentButtonStyle = computed(() => ({
  left: `${componentButtonPosition.value.x ?? 20}px`,
  ...(hasCustomComponentButtonPosition.value
    ? { top: `${componentButtonPosition.value.y}px` }
    : { bottom: '20px' }),
}))
const selectedPageBlock = computed(() => findPageBlockInTree(pageBlocks.value, selectedPageBlockId.value))
const formAssets = computed(() => builder.value?.formAssets || [])
const filteredFormAssets = computed(() => {
  const keyword = formAssetSelectorKeyword.value.trim().toLowerCase()
  return !keyword
    ? formAssets.value
    : formAssets.value.filter(asset => `${asset.name || ''}${asset.id || ''}`.toLowerCase().includes(keyword))
})
const pageFormAssetOptions = computed(() => formAssets.value.map(asset => ({
  label: `${asset.name || asset.formKey || asset.id}（${resolveFormAssetFields(asset).length} 个字段）`,
  value: asset.id,
})))
const activeFormAsset = computed(() => formAssets.value.find(asset => asset.id === activeFormAssetId.value) || null)
// activeFormDesignerSchema：从 computed 改为 ref + watch 签名去重。
// 原因：normalizeFormDesignerSchema 每次做深拷贝（JSON.parse/JSON.stringify），
// computed 每次求值都产生新对象引用 → 子组件反复重新渲染 → watch 链回环 →
// "Maximum recursive updates exceeded"。改为 ref 后只在内容真正变化时才更新引用。
const activeFormDesignerSchema = ref(normalizeFormDesignerSchema({}))
let _activeFormDesignerSchemaSignature = ''
watch(
  () => activeFormAsset.value?.formDesignerSchema,
  (source) => {
    const normalized = presentFormDesignerSchema(source || {})
    const signature = JSON.stringify(normalized)
    if (signature !== _activeFormDesignerSchemaSignature) {
      _activeFormDesignerSchemaSignature = signature
      activeFormDesignerSchema.value = normalized
    }
  },
  { immediate: true },
)
// 表单资产被区块引用后，设计器按该区块绑定的业务对象补齐关系/动作上下文。
const activeFormAssetBlock = computed(() => {
  const assetId = activeFormAssetId.value
  if (!assetId)
    return null
  let matched = null
  visitPageBlocksInTree(pageBlocks.value, (block) => {
    if (matched || !isDataFieldBlockType(block.blockType))
      return
    if (String(block.props?.formAssetId || '') === String(assetId))
      matched = block
  })
  return matched
})
const activeFormDesignerObjectRef = computed(() => {
  // 表单设计 Tab 默认不进 formDesignerMode；字段资产仍要按绑定对象补齐，
  // 否则货架只会从当前画布抽字段，「未使用」恒为空。
  const objectRef = resolvePageBlockObjectRef(activeFormAssetBlock.value || {})
  return isValidPageBlockObjectRef(objectRef) ? objectRef : null
})
const activeFormDesignerContext = computed(() => {
  const cacheKey = activeFormDesignerObjectRef.value ? resolveRuntimeObjectCacheKey(activeFormDesignerObjectRef.value) : ''
  return cacheKey ? formDesignerObjectContextByObjectId.value[cacheKey] || null : null
})
const activeFormDesignerRelations = computed(() => activeFormDesignerContext.value?.relations || [])
const activeFormDesignerActions = computed(() => activeFormDesignerContext.value?.actions || [])
const activeFormFields = computed(() => {
  const assetFields = activeFormAsset.value ? resolveFormAssetFields(activeFormAsset.value) : []
  const objectRef = activeFormDesignerObjectRef.value
  if (!objectRef)
    return assetFields
  const cacheKey = resolveRuntimeObjectCacheKey(objectRef)
  const designerFields = formDesignerObjectContextByObjectId.value[cacheKey]?.fields || []
  const runtimeFields = runtimeCrudPropsByObjectId.value[cacheKey]?.fieldCatalog || []
  const objectFields = designerFields.length ? designerFields : runtimeFields
  const protectedObjectFields = objectRef.hasBusinessData === true
    ? objectFields.map(field => ({
        ...field,
        locked: true,
        fieldBinding: {
          ...(field.fieldBinding || {}),
          locked: true,
        },
      }))
    : objectFields
  // 对象字段目录是字段资产货架的事实源；画布字段只补充尚未保存的新字段。
  // 不能只用当前表单 schema 当字段资产，否则未使用列表恒为空，删除组件后也无法回到未使用。
  return objectFields.length ? mergePageFieldCatalogs(assetFields, protectedObjectFields) : assetFields
})
const activeFormDataState = computed(() => formDataProvisioningByAssetId.value[activeFormAssetId.value] || { status: 'idle', message: '' })
const selectedPageBlockFormAssetId = computed(() => selectedPageBlock.value?.props?.formAssetId || (formAssets.value.length === 1 ? formAssets.value[0].id : ''))
const selectedPageBlockFormAsset = computed(() => formAssets.value.find(asset => asset.id === selectedPageBlockFormAssetId.value) || null)
const selectedPageBlockRuntimeObjectRef = computed(() => selectedPageBlock.value ? resolvePageBlockObjectRef(selectedPageBlock.value) : null)
const selectedPageBlockIsCrud = computed(() => selectedPageBlock.value?.blockType === 'AiCrudPage')
const selectedPageBlockSupportsDataSource = computed(() => isDataFieldBlockType(selectedPageBlock.value?.blockType))
const selectedPageBlockUsesObjectRuntime = computed(() => {
  if (!selectedPageBlockSupportsDataSource.value)
    return false
  return isValidPageBlockObjectRef(selectedPageBlockRuntimeObjectRef.value)
})
const selectedPageBlockRuntimeObjectId = computed(() => String(selectedPageBlockRuntimeObjectRef.value?.objectId ?? selectedPageBlockRuntimeObjectRef.value?.id ?? ''))
const selectedPageBlockObjectDesignerPanel = computed(() => {
  if (selectedPageBlock.value?.blockType === 'AiForm')
    return 'form'
  if (selectedPageBlock.value?.blockType === 'detail-info')
    return 'detail'
  return 'list'
})
const selectedPageBlockFormDataState = computed(() => formDataProvisioningByAssetId.value[selectedPageBlockFormAssetId.value] || { status: 'idle', message: '' })
const runtimeObjectFormOptions = computed(() => objects.value
  .filter(item => item.objectId ?? item.id)
  .map(item => ({
    value: String(item.objectId ?? item.id),
    label: item.objectName || item.objectCode || '未命名表单',
  })))
const pageTemplateOptions = computed(() => inAppPageTemplateCatalog.filter(template => ['blank', 'intro', 'crud', 'tree-list', 'tree-table', 'master-detail'].includes(template.key)))
const selectedPageBlockFields = computed(() => {
  if (!selectedPageBlock.value)
    return []
  const blockFields = resolvePageBlockFields(selectedPageBlock.value)
  if (blockFields.length)
    return blockFields
  return resolvePageContextFieldCatalog()
})
const selectedPageBlockFormDesignerSchema = computed(() => {
  const formAssetId = selectedPageBlock.value?.props?.formAssetId
    || (formAssets.value.length === 1 ? formAssets.value[0]?.id : '')
  const asset = formAssets.value.find(item => item.id === formAssetId)
    || formAssets.value[0]
  return asset?.formDesignerSchema || null
})

function resolvePageContextFieldCatalog() {
  const merged = []
  const seen = new Set()
  formAssets.value.forEach((asset) => {
    resolveFormAssetFields(asset).forEach((field) => {
      const code = field.fieldCode || field.field
      if (!code || seen.has(code))
        return
      seen.add(code)
      merged.push(field)
    })
  })
  return merged
}
const applicationGridModelSchema = computed(() => {
  // ListPageGridDesigner 会在 modelSchema 改变时同步并回写整个布局。
  // 这里必须保持页面级模型稳定；当前区块切换数据源仅更新 fields prop，避免循环回写卡死。
  const objectRef = resolvePageBlockObjectRef({})
  const cacheKey = resolveRuntimeObjectCacheKey(objectRef)
  const pageRuntimeFields = runtimeCrudPropsByObjectId.value[cacheKey]?.fieldCatalog || []
  return {
    businessName: currentNode.value?.title || '应用页面',
    // 列表设计器用 configKey 生成 /ai/crud/{configKey} 的默认接口。
    // 应用页必须把业务对象的真实配置键带过去，不能触发“当前配置”占位兜底。
    configKey: objectRef?.configKey || '',
    objectCode: objectRef?.objectCode || '',
    object: objectRef
      ? {
          code: objectRef.objectCode || '',
          configKey: objectRef.configKey || '',
        }
      : undefined,
    fields: pageRuntimeFields,
  }
})
const dragPreviewBlock = computed(() => dragPreview.value
  ? findPageBlockInTree(pageBlocks.value, dragPreview.value.blockId) || null
  : null)
const nestedMovingPageBlockId = ref('')
let nestedPageBlockMoveCtx = null
let nestedPageBlockMoveFrame = 0
let pendingNestedPageBlockMoveEvent = null
const copyBlockPageOptions = computed(() => flattenNodes(builder.value?.nodes || [])
  .filter(node => node.type === 'page' && node.id !== currentNode.value?.id)
  .map(node => ({ label: `${'　'.repeat(node.depth || 0)}${node.title}`, value: node.id })))
const dirty = computed(() => JSON.stringify(builder.value || {}) !== savedSignature.value)

/** 表单设计器保存：草稿有改动、DDL/对象落库失败重试、或首次尚未创建对象时都可点。 */
const canSaveActiveFormDesigner = computed(() => {
  if (activeFormDataState.value.status === 'error')
    return true
  if (dirty.value || embeddedDesignerDirty.value)
    return true
  // 新建页面后草稿已落盘但业务对象尚未创建，必须允许首次「保存应用页面设计」
  if (activePageShapeDesign.value && !activePageShapeDesign.value.objectId)
    return true
  return false
})

/**
 * 保存后对齐草稿签名。部分 watch/子组件会在 nextTick 后继续归一化 builder，
 * 只写一次签名会立刻再次变脏，表现为「保存成功但退出仍提示未保存」。
 */
async function markBuilderClean() {
  savedSignature.value = JSON.stringify(builder.value || {})
  await nextTick()
  savedSignature.value = JSON.stringify(builder.value || {})
  embeddedDesignerDirty.value = false
}

const {
  historyReady,
  canUndo,
  canRedo,
  resetBuilderHistory,
  undoBuilder,
  redoBuilder,
  handleBuilderShortcut,
} = useBuilderHistory({
  builder,
  scheduleNavigationSave,
  // 快捷键仅在编辑态且非表单设计器时生效
  isHistoryActive: () => editing.value && !formDesignerMode.value,
})
const loadErrorTitle = computed(() => isDraftMode.value ? '应用草稿加载失败' : '应用暂不可访问')
const filteredComponents = computed(() => {
  const keyword = componentKeyword.value.trim().toLowerCase()
  const seen = new Set()
  return listPageBlockCatalog.filter((item) => {
    const key = String(item.blockType || '').trim()
    if (!key || seen.has(key))
      return false
    if (item.hidden)
      return false
    seen.add(key)
    if (item.onlyFor && !item.onlyFor.includes('simple-crud'))
      return false
    if (!keyword)
      return true
    return `${item.title || ''}${item.techTitle || ''}${item.desc || ''}${item.blockType || ''}`.toLowerCase().includes(keyword)
  })
})
const componentPickerGroups = computed(() => componentPickerGroupOptions
  .map(group => ({
    ...group,
    items: filteredComponents.value.filter(item => resolveComponentPickerGroup(item) === group.key),
  }))
  .filter(group => group.items.length))

function resolveComponentPickerGroup(item = {}) {
  const descriptor = `${item.blockType || ''} ${item.title || ''} ${item.group || ''}`.toLowerCase()
  if (/chart|gauge|stat|progress|趋势|图表|指标/.test(descriptor))
    return 'chart'
  if (/crud|table|list|search|列表|查询|数据/.test(descriptor))
    return 'list'
  if (/title|text|html|layout|tabs|divider|view|标题|文本|布局|标签|分割/.test(descriptor))
    return 'view'
  return 'other'
}

function resolveComponentIcon(item = {}) {
  const fileName = formComponentIconFileByBlockType[item.blockType]
  if (!fileName)
    return ''
  const path = `/src/assets/images/form/${fileName}.png`
  if (formComponentIconCache[path])
    return formComponentIconCache[path]
  const loader = formComponentIconModules[path]
  if (!loader)
    return ''
  // 组件面板图标按需加载，避免运行页首屏同步打入全部 PNG。
  void loader().then((url) => {
    formComponentIconCache[path] = url
  })
  return formComponentIconCache[path] || ''
}

function resolveEmptyGuideIcon(item = {}) {
  const icons = {
    'page-title': TextOutline,
    'stats-strip': StatsChartOutline,
    'workspace-summary-metrics': StatsChartOutline,
    'AiCrudPage': ListOutline,
    'AiForm': ReaderOutline,
    'custom-html': DocumentTextOutline,
    'info-panel': InformationCircleOutline,
  }
  return icons[item.blockType] || SquareOutline
}

const recommendedComponents = computed(() => {
  const recommendedTypes = ['page-title', 'workspace-summary-metrics', 'stats-strip', 'AiCrudPage', 'AiForm', 'custom-html']
  return recommendedTypes
    .map(blockType => resolveListPageBlockMeta(blockType))
    .filter(Boolean)
    .slice(0, 6)
})

watch([
  () => route.params.applicationCode,
  () => route.query.edit,
  () => route.query.draft,
  canEditApplication,
], ([, edit]) => {
  editing.value = edit === '1'
  applicationRuntimeLoadCoordinator.run(resolveApplicationRuntimeLoadKey(route, canEditApplication.value))
}, { immediate: true })
watch(() => route.query.designResource, (resourceKey) => {
  selectedDesignerResourceKey.value = String(resourceKey || '')
})
watch(() => route.query.designTab, (tab) => {
  const next = resolvePageDesignTab(tab, String(route.query.pageId || selectedNodeId.value || '').trim())
  if (activePageDesignTab.value !== next)
    activePageDesignTab.value = next
})
// 应用节点加载完成后，按 URL designTab 重新对齐（修复刷新时 nodes 为空把 list 误写成 page）
watch([editing, () => builder.value?.nodes, () => route.query.pageId, () => route.query.designTab], () => {
  if (!editing.value || !builder.value)
    return
  const tab = readRouteDesignTab()
  if (!tab)
    return
  const next = resolvePageDesignTab(tab, String(route.query.pageId || selectedNodeId.value || '').trim())
  if (activePageDesignTab.value !== next)
    activePageDesignTab.value = next
}, { flush: 'post' })
// 编辑态且 URL 未显式带 designTab 时，按页面内容对齐默认 Tab（避免自由布局预览却进表单设计）
watch([editing, () => builder.value?.nodes, selectedNodeId, () => route.query.designTab], () => {
  if (!editing.value || !builder.value)
    return
  if (readRouteDesignTab() !== '')
    return
  const next = resolveEntryDesignTab(String(route.query.pageId || selectedNodeId.value || '').trim())
  if (activePageDesignTab.value !== next)
    activePageDesignTab.value = next
})
// designTab=page：强制页面设计 Tab，并回写 pageShape，避免节点形态丢失后只剩表单/列表
watch([editing, () => route.query.designTab, () => route.query.pageId, () => builder.value?.nodes], () => {
  if (!editing.value || !builder.value)
    return
  if (readRouteDesignTab() !== 'page')
    return
  if (!['settings', 'publish'].includes(activePageDesignTab.value) && activePageDesignTab.value !== 'page')
    activePageDesignTab.value = 'page'
  const pageId = String(route.query.pageId || selectedNodeId.value || '').trim()
  if (!pageId)
    return
  // 自由布局 URL 不要 resolve 到首页；保持 URL 指向的 pageId
  if (selectedNodeId.value !== pageId)
    selectedNodeId.value = pageId
  const node = (builder.value.nodes || []).find(item => String(item.id) === pageId)
  if (!node || node.type !== 'page')
    return
  const repaired = ensureFreeLayoutPageNode(node)
  if (repaired === node)
    return
  builder.value = {
    ...builder.value,
    nodes: builder.value.nodes.map(item => String(item.id) === pageId ? repaired : item),
  }
}, { flush: 'post' })
watch(() => route.query.view, (view) => {
  // 发布功能已迁移到独立发布页
  if (String(view || '').toLowerCase() === 'publish') {
    router.replace({ name: 'BusinessApplicationPublish', params: { applicationCode: route.params.applicationCode } })
    return
  }
  const next = resolveRuntimeView(view)
  if (runtimeViewMode.value !== next)
    runtimeViewMode.value = next
})
watch(() => activeDesignerResource.value?.key, (activeKey) => {
  activeFlowContext.value = {}
  if (activeDesignerResource.value?.kind === 'page-custom' && activeDesignerResource.value.pageId) {
    const resourcePageId = String(activeDesignerResource.value.pageId)
    const routePageId = String(route.query.pageId || selectedNodeId.value || '').trim()
    // 预览/运行态不要用设计资源回写 pageId，否则会从工作台跳到第一个业务菜单
    if (!editing.value)
      return
    if (isPageManagementSystemPageId(routePageId) && resourcePageId !== routePageId)
      return
    if (resourcePageId !== String(selectedNodeId.value || ''))
      selectNode(resourcePageId)
  }
  const requestedKey = String(route.query.designResource || '')
  if (!editing.value || !activeKey || !requestedKey || requestedKey === activeKey)
    return
  // 本地已指向 requestedKey（新建页刚写入）时，不要用 fallback 资源盖回去
  if (String(selectedDesignerResourceKey.value || '') === requestedKey && requestedKey !== activeKey)
    return
  selectedDesignerResourceKey.value = activeKey
  router.replace({
    query: {
      ...route.query,
      designResource: activeKey,
    },
  })
})
watch(() => route.query.pageId, (pageId) => {
  if (!builder.value)
    return
  const requested = String(pageId || '').trim()
  const nextPageId = (editing.value && readRouteDesignTab() === 'page' && requested)
    ? requested
    : (isWorkbenchPageId(requested) || resolvePageManagementSystemPage(requested)
      ? requested
      : resolveSelectablePageId(pageId))
  if (nextPageId === selectedNodeId.value)
    return
  selectedNodeId.value = nextPageId
  selectedPageBlockId.value = ''
  if (editing.value && activePageDesignTab.value === 'form' && !formDesignerMode.value)
    syncActiveFormAssetForPage(nextPageId)
})
watch(() => currentNode.value?.id, () => {
  preloadCurrentPageCrudRuntimeProps()
}, { flush: 'post' })
watch(editing, (value) => {
  if (value) {
    if (route.query.edit === '1')
      return
    router.replace({ query: { ...route.query, edit: '1' } })
    return
  }
  // 退出编辑：清掉设计态参数，但保留 pageId，避免左侧选中与预览脱节
  const hasDesignerQuery = route.query.edit != null
    || route.query.designResource != null
    || route.query.designTab != null
    || route.query.designSection != null
  if (!hasDesignerQuery)
    return
  const nextQuery = { ...route.query }
  delete nextQuery.edit
  delete nextQuery.designResource
  delete nextQuery.designTab
  delete nextQuery.designSection
  router.replace({ query: nextQuery })
})
watch(editing, (value) => {
  // 从运行态切入编辑态时补加载编辑器画布需要的渲染配置
  if (value && builder.value)
    preloadCurrentPageCrudRuntimeProps()
})
watch(() => navigationActionForm.value.icon, (icon) => {
  if (navigationActionMode.value !== 'icon' || !navigationActionNode.value)
    return
  builder.value = {
    ...builder.value,
    nodes: builder.value.nodes.map(item => item.id === navigationActionNode.value.id ? { ...item, icon: String(icon || '') } : item),
  }
})
onMounted(() => window.addEventListener('keydown', handleBuilderShortcut))
onBeforeUnmount(() => {
  endPageBlockResize()
  endPageBlockMove()
  endComponentButtonMove()
  window.removeEventListener('keydown', handleBuilderShortcut)
})

async function load() {
  const code = String(route.params.applicationCode || '')
  if (!code)
    return
  loading.value = true
  loadError.value = ''
  historyReady.value = false
  portalCrudSeed.value = {}
  resetRuntimeCrudConfig()
  runtimeTreeFilter.value = {}
  runtimeTreeFilterByBlockId.value = {}
  runtimeTreeActiveKeyByBlockId.value = {}
  // 与 workspace API 并行预拉页面渲染器 / CRUD / 设计器，缩短骨架结束后的二次白屏
  const warmChunks = [
    import('@/components/lowcode-builder/page/GridBlockRenderer.vue').catch(() => {}),
    import('@/components/ai-form/AiCrudPage.vue').catch(() => {}),
  ]
  if (editing.value) {
    warmChunks.push(
      import('@/components/lowcode-builder/page/ListPageGridDesigner.vue').catch(() => {}),
      import('@/views/app-center/components/designer/PageDesignSettingsPanel.vue').catch(() => {}),
    )
  }
  const warmChunksPromise = Promise.all(warmChunks)
  try {
    const response = shouldUseApplicationWorkspaceLoad(route, canEditApplication.value)
      ? await businessApplicationWorkspaceByCode(code)
      : await businessApplicationRuntimeByCode(code)
    const payload = response.data || {}
    application.value = payload.application || null
    objects.value = payload.objects || []
    workspaceExtensions.value = payload.extensions || []
    workspaceEntries.value = payload.entries || []
    builder.value = ensurePageTitleComponents(normalizeInAppBuilder(application.value?.options, application.value, objects.value))
    const ensuredWorkbench = ensureWorkbenchPageInBuilder(builder.value)
    builder.value = ensuredWorkbench.schema
    hydratePageCrudApiPlaceholders()
    bindSingleFormToCompatibleBlocks()
    savedSignature.value = JSON.stringify(builder.value)
    resetBuilderHistory(builder.value)
    if (hasPendingLegacyObjectPageMigration(application.value?.options, builder.value)) {
      try {
        await persistApplicationDraft()
        savedSignature.value = JSON.stringify(builder.value)
        message.info('已将旧版业务对象页面恢复到新版页面结构')
      }
      catch (error) {
        message.warning(error?.message || '旧版业务对象页面已恢复到当前页面，但草稿自动保存失败，请手动保存')
      }
    }
    const requestedPageId = String(route.query.pageId || '').trim()
    const designTab = readRouteDesignTab()
    const requestedNodeExists = Boolean(
      requestedPageId
      && (builder.value?.nodes || []).some(item => String(item.id) === requestedPageId),
    )
    // 编辑态带 designTab / 节点已存在时，保留 URL pageId，避免刷新落到首页空白对象卡
    selectedNodeId.value = (
      resolvePageManagementSystemPage(requestedPageId)
      || (requestedPageId && designTab === 'page')
      || (requestedPageId && ['list', 'form', 'settings', 'publish'].includes(designTab))
      || requestedNodeExists
    )
      ? requestedPageId
      : resolveSelectablePageId(route.query.pageId)
    if (editing.value)
      syncActiveFormAssetForPage(selectedNodeId.value)
    await nextTick()
    // 非编辑态首屏：骨架阶段把第一个业务页的 CRUD render 预热完（或超时），减少壳出后空白
    if (!editing.value && !isPageManagementSystemPageId(selectedNodeId.value))
      portalCrudSeed.value = await warmCurrentPortalPageCrud()
    preloadCurrentPageCrudRuntimeProps()
    if (canEditApplication.value)
      prefetchRuntimeWorkspacePanels()
    // 编辑态：等设计器 chunk 就绪再撤骨架，避免「壳出来了但画布白屏」
    if (editing.value)
      await warmChunksPromise
  }
  catch (error) {
    application.value = null
    objects.value = []
    builder.value = null
    selectedNodeId.value = ''
    portalCrudSeed.value = {}
    loadError.value = String(
      error?.message
      || error?.detail?.rawMessage
      || error?.error?.message
      || '应用配置加载失败，请稍后重试。',
    )
  }
  finally {
    await warmChunksPromise
    loading.value = false
  }
}

async function warmCurrentPortalPageCrud() {
  const pageId = String(selectedNodeId.value || '').trim()
  if (!pageId || !builder.value)
    return {}
  const node = (builder.value.nodes || []).find(item => String(item?.id || '') === pageId) || null
  const page = builder.value.pages?.[pageId] || null
  if (!node && !page)
    return {}
  const targets = collectPortalPageCrudTargets({
    page,
    node,
    objects: objects.value,
    entries: workspaceEntries.value,
  })
  if (!targets.length)
    return {}
  return warmPortalPageCrudProps(targets, {
    // 工作台编辑者预览草稿配置；正式门户路由不会走这条预热
    designPreview: usePortalDesignPreview.value,
    applicationId: application.value?.id,
    pageId,
    timeoutMs: 6500,
  })
}

function selectNode(nodeId) {
  const nextId = nodeId || ''
  if (String(nextId) !== String(selectedNodeId.value || '')) {
    runtimeTreeFilter.value = {}
    runtimeTreeFilterByBlockId.value = {}
    runtimeTreeActiveKeyByBlockId.value = {}
  }
  selectedNodeId.value = nextId
  selectedPageBlockId.value = ''
  // 保留路由现有 edit 参数，选择页面不应切换编辑/运行模式，
  // 也不要用可能过期的 editing.value 重建，避免覆盖并发导航中的 edit 参数。
  const nextQuery = {
    ...route.query,
    pageId: nextId || undefined,
  }
  // 编辑态下 pageId 与 designResource 必须同指一页，否则会改到旧页
  if (nextId && (editing.value || route.query.edit === '1')) {
    const resourceKey = `page-custom:${nextId}`
    selectedDesignerResourceKey.value = resourceKey
    nextQuery.designResource = resourceKey
  }
  router.replace({ query: nextQuery })
}

function resolveSelectablePageId(pageId) {
  const nodes = editing.value || canEditApplication.value
    ? builder.value?.nodes || []
    : filterNavigationNodesByClient(builder.value?.nodes || [], 'pc')
  return resolvePageManagementSelection(
    nodes,
    pageId,
    builder.value?.homePageId,
  )
}

function ensurePageTitleComponents(schema) {
  if (!schema?.pages || !Array.isArray(schema.nodes))
    return schema
  const nodeMap = new Map(schema.nodes.map(node => [node.id, node]))
  let changed = false
  const pages = Object.fromEntries(Object.entries(schema.pages).map(([pageId, page]) => {
    const node = nodeMap.get(pageId)
    if (node?.type !== 'page' || page?.layout?.pageTitleComponentInitialized)
      return [pageId, page]
    // 不再自动添加 page-title 区块，仅标记为已初始化
    changed = true
    return [pageId, {
      ...page,
      layout: {
        ...(page?.layout || {}),
        pageTitleComponentInitialized: true,
      },
    }]
  }))
  return changed ? { ...schema, pages } : schema
}

function createQuickNode(type = 'page', parentId = null) {
  const normalizedType = type === 'group' ? 'group' : 'page'
  if (normalizedType === 'page') {
    openPageTypeSelector(parentId)
    return
  }
  const previousIds = new Set(builder.value.nodes.map(item => item.id))
  builder.value = createNavigationNode(builder.value, {
    type: normalizedType,
    title: resolveNextNavigationTitle(normalizedType),
    pageType: 'content',
    parentId,
  })
  const created = builder.value.nodes.find(item => !previousIds.has(item.id))
  newNodePopoverVisible.value = false
  // 确保父分组展开，使新建的子节点可见
  if (parentId && collapsedGroupIds.value.has(parentId)) {
    const next = new Set(collapsedGroupIds.value)
    next.delete(parentId)
    collapsedGroupIds.value = next
  }
  if (created?.type === 'page')
    selectNode(created.id)
  // 页面组仅在内存中创建，后续进入编辑模式会触发路由变化和后端重载，
  // 必须先持久化草稿否则新建的页面组会被重载覆盖。
  if (created?.type === 'group')
    saveDraft()
}

function toggleGroupExpanded(groupId) {
  const next = new Set(collapsedGroupIds.value)
  if (next.has(groupId))
    next.delete(groupId)
  else
    next.add(groupId)
  collapsedGroupIds.value = next
}

function isGroupCollapsed(groupId) {
  return collapsedGroupIds.value.has(groupId)
}

function openPageTypeSelector(parentId = null, defaultPageType = 'form') {
  pageTypeSelectorParentId.value = parentId || null
  pageTypeSelectorDefaultType.value = defaultPageType || 'form'
  pageTypeSelectorVisible.value = true
  newNodePopoverVisible.value = false
  // 页面管理态若 URL 仍残留旧 designResource，先清掉，避免确认创建后串回旧页
  if (!editing.value) {
    selectedDesignerResourceKey.value = ''
    if (route.query.designResource || route.query.designTab || route.query.edit) {
      const nextQuery = { ...route.query }
      delete nextQuery.designResource
      delete nextQuery.designTab
      delete nextQuery.designSection
      delete nextQuery.edit
      router.replace({ query: nextQuery })
    }
  }
  // 不要提前进入编辑态：弹窗应叠在当前运行页上，创建成功后再跳转到设计页。
  // 确保父分组展开
  if (parentId && collapsedGroupIds.value.has(parentId)) {
    const next = new Set(collapsedGroupIds.value)
    next.delete(parentId)
    collapsedGroupIds.value = next
  }
}

async function handlePageTypeSelection(selection = {}) {
  // 父页面组由打开弹窗的当前操作上下文决定。不能使用选择器内部上次打开时缓存的 parentId，
  // 否则“新建页面组后立即新增页面”会把旧组传给 schema 校验。
  const parentId = pageTypeSelectorParentId.value == null || pageTypeSelectorParentId.value === ''
    ? null
    : String(pageTypeSelectorParentId.value)
  if (parentId && !builder.value?.nodes?.some(node => String(node.id) === parentId && node.type === 'group')) {
    message.warning('目标页面组已不存在，请在页面树中重新选择页面组')
    return
  }
  designerTransitionLoading.value = true
  try {
    const result = createPageShapeBuilder(builder.value, {
      ...selection,
      parentId,
    })
    builder.value = result.schema
    pageTypeSelectorVisible.value = false
    pageTypeSelectorParentId.value = null
    const customPage = result.selection?.pageType === 'custom' || !result.formAssetId
    if (customPage)
      activePageDesignTab.value = 'page'
    // 先落盘草稿再进编辑：避免进入 edit 时若触发重载，服务端还没有新页
    try {
      await persistApplicationDraft()
      savedSignature.value = JSON.stringify(builder.value)
    }
    catch (persistError) {
      message.warning(persistError?.message || '页面已创建，但草稿暂未保存成功，请稍后手动保存')
    }
    selectCreatedDesignerPage(result.pageId, { designTab: customPage ? 'page' : undefined })
    if (!result.formAssetId)
      return
    activePageShapeDesign.value = {
      pageId: result.pageId,
      pageType: result.selection.pageType,
      formAssetId: result.formAssetId,
      objectId: null,
      objectCode: result.selection.objectCode,
      objectName: result.selection.objectName,
      runtimeDatasourceId: result.selection.runtimeDatasourceId || null,
      createMode: result.selection.createMode || '',
      importDatasourceId: result.selection.importDatasourceId || null,
      importTableName: result.selection.importTableName || '',
    }
    activeFormAssetId.value = result.formAssetId
    selectedPageBlockId.value = builder.value.pages?.[result.pageId]?.layout?.gridLayout?.items?.[0]?.id || ''
    formDesignerMode.value = true
    await nextTick()
  }
  catch (error) {
    message.error(error?.message || '页面创建失败，请刷新后重试')
  }
  finally {
    designerTransitionLoading.value = false
  }
}

function selectIntroTemplate(templateKey) {
  selectedPageTemplateKey.value = templateKey
  createPageFromTemplate(templateKey)
}

function resolvePageTemplateIcon(template = {}) {
  const icons = {
    'blank': DocumentTextOutline,
    'intro': InformationCircleOutline,
    'crud': ListOutline,
    'tree-list': GitNetworkOutline,
    'tree-table': GitBranchOutline,
    'master-detail': ReaderOutline,
  }
  return icons[template.key] || DocumentTextOutline
}

function createPageFromTemplate(templateKey = selectedPageTemplateKey.value, initialBlockType = '', parentId = null) {
  const template = resolveInAppPageTemplate(templateKey)
  const previousIds = new Set(builder.value.nodes.map(item => item.id))
  builder.value = createNavigationNode(builder.value, {
    type: 'page',
    title: resolveNextNavigationTitle('page'),
    parentId,
    pageType: template.dataTemplate ? 'content' : template.pageType || 'content',
    pageTemplate: template.key,
    pageShape: (template.dataTemplate || template.pageType === 'content' || template.key === 'blank' || template.key === 'intro')
      ? 'custom'
      : (['form', 'list', 'list-form', 'tree-list', 'tree-table'].includes(template.key) ? template.key : 'custom'),
  })
  const created = builder.value.nodes.find(item => !previousIds.has(item.id))
  if (created) {
    const freeLayout = created.pageType !== 'object'
    selectCreatedDesignerPage(created.id, { designTab: freeLayout ? 'page' : undefined })
    applyPageTemplate(created.id, template.key, initialBlockType)
    if (template.dataTemplate || initialBlockType === 'AiCrudPage')
      createFormAssetForPageCrud(created.id)
  }
}

function selectCreatedDesignerPage(pageId, options = {}) {
  const resourceKey = `page-custom:${pageId}`
  selectedNodeId.value = pageId
  selectedPageBlockId.value = ''
  selectedDesignerResourceKey.value = resourceKey
  formDesignerMode.value = false
  newNodePopoverVisible.value = false
  const designTab = options.designTab === 'page'
    ? 'page'
    : options.designTab || undefined
  if (designTab)
    activePageDesignTab.value = designTab
  else if (!options.designTab)
    activePageDesignTab.value = 'form'
  // 只走一次路由更新。不要先写 editing.value=true：
  // watch(editing) 会用当时仍带旧 pageId 的 route.query 再 replace，把新建页盖回旧页。
  router.replace({
    query: {
      ...route.query,
      edit: '1',
      designResource: resourceKey,
      designSection: undefined,
      designTab,
      pageId,
      view: undefined,
    },
  })
}

function createFormAssetForPageCrud(pageId) {
  const page = builder.value?.pages?.[pageId]
  const items = page?.layout?.gridLayout?.items || []
  const crud = items.find(item => item?.blockType === 'AiCrudPage')
  if (!crud)
    return
  const pageNode = builder.value.nodes.find(node => node.id === pageId)
  const name = `${pageNode?.title || '页面'}数据表单`
  const result = createInAppFormAsset(builder.value, {
    name,
    formDesignerSchema: createDefaultFormDesignerSchema({
      objectCode: application.value?.applicationCode || 'application',
      objectName: name,
      formName: name,
    }),
  })
  const resultPage = result.schema.pages?.[pageId]
  const resultItems = resultPage?.layout?.gridLayout?.items || []
  builder.value = {
    ...result.schema,
    pages: {
      ...result.schema.pages,
      [pageId]: {
        ...resultPage,
        layout: {
          ...resultPage.layout,
          gridLayout: {
            ...resultPage.layout.gridLayout,
            items: resultItems.map(item => item.id === crud.id
              ? {
                  ...item,
                  props: {
                    ...(item.props || {}),
                    formAssetId: result.formAssetId,
                    formAssetFieldsInitialized: false,
                  },
                }
              : item),
          },
        },
      },
    },
  }
  selectedPageBlockId.value = crud.id
  activeFormAssetId.value = result.formAssetId
  formDesignerMode.value = false
  configPanelVisible.value = true
  inspectorTab.value = 'data'
}

function resolveNextNavigationTitle(type = 'page') {
  const prefix = type === 'group' ? '页面组' : '页面'
  const count = (builder.value?.nodes || []).filter(node => node.type === type).length + 1
  return `${prefix} ${count}`
}

function applyPageTemplate(pageId, templateKey, initialBlockType = '') {
  const template = resolveInAppPageTemplate(templateKey)
  const blockTypes = initialBlockType ? [initialBlockType] : template.blockTypes
  const items = blockTypes
    .map((blockType, index) => attachDefaultRuntimeObject(createGridBlock(blockType, { businessName: currentNode.value?.title || '应用页面', fields: [] }, { gridX: 0, gridY: index * 2 })))
    .filter(Boolean)
  const page = builder.value?.pages?.[pageId]
  if (!page)
    return
  builder.value = {
    ...builder.value,
    pages: {
      ...builder.value.pages,
      [pageId]: {
        ...page,
        layout: {
          ...page.layout,
          items: [],
          // 空白页不再被旧页面迁移逻辑补入标题组件。
          pageTitleComponentInitialized: true,
          gridLayout: { cols: 12, rowHeight: 32, gap: 8, designWidth: 1366, layoutType: 'simple-crud', items },
        },
      },
    },
  }
}

function resolveNavigationMoreOptions(node) {
  const siblings = (builder.value?.nodes || []).filter(item => item.parentId === node.parentId).sort((a, b) => a.sort - b.sort)
  const index = siblings.findIndex(item => item.id === node.id)
  return [
    ...(
      node.type === 'group'
        ? [
            { label: '在本组新建页面', key: 'create-page', icon: () => renderNavigationMenuIcon(AddOutline) },
            { label: '在本组新建页面组', key: 'create-group', icon: () => renderNavigationMenuIcon(FolderOpenOutline) },
            { type: 'divider', key: 'create-divider' },
          ]
        : []
    ),
    { label: '重命名', key: 'rename', icon: () => renderNavigationMenuIcon(CreateOutline) },
    { label: '更改图标', key: 'icon', icon: () => renderNavigationMenuIcon(ColorFillOutline) },
    { label: isNavigationVisible(node) ? '隐藏菜单' : '显示菜单', key: 'toggle-visible', icon: () => renderNavigationMenuIcon(isNavigationVisible(node) ? EyeOutline : EyeOffOutline) },
    ...(
      node.type === 'page'
        ? [{
            label: isPageSystemMenuMounted(node) ? '修改系统菜单挂载' : '挂载到系统菜单',
            key: 'system-menu-mount',
            icon: () => renderNavigationMenuIcon(GridOutline),
          }]
        : []
    ),
    { label: '复制', key: 'duplicate', icon: () => renderNavigationMenuIcon(CopyOutline) },
    { label: '移动至', key: 'move', icon: () => renderNavigationMenuIcon(MoveOutline) },
    { type: 'divider', key: 'move-divider' },
    { label: '上移', key: 'move-up', disabled: index <= 0, icon: () => renderNavigationMenuIcon(ArrowUpOutline) },
    { label: '下移', key: 'move-down', disabled: index < 0 || index >= siblings.length - 1, icon: () => renderNavigationMenuIcon(ArrowDownOutline) },
    { type: 'divider', key: 'danger-divider' },
    { label: '删除', key: 'delete', icon: () => renderNavigationMenuIcon(TrashOutline) },
  ]
}

function renderNavigationMenuIcon(icon) {
  return h(NIcon, { size: 16, class: 'navigation-menu-icon' }, { default: () => h(icon) })
}

function handleNavigationMoreSelect(key, node) {
  if (key === 'create-page' || key === 'create-group') {
    createQuickNode(key === 'create-group' ? 'group' : 'page', node.id)
    return
  }
  if (key === 'move-up' || key === 'move-down') {
    moveNavigationByOffset(node, key === 'move-up' ? -1 : 1)
    return
  }
  if (key === 'toggle-visible') {
    toggleNavigationVisible(node)
    return
  }
  if (key === 'system-menu-mount') {
    openSystemMenuMountDialog(node)
    return
  }
  if (key === 'duplicate') {
    duplicateNavigationNode(node)
    return
  }
  if (key === 'icon') {
    iconPickerNodeId.value = node.id
    iconPickerVisible.value = true
    nextTick(() => document.querySelector('.navigation-icon-picker .icon-selector button')?.click())
    return
  }
  navigationActionNodeId.value = node.id
  navigationActionMode.value = key
  navigationActionForm.value = {
    title: node.title || '',
    icon: node.icon || '',
    parentId: node.parentId || null,
    deleteStrategy: 'delete-children',
    targetParentId: null,
  }
  navigationActionVisible.value = true
}

function toggleNavigationVisible(node) {
  const isVisible = isNavigationVisible(node)
  builder.value = {
    ...builder.value,
    nodes: builder.value.nodes.map(item => item.id === node.id
      ? { ...item, navigationVisible: !isVisible }
      : item),
  }
  scheduleNavigationSave()
}

function openSystemMenuMountDialog(node) {
  if (!node || node.type !== 'page')
    return
  systemMenuMountNodeId.value = node.id
  systemMenuMountVisible.value = true
}

function patchNavigationNodeMount(nodeId, partial = {}) {
  if (!nodeId || !builder.value)
    return
  builder.value = {
    ...builder.value,
    nodes: builder.value.nodes.map((item) => {
      if (item.id !== nodeId)
        return item
      return applyPageMountFieldsToNode(item, partial)
    }),
  }
  scheduleNavigationSave()
}

function confirmSystemMenuMount(partial = {}) {
  const nodeId = systemMenuMountNodeId.value
  if (!nodeId)
    return
  patchNavigationNodeMount(nodeId, {
    ...partial,
    systemMenuVisible: true,
  })
  systemMenuMountVisible.value = false
  message.success('系统菜单挂载已写入草稿，应用发布后生效')
}

function unmountSystemMenu() {
  const nodeId = systemMenuMountNodeId.value
  if (!nodeId)
    return
  patchNavigationNodeMount(nodeId, { systemMenuVisible: false })
  systemMenuMountVisible.value = false
  message.success('已取消系统菜单挂载（应用发布后生效）')
}

function duplicateNavigationNode(node) {
  const copyId = `${node.type}_${Date.now()}`
  const copy = {
    ...JSON.parse(JSON.stringify(node)),
    id: copyId,
    title: `${node.title} 副本`,
    sort: Number(node.sort || 0) + 1,
  }
  builder.value = {
    ...builder.value,
    nodes: [...builder.value.nodes, copy],
    pages: node.type === 'page' && builder.value.pages[node.id]
      ? { ...builder.value.pages, [copyId]: JSON.parse(JSON.stringify(builder.value.pages[node.id])) }
      : builder.value.pages,
  }
  if (copy.type === 'page')
    selectNode(copy.id)
  scheduleNavigationSave()
}

function moveNavigationByOffset(node, offset) {
  const siblings = (builder.value?.nodes || []).filter(item => item.parentId === node.parentId).sort((a, b) => a.sort - b.sort)
  const index = siblings.findIndex(item => item.id === node.id)
  if (index < 0 || index + offset < 0 || index + offset >= siblings.length)
    return
  try {
    builder.value = moveNavigationNode(builder.value, node.id, node.parentId, offset < 0 ? index - 1 : index + 1)
    scheduleNavigationSave()
  }
  catch (error) {
    message.error(error?.message || '页面排序失败')
  }
}

function confirmNavigationAction() {
  const node = navigationActionNode.value
  if (!node)
    return
  try {
    if (navigationActionMode.value === 'rename') {
      const title = navigationActionForm.value.title.trim()
      if (!title) {
        message.warning('请输入名称')
        return
      }
      builder.value = {
        ...builder.value,
        nodes: builder.value.nodes.map(item => item.id === node.id ? { ...item, title } : item),
        pages: node.type === 'page'
          ? { ...builder.value.pages, [node.id]: { ...builder.value.pages[node.id], title } }
          : builder.value.pages,
      }
    }
    if (navigationActionMode.value === 'icon') {
      builder.value = {
        ...builder.value,
        nodes: builder.value.nodes.map(item => item.id === node.id ? { ...item, icon: navigationActionForm.value.icon.trim() } : item),
      }
    }
    if (navigationActionMode.value === 'move')
      builder.value = moveNavigationNode(builder.value, node.id, navigationActionForm.value.parentId)
    if (navigationActionMode.value === 'delete') {
      const strategy = navigationActionHasChildren.value
        ? {
            type: navigationActionForm.value.deleteStrategy,
            targetParentId: navigationActionForm.value.targetParentId,
          }
        : undefined
      if (strategy?.type === 'move-children' && !strategy.targetParentId && strategy.targetParentId !== null) {
        message.warning('请选择子项移动目标')
        return
      }
      builder.value = removeNavigationNode(builder.value, node.id, strategy)
      if (!builder.value.nodes.some(item => item.id === selectedNodeId.value))
        selectNode(builder.value.homePageId)
      // 资源树选中项可能指向已删除页面，回落到首页节点，避免设计区停留在空资源。
      if (String(activeDesignerResource.value?.pageId || '') === String(node.id)) {
        applyDesignerResource({
          key: `page-custom:${String(builder.value.homePageId)}`,
          kind: 'page-custom',
          pageId: String(builder.value.homePageId),
        })
      }
    }
    navigationActionVisible.value = false
    scheduleNavigationSave()
  }
  catch (error) {
    message.error(error?.message || '页面操作失败')
  }
}

function isNavigationGroupDescendant(candidateId, nodeId) {
  if (!candidateId || !nodeId)
    return false
  const nodes = builder.value?.nodes || []
  let current = nodes.find(item => item.id === candidateId)
  const visited = new Set()
  while (current?.parentId && !visited.has(current.id)) {
    if (current.parentId === nodeId)
      return true
    visited.add(current.id)
    current = nodes.find(item => item.id === current.parentId)
  }
  return false
}

function insertComponent(component) {
  appendPageBlock(component.blockType)
  componentPopoverVisible.value = false
}

function updateCurrentGridLayout(gridLayout) {
  const pageId = resolveActiveDesignerPageId()
  if (!pageId || !builder.value)
    return
  let page = builder.value.pages?.[pageId]
  if (!page && isWorkbenchPageId(pageId)) {
    const ensured = ensureWorkbenchPageInBuilder(builder.value)
    builder.value = ensured.schema
    page = ensured.page
  }
  if (!page)
    return
  const previous = page.layout?.gridLayout && typeof page.layout.gridLayout === 'object'
    ? page.layout.gridLayout
    : {}
  const nextLayout = {
    ...previous,
    ...(gridLayout && typeof gridLayout === 'object' ? gridLayout : {}),
  }
  // 属性面板回写整份 layout 时可能丢掉 pagePadding，这里强制保留。
  if (nextLayout.pagePadding == null && previous.pagePadding != null)
    nextLayout.pagePadding = previous.pagePadding
  builder.value = {
    ...builder.value,
    pages: {
      ...builder.value.pages,
      [pageId]: {
        ...page,
        layout: { ...page.layout, items: [], gridLayout: nextLayout },
      },
    },
  }
}

function ensurePageFlowContentCoords(layout = {}, pad = pageCanvasPadding.value) {
  if (pageFlowStackMode.value || layout?.pageFlowCoordSpace === 'content')
    return layout
  return migratePageFlowToContentCoords(layout, pad)
}

function updatePageCanvasPadding(partial = {}) {
  const previous = pageCanvasPadding.value
  const next = normalizePagePadding({
    ...previous,
    ...(partial && typeof partial === 'object' ? partial : {}),
  })
  // 绝对自由布局：先迁到内容区坐标，再改 padding；上下左右都即时生效，无需再平移 X/Y。
  let layout = { ...(currentGridLayout.value || {}) }
  if (!pageFlowStackMode.value)
    layout = ensurePageFlowContentCoords(layout, previous)
  layout = { ...layout, pagePadding: next }
  updateCurrentGridLayout(layout)
}

/** 编辑态写画布时锁定目标页，避免 currentNode 漂移把组件写到首页 */
function resolveActiveDesignerPageId() {
  const routeId = String(route.query.pageId || '').trim()
  const selectedId = String(selectedNodeId.value || '').trim()
  if (editing.value && routeId)
    return routeId
  return selectedId || routeId
}

function appendPageBlock(blockType) {
  const meta = resolveListPageBlockMeta(blockType)
  if (!meta)
    return
  if (meta.unique && pageBlocks.value.some(block => block.blockType === blockType)) {
    message.info(`${meta.title} 每个页面只能添加一个`)
    return
  }
  const nextY = resolveNextPageBlockStackY(pageBlocks.value)
  let block = createGridBlock(blockType, applicationGridModelSchema.value, {
    gridX: 0,
    gridY: pageBlocks.value.length * 2,
  })
  if (!block)
    return
  block = attachDefaultRuntimeObject(block)
  block = attachSingleFormAsset(block)
  const height = resolveDefaultPageBlockHeight(block)
  // 内容区相对坐标：通栏 X=0，Y 从内容区顶向下堆叠
  if (!pageFlowStackMode.value)
    updateCurrentGridLayout(ensurePageFlowContentCoords({ ...(currentGridLayout.value || {}) }))
  block = {
    ...block,
    props: {
      ...(block.props || {}),
      style: {
        ...(block.props?.style || {}),
        pageFlowX: 0,
        pageFlowY: Math.max(0, nextY),
        pageFlowHeight: height,
        widthMode: block.props?.style?.widthMode || 'full',
        heightMode: block.props?.style?.heightMode
          || (['grid-layout', 'card', 'box-layout', 'tabs'].includes(blockType) ? 'auto' : 'fixed'),
        width: '100%',
        height,
      },
    },
  }
  updatePageBlocks([...pageBlocks.value, block], { resolveCollisions: true, changedBlockId: block.id })
  selectedPageBlockId.value = block.id
  preloadPageBlockCrudRuntimeProps(block)
}

/** 加号新增：始终落在现有根级组件下方，自上而下排列 */
function resolveNextPageBlockStackY(items = []) {
  const gap = 16
  const topPadding = 20
  if (!items.length)
    return topPadding
  return items.reduce((bottom, item, index) => {
    const style = item?.props?.style || {}
    const y = Number.isFinite(Number(style.pageFlowY)) && Number(style.pageFlowY) >= 0
      ? Number(style.pageFlowY)
      : resolveDefaultPageBlockYFromItems(items, index)
    const height = Number(style.pageFlowHeight) > 0
      ? Number(style.pageFlowHeight)
      : resolveDefaultPageBlockHeight(item)
    return Math.max(bottom, y + height + gap)
  }, topPadding)
}

function supportsFormAsset(block = {}) {
  return DATA_FIELD_BLOCK_TYPES.includes(block.blockType)
}

function bindSingleFormToCompatibleBlocks() {
  if (formAssets.value.length !== 1 || !builder.value)
    return
  const nextBuilder = {
    ...builder.value,
    pages: Object.fromEntries(Object.entries(builder.value.pages || {}).map(([pageId, page]) => {
      const items = page?.layout?.gridLayout?.items
      if (!Array.isArray(items))
        return [pageId, page]
      return [pageId, {
        ...page,
        layout: {
          ...page.layout,
          gridLayout: {
            ...page.layout.gridLayout,
            items: items.map(item => attachSingleFormAsset(item)),
          },
        },
      }]
    })),
  }
  if (JSON.stringify(nextBuilder) === JSON.stringify(builder.value))
    return
  builder.value = nextBuilder
}

function attachSingleFormAsset(block = {}) {
  if (formAssets.value.length !== 1 || !supportsFormAsset(block))
    return block
  const asset = formAssets.value[0]
  if (block.props?.formAssetId && block.props.formAssetId !== asset.id)
    return block
  const fieldRefs = resolveFormAssetFields(asset).map(field => field.fieldCode)
  const synced = syncFormBoundFieldRefs({
    formFieldCodes: fieldRefs,
    searchFieldRefs: block.props?.searchFieldRefs,
  })
  const currentSettings = block.props?.fieldSettings || {}
  const allBoundFieldsHidden = synced.fieldRefs.length > 0 && synced.fieldRefs.every(field => currentSettings[field]?.visible === false)
  return {
    ...block,
    fieldRefs: synced.fieldRefs,
    props: {
      ...(block.props || {}),
      formAssetId: asset.id,
      formAssetFieldsInitialized: synced.fieldRefs.length > 0,
      fieldSettings: createFormFieldVisibilitySettings(currentSettings, synced.fieldRefs, !block.props?.formAssetId || allBoundFieldsHidden),
      ...(['AiCrudPage', 'search-form'].includes(block.blockType)
        ? { searchFieldRefs: synced.searchFieldRefs }
        : {}),
    },
  }
}

function resolveFormAssetFields(asset = {}) {
  const schema = normalizeFormDesignerSchema(asset.formDesignerSchema || {})
  const fieldsByCode = new Map(buildAutoFieldAssets(schema).fields.map(field => [field.fieldCode || field.field, field]))
  const widgetNodes = []
  const childFields = []
  const appendComponentFields = (components = []) => {
    ;(Array.isArray(components) ? components : []).forEach((component, index) => {
      const fieldCode = component?.fieldBinding?.fieldCode || component?.field || ''
      const componentKey = component?.componentKey || component?.type || ''
      if (componentKey === 'subTable') {
        const relationKey = String(component?.props?.relationKey || fieldCode || component?.id || '').trim()
        const header = String(component?.props?.header || component?.label || relationKey || '子表').trim()
        const columns = Array.isArray(component?.props?.columns) ? component.props.columns : []
        columns.forEach((column, columnIndex) => {
          const childCode = String(column?.fieldCode || column?.field || column?.key || column?.value || '').trim()
          if (!childCode || !relationKey)
            return
          childFields.push({
            field: `${relationKey}__${childCode}`,
            fieldCode: `${relationKey}__${childCode}`,
            sourceField: childCode,
            label: column.label || column.title || childCode,
            fieldName: column.label || column.title || childCode,
            modelCode: relationKey,
            modelName: header,
            sourceLabel: header,
            fieldScope: 'child',
            listVisible: true,
            formVisible: true,
            fieldStatus: 'ENABLED',
            systemField: false,
            dataType: column.dataType || column.fieldType || 'STRING',
            _order: columnIndex,
          })
        })
      }
      else if (component?.fieldBinding?.mode === 'virtual' && componentKey && isPageWidgetComponentKey(componentKey)) {
        widgetNodes.push({
          field: fieldCode || component?.id || `widget_${componentKey}_${index}`,
          label: component.label || componentKey,
          componentKey,
          type: componentKey,
          nodeType: 'widget',
          span: component.layout?.span ?? component.span ?? 1,
          props: component.props || {},
          fieldBinding: { mode: 'virtual' },
          showLabel: false,
          showFeedback: false,
          formVisible: true,
          listVisible: false,
        })
      }
      else if (component?.fieldBinding?.mode !== 'virtual' && isFieldComponent(component) && fieldCode && !fieldsByCode.has(fieldCode)) {
        fieldsByCode.set(fieldCode, createFieldFromComponent(component, index))
      }
      appendComponentFields(component?.children || [])
    })
  }
  appendComponentFields(schema.components)
  const dataFields = [...fieldsByCode.values()].map((field, index) => ({
    ...field,
    field: field.field || field.fieldCode || field.fieldBinding?.fieldCode,
    fieldCode: field.fieldCode || field.field || field.fieldBinding?.fieldCode,
    sourceField: field.sourceField || field.field || field.fieldCode || field.fieldBinding?.fieldCode,
    fieldName: field.fieldName || field.label || field.fieldCode || `字段 ${index + 1}`,
    label: field.fieldName || field.label || field.fieldCode || `字段 ${index + 1}`,
    listVisible: field.listVisible !== false,
    formVisible: field.formVisible !== false,
    fieldStatus: field.fieldStatus || 'ENABLED',
    systemField: Boolean(field.systemField),
  })).filter(field => field.fieldCode && field.field)
  return [...dataFields, ...childFields, ...widgetNodes]
}

function resolvePageBlockFields(block = {}) {
  const runtimeFields = resolvePageBlockRuntimeCrudProps(block)?.fieldCatalog || []
  const formAssetId = block?.props?.formAssetId || (formAssets.value.length === 1 ? formAssets.value[0]?.id : '')
  const asset = formAssets.value.find(item => item.id === formAssetId)
  const formFields = asset ? resolveFormAssetFields(asset) : []
  return mergePageFieldCatalogs(formFields, runtimeFields)
}

/**
 * PortalPageRenderer 专用：仅解析区块关联的表单资产字段（含 widget 虚拟组件）。
 * 与 resolvePageBlockFields 不同，这里不合并运行时 CRUD 字段，
 * 因为 PortalPageRenderer 内部已自行加载运行时配置，并在 resolveBlockFields 中做合并。
 */
function resolvePortalFormFields(block = {}) {
  const formAssetId = block?.props?.formAssetId || (formAssets.value.length === 1 ? formAssets.value[0]?.id : '')
  if (!formAssetId)
    return []
  const asset = formAssets.value.find(item => item.id === formAssetId)
  return asset ? resolveFormAssetFields(asset) : []
}

/**
 * 应用页不是让业务人员再次填写接口地址的地方：
 * - 业务对象页天然绑定当前对象；
 * - 单对象应用无需选择，直接绑定唯一对象；
 * - 多对象的普通内容页不猜测数据源，仍保持安全的静态预览。
 */
function resolvePageBlockObjectRef(block = {}, pageNode = currentNode.value) {
  const blockRef = block?.props?.objectRef || block?.props?.businessObjectRef
  // 业务应用存在多个关联对象时，未显式选择数据源的页面组件默认复用主对象。
  // 这是列表设计器原本的默认行为，不能退回成“当前配置”这种不可运行占位值。
  const primaryObject = objects.value.find(item => String(item.objectRole || '').toUpperCase() === 'PRIMARY')
  const candidate = blockRef || pageNode?.objectRef || primaryObject || (objects.value.length === 1 ? objects.value[0] : null)
  if (!candidate)
    return null
  const objectId = candidate.objectId ?? candidate.id
  const objectCode = candidate.objectCode || ''
  if (objectId === undefined || objectId === null || objectId === '') {
    const matched = objects.value.find(item => objectCode && String(item.objectCode || '') === String(objectCode))
    if (!matched)
      return null
    return {
      ...matched,
      ...candidate,
      objectId: matched.objectId ?? matched.id,
      valid: candidate.valid !== false && matched.valid !== false,
    }
  }
  const matched = objects.value.find(item => String(item.objectId ?? item.id ?? '') === String(objectId))
  return {
    ...(matched || {}),
    ...candidate,
    objectId: String(objectId),
    objectCode: matched?.objectCode || objectCode || '',
    valid: Boolean(matched) && candidate.valid !== false && matched.valid !== false,
  }
}

function isValidPageBlockObjectRef(objectRef) {
  return objectRef?.valid !== false
    && Boolean(objectRef?.objectId ?? objectRef?.id ?? objectRef?.objectCode)
}

function isPageBlockDataSourceConfigured(block = {}) {
  return isValidPageBlockObjectRef(resolvePageBlockObjectRef(block))
}

/**
 * 早期应用页创建的区块会把列表设计器的无上下文占位值持久化为
 * `/ai/crud/当前配置`。加载时按每页实际绑定的业务对象完成一次迁移，
 * 让右侧属性面板与真实运行接口都展示同一个 configKey。
 */
function hydratePageCrudApiPlaceholders() {
  if (!builder.value?.pages || !builder.value?.nodes)
    return
  const nodeById = new Map(builder.value.nodes.map(node => [node.id, node]))
  let changed = false
  const pages = Object.fromEntries(Object.entries(builder.value.pages).map(([pageId, page]) => {
    const items = page?.layout?.gridLayout?.items
    if (!Array.isArray(items))
      return [pageId, page]
    const pageNode = nodeById.get(pageId)
    const nextItems = items.map((block) => {
      if (block?.blockType !== 'AiCrudPage')
        return block
      const objectRef = resolvePageBlockObjectRef(block, pageNode)
      const configKey = isValidPageBlockObjectRef(objectRef) ? objectRef.configKey || '' : ''
      if (!configKey)
        return block
      const serializedProps = JSON.stringify(block.props || {})
      const nextSerializedProps = serializedProps.replaceAll('/ai/crud/当前配置', `/ai/crud/${configKey}`)
      if (nextSerializedProps === serializedProps)
        return block
      changed = true
      return { ...block, props: JSON.parse(nextSerializedProps) }
    })
    if (!nextItems.some((item, index) => item !== items[index]))
      return [pageId, page]
    return [pageId, {
      ...page,
      layout: {
        ...page.layout,
        gridLayout: { ...page.layout.gridLayout, items: nextItems },
      },
    }]
  }))
  if (changed) {
    const nextBuilder = { ...builder.value, pages }
    if (JSON.stringify(nextBuilder) !== JSON.stringify(builder.value))
      builder.value = nextBuilder
  }
}

function resolveRuntimeObjectCacheKey(objectRef) {
  if (!isValidPageBlockObjectRef(objectRef))
    return ''
  return String(objectRef.objectId ?? objectRef.id ?? objectRef.objectCode ?? '').trim()
}

function attachDefaultRuntimeObject(block = {}) {
  if (!isDataFieldBlockType(block.blockType) || block.props?.objectRef || block.props?.businessObjectRef)
    return block
  const objectRef = resolvePageBlockObjectRef(block)
  if (!objectRef)
    return block
  return {
    ...block,
    props: {
      ...(block.props || {}),
      objectRef: {
        objectId: String(objectRef.objectId ?? objectRef.id),
        objectCode: objectRef.objectCode || '',
        objectName: objectRef.objectName || '',
      },
    },
  }
}

function resolvePageBlockRuntimeCrudProps(block = {}) {
  if (!isDataFieldBlockType(block.blockType) && block.blockType !== 'tree-panel')
    return null
  const objectRef = resolvePageBlockObjectRef(block)
  if (!isValidPageBlockObjectRef(objectRef))
    return null
  const cacheKey = resolveRuntimeObjectCacheKey(objectRef)
  if (!cacheKey)
    return null
  if (!runtimeCrudPropsByObjectId.value[cacheKey])
    preloadPageBlockCrudRuntimeProps(block)
  const runtimeProps = runtimeCrudPropsByObjectId.value[cacheKey] || null
  if (!runtimeProps)
    return null
  const treePanelProps = findTreePanelProps(pageBlocks.value) || {}
  const searchSchema = alignSearchSchemaWithLeftTree(runtimeProps.searchSchema, {
    treePanelProps,
    runtimeProps,
  })
  const alignedProps = searchSchema === runtimeProps.searchSchema
    ? runtimeProps
    : { ...runtimeProps, searchSchema }
  const blockId = String(block?.id || '').trim()
  const treeFilter = blockId ? runtimeTreeFilterByBlockId.value[blockId] : null
  const pageTreeFilter = runtimeTreeFilter.value
  const activeTreeFilter = treeFilter && Object.keys(treeFilter).length ? treeFilter : pageTreeFilter
  if (!activeTreeFilter || !Object.keys(activeTreeFilter).length)
    return alignedProps
  return {
    ...alignedProps,
    publicParams: {
      ...(alignedProps.publicParams || {}),
      ...activeTreeFilter,
    },
  }
}

function isPageBlockRuntimeCrudLoading(block = {}) {
  if (!isDataFieldBlockType(block.blockType) && block.blockType !== 'tree-panel')
    return false
  const objectRef = resolvePageBlockObjectRef(block)
  if (!isValidPageBlockObjectRef(objectRef))
    return false
  const cacheKey = resolveRuntimeObjectCacheKey(objectRef)
  if (!cacheKey)
    return false
  if (!runtimeCrudPropsByObjectId.value[cacheKey] && !runtimeCrudUnavailableObjectIds.has(cacheKey))
    preloadPageBlockCrudRuntimeProps(block)
  return runtimeCrudLoadingObjectIds.has(cacheKey)
}

function preloadCurrentPageCrudRuntimeProps() {
  // 运行态（非编辑）页面由 PortalPageRenderer 自行加载渲染配置，
  // 这里再预加载会重复请求同一接口；编辑器画布才消费 runtimeCrudPropsByObjectId
  if (!editing.value)
    return
  visitPageBlocksInTree(pageBlocks.value, preloadPageBlockCrudRuntimeProps)
}

function preloadPageBlockCrudRuntimeProps(block = {}) {
  if (!isDataFieldBlockType(block.blockType) && block.blockType !== 'tree-panel')
    return
  const objectRef = resolvePageBlockObjectRef(block)
  if (!isValidPageBlockObjectRef(objectRef))
    return
  const cacheKey = resolveRuntimeObjectCacheKey(objectRef)
  const objectId = objectRef?.objectId ?? objectRef?.id
  if (!cacheKey || objectId === undefined || objectId === null || objectId === '')
    return
  if (runtimeCrudPropsByObjectId.value[cacheKey] || runtimeCrudLoadingObjectIds.has(cacheKey) || runtimeCrudUnavailableObjectIds.has(cacheKey))
    return
  runtimeCrudLoadingObjectIds.add(cacheKey)
  void loadRuntimeCrudProps(objectRef, cacheKey)
}

/**
 * 表单设计器需要对象级字段资产、关系与动作上下文。
 * 与对象设计器同源调用 businessObjectDesigner，按对象缓存；失败仅降级为无上下文。
 */
async function ensureFormDesignerObjectContext(objectRef) {
  const cacheKey = resolveRuntimeObjectCacheKey(objectRef)
  const objectId = objectRef?.objectId ?? objectRef?.id
  if (!cacheKey || objectId === undefined || objectId === null || objectId === '')
    return
  const cached = formDesignerObjectContextByObjectId.value[cacheKey]
  if ((cached && Array.isArray(cached.fields)) || formDesignerObjectContextLoadingIds.has(cacheKey))
    return
  formDesignerObjectContextLoadingIds.add(cacheKey)
  try {
    const designer = (await businessObjectDesigner(objectId)).data || {}
    formDesignerObjectContextByObjectId.value = {
      ...formDesignerObjectContextByObjectId.value,
      [cacheKey]: {
        objectCode: designer.objectCode || objectRef.objectCode || '',
        objectName: designer.objectName || objectRef.objectName || '',
        relations: Array.isArray(designer.relations) ? designer.relations : [],
        actions: Array.isArray(designer.designerOptions?.actions) ? designer.designerOptions.actions : [],
        fields: normalizeObjectDesignerFieldCatalog(designer.modelSchema?.fields || designer.fields || []),
        formDesignerSchema: designer.formDesignerSchema || designer.designerOptions?.formDesignerSchema || null,
      },
    }
  }
  catch (error) {
    console.warn('[application-runtime] 加载表单设计器对象上下文失败', error?.message || error)
  }
  finally {
    formDesignerObjectContextLoadingIds.delete(cacheKey)
  }
}

watch(activeFormDesignerObjectRef, (objectRef) => {
  if (!objectRef)
    return
  void ensureFormDesignerObjectContext(objectRef)
  const cacheKey = resolveRuntimeObjectCacheKey(objectRef)
  const objectId = objectRef.objectId ?? objectRef.id
  if (!cacheKey || objectId === undefined || objectId === null || objectId === '')
    return
  if (runtimeCrudPropsByObjectId.value[cacheKey] || runtimeCrudLoadingObjectIds.has(cacheKey) || runtimeCrudUnavailableObjectIds.has(cacheKey))
    return
  runtimeCrudLoadingObjectIds.add(cacheKey)
  void loadRuntimeCrudProps(objectRef, cacheKey)
}, { immediate: true })

watch(
  () => activeFormDesignerContext.value?.formDesignerSchema,
  (objectSchema) => {
    if (activeFormDesignerSchema.value?.components?.length)
      return
    const normalized = presentFormDesignerSchema(objectSchema || {})
    if (!normalized.components?.length)
      return
    const signature = JSON.stringify(normalized)
    if (signature === _activeFormDesignerSchemaSignature)
      return
    _activeFormDesignerSchemaSignature = signature
    activeFormDesignerSchema.value = normalized
  },
)

function createFormFieldVisibilitySettings(currentSettings = {}, fieldRefs = [], forceVisible = false) {
  const settings = {}
  fieldRefs.filter(Boolean).forEach((field) => {
    const current = currentSettings?.[field] || {}
    settings[field] = forceVisible || !Object.prototype.hasOwnProperty.call(current, 'visible')
      ? { ...current, visible: true }
      : { ...current }
  })
  return settings
}

function updateSelectedBlockFormAsset(formAssetId) {
  if (!selectedPageBlock.value)
    return
  const asset = formAssets.value.find(item => item.id === formAssetId)
  const fieldRefs = asset ? resolveFormAssetFields(asset).map(field => field.fieldCode) : []
  updatePageBlocks(mapPageBlocksInTree(pageBlocks.value, item => item.id === selectedPageBlock.value.id
    ? {
        ...item,
        fieldRefs: fieldRefs.length ? fieldRefs : item.fieldRefs,
        props: {
          ...(item.props || {}),
          formAssetId: formAssetId || '',
          formAssetFieldsInitialized: fieldRefs.length > 0,
          fieldSettings: createFormFieldVisibilitySettings(item.props?.fieldSettings, fieldRefs, true),
          ...(['AiCrudPage', 'search-form'].includes(item.blockType) && fieldRefs.length
            ? { searchFieldRefs: fieldRefs.slice(0, 8) }
            : {}),
        },
      }
    : item))
}

function selectFormAssetFromPicker(formAssetId) {
  updateSelectedBlockFormAsset(formAssetId)
  formAssetSelectorOpen.value = false
  formAssetSelectorKeyword.value = ''
}

function updateSelectedPageBlockRuntimeObject(objectId) {
  if (!selectedPageBlock.value)
    return
  const object = objects.value.find(item => String(item.objectId ?? item.id ?? '') === String(objectId || ''))
  if (!object)
    return
  const previousObjectKey = resolveRuntimeObjectCacheKey(resolvePageBlockObjectRef(selectedPageBlock.value))
  const nextObjectKey = resolveRuntimeObjectCacheKey(object)
  const objectChanged = previousObjectKey !== nextObjectKey
  const configKey = String(object.configKey || '').trim()
  const apiPrefix = configKey ? `/ai/crud/${configKey}` : ''
  const objectApiProps = apiPrefix
    ? {
        api: apiPrefix,
        listApi: `get@${apiPrefix}/page`,
        detailApi: `get@${apiPrefix}/:id`,
        createApi: `post@${apiPrefix}`,
        updateApi: `put@${apiPrefix}`,
        deleteApi: `delete@${apiPrefix}/:id`,
        importApi: `post@${apiPrefix}/import`,
        exportApi: `post@${apiPrefix}/export`,
      }
    : {}
  const nextProps = {
    ...(selectedPageBlock.value.props || {}),
    ...objectApiProps,
    objectRef: {
      objectId: String(object.objectId ?? object.id),
      objectCode: object.objectCode || '',
      objectName: object.objectName || '',
      configKey: object.configKey || '',
    },
  }
  if (objectChanged) {
    delete nextProps.fieldSettings
    delete nextProps.searchFieldRefs
    delete nextProps.searchFieldSettings
  }
  const nextBlock = {
    ...selectedPageBlock.value,
    fieldRefs: objectChanged ? [] : selectedPageBlock.value.fieldRefs,
    props: nextProps,
  }
  // 数据源切换不影响坐标和尺寸，直接更新布局，避免触发根页面碰撞重算。
  updateCurrentGridLayout({
    ...currentGridLayout.value,
    items: mapPageBlocksInTree(pageBlocks.value, block => block.id === nextBlock.id ? nextBlock : block),
  })
  runtimeCrudUnavailableObjectIds.delete(resolveRuntimeObjectCacheKey(object))
  preloadPageBlockCrudRuntimeProps(nextBlock)
}

function createFormAssetForSelectedBlock() {
  if (!currentNode.value)
    return
  const blockTitle = selectedPageBlock.value?.label || resolveListPageBlockMeta(selectedPageBlock.value?.blockType)?.title || '页面'
  const name = `${currentNode.value.title}${blockTitle === 'AiForm' ? '录入表单' : '数据表单'}`
  const result = createInAppFormAsset(builder.value, {
    name,
    formDesignerSchema: createDefaultFormDesignerSchema({
      objectCode: application.value?.applicationCode || 'application',
      objectName: name,
      formName: name,
    }),
  })
  builder.value = result.schema
  updateSelectedBlockFormAsset(result.formAssetId)
  activeFormAssetId.value = result.formAssetId
  formDesignerMode.value = true
}

function createStandaloneFormAsset() {
  const name = `${application.value?.applicationName || '应用'}表单`
  const result = createInAppFormAsset(builder.value, {
    name,
    formDesignerSchema: createDefaultFormDesignerSchema({
      objectCode: application.value?.applicationCode || 'application',
      objectName: name,
      formName: name,
    }),
  })
  builder.value = result.schema
  bindSingleFormToCompatibleBlocks()
  openFormAssetDesigner(result.formAssetId)
}

function openFormAssetDesigner(formAssetId) {
  activeFormAssetId.value = formAssetId
  activePageShapeDesign.value = resolvePageShapeDesignContext(formAssetId)
  formDesignerMode.value = true
}

function resolvePageShapeDesignContext(formAssetId) {
  const supportedTypes = new Set(['form', 'list', 'list-form', 'tree-list', 'tree-table'])
  for (const node of builder.value?.nodes || []) {
    if (node?.type !== 'page')
      continue
    const page = builder.value?.pages?.[node.id]
    const block = findPageBlockByFormAssetId(page?.layout?.gridLayout?.items || [], formAssetId)
    if (!block)
      continue
    const layoutType = page?.layout?.gridLayout?.layoutType
    const pageType = supportedTypes.has(node.pageTemplate)
      ? node.pageTemplate
      : supportedTypes.has(node.pageShape)
        ? node.pageShape
        : layoutType === 'tree-crud'
          ? 'tree-table'
          : supportedTypes.has(layoutType)
            ? layoutType
            : 'list-form'
    const objectRef = block.props?.objectRef || node.objectRef || {}
    return {
      pageId: node.id,
      pageType,
      formAssetId,
      objectId: objectRef.objectId || null,
      objectCode: objectRef.objectCode || '',
      objectName: objectRef.objectName || node.title || '',
      runtimeDatasourceId: objectRef.runtimeDatasourceId || null,
      createMode: objectRef.createMode || '',
      importDatasourceId: objectRef.importDatasourceId || objectRef.runtimeDatasourceId || null,
      importTableName: objectRef.importTableName || '',
    }
  }
  return null
}

function findPageBlockByFormAssetId(blocks, formAssetId) {
  for (const block of blocks || []) {
    if (String(block?.props?.formAssetId || '') === String(formAssetId || ''))
      return block
    const nested = [
      ...(block?.children || []),
      ...(block?.props?.tabs || []).flatMap(tab => tab?.children || []),
      ...(block?.props?.cells || []).flatMap(cell => cell?.children || []),
    ]
    const matched = findPageBlockByFormAssetId(nested, formAssetId)
    if (matched)
      return matched
  }
  return null
}

function syncActivePageShapeObject() {
  const context = activePageShapeDesign.value
  if (!context)
    return
  const patchObjectRef = objectRef => ({
    ...(objectRef || {}),
    objectId: context.objectId || objectRef?.objectId || null,
    objectCode: context.objectCode,
    objectName: context.objectName,
    runtimeDatasourceId: context.runtimeDatasourceId ?? objectRef?.runtimeDatasourceId ?? null,
    createMode: context.createMode || objectRef?.createMode || '',
    importDatasourceId: context.importDatasourceId ?? objectRef?.importDatasourceId ?? context.runtimeDatasourceId ?? null,
    importTableName: context.importTableName || objectRef?.importTableName || '',
    valid: true,
  })
  builder.value = {
    ...builder.value,
    nodes: builder.value.nodes.map(node => node.id === context.pageId
      ? { ...node, objectRef: patchObjectRef(node.objectRef) }
      : node),
    pages: Object.fromEntries(Object.entries(builder.value.pages || {}).map(([pageId, page]) => [
      pageId,
      pageId === context.pageId
        ? {
            ...page,
            layout: {
              ...page.layout,
              gridLayout: {
                ...page.layout?.gridLayout,
                items: mapPageBlocksInTree(page.layout?.gridLayout?.items || [], block =>
                  String(block?.props?.formAssetId || '') === String(context.formAssetId)
                    ? {
                        ...block,
                        props: {
                          ...(block.props || {}),
                          objectRef: patchObjectRef(block.props?.objectRef),
                        },
                      }
                    : block),
              },
            },
          }
        : page,
    ])),
  }
}

function openPageBlockConfiguration(block = {}) {
  if (!editing.value || !block?.id)
    return
  selectedPageBlockId.value = block.id
  inspectorTab.value = 'properties'
  configPanelVisible.value = true
}

function handlePageBlockDataSourceRequest(blockId) {
  const block = findPageBlockInTree(pageBlocks.value, blockId)
  if (!editing.value || !block)
    return
  selectedPageBlockId.value = block.id
  configPanelVisible.value = true
  inspectorTab.value = 'data'
}

function editSelectedBlockFormAsset() {
  const formAssetId = selectedPageBlockFormAssetId.value
  if (!formAssetId)
    return
  openFormAssetDesigner(formAssetId)
}

function openSelectedBlockFormDesigner() {
  if (selectedPageBlockFormAssetId.value) {
    editSelectedBlockFormAsset()
    return
  }
  createFormAssetForSelectedBlock()
}

function updateActiveFormDesignerSchema(schema) {
  if (!activeFormAsset.value)
    return
  const normalizedSchema = normalizeFormDesignerSchema(schema)
  const formAssetId = activeFormAsset.value.id
  let nextBuilder = updateInAppFormAsset(builder.value, formAssetId, {
    name: normalizedSchema.formName || activeFormAsset.value.name,
    formDesignerSchema: normalizedSchema,
  })
  const nextAsset = nextBuilder.formAssets.find(asset => asset.id === formAssetId)
  const fieldRefs = resolveFormAssetFields(nextAsset).map(field => field.fieldCode)
  nextBuilder = {
    ...nextBuilder,
    pages: Object.fromEntries(Object.entries(nextBuilder.pages || {}).map(([pageId, page]) => {
      const items = page?.layout?.gridLayout?.items
      if (!Array.isArray(items))
        return [pageId, page]
      return [pageId, {
        ...page,
        layout: {
          ...page.layout,
          gridLayout: {
            ...page.layout.gridLayout,
            items: items.map((item) => {
              if (item?.props?.formAssetId !== formAssetId)
                return item
              const synced = syncFormBoundFieldRefs({
                formFieldCodes: fieldRefs,
                searchFieldRefs: item.props?.searchFieldRefs,
              })
              return {
                ...item,
                fieldRefs: synced.fieldRefs,
                props: {
                  ...(item.props || {}),
                  formAssetFieldsInitialized: synced.fieldRefs.length > 0,
                  fieldSettings: createFormFieldVisibilitySettings(item.props?.fieldSettings, synced.fieldRefs),
                  ...(['AiCrudPage', 'search-form'].includes(item.blockType)
                    ? { searchFieldRefs: synced.searchFieldRefs }
                    : {}),
                },
              }
            }),
          },
        },
      }]
    })),
  }
  // 设计器 props 回写/分区派生若未产生实质差异，不要弄脏草稿签名
  if (JSON.stringify(nextBuilder) === JSON.stringify(builder.value || {}))
    return
  builder.value = nextBuilder
}

function returnToPageDesigner() {
  // 以当前选中页为准。设计上下文里的 pageId 可能是另一张也绑了表单的页面，
  // 用它去挂表单会把刚设计的内容换成那张页上的空表单。
  const pageId = selectedNodeId.value || activePageShapeDesign.value?.pageId
  formDesignerMode.value = false
  // 如果用户从页面管理视图进入表单设计器，返回时退出编辑模式，避免出现设计工作台
  if (formDesignerFromPageManagement.value) {
    exitToPageManagement()
    return
  }
  // 返回后仍停在表单/列表设计页，必须重新挂上当前页面的表单，否则画布是空的
  if (pageId) {
    const entryTab = resolveEntryDesignTab(pageId)
    activePageDesignTab.value = entryTab === 'page' ? 'form' : entryTab
    syncActiveFormAssetForPage(pageId)
    const nextQuery = {
      ...route.query,
      pageId,
      edit: '1',
      designResource: `page-custom:${pageId}`,
      designTab: activePageDesignTab.value === resolveEntryDesignTab(pageId) ? undefined : activePageDesignTab.value,
    }
    // 对象页从全屏表单设计返回时，清掉误带的 designTab=page，否则表单/列表 Tab 会被藏掉
    if (nextQuery.designTab === 'page' && resolvePageShapeKey(pageId) !== 'custom')
      delete nextQuery.designTab
    router.replace({ query: nextQuery })
  }
  else {
    activeFormAssetId.value = ''
    activePageShapeDesign.value = null
  }
}

function updatePageBlocks(items, options = {}) {
  const nextItems = options.resolveCollisions
    ? resolveRootPageBlockCollisions(items, options.changedBlockId)
    : items
  updateCurrentGridLayout({ ...currentGridLayout.value, items: nextItems })
}

/** 卡片 / 盒子：整块主体就是投放区（对齐栅格 cell 命中） */
function isSimpleBodyContainer(block = {}) {
  return ['card', 'box-layout'].includes(block?.blockType)
}

function resolvePageBlockShellStyle(block) {
  return computePageBlockShellStyle(block, pageBlocks.value, {
    pageId: String(route.query.pageId || selectedNodeId.value || currentNode.value?.id || ''),
    pagePadding: pageCanvasPadding.value,
    pageFlowCoordSpace: currentGridLayout.value?.pageFlowCoordSpace,
  })
}

function handlePageFlowBlankClick(event) {
  if (!editing.value)
    return
  if (event.target?.closest?.('[data-page-block-id]'))
    return
  selectedPageBlockId.value = ''
  inspectorTab.value = 'properties'
  configPanelVisible.value = true
}

function selectPageBlock(blockId) {
  if (!editing.value)
    return
  selectedPageBlockId.value = blockId
  inspectorTab.value = 'properties'
}

function handleNestedPageBlockSelect(blockId) {
  const block = findPageBlockInTree(pageBlocks.value, blockId)
  if (!block)
    return
  openPageBlockConfiguration(block)
}

function handleInlineTextUpdate({ blockId, patch }) {
  if (!blockId || !patch)
    return
  updatePageBlocks(pageBlocks.value.map(item => item.id === blockId
    ? { ...item, props: { ...(item.props || {}), ...patch } }
    : item))
}

function handleRuntimeTreeSelect(payload = {}) {
  const blockId = String(payload.blockId || '').trim()
  if (!blockId)
    return
  const nextActiveKeys = { ...runtimeTreeActiveKeyByBlockId.value }
  const nextFilters = { ...runtimeTreeFilterByBlockId.value }
  nextActiveKeys[blockId] = payload.key || '__all__'
  if (!payload.filterField || payload.clear || payload.value === undefined
    || payload.value === null || payload.value === '') {
    runtimeTreeFilter.value = {}
    delete nextFilters[blockId]
  }
  else {
    const nextFilter = buildLeftTreeFilterParams({
      filterField: payload.filterField,
      value: payload.value,
      includeChildren: payload.includeChildren !== false,
      expandedValues: payload.expandedValues,
    })
    runtimeTreeFilter.value = nextFilter
    nextFilters[blockId] = nextFilter
  }
  runtimeTreeActiveKeyByBlockId.value = nextActiveKeys
  runtimeTreeFilterByBlockId.value = nextFilters
}

function resolvePagePreviewBlock(block) {
  return {
    ...block,
    props: {
      ...(block.props || {}),
      style: {
        ...(block.props?.style || {}),
        width: '100%',
        height: '100%',
        minHeight: '',
        maxHeight: '',
        margin: 0,
      },
    },
  }
}

function handleComponentCatalogDragStart(event, component) {
  if (!editing.value || !component?.blockType)
    return
  catalogDragBlockType.value = component.blockType
  suppressCatalogClick.value = true
  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.setData('application/x-forge-app-page-block', component.blockType)
  // 兼容列表设计器的原生拖拽协议，确保从同一套左侧组件面板拖入运行时 Tabs 时可命中。
  event.dataTransfer.setData('application/x-list-block', component.blockType)
  // 使用表单设计器相同的布局拖拽协议，避免 Popover/浏览器清理自定义页面协议。
  event.dataTransfer.setData('application/x-forge-form-layout', JSON.stringify({
    componentKey: component.blockType,
    label: component.title || component.label || component.blockType,
  }))
}

function startCatalogPointerDrag(event, component) {
  if (!editing.value || !component?.blockType)
    return
  catalogDragBlockType.value = component.blockType
  suppressCatalogClick.value = false
  catalogPointerDragCtx = {
    blockType: component.blockType,
    startX: event.clientX,
    startY: event.clientY,
    moved: false,
    pointerId: event.pointerId,
  }
  try {
    event.currentTarget?.setPointerCapture?.(event.pointerId)
  }
  catch {
    // ignore capture failure
  }
  window.addEventListener('pointermove', handleCatalogPointerMove, { passive: false })
  window.addEventListener('pointerup', finishCatalogPointerDrag, { once: true })
  window.addEventListener('pointercancel', finishCatalogPointerDrag, { once: true })
}

function normalizeContainerDropTarget(target = null) {
  if (!target)
    return null
  const containerId = String(target.containerId || target.blockId || '')
  const containerType = String(target.containerType || target.blockType || '')
  if (!containerId)
    return null
  return { containerId, blockId: containerId, containerType }
}

function handleCatalogPointerMove(event) {
  if (!catalogPointerDragCtx)
    return
  const ctx = catalogPointerDragCtx
  if (!ctx.moved && Math.hypot(event.clientX - ctx.startX, event.clientY - ctx.startY) < 6)
    return
  ctx.moved = true
  suppressCatalogClick.value = true
  event.preventDefault()
  // 拖动中不关闭组件浮层（关闭会触发 pointercancel，导致栅格命中失败）
  // 仅用样式屏蔽浮层命中，松手后再收起
  document.body.classList.add('is-catalog-pointer-dragging')
  activePageFlowTabTarget.value = resolvePageFlowTabTargetFromPoint(event)
  const gridTarget = resolvePageFlowGridCellTargetFromPoint(event)
  activePageFlowGridTarget.value = gridTarget
    ? { containerId: gridTarget.blockId, cellKey: gridTarget.cellKey, cellIndex: gridTarget.cellIndex }
    : null
  activePageFlowContainerTarget.value = gridTarget
    ? null
    : normalizeContainerDropTarget(resolvePageFlowContainerTargetFromPoint(event))
  syncCatalogDropActiveClass(
    activePageFlowGridTarget.value?.containerId
    || activePageFlowContainerTarget.value?.containerId
    || activePageFlowTabTarget.value?.blockId
    || '',
  )
  // 组件库拖入时也显示十字标线，便于对准栅格
  const flow = document.querySelector('.application-page-flow')
  const flowRect = flow?.getBoundingClientRect?.()
  if (flowRect) {
    pageAlignGuides.value = {
      visible: true,
      cross: {
        x: Math.round(event.clientX - flowRect.left + (flow.scrollLeft || 0)),
        y: Math.round(event.clientY - flowRect.top + (flow.scrollTop || 0)),
      },
      x: [],
      y: [],
    }
  }
}

function syncCatalogDropActiveClass(containerId = '') {
  document.querySelectorAll('.application-page-block.is-catalog-drop-active').forEach((el) => {
    el.classList.remove('is-catalog-drop-active')
  })
  if (!containerId)
    return
  document.querySelector(`[data-page-block-id="${containerId}"]`)?.classList.add('is-catalog-drop-active')
}

function finishCatalogPointerDrag(event) {
  const ctx = catalogPointerDragCtx
  catalogPointerDragCtx = null
  window.removeEventListener('pointermove', handleCatalogPointerMove)
  window.removeEventListener('pointercancel', finishCatalogPointerDrag)
  // 先在关闭浮层/去掉拖拽 class 之前锁定投放目标，避免布局抖动导致松手重新命中失败
  const blockType = ctx?.blockType || ''
  const tabTarget = ctx?.moved
    ? (activePageFlowTabTarget.value || resolvePageFlowTabTargetFromPoint(event))
    : null
  const gridTarget = ctx?.moved
    ? (activePageFlowGridTarget.value
      ? {
          blockId: activePageFlowGridTarget.value.containerId,
          cellKey: activePageFlowGridTarget.value.cellKey,
          cellIndex: activePageFlowGridTarget.value.cellIndex,
        }
      : resolvePageFlowGridCellTargetFromPoint(event))
    : null
  const containerTarget = ctx?.moved
    ? normalizeContainerDropTarget(
      activePageFlowContainerTarget.value || resolvePageFlowContainerTargetFromPoint(event),
    )
    : null
  document.body.classList.remove('is-catalog-pointer-dragging')
  syncCatalogDropActiveClass('')
  componentPopoverVisible.value = false
  clearPageAlignGuides()
  activePageFlowTabTarget.value = null
  activePageFlowGridTarget.value = null
  activePageFlowContainerTarget.value = null
  if (!ctx?.moved) {
    catalogDragBlockType.value = ''
    return
  }
  if (tabTarget)
    appendPageBlockToTab(blockType, tabTarget.blockId, tabTarget.tabKey)
  else if (gridTarget) {
    const host = findPageBlockInTree(pageBlocks.value, gridTarget.blockId)
    const beforeCount = JSON.stringify(pageBlocks.value)
    appendPageBlockToGridCell(blockType, gridTarget.blockId, gridTarget.cellKey, gridTarget.cellIndex)
    const inserted = beforeCount !== JSON.stringify(pageBlocks.value)
    if (inserted) {
      if (host?.blockType === 'card')
        message.success('组件已放入卡片')
      else if (host?.blockType === 'box-layout')
        message.success('组件已放入盒子')
      else
        message.success('组件已放入栅格')
    }
  }
  else if (containerTarget) {
    if (appendPageBlockToContainer(blockType, containerTarget.containerId))
      message.success(containerTarget.containerType === 'card' ? '组件已放入卡片' : '组件已放入容器')
  }
  else
    appendPageBlock(blockType)
  catalogDragBlockType.value = ''
}

function handleComponentCatalogDragEnd() {
  // drop 事件在部分浏览器中晚于 dragend 到达，延迟清理拖拽类型。
  window.setTimeout(() => {
    if (catalogPointerDragCtx)
      return
    catalogDragBlockType.value = ''
    suppressCatalogClick.value = false
  }, 250)
}

function handleComponentCatalogClick(component, event) {
  if (suppressCatalogClick.value) {
    event.preventDefault()
    event.stopPropagation()
    suppressCatalogClick.value = false
    return
  }
  insertComponent(component)
}

function isPageCatalogDrag(event) {
  return Boolean(catalogDragBlockType.value)
    || Array.from(event.dataTransfer?.types || []).some(type => [
      'application/x-forge-app-page-block',
      'application/x-list-block',
      'application/x-forge-form-layout',
    ].includes(type))
}

function resolvePageFlowTabTarget(event) {
  const pointTarget = Number.isFinite(event.clientX) && Number.isFinite(event.clientY)
    ? document.elementFromPoint(event.clientX, event.clientY)
    : null
  const target = pointTarget?.closest?.('[data-grid-container-id][data-grid-tab-key]')
    || event.target?.closest?.('[data-grid-container-id][data-grid-tab-key]')
  if (!target || !event.currentTarget?.contains?.(target))
    return null
  const rect = target.getBoundingClientRect?.()
  const style = window.getComputedStyle?.(target)
  if (!rect || rect.width <= 0 || rect.height <= 0 || style?.display === 'none' || style?.visibility === 'hidden')
    return null
  const blockId = String(target.dataset.gridContainerId || '')
  const tabKey = String(target.dataset.gridTabKey || '')
  return blockId && tabKey ? { blockId, tabKey } : null
}

function resolvePageFlowTabTargetFromPoint(event) {
  if (!Number.isFinite(event.clientX) || !Number.isFinite(event.clientY))
    return null
  const pointTarget = document.elementFromPoint(event.clientX, event.clientY)
  const target = pointTarget?.closest?.('[data-grid-container-id][data-grid-tab-key]')
  if (!target)
    return null
  const rect = target.getBoundingClientRect?.()
  if (!rect || rect.width <= 0 || rect.height <= 0)
    return null
  const blockId = String(target.dataset.gridContainerId || '')
  const tabKey = String(target.dataset.gridTabKey || '')
  return blockId && tabKey ? { blockId, tabKey } : null
}

function resolvePageFlowGridCellTarget(event) {
  return resolvePageFlowGridCellTargetFromPoint(event)
    || resolvePageFlowGridCellTargetFromElement(event.target)
}

function resolvePageFlowGridCellTargetFromPoint(event) {
  if (!Number.isFinite(event.clientX) || !Number.isFinite(event.clientY))
    return null
  const x = event.clientX
  const y = event.clientY
  // 卡片/盒子优先按整块几何命中，避免中间区域被子节点/遮罩挡住时落到画布根级
  const hostHit = resolvePageFlowGridCellTargetByPageBlock(x, y)
  if (hostHit && (hostHit.cellKey === '__body__' || findPageBlockInTree(pageBlocks.value, hostHit.blockId)?.blockType === 'grid-layout'))
    return hostHit
  const stack = typeof document.elementsFromPoint === 'function'
    ? document.elementsFromPoint(x, y)
    : [document.elementFromPoint(x, y)].filter(Boolean)
  for (const el of stack) {
    const hit = resolvePageFlowGridCellTargetFromElement(el)
    if (hit)
      return hit
  }
  return resolvePageFlowGridCellTargetByRect(x, y) || hostHit
}

function resolvePageFlowGridCellTargetByRect(x, y) {
  const root = document.querySelector('.application-page-flow')
  if (!root)
    return null
  const cells = root.querySelectorAll('[data-grid-container-id][data-grid-cell-key]')
  let best = null
  let bestArea = Number.POSITIVE_INFINITY
  cells.forEach((el) => {
    const rect = el.getBoundingClientRect?.()
    if (!rect || rect.width <= 0 || rect.height <= 0)
      return
    if (x < rect.left || x > rect.right || y < rect.top || y > rect.bottom)
      return
    const area = rect.width * rect.height
    if (area >= bestArea)
      return
    bestArea = area
    best = el
  })
  return best ? resolvePageFlowGridCellTargetFromElement(best) : null
}

/** 指针落在栅格区块任意位置时，按最近格子命中（对齐表单设计器「拖进容器」手感） */
function resolvePageFlowGridCellTargetByPageBlock(x, y) {
  const root = document.querySelector('.application-page-flow')
  if (!root)
    return null
  const candidates = []
  root.querySelectorAll(
    '[data-page-block-id][data-page-block-type="grid-layout"], [data-page-block-id][data-page-block-type="card"], [data-page-block-id][data-page-block-type="box-layout"]',
  ).forEach((el) => {
    const rect = el.getBoundingClientRect?.()
    if (!rect || rect.width <= 0 || rect.height <= 0)
      return
    if (x < rect.left || x > rect.right || y < rect.top || y > rect.bottom)
      return
    candidates.push({ el, area: rect.width * rect.height })
  })
  candidates.sort((a, b) => a.area - b.area)
  for (const { el } of candidates) {
    const blockId = String(el.dataset.pageBlockId || '')
    const blockType = String(el.dataset.pageBlockType || '')
    if (!blockId)
      continue
    if (blockType === 'card' || blockType === 'box-layout')
      return { blockId, cellKey: '__body__', cellIndex: 0 }
    const cellNodes = [...el.querySelectorAll('[data-grid-cell-key]')]
    if (!cellNodes.length) {
      const block = findPageBlockInTree(pageBlocks.value, blockId)
      const first = Array.isArray(block?.props?.cells) ? block.props.cells[0] : null
      if (first?.key)
        return { blockId, cellKey: String(first.key), cellIndex: 0 }
      continue
    }
    let best = null
    let bestDist = Number.POSITIVE_INFINITY
    cellNodes.forEach((cellEl, index) => {
      const rect = cellEl.getBoundingClientRect?.()
      if (!rect || rect.width <= 0 || rect.height <= 0)
        return
      const cx = rect.left + rect.width / 2
      const cy = rect.top + rect.height / 2
      const dist = Math.hypot(x - cx, y - cy)
      const inside = x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom
      const score = inside ? dist / 4 : dist
      if (score >= bestDist)
        return
      bestDist = score
      best = { el: cellEl, index }
    })
    if (best) {
      const hit = resolvePageFlowGridCellTargetFromElement(best.el)
      if (hit)
        return hit
      return {
        blockId,
        cellKey: String(best.el.dataset.gridCellKey || ''),
        cellIndex: best.index,
      }
    }
  }
  return null
}

function resolvePageFlowGridCellTargetFromElement(element) {
  const simpleHost = element?.closest?.('[data-page-block-type="card"][data-page-block-id], [data-page-block-type="box-layout"][data-page-block-id]')
  if (simpleHost?.dataset?.pageBlockId) {
    return {
      blockId: String(simpleHost.dataset.pageBlockId),
      cellKey: '__body__',
      cellIndex: 0,
    }
  }
  const target = element?.closest?.('[data-grid-container-id][data-grid-cell-key]')
    || element?.closest?.('[data-grid-cell-key]')
  if (!target)
    return null
  const rect = target.getBoundingClientRect?.()
  if (!rect || rect.width <= 0 || rect.height <= 0)
    return null
  const style = window.getComputedStyle?.(target)
  if (style?.display === 'none' || style?.visibility === 'hidden')
    return null
  const blockId = String(target.dataset.gridContainerId || target.closest?.('[data-page-block-id]')?.dataset?.pageBlockId || '')
  const cellKey = String(target.dataset.gridCellKey || target.dataset.cellKey || '')
  const cellIndex = Number(target.dataset.cellIndex)
  return blockId && cellKey
    ? { blockId, cellKey, cellIndex: Number.isFinite(cellIndex) ? cellIndex : -1 }
    : null
}

/** 卡片 / 盒子：命中容器中部即可放入（对齐栅格拖入） */
function resolvePageFlowContainerTargetFromPoint(event) {
  if (!Number.isFinite(event.clientX) || !Number.isFinite(event.clientY))
    return null
  const x = event.clientX
  const y = event.clientY
  const stack = typeof document.elementsFromPoint === 'function'
    ? document.elementsFromPoint(x, y)
    : [document.elementFromPoint(x, y)].filter(Boolean)
  for (const el of stack) {
    const hit = resolvePageFlowContainerTargetFromElement(el)
    if (hit)
      return hit
  }
  return resolvePageFlowContainerTargetByRect(x, y)
    || resolvePageFlowContainerTargetByPageBlock(x, y)
}

function resolvePageFlowContainerTargetFromElement(element) {
  const target = element?.closest?.('[data-page-container-id][data-page-container-type]')
    || element?.closest?.('[data-page-block-type="card"], [data-page-block-type="box-layout"]')
  if (!target)
    return null
  const containerType = String(target.dataset.pageContainerType || target.dataset.pageBlockType || '')
  if (!['card', 'box-layout'].includes(containerType))
    return null
  const rect = target.getBoundingClientRect?.()
  if (!rect || rect.width <= 0 || rect.height <= 0)
    return null
  const blockId = String(
    target.dataset.pageContainerId
    || target.dataset.pageBlockId
    || target.closest?.('[data-page-block-id]')?.dataset?.pageBlockId
    || '',
  )
  return blockId ? { blockId, containerType } : null
}

function resolvePageFlowContainerTargetByRect(x, y) {
  const root = document.querySelector('.application-page-flow')
  if (!root)
    return null
  const nodes = root.querySelectorAll(
    '[data-page-container-id][data-page-container-type], [data-page-block-type="card"], [data-page-block-type="box-layout"]',
  )
  let best = null
  let bestArea = Number.POSITIVE_INFINITY
  nodes.forEach((el) => {
    const containerType = String(el.dataset.pageContainerType || el.dataset.pageBlockType || '')
    if (!['card', 'box-layout'].includes(containerType))
      return
    const rect = el.getBoundingClientRect?.()
    if (!rect || rect.width <= 0 || rect.height <= 0)
      return
    if (x < rect.left || x > rect.right || y < rect.top || y > rect.bottom)
      return
    const area = rect.width * rect.height
    if (area >= bestArea)
      return
    bestArea = area
    best = el
  })
  return best ? resolvePageFlowContainerTargetFromElement(best) : null
}

/** 指针落在卡片/盒子整块上时也命中（对齐栅格 byPageBlock） */
function resolvePageFlowContainerTargetByPageBlock(x, y) {
  const root = document.querySelector('.application-page-flow')
  if (!root)
    return null
  const candidates = []
  root.querySelectorAll('[data-page-block-id][data-page-block-type="card"], [data-page-block-id][data-page-block-type="box-layout"]').forEach((el) => {
    const rect = el.getBoundingClientRect?.()
    if (!rect || rect.width <= 0 || rect.height <= 0)
      return
    if (x < rect.left || x > rect.right || y < rect.top || y > rect.bottom)
      return
    candidates.push({ el, area: rect.width * rect.height })
  })
  candidates.sort((a, b) => a.area - b.area)
  for (const { el } of candidates) {
    const hit = resolvePageFlowContainerTargetFromElement(el)
    if (hit)
      return hit
  }
  return null
}

function handlePageFlowDragOver(event) {
  if (!isPageCatalogDrag(event))
    return
  event.preventDefault()
  activePageFlowTabTarget.value = resolvePageFlowTabTargetFromPoint(event) || resolvePageFlowTabTarget(event)
  const gridTarget = resolvePageFlowGridCellTargetFromPoint(event) || resolvePageFlowGridCellTarget(event)
  activePageFlowGridTarget.value = gridTarget
    ? { containerId: gridTarget.blockId, cellKey: gridTarget.cellKey, cellIndex: gridTarget.cellIndex }
    : null
  activePageFlowContainerTarget.value = gridTarget
    ? null
    : normalizeContainerDropTarget(resolvePageFlowContainerTargetFromPoint(event))
  event.dataTransfer.dropEffect = 'copy'
}

function handlePageFlowDrop(event) {
  const blockType = event.dataTransfer?.getData('application/x-forge-app-page-block')
    || event.dataTransfer?.getData('application/x-list-block')
    || resolveFormLayoutBlockType(event)
    || catalogDragBlockType.value
  if (!blockType)
    return
  const tabTarget = activePageFlowTabTarget.value || resolvePageFlowTabTargetFromPoint(event) || resolvePageFlowTabTarget(event)
  if (tabTarget) {
    event.preventDefault()
    event.stopPropagation()
    appendPageBlockToTab(blockType, tabTarget.blockId, tabTarget.tabKey)
    catalogDragBlockType.value = ''
    activePageFlowTabTarget.value = null
    activePageFlowGridTarget.value = null
    activePageFlowContainerTarget.value = null
    return
  }
  const gridTarget = activePageFlowGridTarget.value
    ? {
        blockId: activePageFlowGridTarget.value.containerId,
        cellKey: activePageFlowGridTarget.value.cellKey,
        cellIndex: activePageFlowGridTarget.value.cellIndex,
      }
    : (resolvePageFlowGridCellTargetFromPoint(event) || resolvePageFlowGridCellTarget(event))
  if (gridTarget) {
    event.preventDefault()
    event.stopPropagation()
    appendPageBlockToGridCell(blockType, gridTarget.blockId, gridTarget.cellKey, gridTarget.cellIndex)
    catalogDragBlockType.value = ''
    activePageFlowTabTarget.value = null
    activePageFlowGridTarget.value = null
    activePageFlowContainerTarget.value = null
    return
  }
  const containerTarget = normalizeContainerDropTarget(
    activePageFlowContainerTarget.value || resolvePageFlowContainerTargetFromPoint(event),
  )
  if (containerTarget) {
    event.preventDefault()
    event.stopPropagation()
    appendPageBlockToContainer(blockType, containerTarget.containerId)
    catalogDragBlockType.value = ''
    activePageFlowTabTarget.value = null
    activePageFlowGridTarget.value = null
    activePageFlowContainerTarget.value = null
    message.success(containerTarget.containerType === 'card' ? '组件已放入卡片' : '组件已放入容器')
    return
  }
  event.preventDefault()
  event.stopPropagation()
  appendPageBlock(blockType)
  catalogDragBlockType.value = ''
  activePageFlowTabTarget.value = null
  activePageFlowGridTarget.value = null
  activePageFlowContainerTarget.value = null
}

function resolveFormLayoutBlockType(event) {
  const raw = event.dataTransfer?.getData('application/x-forge-form-layout')
  if (!raw)
    return ''
  try {
    return String(JSON.parse(raw)?.componentKey || '').trim()
  }
  catch {
    return ''
  }
}

function handlePageFlowTabDrop(payload = {}) {
  if (!editing.value)
    return
  const blockType = String(payload.blockType || '').trim()
  const blockId = String(payload.blockId || '').trim()
  const tabKey = String(payload.tabKey || '').trim()
  if (!blockType || !blockId || !tabKey)
    return
  appendPageBlockToTab(blockType, blockId, tabKey)
  catalogDragBlockType.value = ''
}

function handlePageFlowGridCellDrop(payload = {}) {
  if (!editing.value)
    return
  const blockType = String(payload.blockType || '').trim()
  const blockId = String(payload.blockId || '').trim()
  const cellKey = String(payload.cellKey || '').trim()
  if (!blockType || !blockId)
    return
  appendPageBlockToGridCell(blockType, blockId, cellKey, payload.cellIndex)
  catalogDragBlockType.value = ''
}

function handlePageFlowContainerInsert(payload = {}) {
  if (!editing.value)
    return
  const blockType = String(payload.blockType || '').trim()
  const blockId = String(payload.blockId || '').trim()
  const containerType = String(payload.containerType || '').trim()
  if (!blockType || !blockId)
    return
  if (containerType === 'grid-layout') {
    appendPageBlockToGridCell(blockType, blockId, payload.cellKey, payload.cellIndex)
    return
  }
  if (containerType === 'tabs') {
    appendPageBlockToTab(blockType, blockId, payload.tabKey)
    return
  }
  appendPageBlockToContainer(blockType, blockId)
}

function handlePageFlowContainerClear(payload = {}) {
  if (!editing.value)
    return
  const blockId = String(payload.blockId || '').trim()
  const container = findPageBlockInTree(pageBlocks.value, blockId)
  if (!container)
    return
  if (container.blockType === 'grid-layout') {
    const cellKey = String(payload.cellKey || '')
    const cells = (Array.isArray(container.props?.cells) ? container.props.cells : []).map(cell => (
      !cellKey || cell.key === cellKey
        ? { ...cell, children: [] }
        : cell
    ))
    updatePageBlocks(mapPageBlocksInTree(pageBlocks.value, item => item.id === blockId
      ? { ...item, props: { ...(item.props || {}), cells } }
      : item))
    selectedPageBlockId.value = blockId
    return
  }
  if (container.blockType === 'tabs') {
    const tabKey = String(payload.tabKey || '')
    const tabs = (Array.isArray(container.props?.tabs) ? container.props.tabs : []).map(tab => (
      !tabKey || tab.key === tabKey
        ? { ...tab, children: [] }
        : tab
    ))
    updatePageBlocks(mapPageBlocksInTree(pageBlocks.value, item => item.id === blockId
      ? { ...item, props: { ...(item.props || {}), tabs } }
      : item))
    selectedPageBlockId.value = blockId
    return
  }
  updatePageBlocks(mapPageBlocksInTree(pageBlocks.value, item => item.id === blockId
    ? { ...item, children: [] }
    : item))
  selectedPageBlockId.value = blockId
}

function appendPageBlockToContainer(blockType, containerId) {
  const meta = resolveListPageBlockMeta(blockType)
  const container = findPageBlockInTree(pageBlocks.value, containerId)
  if (!meta || !container || !['card', 'box-layout'].includes(container.blockType))
    return false
  // 容器内禁止再嵌套同级大容器，避免拖进后看不见
  if (['grid-layout', 'tabs', 'box-layout', 'card'].includes(blockType)) {
    message.warning('卡片/盒子内请放入普通组件，布局容器请放在画布根级')
    return false
  }
  let block = createGridBlock(blockType, applicationGridModelSchema.value, {
    gridX: 0,
    gridY: (container.children || []).length * 2,
  })
  if (!block)
    return false
  block = attachDefaultRuntimeObject(block)
  block = attachSingleFormAsset(block)
  block = normalizePageBlockForContainer(block)
  updatePageBlocks(mapPageBlocksInTree(pageBlocks.value, item => item.id === containerId
    ? { ...item, children: [...(item.children || []), block] }
    : item))
  selectedPageBlockId.value = block.id
  preloadPageBlockCrudRuntimeProps(block)
  return true
}

function handleNestedPageBlockMenuSelect(payload = {}) {
  const blockId = String(payload.block?.id || '').trim()
  if (!blockId)
    return
  if (payload.key === 'delete') {
    updatePageBlocks(removePageBlockFromTree(pageBlocks.value, blockId))
    if (selectedPageBlockId.value === blockId)
      selectedPageBlockId.value = ''
    return
  }
  if (payload.key === 'duplicate')
    duplicateNestedPageBlock(blockId)
}

function duplicateNestedPageBlock(blockId) {
  const source = findPageBlockInTree(pageBlocks.value, blockId)
  if (!source)
    return
  const copy = clonePageBlockTree(source)
  let inserted = false
  updatePageBlocks(mapPageBlocksInTree(pageBlocks.value, (block) => {
    if (inserted)
      return block
    if (Array.isArray(block.children)) {
      const index = block.children.findIndex(child => child.id === blockId)
      if (index >= 0) {
        inserted = true
        const children = [...block.children]
        children.splice(index + 1, 0, copy)
        return { ...block, children }
      }
    }
    if (Array.isArray(block.props?.tabs)) {
      let changed = false
      const tabs = block.props.tabs.map((tab) => {
        const index = (tab.children || []).findIndex(child => child.id === blockId)
        if (index < 0)
          return tab
        changed = true
        inserted = true
        const children = [...tab.children]
        children.splice(index + 1, 0, copy)
        return { ...tab, children }
      })
      if (changed)
        return { ...block, props: { ...(block.props || {}), tabs } }
    }
    if (Array.isArray(block.props?.cells)) {
      let changed = false
      const cells = block.props.cells.map((cell) => {
        const index = (cell.children || []).findIndex(child => child.id === blockId)
        if (index < 0)
          return cell
        changed = true
        inserted = true
        const children = [...cell.children]
        children.splice(index + 1, 0, copy)
        return { ...cell, children }
      })
      if (changed)
        return { ...block, props: { ...(block.props || {}), cells } }
    }
    return block
  }))
  if (inserted)
    selectedPageBlockId.value = copy.id
}

function clonePageBlockTree(source = {}) {
  const stamp = Date.now()
  let seq = 0
  const walk = (node) => {
    if (!node || typeof node !== 'object')
      return node
    const next = {
      ...node,
      id: `${node.blockType || 'block'}_${stamp}_${seq++}`,
    }
    if (Array.isArray(node.children))
      next.children = node.children.map(walk)
    if (Array.isArray(node.props?.tabs) || Array.isArray(node.props?.cells)) {
      next.props = { ...(node.props || {}) }
      if (Array.isArray(node.props?.tabs)) {
        next.props.tabs = node.props.tabs.map(tab => ({
          ...tab,
          children: (tab.children || []).map(walk),
        }))
      }
      if (Array.isArray(node.props?.cells)) {
        next.props.cells = node.props.cells.map(cell => ({
          ...cell,
          children: (cell.children || []).map(walk),
        }))
      }
    }
    return next
  }
  return walk(JSON.parse(JSON.stringify(source)))
}

function appendPageBlockToGridCell(blockType, containerId, cellKey, cellIndex = -1) {
  const meta = resolveListPageBlockMeta(blockType)
  const container = findPageBlockInTree(pageBlocks.value, containerId)
  if (!meta || !container)
    return
  // 卡片/盒子复用栅格命中协议（data-grid-cell-key=__body__），落到 children
  if (['card', 'box-layout'].includes(container.blockType)) {
    appendPageBlockToContainer(blockType, containerId)
    return
  }
  if (container.blockType !== 'grid-layout')
    return
  const rawCells = Array.isArray(container.props?.cells) && container.props.cells.length
    ? container.props.cells
    : [{ key: 'cell_1', title: '栅格 1', span: 24, children: [] }]
  const cells = rawCells.map((cell, index) => ({
    ...cell,
    key: String(cell.key || `cell_${index + 1}`),
  }))
  let targetKey = String(cellKey || '')
  if (!cells.some(cell => cell.key === targetKey)) {
    const idx = Number(cellIndex)
    if (Number.isFinite(idx) && idx >= 0 && idx < cells.length)
      targetKey = cells[idx].key
  }
  if (!cells.some(cell => cell.key === targetKey))
    targetKey = cells[0]?.key || ''
  if (!targetKey)
    return
  let block = createGridBlock(blockType, applicationGridModelSchema.value, {
    gridX: 0,
    gridY: (cells.find(cell => cell.key === targetKey)?.children || []).length * 2,
  })
  if (!block)
    return
  block = attachDefaultRuntimeObject(block)
  block = attachSingleFormAsset(block)
  block = normalizePageBlockForContainer(block)
  const nextCells = cells.map(cell => cell.key === targetKey
    ? { ...cell, children: [...(cell.children || []), block] }
    : cell)
  updatePageBlocks(mapPageBlocksInTree(pageBlocks.value, item => item.id === containerId
    ? { ...item, props: { ...(item.props || {}), cells: nextCells } }
    : item))
  selectedPageBlockId.value = block.id
  preloadPageBlockCrudRuntimeProps(block)
}

function appendPageBlockToTab(blockType, containerId, tabKey) {
  const meta = resolveListPageBlockMeta(blockType)
  const container = findPageBlockInTree(pageBlocks.value, containerId)
  if (!meta || container?.blockType !== 'tabs')
    return
  const tabs = Array.isArray(container.props?.tabs) && container.props.tabs.length
    ? container.props.tabs
    : [{ key: 'tab1', title: '标签一', children: [] }]
  if (!tabs.some(tab => tab.key === tabKey))
    return
  let block = createGridBlock(blockType, applicationGridModelSchema.value, {
    gridX: 0,
    gridY: (tabs.find(tab => tab.key === tabKey)?.children || []).length * 2,
  })
  if (!block)
    return
  block = attachDefaultRuntimeObject(block)
  block = attachSingleFormAsset(block)
  block = normalizePageBlockForContainer(block)
  const nextTabs = tabs.map(tab => tab.key === tabKey
    ? { ...tab, children: [...(tab.children || []), block] }
    : tab)
  updatePageBlocks(mapPageBlocksInTree(pageBlocks.value, item => item.id === containerId
    ? { ...item, props: { ...(item.props || {}), tabs: nextTabs } }
    : item))
  selectedPageBlockId.value = block.id
  preloadPageBlockCrudRuntimeProps(block)
}

function resolvePageBlockMoreOptions(block = {}) {
  const borderWidth = Number(block.props?.style?.borderWidth || 0)
  const borderMode = itemBorderColorMode(block)
  const isThemeBorder = borderMode === 'theme' && borderWidth > 0
  const menuLabel = (label, active) => active ? `✓  ${label}` : label
  const moveIntoOptions = resolvePageBlockMoveIntoOptions(block)
  return [
    { label: '配置', key: 'configure', icon: () => renderNavigationMenuIcon(SettingsOutline) },
    { type: 'divider', key: 'configureDivider' },
    { label: '复制到当前页面', key: 'duplicate', icon: () => renderNavigationMenuIcon(DuplicateOutline) },
    { label: '复制到其他页面', key: 'copyToPage', disabled: !copyBlockPageOptions.value.length, icon: () => renderNavigationMenuIcon(CopyOutline) },
    {
      label: '移入布局',
      key: 'moveIntoLayout',
      icon: () => renderNavigationMenuIcon(GitBranchOutline),
      children: moveIntoOptions.length
        ? moveIntoOptions
        : [{ label: '暂无布局组合和标签页', key: 'moveInto:empty', disabled: true }],
    },
    {
      label: '尺寸',
      key: 'size',
      icon: () => renderNavigationMenuIcon(SettingsOutline),
      children: [
        { type: 'group', label: '宽度', key: 'widthGroup', children: ['auto', 'full', 'fixed'].map(mode => ({ label: menuLabel({ auto: '默认宽度', full: '填充容器', fixed: '固定宽度' }[mode], (block.props?.style?.widthMode || 'full') === mode), key: `size:width:${mode}`, icon: () => renderNavigationMenuIcon(mode === 'auto' ? RemoveOutline : mode === 'full' ? SwapHorizontalOutline : ResizeOutline) })) },
        { type: 'group', label: '高度', key: 'heightGroup', children: ['fixed', 'auto', 'full'].map(mode => ({ label: menuLabel({ fixed: '默认高度', auto: '适应内容', full: '填充容器' }[mode], (block.props?.style?.heightMode || 'fixed') === mode), key: `size:height:${mode}`, icon: () => renderNavigationMenuIcon(mode === 'auto' ? ResizeOutline : mode === 'full' ? ExpandOutline : RemoveOutline) })) },
      ],
    },
    {
      label: '更换背景色',
      key: 'backgroundColor',
      icon: () => renderNavigationMenuIcon(ColorFillOutline),
    },
    {
      label: '背景描边',
      key: 'backgroundBorder',
      icon: () => renderNavigationMenuIcon(SquareOutline),
      children: [
        { label: menuLabel('跟随主题', isThemeBorder), key: 'border:theme', icon: () => renderNavigationMenuIcon(SettingsOutline) },
        {
          label: '粗细',
          key: 'borderWidth',
          children: [
            { label: menuLabel('跟随主题', isThemeBorder), key: 'borderWidth:theme', icon: () => renderNavigationMenuIcon(SettingsOutline) },
            ...[0, 0.5, 1, 2, 3, 4].map(width => ({ label: menuLabel(`${width} px`, borderWidth === width && borderMode !== 'theme'), key: `borderWidth:${width}`, icon: () => renderNavigationMenuIcon(RemoveOutline) })),
          ],
        },
        {
          label: '颜色',
          key: 'borderColor',
          children: [
            { label: menuLabel('跟随主题', borderMode === 'theme'), key: 'border:theme', icon: () => renderNavigationMenuIcon(SettingsOutline) },
            { label: menuLabel('浅灰', borderMode === '#d0d3d8'), key: 'border:#d0d3d8', icon: () => renderNavigationMenuIcon(SquareOutline) },
            { label: menuLabel('深灰', borderMode === '#86909c'), key: 'border:#86909c', icon: () => renderNavigationMenuIcon(SquareOutline) },
            { label: menuLabel('蓝色', borderMode === '#3370ff'), key: 'border:#3370ff', icon: () => renderNavigationMenuIcon(SquareOutline) },
          ],
        },
      ],
    },
    { type: 'divider', key: 'dangerDivider' },
    { label: '删除', key: 'delete', icon: () => renderNavigationMenuIcon(TrashOutline) },
  ]
}

function handlePageBlockMoreSelect(key, block) {
  const index = pageBlocks.value.findIndex(item => item.id === block.id)
  if (index < 0)
    return
  selectPageBlock(block.id)
  if (key === 'configure') {
    configPanelVisible.value = true
    return
  }
  if (key === 'copyToPage') {
    copyBlockId.value = block.id
    copyBlockTargetPageId.value = ''
    copyBlockVisible.value = true
    return
  }
  if (key.startsWith('moveInto:')) {
    movePageBlockIntoContainer(block.id, key.slice('moveInto:'.length))
    return
  }
  if (key === 'backgroundColor') {
    backgroundPickerBlockId.value = block.id
    blockBackgroundPickerVisible.value = true
    return
  }
  if (key.startsWith('size:')) {
    updatePageBlockSize(block, key)
    return
  }
  if (key.startsWith('background:')) {
    updateSelectedBlockAppearance({ backgroundColor: key.slice('background:'.length) || 'transparent' })
    return
  }
  if (key.startsWith('borderWidth:')) {
    const value = key.slice('borderWidth:'.length)
    updateSelectedBlockAppearance(value === 'theme'
      ? { borderColor: 'theme', borderWidth: 1 }
      : { borderWidth: Number(value) || 0 })
    return
  }
  if (key.startsWith('border:')) {
    updateSelectedBlockAppearance({ borderColor: key.slice('border:'.length) || 'theme' })
    return
  }
  const items = [...pageBlocks.value]
  if (key === 'duplicate') {
    const copy = JSON.parse(JSON.stringify(block))
    copy.id = `${block.blockType}_${Date.now()}`
    copy.label = `${block.label || resolveListPageBlockMeta(block.blockType)?.title || '区块'} 副本`
    items.splice(index + 1, 0, copy)
    updatePageBlocks(items)
    selectedPageBlockId.value = copy.id
    return
  }
  if (key === 'delete') {
    items.splice(index, 1)
    updatePageBlocks(items)
    selectedPageBlockId.value = ''
    return
  }
  updatePageBlocks(items)
}

function resolvePageBlockMoveIntoOptions(block = {}) {
  return pageBlocks.value
    .filter((candidate) => {
      if (!candidate?.id || candidate.id === block.id)
        return false
      const meta = resolveListPageBlockMeta(candidate.blockType)
      return meta?.container === true && ['grid-layout', 'box-layout', 'card', 'tabs'].includes(candidate.blockType)
    })
    .map((candidate) => {
      const meta = resolveListPageBlockMeta(candidate.blockType)
      return {
        label: `${meta?.title || '布局组合'} · ${candidate.label || meta?.title || '未命名布局'}`,
        key: `moveInto:${candidate.id}`,
        icon: () => renderNavigationMenuIcon(candidate.blockType === 'tabs' ? DocumentTextOutline : MoveOutline),
      }
    })
}

function movePageBlockIntoContainer(blockId, containerId) {
  if (!blockId || !containerId || blockId === containerId)
    return
  const source = pageBlocks.value.find(item => item.id === blockId)
  const container = pageBlocks.value.find(item => item.id === containerId)
  if (!source || !container)
    return

  const nested = normalizePageBlockForContainer(source)
  const nextItems = pageBlocks.value
    .filter(item => item.id !== blockId)
    .map((item) => {
      if (item.id !== containerId)
        return item
      if (item.blockType === 'grid-layout') {
        const cells = Array.isArray(item.props?.cells) && item.props.cells.length
          ? item.props.cells.map(cell => ({ ...cell, children: [...(cell.children || [])] }))
          : [{ key: 'cell_1', title: '栅格 1', span: 24, children: [] }]
        cells[0] = { ...cells[0], children: [...cells[0].children, nested] }
        return { ...item, props: { ...(item.props || {}), cells } }
      }
      if (item.blockType === 'tabs') {
        const tabs = Array.isArray(item.props?.tabs) && item.props.tabs.length
          ? item.props.tabs.map(tab => ({ ...tab, children: [...(tab.children || [])] }))
          : [{ key: 'tab1', title: '标签一', children: [] }]
        tabs[0] = { ...tabs[0], children: [...tabs[0].children, nested] }
        return { ...item, props: { ...(item.props || {}), tabs } }
      }
      return { ...item, children: [...(item.children || []), nested] }
    })
  updatePageBlocks(nextItems, { resolveCollisions: true, changedBlockId: containerId })
  selectedPageBlockId.value = containerId
  message.success(`组件已移入${container.label || resolveListPageBlockMeta(container.blockType)?.title || '布局组合'}`)
}

function normalizePageBlockForContainer(block) {
  const {
    pageFlowX,
    pageFlowY,
    pageFlowWidth,
    pageFlowHeight,
    x,
    y,
    left,
    top,
    ...containerStyle
  } = block.props?.style || {}
  return {
    ...JSON.parse(JSON.stringify(block)),
    props: {
      ...(block.props || {}),
      style: {
        ...containerStyle,
        // 嵌套容器内默认铺满，禁止带出画布绝对坐标/固定宽
        widthMode: 'full',
        width: '100%',
        maxWidth: '100%',
        heightMode: containerStyle.heightMode || 'auto',
        x: 0,
        y: 0,
        left: 0,
        top: 0,
      },
    },
  }
}

function updatePageBlockSize(block, key) {
  const [, axis, mode] = key.split(':')
  const style = block.props?.style || {}
  const nextStyle = { ...style }
  if (axis === 'width') {
    nextStyle.widthMode = mode
    nextStyle.width = mode === 'full' ? '100%' : mode === 'auto' ? 'auto' : Math.max(280, Math.min(640, readPageBlockLength(style.width, 640)))
    nextStyle.pageFlowWidth = mode === 'full' ? 'calc(100% - 48px)' : `${readPageBlockLength(nextStyle.width, mode === 'auto' ? 520 : 640)}px`
  }
  if (axis === 'height')
    nextStyle.heightMode = mode
  updatePageBlocks(
    pageBlocks.value.map(item => item.id === block.id ? { ...item, props: { ...(item.props || {}), style: nextStyle } } : item),
    { resolveCollisions: true, changedBlockId: block.id },
  )
}

function resolvePageBlockBackgroundColor(block = {}) {
  const value = String(block.props?.style?.backgroundColor || '').trim()
  return value && value !== 'transparent' ? value : '#FFFFFF00'
}

function updatePageBlockBackgroundColor(block, value) {
  if (!block?.id)
    return
  selectPageBlock(block.id)
  updateSelectedBlockAppearance({ backgroundColor: value || 'transparent' })
}

function copySelectedBlockToOtherPage() {
  const block = pageBlocks.value.find(item => item.id === copyBlockId.value)
  const targetPage = builder.value?.pages?.[copyBlockTargetPageId.value]
  if (!block || !targetPage)
    return
  const targetLayout = targetPage.layout?.gridLayout || {
    cols: 12,
    rowHeight: 32,
    gap: 8,
    designWidth: 1366,
    layoutType: 'simple-crud',
    items: [],
  }
  const copy = JSON.parse(JSON.stringify(block))
  copy.id = `${block.blockType}_${Date.now()}`
  copy.label = `${block.label || resolveListPageBlockMeta(block.blockType)?.title || '组件'} 副本`
  const targetItems = [...(targetLayout.items || []), copy]
  builder.value = {
    ...builder.value,
    pages: {
      ...builder.value.pages,
      [copyBlockTargetPageId.value]: {
        ...targetPage,
        layout: { ...targetPage.layout, gridLayout: { ...targetLayout, items: targetItems } },
      },
    },
  }
  copyBlockVisible.value = false
  message.success('组件已复制到目标页面')
}

function updateSelectedBlockAppearance(patch = {}) {
  if (!selectedPageBlock.value)
    return
  const borderColorMode = patch.borderColor || itemBorderColorMode(selectedPageBlock.value)
  const normalizeBorderColor = borderColorMode === 'theme' ? 'var(--primary-color, #3370ff)' : borderColorMode
  updatePageBlocks(mapPageBlocksInTree(pageBlocks.value, item => item.id === selectedPageBlock.value.id
    ? {
        ...item,
        props: {
          ...(item.props || {}),
          style: {
            ...(item.props?.style || {}),
            ...patch,
            ...(Object.prototype.hasOwnProperty.call(patch, 'borderColor')
              ? { borderColor: normalizeBorderColor, borderColorMode }
              : {}),
            borderStyle: Number(patch.borderWidth ?? item.props?.style?.borderWidth ?? 0) > 0 ? 'solid' : 'none',
          },
        },
      }
    : item))
}

function itemBorderColorMode(block = {}) {
  return block.props?.style?.borderColorMode || (block.props?.style?.borderColor === 'var(--primary-color, #3370ff)' ? 'theme' : block.props?.style?.borderColor) || 'theme'
}

let pageBlockResizeCtx = null
let nestedPageBlockResizeCtx = null

function handleNestedPageBlockResizeStart(payload = {}) {
  const block = payload.block
  const event = payload.event
  if (!block?.id || !event || event.button !== 0)
    return
  const node = event.currentTarget?.closest?.('[data-grid-child-id]')
  const rect = node?.getBoundingClientRect?.()
  if (!rect)
    return
  event.preventDefault()
  handleNestedPageBlockSelect(block.id)
  const parent = node?.parentElement
  const parentRect = parent?.getBoundingClientRect?.()
  const parentStyle = parent ? window.getComputedStyle(parent) : null
  const parentPadX = parentStyle
    ? (Number.parseFloat(parentStyle.paddingLeft) || 0) + (Number.parseFloat(parentStyle.paddingRight) || 0)
    : 0
  const maxWidth = parentRect?.width
    ? Math.max(120, Math.floor(parentRect.width - parentPadX))
    : 0
  nestedPageBlockResizeCtx = {
    blockId: block.id,
    anchor: payload.anchor || 'bottom-right',
    startX: event.clientX,
    startY: event.clientY,
    originWidth: rect.width,
    originHeight: rect.height,
    maxWidth,
  }
  window.addEventListener('pointermove', resizeNestedPageBlock)
  window.addEventListener('pointerup', endNestedPageBlockResize)
}

function handleNestedPageBlockMoveStart(payload = {}) {
  const block = payload.block
  const event = payload.event
  if (!editing.value || !block?.id || !event || event.button !== 0)
    return
  const node = event.currentTarget?.closest?.('[data-grid-child-id]')
    || event.target?.closest?.('[data-grid-child-id]')
  const flow = document.querySelector('.application-page-flow')
  const rect = node?.getBoundingClientRect?.()
  const flowRect = flow?.getBoundingClientRect?.()
  if (!rect || !flowRect)
    return
  event.preventDefault()
  event.stopPropagation()
  handleNestedPageBlockSelect(block.id)
  const originX = Math.round(rect.left - flowRect.left + (flow.scrollLeft || 0))
  const originY = Math.round(rect.top - flowRect.top + (flow.scrollTop || 0))
  nestedPageBlockMoveCtx = {
    blockId: block.id,
    startX: event.clientX,
    startY: event.clientY,
    originX,
    originY,
    width: rect.width,
    height: rect.height,
    maxX: Math.max(0, flowRect.width - rect.width),
    moved: false,
  }
  nestedMovingPageBlockId.value = block.id
  dragPreview.value = {
    blockId: block.id,
    x: originX,
    y: originY,
    width: rect.width,
    height: rect.height,
    nested: true,
  }
  document.body.classList.add('is-nested-page-block-moving')
  window.addEventListener('pointermove', onNestedPageBlockMove, { passive: false })
  window.addEventListener('pointerup', endNestedPageBlockMove)
  window.addEventListener('pointercancel', endNestedPageBlockMove)
}

function onNestedPageBlockMove(event) {
  pendingNestedPageBlockMoveEvent = event
  if (nestedPageBlockMoveFrame)
    return
  nestedPageBlockMoveFrame = window.requestAnimationFrame(() => {
    nestedPageBlockMoveFrame = 0
    const nextEvent = pendingNestedPageBlockMoveEvent
    pendingNestedPageBlockMoveEvent = null
    if (nextEvent)
      applyNestedPageBlockMove(nextEvent)
  })
}

function applyNestedPageBlockMove(event) {
  if (!nestedPageBlockMoveCtx)
    return
  event.preventDefault?.()
  const ctx = nestedPageBlockMoveCtx
  if (!ctx.moved && Math.hypot(event.clientX - ctx.startX, event.clientY - ctx.startY) > 3)
    ctx.moved = true
  const pageFlowX = Math.round(Math.max(0, Math.min(ctx.maxX, ctx.originX + event.clientX - ctx.startX)))
  const pageFlowY = Math.round(Math.max(0, ctx.originY + event.clientY - ctx.startY))
  dragPreview.value = { ...dragPreview.value, x: pageFlowX, y: pageFlowY }
  pageAlignGuides.value = {
    visible: true,
    cross: {
      x: Math.round(pageFlowX + ctx.width / 2),
      y: Math.round(pageFlowY + ctx.height / 2),
    },
    x: [],
    y: [],
  }
  ctx.activeTabTarget = resolvePageFlowTabTargetFromPoint(event)
  ctx.activeGridTarget = resolvePageFlowGridCellTargetFromPoint(event)
  activePageFlowGridTarget.value = ctx.activeGridTarget
    ? {
        containerId: ctx.activeGridTarget.blockId,
        cellKey: ctx.activeGridTarget.cellKey,
        cellIndex: ctx.activeGridTarget.cellIndex,
      }
    : null
  ctx.activeContainerTarget = ctx.activeGridTarget
    ? null
    : normalizeContainerDropTarget(resolvePageFlowContainerTargetFromPoint(event))
  activePageFlowContainerTarget.value = ctx.activeContainerTarget
}

function endNestedPageBlockMove(event) {
  if (nestedPageBlockMoveFrame) {
    window.cancelAnimationFrame(nestedPageBlockMoveFrame)
    nestedPageBlockMoveFrame = 0
    pendingNestedPageBlockMoveEvent = null
  }
  const ctx = nestedPageBlockMoveCtx
  nestedPageBlockMoveCtx = null
  window.removeEventListener('pointermove', onNestedPageBlockMove)
  window.removeEventListener('pointerup', endNestedPageBlockMove)
  window.removeEventListener('pointercancel', endNestedPageBlockMove)
  document.body.classList.remove('is-nested-page-block-moving')
  nestedMovingPageBlockId.value = ''
  activePageFlowGridTarget.value = null
  activePageFlowContainerTarget.value = null
  clearPageAlignGuides()
  const preview = dragPreview.value
  dragPreview.value = null
  if (!ctx?.moved)
    return
  const blockId = ctx.blockId
  const tabTarget = ctx.activeTabTarget
    || (event ? resolvePageFlowTabTargetFromPoint(event) : null)
  if (tabTarget && relocateNestedPageBlockToTab(blockId, tabTarget.blockId, tabTarget.tabKey))
    return
  const gridTarget = ctx.activeGridTarget
    || (event ? resolvePageFlowGridCellTargetFromPoint(event) : null)
  if (gridTarget && relocateNestedPageBlockToGridCell(blockId, gridTarget.blockId, gridTarget.cellKey))
    return
  const containerTarget = normalizeContainerDropTarget(
    ctx.activeContainerTarget || (event ? resolvePageFlowContainerTargetFromPoint(event) : null),
  )
  if (containerTarget && relocateNestedPageBlockToContainer(blockId, containerTarget.containerId))
    return
  extractNestedPageBlockToCanvas(
    blockId,
    Math.round(preview?.x ?? ctx.originX),
    Math.round(preview?.y ?? ctx.originY),
    Math.round(preview?.width ?? ctx.width),
    Math.round(preview?.height ?? ctx.height),
  )
}

function relocateNestedPageBlockToGridCell(blockId, containerId, cellKey) {
  const source = findPageBlockInTree(pageBlocks.value, blockId)
  const container = findPageBlockInTree(pageBlocks.value, containerId)
  if (!source || !container)
    return false
  if (['card', 'box-layout'].includes(container.blockType))
    return relocateNestedPageBlockToContainer(blockId, containerId)
  if (container.blockType !== 'grid-layout')
    return false
  // 禁止拖进自身或自己的子孙
  if (source.id === containerId || findPageBlockInTree([source], containerId))
    return false
  const cells = Array.isArray(container.props?.cells) ? container.props.cells : []
  let targetKey = String(cellKey || '')
  if (!cells.some(cell => String(cell.key) === targetKey))
    targetKey = String(cells[0]?.key || '')
  if (!targetKey)
    return false
  // 已在目标格则不动
  const alreadyInTarget = cells.some(cell => String(cell.key) === targetKey
    && (cell.children || []).some(child => child.id === blockId))
  if (alreadyInTarget)
    return true
  const nested = normalizePageBlockForContainer(source)
  const withoutSource = removePageBlockFromTree(pageBlocks.value, blockId)
  updatePageBlocks(mapPageBlocksInTree(withoutSource, item => item.id === containerId
    ? {
        ...item,
        props: {
          ...(item.props || {}),
          cells: (item.props?.cells || []).map(cell => String(cell.key) === targetKey
            ? { ...cell, children: [...(cell.children || []), nested] }
            : cell),
        },
      }
    : item))
  selectedPageBlockId.value = blockId
  return true
}

function relocateNestedPageBlockToTab(blockId, containerId, tabKey) {
  const source = findPageBlockInTree(pageBlocks.value, blockId)
  const container = findPageBlockInTree(pageBlocks.value, containerId)
  if (!source || container?.blockType !== 'tabs' || source.id === containerId)
    return false
  const tabs = Array.isArray(container.props?.tabs) ? container.props.tabs : []
  if (!tabs.some(tab => tab.key === tabKey))
    return false
  const nested = normalizePageBlockForContainer(source)
  const withoutSource = removePageBlockFromTree(pageBlocks.value, blockId)
  updatePageBlocks(mapPageBlocksInTree(withoutSource, item => item.id === containerId
    ? {
        ...item,
        props: {
          ...(item.props || {}),
          tabs: (item.props?.tabs || []).map(tab => tab.key === tabKey
            ? { ...tab, children: [...(tab.children || []), nested] }
            : tab),
        },
      }
    : item))
  selectedPageBlockId.value = blockId
  return true
}

function relocateNestedPageBlockToContainer(blockId, containerId) {
  const source = findPageBlockInTree(pageBlocks.value, blockId)
  const container = findPageBlockInTree(pageBlocks.value, containerId)
  if (!source || !container || !['card', 'box-layout'].includes(container.blockType))
    return false
  if (source.id === containerId || findPageBlockInTree([source], containerId))
    return false
  if ((container.children || []).some(child => child.id === blockId))
    return true
  const nested = normalizePageBlockForContainer(source)
  const withoutSource = removePageBlockFromTree(pageBlocks.value, blockId)
  updatePageBlocks(mapPageBlocksInTree(withoutSource, item => item.id === containerId
    ? { ...item, children: [...(item.children || []), nested] }
    : item))
  selectedPageBlockId.value = blockId
  return true
}

function extractNestedPageBlockToCanvas(blockId, x, y, width, height) {
  const source = findPageBlockInTree(pageBlocks.value, blockId)
  if (!source)
    return false
  // 已是根级则只更新坐标
  if (pageBlocks.value.some(item => item.id === blockId)) {
    updatePageBlocks(pageBlocks.value.map(item => item.id === blockId
      ? {
          ...item,
          props: {
            ...(item.props || {}),
            style: {
              ...(item.props?.style || {}),
              pageFlowX: x,
              pageFlowY: y,
              pageFlowWidth: `${width}px`,
              pageFlowHeight: `${height}px`,
              widthMode: 'fixed',
              width,
              heightMode: 'fixed',
              height,
            },
          },
        }
      : item))
    selectedPageBlockId.value = blockId
    return true
  }
  const withoutSource = removePageBlockFromTree(pageBlocks.value, blockId)
  const restored = {
    ...JSON.parse(JSON.stringify(source)),
    props: {
      ...(source.props || {}),
      style: {
        ...(source.props?.style || {}),
        pageFlowX: x,
        pageFlowY: y,
        pageFlowWidth: `${width}px`,
        pageFlowHeight: `${height}px`,
        widthMode: 'fixed',
        width,
        heightMode: 'fixed',
        height,
      },
    },
  }
  updatePageBlocks([...withoutSource, restored])
  selectedPageBlockId.value = blockId
  return true
}

function resizeNestedPageBlock(event) {
  const ctx = nestedPageBlockResizeCtx
  if (!ctx)
    return
  const dx = event.clientX - ctx.startX
  const dy = event.clientY - ctx.startY
  let width = ctx.originWidth
  let height = ctx.originHeight
  if (ctx.anchor.includes('right'))
    width += dx
  if (ctx.anchor.includes('left'))
    width -= dx
  if (ctx.anchor.includes('bottom'))
    height += dy
  if (ctx.anchor.includes('top'))
    height -= dy
  const maxWidth = Number(ctx.maxWidth) > 0 ? Number(ctx.maxWidth) : Number.POSITIVE_INFINITY
  const nextWidth = Math.min(maxWidth, Math.max(120, Math.round(width)))
  const nextHeight = Math.max(40, Math.round(height))
  // 嵌套容器内拉伸到接近父宽时切回铺满，避免越界
  const useFullWidth = Number.isFinite(maxWidth) && nextWidth >= maxWidth - 2
  updatePageBlocks(mapPageBlocksInTree(pageBlocks.value, block => block.id === ctx.blockId
    ? {
        ...block,
        props: {
          ...(block.props || {}),
          style: {
            ...(block.props?.style || {}),
            widthMode: useFullWidth ? 'full' : 'fixed',
            width: useFullWidth ? '100%' : `${nextWidth}px`,
            maxWidth: '100%',
            heightMode: 'fixed',
            height: `${nextHeight}px`,
            pageFlowHeight: nextHeight,
          },
        },
      }
    : block))
}

function endNestedPageBlockResize() {
  nestedPageBlockResizeCtx = null
  window.removeEventListener('pointermove', resizeNestedPageBlock)
  window.removeEventListener('pointerup', endNestedPageBlockResize)
}

function startPageBlockResize(block, event, anchor = 'bottom-right') {
  if (event.button !== 0)
    return
  const node = event.currentTarget.closest('[data-page-block-id]')
  const flow = node?.parentElement
  const rect = node?.getBoundingClientRect?.()
  const flowRect = flow?.getBoundingClientRect?.()
  if (!rect || !flowRect)
    return
  event.preventDefault()
  selectPageBlock(block.id)
  if (!pageFlowStackMode.value)
    updateCurrentGridLayout(ensurePageFlowContentCoords({ ...(currentGridLayout.value || {}) }))
  const pad = pageCanvasPadding.value
  pageBlockResizeCtx = {
    blockId: block.id,
    anchor,
    startX: event.clientX,
    startY: event.clientY,
    originWidth: rect.width,
    originHeight: rect.height,
    originX: rect.left - flowRect.left + (flow.scrollLeft || 0),
    originY: rect.top - flowRect.top + (flow.scrollTop || 0),
    canvasPadLeft: pad.left,
    canvasPadRight: pad.right,
    canvasPadTop: pad.top,
    widthMode: block.props?.style?.widthMode || 'full',
    canvasWidth: Math.max(240, flowRect.width),
  }
  window.addEventListener('pointermove', onPageBlockResize)
  window.addEventListener('pointerup', endPageBlockResize)
}

function onPageBlockResize(event) {
  if (!pageBlockResizeCtx)
    return
  const ctx = pageBlockResizeCtx
  const widthDelta = event.clientX - ctx.startX
  const heightDelta = event.clientY - ctx.startY
  const anchor = ctx.anchor || 'bottom-right'
  let width = ctx.originWidth
  let height = ctx.originHeight
  let canvasX = ctx.originX
  let canvasY = ctx.originY
  const resizingWidth = anchor.includes('left') || anchor.includes('right')
  if (anchor.includes('right'))
    width = ctx.originWidth + widthDelta
  if (anchor.includes('left')) {
    width = ctx.originWidth - widthDelta
    canvasX = ctx.originX + widthDelta
  }
  if (anchor.includes('bottom'))
    height = ctx.originHeight + heightDelta
  if (anchor.includes('top')) {
    height = ctx.originHeight - heightDelta
    canvasY = ctx.originY + heightDelta
  }
  const padLeft = Number(ctx.canvasPadLeft) || 24
  const padRight = Number(ctx.canvasPadRight) || 24
  const padTop = Number(ctx.canvasPadTop) || 24
  const canvasWidth = Number(ctx.canvasWidth) || 1200
  const maxRight = Math.max(padLeft + 180, canvasWidth - padRight)
  canvasX = Math.max(padLeft, canvasX)
  canvasY = Math.max(padTop, canvasY)
  let nextW = Math.min(Math.max(180, width), Math.max(180, maxRight - canvasX))
  if (anchor.includes('left') && nextW < width)
    canvasX = Math.max(padLeft, canvasX + (width - nextW))
  nextW = Math.round(Math.min(Math.max(180, nextW), Math.max(180, maxRight - canvasX)))
  const content = canvasToContentFlowPoint(canvasX, canvasY, {
    left: padLeft,
    right: padRight,
    top: padTop,
    bottom: 0,
  })
  const pageFlowHeight = Math.round(Math.max(40, height))
  const keepFullWidth = !resizingWidth && (ctx.widthMode === 'full' || ctx.widthMode === 'auto')
  updateResizeCollisionHighlights(ctx.blockId, {
    x: canvasX,
    y: canvasY,
    width: keepFullWidth ? Math.max(180, canvasWidth - padLeft - padRight) : nextW,
    height: pageFlowHeight,
  })
  updatePageBlocks(pageBlocks.value.map((item) => {
    if (item.id !== ctx.blockId)
      return item
    const prev = item.props?.style || {}
    const nextStyle = {
      ...prev,
      heightMode: 'fixed',
      height: pageFlowHeight,
      pageFlowHeight,
      pageFlowY: content.y,
    }
    if (keepFullWidth) {
      nextStyle.widthMode = prev.widthMode === 'auto' ? 'auto' : 'full'
      nextStyle.width = nextStyle.widthMode === 'auto' ? 'auto' : '100%'
      delete nextStyle.pageFlowWidth
      nextStyle.pageFlowX = 0
    }
    else {
      nextStyle.widthMode = 'fixed'
      nextStyle.width = nextW
      nextStyle.pageFlowWidth = `${nextW}px`
      nextStyle.pageFlowX = content.x
    }
    return {
      ...item,
      props: {
        ...(item.props || {}),
        style: nextStyle,
      },
    }
  }))
}

function updateResizeCollisionHighlights(blockId, rect = {}) {
  const pad = 2
  const left = Number(rect.x) || 0
  const top = Number(rect.y) || 0
  const right = left + (Number(rect.width) || 0)
  const bottom = top + (Number(rect.height) || 0)
  const hits = []
  pageBlocks.value.forEach((item, index) => {
    if (item.id === blockId)
      return
    const geo = resolvePageBlockFlowGeometry(item, index, pageBlocks.value)
    const overlaps = left - pad < geo.right
      && right + pad > geo.x
      && top - pad < geo.bottom
      && bottom + pad > geo.y
    if (overlaps)
      hits.push(item.id)
  })
  resizeCollisionBlockIds.value = hits
}

function endPageBlockResize() {
  const resizedBlockId = pageBlockResizeCtx?.blockId || ''
  pageBlockResizeCtx = null
  resizeCollisionBlockIds.value = []
  window.removeEventListener('pointermove', onPageBlockResize)
  window.removeEventListener('pointerup', endPageBlockResize)
  if (resizedBlockId)
    updatePageBlocks(pageBlocks.value, { resolveCollisions: true, changedBlockId: resizedBlockId })
}

let pageBlockMoveCtx = null
let pageBlockMoveFrame = 0
let pendingPageBlockMoveEvent = null
function startPageBlockMove(block, event) {
  if (event.button !== 0)
    return
  const node = event.currentTarget.closest('[data-page-block-id]')
  const flow = node?.parentElement
  const rect = node?.getBoundingClientRect?.()
  const flowRect = flow?.getBoundingClientRect?.()
  if (!rect || !flowRect)
    return
  event.preventDefault()
  selectPageBlock(block.id)
  if (!pageFlowStackMode.value)
    updateCurrentGridLayout(ensurePageFlowContentCoords({ ...(currentGridLayout.value || {}) }))
  const pad = pageCanvasPadding.value
  const originX = Math.round(rect.left - flowRect.left + (flow.scrollLeft || 0))
  const originY = Math.round(rect.top - flowRect.top + (flow.scrollTop || 0))
  pageBlockMoveCtx = {
    blockId: block.id,
    startX: event.clientX,
    startY: event.clientY,
    originX,
    originY,
    originClientLeft: rect.left,
    originClientTop: rect.top,
    width: rect.width,
    height: rect.height,
    activeSwapTargetId: '',
    blockSlots: new Map(pageBlocks.value.map((item) => {
      const style = resolvePageBlockShellStyle(item)
      const contentX = readPageBlockLength(item.props?.style?.pageFlowX, 0)
      const contentY = readPageBlockLength(item.props?.style?.pageFlowY, 0)
      const canvas = contentToCanvasFlowPoint(contentX, contentY, pad)
      return [item.id, {
        x: canvas.x || readPageBlockLength(style.left, pad.left),
        y: canvas.y || readPageBlockLength(style.top, pad.top),
        contentX,
        contentY,
      }]
    })),
    canvasPad: pad.left,
    canvasPadTop: pad.top,
    maxX: Math.max(pad.left, flowRect.width - rect.width - pad.right),
    maxY: Math.max(pad.top, flowRect.height - rect.height),
  }
  dragPreview.value = { blockId: block.id, x: originX, y: originY, width: rect.width, height: rect.height }
  draggingPageBlockId.value = block.id
  window.addEventListener('pointermove', onPageBlockMove)
  window.addEventListener('pointerup', endPageBlockMove)
}

function onPageBlockMove(event) {
  pendingPageBlockMoveEvent = event
  if (pageBlockMoveFrame)
    return
  pageBlockMoveFrame = window.requestAnimationFrame(() => {
    pageBlockMoveFrame = 0
    const nextEvent = pendingPageBlockMoveEvent
    pendingPageBlockMoveEvent = null
    if (nextEvent)
      applyPageBlockMove(nextEvent)
  })
}

function applyPageBlockMove(event) {
  if (!pageBlockMoveCtx)
    return
  const ctx = pageBlockMoveCtx
  const padLeft = Number(ctx.canvasPad) || 24
  const padTop = Number(ctx.canvasPadTop) || 24
  let pageFlowX = Math.round(Math.max(padLeft, Math.min(ctx.maxX, ctx.originX + event.clientX - ctx.startX)))
  let pageFlowY = Math.round(Math.max(padTop, ctx.originY + event.clientY - ctx.startY))
  const snapped = snapPageBlockPosition(ctx, pageFlowX, pageFlowY)
  pageFlowX = Math.round(Math.max(padLeft, Math.min(ctx.maxX, snapped.x)))
  pageFlowY = Math.max(padTop, snapped.y)
  // 防止吸附后右边缘越界
  const maxRight = (ctx.maxX || 0) + Number(ctx.width || 0)
  if (pageFlowX + Number(ctx.width || 0) > maxRight)
    pageFlowX = Math.max(padLeft, maxRight - Number(ctx.width || 0))
  dragPreview.value = { ...dragPreview.value, x: pageFlowX, y: pageFlowY }
  updatePageAlignGuides(ctx, pageFlowX, pageFlowY)
  ctx.activeTabTarget = resolvePageFlowTabTargetFromPoint(event)
  ctx.activeGridTarget = resolvePageFlowGridCellTargetFromPoint(event)
  // 自由拖放：拖动中不做实时对调，避免其它元素跟着弹跳
  if (ctx.activeSwapTargetId)
    clearPageBlockSwapPreview(ctx)
}

function snapPageBlockPosition(ctx, x, y) {
  const width = Number(ctx.width || 0)
  const height = Number(ctx.height || 0)
  const moving = {
    left: x,
    top: y,
    right: x + width,
    bottom: y + height,
    cx: x + width / 2,
    cy: y + height / 2,
  }
  let nextX = x
  let nextY = y
  let bestX = null
  let bestY = null
  pageBlocks.value.forEach((item) => {
    if (!item?.id || item.id === ctx.blockId)
      return
    const style = resolvePageBlockShellStyle(item)
    const left = readPageBlockLength(style.left, 0)
    const top = readPageBlockLength(style.top, 0)
    const w = readPageBlockLength(item.props?.style?.pageFlowWidth, style.width) || width
    const h = readPageBlockLength(item.props?.style?.pageFlowHeight, style.height) || height
    const target = {
      left,
      top,
      right: left + w,
      bottom: top + h,
      cx: left + w / 2,
      cy: top + h / 2,
    }
    ;[
      [moving.left, target.left],
      [moving.left, target.right],
      [moving.left, target.cx],
      [moving.cx, target.left],
      [moving.cx, target.right],
      [moving.cx, target.cx],
      [moving.right, target.left],
      [moving.right, target.right],
      [moving.right, target.cx],
    ].forEach(([from, to]) => {
      const delta = Math.abs(from - to)
      if (delta > PAGE_ALIGN_SNAP_PX)
        return
      if (bestX && delta >= bestX.delta)
        return
      bestX = { delta, to: Math.round(to), nextX: Math.round(x + (to - from)) }
    })
    ;[
      [moving.top, target.top],
      [moving.top, target.bottom],
      [moving.top, target.cy],
      [moving.cy, target.top],
      [moving.cy, target.bottom],
      [moving.cy, target.cy],
      [moving.bottom, target.top],
      [moving.bottom, target.bottom],
      [moving.bottom, target.cy],
    ].forEach(([from, to]) => {
      const delta = Math.abs(from - to)
      if (delta > PAGE_ALIGN_SNAP_PX)
        return
      if (bestY && delta >= bestY.delta)
        return
      bestY = { delta, to: Math.round(to), nextY: Math.round(y + (to - from)) }
    })
  })
  if (bestX)
    nextX = bestX.nextX
  if (bestY)
    nextY = bestY.nextY
  // 每个轴最多一条吸附线，避免标线刷屏
  ctx._snapLines = {
    x: bestX ? [bestX.to] : [],
    y: bestY ? [bestY.to] : [],
  }
  return { x: nextX, y: nextY }
}

function updatePageAlignGuides(ctx, x, y) {
  const width = Number(ctx.width || 0)
  const height = Number(ctx.height || 0)
  pageAlignGuides.value = {
    visible: true,
    cross: {
      x: Math.round(x + width / 2),
      y: Math.round(y + height / 2),
    },
    x: ctx._snapLines?.x || [],
    y: ctx._snapLines?.y || [],
  }
}

function clearPageAlignGuides() {
  pageAlignGuides.value = { visible: false, cross: null, x: [], y: [] }
}

function endPageBlockMove(event) {
  if (pageBlockMoveFrame) {
    window.cancelAnimationFrame(pageBlockMoveFrame)
    pageBlockMoveFrame = 0
    pendingPageBlockMoveEvent = null
  }
  const ctx = pageBlockMoveCtx
  if (ctx) {
    const tabTarget = ctx.activeTabTarget
      || (event ? resolvePageFlowTabTargetFromPoint(event) : null)
    if (tabTarget && moveRootPageBlockToTab(ctx.blockId, tabTarget.blockId, tabTarget.tabKey)) {
      pageBlockMoveCtx = null
      draggingPageBlockId.value = ''
      dragPreview.value = null
      clearPageAlignGuides()
      window.removeEventListener('pointermove', onPageBlockMove)
      window.removeEventListener('pointerup', endPageBlockMove)
      return
    }
    const gridTarget = ctx.activeGridTarget
      || (event ? resolvePageFlowGridCellTargetFromPoint(event) : null)
    if (gridTarget && moveRootPageBlockToGridCell(ctx.blockId, gridTarget.blockId, gridTarget.cellKey)) {
      pageBlockMoveCtx = null
      draggingPageBlockId.value = ''
      dragPreview.value = null
      clearPageAlignGuides()
      window.removeEventListener('pointermove', onPageBlockMove)
      window.removeEventListener('pointerup', endPageBlockMove)
      return
    }
    if (ctx.activeSwapTargetId)
      clearPageBlockSwapPreview(ctx)
    const finalX = Math.round(dragPreview.value?.x ?? ctx.originX)
    const finalY = Math.round(dragPreview.value?.y ?? ctx.originY)
    const content = canvasToContentFlowPoint(finalX, finalY, pageCanvasPadding.value)
    // 直接落到预览位置，不再自动碰撞推挤，避免松手弹一下
    updatePageBlocks(
      pageBlocks.value.map(item => item.id === ctx.blockId
        ? {
            ...item,
            props: {
              ...(item.props || {}),
              style: {
                ...(item.props?.style || {}),
                pageFlowX: content.x,
                pageFlowY: content.y,
              },
            },
          }
        : item),
    )
  }
  pageBlockMoveCtx = null
  draggingPageBlockId.value = ''
  dragPreview.value = null
  clearPageAlignGuides()
  window.removeEventListener('pointermove', onPageBlockMove)
  window.removeEventListener('pointerup', endPageBlockMove)
}

function applyPageBlockSwapPreview(ctx, targetId) {
  const previousTargetId = ctx.activeSwapTargetId
  const targetNode = document.querySelector(`[data-page-block-id="${targetId}"]`)
  const previousTargetNode = previousTargetId ? document.querySelector(`[data-page-block-id="${previousTargetId}"]`) : null
  const targetRect = targetNode?.getBoundingClientRect?.()
  const previousTargetRect = previousTargetNode?.getBoundingClientRect?.()
  const originSlot = ctx.blockSlots.get(ctx.blockId)
  const previousTargetSlot = previousTargetId ? ctx.blockSlots.get(previousTargetId) : null
  if (!originSlot)
    return
  updatePageBlocks(pageBlocks.value.map((item) => {
    if (item.id === previousTargetId && previousTargetSlot) {
      return {
        ...item,
        props: {
          ...(item.props || {}),
          style: {
            ...(item.props?.style || {}),
            pageFlowX: previousTargetSlot.contentX ?? previousTargetSlot.x,
            pageFlowY: previousTargetSlot.contentY ?? previousTargetSlot.y,
          },
        },
      }
    }
    if (item.id === targetId) {
      return {
        ...item,
        props: {
          ...(item.props || {}),
          style: {
            ...(item.props?.style || {}),
            pageFlowX: originSlot.contentX ?? originSlot.x,
            pageFlowY: originSlot.contentY ?? originSlot.y,
          },
        },
      }
    }
    return item
  }))
  if (previousTargetId && previousTargetRect)
    animatePageBlockSwap(previousTargetId, previousTargetRect)
  if (targetRect)
    animatePageBlockSwap(targetId, targetRect)
  ctx.activeSwapTargetId = targetId
}

function clearPageBlockSwapPreview(ctx) {
  if (!ctx.activeSwapTargetId)
    return
  const targetId = ctx.activeSwapTargetId
  const targetNode = document.querySelector(`[data-page-block-id="${targetId}"]`)
  const targetRect = targetNode?.getBoundingClientRect?.()
  const targetSlot = ctx.blockSlots.get(targetId)
  if (targetSlot) {
    updatePageBlocks(pageBlocks.value.map(item => item.id === targetId
      ? {
          ...item,
          props: {
            ...(item.props || {}),
            style: {
              ...(item.props?.style || {}),
              pageFlowX: targetSlot.contentX ?? targetSlot.x,
              pageFlowY: targetSlot.contentY ?? targetSlot.y,
            },
          },
        }
      : item))
  }
  if (targetRect)
    animatePageBlockSwap(targetId, targetRect)
  ctx.activeSwapTargetId = ''
}

function animatePageBlockSwap(blockId, previousRect) {
  window.requestAnimationFrame(() => {
    const node = document.querySelector(`[data-page-block-id="${blockId}"]`)
    const nextRect = node?.getBoundingClientRect?.()
    if (!node || !nextRect)
      return
    const deltaX = previousRect.left - nextRect.left
    const deltaY = previousRect.top - nextRect.top
    if (Math.abs(deltaX) < 1 && Math.abs(deltaY) < 1)
      return
    node.animate([
      { transform: `translate(${deltaX}px, ${deltaY}px)` },
      { transform: 'translate(0, 0)' },
    ], {
      duration: 280,
      easing: 'cubic-bezier(0.22, 0.8, 0.24, 1)',
      fill: 'both',
    })
  })
}

function moveRootPageBlockToTab(blockId, containerId, tabKey) {
  const source = pageBlocks.value.find(item => item.id === blockId)
  const container = findPageBlockInTree(pageBlocks.value, containerId)
  if (!source || container?.blockType !== 'tabs' || source.id === container.id)
    return false
  const tabs = Array.isArray(container.props?.tabs) ? container.props.tabs : []
  if (!tabs.some(tab => tab.key === tabKey))
    return false
  const nested = normalizePageBlockForContainer(source)
  const withoutSource = pageBlocks.value.filter(item => item.id !== blockId)
  updatePageBlocks(mapPageBlocksInTree(withoutSource, item => item.id === containerId
    ? {
        ...item,
        props: {
          ...(item.props || {}),
          tabs: item.props.tabs.map(tab => tab.key === tabKey
            ? { ...tab, children: [...(tab.children || []), nested] }
            : tab),
        },
      }
    : item))
  selectedPageBlockId.value = containerId
  return true
}

function moveRootPageBlockToGridCell(blockId, containerId, cellKey) {
  const source = pageBlocks.value.find(item => item.id === blockId)
  const container = findPageBlockInTree(pageBlocks.value, containerId)
  if (!source || !container || source.id === container.id)
    return false
  if (['card', 'box-layout'].includes(container.blockType)) {
    if (['grid-layout', 'tabs', 'box-layout', 'card'].includes(source.blockType)) {
      message.warning('卡片/盒子内请放入普通组件，布局容器请放在画布根级')
      return false
    }
    const nested = normalizePageBlockForContainer(source)
    const withoutSource = pageBlocks.value.filter(item => item.id !== blockId)
    updatePageBlocks(mapPageBlocksInTree(withoutSource, item => item.id === containerId
      ? { ...item, children: [...(item.children || []), nested] }
      : item))
    selectedPageBlockId.value = nested.id
    message.success(container.blockType === 'card' ? '组件已放入卡片' : '组件已放入盒子')
    return true
  }
  if (container.blockType !== 'grid-layout')
    return false
  const cells = Array.isArray(container.props?.cells) ? container.props.cells : []
  let targetKey = String(cellKey || '')
  if (!cells.some(cell => String(cell.key) === targetKey))
    targetKey = String(cells[0]?.key || '')
  if (!targetKey)
    return false
  const nested = normalizePageBlockForContainer(source)
  const withoutSource = pageBlocks.value.filter(item => item.id !== blockId)
  updatePageBlocks(mapPageBlocksInTree(withoutSource, item => item.id === containerId
    ? {
        ...item,
        props: {
          ...(item.props || {}),
          cells: (item.props?.cells || []).map(cell => String(cell.key) === targetKey
            ? { ...cell, children: [...(cell.children || []), nested] }
            : cell),
        },
      }
    : item))
  selectedPageBlockId.value = nested.id
  message.success('组件已放入栅格')
  return true
}

function resolvePageBlockSwapTarget(blockId, movingRectOverride) {
  const movingNode = document.querySelector(`[data-page-block-id="${blockId}"]`)
  const movingRect = movingRectOverride || movingNode?.getBoundingClientRect?.()
  if (!movingRect)
    return ''
  let matchedId = ''
  let maxArea = 0
  document.querySelectorAll('[data-page-block-id]').forEach((node) => {
    const targetId = node.dataset.pageBlockId
    if (!targetId || targetId === blockId)
      return
    const rect = node.getBoundingClientRect()
    const overlapWidth = Math.max(0, Math.min(movingRect.right, rect.right) - Math.max(movingRect.left, rect.left))
    const overlapHeight = Math.max(0, Math.min(movingRect.bottom, rect.bottom) - Math.max(movingRect.top, rect.top))
    const area = overlapWidth * overlapHeight
    if (area > maxArea) {
      maxArea = area
      matchedId = targetId
    }
  })
  return maxArea >= 900 ? matchedId : ''
}

function startComponentButtonMove(event) {
  if (event.button !== 0)
    return
  const button = event.currentTarget
  const host = button.closest('.page-surface')
  const buttonRect = button.getBoundingClientRect()
  const hostRect = host?.getBoundingClientRect()
  if (!hostRect)
    return
  componentButtonMoveCtx.value = {
    host,
    button,
    startX: event.clientX,
    startY: event.clientY,
    originTop: buttonRect.top - hostRect.top,
    originLeft: buttonRect.left - hostRect.left,
  }
  window.addEventListener('pointermove', onComponentButtonMove)
  window.addEventListener('pointerup', endComponentButtonMove)
}

function onComponentButtonMove(event) {
  if (!componentButtonMoveCtx.value)
    return
  const nextTop = componentButtonMoveCtx.value.originTop + event.clientY - componentButtonMoveCtx.value.startY
  const nextLeft = componentButtonMoveCtx.value.originLeft + event.clientX - componentButtonMoveCtx.value.startX
  const hostRect = componentButtonMoveCtx.value.host?.getBoundingClientRect()
  const buttonRect = componentButtonMoveCtx.value.button?.getBoundingClientRect()
  const maxX = Math.max(12, (hostRect?.width || 0) - (buttonRect?.width || 44) - 12)
  const maxY = Math.max(12, (hostRect?.height || pageFlowHeight.value) - (buttonRect?.height || 44) - 12)
  componentButtonPosition.value = {
    x: Math.round(Math.max(12, Math.min(maxX, nextLeft))),
    y: Math.round(Math.max(12, Math.min(maxY, nextTop))),
  }
}

function endComponentButtonMove() {
  componentButtonMoveCtx.value = null
  window.removeEventListener('pointermove', onComponentButtonMove)
  window.removeEventListener('pointerup', endComponentButtonMove)
}

function createLegacyBlock(item, index) {
  const block = createGridBlock(legacyBlockTypeMap[item.componentKey] || 'info-panel', { fields: [] }, { gridX: index % 2 ? 6 : 0, gridY: Math.floor(index / 2) * 4 })
  if (!block)
    return null
  return { ...block, label: item.props?.title || item.label || block.label, props: { ...block.props, title: item.props?.title || item.label || block.label, subtitle: item.props?.description || item.props?.subtitle || item.props?.content || '' } }
}

async function saveActiveFormDesigner(returnAfter = true) {
  const context = activePageShapeDesign.value
  if (!context)
    return saveDraft()
  if (!application.value || !activeFormAsset.value || saving.value)
    return false
  const objectName = String(context.objectName || '').trim()
  const objectCode = String(context.objectCode || '').trim()
  if (!objectName) {
    message.warning('请填写数据表名称')
    return false
  }
  if (!/^[a-z]\w{1,47}$/i.test(objectCode)) {
    message.warning('数据表编码需以字母开头，仅含字母、数字和下划线（2-48 位）')
    return false
  }
  const designer = buildBusinessObjectDesignerPayloadFromFormAsset(
    activeFormAsset.value,
    activeFormFields.value.filter(field => field?.systemField !== true),
  )
  if (!designer.fields.length) {
    message.warning('请先从左侧添加至少一个字段组件')
    return false
  }
  saving.value = true
  try {
    syncActivePageShapeObject()
    const response = await designBusinessApplicationPage(application.value.id, {
      pageId: context.pageId,
      pageType: context.pageType,
      formAssetId: context.formAssetId,
      objectId: context.objectId || null,
      objectCode,
      objectName,
      runtimeDatasourceId: context.runtimeDatasourceId || null,
      createMode: context.createMode || '',
      importDatasourceId: context.importDatasourceId || context.runtimeDatasourceId || null,
      importTableName: context.importTableName || '',
      fields: designer.fields,
      formDesignerSchema: designer.formDesignerSchema,
      builder: builder.value,
    })
    const saved = response.data || {}
    if (saved.builder)
      builder.value = saved.builder
    resetBuilderHistory(builder.value)
    const pageId = saved.pageId || context.pageId
    if (activePageShapeDesign.value) {
      activePageShapeDesign.value = {
        ...activePageShapeDesign.value,
        pageId,
        objectId: saved.objectId || activePageShapeDesign.value.objectId || null,
        objectCode: saved.objectCode || objectCode,
        objectName: saved.objectName || objectName,
        createMode: '',
      }
    }
    await refreshWorkspaceMetadata({ syncBuilder: true, markClean: true })
    embeddedDesignerDirty.value = false
    // refreshWorkspaceMetadata 已 reset + bump portalCrudConfigRevision；
    // 编辑画布若仍打开再补一次预加载（返回页面管理时 preload 会因 !editing 直接跳过）
    preloadCurrentPageCrudRuntimeProps()
    // 设计器可能在保存回写后继续派生 pageSections，再对齐一次避免假脏
    if (dirty.value)
      await markBuilderClean()
    if (returnAfter) {
      formDesignerMode.value = false
      if (formDesignerFromPageManagement.value) {
        // 从页面管理视图进入的，保存后返回页面管理视图
        selectPageManagementNode(pageId)
        exitToPageManagement()
      }
      else {
        selectCreatedDesignerPage(pageId)
        syncActiveFormAssetForPage(pageId)
      }
    }
    message.success('页面、数据对象和字段已保存')
    return true
  }
  catch (error) {
    message.error(error?.message || '页面设计保存失败，请稍后重试')
    return false
  }
  finally {
    saving.value = false
  }
}

let navigationSaveTimer = null
let navigationSavePending = false

/**
 * 导航树操作（删除/排序/隐藏/系统菜单挂载等）后静默自动保存到草稿。
 * 不弹成功提示，失败时提示用户手动保存。
 * 防抖 300ms，避免连续操作多次调接口。
 */
function scheduleNavigationSave() {
  if (navigationSaveTimer)
    clearTimeout(navigationSaveTimer)
  navigationSaveTimer = setTimeout(() => {
    navigationSaveTimer = null
    void saveNavigationDraft()
  }, 300)
}

async function saveNavigationDraft() {
  if (!application.value || !dirty.value)
    return
  if (saving.value) {
    navigationSavePending = true
    return
  }
  saving.value = true
  try {
    await persistApplicationDraft()
    savedSignature.value = JSON.stringify(builder.value)
  }
  catch {
    message.error('导航配置保存失败，请点击"保存草稿"手动保存')
  }
  finally {
    saving.value = false
    if (navigationSavePending) {
      navigationSavePending = false
      void saveNavigationDraft()
    }
  }
}

async function saveDraft(options = {}) {
  if (!application.value || saving.value)
    return false
  saving.value = true
  let draftPersisted = false
  try {
    await persistApplicationDraft()
    draftPersisted = true
    const provisionSummary = await provisionPendingFormData()
    if (provisionSummary.builderChanged)
      await persistApplicationDraft()
    if (provisionSummary.succeeded > 0)
      await refreshWorkspaceMetadata({ syncBuilder: true, markClean: true })
    else
      await markBuilderClean()
    // refresh / 设计器回写后可能仍有一拍归一化差异，再对齐一次
    if (dirty.value)
      await markBuilderClean()
    if (!options.quiet) {
      if (provisionSummary.failed > 0) {
        message.warning(`表单草稿已保存；${provisionSummary.firstError || '数据存储暂未准备完成，可在数据配置中重试'}`)
      }
      else if (provisionSummary.succeeded > 0) {
        message.success('表单和数据存储已准备完成')
      }
      else {
        message.success('应用草稿已保存')
      }
    }
    return true
  }
  catch (error) {
    message.error(draftPersisted
      ? '表单已保存，但数据存储与页面连接未完整保存，请重试'
      : error?.message || '草稿保存失败，请稍后重试')
    return false
  }
  finally { saving.value = false }
}

async function persistApplicationDraft() {
  const options = mergeInAppBuilderOptions(application.value.options, builder.value)
  await updateBusinessApplication({
    id: application.value.id,
    applicationCode: application.value.applicationCode,
    applicationName: application.value.applicationName,
    suiteCode: application.value.suiteCode,
    icon: application.value.icon,
    description: application.value.description,
    status: application.value.status,
    options: JSON.stringify(options),
  })
  application.value.options = JSON.stringify(options)
}

async function provisionPendingFormData() {
  const targets = collectFormDataProvisionTargets(builder.value, objects.value)
  const summary = { succeeded: 0, failed: 0, builderChanged: false, firstError: '' }

  // 并行发起所有表单的 provision 请求，避免多表单串行叠加
  targets.forEach((target) => {
    setFormDataProvisionState(target.formAssetId, { status: 'preparing', message: '正在准备表单数据存储' })
  })
  const results = await Promise.allSettled(
    targets.map(target =>
      provisionBusinessApplicationFormData(application.value.id, target.request)
        .then(response => ({ target, response: response.data || {} }))
        .catch(error => ({ target, error })),
    ),
  )

  // 按原始顺序应用结果，避免并发写 builder 产生冲突
  for (const result of results) {
    if (result.status === 'fulfilled' && !result.value.error) {
      const { target, response: provisioned } = result.value
      const bound = bindProvisionedFormData(builder.value, target.formAssetId, provisioned)
      if (bound.changed) {
        builder.value = bound.schema
        summary.builderChanged = true
      }
      if (provisioned.unchanged) {
        setFormDataProvisionState(target.formAssetId, { status: 'ready', message: '表单数据无变化' })
        continue
      }
      summary.succeeded += 1
      if (provisioned.ddlWarning) {
        setFormDataProvisionState(target.formAssetId, { status: 'warning', message: provisioned.ddlWarning })
        summary.ddlWarnings = (summary.ddlWarnings || 0) + 1
      }
      else {
        setFormDataProvisionState(target.formAssetId, { status: 'ready', message: '表单数据已准备完成' })
      }
    }
    else {
      const { target, error } = result.status === 'fulfilled' ? result.value : { target: null, error: result.reason }
      const errorMessage = error?.message || '数据存储准备失败，请稍后重试'
      summary.failed += 1
      summary.firstError ||= errorMessage
      if (target?.formAssetId)
        setFormDataProvisionState(target.formAssetId, { status: 'error', message: errorMessage })
    }
  }
  return summary
}

function setFormDataProvisionState(formAssetId, state) {
  if (!formAssetId)
    return
  formDataProvisioningByAssetId.value = {
    ...formDataProvisioningByAssetId.value,
    [formAssetId]: state,
  }
}

function requestExitEditing() {
  if (currentDesignerDirty()) {
    exitEditingVisible.value = true
    return
  }
  exitToPageManagement()
}

function isDraftPreviewMode() {
  return isDraftMode.value && !editing.value
}

function resolveRuntimeBackLabel() {
  if (editing.value)
    return '页面管理'
  if (isDraftPreviewMode())
    return '返回编辑'
  return '应用中心'
}

function resolveRuntimeBrandEyebrow() {
  if (editing.value)
    return '页面设计'
  if (isDraftPreviewMode())
    return '草稿预览'
  return '页面管理'
}

function resolveRuntimeBrandStatus() {
  if (editing.value)
    return dirty.value ? '未保存修改' : '已保存到草稿'
  if (isDraftPreviewMode())
    return '只读预览'
  return currentPageManagementTitle.value
}

function handleRuntimeBack() {
  if (editing.value) {
    requestExitEditing()
    return
  }
  if (isDraftPreviewMode()) {
    returnToEditorFromDraftPreview()
    return
  }
  openWorkspace()
}

function returnToEditorFromDraftPreview() {
  const pageId = String(route.query.pageId || selectedNodeId.value || '').trim()
  // 预览若在新标签打开，优先关窗回到编辑页；同标签则恢复 edit=1
  if (window.opener && !window.opener.closed) {
    try {
      window.opener.focus?.()
    }
    catch {
      // ignore cross-origin focus errors
    }
    window.close()
    // 部分浏览器不允许脚本关窗，继续走路由回编辑
  }
  const nextQuery = { ...route.query, edit: '1' }
  delete nextQuery.draft
  delete nextQuery.returnEdit
  if (pageId)
    nextQuery.pageId = pageId
  router.replace({
    name: 'BusinessApplicationRuntime',
    params: { applicationCode: application.value?.applicationCode || route.params.applicationCode },
    query: nextQuery,
  })
}

function exitToPageManagement(options = {}) {
  formDesignerMode.value = false
  formDesignerFromPageManagement.value = false
  activePageShapeDesign.value = null
  activeFormAssetId.value = ''
  selectedPageBlockId.value = ''
  selectedDesignerResourceKey.value = ''
  configPanelVisible.value = false
  componentPopoverVisible.value = false
  activePageDesignTab.value = 'form'
  // 返回页面管理后仍高亮刚编辑的页，方便在左侧菜单里找到它
  const keepSelection = options.keepSelection !== false
  const highlightPageId = keepSelection
    ? (selectedNodeId.value || resolveSelectablePageId(route.query.pageId))
    : ''
  if (highlightPageId)
    selectedNodeId.value = highlightPageId
  editing.value = false
  const nextQuery = { ...route.query }
  delete nextQuery.edit
  delete nextQuery.designResource
  delete nextQuery.designTab
  delete nextQuery.designSection
  // 保留 pageId，左侧选中与中间预览一致；不要留 design* 设计态参数
  if (highlightPageId && !isPageManagementSystemPageId(highlightPageId))
    nextQuery.pageId = highlightPageId
  else
    delete nextQuery.pageId
  router.replace({ query: nextQuery })
}

function currentDesignerDirty() {
  if (pageBuilderResourceActive.value)
    return dirty.value
  return dirty.value || embeddedDesignerDirty.value
}

async function saveCurrentDesignerSection() {
  // 非编辑模式下（页面管理视图等），导航树操作只修改了 builder，直接保存草稿
  if (!editing.value && canEditApplication.value && dirty.value)
    return await saveDraft()
  if (editing.value && activePageDesignTab.value === 'form') {
    const formSaved = await saveActiveFormDesigner(false)
    if (!formSaved)
      return false
    // 表单保存后若本地归一化仍留下差异，静默落盘，避免连弹两次“保存成功”
    if (dirty.value) {
      const draftSaved = await saveDraft({ quiet: true })
      bumpPortalCrudConfigAfterDesignerSave()
      return draftSaved
    }
    bumpPortalCrudConfigAfterDesignerSave()
    return true
  }
  if (editing.value && activePageDesignTab.value === 'list') {
    if (!pageDesignObject.value)
      return false
    embeddedDesignerSaving.value = true
    try {
      const saved = await embeddedDesignerRef.value?.save?.()
      if (saved !== false)
        embeddedDesignerDirty.value = false
      if (dirty.value) {
        const draftSaved = await saveDraft({ quiet: saved !== false })
        bumpPortalCrudConfigAfterDesignerSave()
        return draftSaved
      }
      bumpPortalCrudConfigAfterDesignerSave()
      return saved !== false
    }
    finally {
      embeddedDesignerSaving.value = false
    }
  }
  if (pageBuilderResourceActive.value)
    return saveDraft()
  if (designerSection.value === 'settings') {
    if (dirty.value)
      return await saveDraft()
    return true
  }
  embeddedDesignerSaving.value = true
  try {
    const saved = await embeddedDesignerRef.value?.save?.()
    if (saved !== false)
      embeddedDesignerDirty.value = false
    if (dirty.value) {
      const draftSaved = await saveDraft({ quiet: saved !== false })
      bumpPortalCrudConfigAfterDesignerSave()
      return draftSaved
    }
    bumpPortalCrudConfigAfterDesignerSave()
    return saved !== false
  }
  finally {
    embeddedDesignerSaving.value = false
  }
}

/** 列表/表单草稿保存后立刻刷新门户 CRUD 预览，不必等到发布 */
function bumpPortalCrudConfigAfterDesignerSave() {
  resetRuntimeCrudConfig()
  portalCrudSeed.value = {}
  portalCrudConfigRevision.value += 1
  if (editing.value || isDraftMode.value)
    preloadCurrentPageCrudRuntimeProps()
}

function applyDesignerResource(resource) {
  if (!resource?.key)
    return
  selectedDesignerResourceKey.value = resource.key
  formDesignerMode.value = false
  embeddedDesignerDirty.value = false
  activeFlowContext.value = {}
  if (resource.kind === 'page-custom' && resource.pageId)
    selectNode(resource.pageId)
  router.replace({
    query: {
      ...route.query,
      designResource: resource.key,
      designSection: undefined,
      pageId: resource.kind === 'page-custom' ? resource.pageId : undefined,
      edit: '1',
    },
  })
}

function selectDesignerResource(resource) {
  if (!resource?.key || (resource.key === activeDesignerResource.value?.key && !formDesignerMode.value))
    return
  if (!currentDesignerDirty()) {
    applyDesignerResource(resource)
    return
  }
  if (!window.$dialog) {
    message.warning('请先保存当前设计，再切换功能')
    return
  }
  window.$dialog.warning({
    title: '保存当前设计',
    content: '当前功能存在未保存修改。保存后再切换，避免丢失配置。',
    positiveText: '保存并切换',
    negativeText: '留在当前',
    onPositiveClick: async () => {
      if (await saveCurrentDesignerSection())
        applyDesignerResource(resource)
    },
  })
}

function resolveResourceNodeBuilderNode(resource) {
  if (!resource?.pageId)
    return null
  return (builder.value?.nodes || []).find(item => String(item.id) === String(resource.pageId)) || null
}

function resolveResourceNodeMenuOptions(resource) {
  const node = resolveResourceNodeBuilderNode(resource)
  return node ? resolveNavigationMoreOptions(node) : []
}

function handleResourceNodeAction({ key, node } = {}) {
  const builderNode = resolveResourceNodeBuilderNode(node)
  if (!builderNode) {
    message.warning('该资源不支持结构编辑')
    return
  }
  handleNavigationMoreSelect(key, builderNode)
}

// 扩展面板里"打开对象动作设计"已并入业务流程画布，直接落到业务流程列表。
function openEmbeddedObjectActions() {
  const resource = designerResourceGroups.value
    .flatMap(group => group.nodes || [])
    .find(node => node.kind === 'automation-processes')
  if (resource)
    selectDesignerResource(resource)
}

// 流程列表面板：内嵌画布，避免路由跳转导致上下文丢失。
function openProcessDesigner(payload = {}) {
  const processId = String(payload?.processId || '')
  if (!processId)
    return
  embeddedProcessDesignerId.value = processId
  embeddedProcessDesignerVisible.value = true
}

async function handleEmbeddedProcessDesignerSaved() {
  embeddedProcessDesignerVisible.value = false
  await refreshWorkspaceMetadata()
}

// 流程列表面板的“应用发布”入口：跳转到独立发布页
function handleProcessPanelNavigate(section) {
  if (section === 'releases') {
    router.push({ name: 'BusinessApplicationPublish', params: { applicationCode: route.params.applicationCode } })
  }
}

async function handleExtensionsChanged() {
  await refreshWorkspaceMetadata()
}

async function handleEmbeddedDesignerSaved(savedSchema) {
  embeddedDesignerDirty.value = false
  if (activePageDesignTab.value === 'list')
    promoteCurrentPageToListShape(savedSchema)
  // 列表设计回写页面区块后可能弄脏 builder，这里一并落盘并清脏，避免退出仍提示未保存。
  if (dirty.value)
    await persistApplicationDraft()
  await refreshWorkspaceMetadata({ syncBuilder: true, markClean: true })
  if (dirty.value)
    await markBuilderClean()
}

/**
 * “表单页”创建时区块写入了 formOnly / showSearch:false 等表单形态锁死配置，
 * 且区块上的 showImport/searchFieldRefs 等快照会优先于运行时配置。
 * 用户在该页保存列表设计后，列表设计就是该页列表区块的事实来源：
 * 同步查询字段快照、解除形态限制并清除模板遗留的工具栏覆盖项，
 * 否则渲染页面会缺查询条件或批量操作，与列表设计结果不一致。
 */
function promoteCurrentPageToListShape(savedSchema = null) {
  const pageId = selectedNodeId.value
  if (!pageId)
    return
  const node = (builder.value?.nodes || []).find(item => item.id === pageId)
  const isFormShapeNode = node?.pageTemplate === 'form' || node?.objectRef?.pageMode === 'form'
  const savedZones = Array.isArray(savedSchema?.zones) ? savedSchema.zones : []
  const savedSearchZone = savedZones.find(zone => zone?.zoneKey === 'search')
  // 优先取本次列表设计保存的查询区配置，缓存的运行时配置仅作兜底（缓存可能落后于草稿）。
  const latestSearchRefs = (savedSearchZone && Array.isArray(savedSearchZone.fieldRefs)
    ? savedSearchZone.fieldRefs
    : (runtimeCrudPropsByObjectId.value[resolveRuntimeObjectCacheKey(node?.objectRef || {})]?.searchSchema || [])
        .map(field => field?.field))
    .map(fieldCode => String(fieldCode || '').trim())
    .filter(Boolean)
  const searchEnabled = savedSearchZone ? savedSearchZone.enabled !== false : true
  // 模板/早期快照遗留的覆盖项一律清除，交回运行时配置（列表设计编译结果）接管。
  const legacyOverrideKeys = ['formOnly', 'showImport', 'showExport', 'enableCustomQuery', 'hideAdd', 'hideToolbar', 'hideBatchDelete', 'hideSelection']
  let blockPromoted = false
  const items = mapPageBlocksInTree(pageBlocks.value, (block) => {
    if (block.blockType !== 'AiCrudPage')
      return block
    const blockProps = block.props || {}
    const hasSearchRefs = Array.isArray(blockProps.searchFieldRefs)
    const searchRefsChanged = latestSearchRefs.length > 0
      && (!hasSearchRefs
        || blockProps.searchFieldRefs.length !== latestSearchRefs.length
        || blockProps.searchFieldRefs.some(fieldCode => !latestSearchRefs.includes(String(fieldCode))))
    // 静态预览（previewLiveData=false）会禁用导入/导出/自定义查询等批量操作，
    // 列表页必须按实时数据预览渲染，才能和列表设计的工具栏配置一致。
    const needsLivePreview = blockProps.previewLiveData !== true
    const hasLegacyOverrides = legacyOverrideKeys.some(key => key in blockProps)
      || blockProps.showSearch !== searchEnabled
      || needsLivePreview
    if (!searchRefsChanged && !hasLegacyOverrides)
      return block
    blockPromoted = true
    const nextProps = {
      ...blockProps,
      showSearch: searchEnabled,
      previewLiveData: true,
      previewMode: 'realList',
      objectRef: blockProps.objectRef
        ? { ...blockProps.objectRef, pageKey: 'list', pageMode: 'list' }
        : blockProps.objectRef,
    }
    if (latestSearchRefs.length)
      nextProps.searchFieldRefs = latestSearchRefs
    else
      delete nextProps.searchFieldRefs
    for (const key of legacyOverrideKeys)
      delete nextProps[key]
    return { ...block, props: nextProps }
  })
  if (!blockPromoted && !isFormShapeNode)
    return
  if (blockPromoted)
    updatePageBlocks(items)
  if (isFormShapeNode) {
    builder.value = {
      ...builder.value,
      nodes: builder.value.nodes.map(item => item.id === pageId
        ? {
            ...item,
            pageTemplate: 'list',
            objectRef: item.objectRef
              ? { ...item.objectRef, pageKey: 'list', pageMode: 'list' }
              : item.objectRef,
          }
        : item),
    }
  }
  scheduleNavigationSave()
}

function handleRuntimeHeaderMoreSelect(key) {
  if (key === 'undo') {
    undoBuilder()
    return
  }
  if (key === 'redo') {
    redoBuilder()
    return
  }
  if (key === 'object-design') {
    openBusinessObjectDesign()
    return
  }
  if (key === 'exit-editing')
    requestExitEditing()
}

function discardAndExitEditing() {
  historyReady.value = false
  builder.value = ensurePageTitleComponents(normalizeInAppBuilder(application.value?.options, application.value, objects.value))
  builder.value = ensureWorkbenchPageInBuilder(builder.value).schema
  savedSignature.value = JSON.stringify(builder.value)
  resetBuilderHistory(builder.value)
  exitEditingVisible.value = false
  exitToPageManagement()
}

function openWorkspace() {
  router.push({ path: '/app-center' })
}

async function switchRuntimeView(view) {
  const next = resolveRuntimeView(view)
  // 发布功能已统一到独立发布页，runtime 不再内嵌发布面板
  if (String(view || '').toLowerCase() === 'publish') {
    router.push({ name: 'BusinessApplicationPublish', params: { applicationCode: route.params.applicationCode } })
    return
  }
  runtimeViewMode.value = next
  router.replace({
    query: {
      ...route.query,
      view: next === 'pages' ? undefined : next,
    },
  })
}

function resolveRuntimeView(value) {
  const normalized = String(Array.isArray(value) ? value[0] : value || '').toLowerCase()
  return ['pages', 'process', 'enhance', 'settings'].includes(normalized) ? normalized : 'pages'
}

// 页面级 Tab（编辑模式）——严格按创建时的页面形态展示
const PAGE_DESIGN_TABS = new Set(['page', 'form', 'list', 'settings', 'publish'])

/**
 * 创建时页面形态：
 * custom → 自由布局（仅页面设计）
 * form → 表单页（仅表单设计）
 * list → 列表页（仅列表设计）
 * list-form / tree-list / tree-table → 表单 + 列表设计
 */
function resolvePageShapeKey(pageId = '') {
  const routePageId = String(route.query.pageId || '').trim()
  const id = String(pageId || routePageId || selectedNodeId.value || '').trim()
  const designTab = readRouteDesignTab()
  if (isWorkbenchPageId(id) || isWorkbenchPageId(routePageId))
    return 'custom'
  // 编辑态 URL 明确是自由布局时，始终按 custom 展示（不因 selectedNodeId 漂移或首页回退误判）
  if (designTab === 'page' && (!routePageId || !id || routePageId === id))
    return 'custom'

  const node = (builder.value?.nodes || []).find(item => String(item.id) === id)
  return resolvePageShapeFromNode(node, { designTab: designTab === 'page' && routePageId === id ? 'page' : '' })
}

function pageUsesFreeLayoutCanvas(pageId) {
  return resolvePageShapeKey(pageId) === 'custom'
}

function resolveEntryDesignTab(pageId) {
  const shape = resolvePageShapeKey(pageId)
  if (shape === 'custom')
    return 'page'
  if (shape === 'list')
    return 'list'
  return 'form'
}

const FORM_DESIGN_SHAPES = new Set(['form', 'list-form', 'tree-list', 'tree-table'])
const LIST_DESIGN_SHAPES = new Set(['list', 'list-form', 'tree-list', 'tree-table'])

function resolvePageDesignTab(value, pageId = '') {
  const normalized = String(Array.isArray(value) ? value[0] : value || '').trim()
  const resolvedPageId = String(pageId || route.query.pageId || selectedNodeId.value || '').trim()
  const node = (builder.value?.nodes || []).find(item => String(item.id) === resolvedPageId)
  // 刷新时 nodes 尚未加载：先信任 URL designTab，避免 list 被误判成 page 落到空白对象卡
  if (!node && PAGE_DESIGN_TABS.has(normalized))
    return normalized

  const shape = resolvePageShapeKey(resolvedPageId)
  // 明确的 page Tab 不再被改写成 form（否则自由布局中间会空白）
  if (normalized === 'page')
    return shape === 'custom' || readRouteDesignTab() === 'page' ? 'page' : resolveEntryDesignTab(resolvedPageId)
  if (normalized === 'form' && !FORM_DESIGN_SHAPES.has(shape))
    return resolveEntryDesignTab(resolvedPageId)
  if (normalized === 'list' && !LIST_DESIGN_SHAPES.has(shape))
    return resolveEntryDesignTab(resolvedPageId)
  if (PAGE_DESIGN_TABS.has(normalized))
    return normalized
  return resolveEntryDesignTab(resolvedPageId)
}

const activePageDesignTab = ref(resolvePageDesignTab(route.query.designTab, route.query.pageId))

const currentPageShape = computed(() => {
  // 编辑态优先用 URL pageId，避免 selectedNodeId 短暂落在旧对象页时把 Tab 打成表单/列表
  const routeId = String(route.query.pageId || '').trim()
  if (editing.value && readRouteDesignTab() === 'page')
    return 'custom'
  return resolvePageShapeKey(routeId || selectedNodeId.value)
})
const isObjectBoundPage = computed(() => currentPageShape.value !== 'custom')
const isFreeLayoutPage = computed(() => currentPageShape.value === 'custom')
const showFreeLayoutCanvas = computed(() => {
  if (!editing.value || formDesignerMode.value)
    return false
  if (['list', 'settings', 'publish'].includes(activePageDesignTab.value))
    return false
  // URL 明确自由布局时，即使 activeTab 曾被写成 form 也要出画布
  if (readRouteDesignTab() === 'page')
    return activePageDesignTab.value !== 'form' || !showFormDesignTab.value
  if (activePageDesignTab.value === 'form' && showFormDesignTab.value)
    return false
  return isFreeLayoutPage.value || activePageDesignTab.value === 'page'
})
const showFormDesignWorkbench = computed(() => {
  if (formDesignerMode.value)
    return true
  if (!showFormDesignTab.value)
    return false
  return editing.value && activePageDesignTab.value === 'form'
})
const showPageDesignTab = computed(() => readRouteDesignTab() === 'page' || currentPageShape.value === 'custom')
const showFormDesignTab = computed(() => readRouteDesignTab() !== 'page' && FORM_DESIGN_SHAPES.has(currentPageShape.value))
const showListDesignTab = computed(() => readRouteDesignTab() !== 'page' && LIST_DESIGN_SHAPES.has(currentPageShape.value))
const isPageDesignTabActive = computed(() => showPageDesignTab.value && (activePageDesignTab.value === 'page' || showFreeLayoutCanvas.value))

function switchPageDesignTab(tab) {
  const next = resolvePageDesignTab(tab, selectedNodeId.value)
  activePageDesignTab.value = next
  if (formDesignerMode.value)
    formDesignerMode.value = false
  if (next === 'form' && selectedNodeId.value)
    syncActiveFormAssetForPage(selectedNodeId.value)
  const defaultTab = resolveEntryDesignTab(selectedNodeId.value)
  const designTab = next === defaultTab ? undefined : next
  const current = route.query.designTab == null || route.query.designTab === ''
    ? undefined
    : String(route.query.designTab)
  if (current === designTab)
    return
  router.replace({
    query: {
      ...route.query,
      designTab,
    },
  })
}

function resolveFormAssetIdForPage(pageId) {
  const page = builder.value?.pages?.[pageId]
  const items = page?.layout?.gridLayout?.items || []
  const fromBlock = findFirstBlockWithFormAssetId(items)
  if (fromBlock)
    return fromBlock
  const assets = builder.value?.formAssets || []
  if (!assets.length)
    return ''
  const node = builder.value?.nodes?.find(item => item.id === pageId)
  return assets.find(asset => asset.name === node?.title)?.id || assets[0].id || ''
}

function findFirstBlockWithFormAssetId(blocks = []) {
  for (const block of blocks || []) {
    const formAssetId = String(block?.props?.formAssetId || '').trim()
    if (formAssetId)
      return formAssetId
    const nested = [
      ...(block?.children || []),
      ...(block?.props?.tabs || []).flatMap(tab => tab?.children || []),
      ...(block?.props?.cells || []).flatMap(cell => cell?.children || []),
    ]
    const matched = findFirstBlockWithFormAssetId(nested)
    if (matched)
      return matched
  }
  return ''
}

function syncActiveFormAssetForPage(pageId) {
  const formAssetId = resolveFormAssetIdForPage(pageId)
  if (!formAssetId) {
    activeFormAssetId.value = ''
    activePageShapeDesign.value = null
    return
  }
  if (activeFormAssetId.value !== formAssetId)
    activeFormAssetId.value = formAssetId
  activePageShapeDesign.value = resolvePageShapeDesignContext(formAssetId)
}

function createFormAssetForCurrentPage() {
  if (!currentNode.value || !application.value)
    return
  const name = currentNode.value.title || '未命名表单'
  const objectRef = currentNode.value.objectRef || {}
  const result = createInAppFormAsset(builder.value, {
    name,
    formKey: objectRef.objectCode ? `${objectRef.objectCode}_form` : undefined,
    formDesignerSchema: createDefaultFormDesignerSchema({
      objectCode: objectRef.objectCode || application.value.applicationCode || 'application',
      objectName: objectRef.objectName || name,
      formName: name,
    }),
  })
  const page = result.schema.pages?.[currentNode.value.id]
  const items = page?.layout?.gridLayout?.items || []
  const nextItems = items.map((item) => {
    if (item?.blockType !== 'AiCrudPage' && item?.blockType !== 'AiForm')
      return item
    return {
      ...item,
      props: {
        ...(item.props || {}),
        formAssetId: result.formAssetId,
      },
    }
  })
  builder.value = {
    ...result.schema,
    pages: {
      ...result.schema.pages,
      [currentNode.value.id]: {
        ...page,
        layout: {
          ...(page?.layout || {}),
          gridLayout: {
            ...(page?.layout?.gridLayout || {}),
            items: nextItems,
          },
        },
      },
    },
  }
  activeFormAssetId.value = result.formAssetId
  activePageShapeDesign.value = resolvePageShapeDesignContext(result.formAssetId)
}

function patchCurrentPageNode(partial = {}) {
  if (!currentNode.value || !builder.value)
    return
  const pageId = currentNode.value.id
  const settingsFields = ['systemMenuVisible', 'navigationVisible', 'mountTarget', 'menuName', 'menuParentId', 'mobileMenuParentId', 'menuSort', 'printWatermark']
  builder.value = {
    ...builder.value,
    nodes: builder.value.nodes.map((item) => {
      if (item.id !== pageId)
        return item
      const next = { ...item, ...partial }
      const settingsPatch = {}
      for (const key of settingsFields) {
        if (Object.prototype.hasOwnProperty.call(partial, key)) {
          settingsPatch[key] = key === 'navigationVisible'
            ? partial[key] !== false
            : partial[key]
        }
      }
      if (Object.keys(settingsPatch).length > 0) {
        next.settings = {
          ...(next.settings || item.settings || {}),
          ...settingsPatch,
        }
      }
      return next
    }),
  }
  // 页面发布面板的挂载配置属于导航树草稿。自动保存可避免用户直接
  // 切换到“应用发布”时，发布服务仍读取旧的菜单挂载状态。
  scheduleNavigationSave()
}

async function restoreCurrentObjectPageLayout() {
  const pageId = String(currentNode.value?.id || selectedNodeId.value || route.query.pageId || '').trim()
  if (!pageId || !builder.value || restoringObjectPageLayout.value)
    return
  restoringObjectPageLayout.value = true
  try {
    builder.value = restoreObjectBoundPageLayout(builder.value, pageId)
    selectedPageBlockId.value = builder.value.pages?.[pageId]?.layout?.gridLayout?.items?.[0]?.id || ''
    syncActiveFormAssetForPage(pageId)
    await persistApplicationDraft()
    savedSignature.value = JSON.stringify(builder.value)
    message.success('已恢复为列表/表单布局，自由布局组件已清除')
  }
  catch (error) {
    message.error(error?.message || '恢复列表/表单布局失败')
  }
  finally {
    restoringObjectPageLayout.value = false
  }
}

function selectPageManagementNode(pageId) {
  if (isWorkbenchPageId(pageId) && builder.value) {
    const ensured = ensureWorkbenchPageInBuilder(builder.value)
    if (ensured.created || ensured.upgraded)
      builder.value = ensured.schema
  }
  selectedNodeId.value = pageId
  selectedDesignerResourceKey.value = ''
  if (editing.value)
    return
  // 页面管理态：选中与 URL pageId、中间预览必须同页，避免残留 design* 或旧 pageId 串预览
  const nextQuery = {
    pageId: pageId || undefined,
  }
  const currentPageId = route.query.pageId == null || route.query.pageId === ''
    ? undefined
    : String(route.query.pageId)
  const hasDesignerResidue = route.query.designResource != null
    || route.query.edit != null
    || route.query.designTab != null
    || route.query.designSection != null
    || route.query.draft != null
  if (!hasDesignerResidue && currentPageId === nextQuery.pageId)
    return
  router.replace({ query: nextQuery })
}

async function startRenameApplication() {
  renameApplicationValue.value = application.value?.applicationName || ''
  renamingApplication.value = true
  await nextTick()
  renameInputRef.value?.focus?.()
}

function cancelRenameApplication() {
  renamingApplication.value = false
  renameApplicationValue.value = ''
}

async function confirmRenameApplication() {
  const newName = String(renameApplicationValue.value || '').trim()
  if (!newName || !application.value) {
    cancelRenameApplication()
    return
  }
  if (newName === application.value.applicationName) {
    cancelRenameApplication()
    return
  }
  renameSaving.value = true
  try {
    await updateBusinessApplication({
      id: application.value.id,
      applicationCode: application.value.applicationCode,
      applicationName: newName,
      suiteCode: application.value.suiteCode,
      status: application.value.status,
      options: application.value.options,
    })
    application.value = { ...application.value, applicationName: newName }
    message.success('应用名称已修改')
    cancelRenameApplication()
    await refreshWorkspaceMetadata()
  }
  catch (error) {
    message.error(error?.message || '修改应用名称失败')
  }
  finally {
    renameSaving.value = false
  }
}

function enterPageDesign(pageId) {
  // 记录用户是否从页面管理视图（非编辑模式）进入
  formDesignerFromPageManagement.value = !editing.value
  selectedNodeId.value = pageId
  // 与左侧 Portal 预览对齐：有自由布局画布内容时进「页面设计」，纯对象 CRUD 才进表单设计
  const entryTab = resolveEntryDesignTab(pageId)
  activePageDesignTab.value = entryTab
  if (entryTab === 'form')
    syncActiveFormAssetForPage(pageId)
  // 所有状态（编辑模式、选中页面、设计资源）统一通过一次路由更新驱动：
  // watch(route.query.edit) 设置 editing，watch(route.query.pageId) 设置 selectedNodeId，
  // watch(route.query.designResource) 设置 selectedDesignerResourceKey。
  // 避免在路由生效前同步修改这些 ref 触发 watch 产生不带 edit 的并发 router.replace。
  router.replace({
    query: {
      ...route.query,
      pageId,
      edit: '1',
      designResource: `page-custom:${pageId}`,
      designTab: entryTab === 'form' ? undefined : entryTab,
    },
  })
  // 进入自由布局时立刻回写 pageShape，避免刷新后再次误判
  if (entryTab === 'page' && builder.value && !isWorkbenchPageId(pageId)) {
    const node = (builder.value.nodes || []).find(item => String(item.id) === String(pageId))
    const repaired = ensureFreeLayoutPageNode(node)
    if (node && repaired !== node) {
      builder.value = {
        ...builder.value,
        nodes: builder.value.nodes.map(item => String(item.id) === String(pageId) ? repaired : item),
      }
    }
  }
}

function enterWorkbenchDesign() {
  if (!builder.value)
    return
  const ensured = ensureWorkbenchPageInBuilder(builder.value)
  if (ensured.created || ensured.upgraded) {
    builder.value = ensured.schema
    if (ensured.created)
      message.success('已初始化个人工作台默认布局')
    else if (ensured.upgraded)
      message.success('已更新个人工作台默认布局')
  }
  enterPageDesign(WORKBENCH_PAGE_ID)
}

function openCustomPageSelector() {
  openPageTypeSelector(null, 'custom')
}

async function openExcelPageImport() {
  if (!application.value?.id)
    return
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.xlsx,.xls'
  input.addEventListener('change', async () => {
    const file = input.files?.[0]
    if (!file)
      return
    saving.value = true
    try {
      await previewBusinessApplicationExcel(file)
      await initializeBusinessApplicationExcel(application.value.id, file)
      await refreshWorkspaceMetadata()
      message.success('已根据 Excel 生成数据对象，请选择页面形态继续设计')
      openPageTypeSelector()
    }
    catch (error) {
      message.error(error?.message || '从 Excel 创建页面失败')
    }
    finally {
      saving.value = false
    }
  })
  input.click()
}

function openFormAssetDesignerForPage(pageId) {
  const page = builder.value?.pages?.[pageId]
  const formAssetId = page?.layout?.gridLayout?.items?.find(item => item?.props?.formAssetId)?.props?.formAssetId
  if (formAssetId)
    openFormAssetDesigner(formAssetId)
}

async function openDraftPreview() {
  if (dirty.value && !await saveDraft())
    return
  // 自由编排页面走应用运行壳；对象页面/数据结构落到对象自身的 CRUD 运行页预览。
  if (!pageBuilderResourceActive.value && previewableResourceActive.value) {
    openObjectResourcePreview()
    return
  }
  const selected = String(selectedNodeId.value || '').trim()
  const routeId = String(route.query.pageId || '').trim()
  const resourcePageId = String(activeDesignerResource.value?.pageId || '').trim()
  const previewPageId = (isPageManagementSystemPageId(resourcePageId) && resourcePageId)
    || (isPageManagementSystemPageId(selected) && selected)
    || routeId
    || resolveActiveDesignerPageId()
    || selected
    || WORKBENCH_PAGE_ID
  const target = router.resolve({
    name: 'BusinessApplicationRuntime',
    params: { applicationCode: application.value.applicationCode },
    query: { pageId: previewPageId, draft: '1', returnEdit: '1' },
  })
  const win = window.open(target.href, '_blank')
  if (!win) {
    // 浏览器弹窗拦截器阻止了新窗口，回退到同标签页导航
    console.warn('[openDraftPreview] window.open 被浏览器拦截，回退到 router.push')
    router.push(target)
  }
}

function openObjectResourcePreview() {
  const configKey = activeResourceConfigKey.value
  if (!configKey) {
    message.warning('该业务对象还没有可用的运行配置')
    return
  }
  const kind = activeDesignerResource.value?.kind || ''
  const formMode = kind === 'page-form'
  const query = { designPreview: '1' }
  if (formMode) {
    query.runtimeOpenMode = 'CREATE_FORM'
    query.pageKey = 'create'
    query.mode = 'create'
  }
  else {
    query.runtimeOpenMode = 'LIST'
    query.pageKey = 'list'
  }
  const target = router.resolve({ path: `/ai/crud-page/${configKey}`, query })
  const win = window.open(target.href, '_blank', 'noopener,noreferrer')
  if (!win) {
    // 浏览器弹窗拦截器阻止了新窗口，回退到同标签页导航
    console.warn('[openObjectResourcePreview] window.open 被浏览器拦截，回退到 router.push')
    router.push(target)
  }
}

function openObjectDesigner(panel = 'list', targetObjectRef) {
  const objectRef = targetObjectRef || currentNode.value?.objectRef || selectedPageBlockRuntimeObjectRef.value
  const target = resolveObjectDesignerNavigationTarget(objectRef, objects.value)
  if (!target?.objectCode)
    return
  const detailTab = panel === 'detail' ? 'detail' : panel === 'form' ? 'form' : 'list'
  router.push({
    name: 'BusinessObjectDesigner',
    params: { objectCode: target.objectCode },
    query: {
      objectId: target.objectId,
      panel,
      detailTab,
      returnTo: route.fullPath,
    },
  })
}

function openBusinessObjectDesign() {
  const objectRef = currentNode.value?.objectRef || selectedPageBlockRuntimeObjectRef.value
  if (resolveObjectDesignerNavigationTarget(objectRef, objects.value)?.objectCode) {
    openObjectDesigner('fields', objectRef)
    return
  }
  openObjectSetup()
}

async function openObjectSetup() {
  if (dirty.value && !await saveDraft())
    return
  objectSetupVisible.value = true
}

async function handleApplicationObjectsChanged(change = null) {
  const shouldBindCurrentCrud = Boolean(
    change?.objectId
    && selectedPageBlock.value?.blockType === 'AiCrudPage'
    && !selectedPageBlockRuntimeObjectRef.value,
  )
  await refreshWorkspaceMetadata()
  if (shouldBindCurrentCrud) {
    updateSelectedPageBlockRuntimeObject(change.objectId)
    objectSetupVisible.value = false
  }
}

async function refreshWorkspaceMetadata(options = {}) {
  const code = route.params.applicationCode
  if (!code)
    return
  const syncBuilder = options.syncBuilder === true
    || (options.syncBuilder !== false && !dirty.value)
  const markClean = options.markClean === true
    || (options.markClean !== false && !dirty.value)
  const response = await businessApplicationWorkspaceByCode(code)
  const workspace = response.data || {}
  application.value = workspace.application || application.value
  objects.value = workspace.objects || []
  workspaceExtensions.value = workspace.extensions || []
  workspaceEntries.value = workspace.entries || []
  // 切 edit 不再整页 load 后，这里必须把 workspace 草稿同步回 builder。
  // 否则页面管理仍渲染内存里未规范化的布局（例如落成默认「提示信息」面板）。
  if (application.value && syncBuilder) {
    builder.value = ensurePageTitleComponents(
      normalizeInAppBuilder(application.value.options, application.value, objects.value),
    )
    resetBuilderHistory(builder.value)
    bindSingleFormToCompatibleBlocks()
  }
  resetRuntimeCrudConfig()
  // 草稿配置已变：通知页面管理 Portal 丢掉本地 CRUD 缓存并重新拉 render
  portalCrudSeed.value = {}
  portalCrudConfigRevision.value += 1
  hydratePageCrudApiPlaceholders()
  // bind/hydrate 可能继续改 builder；签名必须在全部本地归一化之后再落，否则保存后仍显示未保存。
  if (markClean)
    await markBuilderClean()
  else
    await nextTick()
  preloadCurrentPageCrudRuntimeProps()
  // preload 触发的对象上下文加载不改 builder；若仍有同步归一化尾差再对齐一次
  if (markClean && dirty.value)
    await markBuilderClean()
}
</script>

<style scoped src="./runtime-modules/application-runtime.css"></style>
