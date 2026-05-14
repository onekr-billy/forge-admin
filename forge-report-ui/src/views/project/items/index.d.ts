export type Chartype = {
  id: number | string
  directoryId?: number | string
  title: string
  label: string
  release: boolean
  publishUrl?: string
  createTime?: string
  indexImg?: string
}

export type ChartList = Chartype[]
