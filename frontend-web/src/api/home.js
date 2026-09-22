import request from './request'
/**
 * 获取首页金融产品列表
 * 对应后端接口：GET /api/home/products
 * 后端返回示例（二选一）：
 * 1. 直接数组：[ { id, name, bankName, rate, amountRange, termRange, condition } ]
 * 2. 统一包装：{ code: 200, data: [ ... ] }
 */
export function getHomeProducts() {
  return request.get('/home/products')
}

export function getHomeNews(){

}