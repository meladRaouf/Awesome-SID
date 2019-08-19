package com.simprints.id.activities.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.simprints.core.tools.LanguageHelper
import com.simprints.id.Application
import com.simprints.id.BuildConfig
import com.simprints.id.R
import com.simprints.id.activities.alert.AlertActivityHelper
import com.simprints.id.activities.dashboard.views.WrapContentLinearLayoutManager
import com.simprints.id.activities.debug.DebugActivity
import com.simprints.id.activities.longConsent.LongConsentActivity
import com.simprints.id.activities.requestLogin.RequestLoginActivity
import com.simprints.id.activities.settings.SettingsActivity
import com.simprints.id.data.loginInfo.LoginInfoManager
import com.simprints.id.data.prefs.PreferencesManager
import com.simprints.id.tools.extensions.showToast
import kotlinx.android.synthetic.main.activity_dashboard.*
import kotlinx.android.synthetic.main.content_dashboard.*
import javax.inject.Inject

class DashboardActivity : AppCompatActivity(), DashboardContract.View {

    @Inject
    lateinit var preferences: PreferencesManager
    @Inject
    lateinit var loginInfoManager: LoginInfoManager

    companion object {
        private const val SETTINGS_ACTIVITY_REQUEST_CODE = 1
        private const val LOGOUT_RESULT_CODE = 1

    }

    override lateinit var viewPresenter: DashboardContract.Presenter
    private lateinit var cardsViewAdapter: DashboardCardAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val component = (application as Application).component
        component.inject(this)
        LanguageHelper.setLanguage(this, preferences.language)
        setupActionBar()

        viewPresenter = DashboardPresenter(this, component)
        setMenuItemClickListener()

        initCards()
    }

    private fun setupActionBar() {
        dashboardToolbar.title = getString(R.string.dashboard_label)
        setSupportActionBar(dashboardToolbar)
    }

    private fun initCards() {
        initRecyclerCardViews(viewPresenter)
        initSwipeRefreshLayout(viewPresenter)
    }

    private fun initRecyclerCardViews(viewPresenter: DashboardContract.Presenter) {
        cardsViewAdapter = DashboardCardAdapter(viewPresenter.cardsViewModelsList)
        (dashboardCardsView as RecyclerView).also {
            it.setHasFixedSize(false)
            it.itemAnimator = DefaultItemAnimator()
            it.layoutManager = WrapContentLinearLayoutManager(this)
            it.adapter = cardsViewAdapter
        }
    }

    private fun initSwipeRefreshLayout(viewPresenter: DashboardContract.Presenter) {
        swipeRefreshLayout.setOnRefreshListener {
            viewPresenter.userDidWantToRefreshCardsIfPossible()
        }
    }

    override fun notifyCardViewChanged(position: Int) {
        runOnUiThread {
            cardsViewAdapter.notifyItemChanged(position)
        }
    }

    override fun updateCardViews() {
        runOnUiThread {
            dashboardCardsView.recycledViewPool.clear()
            cardsViewAdapter.notifyDataSetChanged()
        }
    }

    override fun stopRequestIfRequired() {
        swipeRefreshLayout.isRefreshing = false
    }

    override fun onResume() {
        super.onResume()
        viewPresenter.start()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.app_menu, menu)
        if(BuildConfig.DEBUG){
            menu?.findItem(R.id.debug)?.isVisible = true
        }
        return true
    }

    private fun setMenuItemClickListener() {
        dashboardToolbar.setOnMenuItemClickListener { menuItem ->

            val id = menuItem.itemId
            when (id) {
                R.id.menuPrivacyNotice -> startActivity(Intent(this, LongConsentActivity::class.java))
                R.id.menuSettings -> startActivityForResult(Intent(this, SettingsActivity::class.java), SETTINGS_ACTIVITY_REQUEST_CODE)
                R.id.debug -> if(BuildConfig.DEBUG) startActivity(Intent(this, DebugActivity::class.java))
            }
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val potentialAlertScreenResponse = AlertActivityHelper.extractPotentialAlertScreenResponse(data)
        if (potentialAlertScreenResponse != null) {
            finish()
        }

        if (resultCode == LOGOUT_RESULT_CODE && requestCode == SETTINGS_ACTIVITY_REQUEST_CODE) {
            viewPresenter.logout()
        }
    }

    override fun startCheckLoginActivityAndFinish(){
        startActivity(Intent(this, RequestLoginActivity::class.java))
        finish()
    }

    override fun getStringWithParams(stringRes: Int, currentValue: Int, maxValue: Int): String {
        return getString(stringRes, currentValue, maxValue)
    }

    override fun showToastForUserOffline() {
        showToast(R.string.login_no_network)
    }

    override fun showToastForRecordsUpToDate() {
        showToast(R.string.records_up_to_date)
    }
}
