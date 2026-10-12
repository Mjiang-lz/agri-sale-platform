/**
 * 上传前的图片压缩。
 *
 * 背景：后端 `ImageFileValidator` 只做「5MB 上限 + 文件头识别格式」，
 * **完全不压缩**。手机拍的照片普遍 2–3MB、长边 4000px 以上，原样落盘会连带三个后果：
 *   1. 对象存储的**存储费**与**公网流出流量费**被放大 10 倍量级（流量费是 OBS 唯一的成本大头）；
 *   2. 上传慢（走一遍上行带宽），弱网时用户会以为卡死了；
 *   3. 商品列表/首页一次加载十几张原图，**首屏很慢**。
 * 而它们在页面上实际只显示到几百像素宽 —— 属于「传上去就注定被缩着看」的图。
 *
 * 所以放在**浏览器侧**做：省的不只是存储，连**上传流量**一起省了。
 * 服务端压缩做不到这一点（字节已经传上去了）。
 *
 * ★ 三个必须处理的坑（都不是理论问题）：
 *
 * 1. **EXIF 方向**：手机竖拍的照片，像素其实是横的，靠 EXIF 的 Orientation 标记"转正"。
 *    直接 `drawImage` 到 canvas **可能丢掉这个标记**，结果用户传完发现自己变成躺着的。
 *    这里用 `createImageBitmap(file, { imageOrientation: 'from-image' })` 让浏览器
 *    在解码阶段就把方向烘焙进像素；老浏览器回退到 `<img>`（现代浏览器对 `<img>`
 *    默认也是 `image-orientation: from-image`，两条路都能转正）。
 *
 * 2. **GIF 不能碰**：canvas 只会取第一帧，压完动画就没了。直接把 GIF 原样放行。
 *
 * 3. **PNG 的透明通道**：无脑转 JPEG 会把透明背景刷成黑块。
 *    所以 PNG 要先**探测是否真的含透明像素** —— 有就保持 PNG，没有才转 JPEG（体积差很多）。
 *
 * ★ 输出格式用 canvas 重新编码，文件头天然就是真实的 JPEG/PNG，
 *   能过后端那个「按 magic number 判类型」的校验（它不认文件名后缀与 Content-Type）。
 */

/** 压缩目标：长边上限（px）。超出才缩放，不放大。 */
const DEFAULT_MAX_DIMENSION = 1600
/** JPEG 质量。0.82 是肉眼看不出差别、体积已经明显下降的常用拐点。 */
const DEFAULT_QUALITY = 0.82
/** 低于这个体积就不折腾了（再压省不了多少，还有损）。 */
const SKIP_SIZE = 300 * 1024
/** 服务端 ImageFileValidator.MAX_SIZE = 5MB。留点余量，压到 4.5MB 以内。 */
const TARGET_MAX_SIZE = 4.5 * 1024 * 1024
/** 画布重编码不会丢动画以外的信息，但 GIF 一定会掉动画，直接放行。 */
const PASSTHROUGH_TYPES = new Set(['image/gif'])

/**
 * 解码成可绘制对象，并**把 EXIF 方向烘焙进去**。
 * @returns {Promise<{source: ImageBitmap|HTMLImageElement, width: number, height: number, release: Function}>}
 */
async function decode(file) {
  if (typeof createImageBitmap === 'function') {
    try {
      const bmp = await createImageBitmap(file, { imageOrientation: 'from-image' })
      return { source: bmp, width: bmp.width, height: bmp.height, release: () => bmp.close() }
    } catch {
      // 某些浏览器/某些图片格式（如带 EXIF 的 HEIC 转码）会在这里失败，走 <img> 兜底
    }
  }
  const url = URL.createObjectURL(file)
  try {
    const img = await new Promise((resolve, reject) => {
      const el = new Image()
      el.onload = () => resolve(el)
      el.onerror = () => reject(new Error('图片解码失败'))
      el.src = url
    })
    return {
      source: img,
      width: img.naturalWidth,
      height: img.naturalHeight,
      release: () => URL.revokeObjectURL(url)
    }
  } catch (e) {
    URL.revokeObjectURL(url)
    throw e
  }
}

/** PNG 是否含透明像素。含则必须保持 PNG，否则透明区会变成黑块。 */
function hasTransparency(ctx, width, height) {
  const { data } = ctx.getImageData(0, 0, width, height)
  // 只扫 alpha 通道（每像素第 4 字节）
  for (let i = 3; i < data.length; i += 4) {
    if (data[i] < 255) return true
  }
  return false
}

const canvasToBlob = (canvas, type, quality) =>
  new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('图片编码失败'))),
      type,
      quality
    )
  })

/** 按长边上限算出目标尺寸，**只缩不放** */
function fitSize(width, height, maxDimension) {
  const longest = Math.max(width, height)
  if (longest <= maxDimension) return { width, height }
  const ratio = maxDimension / longest
  return { width: Math.round(width * ratio), height: Math.round(height * ratio) }
}

/**
 * 压缩一张图片。失败或"压完反而更大"时**原样返回**，绝不阻断上传。
 *
 * @param {File|Blob} file 浏览器选中的原始文件
 * @param {{maxDimension?: number, quality?: number, skipUnderKB?: number}} [options]
 * @returns {Promise<File|Blob>} 压缩后的文件；不值得压 / 压不动时返回入参本身
 */
export async function compressImage(file, options = {}) {
  const maxDimension = options.maxDimension ?? DEFAULT_MAX_DIMENSION
  const quality = options.quality ?? DEFAULT_QUALITY
  const skipUnder = (options.skipUnderKB ?? SKIP_SIZE / 1024) * 1024

  // 不是图片、是 GIF、本来就很小 —— 三种情况都不必压
  if (!file || !String(file.type || '').startsWith('image/')) return file
  if (PASSTHROUGH_TYPES.has(file.type)) return file
  if (file.size <= skipUnder) return file

  let decoded = null
  let canvas = null
  try {
    decoded = await decode(file)
    const { width, height } = fitSize(decoded.width, decoded.height, maxDimension)

    canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const ctx = canvas.getContext('2d', { willReadFrequently: true })
    if (!ctx) return file

    /**
     * 把「白底垫在像素下面」这件事做成可重复调用的一步。
     * 用 destination-over 而不是「clearRect + fillRect + drawImage 重画一遍」：
     * 后者要多做一次全尺寸绘制，而且中间态（清空后未画）如果出错就是一张白图。
     */
    const flattenOntoWhite = () => {
      ctx.globalCompositeOperation = 'destination-over'
      ctx.fillStyle = '#ffffff'
      ctx.fillRect(0, 0, width, height)
      ctx.globalCompositeOperation = 'source-over'
    }

    // ① 先**原样**画上去 —— 只有此时画布上的 alpha 才是图片真实的 alpha。
    ctx.drawImage(decoded.source, 0, 0, width, height)

    // ★ 透明判断必须发生在铺白底之前。
    //   曾经的写法是「先 fillRect('#ffffff') 再 drawImage」，白底会把整个画布的
    //   alpha 刷成 255，于是 hasTransparency() 永远返回 false ——
    //   带透明的 PNG 被误判成不透明、转成 JPEG，透明区变成黑/白块。
    //   这个 bug 是被 /tmp/probe-compress.mjs 的 T2 用例抓出来的（T1、T3–T8 当时全绿）。
    const keepPng = file.type === 'image/png' && hasTransparency(ctx, width, height)
    let outputType = keepPng ? 'image/png' : 'image/jpeg'

    // ② 确定要转 JPEG 了，才补白底：JPEG 没有 alpha 通道，
    //    不铺底的话半透明像素会被编码成黑色。
    if (!keepPng) flattenOntoWhite()

    let blob = await canvasToBlob(canvas, outputType, keepPng ? undefined : quality)

    // ③ 兜底一：不透明的图压完仍超服务端上限 → 逐级降质量重试。
    //    （PNG 的 quality 参数无效，所以这一步只对 JPEG 有意义）
    if (!keepPng && blob.size > TARGET_MAX_SIZE) {
      for (const q of [0.7, 0.6]) {
        const retry = await canvasToBlob(canvas, 'image/jpeg', q)
        if (retry.size < blob.size) blob = retry
        if (blob.size <= TARGET_MAX_SIZE) break
      }
    }

    // ④ 兜底二：透明 PNG 怎么压都超限额（现实里只有"大面积照片存成带透明 PNG"才会走到）。
    //    此时宁可**牺牲透明通道**也不能让上传失败 —— 一张传不上去的图对用户毫无价值。
    //    能到这里的基本没有有效透明信息，且会打警告日志，不是静默降级。
    if (keepPng && blob.size > TARGET_MAX_SIZE) {
      console.warn(
        '[image-compress] 带透明的 PNG 压不进 4.5MB 限额，降级为白底 JPEG：',
        `${width}×${height}，${(blob.size / 1048576).toFixed(1)}MB`
      )
      flattenOntoWhite()
      const flat = await canvasToBlob(canvas, 'image/jpeg', 0.75)
      if (flat.size < blob.size) {
        blob = flat
        outputType = 'image/jpeg'
      }
    }

    // 压完反而更大（已优化过的图很常见）→ 用原图
    if (blob.size >= file.size) return file

    const name = renameFor(file.name, outputType)
    if (typeof File === 'function') {
      return new File([blob], name, { type: outputType, lastModified: Date.now() })
    }
    blob.name = name
    return blob
  } catch (error) {
    // 压缩是"锦上添花"，不能因为它失败就让用户传不了图
    console.warn('[image-compress] 压缩失败，改用原图上传：', error)
    return file
  } finally {
    if (decoded) decoded.release()
    if (canvas) {
      canvas.width = 0
      canvas.height = 0
    }
  }
}

/** 改扩展名以匹配真实编码结果（后端反正会重命名成 UUID，这里只为可读性） */
function renameFor(originalName, mime) {
  const base = String(originalName || 'image').replace(/\.[^.]+$/, '')
  return `${base}.${mime === 'image/png' ? 'png' : 'jpg'}`
}
