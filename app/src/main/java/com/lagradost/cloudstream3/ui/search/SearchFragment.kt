package com.lagradost.cloudstream3.ui.search

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.APIHolder.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.APIHolder.getApiFromName
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.home.HomeFragment
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.currentSpan
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.loadHomepageList
import com.lagradost.cloudstream3.ui.home.ParentItemAdapter
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.isTvSettings
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.setKey
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixPaddingStatusbar
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import kotlinx.android.synthetic.main.fragment_search.*
import java.util.concurrent.locks.ReentrantLock

const val SEARCH_PREF_TAGS = "search_pref_tags"
const val SEARCH_PREF_PROVIDERS = "search_pref_providers"

class SearchFragment : Fragment() {
    companion object {
        fun List<SearchResponse>.filterSearchResponse(): List<SearchResponse> {
            return this.filter { response ->
                if (response is AnimeSearchResponse) {
                    (response.dubStatus.isNullOrEmpty()) || (response.dubStatus.any {
                        APIRepository.dubStatusActive.contains(it)
                    })
                } else {
                    true
                }
            }
        }
    }

    private val searchViewModel: SearchViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    private fun fixGrid() {
        activity?.getSpanCount()?.let {
            currentSpan = it
        }
        search_autofit_results.spanCount = currentSpan
        currentSpan = currentSpan
        HomeFragment.configEvent.invoke(currentSpan)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fixGrid()
    }

    override fun onDestroyView() {
        hideKeyboard()
        super.onDestroyView()
    }

    var selectedSearchTypes = mutableListOf<TvType>()
    var selectedApis = mutableSetOf<String>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        context?.fixPaddingStatusbar(searchRoot)
        fixGrid()

        val adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>? = activity?.let {
            SearchAdapter(
                ArrayList(),
                search_autofit_results,
            ) { callback ->
                SearchHelper.handleSearchClickCallback(activity, callback)
            }
        }

        search_autofit_results.adapter = adapter
        search_loading_bar.alpha = 0f

        val searchExitIcon =
            main_search.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        val searchMagIcon =
            main_search.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)
        searchMagIcon.scaleX = 0.65f
        searchMagIcon.scaleY = 0.65f

        context?.let { ctx ->
            val validAPIs = ctx.filterProviderByPreferredMedia()
            selectedApis = ctx.getKey(
                SEARCH_PREF_PROVIDERS,
                defVal = validAPIs.map { it.name }
            )!!.toMutableSet()
        }

        search_filter.setOnClickListener { searchView ->
            searchView?.context?.let { ctx ->
                val validAPIs = ctx.filterProviderByPreferredMedia()
                var currentValidApis = listOf<MainAPI>()
                val currentSelectedApis = if (selectedApis.isEmpty()) validAPIs.map { it.name }
                    .toMutableSet() else selectedApis
                val builder =
                    BottomSheetDialog(ctx)

                builder.setContentView(R.layout.home_select_mainpage)
                builder.show()
                builder.let { dialog ->
                    val anime = dialog.findViewById<MaterialButton>(R.id.home_select_anime)
                    val cartoons = dialog.findViewById<MaterialButton>(R.id.home_select_cartoons)
                    val tvs = dialog.findViewById<MaterialButton>(R.id.home_select_tv_series)
                    val docs = dialog.findViewById<MaterialButton>(R.id.home_select_documentaries)
                    val movies = dialog.findViewById<MaterialButton>(R.id.home_select_movies)
                    val cancelBtt = dialog.findViewById<MaterialButton>(R.id.cancel_btt)
                    val applyBtt = dialog.findViewById<MaterialButton>(R.id.apply_btt)

                    val pairList = HomeFragment.getPairList(anime, cartoons, tvs, docs, movies)

                    cancelBtt?.setOnClickListener {
                        dialog.dismissSafe()
                    }

                    cancelBtt?.setOnClickListener {
                        dialog.dismissSafe()
                    }

                    applyBtt?.setOnClickListener {
                        //if (currentApiName != selectedApiName) {
                        //    currentApiName?.let(callback)
                        //}
                        dialog.dismissSafe()
                    }

                    dialog.setOnDismissListener {
                        context?.setKey(SEARCH_PREF_PROVIDERS, currentSelectedApis.toList())
                        selectedApis = currentSelectedApis
                    }

                    val selectedSearchTypes = context?.getKey<List<String>>(SEARCH_PREF_TAGS)
                        ?.mapNotNull { listName ->
                            TvType.values().firstOrNull { it.name == listName }
                        }
                        ?.toMutableList()
                        ?: mutableListOf(TvType.Movie, TvType.TvSeries)

                    val listView = dialog.findViewById<ListView>(R.id.listview1)
                    val arrayAdapter = ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)
                    listView?.adapter = arrayAdapter
                    listView?.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE

                    listView?.setOnItemClickListener { _, _, i, _ ->
                        if (!currentValidApis.isNullOrEmpty()) {
                            val api = currentValidApis[i].name
                            if (currentSelectedApis.contains(api)) {
                                listView.setItemChecked(i, false)
                                currentSelectedApis -= api
                            } else {
                                listView.setItemChecked(i, true)
                                currentSelectedApis += api
                            }
                        }
                    }

                    fun updateList() {
                        arrayAdapter.clear()
                        currentValidApis = validAPIs.filter { api ->
                            api.hasMainPage && api.supportedTypes.any {
                                selectedSearchTypes.contains(it)
                            }
                        }.sortedBy { it.name }

                        val names = currentValidApis.map { it.name }

                        for ((index, api) in names.withIndex()) {
                            listView?.setItemChecked(index, currentSelectedApis.contains(api))
                        }

                        arrayAdapter.notifyDataSetChanged()
                        arrayAdapter.addAll(names)
                        arrayAdapter.notifyDataSetChanged()
                    }

                    for ((button, validTypes) in pairList) {
                        val isValid =
                            validAPIs.any { api -> validTypes.any { api.supportedTypes.contains(it) } }
                        button?.isVisible = isValid
                        if (isValid) {
                            fun buttonContains(): Boolean {
                                return selectedSearchTypes.any { validTypes.contains(it) }
                            }

                            button?.isSelected = buttonContains()
                            button?.setOnClickListener {
                                selectedSearchTypes.clear()
                                selectedSearchTypes.addAll(validTypes)
                                for ((otherButton, _) in pairList) {
                                    otherButton?.isSelected = false
                                }
                                button.isSelected = true
                                updateList()
                            }

                            button?.setOnLongClickListener {
                                if (!buttonContains()) {
                                    button.isSelected = true
                                    selectedSearchTypes.addAll(validTypes)
                                } else {
                                    button.isSelected = false
                                    selectedSearchTypes.removeAll(validTypes)
                                }
                                updateList()
                                return@setOnLongClickListener true
                            }
                        }
                    }
                    updateList()
                }
            }
        }

        val pairList = HomeFragment.getPairList(
            search_select_anime,
            search_select_cartoons,
            search_select_tv_series,
            search_select_documentaries,
            search_select_movies
        )

        selectedSearchTypes = context?.getKey<List<String>>(SEARCH_PREF_TAGS)
            ?.mapNotNull { listName -> TvType.values().firstOrNull { it.name == listName } }
            ?.toMutableList()
            ?: mutableListOf(TvType.Movie, TvType.TvSeries)
        context?.filterProviderByPreferredMedia()?.let { validAPIs ->
            for ((button, validTypes) in pairList) {
                val isValid =
                    validAPIs.any { api -> validTypes.any { api.supportedTypes.contains(it) } }
                button?.isVisible = isValid
                if (isValid) {
                    fun buttonContains(): Boolean {
                        return selectedSearchTypes.any { validTypes.contains(it) }
                    }

                    button?.isSelected = buttonContains()
                    button?.setOnClickListener {
                        selectedSearchTypes.clear()
                        selectedSearchTypes.addAll(validTypes)
                        for ((otherButton, _) in pairList) {
                            otherButton?.isSelected = false
                        }
                        it?.context?.setKey(SEARCH_PREF_TAGS, selectedSearchTypes)
                        it?.isSelected = true
                    }

                    button?.setOnLongClickListener {
                        if (!buttonContains()) {
                            it?.isSelected = true
                            selectedSearchTypes.addAll(validTypes)
                        } else {
                            it?.isSelected = false
                            selectedSearchTypes.removeAll(validTypes)
                        }
                        it?.context?.setKey(SEARCH_PREF_TAGS, selectedSearchTypes)
                        return@setOnLongClickListener true
                    }
                }
            }
        }

        if (context?.isTvSettings() == true) {
            search_filter.isFocusable = true
            search_filter.isFocusableInTouchMode = true
        }

        main_search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                searchViewModel.searchAndCancel(
                    query = query,
                    providersActive = selectedApis.filter { name ->
                        getApiFromName(name).supportedTypes.any { selectedSearchTypes.contains(it) }
                    }.toSet()
                )

                main_search?.let {
                    hideKeyboard(it)
                }

                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                //searchViewModel.quickSearch(newText)
                return true
            }
        })

        observe(searchViewModel.searchResponse) {
            when (it) {
                is Resource.Success -> {
                    it.value.let { data ->
                        if (data.isNotEmpty()) {
                            (search_autofit_results?.adapter as SearchAdapter?)?.apply {
                                cardList = data.toList()
                                notifyDataSetChanged()
                            }
                        }
                    }
                    searchExitIcon.alpha = 1f
                    search_loading_bar.alpha = 0f
                }
                is Resource.Failure -> {
                    // Toast.makeText(activity, "Server error", Toast.LENGTH_LONG).show()
                    searchExitIcon.alpha = 1f
                    search_loading_bar.alpha = 0f
                }
                is Resource.Loading -> {
                    searchExitIcon.alpha = 0f
                    search_loading_bar.alpha = 1f
                }
            }
        }

        val listLock = ReentrantLock()
        observe(searchViewModel.currentSearch) { list ->
            try {
                // https://stackoverflow.com/questions/6866238/concurrent-modification-exception-adding-to-an-arraylist
                listLock.lock()
                (search_master_recycler?.adapter as ParentItemAdapter?)?.apply {
                    items = list.map { ongoing ->
                        val ongoingList = HomePageList(
                            ongoing.apiName,
                            if (ongoing.data is Resource.Success) ongoing.data.value.filterSearchResponse() else ArrayList()
                        )
                        ongoingList
                    }
                    notifyDataSetChanged()
                }
            } catch (e: Exception) {
                logError(e)
            } finally {
                listLock.unlock()
            }
        }


        /*main_search.setOnQueryTextFocusChangeListener { _, b ->
            if (b) {
                // https://stackoverflow.com/questions/12022715/unable-to-show-keyboard-automatically-in-the-searchview
                showInputMethod(view.findFocus())
            }
        }*/
        //main_search.onActionViewExpanded()*/

        val masterAdapter: RecyclerView.Adapter<RecyclerView.ViewHolder> =
            ParentItemAdapter(listOf(), { callback ->
                SearchHelper.handleSearchClickCallback(activity, callback)
            }, { item ->
                activity?.loadHomepageList(item)
            })

        search_master_recycler?.adapter = masterAdapter
        search_master_recycler?.layoutManager = GridLayoutManager(context, 1)

        val settingsManager = PreferenceManager.getDefaultSharedPreferences(context)
        val isAdvancedSearch = settingsManager.getBoolean("advanced_search", true)

        search_master_recycler?.isVisible = isAdvancedSearch
        search_autofit_results?.isVisible = !isAdvancedSearch

        // SubtitlesFragment.push(activity)
        //searchViewModel.search("iron man")
        //(activity as AppCompatActivity).loadResult("https://shiro.is/overlord-dubbed", "overlord-dubbed", "Shiro")
/*
        (activity as AppCompatActivity?)?.supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.enter_anim,
                R.anim.exit_anim,
                R.anim.pop_enter,
                R.anim.pop_exit)
            .add(R.id.homeRoot, PlayerFragment.newInstance(PlayerData(0, null,0)))
            .commit()*/
    }

}