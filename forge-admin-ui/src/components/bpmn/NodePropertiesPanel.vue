<template>
  <div class="node-properties-panel">
    <!-- 用户选择弹窗 -->
    <UserSelectModal
      v-model:show="showUserSelect"
      :title="userSelectTitle"
      :multiple="userSelectMultiple"
      :selected-users="currentSelectedUsers"
      @confirm="handleUserSelectConfirm"
    />

    <!-- 角色选择弹窗 -->
    <n-modal
      v-model:show="showRoleSelect"
      preset="card"
      title="选择角色"
      style="width: 600px"
      :mask-closable="false"
    >
      <n-data-table
        :columns="roleColumns"
        :data="roleList"
        :loading="roleLoading"
        :row-key="row => row.id"
        :checked-row-keys="checkedRoleKeys"
        @update:checked-row-keys="handleRoleCheck"
      />
      <template #footer>
        <n-space justify="end">
          <n-button @click="showRoleSelect = false">
            取消
          </n-button>
          <n-button type="primary" @click="handleRoleConfirm">
            确定
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <Teleport to="body">
      <n-modal
        v-model:show="showNodeFormDesigner"
        preset="card"
        :title="nodeFormDesignerTitle"
        style="width: min(1180px, 96vw); height: 90vh"
        content-style="height: calc(90vh - 66px); padding: 16px;"
        :mask-closable="false"
      >
        <FlowFormCreateDesigner
          ref="nodeFormDesignerRef"
          v-model="nodeFormDesignerSchema"
          height="calc(90vh - 156px)"
          @save="handleSaveNodeFormSchema"
        />
      </n-modal>
    </Teleport>

    <Teleport to="body">
      <n-modal
        v-model:show="showNodeFormPreview"
        preset="card"
        :title="nodeFormPreviewTitle"
        style="width: min(780px, 92vw)"
      >
        <FlowFormCreateRenderer
          :schema="nodeFormPreviewSchema"
          read-only
        />
      </n-modal>
    </Teleport>

    <!-- Tab分隔配置 - 横向滚动 -->
    <div ref="tabsWrapperRef" class="tabs-wrapper">
      <div class="tabs-toolbar">
        <n-button
          quaternary
          circle
          size="small"
          :disabled="!canGoPrevTab"
          aria-label="上一个配置页签"
          @click="switchRelativeTab(-1)"
        >
          <template #icon>
            <i class="i-material-symbols:chevron-left" />
          </template>
        </n-button>
        <span class="tabs-position">{{ activeTabPositionText }}</span>
        <n-button
          quaternary
          circle
          size="small"
          :disabled="!canGoNextTab"
          aria-label="下一个配置页签"
          @click="switchRelativeTab(1)"
        >
          <template #icon>
            <i class="i-material-symbols:chevron-right" />
          </template>
        </n-button>
      </div>
      <div class="tabs-shell" :class="{ 'has-next': canGoNextTab }">
        <n-tabs
          v-model:value="activeTab"
          type="line"
          size="small"
          class="config-tabs"
        >
          <!-- 基础属性Tab -->
          <n-tab-pane name="basic" tab="基础属性">
            <n-form :model="properties" label-placement="top" size="small">
              <n-form-item label="节点ID">
                <n-input v-model:value="properties.id" placeholder="请输入节点ID" @input="markDirty" />
              </n-form-item>
              <n-form-item label="节点名称">
                <n-input v-model:value="properties.name" placeholder="请输入节点名称" @input="markDirty" />
              </n-form-item>
              <n-form-item label="节点描述">
                <n-input
                  v-model:value="properties.documentation"
                  type="textarea"
                  :rows="2"
                  placeholder="请输入节点描述"
                  @input="markDirty"
                />
              </n-form-item>
            </n-form>
          </n-tab-pane>

          <!-- 开始节点配置Tab -->
          <n-tab-pane v-if="elementType === 'bpmn:StartEvent'" name="startConfig" tab="开始配置">
            <n-form :model="properties" label-placement="top" size="small">
              <n-form-item label="发起人变量">
                <n-input
                  v-model:value="properties.initiator"
                  placeholder="默认: initiator"
                  @input="markDirty"
                />
              </n-form-item>
              <n-form-item label="表单Key">
                <n-input
                  v-model:value="properties.formKey"
                  placeholder="表单标识"
                  @input="markDirty"
                />
              </n-form-item>
            </n-form>
          </n-tab-pane>

          <!-- 审批设置Tab（仅用户任务） -->
          <n-tab-pane v-if="elementType === 'bpmn:UserTask'" name="approval" tab="审批设置">
            <n-form :model="properties" label-placement="top" size="small">
              <n-form-item label="任务类型">
                <n-select
                  v-model:value="properties.taskType"
                  :options="taskTypeOptions"
                  @update:value="updateTaskType"
                />
              </n-form-item>

              <!-- 指定审批人 -->
              <template v-if="properties.taskType === 'assignee'">
                <n-form-item label="审批人">
                  <n-space vertical size="small" style="width: 100%">
                    <n-select
                      v-model:value="properties.assignee"
                      :options="assigneeOptions"
                      placeholder="选择审批人类型"
                      filterable
                      tag
                      @update:value="updateUserTaskAssignee"
                    />
                    <div v-if="formVariableOptions.length > 0" class="field-catalog-tip">
                      可直接选择绑定表单中的用户、部门或负责人字段作为审批人变量。
                    </div>
                    <n-button
                      v-if="properties.assignee === 'custom'"
                      type="primary"
                      dashed
                      block
                      @click="openUserSelect('assignee')"
                    >
                      <template #icon>
                        <i class="i-material-symbols:person-add" />
                      </template>
                      从用户列表选择
                    </n-button>
                  </n-space>
                </n-form-item>

                <!-- SPEL 表达式配置 -->
                <template v-if="properties.assignee === 'spel'">
                  <n-form-item label="表达式模板">
                    <n-select
                      v-model:value="selectedSpelTemplate"
                      :options="spelTemplatesFromApi"
                      placeholder="选择常用表达式模板（可选）"
                      clearable
                      @update:value="applySpelTemplate"
                    >
                      <template #option="{ option }">
                        <div>
                          <div style="font-weight: 500">
                            {{ option.label }}
                          </div>
                          <div v-if="option.description" style="font-size: 12px; color: #999; margin-top: 4px">
                            {{ option.description }}
                          </div>
                        </div>
                      </template>
                    </n-select>
                  </n-form-item>

                  <n-form-item label="SPEL 表达式">
                    <n-space vertical size="small" style="width: 100%">
                      <n-select
                        v-model:value="selectedSpelVariable"
                        :options="variableCatalogOptions"
                        placeholder="插入表单字段或系统变量"
                        filterable
                        clearable
                        @update:value="insertSpelVariable"
                      />
                      <n-input
                        v-model:value="properties.assigneeExpr"
                        type="textarea"
                        placeholder="输入 SPEL 表达式，例如：${userService.findContactByRegion(execution.getVariable('regionCode'))}"
                        :autosize="{ minRows: 3, maxRows: 6 }"
                        @blur="validateSpelExpression"
                      />
                      <n-alert
                        v-if="spelValidationError"
                        type="error"
                        :title="spelValidationError"
                        closable
                        @close="spelValidationError = ''"
                      />
                      <n-collapse>
                        <n-collapse-item title="表达式语法提示" name="help">
                          <n-space vertical size="small">
                            <div style="font-size: 13px; line-height: 1.6">
                              <p><strong>可用对象：</strong></p>
                              <ul style="margin: 8px 0; padding-left: 20px">
                                <li><code>execution</code> - 流程执行上下文</li>
                                <li><code>userService</code> - 用户查询服务</li>
                                <li><code>deptService</code> - 部门查询服务</li>
                                <li><code>roleService</code> - 角色查询服务</li>
                              </ul>
                              <p><strong>常用方法：</strong></p>
                              <ul style="margin: 8px 0; padding-left: 20px">
                                <li><code>execution.getVariable("key")</code> - 获取流程变量</li>
                                <li><code>userService.findById(userId)</code> - 根据ID查找用户</li>
                                <li><code>userService.findByRole(roleKey)</code> - 根据角色查找用户</li>
                                <li><code>deptService.findManager(deptId)</code> - 查找部门负责人</li>
                              </ul>
                              <p><strong>示例：</strong></p>
                              <ul style="margin: 8px 0; padding-left: 20px">
                                <li>条件判断：<code>${amount > 10000 ? 'manager' : 'staff'}</code></li>
                                <li>方法调用：<code>${userService.findContactByRegion(regionCode)}</code></li>
                                <li>链式调用：<code>${deptService.findById(deptId).getManager()}</code></li>
                              </ul>
                            </div>
                          </n-space>
                        </n-collapse-item>
                      </n-collapse>
                    </n-space>
                  </n-form-item>
                </template>

                <n-form-item v-if="properties.assigneeUserName" label="已选用户">
                  <n-tag type="info" closable @close="clearAssigneeUser">
                    {{ properties.assigneeUserName }}
                  </n-tag>
                </n-form-item>
              </template>

              <!-- 候选用户 -->
              <template v-if="properties.taskType === 'candidateUsers'">
                <n-form-item label="候选用户">
                  <n-space vertical size="small" style="width: 100%">
                    <n-select
                      v-if="formVariableOptions.length > 0"
                      :value="null"
                      :options="variableCatalogOptions"
                      placeholder="选择表单字段作为候选用户变量"
                      filterable
                      clearable
                      @update:value="field => applyCandidateVariable('users', field)"
                    />
                    <n-button
                      type="primary"
                      dashed
                      block
                      @click="openUserSelect('candidateUsers')"
                    >
                      <template #icon>
                        <i class="i-material-symbols:group-add" />
                      </template>
                      从用户列表选择
                    </n-button>
                    <div v-if="properties.candidateUserNames.length > 0" style="margin-top: 8px">
                      <n-tag
                        v-for="(name, index) in properties.candidateUserNames"
                        :key="index"
                        type="info"
                        closable
                        style="margin: 2px"
                        @close="removeCandidateUser(index)"
                      >
                        {{ name }}
                      </n-tag>
                    </div>
                  </n-space>
                </n-form-item>
              </template>

              <!-- 候选组 -->
              <template v-if="properties.taskType === 'candidateGroups'">
                <n-form-item label="候选组(角色)">
                  <n-space vertical size="small" style="width: 100%">
                    <n-select
                      v-if="formVariableOptions.length > 0"
                      :value="null"
                      :options="variableCatalogOptions"
                      placeholder="选择表单字段作为候选组变量"
                      filterable
                      clearable
                      @update:value="field => applyCandidateVariable('groups', field)"
                    />
                    <n-button
                      type="primary"
                      dashed
                      block
                      @click="openRoleSelect"
                    >
                      <template #icon>
                        <i class="i-material-symbols:shield" />
                      </template>
                      从角色列表选择
                    </n-button>
                    <div v-if="properties.candidateGroupNames.length > 0" style="margin-top: 8px">
                      <n-tag
                        v-for="(name, index) in properties.candidateGroupNames"
                        :key="index"
                        type="success"
                        closable
                        style="margin: 2px"
                        @close="removeCandidateGroup(index)"
                      >
                        {{ name }}
                      </n-tag>
                    </div>
                  </n-space>
                </n-form-item>
              </template>

              <!-- 表单配置 -->
              <n-form-item label="表单类型">
                <n-select
                  v-model:value="properties.formType"
                  :options="formTypeOptions"
                  @update:value="updateFormType"
                />
              </n-form-item>

              <template v-if="properties.formType === 'dynamic'">
                <n-form-item label="引用表单">
                  <n-select
                    v-model:value="properties.formKey"
                    :options="formDefinitionOptions"
                    :loading="formDefinitionLoading"
                    placeholder="选择已有表单或输入表单Key"
                    clearable
                    filterable
                    tag
                    @update:value="handleFormKeyChange"
                  />
                </n-form-item>

                <n-form-item label="节点表单">
                  <div class="node-form-builder-card">
                    <div class="node-form-builder-main">
                      <div class="node-form-builder-icon">
                        <i class="i-material-symbols:edit-document" />
                      </div>
                      <div class="node-form-builder-copy">
                        <div class="node-form-builder-title">
                          {{ nodeFormBuilderTitle }}
                        </div>
                        <div class="node-form-builder-desc">
                          {{ nodeFormBuilderDesc }}
                        </div>
                      </div>
                    </div>
                    <n-space size="small" class="node-form-builder-actions">
                      <n-button size="small" type="primary" @click="openNodeFormDesigner">
                        <template #icon>
                          <i class="i-material-symbols:edit-document" />
                        </template>
                        在线设计
                      </n-button>
                      <n-button
                        size="small"
                        :disabled="!canPreviewNodeForm"
                        @click="openNodeFormPreview"
                      >
                        <template #icon>
                          <i class="i-material-symbols:visibility" />
                        </template>
                        预览
                      </n-button>
                      <n-button
                        size="small"
                        :disabled="!properties.formJson"
                        @click="clearNodeInlineForm"
                      >
                        <template #icon>
                          <i class="i-material-symbols:delete" />
                        </template>
                        清空设计
                      </n-button>
                    </n-space>
                  </div>
                </n-form-item>

                <n-collapse class="node-form-json-collapse">
                  <n-collapse-item title="高级 JSON 配置" name="formJson">
                    <n-form-item label="表单JSON">
                      <n-input
                        v-model:value="properties.formJson"
                        type="textarea"
                        :autosize="{ minRows: 4, maxRows: 10 }"
                        placeholder="由在线设计器自动生成；仅开发调试时手动修改"
                        @input="markDirty"
                      />
                    </n-form-item>
                  </n-collapse-item>
                </n-collapse>
              </template>

              <template v-if="properties.formType === 'external'">
                <n-form-item label="表单URL">
                  <n-input
                    v-model:value="properties.formUrl"
                    placeholder="外部表单URL"
                    @input="markDirty"
                  />
                </n-form-item>
              </template>

              <!-- 优先级 -->
              <n-form-item label="优先级">
                <n-slider
                  v-model:value="properties.priority"
                  :min="0"
                  :max="100"
                  :step="10"
                  @update:value="updateExtensionProperty('priority')"
                />
              </n-form-item>

              <!-- 截止日期 -->
              <n-form-item label="截止日期(天)">
                <n-input-number
                  v-model:value="properties.dueDate"
                  :min="0"
                  placeholder="0表示不限制"
                  @update:value="markDirty"
                />
              </n-form-item>
            </n-form>
          </n-tab-pane>

          <!-- 办理控制Tab -->
          <n-tab-pane v-if="elementType === 'bpmn:UserTask'" name="actionControl" tab="办理控制">
            <n-form :model="properties" label-placement="top" size="small">
              <div class="action-control-group">
                <div class="action-control-title">
                  可执行动作
                </div>
                <div class="action-switch-list">
                  <div
                    v-for="item in approvalActionOptions"
                    :key="item.key"
                    class="action-switch-row"
                  >
                    <div class="action-switch-main">
                      <i :class="item.icon" />
                      <span>{{ item.label }}</span>
                    </div>
                    <n-switch
                      v-model:value="properties[item.key]"
                      size="small"
                      @update:value="markDirty"
                    />
                  </div>
                </div>
              </div>

              <div class="action-control-group">
                <div class="action-control-title">
                  办理要求
                </div>
                <div class="action-switch-list">
                  <div class="action-switch-row">
                    <div class="action-switch-main">
                      <i class="i-material-symbols:rate-review" />
                      <span>审批意见必填</span>
                    </div>
                    <n-switch
                      v-model:value="properties.requireComment"
                      size="small"
                      @update:value="markDirty"
                    />
                  </div>
                  <div class="action-switch-row">
                    <div class="action-switch-main">
                      <i class="i-material-symbols:draw" />
                      <span>签名必填</span>
                    </div>
                    <n-switch
                      v-model:value="properties.requireSignature"
                      size="small"
                      @update:value="markDirty"
                    />
                  </div>
                </div>
              </div>
            </n-form>
          </n-tab-pane>

          <!-- 会签配置Tab -->
          <n-tab-pane v-if="elementType === 'bpmn:UserTask'" name="multiInstance" tab="会签配置">
            <n-form :model="properties" label-placement="top" size="small">
              <n-form-item label="多人审批方式">
                <n-radio-group v-model:value="properties.multiInstanceType" @update:value="updateMultiInstance">
                  <n-radio-button value="none">
                    单人审批
                  </n-radio-button>
                  <n-radio-button value="parallel">
                    并行会签
                  </n-radio-button>
                  <n-radio-button value="sequential">
                    依次审批
                  </n-radio-button>
                </n-radio-group>
              </n-form-item>

              <template v-if="properties.multiInstanceType !== 'none'">
                <n-form-item label="完成条件">
                  <n-select
                    v-model:value="properties.completionCondition"
                    :options="completionConditionOptions"
                    @update:value="updateMultiInstance"
                  />
                </n-form-item>

                <n-form-item v-if="properties.completionCondition === 'rate'" label="通过比例">
                  <div class="pass-rate-config">
                    <!-- 预设快选 -->
                    <div class="pass-rate-presets">
                      <n-button-group size="tiny">
                        <n-button
                          v-for="preset in passRatePresets"
                          :key="preset.value"
                          :type="properties.passRate === preset.value ? 'primary' : 'default'"
                          @click="setPassRate(preset.value)"
                        >
                          {{ preset.label }}
                        </n-button>
                      </n-button-group>
                    </div>
                    <!-- 滑块 + 精确输入 -->
                    <div class="pass-rate-slider-row">
                      <n-slider
                        v-model:value="properties.passRate"
                        :min="10"
                        :max="100"
                        :step="1"
                        :marks="passRateMarks"
                        :format-tooltip="v => `${v}%`"
                        style="flex: 1"
                        @update:value="updateMultiInstance"
                      />
                      <n-input-number
                        v-model:value="properties.passRate"
                        :min="10"
                        :max="100"
                        size="small"
                        style="width: 82px; flex-shrink: 0"
                        @update:value="updateMultiInstance"
                      >
                        <template #suffix>
                          %
                        </template>
                      </n-input-number>
                    </div>
                    <!-- 描述文字 -->
                    <div class="pass-rate-desc">
                      <i class="i-material-symbols:info-outline" style="color:#2080f0;margin-right:4px" />
                      {{ passRateDesc }}
                    </div>
                  </div>
                </n-form-item>
              </template>
            </n-form>
          </n-tab-pane>

          <!-- 任务监听器Tab -->
          <n-tab-pane v-if="elementType === 'bpmn:UserTask'" name="listener" tab="监听器">
            <div class="listener-list">
              <div v-for="(listener, index) in properties.taskListeners" :key="index" class="listener-item">
                <n-card size="small" :title="listener.event">
                  <template #header-extra>
                    <n-button text type="error" @click="removeTaskListener(index)">
                      <i class="i-material-symbols:delete" />
                    </n-button>
                  </template>
                  <n-form :model="listener" label-placement="left" size="small">
                    <n-form-item label="事件">
                      <n-select
                        v-model:value="listener.event"
                        :options="taskEventOptions"
                        size="small"
                      />
                    </n-form-item>
                    <n-form-item label="类名">
                      <n-input v-model:value="listener.class" placeholder="全限定类名" />
                    </n-form-item>
                  </n-form>
                </n-card>
              </div>
              <n-button dashed block @click="addTaskListener">
                <template #icon>
                  <i class="i-material-symbols:add" />
                </template>
                添加监听器
              </n-button>
            </div>
          </n-tab-pane>

          <!-- 服务任务配置Tab -->
          <n-tab-pane v-if="elementType === 'bpmn:ServiceTask'" name="service" tab="服务配置">
            <n-form :model="properties" label-placement="top" size="small">
              <n-form-item>
                <n-checkbox
                  :checked="properties.flowableType === 'cc'"
                  @update:checked="toggleCarbonCopyService"
                >
                  作为抄送节点
                </n-checkbox>
              </n-form-item>

              <template v-if="properties.flowableType === 'cc'">
                <n-form-item label="抄送来源">
                  <div class="carbon-copy-source-switch">
                    <button
                      v-for="item in carbonCopyReceiverTypeOptions"
                      :key="item.value"
                      type="button"
                      class="carbon-copy-source-button"
                      :class="{ active: properties.ccReceiverType === item.value }"
                      @click="handleCarbonCopyReceiverTypeChange(item.value)"
                    >
                      {{ item.label }}
                    </button>
                  </div>
                </n-form-item>

                <n-form-item v-if="properties.ccReceiverType === 'users'" label="抄送人">
                  <n-space vertical size="small" style="width: 100%">
                    <n-button
                      type="primary"
                      dashed
                      block
                      @click="openUserSelect('carbonCopyUsers')"
                    >
                      <template #icon>
                        <i class="i-material-symbols:group-add" />
                      </template>
                      从用户列表选择
                    </n-button>
                    <div v-if="properties.candidateUserNames.length > 0" style="margin-top: 8px">
                      <n-tag
                        v-for="(name, index) in properties.candidateUserNames"
                        :key="index"
                        type="info"
                        closable
                        style="margin: 2px"
                        @close="removeCarbonCopyUser(index)"
                      >
                        {{ name }}
                      </n-tag>
                    </div>
                  </n-space>
                </n-form-item>

                <n-form-item v-else-if="properties.ccReceiverType === 'roles'" label="抄送角色">
                  <n-select
                    :value="properties.candidateGroups"
                    :options="carbonCopyRoleOptions"
                    :loading="roleLoading"
                    placeholder="请选择角色"
                    multiple
                    clearable
                    filterable
                    remote
                    @focus="loadRoleList"
                    @search="loadRoleList"
                    @update:value="handleCarbonCopyRolesChange"
                  />
                </n-form-item>

                <template v-else>
                  <n-form-item label="表达式返回内容">
                    <n-select
                      v-model:value="properties.ccExpressionTarget"
                      :options="carbonCopyExpressionTargetOptions"
                      @update:value="updateCarbonCopyExpression"
                    />
                  </n-form-item>
                  <n-form-item label="抄送表达式">
                    <n-input
                      v-model:value="properties.ccExpression"
                      type="textarea"
                      :autosize="{ minRows: 3, maxRows: 5 }"
                      placeholder="${ccUserIds} 或 ${flowSpelService.findUsersByRole('general_manager')}"
                      @blur="updateCarbonCopyExpression"
                    />
                  </n-form-item>
                  <n-alert type="info" size="small" style="margin-bottom: 12px">
                    表达式可返回单个 ID、逗号分隔字符串或数组；选择“返回角色”时系统会按角色编码解析抄送人。
                  </n-alert>
                </template>

                <n-alert type="info" size="small" style="margin-bottom: 12px">
                  流程到达该节点时会发送抄送消息，不需要审批，发送后自动流转到下一节点。
                </n-alert>
              </template>

              <template v-if="properties.flowableType === 'cc'">
                <n-collapse class="advanced-condition-collapse">
                  <n-collapse-item title="开发者高级配置（可选）" name="carbonCopyAdvanced">
                    <n-form-item label="实现方式">
                      <n-select
                        v-model:value="properties.implementationType"
                        :options="carbonCopyImplementationTypeOptions"
                        @update:value="updateServiceImplementation"
                      />
                    </n-form-item>
                    <n-form-item label="实现值">
                      <n-input
                        v-model:value="properties.implementation"
                        :placeholder="getImplementationPlaceholder()"
                        @blur="updateServiceImplementation"
                      />
                    </n-form-item>
                  </n-collapse-item>
                </n-collapse>
              </template>
              <template v-else>
                <n-form-item label="实现方式">
                  <n-select
                    v-model:value="properties.implementationType"
                    :options="implementationTypeOptions"
                    @update:value="updateServiceImplementation"
                  />
                </n-form-item>
                <n-form-item label="实现值">
                  <n-input
                    v-model:value="properties.implementation"
                    :placeholder="getImplementationPlaceholder()"
                    @blur="updateServiceImplementation"
                  />
                </n-form-item>
              </template>
              <n-form-item label="异步执行">
                <n-switch v-model:value="properties.async" @update:value="markDirty" />
              </n-form-item>
            </n-form>
          </n-tab-pane>

          <!-- 网关配置Tab -->
          <n-tab-pane v-if="elementType === 'bpmn:ExclusiveGateway'" name="gateway" tab="网关配置">
            <n-form :model="properties" label-placement="top" size="small">
              <n-form-item label="网关类型">
                <n-radio-group v-model:value="properties.gatewayType" disabled>
                  <n-radio value="exclusive">
                    排他网关
                  </n-radio>
                  <n-radio value="parallel">
                    并行网关
                  </n-radio>
                  <n-radio value="inclusive">
                    包容网关
                  </n-radio>
                </n-radio-group>
              </n-form-item>
              <n-alert type="info" size="small">
                排他网关：只选择一条路径执行<br>
                并行网关：所有路径同时执行<br>
                包容网关：满足条件的路径同时执行
              </n-alert>
            </n-form>
          </n-tab-pane>

          <!-- 流转条件Tab -->
          <n-tab-pane v-if="elementType === 'bpmn:SequenceFlow'" name="sequence" tab="流转条件">
            <n-form :model="properties" label-placement="top" size="small">
              <n-form-item>
                <n-checkbox v-model:checked="properties.hasCondition" @update:checked="toggleCondition">
                  设置这条线的流转条件
                </n-checkbox>
              </n-form-item>

              <template v-if="properties.hasCondition">
                <n-form-item label="这条线什么时候走">
                  <n-radio-group
                    v-model:value="properties.conditionPreset"
                    class="approval-result-options"
                    @update:value="updateConditionPreset"
                  >
                    <n-radio
                      v-for="item in approvalResultConditionOptions"
                      :key="item.value"
                      :value="item.value"
                      class="approval-result-option"
                    >
                      <div class="approval-result-card" :class="{ active: properties.conditionPreset === item.value }">
                        <div class="approval-result-title">
                          <i :class="item.icon" />
                          <span>{{ item.label }}</span>
                        </div>
                        <div class="approval-result-desc">
                          {{ item.desc }}
                        </div>
                      </div>
                    </n-radio>
                  </n-radio-group>
                </n-form-item>

                <n-form-item v-if="properties.conditionPreset === 'custom'" label="按业务字段判断">
                  <n-space vertical size="small" style="width: 100%">
                    <div class="condition-builder">
                      <n-select
                        v-model:value="conditionBuilder.field"
                        :options="variableCatalogOptions"
                        placeholder="选择字段"
                        filterable
                        clearable
                      />
                      <n-select
                        v-model:value="conditionBuilder.operator"
                        :options="conditionOperatorOptions"
                        class="condition-operator"
                      />
                      <n-input
                        v-model:value="conditionBuilder.value"
                        placeholder="比较值"
                        clearable
                        @keydown.enter="applyConditionBuilder"
                      />
                      <n-button type="primary" secondary @click="applyConditionBuilder">
                        生成
                      </n-button>
                    </div>
                    <div class="field-catalog-tip">
                      例如：采购金额大于 10000 时走经理审批；合同类型等于框架合同时走法务审核。
                    </div>
                  </n-space>
                </n-form-item>

                <n-collapse class="advanced-condition-collapse">
                  <n-collapse-item title="开发者高级配置（可选）" name="advancedCondition">
                    <n-form-item label="高级类型">
                      <n-radio-group v-model:value="properties.conditionType" @update:value="updateConditionType">
                        <n-radio-button value="expression">
                          表达式
                        </n-radio-button>
                        <n-radio-button value="script">
                          脚本
                        </n-radio-button>
                      </n-radio-group>
                    </n-form-item>

                    <n-form-item v-if="properties.conditionType === 'expression'" label="条件表达式">
                      <n-input
                        v-model:value="properties.condition"
                        type="textarea"
                        :rows="3"
                        placeholder="${approvalResult == 'approve'}"
                        @blur="syncConditionPresetFromCondition(); updateCondition()"
                      />
                    </n-form-item>

                    <n-form-item v-if="properties.conditionType === 'script'" label="脚本内容">
                      <n-input
                        v-model:value="properties.script"
                        type="textarea"
                        :rows="5"
                        placeholder="return approvalResult == 'approve';"
                        @blur="updateCondition"
                      />
                    </n-form-item>

                    <n-form-item v-if="properties.conditionType === 'script'" label="脚本语言">
                      <n-select
                        v-model:value="properties.scriptFormat"
                        :options="scriptFormatOptions"
                        @update:value="updateCondition"
                      />
                    </n-form-item>
                  </n-collapse-item>
                </n-collapse>
              </template>

              <n-form-item>
                <n-checkbox v-model:checked="properties.isDefault" @update:checked="toggleDefault">
                  其它情况默认走这条线
                </n-checkbox>
              </n-form-item>
            </n-form>
          </n-tab-pane>

          <!-- 结束节点配置Tab -->
          <n-tab-pane v-if="elementType === 'bpmn:EndEvent'" name="end" tab="结束配置">
            <n-form :model="properties" label-placement="top" size="small">
              <n-form-item label="结束类型">
                <n-radio-group v-model:value="properties.endType">
                  <n-radio value="terminate">
                    终止流程
                  </n-radio>
                  <n-radio value="normal">
                    正常结束
                  </n-radio>
                </n-radio-group>
              </n-form-item>
            </n-form>
          </n-tab-pane>

          <!-- 执行监听器Tab（通用） -->
          <n-tab-pane v-if="showExecutionListener" name="executionListener" tab="执行监听器">
            <div class="listener-list">
              <div v-for="(listener, index) in properties.executionListeners" :key="index" class="listener-item">
                <n-card size="small" :title="listener.event">
                  <template #header-extra>
                    <n-button text type="error" @click="removeExecutionListener(index)">
                      <i class="i-material-symbols:delete" />
                    </n-button>
                  </template>
                  <n-form :model="listener" label-placement="left" size="small">
                    <n-form-item label="事件">
                      <n-select
                        v-model:value="listener.event"
                        :options="executionEventOptions"
                        size="small"
                      />
                    </n-form-item>
                    <n-form-item label="类名">
                      <n-input v-model:value="listener.class" placeholder="全限定类名" />
                    </n-form-item>
                  </n-form>
                </n-card>
              </div>
              <n-button dashed block @click="addExecutionListener">
                <template #icon>
                  <i class="i-material-symbols:add" />
                </template>
                添加监听器
              </n-button>
            </div>
          </n-tab-pane>
        </n-tabs>
      </div>
    </div>

    <!-- 底部固定按钮区 -->
    <div class="panel-footer">
      <n-alert v-if="isDirty" type="warning" size="small" style="margin-bottom: 8px">
        配置已修改，请点击保存按钮生效
      </n-alert>
      <n-button type="primary" block :loading="saving" @click="handleSaveConfig">
        <template #icon>
          <i class="i-material-symbols:save" />
        </template>
        保存配置
      </n-button>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, toRaw, watch } from 'vue'
import flowApi from '@/api/flow'
import FlowFormCreateDesigner from '@/components/form-create/FlowFormCreateDesigner.vue'
import FlowFormCreateRenderer from '@/components/form-create/FlowFormCreateRenderer.vue'
import { cloneValue, normalizeFormCreateRules } from '@/components/form-create/formCreateBridge'
import { request } from '@/utils/http'
import UserSelectModal from '@/components/common/UserSelectModal.vue'

const props = defineProps({
  element: {
    type: Object,
    default: null,
  },
  modeler: {
    type: Object,
    default: null,
  },
  fieldCatalog: {
    type: Array,
    default: () => [],
  },
})

const emit = defineEmits(['update'])

// Tab控制状态
const activeTab = ref('basic')
const tabsWrapperRef = ref(null)

// 保存状态
const isDirty = ref(false)
const saving = ref(false)

// SPEL模板（从API加载）
const spelTemplatesFromApi = ref([])

// 会签比例预设值
const passRatePresets = [
  { label: '过半', value: 50 },
  { label: '2/3', value: 67 },
  { label: '3/4', value: 75 },
  { label: '全部', value: 100 },
]

// 会签比例滑块刻度
const passRateMarks = { 50: '50%', 67: '2/3', 75: '75%', 100: '100%' }

// 使用全局 message 实例（保留供未来使用）
// const _message = window.$message

// 获取原始元素（避免 Vue 代理与 bpmn-js 冲突）
const rawElement = computed(() => toRaw(props.element))

// 元素类型
const elementType = computed(() => rawElement.value?.type || '')

// 是否显示执行监听器
const showExecutionListener = computed(() => {
  return ['bpmn:UserTask', 'bpmn:ServiceTask', 'bpmn:ScriptTask', 'bpmn:StartEvent', 'bpmn:EndEvent'].includes(elementType.value)
})

const visibleTabs = computed(() => {
  const tabs = [{ name: 'basic', label: '基础属性' }]
  if (elementType.value === 'bpmn:StartEvent')
    tabs.push({ name: 'startConfig', label: '开始配置' })
  if (elementType.value === 'bpmn:UserTask') {
    tabs.push(
      { name: 'approval', label: '审批设置' },
      { name: 'actionControl', label: '办理控制' },
      { name: 'multiInstance', label: '会签配置' },
      { name: 'listener', label: '监听器' },
    )
  }
  if (elementType.value === 'bpmn:ServiceTask')
    tabs.push({ name: 'service', label: '服务配置' })
  if (elementType.value === 'bpmn:ExclusiveGateway')
    tabs.push({ name: 'gateway', label: '网关配置' })
  if (elementType.value === 'bpmn:SequenceFlow')
    tabs.push({ name: 'sequence', label: '流转条件' })
  if (elementType.value === 'bpmn:EndEvent')
    tabs.push({ name: 'end', label: '结束配置' })
  if (showExecutionListener.value)
    tabs.push({ name: 'executionListener', label: '执行监听器' })
  return tabs
})

const activeTabIndex = computed(() => Math.max(0, visibleTabs.value.findIndex(tab => tab.name === activeTab.value)))
const canGoPrevTab = computed(() => activeTabIndex.value > 0)
const canGoNextTab = computed(() => activeTabIndex.value < visibleTabs.value.length - 1)
const activeTabPositionText = computed(() => {
  const total = visibleTabs.value.length
  if (!total)
    return '0/0'
  return `${activeTabIndex.value + 1}/${total} ${visibleTabs.value[activeTabIndex.value]?.label || ''}`
})

// 属性对象
const properties = reactive({
  id: '',
  name: '',
  documentation: '',
  // 用户任务
  taskType: 'assignee',
  assignee: '',
  assigneeExpr: '',
  assigneeUserName: '',
  candidateUsers: [],
  candidateUserNames: [],
  candidateGroups: [],
  candidateGroupNames: [],
  formType: 'dynamic',
  formKey: '',
  formJson: '',
  formUrl: '',
  priority: 50,
  dueDate: 0,
  allowApprove: true,
  allowReject: true,
  allowDelegate: true,
  allowReturn: false,
  allowTerminate: false,
  requireSignature: false,
  requireComment: true,
  // 多实例
  multiInstanceType: 'none',
  completionCondition: 'all',
  passRate: 100,
  // 任务监听器
  taskListeners: [],
  // 执行监听器
  executionListeners: [],
  // 服务任务
  flowableType: '',
  ccReceiverType: 'users',
  ccExpression: '',
  ccExpressionTarget: 'users',
  implementationType: 'class',
  implementation: '',
  async: false,
  // 序列流
  hasCondition: false,
  conditionType: 'expression',
  conditionPreset: 'custom',
  condition: '',
  script: '',
  scriptFormat: 'javascript',
  isDefault: false,
  // 开始节点
  initiator: 'initiator',
  // 结束节点
  endType: 'normal',
})

// 选项配置
const taskTypeOptions = [
  { label: '指定审批人', value: 'assignee' },
  { label: '候选用户', value: 'candidateUsers' },
  { label: '候选组(角色)', value: 'candidateGroups' },
]

const staticAssigneeOptions = [
  { label: '发起人', value: '$' + '{initiator}' },
  { label: '发起人上级', value: '$' + '{initiatorLeader}' },
  { label: '部门经理', value: '$' + '{deptManager}' },
  { label: 'HR', value: '$' + '{hr}' },
  { label: '指定用户', value: 'custom' },
  { label: 'SPEL 表达式', value: 'spel' },
]

const systemVariableOptions = [
  { label: '发起人', value: 'initiator', source: 'system' },
  { label: '发起人ID', value: 'startUserId', source: 'system' },
  { label: '发起部门ID', value: 'startDeptId', source: 'system' },
  { label: '业务键', value: 'businessKey', source: 'system' },
  { label: '流程实例ID', value: 'processInstanceId', source: 'system' },
  { label: '流程入口编码', value: 'flowEntryCode', source: 'system' },
  { label: '表单实例ID', value: 'flowFormInstanceId', source: 'system' },
]

const formVariableOptions = computed(() => {
  return (props.fieldCatalog || [])
    .filter(item => item?.field)
    .map(item => ({
      label: item.label ? `${item.label}（${item.field}）` : item.field,
      value: item.field,
      source: item.source || 'form',
      componentType: item.componentType || '',
      dataType: item.dataType || '',
    }))
})

const variableCatalogOptions = computed(() => [
  {
    type: 'group',
    label: '表单字段',
    key: 'form-fields',
    children: formVariableOptions.value,
  },
  {
    type: 'group',
    label: '系统变量',
    key: 'system-vars',
    children: systemVariableOptions,
  },
])

const assigneeOptions = computed(() => {
  const formAssignees = formVariableOptions.value.map(item => ({
    label: `表单字段：${item.label}`,
    value: toExpression(item.value),
  }))
  return [
    ...staticAssigneeOptions,
    ...(formAssignees.length
      ? [{
          type: 'group',
          label: '表单字段',
          key: 'form-assignee-fields',
          children: formAssignees,
        }]
      : []),
  ]
})

const conditionOperatorOptions = [
  { label: '等于', value: '==' },
  { label: '不等于', value: '!=' },
  { label: '大于', value: '>' },
  { label: '大于等于', value: '>=' },
  { label: '小于', value: '<' },
  { label: '小于等于', value: '<=' },
  { label: '包含', value: 'contains' },
]

const approvalResultConditionOptions = [
  {
    label: '同意通过',
    value: 'approve',
    expression: '$' + '{approvalResult == \'approve\'}',
    icon: 'i-material-symbols:check-circle',
    desc: '审批人点击同意后走这条线',
  },
  {
    label: '驳回修改',
    value: 'reject',
    expression: '$' + '{approvalResult == \'reject\'}',
    icon: 'i-material-symbols:edit-note',
    desc: '审批人点击驳回修改后走这条线',
  },
  {
    label: '退回上一步',
    value: 'return',
    expression: '$' + '{approvalResult == \'return\'}',
    icon: 'i-material-symbols:keyboard-return',
    desc: '审批人点击退回时走这条线',
  },
  {
    label: '终止流程',
    value: 'terminate',
    expression: '$' + '{approvalResult == \'terminate\'}',
    icon: 'i-material-symbols:stop-circle',
    desc: '审批人点击终止时走这条线',
  },
  {
    label: '按业务字段判断',
    value: 'custom',
    expression: '',
    icon: 'i-material-symbols:tune',
    desc: '按金额、部门、类型等字段配置条件',
  },
]

const formTypeOptions = [
  { label: '动态表单', value: 'dynamic' },
  { label: '外部表单', value: 'external' },
  { label: '无表单', value: 'none' },
]

const nodeInlineFormRules = computed(() => normalizeFormCreateRules(properties.formJson))
const nodeFormFieldCount = computed(() => countFormRules(nodeInlineFormRules.value))
const canPreviewNodeForm = computed(() => Boolean(properties.formJson?.trim() || properties.formKey?.trim()))
const nodeFormDesignerTitle = computed(() => `节点表单设计 - ${properties.name || properties.id || '未命名节点'}`)
const nodeFormPreviewTitle = computed(() => `节点表单预览 - ${properties.name || properties.id || '未命名节点'}`)
const nodeFormBuilderTitle = computed(() => {
  if (nodeFormFieldCount.value > 0)
    return `已内嵌 ${nodeFormFieldCount.value} 个字段`
  if (properties.formKey)
    return '引用已有动态表单'
  return '未配置节点表单'
})
const nodeFormBuilderDesc = computed(() => {
  if (nodeFormFieldCount.value > 0)
    return '办理该节点时会优先渲染这里设计的表单，并把填写内容提交为流程变量'
  if (properties.formKey)
    return `当前引用 ${properties.formKey}，也可以点击在线设计生成节点专属表单`
  return '点击在线设计为当前审批节点配置专属字段、校验和布局'
})

const approvalActionOptions = [
  { key: 'allowApprove', label: '通过', icon: 'i-material-symbols:check-circle' },
  { key: 'allowReject', label: '拒绝', icon: 'i-material-symbols:cancel' },
  { key: 'allowReturn', label: '退回', icon: 'i-material-symbols:keyboard-return' },
  { key: 'allowTerminate', label: '终结流程', icon: 'i-material-symbols:stop-circle' },
  { key: 'allowDelegate', label: '转办', icon: 'i-material-symbols:person-add' },
]

// 用户选择相关
const showUserSelect = ref(false)
const userSelectTitle = ref('选择用户')
const userSelectMultiple = ref(false)
const userSelectType = ref('')
const currentSelectedUsers = ref([])

// 角色选择相关
const showRoleSelect = ref(false)
const roleList = ref([])
const roleLoading = ref(false)
const checkedRoleKeys = ref([])

// 角色表格列
const roleColumns = [
  { type: 'selection' },
  { title: '角色名称', key: 'roleName' },
  { title: '角色编码', key: 'roleKey' },
]

// 节点动态表单
const formDefinitionOptions = ref([])
const formDefinitionLoading = ref(false)
const showNodeFormDesigner = ref(false)
const showNodeFormPreview = ref(false)
const nodeFormDesignerRef = ref(null)
const nodeFormDesignerSchema = ref([])
const nodeFormPreviewSchema = ref([])

// SPEL表达式相关
const selectedSpelTemplate = ref('')
const spelValidationError = ref('')
const selectedSpelVariable = ref(null)
const conditionBuilder = reactive({
  field: '',
  operator: '==',
  value: '',
})

function toExpression(field) {
  return '$' + `{${field}}`
}

function unwrapExpression(value) {
  const text = String(value || '').trim()
  const prefix = '$' + '{'
  if (text.startsWith(prefix) && text.endsWith('}'))
    return text.slice(2, -1).trim()
  return ''
}

function isSimpleVariableExpression(value) {
  const inner = unwrapExpression(value)
  return /^[A-Z_][\w.]*$/i.test(inner)
}

function getCatalogField(field) {
  return formVariableOptions.value.find(item => item.value === field)
    || systemVariableOptions.find(item => item.value === field)
}

function quoteConditionValue(value) {
  const text = String(value ?? '').trim()
  if (text === '')
    return 'null'
  if (['true', 'false', 'null'].includes(text))
    return text
  if (/^-?\d+(?:\.\d+)?$/.test(text))
    return text
  if ((text.startsWith('\'') && text.endsWith('\'')) || (text.startsWith('"') && text.endsWith('"')))
    return text
  return `'${text.replaceAll('\'', '\\\'')}'`
}

function normalizeConditionExpression(value) {
  return String(value || '')
    .trim()
    .replaceAll('"', '\'')
    .replace(/\s+/g, '')
}

function findApprovalResultPresetByExpression(expression) {
  const normalized = normalizeConditionExpression(expression)
  return approvalResultConditionOptions.find(item =>
    item.expression && normalizeConditionExpression(item.expression) === normalized,
  )
}

function syncConditionPresetFromCondition() {
  if (!properties.condition) {
    properties.conditionPreset = 'custom'
    return
  }
  properties.conditionPreset = findApprovalResultPresetByExpression(properties.condition)?.value || 'custom'
}

function updateConditionPreset(value) {
  const preset = approvalResultConditionOptions.find(item => item.value === value)
  properties.hasCondition = true
  properties.conditionType = 'expression'
  properties.script = ''
  if (preset?.expression) {
    properties.condition = preset.expression
  }
  else if (value === 'custom' && findApprovalResultPresetByExpression(properties.condition)) {
    properties.condition = ''
  }
  updateCondition()
}

// 标记为未保存状态
function markDirty() {
  isDirty.value = true
}

function switchRelativeTab(offset) {
  const tabs = visibleTabs.value
  const nextIndex = Math.min(Math.max(activeTabIndex.value + offset, 0), tabs.length - 1)
  activeTab.value = tabs[nextIndex]?.name || 'basic'
}

function scrollActiveTabIntoView() {
  const activeNode = tabsWrapperRef.value?.querySelector?.('.n-tabs-tab--active')
  activeNode?.scrollIntoView?.({
    behavior: 'smooth',
    block: 'nearest',
    inline: 'center',
  })
}

// 从API加载SPEL模板
async function loadSpelTemplates() {
  try {
    const res = await request.get('/api/flow/spelTemplate/list')
    if (res.code === 200) {
      spelTemplatesFromApi.value = (res.data || []).map(t => ({
        label: t.templateName,
        value: t.expression,
        description: t.description || '',
      }))
    }
  }
  catch (e) {
    console.error('加载SPEL模板失败', e)
  }
}

async function loadFormDefinitions() {
  formDefinitionLoading.value = true
  try {
    const res = await flowApi.getEnabledForms()
    if (res.code === 200) {
      formDefinitionOptions.value = (res.data || [])
        .filter(item => item.formKey)
        .map(item => ({
          label: item.formName ? `${item.formName}（${item.formKey}）` : item.formKey,
          value: item.formKey,
        }))
    }
  }
  catch (error) {
    console.error('加载流程表单失败:', error)
  }
  finally {
    formDefinitionLoading.value = false
  }
}

// 手动保存配置
async function handleSaveConfig() {
  saving.value = true
  try {
    // 执行所有必要的update方法
    updateProperty('id')
    updateProperty('name')
    updateProperty('documentation')

    // 根据元素类型执行特定更新
    if (elementType.value === 'bpmn:StartEvent') {
      updateExtensionProperty('initiator')
      updateExtensionProperty('formKey')
    }

    if (elementType.value === 'bpmn:UserTask') {
      updateUserTaskAssignee()
      updateFormType()
      updateExtensionProperty('priority')
      updateDueDate()
      updateApprovalControl()
      updateMultiInstance()
      updateTaskListeners()
    }

    if (elementType.value === 'bpmn:ServiceTask') {
      updateServiceImplementation()
      updateCarbonCopyConfig()
      updateAsync()
    }

    if (elementType.value === 'bpmn:SequenceFlow') {
      updateCondition()
    }

    // 更新执行监听器
    if (showExecutionListener.value) {
      updateExecutionListeners()
    }

    // 触发父组件更新
    emit('update')

    isDirty.value = false
    window.$message?.success('配置已保存')
  }
  catch (error) {
    console.error('保存配置失败:', error)
    window.$message?.error('保存失败')
  }
  finally {
    saving.value = false
  }
}

// 组件挂载时加载SPEL模板
onMounted(() => {
  loadSpelTemplates()
  loadFormDefinitions()
})

const completionConditionOptions = [
  { label: '全部通过', value: 'all' },
  { label: '任一通过', value: 'any' },
  { label: '按比例通过', value: 'rate' },
]

// 通过比例描述文字
const passRateDesc = computed(() => {
  const rate = properties.passRate
  if (rate >= 100)
    return '需要所有审批人全部同意才能通过'
  if (rate === 50)
    return `需要超过一半的审批人同意才能通过`
  if (rate === 67)
    return '需要 2/3 以上的审批人同意才能通过'
  if (rate === 75)
    return '需要 3/4 以上的审批人同意才能通过'
  return `需要至少 ${rate}% 的审批人同意才能通过`
})

// 设置预设比例并触发更新
function setPassRate(value) {
  properties.passRate = value
  updateMultiInstance()
}

const taskEventOptions = [
  { label: '创建(create)', value: 'create' },
  { label: '分配(assignment)', value: 'assignment' },
  { label: '完成(complete)', value: 'complete' },
  { label: '删除(delete)', value: 'delete' },
]

const executionEventOptions = [
  { label: '开始(start)', value: 'start' },
  { label: '结束(end)', value: 'end' },
  { label: '执行(take)', value: 'take' },
]

const implementationTypeOptions = [
  { label: 'Java类', value: 'class' },
  { label: '表达式', value: 'expression' },
  { label: '委托表达式', value: 'delegateExpression' },
]

const carbonCopyImplementationTypeOptions = [
  { label: '平台默认/委托表达式', value: 'delegateExpression' },
  { label: '表达式', value: 'expression' },
  { label: 'Java类', value: 'class' },
]

const carbonCopyReceiverTypeOptions = [
  { label: '指定人员', value: 'users' },
  { label: '指定角色', value: 'roles' },
  { label: '表达式', value: 'expression' },
]

const carbonCopyExpressionTargetOptions = [
  { label: '表达式返回人员', value: 'users' },
  { label: '表达式返回角色', value: 'roles' },
]

const carbonCopyRoleOptions = computed(() => {
  const selected = properties.candidateGroups.map((value, index) => ({
    label: properties.candidateGroupNames[index] || value,
    value,
    roleName: properties.candidateGroupNames[index] || value,
    roleKey: value,
  }))
  return mergeSelectOptions(roleList.value.map(normalizeRoleOption).filter(Boolean), selected)
})

const scriptFormatOptions = [
  { label: 'JavaScript', value: 'javascript' },
  { label: 'Groovy', value: 'groovy' },
  { label: 'JUEL', value: 'juel' },
]

// 监听元素变化，加载属性
watch(() => props.element, (newElement) => {
  if (newElement) {
    loadElementProperties(toRaw(newElement))
    // 自动修复：老模型可能缺少 loopCardinality，打开时自动补全
    nextTick(() => {
      if (properties.multiInstanceType !== 'none' && rawElement.value?.type === 'bpmn:UserTask') {
        const bo = rawElement.value.businessObject
        if (bo?.loopCharacteristics && !bo.loopCharacteristics.loopCardinality) {
          updateMultiInstance()
        }
      }
    })
    // 切换节点时回到第一个Tab
    activeTab.value = 'basic'
    nextTick(scrollActiveTabIntoView)
  }
}, { immediate: true })

watch(activeTab, () => {
  nextTick(scrollActiveTabIntoView)
})

let spelSaveTimer = null
watch(() => properties.assigneeExpr, (newVal) => {
  if (properties.assignee !== 'spel' || !newVal)
    return
  clearTimeout(spelSaveTimer)
  spelSaveTimer = setTimeout(() => {
    updateUserTaskAssignee()
  }, 500)
})

watch(formVariableOptions, () => {
  if (properties.assignee === 'spel' && isSimpleVariableExpression(properties.assigneeExpr)) {
    const field = unwrapExpression(properties.assigneeExpr)
    if (getCatalogField(field)) {
      properties.assignee = properties.assigneeExpr
      properties.assigneeExpr = ''
      properties.assigneeUserName = ''
    }
  }
})

// 打开用户选择弹窗
function openUserSelect(type) {
  userSelectType.value = type
  if (type === 'assignee') {
    userSelectTitle.value = '选择审批人'
    userSelectMultiple.value = false
    // 如果已有选中的用户，回显
    if (properties.assigneeExpr && properties.assigneeExpr.startsWith('${user_')) {
      // 从表达式中提取用户ID，格式: ${user_1}
      const match = properties.assigneeExpr.match(/\$\{user_(\d+)\}/)
      if (match) {
        currentSelectedUsers.value = [{
          id: Number.parseInt(match[1]),
          nickName: properties.assigneeUserName,
        }]
      }
      else {
        currentSelectedUsers.value = []
      }
    }
    else {
      currentSelectedUsers.value = []
    }
  }
  else if (type === 'candidateUsers') {
    userSelectTitle.value = '选择候选用户'
    userSelectMultiple.value = true
    // 回显已选候选用户
    if (properties.candidateUsers.length > 0) {
      currentSelectedUsers.value = properties.candidateUsers.map((id, index) => ({
        id: Number.parseInt(id),
        nickName: properties.candidateUserNames[index] || '',
      }))
    }
    else {
      currentSelectedUsers.value = []
    }
  }
  else if (type === 'carbonCopyUsers') {
    userSelectTitle.value = '选择抄送人'
    userSelectMultiple.value = true
    if (properties.candidateUsers.length > 0) {
      currentSelectedUsers.value = properties.candidateUsers.map((id, index) => ({
        id: Number.parseInt(id),
        nickName: properties.candidateUserNames[index] || '',
      }))
    }
    else {
      currentSelectedUsers.value = []
    }
  }
  showUserSelect.value = true
}

// 用户选择确认
function handleUserSelectConfirm(users) {
  if (userSelectType.value === 'assignee') {
    const user = Array.isArray(users) ? users[0] : users
    if (user) {
      properties.assignee = 'custom'
      properties.assigneeExpr = '$' + `{user_${user.id}}`
      properties.assigneeUserName = user.nickName || user.userName
      updateUserTaskAssignee()
    }
  }
  else if (userSelectType.value === 'candidateUsers') {
    const userList = Array.isArray(users) ? users : [users]
    userList.forEach((user) => {
      if (!properties.candidateUsers.includes(user.id.toString())) {
        properties.candidateUsers.push(user.id.toString())
        properties.candidateUserNames.push(user.nickName || user.userName)
      }
    })
    updateCandidateUsers()
  }
  else if (userSelectType.value === 'carbonCopyUsers') {
    const userList = Array.isArray(users) ? users : [users]
    properties.ccReceiverType = 'users'
    properties.ccExpression = ''
    properties.ccExpressionTarget = 'users'
    properties.candidateGroups = []
    properties.candidateGroupNames = []
    userList.filter(Boolean).forEach((user) => {
      if (!properties.candidateUsers.includes(user.id.toString())) {
        properties.candidateUsers.push(user.id.toString())
        properties.candidateUserNames.push(user.nickName || user.realName || user.name || user.userName)
      }
    })
    updateCarbonCopyConfig()
  }
  showUserSelect.value = false
}

// 清除审批人
function clearAssigneeUser() {
  properties.assignee = ''
  properties.assigneeExpr = ''
  properties.assigneeUserName = ''
  updateUserTaskAssignee()
}

// 移除候选用户
function removeCandidateUser(index) {
  properties.candidateUsers.splice(index, 1)
  properties.candidateUserNames.splice(index, 1)
  updateCandidateUsers()
}

function removeCarbonCopyUser(index) {
  properties.candidateUsers.splice(index, 1)
  properties.candidateUserNames.splice(index, 1)
  updateCarbonCopyConfig()
}

// 打开角色选择弹窗
async function openRoleSelect() {
  // 回显已选角色
  if (properties.candidateGroups.length > 0) {
    checkedRoleKeys.value = properties.candidateGroups.map(id => Number.parseInt(id))
  }
  else {
    checkedRoleKeys.value = []
  }
  showRoleSelect.value = true
  await loadRoleList()
}

// 加载角色列表
async function loadRoleList(keyword = '') {
  roleLoading.value = true
  try {
    const res = await request.get('/system/role/page', {
      params: {
        pageNum: 1,
        pageSize: 1000,
        roleName: typeof keyword === 'string' ? keyword || undefined : undefined,
      },
    })
    if (res.code === 200 && res.data?.records) {
      roleList.value = res.data.records
    }
  }
  catch (error) {
    console.error('加载角色列表失败:', error)
  }
  finally {
    roleLoading.value = false
  }
}

function normalizeRoleOption(role) {
  const value = isFilled(role?.roleKey) ? role.roleKey : role?.id
  if (!isFilled(value))
    return null
  const label = String(role.roleName || role.roleKey || value)
  return {
    label,
    value: String(value),
    roleName: label,
    roleKey: String(value),
  }
}

function mergeSelectOptions(primary = [], append = []) {
  const map = new Map()
  for (const option of [...append, ...primary]) {
    if (!option || !isFilled(option.value))
      continue
    map.set(String(option.value), option)
  }
  return Array.from(map.values())
}

function isFilled(value) {
  return value !== null && value !== undefined && String(value).trim() !== ''
}

// 角色选择
function handleRoleCheck(keys) {
  checkedRoleKeys.value = keys
}

// 角色选择确认
function handleRoleConfirm() {
  const selectedRoles = roleList.value.filter(r => checkedRoleKeys.value.includes(r.id))
  selectedRoles.forEach((role) => {
    if (!properties.candidateGroups.includes(role.id.toString())) {
      properties.candidateGroups.push(role.id.toString())
      properties.candidateGroupNames.push(role.roleName)
    }
  })
  updateCandidateGroups()
  showRoleSelect.value = false
}

// 移除候选组
function removeCandidateGroup(index) {
  properties.candidateGroups.splice(index, 1)
  properties.candidateGroupNames.splice(index, 1)
  updateCandidateGroups()
}

function handleCarbonCopyReceiverTypeChange(value) {
  properties.ccReceiverType = value || 'users'
  if (properties.ccReceiverType === 'users') {
    properties.candidateGroups = []
    properties.candidateGroupNames = []
    properties.ccExpression = ''
    properties.ccExpressionTarget = 'users'
  }
  else if (properties.ccReceiverType === 'roles') {
    properties.candidateUsers = []
    properties.candidateUserNames = []
    properties.ccExpression = ''
    properties.ccExpressionTarget = 'roles'
    loadRoleList()
  }
  else {
    applyCarbonCopyExpressionToProperties()
  }
  updateCarbonCopyConfig()
}

function handleCarbonCopyRolesChange(values, selectedOptions = []) {
  const nextValues = normalizeList(values)
  const selectedOptionList = Array.isArray(selectedOptions) ? selectedOptions : selectedOptions ? [selectedOptions] : []
  const optionMap = new Map(carbonCopyRoleOptions.value.map(option => [String(option.value), option]))
  const selectedMap = new Map(selectedOptionList.map(option => [String(option.value), option]))
  properties.ccReceiverType = 'roles'
  properties.ccExpression = ''
  properties.ccExpressionTarget = 'roles'
  properties.candidateUsers = []
  properties.candidateUserNames = []
  properties.candidateGroups = nextValues
  properties.candidateGroupNames = nextValues.map((value) => {
    const option = selectedMap.get(String(value)) || optionMap.get(String(value))
    return option?.roleName || option?.label || value
  })
  updateCarbonCopyConfig()
}

function updateCarbonCopyExpression() {
  properties.ccReceiverType = 'expression'
  applyCarbonCopyExpressionToProperties()
  updateCarbonCopyConfig()
}

function applyCarbonCopyExpressionToProperties() {
  const expression = normalizeCarbonCopyExpression(properties.ccExpression)
  properties.ccExpression = expression
  const label = expression ? ['表达式配置'] : []
  if (properties.ccExpressionTarget === 'roles') {
    properties.candidateUsers = []
    properties.candidateUserNames = []
    properties.candidateGroups = expression ? [expression] : []
    properties.candidateGroupNames = label
    return
  }
  properties.candidateUsers = expression ? [expression] : []
  properties.candidateUserNames = label
  properties.candidateGroups = []
  properties.candidateGroupNames = []
}

function normalizeCarbonCopyExpression(value) {
  const text = String(value || '').trim()
  if (!text)
    return ''
  if (text.startsWith('$' + '{') && text.endsWith('}'))
    return text
  return '$' + `{${text}}`
}

function normalizeList(value) {
  if (Array.isArray(value))
    return value.map(item => String(item ?? '').trim()).filter(Boolean)
  if (!isFilled(value))
    return []
  return String(value).split(/[,，\s]+/).map(item => item.trim()).filter(Boolean)
}

function findCarbonCopyExpression(values) {
  return normalizeList(values).find(value => value.startsWith('$' + '{') && value.endsWith('}')) || ''
}

function inferCarbonCopyReceiverType(candidateUsers, candidateGroups, configuredType) {
  if (configuredType)
    return configuredType
  if (findCarbonCopyExpression(candidateUsers) || findCarbonCopyExpression(candidateGroups))
    return 'expression'
  if (normalizeList(candidateGroups).length)
    return 'roles'
  return 'users'
}

// 加载元素属性
function loadElementProperties(element) {
  const bo = element.businessObject
  if (!bo)
    return

  // 基础属性
  properties.id = bo.id || ''
  properties.name = bo.name || ''

  // 文档
  const docs = bo.documentation || []
  properties.documentation = docs.length > 0 ? docs[0].text : ''

  // 根据元素类型加载不同属性
  if (element.type === 'bpmn:UserTask') {
    loadUserTaskProperties(bo)
  }
  else if (element.type === 'bpmn:ServiceTask') {
    loadServiceTaskProperties(bo)
  }
  else if (element.type === 'bpmn:SequenceFlow') {
    loadSequenceFlowProperties(bo)
  }
  else if (element.type === 'bpmn:StartEvent') {
    loadStartEventProperties(bo)
  }
}

// 加载用户任务属性
function loadUserTaskProperties(bo) {
  // moddleExtensions 注册后，flowable 属性直接挂载在 bo 上（无命名空间前缀）
  // 同时兼容 $attrs 里的原始属性（XML 里带前缀的情况）
  const attrs = bo.$attrs || {}

  // 审批人类型判断 - 先读直接属性，再读 $attrs 兼容
  const assignee = bo.assignee ?? attrs['flowable:assignee'] ?? ''
  const candidateUsers = bo.candidateUsers ?? attrs['flowable:candidateUsers'] ?? ''
  const candidateGroups = bo.candidateGroups ?? attrs['flowable:candidateGroups'] ?? ''

  // 读取保存的用户名/角色名（自定义扩展属性）
  const assigneeName = bo.assigneeName ?? attrs['flowable:assigneeName'] ?? ''
  const candidateUserNames = bo.candidateUserNames ?? attrs['flowable:candidateUserNames'] ?? ''
  const candidateGroupNames = bo.candidateGroupNames ?? attrs['flowable:candidateGroupNames'] ?? ''

  // 读取审批人类型标识（用于区分 SPEL 表达式）
  const assigneeType = bo.assigneeType ?? attrs['flowable:assigneeType'] ?? ''
  // 读取 SPEL 表达式模板（用于回显模板选择）
  const spelTemplate = bo.spelTemplate ?? attrs['flowable:spelTemplate'] ?? ''

  // 重置审批相关属性，防止上一个节点的数据残留
  properties.taskType = 'assignee'
  properties.assignee = ''
  properties.assigneeExpr = ''
  properties.assigneeUserName = ''
  properties.candidateUsers = []
  properties.candidateUserNames = []
  properties.candidateGroups = []
  properties.candidateGroupNames = []

  if (assignee) {
    properties.taskType = 'assignee'
    properties.assigneeUserName = assigneeName

    // 优先使用 assigneeType 标识来判断类型
    if (assigneeType === 'spel') {
      properties.assignee = 'spel'
      properties.assigneeExpr = assignee
    }
    // 判断是否是自定义用户 ID 表达式：${user_123}
    else if (assignee.startsWith('$' + '{user_')) {
      properties.assignee = 'custom'
      properties.assigneeExpr = assignee
    }
    // 预定义表达式：${initiator}, ${initiatorLeader}, ${deptManager}, ${hr}
    else if (['$' + '{initiator}', '$' + '{initiatorLeader}', '$' + '{deptManager}', '$' + '{hr}'].includes(assignee)) {
      properties.assignee = assignee
    }
    // 简单变量表达式可直接作为 Flowable 审批人表达式回显
    else if (isSimpleVariableExpression(assignee)) {
      properties.assignee = assignee
    }
    // 其他 ${} 表达式默认当作 SPEL（向后兼容旧数据）
    else if (assignee.startsWith('$' + '{') && assignee.endsWith('}')) {
      properties.assignee = 'spel'
      properties.assigneeExpr = assignee
    }
    else {
      properties.assignee = assignee
    }
  }
  else if (assigneeType === 'spel') {
    properties.taskType = 'assignee'
    properties.assignee = 'spel'
  }
  else if (candidateUsers) {
    properties.taskType = 'candidateUsers'
    properties.candidateUsers = candidateUsers.split(',').filter(Boolean)
    properties.candidateUserNames = candidateUserNames ? candidateUserNames.split(',').filter(Boolean) : []
  }
  else if (candidateGroups) {
    properties.taskType = 'candidateGroups'
    properties.candidateGroups = candidateGroups.split(',').filter(Boolean)
    properties.candidateGroupNames = candidateGroupNames ? candidateGroupNames.split(',').filter(Boolean) : []
  }

  if (assigneeType === 'spel') {
    selectedSpelTemplate.value = spelTemplate
  }

  // 表单配置 - 直接读 bo 上的属性
  properties.formKey = bo.formKey ?? attrs['flowable:formKey'] ?? ''
  properties.formJson = bo.formJson ?? attrs['flowable:formJson'] ?? ''
  properties.formUrl = bo.formUrl ?? attrs['flowable:formUrl'] ?? ''

  // 表单类型判断
  if (properties.formUrl) {
    properties.formType = 'external'
  }
  else if (properties.formKey || properties.formJson) {
    properties.formType = 'dynamic'
  }
  else {
    properties.formType = 'none'
  }

  // 优先级和截止日期
  const priorityVal = bo.priority ?? attrs['flowable:priority']
  properties.priority = priorityVal != null ? Number.parseInt(priorityVal) : 50
  const dueDateVal = bo.dueDate ?? attrs['flowable:dueDate']
  if (dueDateVal) {
    // 格式可能是 P3D (ISO 8601 duration) 或纯数字
    const match = String(dueDateVal).match(/(\d+)/)
    properties.dueDate = match ? Number.parseInt(match[1]) : 0
  }
  else {
    properties.dueDate = 0
  }

  properties.allowApprove = readBooleanAttr(bo, attrs, 'allowApprove', true)
  properties.allowReject = readBooleanAttr(bo, attrs, 'allowReject', true)
  properties.allowDelegate = readBooleanAttr(bo, attrs, 'allowDelegate', true)
  properties.allowReturn = readBooleanAttr(bo, attrs, 'allowReturn', false)
  properties.allowTerminate = readBooleanAttr(bo, attrs, 'allowTerminate', false)
  properties.requireSignature = readBooleanAttr(bo, attrs, 'requireSignature', false)
  properties.requireComment = readBooleanAttr(bo, attrs, 'requireComment', true)

  // 多实例配置
  const loopCharacteristics = bo.loopCharacteristics
  if (loopCharacteristics) {
    properties.multiInstanceType = loopCharacteristics.isSequential ? 'sequential' : 'parallel'
    // 解析完成条件
    if (loopCharacteristics.completionCondition) {
      const condition = loopCharacteristics.completionCondition.body || ''
      if (condition.includes('nrOfCompletedInstances == nrOfInstances')) {
        properties.completionCondition = 'all'
      }
      else if (condition.includes('nrOfCompletedInstances >= 1')) {
        properties.completionCondition = 'any'
      }
      else if (condition.includes('/ nrOfInstances')) {
        properties.completionCondition = 'rate'
        const match = condition.match(/>= *([\d.]+)/)
        if (match) {
          properties.passRate = Math.round(Number.parseFloat(match[1]) * 100)
        }
      }
    }
    else {
      properties.completionCondition = 'all'
    }
  }
  else {
    properties.multiInstanceType = 'none'
    properties.completionCondition = 'all'
    properties.passRate = 100
  }

  // 任务监听器
  properties.taskListeners = []
  const extensionElements = bo.extensionElements?.values || []
  extensionElements.forEach((ext) => {
    if (ext.$type === 'flowable:TaskListener') {
      properties.taskListeners.push({
        event: ext.event || 'create',
        class: ext.class || '',
      })
    }
  })

  // 执行监听器
  properties.executionListeners = []
  extensionElements.forEach((ext) => {
    if (ext.$type === 'flowable:ExecutionListener') {
      properties.executionListeners.push({
        event: ext.event || 'start',
        class: ext.class || '',
      })
    }
  })
}

// 加载服务任务属性
function loadServiceTaskProperties(bo) {
  const attrs = bo.$attrs || {}

  // moddleExtensions 注册后直接读 bo.class / bo.expression / bo.delegateExpression
  const classVal = bo.class ?? bo['flowable:class'] ?? attrs['flowable:class']
  const exprVal = bo.expression ?? bo['flowable:expression'] ?? attrs['flowable:expression']
  const delegateVal = bo.delegateExpression ?? bo['flowable:delegateExpression'] ?? attrs['flowable:delegateExpression']
  const flowableType = bo.type ?? bo['flowable:type'] ?? attrs['flowable:type'] ?? ''
  const candidateUsers = bo.candidateUsers ?? attrs['flowable:candidateUsers'] ?? ''
  const candidateUserNames = bo.candidateUserNames ?? attrs['flowable:candidateUserNames'] ?? ''
  const candidateGroups = bo.candidateGroups ?? attrs['flowable:candidateGroups'] ?? ''
  const candidateGroupNames = bo.candidateGroupNames ?? attrs['flowable:candidateGroupNames'] ?? ''
  const ccReceiverType = bo.ccReceiverType ?? attrs['flowable:ccReceiverType'] ?? ''
  const ccExpressionTarget = bo.ccExpressionTarget ?? attrs['flowable:ccExpressionTarget'] ?? ''

  if (classVal) {
    properties.implementationType = 'class'
    properties.implementation = classVal
  }
  else if (exprVal) {
    properties.implementationType = 'expression'
    properties.implementation = exprVal
  }
  else if (delegateVal) {
    properties.implementationType = 'delegateExpression'
    properties.implementation = flowableType === 'cc' && delegateVal === '$' + '{flowCcNodeDelegate}' ? '' : delegateVal
  }
  else {
    properties.implementationType = flowableType === 'cc' ? 'delegateExpression' : 'class'
    properties.implementation = ''
  }
  properties.flowableType = flowableType
  properties.candidateUsers = candidateUsers ? candidateUsers.split(',').filter(Boolean) : []
  properties.candidateUserNames = candidateUserNames ? candidateUserNames.split(',').filter(Boolean) : []
  properties.candidateGroups = candidateGroups ? candidateGroups.split(',').filter(Boolean) : []
  properties.candidateGroupNames = candidateGroupNames ? candidateGroupNames.split(',').filter(Boolean) : []
  properties.ccReceiverType = flowableType === 'cc'
    ? inferCarbonCopyReceiverType(properties.candidateUsers, properties.candidateGroups, ccReceiverType)
    : 'users'
  properties.ccExpressionTarget = ccExpressionTarget || (findCarbonCopyExpression(properties.candidateGroups) ? 'roles' : 'users')
  properties.ccExpression = findCarbonCopyExpression(properties.candidateUsers) || findCarbonCopyExpression(properties.candidateGroups) || ''
  properties.async = bo.async ?? attrs['flowable:async'] ?? false
}

// 加载序列流属性
function loadSequenceFlowProperties(bo) {
  const conditionExpression = bo.conditionExpression
  if (conditionExpression) {
    properties.hasCondition = true
    properties.condition = conditionExpression.body || ''
    properties.conditionType = 'expression'
    syncConditionPresetFromCondition()
  }
  else {
    properties.hasCondition = false
    properties.condition = ''
    properties.conditionType = 'expression'
    properties.conditionPreset = 'custom'
  }

  // 检查是否默认流
  const source = bo.sourceRef
  if (source && source.default) {
    properties.isDefault = source.default.id === bo.id
  }
  else {
    properties.isDefault = false
  }
}

// 加载开始事件属性
function loadStartEventProperties(bo) {
  const attrs = bo.$attrs || {}
  // moddleExtensions 注册后直接读 bo.initiator
  properties.initiator = bo.initiator ?? bo['flowable:initiator'] ?? attrs['flowable:initiator'] ?? 'initiator'
  properties.formKey = bo.formKey ?? bo['flowable:formKey'] ?? attrs['flowable:formKey'] ?? ''
}

function readBooleanAttr(bo, attrs, name, defaultValue) {
  const value = bo[name] ?? bo[`flowable:${name}`] ?? attrs[`flowable:${name}`]
  if (value === undefined || value === null || value === '')
    return defaultValue
  if (typeof value === 'boolean')
    return value
  return ['true', '1', 'y', 'yes'].includes(String(value).trim().toLowerCase())
}

// 更新扩展属性（flowable 命名空间属性，通过 moddleExtensions 注册后直接用属性名）
function updateExtensionProperty(prop) {
  if (!rawElement.value || !props.modeler)
    return

  const modeling = props.modeler.get('modeling')
  const value = properties[prop]
  // 使用 flowable: 前缀写入，bpmn-js 会根据 moddleExtensions 映射
  modeling.updateProperties(rawElement.value, {
    [`flowable:${prop}`]: value !== '' ? value : null,
  })
}

// 更新任务类型
function updateTaskType() {
  // 清空其他审批人配置
  properties.assignee = ''
  properties.assigneeExpr = ''
  properties.assigneeUserName = ''
  properties.candidateUsers = []
  properties.candidateUserNames = []
  properties.candidateGroups = []
  properties.candidateGroupNames = []
}

function insertSpelVariable(field) {
  if (!field)
    return
  properties.assigneeExpr = toExpression(field)
  selectedSpelVariable.value = null
  updateUserTaskAssignee()
}

function applyConditionBuilder() {
  if (!conditionBuilder.field) {
    window.$message?.warning('请选择条件字段')
    return
  }
  const field = conditionBuilder.field
  const operator = conditionBuilder.operator || '=='
  const rightValue = operator === 'contains'
    ? `.contains(${quoteConditionValue(conditionBuilder.value)})`
    : ` ${operator} ${quoteConditionValue(conditionBuilder.value)}`
  properties.hasCondition = true
  properties.conditionType = 'expression'
  properties.conditionPreset = 'custom'
  properties.condition = operator === 'contains'
    ? toExpression(`${field}${rightValue}`)
    : toExpression(`${field}${rightValue}`)
  updateCondition()
}

function applyCandidateVariable(type, field) {
  if (!field)
    return
  const option = getCatalogField(field)
  const expression = toExpression(field)
  if (type === 'users') {
    properties.candidateUsers = [expression]
    properties.candidateUserNames = [option?.label ? `表单字段：${option.label}` : expression]
    updateCandidateUsers()
    return
  }
  properties.candidateGroups = [expression]
  properties.candidateGroupNames = [option?.label ? `表单字段：${option.label}` : expression]
  updateCandidateGroups()
}

function handleFormKeyChange(value) {
  properties.formKey = value || ''
  markDirty()
}

async function openNodeFormDesigner() {
  properties.formType = 'dynamic'
  nodeFormDesignerSchema.value = cloneValue(await resolveNodeFormRules()) || []
  showNodeFormDesigner.value = true
}

async function openNodeFormPreview() {
  const rules = await resolveNodeFormRules()
  if (!rules.length) {
    window.$message?.warning('暂无可预览的节点表单')
    return
  }
  nodeFormPreviewSchema.value = cloneValue(rules) || []
  showNodeFormPreview.value = true
}

function handleSaveNodeFormSchema(schema) {
  const rules = Array.isArray(schema) ? cloneValue(schema) : []
  properties.formType = 'dynamic'
  if (!properties.formKey)
    properties.formKey = buildNodeFormKey()
  properties.formJson = JSON.stringify(rules)
  updateFormType()
  showNodeFormDesigner.value = false
  isDirty.value = false
  window.$message?.success('节点表单已保存到当前节点')
}

function clearNodeInlineForm() {
  properties.formJson = ''
  updateFormType()
  isDirty.value = false
  window.$message?.success('已清空节点内嵌表单')
}

async function resolveNodeFormRules() {
  const inlineRules = normalizeFormCreateRules(properties.formJson)
  if (inlineRules.length || !properties.formKey)
    return inlineRules
  const schema = await loadFormSchemaByKey(properties.formKey)
  return normalizeFormCreateRules(schema)
}

async function loadFormSchemaByKey(formKey) {
  if (!formKey)
    return []
  try {
    const res = await flowApi.getFormByKey(formKey)
    if (res.code === 200 && res.data?.formSchema)
      return res.data.formSchema
  }
  catch (error) {
    console.error('加载引用表单失败:', error)
    window.$message?.warning('引用表单加载失败，请检查表单Key')
  }
  return []
}

function buildNodeFormKey() {
  const rawKey = `${properties.id || rawElement.value?.id || 'user_task'}_form`
  return rawKey.replace(/[^\w-]/g, '_')
}

function countFormRules(rules) {
  if (!Array.isArray(rules))
    return 0
  return rules.reduce((count, rule) => {
    const childCount = Array.isArray(rule.children) ? countFormRules(rule.children) : 0
    return count + 1 + childCount
  }, 0)
}

// 更新表单类型
function updateFormType() {
  if (!rawElement.value || !props.modeler)
    return

  const modeling = props.modeler.get('modeling')

  if (properties.formType === 'none') {
    properties.formKey = ''
    properties.formJson = ''
    properties.formUrl = ''
    modeling.updateProperties(rawElement.value, {
      'flowable:formKey': null,
      'flowable:formJson': null,
      'flowable:formUrl': null,
    })
  }
  else if (properties.formType === 'external') {
    properties.formKey = ''
    properties.formJson = ''
    modeling.updateProperties(rawElement.value, {
      'flowable:formKey': null,
      'flowable:formJson': null,
      'flowable:formUrl': properties.formUrl, // ✅ 保存外部表单URL
    })
  }
  else if (properties.formType === 'dynamic') {
    properties.formUrl = ''
    modeling.updateProperties(rawElement.value, {
      'flowable:formUrl': null,
      'flowable:formKey': properties.formKey || null,
      'flowable:formJson': properties.formJson || null,
    })
  }
  emit('update')
}

// 更新用户任务审批人
function updateUserTaskAssignee() {
  if (!rawElement.value || !props.modeler)
    return

  const modeling = props.modeler.get('modeling')
  let value = properties.assignee
  let assigneeType = null

  // 处理不同的审批人类型
  if (properties.assignee === 'custom') {
    value = properties.assigneeExpr
    assigneeType = 'custom'
  }
  else if (properties.assignee === 'spel') {
    value = properties.assigneeExpr
    assigneeType = 'spel'
  }

  const element = rawElement.value

  modeling.updateProperties(element, {
    'flowable:assignee': value || null,
    'flowable:assigneeType': assigneeType,
    'flowable:assigneeName': properties.assigneeUserName || null,
    'flowable:spelTemplate': selectedSpelTemplate.value || null,
    'flowable:candidateUsers': null,
    'flowable:candidateUserNames': null,
    'flowable:candidateGroups': null,
    'flowable:candidateGroupNames': null,
  })
  emit('update')
}

// 更新候选用户
function updateCandidateUsers() {
  if (!rawElement.value || !props.modeler)
    return

  const modeling = props.modeler.get('modeling')
  const element = rawElement.value
  const usersStr = properties.candidateUsers.join(',')
  const userNamesStr = properties.candidateUserNames.join(',')

  modeling.updateProperties(element, {
    'flowable:assignee': null,
    'flowable:assigneeType': null,
    'flowable:assigneeName': null,
    'flowable:spelTemplate': null,
    'flowable:candidateUsers': usersStr || null,
    'flowable:candidateUserNames': userNamesStr || null,
    'flowable:candidateGroups': null,
    'flowable:candidateGroupNames': null,
  })
  emit('update')
}

// 更新候选组
function updateCandidateGroups() {
  if (!rawElement.value || !props.modeler)
    return

  const modeling = props.modeler.get('modeling')
  const element = rawElement.value
  const groupsStr = properties.candidateGroups.join(',')
  const groupNamesStr = properties.candidateGroupNames.join(',')

  modeling.updateProperties(element, {
    'flowable:assignee': null,
    'flowable:assigneeType': null,
    'flowable:assigneeName': null,
    'flowable:spelTemplate': null,
    'flowable:candidateUsers': null,
    'flowable:candidateUserNames': null,
    'flowable:candidateGroups': groupsStr || null,
    'flowable:candidateGroupNames': groupNamesStr || null,
  })
  emit('update')
}

// 更新截止日期
function updateDueDate() {
  if (!rawElement.value || !props.modeler)
    return

  const modeling = props.modeler.get('modeling')
  modeling.updateProperties(rawElement.value, {
    'flowable:dueDate': properties.dueDate > 0 ? `P${properties.dueDate}D` : null,
  })
}

function updateApprovalControl() {
  if (!rawElement.value || !props.modeler)
    return

  const modeling = props.modeler.get('modeling')
  modeling.updateProperties(rawElement.value, {
    'flowable:allowApprove': properties.allowApprove,
    'flowable:allowReject': properties.allowReject,
    'flowable:allowDelegate': properties.allowDelegate,
    'flowable:allowReturn': properties.allowReturn,
    'flowable:allowTerminate': properties.allowTerminate,
    'flowable:requireSignature': properties.requireSignature,
    'flowable:requireComment': properties.requireComment,
  })
  emit('update')
}

// 更新多实例配置
function updateMultiInstance() {
  if (!rawElement.value || !props.modeler)
    return

  const moddle = props.modeler.get('moddle')
  const modeling = props.modeler.get('modeling')
  const bo = rawElement.value.businessObject

  if (properties.multiInstanceType === 'none') {
    modeling.updateProperties(rawElement.value, { loopCharacteristics: null })
    emit('update')
    return
  }

  // 构建完成条件
  let completionConditionStr = ''
  if (properties.completionCondition === 'all')
    completionConditionStr = '$' + '{nrOfCompletedInstances == nrOfInstances}'
  else if (properties.completionCondition === 'any')
    completionConditionStr = '$' + '{nrOfCompletedInstances >= 1}'
  else if (properties.completionCondition === 'rate')
    completionConditionStr = '$' + `{nrOfCompletedInstances / nrOfInstances >= ${(properties.passRate / 100).toFixed(2)}}`

  const loopCardinalityObj = moddle.create('bpmn:FormalExpression', { body: '$' + '{nrOfInstances}' })
  const completionCondObj = completionConditionStr
    ? moddle.create('bpmn:FormalExpression', { body: completionConditionStr })
    : null

  // 确保 loopCardinality 被正确序列化到 XML：
  // updateModdleProperties 适用于修改已有的 moddle 子元素
  if (bo.loopCharacteristics) {
    modeling.updateModdleProperties(rawElement.value, bo.loopCharacteristics, {
      isSequential: properties.multiInstanceType === 'sequential',
      loopCardinality: loopCardinalityObj,
      completionCondition: completionCondObj,
    })
  }
  else {
    // 首次创建：用 updateProperties 设置整个 loopCharacteristics
    const lc = moddle.create('bpmn:MultiInstanceLoopCharacteristics', {
      isSequential: properties.multiInstanceType === 'sequential',
      loopCardinality: loopCardinalityObj,
      completionCondition: completionCondObj,
    })
    modeling.updateProperties(rawElement.value, { loopCharacteristics: lc })
  }

  emit('update')
}

// 添加任务监听器
function addTaskListener() {
  properties.taskListeners.push({
    event: 'create',
    class: '',
  })
}

// 移除任务监听器
function removeTaskListener(index) {
  properties.taskListeners.splice(index, 1)
  updateTaskListeners()
}

// 更新任务监听器
function updateTaskListeners() {
  if (!rawElement.value || !props.modeler)
    return

  const moddle = props.modeler.get('moddle')
  const modeling = props.modeler.get('modeling')
  const bo = rawElement.value.businessObject

  // 保留执行监听器，合并任务监听器
  const existingExtValues = bo.extensionElements?.values || []
  const executionListeners = existingExtValues.filter(v => v.$type === 'flowable:ExecutionListener')

  const taskListeners = properties.taskListeners
    .filter(l => l.class)
    .map(l => moddle.create('flowable:TaskListener', {
      event: l.event,
      class: l.class,
    }))

  const allValues = [...executionListeners, ...taskListeners]

  const extensionElements = moddle.create('bpmn:ExtensionElements', { values: allValues })
  modeling.updateProperties(rawElement.value, { extensionElements })
  emit('update')
}

// 添加执行监听器
function addExecutionListener() {
  properties.executionListeners.push({
    event: 'start',
    class: '',
  })
}

// 移除执行监听器
function removeExecutionListener(index) {
  properties.executionListeners.splice(index, 1)
}

// 更新执行监听器
function updateExecutionListeners() {
  if (!rawElement.value || !props.modeler)
    return

  const moddle = props.modeler.get('moddle')
  const modeling = props.modeler.get('modeling')
  const bo = rawElement.value.businessObject

  // 保留任务监听器，合并执行监听器
  const existingExtValues = bo.extensionElements?.values || []
  const taskListeners = existingExtValues.filter(v => v.$type === 'flowable:TaskListener')

  const executionListeners = properties.executionListeners
    .filter(l => l.class)
    .map(l => moddle.create('flowable:ExecutionListener', {
      event: l.event,
      class: l.class,
    }))

  const allValues = [...taskListeners, ...executionListeners]

  const extensionElements = moddle.create('bpmn:ExtensionElements', { values: allValues })
  modeling.updateProperties(rawElement.value, { extensionElements })
  emit('update')
}

// 更新服务任务实现
function updateServiceImplementation() {
  if (!rawElement.value || !props.modeler)
    return

  const modeling = props.modeler.get('modeling')
  const updateProps = {
    'flowable:class': null,
    'flowable:expression': null,
    'flowable:delegateExpression': null,
  }

  const key = `flowable:${properties.implementationType}`
  updateProps[key] = properties.implementation || null

  modeling.updateProperties(rawElement.value, updateProps)
  emit('update')
}

function toggleCarbonCopyService(checked) {
  properties.flowableType = checked ? 'cc' : ''
  if (checked) {
    properties.ccReceiverType = properties.ccReceiverType || 'users'
    if (!properties.implementation) {
      properties.implementationType = 'delegateExpression'
    }
  }
  else {
    properties.candidateUsers = []
    properties.candidateUserNames = []
    properties.candidateGroups = []
    properties.candidateGroupNames = []
    properties.ccReceiverType = 'users'
    properties.ccExpression = ''
    properties.ccExpressionTarget = 'users'
    if (properties.implementationType === 'delegateExpression' && !properties.implementation) {
      properties.implementationType = 'class'
    }
  }
  updateCarbonCopyConfig()
}

function updateCarbonCopyConfig() {
  if (!rawElement.value || !props.modeler)
    return

  const modeling = props.modeler.get('modeling')
  const isCarbonCopy = properties.flowableType === 'cc'
  if (isCarbonCopy && properties.ccReceiverType === 'expression') {
    applyCarbonCopyExpressionToProperties()
  }
  const usersStr = properties.candidateUsers.join(',')
  const userNamesStr = properties.candidateUserNames.join(',')
  const groupsStr = properties.candidateGroups.join(',')
  const groupNamesStr = properties.candidateGroupNames.join(',')
  const updateProps = {
    'flowable:type': isCarbonCopy ? 'cc' : null,
    'flowable:ccReceiverType': isCarbonCopy ? properties.ccReceiverType : null,
    'flowable:ccExpressionTarget': isCarbonCopy ? properties.ccExpressionTarget : null,
    'flowable:candidateUsers': isCarbonCopy && usersStr ? usersStr : null,
    'flowable:candidateUserNames': isCarbonCopy && userNamesStr ? userNamesStr : null,
    'flowable:candidateGroups': isCarbonCopy && groupsStr ? groupsStr : null,
    'flowable:candidateGroupNames': isCarbonCopy && groupNamesStr ? groupNamesStr : null,
  }
  if (isCarbonCopy && !properties.implementation) {
    updateProps['flowable:delegateExpression'] = '$' + '{flowCcNodeDelegate}'
  }
  if (!isCarbonCopy && !properties.implementation) {
    updateProps['flowable:delegateExpression'] = null
  }
  modeling.updateProperties(rawElement.value, updateProps)
  emit('update')
}

// 更新异步
function updateAsync() {
  if (!rawElement.value || !props.modeler)
    return

  const modeling = props.modeler.get('modeling')
  modeling.updateProperties(rawElement.value, {
    'flowable:async': properties.async,
  })
  emit('update')
}

// 获取实现方式占位符
function getImplementationPlaceholder() {
  const placeholders = {
    class: 'com.example.MyServiceTask',
    expression: '${' + 'myService.execute()}',
    delegateExpression: '${' + 'myServiceDelegate}',
  }
  return placeholders[properties.implementationType] || ''
}

// 切换条件
function toggleCondition(checked) {
  if (!checked) {
    properties.condition = ''
    properties.conditionPreset = 'custom'
    updateCondition()
    return
  }
  syncConditionPresetFromCondition()
}

// 更新条件类型
function updateConditionType() {
  properties.condition = ''
  properties.script = ''
  properties.conditionPreset = 'custom'
  updateCondition()
}

// 更新流转条件
function updateCondition() {
  if (!rawElement.value || !props.modeler)
    return

  const moddle = props.modeler.get('moddle')
  const modeling = props.modeler.get('modeling')

  if (properties.conditionType === 'expression' && properties.condition) {
    const expr = moddle.create('bpmn:FormalExpression', { body: properties.condition })
    modeling.updateProperties(rawElement.value, { conditionExpression: expr })
  }
  else if (properties.conditionType === 'script' && properties.script) {
    const expr = moddle.create('bpmn:FormalExpression', {
      body: properties.script,
      language: properties.scriptFormat,
    })
    modeling.updateProperties(rawElement.value, { conditionExpression: expr })
  }
  else {
    modeling.updateProperties(rawElement.value, { conditionExpression: null })
  }
  emit('update')
}

// 切换默认路径
function toggleDefault(checked) {
  if (!rawElement.value || !props.modeler)
    return

  const modeling = props.modeler.get('modeling')
  const bo = rawElement.value.businessObject

  if (checked) {
    // 设置为默认流
    modeling.updateProperties(bo.sourceRef, {
      default: rawElement.value,
    })
  }
  else {
    // 取消默认流
    modeling.updateProperties(bo.sourceRef, {
      default: null,
    })
  }
}

// 自定义审批人表达式
// 应用 SPEL 表达式模板
function applySpelTemplate(value) {
  if (value) {
    properties.assigneeExpr = value
    selectedSpelTemplate.value = value
    updateUserTaskAssignee()
  }
}

// 验证 SPEL 表达式
function validateSpelExpression() {
  spelValidationError.value = ''

  if (!properties.assigneeExpr || !properties.assigneeExpr.trim()) {
    return
  }

  const expr = properties.assigneeExpr.trim()

  // 检查是否包含 ${} 包裹
  if (!expr.startsWith('$' + '{') || !expr.endsWith('}')) {
    spelValidationError.value = 'SPEL 表达式必须使用 $' + '{} 包裹'
    return
  }

  // 检查括号匹配
  const openCount = (expr.match(/\(/g) || []).length
  const closeCount = (expr.match(/\)/g) || []).length
  if (openCount !== closeCount) {
    spelValidationError.value = '括号不匹配，请检查表达式语法'
    return
  }

  // 验证通过，更新到节点
  updateUserTaskAssignee()
}

// 更新基础属性（id/name/documentation）时也触发 emit
function updateProperty(prop) {
  if (!rawElement.value || !props.modeler)
    return

  const modeling = props.modeler.get('modeling')
  const moddle = props.modeler.get('moddle')

  if (prop === 'id') {
    modeling.updateProperties(rawElement.value, { id: properties.id })
  }
  else if (prop === 'name') {
    modeling.updateProperties(rawElement.value, { name: properties.name })
  }
  else if (prop === 'documentation') {
    const docs = properties.documentation
      ? [moddle.create('bpmn:Documentation', { text: properties.documentation })]
      : []
    modeling.updateProperties(rawElement.value, { documentation: docs })
  }
  emit('update')
}
</script>

<style scoped>
.node-properties-panel {
  min-height: 100%;
  overflow: visible;
  padding: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
}

/* Tab横向滚动容器 */
.tabs-wrapper {
  flex-shrink: 0;
  border-bottom: 1px solid #eef2f7;
  margin-bottom: 0;
  background: #fff;
}

.tabs-toolbar {
  min-height: 36px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  padding: 4px 12px 0;
  background: #f8fafc;
}

.tabs-position {
  min-width: 76px;
  text-align: center;
  font-size: 12px;
  color: #667085;
  white-space: nowrap;
}

.tabs-shell {
  position: relative;
  overflow: hidden;
}

.tabs-shell.has-next::after {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 36px;
  pointer-events: none;
  background: linear-gradient(90deg, rgba(255, 255, 255, 0), #fff 72%);
}

.config-tabs {
  width: 100%;
}

.action-control-group {
  margin-bottom: 12px;
}

.action-control-title {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}

.action-switch-list {
  display: grid;
  gap: 8px;
}

.action-switch-row {
  min-height: 34px;
  padding: 7px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.action-switch-main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: #334155;
}

.action-switch-main i {
  width: 16px;
  height: 16px;
  color: #64748b;
  flex-shrink: 0;
}

.node-form-builder-card {
  width: 100%;
  padding: 12px;
  border: 1px solid #d8dee8;
  border-radius: 8px;
  background: #f8fafc;
}

.node-form-builder-main {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-width: 0;
}

.node-form-builder-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid #cbd5e1;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #2563eb;
  flex-shrink: 0;
}

.node-form-builder-icon i {
  width: 18px;
  height: 18px;
}

.node-form-builder-copy {
  min-width: 0;
}

.node-form-builder-title {
  font-size: 13px;
  font-weight: 700;
  color: #172033;
}

.node-form-builder-desc {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.5;
  color: #667085;
}

.node-form-builder-actions {
  margin-top: 12px;
}

.node-form-json-collapse {
  margin-top: -4px;
}

.node-form-json-collapse :deep(.n-collapse-item__header) {
  font-size: 12px;
  color: #475569;
}

.field-catalog-tip {
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.approval-result-options {
  width: 100%;
  display: grid;
  gap: 8px;
}

.approval-result-option {
  width: 100%;
  margin: 0;
}

.approval-result-card {
  width: 100%;
  min-height: 54px;
  padding: 9px 10px;
  border: 1px solid #d8dee8;
  border-radius: 8px;
  background: #fff;
  transition:
    border-color 0.16s ease,
    background-color 0.16s ease;
}

.approval-result-option :deep(.n-radio__label) {
  flex: 1;
  min-width: 0;
}

.approval-result-card.active {
  border-color: #2563eb;
  background: #eff6ff;
}

.approval-result-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 700;
  color: #172033;
}

.approval-result-title i {
  width: 16px;
  height: 16px;
  color: #2563eb;
}

.approval-result-desc {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.45;
  color: #64748b;
}

.advanced-condition-collapse {
  margin-top: -4px;
}

.advanced-condition-collapse :deep(.n-collapse-item__header) {
  font-size: 12px;
  color: #475569;
}

.carbon-copy-source-switch {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border: 1px solid #d8dee8;
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
}

.carbon-copy-source-button {
  min-width: 0;
  height: 32px;
  border: 0;
  border-right: 1px solid #d8dee8;
  background: transparent;
  color: #344054;
  font-size: 12px;
  line-height: 32px;
  text-align: center;
  cursor: pointer;
  transition:
    color 0.16s ease,
    background-color 0.16s ease,
    box-shadow 0.16s ease;
}

.carbon-copy-source-button:last-child {
  border-right: 0;
}

.carbon-copy-source-button.active {
  background: #eef4ff;
  color: #2563eb;
  box-shadow: inset 0 -2px 0 #2563eb;
  font-weight: 600;
}

.condition-builder {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 92px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.condition-operator {
  min-width: 0;
}

/* Tab导航栏可滚动 + 显示滚动条 */
.config-tabs :deep(.n-tabs-nav) {
  overflow-x: auto !important;
  overflow-y: hidden !important;
  white-space: nowrap !important;
  scrollbar-width: thin;
  scrollbar-color: #d1d5db transparent;
  min-height: 46px;
  padding: 0 46px 0 18px;
  border-bottom: none;
}

/* 自定义滚动条样式（Webkit） */
.config-tabs :deep(.n-tabs-nav::-webkit-scrollbar) {
  height: 6px;
}

.config-tabs :deep(.n-tabs-nav::-webkit-scrollbar-track) {
  background: transparent;
}

.config-tabs :deep(.n-tabs-nav::-webkit-scrollbar-thumb) {
  background: #d1d5db;
  border-radius: 3px;
}

.config-tabs :deep(.n-tabs-nav::-webkit-scrollbar-thumb:hover) {
  background: #9ca3af;
}

/* Tab项不换行 */
.config-tabs :deep(.n-tabs-tab) {
  white-space: nowrap;
}

.panel-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--border, #e2e8f0);
  background: var(--surface, #ffffff);
  position: sticky;
  bottom: 0;
  margin-top: 8px;
  flex-shrink: 0;
}

.listener-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.listener-item {
  margin-bottom: 8px;
}

:deep(.n-tabs-pane-wrapper) {
  overflow: visible;
}

:deep(.n-tab-pane) {
  padding: 16px 18px 20px;
}

:deep(.n-form-item) {
  margin-bottom: 14px;
}

:deep(.n-form-item:last-child) {
  margin-bottom: 0;
}

:deep(.n-form-item .n-form-item-label) {
  min-height: 22px;
  padding: 0 0 6px;
  color: #111827;
  font-size: 13px;
  font-weight: 700;
}

:deep(.n-input),
:deep(.n-base-selection),
:deep(.n-input-number) {
  width: 100%;
}

:deep(.n-input .n-input-wrapper),
:deep(.n-base-selection .n-base-selection-label) {
  min-height: 38px;
}

:deep(.n-input__input-el),
:deep(.n-base-selection-input),
:deep(.n-base-selection-placeholder) {
  font-size: 14px;
}

.pass-rate-config {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.pass-rate-presets {
  display: flex;
}

.pass-rate-slider-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pass-rate-desc {
  font-size: 12px;
  color: #666;
  display: flex;
  align-items: center;
  background: #f0f7ff;
  border-radius: 4px;
  padding: 5px 8px;
}

@media (max-width: 720px) {
  .condition-builder {
    grid-template-columns: 1fr;
  }
}
</style>
