package com.lagradost.cloudstream3.ui.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.*
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.APIHolder.getApiProviderLangSettings
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.MainActivity.Companion.bookmarksUpdatedEvent
import com.lagradost.cloudstream3.MainActivity.Companion.mainPluginsLoadedEvent
import com.lagradost.cloudstream3.databinding.FragmentHomeBinding
import com.lagradost.cloudstream3.databinding.HomeEpisodesExpandedBinding
import com.lagradost.cloudstream3.databinding.HomeSelectMainpageBinding
import com.lagradost.cloudstream3.databinding.TvtypesChipsBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.APIRepository.Companion.noneApi
import com.lagradost.cloudstream3.ui.APIRepository.Companion.randomApi
import com.lagradost.cloudstream3.ui.WatchType
import com.lagradost.cloudstream3.ui.quicksearch.QuickSearchFragment
import com.lagradost.cloudstream3.ui.search.*
import com.lagradost.cloudstream3.ui.search.SearchHelper.handleSearchClickCallback
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTrueTvSettings
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.AppUtils.addProgramsToContinueWatching
import com.lagradost.cloudstream3.utils.AppUtils.isRecyclerScrollable
import com.lagradost.cloudstream3.utils.AppUtils.loadResult
import com.lagradost.cloudstream3.utils.AppUtils.loadSearchResult
import com.lagradost.cloudstream3.utils.AppUtils.ownHide
import com.lagradost.cloudstream3.utils.AppUtils.ownShow
import com.lagradost.cloudstream3.utils.AppUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.cloudstream3.utils.SubtitleHelper.getFlagFromIso
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import com.lagradost.cloudstream3.utils.UIHelper.popupMenuNoIconsAndNoStringRes
import com.lagradost.cloudstream3.utils.USER_SELECTED_HOMEPAGE_API

import java.util.*


const val HOME_BOOKMARK_VALUE_LIST = "home_bookmarked_last_list"
const val HOME_PREF_HOMEPAGE = "home_pref_homepage"

class HomeFragment : Fragment() {
    companion object {
        val configEvent = Event<Int>()
        var currentSpan = 1
        val listHomepageItems = mutableListOf<SearchResponse>()

        private val errorProfilePics = listOf(
            R.drawable.monke_benene,
            R.drawable.monke_burrito,
            R.drawable.monke_coco,
            R.drawable.monke_cookie,
            R.drawable.monke_flusdered,
            R.drawable.monke_funny,
            R.drawable.monke_like,
            R.drawable.monke_party,
            R.drawable.monke_sob,
            R.drawable.monke_drink,
        )

        val errorProfilePic = errorProfilePics.random()

        //fun Activity.loadHomepageList(
        //    item: HomePageList,
        //    deleteCallback: (() -> Unit)? = null,
        //) {
        //    loadHomepageList(
        //        expand = HomeViewModel.ExpandableHomepageList(item, 1, false),
        //        deleteCallback = deleteCallback,
        //        expandCallback = null
        //    )
        //}

        // returns a BottomSheetDialog that will be hidden with OwnHidden upon hide, and must be saved to be able call ownShow in onCreateView
        fun Activity.loadHomepageList(
            expand: HomeViewModel.ExpandableHomepageList,
            deleteCallback: (() -> Unit)? = null,
            expandCallback: (suspend (String) -> HomeViewModel.ExpandableHomepageList?)? = null,
            dismissCallback: (() -> Unit),
        ): BottomSheetDialog {
            val context = this
            val bottomSheetDialogBuilder = BottomSheetDialog(context)
            val binding: HomeEpisodesExpandedBinding = HomeEpisodesExpandedBinding.inflate(
                bottomSheetDialogBuilder.layoutInflater,
                null,
                false
            )
            bottomSheetDialogBuilder.setContentView(binding.root)
            //val title = bottomSheetDialogBuilder.findViewById<TextView>(R.id.home_expanded_text)!!

            //title.findViewTreeLifecycleOwner().lifecycle.addObserver()

            val item = expand.list
            binding.homeExpandedText.text = item.name
            // val recycle =
            //    bottomSheetDialogBuilder.findViewById<AutofitRecyclerView>(R.id.home_expanded_recycler)!!
            //val titleHolder =
            //    bottomSheetDialogBuilder.findViewById<FrameLayout>(R.id.home_expanded_drag_down)!!

            // main {
            //(bottomSheetDialogBuilder.ownerActivity as androidx.fragment.app.FragmentActivity?)?.supportFragmentManager?.fragments?.lastOrNull()?.viewLifecycleOwner?.apply {
            //    println("GOT LIFE: lifecycle $this")
            //    this.lifecycle.addObserver(object : DefaultLifecycleObserver {
            //        override fun onResume(owner: LifecycleOwner) {
            //            super.onResume(owner)
            //            println("onResume!!!!")
            //            bottomSheetDialogBuilder?.ownShow()
            //        }

            //        override fun onStop(owner: LifecycleOwner) {
            //            super.onStop(owner)
            //            bottomSheetDialogBuilder?.ownHide()
            //        }
            //    })
            //}
            // }
            //val delete = bottomSheetDialogBuilder.home_expanded_delete
            binding.homeExpandedDelete.isGone = deleteCallback == null
            if (deleteCallback != null) {
                binding.homeExpandedDelete.setOnClickListener {
                    try {
                        val builder: AlertDialog.Builder = AlertDialog.Builder(context)
                        val dialogClickListener =
                            DialogInterface.OnClickListener { _, which ->
                                when (which) {
                                    DialogInterface.BUTTON_POSITIVE -> {
                                        deleteCallback.invoke()
                                        bottomSheetDialogBuilder.dismissSafe(this)
                                    }

                                    DialogInterface.BUTTON_NEGATIVE -> {}
                                }
                            }

                        builder.setTitle(R.string.delete_file)
                            .setMessage(
                                context.getString(R.string.delete_message).format(
                                    item.name
                                )
                            )
                            .setPositiveButton(R.string.delete, dialogClickListener)
                            .setNegativeButton(R.string.cancel, dialogClickListener)
                            .show().setDefaultFocus()
                    } catch (e: Exception) {
                        logError(e)
                        // ye you somehow fucked up formatting did you?
                    }
                }
            }
            binding.homeExpandedDragDown.setOnClickListener {
                bottomSheetDialogBuilder.dismissSafe(this)
            }


            // Span settings
            binding.homeExpandedRecycler.spanCount = currentSpan

            binding.homeExpandedRecycler.adapter =
                SearchAdapter(item.list.toMutableList(), binding.homeExpandedRecycler) { callback ->
                    handleSearchClickCallback(this, callback)
                    if (callback.action == SEARCH_ACTION_LOAD || callback.action == SEARCH_ACTION_PLAY_FILE) {
                        bottomSheetDialogBuilder.ownHide() // we hide here because we want to resume it later
                        //bottomSheetDialogBuilder.dismissSafe(this)
                    }
                }.apply {
                    hasNext = expand.hasNext
                }

            binding.homeExpandedRecycler.addOnScrollListener(object :
                RecyclerView.OnScrollListener() {
                var expandCount = 0
                val name = expand.list.name

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)

                    val adapter = recyclerView.adapter
                    if (adapter !is SearchAdapter) return

                    val count = adapter.itemCount
                    val currentHasNext = adapter.hasNext
                    //!recyclerView.canScrollVertically(1)
                    if (!recyclerView.isRecyclerScrollable() && currentHasNext && expandCount != count) {
                        expandCount = count
                        ioSafe {
                            expandCallback?.invoke(name)?.let { newExpand ->
                                (recyclerView.adapter as? SearchAdapter?)?.apply {
                                    hasNext = newExpand.hasNext
                                    updateList(newExpand.list.list)
                                }
                            }
                        }
                    }
                }
            })

            val spanListener = { span: Int ->
                binding.homeExpandedRecycler.spanCount = span
                //(recycle.adapter as SearchAdapter).notifyDataSetChanged()
            }

            configEvent += spanListener

            bottomSheetDialogBuilder.setOnDismissListener {
                dismissCallback.invoke()
                configEvent -= spanListener
            }

            //(recycle.adapter as SearchAdapter).notifyDataSetChanged()

            bottomSheetDialogBuilder.show()
            return bottomSheetDialogBuilder
        }

        fun getPairList(
            anime: Chip?,
            cartoons: Chip?,
            tvs: Chip?,
            docs: Chip?,
            movies: Chip?,
            asian: Chip?,
            livestream: Chip?,
            nsfw: Chip?,
            others: Chip?,
        ): List<Pair<Chip?, List<TvType>>> {
            // This list should be same order as home screen to aid navigation
            return listOf(
                Pair(movies, listOf(TvType.Movie, TvType.Torrent)),
                Pair(tvs, listOf(TvType.TvSeries)),
                Pair(anime, listOf(TvType.Anime, TvType.OVA, TvType.AnimeMovie)),
                Pair(asian, listOf(TvType.AsianDrama)),
                Pair(cartoons, listOf(TvType.Cartoon)),
                Pair(docs, listOf(TvType.Documentary)),
                Pair(livestream, listOf(TvType.Live)),
                Pair(nsfw, listOf(TvType.NSFW)),
                Pair(others, listOf(TvType.Others)),
            )
        }

        private fun getPairList(header: TvtypesChipsBinding) = getPairList(
            header.homeSelectAnime,
            header.homeSelectCartoons,
            header.homeSelectTvSeries,
            header.homeSelectDocumentaries,
            header.homeSelectMovies,
            header.homeSelectAsian,
            header.homeSelectLivestreams,
            header.homeSelectNsfw,
            header.homeSelectOthers
        )

        fun validateChips(header: TvtypesChipsBinding?, validTypes: List<TvType>) {
            if (header == null) return
            val pairList = getPairList(header)
            for ((button, types) in pairList) {
                val isValid = validTypes.any { types.contains(it) }
                button?.isVisible = isValid
            }
        }

        fun updateChips(header: TvtypesChipsBinding?, selectedTypes: List<TvType>) {
            if (header == null) return
            val pairList = getPairList(header)
            for ((button, types) in pairList) {
                button?.isChecked =
                    button?.isVisible == true && selectedTypes.any { types.contains(it) }
            }
        }

        fun bindChips(
            header: TvtypesChipsBinding?,
            selectedTypes: List<TvType>,
            validTypes: List<TvType>,
            callback: (List<TvType>) -> Unit
        ) {
            if (header == null) return
            val pairList = getPairList(header)
            for ((button, types) in pairList) {
                val isValid = validTypes.any { types.contains(it) }
                button?.isVisible = isValid
                button?.isChecked = isValid && selectedTypes.any { types.contains(it) }
                button?.setOnCheckedChangeListener { _, _ ->
                    val list = ArrayList<TvType>()
                    for ((sbutton, vvalidTypes) in pairList) {
                        if (sbutton?.isChecked == true)
                            list.addAll(vvalidTypes)
                    }
                    callback(list)
                }
            }
        }

        fun Context.selectHomepage(selectedApiName: String?, callback: (String) -> Unit) {
            val validAPIs = filterProviderByPreferredMedia().toMutableList()

            validAPIs.add(0, randomApi)
            validAPIs.add(0, noneApi)
            //val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            //builder.setView(R.layout.home_select_mainpage)
            val builder =
                BottomSheetDialog(this)

            builder.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            val binding: HomeSelectMainpageBinding = HomeSelectMainpageBinding.inflate(
                builder.layoutInflater,
                null,
                false
            )

            builder.setContentView(binding.root)
            builder.show()
            builder.let { dialog ->
                val isMultiLang = getApiProviderLangSettings().let { set ->
                    set.size > 1 || set.contains(AllLanguagesName)
                }
                //dialog.window?.setGravity(Gravity.BOTTOM)

                var currentApiName = selectedApiName

                var currentValidApis: MutableList<MainAPI> = mutableListOf()
                val preSelectedTypes = this.getKey<List<String>>(HOME_PREF_HOMEPAGE)
                    ?.mapNotNull { listName -> TvType.values().firstOrNull { it.name == listName } }
                    ?.toMutableList()
                    ?: mutableListOf(TvType.Movie, TvType.TvSeries)

                val cancelBtt = dialog.findViewById<MaterialButton>(R.id.cancel_btt)
                val applyBtt = dialog.findViewById<MaterialButton>(R.id.apply_btt)

                cancelBtt?.setOnClickListener {
                    dialog.dismissSafe()
                }

                applyBtt?.setOnClickListener {
                    if (currentApiName != selectedApiName) {
                        currentApiName?.let(callback)
                    }
                    dialog.dismissSafe()
                }

                val listView = dialog.findViewById<ListView>(R.id.listview1)
                val arrayAdapter = ArrayAdapter<String>(this, R.layout.sort_bottom_single_choice)
                listView?.adapter = arrayAdapter
                listView?.choiceMode = AbsListView.CHOICE_MODE_SINGLE

                listView?.setOnItemClickListener { _, _, i, _ ->
                    if (currentValidApis.isNotEmpty()) {
                        currentApiName = currentValidApis[i].name
                        //to switch to apply simply remove this
                        currentApiName?.let(callback)
                        dialog.dismissSafe()
                    }
                }

                fun updateList() {
                    this.setKey(HOME_PREF_HOMEPAGE, preSelectedTypes)

                    arrayAdapter.clear()
                    currentValidApis = validAPIs.filter { api ->
                        api.hasMainPage && api.supportedTypes.any {
                            preSelectedTypes.contains(it)
                        }
                    }.sortedBy { it.name.lowercase() }.toMutableList()
                    currentValidApis.addAll(0, validAPIs.subList(0, 2))

                    val names =
                        currentValidApis.map { if (isMultiLang) "${getFlagFromIso(it.lang)?.plus(" ") ?: ""}${it.name}" else it.name }
                    val index = currentValidApis.map { it.name }.indexOf(currentApiName)
                    listView?.setItemChecked(index, true)
                    arrayAdapter.addAll(names)
                    arrayAdapter.notifyDataSetChanged()
                }

                bindChips(
                    binding.tvtypesChipsScroll.tvtypesChips,
                    preSelectedTypes,
                    validAPIs.flatMap { it.supportedTypes }.distinct()
                ) { list ->
                    preSelectedTypes.clear()
                    preSelectedTypes.addAll(list)
                    updateList()
                }
                updateList()
            }
        }
    }

    private val homeViewModel: HomeViewModel by activityViewModels()

    var binding: FragmentHomeBinding? = null


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        //homeViewModel =
        //     ViewModelProvider(this).get(HomeViewModel::class.java)
        bottomSheetDialog?.ownShow()
        val layout =
            if (isTvSettings()) R.layout.fragment_home_tv else R.layout.fragment_home

        /* val binding = FragmentHomeTvBinding.inflate(layout, container, false)
         binding.homeLoadingError

         val binding2 = FragmentHomeBinding.inflate(layout, container, false)
         binding2.homeLoadingError*/
        val root = inflater.inflate(layout, container, false)
        binding = FragmentHomeBinding.bind(root)
        //val localBinding = FragmentHomeBinding.inflate(inflater)
        //binding = localBinding
        return root

        //return inflater.inflate(layout, container, false)
    }

    override fun onDestroyView() {
        bottomSheetDialog?.ownHide()
        binding = null
        super.onDestroyView()
    }

    private fun fixGrid() {
        activity?.getSpanCount()?.let {
            currentSpan = it
        }
        configEvent.invoke(currentSpan)
    }

    private val apiChangeClickListener = View.OnClickListener { view ->
        view.context.selectHomepage(currentApiName) { api ->
            homeViewModel.loadAndCancel(api)
        }
        /*val validAPIs = view.context?.filterProviderByPreferredMedia()?.toMutableList() ?: mutableListOf()

        validAPIs.add(0, randomApi)
        validAPIs.add(0, noneApi)
        view.popupMenuNoIconsAndNoStringRes(validAPIs.mapIndexed { index, api -> Pair(index, api.name) }) {
            homeViewModel.loadAndCancel(validAPIs[itemId].name)
        }*/
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        //(home_preview_viewpager?.adapter as? HomeScrollAdapter)?.notifyDataSetChanged()
        fixGrid()
    }

    fun bookmarksUpdated(_data: Boolean) {
        reloadStored()
    }

    override fun onResume() {
        super.onResume()
        reloadStored()
        bookmarksUpdatedEvent += ::bookmarksUpdated
        afterPluginsLoadedEvent += ::afterPluginsLoaded
        mainPluginsLoadedEvent += ::afterMainPluginsLoaded
    }

    override fun onStop() {
        bookmarksUpdatedEvent -= ::bookmarksUpdated
        afterPluginsLoadedEvent -= ::afterPluginsLoaded
        mainPluginsLoadedEvent -= ::afterMainPluginsLoaded
        super.onStop()
    }

    private fun reloadStored() {
        homeViewModel.loadResumeWatching()
        val list = EnumSet.noneOf(WatchType::class.java)
        getKey<IntArray>(HOME_BOOKMARK_VALUE_LIST)?.map { WatchType.fromInternalId(it) }?.let {
            list.addAll(it)
        }
        homeViewModel.loadStoredData(list)
    }

    private fun afterMainPluginsLoaded(unused: Boolean = false) {
        loadHomePage(false)
    }

    private fun afterPluginsLoaded(forceReload: Boolean) {
        loadHomePage(forceReload)
    }

    private fun loadHomePage(forceReload: Boolean) {
        val apiName = context?.getKey<String>(USER_SELECTED_HOMEPAGE_API)

        if (homeViewModel.apiName.value != apiName || apiName == null || forceReload) {
            //println("Caught home: " + homeViewModel.apiName.value + " at " + apiName)
            homeViewModel.loadAndCancel(apiName, forceReload)
        }
    }

    private fun homeHandleSearch(callback: SearchClickCallback) {
        if (callback.action == SEARCH_ACTION_FOCUSED) {
            //focusCallback(callback.card)
        } else {
            handleSearchClickCallback(activity, callback)
        }
    }

    private var currentApiName: String? = null
    private var toggleRandomButton = false

    private var bottomSheetDialog: BottomSheetDialog? = null


    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fixGrid()

        binding?.homeChangeApiLoading?.setOnClickListener(apiChangeClickListener)


        binding?.homeChangeApiLoading?.setOnClickListener(apiChangeClickListener)
        binding?.homeApiFab?.setOnClickListener(apiChangeClickListener)
        binding?.homeRandom?.setOnClickListener {
            if (listHomepageItems.isNotEmpty()) {
                activity.loadSearchResult(listHomepageItems.random())
            }
        }

        //Load value for toggling Random button. Hide at startup
        context?.let {
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(it)
            toggleRandomButton =
                settingsManager.getBoolean(
                    getString(R.string.random_button_key),
                    false
                ) && !isTvSettings()
            binding?.homeRandom?.visibility = View.GONE
        }

        observe(homeViewModel.preview) { preview ->
            (binding?.homeMasterRecycler?.adapter as? HomeParentItemAdapterPreview?)?.setPreviewData(
                preview
            )
        }

        observe(homeViewModel.apiName) { apiName ->
            currentApiName = apiName
            binding?.homeApiFab?.text = apiName
            (binding?.homeMasterRecycler?.adapter as? HomeParentItemAdapterPreview?)?.setApiName(
                apiName
            )
        }

        observe(homeViewModel.page) { data ->
            binding?.apply {
                when (data) {
                    is Resource.Success -> {
                        homeLoadingShimmer.stopShimmer()

                        val d = data.value
                        val mutableListOfResponse = mutableListOf<SearchResponse>()
                        listHomepageItems.clear()

                        (homeMasterRecycler.adapter as? ParentItemAdapter)?.updateList(
                            d.values.toMutableList(),
                            homeMasterRecycler
                        )

                        homeLoading.isVisible = false
                        homeLoadingError.isVisible = false
                        homeMasterRecycler.isVisible = true
                        //home_loaded?.isVisible = true
                        if (toggleRandomButton) {
                            //Flatten list
                            d.values.forEach { dlist ->
                                mutableListOfResponse.addAll(dlist.list.list)
                            }
                            listHomepageItems.addAll(mutableListOfResponse.distinctBy { it.url })

                            homeRandom.isVisible = listHomepageItems.isNotEmpty()
                        } else {
                            homeRandom.isGone = true
                        }
                    }

                    is Resource.Failure -> {

                        homeLoadingShimmer.stopShimmer()

                        resultErrorText.text = data.errorString

                        homeReloadConnectionerror.setOnClickListener(apiChangeClickListener)

                        homeReloadConnectionOpenInBrowser.setOnClickListener { view ->
                            val validAPIs = apis//.filter { api -> api.hasMainPage }

                            view.popupMenuNoIconsAndNoStringRes(validAPIs.mapIndexed { index, api ->
                                Pair(
                                    index,
                                    api.name
                                )
                            }) {
                                try {
                                    val i = Intent(Intent.ACTION_VIEW)
                                    i.data = Uri.parse(validAPIs[itemId].mainUrl)
                                    startActivity(i)
                                } catch (e: Exception) {
                                    logError(e)
                                }
                            }
                        }

                        homeLoading.isVisible = false
                        homeLoadingError.isVisible = true
                        homeMasterRecycler.isVisible = false
                        //home_loaded?.isVisible = false
                    }

                    is Resource.Loading -> {
                        (homeMasterRecycler.adapter as? ParentItemAdapter)?.updateList(listOf())
                        homeLoadingShimmer.startShimmer()
                        homeLoading.isVisible = true
                        homeLoadingError.isVisible = false
                        homeMasterRecycler.isVisible = false
                        //home_loaded?.isVisible = false
                    }
                }
            }
        }



        observe(homeViewModel.availableWatchStatusTypes) { availableWatchStatusTypes ->
            context?.setKey(
                HOME_BOOKMARK_VALUE_LIST,
                availableWatchStatusTypes.first.map { it.internalId }.toIntArray()
            )
            (binding?.homeMasterRecycler?.adapter as? HomeParentItemAdapterPreview?)?.setAvailableWatchStatusTypes(
                availableWatchStatusTypes
            )
        }

        observe(homeViewModel.bookmarks) { data ->
            (binding?.homeMasterRecycler?.adapter as? HomeParentItemAdapterPreview?)?.setBookmarkData(
                data
            )
        }

        observe(homeViewModel.resumeWatching) { resumeWatching ->
            (binding?.homeMasterRecycler?.adapter as? HomeParentItemAdapterPreview?)?.setResumeWatchingData(
                resumeWatching
            )
            if (isTrueTvSettings()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ioSafe {
                        activity?.addProgramsToContinueWatching(resumeWatching.mapNotNull { it as? DataStoreHelper.ResumeWatchingResult })
                    }
                }
            }
        }


        //context?.fixPaddingStatusbarView(home_statusbar)
        //context?.fixPaddingStatusbar(home_padding)
        fixPaddingStatusbar(binding?.homeLoadingStatusbar)

        binding?.homeMasterRecycler?.adapter =
            HomeParentItemAdapterPreview(mutableListOf(), { callback ->
                homeHandleSearch(callback)
            }, { item ->
                bottomSheetDialog = activity?.loadHomepageList(item, expandCallback = {
                    homeViewModel.expandAndReturn(it)
                }, dismissCallback = {
                    bottomSheetDialog = null
                })
            }, { name ->
                homeViewModel.expand(name)
            }, { load ->
                activity?.loadResult(load.response.url, load.response.apiName, load.action)
            }, {
                homeViewModel.loadMoreHomeScrollResponses()
            }, {
                apiChangeClickListener.onClick(it)
            }, reloadStored = {
                reloadStored()
            }, loadStoredData = {
                homeViewModel.loadStoredData(it)
            }, { (isQuickSearch, text) ->
                if (!isQuickSearch) {
                    QuickSearchFragment.pushSearch(
                        activity,
                        text,
                        currentApiName?.let { arrayOf(it) })
                }
            })

        reloadStored()
        loadHomePage(false)
        binding?.homeMasterRecycler?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {

                binding?.apply {
                    if (dy > 0) { //check for scroll down
                        homeApiFab.shrink() // hide
                        homeRandom.shrink()
                    } else if (dy < -5) {
                        if (!isTvSettings()) {
                            homeApiFab.extend() // show
                            homeRandom.extend()
                        }
                    }
                }


                super.onScrolled(recyclerView, dx, dy)
            }
        })

        // nice profile pic on homepage
        //home_profile_picture_holder?.isVisible = false
        // just in case
        binding?.apply {
            if (isTvSettings()) {
                homeApiFab.isVisible = false
                if (isTrueTvSettings()) {
                    homeChangeApiLoading.isVisible = true
                    homeChangeApiLoading.isFocusable = true
                    homeChangeApiLoading.isFocusableInTouchMode = true
                }
                // home_bookmark_select?.isFocusable = true
                // home_bookmark_select?.isFocusableInTouchMode = true
            } else {
                homeApiFab.isVisible = true
                homeChangeApiLoading.isVisible = false
            }
        }
        //TODO READD THIS
        /*for (syncApi in OAuth2Apis) {
            val login = syncApi.loginInfo()
            val pic = login?.profilePicture
            if (home_profile_picture?.setImage(
                    pic,
                    errorImageDrawable = errorProfilePic
                ) == true
            ) {
                home_profile_picture_holder?.isVisible = true
                break
            }
        }*/
    }
}
