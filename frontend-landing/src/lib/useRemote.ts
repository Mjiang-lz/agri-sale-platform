import { useEffect, useState } from 'react'

export type DataSource = 'api' | 'demo'

/**
 * 「接口优先，静态兜底」。
 *
 * 落地页要能随时演示：后端没起、接口超时、返回空数组时，一律回退到本地演示数据，
 * 页面不会白屏；接口通了就换成真数据，并把 source 置为 'api' 供界面标注。
 *
 * load 只在挂载时跑一次（答辩场景没有实时刷新需求，避免无谓重渲染）。
 */
export function useRemote<T>(
  load: () => Promise<T>,
  fallback: T,
  isEmpty: (value: T) => boolean = (value) => (Array.isArray(value) ? value.length === 0 : value == null),
) {
  const [data, setData] = useState<T>(fallback)
  const [source, setSource] = useState<DataSource>('demo')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let alive = true
    load()
      .then((value) => {
        if (!alive) return
        if (!isEmpty(value)) {
          setData(value)
          setSource('api')
        }
      })
      .catch(() => {
        // 接口不通 / 未登录 / 超时：保持演示数据，不打扰用户
      })
      .finally(() => {
        if (alive) setLoading(false)
      })
    return () => {
      alive = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return { data, source, loading }
}
