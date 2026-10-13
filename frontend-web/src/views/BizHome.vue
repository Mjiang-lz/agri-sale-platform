<template>
  <div class="biz-home">

    <!-- ================================================================
         业务端首页
         ------------------------------------------------------------
         这个页面回答两件事，顺序不能反：
           ① 平台上现在有什么好东西（看得见的实物、图片、专家照片）
           ② 我自己手上有几件事在办（真实数字，来自各自业务接口）

         为什么先放「平台有什么」：农户一进后台，最想知道的是
         「我的脐橙现在卖得到什么价、谁在收、有没有人教我种」，
         而不是「我上周提交了一笔申请」。所以可视化内容占据首屏，
         个人业务数据压缩成一条数据带，但一个都不少。

         版式参考生鲜电商首页：业务数据带 → 通栏轮播 → 品类快捷入口 →
         实物大图商品卡（图占卡片约 52%）→ 产地求购 → 专家照片墙 →
         惠农信贷 → 农技知识收尾。不做窄屏塌缩，只面向桌面端。

         ★ 顶部为什么是「业务数据带」（而不是问候语或资讯）：
         这里先后放过三样东西，最终只留下数据带 ——
           · 「早上好，X」+ 待办胶囊：是「我的东西」，已挪去个人中心；
           · 「农技知识」：是「逛完之后再看的内容」，2026-10-10 挪到页面末尾；
           · 「业务数据带」：一进页面就回答「我手上还有几件事」，留在顶部。
         ================================================================ -->

    <!-- ---------- 业务数据带 ----------
         原「指标条」与「我的业务」两块显示的是同一批数字（采购 0 / 卖出 4 /
         融资 2 / 已通过 2 vs 融资 2 / 订单 4 / 预约 1），合计占 383px。
         合并成一条紧凑数据带：一张卡一个业务域，主数字 + 一行摘要，
         原来多出来的数字全部并进摘要行。点击直达该业务的列表页。 -->
    <section v-if="bizTiles.length" class="biz-bar" :class="{ 'is-solo': bizTiles.length === 1 }">
      <button
        v-for="t in bizTiles"
        :key="t.key"
        type="button"
        class="bt-item"
        :class="`is-${t.tone}`"
        @click="go(t.path)"
      >
        <span class="bt-num">{{ t.value }}</span>
        <span class="bt-text">
          <span class="bt-label">{{ t.label }}</span>
          <span class="bt-sub">{{ t.sub }}</span>
        </span>
      </button>
    </section>

    <!-- ---------- 空态 ----------
         原来是一张整宽的大白卡片（148px）压在首屏最上方，
         一句话就把轮播推到 430px。改成一条紧凑提示条，
         占的高度与「业务数据带」一致，首屏结构不因数据为空而变。 -->
    <div v-else-if="isEmptyState" class="empty-bar">
      <div class="eb-text">
        <strong>{{ emptyTitle }}</strong>
        <span>{{ emptyDesc }}</span>
      </div>
      <el-button type="primary" plain size="small" @click="go(emptyAction.path)">
        {{ emptyAction.label }}
      </el-button>
    </div>

    <!-- ================================================================
         首屏：轮播产地实拍大图。生鲜电商首页的标准第一屏。
         左侧文案 + 搜索，右侧 4 张实景方图，中间一张通栏大图。
         ================================================================ -->
    <section class="hero">
      <div class="hero-main">
        <img :src="HERO_IMAGES[heroIdx]" :alt="HERO_ALT[heroIdx]" />
        <div class="hero-mask"></div>
        <div class="hero-text">
          <p class="hero-kicker">{{ heroKicker }}</p>
          <h3>{{ heroTitle }}</h3>
          <p class="hero-desc">{{ heroDesc }}</p>
          <div class="hero-search">
            <!-- ★ placeholder 只承诺 doSearch 真能兑现的范围：商品名 / 品类 / 产地。
                 2026-10-12：原文案末尾还有一个「专家」，但 doSearch 一律跳
                 /trade/products（商品页），后端 keyword 也只 LIKE 商品名/品类/产地
                 （TradeServiceImpl#pageProducts），搜「王教授」只会得到空结果。
                 专家搜索其实已经有了，入口在 /expert/appointment 页（按姓名/领域/简介
                 前端过滤），不该由这个框冒充。 -->
            <el-input
              v-model="keyword"
              placeholder="搜农产品、产地"
              clearable
              @keyup.enter="doSearch"
            >
              <template #prefix><span class="hs-icon">🔍</span></template>
            </el-input>
            <el-button type="primary" class="hs-btn" @click="doSearch">搜索</el-button>
          </div>
          <div class="hero-hot">
            <span>热门：</span>
            <button v-for="h in hotWords" :key="h" type="button" class="hot-word" @click="searchBy(h)">
              {{ h }}
            </button>
          </div>
        </div>
        <div class="hero-dots">
          <button
            v-for="(s, i) in HERO_IMAGES"
            :key="i"
            type="button"
            class="hd-dot"
            :class="{ 'is-on': i === heroIdx }"
            :aria-label="`第 ${i + 1} 张`"
            @click="heroIdx = i"
          ></button>
        </div>
      </div>

      <!-- 右列：平台数据 + 快捷办理。
           原来这里是 4 张缩略图，就是轮播图本身的缩小版（纯冗余），
           却把整行撑到 677px，而 .hero-main 只有 360px。
           换成带标题的快捷入口，高度不变但内容不重复。 -->
      <div class="hero-side">
        <div class="hero-stats">
          <div v-for="s in platformStats" :key="s.label" class="hs-item">
            <span class="hss-num">{{ s.value }}</span>
            <span class="hss-label">{{ s.label }}</span>
          </div>
        </div>
        <div class="hero-quick" v-if="quickLinks.length">
          <h4 class="hq-title">快捷办理</h4>
          <button
            v-for="q in quickLinks"
            :key="q.label"
            type="button"
            class="hq-item"
            @click="go(q.path)"
          >
            <span class="hq-main">
              <strong>{{ q.label }}</strong>
              <i>{{ q.desc }}</i>
            </span>
            <span class="hq-arrow">→</span>
          </button>
        </div>
      </div>
    </section>

    <!-- ---------- 品类快捷入口 ---------- -->
    <section class="cats">
      <button
        v-for="c in categories"
        :key="c.name"
        type="button"
        class="cat-cell"
        @click="goCategory(c.name)"
      >
        <span class="cat-circle">
          <img :src="c.img" :alt="c.name" loading="lazy" />
        </span>
        <span class="cat-name">{{ c.name }}</span>
      </button>
    </section>

    <!-- ================================================================
         购物区：实物大图商品卡。这是首页的主体，图片占卡片约 65%。
         ================================================================ -->
    <section class="sec" v-if="showTrade">
      <div class="sec-head">
        <div class="sh-left">
          <h3>今日在售</h3>
        </div>
        <el-link type="primary" :underline="'never'" @click="go('/trade/products')">
          全部货源 →
        </el-link>
      </div>

      <div v-if="loadingProducts" class="goods-grid">
        <div v-for="n in 5" :key="n" class="goods-card is-ph">
          <div class="gc-photo"></div>
          <div class="gc-lines"><i></i><i></i></div>
        </div>
      </div>
      <div v-else-if="productRows.length" class="goods-grid">
        <article
          v-for="p in productRows"
          :key="p.productId"
          class="goods-card"
          @click="goProduct(p)"
        >
          <div class="gc-photo" :style="{ background: productFallbackBg(p) }">
            <span class="gc-fallback">{{ productFallbackText(p) }}</span>
            <img :src="productImage(p)" :alt="p.productName" loading="lazy" @error="onImgError" />
            <span class="gc-badge">{{ p.origin ? '产地直发' : '热卖中' }}</span>
          </div>
          <div class="gc-body">
            <h4 class="gc-name">{{ p.productName }}</h4>
            <p class="gc-meta">
              <span v-if="p.category" class="gc-cat">{{ p.category }}</span>
              <span v-if="p.origin">{{ p.origin }}</span>
            </p>
            <div class="gc-foot">
              <div class="gc-price">
                <span class="gp-cur">¥</span>{{ money(p.price) }}
                <i>/{{ p.unit || '件' }}</i>
              </div>
              <button type="button" class="gc-cart" @click.stop="goProduct(p)">看货源</button>
            </div>
            <p class="gc-stock">库存 {{ p.stock ?? '—' }}{{ p.unit || '' }}</p>
          </div>
        </article>
      </div>
      <div v-else class="sec-empty">
        <p>暂无在售货源</p>
        <el-button type="primary" plain @click="go('/trade/products')">去货源市场</el-button>
      </div>
    </section>

    <!-- ---------- 田间求购：谁在收我的货 ---------- -->
    <section class="sec" v-if="demands.length">
      <div class="sec-head">
        <div class="sh-left">
          <h3>田间求购</h3>
        </div>
        <el-link type="primary" :underline="'never'" @click="go('/trade/demand')">
          全部需求 →
        </el-link>
      </div>
      <div class="demand-grid">
        <article
          v-for="d in demands"
          :key="d.demandId"
          class="demand-card"
          @click="go('/trade/demand')"
        >
          <div class="dc-photo" :style="{ background: demandFallbackBg(d) }">
            <span class="dc-fallback">{{ demandInitial(d) }}</span>
            <img :src="demandImage(d)" :alt="d.category" loading="lazy" @error="onImgError" />
            <span class="dc-badge">{{ d.region || '产地直发' }}</span>
          </div>
          <div class="dc-body">
            <div class="dc-top">
              <strong>{{ d.category || '农产品' }}</strong>
              <span class="dc-need">{{ d.quantity ?? '—' }}{{ d.unit || '' }}</span>
            </div>
            <p class="dc-desc">{{ cut(d.description, 34) || '品相要求详见详情' }}</p>
            <div class="dc-foot">
              <span class="dc-price">
                期望 <b>{{ money(d.expectPrice) }}</b> 元/{{ d.unit || '件' }}
              </span>
              <span class="dc-date">{{ day(d.createTime) }}</span>
            </div>
          </div>
        </article>
      </div>
    </section>

    <!-- ---------- 专家展示区：必须有照片墙，不能只有文字卡 ----------
         ★ v-if="showExpert" 是 2026-10-10 补的权限判断。
         此前这块对所有角色都渲染，但银行用户的 menuPerms 是
         `home,finance,user` —— 没有 expert。结果是银行用户能看到整面照片墙，
         点「去预约」（以及点卡片本身）都会跳到 /expert/appointment，
         该页拉 /api/expert/appointment/my 时拿到 **HTTP 200 + body code 403**
         （后端刻意如此：HTTP 仍是 200，拒绝写在 body 的 code 里），
         表现为「页面能进、数据全空」，看上去像功能坏了。

         两种改法里选了「整块隐藏」而不是「只隐藏去预约按钮」：
         卡片本身也是点击跳同一个 403 页面的入口，只去掉按钮等于留了个假出口。
         判断条件与顶部导航的「专家助力」菜单同源（menuPerms 含 expert），
         菜单没了、内容也没了，两边口径一致。 -->
    <section v-if="showExpert" class="sec">
      <div class="sec-head">
        <div class="sh-left">
          <h3>驻场专家</h3>
        </div>
        <div class="sh-right">
          <!-- ---------- 滑动箭头 ----------
               专家会越来越多（现在库里只有 2 位，一屏放得下），所以专家墙做
               横向滑动轨道。箭头**只在内容真的溢出时渲染** —— railOverflow
               由 measureRail() 实测 scrollWidth/clientWidth 得出，不是按
               列表长度猜的。只有 2 位时整组按钮不出现，不会有「两个永远点不动
               的死按钮」堵在标题右边。
               到边界则 :disabled，视觉上置灰 + 不可点。 -->
          <div v-if="railOverflow" class="rail-navs">
            <button
              type="button"
              class="rail-nav"
              :disabled="railAtStart"
              aria-label="看前一组专家"
              @click="railStep(-1)"
            >
              <svg
                viewBox="0 0 24 24" width="16" height="16" fill="none"
                stroke="currentColor" stroke-width="2.2"
                stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
              >
                <path d="M15 5 8 12l7 7" />
              </svg>
            </button>
            <button
              type="button"
              class="rail-nav"
              :disabled="railAtEnd"
              aria-label="看后一组专家"
              @click="railStep(1)"
            >
              <svg
                viewBox="0 0 24 24" width="16" height="16" fill="none"
                stroke="currentColor" stroke-width="2.2"
                stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
              >
                <path d="M9 5l7 7-7 7" />
              </svg>
            </button>
          </div>
          <el-link type="primary" :underline="'never'" @click="go('/expert/appointment')">
            预约专家 →
          </el-link>
        </div>
      </div>

      <!-- 骨架走同一条轨道（4 张），数据到位时不会横向跳一下 -->
      <div v-if="loadingExperts" class="expert-track">
        <div v-for="n in 4" :key="n" class="expert-card is-ph">
          <div class="ec-banner"></div>
          <div class="ec-avatar is-ph-round"></div>
        </div>
      </div>

      <div v-else-if="experts.length" class="expert-rail">
        <div
          ref="expertRailRef"
          class="expert-track"
          @scroll.passive="onRailScroll"
        >
          <article
            v-for="e in experts"
            :key="e.expertId"
            class="expert-card"
            @click="go('/expert/appointment')"
          >
            <!-- 横幅用该专家领域对应的农业实景图：真实田间照片，不是占位插画 -->
            <div class="ec-banner">
              <img :src="expertBanner(e)" :alt="e.expertDomain" loading="lazy" @error="onImgError" />
            </div>
            <!-- 圆形头像：
                 1) 后端 avatarUrl 可用（avatarOk 记录成功加载）→ 用真实照片
                 2) 否则用按 expertId 生成的写实专家头像
                 3) 都失败 → 姓名首字兜底，不留碎图
                 ★ 2026-10-10 实测更正（此前这里写成「uploads/ 空目录 + 路径多一层 /api」，两条都是错的）：
                 - /api/files/** 就是正确路径。context-path=/api，资源处理器映射的是不含
                   context-path 的 /files/**（FileStorageService.LOCAL_PATH_PATTERN），
                   两者拼起来才是对外 URL。实测直连与走 Vite 代理都返回 200 + image/jpeg。
                 - 真实上传的头像是能显示的：李官杏 2026-10-08 上传的 a6218....jpg
                   在 disk 上真实存在（1.48MB），URL 可正常取图。
                 - 死链只出现在 3 条**种子数据**上（王建国/张桂芳 2026-08-21、赵国强 2026-08-23），
                   它们 update_time 停在 8 月、对应的文件从来没落过盘。
                 所以这里按 id 逐个探测、失败才兜底，是给种子数据兜的，不是给上传流程兜的。 -->
            <div class="ec-avatar" :style="{ background: expertBg(e.expertId) }">
              <img
                v-if="avatarOk[e.expertId] && e.avatarUrl"
                :src="e.avatarUrl"
                :alt="e.realName"
                loading="lazy"
                @error="avatarOk[e.expertId] = false"
              />
              <img
                v-else-if="expertAvatar(e)"
                :src="expertAvatar(e)"
                :alt="e.realName"
                loading="lazy"
                @error="onImgError"
              />
              <span v-else>{{ expertInitial(e.realName) }}</span>
            </div>
            <div class="ec-body">
              <div class="ec-title">
                <strong>{{ e.realName }}</strong>
                <span v-if="e.expertDomain" class="ec-domain">{{ e.expertDomain }}</span>
              </div>
              <p class="ec-intro">{{ cut(e.introduction, 46) || '提供农技咨询与产销对接服务' }}</p>
              <div class="ec-foot">
                <span class="ec-stat">{{ e.workloadStat || '暂无服务记录' }}</span>
                <button type="button" class="ec-btn" @click.stop="go('/expert/appointment')">去预约</button>
              </div>
            </div>
          </article>
        </div>
        <!-- 进度条：横向滚动条被隐藏了，用它告诉用户「右边还有内容」。
             滑块宽度 = 可视比例，位置 = 已滚比例 —— 和原生滚动条一个语义。 -->
        <div v-if="railOverflow" class="rail-bar" aria-hidden="true">
          <i :style="railThumbStyle"></i>
        </div>
      </div>
      <div v-else class="sec-empty"><p>暂无入驻专家</p></div>
    </section>

    <!-- ---------- 惠农信贷 ----------
         「农业知识」原来在这一行与信贷并排，现在整块提到页面头部做「平台资讯」，
         所以两栏结构取消，信贷改成一行三列横排。
         卡片内部改为左右布局（左：产品信息；右：大号利率），
         否则 460px 的宽度里三行文字纵向堆叠会显得很空。 -->
    <section class="sec" v-if="showFinance">
      <div class="sec-head">
        <div class="sh-left">
          <h3>惠农信贷</h3>
        </div>
        <el-link type="primary" :underline="'never'" @click="go('/finance/apply')">去申请 →</el-link>
      </div>

      <div v-if="loadingLoans" class="loan-grid">
        <div v-for="n in 3" :key="n" class="loan-card is-ph"></div>
      </div>
      <div v-else-if="loanRows.length" class="loan-grid">
        <article
          v-for="p in loanRows"
          :key="p.id"
          class="loan-card"
          @click="go('/finance/apply')"
        >
          <div class="lb-main">
            <strong>{{ p.name }}</strong>
            <p class="lb-range">{{ p.amountRange }}</p>
            <p class="lb-cond">{{ p.condition }}</p>
          </div>
          <span class="lb-rate">{{ p.rate }}<i>%</i></span>
        </article>
      </div>
      <div v-else class="sec-empty"><p>暂无金融产品</p></div>
    </section>

    <!-- ================================================================
         收尾区：农技知识（2026-10-10 从头部挪到页面最下方）
         ------------------------------------------------------------
         原来它在最顶部、占 110px，理由是「头部让给平台公共内容」。
         但实测下来这个理由站不住：首屏最该被看到的是**能卖的东西**
         （轮播 + 品类 + 今日在售），知识属于「逛完之后再看」的内容，
         压在头部只会把商品图往下推（实测首屏商品露出因此掉到 12px）。

         挪到末尾后形态也跟着变：不再是纯文字的信息条，而是**封面卡网格**。
         底部是内容收尾区，有图才像内容；一条没有图的文字竖排在末尾
         会被当成脚注，页面收得不干净。

         头部复用各分区通用的 .sec-head / .sh-left，与「今日在售」
         「田间求购」保持同一套体系，不另外造一套标题样式。

         数据源：GET /home/knowledge（t_knowledge），按 createTime 倒序取 4 条。
         配图：统一走 knowledgeImage(k)（接口图 → 标题命中实拍图 → 分类渐变封面）。
         （★ 2026-10-12 第六轮起 t_knowledge.image_url 已经不是 NULL 了 ——
           1~18 号回填成了 OBS 绝对地址，所以正常情况下第一级就命中，
           显示的是一张真照片。后两级是给「OBS 取不到 / 断网」和
           演示数据兜底的：标题里带具象农产品/养殖词 → 配对应本地实拍图；
           政策、认证这类本就没有对应实物的 → 配分类渐变封面。
           **不要**再按分类随机取模，那会让「畜禽粪污资源化」配上一碗小米。）

         ★ 单篇详情页（/knowledge/:id）用的是另一个函数 knowledgePhoto() ——
           它只返回前两级，**不含渐变封面**，没有真图就不显示配图块。
           卡片能接受渐变块当装饰，文章页不能：那是纯噪音。两处别混用。
         ★ 卡片 `<img>` 还挂了 onKnowledgeImgError：OBS 图挂了就退本地图
           （已回填，等于把「静态资源永不裂图」的保证换成了「依赖公网」，
           所以必须补这层兜底）。见该函数定义处的注释。
         ================================================================ -->
    <section class="news">
      <div class="sec-head">
        <div class="sh-left">
          <h3>农技知识</h3>
        </div>
        <el-link type="primary" :underline="'never'" @click="go('/expert/knowledge')">
          更多知识 →
        </el-link>
      </div>

      <div v-if="loadingKnowledge" class="news-grid">
        <div v-for="n in 4" :key="n" class="news-card is-ph"></div>
      </div>
      <div v-else-if="newsRows.length" class="news-grid">
        <article
          v-for="k in newsRows"
          :key="k.knowledgeId"
          class="news-card"
          @click="goKnowledge(k)"
        >
          <div class="nc-cover">
            <!--
              knowledgeImage() 内部已经优先取 k.imageUrl，所以这里不必再 `k.imageUrl ||` ——
              那样写会让人误以为"要先把接口图挡在前面"，而实际上后者永远不会被触发到。
            -->
            <img
              :src="knowledgeImage(k)"
              :alt="k.title"
              loading="lazy"
              @error="onKnowledgeImgError($event, k)"
            />
          </div>
          <div class="nc-body">
            <h4 class="nc-title">{{ k.title }}</h4>
            <p class="nc-foot">
              <span v-if="k.category" class="nc-cat">{{ k.category }}</span>
              <span>{{ day(k.createTime) }}</span>
              <span>浏览 {{ k.viewCount ?? 0 }}</span>
            </p>
          </div>
        </article>
      </div>
      <div v-else class="news-empty">暂无农技知识</div>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/api/request'
import { useUserStore } from '@/store/user'
import { isReadable } from '@/utils/text'
import { APPLY_STATUS, isApprovedApply } from '@/constants/finance'
import {
  productImage,
  assetUrl,
  productFallbackBg,
  productFallbackText,
  expertInitial,
  expertBg,
  expertAvatar,
  demandImage,
  demandFallbackBg,
  demandInitial,
  expertBanner,
  knowledgeImage,
  knowledgeLocalImage
} from '@/utils/imagery'

/* ====================================================================
 * 业务端首页。
 *
 * 视觉优先原则：这个页面的价值在于「看得见」——
 *   商品有实物大图、品类有圆形入口、专家有照片墙、知识有封面图。
 *   `image_url` 在演示数据里全是 NULL，所以图片全部走 utils/imagery.js 的
 *   品类映射兜底，接口真给了图就优先用接口的。
 *
 * 数据来源（真实业务接口，不是写死数字）：
 *   /home/products     平台在售货源（游客可见）
 *   /home/stats        平台运行数据（在售/求购/知识条数）
 *   /demand/list       田间求购
 *   /expert/experts    专家一览
 *   /home/knowledge    农业知识
 *   /trade/order/*     我的订单（买卖双向）
 *   /finance/*         融资与金融产品
 *   /expert/appointment/my  我的预约
 *
 * 权限口径：与 MainLayout 的 menuPerms 完全一致 —— 首页出现的入口，
 * 点进去一定不会被 401 打回。
 * ==================================================================== */

const router = useRouter()
const userStore = useUserStore()

const actualRole = computed(() => userStore.profile?.roleName || '')
const menuPerms = computed(() => String(userStore.menuPerms || '').split(',').filter(Boolean))
const isLoggedIn = computed(() => Boolean(userStore.token))

const can = (k) => menuPerms.value.includes(k)
const showTrade = computed(() => can('trade'))
const showFinance = computed(() => can('finance'))
const showExpert = computed(() => can('expert'))
const showBankReview = computed(() => actualRole.value === '银行用户')

/* ---------------- 格式化 ---------------- */
function money(v) {
  const n = Number(v || 0)
  if (!n) return '—'
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}
function day(t) {
  if (!t) return ''
  return String(t).slice(0, 10)
}
function cut(s, n) {
  const t = String(s || '').replace(/\s+/g, ' ')
  return t.length > n ? t.slice(0, n) + '…' : t
}
/** 图片 404 时就地隐藏，露出下层的首字/品类底色，不留碎图图标 */
function onImgError(e) {
  e.target.style.opacity = '0'
}

/**
 * 知识卡配图加载失败时的兜底。
 *
 * 为什么要单独一个：2026-10-12 把 `t_knowledge.image_url` 回填成了 OBS 绝对地址，
 * 这带来一个改前没有的依赖 —— **图文要靠公网**。断网 / 对象被删时，如果不接住，
 * 首页知识区会整排变成空白图框（改前它们是前端静态资源，反而断网也能显示）。
 *
 * 退到 `knowledgeLocalImage()`：本地实拍图，再不行就是分类渐变封面，恒非空。
 * ⚠️ 关键是**不能再走一遍 `knowledgeImage()`** —— 它第一步又是那个坏掉的 OBS 地址，
 * 会变成「失败→换成同一个 URL→再失败」的循环。所以用 dataset 打一次标记，
 * 本地兜底也失败时（理论上不会）才真的置空。
 */
function onKnowledgeImgError(e, k) {
  const img = e.target
  if (img.dataset.localFallback === '1') {
    img.style.opacity = '0'
    return
  }
  img.dataset.localFallback = '1'
  img.src = knowledgeLocalImage(k)
}

/* ---------------- 首屏轮播 ---------------- */
/* 这四张全部是真实田间/产地实拍图，不用抽象插画。 */
/* ★ 路径必须过 assetUrl()：表里写的是「站点根绝对路径」，而本应用构建后挂在 `/app/` 下。
   不过这一步，部署到子路径后浏览器会去**站点根**找图 —— 那里若恰好没有同名文件，
   nginx 的 SPA 回退会返回 `200 + index.html`，图片静默变碎图：状态码全绿、
   控制台无报错，只有肉眼看得出（2026-10-13 实际踩过一次）。 */
const HERO_IMAGES = [
  '/products/p08.jpg',
  '/products/p25.jpg',
  '/products/p20.jpg',
  '/products/p17.jpg'
].map((p) => assetUrl(p))
const HERO_ALT = ['水稻田里的稻谷长势', '田间蔬菜批发市场的货架', '养殖基地的鱼塘', '农场的散养鸡蛋']
const HERO_TEXT = [
  { kicker: '产地直供', title: '田间到餐桌，少中间一环', desc: '24 个在售货源全部来自产地直发，看得到产地、看得到库存' },
  { kicker: '今日采摘', title: '蔬菜类当日采摘当日发', desc: '叶菜按筐起发，冷链优先派送，江浙沪次日可达' },
  { kicker: '水产直供', title: '活鱼现捞，水库到餐桌', desc: '水产类按斤计价，下单后由基地现捞现发' },
  { kicker: '散养蛋品', title: '林下散养，枚枚可溯', desc: '畜禽产品标注产期与养殖方式，按盒装发货' }
]
const heroIdx = ref(0)
let heroTimer = null
function startHero() {
  stopHero()
  heroTimer = setInterval(() => {
    heroIdx.value = (heroIdx.value + 1) % HERO_IMAGES.length
  }, 5000)
}
function stopHero() {
  if (heroTimer) clearInterval(heroTimer)
  heroTimer = null
}
const heroKicker = computed(() => HERO_TEXT[heroIdx.value].kicker)
const heroTitle = computed(() => HERO_TEXT[heroIdx.value].title)
const heroDesc = computed(() => HERO_TEXT[heroIdx.value].desc)

const keyword = ref('')
/**
 * 热门搜索词。
 *
 * ★ 2026-10-10：原先是 ['赣南脐橙', '高山蔬菜', '散养鸡蛋', '稻米'] ——
 * 实测其中 3 个点下去是**空结果**：「高山蔬菜」「散养鸡蛋」「稻米」
 * 既不是任何商品名的子串，也不是品类或产地（库里实际叫「散养土鸡蛋」「五常大米」）。
 * 热词点了没反应，比不显示热词更伤。
 *
 * 换词口径：每个词都必须能命中 ≥1 条在架商品。
 * 判定方式 = 后端 keyword 的匹配范围（商品名 / 品类 / 产地三者取或），
 * **改这批词前请先照这个范围实测一遍**，别照感觉写。
 * 当前 4 个词各自命中：赣南脐橙 1 条、散养土鸡蛋 1 条、五常大米 1 条、小龙虾 1 条。
 */
const hotWords = ['赣南脐橙', '散养土鸡蛋', '五常大米', '小龙虾']
function doSearch() {
  const k = keyword.value.trim()
  router.push({ path: '/trade/products', query: k ? { keyword: k } : {} })
}
function searchBy(w) {
  keyword.value = w
  doSearch()
}

/* ---------------- 品类入口 ----------------
   圆形裁到 74px，所以要选**主体占满画面**的特写图。
   例：「水果」原用 p01（草地上的苹果箱，竖构图、主体只占中间一小块），
   缩成圆图后糊成一团黄绿色，第一眼看着像土豆 —— 换成 p10 满幅红苹果特写。
   ★ img 同样要过 assetUrl()，理由见上面 HERO_IMAGES 的注释。 */
const categories = [
  { name: '水果', img: '/products/p10.jpg' },
  { name: '蔬菜', img: '/products/p26.jpg' },
  { name: '水产', img: '/products/p19.jpg' },
  { name: '畜禽产品', img: '/products/p27.jpg' },
  { name: '粮油', img: '/products/g02.jpg' },
  { name: '干货', img: '/products/p22.jpg' }
].map((c) => ({ ...c, img: assetUrl(c.img) }))
function goCategory(name) {
  router.push({ path: '/trade/products', query: { category: name } })
}

/* ---------------- 各区块数据 ---------------- */
const loadingProducts = ref(false)
const productRows = ref([])

const loadingOrders = ref(false)
const buyOrders = ref([])
const soldOrders = ref([])

const loadingApplies = ref(false)
const applies = ref([])
const bankApplies = ref([])

const loadingLoans = ref(false)
const loanRows = ref([])

const loadingKnowledge = ref(false)
const knowledgeAll = ref([])

const loadingExperts = ref(false)
const experts = ref([])
/* 专家总数（全量）。首页「入驻专家 N 位」要报平台真实规模，
   而 experts 是截断过的列表（滑轨上限 12），拿它当分母会少报。 */
const expertTotal = ref(0)
/* 每位专家的头像是否可用。死链来自**种子数据**：王建国/张桂芳/赵国强 三条
   seed 行在 2026-08 就写好了 avatar_url，但对应文件从未落盘（uploads/ 里没有），
   所以「有 avatarUrl」不等于「图能显示」——必须按 id 单独记成功与否，
   一张图失败不能连累其他专家。
   注意：真实上传的头像是好的（李官杏 2026-10-08 那张在磁盘上真实存在且可取图），
   别再把这里的兜底误解成「上传流程坏了」。 */
const avatarOk = reactive({})

/* ---------------- 专家墙横向滑动轨道（2026-10-10） ----------------
   原来是一行 grid（`repeat(auto-fit, minmax(300px,1fr))`），库里只有 2 位专家
   时看着没问题，但专家一多就只会往下堆行，一屏塞不下就必须整页往下翻。
   改成横向滑轨后：卡片宽度固定，多出来的往右走，不动页面高度。

   ★ 下面是几个刻意的取舍：
   - 卡片宽度用 `flex: 1 0 calc(25% - 31.5px)`（详见下面 .expert-card 的注释）：
     **≤4 张时靠 flex-grow 撑满一屏，保持和老 grid 一样的观感；≥5 张时
     flex-shrink:0 让它溢出，才开始滑。** 不能简单写死宽度 —— 写死会让
     原来的「2 位专家 = 2 张宽卡」缩水成两张窄卡，是视觉回归（实测踩过）。
   - 箭头/进度条**只在真的溢出时渲染**，判据是实测的 scrollWidth/clientWidth，
     不是「专家数 > N」这种猜法（窗口越窄，一屏能放的越少，"N" 根本定不下来）。
   - 不做鼠标拖拽：卡片本身是 @click 跳转，加拖拽就得处理「拖完误触跳转」，
     收益（Mac 触控板本来就能横向滑）远小于引入的 bug 面。 */
const expertRailRef = ref(null)
const railOverflow = ref(false) // 内容是否溢出（决定箭头/进度条是否出现）
const railAtStart = ref(true)
const railAtEnd = ref(true)
const railProgress = ref(0) // 已滚比例 0~1
const railRatio = ref(1) // 可视宽度占比 0~1（进度条滑块宽度）

// 卡片间距。JS 里也要用（算「一屏几张」），所以提到常量 ——
// 改 CSS 的 gap 时必须同步改这里，否则翻页步长会和实际排版错位。
const RAIL_GAP = 18

/* 量一次轨道状态。scroll/ resize / 数据变化后都要调。
   读的全是同步布局属性（scrollLeft / scrollWidth / clientWidth），很轻，
   但仍然用 rAF 合帧，避免滚动时每帧多次触发。 */
function measureRail() {
  const el = expertRailRef.value
  if (!el) {
    railOverflow.value = false
    return
  }
  const max = el.scrollWidth - el.clientWidth
  railOverflow.value = max > 2
  railRatio.value = el.scrollWidth > 0 ? Math.min(1, el.clientWidth / el.scrollWidth) : 1
  railAtStart.value = el.scrollLeft <= 2
  railAtEnd.value = max <= 2 || el.scrollLeft >= max - 2
  railProgress.value = max <= 2 ? 0 : Math.min(1, Math.max(0, el.scrollLeft / max))
}

let railRaf = 0
function onRailScroll() {
  if (railRaf) return
  railRaf = requestAnimationFrame(() => {
    railRaf = 0
    measureRail()
  })
}

/* 一次翻「一屏能放几张 − 1」张。
   减一是为了让前后两次有重叠 —— 直接翻满一屏的话，边界那半张卡会被跳过，
   用户会觉得「怎么漏了一个人」。
   目标位置显式 clamp 到 [0, max]：用 scrollBy 在末端会「想滚但滚不动」，
   scrollTo 到 max 则是明确的终点，配合 railAtEnd 把按钮置灰。 */
function railStep(dir) {
  const el = expertRailRef.value
  if (!el) return
  const card = el.querySelector('.expert-card')
  const per = card ? card.offsetWidth + RAIL_GAP : el.clientWidth
  const visible = card ? Math.max(1, Math.floor((el.clientWidth + RAIL_GAP) / per)) : 1
  const max = el.scrollWidth - el.clientWidth
  const step = per * Math.max(1, visible - 1)
  const target = Math.min(max, Math.max(0, el.scrollLeft + dir * step))
  el.scrollTo({ left: target, behavior: 'smooth' })
}

const railThumbStyle = computed(() => {
  const w = railRatio.value * 100
  return { width: `${w.toFixed(2)}%`, left: `${(railProgress.value * (100 - w)).toFixed(2)}%` }
})

const demands = ref([])
const platformStats = ref([])

const appointments = ref([])

/* ---------------- 业务数据带 ----------------
   一张卡 = 一个业务域。卡面只放「最该被看到的那一个数」，
   同一批数据里的其他数字（买入/卖出、已通过笔数、合计金额）
   全部并进摘要行，避免同一个数在页面上出现两遍。 */
const pendingOrderCount = computed(
  () => [...buyOrders.value, ...soldOrders.value]
    .filter((o) => /待发货|待支付|待收货/.test(o.orderStatus || o.status || '')).length
)

const myTiles = computed(() => {
  const list = []

  if (showTrade.value) {
    const all = [...buyOrders.value, ...soldOrders.value]
    if (all.length) {
      const extra = []
      if (buyOrders.value.length) extra.push(`买入 ${buyOrders.value.length}`)
      if (soldOrders.value.length) extra.push(`卖出 ${soldOrders.value.length}`)
      if (pendingOrderCount.value) extra.push(`${pendingOrderCount.value} 笔待处理`)
      list.push({
        key: 'order',
        value: all.length,
        label: '我的订单',
        tone: 'green',
        path: '/trade/orders',
        sub: extra.join(' · ') || '全部订单状态正常'
      })
    }
  }

  if (showFinance.value) {
    const src = showBankReview.value ? bankApplies.value : applies.value
    if (src.length) {
      // ⚠️ 这里原来写的是 /通过|已放款/ 正则 —— 它会把「审批未通过」也算成通过
      //（字面量里含「通过」二字），报出一个虚高的「已通过 N 笔」。
      // 改用 constants/finance 的白名单判定，两个页面共用同一口径。
      const approved = src.filter((a) => isApprovedApply(a.applyStatus)).length
      const total = src.reduce((s, a) => s + Number(a.applyAmount || 0), 0)
      const extra = [`合计 ${money(total)} 元在流程中`]
      if (approved) extra.push(`已通过 ${approved} 笔`)
      list.push({
        key: 'apply',
        value: src.length,
        label: showBankReview.value ? '待审批融资申请' : '我的融资',
        tone: 'amber',
        // 银行侧要进审批页；/finance/my 是「申请人看自己提交的申请」，
        // 银行用户进去永远是空列表。
        path: showBankReview.value ? '/finance/bank-review' : '/finance/my',
        sub: extra.join(' · ')
      })
    }
  }

  if (showExpert.value && appointments.value.length) {
    list.push({
      key: 'appt',
      value: appointments.value.length,
      label: actualRole.value === '专家' ? '待我接待的预约' : '我的专家预约',
      tone: 'green',
      path: '/expert/appointment',
      sub: '按排期提前准备资料'
    })
  }

  return list.slice(0, 4)
})

/* 没有任何业务数据时（专家角色最典型），至少给一条与本角色相关的数，
   而不是让首屏空着。注意这不参与空态判断 —— 空态说的是「没有业务记录」。 */
const bizTiles = computed(() => {
  if (myTiles.value.length) return myTiles.value
  if (showExpert.value && knowledgeAll.value.length) {
    return [{
      key: 'know',
      value: knowledgeAll.value.length,
      label: '农业知识',
      tone: 'green',
      path: '/expert/knowledge',
      sub: '平台专家与农技站发布的实用内容'
    }]
  }
  return []
})

/* ---------------- 首屏右侧快捷入口 ----------------
   按角色权限给，与 MainLayout 的 menuPerms 同一口径：
   首页上出现的入口，点进去一定不会被 401 打回。 */
const quickLinks = computed(() => {
  const list = []
  if (showTrade.value) {
    list.push({ label: '发布货源', desc: '把今天的货挂上去', path: '/trade/products' })
    list.push({ label: '发布求购', desc: '写清品类与期望价', path: '/trade/demand' })
  }
  if (showFinance.value) {
    list.push(
      showBankReview.value
        ? { label: '审批申请', desc: '农户提交的融资单', path: '/finance/bank-review' }
        : { label: '融资申请', desc: '贴息惠农产品', path: '/finance/apply' }
    )
  }
  if (showExpert.value) {
    list.push({ label: '预约专家', desc: '农技与经营问题', path: '/expert/appointment' })
  }
  return list.slice(0, 3)
})

/* ---------- 空态 ----------
   只对「本来就有订单/融资概念」的角色显示。专家与银行用户没有这些概念，
   让他们看到「还没有订单」是错的 —— 之前就是因为没加角色判断，
   这两位角色一进首页就看到一句与自己无关的话。 */
const showBusinessBlocks = computed(() => showTrade.value || showFinance.value)
const isEmptyState = computed(() => {
  if (!showBusinessBlocks.value) return false
  if (myTiles.value.length) return false
  // 有可视化内容时不显示空态 —— 平台有货有专家，不是"什么都没有"
  return true
})

// 管理员与农户同口径：农户是平台主体，管理员看到的「我的业务」视图与农户一致。
// 不加这条的话管理员会掉到最后一行兜底，首页显示「暂无业务记录」+「发布货源」，
// 既不像农户也不像管理员，看着像功能缺失（实测截图就是这个状态）。
const isFarmerLike = computed(() => ['农户', '管理员'].includes(actualRole.value))

const emptyTitle = computed(() => {
  if (isFarmerLike.value) return '你还没有业务记录'
  if (actualRole.value === '采购方') return '你还没有订单'
  if (actualRole.value === '银行用户') return '当前没有待审批的申请'
  return '暂无业务记录'
})
const emptyDesc = computed(() => {

  if (isFarmerLike.value) return '先看看下面的在售行情与田间求购，定下价再发布你的货源；需要周转可以提交融资意向。'
  if (actualRole.value === '采购方') return '挑中上面的商品加入购物车即可下单，订单会出现在这里。'
  if (actualRole.value === '银行用户') return '农户提交融资申请后，清单会出现在这里。'
  return '当前没有待处理事项。'

})
const emptyAction = computed(() => {
  if (actualRole.value === '采购方') return { label: '去挑货', path: '/trade/products' }
  if (actualRole.value === '银行用户') return { label: '查看金融产品', path: '/finance/apply' }
  if (actualRole.value === '专家') return { label: '去知识库', path: '/expert/knowledge' }
  return { label: '发布货源', path: '/trade/products' }
})

/* ---------------- 数据加载 ---------------- */
function listOf(d) {
  return Array.isArray(d) ? d : (d?.records || d?.list || [])
}

async function loadOrders() {
  if (!showTrade.value || !isLoggedIn.value) return
  loadingOrders.value = true
  try {
    const [buy, sold] = await Promise.all([
      request.get('/trade/order/list', { params: { pageNum: 1, pageSize: 5 }, silent: true }),
      request.get('/trade/order/sold', { params: { pageNum: 1, pageSize: 5 }, silent: true }).catch(() => null)
    ])
    buyOrders.value = listOf(buy)
    soldOrders.value = listOf(sold)
  } catch (e) {
    buyOrders.value = []
    soldOrders.value = []
  } finally {
    loadingOrders.value = false
  }
}

async function loadApplies() {
  if (!showFinance.value || !isLoggedIn.value) return
  loadingApplies.value = true
  try {
    // 银行用户要「别人提交的申请」，农户要「自己提交的申请」——
    // 同一模块的两个视角，接口不同，混用会让银行看到自己的空列表。
    if (showBankReview.value) {
      // ⚠️ 这里原来打的是 '/loan/applies' —— 后端**根本没有这个路径**
      // （真实存在的是 /loan-admin/applies、/finance/apply/my、/admin/finance/applies）。
      // 404 被下面的 catch 吞掉后 bankApplies 恒为空数组，于是首页
      // 「待审批融资申请」这张卡**永远不会出现**，银行用户看到的是
      // 「当前没有待审批的申请」—— 而审批页里明明躺着待办单。
      //
      // 另外 /loan-admin/applies 返回的是**数组**（银行侧不做分页），
      // 不认 pageNum/pageSize。这里再按「已推送银行」过滤一次，
      // 让卡上的数字是「待我处理」的口径，而不是「历史上推送过的全部申请」。
      const pushed = listOf(await request.get('/loan-admin/applies', { silent: true }))
      bankApplies.value = pushed.filter((a) => a.applyStatus === APPLY_STATUS.PUSHED)
    } else {
      applies.value = listOf(await request.get('/finance/apply/my', { params: { pageNum: 1, pageSize: 5 }, silent: true }))
    }
  } catch (e) {
    if (showBankReview.value) bankApplies.value = []
    else applies.value = []
  } finally {
    loadingApplies.value = false
  }
}

async function loadAppointments() {
  if (!showExpert.value || !isLoggedIn.value) return
  try {
    appointments.value = listOf(
      await request.get('/expert/appointment/my', { silent: true }).catch(() => null)
    )
  } catch (e) {
    appointments.value = []
  }
}

async function loadLoans() {
  if (!showFinance.value) return
  loadingLoans.value = true
  try {
    const d = await request.get('/finance/loan-products', { silent: true })
    loanRows.value = listOf(d)
      .filter((p) => p.status === 1)
      .slice(0, 3)
      .map((p) => ({
        id: p.loanProductId ?? p.id,
        name: p.productName ?? p.name ?? '',
        rate: (() => { const v = Number(p.annualRate ?? p.rate ?? 0); return (v < 1 ? v * 100 : v).toFixed(2) })(),
        amountRange: `最高 ${Number(p.maxAmount || 0) / 10000}万`,
        condition: p.description ?? p.condition ?? '详见说明'
      }))
  } catch (e) {
    loanRows.value = []
  } finally {
    loadingLoans.value = false
  }
}

async function loadProducts() {
  loadingProducts.value = true
  try {
    const d = await request.get('/home/products', { params: { limit: 5 }, silent: true })
    productRows.value = listOf(d).slice(0, 5)
  } catch (e) {
    productRows.value = []
  } finally {
    loadingProducts.value = false
  }
}

async function loadDemands() {
  try {
    const d = await request.get('/demand/list', { params: { pageNum: 1, pageSize: 5 }, silent: true })
    demands.value = listOf(d)
      .filter((x) => x.status === 1 || x.status == null)
      .slice(0, 5)
  } catch (e) {
    demands.value = []
  }
}

async function loadExperts() {
  // ★ 这里**故意不判 showExpert**（2026-10-10 补注）。
  // 专家照片墙本身按 showExpert 隐藏（银行用户的 menuPerms 里没有 expert），
  // 但「入驻专家 N 位」是**平台级**数字，四角色都该看到真实值 ——
  // 跟着一起跳过的话，银行用户的右列会显示「入驻专家 —」，
  // 那比不显示更糟：看起来像数据没加载出来。
  // /api/expert/experts 在后端也确实没有卡角色（与其他专家接口不同）。
  if (!isLoggedIn.value) return
  loadingExperts.value = true
  try {
    // ★ 专家墙现在是横向滑轨，不必再压到 4 位（原来 slice(0,4) 是一行 grid
    //   时代的限制）。这里留 12 的上限纯粹是防止后端某天返回几百条把 DOM
    //   撑爆，正常规模全都会显示出来。
    //
    // ★ 另外单独记 expertTotal —— 「入驻专家 N 位」那个统计口径必须读**全量**
    //   长度。原来它读 experts.length，而 experts 是 slice 过的：
    //   库里 2 位时凑巧对，一旦超过 slice 上限，首页数字就会卡在上限上不动，
    //   是个「种子数据太少所以一直没暴露」的潜伏 bug。
    const allExperts = listOf(await request.get('/expert/experts', { silent: true }))
    expertTotal.value = allExperts.length
    experts.value = allExperts.slice(0, 12)
    // 只有真的有 avatarUrl 才置 true 去尝试加载；没有的直接走首字兜底，
    // 避免浏览器先发一次注定 404 的请求
    experts.value.forEach((e) => {
      avatarOk[e.expertId] = Boolean(e.avatarUrl)
    })
  } catch (e) {
    experts.value = []
  } finally {
    loadingExperts.value = false
  }
}

async function loadStats() {
  try {
    const s = await request.get('/home/stats', { silent: true })
    // HomeStatisticsVO 只有 productCount / demandCount / knowledgeCount 三个字段，
    // 没有专家数 —— 专家数取我们自己已经拉到的列表长度，不去猜后端字段名。
    platformStats.value = [
      { label: '在售货源', value: s?.productCount ?? '—' },
      { label: '有效求购', value: s?.demandCount ?? '—' },
      { label: '入驻专家', value: expertTotal.value || '—' },
      { label: '农业知识', value: s?.knowledgeCount ?? '—' }
    ]
  } catch (e) {
    platformStats.value = []
  }
}

async function loadKnowledge() {
  loadingKnowledge.value = true
  try {
    const d = await request.get('/home/knowledge', { params: { limit: 20 }, silent: true })
    knowledgeAll.value = listOf(d).filter((k) => k.auditStatus === '已通过' && isReadable(k.title))
  } catch (e) {
    knowledgeAll.value = []
  } finally {
    loadingKnowledge.value = false
  }
}

/* 头部资讯：按发布时间倒序取最新 4 条。
   注意这里**不是**按浏览量排 —— 那是「知识库列表」的排序；
   头部资讯要的是「最新发生了什么」，所以用 createTime。 */
const newsRows = computed(() =>
  knowledgeAll.value
    .slice()
    .sort((a, b) => String(b.createTime || '').localeCompare(String(a.createTime || '')))
    .slice(0, 4)
)

/* ---------------- 跳转 ---------------- */
function go(p) {
  if (p) router.push(p)
}
function goProduct(p) {
  if (p?.productId) router.push(`/trade/product/${p.productId}`)
}
function goKnowledge(k) {
  // ★ 2026-10-12 改：原来推的是 `/expert/knowledge?knowledgeId=xxx`，
  //   而 Knowledge.vue 里**根本没有 useRoute** —— 那个 query 从来没被读过，
  //   于是点一张知识卡的效果是「跳到专家助力板块的列表页，什么都没打开」，
  //   参数被静默丢掉（本仓库第二次踩「上游传参、下游没接」，上次是商品页）。
  //   现在每篇知识有自己的地址，直接推详情页，参数传递这一跳彻底不需要了。
  if (k?.knowledgeId) router.push(`/knowledge/${k.knowledgeId}`)
}

/* 专家列表是异步来的，onMounted 那一刻 DOM 里一张卡片都没有，
   所以要在数据到位、且真正渲染完之后再量一次轨道。 */
watch(
  () => [loadingExperts.value, experts.value.length],
  async () => {
    await nextTick()
    measureRail()
  }
)

onMounted(async () => {
  startHero()
  window.addEventListener('resize', measureRail)
  loadProducts()
  loadDemands()
  loadKnowledge()
  loadOrders()
  loadApplies()
  loadAppointments()
  loadLoans()
  // 「入驻专家 N 位」要读 loadExperts 拉回来的 expertTotal，
  // 必须排在 loadStats 之前，否则 loadStats 先返回时专家数还是 0，
  // 页面上出现一个假的「—」。
  await loadExperts()
  loadStats()
})
onUnmounted(() => {
  stopHero()
  window.removeEventListener('resize', measureRail)
  if (railRaf) cancelAnimationFrame(railRaf)
})
</script>

<style scoped>
/* ====================================================================
 * 配色全部走 --rxt-* / --el-* 令牌，与业务端浅色体系一致，不硬编码颜色。
 * 只面向桌面端：不做窄屏塌缩。
 * ==================================================================== */
.biz-home { color: var(--rxt-ink); }

/* ---------- 农技知识（收尾区） ----------
   ★ 2026-10-10 从头部挪到页面最下方，形态也从「纯文字信息条」
   升级成「封面卡网格」—— 底部是逛完之后的收尾区，有图才像内容；
   一条没有图的文字竖排在末尾会被当成脚注，页面收不干净。

   头部有意复用 .sec-head / .sh-left（与「今日在售」「田间求购」同一套），
   所以这里不再自定义标题样式，只负责卡片本身。
   卡片沿用商品卡的语言（圆角 + 细边框 + hover 上浮），
   让整页从头到尾是一套卡片体系。 */
.news {
  /* 它是页面最后一块，下面紧接页脚，不留 .sec 那 36px 的区块间距 */
  margin-bottom: 0;
}
.news-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.news-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  cursor: pointer;
  border: 1px solid var(--rxt-border);
  border-radius: var(--rxt-radius-lg);
  background: var(--el-bg-color);
  transition: transform 0.2s, box-shadow 0.2s, border-color 0.2s;
}
.news-card:hover {
  transform: translateY(-2px);
  border-color: var(--el-color-primary-light-7);
  box-shadow: var(--rxt-shadow-md);
}

/* 封面。图片 404 时 onImgError 会把 img 透明度归零，
   露出下面这层柔和渐变 —— 而不是一个生硬的灰块。
   高度刻意压扁（约 2.2:1），与上方商品卡的方图区分开，
   免得两张卡看起来是同一个模块。 */
.nc-cover {
  height: 150px;
  background: linear-gradient(135deg, var(--el-color-primary-light-9), var(--el-color-primary-light-8));
}
.nc-cover img { display: block; width: 100%; height: 100%; object-fit: cover; }

.nc-body {
  display: flex;
  flex-direction: column;
  gap: 9px;
  padding: 12px 13px 13px;
}
/* 固定两行高度：4 张卡标题长短不一时，底部那行元信息才不会参差不齐 */
.nc-title {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 42px;
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  transition: color 0.2s;
}
.news-card:hover .nc-title { color: var(--rxt-primary); }

.nc-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 12px;
  color: var(--rxt-ink-weak);
}
.nc-cat {
  padding: 1px 7px;
  border-radius: var(--rxt-radius-pill);
  background: var(--el-color-primary-light-9);
  color: var(--rxt-primary-deep);
  font-size: 11px;
  white-space: nowrap;
}
/* 浏览量靠右收尾，避免三项挤成一团 */
.nc-foot > span:last-child { margin-left: auto; white-space: nowrap; }

.news-empty {
  padding: 26px 0 30px;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

/* 骨架高度对齐真实卡片，加载完不会跳一下
   （实测 .news-card 高 246 = 150 封面 + 94 body + 2×1 边框） */
.news-card.is-ph {
  height: 246px;
  pointer-events: none;
  border-color: transparent;
  background: var(--el-fill-color-light);
}

/* ---------- 业务数据带（原「指标条」+「我的业务」合并） ----------
   一张卡一个业务域：左侧大数字，右侧标题 + 一行摘要。
   卡片整块可点，直达该业务的列表页。整条高度约 84px，
   原来两块加起来 383px，且显示的是同一批数字。 */
.biz-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-bottom: 18px;
}
.bt-item {
  flex: 1 1 260px;
  min-width: 0;
  display: flex; align-items: center; gap: 16px;
  padding: 17px 22px;
  border: 1px solid var(--rxt-border);
  border-radius: var(--rxt-radius-lg);
  background: var(--el-bg-color);
  box-shadow: var(--rxt-shadow-sm);
  font-family: inherit; text-align: left; cursor: pointer;
  transition: border-color 0.2s, transform 0.2s, box-shadow 0.2s;
}
.bt-item:hover {
  border-color: var(--rxt-primary);
  transform: translateY(-2px);
  box-shadow: var(--rxt-shadow-md);
}
/* 只有一条数据时不要拉满整行，看着像标题栏 */
.biz-bar.is-solo .bt-item { flex: 0 1 380px; }
.bt-num {
  flex: none; min-width: 46px; text-align: center;
  font-size: 32px; font-weight: 700; line-height: 1;
  font-variant-numeric: tabular-nums;
  color: var(--rxt-primary);
}
.bt-item.is-amber .bt-num { color: var(--rxt-accent); }
.bt-text { display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.bt-label { font-size: 15px; font-weight: 600; }
.bt-sub {
  font-size: 12px; color: var(--rxt-ink-weak);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

/* ================================================================
   首屏轮播 —— 生鲜电商首页的标准第一屏
   ================================================================ */
.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 316px;
  gap: 16px;
  margin-bottom: 20px;
}
.hero-main {
  position: relative;
  /* 高度是首屏预算的旋钮，定 300px：
     实测 360 时「今日在售」的商品卡第一张落在 899px，卡在首屏（900px）之外，
     首屏一张货都看不到。头部换上资讯区后又多吃掉约 110px，
     所以再压 20px 把商品卡拉回首屏内。 */
  height: 300px;
  border-radius: var(--rxt-radius-lg);
  overflow: hidden;
  background: var(--rxt-primary-deep);
}
.hero-main > img { width: 100%; height: 100%; object-fit: cover; display: block; }
.hero-mask {
  position: absolute; inset: 0;
  background: linear-gradient(
    100deg,
    rgba(10, 52, 40, 0.88) 0%,
    rgba(10, 52, 40, 0.66) 42%,
    rgba(10, 52, 40, 0.15) 100%
  );
}
.hero-text {
  position: absolute; inset: 0;
  display: flex; flex-direction: column; justify-content: center;
  padding: 0 52px;
  max-width: 660px;
  color: #fff;
}
.hero-kicker {
  margin: 0 0 10px;
  align-self: flex-start;
  padding: 4px 12px;
  border-radius: var(--rxt-radius-pill);
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.3);
  font-size: 12px; letter-spacing: 1px;
}
.hero-text h3 {
  margin: 0 0 12px;
  font-size: 38px; line-height: 1.25; font-weight: 700;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.25);
}
.hero-desc {
  margin: 0 0 22px;
  font-size: 15px; line-height: 1.75;
  color: rgba(255, 255, 255, 0.88);
}
.hero-search { display: flex; gap: 10px; max-width: 480px; }
.hero-search :deep(.el-input__wrapper) {
  border-radius: var(--rxt-radius-pill);
  box-shadow: none;
  background: rgba(255, 255, 255, 0.94);
}
.hs-icon { color: var(--rxt-ink-weak); }
.hs-btn {
  flex: none;
  height: 40px; padding: 0 30px;
  border-radius: var(--rxt-radius-pill);
  background: var(--rxt-accent);
  border-color: var(--rxt-accent);
  color: #3a2400; font-weight: 600;
}
.hs-btn:hover { background: #e09512; border-color: #e09512; color: #3a2400; }
.hero-hot {
  display: flex; align-items: center; gap: 12px;
  margin-top: 16px;
  font-size: 13px; color: rgba(255, 255, 255, 0.75);
}
.hot-word {
  padding: 0; border: 0; background: none;
  font-family: inherit; font-size: 13px;
  color: rgba(255, 255, 255, 0.95);
  cursor: pointer; border-bottom: 1px dashed rgba(255, 255, 255, 0.45);
}
.hot-word:hover { color: var(--rxt-accent); border-bottom-color: var(--rxt-accent); }
/* 轮播指示点：原来是 0.42 白点，压在浅色沙地/水面这类亮图上几乎看不见，
   用户根本判断不出有几张图、也点不到。改成「深色填充 + 白色描边」的环，
   任何底色上都可见；当前点是实心白。 */
.hero-dots { position: absolute; right: 22px; bottom: 18px; display: flex; gap: 8px; }
.hd-dot {
  width: 8px; height: 8px; padding: 0;
  border: 1.5px solid rgba(255, 255, 255, 0.9);
  border-radius: 50%;
  background: rgba(10, 40, 32, 0.42);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.28);
  cursor: pointer;
  transition: background 0.2s, transform 0.2s, border-color 0.2s;
}
.hd-dot.is-on { background: #fff; border-color: #fff; transform: scale(1.45); }

/* 右列：平台数据 + 快捷办理。
   高度锁在 .hero-main 的 360px 内（1fr + auto），不会把整行撑高。 */
.hero-side { display: grid; grid-template-rows: 1fr auto; gap: 14px; }
.hero-stats {
  display: grid; grid-template-columns: 1fr 1fr; gap: 1px;
  background: var(--rxt-border);
  border: 1px solid var(--rxt-border);
  border-radius: var(--rxt-radius-lg);
  overflow: hidden;
}
.hs-item {
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px;
  padding: 12px 8px;
  background: var(--el-bg-color);
}
.hss-num {
  font-size: 24px; font-weight: 700; line-height: 1;
  color: var(--rxt-primary); font-variant-numeric: tabular-nums;
}
.hss-label { font-size: 12px; color: var(--el-text-color-secondary); }

/* 快捷办理：按角色权限给的三个入口，替代原来的重复缩略图。
   ⚠️ 这三条的 padding 会**反向撑高整个轮播行** ——
   .hero-side 是 grid 的「1fr + auto」，快捷办理是 auto 那行，
   它比 .hero-main 高时整行就跟着长。实测 padding 8px 时
   轮播行被撑到 313px（.hero-main 只设了 300px）。收到 6px 刚好压回 300。 */
.hero-quick {
  padding: 12px 16px 8px;
  border: 1px solid var(--rxt-border);
  border-radius: var(--rxt-radius-lg);
  background: var(--el-bg-color);
}
.hq-title { margin: 0 0 2px; font-size: 13px; font-weight: 600; color: var(--rxt-ink-weak); }
.hq-item {
  width: 100%;
  display: flex; align-items: center; justify-content: space-between; gap: 10px;
  padding: 6px 0;
  border: 0; border-top: 1px solid var(--el-border-color-lighter);
  background: none; font-family: inherit; text-align: left; cursor: pointer;
}
.hq-item:first-of-type { border-top: 0; }
.hq-main { display: flex; align-items: baseline; gap: 8px; min-width: 0; }
.hq-main strong { font-size: 14px; font-weight: 600; flex: none; }
.hq-main i {
  font-style: normal; font-size: 12px; color: var(--rxt-ink-weak);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.hq-arrow { flex: none; color: var(--rxt-primary); transition: transform 0.2s; }
.hq-item:hover .hq-arrow { transform: translateX(3px); }

/* ---------- 品类入口 ---------- */
.cats {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 14px;
  margin-bottom: 22px;
  padding: 12px;
  border: 1px solid var(--rxt-border);
  border-radius: var(--rxt-radius-lg);
  background: var(--el-bg-color);
}
.cat-cell {
  display: flex; flex-direction: column; align-items: center; gap: 10px;
  padding: 0; border: 0; background: none;
  font-family: inherit; cursor: pointer;
}
.cat-circle {
  width: 74px; height: 74px;
  border-radius: 50%;
  overflow: hidden;
  border: 1px solid var(--rxt-border);
  transition: transform 0.24s, border-color 0.24s;
}
.cat-circle img { width: 100%; height: 100%; object-fit: cover; display: block; }
.cat-cell:hover .cat-circle { transform: scale(1.08); border-color: var(--rxt-primary); }
.cat-name { font-size: 14px; color: var(--el-text-color-primary); }

/* ================================================================
   分区通用
   ================================================================ */
.sec { margin-bottom: 36px; }
.sec-head {
  display: flex; align-items: flex-end; justify-content: space-between;
  gap: 20px; margin-bottom: 18px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--rxt-border);
}
.sh-left { position: relative; padding-left: 14px; }
.sh-left::before {
  content: '';
  position: absolute; left: 0; top: 3px; bottom: 3px;
  width: 4px;
  border-radius: 2px;
  background: var(--rxt-primary);
}
/* 分区标题下原来有一行灰色描述（「产地直发 · 下单后由农户直接发货」这类），
   5 处基本都在复述标题本身，已全部删除，所以 h3 不再需要下边距。 */
.sh-left h3 { margin: 0; font-size: 21px; font-weight: 700; letter-spacing: -0.2px; }
.sec-empty {
  padding: 40px 0; text-align: center;
  border: 1px dashed var(--rxt-border);
  border-radius: var(--rxt-radius-lg);
  background: var(--el-bg-color);
}
.sec-empty p { margin: 0 0 14px; font-size: 14px; color: var(--el-text-color-secondary); }

/* ================================================================
   商品区 —— 图片占卡片约 65%，这是生鲜电商模板的核心比例
   ================================================================ */
.goods-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
}
.goods-card {
  display: flex; flex-direction: column;
  border: 1px solid var(--rxt-border);
  border-radius: var(--rxt-radius-lg);
  background: var(--el-bg-color);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.24s, box-shadow 0.24s, border-color 0.24s;
}
.goods-card:hover {
  transform: translateY(-5px);
  border-color: var(--rxt-primary);
  box-shadow: var(--rxt-shadow-lg);
}
.gc-photo {
  position: relative;
  /* 卡片宽 268px，图高 178px ≈ 66% —— 对齐主流生鲜电商模板的「图占三分之二」比例。
     实测比例写在这里是为了下次调整时能直接核对，而不是凭感觉改高度。 */
  height: 178px;
  overflow: hidden;
}
.gc-photo img {
  position: relative; z-index: 1;
  width: 100%; height: 100%;
  object-fit: cover; display: block;
  transition: transform 0.45s, filter 0.35s;
}
.goods-card:hover .gc-photo img { transform: scale(1.08); filter: brightness(1.04); }
.gc-fallback {
  position: absolute; inset: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 44px; font-weight: 700;
  color: rgba(20, 48, 43, 0.18);
}
.gc-badge {
  position: absolute; z-index: 2; left: 10px; top: 10px;
  padding: 3px 9px;
  border-radius: var(--rxt-radius-pill);
  background: rgba(14, 122, 95, 0.92);
  color: #fff; font-size: 11px;
}
.gc-body { padding: 12px 14px 14px; display: flex; flex-direction: column; flex: 1; }
.gc-name {
  margin: 0 0 7px;
  font-size: 15px; font-weight: 600; line-height: 1.45;
  height: 44px;
  overflow: hidden;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
}
.gc-meta {
  display: flex; align-items: center; gap: 8px;
  margin: 0 0 8px;
  font-size: 12px; color: var(--rxt-ink-weak);
}
.gc-cat {
  padding: 1px 7px;
  border-radius: 3px;
  background: var(--el-color-primary-light-9);
  color: var(--rxt-primary-deep);
}
.gc-foot {
  display: flex; align-items: center; justify-content: space-between;
  gap: 10px; margin-top: auto;
}
.gc-price {
  font-size: 22px; font-weight: 800; line-height: 1;
  color: var(--rxt-accent);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.3px;
}
.gp-cur { font-size: 14px; margin-right: 1px; font-weight: 700; }
.gc-price i {
  font-style: normal; font-size: 12px; font-weight: 400;
  color: var(--rxt-ink-weak);
}
.gc-cart {
  flex: none;
  padding: 6px 14px;
  border: 1px solid var(--rxt-primary);
  border-radius: var(--rxt-radius-pill);
  background: transparent;
  color: var(--rxt-primary);
  font-family: inherit; font-size: 13px;
  cursor: pointer; transition: background 0.2s, color 0.2s;
}
.gc-cart:hover { background: var(--rxt-primary); color: #fff; }
.gc-stock { margin: 8px 0 0; font-size: 12px; color: var(--rxt-ink-weak); }

/* 骨架：浅色底，不用 el-skeleton 的默认灰块 */
.goods-card.is-ph { pointer-events: none; }
.gc-lines { padding: 14px; display: flex; flex-direction: column; gap: 9px; }
.gc-lines i, .ec-banner, .ec-avatar.is-ph-round, .news-card.is-ph, .loan-card.is-ph {
  background: linear-gradient(90deg, #eef2ef 25%, #f6f9f7 37%, #eef2ef 63%);
  background-size: 400% 100%;
  animation: bhShine 1.4s ease infinite;
}
@keyframes bhShine { 0% { background-position: 100% 50%; } 100% { background-position: 0 50%; } }

/* ================================================================
   田间求购
   ================================================================ */
.demand-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
}
.demand-card {
  border: 1px solid var(--rxt-border);
  border-radius: var(--rxt-radius-lg);
  background: var(--el-bg-color);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.24s, box-shadow 0.24s, border-color 0.24s;
}
.demand-card:hover {
  transform: translateY(-4px);
  border-color: var(--rxt-primary);
  box-shadow: var(--rxt-shadow-lg);
}
.dc-photo { position: relative; height: 124px; overflow: hidden; }
.dc-photo img {
  position: relative; z-index: 1;
  width: 100%; height: 100%; object-fit: cover; display: block;
}
.dc-fallback {
  position: absolute; inset: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 36px; font-weight: 700; color: rgba(20, 48, 43, 0.18);
}
.dc-badge {
  position: absolute; z-index: 2; right: 10px; top: 10px;
  padding: 3px 9px; border-radius: var(--rxt-radius-pill);
  background: rgba(255, 255, 255, 0.94); color: var(--rxt-primary-deep);
  font-size: 11px; font-weight: 600;
}
.dc-body { padding: 12px 14px 14px; }
.dc-top { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; }
.dc-top strong { font-size: 15px; font-weight: 600; }
.dc-need {
  flex: none; font-size: 12px; font-weight: 600;
  color: var(--rxt-primary);
}
.dc-desc {
  margin: 7px 0 10px;
  font-size: 12px; line-height: 1.6; color: var(--el-text-color-secondary);
  height: 38px; overflow: hidden;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
}
.dc-foot {
  display: flex; align-items: center; justify-content: space-between;
  padding-top: 10px; border-top: 1px solid var(--el-border-color-lighter);
  font-size: 12px; color: var(--rxt-ink-weak);
}
.dc-price b { font-size: 15px; color: var(--rxt-accent); font-variant-numeric: tabular-nums; }

/* ================================================================
   专家照片墙 —— 圆照 + 实景横幅，不要只有一行文字
   2026-10-10：从「一行 grid」改成**横向滑动轨道**。
   库里现在只有 2 位专家，一屏放得下，看不出差别；但专家一多，
   grid 只会往下堆行，一屏塞不下就得整页往下翻。滑轨则是往右走，
   页面高度不变。
   ================================================================ */
.expert-rail { position: relative; }
.expert-track {
  display: flex;
  gap: 18px; /* ★ 这个值 JS 里也有（RAIL_GAP），改一处必须改两处 */
  /* 横向滚、纵向裁。注意 overflow-x:auto 时 overflow-y 无法保持 visible，
     所以纵向余量只能靠 padding 撑。算过账：
       hover 上浮 4px       → padding-top 8px 够（余 4px）
       hover 阴影           → --rxt-shadow-lg 最外那层是
                              0 24px 48px -24px，向下伸出 24+48/2-24 = 24px，
                              上浮后变 20px；padding-bottom 18px 只切掉最后 2px
                              极淡的边缘，看不出来。 */
  padding: 8px 0 18px;
  overflow-x: auto;
  overflow-y: hidden;
  /* ★ 用 proximity 而不是 mandatory，但**不是**因为「mandatory 会锁死末端」——
     那个说法我实测过，是错的：Chrome 154 里把 maxScroll 也当成合法落点，
     proximity / mandatory 对同一组目标位置（860/1031/1100/1202/1310）
     落点**完全一致**，两种模式都能滚到最右端 1310 并停住（见下面的实测表）。
     选 proximity 是因为它在**定义上更保守**：Chrome 之外的引擎（WebKit /
     Firefox）里 proximity 的吸附是「就近轻轻吸一下」，不会把滚动末端锁死；
     而 mandatory 在这些引擎里必须停在吸附点上。滑轨末端锁死会导致
     「下一页按钮一直亮着但点了不动」，是更糟的失败模式，所以取保守的那个。
     本项目的箭头每一步位移正好是「卡宽 + 间距」的整数倍（天然落在吸附点上），
     所以在 Chrome 里该吸的照样吸，不损失什么。

     实测（8 张卡，1420px 内容区，吸附点 0/344/687/1031/1374，max=1310）：
       scroll-snap-type  设 860  设 1100  设 1202  设 1310
       x proximity       1031     1031      1310     1310
       x mandatory       1031     1031      1310     1310
       none               860     1100      1202     1310
     注：`getComputedStyle().scrollSnapType` 对 proximity 只回 "x"（省略了默认
     严格度），mandatory 才回 "x mandatory" —— 别把 "x" 误读成「没生效」。 */
  scroll-snap-type: x proximity;
  scroll-behavior: smooth;
  /* 原生滚动条藏掉 —— 已经有箭头 + 进度条做提示了，
     再挂一条系统灰条会破坏卡片区的干净。触控板横向滑动照常可用。 */
  scrollbar-width: none;
}
.expert-track::-webkit-scrollbar { display: none; }
@media (prefers-reduced-motion: reduce) {
  .expert-track { scroll-behavior: auto; }
}

/* ★ 卡片宽度：flex: 1 0 calc(25% - 31.5px) + min-width: 300px。
   三个参数都有原因，删任何一个都会出问题：

   - `flex-grow: 1`（第一个 1）—— **不足 4 张时把剩余宽度摊给卡片**。
     之前的实现是 grid `repeat(auto-fit, minmax(300px,1fr))`，auto-fit 会把
     空轨道连同间隙一起折叠，所以只有 2 位专家时每张卡会被撑到 701px 宽。
     换成固定 px 后同样是 2 位专家，卡片缩到 326px、右边空出 768px —— 这是
     实打实的视觉回归（实测确认过）。grow 让 ≤4 张时回到和老 grid 一样的宽度。
   - `flex-shrink: 0`（那个 0）—— **超过 4 张时不许压缩**，必须溢出才能滑动。
     如果让它 shrink，5 张以上会被压扁塞进一屏，滑轨就永远触发不了。
   - `calc(25% - 31.5px)` —— 4 张卡 + 3 个 18px 间距正好铺满一屏。
   - `min-width: 300px` —— 窗口窄时 25% 会小到放不下卡片标题 + 领域标签，
     卡在 300px 上；此时一屏显示 3 张，靠露头提示可滑。

   ★ 于是行为是：
     ≤4 张 → 卡片撑开铺满一屏，没有溢出，箭头/进度条不出现（和改之前一样）；
     ≥5 张 → 卡片固定 325.5px，溢出，出现箭头 + 进度条，可横向滑动。
   实测（1420px 内容区）：2 张=701px，3 张=461px，4 张=341.5px，
                          8 张=325.5px + 右侧露出 46px 的第 5 张。 */
.expert-card {
  position: relative;
  flex: 1 0 calc(25% - 31.5px);
  min-width: 300px;
  display: flex; flex-direction: column;
  padding-bottom: 16px;
  border: 1px solid var(--rxt-border);
  border-radius: var(--rxt-radius-lg);
  background: var(--el-bg-color);
  overflow: hidden;
  cursor: pointer;
  scroll-snap-align: start;
  transition: transform 0.24s, box-shadow 0.24s, border-color 0.24s;
}
.expert-card:hover {
  transform: translateY(-4px);
  border-color: var(--rxt-primary);
  box-shadow: var(--rxt-shadow-lg);
}
.ec-banner { position: relative; height: 118px; overflow: hidden; }
.ec-banner img { width: 100%; height: 100%; object-fit: cover; display: block; }
.ec-avatar {
  position: absolute; left: 20px; top: 76px;
  width: 82px; height: 82px;
  border-radius: 50%;
  border: 4px solid var(--el-bg-color);
  box-shadow: 0 2px 10px rgba(16, 42, 35, 0.12);
  overflow: hidden;
  display: flex; align-items: center; justify-content: center;
  font-size: 30px; font-weight: 700; color: var(--rxt-primary-deep);
}
.ec-avatar img { width: 100%; height: 100%; object-fit: cover; }
.ec-body { padding: 46px 20px 0; display: flex; flex-direction: column; flex: 1; }
.ec-title { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.ec-title strong { font-size: 18px; font-weight: 600; }
.ec-domain {
  padding: 2px 10px;
  border-radius: var(--rxt-radius-pill);
  background: var(--el-color-primary-light-9);
  color: var(--rxt-primary-deep);
  font-size: 12px;
}
.ec-intro {
  margin: 0 0 14px;
  font-size: 13px; line-height: 1.7; color: var(--el-text-color-secondary);
  min-height: 44px;
}
.ec-foot {
  display: flex; align-items: center; justify-content: space-between;
  gap: 12px; margin-top: auto;
  padding-top: 12px; border-top: 1px solid var(--el-border-color-lighter);
}
.ec-stat { font-size: 12px; color: var(--rxt-ink-weak); }
.ec-btn {
  flex: none;
  padding: 7px 18px;
  border: 0; border-radius: var(--rxt-radius-pill);
  background: var(--rxt-primary); color: #fff;
  font-family: inherit; font-size: 13px;
  cursor: pointer; transition: background 0.2s;
}
.ec-btn:hover { background: var(--rxt-primary-deep); }
/* 骨架卡给个和真实卡接近的高度（实测真卡约 302px），
   否则加载完成时整块会往下撑一截，视觉上「跳一下」 */
.expert-card.is-ph { pointer-events: none; min-height: 300px; }

/* ================================================================
   分区头右侧：滑动箭头 + 「预约专家 →」
   ================================================================ */
.sh-right { display: flex; align-items: center; gap: 14px; }
.rail-navs { display: flex; gap: 8px; }
.rail-nav {
  width: 32px; height: 32px;
  display: flex; align-items: center; justify-content: center;
  padding: 0;
  border: 1px solid var(--rxt-border);
  border-radius: 50%;
  background: var(--el-bg-color);
  color: var(--rxt-ink);
  cursor: pointer;
  transition: background 0.2s, color 0.2s, border-color 0.2s, opacity 0.2s;
}
.rail-nav:hover:not(:disabled) {
  background: var(--rxt-primary);
  border-color: var(--rxt-primary);
  color: #fff;
}
/* 到头了：置灰 + 不可点。比直接隐藏好 —— 位置固定，不会因为按钮
   忽隐忽现让「预约专家 →」左右横跳。 */
.rail-nav:disabled { opacity: 0.3; cursor: default; }

/* 进度条：滑块宽度 = 可视比例，位置 = 已滚比例，语义同原生滚动条。
   不加 transition —— 原生平滑滚动本身就在动，再加过渡会拖后腿。 */
.rail-bar {
  position: relative;
  height: 3px;
  margin-top: -10px; /* 抵消轨道底部为阴影留的 padding，视觉上贴住卡片 */
  border-radius: 2px;
  background: var(--rxt-border);
  overflow: hidden;
}
.rail-bar i {
  position: absolute; top: 0; bottom: 0;
  border-radius: 2px;
  background: var(--rxt-primary);
}

/* ================================================================
   惠农信贷（一行三列）
   「农业知识」原来在这一行与信贷并排，现在整块提到页面头部做「平台资讯」，
   两栏结构取消（原 .bh-body / .bh-side-* / .know-* 样式一并删除）。
   卡片内改左右布局：左边产品信息，右边大号利率 ——
   460px 的宽度里三行文字纵向堆叠会显得很空。
   ================================================================ */
.loan-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.loan-card {
  display: flex; align-items: center; justify-content: space-between; gap: 18px;
  padding: 18px 20px;
  border: 1px solid var(--rxt-border);
  border-radius: var(--rxt-radius-lg);
  background: var(--el-bg-color);
  cursor: pointer;
  transition: border-color 0.2s, transform 0.2s, box-shadow 0.2s;
}
.loan-card:hover {
  border-color: var(--rxt-accent);
  transform: translateY(-2px);
  box-shadow: var(--rxt-shadow-md);
}
.loan-card.is-ph { height: 104px; cursor: default; }
.lb-main { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.lb-main strong { font-size: 15px; font-weight: 600; }
.lb-range { margin: 0; font-size: 13px; color: var(--rxt-primary-deep); font-weight: 500; }
.lb-cond {
  margin: 0; font-size: 12px; line-height: 1.6; color: var(--el-text-color-secondary);
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.lb-rate {
  flex: none;
  font-size: 26px; font-weight: 700; line-height: 1;
  color: var(--rxt-accent); font-variant-numeric: tabular-nums;
}
.lb-rate i { font-style: normal; font-size: 13px; }

/* ---------- 空态提示条（替代原来的整宽大卡） ---------- */
.empty-bar {
  display: flex; align-items: center; justify-content: space-between; gap: 20px;
  margin-bottom: 18px;
  padding: 14px 20px;
  border: 1px dashed var(--rxt-border);
  border-radius: var(--rxt-radius-lg);
  background: var(--el-color-primary-light-9);
}
.eb-text { display: flex; align-items: baseline; gap: 12px; min-width: 0; }
.eb-text strong { flex: none; font-size: 15px; font-weight: 600; }
.eb-text span {
  font-size: 13px; color: var(--el-text-color-secondary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
</style>