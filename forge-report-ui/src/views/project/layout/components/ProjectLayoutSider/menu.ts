import { reactive, h } from 'vue'
import { renderIcon } from '@/utils'
import { RouterLink } from 'vue-router'
import { PageEnum } from '@/enums/pageEnum'
import { MenuOption, MenuGroupOption } from 'naive-ui'
import { icon } from '@/plugins'

const { TvOutlineIcon, SparklesIcon, ImagesIcon,FolderOpenIcon } = icon.ionicons5
const { StoreIcon, ObjectStorageIcon, DevicesIcon } = icon.carbon
export const renderMenuLabel = (option: MenuOption | MenuGroupOption) => {
  return option.label
}

export const expandedKeys = () => ['all-project']

export const menuOptionsInit = () => {
  const t = window['$t']

  return reactive([
    {
      key: 'divider-1',
      type: 'divider',
    },
    {
      label: () => h('span', null, { default: () => t('project.project') }),
      key: 'all-project',
      icon: renderIcon(DevicesIcon),
      children: [
        {
          type: 'group',
          label: () => h('span', null, { default: () => t('project.my') }),
          key: 'my-project',
          children: [
            {
              label: () =>
                h(
                  RouterLink,
                  {
                    to: {
                      name: PageEnum.BASE_HOME_MATERIALS_NAME,
                    },
                  },
                  { default: () => '素材库' }
                ),
              key: PageEnum.BASE_HOME_MATERIALS_NAME,
              icon: renderIcon(ImagesIcon),
            },
            {
              label: () =>
                h(
                  RouterLink,
                  {
                    to: {
                      name: PageEnum.BASE_HOME_ITEMS_NAME,
                    },
                  },
                  { default: () => t('project.all_project') }
                ),
              key: PageEnum.BASE_HOME_ITEMS_NAME,
              icon: renderIcon(TvOutlineIcon),
            },
            {
              label: () =>
                h(
                  RouterLink,
                  {
                    to: {
                      name: PageEnum.BASE_HOME_DIRECTORY_NAME,
                    },
                  },
                  { default: () => t('project.directory_manage') }
                ),
              key: PageEnum.BASE_HOME_DIRECTORY_NAME,
              icon: renderIcon(FolderOpenIcon),
            },
            {
              label: () =>
                h(
                  RouterLink,
                  {
                    to: {
                      name: PageEnum.BASE_HOME_TEMPLATE_NAME,
                    },
                  },
                  { default: () => t('project.my_template') }
                ),
              key: PageEnum.BASE_HOME_TEMPLATE_NAME,
              icon: renderIcon(ObjectStorageIcon),
            },
          ],
        },
      ],
    },

    {
      key: 'divider-2',
      type: 'divider',
    },
    {
      label: () =>
        h(
          RouterLink,
          {
            to: {
              name: PageEnum.BASE_HOME_TEMPLATE_MARKET_NAME,
            },
          },
          { default: () => t('project.template_market') }
        ),
      key: PageEnum.BASE_HOME_TEMPLATE_MARKET_NAME,
      icon: renderIcon(StoreIcon),
    },
  ])
}
