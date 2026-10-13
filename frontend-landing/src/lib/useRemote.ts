import { useEffect, useState } from 'react'

/**
 * 远端数据状态。
 *
 * 刻意区分 ok 与 error：**「接口通了但确实没数据」和「接口挂了」是两件事** ——
 * 前者该显示「暂无」，后者该显示「平台接口暂不可用」。糊成一个状态的话，
 * 演示时看到空白区域根本分不清是后端没起还是库里真的没数据。
 */
export type RemoteState = 'loading' | 'ok' | 'error'

/**
 * 状态标注文案，供各区块角落的小字统一使用。
 *
 * ★ ok 态**刻意留空**（2026-10-14 用户要求「去掉提示词」）：
 * 「数据实时同步自平台接口」属于**实现细节**，落地页是对外展示位，不该亮给访客看。
 * 留空而不是删掉这个 key，是为了让各调用方仍然只按 state 取一次文案、缺省即不渲染。
 *
 * ★ loading / error **必须保留** —— 接口挂了却一声不吭，页面空白会让人以为是「没数据」，
 * 这正是本文件开头区分 ok 与 error 的初衷，别顺手一起删了。
 */
export const REMOTE_STATUS_TEXT: Record<RemoteState, string> = {
  loading: '正在同步平台数据…',
  ok: '',
  error: '平台接口暂不可用',
}

/**
 * 只取平台真实数据，**不做任何本地演示兜底**。
 *
 * 为什么去掉了「静态兜底」：
 * 先前这里是「接口优先，静态兜底」——接口报错**或返回空数组**时，静默保留传入的
 * 本地演示数据。结果是页面在演示时永远好看，但观众看到的每一处都可能不是平台里
 * 真实存在的东西：编出来的合作社名、编出来的价格、编出来的农户数量，
 * 而且没有任何迹象能看出来 —— 不报错、不提示、控制台也干净。
 *
 * 落地页承担的是「平台对外展示」职责，页面上出现的每个商品、每个数字，
 * 都必须能在数据库里查到。所以这里改成：接口成功就渲染接口给的数据（**哪怕是空数组**），
 * 失败就把失败摆在明面上，由调用方渲染空态 / 失败态。宁可显示「暂无」，也不显示编的。
 *
 * load 只在挂载时跑一次（答辩场景没有实时刷新需求，避免无谓重渲染）。
 */
export function useRemote<T>(load: () => Promise<T>, initial: T) {
  const [data, setData] = useState<T>(initial)
  const [state, setState] = useState<RemoteState>('loading')

  useEffect(() => {
    let alive = true
    load()
      .then((value) => {
        if (!alive) return
        setData(value)
        setState('ok')
      })
      .catch(() => {
        if (!alive) return
        // 不覆盖 data：调用方靠 state 决定渲染空态还是失败态
        setState('error')
      })
    return () => {
      alive = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return { data, state, loading: state === 'loading' }
}
