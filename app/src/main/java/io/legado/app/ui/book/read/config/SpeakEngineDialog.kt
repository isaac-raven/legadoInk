package io.legado.app.ui.book.read.config

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.HttpTTS
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.DialogTtsEngineConfigBinding
import io.legado.app.databinding.ItemHttpTtsBinding
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.config.AppConfig
import io.legado.app.help.edgeTts.EdgeTts
import io.legado.app.help.openAiTts.OpenAiCompatTts
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.ui.association.ImportHttpTtsDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.utils.ACache
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.StringUtils
import io.legado.app.utils.applyTint
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.gone
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.File

/**
 * tts引擎管理
 */
class SpeakEngineDialog() : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val viewModel: SpeakEngineViewModel by viewModels()
    private val ttsUrlKey = "ttsUrlKey"
    private val adapter by lazy { Adapter(requireContext()) }
    private var ttsEngine: String? = ReadAloud.ttsEngine
    private val sysTtsViews = arrayListOf<RadioButton>()
    private val callBack: CallBack? get() = parentFragment as? CallBack
    private var currentSelect = -1
    private val importDocResult = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            showDialogFragment(ImportHttpTtsDialog(uri.toString()))
        }
    }
    private val exportResult = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            alert(R.string.export_success) {
                val url = uri.toString()
                if (url.isAbsUrl()) {
                    setMessage(DirectLinkUpload.getSummary())
                    val time = System.currentTimeMillis()
                    shibboleth {
                        val shibbolethUrl = StringUtils.toShibboleth(url, StringUtils.TTS_RULE, time)
                        alert(R.string.shibboleth) {
                            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                                editView.setText(shibbolethUrl)
                            }
                            customView { alertBinding.root }
                            okButton {
                                requireContext().sendToClip(shibbolethUrl)
                            }
                        }
                    }
                }
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.hint = getString(R.string.path)
                    editView.setText(url)
                }
                customView { alertBinding.root }
                okButton {
                    requireContext().sendToClip(url)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initView()
        initMenu()
        initData()
    }

    private fun initView() = binding.run {
        toolBar.setBackgroundColor(primaryColor)
        toolBar.setTitle(R.string.speak_engine)
        recyclerView.setEdgeEffectColor(primaryColor)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        adapter.addHeaderView {
            ItemHttpTtsBinding.inflate(layoutInflater, recyclerView, false).apply {
                sysTtsViews.add(cbName)
                ivEdit.gone()
                ivMenuDelete.gone()
                labelSys.visible()
                cbName.text = "系统默认"
                cbName.tag = ""
                cbName.isChecked = ttsEngine == null || ttsEngine!!.isJsonObject()
                        && GSON.fromJsonObject<SelectItem<String>>(ttsEngine)
                    .getOrNull()?.value.isNullOrEmpty()
                cbName.setOnClickListener {
                    upTts(GSON.toJson(SelectItem("系统默认", "")))
                }
            }
        }
        viewModel.sysEngines.forEach { engine ->
            adapter.addHeaderView {
                ItemHttpTtsBinding.inflate(layoutInflater, recyclerView, false).apply {
                    sysTtsViews.add(cbName)
                    ivEdit.gone()
                    ivMenuDelete.gone()
                    labelSys.visible()
                    cbName.text = engine.label
                    cbName.tag = engine.name
                    cbName.isChecked = GSON.fromJsonObject<SelectItem<String>>(ttsEngine)
                        .getOrNull()?.value == cbName.tag
                    cbName.setOnClickListener {
                        upTts(GSON.toJson(SelectItem(engine.label, engine.name)))
                    }
                }
            }
        }
        //Edge TTS 内置引擎
        adapter.addHeaderView {
            ItemHttpTtsBinding.inflate(layoutInflater, recyclerView, false).apply {
                sysTtsViews.add(cbName)
                ivEdit.gone()
                ivMenuDelete.gone()
                labelSys.visible()
                cbName.text = "Edge TTS(${EdgeTts.voiceDisplayName(AppConfig.edgeTtsVoice)})"
                cbName.tag = EdgeTts.ENGINE_ID
                cbName.isChecked = ttsEngine == EdgeTts.ENGINE_ID
                cbName.setOnClickListener {
                    upTts(EdgeTts.ENGINE_ID)
                }
                //长按配置声音与中英混读
                cbName.setOnLongClickListener {
                    showTtsEngineConfigDialog(EdgeTts.ENGINE_ID)
                    true
                }
            }
        }
        //Qwen3 TTS (阿里 DashScope)
        adapter.addHeaderView {
            ItemHttpTtsBinding.inflate(layoutInflater, recyclerView, false).apply {
                sysTtsViews.add(cbName)
                ivEdit.gone()
                ivMenuDelete.gone()
                labelSys.visible()
                cbName.text = "Qwen3 TTS(${AppConfig.dashscopeVoice})"
                cbName.tag = OpenAiCompatTts.DASHSCOPE_ENGINE_ID
                cbName.isChecked = ttsEngine == OpenAiCompatTts.DASHSCOPE_ENGINE_ID
                cbName.setOnClickListener {
                    upTts(OpenAiCompatTts.DASHSCOPE_ENGINE_ID)
                }
                //长按配置API Key/模型/音色
                cbName.setOnLongClickListener {
                    showTtsEngineConfigDialog(OpenAiCompatTts.DASHSCOPE_ENGINE_ID)
                    true
                }
            }
        }
        //OpenAI 兼容 TTS (VibeVoice/Confucius等自建)
        adapter.addHeaderView {
            ItemHttpTtsBinding.inflate(layoutInflater, recyclerView, false).apply {
                sysTtsViews.add(cbName)
                ivEdit.gone()
                ivMenuDelete.gone()
                labelSys.visible()
                cbName.text = "自定义TTS(${AppConfig.openaiTtsModel})"
                cbName.tag = OpenAiCompatTts.OPENAI_ENGINE_ID
                cbName.isChecked = ttsEngine == OpenAiCompatTts.OPENAI_ENGINE_ID
                cbName.setOnClickListener {
                    upTts(OpenAiCompatTts.OPENAI_ENGINE_ID)
                }
                //长按配置服务地址/密钥/模型/音色
                cbName.setOnLongClickListener {
                    showTtsEngineConfigDialog(OpenAiCompatTts.OPENAI_ENGINE_ID)
                    true
                }
            }
        }
        tvFooterLeft.setText(R.string.book)
        tvFooterLeft.visible()
        tvFooterLeft.setOnClickListener {
            ReadBook.book?.setTtsEngine(ttsEngine)
            callBack?.upSpeakEngineSummary()
            ReadAloud.upReadAloudClass()
            dismissAllowingStateLoss()
        }
        tvOk.setText(R.string.general)
        tvOk.visible()
        tvOk.setOnClickListener {
            ReadBook.book?.setTtsEngine(null)
            AppConfig.ttsEngine = ttsEngine
            callBack?.upSpeakEngineSummary()
            ReadAloud.upReadAloudClass()
            dismissAllowingStateLoss()
        }
        tvCancel.visible()
        tvCancel.setOnClickListener {
            dismissAllowingStateLoss()
        }
    }

    private fun initMenu() = binding.run {
        toolBar.inflateMenu(R.menu.speak_engine)
        toolBar.menu.applyTint(requireContext())
        toolBar.setOnMenuItemClickListener(this@SpeakEngineDialog)
    }

    private fun initData() {
        lifecycleScope.launch {
            appDb.httpTTSDao.flowAll().catch {
                AppLog.put("朗读引擎界面获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).conflate().collect {
                adapter.setItems(it)
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_clear -> clearCache()
            R.id.menu_add -> showDialogFragment<HttpTtsEditDialog>()
            R.id.menu_default -> viewModel.importDefault()
            R.id.menu_import_local -> importDocResult.launch {
                mode = HandleFileContract.FILE
                allowExtensions = arrayOf("txt", "json")
            }

            R.id.menu_import_onLine -> importAlert()
            R.id.menu_export_all -> exportResult.launch {
                mode = HandleFileContract.EXPORT
                fileData = HandleFileContract.FileData(
                    "httpTts.json",
                    GSON.toJson(adapter.getItems()).toByteArray(),
                    "application/json"
                )
            }
            R.id.menu_export -> {
                if (currentSelect == -1) {
                    toastOnUi(R.string.is_system_tts_no_export)
                    return true
                }
                val tts = adapter.getItem(currentSelect) ?: return true
                exportResult.launch {
                    mode = HandleFileContract.EXPORT
                    fileData = HandleFileContract.FileData(
                        "httpTts_${tts.name}.json",
                        GSON.toJson(tts).toByteArray(),
                        "application/json"
                    )
                }
            }
        }
        return true
    }

    fun clearCache() {
        execute {
            ReadAloud.upReadAloudClass()
            val ttsFolderPath = "${requireContext().cacheDir.absolutePath}${File.separator}httpTTS${File.separator}"
            FileUtils.listDirsAndFiles(ttsFolderPath)?.forEach {
                FileUtils.delete(it.absolutePath)
            }
            toastOnUi(R.string.clear_cache_success)
        }
    }

    private fun importAlert() {
        val aCache = ACache.get(cacheDir = false)
        val cacheUrls: MutableList<String> = aCache
            .getAsString(ttsUrlKey)
            ?.splitNotBlank(",")
            ?.toMutableList() ?: mutableListOf()
        alert(R.string.import_on_line) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "url"
                editView.setFilterValues(cacheUrls)
                editView.delCallBack = {
                    cacheUrls.remove(it)
                    aCache.put(ttsUrlKey, cacheUrls.joinToString(","))
                }
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let { url ->
                    if (url.isAbsUrl() && !cacheUrls.contains(url)) {
                        cacheUrls.add(0, url)
                        aCache.put(ttsUrlKey, cacheUrls.joinToString(","))
                    }
                    showDialogFragment(ImportHttpTtsDialog(url))
                }
            }
        }
    }

    private fun upTts(tts: String) {
        ttsEngine = tts
        sysTtsViews.forEach {
            val isChecked = when (ttsEngine) {
                EdgeTts.ENGINE_ID,
                OpenAiCompatTts.DASHSCOPE_ENGINE_ID,
                OpenAiCompatTts.OPENAI_ENGINE_ID -> it.tag == ttsEngine

                else -> GSON.fromJsonObject<SelectItem<String>>(ttsEngine)
                    .getOrNull()?.value == it.tag
            }
            if (isChecked) {
                currentSelect = -1
            }
            it.isChecked = isChecked
        }
        adapter.notifyItemRangeChanged(adapter.getHeaderCount(), adapter.itemCount)
    }

    /**
     * 内置TTS引擎配置对话框
     * Edge TTS: 中文声音/英文声音/中英混读
     * Qwen3 TTS: API Key/模型/音色
     * 自定义TTS: 服务地址/API Key/模型/音色
     */
    private fun showTtsEngineConfigDialog(engineId: String) {
        val configBinding = DialogTtsEngineConfigBinding.inflate(layoutInflater)
        configBinding.run {
            when (engineId) {
                EdgeTts.ENGINE_ID -> {
                    tvDesc.text = "微软免费在线语音。朗读模式可选中文音色读全部、中英文分流或英文音色读全部。"
                    tilBaseUrl.gone()
                    tilApiKey.gone()
                    tilModel.gone()
                    tilVoice.hint = "中文声音"
                    etVoice.setText(EdgeTts.voiceDisplayName(AppConfig.edgeTtsVoice))
                    etVoice.setFilterValues(EdgeTts.VOICES.map { it.display })
                    etVoiceEn.setText(EdgeTts.enVoiceDisplayName(AppConfig.edgeTtsVoiceEn))
                    etVoiceEn.setFilterValues(EdgeTts.EN_VOICES.map { it.display })
                    when (AppConfig.edgeTtsReadMode) {
                        0 -> rbModeZhAll.isChecked = true
                        2 -> rbModeEnAll.isChecked = true
                        else -> rbModeSplit.isChecked = true
                    }
                }

                OpenAiCompatTts.DASHSCOPE_ENGINE_ID -> {
                    tvDesc.text = "阿里云百炼 qwen3-tts, 需在 dashscope.aliyuncs.com 申请 API Key(有免费额度)。"
                    tilBaseUrl.gone()
                    etApiKey.setText(AppConfig.dashscopeApiKey)
                    etModel.setText(AppConfig.dashscopeModel)
                    etModel.setFilterValues(
                        OpenAiCompatTts.DASHSCOPE_DEFAULT_MODEL,
                        "qwen-tts-latest",
                        "qwen-tts"
                    )
                    etVoice.setText(AppConfig.dashscopeVoice)
                    etVoice.setFilterValues(OpenAiCompatTts.DASHSCOPE_VOICES)
                    tilVoiceEn.gone()
                    rgReadMode.gone()
                }

                OpenAiCompatTts.OPENAI_ENGINE_ID -> {
                    tvDesc.text = "OpenAI 兼容 /audio/speech 接口, 支持微软 VibeVoice、网易有道 Confucius-TTS 等自建服务。"
                    etBaseUrl.setText(AppConfig.openaiTtsBaseUrl)
                    etApiKey.setText(AppConfig.openaiTtsApiKey)
                    etModel.setText(AppConfig.openaiTtsModel)
                    etModel.setFilterValues(
                        "VibeVoice-7B",
                        "Confucius-TTS",
                        "tts-1",
                        "tts-1-hd"
                    )
                    etVoice.setText(AppConfig.openaiTtsVoice)
                    etVoice.setFilterValues(OpenAiCompatTts.OPENAI_VOICES)
                    tilVoiceEn.gone()
                    rgReadMode.gone()
                }
            }
        }
        alert("TTS引擎设置") {
            customView { configBinding.root }
            okButton {
                configBinding.run {
                    when (engineId) {
                        EdgeTts.ENGINE_ID -> {
                            val zhDisplay = etVoice.text?.toString()?.trim() ?: ""
                            val zhVoice = EdgeTts.VOICES.firstOrNull { it.display == zhDisplay }?.name
                                ?: EdgeTts.VOICES.firstOrNull { it.name == zhDisplay }?.name
                            if (zhVoice != null) {
                                AppConfig.edgeTtsVoice = zhVoice
                            } else {
                                toastOnUi("中文声音无效, 未修改")
                            }
                            val enDisplay = etVoiceEn.text?.toString()?.trim() ?: ""
                            val enVoice = EdgeTts.EN_VOICES.firstOrNull { it.display == enDisplay }?.name
                                ?: EdgeTts.EN_VOICES.firstOrNull { it.name == enDisplay }?.name
                            if (enVoice != null) {
                                AppConfig.edgeTtsVoiceEn = enVoice
                            } else {
                                toastOnUi("英文声音无效, 未修改")
                            }
                            AppConfig.edgeTtsReadMode = when {
                                rbModeZhAll.isChecked -> 0
                                rbModeEnAll.isChecked -> 2
                                else -> 1
                            }
                        }

                        OpenAiCompatTts.DASHSCOPE_ENGINE_ID -> {
                            AppConfig.dashscopeApiKey = etApiKey.text?.toString()?.trim() ?: ""
                            AppConfig.dashscopeModel = etModel.text?.toString()?.trim()
                                ?.ifBlank { OpenAiCompatTts.DASHSCOPE_DEFAULT_MODEL } ?: ""
                            AppConfig.dashscopeVoice = etVoice.text?.toString()?.trim()
                                ?.ifBlank { "Cherry" } ?: "Cherry"
                        }

                        OpenAiCompatTts.OPENAI_ENGINE_ID -> {
                            AppConfig.openaiTtsBaseUrl = etBaseUrl.text?.toString()?.trim()
                                ?.ifBlank { AppConfig.openaiTtsBaseUrl } ?: AppConfig.openaiTtsBaseUrl
                            AppConfig.openaiTtsApiKey = etApiKey.text?.toString()?.trim() ?: ""
                            AppConfig.openaiTtsModel = etModel.text?.toString()?.trim()
                                ?.ifBlank { AppConfig.openaiTtsModel } ?: AppConfig.openaiTtsModel
                            AppConfig.openaiTtsVoice = etVoice.text?.toString()?.trim()
                                ?.ifBlank { AppConfig.openaiTtsVoice } ?: AppConfig.openaiTtsVoice
                        }
                    }
                }
                upTts(engineId)
                //刷新列表头显示
                adapter.notifyItemRangeChanged(0, adapter.getHeaderCount())
            }
        }
    }

    inner class Adapter(context: Context) :
        RecyclerAdapter<HttpTTS, ItemHttpTtsBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemHttpTtsBinding {
            return ItemHttpTtsBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemHttpTtsBinding,
            item: HttpTTS,
            payloads: MutableList<Any>
        ) {
            binding.apply {
                cbName.text = item.name
                val isChecked = item.id.toString() == ttsEngine
                if (isChecked) {
                    currentSelect = holder.layoutPosition - getHeaderCount()
                }
                cbName.isChecked = isChecked
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemHttpTtsBinding) {
            binding.run {
                cbName.setOnClickListener {
                    getItemByLayoutPosition(holder.layoutPosition)?.let { httpTTS ->
                        val id = httpTTS.id.toString()
                        upTts(id)
                        if (!httpTTS.loginUrl.isNullOrBlank()
                            && httpTTS.getLoginInfo().isNullOrBlank()
                        ) {
                            startActivity<SourceLoginActivity> {
                                putExtra("type", "httpTts")
                                putExtra("key", id)
                            }
                        }
                    }
                }
                cbName.setOnLongClickListener {
                    getItemByLayoutPosition(holder.layoutPosition)?.let { httpTTS ->
                        if (!httpTTS.loginUrl.isNullOrBlank()) {
                            val id = httpTTS.id.toString()
                            startActivity<SourceLoginActivity> {
                                putExtra("type", "httpTts")
                                putExtra("key", id)
                            }
                            return@setOnLongClickListener true
                        }
                    }
                    false
                }
                ivEdit.setOnClickListener {
                    val id = getItemByLayoutPosition(holder.layoutPosition)!!.id
                    showDialogFragment(HttpTtsEditDialog(id))
                }
                ivMenuDelete.setOnClickListener {
                    getItemByLayoutPosition(holder.layoutPosition)?.let { httpTTS ->
                        alert(R.string.draw) {
                            setMessage(getString(R.string.sure_del) + "\n" + httpTTS.name)
                            noButton()
                            yesButton {
                                appDb.httpTTSDao.delete(httpTTS)
                            }
                        }
                    }
                }
            }
        }

    }

    interface CallBack {
        fun upSpeakEngineSummary()
    }

}