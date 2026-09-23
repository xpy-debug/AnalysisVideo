<template>
  <div class="app-shell" :class="{ 'nav-open': navOpen }">
    <header class="topstrip">
      <button
          class="nav-toggle"
          type="button"
          :aria-expanded="navOpen"
          aria-label="切换侧边导航"
          @click="toggleNav"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><line x1="3" y1="7" x2="21" y2="7"></line><line x1="3" y1="12" x2="21" y2="12"></line><line x1="3" y1="17" x2="21" y2="17"></line></svg>
      </button>
      <span class="topstrip-label">AnalysisVideo · 智能视频解析</span>
      <span class="topstrip-right">
        <span
            class="status-dot"
            :class="{ 'is-live': uploading || activeTasks.length > 0, 'is-off': isOffline }"
            aria-hidden="true"
        ></span>
        <span class="topstrip-status">{{ systemStatusText }}</span>
      </span>
    </header>

    <div v-if="navOpen" class="nav-scrim" @click="closeNav"></div>

    <aside class="side-nav" aria-label="主导航">
      <div class="nav-brand">
        <span class="nav-brand-mark">AnalysisVideo</span>
        <span class="nav-brand-sub">智能视频解析 · 工作台</span>
      </div>

      <nav class="nav-menu">
        <button
            type="button"
            class="nav-item"
            :class="{ 'is-active': view === 'upload' }"
            :aria-current="view === 'upload' ? 'page' : null"
            @click="goTo('upload')"
        >上传页面</button>
        <button
            type="button"
            class="nav-item"
            :class="{ 'is-active': view === 'database' }"
            :aria-current="view === 'database' ? 'page' : null"
            @click="goTo('database')"
        >数据库</button>
      </nav>

      <div class="nav-foot">
        <div
            class="signal"
            :class="{ 'is-live': uploading || activeTasks.length > 0, 'is-off': isOffline }"
            role="status"
            aria-live="polite"
        >
          <span class="signal-led" aria-hidden="true"></span>
          <span class="signal-text">{{ systemStatusText }}</span>
        </div>

        <button v-if="!currentUser" class="auth-btn" @click="openAuthModal">
          <span class="btn-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path><circle cx="12" cy="7" r="4"></circle></svg>
          </span>
          登录 / 注册
        </button>

        <div v-else class="user-profile">
          <span class="user-name">{{ currentUser.nickname }}</span>
          <button class="logout-btn" @click="logout" title="退出登录" aria-label="退出登录">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path><polyline points="16 17 21 12 16 7"></polyline><line x1="21" y1="12" x2="9" y2="12"></line></svg>
          </button>
        </div>
      </div>
    </aside>

    <div class="view-port">
      <main class="workstation">
        <h1 class="sr-only">AnalysisVideo 视频分析工作台</h1>

        <section v-if="view === 'upload'" class="page page-upload" aria-labelledby="upload-title">
          <p class="eyebrow">上传 / 新任务</p>
          <h2 id="upload-title" class="display">上传视频</h2>
          <p class="lede">上传本地视频，或粘贴一个视频链接。Agent 会按你的目标拆解内容，产出带时间戳证据的结构化结果。</p>

          <div
              class="deck-frame"
              :class="{ 'is-dragover': isDragOver }"
              @dragenter.prevent="handleDragEnter"
              @dragover.prevent="isDragOver = true"
              @dragleave.prevent="handleDragLeave"
              @drop.prevent="handleDrop"
          >
            <input
                type="file"
                id="video-input"
                @change="e => handleFileChange(e, 'video')"
                accept="video/*,.mkv,.avi,.mov,.mp4,.webm,.m4v"
                hidden
            />
            <input
                type="file"
                id="audio-input"
                @change="e => handleFileChange(e, 'audio')"
                accept="audio/*,.mp3,.wav,.m4a,.aac,.flac,.ogg"
                hidden
            />

            <template v-if="!uploading">
              <label for="video-input" class="upload-block upload-block-file">
                <span class="block-icon">
                  <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"><polygon points="23 7 16 12 23 17 23 7"></polygon><rect x="1" y="5" width="15" height="14" rx="2" ry="2"></rect></svg>
                </span>
                <span class="block-title">视频文件</span>
                <span class="block-hint">{{ isDragOver ? '松手上传' : 'MP4 / MOV / MKV / WebM，抽帧 + 语音识别' }}</span>
              </label>

              <label for="audio-input" class="upload-block upload-block-audio">
                <span class="block-icon">
                  <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13"></path><circle cx="6" cy="18" r="3"></circle><circle cx="18" cy="16" r="3"></circle></svg>
                </span>
                <span class="block-title">音频文件</span>
                <span class="block-hint">MP3 / WAV / M4A / FLAC，仅做语音识别</span>
              </label>

              <div class="upload-block upload-block-url">
                <span class="block-icon">
                  <svg width="30" height="30" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="2" y1="12" x2="22" y2="12"></line><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1 4-10z"></path></svg>
                </span>
                <span class="block-title">视频链接</span>

                <div class="url-field" @click.stop>
                  <input
                      v-model="videoUrl"
                      type="text"
                      inputmode="url"
                      autocomplete="off"
                      spellcheck="false"
                      placeholder="粘贴视频链接"
                      aria-label="视频链接"
                      :disabled="uploading"
                      @keyup.enter="handleUrlUpload"
                  />
                  <button class="url-go" :disabled="uploading" @click="handleUrlUpload" aria-label="解析视频链接">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"></polyline></svg>
                  </button>
                </div>
              </div>
            </template>

            <div v-else class="deck-run">
              <div class="quantum-loader"></div>
              <span class="run-label">{{ uploadProgress.label }}</span>
              <span v-if="uploadProgress.filename" class="run-file">{{ uploadProgress.filename }}</span>
              <div
                  v-if="uploadProgress.percent !== null"
                  class="run-bar"
                  role="progressbar"
                  aria-label="视频上传进度"
                  aria-valuemin="0"
                  aria-valuemax="100"
                  :aria-valuenow="uploadProgress.percent"
              >
                <span :style="{ width: `${uploadProgress.percent}%` }"></span>
              </div>
              <span v-if="uploadProgress.detail" class="run-stat" aria-live="polite">{{ uploadProgress.detail }}</span>
              <span v-if="uploadProgress.warning" class="run-warn" role="status">{{ uploadProgress.warning }}</span>
              <button v-if="uploadAbort" type="button" class="run-cancel" @click="cancelUpload">取消上传</button>
            </div>
          </div>

          <div v-if="resumableFile && !uploading" class="resume" role="status">
            <span class="resume-text">{{ resumeHint }}</span>
            <div class="resume-actions">
              <button type="button" @click="resumeUpload">继续上传</button>
              <button type="button" @click="discardResumableUpload">重新开始</button>
            </div>
          </div>
        </section>

        <section v-else class="page page-database" aria-labelledby="db-title">
          <p class="eyebrow">数据库 / 资料库</p>
          <div class="page-head">
            <h2 id="db-title" class="display">资料库</h2>
            <span v-if="list.length" class="count-chip">{{ list.length }} 个视频</span>
          </div>
          <p class="lede">这里是你上传过的全部视频。选择一个执行文字提取、进入智能分析，或查看历史分析。</p>

          <label v-if="list.length" class="search">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
            <input v-model="searchQuery" type="search" placeholder="搜索视频名称" aria-label="搜索视频名称" />
          </label>

          <div v-if="list.length" class="rack-grid">
            <article
                v-for="item in visibleList"
                :key="item.id"
                class="tape"
                :class="cardStatusClass(item)"
            >
              <button
                  class="tape-del"
                  :disabled="deletingId === item.id"
                  :title="deletingId === item.id ? '正在删除…' : '删除视频'"
                  :aria-label="`删除 ${item.filename}`"
                  @click.stop="deleteItem(item)"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="3 6 5 6 21 6"></polyline><path d="M19 6l-1 14H6L5 6"></path><path d="M8 6V4h8v2"></path>
                </svg>
              </button>

              <div class="tape-label">
                <span class="tape-icon">
                  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><polygon points="23 7 16 12 23 17 23 7"></polygon><rect x="1" y="5" width="15" height="14" rx="2" ry="2"></rect></svg>
                </span>
                <h3 class="tape-name" :title="item.filename">{{ item.filename }}</h3>
              </div>

              <div class="tape-meta">
                <span class="tape-time">{{ formatTime(item.uploadTime) }}</span>
                <span
                    class="tape-status"
                    :class="cardStatusClass(item)"
                    :title="cardStatusTitle(item)"
                >
                  {{ cardStatusLabel(item) }}
                </span>
              </div>

              <div class="tape-actions">
                <button
                    class="dock-item"
                    :disabled="item.status !== 'COMPLETED'"
                    :title="actionTitle(item, '下载音频')"
                    @click="downloadAudio(item)"
                >
                  <span class="item-icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18V5l12-2v13"></path><circle cx="6" cy="18" r="3"></circle><circle cx="18" cy="16" r="3"></circle></svg>
                  </span>
                  <span class="item-label">下载音频</span>
                </button>

                <button
                    class="dock-item"
                    :disabled="item.status !== 'COMPLETED'"
                    :title="actionTitle(item, '提取文字')"
                    @click="transcribe(item.id)"
                >
                  <span class="item-icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
                  </span>
                  <span class="item-label">提取文字</span>
                </button>

                <button
                    class="dock-item ai-core"
                    :disabled="item.status !== 'COMPLETED'"
                    :title="actionTitle(item, '打开智能分析')"
                    @click="openAgent(item)"
                >
                  <span class="item-icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="4" y="4" width="16" height="16" rx="2" ry="2"></rect><rect x="9" y="9" width="6" height="6"></rect><line x1="9" y1="1" x2="9" y2="4"></line><line x1="15" y1="1" x2="15" y2="4"></line><line x1="9" y1="20" x2="9" y2="23"></line><line x1="15" y1="20" x2="15" y2="23"></line><line x1="20" y1="9" x2="23" y2="9"></line><line x1="20" y1="14" x2="23" y2="14"></line><line x1="1" y1="9" x2="4" y2="9"></line><line x1="1" y1="14" x2="4" y2="14"></line></svg>
                  </span>
                  <div class="label-group">
                    <span class="item-label">智能分析</span>
                  </div>
                </button>

                <button
                    class="dock-item"
                    :title="`查看 ${item.filename} 的历史分析记录`"
                    @click="openHistory(item)"
                >
                  <span class="item-icon">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 3v5h5"></path><path d="M3.05 13A9 9 0 1 0 6 5.3L3 8"></path><path d="M12 7v5l3.5 2"></path></svg>
                  </span>
                  <span class="item-label">历史分析</span>
                </button>
              </div>
            </article>
          </div>

          <div v-if="list.length && visibleList.length === 0" class="rack-empty">
            <p class="rack-empty-title">没有找到“{{ searchQuery }}”</p>
            <button type="button" @click="searchQuery = ''">清除搜索</button>
          </div>

          <div v-if="!list.length" class="rack-empty">
            <p class="rack-empty-title">资料库是空的</p>
            <p class="rack-empty-hint">回到「上传页面」选择本地文件，或粘贴一个视频链接。</p>
          </div>
        </section>
      </main>
    </div>

    <transition name="toast-pop">
      <div
          v-if="message"
          class="toast"
          :class="{ 'is-error': messageIsError }"
          :role="messageIsError ? 'alert' : 'status'"
          :aria-live="messageIsError ? 'assertive' : 'polite'"
          :title="messageIsError ? '点击关闭这条提示' : null"
          @click="dismissMessage"
      >
        {{ message }}
      </div>
    </transition>

      <div class="sidebar-backdrop" v-if="sidebar.visible" @click="closeSidebar"></div>
      <div
          ref="sidebarPanel"
          class="agent-window"
          :class="{ 'is-open': sidebar.visible }"
          :inert="!sidebar.visible"
          role="dialog"
          aria-modal="true"
          tabindex="-1"
          :aria-label="sidebar.title || '任务详情'"
      >
        <div class="agent-window-head">
          <div class="sidebar-title">
            <span class="icon" v-if="sidebar.type === 'ai'">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M2 12h2"></path><path d="M20 12h2"></path><path d="M12 2v2"></path><path d="M12 20v2"></path><path d="M20.2 6.47l-1.4 1.4"></path><path d="M15.9 5.35l-1.4-1.4"></path><path d="M9 11a3 3 0 1 0 6 0a3 3 0 0 0-6 0"></path></svg>
            </span>
            <span class="icon" v-else>
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
            </span>
            {{ sidebar.title }}
          </div>
          <button class="close-btn" @click="closeSidebar" aria-label="关闭分析面板">×</button>
        </div>
        <div class="agent-window-body" :class="{ 'is-split': sidebar.type === 'ai' }">
          <aside v-if="sidebar.type === 'ai'" class="agent-video">
            <div v-if="sidebar.playbackUrl" class="agent-video-frame">
              <video
                  ref="videoPlayer"
                  :src="sidebar.playbackUrl"
                  controls
                  playsinline
                  preload="metadata"
                  @error="handlePlaybackError"
              ></video>
            </div>
            <div v-else-if="sidebar.playbackLoading" class="video-evidence-loading">正在载入原视频...</div>
            <div v-else-if="sidebar.playbackError" class="video-evidence-error" role="alert">
              <span>{{ sidebar.playbackError }}</span>
              <button type="button" @click="retryPlayback">重新加载</button>
            </div>
            <p v-if="sidebar.playbackUrl" class="agent-video-hint">点击右侧结果中的时间戳，可跳转到对应画面</p>
          </aside>
          <div ref="sidebarBody" class="agent-main">
          <div v-if="sidebar.type === 'ai' && sidebar.mode === 'compose'" class="agent-composer">
            <p class="agent-caption">选择分析模式（决定产物形态）</p>
            <div class="goal-presets agent-mode-row">
              <button
                  v-for="m in analysisModes"
                  :key="m.value"
                  :class="{ active: sidebar.analysisMode === m.value }"
                  @click="sidebar.analysisMode = m.value"
              >
                <strong>{{ m.title }}</strong>
                <span>{{ m.description }}</span>
              </button>
            </div>
            <p class="agent-caption">告诉 Agent 你希望从视频中得到什么产物</p>
            <p v-if="sidebar.error" class="inline-error" role="alert">{{ sidebar.error }}</p>
            <textarea
                v-model="sidebar.goal"
                maxlength="500"
                placeholder="例如：梳理核心观点，给出带时间戳的证据和可执行建议（Ctrl / ⌘ + Enter 提交）"
                @keydown.ctrl.enter.prevent="submitAgent"
                @keydown.meta.enter.prevent="submitAgent"
            ></textarea>
            <p v-if="sidebar.goal.length > 400" class="field-counter">已输入 {{ sidebar.goal.length }} / 500 字</p>
            <div class="goal-presets">
              <button
                  v-for="preset in goalPresets"
                  :key="preset.title"
                  :class="{ active: sidebar.goal === preset.prompt }"
                  @click="sidebar.goal = preset.prompt"
              >
                <strong>{{ preset.title }}</strong>
                <span>{{ preset.description }}</span>
              </button>
            </div>
            <button class="agent-run-btn" :disabled="!sidebar.goal.trim()" @click="submitAgent">
              {{ sidebar.error ? '重新分析' : '开始分析' }}
            </button>
          </div>

          <div v-else-if="sidebar.loading" class="agent-running">
            <div class="loading-state">
              <div class="quantum-loader small"></div>
              <p aria-live="polite">{{ loadingHeadline }}</p>
              <p v-if="sidebar.streamOffline" class="stream-offline" role="status">
                连接中断，正在自动重连（第 {{ sidebar.streamRetry }} 次）· 任务仍在服务端继续
              </p>
              <p class="loading-hint">可以关闭本面板，任务会在后台继续，完成后会通知你</p>
            </div>
            <div v-if="sidebar.plan?.tasks?.length" class="agent-meta-block">
              <span class="meta-label">任务计划</span>
              <ol><li v-for="task in sidebar.plan.tasks" :key="task">{{ task }}</li></ol>
            </div>
            <div v-if="traceStages.length" class="agent-meta-block">
              <span class="meta-label">已完成阶段</span>
              <div class="stage-list"><span v-for="stage in traceStages" :key="stage[0]">{{ stage[0] }} · {{ stage[1] }}</span></div>
            </div>
          </div>

          <div v-else>
            <div v-if="sidebar.type === 'ai'">
              <div class="result-actions">
                <button type="button" @click="startNewAnalysis">更换产物</button>
                <button type="button" :disabled="!sidebar.content" @click="copyResult">复制结果</button>
                <button type="button" :disabled="!sidebar.content" @click="downloadResult">导出 Markdown</button>
              </div>
              <div class="evidence-search">
                <div class="evidence-search-form">
                  <input
                      v-model="sidebar.evidenceQuery"
                      aria-label="视频证据检索"
                      maxlength="500"
                      placeholder="定位 PPT、字幕、代码或某段讲解"
                      @keyup.enter="searchEvidence"
                  />
                  <button type="button" :disabled="sidebar.evidenceLoading || !sidebar.evidenceQuery.trim()" @click="searchEvidence">
                    {{ sidebar.evidenceLoading ? '检索中' : '定位证据' }}
                  </button>
                </div>
                <p v-if="sidebar.evidenceError" class="evidence-search-error" aria-live="polite">{{ sidebar.evidenceError }}</p>
                <div v-if="sidebar.evidenceResults.length" class="evidence-search-results" aria-live="polite">
                  <button
                      v-for="hit in sidebar.evidenceResults"
                      :key="`${hit.startMs}-${hit.endMs}`"
                      type="button"
                      :title="hit.snippet || '该时间段暂无可展示文本'"
                      @click="seekToEvidence(hit.startMs)"
                  >
                    <strong>{{ formatEvidenceTime(hit.startMs) }}</strong>
                    <small>{{ hit.source || '视频证据' }}</small>
                    <span>{{ hit.snippet || '该时间段暂无可展示文本' }}</span>
                  </button>
                </div>
              </div>
              <div class="markdown-content" v-html="renderedMarkdown" @click="seekEvidence"></div>
              <details v-if="sidebar.plan?.tasks?.length || traceStages.length" class="agent-inspector">
                <summary>分析详情</summary>
                <div class="agent-inspector-content">
                <div v-if="sidebar.plan?.tasks?.length" class="agent-meta-block">
                  <span class="meta-label">Planner 任务</span>
                  <div v-if="sidebar.editingPlan" class="plan-editor">
                    <div v-for="(_, index) in sidebar.planDraft" :key="index" class="plan-editor-row">
                      <input v-model="sidebar.planDraft[index]" maxlength="500" :aria-label="`任务 ${index + 1}`" />
                      <button type="button" title="删除任务" @click="removePlanTask(index)">×</button>
                    </div>
                    <button v-if="sidebar.planDraft.length < 5" type="button" @click="addPlanTask">添加任务</button>
                    <div class="plan-editor-actions">
                      <button type="button" @click="cancelPlanEdit">取消</button>
                      <button type="button" :disabled="sidebar.rerunLoading" @click="rerunWithPlan">
                        {{ sidebar.rerunLoading ? '提交中' : '按新计划重跑' }}
                      </button>
                    </div>
                  </div>
                  <template v-else>
                    <ol><li v-for="task in sidebar.plan.tasks" :key="task">{{ task }}</li></ol>
                    <button type="button" class="plan-edit-trigger" @click="startPlanEdit">调整计划</button>
                  </template>
                </div>
                <div v-if="traceStages.length" class="agent-meta-block">
                  <span class="meta-label">执行轨迹</span>
                  <div class="stage-list"><span v-for="stage in traceStages" :key="stage[0]">{{ stage[0] }} · {{ stage[1] }}</span></div>
                </div>
                <div v-if="sidebar.evaluation && Object.keys(sidebar.evaluation).length" class="quality-row">
                  <span>结构完整 {{ sidebar.evaluation.structuredValid ? '通过' : '待完善' }}</span>
                  <span>证据支持 {{ formatPercent(sidebar.evaluation.evidenceSupportRate) }}</span>
                  <span>Critic {{ sidebar.evaluation.criticPassed ? '通过' : '达到轮次上限' }}</span>
                </div>
                </div>
              </details>
              <div class="follow-up-box">
                <textarea
                    v-model="sidebar.followUp"
                    maxlength="500"
                    placeholder="基于视频继续追问...（Ctrl / ⌘ + Enter 发送）"
                    @keydown.ctrl.enter.prevent="submitFollowUp"
                    @keydown.meta.enter.prevent="submitFollowUp"
                ></textarea>
                <button :disabled="sidebar.followUpLoading || !sidebar.followUp.trim()" @click="submitFollowUp">
                  {{ sidebar.followUpLoading ? '分析中' : '追问' }}
                </button>
              </div>
              <div class="feedback-row">
                <span>这个结果有帮助吗？</span>
                <button :disabled="sidebar.feedbackLoading" :class="{ active: sidebar.feedback === 1 }" :aria-pressed="sidebar.feedback === 1" @click="sendFeedback(1)" title="有帮助">赞</button>
                <button :disabled="sidebar.feedbackLoading" :class="{ active: sidebar.feedback === -1 }" :aria-pressed="sidebar.feedback === -1" @click="sendFeedback(-1)" title="需改进">踩</button>
              </div>
            </div>
            <div v-else class="text-content">
              <p v-if="sidebar.error" class="inline-error" role="alert">{{ sidebar.error }}</p>
              <template v-if="sidebar.content">
                <div class="result-actions">
                  <button type="button" @click="copyResult">复制全文</button>
                  <button type="button" @click="downloadResult">导出文本</button>
                </div>
                <p class="text-meta">{{ transcriptMeta }}</p>
                <pre>{{ sidebar.content }}</pre>
              </template>
              <p v-else-if="!sidebar.error" class="text-meta">这个视频还没有可展示的转写文本。</p>
            </div>
          </div>
          </div>
        </div>
      </div>

      <div v-if="showAuthModal" class="auth-backdrop" @click.self="closeAuthModal">
        <div
            ref="authPanel"
            class="auth-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="auth-title"
            @keydown="trapAuthFocus"
        >
          <div class="auth-header">
            <h2 id="auth-title" class="auth-title">{{ authMode === 'login' ? '用户登录' : '新用户注册' }}</h2>
            <button class="close-btn" @click="closeAuthModal" aria-label="关闭登录窗口">×</button>
          </div>
          <form class="auth-body" @submit.prevent="handleAuth">
            <div class="input-group">
              <label for="auth-username">用户名</label>
              <input id="auth-username" v-model="authForm.username" type="text" placeholder="输入账号" autocomplete="username" autofocus />
            </div>
            <div class="input-group">
              <label for="auth-password">密码</label>
              <input id="auth-password" v-model="authForm.password" type="password" placeholder="输入密码" :autocomplete="authMode === 'login' ? 'current-password' : 'new-password'" />
            </div>
            <div class="input-group" v-if="authMode === 'register'">
              <label for="auth-nickname">昵称</label>
              <input id="auth-nickname" v-model="authForm.nickname" type="text" placeholder="设置一个好听的名字" autocomplete="nickname" />
            </div>
            <div class="auth-action">
              <button type="submit" class="cyber-btn" :disabled="authLoading">
                <span v-if="!authLoading">{{ authMode === 'login' ? '立即登录' : '提交注册' }}</span>
                <span v-else>请求处理中...</span>
              </button>
            </div>
            <div class="auth-toggle">
              <span class="toggle-text">{{ authMode === 'login' ? '没有账号?' : '已有账号?' }}</span>
              <button type="button" class="toggle-link" @click="switchAuthMode()">{{ authMode === 'login' ? '去注册' : '去登录' }}</button>
            </div>
            <p
                v-if="authMessage"
                class="auth-msg"
                :class="{'error': authError}"
                :role="authError ? 'alert' : 'status'"
                aria-live="polite"
            >{{ authMessage }}</p>
          </form>
        </div>
      </div>

      <div v-if="showHistoryModal" class="auth-backdrop" @click.self="closeHistory">
        <div
            ref="historyPanel"
            class="history-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="history-title"
            @keydown="trapHistoryFocus"
        >
          <div class="auth-header">
            <h2 id="history-title" class="auth-title">历史分析 · {{ historyFilename }}</h2>
            <button class="close-btn" @click="closeHistory" aria-label="关闭历史窗口">×</button>
          </div>
          <div class="history-body">
            <p v-if="historyLoading" class="history-hint">正在加载历史记录…</p>
            <p v-else-if="historyError" class="history-hint is-error" role="alert">{{ historyError }}</p>
            <p v-else-if="!historyItems.length" class="history-hint">这个视频还没有成功分析过的记录</p>
            <template v-else>
              <div class="history-content">
                <ul class="history-list">
                  <li v-for="(entry, index) in historyItems" :key="index">
                    <button
                        type="button"
                        class="history-entry"
                        :class="{ active: historySelected === index }"
                        @click="historySelected = index"
                    >
                      <span class="history-goal">{{ entry.goal }}</span>
                      <span class="history-meta">
                        {{ formatTime(entry.analyzedAt) }}
                        <template v-if="entry.stage === 'ANALYSIS_COMPLETED_WITH_WARNINGS'"> · 带警告</template>
                      </span>
                    </button>
                  </li>
                </ul>
                <div v-if="selectedHistory" class="history-detail">
                  <div class="history-detail-head">
                    <strong>{{ selectedHistory.title }}</strong>
                    <span class="history-detail-goal">目标：{{ selectedHistory.goal }}</span>
                  </div>
                  <div class="history-detail-scroll markdown-content" v-html="historyMarkdown" @click="ignoreHistoryEvidence"></div>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch, onMounted, onUnmounted } from 'vue'
import { apiRequest, clearAuthToken, hasAuthToken, setAuthToken } from './api'
import {
  forgetUploadProgress,
  formatBytes,
  formatDurationText,
  hasUploadProgress,
  isSupportedAudio,
  isSupportedVideo,
  uploadMediaInChunks,
  validateMediaFile
} from './chunkUpload'
import { DEMO_ITEM } from './demoData'
import { renderMarkdown } from './markdown'
import { createTaskStreams } from './taskEvents'
import { useAnalysisWorkspace } from './useAnalysisWorkspace'

// --- 变量定义 ---
const DEMO_MODE = new URLSearchParams(window.location.search).has('demo')
const MESSAGE_TIMEOUT_MS = 4000
const file = ref(null)
const fileKind = ref('video')
const videoUrl = ref('')
const message = ref('')
const messageIsError = ref(false)
const uploading = ref(false)
const uploadProgress = ref({ label: '准备上传', filename: '', percent: null, detail: '', warning: '' })
const uploadAbort = ref(null)
const resumableFile = ref(null)
const resumableChunks = ref({ done: 0, total: 0 })
const list = ref([])
const searchQuery = ref('')
const videoPlayer = ref(null)
const sidebarPanel = ref(null)
const sidebarBody = ref(null)
const authPanel = ref(null)
const deletingId = ref(null)
const isOffline = ref(typeof navigator !== 'undefined' && navigator.onLine === false)
const activeTasks = ref([])
const elapsedSeconds = ref(0)
// 两个独立页面 + 滑动侧边栏：宽屏默认展开，窄屏默认收起为抽屉。
const view = ref('upload')
const navOpen = ref(typeof window !== 'undefined' ? window.innerWidth >= 900 : true)
const visibleList = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase()
  if (!query) return list.value
  return list.value.filter(item => item.filename?.toLocaleLowerCase().includes(query))
})
const isDragOver = ref(false)
const currentUser = ref(null)
const showAuthModal = ref(false)
const authMode = ref('login')
const authLoading = ref(false)
const authMessage = ref('')
const authError = ref(false)
const authForm = ref({ username: '', password: '', nickname: '' })
const historyPanel = ref(null)
const showHistoryModal = ref(false)
const historyLoading = ref(false)
const historyError = ref('')
const historyFilename = ref('')
const historyItems = ref([])
const historySelected = ref(0)
const selectedHistory = computed(() => historyItems.value[historySelected.value] || null)
const historyMarkdown = computed(() =>
  selectedHistory.value ? renderMarkdown(selectedHistory.value.markdown) : '')
const taskStreams = createTaskStreams({
  onActiveChange: tasks => { activeTasks.value = tasks }
})
let dragDepth = 0
let messageTimer = null
let elapsedTimer = null
let lastUploadProgress = {}
let focusBeforeAuth = null
let focusBeforeSidebar = null
let focusBeforeHistory = null

// --- 状态展示 ---
const activeTaskOf = mediaId => activeTasks.value.find(task => String(task.id) === String(mediaId))

const systemStatusText = computed(() => {
  if (isOffline.value) return '网络已断开'
  if (uploading.value) {
    return uploadProgress.value.percent !== null
      ? `上传 ${uploadProgress.value.percent}%`
      : '处理中'
  }
  if (activeTasks.value.length) return `后台任务 ${activeTasks.value.length}`
  return '系统就绪'
})

const cardStatusClass = item => {
  if (activeTaskOf(item.id)) return 'processing'
  return mediaStatusClass(item.status)
}
const cardStatusLabel = item => {
  const task = activeTaskOf(item.id)
  if (task) return task.type === 'ai' ? 'ANALYZING' : 'TRANSCRIBING'
  return mediaStatusLabel(item.status)
}
const cardStatusTitle = item => {
  const task = activeTaskOf(item.id)
  if (!task) return null
  return task.type === 'ai'
    ? 'AI 分析正在后台执行，完成后会提示你'
    : '文字提取正在后台执行，完成后会提示你'
}
const actionTitle = (item, label) => item.status === 'COMPLETED'
  ? null
  : `视频尚未处理完成，暂时无法${label}`

const elapsedLabel = computed(() => {
  const total = elapsedSeconds.value
  if (total < 1) return ''
  const minutes = String(Math.floor(total / 60)).padStart(2, '0')
  return `${minutes}:${String(total % 60).padStart(2, '0')}`
})

const loadingHeadline = computed(() => {
  const fallback = sidebar.value.type === 'ai'
    ? 'Agent 正在分析视频证据'
    : '正在识别视频语音'
  const headline = sidebar.value.statusMessage || fallback
  // 用“已等待”而不是“已运行”：接管历史任务时计时是从打开面板算起的。
  return elapsedLabel.value ? `${headline} · 已等待 ${elapsedLabel.value}` : headline
})

const transcriptMeta = computed(() => {
  const length = sidebar.value.content?.length || 0
  if (!length) return ''
  return `共 ${length.toLocaleString('zh-CN')} 字`
})

const resumeHint = computed(() => {
  const target = resumableFile.value
  if (!target) return ''
  const { done, total } = resumableChunks.value
  const progress = total ? `已完成 ${Math.round((done / total) * 100)}%` : '已保留上传进度'
  return `${target.name} ${progress}，可继续未完成的上传`
})

// --- 核心业务逻辑 ---

const handleDragEnter = () => {
  dragDepth += 1
  isDragOver.value = true
}

// 拖过子元素也会触发 dragleave，用进出计数避免提示文案反复闪烁。
const handleDragLeave = () => {
  dragDepth = Math.max(0, dragDepth - 1)
  if (!dragDepth) isDragOver.value = false
}

const resetDragState = () => {
  dragDepth = 0
  isDragOver.value = false
}

/** 统一入口：登录、格式、体积三道校验全部在进入上传态之前完成。 */
const startUpload = async (selectedFile, extraFileCount = 0, expectedKind = null) => {
  if (uploading.value) {
    showMsg('已有上传任务在进行，请等当前任务结束', true)
    return
  }
  if (!currentUser.value) {
    showMsg('⚠️ 权限受限：请先登录系统', true)
    openAuthModal()
    return
  }
  if (!selectedFile) return
  const kind = isSupportedVideo(selectedFile) ? 'video'
    : (isSupportedAudio(selectedFile) ? 'audio' : null)
  if (!kind) {
    showMsg(`⚠️ ${selectedFile.name} 不是受支持的音视频格式`, true)
    return
  }
  // 上传页把「视频文件」「音频文件」拆成两个入口：选错入口时直接退回，
  // 否则音频会被当成视频入库，后续多跑一遍无意义的抽帧与 OCR。
  if (expectedKind && kind !== expectedKind) {
    showMsg(`⚠️ 这个入口只能上传${expectedKind === 'audio' ? '音频' : '视频'}，${selectedFile.name} 请改用另一个入口`, true)
    return
  }
  const invalid = validateMediaFile(selectedFile, kind)
  if (invalid) {
    showMsg(`⚠️ ${invalid}`, true)
    return
  }
  if (extraFileCount > 0) {
    showMsg(`一次只处理一个文件，已选择 ${selectedFile.name}，其余 ${extraFileCount} 个已忽略`)
  }
  fileKind.value = kind
  file.value = selectedFile
  videoUrl.value = ''
  await uploadFile()
}

const handleFileChange = async (e, kind = null) => {
  const selected = e.target.files
  await startUpload(selected?.[0], Math.max(0, (selected?.length || 0) - 1), kind)
  e.target.value = ''
}

const handleDrop = async (e) => {
  resetDragState()
  const dropped = e.dataTransfer?.files
  if (!dropped?.length) return
  await startUpload(dropped[0], dropped.length - 1)
}

const buildUploadWarning = progress => {
  if (progress.retryingCount) {
    return `网络不稳定，正在重试 ${progress.retryingCount} 个分片（第 ${progress.retryAttempt}/${progress.retryMaxAttempts} 次）`
  }
  if (progress.resumedChunks) {
    return `已续传：跳过 ${progress.resumedChunks} 个此前完成的分片`
  }
  return ''
}

const applyUploadProgress = progress => {
  lastUploadProgress = progress
  const merging = progress.phase === 'merging'
  const detail = [`${formatBytes(progress.uploadedBytes)} / ${formatBytes(progress.totalBytes)}`]
  detail.push(`分片 ${progress.completedChunks}/${progress.totalChunks}`)
  if (!merging && progress.bytesPerSecond) {
    detail.push(`${formatBytes(progress.bytesPerSecond)}/s`)
    const eta = formatDurationText(progress.etaSeconds)
    if (eta) detail.push(`剩余约 ${eta}`)
  }
  uploadProgress.value = {
    label: merging ? '分片已全部送达，正在服务端合并' : '正在安全上传',
    filename: file.value?.name || uploadProgress.value.filename,
    percent: progress.percent,
    detail: detail.join(' · '),
    warning: buildUploadWarning(progress)
  }
}

const rememberResumableUpload = target => {
  if (!target || !hasUploadProgress(target)) {
    resumableFile.value = null
    return
  }
  resumableFile.value = target
  resumableChunks.value = {
    done: lastUploadProgress.completedChunks || 0,
    total: lastUploadProgress.totalChunks || 0
  }
}

const uploadFile = async () => {
  const target = file.value
  if (!target) return
  if (DEMO_MODE) {
    showMsg('演示模式：已模拟完成分片上传')
    return
  }

  const controller = new AbortController()
  uploadAbort.value = controller
  uploading.value = true
  resumableFile.value = null
  lastUploadProgress = {}
  const uploadUserId = currentUser.value?.id
  uploadProgress.value = {
    label: hasUploadProgress(target) ? '正在核对已上传分片' : '准备分片上传',
    filename: target.name,
    percent: 0,
    detail: `0 B / ${formatBytes(target.size)}`,
    warning: ''
  }

  try {
    const uploadedMedia = await uploadMediaInChunks(
      target, applyUploadProgress, controller.signal, fileKind.value)
    if (currentUser.value?.id !== uploadUserId) return
    resumableFile.value = null
    showMsg(`✅ ${target.name} 上传完成`)
    await fetchList({ notify: true })
    openAgent(uploadedMedia)
  } catch (error) {
    if (currentUser.value?.id !== uploadUserId) return
    rememberResumableUpload(target)
    if (error?.aborted) {
      showMsg('上传已取消，进度已保留，可点“继续上传”接着传')
      return
    }
    console.error(error)
    showMsg(
      resumableFile.value
        ? `❌ 上传中断：${error.message}（进度已保留，可继续上传）`
        : `❌ 上传失败：${error.message}`,
      true
    )
  } finally {
    uploading.value = false
    uploadAbort.value = null
    file.value = null
  }
}

const cancelUpload = () => {
  if (!uploadAbort.value) return
  uploadProgress.value = { ...uploadProgress.value, label: '正在取消上传', warning: '' }
  uploadAbort.value.abort()
}

const resumeUpload = async () => {
  const target = resumableFile.value
  if (!target || uploading.value) return
  file.value = target
  await uploadFile()
}

const discardResumableUpload = () => {
  forgetUploadProgress(resumableFile.value)
  resumableFile.value = null
  resumableChunks.value = { done: 0, total: 0 }
  showMsg('已清除保留的上传进度，下次将从头开始')
}

const handleUrlUpload = async () => {
  const normalizedUrl = videoUrl.value.trim()
  if (!normalizedUrl) return
  if (uploading.value) {
    showMsg('已有上传任务在进行，请等当前任务结束', true)
    return
  }
  if (DEMO_MODE) {
    videoUrl.value = ''
    showMsg('演示模式：已模拟完成链接解析')
    return
  }

  if (!currentUser.value) {
    showMsg('⚠️ 权限受限：请先登录系统', true)
    openAuthModal()
    return
  }

  let parsedUrl
  try {
    parsedUrl = new URL(normalizedUrl)
  } catch {
    parsedUrl = null
  }
  if (!parsedUrl || !['http:', 'https:'].includes(parsedUrl.protocol)) {
    showMsg('⚠️ 请输入合法的 http/https 链接', true)
    return
  }

  uploading.value = true
  const uploadUserId = currentUser.value?.id
  uploadProgress.value = {
    label: '正在解析视频链接',
    filename: parsedUrl.hostname,
    percent: null,
    detail: '服务端正在拉取源视频，时长取决于源站速度',
    warning: ''
  }
  messageIsError.value = false
  message.value = '正在解析链接并极速下载 (低码率模式)...'

  const formData = new FormData()
  formData.append('url', normalizedUrl)

  try {
    const res = await apiRequest('/media/upload-url', {
      method: 'POST',
      body: formData
    })
    if (!res.ok) throw new Error(await res.text())
    const uploadedMedia = await res.json()
    if (currentUser.value?.id !== uploadUserId) return

    showMsg('✅ 链接资源已入库')
    videoUrl.value = ''
    await fetchList({ notify: true })
    openAgent(uploadedMedia)
  } catch (error) {
    console.error(error)
    if (currentUser.value?.id !== uploadUserId) return
    let errMsg = error.message
    if (errMsg.includes("Unsupported URL")) errMsg = "不支持该平台链接"
    showMsg('❌ 解析失败: ' + errMsg, true)
  } finally {
    uploading.value = false
  }
}

/** 成功提示自动消失；错误提示保留到用户点掉，避免关键失败原因 4 秒后就没了。 */
const showMsg = (msg, isError = false) => {
  clearTimeout(messageTimer)
  messageTimer = null
  message.value = msg
  messageIsError.value = isError
  if (isError) return
  messageTimer = setTimeout(() => {
    if (message.value !== msg) return
    message.value = ''
    messageIsError.value = false
  }, MESSAGE_TIMEOUT_MS)
}

const dismissMessage = () => {
  if (!messageIsError.value) return
  clearTimeout(messageTimer)
  messageTimer = null
  message.value = ''
  messageIsError.value = false
}

const fetchList = async ({ notify = false } = {}) => {
  if (DEMO_MODE) return list.value
  if (!currentUser.value) {
    list.value = []
    return list.value
  }
  try {
    // 带时间戳绕开浏览器缓存，避免删除/新增之后列表还是旧的。
    const res = await apiRequest(`/media/list?_t=${Date.now()}`)
    if (res.status === 401) return null
    if (!res.ok) throw new Error('加载视频列表失败')
    list.value = await res.json()
  } catch (error) {
    console.error(error)
    if (notify) showMsg('视频资料库加载失败，请稍后刷新', true)
    return null
  }
  return list.value
}

const mediaStatusClass = status => ['COMPLETED', 'PROCESSING', 'FAILED'].includes(status)
  ? status.toLowerCase()
  : 'unknown'
const mediaStatusLabel = status => ({
  COMPLETED: 'READY',
  PROCESSING: 'PROCESSING',
  FAILED: 'FAILED'
})[status] || 'PENDING'

const {
  sidebar,
  goalPresets,
  analysisModes,
  traceStages,
  renderedMarkdown,
  transcribe,
  closeSidebar,
  openAgent,
  submitAgent,
  startNewAnalysis,
  showDemoResult,
  startPlanEdit,
  cancelPlanEdit,
  addPlanTask,
  removePlanTask,
  rerunWithPlan,
  submitFollowUp,
  searchEvidence,
  sendFeedback,
  retryPlayback,
  handlePlaybackError,
  resetWorkspace,
  discardMediaWorkspace,
  formatPercent
} = useAnalysisWorkspace({
  demoMode: DEMO_MODE,
  taskStreams,
  showMessage: showMsg,
  refreshMediaList: fetchList,
  findMediaItem: id => list.value.find(item => item.id === id),
  onAnswerAppended: () => scrollToLatestAnswer()
})

/** 追问的答案追加在长文末尾，主动滚过去，否则用户会以为“点了没反应”。 */
const scrollToLatestAnswer = async () => {
  await nextTick()
  const container = sidebarBody.value?.querySelector('.markdown-content')
  if (!container) return
  const headings = container.querySelectorAll('h2, h3')
  const anchor = headings.length ? headings[headings.length - 1] : container.lastElementChild
  anchor?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const seekVideo = seconds => {
  if (!Number.isFinite(seconds)) return
  const player = videoPlayer.value
  if (!player) {
    if (sidebar.value.playbackError) {
      showMsg('原视频加载失败，无法跳转，可先点“重新加载”', true)
    } else if (sidebar.value.playbackLoading) {
      showMsg('原视频还在载入，稍等一下再点这个时间戳')
    } else {
      showMsg('这个视频暂时没有可播放的原片，无法跳转', true)
    }
    return
  }
  if (player.readyState === 0) {
    player.addEventListener('loadedmetadata', () => seekVideo(seconds), { once: true })
    return
  }
  const duration = player.duration
  const maxTime = Number.isFinite(duration) ? Math.max(0, duration - 0.1) : Number.MAX_SAFE_INTEGER
  player.currentTime = Math.min(Math.max(0, seconds), maxTime)
  player.play().catch(() => {})
  player.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
}

const seekEvidence = event => {
  const link = event.target.closest('a[href^="#video-t="]')
  if (!link) return
  event.preventDefault()
  seekVideo(Number(link.getAttribute('href').split('=')[1]))
}

const seekToEvidence = timestampMs => seekVideo(Number(timestampMs) / 1000)
const formatEvidenceTime = timestampMs => {
  const seconds = Math.max(0, Math.floor(Number(timestampMs) / 1000))
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const time = `${String(minutes).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
  return hours ? `${String(hours).padStart(2, '0')}:${time}` : time
}

/** Clipboard API 在非 HTTPS 环境不可用，这里保留一条降级路径，避免“复制失败”变成死路。 */
const copyToClipboard = async text => {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    // 继续走下面的降级方案。
  }
  try {
    const scratch = document.createElement('textarea')
    scratch.value = text
    scratch.setAttribute('readonly', '')
    scratch.style.position = 'fixed'
    scratch.style.top = '0'
    scratch.style.opacity = '0'
    document.body.appendChild(scratch)
    scratch.select()
    const copied = document.execCommand('copy')
    document.body.removeChild(scratch)
    return copied
  } catch {
    return false
  }
}

const copyResult = async () => {
  const content = sidebar.value.content
  if (!content) {
    showMsg('还没有可复制的内容', true)
    return
  }
  const label = sidebar.value.type === 'ai' ? '分析结果' : '转写全文'
  if (await copyToClipboard(content)) showMsg(`${label}已复制`)
  else showMsg('复制失败，请手动选中内容后复制', true)
}

const resultFileBaseName = () => {
  const title = sidebar.value.title || ''
  const raw = title.split(' · ').slice(1).join(' · ') || title
  const cleaned = raw.replace(/\.[^/.]+$/, '').replace(/[\\/:*?"<>|]/g, '_').trim()
  return cleaned || (sidebar.value.type === 'ai' ? 'analysis' : 'transcript')
}

const downloadResult = () => {
  const content = sidebar.value.content
  if (!content) {
    showMsg('还没有可导出的内容', true)
    return
  }
  const isMarkdown = sidebar.value.type === 'ai'
  const blob = new Blob([content], {
    type: isMarkdown ? 'text/markdown;charset=utf-8' : 'text/plain;charset=utf-8'
  })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${resultFileBaseName()}.${isMarkdown ? 'md' : 'txt'}`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // 立刻 revoke 在部分浏览器会导致下载拿不到内容，延后一拍更稳。
  setTimeout(() => URL.revokeObjectURL(url), 0)
  showMsg(`已导出 ${link.download}`)
}

const deleteItem = async (item) => {
  if (DEMO_MODE) {
    list.value = list.value.filter(i => i.id !== item.id)
    discardMediaWorkspace(item.id)
    showMsg('演示任务已移除')
    return
  }
  if (deletingId.value) return
  const runningTask = activeTaskOf(item.id)
  const warning = runningTask
    ? '\n\n注意：该视频还有任务正在后台执行，删除后这次的结果会丢失。'
    : ''
  if (!confirm(`确认要永久删除 "${item.filename}" 吗？${warning}`)) return
  deletingId.value = item.id
  try {
    const res = await apiRequest(`/media/delete?id=${item.id}`, { method: 'DELETE' })
    const text = await res.text()
    if (res.ok) {
      showMsg(`已删除 ${item.filename}`)
      list.value = list.value.filter(i => i.id !== item.id)
      discardMediaWorkspace(item.id)
    } else {
      showMsg('❌ ' + text, true)
    }
  } catch (e) {
    showMsg('❌ 删除请求失败', true)
  } finally {
    deletingId.value = null
  }
}

const formatTime = (timeStr) => {
  if (!timeStr) return '--'
  const date = new Date(timeStr)
  if (Number.isNaN(date.getTime())) return '--'
  return `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

const downloadAudio = async (item) => {
  if (DEMO_MODE) {
    showMsg(`演示模式：${item.filename} 音频已准备`)
    return
  }
  let fileName = item.filename || 'audio.mp3';
  fileName = fileName.replace(/\.[^/.]+$/, "") + ".mp3";
  try {
    showMsg('正在转码并下载...')
    const res = await apiRequest(`/analysis/download?id=${item.id}`)
    // 失败时后端返回的是 JSON 信封，api.js 会把 message 解包给 text()，
    // 这里读出来向上抛，避免把“视频不存在 / 无权访问 / 转码失败”统一显示成同一句话。
    if (!res.ok) throw new Error((await res.text()) || '请稍后重试')
    const blob = await res.blob()
    const downloadUrl = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = downloadUrl
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(downloadUrl)
    showMsg('✅ 下载完成')
  } catch (e) {
    showMsg('音频下载失败：' + (e?.message || '请稍后重试'), true)
  }
}

const restoreFocus = element => {
  if (element?.isConnected && typeof element.focus === 'function') element.focus()
}

/** 切换页面：窄屏上顺带收起抽屉，避免遮住刚打开的内容。 */
const goTo = target => {
  view.value = target
  if (typeof window !== 'undefined' && window.innerWidth < 900) navOpen.value = false
}
const toggleNav = () => { navOpen.value = !navOpen.value }
const closeNav = () => { navOpen.value = false }

const openAuthModal = () => {
  if (showAuthModal.value) return
  focusBeforeAuth = document.activeElement
  showAuthModal.value = true
  authMessage.value = ''
  authForm.value = { username: '', password: '', nickname: '' }
}
const closeAuthModal = () => {
  showAuthModal.value = false
  restoreFocus(focusBeforeAuth)
  focusBeforeAuth = null
}

/** 打开某个视频的历史分析窗口：拉取该视频全部已成功产出的结果，默认选中最近一次。 */
const openHistory = async (item) => {
  if (DEMO_MODE) {
    showMsg('演示模式暂不提供历史记录')
    return
  }
  if (showHistoryModal.value) return
  focusBeforeHistory = document.activeElement
  showHistoryModal.value = true
  historyFilename.value = item.filename
  historyItems.value = []
  historySelected.value = 0
  historyError.value = ''
  historyLoading.value = true
  try {
    const res = await apiRequest(`/analysis/history?id=${item.id}`)
    if (!res.ok) throw new Error((await res.text()) || '加载历史记录失败')
    const data = await res.json()
    historyItems.value = Array.isArray(data) ? data : []
  } catch (error) {
    historyError.value = error?.message || '加载历史记录失败'
  } finally {
    historyLoading.value = false
  }
}
const closeHistory = () => {
  showHistoryModal.value = false
  restoreFocus(focusBeforeHistory)
  focusBeforeHistory = null
}
/** 历史窗口里没有播放器，时间戳锚点跳不过去，拦截默认行为避免页面被顶到顶部。 */
const ignoreHistoryEvidence = event => {
  if (event.target.closest('a[href^="#video-t="]')) event.preventDefault()
}
const closeActiveOverlay = () => {
  if (showHistoryModal.value) closeHistory()
  else if (showAuthModal.value) closeAuthModal()
  else if (sidebar.value.visible) closeSidebar()
  else if (navOpen.value && typeof window !== 'undefined' && window.innerWidth < 900) closeNav()
}
const handleKeydown = event => {
  if (event.key === 'Escape') closeActiveOverlay()
}

/** 弹窗内循环 Tab，键盘用户不会一路跳到被遮住的背景里。 */
const trapFocusWithin = (panel, event) => {
  if (event.key !== 'Tab' || !panel) return
  const focusable = [...panel.querySelectorAll('button, input, [tabindex]:not([tabindex="-1"])')]
    .filter(element => !element.disabled && element.offsetParent !== null)
  if (!focusable.length) return
  const first = focusable[0]
  const last = focusable[focusable.length - 1]
  const active = document.activeElement
  if (event.shiftKey && (active === first || !panel.contains(active))) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && active === last) {
    event.preventDefault()
    first.focus()
  }
}
const trapAuthFocus = event => trapFocusWithin(authPanel.value, event)
const trapHistoryFocus = event => trapFocusWithin(historyPanel.value, event)

const switchAuthMode = ({ keepMessage = false } = {}) => {
  authMode.value = authMode.value === 'login' ? 'register' : 'login'
  if (!keepMessage) authMessage.value = ''
}
const handleAuth = async () => {
  if (!authForm.value.username || !authForm.value.password) {
    authMessage.value = '请输入完整的账号和密码'
    authError.value = true
    return
  }
  authLoading.value = true
  authMessage.value = ''
  const endpoint = authMode.value === 'login' ? '/user/login' : '/user/register'
  try {
    const res = await apiRequest(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(authForm.value)
    })
    if (!res.ok) {
      authMessage.value = (await res.text()) || `请求失败（HTTP ${res.status}）`
      authError.value = true
      return
    }
    const data = await res.json().catch(() => null)
    if (!data?.userInfo) {
      authMessage.value = '服务端返回异常，请稍后重试'
      authError.value = true
      return
    }
    if (authMode.value === 'login') {
      currentUser.value = data.userInfo
      localStorage.setItem('user', JSON.stringify(data.userInfo))
      setAuthToken(data.token)
      closeAuthModal()
      showMsg(`欢迎回来，${data.userInfo.nickname}`)
      fetchList({ notify: true })
    } else {
      authMessage.value = '注册成功，账号密码已保留，直接点“立即登录”即可'
      authError.value = false
      setTimeout(() => switchAuthMode({ keepMessage: true }), 900)
    }
  } catch (e) {
    console.error(e)
    authMessage.value = e?.message || '网络连接错误'
    authError.value = true
  } finally {
    authLoading.value = false
  }
}
/** 退出与登录失效走同一套清理，避免两处漏掉不同的字段。 */
const resetSessionState = () => {
  uploadAbort.value?.abort()
  uploadAbort.value = null
  taskStreams.stopAll()
  resetWorkspace()
  currentUser.value = null
  list.value = []
  searchQuery.value = ''
  videoUrl.value = ''
  file.value = null
  resumableFile.value = null
  resumableChunks.value = { done: 0, total: 0 }
  uploading.value = false
  localStorage.removeItem('user')
}

const logout = () => {
  if (hasAuthToken()) {
    apiRequest('/user/logout', { method: 'POST' }).catch(() => {})
  }
  resetSessionState()
  clearAuthToken()
  showMsg('已退出系统')
}

const handleAuthExpired = () => {
  resetSessionState()
  showMsg('登录状态已失效，请重新登录', true)
  openAuthModal()
}

const handleOnline = () => {
  isOffline.value = false
  showMsg('网络已恢复，正在同步最新状态')
  if (currentUser.value) fetchList()
}

const handleOffline = () => {
  isOffline.value = true
  showMsg('网络已断开：上传会自动重试，后台任务会在恢复后继续', true)
}

// 上传中误关标签页会白丢已传分片，这里让浏览器先问一句。
const handleBeforeUnload = event => {
  if (!uploading.value) return
  event.preventDefault()
  event.returnValue = ''
}

// 打开弹层时挂上标记类，锁背景滚动的规则只在窄屏生效（见样式里的说明）。
const overlayOpen = computed(() => sidebar.value.visible || showAuthModal.value || navOpen.value)
watch(overlayOpen, open => {
  document.body.classList.toggle('overlay-open', open)
})

watch(() => sidebar.value.visible, async visible => {
  if (visible) {
    focusBeforeSidebar = document.activeElement
    await nextTick()
    sidebarPanel.value?.focus()
    return
  }
  restoreFocus(focusBeforeSidebar)
  focusBeforeSidebar = null
})

// 长任务给一个时间锚点，用户才不会怀疑是不是卡死了。
watch(() => sidebar.value.loading, loading => {
  clearInterval(elapsedTimer)
  elapsedTimer = null
  elapsedSeconds.value = 0
  if (!loading) return
  const startedAt = Date.now()
  elapsedTimer = setInterval(() => {
    elapsedSeconds.value = Math.floor((Date.now() - startedAt) / 1000)
  }, 1000)
})

onMounted(() => {
  window.addEventListener('auth-expired', handleAuthExpired)
  window.addEventListener('keydown', handleKeydown)
  window.addEventListener('online', handleOnline)
  window.addEventListener('offline', handleOffline)
  window.addEventListener('beforeunload', handleBeforeUnload)
  if (DEMO_MODE) {
    currentUser.value = { id: 1, nickname: 'Agent Demo' }
    list.value = [DEMO_ITEM]
    openAgent(DEMO_ITEM)
    showDemoResult()
    return
  }
  const savedUser = localStorage.getItem('user')
  if (savedUser && hasAuthToken()) {
    try {
      currentUser.value = JSON.parse(savedUser)
    } catch(e) {}
  }
  fetchList({ notify: Boolean(currentUser.value) })
})
onUnmounted(() => {
  window.removeEventListener('auth-expired', handleAuthExpired)
  window.removeEventListener('keydown', handleKeydown)
  window.removeEventListener('online', handleOnline)
  window.removeEventListener('offline', handleOffline)
  window.removeEventListener('beforeunload', handleBeforeUnload)
  clearTimeout(messageTimer)
  clearInterval(elapsedTimer)
  uploadAbort.value?.abort()
  document.body.classList.remove('overlay-open')
  taskStreams.stopAll()
})
</script>

<style>
/*
  字体在 index.html 中以 <link media="print" onload> 非阻塞加载，避免阻塞首屏 CSSOM。
  显示字采思源宋体（Noto Serif SC）承担标题，正文用思源黑体（Noto Sans SC）。
*/

:root {
  /* 暖色暗调编辑风：深咖底色 + 赤陶强调 + 骨白文字 */
  --bg:     #17120F;   /* 页面底色，深咖 */
  --bg-2:   #201A15;   /* 面板 / 卡片 */
  --bg-3:   #2A231C;   /* 抬升 / 悬停 */
  --line:   #342B23;   /* 分隔线 */
  --line-2: #463A2F;   /* 分隔线（强） */
  --ink:    #F2EBE1;   /* 主文字，暖骨白 */
  --ink-2:  #B3A697;   /* 次要文字 */
  --ink-3:  #7C7062;   /* 弱化文字 / 未激活导航 */
  --clay:   #9C5433;   /* 赤陶主强调 */
  --clay-2: #B26741;   /* 赤陶悬停 */
  --clay-3: #7A3F26;   /* 赤陶深色 */
  --bone:   #EFE7DA;   /* 顶部窄条 */
  --ok:     #8FA97B;
  --warn:   #D2A24E;
  --bad:    #C2604A;

  --r-sm: 8px;
  --r-md: 14px;
  --r-lg: 20px;
  --nav-w: 264px;
  --top-h: 46px;

  --font-display: 'Noto Serif SC', 'Songti SC', 'STSong', 'SimSun', serif;
  --font-body: 'Noto Sans SC', system-ui, -apple-system, 'Segoe UI', 'Microsoft YaHei', sans-serif;
}

* { box-sizing: border-box; margin: 0; padding: 0; }

html, body, #app {
  width: 100%;
  min-height: 100vh;
  background-color: var(--bg);
}

body {
  background-color: var(--bg);
  color: var(--ink);
  font-family: var(--font-body);
  font-size: 16px;
  line-height: 1.7;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  overflow-x: hidden;
}

.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0; }

button { font-family: inherit; }
:focus-visible { outline: 2px solid var(--clay-2); outline-offset: 2px; }

.app-shell { position: relative; min-height: 100vh; }

/* ---------- 顶部窄条 ---------- */
.topstrip {
  position: fixed; top: 0; left: 0; right: 0; height: var(--top-h); z-index: 60;
  display: flex; align-items: center; gap: 14px; padding: 0 18px;
  background: var(--bone); color: #2A2118;
}
.nav-toggle {
  display: inline-flex; align-items: center; justify-content: center;
  width: 30px; height: 30px; border: 0; background: transparent; color: #2A2118;
  cursor: pointer; border-radius: 6px;
}
.nav-toggle:hover { background: rgba(42, 33, 24, 0.1); }
.topstrip-label { font-size: 0.76rem; letter-spacing: 0.14em; color: #4A3E31; }
.topstrip-right { margin-left: auto; display: inline-flex; align-items: center; gap: 8px; font-size: 0.74rem; letter-spacing: 0.06em; color: #5B4E40; }
.status-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--ok); flex: none; }
.status-dot.is-live { background: var(--warn); animation: blink 1.3s ease-in-out infinite; }
.status-dot.is-off { background: var(--bad); }

.nav-scrim { position: fixed; top: var(--top-h); left: 0; right: 0; bottom: 0; background: rgba(10, 7, 5, 0.62); z-index: 48; }

/* ---------- 滑动侧边栏 ---------- */
.side-nav {
  position: fixed; top: var(--top-h); bottom: 0; left: 0; width: var(--nav-w); z-index: 49;
  display: flex; flex-direction: column; padding: 30px 22px 22px;
  background: var(--bg-2); border-right: 1px solid var(--line);
  overflow-y: auto;
  transform: translateX(-100%);
  transition: transform 0.3s cubic-bezier(0.2, 0.7, 0.2, 1);
}
.app-shell.nav-open .side-nav { transform: none; }

.nav-brand { display: flex; flex-direction: column; gap: 5px; padding-bottom: 30px; }
.nav-brand-mark { font-family: var(--font-display); font-weight: 700; font-size: 1.1rem; letter-spacing: 0.04em; color: var(--ink); }
.nav-brand-sub { font-size: 0.68rem; letter-spacing: 0.16em; color: var(--ink-3); }

.nav-menu { display: flex; flex-direction: column; gap: 4px; margin-top: 14px; }
.nav-item {
  position: relative; text-align: left; background: none; border: 0; cursor: pointer;
  font-family: var(--font-display); font-size: 1.18rem; font-weight: 600; letter-spacing: 0.05em;
  color: var(--ink-3); padding: 11px 0 11px 34px;
  transition: color 0.2s ease;
}
.nav-item::before {
  content: ""; position: absolute; left: 0; top: 50%; width: 22px; height: 1.5px;
  background: var(--ink); transform: translateY(-50%) scaleX(0); transform-origin: left;
  transition: transform 0.25s cubic-bezier(0.2, 0.7, 0.2, 1);
}
.nav-item:hover { color: var(--ink-2); }
.nav-item.is-active { color: var(--ink); }
.nav-item.is-active::before { transform: translateY(-50%) scaleX(1); }

.nav-foot { margin-top: auto; display: flex; flex-direction: column; gap: 12px; padding-top: 26px; }

.signal { display: inline-flex; align-items: center; gap: 9px; padding: 8px 12px; border: 1px solid var(--line); border-radius: var(--r-sm); font-size: 0.76rem; color: var(--ink-2); }
.signal-led { width: 7px; height: 7px; border-radius: 50%; background: var(--ok); flex: none; }
.signal.is-live .signal-led { background: var(--warn); animation: blink 1.3s ease-in-out infinite; }
.signal.is-off { color: var(--bad); border-color: rgba(194, 96, 74, 0.4); }
.signal.is-off .signal-led { background: var(--bad); }

.auth-btn {
  display: inline-flex; align-items: center; justify-content: center; gap: 8px;
  padding: 11px 15px; border: 0; border-radius: var(--r-sm);
  background: var(--clay); color: var(--bone);
  font-size: 0.86rem; font-weight: 700; cursor: pointer;
  transition: background 0.2s ease;
}
.auth-btn:hover { background: var(--clay-2); }
.btn-icon { display: inline-flex; }
.user-profile { display: flex; align-items: center; gap: 10px; padding: 7px 8px 7px 13px; border: 1px solid var(--line); border-radius: var(--r-sm); }
.user-name { flex: 1; font-size: 0.84rem; color: var(--ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.logout-btn { display: inline-flex; align-items: center; padding: 5px; border: 1px solid var(--line); border-radius: 6px; background: transparent; color: var(--ink-3); cursor: pointer; transition: all 0.15s ease; }
.logout-btn:hover { color: var(--bone); background: var(--bad); border-color: var(--bad); }

/* ---------- 主内容 ---------- */
.view-port { padding-top: var(--top-h); transition: margin-left 0.3s cubic-bezier(0.2, 0.7, 0.2, 1); }
.workstation { max-width: 1160px; margin: 0 auto; padding: 58px 40px 96px; }

.eyebrow { font-size: 0.72rem; letter-spacing: 0.28em; color: var(--ink-3); }
.display {
  font-family: var(--font-display); font-weight: 900; color: var(--ink);
  font-size: clamp(2.4rem, 5.2vw, 4rem); line-height: 1.06; letter-spacing: 0.01em;
  margin: 14px 0 18px;
}
.lede { max-width: 48ch; font-size: 1rem; line-height: 1.85; color: var(--ink-2); }
.page-head { display: flex; align-items: baseline; gap: 16px; flex-wrap: wrap; }
.count-chip { padding: 4px 12px; border: 1px solid var(--line); border-radius: 999px; font-size: 0.74rem; color: var(--ink-3); }

/* ---------- 上传页面 ---------- */
/* 左边「视频文件」占满两行作为主入口，右边「音频文件」「视频链接」上下各一格。 */
.deck-frame {
  margin-top: 42px;
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr);
  grid-auto-rows: minmax(158px, auto);
  gap: 20px;
}
.upload-block {
  position: relative; overflow: hidden;
  display: flex; flex-direction: column; justify-content: flex-end; gap: 10px;
  padding: 28px; border-radius: var(--r-lg);
  background: var(--clay); color: var(--bone);
  cursor: pointer; text-decoration: none;
  transition: background 0.2s ease, transform 0.2s ease, border-color 0.2s ease;
}
.upload-block::after {
  content: ""; position: absolute; right: -46px; bottom: -70px; width: 230px; height: 230px;
  border: 1px solid rgba(239, 231, 218, 0.32); border-radius: 50%; pointer-events: none;
}
.upload-block:hover { background: var(--clay-2); }
.upload-block:active { transform: scale(0.995); }
.deck-frame.is-dragover .upload-block:not(.upload-block-url) { background: var(--clay-2); }

.upload-block-file { grid-row: span 2; }
.upload-block-audio { background: var(--clay-3); }

.block-icon { display: inline-flex; color: var(--bone); }
.block-title { font-family: var(--font-display); font-weight: 700; font-size: 1.5rem; line-height: 1.2; }
.block-hint { font-size: 0.84rem; color: rgba(239, 231, 218, 0.78); }

/* 「视频链接」是表单而非拖拽区，用安静些的暗底，把赤陶留给两个文件入口。 */
.upload-block-url {
  cursor: default; min-height: 182px;
  background: var(--bg-2); border: 1px solid var(--line); color: var(--ink);
}
.upload-block-url:hover { background: var(--bg-2); border-color: var(--line-2); }
.upload-block-url::after { border-color: rgba(178, 103, 65, 0.26); }
.upload-block-url .block-icon { color: var(--clay-2); }
.upload-block-url .block-hint { color: var(--ink-3); }
.url-field {
  margin-top: auto; display: flex; align-items: stretch;
  background: rgba(18, 12, 8, 0.5); border-radius: var(--r-sm); overflow: hidden;
}
.url-field input {
  flex: 1; min-width: 0; padding: 12px 14px; border: 0; outline: none;
  background: transparent; color: var(--bone); font-family: var(--font-body); font-size: 0.88rem;
}
.url-field input::placeholder { color: rgba(239, 231, 218, 0.55); }
.url-field input:disabled { opacity: 0.5; }
.url-go { display: inline-flex; align-items: center; padding: 0 15px; border: 0; background: rgba(18, 12, 8, 0.55); color: var(--bone); cursor: pointer; transition: background 0.2s ease; }
.url-go:hover { background: rgba(18, 12, 8, 0.8); }
.url-go:disabled { opacity: 0.5; cursor: not-allowed; }

.deck-run { grid-column: 1 / -1; display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 54px 24px; text-align: center; background: var(--bg-2); border: 1px solid var(--line); border-radius: var(--r-lg); }
.run-label { font-weight: 700; color: var(--ink); }
.run-file { max-width: 100%; color: var(--ink-3); font-size: 0.8rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.run-bar { width: 100%; max-width: 300px; height: 6px; margin-top: 8px; background: var(--bg); border-radius: 999px; overflow: hidden; }
.run-bar span { display: block; height: 100%; background: var(--clay-2); border-radius: 999px; transition: width 0.25s ease; }
.run-stat { color: var(--ink-3); font-size: 0.78rem; }
.run-warn { color: var(--warn); font-size: 0.78rem; }
.run-cancel { margin-top: 10px; padding: 9px 18px; border: 1px solid rgba(194, 96, 74, 0.5); border-radius: var(--r-sm); background: transparent; color: var(--bad); font-size: 0.82rem; cursor: pointer; transition: background 0.15s ease, color 0.15s ease; }
.run-cancel:hover { background: var(--bad); color: var(--bone); }

.resume {
  margin-top: 20px; display: flex; flex-wrap: wrap; gap: 12px; align-items: center; justify-content: space-between;
  padding: 16px 18px; background: var(--bg-2); border: 1px solid var(--line); border-left: 3px solid var(--warn);
  border-radius: var(--r-sm); font-size: 0.84rem; color: var(--ink-2);
}
.resume-text { flex: 1 1 auto; }
.resume-actions { display: flex; gap: 8px; }
.resume-actions button { padding: 8px 14px; border: 1px solid var(--line-2); border-radius: var(--r-sm); background: transparent; color: var(--ink); font-size: 0.8rem; cursor: pointer; transition: all 0.15s ease; }
.resume-actions button:hover { border-color: var(--clay-2); color: var(--clay-2); }

/* ---------- 资料库 ---------- */
.search {
  display: flex; align-items: center; gap: 10px; width: min(340px, 100%);
  margin: 32px 0 24px; padding: 12px 16px;
  background: var(--bg-2); border: 1px solid var(--line); border-radius: 999px;
  color: var(--ink-3); transition: border-color 0.2s ease;
}
.search:focus-within { border-color: var(--line-2); color: var(--clay-2); }
.search input { width: 100%; border: 0; outline: none; background: transparent; color: var(--ink); font-family: inherit; font-size: 0.86rem; }
.search input::placeholder { color: var(--ink-3); }

.rack-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(272px, 1fr)); gap: 18px; }

.tape {
  position: relative; display: flex; flex-direction: column;
  background: var(--bg-2); border: 1px solid var(--line); border-radius: var(--r-md);
  overflow: hidden; transition: border-color 0.2s ease, background 0.2s ease, transform 0.2s ease;
}
.tape:hover { background: var(--bg-3); border-color: var(--line-2); transform: translateY(-2px); }

.tape-del {
  position: absolute; top: 12px; right: 12px; z-index: 2;
  display: inline-flex; align-items: center; justify-content: center; width: 30px; height: 30px;
  border: 1px solid transparent; border-radius: 8px; background: transparent; color: var(--ink-3);
  cursor: pointer; opacity: 0; transition: opacity 0.15s ease, color 0.15s ease, background 0.15s ease;
}
.tape:hover .tape-del, .tape-del:focus-visible { opacity: 1; }
.tape-del:hover:not(:disabled) { color: var(--bone); background: var(--bad); }
.tape-del:disabled { opacity: 0.5; cursor: progress; }

.tape-label { display: flex; align-items: flex-start; gap: 12px; padding: 18px 18px 12px; }
.tape-icon { flex: none; margin-top: 2px; color: var(--clay-2); }
.tape-name {
  font-size: 0.95rem; font-weight: 700; color: var(--ink); line-height: 1.55; padding-right: 26px;
  word-break: break-all; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
}

.tape-meta { display: flex; align-items: center; gap: 10px; padding: 0 18px 16px; font-size: 0.74rem; }
.tape-time { color: var(--ink-3); }
.tape-status { margin-left: auto; padding: 3px 11px; border-radius: 999px; font-size: 0.68rem; letter-spacing: 0.03em; }
.tape-status.completed { color: #A9C08F; background: rgba(143, 169, 123, 0.14); }
.tape-status.processing { color: var(--warn); background: rgba(210, 162, 78, 0.14); animation: blink 1.5s ease-in-out infinite; }
.tape-status.failed { color: var(--bad); background: rgba(194, 96, 74, 0.14); }
.tape-status.unknown { color: var(--ink-3); border: 1px solid var(--line); }

.tape-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; padding: 14px; margin-top: auto; border-top: 1px solid var(--line); }
.dock-item {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  padding: 11px 8px; border: 1px solid var(--line); border-radius: var(--r-sm);
  background: transparent; color: var(--ink-2); font-size: 0.78rem; cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}
.dock-item:hover:not(:disabled) { background: var(--clay); border-color: var(--clay); color: var(--bone); }
.dock-item:disabled { opacity: 0.32; cursor: not-allowed; }
.dock-item .item-icon { display: inline-flex; }
.dock-item .label-group { display: flex; flex-direction: column; }
.dock-item.ai-core { color: var(--clay-2); border-color: rgba(178, 103, 65, 0.45); }
.dock-item.ai-core:hover:not(:disabled) { background: var(--clay); border-color: var(--clay); color: var(--bone); }

.rack-empty { padding: 64px 24px; text-align: center; border: 1px dashed var(--line-2); border-radius: var(--r-md); color: var(--ink-3); }
.rack-empty-title { color: var(--ink); font-size: 0.94rem; }
.rack-empty-hint { margin-top: 10px; font-size: 0.84rem; }
.rack-empty button { margin-top: 16px; padding: 9px 18px; border: 1px solid var(--line-2); border-radius: var(--r-sm); background: transparent; color: var(--ink); font-size: 0.8rem; cursor: pointer; transition: all 0.15s ease; }
.rack-empty button:hover { border-color: var(--clay-2); color: var(--clay-2); }

/* ---------- 提示条 ---------- */
.toast {
  position: fixed; left: 26px; bottom: 26px; z-index: 80;
  max-width: min(520px, calc(100vw - 52px)); padding: 14px 18px;
  background: var(--bg-3); border: 1px solid var(--line-2); border-left: 3px solid var(--clay-2);
  border-radius: var(--r-sm); color: var(--ink); font-size: 0.84rem;
}
.toast.is-error { border-left-color: var(--bad); color: #E7A99C; cursor: pointer; }
.toast-pop-enter-active, .toast-pop-leave-active { transition: opacity 0.2s ease, transform 0.2s ease; }
.toast-pop-enter-from, .toast-pop-leave-to { opacity: 0; transform: translateY(8px); }

/* 加载指示：细腻的环形旋转，替代此前的像素方块 */
.quantum-loader { width: 34px; height: 34px; border-radius: 50%; border: 2px solid var(--line-2); border-top-color: var(--clay-2); animation: spin 0.85s linear infinite; }
.quantum-loader.small { width: 22px; height: 22px; margin: 0 auto; }

/* ---------- 智能分析：居中窗口，左原视频 / 右生成结果 ---------- */
.sidebar-backdrop { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(10, 7, 5, 0.72); z-index: 100; }
.agent-window {
  position: fixed; top: 50%; left: 50%; z-index: 101;
  width: min(1160px, calc(100vw - 40px)); height: min(88vh, 880px);
  display: flex; flex-direction: column;
  background: var(--bg-2); border: 1px solid var(--line); border-radius: var(--r-lg);
  overflow: hidden;
  opacity: 0; visibility: hidden; pointer-events: none;
  transform: translate(-50%, -47%) scale(0.98);
  transition: opacity 0.18s ease, transform 0.18s ease, visibility 0.18s;
}
.agent-window.is-open { opacity: 1; visibility: visible; pointer-events: auto; transform: translate(-50%, -50%) scale(1); }
.agent-window-head {
  display: flex; justify-content: space-between; align-items: center; gap: 12px;
  padding: 16px 20px; background: var(--bg-2); border-bottom: 1px solid var(--line);
}
.sidebar-title { display: flex; align-items: center; gap: 10px; font-size: 0.9rem; font-weight: 700; color: var(--ink); }
.icon { display: inline-flex; color: var(--clay-2); }
.close-btn {
  display: inline-flex; align-items: center; justify-content: center; width: 34px; height: 34px;
  border: 1px solid var(--line); border-radius: 8px; background: transparent; color: var(--ink-3);
  font-size: 1.2rem; line-height: 1; cursor: pointer; transition: all 0.15s ease;
}
.close-btn:hover { color: var(--bone); background: var(--bad); border-color: var(--bad); }

/* 左右分栏：原视频与生成结果各自独立滚动，读结果时画面不会被顶走 */
.agent-window-body { flex: 1; min-height: 0; display: grid; grid-template-columns: 1fr; }
.agent-window-body.is-split { grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr); }
.agent-video { display: flex; flex-direction: column; min-width: 0; overflow-y: auto; background: var(--bg); border-right: 1px solid var(--line); }
.agent-video-frame { background: #000; }
.agent-video video { display: block; width: 100%; aspect-ratio: 16 / 9; object-fit: contain; background: #000; }
.agent-video-hint { padding: 12px 20px; color: var(--ink-3); font-size: 0.78rem; }
.agent-main { min-width: 0; overflow-y: auto; padding: 24px; }

.loading-state { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 16px; height: 100%; color: var(--ink-2); }
.markdown-content, .text-content { font-size: 0.92rem; line-height: 1.95; color: var(--ink); }
.text-content pre { white-space: pre-wrap; font-family: var(--font-body); background: var(--bg); border: 1px solid var(--line); border-radius: var(--r-sm); padding: 16px; color: var(--ink); }
.text-meta { margin-bottom: 10px; color: var(--ink-3); font-size: 0.78rem; }
.markdown-content h1, .markdown-content h2, .markdown-content h3 { font-family: var(--font-display); margin-top: 1.6em; margin-bottom: 0.5em; color: var(--ink); }
.markdown-content h1 { padding-bottom: 10px; border-bottom: 1px solid var(--line); }
.markdown-content h2 { font-size: 1.2rem; }
.markdown-content ul { padding-left: 20px; }
.markdown-content li { margin-bottom: 8px; }
.markdown-content strong { color: var(--ink); }
.markdown-content p { margin-bottom: 1em; }
.markdown-content a { color: var(--clay-2); }
/* 结果里的时间戳就是证据锚点，做成赤陶色时间码 */
.markdown-content a[href^="#video-t="] { display: inline-block; padding: 1px 8px; border-radius: 6px; background: var(--clay); color: var(--bone); text-decoration: none; font-size: 0.82em; }
.markdown-content a[href^="#video-t="]:hover { background: var(--clay-2); }

.result-actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 8px; margin-bottom: 20px; }
.result-actions button { padding: 9px 14px; border: 1px solid var(--line); border-radius: var(--r-sm); background: transparent; color: var(--ink-2); font-size: 0.8rem; cursor: pointer; transition: all 0.15s ease; }
.result-actions button:hover:not(:disabled) { border-color: var(--clay-2); color: var(--clay-2); }
.result-actions button:disabled { opacity: 0.4; cursor: not-allowed; }

.video-evidence-loading { min-height: 200px; display: grid; place-items: center; padding: 24px; color: var(--ink-3); font-size: 0.82rem; }
.video-evidence-error { min-height: 200px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; padding: 24px; color: var(--bad); text-align: center; }
.video-evidence-error button { padding: 7px 14px; border: 1px solid rgba(194, 96, 74, 0.5); border-radius: var(--r-sm); background: transparent; color: var(--bad); cursor: pointer; transition: all 0.15s ease; }
.video-evidence-error button:hover { background: var(--bad); color: var(--bone); }

.agent-composer { display: flex; flex-direction: column; gap: 18px; }
.agent-caption { color: var(--ink-2); line-height: 1.8; }
.inline-error { padding: 12px 14px; border: 1px solid rgba(194, 96, 74, 0.45); border-radius: var(--r-sm); color: #E7A99C; line-height: 1.6; }

.agent-composer textarea, .follow-up-box textarea {
  width: 100%; min-height: 128px; padding: 15px; border: 1px solid var(--line); border-radius: var(--r-sm);
  background: var(--bg); color: var(--ink); outline: none; resize: vertical;
  font-family: var(--font-body); font-size: 0.9rem; line-height: 1.7; transition: border-color 0.2s ease;
}
.agent-composer textarea::placeholder, .follow-up-box textarea::placeholder { color: var(--ink-3); }
.agent-composer textarea:focus, .follow-up-box textarea:focus { border-color: var(--clay-2); }
.field-counter { color: var(--ink-3); font-size: 0.76rem; text-align: right; }

.goal-presets { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.goal-presets button, .feedback-row button {
  padding: 9px 11px; border: 1px solid var(--line); border-radius: var(--r-sm);
  background: transparent; color: var(--ink-2); cursor: pointer; transition: border-color 0.15s ease, color 0.15s ease, background 0.15s ease;
}
.goal-presets button { min-height: 84px; padding: 13px; text-align: left; }
.goal-presets strong, .goal-presets span { display: block; }
.goal-presets strong { margin-bottom: 6px; color: var(--ink); font-size: 0.86rem; }
.goal-presets span { font-size: 0.76rem; line-height: 1.5; color: var(--ink-3); }
.goal-presets button:hover, .goal-presets button.active { border-color: var(--clay-2); background: rgba(156, 84, 51, 0.1); }
.goal-presets button:hover strong, .goal-presets button.active strong { color: var(--clay-2); }
.feedback-row button:hover, .feedback-row button.active { border-color: var(--clay-2); color: var(--clay-2); }

.agent-run-btn {
  padding: 15px 18px; border: 0; border-radius: var(--r-sm);
  background: var(--clay); color: var(--bone); font-size: 0.9rem; font-weight: 700; cursor: pointer;
  transition: background 0.2s ease;
}
.agent-run-btn:hover:not(:disabled) { background: var(--clay-2); }
.agent-run-btn:disabled, .follow-up-box button:disabled { opacity: 0.4; cursor: not-allowed; }

.evidence-search { margin: 18px 0 24px; }
.evidence-search-form { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; }
.evidence-search-form input { min-width: 0; padding: 12px 14px; border: 1px solid var(--line); border-radius: var(--r-sm); background: var(--bg); color: var(--ink); outline: none; font-family: var(--font-body); font-size: 0.86rem; transition: border-color 0.2s ease; }
.evidence-search-form input::placeholder { color: var(--ink-3); }
.evidence-search-form input:focus { border-color: var(--clay-2); }
.evidence-search-form button, .evidence-search-results button {
  padding: 10px 14px; border: 1px solid var(--line); border-radius: var(--r-sm);
  background: transparent; color: var(--ink-2); font-size: 0.8rem; cursor: pointer; transition: all 0.15s ease;
}
.evidence-search-form button:hover, .evidence-search-results button:hover { border-color: var(--clay-2); color: var(--clay-2); }
.evidence-search-form button:disabled { opacity: 0.4; cursor: not-allowed; }
.evidence-search-error { margin-top: 8px; color: var(--bad); font-size: 0.82rem; }
.evidence-search-results { display: grid; gap: 6px; margin-top: 8px; }
.evidence-search-results button { display: grid; grid-template-columns: 54px 66px minmax(0, 1fr); gap: 10px; text-align: left; }
.evidence-search-results strong { color: var(--clay-2); }
.evidence-search-results small { color: var(--ink-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.evidence-search-results span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.agent-running { display: flex; flex-direction: column; gap: 20px; }
.agent-running .loading-state { min-height: 200px; height: auto; }
.loading-state p { max-width: 34rem; text-align: center; }
.stream-offline { color: var(--warn); font-size: 0.82rem; }
.loading-hint { color: var(--ink-3); font-size: 0.8rem; }

.agent-inspector { margin-top: 26px; padding-top: 16px; border-top: 1px solid var(--line); }
.agent-inspector summary { padding: 8px 0; color: var(--ink-2); font-weight: 700; cursor: pointer; }
.agent-inspector summary:hover { color: var(--clay-2); }
.agent-inspector-content { padding-top: 14px; }
.agent-meta-block { margin-bottom: 18px; padding: 15px; background: var(--bg); border: 1px solid var(--line); border-left: 3px solid var(--clay-2); border-radius: var(--r-sm); }
.meta-label { display: block; margin-bottom: 10px; color: var(--clay-2); font-size: 0.78rem; font-weight: 700; }
.agent-meta-block ol { padding-left: 20px; color: var(--ink); }
.agent-meta-block li { margin: 7px 0; }
.plan-editor { display: grid; gap: 8px; margin-top: 12px; }
.plan-editor-row { display: grid; grid-template-columns: minmax(0, 1fr) 34px; gap: 8px; }
.plan-editor input { min-width: 0; padding: 10px; border: 1px solid var(--line); border-radius: 6px; background: var(--bg-2); color: var(--ink); font-family: var(--font-body); font-size: 0.84rem; }
.plan-editor button, .plan-edit-trigger { padding: 8px 14px; border: 1px solid var(--line); border-radius: var(--r-sm); background: transparent; color: var(--ink-2); font-size: 0.78rem; cursor: pointer; transition: all 0.15s ease; }
.plan-editor button:hover, .plan-edit-trigger:hover { border-color: var(--clay-2); color: var(--clay-2); }
.plan-editor-actions { display: flex; justify-content: flex-end; gap: 8px; }
.plan-edit-trigger { margin-top: 8px; }
.stage-list { display: flex; flex-wrap: wrap; gap: 8px; }
.stage-list span, .quality-row span { padding: 7px 12px; border: 1px solid var(--line); border-radius: 999px; color: var(--ink-2); font-size: 0.74rem; }
.quality-row { display: flex; flex-wrap: wrap; gap: 8px; }
.follow-up-box { display: grid; grid-template-columns: 1fr auto; gap: 10px; margin-top: 24px; }
.follow-up-box textarea { min-height: 76px; }
.follow-up-box button { align-self: stretch; min-width: 78px; border: 1px solid rgba(178, 103, 65, 0.5); border-radius: var(--r-sm); background: transparent; color: var(--clay-2); font-weight: 700; cursor: pointer; transition: all 0.15s ease; }
.follow-up-box button:hover:not(:disabled) { background: var(--clay); color: var(--bone); }
.feedback-row { display: flex; align-items: center; gap: 8px; margin-top: 18px; color: var(--ink-3); font-size: 0.84rem; }

/* ---------- 登录 / 注册、历史记录弹窗 ---------- */
.auth-backdrop { position: fixed; top: 0; left: 0; right: 0; bottom: 0; z-index: 120; display: flex; justify-content: center; align-items: center; padding: 24px; background: rgba(10, 7, 5, 0.72); }
.auth-panel { width: 420px; max-width: 92vw; margin: auto; display: flex; flex-direction: column; overflow: hidden; background: var(--bg-2); border: 1px solid var(--line); border-radius: var(--r-lg); }
.auth-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 16px 20px; border-bottom: 1px solid var(--line); }
.auth-title { font-family: var(--font-display); font-size: 1.05rem; font-weight: 700; color: var(--ink); }
.auth-body { padding: 24px; }
.input-group { margin-bottom: 18px; }
.input-group label { display: block; margin-bottom: 8px; color: var(--ink-3); font-size: 0.78rem; }
.input-group input { width: 100%; padding: 13px 14px; border: 1px solid var(--line); border-radius: var(--r-sm); background: var(--bg); color: var(--ink); font-family: var(--font-body); font-size: 0.92rem; outline: none; transition: border-color 0.2s ease; }
.input-group input::placeholder { color: var(--ink-3); }
.input-group input:focus { border-color: var(--clay-2); }
.cyber-btn { width: 100%; margin-bottom: 18px; padding: 14px; border: 0; border-radius: var(--r-sm); background: var(--clay); color: var(--bone); font-size: 0.9rem; font-weight: 700; cursor: pointer; transition: background 0.2s ease; }
.cyber-btn:hover:not(:disabled) { background: var(--clay-2); }
.cyber-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.auth-toggle { text-align: center; font-size: 0.84rem; color: var(--ink-3); }
.toggle-text { }
.toggle-link { margin-left: 5px; background: none; border: 0; color: var(--clay-2); font-weight: 700; cursor: pointer; text-decoration: underline; }
.toggle-link:hover { color: var(--ink); }
.auth-msg { margin-top: 15px; text-align: center; font-size: 0.8rem; color: var(--ink-3); }
.auth-msg.error { color: var(--bad); }

.history-panel { width: 780px; max-width: 92vw; max-height: 86vh; margin: auto; display: flex; flex-direction: column; overflow: hidden; background: var(--bg-2); border: 1px solid var(--line); border-radius: var(--r-lg); }
.history-body { display: flex; flex-direction: column; min-height: 260px; max-height: 72vh; overflow: hidden; }
.history-hint { padding: 24px; color: var(--ink-3); font-size: 0.86rem; }
.history-hint.is-error { color: var(--bad); }
.history-content { display: grid; grid-template-columns: 236px minmax(0, 1fr); flex: 1; min-height: 0; }
.history-list { list-style: none; margin: 0; padding: 12px; overflow-y: auto; background: var(--bg); border-right: 1px solid var(--line); display: flex; flex-direction: column; gap: 6px; }
.history-entry { display: flex; flex-direction: column; gap: 6px; width: 100%; padding: 12px; text-align: left; border: 1px solid var(--line); border-radius: var(--r-sm); background: transparent; color: var(--ink-2); cursor: pointer; transition: all 0.15s ease; }
.history-entry:hover { border-color: var(--line-2); color: var(--ink); }
.history-entry.active { border-color: var(--clay-2); color: var(--ink); background: rgba(156, 84, 51, 0.1); }
.history-goal { font-size: 0.84rem; line-height: 1.5; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.history-meta { font-size: 0.72rem; color: var(--ink-3); }
.history-detail { display: flex; flex-direction: column; min-width: 0; min-height: 0; }
.history-detail-head { display: flex; flex-direction: column; gap: 4px; padding: 16px 20px; border-bottom: 1px solid var(--line); }
.history-detail-head strong { color: var(--ink); font-size: 0.95rem; }
.history-detail-goal { color: var(--ink-3); font-size: 0.78rem; word-break: break-word; }
.history-detail-scroll { flex: 1; overflow-y: auto; padding: 20px; }

.auth-panel, .history-panel { animation: fadeUp 0.22s ease forwards; }

/* ---------- 响应式 ---------- */
@media (min-width: 900px) {
  .nav-scrim { display: none; }
  .app-shell.nav-open .view-port { margin-left: var(--nav-w); }
}

@media (max-width: 899px) {
  .workstation { padding: 34px 20px 72px; }
  .deck-frame { grid-template-columns: 1fr; grid-auto-rows: auto; }
  .upload-block { min-height: 180px; }
  .upload-block-file { grid-row: auto; }
  /* 窄屏把左右分栏改成上下：视频在上，结果在下 */
  .agent-window-body.is-split { grid-template-columns: 1fr; grid-template-rows: auto minmax(0, 1fr); }
  .agent-video { border-right: 0; border-bottom: 1px solid var(--line); }
  body.overlay-open { overflow: hidden; }
}

@media (max-width: 560px) {
  .topstrip { padding: 0 12px; }
  .topstrip-label { font-size: 0.7rem; letter-spacing: 0.08em; }
  .topstrip-status { display: none; }
  .workstation { padding: 28px 16px 64px; }
  .display { font-size: clamp(2rem, 9vw, 2.6rem); }
  .rack-grid { grid-template-columns: 1fr; }
  .search { width: 100%; }
  .agent-window { width: 100vw; height: 100vh; border: 0; border-radius: 0; }
  .agent-window-head { padding: 14px 16px; }
  .sidebar-title { font-size: 0.8rem; max-width: calc(100vw - 68px); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .agent-main { padding: 16px; }
  .agent-video-hint { padding: 10px 16px; }
  .goal-presets { grid-template-columns: 1fr; }
  .goal-presets button { min-height: 68px; }
  .evidence-search-form { grid-template-columns: 1fr; }
  .evidence-search-results button { grid-template-columns: 50px minmax(0, 1fr); }
  .evidence-search-results small { display: none; }
  .follow-up-box { grid-template-columns: 1fr; }
  .follow-up-box button { min-height: 44px; }
  .result-actions { justify-content: stretch; }
  .result-actions button { flex: 1; }
  .resume { flex-direction: column; align-items: stretch; }
  .resume-actions button { flex: 1; }
  .tape-del { opacity: 1; }
  .history-content { grid-template-columns: 1fr; }
  .history-list { border-right: 0; border-bottom: 1px solid var(--line); max-height: 180px; flex-direction: row; overflow-x: auto; }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation-duration: 0.01ms !important; animation-iteration-count: 1 !important; scroll-behavior: auto !important; transition-duration: 0.01ms !important; }
}

@keyframes spin { to { transform: rotate(360deg); } }
@keyframes blink { 50% { opacity: 0.35; } }
@keyframes fadeUp { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: none; } }
</style>
